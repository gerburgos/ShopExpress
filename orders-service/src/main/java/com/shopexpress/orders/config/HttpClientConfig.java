package com.shopexpress.orders.config;

import java.time.Duration;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

// Un RestClient por servicio, con su URL base y timeouts. Sin timeout de lectura, un servicio colgado dejaría la
// compra esperando para siempre; con él, la falla llega como ServiceUnavailableException y la SAGA decide (D-03).
@Configuration
public class HttpClientConfig {

	@Bean
	RestClient usersRestClient(@Value("${services.users.url}") String url,
			SimpleClientHttpRequestFactory requestFactory) {
		return RestClient.builder().baseUrl(url).requestFactory(requestFactory).build();
	}

	@Bean
	RestClient catalogRestClient(@Value("${services.catalog.url}") String url,
			SimpleClientHttpRequestFactory requestFactory) {
		return RestClient.builder().baseUrl(url).requestFactory(requestFactory).build();
	}

	@Bean
	SimpleClientHttpRequestFactory requestFactory(@Value("${http.client.connect-timeout-ms}") long connectTimeout,
			@Value("${http.client.read-timeout-ms}") long readTimeout) {
		SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
		factory.setConnectTimeout(Duration.ofMillis(connectTimeout));
		factory.setReadTimeout(Duration.ofMillis(readTimeout));
		return factory;
	}

}
