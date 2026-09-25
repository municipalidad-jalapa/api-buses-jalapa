-- Datos reales de las rutas de Jalapa (SCRUM-136 / HU-41).
--
-- Sustituye la ruta principal aproximada de V6 y la secundaria de prueba de V12
-- por el levantamiento GPS del 13 de septiembre de 2026.
--
-- RUTA PRINCIPAL: circuito completo con 8 paradas fisicas. El punto inicial se
-- repite solo en el LineString para cerrar el circuito; no hay una novena parada.
--
-- RUTA SECUNDARIA: levantamiento PARCIAL. Se carga unicamente el tramo GPS
-- disponible (6 paradas). No se inventa el regreso ni se cierra artificialmente.
--
-- Coordenadas WKT y ST_MakePoint usan (longitud, latitud) — orden PostGIS/JTS,
-- nunca (latitud, longitud).
--
-- Los vehiculos conservan su identificador (BUS-01, BUS-02): el resto del
-- sistema, el simulador y las pruebas los buscan por ese nombre. Solo cambian
-- placa, ruta y estado.
--
-- Originalmente V13; renumerada a V26 porque develop ya ocupa V1-V23 y las
-- PRs de ETA (V24/V25) van antes. Si QA ya no tiene la ruta o el bus
-- esperado (por ejemplo, se edito desde el panel), la migracion avisa con
-- NOTICE y no toca nada, en vez de impedir que el backend arranque.
--
-- MIBUS-001 y MIBUS-002 son placas PROVISIONALES: el esquema exige placa NOT NULL
-- UNIQUE y aun no hay placas oficiales asignadas.
--
-- Actualiza en sitio (sin DELETE/TRUNCATE de rutas, paradas principales ni
-- vehiculos) para preservar IDs referenciados por reservas, equipos y telemetria.

DO $$
DECLARE
    v_principal_id   BIGINT;
    v_secundaria_id  BIGINT;
    v_cnt            INT;
