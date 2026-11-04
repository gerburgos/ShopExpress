package com.shopexpress.catalog.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

// Se recibe la cantidad a sumar y no el stock nuevo: fijar el valor pisaría una reserva hecha entre que el
// administrador miró el stock y lo cargó (RN-12).
public record RestockRequest(
		@NotNull(message = "es obligatorio") @Positive(message = "debe ser mayor que 0") Integer quantity) {
}
