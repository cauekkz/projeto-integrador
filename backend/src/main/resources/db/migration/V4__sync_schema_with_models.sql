ALTER TABLE users ADD COLUMN email VARCHAR(255) NOT NULL;
ALTER TABLE users ADD COLUMN phone VARCHAR(255) NOT NULL;
ALTER TABLE users ADD CONSTRAINT users_email_unique UNIQUE (email);
ALTER TABLE users ADD CONSTRAINT users_phone_unique UNIQUE (phone);
ALTER TABLE users ALTER COLUMN status SET NOT NULL;

ALTER TABLE responsibles DROP COLUMN payment_method;

ALTER TABLE schools ALTER COLUMN email SET NOT NULL;

ALTER TABLE student_responsibles DROP COLUMN is_primary;

ALTER TABLE student_addresses DROP COLUMN active;

ALTER TABLE responsible_addresses DROP CONSTRAINT responsible_addresses_responsible_id_address_id_key;
ALTER TABLE responsible_addresses DROP COLUMN active;

ALTER TABLE driver_vehicles DROP COLUMN active;

ALTER TABLE notifications DROP COLUMN read;

ALTER TABLE contracts DROP COLUMN party_a_type;
ALTER TABLE contracts DROP COLUMN party_a_id;
ALTER TABLE contracts DROP COLUMN party_b_type;
ALTER TABLE contracts DROP COLUMN party_b_id;
ALTER TABLE contracts ALTER COLUMN periodicity SET NOT NULL;

DROP TABLE user_emails;
DROP TABLE user_phones;
DROP TABLE emails;
DROP TABLE phones;
DROP TABLE route_schools;
DROP TABLE driver_schools;

CREATE TABLE school_stops (
    id UUID PRIMARY KEY,
    school_id UUID NOT NULL,
    stop_id UUID NOT NULL,

    FOREIGN KEY (school_id)
        REFERENCES schools(id),

    FOREIGN KEY (stop_id)
        REFERENCES route_stops(id)
);

CREATE TABLE user_driver_contracts (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL,
    driver_id UUID NOT NULL,
    contract_id UUID NOT NULL UNIQUE,

    FOREIGN KEY (user_id)
        REFERENCES users(id),

    FOREIGN KEY (driver_id)
        REFERENCES drivers(user_id),

    FOREIGN KEY (contract_id)
        REFERENCES contracts(id)
);
