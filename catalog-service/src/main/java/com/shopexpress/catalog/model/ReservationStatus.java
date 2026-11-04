package com.shopexpress.catalog.model;

// Una reserva solo pasa de RESERVED a CONFIRMED o a RELEASED, y no vuelve atrás (RN-09).
public enum ReservationStatus {
	RESERVED,
	CONFIRMED,
	RELEASED
}
