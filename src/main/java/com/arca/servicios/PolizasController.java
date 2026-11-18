package com.arca.servicios;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.sql.Date;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Consulta de pólizas para C10: existencia, titularidad y vigencia a la fecha del hecho. */
@RestController
@RequestMapping("/api/polizas")
public class PolizasController {
    private final JdbcClient db;

    public PolizasController(JdbcClient db) {
        this.db = db;
    }

    @GetMapping("/{numero}/vigencia")
    public Map<String, Object> vigencia(@PathVariable String numero, @RequestParam String fecha,
                                        @RequestParam(required = false) String firma) {
        LocalDate hecho = Validar.fecha(fecha, "la fecha del hecho");
        List<Map<String, Object>> filas = db.sql("""
                SELECT p.numero, p.inicio_vigencia, p.fin_vigencia, p.limite_cobertura, p.deducible, p.exclusiones,
                       f.nombre AS firma
                FROM poliza p JOIN firma f ON f.id = p.firma_id
                WHERE p.numero = :numero""").param("numero", numero.trim().toUpperCase()).query().listOfRows();

        Map<String, Object> r = new LinkedHashMap<>();
        r.put("numero", numero.trim().toUpperCase());
        if (filas.isEmpty()) {
            r.put("existe", false);
            r.put("vigente", false);
            r.put("motivo", "La póliza no existe en el registro de ARCA");
            return r;
        }
        Map<String, Object> p = Filas.camel(filas.get(0));
        LocalDate inicio = ((Date) filas.get(0).get("inicio_vigencia")).toLocalDate();
        LocalDate fin = ((Date) filas.get(0).get("fin_vigencia")).toLocalDate();
        boolean deLaFirma = firma == null || firma.isBlank() || firma.trim().equalsIgnoreCase((String) p.get("firma"));
        boolean enFechas = !hecho.isBefore(inicio) && !hecho.isAfter(fin);
        r.putAll(p);
        r.put("existe", true);
        r.put("deLaFirma", deLaFirma);
        r.put("vigente", deLaFirma && enFechas);
        r.put("motivo", !deLaFirma ? "La póliza pertenece a otra firma"
                : enFechas ? "La póliza estaba vigente a la fecha del hecho"
                : "El hecho ocurrió fuera de la vigencia de la póliza (" + inicio + " a " + fin + ")");
        return r;
    }
}
