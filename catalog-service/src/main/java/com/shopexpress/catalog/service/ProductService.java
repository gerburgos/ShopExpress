package com.shopexpress.catalog.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.UUID;

import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import com.shopexpress.catalog.dto.CreateProductRequest;
import com.shopexpress.catalog.dto.UpdateProductRequest;
import com.shopexpress.catalog.exception.ApiException;
import com.shopexpress.catalog.model.Product;
import com.shopexpress.catalog.repository.ProductRepository;

@Service
public class ProductService {

	private final ProductRepository repository;
	private final MongoTemplate mongoTemplate;

	public ProductService(ProductRepository repository, MongoTemplate mongoTemplate) {
		this.repository = repository;
		this.mongoTemplate = mongoTemplate;
	}

	public Product create(CreateProductRequest request) {
		Product product = new Product(
				"prod_" + UUID.randomUUID(),
				request.name(),
				request.description(),
				request.categories() == null ? List.of() : request.categories(),
				normalizePrice(request.price()),
				request.stock());
		// insert y no save: save reemplaza el documento si el id ya existe, en lugar de fallar.
		return repository.insert(product);
	}

	public Product get(String productId) {
		return repository.findById(productId).orElseThrow(() -> notFound(productId));
	}

	public Product update(String productId, UpdateProductRequest request) {
		Update update = new Update();
		if (request.name() != null) {
			update.set("name", request.name());
		}
		if (request.description() != null) {
			update.set("description", request.description());
		}
		if (request.categories() != null) {
			update.set("categories", request.categories());
		}
		if (request.price() != null) {
			update.set("price", normalizePrice(request.price()));
		}
		if (update.getUpdateObject().isEmpty()) {
			return get(productId);
		}
		// Solo un $set de los campos recibidos, nunca un save() del documento entero. save también reescribiría
		// el stock que se leyó, y pisaría una reserva o una reposición hecha entre la lectura y la escritura (RN-12).
		Product updated = mongoTemplate.findAndModify(Query.query(Criteria.where("id").is(productId)), update,
				FindAndModifyOptions.options().returnNew(true), Product.class);
		if (updated == null) {
			throw notFound(productId);
		}
		return updated;
	}

	// La API muestra los importes con dos decimales: 25.5 se guarda como 25.50. La validación ya rechazó más de dos
	// decimales, así que UNNECESSARY nunca redondea: si llegara a hacerlo, falla.
	private static BigDecimal normalizePrice(BigDecimal price) {
		return price.setScale(2, RoundingMode.UNNECESSARY);
	}

	private static ApiException notFound(String productId) {
		return new ApiException(HttpStatus.NOT_FOUND, "PRODUCT_NOT_FOUND", "No existe el producto " + productId);
	}

}
