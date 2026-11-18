package com.arca.servicios;

import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/** Registro y consulta de las firmas de abogados y de sus pólizas. */
@RestController
@RequestMapping("/api/firmas")
public class FirmasController {
    private static final String CAMPOS =
            "id, nombre, dominio, email, abogados, estado_ejercicio, pre_score, restriccion_disciplinaria, creada_en";
    private final JdbcClient db;

    public FirmasController(JdbcClient db) {
        this.db = db;
    }

    public record NuevaFirma(String nombre, String dominio, String email, Integer abogados) {
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Map<String, Object> registrar(@RequestBody NuevaFirma f) {
        Validar.texto(f.nombre(), "el nombre de la firma");
        Validar.dominio(f.dominio());
        Validar.email(f.email());
        if (f.abogados() == null || f.abogados() < 1 || f.abogados() > 5000) {
            throw new IllegalArgumentException("El número de abogados debe estar entre 1 y 5000");
        }
        db.sql("INSERT INTO firma (nombre, dominio, email, abogados) VALUES (:nombre, :dominio, :email, :abogados)")
                .param("nombre", f.nombre().trim())
                .param("dominio", f.dominio().trim().toLowerCase())
                .param("email", f.email().trim().toLowerCase())
                .param("abogados", f.abogados())
                .update();
        return porEmail(f.email().trim().toLowerCase());
    }

    /** Búsqueda por email (ingreso al portal), por dominio (Score Engine) o por nombre. */
    @GetMapping
    public List<Map<String, Object>> buscar(@RequestParam(required = false) String email,
                                            @RequestParam(required = false) String dominio,
                                            @RequestParam(required = false) String nombre) {
        if (email != null) {
            return Filas.camel(db.sql("SELECT " + CAMPOS + " FROM firma WHERE email = :v").param("v", email.trim().toLowerCase()).query().listOfRows());
        }
        if (dominio != null) {
            return Filas.camel(db.sql("SELECT " + CAMPOS + " FROM firma WHERE dominio = :v").param("v", dominio.trim().toLowerCase()).query().listOfRows());
        }
        if (nombre != null) {
            return Filas.camel(db.sql("SELECT " + CAMPOS + " FROM firma WHERE LOWER(nombre) = LOWER(:v)").param("v", nombre.trim()).query().listOfRows());
        }
        return Filas.camel(db.sql("SELECT " + CAMPOS + " FROM firma ORDER BY nombre").query().listOfRows());
    }

    @GetMapping("/{id}/polizas")
    public List<Map<String, Object>> polizas(@PathVariable long id) {
        return Filas.camel(db.sql("""
                SELECT numero, inicio_vigencia, fin_vigencia, limite_cobertura, deducible, exclusiones
                FROM poliza WHERE firma_id = :id ORDER BY inicio_vigencia DESC""").param("id", id).query().listOfRows());
    }

    private Map<String, Object> porEmail(String email) {
        return Filas.unica(db.sql("SELECT " + CAMPOS + " FROM firma WHERE email = :v").param("v", email).query().listOfRows(),
                "No existe la firma " + email);
    }
}
