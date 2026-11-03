-- user_driver_contracts tinha a mesma FK declarada duas vezes (contrato e motorista) e o responsavel
-- apontando pra users e pra responsibles ao mesmo tempo; fica uma FK por coluna, e a escola ganha a dela
ALTER TABLE user_driver_contracts DROP CONSTRAINT IF EXISTS user_driver_contracts_contract_id_fkey;
ALTER TABLE user_driver_contracts DROP CONSTRAINT IF EXISTS user_driver_contracts_driver_id_fkey;
ALTER TABLE user_driver_contracts DROP CONSTRAINT IF EXISTS user_driver_contracts_user_id_fkey;
ALTER TABLE user_driver_contracts
    ADD CONSTRAINT fk_udc_school FOREIGN KEY (school_id) REFERENCES schools(id);

-- todo ponto e de uma rota, num endereco, numa posicao
ALTER TABLE route_stops ALTER COLUMN route_id SET NOT NULL;
ALTER TABLE route_stops ALTER COLUMN address_id SET NOT NULL;
ALTER TABLE route_stops ALTER COLUMN order_index SET NOT NULL;
