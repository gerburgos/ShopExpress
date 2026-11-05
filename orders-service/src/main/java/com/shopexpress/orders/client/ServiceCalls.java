package com.shopexpress.orders.client;

import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

import com.shopexpress.orders.exception.ApiError;

// Traduce cada respuesta de otro servicio a una de tres salidas: el cuerpo si salió bien, RejectedException si fue un
// error definitivo, o ServiceUnavailableException si no se sabe qué pasó. Los clientes la usan para no repetir esa
// traducción en cada llamada.
final class ServiceCalls {

	private ServiceCalls() {
	}

	static <T> T call(String service, RestClient.RequestHeadersSpec<?> request, Class<T> bodyType) {
		try {
			return request.exchange((req, response) -> {
				int status = response.getStatusCode().value();
				if (response.getStatusCode().is2xxSuccessful()) {
					return bodyType == Void.class ? null : response.bodyTo(bodyType);
				}
				if (response.getStatusCode().is5xxServerError()) {
					throw new ServiceUnavailableException(service + " respondió " + status, null);
				}
				ApiError error = response.bodyTo(ApiError.class);
				throw new RejectedException(status, error == null ? null : error.code(),
						error == null ? service + " respondió " + status : error.message());
			});
		}
		catch (ResourceAccessException e) {
			// Conexión rechazada o timeout: el pedido pudo haber llegado y aplicarse, aunque no llegó la respuesta.
			throw new ServiceUnavailableException(service + " no respondió: " + e.getMessage(), e);
		}
	}

}
