package com.arca.servicios;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Servicios de negocio de ARCA: registro de firmas y pólizas, avisos de siniestro, evaluaciones,
 * versiones del modelo y el Score Engine. El motor BPM (arca-bpm-engine) los invoca por REST
 * desde las tareas de servicio de C10, C3 y G3.
 */
@SpringBootApplication
public class ArcaServiciosApplication {
    public static void main(String[] args) {
        SpringApplication.run(ArcaServiciosApplication.class, args);
    }
}
