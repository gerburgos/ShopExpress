package com.shopexpress.orders.payment;

import java.math.BigDecimal;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

// Lo que registra la pasarela simulada, separado del pedido: representa el sistema de otra empresa, que Pedidos no
// controla. El id es el orderId, así repetir una operación encuentra la misma autorización (D-07).
@Document("authorizations")
public record Authorization(@Id String orderId, AuthorizationStatus status, String authorizationCode,
		BigDecimal amount) {

	public enum AuthorizationStatus {
		AUTHORIZED,
		DECLINED,
		CAPTURED,
		REVERSED
	}

}
