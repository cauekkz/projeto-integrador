-- "excluir minha conta" apaga o CPF, o e-mail e o telefone de verdade (ficam nulos), em vez de trocar por um valor falso.
-- A linha do usuario continua, como "Usuario removido", pra nao quebrar o historico dos outros (contratos, chat, parcelas)
ALTER TABLE users ALTER COLUMN cpf DROP NOT NULL;
ALTER TABLE users ALTER COLUMN email DROP NOT NULL;
ALTER TABLE users ALTER COLUMN phone DROP NOT NULL;
