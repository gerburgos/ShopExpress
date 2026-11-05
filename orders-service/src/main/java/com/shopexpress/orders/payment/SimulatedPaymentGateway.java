package com.shopexpress.orders.payment;

import java.math.BigDecimal;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Component;

import com.shopexpress.orders.payment.Authorization.AuthorizationStatus;

// Pasarela de pago simulada (D-07). Separa autorizar de capturar, como las pasarelas reales: autorizar retiene el
// monto sin cobrarlo, y si la compra falla antes del pivote se reversa la retención en lugar de devolver un cobro.
// Las tres operaciones son idempotentes por orderId: repetirlas no retiene ni cobra dos veces.
@Component
public class SimulatedPaymentGateway {

	private static final Logger log = LoggerFactory.getLogger(SimulatedPaymentGateway.class);

	private final MongoTemplate mongoTemplate;

	public SimulatedPaymentGateway(MongoTemplate mongoTemplate) {
		this.mongoTemplate = mongoTemplate;
	}

	public Authorization authorize(String orderId, BigDecimal amount, SimulatedOutcome outcome) {
		Authorization existing = mongoTemplate.findById(orderId, Authorization.class);
		if (existing != null) {
			// Repetición: se responde lo mismo que la primera vez, sin retener otra vez.
			return existing;
		}
		AuthorizationStatus status = outcome == SimulatedOutcome.DECLINED ? AuthorizationStatus.DECLINED
				: AuthorizationStatus.AUTHORIZED;
		String code = status == AuthorizationStatus.AUTHORIZED ? "auth_" + UUID.randomUUID() : null;
		Authorization authorization;
		try {
			authorization = mongoTemplate.insert(new Authorization(orderId, status, code, amount));
		}
		catch (DuplicateKeyException e) {
			// Otra llamada con el mismo orderId llegó primero: vale la suya.
			authorization = mongoTemplate.findById(orderId, Authorization.class);
		}
		if (outcome == SimulatedOutcome.NO_RESPONSE) {
			// La autorización quedó hecha, pero la respuesta "se pierde". Pedidos solo ve el timeout.
			log.info("Pasarela simulada: autorización del pedido {} hecha, pero sin respuesta", orderId);
			throw new GatewayTimeoutException("La pasarela no respondió a la autorización del pedido " + orderId);
		}
		return authorization;
	}

	public Authorization capture(String orderId) {
		Authorization captured = transition(orderId, AuthorizationStatus.AUTHORIZED, AuthorizationStatus.CAPTURED);
		if (captured != null) {
			return captured;
		}
		Authorization current = mongoTemplate.findById(orderId, Authorization.class);
		if (current != null && current.status() == AuthorizationStatus.CAPTURED) {
			return current;
		}
		throw new IllegalStateException("No hay una autorización vigente para capturar el pedido " + orderId);
	}

	public void reverse(String orderId) {
		if (transition(orderId, AuthorizationStatus.AUTHORIZED, AuthorizationStatus.REVERSED) != null) {
			return;
		}
		Authorization current = mongoTemplate.findById(orderId, Authorization.class);
		// Ya reversada, rechazada o inexistente: no hay nada retenido. Responder bien hace que repetir el reverso sea
		// seguro (RN-06).
		if (current != null && current.status() == AuthorizationStatus.CAPTURED) {
			// Después de capturar no se reversa: se cobró. Si pasa, es un error de Pedidos, que no reversa después del
			// pivote.
			throw new IllegalStateException("El pago del pedido " + orderId + " ya fue capturado");
		}
	}

	// Update condicional: solo cambia si el estado es el esperado, así dos operaciones simultáneas no se pisan.
	private Authorization transition(String orderId, AuthorizationStatus from, AuthorizationStatus to) {
		Query query = Query.query(Criteria.where("orderId").is(orderId).and("status").is(from));
		return mongoTemplate.findAndModify(query, new Update().set("status", to),
				FindAndModifyOptions.options().returnNew(true), Authorization.class);
	}

}
