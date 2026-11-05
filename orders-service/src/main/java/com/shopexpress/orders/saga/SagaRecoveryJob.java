package com.shopexpress.orders.saga;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.shopexpress.orders.client.CatalogClient;
import com.shopexpress.orders.client.RejectedException;
import com.shopexpress.orders.client.ServiceUnavailableException;
import com.shopexpress.orders.client.UsersClient;
import com.shopexpress.orders.model.CancellationReason;
import com.shopexpress.orders.model.Order;
import com.shopexpress.orders.model.OrderStatus;
import com.shopexpress.orders.model.Payment;
import com.shopexpress.orders.model.PaymentStatus;
import com.shopexpress.orders.model.PendingAction;
import com.shopexpress.orders.payment.SimulatedPaymentGateway;

// El segundo nivel de reintento (3.3.6, D-10): resuelve en segundo plano lo que la solicitud no pudo terminar, para
// caídas largas y reinicios. Hace dos trabajos en cada pasada:
// - retoma los pedidos abandonados, los que tienen el lease vencido porque Pedidos se cayó a mitad de la compra;
// - procesa las acciones pendientes, con un intento por acción y pasada.
// No usa los reintentos de la solicitud: la pasada siguiente ya es el reintento.
@Component
public class SagaRecoveryJob {

	private static final Logger log = LoggerFactory.getLogger(SagaRecoveryJob.class);

	private final MongoTemplate mongoTemplate;
	private final CatalogClient catalogClient;
	private final UsersClient usersClient;
	private final SimulatedPaymentGateway gateway;
	private final SagaSettings settings;
	private final int maxAttempts;

	public SagaRecoveryJob(MongoTemplate mongoTemplate, CatalogClient catalogClient, UsersClient usersClient,
			SimulatedPaymentGateway gateway, SagaSettings settings,
			@Value("${saga.recovery.max-attempts}") int maxAttempts) {
		this.mongoTemplate = mongoTemplate;
		this.catalogClient = catalogClient;
		this.usersClient = usersClient;
		this.gateway = gateway;
		this.settings = settings;
		this.maxAttempts = maxAttempts;
	}

	// fixedDelay y no fixedRate: la pasada siguiente empieza cuando termina la anterior, así dos pasadas nunca corren a
	// la vez en esta instancia.
	@Scheduled(fixedDelayString = "${saga.recovery.interval-ms}", initialDelayString = "${saga.recovery.interval-ms}")
	public void run() {
		Instant now = Instant.now();
		// Un intento por acción y pasada: un pedido que se retoma como abandonado no se vuelve a procesar en la segunda
		// parte de la misma pasada.
		Set<String> processed = new HashSet<>();
		Query abandoned = Query.query(Criteria.where("status").in(OrderStatus.PROCESSING, OrderStatus.CONFIRMED)
				.and("leaseUntil").lt(now));
		for (Order candidate : mongoTemplate.find(abandoned, Order.class)) {
			Order order = claim(candidate.getId());
			if (order != null) {
				boolean decided = recoverAbandoned(order);
				processPendingActions(order);
				release(order, decided);
				processed.add(order.getId());
			}
		}
		Query pending = Query.query(new Criteria().andOperator(
				Criteria.where("pendingActions.0").exists(true),
				// ne(true) y no is(false): un pedido sin el campo también cuenta.
				Criteria.where("requiresAttention").ne(true),
				leaseFree(Instant.now())));
		for (Order candidate : mongoTemplate.find(pending, Order.class)) {
			if (processed.contains(candidate.getId())) {
				continue;
			}
			Order order = claim(candidate.getId());
			if (order != null) {
				processPendingActions(order);
				release(order, true);
			}
		}
	}

	// Toma el pedido con un update condicional: solo si nadie lo tiene (sin lease, o con el lease vencido). Si una
	// solicitud lo renovó en el medio, o lo tomó otra pasada, no se toca. Mientras lo procesa, el lease nuevo impide
	// que lo tome otro.
	private Order claim(String orderId) {
		Instant now = Instant.now();
		Query free = Query.query(new Criteria().andOperator(Criteria.where("id").is(orderId), leaseFree(now)));
		return mongoTemplate.findAndModify(free, new Update().set("leaseUntil", settings.leaseFrom(now)),
				FindAndModifyOptions.options().returnNew(true), Order.class);
	}

	private static Criteria leaseFree(Instant now) {
		return new Criteria().orOperator(Criteria.where("leaseUntil").is(null), Criteria.where("leaseUntil").lt(now));
	}

	// Si no se pudo decidir qué hacer con un pedido abandonado, el lease queda vencido para que la próxima pasada lo
	// vuelva a tomar. Si se decidió, se borra, como cuando una solicitud termina.
	private void release(Order order, boolean decided) {
		Instant now = Instant.now().truncatedTo(ChronoUnit.MILLIS);
		order.setLeaseUntil(decided ? null : now);
		order.setUpdatedAt(now);
		mongoTemplate.save(order);
	}

