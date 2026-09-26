-- SCRUM-26 (HU Desarrollo-146), bloque F: tres valoraciones independientes.
--
-- El bloque A guardaba una sola calificacion. El refinamiento de PMO pide tres,
-- que la gente puntua por separado porque miden cosas distintas: el servicio
-- puede ser puntual con la unidad sucia, o la unidad impecable y el piloto
-- manejando mal.
--
-- Las tres son OPCIONALES: el formulario corto de una sola estrella sigue
-- funcionando igual, y las opiniones ya guardadas no se tocan.
ALTER TABLE opiniones
    ADD COLUMN calidad    INTEGER CHECK (calidad    BETWEEN 1 AND 5),
    ADD COLUMN limpieza   INTEGER CHECK (limpieza   BETWEEN 1 AND 5),
    ADD COLUMN conduccion INTEGER CHECK (conduccion BETWEEN 1 AND 5);

COMMENT ON COLUMN opiniones.calidad    IS 'Calidad del servicio, de 1 a 5';
COMMENT ON COLUMN opiniones.limpieza   IS 'Limpieza de la unidad, de 1 a 5';
COMMENT ON COLUMN opiniones.conduccion IS 'Conduccion prudente del piloto, de 1 a 5';

-- Una opinion sigue necesitando contenido, pero ahora cualquiera de las tres
-- valoraciones nuevas tambien cuenta como contenido.
ALTER TABLE opiniones DROP CONSTRAINT ck_opinion_con_contenido;

ALTER TABLE opiniones
    ADD CONSTRAINT ck_opinion_con_contenido CHECK (
        texto      IS NOT NULL
     OR estrellas  IS NOT NULL
     OR calidad    IS NOT NULL
     OR limpieza   IS NOT NULL
     OR conduccion IS NOT NULL
    );
