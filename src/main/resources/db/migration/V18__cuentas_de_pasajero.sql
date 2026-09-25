-- SCRUM-26 (HU Desarrollo-146), bloque B: sesion opcional del pasajero.
--
-- La cuenta vive en Firebase (inicio con Google); aqui solo se guarda el
-- enlace. El pasajero no es un usuario del sistema (tabla usuarios): no tiene
-- rol de operacion ni contrasena.
CREATE TABLE pasajeros (
    id            BIGSERIAL PRIMARY KEY,
    firebase_uid  VARCHAR(128) NOT NULL UNIQUE,
    correo        VARCHAR(254),
    creado_en     TIMESTAMPTZ  NOT NULL DEFAULT now()
);

-- La cuenta es OPCIONAL en la reserva y en la opinion: el uso anonimo (solo
-- dispositivo_id) sigue funcionando igual. Se llena al vincular el navegador
-- con la cuenta.
ALTER TABLE registros_espera
    ADD COLUMN pasajero_id BIGINT REFERENCES pasajeros(id);

ALTER TABLE opiniones
    ADD COLUMN pasajero_id BIGINT REFERENCES pasajeros(id);

CREATE INDEX idx_reserva_pasajero ON registros_espera (pasajero_id) WHERE pasajero_id IS NOT NULL;
CREATE INDEX idx_opinion_pasajero ON opiniones (pasajero_id) WHERE pasajero_id IS NOT NULL;
