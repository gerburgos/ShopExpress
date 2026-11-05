package com.shopexpress.orders.model;

// Solo dice cómo terminó la compra. El trabajo interno que falte va aparte, en las acciones pendientes (D-16).
public enum OrderStatus {
	PROCESSING,
	CONFIRMED,
	CANCELLED
}
