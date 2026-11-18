-- Modelo de datos del negocio de ARCA. Cada registro que nace de un proceso guarda la clave de negocio
-- (business key) y el id de su instancia en Camunda, para unir el dato con el proceso que lo produjo.

CREATE TABLE IF NOT EXISTS firma (
    id                        BIGSERIAL PRIMARY KEY,
    nombre                    VARCHAR(160) NOT NULL UNIQUE,
    dominio                   VARCHAR(120) NOT NULL UNIQUE,
    email                     VARCHAR(160) NOT NULL UNIQUE,
    abogados                  INTEGER      NOT NULL CHECK (abogados > 0),
    estado_ejercicio          VARCHAR(2)   NOT NULL DEFAULT 'FL',
    pre_score                 INTEGER      NOT NULL DEFAULT 60 CHECK (pre_score BETWEEN 0 AND 100),
    restriccion_disciplinaria BOOLEAN      NOT NULL DEFAULT FALSE,
    creada_en                 TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS poliza (
    numero           VARCHAR(30)    PRIMARY KEY,
    firma_id         BIGINT         NOT NULL REFERENCES firma (id),
    inicio_vigencia  DATE           NOT NULL,
    fin_vigencia     DATE           NOT NULL,
    limite_cobertura NUMERIC(14, 2) NOT NULL,
    deducible        NUMERIC(14, 2) NOT NULL,
    exclusiones      VARCHAR(400)   NOT NULL,
    CHECK (fin_vigencia > inicio_vigencia)
);

CREATE SEQUENCE IF NOT EXISTS expediente_seq START WITH 1;

CREATE TABLE IF NOT EXISTS aviso_siniestro (
    id                  BIGSERIAL PRIMARY KEY,
    clave               VARCHAR(30)    NOT NULL UNIQUE,
    process_instance_id VARCHAR(64)    NOT NULL,
    firma               VARCHAR(160)   NOT NULL,
    numero_poliza       VARCHAR(30)    NOT NULL,
    fecha_hecho         DATE           NOT NULL,
    descripcion         VARCHAR(2000)  NOT NULL,
    monto_reclamado     NUMERIC(14, 2),
    resultado           VARCHAR(20),
    fundamento          VARCHAR(2000),
    numero_expediente   VARCHAR(30)    UNIQUE,
    estado              VARCHAR(30)    NOT NULL,
    creado_en           TIMESTAMP      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    actualizado_en      TIMESTAMP      NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS evaluacion (
    id                   BIGSERIAL PRIMARY KEY,
    clave                VARCHAR(30)    NOT NULL UNIQUE,
    process_instance_id  VARCHAR(64)    NOT NULL,
    firma                VARCHAR(160)   NOT NULL,
    dominio              VARCHAR(120)   NOT NULL,
    abogados             INTEGER        NOT NULL,
    pre_score            INTEGER,
    puntaje_cuestionario INTEGER,
    puntaje              INTEGER,
    tier                 VARCHAR(12),
    decision             VARCHAR(10),
    prima_anual          NUMERIC(14, 2),
    limite_ofrecido      VARCHAR(40),
    fecha_reevaluacion   DATE,
    estado               VARCHAR(30)    NOT NULL,
    creada_en            TIMESTAMP      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    actualizada_en       TIMESTAMP      NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS version_modelo (
    id                    BIGSERIAL PRIMARY KEY,
    clave                 VARCHAR(30)   NOT NULL UNIQUE,
    process_instance_id   VARCHAR(64),
    version               VARCHAR(40)   NOT NULL,
    motivo                VARCHAR(400),
    auc                   NUMERIC(5, 3),
    desviacion_loss_ratio NUMERIC(5, 3),
    impacto_dispar        NUMERIC(5, 3),
    estado                VARCHAR(30)   NOT NULL,
    vigente_desde         TIMESTAMP,
    creada_en             TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    actualizada_en        TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP
);
