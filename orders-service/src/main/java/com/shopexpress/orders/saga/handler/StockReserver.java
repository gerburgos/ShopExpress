package com.shopexpress.orders.saga.handler;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.shopexpress.orders.client.CatalogClient;
import com.shopexpress.orders.client.CatalogReservation;
import com.shopexpress.orders.client.RejectedException;
import com.shopexpress.orders.client.ServiceUnavailableException;
import com.shopexpress.orders.model.CancellationReason;
import com.shopexpress.orders.model.Order;
import com.shopexpress.orders.model.PendingAction;
import com.shopexpress.orders.saga.OrderContext;
import com.shopexpress.orders.saga.OrderHandler;
import com.shopexpress.orders.saga.Retrier;
import com.shopexpress.orders.saga.StepFailedException;

// Paso 4, ValidadorDeStock en la consigna. Valida reservando y no consultando: una consulta puede venir de la caché y
// estar atrasada, y solo el descuento condicional de Catálogo garantiza que no haya sobreventa (RN-08).
@Component
public class StockReserver extends OrderHandler {

	private static final Logger log = LoggerFactory.getLogger(StockReserver.class);

	private final CatalogClient catalogClient;
	private final Retrier retrier;

	public StockReserver(CatalogClient catalogClient, Retrier retrier) {
		this.catalogClient = catalogClient;
		this.retrier = retrier;
	}

	@Override
	protected void process(OrderContext context) {
		Order order = context.getOrder();
		try {
			// Repetir con el mismo orderId es seguro: si la reserva anterior entró, Catálogo devuelve esa (INT-01).
			CatalogReservation reservation = retrier.call("Pedido " + order.getId() + ": reservar el stock",
					attempt -> catalogClient.reserve(order.getId(), context.getRequest().items()));
			order.setItems(reservation.items());
		}
		catch (RejectedException e) {
			// Catálogo abortó su transacción: no quedó nada reservado y no hay que liberar (F-04).
			if ("OUT_OF_STOCK".equals(e.getCode())) {
				throw new StepFailedException(CancellationReason.OUT_OF_STOCK, e.getMessage());
			}
			if ("PRODUCT_NOT_FOUND".equals(e.getCode())) {
				throw new StepFailedException(CancellationReason.PRODUCT_NOT_FOUND, e.getMessage());
			}
			// Cualquier otro rechazo es un error de programación: Pedidos mandó algo que no debía.
			throw new IllegalStateException("Catálogo rechazó la reserva del pedido " + order.getId() + ": "
					+ e.getCode() + " " + e.getMessage(), e);
		}
		catch (ServiceUnavailableException e) {
			// Se agotaron los intentos y no se sabe si la reserva se hizo: la respuesta pudo perderse después de que
			// Catálogo descontara. Se libera por las dudas; si nunca llegó, la liberación deja una lápida y la reserva
			// tardía no descuenta (F-05).
			releaseQuietly(order);
			throw new StepFailedException(CancellationReason.CATALOG_UNAVAILABLE, e.getMessage());
		}
	}

	// Un paso posterior falló antes del pivote: se devuelve el stock (INT-02).
	@Override
	public void compensate(OrderContext context) {
		release(context.getOrder().getId());
	}

	@Override
	public PendingAction.Type compensationAction() {
		return PendingAction.Type.RELEASE_RESERVATION;
	}

	private void release(String orderId) {
		retrier.call("Pedido " + orderId + ": liberar la reserva", attempt -> catalogClient.release(orderId));
	}

	private void releaseQuietly(Order order) {
		try {
			release(order.getId());
		}
		catch (RuntimeException e) {
			// Catálogo sigue sin responder: el recuperador lo reintenta cuando vuelva, así no queda stock reservado de
			// un pedido cancelado (CA-15).
			order.addPendingAction(PendingAction.Type.RELEASE_RESERVATION, e.getMessage());
			log.warn("Pedido {}: no se pudo liberar la reserva; queda pendiente. Causa: {}", order.getId(),
					e.getMessage());
		}
	}

}
