-- situacao financeira do responsavel: nasce UP_TO_DATE; o worker diario marca OVERDUE quem tem parcela atrasada
-- e o backend volta pra UP_TO_DATE quando o motorista da baixa. PENDING nao e mais usado
UPDATE responsibles SET financial_status = 'UP_TO_DATE' WHERE financial_status = 'PENDING';
