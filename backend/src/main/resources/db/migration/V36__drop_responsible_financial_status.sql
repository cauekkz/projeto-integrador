-- a situacao financeira do responsavel e calculada na hora pelas parcelas atrasadas (perfil do responsavel).
-- A coluna era um resumo gravado por dois processos (worker e baixa da parcela) e ninguem lia: sai
ALTER TABLE responsibles DROP COLUMN financial_status;
