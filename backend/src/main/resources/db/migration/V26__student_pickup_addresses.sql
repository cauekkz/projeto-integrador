-- 1) endereco sem repeticao: mesmo endereco = mesmo CEP + mesmo numero (o jeito de escrever a rua nao importa).
--    CEP terminado em 000 pode ser o CEP unico de uma cidade pequena: ai a chave tambem leva a rua e a cidade.
--    ao criar um endereco que ja existe, o backend so liga nele (nao duplica nem chama a API de mapas).
--    a mesma regra esta em AddressService.key(); aqui calcula pros enderecos que ja existem.
ALTER TABLE addresses ADD COLUMN address_key VARCHAR(400);

UPDATE addresses a SET address_key = CASE
    WHEN length(z.zip) = 8 AND right(z.zip, 3) <> '000' AND a.number IS NOT NULL THEN z.zip || '|' || a.number
    ELSE z.zip || '|' || coalesce(a.number::text, '')
        || '|' || trim(regexp_replace(regexp_replace(translate(lower(coalesce(a.street, '')),
               'áàâãäéèêëíìîïóòôõöúùûüçñ', 'aaaaaeeeeiiiiooooouuuucn'), '[^a-z0-9 ]', ' ', 'g'), '\s+', ' ', 'g'))
        || '|' || trim(regexp_replace(regexp_replace(translate(lower(coalesce(a.city, '')),
               'áàâãäéèêëíìîïóòôõöúùûüçñ', 'aaaaaeeeeiiiiooooouuuucn'), '[^a-z0-9 ]', ' ', 'g'), '\s+', ' ', 'g'))
    END
FROM (SELECT id, regexp_replace(coalesce(zip_code, ''), '\D', '', 'g') AS zip FROM addresses) z
WHERE z.id = a.id;

-- nao e unico: bancos antigos podem ter o mesmo endereco duplicado
CREATE INDEX idx_addresses_key ON addresses (address_key);

-- o endereco agora e compartilhado: duas escolas no mesmo endereco (mesmo predio) apontam pro mesmo registro
ALTER TABLE schools DROP CONSTRAINT IF EXISTS schools_address_id_key;

-- 2) enderecos do aluno = onde ele e buscado/deixado (varios, com apelido: "Casa", "Casa da avo", "Ingles de terca").
--    tirar um endereco do aluno apaga so esta ligacao; o endereco continua pra quem mais usa (ex.: o irmao).
--    os dias da semana continuam no student stop.
--    o endereco do responsavel (responsibles.address_id) e so o de cobranca.
ALTER TABLE student_addresses ADD COLUMN label VARCHAR(60) NOT NULL DEFAULT 'Casa';
ALTER TABLE student_addresses ADD COLUMN created_at TIMESTAMP NOT NULL DEFAULT now();

CREATE UNIQUE INDEX uk_student_addresses ON student_addresses (student_id, address_id);
CREATE INDEX idx_student_addresses_address ON student_addresses (address_id);

-- 3) o ponto da rota aponta pro endereco do aluno ou da escola. "um ponto por endereco na rota" fica no codigo (409),
--    nao num indice unico: bancos antigos podem ter dois pontos no mesmo endereco
CREATE INDEX idx_route_stops_route_address ON route_stops (route_id, address_id);
