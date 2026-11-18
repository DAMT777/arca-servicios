package com.arca.servicios;

import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.time.Year;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Avisos de siniestro de C10: registro, revisión, apertura del expediente y cierre. */
@RestController
@RequestMapping("/api/avisos")
public class AvisosController {
    private static final Set<String> RESULTADOS = Set.of("CUBIERTO", "RESERVA", "SIN_COBERTURA");
    private final JdbcClient db;

    public AvisosController(JdbcClient db) {
        this.db = db;
    }

    public record NuevoAviso(String clave, String processInstanceId, String firma, String numeroPoliza,
                             String fechaHecho, String descripcion) {
    }

    public record CambioAviso(String estado, Double montoReclamado, String resultado, String fundamento) {
    }

    /** Lo invoca la tarea «Enviar el acuse de recibo»: el aviso queda registrado con su instancia. */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Map<String, Object> registrar(@RequestBody NuevoAviso a) {
        db.sql("""
                INSERT INTO aviso_siniestro (clave, process_instance_id, firma, numero_poliza, fecha_hecho, descripcion, estado)
                VALUES (:clave, :pi, :firma, :poliza, :fecha, :descripcion, 'RECIBIDO')
                ON CONFLICT DO NOTHING""")
                .param("clave", Validar.texto(a.clave(), "la clave del aviso"))
                .param("pi", Validar.texto(a.processInstanceId(), "el id de la instancia"))
                .param("firma", Validar.texto(a.firma(), "la firma"))
                .param("poliza", Validar.texto(a.numeroPoliza(), "el número de póliza").toUpperCase())
                .param("fecha", Validar.fecha(a.fechaHecho(), "la fecha del hecho"))
                .param("descripcion", Validar.texto(a.descripcion(), "la descripción del hecho"))
                .update();
        return uno(a.clave());
    }

    @PutMapping("/{clave}")
    public Map<String, Object> actualizar(@PathVariable String clave, @RequestBody CambioAviso c) {
        if (c.resultado() != null && !RESULTADOS.contains(c.resultado())) {
            throw new IllegalArgumentException("Resultado inválido: " + c.resultado());
        }
        int n = db.sql("""
                UPDATE aviso_siniestro SET
                    estado = COALESCE(:estado, estado),
                    monto_reclamado = COALESCE(:monto, monto_reclamado),
                    resultado = COALESCE(:resultado, resultado),
                    fundamento = COALESCE(:fundamento, fundamento),
                    actualizado_en = CURRENT_TIMESTAMP
                WHERE clave = :clave""")
                .param("estado", c.estado()).param("monto", c.montoReclamado())
                .param("resultado", c.resultado()).param("fundamento", c.fundamento())
                .param("clave", clave).update();
        if (n == 0) {
            throw new java.util.NoSuchElementException("No existe el aviso " + clave);
        }
        return uno(clave);
    }

    /** Abre el expediente con un número consecutivo. Si ya existe, devuelve el mismo. */
    @PostMapping("/{clave}/expediente")
    @Transactional
    public Map<String, Object> abrirExpediente(@PathVariable String clave) {
        Map<String, Object> aviso = uno(clave);
        if (aviso.get("numeroExpediente") == null) {
            long consecutivo = db.sql("SELECT nextval('expediente_seq')").query(Long.class).single();
            db.sql("""
                    UPDATE aviso_siniestro SET numero_expediente = :numero, estado = 'EXPEDIENTE_ABIERTO',
                           actualizado_en = CURRENT_TIMESTAMP WHERE clave = :clave""")
                    .param("numero", String.format("SIN-%d-%05d", Year.now().getValue(), consecutivo))
                    .param("clave", clave).update();
        }
        return uno(clave);
    }

    @GetMapping("/{clave}")
    public Map<String, Object> uno(@PathVariable String clave) {
        return Filas.unica(db.sql("SELECT * FROM aviso_siniestro WHERE clave = :clave").param("clave", clave)
                .query().listOfRows(), "No existe el aviso " + clave);
    }

    @GetMapping
    public List<Map<String, Object>> listar(@RequestParam(required = false) String firma) {
        return Filas.camel(firma == null
                ? db.sql("SELECT * FROM aviso_siniestro ORDER BY creado_en DESC").query().listOfRows()
                : db.sql("SELECT * FROM aviso_siniestro WHERE LOWER(firma) = LOWER(:f) ORDER BY creado_en DESC")
                        .param("f", firma.trim()).query().listOfRows());
    }
}
