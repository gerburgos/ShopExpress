package com.shopexpress.catalog.dto;

import java.math.BigDecimal;
import java.util.List;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

public record CreateProductRequest(
		// Letras, números y guiones, sin espacios: el SKU se escribe y se dicta, y un espacio o un símbolo es fácil de
		// perder al copiarlo. Las minúsculas se aceptan y se guardan en mayúsculas.
		@NotNull(message = "es obligatorio")
		@Pattern(regexp = "[A-Za-z0-9]+(-[A-Za-z0-9]+)*", message = "solo admite letras, números y guiones entre ellos")
		@Size(max = 32, message = "admite hasta {max} caracteres")
		String sku,
		@NotBlank(message = "es obligatorio") String name,
		String description,
		List<@NotBlank(message = "no puede estar vacía") String> categories,
		@NotNull(message = "es obligatorio")
		@DecimalMin(value = "0", inclusive = false, message = "debe ser mayor que 0")
		// Se rechaza en lugar de redondear: redondear cambiaría en silencio el precio que fijó el administrador.
		@Digits(integer = 10, fraction = 2, message = "admite hasta {integer} dígitos enteros y {fraction} decimales")
		BigDecimal price,
		@NotNull(message = "es obligatorio") @PositiveOrZero(message = "no puede ser negativo") Integer stock) {
}
