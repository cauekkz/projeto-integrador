CREATE TABLE users (
    id UUID PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    cpf CHAR(11) NOT NULL UNIQUE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
    status TEXT
);

CREATE TABLE emails (
    id UUID PRIMARY KEY,
    email VARCHAR(255) NOT NULL UNIQUE,
    finalized_at TIMESTAMP DEFAULT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL
);

CREATE TABLE user_emails (
    id UUID PRIMARY KEY,

    user_id UUID NOT NULL,
    email_id UUID NOT NULL,

    UNIQUE(user_id, email_id),

    FOREIGN KEY (user_id)
        REFERENCES users(id),

    FOREIGN KEY (email_id)
        REFERENCES emails(id)
);

CREATE TABLE phones (
    id UUID PRIMARY KEY,
    phone VARCHAR(255) NOT NULL UNIQUE,
    finalized_at TIMESTAMP DEFAULT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL
);

CREATE TABLE user_phones (
    id UUID PRIMARY KEY,

    user_id UUID NOT NULL,
    phone_id UUID NOT NULL,

    UNIQUE(user_id, phone_id),

    FOREIGN KEY (user_id)
        REFERENCES users(id),

    FOREIGN KEY (phone_id)
        REFERENCES phones(id)
);

CREATE TABLE addresses (
    id UUID PRIMARY KEY,
    street VARCHAR(255) NOT NULL,
    zip_code VARCHAR(20) NOT NULL,
    city VARCHAR(100) NOT NULL,
    neighborhood VARCHAR(100),
    number INT,
    state VARCHAR(50),
    coordinates POINT
);

CREATE TABLE responsibles (
    user_id UUID PRIMARY KEY,

    payment_method TEXT NOT NULL,
    financial_status TEXT NOT NULL,

    FOREIGN KEY (user_id)
        REFERENCES users(id)
);

CREATE TABLE schools (
    id UUID PRIMARY KEY,

    name VARCHAR(255) NOT NULL,

    address_id UUID UNIQUE,

    phone VARCHAR(20),
    email VARCHAR(255),

    FOREIGN KEY (address_id)
        REFERENCES addresses(id)
);

CREATE TABLE classes (
    id UUID PRIMARY KEY,
    name VARCHAR(100) NOT NULL
);

CREATE TABLE students (
    id UUID PRIMARY KEY,

    birth_date DATE NOT NULL,
    notes TEXT,

    school_id UUID NOT NULL,
    class_id UUID NOT NULL,

    FOREIGN KEY (school_id)
        REFERENCES schools(id),

    FOREIGN KEY (class_id)
        REFERENCES classes(id)
);

CREATE TABLE student_responsibles (
    id UUID PRIMARY KEY,

    student_id UUID NOT NULL,
    responsible_id UUID NOT NULL,

    relation_type TEXT,
    is_primary BOOLEAN DEFAULT FALSE,

    UNIQUE(student_id, responsible_id),

    FOREIGN KEY (student_id)
        REFERENCES students(id),

    FOREIGN KEY (responsible_id)
        REFERENCES responsibles(user_id)
);

CREATE TABLE student_addresses (
    id UUID PRIMARY KEY,

    student_id UUID NOT NULL,
    address_id UUID NOT NULL,

    weekdays VARCHAR(100),

    active BOOLEAN DEFAULT TRUE,

    FOREIGN KEY (student_id)
        REFERENCES students(id),

    FOREIGN KEY (address_id)
        REFERENCES addresses(id)
);

CREATE TABLE responsible_addresses (
    id UUID PRIMARY KEY,

    responsible_id UUID NOT NULL,
    address_id UUID NOT NULL,

    active BOOLEAN DEFAULT TRUE,

    UNIQUE(responsible_id, address_id),

    FOREIGN KEY (responsible_id)
        REFERENCES responsibles(user_id),

    FOREIGN KEY (address_id)
        REFERENCES addresses(id)
);

CREATE TABLE drivers (
    user_id UUID PRIMARY KEY,

    cnh_number VARCHAR(50) NOT NULL UNIQUE,

    cnh_expiration DATE,

    approval_status TEXT,
    type TEXT,

    FOREIGN KEY (user_id)
        REFERENCES users(id)
);

CREATE TABLE vehicles (
    id UUID PRIMARY KEY,

    plate VARCHAR(20) NOT NULL UNIQUE,

    model VARCHAR(100),

    year INT CHECK (year >= 1900),

    capacity INT CHECK (capacity > 0),

    status TEXT
);

CREATE TABLE driver_vehicles (
    id UUID PRIMARY KEY,

    driver_id UUID NOT NULL,
    vehicle_id UUID NOT NULL,

    start_date DATE NOT NULL,
    end_date DATE,

    active BOOLEAN DEFAULT TRUE,

    UNIQUE(driver_id, vehicle_id),

    CHECK (
        end_date IS NULL
        OR end_date >= start_date
    ),

    FOREIGN KEY (driver_id)
        REFERENCES drivers(user_id),

    FOREIGN KEY (vehicle_id)
        REFERENCES vehicles(id)
);

CREATE TABLE driver_schools (
    id UUID PRIMARY KEY,

    driver_id UUID NOT NULL,
    school_id UUID NOT NULL,

    start_date DATE NOT NULL,
    end_date DATE,

    active BOOLEAN DEFAULT TRUE,

    UNIQUE(driver_id, school_id),

    CHECK (
        end_date IS NULL
        OR end_date >= start_date
    ),

    FOREIGN KEY (driver_id)
        REFERENCES drivers(user_id),

    FOREIGN KEY (school_id)
        REFERENCES schools(id)
);

