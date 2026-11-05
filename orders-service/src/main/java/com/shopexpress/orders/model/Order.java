package com.shopexpress.orders.model;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

// Una clase y no un record, a diferencia de los otros modelos: la cadena lo va completando paso a paso (los ítems al
// reservar, el total después, el pago al final), y el orquestador lo guarda después de cada paso.
@Document("orders")
public class Order {

	public static final String IDEMPOTENCY_KEY_INDEX = "idempotency_key_unique";

	@Id
	private String id;
	// El índice único es el que garantiza que la misma clave no cree dos pedidos, también con dos solicitudes
	// simultáneas. Buscar primero y después insertar tendría una carrera (D-06).
	@Indexed(name = IDEMPOTENCY_KEY_INDEX, unique = true)
	private String idempotencyKey;
	private String userId;
	private OrderStatus status;
	private CancellationReason reason;
	private List<OrderItem> items = new ArrayList<>();
	private BigDecimal total;
	private String currency;
	private Payment payment;
	// Los pasos de la cadena que ya terminaron, en orden. Si el servicio se cae a mitad, dice qué hay que compensar
	// (D-10).
	private List<String> completedSteps = new ArrayList<>();
	private Instant createdAt;
	private Instant updatedAt;

	public String getId() {
		return id;
	}

	public void setId(String id) {
		this.id = id;
	}

	public String getIdempotencyKey() {
		return idempotencyKey;
	}

	public void setIdempotencyKey(String idempotencyKey) {
		this.idempotencyKey = idempotencyKey;
	}

	public String getUserId() {
		return userId;
	}

	public void setUserId(String userId) {
		this.userId = userId;
	}

	public OrderStatus getStatus() {
		return status;
	}

	public void setStatus(OrderStatus status) {
		this.status = status;
	}

	public CancellationReason getReason() {
		return reason;
	}

	public void setReason(CancellationReason reason) {
		this.reason = reason;
	}

	public List<OrderItem> getItems() {
		return items;
	}

	public void setItems(List<OrderItem> items) {
		this.items = items;
	}

	public BigDecimal getTotal() {
		return total;
	}

	public void setTotal(BigDecimal total) {
		this.total = total;
	}

	public String getCurrency() {
		return currency;
	}

	public void setCurrency(String currency) {
		this.currency = currency;
	}

	public Payment getPayment() {
		return payment;
	}

	public void setPayment(Payment payment) {
		this.payment = payment;
	}

	public List<String> getCompletedSteps() {
		return completedSteps;
	}

	public void setCompletedSteps(List<String> completedSteps) {
		this.completedSteps = completedSteps;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}

	public void setCreatedAt(Instant createdAt) {
		this.createdAt = createdAt;
	}

	public Instant getUpdatedAt() {
		return updatedAt;
	}

	public void setUpdatedAt(Instant updatedAt) {
		this.updatedAt = updatedAt;
	}

}
