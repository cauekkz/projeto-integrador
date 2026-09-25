ALTER TABLE students
DROP COLUMN school_id;


ALTER TABLE user_driver_contracts
ADD COLUMN school_id UUID NOT NULL;