CREATE TABLE roles_tb (
                          id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
                          nome VARCHAR(255) NOT NULL UNIQUE
);