package com.shopexpress.users.dto;

import java.util.List;

import com.shopexpress.users.model.User;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CreateUserRequest(
		@NotBlank(message = "es obligatorio") String documentType,
		@NotBlank(message = "es obligatorio") String documentNumber,
		@NotBlank(message = "es obligatorio") String firstName,
		@NotBlank(message = "es obligatorio") String lastName,
		@NotBlank(message = "es obligatorio") @Email(message = "no es un email válido") String email,
		@Size(max = User.MAX_ADDRESSES, message = "admite como máximo {max} direcciones")
		List<@NotNull(message = "no puede ser null") @Valid AddressDto> addresses) {
}
