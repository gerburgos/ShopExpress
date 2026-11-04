package com.shopexpress.catalog.service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Supplier;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import com.mongodb.MongoException;
import com.shopexpress.catalog.dto.ReserveRequest;
import com.shopexpress.catalog.exception.ApiException;
import com.shopexpress.catalog.model.Product;
import com.shopexpress.catalog.model.Reservation;
import com.shopexpress.catalog.model.ReservationStatus;
import com.shopexpress.catalog.model.ReservedItem;

@Service
public class ReservationService {

	private static final Logger log = LoggerFactory.getLogger(ReservationService.class);

	private final MongoTemplate mongoTemplate;
	private final TransactionTemplate transactionTemplate;
	private final int maxAttempts;

	public ReservationService(MongoTemplate mongoTemplate, TransactionTemplate transactionTemplate,
			@Value("${catalog.reservation.max-transaction-attempts}") int maxAttempts) {
		this.mongoTemplate = mongoTemplate;
		this.transactionTemplate = transactionTemplate;
		this.maxAttempts = maxAttempts;
	}

	// INT-01.
	public Reservation reserve(ReserveRequest request) {
		rejectRepeatedProducts(request);
		return inTransactionWithRetry(request.orderId(), () -> reserveInTransaction(request));
	}

	// INT-03, el pivote de la compra. Sin transacción: cambia un solo documento, y un update condicional sobre un
	// documento ya es atómico. La condición status = RESERVED hace que, si llegan a la vez una confirmación y una
	// liberación, gane una sola.
	public Reservation confirm(String orderId) {
		Query reserved = Query.query(Criteria.where("orderId").is(orderId).and("status").is(ReservationStatus.RESERVED));
		Update toConfirmed = new Update().set("status", ReservationStatus.CONFIRMED).set("updatedAt", Instant.now());
		Reservation confirmed = mongoTemplate.findAndModify(reserved, toConfirmed,
				FindAndModifyOptions.options().returnNew(true), Reservation.class);
		if (confirmed != null) {
			return confirmed;
		}
		// No estaba RESERVED: se lee para responder por qué.
		Reservation current = mongoTemplate.findById(orderId, Reservation.class);
		if (current == null) {
			// En el flujo normal no pasa, porque Pedidos confirma después de reservar: sería un error de programación.
			// 404 y no 409, para que en el log se distinga de una reserva que se perdió en una carrera.
			throw new ApiException(HttpStatus.NOT_FOUND, "RESERVATION_NOT_FOUND",
					"No existe la reserva del pedido " + orderId);
		}
		if (current.status() == ReservationStatus.RELEASED) {
			// La reserva se liberó antes de confirmarse: el stock ya volvió y no se puede vender. Pedidos reversa la
			// autorización del pago (F-08).
			throw released(orderId);
		}
		// Ya estaba CONFIRMED: Pedidos repitió la llamada porque no le llegó la respuesta (RN-03).
		return current;
	}

	// INT-02, la compensación. En una transacción porque cambian varios documentos: el estado de la reserva y el stock
	// de cada producto. Si el servicio cae a mitad, no puede quedar la reserva liberada sin el stock devuelto.
	public Reservation release(String orderId) {
		return inTransactionWithRetry(orderId, () -> releaseInTransaction(orderId));
	}

