package com.shopexpress.users.controller;

import java.nio.charset.StandardCharsets;
import java.util.Locale;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.shopexpress.users.exception.ApiException;
import com.shopexpress.users.service.UserExportService;

@RestController
public class UserExportController {

	private static final MediaType CSV = new MediaType("text", "csv", StandardCharsets.UTF_8);

	private final UserExportService service;

	public UserExportController(UserExportService service) {
		this.service = service;
	}

	// RF-08. La consigna lo nombra /api/usuarios/exportar. Esta ruta tiene una parte literal, y por eso gana frente a
	// GET /api/users/{userId}: "export" no se toma como el id de un usuario.
	@GetMapping("/api/users/export")
	public ResponseEntity<Object> export(@RequestParam(defaultValue = "json") String format) {
		return switch (format.toLowerCase(Locale.ROOT)) {
			case "json" -> ResponseEntity.ok(service.export());
			case "csv" -> ResponseEntity.ok()
					.contentType(CSV)
					// Para que el navegador lo descargue con nombre, en lugar de mostrar el texto.
					.header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"usuarios.csv\"")
					.body(service.toCsv(service.export()));
			default -> throw new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR",
					"Datos inválidos. format: debe ser json o csv");
		};
	}

}