	// Devuelve false si todavía no se puede decidir, porque Catálogo no responde.
	private boolean recoverAbandoned(Order order) {
		if (order.getStatus() == OrderStatus.CONFIRMED) {
			// Se cayó después del pivote: la compra está vendida, solo faltan los pasos finales (F-12).
			log.info("Pedido {} abandonado después del pivote: se completan los pasos que faltan.", order.getId());
			completeAfterPivot(order);
			return true;
		}
		// En PROCESSING no se sabe si el pivote ocurrió: la confirmación pudo haber entrado justo antes de la caída. Se
		// pregunta liberando la reserva: si Catálogo la libera, no estaba confirmada; si responde
		// RESERVATION_CONFIRMED, sí. Liberar es seguro en los dos casos: no devuelve stock vendido (RN-09) y, si nunca
		// hubo reserva, deja una lápida.
		try {
			catalogClient.release(order.getId());
		}
		catch (RejectedException e) {
			if (!"RESERVATION_CONFIRMED".equals(e.getCode())) {
				throw e;
			}
			log.info("Pedido {} abandonado: la reserva ya estaba confirmada. Se completa la compra.", order.getId());
			order.setStatus(OrderStatus.CONFIRMED);
			completeAfterPivot(order);
			return true;
		}
		catch (ServiceUnavailableException e) {
			log.warn("Pedido {} abandonado: Catálogo no responde, se decide en la próxima pasada. Causa: {}",
					order.getId(), e.getMessage());
			return false;
		}
		log.info("Pedido {} abandonado antes del pivote: se cancela y se compensa.", order.getId());
		order.setStatus(OrderStatus.CANCELLED);
		order.setReason(CancellationReason.INTERRUPTED);
		Payment payment = order.getPayment();
		// Un pago PENDING pudo haber quedado autorizado sin que llegara la respuesta: se reversa igual. El reverso es
		// idempotente, así que no pasa nada si no había nada retenido.
		if (payment.getStatus() == PaymentStatus.PENDING || payment.getStatus() == PaymentStatus.AUTHORIZED
				|| payment.getStatus() == PaymentStatus.REVERSAL_PENDING) {
			payment.setStatus(PaymentStatus.REVERSAL_PENDING);
			order.addPendingAction(PendingAction.Type.REVERSE_PAYMENT, "Pedido abandonado antes del pivote");
		}
		return true;
	}

	private static void completeAfterPivot(Order order) {
		if (order.getPayment().getStatus() != PaymentStatus.CAPTURED) {
			order.addPendingAction(PendingAction.Type.CAPTURE_PAYMENT, "Pedido abandonado después del pivote");
		}
		// Puede que ya estuviera registrada: el registro es idempotente por orderId, así que repetirlo no la duplica.
		order.addPendingAction(PendingAction.Type.RECORD_PURCHASE, "Pedido abandonado después del pivote");
	}

	private void processPendingActions(Order order) {
		List<PendingAction> actions = List.copyOf(order.getPendingActions());
		// Se vacía antes: una acción resuelta puede agregar otras (confirmar agrega capturar y registrar), y esas se
		// procesan en la próxima pasada.
		order.setPendingActions(new ArrayList<>());
		for (PendingAction action : actions) {
			try {
				execute(order, action.type());
				log.info("Pedido {}: acción pendiente {} resuelta.", order.getId(), action.type());
			}
			catch (RuntimeException e) {
				PendingAction failed = action.failedAgain(e.getMessage(), Instant.now().truncatedTo(ChronoUnit.MILLIS));
				order.getPendingActions().add(failed);
				if (failed.attempts() >= maxAttempts) {
					// Se deja de reintentar: alguien tiene que mirarlo. El estado del pedido no cambia (F-11).
					order.setRequiresAttention(true);
					log.error("Pedido {}: la acción pendiente {} agotó los {} intentos del recuperador. Requiere "
							+ "atención. Último error: {}", order.getId(), action.type(), maxAttempts, e.getMessage());
				}
				else {
					log.info("Pedido {}: la acción pendiente {} falló (intento {} de {}). Causa: {}", order.getId(),
							action.type(), failed.attempts(), maxAttempts, e.getMessage());
				}
			}
		}
	}

	// Cada acción usa el orderId como clave en el otro lado, así que repetirla no duplica efectos.
	private void execute(Order order, PendingAction.Type type) {
		Payment payment = order.getPayment();
		switch (type) {
			case RELEASE_RESERVATION -> catalogClient.release(order.getId());
			case REVERSE_PAYMENT -> {
				gateway.reverse(order.getId());
				payment.setStatus(PaymentStatus.REVERSED);
				payment.record("REVERSE", "OK", nextAttempt(payment, "REVERSE"), null);
			}
			case CONFIRM_RESERVATION -> confirm(order);
			case CAPTURE_PAYMENT -> {
				gateway.capture(order.getId());
				payment.setStatus(PaymentStatus.CAPTURED);
				payment.record("CAPTURE", "OK", nextAttempt(payment, "CAPTURE"), null);
			}
			case RECORD_PURCHASE -> usersClient.recordPurchase(order);
		}
	}

	// F-09: la confirmación no tuvo respuesta en la solicitud. Si ahora da 200, la compra sigue; si da 409, la reserva
	// se perdió y se aplica F-08.
	private static void confirmResult(Order order, boolean confirmed) {
		if (confirmed) {
			order.setStatus(OrderStatus.CONFIRMED);
			completeAfterPivot(order);
			return;
		}
		order.setStatus(OrderStatus.CANCELLED);
		order.setReason(CancellationReason.RESERVATION_LOST);
		order.getPayment().setStatus(PaymentStatus.REVERSAL_PENDING);
		order.addPendingAction(PendingAction.Type.REVERSE_PAYMENT, "La reserva se perdió antes de confirmarse");
	}

	private void confirm(Order order) {
		try {
			catalogClient.confirm(order.getId());
		}
		catch (RejectedException e) {
			confirmResult(order, false);
			return;
		}
		confirmResult(order, true);
	}

	private static int nextAttempt(Payment payment, String operation) {
		return (int) payment.getHistory().stream().filter(entry -> entry.operation().equals(operation)).count() + 1;
	}

}
