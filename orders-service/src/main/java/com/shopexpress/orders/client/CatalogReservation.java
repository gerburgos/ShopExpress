package com.shopexpress.orders.client;

import java.util.List;

import com.shopexpress.orders.model.OrderItem;

// Lo que devuelve Catálogo al reservar: cada ítem con el nombre y el precio vigentes, leídos de MongoDB (D-11).
public record CatalogReservation(String orderId, String status, List<OrderItem> items) {
}
