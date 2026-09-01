ALTER TABLE students
DROP COLUMN school_id;

ALTER TABLE contracts
    ADD COLUMN school_id UUID NOT NULL;

ALTER TABLE contracts
    ADD CONSTRAINT fk_contracts_school
    FOREIGN KEY (school_id)
    REFERENCES schools(id);