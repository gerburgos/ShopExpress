package com.shopexpress.orders.saga.handler;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.stereotype.Component;

import com.shopexpress.orders.model.CancellationReason;
import com.shopexpress.orders.model.Order;
import com.shopexpress.orders.model.Payment;
import com.shopexpress.orders.model.PaymentStatus;
import com.shopexpress.orders.payment.Authorization;
import com.shopexpress.orders.payment.GatewayTimeoutException;
import com.shopexpress.orders.payment.SimulatedOutcome;
import com.shopexpress.orders.payment.SimulatedPaymentGateway;
import com.shopexpress.orders.saga.OrderContext;
import com.shopexpress.orders.saga.OrderHandler;
import com.shopexpress.orders.saga.Retrier;
import com.shopexpress.orders.saga.StepFailedException;

// Paso 6, ProcesadorDePago en la consigna. Solo autoriza: retiene el monto sin cobrarlo. El cobro (la captura) espera
// al paso 8, después del pivote, para no mover plata hasta que el stock está asegurado (D-07).
@Component
public class PaymentProcessor extends OrderHandler {

	private static final Logger log = LoggerFactory.getLogger(PaymentProcessor.class);

	private final SimulatedPaymentGateway gateway;
	private final MongoTemplate mongoTemplate;
	private final Retrier retrier;

	public PaymentProcessor(SimulatedPaymentGateway gateway, MongoTemplate mongoTemplate, Retrier retrier) {
		this.gateway = gateway;
		this.mongoTemplate = mongoTemplate;
		this.retrier = retrier;
	}

	@Override
	protected void process(OrderContext context) {
		Order order = context.getOrder();
		Payment payment = order.getPayment();
		// PENDING se guarda antes de llamar: si el servicio se cae durante la llamada, un reinicio sabe que pudo haber
		// quedado una autorización hecha, y la reversa (3.3.4).
		payment.setStatus(PaymentStatus.PENDING);
		mongoTemplate.save(order);
		Authorization authorization;
		try {
			// Cada intento usa el orderId como clave: si una autorización anterior entró y se perdió la respuesta, la
			// pasarela devuelve esa misma en lugar de retener otra vez (RN-07).
			authorization = retrier.call("Pedido " + order.getId() + ": autorizar el pago", attempt -> {
				try {
					return gateway.authorize(order.getId(), order.getTotal(), outcome(context));
				}
				catch (GatewayTimeoutException e) {
					payment.record("AUTHORIZE", "TIMEOUT", attempt, "La pasarela no respondió");
					throw e;
				}
			});
		}
		catch (GatewayTimeoutException e) {
			// Se agotaron los intentos. Un timeout no es un rechazo: la autorización pudo haberse hecho y el monto
			// estar retenido. Se reversa antes de cancelar (F-07).
			reverse(order);
			throw new StepFailedException(CancellationReason.PAYMENT_TIMEOUT, e.getMessage());
		}
		if (authorization.status() == Authorization.AuthorizationStatus.DECLINED) {
			payment.setStatus(PaymentStatus.DECLINED);
			payment.record("AUTHORIZE", "DECLINED", lastAttempt(payment, "AUTHORIZE") + 1, null);
			// No quedó nada retenido, así que no hay que reversar: el orquestador libera la reserva y cancela (F-06).
			throw new StepFailedException(CancellationReason.PAYMENT_DECLINED, "La pasarela rechazó la autorización");
		}
		payment.setStatus(PaymentStatus.AUTHORIZED);
		payment.setAuthorizationCode(authorization.authorizationCode());
		payment.record("AUTHORIZE", "APPROVED", lastAttempt(payment, "AUTHORIZE") + 1, null);
	}

	// Un paso posterior falló antes del pivote (la reserva se perdió, F-08): se libera la retención.
	@Override
	public void compensate(OrderContext context) {
		reverse(context.getOrder());
	}

	private void reverse(Order order) {
		Payment payment = order.getPayment();
		// REVERSAL_PENDING antes de llamar, por la misma razón que PENDING: si la llamada no termina, queda escrito
		// que hay una retención por liberar.
		payment.setStatus(PaymentStatus.REVERSAL_PENDING);
		mongoTemplate.save(order);
		try {
			retrier.call("Pedido " + order.getId() + ": reversar el pago", attempt -> {
				gateway.reverse(order.getId());
				payment.record("REVERSE", "OK", attempt, null);
				return null;
			});
			payment.setStatus(PaymentStatus.REVERSED);
		}
		catch (RuntimeException e) {
			// Queda REVERSAL_PENDING; la acción pendiente REVERSE_PAYMENT lo reintenta en segundo plano.
			payment.record("REVERSE", "ERROR", 1, e.getMessage());
			log.warn("Pedido {}: no se pudo reversar la autorización. Causa: {}", order.getId(), e.getMessage());
		}
	}

	// El número de intento de la respuesta que llegó: uno más que los timeouts anteriores.
	private static int lastAttempt(Payment payment, String operation) {
		return (int) payment.getHistory().stream().filter(entry -> entry.operation().equals(operation)).count();
	}

	private static SimulatedOutcome outcome(OrderContext context) {
		// DataValidator ya comprobó que el valor sea uno de la lista.
		return context.getSimulatedPayment() == null ? SimulatedOutcome.APPROVED
				: SimulatedOutcome.valueOf(context.getSimulatedPayment());
	}

}
