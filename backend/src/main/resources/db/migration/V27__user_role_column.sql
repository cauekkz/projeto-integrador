-- cada usuario tem um papel so (motorista, responsavel ou admin): vira uma coluna em users,
-- no lugar de roles_tb + user_roles. O JWT continua com a claim "roles" (ex.: ["ROLE_DRIVER"])
ALTER TABLE users ADD COLUMN role VARCHAR(30);

-- papel que o usuario ja tinha (se tiver mais de um, admin > motorista > responsavel)
UPDATE users u SET role = (
    SELECT r.nome FROM user_roles ur JOIN roles_tb r ON r.id = ur.role_id
    WHERE ur.user_id = u.id
    ORDER BY CASE r.nome WHEN 'ROLE_ADMIN' THEN 0 WHEN 'ROLE_DRIVER' THEN 1 ELSE 2 END
    LIMIT 1);

-- sem papel gravado: motorista se estiver em drivers, senao responsavel
UPDATE users u SET role = CASE WHEN EXISTS (SELECT 1 FROM drivers d WHERE d.user_id = u.id)
                               THEN 'ROLE_DRIVER' ELSE 'ROLE_RESPONSIBLE' END
WHERE role IS NULL;

ALTER TABLE users ALTER COLUMN role SET NOT NULL;
ALTER TABLE users ADD CONSTRAINT ck_users_role CHECK (role IN ('ROLE_ADMIN', 'ROLE_DRIVER', 'ROLE_RESPONSIBLE'));

DROP TABLE user_roles;
DROP TABLE roles_tb;
