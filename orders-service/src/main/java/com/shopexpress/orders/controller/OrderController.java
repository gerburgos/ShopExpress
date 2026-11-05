package com.shopexpress.orders.controller;

import java.net.URI;

import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import com.shopexpress.orders.dto.CreateOrderRequest;
import com.shopexpress.orders.dto.OrderResponse;
import com.shopexpress.orders.exception.ApiException;
import com.shopexpress.orders.model.Order;
import com.shopexpress.orders.saga.OrderOrchestrator;

@RestController
@RequestMapping("/api/orders")
public class OrderController {

	private final OrderOrchestrator orchestrator;
	private final MongoTemplate mongoTemplate;

	public OrderController(OrderOrchestrator orchestrator, MongoTemplate mongoTemplate) {
		this.orchestrator = orchestrator;
		this.mongoTemplate = mongoTemplate;
	}

	// RF-05. Sin @Valid: la validación es el primer paso de la cadena (DataValidator). Los headers son opcionales acá
	// para que la falta de la clave llegue a ese paso y se informe junto con los demás errores.
	@PostMapping
	public ResponseEntity<OrderResponse> create(
			@RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
			@RequestHeader(value = "X-Simulated-Payment", required = false) String simulatedPayment,
			@RequestBody CreateOrderRequest request) {
		Order order = orchestrator.placeOrder(idempotencyKey, simulatedPayment, request);
		URI location = ServletUriComponentsBuilder.fromCurrentRequest().path("/{orderId}")
				.buildAndExpand(order.getId()).toUri();
		return ResponseEntity.status(httpStatus(order)).location(location).body(OrderResponse.from(order));
	}

	// RF-06
	@GetMapping("/{orderId}")
	public OrderResponse get(@PathVariable String orderId) {
		Order order = mongoTemplate.findById(orderId, Order.class);
		if (order == null) {
			throw new ApiException(HttpStatus.NOT_FOUND, "ORDER_NOT_FOUND", "No existe el pedido " + orderId);
		}
		return OrderResponse.from(order);
	}

	// El código sale del estado y no se guarda aparte: una repetición con la misma clave responde lo mismo que la
	// original (RF-05).
	private static HttpStatus httpStatus(Order order) {
		return switch (order.getStatus()) {
			case CONFIRMED -> HttpStatus.CREATED;
			case PROCESSING -> HttpStatus.ACCEPTED;
			case CANCELLED -> order.getReason().httpStatus();
		};
	}

}
