package com.shopexpress.orders.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import com.shopexpress.orders.model.CancellationReason;
import com.shopexpress.orders.model.Order;
import com.shopexpress.orders.model.OrderItem;
import com.shopexpress.orders.model.OrderStatus;
import com.shopexpress.orders.model.Payment;

// No expone la clave de idempotencia ni los pasos completados: son datos internos del proceso (3.6.2).
public record OrderResponse(
		String id,
		String userId,
		OrderStatus status,
		CancellationReason reason,
		List<OrderItem> items,
		BigDecimal total,
		String currency,
		Payment payment,
		Instant createdAt) {

	public static OrderResponse from(Order order) {
		return new OrderResponse(order.getId(), order.getUserId(), order.getStatus(), order.getReason(),
				order.getItems(), order.getTotal(), order.getCurrency(), order.getPayment(), order.getCreatedAt());
	}

}
