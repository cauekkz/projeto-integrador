-- pedido de cancelamento de contrato ativo: uma parte pede e a outra aceita ou recusa; quem pediu pode desistir.
-- unilateral: o motorista cancelou sozinho (responsavel com 2+ parcelas atrasadas), sem passar pela aprovacao
CREATE TABLE contract_cancellations (
    id           UUID PRIMARY KEY,
    contract_id  UUID         NOT NULL REFERENCES contracts (id),
    requested_by UUID         NOT NULL REFERENCES users (id),
    reason       VARCHAR(500) NOT NULL,
    requested_at TIMESTAMP    NOT NULL,
    status       VARCHAR(20)  NOT NULL,
    unilateral   BOOLEAN      NOT NULL DEFAULT FALSE,
    decided_by   UUID REFERENCES users (id),
    decided_at   TIMESTAMP,
    CONSTRAINT ck_contract_cancellations_status CHECK (status IN ('PENDING', 'ACCEPTED', 'REJECTED', 'WITHDRAWN')),
    CONSTRAINT ck_contract_cancellations_decided CHECK ((status = 'PENDING') = (decided_at IS NULL))
);
-- no maximo um pedido pendente por contrato
CREATE UNIQUE INDEX uk_contract_cancellations_pending ON contract_cancellations (contract_id) WHERE status = 'PENDING';
CREATE INDEX idx_contract_cancellations_contract ON contract_cancellations (contract_id, requested_at);

-- chave Pix do motorista: o responsavel copia e paga no app do banco; quem confirma o pagamento e o motorista
ALTER TABLE drivers ADD COLUMN pix_key_type VARCHAR(10);
ALTER TABLE drivers ADD COLUMN pix_key VARCHAR(77);
ALTER TABLE drivers ADD CONSTRAINT ck_drivers_pix_key CHECK ((pix_key IS NULL) = (pix_key_type IS NULL));
ALTER TABLE drivers ADD CONSTRAINT ck_drivers_pix_key_type CHECK (pix_key_type IN ('CPF', 'CNPJ', 'EMAIL', 'PHONE', 'RANDOM'));
