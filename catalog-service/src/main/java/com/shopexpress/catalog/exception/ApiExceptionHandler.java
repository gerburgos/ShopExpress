package com.shopexpress.catalog.exception;

import java.util.stream.Collectors;

import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class ApiExceptionHandler {

	@ExceptionHandler(ApiException.class)
	ResponseEntity<ApiError> handleApiException(ApiException e) {
		return ResponseEntity.status(e.getStatus()).body(new ApiError(e.getCode(), e.getMessage()));
	}

	@ExceptionHandler(MethodArgumentNotValidException.class)
	ResponseEntity<ApiError> handleValidation(MethodArgumentNotValidException e) {
		// Se ordena porque el validador no garantiza el orden de los errores, y así el mensaje es siempre el mismo.
		String details = e.getBindingResult().getFieldErrors().stream()
				.map(error -> error.getField() + ": " + error.getDefaultMessage())
				.sorted()
				.collect(Collectors.joining("; "));
		return ResponseEntity.badRequest().body(new ApiError("VALIDATION_ERROR", "Datos inválidos. " + details));
	}

	@ExceptionHandler(HttpMessageNotReadableException.class)
	ResponseEntity<ApiError> handleUnreadable(HttpMessageNotReadableException e) {
		return ResponseEntity.badRequest().body(new ApiError("MALFORMED_REQUEST",
				"El cuerpo falta, no es un JSON válido o tiene un campo con un tipo incorrecto"));
	}

}
