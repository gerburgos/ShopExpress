package com.shopexpress.orders.client;

// El otro servicio respondió con un error definitivo (4xx): la operación no se hizo y repetirla da lo mismo. Trae el
// código de error de la respuesta, que es el que se compara.
public class RejectedException extends RuntimeException {

	private final int status;
	private final String code;

	public RejectedException(int status, String code, String message) {
		super(message);
		this.status = status;
		this.code = code;
	}

	public int getStatus() {
		return status;
	}

	public String getCode() {
		return code;
	}

}
