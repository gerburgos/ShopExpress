package com.shopexpress.catalog.model;

import java.time.Instant;
import java.util.List;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

// El id es el orderId: así hay una sola reserva por pedido, y repetir la llamada encuentra la que ya existe en lugar
// de crear otra (RN-03).
@Document("reservations")
public record Reservation(
		@Id String orderId,
		ReservationStatus status,
		List<ReservedItem> items,
		Instant createdAt,
		Instant updatedAt) {
}
