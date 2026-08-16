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

