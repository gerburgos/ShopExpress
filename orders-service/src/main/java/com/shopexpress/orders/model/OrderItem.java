package com.shopexpress.orders.model;

import java.math.BigDecimal;

// El nombre y el precio son los que devolvió la reserva: el pedido conserva lo que se compró aunque el catálogo
// cambie después (RN-05). El productId queda como referencia.
public record OrderItem(String productId, String name, int quantity, BigDecimal unitPrice) {
}
