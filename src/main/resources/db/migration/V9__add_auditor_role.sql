ALTER TABLE app_user
    DROP CONSTRAINT ck_app_user_role;

ALTER TABLE app_user
    ADD CONSTRAINT ck_app_user_role CHECK (role IN ('USER', 'ADMIN', 'AUDITOR'));
