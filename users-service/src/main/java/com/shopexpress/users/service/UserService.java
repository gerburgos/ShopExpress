package com.shopexpress.users.service;

import java.util.List;
import java.util.Locale;
import java.util.UUID;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import com.shopexpress.users.dto.AddressDto;
import com.shopexpress.users.dto.CreateUserRequest;
import com.shopexpress.users.exception.ApiException;
import com.shopexpress.users.model.User;
import com.shopexpress.users.repository.UserRepository;

@Service
public class UserService {

	private final UserRepository repository;
	private final MongoTemplate mongoTemplate;

	public UserService(UserRepository repository, MongoTemplate mongoTemplate) {
		this.repository = repository;
		this.mongoTemplate = mongoTemplate;
	}

	public User create(CreateUserRequest request) {
		List<AddressDto> addresses = request.addresses() == null ? List.of() : request.addresses();
		// Se normalizan los campos únicos para que "Juan@Example.com" y "juan@example.com", o "ci" y "CI",
		// no pasen como usuarios distintos.
		User user = new User(
				"usr_" + UUID.randomUUID(),
				request.documentType().trim().toUpperCase(Locale.ROOT),
				request.documentNumber().trim(),
				request.firstName(),
				request.lastName(),
				request.email().trim().toLowerCase(Locale.ROOT),
				addresses.stream().map(AddressDto::toAddress).toList());
		try {
			// insert y no save: save reemplaza el documento si el id ya existe, en lugar de fallar.
			return repository.insert(user);
		}
		catch (DuplicateKeyException e) {
			// La unicidad la garantizan los índices, también con dos altas simultáneas. Consultar antes de insertar
			// tendría una carrera entre la consulta y el insert.
			String message = String.valueOf(e.getMessage());
			if (message.contains(User.EMAIL_INDEX)) {
				throw new ApiException(HttpStatus.CONFLICT, "EMAIL_ALREADY_EXISTS",
						"Ya existe un usuario con el email " + user.email());
			}
			if (message.contains(User.DOCUMENT_INDEX)) {
				throw new ApiException(HttpStatus.CONFLICT, "DOCUMENT_ALREADY_EXISTS",
						"Ya existe un usuario con el documento " + user.documentType() + " " + user.documentNumber());
			}
			throw e;
		}
	}

	public User get(String userId) {
		return repository.findById(userId).orElseThrow(() -> notFound(userId));
	}

	public User addAddress(String userId, AddressDto address) {
		// La condición y el push van en una sola operación atómica. Con "leer, contar y guardar", dos altas
		// simultáneas sobre un usuario con 9 direcciones lo dejarían con 11 (RN-11).
		Query query = Query.query(Criteria.where("id").is(userId)
				.and("addresses." + (User.MAX_ADDRESSES - 1)).exists(false));
		Update update = new Update().push("addresses", address.toAddress());
		User updated = mongoTemplate.findAndModify(query, update, FindAndModifyOptions.options().returnNew(true),
				User.class);
		if (updated != null) {
			return updated;
		}
		// Ningún documento cumplió la condición: o el usuario no existe, o ya tiene el máximo.
		if (!repository.existsById(userId)) {
			throw notFound(userId);
		}
		throw new ApiException(HttpStatus.UNPROCESSABLE_CONTENT, "ADDRESS_LIMIT_REACHED",
				"El usuario " + userId + " ya tiene " + User.MAX_ADDRESSES + " direcciones, el máximo permitido");
	}

	private static ApiException notFound(String userId) {
		return new ApiException(HttpStatus.NOT_FOUND, "USER_NOT_FOUND", "No existe el usuario " + userId);
	}

}
