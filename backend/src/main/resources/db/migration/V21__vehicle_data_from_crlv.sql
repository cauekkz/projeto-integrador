-- dados que vem do CRLV digital
ALTER TABLE vehicles
    ADD COLUMN renavam VARCHAR(11) UNIQUE,
    ADD COLUMN chassis VARCHAR(17) UNIQUE,
    ADD COLUMN manufacture_year INT,
    ADD COLUMN color VARCHAR(50),
    ADD COLUMN fuel VARCHAR(50),
    ADD COLUMN category VARCHAR(50),
    ADD COLUMN licensing_year INT;

-- o mesmo motorista pode voltar a usar a mesma van; so nao pode ter dois vinculos ativos com ela
ALTER TABLE driver_vehicles DROP CONSTRAINT IF EXISTS driver_vehicles_driver_id_vehicle_id_key;
CREATE UNIQUE INDEX uk_driver_vehicles_active ON driver_vehicles (driver_id, vehicle_id) WHERE end_date IS NULL;
