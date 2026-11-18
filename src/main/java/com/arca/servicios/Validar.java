package com.arca.servicios;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;

/** Validaciones de entrada comunes. Lanzan IllegalArgumentException, que se responde con 400. */
final class Validar {
    private Validar() {
    }

    static String texto(String valor, String nombre) {
        if (valor == null || valor.isBlank()) {
            throw new IllegalArgumentException("Falta " + nombre);
        }
        return valor.trim();
    }

    static void dominio(String dominio) {
        if (dominio == null || !dominio.trim().matches("[\\w.-]+\\.[a-zA-Z]{2,}")) {
            throw new IllegalArgumentException("El dominio web no es válido, por ejemplo smithlee.com");
        }
    }

    static void email(String email) {
        if (email == null || !email.trim().matches("[^@\\s]+@[^@\\s]+\\.[a-zA-Z]{2,}")) {
            throw new IllegalArgumentException("El email no es válido");
        }
    }

    static LocalDate fecha(String valor, String nombre) {
        try {
            return LocalDate.parse(texto(valor, nombre));
        } catch (DateTimeParseException e) {
            throw new IllegalArgumentException("Fecha inválida en " + nombre + ": usa AAAA-MM-DD");
        }
    }
}
