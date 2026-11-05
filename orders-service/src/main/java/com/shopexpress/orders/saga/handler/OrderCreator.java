package com.shopexpress.orders.saga.handler;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Component;

import com.shopexpress.orders.model.Order;
import com.shopexpress.orders.model.OrderStatus;
import com.shopexpress.orders.model.Payment;
import com.shopexpress.orders.model.PaymentMethod;
import com.shopexpress.orders.saga.OrderContext;
import com.shopexpress.orders.saga.OrderHandler;

// Paso 3. Crea el pedido en PROCESSING antes de tocar otro servicio: así el orderId existe para usarlo como clave en
// Catálogo y en la pasarela, y un reinicio encuentra el pedido a medio hacer (D-10).
@Component
public class OrderCreator extends OrderHandler {

	private final MongoTemplate mongoTemplate;

	public OrderCreator(MongoTemplate mongoTemplate) {
		this.mongoTemplate = mongoTemplate;
	}

	@Override
	protected void process(OrderContext context) {
		// En milisegundos, que es lo que guarda MongoDB: con nanosegundos, la respuesta original y la de una repetición
		// mostrarían createdAt distinto, y tienen que ser iguales (RF-05).
		Instant now = Instant.now().truncatedTo(ChronoUnit.MILLIS);
		Order order = new Order();
		order.setId("ord_" + UUID.randomUUID());
		order.setIdempotencyKey(context.getIdempotencyKey());
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
			// La clave ya tiene un pedido: el cliente repitió la solicitud porque no le llegó la respuesta. Se corta
			// la cadena y se devuelve ese pedido, sin reservar ni cobrar otra vez (RN-03, F-03). Distinguir una
			// solicitud todavía en curso o un cuerpo distinto llega en INC-5.
			context.useExistingOrder(mongoTemplate.findOne(
					Query.query(Criteria.where("idempotencyKey").is(context.getIdempotencyKey())), Order.class));
		}
	}

	// El pedido no se borra: queda CANCELLED con el motivo, para que el cliente y quien opera vean qué pasó.
	@Override
	public void compensate(OrderContext context) {
		context.getOrder().setStatus(OrderStatus.CANCELLED);
		context.getOrder().setReason(context.getFailureReason());
	}

}
