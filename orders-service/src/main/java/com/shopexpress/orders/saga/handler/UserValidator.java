package com.shopexpress.orders.saga.handler;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import com.shopexpress.orders.client.RejectedException;
import com.shopexpress.orders.client.ServiceUnavailableException;
import com.shopexpress.orders.client.UsersClient;
import com.shopexpress.orders.exception.ApiException;
import com.shopexpress.orders.saga.OrderContext;
import com.shopexpress.orders.saga.OrderHandler;

// Paso 2. Sin efectos: si falla, no se persiste nada (F-02).
@Component
public class UserValidator extends OrderHandler {

	private final UsersClient usersClient;

	public UserValidator(UsersClient usersClient) {
		this.usersClient = usersClient;
	}

	@Override
	protected void process(OrderContext context) {
		String userId = context.getRequest().userId();
		try {
			usersClient.requireUser(userId);
		}
		catch (RejectedException e) {
			if (e.getStatus() == HttpStatus.NOT_FOUND.value()) {
				// 422 y no 404: la ruta /api/orders existe; lo que no sirve es un dato del cuerpo (RF-05).
				throw new ApiException(HttpStatus.UNPROCESSABLE_CONTENT, "USER_NOT_FOUND", "No existe el usuario " + userId);
			}
			throw e;
		}
		catch (ServiceUnavailableException e) {
			// Todavía no hay pedido ni nada reservado, así que no hay que compensar: se responde que se reintente.
			throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "USERS_UNAVAILABLE",
					"Usuarios no respondió; reintentar");
		}
	}

}
