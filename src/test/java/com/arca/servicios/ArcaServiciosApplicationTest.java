package com.arca.servicios;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;

import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** Arranca el servicio con H2 en modo PostgreSQL, ejecuta schema.sql y data.sql y prueba la API. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "spring.datasource.url=jdbc:h2:mem:negocio;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DEFAULT_NULL_ORDERING=HIGH",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.config.import=",
})
class ArcaServiciosApplicationTest {
    @Autowired
    TestRestTemplate http;

    @SuppressWarnings("unchecked")
    private Map<String, Object> post(String ruta, Object cuerpo) {
        return http.postForObject(ruta, cuerpo, Map.class);
    }

    @Test
    void losDatosDeEjemploQuedanCargados() {
        List<Map<String, Object>> firmas = http.exchange("/api/firmas", HttpMethod.GET, null,
                new ParameterizedTypeReference<List<Map<String, Object>>>() { }).getBody();
        assertThat(firmas).extracting(f -> f.get("nombre")).contains("Smith & Lee LLP", "Garcia Legal Group", "Rivera & Cole LLP");
    }

    @Test
    void laVigenciaDependeDeLaFechaDelHechoYDeLaFirma() {
        String firma = URLEncoder.encode("Smith & Lee LLP", StandardCharsets.UTF_8).replace("+", "%20");
        assertThat(http.getForObject(URI.create("/api/polizas/POL-2026-0042/vigencia?fecha=2026-10-01&firma=" + firma), Map.class)
                .get("vigente")).isEqualTo(true);
        assertThat(http.getForObject("/api/polizas/POL-2026-0042/vigencia?fecha=2026-01-15", Map.class).get("vigente")).isEqualTo(false);
        assertThat(http.getForObject("/api/polizas/POL-2026-0042/vigencia?fecha=2026-10-01&firma=Otra", Map.class).get("vigente")).isEqualTo(false);
        assertThat(http.getForObject("/api/polizas/NO-EXISTE/vigencia?fecha=2026-10-01", Map.class).get("existe")).isEqualTo(false);
    }

    @Test
    void elAvisoQuedaRegistradoYAbreUnExpedienteUnico() {
        post("/api/avisos", Map.of("clave", "AV-T1", "processInstanceId", "pi-1", "firma", "Smith & Lee LLP",
                "numeroPoliza", "POL-2026-0042", "fechaHecho", "2026-10-01", "descripcion", "Prueba"));
        Map<String, Object> primero = post("/api/avisos/AV-T1/expediente", null);
        Map<String, Object> segundo = post("/api/avisos/AV-T1/expediente", null);
        assertThat(primero.get("numeroExpediente")).asString().startsWith("SIN-");
        assertThat(segundo.get("numeroExpediente")).isEqualTo(primero.get("numeroExpediente"));
        assertThat(primero.get("processInstanceId")).isEqualTo("pi-1");
    }

    @Test
    void elScoreEngineCalculaYGuardaLaEvaluacion() {
        Map<String, Object> pre = post("/api/score/pre-score", Map.of("dominio", "riveracole.com", "abogados", 25));
        assertThat(pre.get("preScore")).isEqualTo(48);
        assertThat(pre.get("restriccion")).isEqualTo(true);

        post("/api/evaluaciones", Map.of("clave", "EV-T1", "processInstanceId", "pi-2", "firma", "Rivera & Cole LLP",
                "dominio", "riveracole.com", "abogados", 25, "preScore", 48));
        Map<String, Object> p = post("/api/score/puntaje", Map.of("clave", "EV-T1", "puntajeCapa1", 48, "puntajeCapa2", 100));
        assertThat(p.get("puntaje")).isEqualTo(84);
        Map<String, Object> c = post("/api/score/cotizacion", Map.of("clave", "EV-T1", "abogados", 25, "tier", "FORTRESS"));
        assertThat(c.get("primaAnual")).isEqualTo(8925);
        assertThat(http.getForObject("/api/evaluaciones/EV-T1", Map.class)).containsEntry("estado", "COTIZADA").containsEntry("puntaje", 84);
    }

    @Test
    void laNuevaVersionReemplazaALaVigente() {
        post("/api/versiones", Map.of("clave", "VM-T1", "processInstanceId", "pi-3", "version", "v9.9.9", "motivo", "Prueba"));
        Map<String, Object> r = post("/api/versiones/VM-T1/vigencia", null);
        assertThat(r).containsEntry("estado", "VIGENTE").containsEntry("versionAnterior", "v2.0.0");
    }

    @Test
    void rechazaEntradasInvalidas() {
        assertThat(http.postForEntity("/api/firmas", Map.of("nombre", "X", "dominio", "sin-punto", "email", "a@b.co", "abogados", 3), Map.class)
                .getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(http.postForEntity("/api/firmas", Map.of("nombre", "Smith & Lee LLP", "dominio", "otra.com", "email", "x@otra.com", "abogados", 3), Map.class)
                .getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    }
}
