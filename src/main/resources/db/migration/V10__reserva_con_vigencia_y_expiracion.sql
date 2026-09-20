-- HU-135: la tarea programada expira las reservas vencidas.
--
-- Indice parcial para que el barrido encuentre rapido las vigentes que ya
-- vencieron: solo indexa las filas que todavia puede tocar.
--
-- Al integrar el sprint 5 se quitaron de aqui tres cosas:
--
--  - la normalizacion ACTIVO -> ACTIVA y el valor por defecto del estado, que
--    V7 ya hace. V7 deja en ACTIVO las filas que chocarian con el indice unico;
--    repetir aqui el UPDATE sin esa guarda las convertiria a ciegas y la
--    migracion fallaria en cualquier base con reservas duplicadas.
--
--  - el reemplazo del indice unico por uno que tambien contaba ABORDO. La tarea
--    programada solo expira ACTIVA y RENOVADA, asi que una reserva en ABORDO no
--    vence nunca: el pasajero que subio una vez no podria volver a reservar.
--    Se conserva el indice de V7, que cuenta solo ACTIVA y RENOVADA.

CREATE INDEX idx_reserva_por_vencer
    ON registros_espera (expira_en)
    WHERE estado IN ('ACTIVA', 'RENOVADA');
