-- chave do arquivo no MinIO (profile/{userId}/...), nao a URL: a URL assinada expira e e gerada na hora de exibir
ALTER TABLE users ADD COLUMN photo_key TEXT;
