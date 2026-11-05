package com.shopexpress.orders.saga.handler;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.shopexpress.orders.client.CatalogClient;
import com.shopexpress.orders.client.RejectedException;
import com.shopexpress.orders.client.ServiceUnavailableException;
import com.shopexpress.orders.model.CancellationReason;
import com.shopexpress.orders.model.Order;
import com.shopexpress.orders.model.OrderStatus;
import com.shopexpress.orders.model.PendingAction;
import com.shopexpress.orders.saga.OrderContext;
import com.shopexpress.orders.saga.OrderHandler;
import com.shopexpress.orders.saga.Retrier;
import com.shopexpress.orders.saga.StepFailedException;

// Paso 7, el pivote de la SAGA. Hasta acá, una falla se compensa; desde que la reserva se confirma, el stock está
// vendido y solo se avanza (RN-09, D-12). Va después de autorizar: confirmar sin el pago asegurado dejaría stock
// vendido sin cobrar.
@Component
public class OrderConfirmer extends OrderHandler {

	private static final Logger log = LoggerFactory.getLogger(OrderConfirmer.class);

	private final CatalogClient catalogClient;
	private final Retrier retrier;

	public OrderConfirmer(CatalogClient catalogClient, Retrier retrier) {
		this.catalogClient = catalogClient;
		this.retrier = retrier;
	}

	@Override
	protected void process(OrderContext context) {
		Order order = context.getOrder();
		try {
			retrier.call("Pedido " + order.getId() + ": confirmar la reserva",
					attempt -> catalogClient.confirm(order.getId()));
		}
		catch (RejectedException e) {
			// La reserva se liberó antes de confirmarse, o no existe: el stock no está asegurado. El orquestador
			// reversa la autorización y cancela (F-08).
			throw new StepFailedException(CancellationReason.RESERVATION_LOST, e.getMessage());
		}
		catch (ServiceUnavailableException e) {
			// Se agotaron los intentos y no se sabe si la confirmación entró: no se puede compensar, porque quizás el
			// stock ya está vendido, ni avanzar, porque quizás no. El pedido queda PROCESSING y la respuesta es 202
			// (F-09). El recuperador vuelve a confirmar: si da 200, completa la compra; si da 409, la cancela.
			order.addPendingAction(PendingAction.Type.CONFIRM_RESERVATION, e.getMessage());
			log.warn("Pedido {}: Catálogo no respondió al confirmar la reserva. Queda PROCESSING. Causa: {}",
					order.getId(), e.getMessage());
			context.stop();
			return;
		}
		order.setStatus(OrderStatus.CONFIRMED);
		context.markPivotPassed();
	}

}
