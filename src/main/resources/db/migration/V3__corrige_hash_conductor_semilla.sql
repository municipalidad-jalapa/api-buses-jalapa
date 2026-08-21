-- El hash sembrado en V2 no correspondia a ninguna contrasena: era un bcrypt
-- copiado a medias, asi que POST /api/v1/auth/login fallaba siempre y ningun
-- flujo de conductor (ingesta GPS) se podia probar.
--
-- V2 no se edita: ya esta aplicada y cambiarla rompe el checksum de Flyway.
--
-- Hash valido de "conductor123" (bcrypt, coste 10). Solo para desarrollo:
-- en produccion el usuario se crea aparte y esta fila no se siembra.
UPDATE usuarios
   SET password_hash = '$2b$10$LkH.fOZyY7g7VmxT.2/7Vu5Lksd9ojiYPfeg38FQ0lgAuaDTUvOjS'
 WHERE username = 'conductor1';
