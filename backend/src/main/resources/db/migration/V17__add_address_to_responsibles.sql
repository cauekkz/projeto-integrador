ALTER TABLE responsibles
ADD COLUMN address_id UUID;

ALTER TABLE responsibles
ADD CONSTRAINT fk_responsibles_address
    FOREIGN KEY (address_id)
    REFERENCES addresses(id);

