-- Cuentas QA para las pruebas visuales (fases 5 y 6). NO es una migracion.
--
-- Vincula cuentas de Firebase ya creadas (Firebase Console > Authentication >
-- Usuarios; correo y contrasena) con usuarios de EcoRuta. Las contrasenas
-- nunca van al repositorio: se reparten por el canal seguro del equipo.
--
-- Uso (el uid se copia de la columna "UID de usuario" en Firebase Console):
--
--   docker exec -i ecoruta-qa-develop-db-1 psql -U ecoruta -d ecoruta \
--     -v uid_conductor='<uid>' -v uid_conductor_sin_ruta='<uid>' \
--     < docs/qa/correcciones-develop/cuentas-qa.sql
--
-- El administrador del panel municipal NO se crea aqui: lo da de alta el
-- backend al arrancar con ECORUTA_ADMIN_FIREBASE_UID y ECORUTA_ADMIN_USUARIO
-- (AdministradorInicial). Ver preparar-entorno-qa.md.
--
-- Es idempotente: correrlo dos veces deja lo mismo.

\set ON_ERROR_STOP on

-- Conductor con la ruta 1 asignada: panel completo y marcar paradas.
INSERT INTO usuarios (username, rol, activo, firebase_uid, ruta_id)
VALUES ('qa-conductor', 'CONDUCTOR', TRUE, :'uid_conductor', 1)
ON CONFLICT (username) DO UPDATE
   SET rol = 'CONDUCTOR', activo = TRUE,
       firebase_uid = EXCLUDED.firebase_uid, ruta_id = 1;

-- Conductor sin ruta: el panel debe decir "No tenes una ruta asignada".
INSERT INTO usuarios (username, rol, activo, firebase_uid, ruta_id)
VALUES ('qa-conductor-sin-ruta', 'CONDUCTOR', TRUE, :'uid_conductor_sin_ruta', NULL)
ON CONFLICT (username) DO UPDATE
   SET rol = 'CONDUCTOR', activo = TRUE,
       firebase_uid = EXCLUDED.firebase_uid, ruta_id = NULL;

SELECT username, rol, activo, firebase_uid, ruta_id
  FROM usuarios
 WHERE username LIKE 'qa-%' OR rol = 'ADMIN'
 ORDER BY username;