BEGIN
    -- ------------------------------------------------------------------
    -- Localizar rutas por nombre anterior (no asumir IDs fijos)
    -- ------------------------------------------------------------------
    SELECT COUNT(*) INTO v_cnt
      FROM rutas
     WHERE nombre = 'Ruta de ejemplo - Centro de Jalapa';
    IF v_cnt <> 1 THEN
        RAISE NOTICE
            'SCRUM-136: se esperaba exactamente 1 ruta ''Ruta de ejemplo - Centro de Jalapa'', se encontraron %. No se cargan los datos reales.',
            v_cnt;
        RETURN;
    END IF;
    SELECT id INTO v_principal_id
      FROM rutas
     WHERE nombre = 'Ruta de ejemplo - Centro de Jalapa';

    SELECT COUNT(*) INTO v_cnt
      FROM rutas
     WHERE nombre = 'Ruta de prueba - Parque Central a Metroplaza';
    IF v_cnt <> 1 THEN
        RAISE NOTICE
            'SCRUM-136: se esperaba exactamente 1 ruta ''Ruta de prueba - Parque Central a Metroplaza'', se encontraron %. No se cargan los datos reales.',
            v_cnt;
        RETURN;
    END IF;
    SELECT id INTO v_secundaria_id
      FROM rutas
     WHERE nombre = 'Ruta de prueba - Parque Central a Metroplaza';

    -- ------------------------------------------------------------------
    -- RUTA PRINCIPAL — circuito GPS del 13-sep-2026
    -- ------------------------------------------------------------------
    UPDATE rutas
       SET nombre = 'RUTA PRINCIPAL',
           activa = TRUE,
           trazado = ST_GeomFromText(
               'LINESTRING(
                 -89.9811330 14.6349220,
                 -89.9811167 14.6350883,
                 -89.9816300 14.6348550,
                 -89.9819800 14.6347250,
                 -89.9837350 14.6339950,
                 -89.9851750 14.6333867,
                 -89.9861840 14.6329774,
                 -89.9882867 14.6320917,
                 -89.9895233 14.6315800,
                 -89.9916383 14.6309533,
                 -89.9947267 14.6300883,
                 -89.9976950 14.6292800,
                 -89.9991834 14.6288663,
                 -90.0012283 14.6283633,
                 -90.0026033 14.6280217,
                 -90.0032457 14.6277477,
                 -90.0027767 14.6279233,
                 -90.0012667 14.6280900,
                 -89.9989383 14.6285017,
                 -89.9969000 14.6288967,
                 -89.9940850 14.6294717,
                 -89.9928783 14.6296933,
                 -89.9904783 14.6301583,
                 -89.9886404 14.6305575,
                 -89.9873910 14.6309884,
                 -89.9865407 14.6315299,
                 -89.9849017 14.6322217,
                 -89.9826200 14.6332817,
                 -89.9822873 14.6334181,
                 -89.9807833 14.6343783,
                 -89.9811167 14.6350883,
                 -89.9811330 14.6349220
               )',
               4326)
     WHERE id = v_principal_id;

    -- Ocho paradas fisicas (orden 1..8). Coordenadas de campo: lat,lon invertidas
    -- a ST_MakePoint(longitud, latitud).
    UPDATE paradas
       SET nombre = 'Parada 1',
           ubicacion = ST_SetSRID(ST_MakePoint(-89.981133, 14.634922), 4326)
     WHERE ruta_id = v_principal_id AND orden = 1;

    UPDATE paradas
       SET nombre = 'Parada 2',
           ubicacion = ST_SetSRID(ST_MakePoint(-89.986805, 14.632649), 4326)
     WHERE ruta_id = v_principal_id AND orden = 2;

    UPDATE paradas
       SET nombre = 'Parada 3',
           ubicacion = ST_SetSRID(ST_MakePoint(-89.992877, 14.630572), 4326)
     WHERE ruta_id = v_principal_id AND orden = 3;

    UPDATE paradas
       SET nombre = 'Parada 4',
           ubicacion = ST_SetSRID(ST_MakePoint(-89.996203, 14.629705), 4326)
     WHERE ruta_id = v_principal_id AND orden = 4;

    UPDATE paradas
       SET nombre = 'Parada 5',
           ubicacion = ST_SetSRID(ST_MakePoint(-90.003096, 14.627899), 4326)
     WHERE ruta_id = v_principal_id AND orden = 5;

    UPDATE paradas
       SET nombre = 'Parada 6',
           ubicacion = ST_SetSRID(ST_MakePoint(-89.996073, 14.629016), 4326)
     WHERE ruta_id = v_principal_id AND orden = 6;

    UPDATE paradas
       SET nombre = 'Parada 7',
           ubicacion = ST_SetSRID(ST_MakePoint(-89.992588, 14.629725), 4326)
     WHERE ruta_id = v_principal_id AND orden = 7;

    UPDATE paradas
       SET nombre = 'Parada 8',
           ubicacion = ST_SetSRID(ST_MakePoint(-89.987091, 14.631173), 4326)
     WHERE ruta_id = v_principal_id AND orden = 8;

    -- ------------------------------------------------------------------
    -- RUTA SECUNDARIA — tramo PARCIAL (datos disponibles, sin inventar)
    -- ------------------------------------------------------------------
    UPDATE rutas
       SET nombre = 'RUTA SECUNDARIA',
           activa = TRUE,
           trazado = ST_GeomFromText(
               'LINESTRING(
                 -89.9811330 14.6349220,
                 -89.9802867 14.6350983,
                 -89.9802917 14.6346867,
                 -89.9811317 14.6341150,
                 -89.9824767 14.6333333,
                 -89.9829033 14.6331117,
                 -89.9844700 14.6324183,
                 -89.9853583 14.6319933,
                 -89.9862117 14.6316033,
                 -89.9872567 14.6311050,
                 -89.9883500 14.6306900,
                 -89.9885400 14.6316767,
                 -89.9886550 14.6322850,
                 -89.9889367 14.6337483,
                 -89.9891533 14.6346283,
                 -89.9892417 14.6350683,
                 -89.9894883 14.6359633,
                 -89.9897967 14.6370450,
                 -89.9906300 14.6391383,
                 -89.9914183 14.6403750,
                 -89.9914817 14.6404433,
                 -89.9920183 14.6412233,
                 -89.9929500 14.6428883,
                 -89.9939517 14.6445283,
                 -89.9952783 14.6462617,
                 -89.9963917 14.6474617,
                 -89.9976250 14.6485800,
                 -89.9985600 14.6494017,
                 -89.9994133 14.6508533,
                 -89.9998417 14.6521450,
                 -89.9996050 14.6544367,
                 -89.9998650 14.6555017,
                 -89.9998667 14.6555333,
                 -90.0001367 14.6568933,
                 -89.9999317 14.6584217,
                 -90.0002533 14.6587700,
                 -90.0007567 14.6587767,
                 -90.0011740 14.6589126
               )',
               4326)
     WHERE id = v_secundaria_id;

    -- Cinco paradas ya existentes (V12): actualizar en sitio por (ruta_id, orden).
    UPDATE paradas
       SET nombre = 'Parada 1',
           ubicacion = ST_SetSRID(ST_MakePoint(-89.981133, 14.634922), 4326)
     WHERE ruta_id = v_secundaria_id AND orden = 1;

    UPDATE paradas
       SET nombre = 'Parada 2',
           ubicacion = ST_SetSRID(ST_MakePoint(-89.988729, 14.632317), 4326)
     WHERE ruta_id = v_secundaria_id AND orden = 2;

    UPDATE paradas
       SET nombre = 'Parada 3',
           ubicacion = ST_SetSRID(ST_MakePoint(-89.989157, 14.634647), 4326)
     WHERE ruta_id = v_secundaria_id AND orden = 3;

    UPDATE paradas
       SET nombre = 'Parada 4',
           ubicacion = ST_SetSRID(ST_MakePoint(-89.991095, 14.639865), 4326)
     WHERE ruta_id = v_secundaria_id AND orden = 4;

    UPDATE paradas
       SET nombre = 'Parada 5',
           ubicacion = ST_SetSRID(ST_MakePoint(-89.999791, 14.651840), 4326)
     WHERE ruta_id = v_secundaria_id AND orden = 5;

    -- Sexta parada: insertar solo si aun no existe (idempotente ante reintentos).
    INSERT INTO paradas (nombre, ubicacion, orden, ruta_id)
    VALUES (
        'Parada 6',
        ST_SetSRID(ST_MakePoint(-90.000046, 14.658814), 4326),
        6,
        v_secundaria_id
    )
    ON CONFLICT (ruta_id, orden) DO UPDATE
        SET nombre = EXCLUDED.nombre,
            ubicacion = EXCLUDED.ubicacion;

    -- ------------------------------------------------------------------
    -- Vehiculos: placas provisionales. Conserva IDs e identificadores
    -- (BUS-01 -> principal, BUS-02 -> secundaria).
    -- ------------------------------------------------------------------
    UPDATE vehiculos
       SET placa = 'MIBUS-001',
           ruta_id = v_principal_id,
           activo = TRUE
     WHERE identificador = 'BUS-01';
    IF NOT FOUND THEN
        RAISE NOTICE 'SCRUM-136: no existe el vehiculo BUS-01; no se le asigna placa ni ruta.';
    END IF;

    UPDATE vehiculos
       SET placa = 'MIBUS-002',
           ruta_id = v_secundaria_id,
           activo = TRUE
     WHERE identificador = 'BUS-02';
    IF NOT FOUND THEN
        RAISE NOTICE 'SCRUM-136: no existe el vehiculo BUS-02; no se le asigna placa ni ruta.';
    END IF;
END $$;
