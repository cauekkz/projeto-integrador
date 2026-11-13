-- foto do aluno no MinIO (students/{studentId}/...): o admin envia; veem os responsaveis do aluno e o motorista que
-- tem ou teve contrato com ele. Antes ficava so no navegador do responsavel
ALTER TABLE students ADD COLUMN photo_key TEXT;
