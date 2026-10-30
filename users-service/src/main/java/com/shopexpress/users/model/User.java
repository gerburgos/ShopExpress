package com.shopexpress.users.model;

import java.util.List;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

// Los índices tienen nombre propio porque UserService lo busca en el error de clave duplicada para saber qué dato
// está repetido.
@Document("users")
@CompoundIndex(name = User.DOCUMENT_INDEX, def = "{'documentType': 1, 'documentNumber': 1}", unique = true)
public record User(
		@Id String id,
		String documentType,
		String documentNumber,
		String firstName,
		String lastName,
		@Indexed(name = User.EMAIL_INDEX, unique = true) String email,
		List<Address> addresses) {

	public static final String EMAIL_INDEX = "email_unique";
	public static final String DOCUMENT_INDEX = "document_unique";

	// RN-11: el límite mantiene chico el array embebido.
	public static final int MAX_ADDRESSES = 10;

}
