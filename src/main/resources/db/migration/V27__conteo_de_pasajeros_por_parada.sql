-- Panel del conductor en ruta: al cerrar una parada el piloto registra
-- cuantos subieron y cuantos bajaron, contando tambien a quien no aviso por
-- la app. Con eso el panel sabe cuantos van a bordo.
--
-- Las paradas atendidas antes de esta migracion quedan en 0: no hay dato.

ALTER TABLE paradas_atendidas
    ADD COLUMN IF NOT EXISTS subieron INT NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS bajaron  INT NOT NULL DEFAULT 0;

ALTER TABLE paradas_atendidas
    ADD CONSTRAINT ck_paradas_atendidas_conteos
        CHECK (subieron >= 0 AND bajaron >= 0);
