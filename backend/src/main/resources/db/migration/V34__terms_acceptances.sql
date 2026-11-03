-- aceite dos Termos de Uso e da Politica de Privacidade (LGPD): quem aceitou, qual versao e quando.
-- O texto de cada versao fica em src/main/resources/terms/v{versao}.md
CREATE TABLE terms_acceptances (
    id          UUID PRIMARY KEY,
    user_id     UUID        NOT NULL REFERENCES users (id),
    version     VARCHAR(20) NOT NULL,
    accepted_at TIMESTAMP   NOT NULL,
    CONSTRAINT uk_terms_acceptances_user_version UNIQUE (user_id, version)
);
