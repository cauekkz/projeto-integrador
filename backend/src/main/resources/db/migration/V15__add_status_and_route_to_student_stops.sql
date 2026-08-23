-- Adiciona as colunas status e route_id em student_stops
ALTER TABLE student_stops
    ADD COLUMN status TEXT DEFAULT 'pending',
    ADD COLUMN route_id UUID NULL;

-- Adiciona a constraint de foreign key para route_id se necessário
ALTER TABLE student_stops
    ADD CONSTRAINT fk_student_stops_route
        FOREIGN KEY (route_id) REFERENCES routes(id) ON DELETE SET NULL;

-- Cria o índice na coluna status agora existente
CREATE INDEX idx_student_stops_status ON student_stops(status);
CREATE INDEX idx_student_stops_route ON student_stops(route_id);