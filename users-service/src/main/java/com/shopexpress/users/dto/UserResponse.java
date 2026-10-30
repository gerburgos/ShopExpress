package com.shopexpress.users.dto;

import java.util.List;

import com.shopexpress.users.model.DocumentType;
import com.shopexpress.users.model.User;

public record UserResponse(
		String id,
		DocumentType documentType,
		String documentNumber,
		String firstName,
		String lastName,
		String email,
		List<AddressDto> addresses) {

	public static UserResponse from(User user) {
		return new UserResponse(user.id(), user.documentType(), user.documentNumber(), user.firstName(),
				user.lastName(), user.email(), user.addresses().stream().map(AddressDto::from).toList());
	}

}
