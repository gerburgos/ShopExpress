package com.shopexpress.orders.saga;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BiConsumer;

import com.shopexpress.orders.dto.CreateOrderRequest;
import com.shopexpress.orders.model.CancellationReason;
import com.shopexpress.orders.model.Order;

// Lo que recorre la cadena: la solicitud, el pedido que se va armando y los pasos que ya terminaron. Uno por compra,
// nunca compartido entre solicitudes.
public class OrderContext {

	private final String idempotencyKey;
	private final String simulatedPayment;
	private final CreateOrderRequest request;
	// El orquestador se registra acá. Así los manejadores le avisan al terminar sin conocerlo, y no hay una
	// dependencia circular entre el orquestador y la cadena.
	private final BiConsumer<OrderContext, OrderHandler> onStepCompleted;
	private final List<OrderHandler> completedSteps = new ArrayList<>();
	private Order order;
	private boolean stopped;
	private boolean existingOrder;
	private boolean pivotPassed;
	private CancellationReason failureReason;

	public OrderContext(String idempotencyKey, String simulatedPayment, CreateOrderRequest request,
			BiConsumer<OrderContext, OrderHandler> onStepCompleted) {
		this.idempotencyKey = idempotencyKey;
		this.simulatedPayment = simulatedPayment;
		this.request = request;
		this.onStepCompleted = onStepCompleted;
	}

	void stepCompleted(OrderHandler step) {
		completedSteps.add(step);
		onStepCompleted.accept(this, step);
	}

	public List<OrderHandler> getCompletedSteps() {
		return completedSteps;
	}

	// Corta la cadena sin error: por ejemplo, cuando la clave ya tenía un pedido y se devuelve ese.
	public void stop() {
		stopped = true;
	}

	public boolean isStopped() {
		return stopped;
	}

	// La clave ya tenía un pedido y se devuelve ese. Es de otra solicitud: esta no lo modifica ni lo guarda.
	public void useExistingOrder(Order existing) {
		this.order = existing;
		this.existingOrder = true;
		this.stopped = true;
	}

	public boolean isExistingOrder() {
		return existingOrder;
	}

	// La reserva se confirmó: desde acá no se compensa nada, solo se avanza (RN-09).
	public void markPivotPassed() {
		pivotPassed = true;
	}

	public boolean isPivotPassed() {
		return pivotPassed;
	}

	public String getIdempotencyKey() {
		return idempotencyKey;
	}

	public String getSimulatedPayment() {
		return simulatedPayment;
	}

	public CreateOrderRequest getRequest() {
		return request;
	}

	public Order getOrder() {
		return order;
	}

	public void setOrder(Order order) {
		this.order = order;
	}

	public CancellationReason getFailureReason() {
		return failureReason;
	}

	void setFailureReason(CancellationReason failureReason) {
		this.failureReason = failureReason;
	}

}
