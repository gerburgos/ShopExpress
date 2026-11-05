package com.shopexpress.orders.saga.handler;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.shopexpress.orders.model.Order;
import com.shopexpress.orders.model.Payment;
import com.shopexpress.orders.model.PendingAction;
import com.shopexpress.orders.model.PaymentStatus;
import com.shopexpress.orders.payment.SimulatedPaymentGateway;
import com.shopexpress.orders.saga.OrderContext;
import com.shopexpress.orders.saga.OrderHandler;
import com.shopexpress.orders.saga.Retrier;

// Paso 8. Cobra lo que se autorizó en el paso 6. Va después del pivote: el stock ya está vendido.
@Component
public class PaymentCapturer extends OrderHandler {

	private static final Logger log = LoggerFactory.getLogger(PaymentCapturer.class);

	private final SimulatedPaymentGateway gateway;
	private final Retrier retrier;

	public PaymentCapturer(SimulatedPaymentGateway gateway, Retrier retrier) {
		this.gateway = gateway;
		this.retrier = retrier;
	}

	@Override
	protected void process(OrderContext context) {
		Order order = context.getOrder();
		Payment payment = order.getPayment();
		try {
			retrier.call("Pedido " + order.getId() + ": capturar el pago", attempt -> {
				gateway.capture(order.getId());
				payment.record("CAPTURE", "OK", attempt, null);
				return null;
			});
			payment.setStatus(PaymentStatus.CAPTURED);
		}
		catch (RuntimeException e) {
			// Después del pivote ninguna falla cancela el pedido: queda CONFIRMED con el pago AUTHORIZED, y la captura
			// queda pendiente para el recuperador (F-10). Por eso no se lanza StepFailedException.
			payment.record("CAPTURE", "ERROR", 1, e.getMessage());
			order.addPendingAction(PendingAction.Type.CAPTURE_PAYMENT, e.getMessage());
			log.warn("Pedido {}: no se pudo capturar el pago. Causa: {}", order.getId(), e.getMessage());
		}
	}

}
