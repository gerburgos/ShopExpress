package com.shopexpress.catalog.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import com.shopexpress.catalog.config.CacheConfig;
import com.shopexpress.catalog.dto.CreateProductRequest;
import com.shopexpress.catalog.dto.UpdateProductRequest;
import com.shopexpress.catalog.exception.ApiException;
import com.shopexpress.catalog.model.Product;
import com.shopexpress.catalog.repository.ProductRepository;

@Service
public class ProductService {

	private static final Logger log = LoggerFactory.getLogger(ProductService.class);

	private final ProductRepository repository;
	private final MongoTemplate mongoTemplate;

	public ProductService(ProductRepository repository, MongoTemplate mongoTemplate) {
		this.repository = repository;
		this.mongoTemplate = mongoTemplate;
	}

	public Product create(CreateProductRequest request) {
		// El SKU es único: se guarda en mayúsculas para que "mate-001" y "MATE-001" no pasen como productos distintos.
		Product product = new Product(
				"prod_" + UUID.randomUUID(),
				request.sku().toUpperCase(Locale.ROOT),
				request.name(),
				request.description(),
				request.categories() == null ? List.of() : request.categories(),
				normalizePrice(request.price()),
				request.stock());
		try {
			// insert y no save: save reemplaza el documento si el id ya existe, en lugar de fallar.
			return repository.insert(product);
		}
		catch (DuplicateKeyException e) {
			// La unicidad la garantiza el índice, también con dos altas simultáneas. Consultar antes de insertar
			// tendría una carrera entre la consulta y el insert.
			if (String.valueOf(e.getMessage()).contains(Product.SKU_INDEX)) {
				throw new ApiException(HttpStatus.CONFLICT, "SKU_ALREADY_EXISTS",
						"Ya existe un producto con el SKU " + product.sku());
			}
			throw e;
		}
	}

	// Cache-aside: si el producto está en Redis, este método no se ejecuta. Por eso el log de abajo aparece solo cuando
	// la lectura llega a MongoDB, y sirve para comprobar que la segunda lectura sale de la caché (CA-11).
	// Las ventas no invalidan esta entrada: el stock que se muestra puede atrasarse hasta un TTL, y la reserva lo
	// controla contra MongoDB (D-05, RN-08).
	@Cacheable(cacheNames = CacheConfig.PRODUCTS, key = "#productId")
	public Product get(String productId) {
		log.info("Producto {} leído de MongoDB", productId);
		return repository.findById(productId).orElseThrow(() -> notFound(productId));
	}

	// Se invalida y no se actualiza la entrada: la próxima lectura la vuelve a cargar desde MongoDB, con el precio
	// nuevo (CA-16). Si la modificación falla, la entrada queda como estaba.
	@CacheEvict(cacheNames = CacheConfig.PRODUCTS, key = "#productId")
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

	// RF-10. A diferencia de las ventas, invalida la caché: es una modificación administrativa, y quien repone espera
	// ver el stock nuevo enseguida (D-05).
	// No es idempotente: si se repite, suma dos veces. Se acepta porque es una operación de demo (sección 5).
	@CacheEvict(cacheNames = CacheConfig.PRODUCTS, key = "#productId")
	public Product restock(String productId, int quantity) {
		// $inc atómico sobre el documento: una reserva concurrente también resta con $inc, y las dos se aplican sin
		// pisarse (RN-12).
		Product restocked = mongoTemplate.findAndModify(Query.query(Criteria.where("id").is(productId)),
				new Update().inc("stock", quantity), FindAndModifyOptions.options().returnNew(true), Product.class);
		if (restocked == null) {
			throw notFound(productId);
		}
		return restocked;
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
