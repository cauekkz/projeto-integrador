-- grupos do motorista (no lugar de classes, que nunca foi usada): o motorista agrupa os alunos dele como quiser
-- e avisa o grupo todo de uma vez. Um aluno pode estar em varios grupos
ALTER TABLE students DROP COLUMN IF EXISTS class_id;
DROP TABLE IF EXISTS classes;

CREATE TABLE driver_groups (
    id         UUID PRIMARY KEY,
    driver_id  UUID         NOT NULL REFERENCES drivers (user_id) ON DELETE CASCADE,
    name       VARCHAR(60)  NOT NULL,
    created_at TIMESTAMP    NOT NULL DEFAULT now(),
    CONSTRAINT uk_driver_groups_name UNIQUE (driver_id, name)
);

CREATE TABLE driver_group_students (
    group_id   UUID NOT NULL REFERENCES driver_groups (id) ON DELETE CASCADE,
    student_id UUID NOT NULL REFERENCES students (id) ON DELETE CASCADE,
    PRIMARY KEY (group_id, student_id)
);

-- assinatura do contrato: uma linha = alguem assinou (o motorista ao propor, o responsavel ao aceitar).
-- o status saiu (a linha so existe se assinou); fica a hora exata e o hash dos termos assinados
ALTER TABLE signatures DROP COLUMN status;
ALTER TABLE signatures ALTER COLUMN signed_at TYPE TIMESTAMP USING signed_at::timestamp;
ALTER TABLE signatures ALTER COLUMN signed_at SET NOT NULL;
ALTER TABLE signatures ADD COLUMN document_hash VARCHAR(64) NOT NULL;

-- documento: version nunca foi lida (sempre 1). status: PENDING ate as duas partes assinarem, SIGNED ou REJECTED
ALTER TABLE documents DROP COLUMN version;
UPDATE documents SET status = 'PENDING' WHERE status NOT IN ('PENDING', 'SIGNED', 'REJECTED');

-- ocorrencia e um aviso da viagem pros pais: sem fluxo de status
ALTER TABLE occurrences DROP COLUMN status;
ALTER TABLE occurrences ALTER COLUMN description SET NOT NULL;
ALTER TABLE occurrences ALTER COLUMN type SET NOT NULL;
ALTER TABLE occurrences ALTER COLUMN date_time SET NOT NULL;
CREATE INDEX idx_occurrences_trip ON occurrences (trip_id);

-- avaliacao: uma por responsavel por motorista (pode mudar a nota), nota inteira de 1 a 5
ALTER TABLE ratings DROP CONSTRAINT IF EXISTS ratings_score_check;
ALTER TABLE ratings ALTER COLUMN score TYPE INTEGER USING score::integer;
ALTER TABLE ratings ALTER COLUMN score SET NOT NULL;
ALTER TABLE ratings ADD CONSTRAINT ck_ratings_score CHECK (score BETWEEN 1 AND 5);
ALTER TABLE ratings ALTER COLUMN date SET NOT NULL;
ALTER TABLE ratings ADD CONSTRAINT uk_ratings_responsible_driver UNIQUE (responsible_id, driver_id);
