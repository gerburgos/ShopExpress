package com.shopexpress.catalog.model;

import java.math.BigDecimal;

// El nombre y el precio se copian al reservar: el pedido cobra el precio vigente en ese momento, leído de MongoDB y
// no de la caché (D-11), aunque el producto cambie después.
public record ReservedItem(String productId, String name, int quantity, BigDecimal unitPrice) {
}
