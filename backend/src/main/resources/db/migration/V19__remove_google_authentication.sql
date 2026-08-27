DROP TABLE google_auth_nonces;
DROP TABLE user_external_identities;

ALTER TABLE users
    ALTER COLUMN password_hash SET NOT NULL;
