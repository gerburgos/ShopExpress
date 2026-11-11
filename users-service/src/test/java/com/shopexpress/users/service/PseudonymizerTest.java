package com.shopexpress.users.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

// Sin Spring ni bases: prueba solo las propiedades que hacen útil al seudónimo.
class PseudonymizerTest {

	private final Pseudonymizer pseudonymizer = new Pseudonymizer("clave-uno");

	@Test
	void elMismoDatoDaSiempreElMismoSeudonimo() {
		assertEquals(pseudonymizer.of("email", "ana@example.com"), pseudonymizer.of("email", "ana@example.com"));
	}

	@Test
	void esUnHexadecimalDe64CaracteresQueNoContieneElOriginal() {
		String pseudonym = pseudonymizer.of("document", "CI:12345678");
		assertTrue(pseudonym.matches("[0-9a-f]{64}"));
		assertFalse(pseudonym.contains("12345678"));
	}

	@Test
	void datosDistintosDanSeudonimosDistintos() {
		assertNotEquals(pseudonymizer.of("document", "CI:12345678"), pseudonymizer.of("document", "DNI:12345678"));
	}

	@Test
	void elMismoTextoComoCampoDistintoDaSeudonimoDistinto() {
		assertNotEquals(pseudonymizer.of("email", "x"), pseudonymizer.of("document", "x"));
	}

	// Es lo que distingue un HMAC de un hash simple: sin la clave no se puede recalcular el seudónimo de un documento
	// probando los 10⁸ valores posibles.
	@Test
	void conOtraClaveElSeudonimoEsOtro() {
		assertNotEquals(new Pseudonymizer("clave-uno").of("email", "ana@example.com"),
				new Pseudonymizer("clave-dos").of("email", "ana@example.com"));
	}

	@Test
	void unaClaveVaciaNoSeAcepta() {
		assertThrows(IllegalStateException.class, () -> new Pseudonymizer("  "));
	}

}
