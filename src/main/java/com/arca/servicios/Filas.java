package com.arca.servicios;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;

/** Convierte filas de la base (columnas en snake_case) a JSON en camelCase. */
public final class Filas {
    private Filas() {
    }

    public static Map<String, Object> camel(Map<String, Object> fila) {
        Map<String, Object> r = new LinkedHashMap<>();
        fila.forEach((k, v) -> r.put(camel(k), v));
        return r;
    }

    public static List<Map<String, Object>> camel(List<Map<String, Object>> filas) {
        return filas.stream().map(Filas::camel).toList();
    }

    public static Map<String, Object> unica(List<Map<String, Object>> filas, String queNoExiste) {
        if (filas.isEmpty()) {
            throw new NoSuchElementException(queNoExiste);
        }
        return camel(filas.get(0));
    }

    private static String camel(String columna) {
        StringBuilder sb = new StringBuilder();
        boolean mayuscula = false;
        for (char c : columna.toLowerCase().toCharArray()) {
            if (c == '_') {
                mayuscula = true;
            } else {
                sb.append(mayuscula ? Character.toUpperCase(c) : c);
                mayuscula = false;
            }
        }
        return sb.toString();
    }
}
