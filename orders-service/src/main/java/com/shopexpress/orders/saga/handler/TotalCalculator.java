package com.shopexpress.orders.saga.handler;

import java.math.BigDecimal;
import java.math.RoundingMode;

import org.springframework.stereotype.Component;

import com.shopexpress.orders.model.Order;
import com.shopexpress.orders.model.OrderItem;
import com.shopexpress.orders.saga.OrderContext;
import com.shopexpress.orders.saga.OrderHandler;

// Paso 5. El total sale de los precios de la reserva, no de lo que mande el cliente (RN-05). Va antes del pago porque
// es el monto que se autoriza.
@Component
public class TotalCalculator extends OrderHandler {

	@Override
	protected void process(OrderContext context) {
		Order order = context.getOrder();
		BigDecimal total = BigDecimal.ZERO;
		for (OrderItem item : order.getItems()) {
			total = total.add(item.unitPrice().multiply(BigDecimal.valueOf(item.quantity())));
		}
		// Los precios ya tienen dos decimales y la cantidad es entera, así que no hay redondeo: UNNECESSARY falla si
		// llegara a haberlo.
		order.setTotal(total.setScale(2, RoundingMode.UNNECESSARY));
	}

}
