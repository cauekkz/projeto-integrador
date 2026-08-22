
-- 1. rmeove shift de routes
ALTER TABLE routes DROP COLUMN shift;

CREATE TABLE driver_schools (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  driver_id UUID NOT NULL,
  school_id UUID NOT NULL,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT fk_driver_schools_driver FOREIGN KEY (driver_id) REFERENCES drivers(user_id) ON DELETE CASCADE,
  CONSTRAINT fk_driver_schools_school FOREIGN KEY (school_id) REFERENCES schools(id) ON DELETE CASCADE,
  CONSTRAINT uk_driver_schools_unique UNIQUE(driver_id, school_id)
);

CREATE INDEX idx_driver_schools_driver ON driver_schools(driver_id);
CREATE INDEX idx_driver_schools_school ON driver_schools(school_id);


-- IA BURRA DO CARALHO PQP POR ISSO HENQUE SO FAZ MERDA, PORRA BICHO NAO PENSA
/*
ALTER TABLE student_stops ADD COLUMN status TEXT DEFAULT 'pending';
ALTER TABLE student_stops ADD COLUMN route_id UUID NULL;
*/
CREATE INDEX idx_student_stops_status ON student_stops(status);



ALTER TABLE student_stops
    RENAME COLUMN type TO location_type;

ALTER TABLE student_stops
    ADD COLUMN action TEXT;