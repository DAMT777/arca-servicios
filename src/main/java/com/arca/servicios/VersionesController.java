package com.arca.servicios;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;

/** Registro de versiones del modelo de score para G3: backtesting, estado y vigencia. */
@RestController
@RequestMapping("/api/versiones")
public class VersionesController {
    private final JdbcClient db;

    public VersionesController(JdbcClient db) {
        this.db = db;
    }

    public record Version(String clave, String processInstanceId, String version, String motivo) {
    }

    public record Backtesting(String escenario) {
    }

    public record CambioVersion(String estado) {
    }

    /** Registra la versión candidata; si la calibración la cambia, actualiza el identificador. */
    @PostMapping
    @Transactional
    public Map<String, Object> registrar(@RequestBody Version v) {
        String clave = Validar.texto(v.clave(), "la clave de la solicitud");
        String version = Validar.texto(v.version(), "el identificador de la versión");
        int n = db.sql("UPDATE version_modelo SET version = :v, estado = 'EN_CALIBRACION', actualizada_en = CURRENT_TIMESTAMP WHERE clave = :c")
                .param("v", version).param("c", clave).update();
        if (n == 0) {
            db.sql("""
                    INSERT INTO version_modelo (clave, process_instance_id, version, motivo, estado)
                    VALUES (:c, :pi, :v, :m, 'EN_CALIBRACION')""")
                    .param("c", clave).param("pi", v.processInstanceId()).param("v", version).param("m", v.motivo()).update();
        }
        return uno(clave);
    }

    /** Backtesting y prueba de sesgo simulados; el escenario «falla» produce métricas que no pasan la DMN. */
    @PostMapping("/{clave}/backtesting")
    public Map<String, Object> backtesting(@PathVariable String clave, @RequestBody Backtesting b) {
        boolean falla = "falla".equals(b.escenario());
        double auc = falla ? 0.64 : 0.78;
        double desviacion = falla ? 0.18 : 0.04;
        double impacto = falla ? 0.72 : 0.91;
        int n = db.sql("""
                UPDATE version_modelo SET auc = :a, desviacion_loss_ratio = :d, impacto_dispar = :i, estado = 'EN_VALIDACION',
                       actualizada_en = CURRENT_TIMESTAMP WHERE clave = :c""")
                .param("a", auc).param("d", desviacion).param("i", impacto).param("c", clave).update();
        if (n == 0) {
            throw new NoSuchElementException("No existe la versión " + clave);
        }
        return Map.of("auc", auc, "desviacionLossRatio", desviacion, "impactoDisparejo", impacto);
    }

    @PutMapping("/{clave}")
    public Map<String, Object> actualizar(@PathVariable String clave, @RequestBody CambioVersion c) {
        int n = db.sql("UPDATE version_modelo SET estado = :e, actualizada_en = CURRENT_TIMESTAMP WHERE clave = :c")
                .param("e", Validar.texto(c.estado(), "el estado")).param("c", clave).update();
        if (n == 0) {
            throw new NoSuchElementException("No existe la versión " + clave);
        }
        return uno(clave);
    }

    /** Publica la versión: la anterior vigente pasa a REEMPLAZADA. */
    @PostMapping("/{clave}/vigencia")
    @Transactional
    public Map<String, Object> vigencia(@PathVariable String clave) {
        uno(clave);
        List<String> anteriores = db.sql("SELECT version FROM version_modelo WHERE estado = 'VIGENTE' AND clave <> :c")
                .param("c", clave).query(String.class).list();
        db.sql("UPDATE version_modelo SET estado = 'REEMPLAZADA', actualizada_en = CURRENT_TIMESTAMP WHERE estado = 'VIGENTE' AND clave <> :c")
                .param("c", clave).update();
        db.sql("UPDATE version_modelo SET estado = 'VIGENTE', vigente_desde = CURRENT_TIMESTAMP, actualizada_en = CURRENT_TIMESTAMP WHERE clave = :c")
                .param("c", clave).update();
        Map<String, Object> r = new LinkedHashMap<>(uno(clave));
        r.put("versionAnterior", anteriores.isEmpty() ? null : anteriores.get(0));
        return r;
    }

    @GetMapping("/{clave}")
    public Map<String, Object> uno(@PathVariable String clave) {
        return Filas.unica(db.sql("SELECT * FROM version_modelo WHERE clave = :c").param("c", clave).query().listOfRows(),
                "No existe la versión " + clave);
    }

    @GetMapping
    public List<Map<String, Object>> listar() {
        return Filas.camel(db.sql("SELECT * FROM version_modelo ORDER BY creada_en DESC").query().listOfRows());
    }
}
