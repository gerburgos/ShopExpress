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
import com.shopexpress.orders.saga.StepFailedException;

// Paso 6, ProcesadorDePago en la consigna. Solo autoriza: retiene el monto sin cobrarlo. El cobro (la captura) espera
// al paso 8, después del pivote, para no mover plata hasta que el stock está asegurado (D-07).
@Component
public class PaymentProcessor extends OrderHandler {

	private static final Logger log = LoggerFactory.getLogger(PaymentProcessor.class);

	private final SimulatedPaymentGateway gateway;
	private final MongoTemplate mongoTemplate;

	public PaymentProcessor(SimulatedPaymentGateway gateway, MongoTemplate mongoTemplate) {
		this.gateway = gateway;
		this.mongoTemplate = mongoTemplate;
	}

	@Override
	protected void process(OrderContext context) {
		Order order = context.getOrder();
		Payment payment = order.getPayment();
		// PENDING se guarda antes de llamar: si el servicio se cae durante la llamada, un reinicio sabe que pudo haber
		// quedado una autorización hecha, y la reversa (3.3.4).
		payment.setStatus(PaymentStatus.PENDING);
		mongoTemplate.save(order);
		try {
			Authorization authorization = gateway.authorize(order.getId(), order.getTotal(), outcome(context));
			if (authorization.status() == Authorization.AuthorizationStatus.DECLINED) {
				payment.setStatus(PaymentStatus.DECLINED);
				payment.record("AUTHORIZE", "DECLINED", null);
				// No quedó nada retenido, así que no hay que reversar: el orquestador libera la reserva y cancela
				// (F-06).
				throw new StepFailedException(CancellationReason.PAYMENT_DECLINED,
						"La pasarela rechazó la autorización");
			}
			payment.setStatus(PaymentStatus.AUTHORIZED);
			payment.setAuthorizationCode(authorization.authorizationCode());
			payment.record("AUTHORIZE", "APPROVED", null);
		}
		catch (GatewayTimeoutException e) {
			payment.record("AUTHORIZE", "TIMEOUT", "La pasarela no respondió");
			// Un timeout no es un rechazo: la autorización pudo haberse hecho y el monto estar retenido. Se reversa
			// antes de cancelar (F-07). Los reintentos con la misma clave antes de rendirse llegan en INC-5.
			reverse(order);
			throw new StepFailedException(CancellationReason.PAYMENT_TIMEOUT, e.getMessage());
		}
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
			gateway.reverse(order.getId());
			payment.setStatus(PaymentStatus.REVERSED);
			payment.record("REVERSE", "OK", null);
		}
		catch (RuntimeException e) {
			// Queda REVERSAL_PENDING; la acción pendiente REVERSE_PAYMENT que lo reintenta llega en INC-5.
			payment.record("REVERSE", "ERROR", e.getMessage());
			log.warn("Pedido {}: no se pudo reversar la autorización. Causa: {}", order.getId(), e.getMessage());
		}
	}

	private static SimulatedOutcome outcome(OrderContext context) {
		// DataValidator ya comprobó que el valor sea uno de la lista.
		return context.getSimulatedPayment() == null ? SimulatedOutcome.APPROVED
				: SimulatedOutcome.valueOf(context.getSimulatedPayment());
	}

}
