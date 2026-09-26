-- SCRUM-26 (HU Desarrollo-146), bloque D: modelo de roles y permisos.
--
-- El sistema tiene cuatro roles. Tres son cuentas de operacion y viven en
-- usuarios.rol:
--
--   CONDUCTOR   el piloto del bus, acotado a la ruta de usuarios.ruta_id
--   ADMIN       la municipalidad: mira el panel, no administra cuentas
--   SUPERADMIN  crea, edita y desactiva cuentas, y administra rutas,
--               paradas y vehiculos
--
-- El cuarto rol, el pasajero, no es una cuenta de operacion: vive en la tabla
-- pasajeros (bloque B) y su rol viaja en su propio JWT. Por eso no aparece aqui.

-- Cuenta inicial de SuperAdmin.
--
-- Nace SIN uid de Firebase a proposito: la contrasena y la identidad viven en
-- Firebase, y el uid de cada entorno no puede quedar en el repositorio. Sin uid
-- la cuenta no puede iniciar sesion. Al desplegar se define
-- ECORUTA_SUPERADMIN_FIREBASE_UID y el arranque la enlaza (SuperAdminInicial).
INSERT INTO usuarios (username, rol, activo)
SELECT 'superadmin', 'SUPERADMIN', TRUE
 WHERE NOT EXISTS (SELECT 1 FROM usuarios WHERE rol = 'SUPERADMIN');

-- Una cuenta desactivada no entra a ningun lado; el filtro de permisos la
-- rechaza igual, pero el indice deja claro por donde se consulta.
CREATE INDEX IF NOT EXISTS idx_usuarios_rol ON usuarios (rol) WHERE activo;
