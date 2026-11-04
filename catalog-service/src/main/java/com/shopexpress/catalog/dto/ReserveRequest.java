package com.shopexpress.catalog.dto;

import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

// Lo manda Pedidos (INT-01). Pedidos ya valida lo mismo, pero Catálogo es el dueño del stock y no confía en que
// todos los que lo llamen lo hagan.
public record ReserveRequest(
		@NotBlank(message = "es obligatorio") String orderId,
		@NotEmpty(message = "debe tener al menos un ítem") List<@NotNull @Valid Item> items) {

	public record Item(
			@NotBlank(message = "es obligatorio") String productId,
			@NotNull(message = "es obligatorio") @Positive(message = "debe ser mayor que 0") Integer quantity) {
	}

}
