package com.arca.servicios;

import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Set;

/** Evaluaciones de riesgo de C3: se registran al presentar el cuestionario y se completan con el puntaje y la decisión. */
@RestController
@RequestMapping("/api/evaluaciones")
public class EvaluacionesController {
    private static final Set<String> DECISIONES = Set.of("ACCEPT", "DECLINE");
    private final JdbcClient db;

    public EvaluacionesController(JdbcClient db) {
        this.db = db;
    }

    public record NuevaEvaluacion(String clave, String processInstanceId, String firma, String dominio,
                                  Integer abogados, Double preScore) {
    }

    public record CambioEvaluacion(String estado, String tier, String decision) {
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Map<String, Object> registrar(@RequestBody NuevaEvaluacion e) {
        if (e.abogados() == null || e.abogados() < 1) {
            throw new IllegalArgumentException("Falta el número de abogados");
        }
        db.sql("""
                INSERT INTO evaluacion (clave, process_instance_id, firma, dominio, abogados, pre_score, estado)
                VALUES (:clave, :pi, :firma, :dominio, :abogados, :pre, 'CUESTIONARIO_PRESENTADO')
                ON CONFLICT DO NOTHING""")
                .param("clave", Validar.texto(e.clave(), "la clave de la evaluación"))
                .param("pi", Validar.texto(e.processInstanceId(), "el id de la instancia"))
                .param("firma", Validar.texto(e.firma(), "la firma"))
                .param("dominio", Validar.texto(e.dominio(), "el dominio"))
                .param("abogados", e.abogados())
                .param("pre", e.preScore() == null ? null : (int) Math.round(e.preScore()))
                .update();
        return uno(e.clave());
    }

    @PutMapping("/{clave}")
    public Map<String, Object> actualizar(@PathVariable String clave, @RequestBody CambioEvaluacion c) {
        if (c.decision() != null && !DECISIONES.contains(c.decision())) {
            throw new IllegalArgumentException("Decisión inválida: " + c.decision());
        }
        int n = db.sql("""
                UPDATE evaluacion SET estado = COALESCE(:estado, estado), tier = COALESCE(:tier, tier),
                       decision = COALESCE(:decision, decision), actualizada_en = CURRENT_TIMESTAMP
                WHERE clave = :clave""")
                .param("estado", c.estado()).param("tier", c.tier()).param("decision", c.decision())
                .param("clave", clave).update();
        if (n == 0) {
            throw new NoSuchElementException("No existe la evaluación " + clave);
        }
        return uno(clave);
    }

    @GetMapping("/{clave}")
    public Map<String, Object> uno(@PathVariable String clave) {
        return Filas.unica(db.sql("SELECT * FROM evaluacion WHERE clave = :clave").param("clave", clave)
                .query().listOfRows(), "No existe la evaluación " + clave);
    }

    @GetMapping
    public List<Map<String, Object>> listar(@RequestParam(required = false) String firma) {
        return Filas.camel(firma == null
                ? db.sql("SELECT * FROM evaluacion ORDER BY creada_en DESC").query().listOfRows()
                : db.sql("SELECT * FROM evaluacion WHERE LOWER(firma) = LOWER(:f) ORDER BY creada_en DESC")
                        .param("f", firma.trim()).query().listOfRows());
    }
}
