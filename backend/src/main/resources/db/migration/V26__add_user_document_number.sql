ALTER TABLE users
    ADD COLUMN document_number VARCHAR(11),
    ADD CONSTRAINT chk_users_document_number CHECK (
        document_number IS NULL OR document_number ~ '^[0-9]{7,11}$'
    );
