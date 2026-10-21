-- Antes era um segundo V15 (conflitava com V15__remove_school_id_prop e o Flyway nao subia).
-- Idempotente para bancos que ja tinham aplicado essa migration como V15.
ALTER TABLE student_stops
    ADD COLUMN IF NOT EXISTS status TEXT DEFAULT 'pending',
    ADD COLUMN IF NOT EXISTS route_id UUID NULL;

DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'fk_student_stops_route') THEN
        ALTER TABLE student_stops
            ADD CONSTRAINT fk_student_stops_route
                FOREIGN KEY (route_id) REFERENCES routes(id) ON DELETE SET NULL;
    END IF;
END $$;

CREATE INDEX IF NOT EXISTS idx_student_stops_status ON student_stops(status);
CREATE INDEX IF NOT EXISTS idx_student_stops_route ON student_stops(route_id);
