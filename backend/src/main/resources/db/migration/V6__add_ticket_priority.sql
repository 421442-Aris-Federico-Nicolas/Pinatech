ALTER TABLE technical_service_tickets ADD COLUMN priority VARCHAR(20) NOT NULL DEFAULT 'NORMAL';
ALTER TABLE technical_service_tickets ADD CONSTRAINT chk_technical_service_tickets_priority CHECK (priority IN ('LOW', 'NORMAL', 'HIGH', 'URGENT'));
