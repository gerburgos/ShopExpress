package com.shopexpress.orders.saga.handler;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.shopexpress.orders.model.Order;
import com.shopexpress.orders.model.Payment;
import com.shopexpress.orders.model.PaymentStatus;
import com.shopexpress.orders.payment.SimulatedPaymentGateway;
import com.shopexpress.orders.saga.OrderContext;
import com.shopexpress.orders.saga.OrderHandler;

// Paso 8. Cobra lo que se autorizó en el paso 6. Va después del pivote: el stock ya está vendido.
@Component
public class PaymentCapturer extends OrderHandler {

	private static final Logger log = LoggerFactory.getLogger(PaymentCapturer.class);

	private final SimulatedPaymentGateway gateway;

	public PaymentCapturer(SimulatedPaymentGateway gateway) {
		this.gateway = gateway;
	}

	@Override
	protected void process(OrderContext context) {
		Order order = context.getOrder();
		Payment payment = order.getPayment();
		try {
			gateway.capture(order.getId());
			payment.setStatus(PaymentStatus.CAPTURED);
			payment.record("CAPTURE", "OK", null);
		}
		catch (RuntimeException e) {
			// Después del pivote ninguna falla cancela el pedido: queda CONFIRMED con el pago AUTHORIZED, y la captura
			// se reintenta (F-10). La acción pendiente CAPTURE_PAYMENT llega en INC-5. Por eso no se lanza
			// StepFailedException.
			payment.record("CAPTURE", "ERROR", e.getMessage());
			log.warn("Pedido {}: no se pudo capturar el pago. Causa: {}", order.getId(), e.getMessage());
		}
	}

}
