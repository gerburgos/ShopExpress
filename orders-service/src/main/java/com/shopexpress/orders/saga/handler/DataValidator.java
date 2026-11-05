package com.shopexpress.orders.saga.handler;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import com.shopexpress.orders.dto.CreateOrderRequest;
import com.shopexpress.orders.exception.ApiException;
import com.shopexpress.orders.saga.OrderContext;
import com.shopexpress.orders.saga.OrderHandler;

import jakarta.validation.Validator;

// Paso 1, ValidadorDeDatos en la consigna. Sin efectos: si falla, no se persiste nada (F-01).
@Component
public class DataValidator extends OrderHandler {

	private final Validator validator;

	public DataValidator(Validator validator) {
		this.validator = validator;
	}

	@Override
	protected void process(OrderContext context) {
		List<String> errors = new ArrayList<>();
		if (context.getIdempotencyKey() == null || context.getIdempotencyKey().isBlank()) {
			errors.add("Idempotency-Key: es obligatorio");
		}
		String simulated = context.getSimulatedPayment();
		if (simulated != null && !List.of("APPROVED", "DECLINED", "NO_RESPONSE").contains(simulated)) {
			errors.add("X-Simulated-Payment: debe ser APPROVED, DECLINED o NO_RESPONSE");
		}
		validator.validate(context.getRequest())
				.forEach(violation -> errors.add(violation.getPropertyPath() + ": " + violation.getMessage()));
		errors.addAll(repeatedProducts(context.getRequest()));
		if (!errors.isEmpty()) {
			// Ordenado, como en los otros servicios: el validador no garantiza el orden, y así el mensaje es siempre
			// el mismo.
			throw new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR",
					"Datos inválidos. " + String.join("; ", errors.stream().sorted().toList()));
		}
	}

	// Un pedido no repite productos entre sus ítems (RN-10). Catálogo lo rechaza igual, pero así el error llega antes
	// de crear el pedido.
	private static List<String> repeatedProducts(CreateOrderRequest request) {
		List<String> errors = new ArrayList<>();
		if (request.items() == null) {
			return errors;
		}
		Set<String> seen = new HashSet<>();
		for (CreateOrderRequest.Item item : request.items()) {
			if (item != null && item.productId() != null && !seen.add(item.productId())) {
				errors.add("items: el producto " + item.productId() + " está repetido");
			}
		}
		return errors;
	}

}
