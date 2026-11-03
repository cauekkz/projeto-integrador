-- uma parcela por vencimento em cada contrato: um aceite repetido (duplo clique) nao gera parcelas em dobro
DROP INDEX IF EXISTS idx_payments_contract;
ALTER TABLE payments ADD CONSTRAINT uk_payments_contract_due_date UNIQUE (contract_id, due_date);

-- cancelamento com aviso previo: o pedido vale sozinho em effective_date (pedido + 30 dias), a nao ser que a outra
-- parte aceite antes (cancela na hora) ou quem pediu desista. COMPLETED = o aviso terminou e o worker cancelou.
-- REJECTED continua aceito so pelo historico antigo: ninguem recusa mais
ALTER TABLE contract_cancellations ADD COLUMN effective_date DATE;
UPDATE contract_cancellations SET effective_date = (requested_at::date + 30) WHERE status = 'PENDING';
ALTER TABLE contract_cancellations ADD CONSTRAINT ck_contract_cancellations_effective
    CHECK (status <> 'PENDING' OR effective_date IS NOT NULL);
ALTER TABLE contract_cancellations DROP CONSTRAINT ck_contract_cancellations_status;
ALTER TABLE contract_cancellations ADD CONSTRAINT ck_contract_cancellations_status
    CHECK (status IN ('PENDING', 'ACCEPTED', 'REJECTED', 'WITHDRAWN', 'COMPLETED'));

-- contrato aceito que ainda nao comecou: SCHEDULED (o worker passa pra ACTIVE na data de inicio)
UPDATE contracts SET status = 'SCHEDULED' WHERE status = 'ACTIVE' AND start_date > CURRENT_DATE;
