package com.shopexpress.orders.saga;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.shopexpress.orders.client.ServiceUnavailableException;
import com.shopexpress.orders.payment.GatewayTimeoutException;

// Reintentos dentro de la solicitud (3.3.6, D-17). Un bucle propio, como el de la reserva en Catálogo, para que la
// espera que se duplica quede a la vista.
@Component
public class Retrier {

	private static final Logger log = LoggerFactory.getLogger(Retrier.class);

	private final SagaSettings settings;

	public Retrier(SagaSettings settings) {
		this.settings = settings;
	}

	// Recibe el número de intento para que quien llama pueda registrarlo, como el historial del pago (RN-14).
	@FunctionalInterface
	public interface Attempt<T> {
		T run(int attempt);
	}

	// Solo se reintentan las fallas transitorias: no se sabe qué pasó, y repetir con la misma clave es seguro porque
	// el otro lado es idempotente. Un rechazo (RejectedException, una autorización DECLINED) no se reintenta: daría lo
	// mismo y solo sumaría carga (RN-13). Si se agotan los intentos, la última falla sube a quien llamó, que decide:
	// antes del pivote se compensa; después, queda como acción pendiente.
	public <T> T call(String description, Attempt<T> attempt) {
		long backoffMs = settings.getInitialBackoffMs();
		for (int n = 1; ; n++) {
			try {
				return attempt.run(n);
			}
			catch (ServiceUnavailableException | GatewayTimeoutException e) {
				if (n == settings.getMaxAttempts()) {
					log.warn("{}: falló en los {} intentos. Causa: {}", description, n, e.getMessage());
					throw e;
				}
				log.info("{}: falló el intento {} de {}; se reintenta en {} ms. Causa: {}", description, n,
						settings.getMaxAttempts(), backoffMs, e.getMessage());
				sleep(backoffMs);
				backoffMs *= 2;
			}
		}
	}

	private static void sleep(long ms) {
		try {
			Thread.sleep(ms);
		}
		catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			throw new IllegalStateException("Se interrumpió la espera entre reintentos", e);
		}
	}

}
