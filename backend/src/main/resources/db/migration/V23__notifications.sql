-- notificacoes do usuario (contrato, viagem): a tabela ja existia desde a V1, sem uso.
-- payload: ids pra o app abrir a tela certa (tripId, contractId...); read_at null = nao lida (a V4 tirou o "read")
ALTER TABLE notifications ADD COLUMN payload JSONB;
ALTER TABLE notifications ADD COLUMN read_at TIMESTAMP;

CREATE INDEX idx_notifications_user_created ON notifications (user_id, created_at DESC);
