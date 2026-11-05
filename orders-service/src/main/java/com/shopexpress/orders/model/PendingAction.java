package com.shopexpress.orders.model;

import java.time.Instant;

// Algo que quedó por hacer después de decidir el resultado del pedido, porque el otro lado no respondió. Lo procesa
// SagaRecoveryJob en segundo plano, con un intento por pasada (3.3.4, 3.3.6).
public record PendingAction(Type type, int attempts, String lastError, Instant lastAttemptAt) {

	public enum Type {
		RELEASE_RESERVATION,
		REVERSE_PAYMENT,
		CONFIRM_RESERVATION,
		CAPTURE_PAYMENT,
		RECORD_PURCHASE
	}

	public PendingAction failedAgain(String error, Instant at) {
		return new PendingAction(type, attempts + 1, error, at);
	}

}
