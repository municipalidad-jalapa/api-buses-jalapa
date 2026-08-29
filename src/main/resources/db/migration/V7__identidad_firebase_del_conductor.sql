-- HU-Desarrollo-63: el conductor se autentica con Firebase, no con password.
-- firebase_uid enlaza la cuenta local (rol CONDUCTOR) con el uid del idToken.
-- password_hash deja de ser obligatorio: la identidad ya no vive en esa columna.
ALTER TABLE usuarios
    ADD COLUMN firebase_uid VARCHAR(128) UNIQUE;

ALTER TABLE usuarios
    ALTER COLUMN password_hash DROP NOT NULL;
