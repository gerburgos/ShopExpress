package com.shopexpress.orders.payment;

// La pasarela no respondió a tiempo. Como con cualquier timeout, no se sabe si la operación se hizo (RN-07).
public class GatewayTimeoutException extends RuntimeException {

	public GatewayTimeoutException(String message) {
		super(message);
	}

}
