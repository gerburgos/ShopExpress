package com.shopexpress.orders.client;

import java.util.List;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import com.shopexpress.orders.dto.CreateOrderRequest;

// Las tres llamadas usan el orderId como clave, así que repetirlas no duplica efectos en Catálogo (3.1.2).
@Component
public class CatalogClient {

	private final RestClient restClient;

	public CatalogClient(@Qualifier("catalogRestClient") RestClient restClient) {
		this.restClient = restClient;
	}

	// INT-01
	public CatalogReservation reserve(String orderId, List<CreateOrderRequest.Item> items) {
		return ServiceCalls.call("Catálogo", restClient.post().uri("/internal/reservations")
				.contentType(MediaType.APPLICATION_JSON).body(new ReserveBody(orderId, items)), CatalogReservation.class);
	}

	// INT-03
	public CatalogReservation confirm(String orderId) {
		return ServiceCalls.call("Catálogo", restClient.post().uri("/internal/reservations/{orderId}/confirm", orderId),
				CatalogReservation.class);
	}

	// INT-02
	public CatalogReservation release(String orderId) {
		return ServiceCalls.call("Catálogo", restClient.post().uri("/internal/reservations/{orderId}/release", orderId),
				CatalogReservation.class);
	}

	private record ReserveBody(String orderId, List<CreateOrderRequest.Item> items) {
	}

}
