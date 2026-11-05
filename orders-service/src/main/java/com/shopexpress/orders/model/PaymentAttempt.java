package com.shopexpress.orders.model;

import java.time.Instant;

// Una entrada del historial del pago por cada operación con la pasarela y cada intento (RN-14). detail solo se
// completa si hubo un error.
public record PaymentAttempt(String operation, String result, int attempt, Instant at, String detail) {
}
