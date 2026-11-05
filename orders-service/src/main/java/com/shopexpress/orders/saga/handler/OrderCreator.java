package com.shopexpress.orders.saga.handler;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Component;

import com.shopexpress.orders.exception.ApiException;
import com.shopexpress.orders.model.Order;
import com.shopexpress.orders.model.OrderStatus;
import com.shopexpress.orders.model.Payment;
import com.shopexpress.orders.model.PaymentMethod;
import com.shopexpress.orders.saga.OrderContext;
import com.shopexpress.orders.saga.OrderHandler;
import com.shopexpress.orders.saga.RequestHash;
import com.shopexpress.orders.saga.SagaSettings;

// Paso 3. Crea el pedido en PROCESSING antes de tocar otro servicio: así el orderId existe para usarlo como clave en
// Catálogo y en la pasarela, y un reinicio encuentra el pedido a medio hacer (D-10).
@Component
public class OrderCreator extends OrderHandler {

	private final MongoTemplate mongoTemplate;
	private final SagaSettings settings;

	public OrderCreator(MongoTemplate mongoTemplate, SagaSettings settings) {
		this.mongoTemplate = mongoTemplate;
		this.settings = settings;
	}

	@Override
	protected void process(OrderContext context) {
		// En milisegundos, que es lo que guarda MongoDB: con nanosegundos, la respuesta original y la de una repetición
		// mostrarían createdAt distinto, y tienen que ser iguales (RF-05).
		Instant now = Instant.now().truncatedTo(ChronoUnit.MILLIS);
		Order order = new Order();
		order.setId("ord_" + UUID.randomUUID());
		order.setIdempotencyKey(context.getIdempotencyKey());
		String requestHash = RequestHash.of(context.getRequest(), context.getSimulatedPayment());
		order.setRequestHash(requestHash);
		// El lease va desde el insert: si quedara vacío hasta el primer guardado, una repetición que llega en ese
		// instante vería el pedido en PROCESSING sin lease, como si estuviera abandonado, en lugar de recibir 409.
		order.setLeaseUntil(settings.leaseFrom(now));
		order.setUserId(context.getRequest().userId());
		order.setStatus(OrderStatus.PROCESSING);
		order.setCurrency("UYU");
		order.setPayment(new Payment(PaymentMethod.valueOf(context.getRequest().payment().method())));
		order.setCreatedAt(now);
		order.setUpdatedAt(now);
		try {
			context.setOrder(mongoTemplate.insert(order));
		}
		catch (DuplicateKeyException e) {
			if (!String.valueOf(e.getMessage()).contains(Order.IDEMPOTENCY_KEY_INDEX)) {
				throw e;
			}
			// La clave ya tiene un pedido. Ninguno de los tres casos reserva ni cobra otra vez (RN-03, F-03).
			Order existing = mongoTemplate.findOne(
					Query.query(Criteria.where("idempotencyKey").is(context.getIdempotencyKey())), Order.class);
			if (!requestHash.equals(existing.getRequestHash())) {
				// Otra compra con una clave ya usada: devolver el pedido existente sería responder por una compra que
				// el cliente no pidió.
				throw new ApiException(HttpStatus.UNPROCESSABLE_CONTENT, "IDEMPOTENCY_KEY_REUSED",
						"La clave " + context.getIdempotencyKey() + " ya se usó con otra compra");
			}
			if (inProgress(existing)) {
				// La solicitud original sigue trabajando: todavía no hay una respuesta que repetir. 409 para que el
				// cliente reintente después con la misma clave.
				throw new ApiException(HttpStatus.CONFLICT, "ORDER_IN_PROGRESS",
						"La compra con la clave " + context.getIdempotencyKey() + " todavía está en curso; reintentar");
			}
			// Terminada: el cliente repitió porque no le llegó la respuesta. Se devuelve el mismo pedido.
			context.useExistingOrder(existing);
		}
	}

	private static boolean inProgress(Order order) {
		return order.getStatus() == OrderStatus.PROCESSING && order.getLeaseUntil() != null
				&& order.getLeaseUntil().isAfter(Instant.now());
	}

	// El pedido no se borra: queda CANCELLED con el motivo, para que el cliente y quien opera vean qué pasó.
	@Override
	public void compensate(OrderContext context) {
		context.getOrder().setStatus(OrderStatus.CANCELLED);
		context.getOrder().setReason(context.getFailureReason());
	}

}
