package com.shopexpress.catalog.dto;

import java.math.BigDecimal;
import java.util.List;

import com.shopexpress.catalog.model.Product;

public record ProductResponse(
		String id,
		String name,
		String description,
		List<String> categories,
		BigDecimal price,
		int stock) {

	public static ProductResponse from(Product product) {
		return new ProductResponse(product.id(), product.name(), product.description(), product.categories(),
				product.price(), product.stock());
	}

}
