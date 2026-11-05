package com.shopexpress.users.model;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.mapping.Document;

// Una compra confirmada en el historial del usuario. Es una proyección: la fuente es el pedido en Pedidos, y esta copia
// solo sirve para consultar "mis compras" desde Usuarios (D-09, un ejemplo de CQRS).
// En su propia colección y no embebida en el usuario: crece sin límite, la escribe otro servicio, y embebida cada
// compra invalidaría la caché del usuario (3.6.1).
// El id es el orderId: registrar dos veces la misma compra encuentra la que ya está, en lugar de duplicarla (INT-04).
@Document("purchases")
// Para RF-07, que filtra por usuario y ordena de la más reciente a la más antigua.
@CompoundIndex(name = "user_date", def = "{'userId': 1, 'date': -1}")
public record Purchase(@Id String orderId, String userId, Instant date, BigDecimal total, List<Item> items) {

	public record Item(String productId, String name, int quantity) {
	}

}
