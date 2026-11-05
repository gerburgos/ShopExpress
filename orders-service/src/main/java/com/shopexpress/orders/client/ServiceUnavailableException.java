package com.shopexpress.orders.client;

// El otro servicio no respondió, respondió 5xx o tardó más que el timeout. No se sabe si la operación se hizo: quien
// la llamó decide cómo seguir (para la reserva, liberarla por las dudas, F-05).
public class ServiceUnavailableException extends RuntimeException {

	public ServiceUnavailableException(String message, Throwable cause) {
		super(message, cause);
	}

}
