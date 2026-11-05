package com.shopexpress.orders.dto;

import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;

// El cliente no manda precios ni total: los fija el servidor con la reserva (RN-05). Las reglas las aplica
// DataValidator, el primer paso de la cadena, y no el controlador: así la validación es un manejador más, como pide la
// consigna (ValidadorDeDatos).
public record CreateOrderRequest(
		@NotBlank(message = "es obligatorio") String userId,
		@NotEmpty(message = "debe tener al menos un ítem") List<@NotNull @Valid Item> items,
		@NotNull(message = "es obligatorio") @Valid PaymentRequest payment) {

	public record Item(
			@NotBlank(message = "es obligatorio") String productId,
			@NotNull(message = "es obligatorio") @Positive(message = "debe ser mayor que 0") Integer quantity) {
	}

	// Un String con patrón y no el enum, como el tipo de documento en Usuarios: un valor fuera de la lista da 400 con
	// el mensaje junto a los demás errores, en lugar de un error de lectura del JSON.
	public record PaymentRequest(
			@NotNull(message = "es obligatorio")
			@Pattern(regexp = "CREDIT_CARD|DEBIT_CARD", message = "debe ser CREDIT_CARD o DEBIT_CARD")
			String method) {
	}

}
