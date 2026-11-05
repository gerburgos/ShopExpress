package com.shopexpress.orders.saga;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.stream.Collectors;

import com.shopexpress.orders.dto.CreateOrderRequest;

// Huella de una solicitud de compra, para saber si una clave repetida trae la misma compra o otra (D-06).
public final class RequestHash {

	private RequestHash() {
	}

	// Se arma un texto con los datos y no se compara el JSON tal como llegó: el mismo pedido con los campos en otro
	// orden, o con espacios distintos, es la misma compra. Los ítems se ordenan por producto por la misma razón; no se
	// repiten productos (RN-10), así que el orden queda definido. El header de pago simulado entra porque cambia el
	// resultado.
	public static String of(CreateOrderRequest request, String simulatedPayment) {
		String items = request.items().stream()
				.sorted(Comparator.comparing(CreateOrderRequest.Item::productId))
				.map(item -> item.productId() + ":" + item.quantity())
				.collect(Collectors.joining(","));
		String canonical = String.join("|", request.userId(), items, request.payment().method(),
				simulatedPayment == null ? "APPROVED" : simulatedPayment);
		try {
			byte[] digest = MessageDigest.getInstance("SHA-256").digest(canonical.getBytes(StandardCharsets.UTF_8));
			return HexFormat.of().formatHex(digest);
		}
		catch (NoSuchAlgorithmException e) {
			// Toda JVM trae SHA-256: si falta, es un problema del entorno y no de la solicitud.
			throw new IllegalStateException(e);
		}
	}

}
