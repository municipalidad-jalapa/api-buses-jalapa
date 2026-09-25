-- SCRUM-26 (HU Desarrollo-146), bloque C: trazado del desvio por calles.
--
-- Cuando el bus se sale del trazado, el ETA ya no estima la vuelta como una
-- linea recta por un factor: la calcula por el camino mas corto sobre la red
-- de calles, que vive aqui. La red se importa de OpenStreetMap una sola vez
-- (V20, generada con herramientas/importar_calles_osm.py); en ejecucion no se
-- consume ningun servicio externo.
CREATE EXTENSION IF NOT EXISTS pgrouting;

-- Un nodo es un cruce o el extremo de una calle. pgr_dijkstra enruta de nodo a
-- nodo; el punto sirve para enganchar el bus y los candidatos de
-- reincorporacion al nodo mas cercano.
CREATE TABLE calles_nodos (
    id    BIGSERIAL PRIMARY KEY,
    punto geometry(Point, 4326) NOT NULL
);

CREATE INDEX idx_calles_nodos_punto ON calles_nodos USING GIST (punto);

-- Una arista va de un cruce al siguiente, sin pasar por un tercero.
--
-- costo es la longitud en metros en el sentido de la via. costo_reverso vale
-- -1 en las de un solo sentido: pgRouting entiende ese -1 como "por aqui no se
-- puede al reves", que es como se respeta el sentido segun OSM.
CREATE TABLE calles (
    id            BIGSERIAL PRIMARY KEY,
    osm_id        BIGINT NOT NULL,
    nombre        TEXT,
    tipo          VARCHAR(30) NOT NULL,
    sentido_unico BOOLEAN NOT NULL DEFAULT false,
    origen        BIGINT NOT NULL REFERENCES calles_nodos(id),
    destino       BIGINT NOT NULL REFERENCES calles_nodos(id),
    costo         DOUBLE PRECISION NOT NULL,
    costo_reverso DOUBLE PRECISION NOT NULL,
    trazo         geometry(LineString, 4326) NOT NULL
);

CREATE INDEX idx_calles_trazo ON calles USING GIST (trazo);
CREATE INDEX idx_calles_origen ON calles (origen);
CREATE INDEX idx_calles_destino ON calles (destino);
