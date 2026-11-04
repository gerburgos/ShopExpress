package com.shopexpress.catalog.controller;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.shopexpress.catalog.dto.ReservationResponse;
import com.shopexpress.catalog.dto.ReserveRequest;
import com.shopexpress.catalog.service.ReservationService;

import jakarta.validation.Valid;

// Endpoints que solo llama Pedidos (3.1.2). Van bajo /internal para separarlos de la API pública.
@RestController
@RequestMapping("/internal/reservations")
public class InternalReservationController {

	private final ReservationService service;

	public InternalReservationController(ReservationService service) {
		this.service = service;
	}

	// 200 y no 201, aunque la primera llamada crea la reserva: repetirla devuelve la misma, y así la respuesta es
	// igual en los dos casos.
	@PostMapping
	public ReservationResponse reserve(@Valid @RequestBody ReserveRequest request) {
		return ReservationResponse.from(service.reserve(request));
	}

}
