package com.shopexpress.orders.saga;

import java.time.Duration;
import java.time.Instant;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

// Los parámetros de reintento de la SAGA (3.8), juntos porque el lease se calcula con todos ellos.
@Component
public class SagaSettings {

	private final int maxAttempts;
	private final long initialBackoffMs;
	private final long connectTimeoutMs;
	private final long readTimeoutMs;
	private final long stepDelayMs;

	public SagaSettings(@Value("${saga.retry.max-attempts}") int maxAttempts,
			@Value("${saga.retry.initial-backoff-ms}") long initialBackoffMs,
			@Value("${http.client.connect-timeout-ms}") long connectTimeoutMs,
			@Value("${http.client.read-timeout-ms}") long readTimeoutMs,
			@Value("${saga.step-delay-ms}") long stepDelayMs) {
		this.maxAttempts = maxAttempts;
		this.initialBackoffMs = initialBackoffMs;
		this.connectTimeoutMs = connectTimeoutMs;
		this.readTimeoutMs = readTimeoutMs;
		this.stepDelayMs = stepDelayMs;
	}

	// Lo más que puede tardar un paso: todos los intentos agotando sus dos timeouts, más las esperas entre intentos,
	// más la demora de prueba (3.3.6). Con los valores iniciales da 12,6 s. Un lease más corto haría que el
	// recuperador tomara una compra lenta que sigue en curso; uno fijo y largo demoraría de más la recuperación.
	public Duration worstCaseStep() {
		long attemptsMs = maxAttempts * (connectTimeoutMs + readTimeoutMs);
		long backoffMs = initialBackoffMs * ((1L << (maxAttempts - 1)) - 1);
		return Duration.ofMillis(attemptsMs + backoffMs + stepDelayMs);
	}

	public Instant leaseFrom(Instant now) {
		return now.plus(worstCaseStep());
	}

	public int getMaxAttempts() {
		return maxAttempts;
	}

	public long getInitialBackoffMs() {
		return initialBackoffMs;
	}

	public long getStepDelayMs() {
		return stepDelayMs;
	}

}
