-- payment_date sempre guardou o vencimento da parcela (inicio do contrato + n periodos): vira due_date.
-- paid_at e quando foi pago de fato; so existe em parcela PAID
ALTER TABLE payments RENAME COLUMN payment_date TO due_date;
ALTER TABLE payments ALTER COLUMN due_date SET NOT NULL;
ALTER TABLE payments ADD COLUMN paid_at TIMESTAMP;
ALTER TABLE payments ADD CONSTRAINT ck_payments_paid_at CHECK ((status = 'PAID') = (paid_at IS NOT NULL));
CREATE INDEX idx_payments_contract ON payments (contract_id, due_date);
