-- Ruta piloto de ejemplo (coordenadas del centro de Jalapa; AJUSTAR con las paradas reales)
INSERT INTO rutas (nombre) VALUES ('Ruta Centro - Terminal');

INSERT INTO paradas (nombre, ubicacion, orden, ruta_id) VALUES
 ('Parque Central',      ST_SetSRID(ST_MakePoint(-89.9890, 14.6330), 4326), 1, 1),
 ('Mercado Municipal',   ST_SetSRID(ST_MakePoint(-89.9870, 14.6345), 4326), 2, 1),
 ('Hospital Nacional',   ST_SetSRID(ST_MakePoint(-89.9840, 14.6365), 4326), 3, 1),
 ('Terminal de Buses',   ST_SetSRID(ST_MakePoint(-89.9810, 14.6390), 4326), 4, 1);

-- Usuario conductor de desarrollo. Password: "conductor123" (bcrypt). CAMBIAR EN PRODUCCION.
INSERT INTO usuarios (username, password_hash, rol) VALUES
 ('conductor1', '$2a$10$N9qo8uLOickgx2ZMRZoMye7VFxJZbP0XCwG3zJ8gDDoFPr8XW3JG6', 'CONDUCTOR');
