package com.shopexpress.orders.saga;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.stereotype.Service;

import com.shopexpress.orders.dto.CreateOrderRequest;
import com.shopexpress.orders.model.Order;

// La SAGA orquestada: hace correr la cadena, guarda el avance después de cada paso y, si un paso falla, compensa los
// que terminaron en orden inverso. No implementa ningún paso: qué pasos hay y en qué orden lo define la cadena
// (3.3.1).
@Service
public class OrderOrchestrator {

	private static final Logger log = LoggerFactory.getLogger(OrderOrchestrator.class);

	private final OrderHandler chain;
	private final MongoTemplate mongoTemplate;
	private final SagaSettings settings;

	public OrderOrchestrator(@Qualifier("orderChain") OrderHandler chain, MongoTemplate mongoTemplate,
			SagaSettings settings) {
		this.chain = chain;
		this.mongoTemplate = mongoTemplate;
		this.settings = settings;
	}

	public Order placeOrder(String idempotencyKey, String simulatedPayment, CreateOrderRequest request) {
		OrderContext context = new OrderContext(idempotencyKey, simulatedPayment, request, this::saveProgress);
		try {
			chain.handle(context);
		}
		catch (StepFailedException e) {
			cancel(context, e);
		}
		finish(context);
		return context.getOrder();
	}

	// Se guarda el pedido entero y no un $set de campos: mientras el lease está vigente, solo esta solicitud lo
	// modifica (el recuperador toma únicamente pedidos con el lease vencido), así que no hay otra escritura que pisar.
	private void saveProgress(OrderContext context, OrderHandler step) {
		Order order = context.getOrder();
		// Antes de registrar el pedido no hay nada que guardar. Si la clave ya tenía un pedido, ese pedido es de otra
		// solicitud y no se toca.
		if (order == null || context.isExistingOrder()) {
			return;
		}
		Instant now = Instant.now().truncatedTo(ChronoUnit.MILLIS);
		order.setCompletedSteps(context.getCompletedSteps().stream().map(OrderHandler::name).toList());
		order.setUpdatedAt(now);
		// Se renueva en cada paso: alcanza con que cubra el paso siguiente en el peor caso (3.3.6).
		order.setLeaseUntil(settings.leaseFrom(now));
		mongoTemplate.save(order);
		delayForTests();
	}

	// La solicitud terminó, bien o mal: se borra el lease. Una repetición ya no recibe 409 sino la respuesta final, y el
	// recuperador no confunde el pedido con uno abandonado.
	private void finish(OrderContext context) {
		Order order = context.getOrder();
		if (order == null || context.isExistingOrder()) {
			return;
		}
		order.setLeaseUntil(null);
		order.setUpdatedAt(Instant.now().truncatedTo(ChronoUnit.MILLIS));
		mongoTemplate.save(order);
	}

	// saga.step-delay-ms vale 0 salvo en las pruebas: alarga la compra para poder detener un servicio a mitad, o para
	// ver que el recuperador no toma una compra lenta (CA-09, CA-12, CA-20, CA-22).
	private void delayForTests() {
		if (settings.getStepDelayMs() <= 0) {
			return;
		}
		try {
			Thread.sleep(settings.getStepDelayMs());
		}
		catch (InterruptedException e) {
			Thread.currentThread().interrupt();
		}
	}

	private void cancel(OrderContext context, StepFailedException failure) {
		Order order = context.getOrder();
		if (context.isPivotPassed()) {
			// No debería pasar: los pasos posteriores al pivote no lanzan StepFailedException. Si pasa, compensar
			// devolvería stock vendido, así que se deja el pedido como está y se avisa.
			log.error("Pedido {}: falla después del pivote ({}). No se compensa.", order.getId(), failure.getMessage());
			return;
		}
		log.info("Pedido {}: {}. Se compensan los pasos completados.", order.getId(), failure.getMessage());
		context.setFailureReason(failure.getReason());
		List<OrderHandler> completed = context.getCompletedSteps();
		for (int i = completed.size() - 1; i >= 0; i--) {
			OrderHandler step = completed.get(i);
			try {
				step.compensate(context);
			}
			catch (RuntimeException e) {
				// Se sigue con los demás: que falle liberar la reserva no puede impedir cancelar el pedido. Lo que no
				// se pudo compensar queda en el log; las acciones pendientes que lo reintentan llegan en INC-5.
				log.warn("Pedido {}: no se pudo compensar {}. Causa: {}", order.getId(), step.name(), e.getMessage());
			}
		}
		// El pedido cancelado lo guarda finish(), junto con el lease borrado.
	}

}
