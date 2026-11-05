package com.shopexpress.orders.saga;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.shopexpress.orders.saga.handler.DataValidator;
import com.shopexpress.orders.saga.handler.OrderConfirmer;
import com.shopexpress.orders.saga.handler.OrderCreator;
import com.shopexpress.orders.saga.handler.PaymentCapturer;
import com.shopexpress.orders.saga.handler.PaymentProcessor;
import com.shopexpress.orders.saga.handler.StockReserver;
import com.shopexpress.orders.saga.handler.TotalCalculator;
import com.shopexpress.orders.saga.handler.UserValidator;

// El orden de la cadena está escrito en un solo lugar. Reservar va antes de pagar: la falta de stock se resuelve sin
// tocar la pasarela, y el monto sale de los precios de la reserva (D-12).
@Configuration
public class OrderChainConfig {

	@Bean
	OrderHandler orderChain(DataValidator dataValidator, UserValidator userValidator, OrderCreator orderCreator,
			StockReserver stockReserver, TotalCalculator totalCalculator, PaymentProcessor paymentProcessor,
			OrderConfirmer orderConfirmer, PaymentCapturer paymentCapturer) {
		// El paso 9, PurchaseHistoryRecorder, llega en INC-5 junto con el historial de compras de Usuarios (INT-04).
		dataValidator
				.setNext(userValidator)
				.setNext(orderCreator)
				.setNext(stockReserver)
				.setNext(totalCalculator)
				.setNext(paymentProcessor)
				.setNext(orderConfirmer)
				.setNext(paymentCapturer);
		return dataValidator;
	}

}
