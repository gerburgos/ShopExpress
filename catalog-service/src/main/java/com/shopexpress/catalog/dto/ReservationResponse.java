package com.shopexpress.catalog.dto;

import java.util.List;

import com.shopexpress.catalog.model.Reservation;
import com.shopexpress.catalog.model.ReservationStatus;
import com.shopexpress.catalog.model.ReservedItem;

public record ReservationResponse(String orderId, ReservationStatus status, List<ReservedItem> items) {

	public static ReservationResponse from(Reservation reservation) {
		return new ReservationResponse(reservation.orderId(), reservation.status(), reservation.items());
	}

}
