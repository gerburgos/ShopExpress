package com.shopexpress.users.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.Cache;
import org.springframework.cache.interceptor.CacheErrorHandler;
import org.springframework.core.NestedExceptionUtils;

// Sin un manejador, Spring propaga los errores de la caché, y una consulta respondería 500 cuando cae Redis. Este
// registra el error y deja seguir, así la lectura va a MongoDB (D-05). Cumple el rol del LoggingCacheErrorHandler de
// Spring, que escribe sus mensajes en inglés.
// No se registra la traza: con Redis caído, cada lectura deja una línea, y la traza entera taparía el resto del log.
public class CacheErrorLogger implements CacheErrorHandler {

	private static final Logger log = LoggerFactory.getLogger(CacheErrorLogger.class);

	@Override
	public void handleCacheGetError(RuntimeException e, Cache cache, Object key) {
		log.warn("Redis no disponible al leer {} de la caché {}; se lee de MongoDB. Causa: {}", key, cache.getName(),
				cause(e));
	}

	@Override
	public void handleCachePutError(RuntimeException e, Cache cache, Object key, Object value) {
		log.warn("No se pudo guardar {} en la caché {}; la próxima lectura irá de nuevo a MongoDB. Causa: {}", key,
				cache.getName(), cause(e));
	}

	@Override
	public void handleCacheEvictError(RuntimeException e, Cache cache, Object key) {
		log.warn("No se pudo invalidar {} en la caché {}; puede verse el dato anterior hasta que venza el TTL. Causa: {}",
				key, cache.getName(), cause(e));
	}

	@Override
	public void handleCacheClearError(RuntimeException e, Cache cache) {
		log.warn("No se pudo vaciar la caché {}. Causa: {}", cache.getName(), cause(e));
	}

	// Spring envuelve el error del cliente en uno genérico ("Redis exception"). El que dice qué pasó, como "Currently
	// not connected", es el más interno.
	private static String cause(RuntimeException e) {
		return NestedExceptionUtils.getMostSpecificCause(e).getMessage();
	}

}
