package com.shopexpress.orders.payment;

// El resultado que se le pide a la pasarela simulada con el header X-Simulated-Payment (D-07). Sin header, APPROVED.
public enum SimulatedOutcome {
	APPROVED,
	DECLINED,
	// La pasarela autoriza, pero la respuesta nunca llega: el dinero queda retenido sin que Pedidos lo sepa. Es el caso
	// que obliga a reversar en lugar de solo cancelar.
	NO_RESPONSE
}
