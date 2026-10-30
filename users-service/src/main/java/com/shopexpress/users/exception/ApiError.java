package com.shopexpress.users.exception;

// Formato de error común a los tres servicios: el código en inglés no cambia y es lo que se compara en las pruebas;
// el mensaje en español es para quien lee la respuesta. Cada servicio tiene su propia copia de esta clase: una
// librería compartida obligaría a recompilar y desplegar los tres juntos por un cambio en uno.
public record ApiError(String code, String message) {
}
