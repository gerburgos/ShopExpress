package com.shopexpress.catalog.config;

import java.time.Duration;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.cache.autoconfigure.RedisCacheManagerBuilderCustomizer;
import org.springframework.boot.data.redis.autoconfigure.LettuceClientOptionsBuilderCustomizer;
import org.springframework.cache.annotation.CachingConfigurer;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.interceptor.CacheErrorHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheWriter;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.serializer.JacksonJsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext.SerializationPair;

import com.shopexpress.catalog.model.Product;

import io.lettuce.core.ClientOptions.DisconnectedBehavior;

@Configuration
@EnableCaching
public class CacheConfig implements CachingConfigurer {

	public static final String PRODUCTS = "products";

	// Se personaliza el RedisCacheManager de Boot en lugar de declarar uno propio: así spring.cache.type=none sigue
	// apagando la caché, que es como se mide la latencia sin ella.
	@Bean
	RedisCacheManagerBuilderCustomizer productsCache(RedisConnectionFactory connectionFactory,
			@Value("${cache.products.ttl-seconds}") long ttlSeconds) {
		RedisCacheConfiguration config = RedisCacheConfiguration.defaultCacheConfig()
				.entryTtl(Duration.ofSeconds(ttlSeconds))
				// JSON con el tipo fijo de la caché: se lee con redis-cli, y no guarda el nombre de la clase en cada
				// entrada, como haría un serializador genérico.
				.serializeValuesWith(SerializationPair.fromSerializer(new JacksonJsonRedisSerializer<>(Product.class)));
		// Por defecto, guardar e invalidar no esperan la respuesta de Redis. Un fallo no llega a errorHandler() ni queda
		// en el log, y la respuesta de una modificación podría salir antes de que se borre la entrada. Se verificó en el
		// código de Spring Data Redis 4.1.2 y con Redis detenido: el PATCH respondía en 0,03 s, sin ningún log.
		RedisCacheWriter writer = RedisCacheWriter.create(connectionFactory, configurer -> configurer.immediateWrites());
		return builder -> builder.cacheWriter(writer).withCacheConfiguration(PRODUCTS, config);
	}

	// Por defecto, mientras el cliente reconecta, encola cada operación hasta que vence el timeout: con Redis caído,
	// cada lectura tardaba 0,5 s antes de ir a MongoDB. Así la rechaza sin esperar. El timeout sigue cubriendo a un
	// Redis colgado que no cortó la conexión.
	@Bean
	LettuceClientOptionsBuilderCustomizer rejectCommandsWhileDisconnected() {
		return options -> options.disconnectedBehavior(DisconnectedBehavior.REJECT_COMMANDS);
	}

	@Override
	public CacheErrorHandler errorHandler() {
		return new CacheErrorLogger();
	}

}
