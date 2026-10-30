package com.shopexpress.users.dto;

import java.util.List;

import com.shopexpress.users.model.User;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record CreateUserRequest(
		// Llega como texto y se valida contra la lista de DocumentType, en lugar de recibir el enum: si Jackson fallara al
		// convertir un valor fuera de la lista, la respuesta nombraría solo ese campo y no los demás errores. El patrón
		// repite la lista del enum: si se agrega un tipo, va en los dos.
		@NotNull(message = "es obligatorio")
		@Pattern(regexp = "CI|DNI|PASSPORT", message = "debe ser CI, DNI o PASSPORT")
		String documentType,
		@NotBlank(message = "es obligatorio") String documentNumber,
		@NotBlank(message = "es obligatorio") String firstName,
		@NotBlank(message = "es obligatorio") String lastName,
		@NotBlank(message = "es obligatorio") @Email(message = "no es un email válido") String email,
		@Size(max = User.MAX_ADDRESSES, message = "admite como máximo {max} direcciones")
		List<@NotNull(message = "no puede ser null") @Valid AddressDto> addresses) {
}
