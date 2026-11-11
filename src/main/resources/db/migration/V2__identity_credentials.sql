ALTER TABLE app_user ADD COLUMN email_normalized VARCHAR(320);
ALTER TABLE app_user ADD COLUMN password_hash VARCHAR(100);
ALTER TABLE app_user ADD COLUMN enabled BOOLEAN NOT NULL DEFAULT TRUE;

UPDATE app_user
SET email_normalized = LOWER(BTRIM(email));

ALTER TABLE app_user ALTER COLUMN email_normalized SET NOT NULL;

ALTER TABLE app_user
    ADD CONSTRAINT uq_app_user_email_normalized UNIQUE (email_normalized);

ALTER TABLE app_user
    ADD CONSTRAINT ck_app_user_email_normalized_canonical
        CHECK (email_normalized = LOWER(BTRIM(email_normalized)));
