package com.shopexpress.orders.client;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import com.shopexpress.orders.model.Order;

@Component
public class UsersClient {

	private final RestClient restClient;

	public UsersClient(@Qualifier("usersRestClient") RestClient restClient) {
		this.restClient = restClient;
	}

	// RF-02. Solo importa el código: 200 existe, 404 no. El cuerpo no se lee, y por eso puede venir de la caché sin
	// problema.
	public void requireUser(String userId) {
		ServiceCalls.call("Usuarios", restClient.get().uri("/api/users/{userId}", userId), Void.class);
	}

	// INT-04. Usuarios usa el orderId como id de la compra, así que repetirla no la duplica.
	public void recordPurchase(Order order) {
		List<PurchaseItem> items = order.getItems().stream()
				.map(item -> new PurchaseItem(item.productId(), item.name(), item.quantity()))
				.toList();
		// La fecha es la del pedido, que no cambia: si la llamada se repite, manda siempre la misma.
		PurchaseBody body = new PurchaseBody(order.getId(), order.getCreatedAt(), order.getTotal(), items);
		ServiceCalls.call("Usuarios", restClient.post().uri("/internal/users/{userId}/purchases", order.getUserId())
				.contentType(MediaType.APPLICATION_JSON).body(body), Void.class);
	}

	private record PurchaseBody(String orderId, Instant date, BigDecimal total, List<PurchaseItem> items) {
	}

	private record PurchaseItem(String productId, String name, int quantity) {
	}

}
