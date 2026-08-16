ALTER TABLE contracts ALTER COLUMN end_date SET NOT NULL;

ALTER TABLE user_driver_contracts RENAME COLUMN user_id TO responsible_id;

ALTER TABLE user_driver_contracts
  ADD COLUMN student_id uuid NOT NULL REFERENCES students(id);

ALTER TABLE user_driver_contracts
  ADD CONSTRAINT fk_udc_responsible FOREIGN KEY (responsible_id) REFERENCES responsibles(user_id);

ALTER TABLE user_driver_contracts
  ADD CONSTRAINT fk_udc_driver FOREIGN KEY (driver_id) REFERENCES drivers(user_id);

ALTER TABLE user_driver_contracts
  ADD CONSTRAINT fk_udc_contract FOREIGN KEY (contract_id) REFERENCES contracts(id);


--indice pq provavelmente vamo chama isso pro motorista ver os fiots que tao ativo etcc
CREATE INDEX idx_udc_student ON user_driver_contracts (student_id);

ALTER TABLE drivers
ADD COLUMN link_code CHAR(9) NOT NULL UNIQUE;


CREATE TABLE chats (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    user_one_id uuid NOT NULL REFERENCES users(id),
    user_two_id uuid NOT NULL REFERENCES users(id),
    created_at timestamp NOT NULL DEFAULT now(),

    CONSTRAINT chk_chat_ordered CHECK (user_one_id < user_two_id),
    CONSTRAINT uq_chat_pair UNIQUE (user_one_id, user_two_id)
);

CREATE TABLE chat_messages (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    chat_id uuid NOT NULL REFERENCES chats(id),
    sender_user_id uuid NOT NULL REFERENCES users(id),
    content text NOT NULL,
    sent_at timestamp NOT NULL DEFAULT now(),
    read_at timestamp
);