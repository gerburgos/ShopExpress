package com.shopexpress.orders.model;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

// Embebido en el pedido: es uno a uno, se lee junto con él, y su historial solo tiene sentido dentro del pedido.
public class Payment {

	private PaymentMethod method;
	private PaymentStatus status;
	private String authorizationCode;
	private List<PaymentAttempt> history = new ArrayList<>();

	public Payment() {
	}

	public Payment(PaymentMethod method) {
		this.method = method;
	}

	// Una entrada por cada operación con la pasarela, y por cada intento (RN-14): así se reconstruye cómo fue el pago y
	// se explica cualquier retención o reverso.
	public void record(String operation, String result, int attempt, String detail) {
		history.add(new PaymentAttempt(operation, result, attempt, Instant.now().truncatedTo(ChronoUnit.MILLIS), detail));
	}

	public PaymentMethod getMethod() {
		return method;
	}

	public void setMethod(PaymentMethod method) {
		this.method = method;
	}

	public PaymentStatus getStatus() {
		return status;
	}

	public void setStatus(PaymentStatus status) {
		this.status = status;
	}

	public String getAuthorizationCode() {
		return authorizationCode;
	}

	public void setAuthorizationCode(String authorizationCode) {
		this.authorizationCode = authorizationCode;
	}

	public List<PaymentAttempt> getHistory() {
		return history;
	}

	public void setHistory(List<PaymentAttempt> history) {
		this.history = history;
	}

}
