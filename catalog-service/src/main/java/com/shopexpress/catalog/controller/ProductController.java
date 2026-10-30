package com.shopexpress.catalog.controller;

import java.net.URI;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import com.shopexpress.catalog.dto.CreateProductRequest;
import com.shopexpress.catalog.dto.ProductResponse;
import com.shopexpress.catalog.dto.UpdateProductRequest;
import com.shopexpress.catalog.model.Product;
import com.shopexpress.catalog.service.ProductService;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/products")
public class ProductController {

	private final ProductService service;

	public ProductController(ProductService service) {
		this.service = service;
	}

	@PostMapping
	public ResponseEntity<ProductResponse> create(@Valid @RequestBody CreateProductRequest request) {
		Product product = service.create(request);
		URI location = ServletUriComponentsBuilder.fromCurrentRequest().path("/{productId}")
				.buildAndExpand(product.id()).toUri();
		return ResponseEntity.created(location).body(ProductResponse.from(product));
	}

	@GetMapping("/{productId}")
	public ProductResponse get(@PathVariable String productId) {
		return ProductResponse.from(service.get(productId));
	}

	@PatchMapping("/{productId}")
	public ProductResponse update(@PathVariable String productId, @Valid @RequestBody UpdateProductRequest request) {
		return ProductResponse.from(service.update(productId, request));
	}

}
