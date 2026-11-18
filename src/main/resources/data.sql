-- Datos de ejemplo reproducibles. ON CONFLICT DO NOTHING evita duplicarlos en cada arranque.

INSERT INTO firma (nombre, dominio, email, abogados, estado_ejercicio, pre_score, restriccion_disciplinaria) VALUES
    ('Smith & Lee LLP', 'smithlee.com', 'riesgos@smithlee.com', 12, 'FL', 60, FALSE),
    ('Garcia Legal Group', 'garcialegal.com', 'socios@garcialegal.com', 5, 'FL', 74, FALSE),
    ('Rivera & Cole LLP', 'riveracole.com', 'operaciones@riveracole.com', 25, 'FL', 48, TRUE)
ON CONFLICT DO NOTHING;

INSERT INTO poliza (numero, firma_id, inicio_vigencia, fin_vigencia, limite_cobertura, deducible, exclusiones)
SELECT 'POL-2026-0042', id, DATE '2026-03-01', DATE '2027-03-01', 1000000, 10000,
       'Uso de IA sin supervisión humana; actos dolosos; multas y sanciones'
FROM firma WHERE dominio = 'smithlee.com'
ON CONFLICT DO NOTHING;

INSERT INTO poliza (numero, firma_id, inicio_vigencia, fin_vigencia, limite_cobertura, deducible, exclusiones)
SELECT 'POL-2026-0107', id, DATE '2026-06-01', DATE '2027-06-01', 500000, 5000,
       'Uso de IA sin supervisión humana; actos dolosos; reclamos anteriores a la vigencia'
FROM firma WHERE dominio = 'garcialegal.com'
ON CONFLICT DO NOTHING;

INSERT INTO poliza (numero, firma_id, inicio_vigencia, fin_vigencia, limite_cobertura, deducible, exclusiones)
SELECT 'POL-2025-0310', id, DATE '2025-01-01', DATE '2026-01-01', 2000000, 25000,
       'Uso de IA sin supervisión humana; actos dolosos; multas y sanciones'
FROM firma WHERE dominio = 'riveracole.com'
ON CONFLICT DO NOTHING;

INSERT INTO version_modelo (clave, version, motivo, estado, vigente_desde) VALUES
    ('VM-INICIAL', 'v2.0.0', 'Versión de lanzamiento del modelo de score', 'VIGENTE', TIMESTAMP '2026-01-15 00:00:00')
ON CONFLICT DO NOTHING;
