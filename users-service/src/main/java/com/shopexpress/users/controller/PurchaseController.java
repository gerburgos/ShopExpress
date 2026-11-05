package com.shopexpress.users.controller;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import com.shopexpress.users.dto.RecordPurchaseRequest;
import com.shopexpress.users.model.Purchase;
import com.shopexpress.users.service.PurchaseService;

import jakarta.validation.Valid;

@RestController
public class PurchaseController {

	private final PurchaseService service;

	public PurchaseController(PurchaseService service) {
		this.service = service;
	}

	// RF-07. Eventualmente consistente: una compra recién confirmada puede tardar en aparecer, si Usuarios no respondía
	// cuando Pedidos la quiso registrar (CA-12).
	@GetMapping("/api/users/{userId}/purchases")
	public List<Purchase> list(@PathVariable String userId) {
		return service.list(userId);
	}

	// INT-04. Solo lo llama Pedidos. 200 y no 201, como la reserva en Catálogo: repetirla devuelve la misma compra, y
	// así la respuesta es igual en los dos casos.
	@PostMapping("/internal/users/{userId}/purchases")
	public Purchase record(@PathVariable String userId, @Valid @RequestBody RecordPurchaseRequest request) {
		return service.record(userId, request);
	}

}
