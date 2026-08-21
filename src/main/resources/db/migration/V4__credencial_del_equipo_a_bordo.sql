-- SCRUM-142 (HU-47): el equipo a bordo se autentica con credencial propia.
--
-- El "equipo" es el aparato montado en la cabina que reporta el GPS. Su
-- credencial NO tiene relacion con la tabla usuarios ni con las cuentas de
-- personas (SCRUM-134, que va con Firebase): es una credencial de maquina, sin
-- nombre de usuario, sin recuperacion de contrasena y sin rol de persona. Por
-- eso vive en su propia tabla y no referencia a usuarios.
--
-- Asi la ingesta deja de depender de que un conductor tenga sesion abierta, y
-- revocar un equipo no toca la cuenta de nadie.
CREATE TABLE equipos (
    id             BIGSERIAL PRIMARY KEY,
    -- Parte publica del token. Sirve para BUSCAR la fila (el hash bcrypt lleva
    -- sal, asi que no se puede consultar por el secreto presentado) y es lo
    -- unico del token que se puede escribir en un log.
    codigo_publico VARCHAR(32) NOT NULL,
    -- bcrypt del secreto: 60 caracteres hoy, 72 deja margen si sube el coste.
    -- El secreto en claro no se guarda en ningun lado, se muestra una sola vez.
    secreto_hash   VARCHAR(72) NOT NULL,
    etiqueta       VARCHAR(60) NOT NULL,          -- 'Tableta cabina 1'
    estado         VARCHAR(20) NOT NULL DEFAULT 'ACTIVO',
    creado_en      TIMESTAMPTZ NOT NULL DEFAULT now(),
    revocado_en    TIMESTAMPTZ,
    ultimo_uso_en  TIMESTAMPTZ,
    CONSTRAINT ck_equipo_estado CHECK (estado IN ('ACTIVO', 'REVOCADO')),
    -- Coherencia dura: revocado exige fecha de revocacion, y al reves.
    CONSTRAINT ck_equipo_revocacion CHECK (
        (estado = 'REVOCADO' AND revocado_en IS NOT NULL) OR
        (estado = 'ACTIVO'   AND revocado_en IS NULL))
);

-- Llave de busqueda en CADA peticion de ingesta, y ademas impide que se emitan
-- dos codigos iguales.
CREATE UNIQUE INDEX uq_equipo_codigo_publico ON equipos (codigo_publico);

-- Queda registro de que aparato mando cada lectura. Nullable: las filas
-- anteriores a esta migracion no tienen equipo y no hay forma honesta de
-- adivinarlo. De aqui en adelante la ingesta siempre la llena, porque no acepta
-- una posicion sin credencial de equipo.
ALTER TABLE posiciones_historicas
    ADD COLUMN equipo_id BIGINT REFERENCES equipos(id);

-- NO se siembra ningun equipo, a proposito. Sembrarlo obligaria a escribir su
-- secreto (o su hash, que basta para probar contra el) dentro del repositorio,
-- y Flyway corre igual en produccion: seria una credencial valida publicada en
-- git. Es exactamente lo que esta historia existe para evitar. El equipo de
-- desarrollo se provisiona con POST /api/v1/admin/equipos.
