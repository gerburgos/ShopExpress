package com.shopexpress.users.dto;

import com.shopexpress.users.model.Address;

import jakarta.validation.constraints.NotBlank;

public record AddressDto(
		@NotBlank(message = "es obligatorio") String street,
		@NotBlank(message = "es obligatorio") String city,
		@NotBlank(message = "es obligatorio") String postalCode) {

	public static AddressDto from(Address address) {
		return new AddressDto(address.street(), address.city(), address.postalCode());
	}

	public Address toAddress() {
		return new Address(street, city, postalCode);
	}

}
