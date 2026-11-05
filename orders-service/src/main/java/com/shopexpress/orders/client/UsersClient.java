package com.shopexpress.orders.client;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

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

}
