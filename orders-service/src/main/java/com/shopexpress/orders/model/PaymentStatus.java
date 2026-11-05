package com.shopexpress.orders.model;

// Cada estado se guarda antes de llamar a la pasarela, para que un reinicio sepa qué operación estaba en curso
// (3.3.4).
public enum PaymentStatus {
	PENDING,
	AUTHORIZED,
	DECLINED,
	CAPTURED,
	REVERSAL_PENDING,
	REVERSED
}
