ALTER TABLE drivers
DROP CONSTRAINT drivers_user_id_fkey;

ALTER TABLE drivers
ADD CONSTRAINT drivers_user_id_fkey
FOREIGN KEY (user_id)
REFERENCES users(id)
ON DELETE CASCADE;


ALTER TABLE responsibles
DROP CONSTRAINT responsibles_user_id_fkey;

ALTER TABLE responsibles
ADD CONSTRAINT responsibles_user_id_fkey
FOREIGN KEY (user_id)
REFERENCES users(id)
ON DELETE CASCADE;