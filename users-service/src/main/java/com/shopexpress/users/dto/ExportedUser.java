package com.shopexpress.users.dto;

// Un usuario tal como sale en la exportación (RF-08). El documento y el email van seudonimizados, y no hay direcciones:
// con la calle y la ciudad se identifica a una persona sin necesidad del documento.
// El nombre y el apellido salen en claro, el tipo de documento también (CI, DNI o PASSPORT no identifica a nadie), y el
// id es un UUID al azar.
public record ExportedUser(
		String id,
		String firstName,
		String lastName,
		String documentType,
		String documentPseudonym,
		String emailPseudonym) {
}
