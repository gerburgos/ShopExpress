package com.shopexpress.users.service;

import java.util.List;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;

import com.shopexpress.users.dto.ExportedUser;
import com.shopexpress.users.repository.UserRepository;

@Service
public class UserExportService {

	private static final String CSV_HEADER = "id,firstName,lastName,documentType,documentPseudonym,emailPseudonym";

	private final UserRepository repository;
	private final Pseudonymizer pseudonymizer;

	public UserExportService(UserRepository repository, Pseudonymizer pseudonymizer) {
		this.repository = repository;
		this.pseudonymizer = pseudonymizer;
	}

	// Trae todos los usuarios de una vez: alcanza para esta escala. Con muchos habría que paginar o ir escribiendo la
	// respuesta de a poco.
	public List<ExportedUser> export() {
		return repository.findAll().stream()
				.map(user -> new ExportedUser(
						user.id(),
						user.firstName(),
						user.lastName(),
						user.documentType().name(),
						// El tipo entra en lo que se firma: (CI, 1234) y (DNI, 1234) son documentos distintos, y no
						// tienen que dar el mismo seudónimo (RF-01).
						pseudonymizer.of("document", user.documentType() + ":" + user.documentNumber()),
						pseudonymizer.of("email", user.email())))
				.toList();
	}

	public String toCsv(List<ExportedUser> users) {
		// \r\n como pide el formato CSV (RFC 4180).
		return CSV_HEADER + "\r\n" + users.stream()
				.map(user -> String.join(",", cell(user.id()), cell(user.firstName()), cell(user.lastName()),
						cell(user.documentType()), cell(user.documentPseudonym()), cell(user.emailPseudonym())))
				.collect(Collectors.joining("\r\n"));
	}

	// El nombre lo escribe quien se registra, y la exportación se abre en una planilla: un nombre que empieza con =, +,
	// - o @ se tomaría como una fórmula. El apóstrofe inicial lo vuelve texto.
	private static String cell(String value) {
		String text = value == null ? "" : value;
		if (!text.isEmpty() && "=+-@\t\r".indexOf(text.charAt(0)) >= 0) {
			text = "'" + text;
		}
		if (text.contains(",") || text.contains("\"") || text.contains("\n") || text.contains("\r")) {
			text = "\"" + text.replace("\"", "\"\"") + "\"";
		}
		return text;
	}

}
