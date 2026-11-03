-- banco dos testes que sobem o Spring (BackendApplicationTests) e do worker no CI.
-- So roda quando o volume do Postgres e criado do zero; num volume que ja existe:
--   docker exec vanroute-db createdb -U postgres vanroute_test
CREATE DATABASE vanroute_test;
