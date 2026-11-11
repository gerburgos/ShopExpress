package com.shopexpress.users.service;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.util.HexFormat;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

// Seudonimiza con un HMAC-SHA256 con clave secreta, y no con un hash simple: un documento de 8 dígitos tiene 10⁸
// valores posibles, y quien tenga la exportación los prueba todos y recupera el original (RF-08). Con la clave secreta,
// no puede calcular el valor de un documento que él mismo inventa.
// El mismo dato da siempre el mismo seudónimo, así se pueden cruzar registros sin saber de quién son. Eso es lo que
// distingue la seudonimización de la anonimización: con la clave, sí se puede volver a calcular.
@Component
public class Pseudonymizer {

	private static final String ALGORITHM = "HmacSHA256";

	private final SecretKeySpec key;

	public Pseudonymizer(@Value("${export.pseudonymization-key}") String secret) {
		// Una clave vacía no falla en HMAC, pero haría que cualquiera pudiera recalcular los seudónimos.
		if (secret.isBlank()) {
			throw new IllegalStateException("export.pseudonymization-key no puede estar vacía");
		}
		this.key = new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), ALGORITHM);
	}

	// El campo entra en lo que se firma: el mismo texto como email y como documento da seudónimos distintos, así uno no
	// sirve para adivinar el otro.
	public String of(String field, String value) {
		try {
			// Mac no es seguro entre hilos: se crea una por llamada.
			Mac mac = Mac.getInstance(ALGORITHM);
			mac.init(key);
			byte[] digest = mac.doFinal((field + ":" + value).getBytes(StandardCharsets.UTF_8));
			return HexFormat.of().formatHex(digest);
		}
		catch (GeneralSecurityException e) {
			// Toda JVM trae HmacSHA256, y la clave ya se validó: si falla, es un problema del entorno.
			throw new IllegalStateException(e);
		}
	}

}
