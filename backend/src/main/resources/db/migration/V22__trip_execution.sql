-- quem fez a trip (a rota pode ter outro motorista no futuro)
ALTER TABLE trips ADD COLUMN driver_id UUID REFERENCES drivers(user_id);

-- uma trip por rota por dia; cancelar libera recomecar do zero
CREATE UNIQUE INDEX uk_trips_route_date_active ON trips (route_id, date) WHERE status <> 'CANCELED';

-- o mesmo aluno so uma vez na trip
ALTER TABLE boardings ADD CONSTRAINT uk_boardings_trip_student UNIQUE (trip_id, student_id);

-- van padrao da rota (usada quando o motorista nao escolhe outra ao iniciar)
ALTER TABLE routes ADD COLUMN vehicle_id UUID REFERENCES vehicles(id);

-- falta avisada pelo responsavel: o aluno nao vai naquela rota naquele dia ai o pai bota isso so que so dura 1 dia
CREATE TABLE student_absences (
    id UUID PRIMARY KEY,
    student_id UUID NOT NULL REFERENCES students(id),
    route_id UUID NOT NULL REFERENCES routes(id),
    date DATE NOT NULL,
    created_by UUID NOT NULL REFERENCES users(id),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_student_absences UNIQUE (student_id, route_id, date)
);
