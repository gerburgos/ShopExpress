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
import com.shopexpress.orders.saga.OrderContext;
import com.shopexpress.orders.saga.OrderHandler;
import com.shopexpress.orders.saga.StepFailedException;

// Paso 4, ValidadorDeStock en la consigna. Valida reservando y no consultando: una consulta puede venir de la caché y
// estar atrasada, y solo el descuento condicional de Catálogo garantiza que no haya sobreventa (RN-08).
@Component
public class StockReserver extends OrderHandler {

	private static final Logger log = LoggerFactory.getLogger(StockReserver.class);

	private final CatalogClient catalogClient;

	public StockReserver(CatalogClient catalogClient) {
		this.catalogClient = catalogClient;
	}

	@Override
	protected void process(OrderContext context) {
		Order order = context.getOrder();
		try {
			CatalogReservation reservation = catalogClient.reserve(order.getId(), context.getRequest().items());
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
			// No se sabe si la reserva se hizo: la respuesta pudo perderse después de que Catálogo descontara. Se
			// libera por las dudas; si nunca llegó, la liberación deja una lápida y la reserva tardía no descuenta
			// (F-05). Los reintentos antes de rendirse llegan en INC-5.
			releaseQuietly(order.getId());
			throw new StepFailedException(CancellationReason.CATALOG_UNAVAILABLE, e.getMessage());
		}
	}

	// Un paso posterior falló antes del pivote: se devuelve el stock (INT-02).
	@Override
	public void compensate(OrderContext context) {
		catalogClient.release(context.getOrder().getId());
	}

	private void releaseQuietly(String orderId) {
		try {
			catalogClient.release(orderId);
		}
		catch (RuntimeException e) {
			log.warn("Pedido {}: no se pudo liberar la reserva después de que Catálogo no respondiera. Causa: {}",
					orderId, e.getMessage());
		}
	}

}
