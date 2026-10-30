package com.shopexpress.users.model;

// Con texto libre, "CI", "C.I." y "cedula" serían tipos distintos, y la misma persona podría registrarse dos veces
// con el mismo número.
public enum DocumentType {

	CI, DNI, PASSPORT

}
