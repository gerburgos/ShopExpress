package com.shopexpress.orders.saga;

import com.shopexpress.orders.model.CancellationReason;

// Un paso falló después de que el pedido existe: el orquestador compensa lo hecho y cancela con este motivo. Las
// fallas de los pasos anteriores (datos inválidos, usuario inexistente) no llegan acá: no hay pedido ni nada que
// compensar, y responden con ApiException.
public class StepFailedException extends RuntimeException {

	private final CancellationReason reason;

	public StepFailedException(CancellationReason reason, String message) {
		super(message);
		this.reason = reason;
	}

	public CancellationReason getReason() {
		return reason;
	}

}