	// El reintento va afuera de la transacción: cuando MongoDB aborta una transacción, no se puede seguir usando, y hay
	// que empezar otra desde cero.
	private Reservation inTransactionWithRetry(String orderId, Supplier<Reservation> work) {
		for (int attempt = 1; ; attempt++) {
			try {
				return transactionTemplate.execute(status -> work.get());
			} catch (DataAccessException e) {
				if (!isRetryable(e)) {
					throw e;
				}
				if (attempt == maxAttempts) {
					log.warn("La operación sobre la reserva del pedido {} chocó con otras en los {} intentos", orderId,
							maxAttempts);
					// 503 y no 409: no es un resultado definitivo como la falta de stock. Pedidos lo trata como una
					// falla transitoria y repite la llamada con el mismo orderId (D-17).
					throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "RESERVATION_CONFLICT",
							"La operación chocó con otras sobre los mismos productos; reintentar");
				}
				log.info("La operación sobre la reserva del pedido {} chocó con otra (intento {} de {}); se reintenta",
						orderId, attempt, maxAttempts);
			}
		}
	}

	private Reservation releaseInTransaction(String orderId) {
		Instant now = Instant.now();
		Reservation existing = mongoTemplate.findById(orderId, Reservation.class);
		if (existing == null) {
			// Lápida: Pedidos libera cuando no supo si la reserva llegó a hacerse (F-05). Si la reserva está todavía en
			// camino y llega después, encuentra esta reserva liberada y no descuenta stock de un pedido cancelado.
			return mongoTemplate.insert(new Reservation(orderId, ReservationStatus.RELEASED, List.of(), now, now));
		}
		if (existing.status() == ReservationStatus.RELEASED) {
			// Ya liberada: Pedidos repitió la compensación. Devolver el stock otra vez sería sumarlo dos veces (RN-06).
			return existing;
		}
		if (existing.status() == ReservationStatus.CONFIRMED) {
			// Después del pivote el stock ya está vendido (RN-09). Si Pedidos lo pide, es un error, y se avisa en lugar
			// de ignorarlo.
			throw new ApiException(HttpStatus.CONFLICT, "RESERVATION_CONFIRMED",
					"La reserva del pedido " + orderId + " ya fue confirmada y no se puede liberar");
		}
		for (ReservedItem item : existing.items()) {
			// Se suma lo reservado, no se fija el valor: fijarlo pisaría otra reserva o una reposición (RN-12). Los
			// productos no se borran (D-13), así que el producto siempre existe.
			mongoTemplate.updateFirst(Query.query(Criteria.where("id").is(item.productId())),
					new Update().inc("stock", item.quantity()), Product.class);
		}
		// save reemplaza la reserva que se leyó al principio. Si una confirmación la cambió en el medio, MongoDB aborta
		// la transacción por conflicto de escritura, el reintento la lee CONFIRMED y responde 409: no se devuelve stock
		// vendido.
		Reservation released = new Reservation(orderId, ReservationStatus.RELEASED, existing.items(),
				existing.createdAt(), now);
		return mongoTemplate.save(released);
	}

	private static ApiException released(String orderId) {
		return new ApiException(HttpStatus.CONFLICT, "RESERVATION_RELEASED",
				"La reserva del pedido " + orderId + " ya fue liberada");
	}

	private Reservation reserveInTransaction(ReserveRequest request) {
		// Si el pedido ya tiene reserva, se devuelve la misma: Pedidos repite la llamada cuando no le llegó la
		// respuesta, y descontar otra vez sería vender dos veces (RN-03).
		Reservation existing = mongoTemplate.findById(request.orderId(), Reservation.class);
		if (existing != null) {
			if (existing.status() == ReservationStatus.RELEASED) {
				// Una reserva liberada, o una lápida, significa que el pedido ya se canceló: una llamada que llega
				// tarde no puede volver a descontar stock (RN-09).
				throw released(request.orderId());
			}
			return existing;
		}

		List<ReservedItem> items = new ArrayList<>();
		for (ReserveRequest.Item item : request.items()) {
			// Descuento condicional: solo si alcanza el stock. Leer, comparar y después escribir dejaría una ventana en
			// la que dos compras ven el mismo stock y lo descuentan las dos (RN-01). Se suma un negativo, no se fija el
			// valor (RN-12).
			Query query = Query.query(Criteria.where("id").is(item.productId()).and("stock").gte(item.quantity()));
			Product product = mongoTemplate.findAndModify(query, new Update().inc("stock", -item.quantity()),
					FindAndModifyOptions.options().returnNew(true), Product.class);
			if (product == null) {
				// La excepción aborta la transacción: los ítems que ya se descontaron vuelven atrás (D-04).
				throw missingStockOrProduct(item.productId());
			}
			items.add(new ReservedItem(product.id(), product.name(), item.quantity(), product.price()));
		}

		Instant now = Instant.now();
		return mongoTemplate.insert(new Reservation(request.orderId(), ReservationStatus.RESERVED, items, now, now));
	}

	// El update condicional no dice por qué no encontró el producto. Se pregunta si existe para responder el motivo
	// correcto: Pedidos cancela con un motivo distinto en cada caso.
	private ApiException missingStockOrProduct(String productId) {
		if (mongoTemplate.exists(Query.query(Criteria.where("id").is(productId)), Product.class)) {
			return new ApiException(HttpStatus.CONFLICT, "OUT_OF_STOCK",
					"No hay stock suficiente del producto " + productId);
		}
		return new ApiException(HttpStatus.NOT_FOUND, "PRODUCT_NOT_FOUND", "No existe el producto " + productId);
	}

	// Con el mismo producto en dos ítems, el segundo descuento vería el stock ya bajado por el primero, y la cantidad
	// total quedaría ambigua (RN-10).
	private static void rejectRepeatedProducts(ReserveRequest request) {
		Set<String> seen = new HashSet<>();
		for (ReserveRequest.Item item : request.items()) {
			if (!seen.add(item.productId())) {
				throw new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR",
						"Datos inválidos. items: el producto " + item.productId() + " está repetido");
			}
		}
	}

	// Se reintenta en dos casos:
	// - MongoDB marcó el error como transitorio: otra transacción modificó el mismo producto, o no se supo si el commit
	//   se aplicó. Repetir es seguro porque el intento siguiente encuentra la reserva si el commit había entrado.
	// - Otra llamada con el mismo orderId insertó la reserva primero. El intento siguiente la encuentra y la devuelve.
	private static boolean isRetryable(DataAccessException e) {
		if (e instanceof DuplicateKeyException) {
			return true;
		}
		for (Throwable cause = e; cause != null; cause = cause.getCause()) {
			if (cause instanceof MongoException mongoException
					&& (mongoException.hasErrorLabel(MongoException.TRANSIENT_TRANSACTION_ERROR_LABEL)
							|| mongoException.hasErrorLabel(MongoException.UNKNOWN_TRANSACTION_COMMIT_RESULT_LABEL))) {
				return true;
			}
		}
		return false;
	}

}
