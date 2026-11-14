# Sugestões futuras

Coisas que **funcionam hoje**, mas que valem uma decisão depois. Nada aqui é bug. É só para pensar com calma.

---

## 1. Colunas e status que quase não servem hoje

| Onde | Situação hoje | Sugestão |
|---|---|---|
| `drivers.approval_status` | sempre `APPROVED`: a CNH já é validada no cadastro (assinatura ICP-Brasil, nome, validade, categoria D/E e 21 anos). Ninguém lê a coluna | usar quando existir a aprovação dos documentos que a CNH não tem (curso de escolar, autorização do município, vistoria): `docs/PENDENCIAS_E_ERROS.md`, 1.3 |
| `contracts.status`: `SUSPENDED`, `OVERDUE` | comentados no enum | entram se um dia existir suspensão de contrato |
| `trips.status`: `SCHEDULED`, `PAUSED`, `FAILED` | a viagem só usa `STARTED`, `IN_PROGRESS`, `FINISHED` e `CANCELED` | apagar do enum, ou usar se surgir "pausar viagem" |
| `vehicles.status`: `INACTIVE`, `UNDER_MAINTENANCE`, `BLOCKED`, `PENDING_DOCUMENTATION` | só `ACTIVE` é usado | útil se o motorista puder marcar a van "em manutenção" |
| `documents.uploaded_at`, `documents.uploaded_by_user_id`, `student_absences.created_by` | gravados, nunca lidos | **manter**: é auditoria (quem fez e quando) e não custa nada |
| `occurrences.driver_id` | repete o motorista da viagem (`trips.driver_id`) | manter (simples) ou tirar e ler da viagem |
| `addresses`: sem complemento | apartamentos do mesmo prédio viram o mesmo endereço | bom para a van (para no mesmo lugar). Se precisar, criar `complement` **fora** da chave de repetição |

---

## 2. Ideias para depois

- **Aviso do grupo com data:** hoje o aviso é sempre "hoje". Dá para aceitar uma data ("não vou buscar na sexta").
- **Assinatura com IP:** a assinatura guarda quem, quando e o hash dos termos. Guardar também o IP e o aparelho deixa a prova mais forte.

O que **falta fazer** (pagamento, ciclo do contrato, documentos do motorista, LGPD, produção) está em `docs/PENDENCIAS_E_ERROS.md`.
