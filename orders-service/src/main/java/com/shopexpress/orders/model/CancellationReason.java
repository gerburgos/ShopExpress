package com.shopexpress.orders.model;

import org.springframework.http.HttpStatus;

// Cada motivo lleva el código HTTP con el que responde la compra (RF-05). Así una repetición con la misma clave
// responde lo mismo que la original, sin guardar el código aparte.
public enum CancellationReason {
	OUT_OF_STOCK(HttpStatus.CONFLICT),
	PRODUCT_NOT_FOUND(HttpStatus.UNPROCESSABLE_CONTENT),
	CATALOG_UNAVAILABLE(HttpStatus.SERVICE_UNAVAILABLE),
	PAYMENT_DECLINED(HttpStatus.UNPROCESSABLE_CONTENT),
	PAYMENT_TIMEOUT(HttpStatus.GATEWAY_TIMEOUT),
	RESERVATION_LOST(HttpStatus.CONFLICT),
	INTERRUPTED(HttpStatus.CONFLICT);

	private final HttpStatus httpStatus;

	CancellationReason(HttpStatus httpStatus) {
		this.httpStatus = httpStatus;
	}

	public HttpStatus httpStatus() {
		return httpStatus;
	}
}
