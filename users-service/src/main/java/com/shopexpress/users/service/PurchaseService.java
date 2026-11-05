package com.shopexpress.users.service;

import java.util.List;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import com.shopexpress.users.dto.RecordPurchaseRequest;
import com.shopexpress.users.exception.ApiException;
import com.shopexpress.users.model.Purchase;
import com.shopexpress.users.repository.UserRepository;

@Service
public class PurchaseService {

	private final MongoTemplate mongoTemplate;
	private final UserRepository userRepository;

	public PurchaseService(MongoTemplate mongoTemplate, UserRepository userRepository) {
		this.mongoTemplate = mongoTemplate;
		this.userRepository = userRepository;
	}

	// INT-04. Pedidos lo reintenta si no le llega la respuesta, y el recuperador también: tiene que poder repetirse.
	public Purchase record(String userId, RecordPurchaseRequest request) {
		requireUser(userId);
		Purchase purchase = new Purchase(request.orderId(), userId, request.date(), request.total(),
				request.items().stream()
						.map(item -> new Purchase.Item(item.productId(), item.name(), item.quantity()))
						.toList());
		try {
			return mongoTemplate.insert(purchase);
		}
		catch (DuplicateKeyException e) {
			// Ya estaba registrada: es una repetición y se devuelve la que hay, sin duplicarla.
			return mongoTemplate.findById(request.orderId(), Purchase.class);
		}
	}

	// RF-07. Se lee de MongoDB y no de la caché: el historial cambia con cada compra, y la caché de usuarios no lo
	// incluye.
	public List<Purchase> list(String userId) {
		requireUser(userId);
		Query query = Query.query(Criteria.where("userId").is(userId)).with(Sort.by(Sort.Direction.DESC, "date"));
		return mongoTemplate.find(query, Purchase.class);
	}

	// existsById y no la consulta del usuario: solo hace falta saber si existe, sin traer el documento ni cargarlo en la
	// caché.
	private void requireUser(String userId) {
		if (!userRepository.existsById(userId)) {
			throw new ApiException(HttpStatus.NOT_FOUND, "USER_NOT_FOUND", "No existe el usuario " + userId);
		}
	}

}
