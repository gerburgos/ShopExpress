package com.shopexpress.orders.saga.handler;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.shopexpress.orders.client.UsersClient;
import com.shopexpress.orders.model.Order;
import com.shopexpress.orders.model.PendingAction;
import com.shopexpress.orders.saga.OrderContext;
import com.shopexpress.orders.saga.OrderHandler;
import com.shopexpress.orders.saga.Retrier;

// Paso 9. Registra la compra en el historial de Usuarios (INT-04). Es el último: el historial acepta consistencia
// eventual, así que su falla no puede cambiar el resultado de una compra ya confirmada y cobrada (D-09).
@Component
public class PurchaseHistoryRecorder extends OrderHandler {

	private static final Logger log = LoggerFactory.getLogger(PurchaseHistoryRecorder.class);

	private final UsersClient usersClient;
	private final Retrier retrier;

	public PurchaseHistoryRecorder(UsersClient usersClient, Retrier retrier) {
		this.usersClient = usersClient;
		this.retrier = retrier;
	}

	@Override
	protected void process(OrderContext context) {
		Order order = context.getOrder();
		try {
			retrier.call("Pedido " + order.getId() + ": registrar la compra en el historial", attempt -> {
				usersClient.recordPurchase(order);
				return null;
			});
		}
		catch (RuntimeException e) {
			// Después del pivote ninguna falla cancela el pedido: queda CONFIRMED, y el recuperador registra la compra
			// cuando Usuarios vuelva (F-10, CA-12).
			order.addPendingAction(PendingAction.Type.RECORD_PURCHASE, e.getMessage());
			log.warn("Pedido {}: no se pudo registrar la compra en el historial; queda pendiente. Causa: {}",
					order.getId(), e.getMessage());
		}
	}

}
