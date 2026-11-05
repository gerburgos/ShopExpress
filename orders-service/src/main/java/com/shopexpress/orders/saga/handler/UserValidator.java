package com.shopexpress.orders.saga.handler;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import com.shopexpress.orders.client.RejectedException;
import com.shopexpress.orders.client.ServiceUnavailableException;
import com.shopexpress.orders.client.UsersClient;
import com.shopexpress.orders.exception.ApiException;
import com.shopexpress.orders.saga.OrderContext;
import com.shopexpress.orders.saga.OrderHandler;
import com.shopexpress.orders.saga.Retrier;

// Paso 2. Sin efectos: si falla, no se persiste nada (F-02).
@Component
public class UserValidator extends OrderHandler {

	private final UsersClient usersClient;
	private final Retrier retrier;

	public UserValidator(UsersClient usersClient, Retrier retrier) {
		this.usersClient = usersClient;
		this.retrier = retrier;
	}

	@Override
	protected void process(OrderContext context) {
		String userId = context.getRequest().userId();
		try {
			retrier.call("Validar el usuario " + userId, attempt -> {
				usersClient.requireUser(userId);
				return null;
			});
		}
		catch (RejectedException e) {
			if (e.getStatus() == HttpStatus.NOT_FOUND.value()) {
				// 422 y no 404: la ruta /api/orders existe; lo que no sirve es un dato del cuerpo (RF-05).
				throw new ApiException(HttpStatus.UNPROCESSABLE_CONTENT, "USER_NOT_FOUND", "No existe el usuario " + userId);
			}
			throw e;
		}
		catch (ServiceUnavailableException e) {
			// Se agotaron los intentos. Todavía no hay pedido ni nada reservado, así que no hay que compensar: se
			// responde que se reintente.
			throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "USERS_UNAVAILABLE",
					"Usuarios no respondió; reintentar");
		}
	}

}
