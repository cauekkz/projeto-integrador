-- dias da semana em que o aluno usa aquele ponto (lista separada por virgula, valores do enum WeekDay).
-- registros antigos passam a valer todos os dias.
ALTER TABLE student_stops
    ADD COLUMN weekdays TEXT NOT NULL DEFAULT 'MONDAY,TUESDAY,WEDNESDAY,THURSDAY,FRIDAY,SATURDAY,SUNDAY';

-- colunas da V18 que nunca foram usadas; a rota ja vem pelo ponto (stop_id -> route_stops.route_id)
DROP INDEX IF EXISTS idx_student_stops_status;
DROP INDEX IF EXISTS idx_student_stops_route;
ALTER TABLE student_stops
    DROP CONSTRAINT IF EXISTS fk_student_stops_route,
    DROP COLUMN IF EXISTS status,
    DROP COLUMN IF EXISTS route_id;

-- os dias agora ficam no student_stop; o weekdays do endereco nao era usado por nenhuma regra
ALTER TABLE student_addresses DROP COLUMN weekdays;
