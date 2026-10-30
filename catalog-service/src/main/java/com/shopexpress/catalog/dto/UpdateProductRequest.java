package com.shopexpress.catalog.dto;

import java.math.BigDecimal;
import java.util.List;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Null;
import jakarta.validation.constraints.Pattern;

// Un campo ausente o null no se modifica. Las reglas son las del alta, salvo que nada es obligatorio.
public record UpdateProductRequest(
		// @NotBlank rechazaría también el null, que acá significa "no cambiar". El patrón solo pide un carácter
		// que no sea espacio, y deja pasar el null.
		@Pattern(regexp = "(?s).*\\S.*", message = "no puede estar vacío") String name,
		String description,
		List<@NotBlank(message = "no puede estar vacía") String> categories,
		@DecimalMin(value = "0", inclusive = false, message = "debe ser mayor que 0")
		@Digits(integer = 10, fraction = 2, message = "admite hasta {integer} dígitos enteros y {fraction} decimales")
		BigDecimal price,
		// stock e id se declaran solo para rechazarlos. Si no estuvieran, Jackson los ignoraría, y el PATCH
		// respondería 200 sin hacer lo que se pidió. El stock cambia solo sumando o restando (RN-12).
		@Null(message = "no se modifica con PATCH: cambia solo con reposiciones y ventas") Object stock,
		@Null(message = "no se puede modificar") Object id) {
}
