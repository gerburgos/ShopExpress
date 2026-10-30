package com.shopexpress.catalog.model;

import java.math.BigDecimal;
import java.util.List;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

// stock es el disponible, ya descontadas las reservas, y vive en el mismo documento que el producto: así el
// descuento condicional de una reserva es atómico sin transacción.
@Document("products")
public record Product(
		@Id String id,
		String name,
		String description,
		List<String> categories,
		BigDecimal price,
		int stock) {
}
