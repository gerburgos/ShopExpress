package com.shopexpress.catalog.model;

import java.math.BigDecimal;
import java.util.List;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

// stock es el disponible, ya descontadas las reservas, y vive en el mismo documento que el producto: así el
// descuento condicional de una reserva es atómico sin transacción.
// El id lo genera el servidor y es el que usan las rutas, las reservas y los pedidos. El sku es el código para las
// personas: lo carga el administrador y no se modifica (D-19).
@Document("products")
public record Product(
		@Id String id,
		// El nombre del índice permite saber, ante un DuplicateKeyException, que lo repetido es el SKU.
		@Indexed(name = Product.SKU_INDEX, unique = true) String sku,
		String name,
		String description,
		List<String> categories,
		BigDecimal price,
		int stock) {

	public static final String SKU_INDEX = "sku_unique";

}
