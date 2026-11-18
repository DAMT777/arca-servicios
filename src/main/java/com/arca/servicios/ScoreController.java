package com.arca.servicios;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Score Engine de ARCA. Calcula el Pre-Score de Capa 1 a partir de la firma registrada, el puntaje compuesto
 * (30 % Capa 1 y 70 % cuestionario) y la cotización. Si recibe la clave de una evaluación, guarda el resultado.
 * Los pesos y factores son los de demostración del modelo de negocio.
 */
@RestController
@RequestMapping("/api/score")
public class ScoreController {
    static final int PRE_SCORE_SIN_HISTORIAL = 60;
    private static final Map<String, Double> FACTOR_GOBERNANZA =
            Map.of("FORTRESS", 0.85, "FORTIFIED", 0.95, "GUARDED", 1.1, "EXPOSED", 1.3, "CRITICAL", 1.5);
    private final JdbcClient db;

    public ScoreController(JdbcClient db) {
        this.db = db;
    }

    public record SolicitudPreScore(String dominio, Integer abogados) {
    }

    public record SolicitudPuntaje(String clave, Double puntajeCapa1, Double puntajeCapa2, Boolean reclamoPagado,
                                   Boolean incidenteIA, Boolean sinEOVigente, Boolean restriccionCapa1) {
    }

    public record SolicitudCotizacion(String clave, Integer abogados, String tier, String limiteSolicitado) {
    }

    public record SolicitudPlan(String clave) {
    }

    /** Capa 1: identifica la firma por su dominio y toma sus señales públicas registradas. */
    @PostMapping("/pre-score")
    public Map<String, Object> preScore(@RequestBody SolicitudPreScore s) {
        Validar.dominio(s.dominio());
        List<Map<String, Object>> firmas = db.sql("SELECT nombre, pre_score, restriccion_disciplinaria FROM firma WHERE dominio = :d")
                .param("d", s.dominio().trim().toLowerCase()).query().listOfRows();
        Map<String, Object> r = new LinkedHashMap<>();
        boolean identificada = !firmas.isEmpty();
        r.put("identificada", identificada);
        r.put("firmaIdentificada", identificada ? firmas.get(0).get("nombre") : s.dominio().split("\\.")[0]);
        r.put("preScore", identificada ? ((Number) firmas.get(0).get("pre_score")).intValue() : PRE_SCORE_SIN_HISTORIAL);
        r.put("restriccion", identificada && Boolean.TRUE.equals(firmas.get(0).get("restriccion_disciplinaria")));
        r.put("senales", "AI Governance, Professional Standing, Reputation, Firm Maturity");
        return r;
    }

    @PostMapping("/puntaje")
    public Map<String, Object> puntaje(@RequestBody SolicitudPuntaje s) {
        double capa1 = rango(s.puntajeCapa1(), "puntajeCapa1");
        double capa2 = rango(s.puntajeCapa2(), "puntajeCapa2");
        int puntaje = (int) Math.round(0.3 * capa1 + 0.7 * capa2);
        boolean forzada = si(s.reclamoPagado()) || si(s.incidenteIA()) || si(s.sinEOVigente()) || si(s.restriccionCapa1());
        if (s.clave() != null) {
            db.sql("""
                    UPDATE evaluacion SET puntaje_cuestionario = :c2, puntaje = :p, estado = 'PUNTAJE_CALCULADO',
                           actualizada_en = CURRENT_TIMESTAMP WHERE clave = :clave""")
                    .param("c2", (int) Math.round(capa2)).param("p", puntaje).param("clave", s.clave()).update();
        }
        return Map.of("puntaje", puntaje, "revisionForzada", forzada);
    }

    /** Prima anual = 420 × abogados × factor de gobernanza del tier (fórmula de diseño, sin tarifa aprobada). */
    @PostMapping("/cotizacion")
    public Map<String, Object> cotizacion(@RequestBody SolicitudCotizacion s) {
        if (s.abogados() == null || s.abogados() < 1) {
            throw new IllegalArgumentException("Falta el número de abogados");
        }
        Double factor = FACTOR_GOBERNANZA.get(s.tier());
        if (factor == null) {
            throw new IllegalArgumentException("Tier inválido: " + s.tier());
        }
        long prima = Math.round(420 * s.abogados() * factor);
        String limite = s.limiteSolicitado() == null || s.limiteSolicitado().isBlank() ? "Professional" : s.limiteSolicitado();
        if (s.clave() != null) {
            db.sql("""
                    UPDATE evaluacion SET prima_anual = :prima, limite_ofrecido = :limite, tier = :tier, estado = 'COTIZADA',
                           actualizada_en = CURRENT_TIMESTAMP WHERE clave = :clave""")
                    .param("prima", prima).param("limite", limite).param("tier", s.tier()).param("clave", s.clave()).update();
        }
        return Map.of("primaAnual", prima, "limiteOfrecido", limite);
    }

    @PostMapping("/plan-mejora")
    public Map<String, Object> planMejora(@RequestBody SolicitudPlan s) {
        LocalDate fecha = LocalDate.now().plusDays(90);
        if (s.clave() != null) {
            db.sql("""
                    UPDATE evaluacion SET fecha_reevaluacion = :fecha, estado = 'PLAN_DE_MEJORA',
                           actualizada_en = CURRENT_TIMESTAMP WHERE clave = :clave""")
                    .param("fecha", fecha).param("clave", s.clave()).update();
        }
        return Map.of("fechaReevaluacion", fecha.toString());
    }

    private static double rango(Double v, String nombre) {
        if (v == null || v < 0 || v > 100) {
            throw new IllegalArgumentException(nombre + " debe estar entre 0 y 100");
        }
        return v;
    }

    private static boolean si(Boolean b) {
        return Boolean.TRUE.equals(b);
    }
}