CREATE TABLE routes (
    id UUID PRIMARY KEY,

    name VARCHAR(255) NOT NULL,

    shift TEXT
);

CREATE TABLE route_schools (
    id UUID PRIMARY KEY,

    route_id UUID NOT NULL,
    school_id UUID NOT NULL,

    school_order INT NOT NULL,

    UNIQUE(route_id, school_id),

    FOREIGN KEY (route_id)
        REFERENCES routes(id),

    FOREIGN KEY (school_id)
        REFERENCES schools(id)
);

CREATE TABLE route_drivers (
    id UUID PRIMARY KEY,

    route_id UUID NOT NULL,
    driver_id UUID NOT NULL,

    start_date DATE NOT NULL,
    end_date DATE DEFAULT NULL,

    UNIQUE(route_id, driver_id),

    CHECK (
        end_date IS NULL
        OR end_date >= start_date
    ),

    FOREIGN KEY (route_id)
        REFERENCES routes(id),

    FOREIGN KEY (driver_id)
        REFERENCES drivers(user_id)
);

CREATE TABLE route_stops (
    id UUID PRIMARY KEY,

    route_id UUID,
    address_id UUID,

    order_index INT,

    FOREIGN KEY (route_id)
        REFERENCES routes(id),

    FOREIGN KEY (address_id)
        REFERENCES addresses(id)
);

CREATE TABLE student_stops (
    id UUID PRIMARY KEY,

    student_id UUID NOT NULL,
    stop_id UUID NOT NULL,

    type TEXT,

    UNIQUE(student_id, stop_id, type),

    FOREIGN KEY (student_id)
        REFERENCES students(id),

    FOREIGN KEY (stop_id)
        REFERENCES route_stops(id)
);

CREATE TABLE trips (
    id UUID PRIMARY KEY,

    route_id UUID NOT NULL,

    date DATE NOT NULL,

    start_time TIME,

    vehicle_id UUID,

    end_time TIME,

    status TEXT,

    CHECK (
        end_time IS NULL
        OR start_time IS NULL
        OR end_time >= start_time
    ),

    FOREIGN KEY (route_id)
        REFERENCES routes(id),

    FOREIGN KEY (vehicle_id)
        REFERENCES vehicles(id)
);

CREATE TABLE boardings (
    id UUID PRIMARY KEY,

    trip_id UUID NOT NULL,
    student_id UUID NOT NULL,

    boarding_time TIMESTAMP,
    unboarding_time TIMESTAMP,

    status TEXT,

    CHECK (
        unboarding_time IS NULL
        OR boarding_time IS NULL
        OR unboarding_time >= boarding_time
    ),

    FOREIGN KEY (trip_id)
        REFERENCES trips(id),

    FOREIGN KEY (student_id)
        REFERENCES students(id)
);

CREATE TABLE occurrences (
    id UUID PRIMARY KEY,

    driver_id UUID NOT NULL,
    trip_id UUID NOT NULL,

    description TEXT,

    date_time TIMESTAMP,

    type TEXT,
    status TEXT,

    FOREIGN KEY (driver_id)
        REFERENCES drivers(user_id),

    FOREIGN KEY (trip_id)
        REFERENCES trips(id)
);

CREATE TABLE ratings (
    id UUID PRIMARY KEY,

    responsible_id UUID NOT NULL,
    driver_id UUID NOT NULL,

    score NUMERIC(3,2)
        CHECK (score >= 0 AND score <= 5),

    comment TEXT,

    date TIMESTAMP,

    FOREIGN KEY (responsible_id)
        REFERENCES responsibles(user_id),

    FOREIGN KEY (driver_id)
        REFERENCES drivers(user_id)
);

CREATE TABLE documents (
    id UUID PRIMARY KEY,

    type TEXT,

    url TEXT,

    uploaded_at TIMESTAMP,

    version INT CHECK (version >= 0),

    status TEXT,

    entity_type TEXT,
    entity_id UUID,

    uploaded_by_user_id UUID,

    FOREIGN KEY (uploaded_by_user_id)
        REFERENCES users(id)
);

CREATE TABLE signatures (
    id UUID PRIMARY KEY,

    document_id UUID NOT NULL,
    user_id UUID NOT NULL,

    signed_at DATE,

    status TEXT,

    UNIQUE(document_id, user_id),

    FOREIGN KEY (document_id)
        REFERENCES documents(id),

    FOREIGN KEY (user_id)
        REFERENCES users(id)
);

CREATE TABLE notifications (
    id UUID PRIMARY KEY,

    user_id UUID NOT NULL,

    title VARCHAR(255) NOT NULL,
    message TEXT NOT NULL,

    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,

    read BOOLEAN DEFAULT FALSE,

    type TEXT,

    FOREIGN KEY (user_id)
        REFERENCES users(id)
);

CREATE TABLE contracts (
    id UUID PRIMARY KEY,

    party_a_type TEXT,
    party_a_id UUID,

    party_b_type TEXT,
    party_b_id UUID,

    periodicity VARCHAR(100),

    value NUMERIC(10,2)
        NOT NULL
        CHECK (value >= 0),

    start_date DATE NOT NULL,
    end_date DATE,

    status TEXT,

    CHECK (
        end_date IS NULL
        OR end_date >= start_date
    )
);

CREATE TABLE payments (
    id UUID PRIMARY KEY,

    contract_id UUID NOT NULL,

    value NUMERIC(10,2)
        NOT NULL
        CHECK (value >= 0),

    payment_date DATE,

    status TEXT,

    payment_method TEXT,

    FOREIGN KEY (contract_id)
        REFERENCES contracts(id)
);
