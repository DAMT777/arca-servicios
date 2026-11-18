package com.arca.servicios;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Map;
import java.util.NoSuchElementException;

/** Respuestas de error en JSON con un mensaje legible para el motor y para las vistas. */
@RestControllerAdvice
public class ErroresControlados {

    @ExceptionHandler({IllegalArgumentException.class, HttpMessageNotReadableException.class})
    public ResponseEntity<Map<String, String>> datosInvalidos(Exception e) {
        return respuesta(HttpStatus.BAD_REQUEST, e instanceof HttpMessageNotReadableException
                ? "El cuerpo de la petición no es un JSON válido" : e.getMessage());
    }

    @ExceptionHandler(NoSuchElementException.class)
    public ResponseEntity<Map<String, String>> noEncontrado(NoSuchElementException e) {
        return respuesta(HttpStatus.NOT_FOUND, e.getMessage());
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<Map<String, String>> duplicado(DataIntegrityViolationException e) {
        return respuesta(HttpStatus.CONFLICT, "Ya existe un registro con esos datos (nombre, dominio, email o clave repetidos)");
    }

    private static ResponseEntity<Map<String, String>> respuesta(HttpStatus estado, String mensaje) {
        return ResponseEntity.status(estado).body(Map.of("error", mensaje == null ? estado.getReasonPhrase() : mensaje));
    }
}
