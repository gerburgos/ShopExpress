package com.shopexpress.users.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

// Lo manda Pedidos después de confirmar una compra (INT-04).
public record RecordPurchaseRequest(
		@NotBlank(message = "es obligatorio") String orderId,
		@NotNull(message = "es obligatorio") Instant date,
		@NotNull(message = "es obligatorio") BigDecimal total,
		@NotEmpty(message = "debe tener al menos un ítem") List<@NotNull @Valid Item> items) {

	public record Item(
			@NotBlank(message = "es obligatorio") String productId,
			@NotBlank(message = "es obligatorio") String name,
			@NotNull(message = "es obligatorio") @Positive(message = "debe ser mayor que 0") Integer quantity) {
	}

}
