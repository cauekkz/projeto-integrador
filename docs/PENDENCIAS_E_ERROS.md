# Pendências e erros encontrados

Levantamento feito em 26/10/2026 na branch `feature/frontend-contracts`.
Separado em: o que já foi corrigido, o que ainda precisa mudar e avisos pro grupo.

---

## 1. Já corrigido nesta branch

### 1.1 Embarque tem que vir antes do desembarque na rota
**Erro:** dava pra configurar o desembarque do aluno num ponto anterior ao embarque (ex.: desce no ponto 2, sobe no ponto 5).
Na trip o `alight` exige o aluno `BOARDED`, então o motorista chegava no ponto de desembarque e não conseguia registrar nada.
O `pending` da rota só olhava os dias, não a ordem.

**Correção:** `RouteService.checkBoardingBeforeAlighting` — por aluno e por dia, o ponto de embarque precisa ter `orderIndex` menor que o de desembarque. Retorna 400 com
`"O embarque de <aluno> precisa vir antes do desembarque na ordem da rota"`. A regra roda em:
- ligar aluno a um ponto (`POST /api/route/stop/{routeStopId}/add-student/{studentId}`)
- mover aluno de ponto (`PUT /api/route/student-stop/{id}/move`)
- trocar os dias do aluno (`PUT /api/route/student-stop/{id}/weekdays`)
- trocar a ordem dos pontos (`PUT /api/route/stop/{routeStopId}/swap-order`) — a troca pode inverter a ordem de algum aluno

Nova query: `StudentStopRepository.findByStudentIdAndStopRouteId`.

> Decisão: a rota não tem sentido (ida/volta) nem horário. Quem define o caminho é a ordem dos pontos configurada pelo motorista.

**Front:** tratar esse 400 nas telas de configuração da rota (mostrar a mensagem pro motorista).

### 1.2 `createRoute` sem transação
**Erro:** a rota era salva antes de buscar o motorista; se o motorista não existisse ficava uma rota órfã no banco.
**Correção:** `@Transactional` no `RouteService.createRoute`.

### 1.3 Testes
Novos testes em `RouteServiceTest` (ordem errada rejeitada, ordem certa aceita, swap que inverte rejeitado) e `StudentStopServiceTest` (o cadastro valida junto com os outros pontos do aluno).
Suíte do backend: **181 testes passando**.

### 1.4 Banco local quebrado pelo Flyway
**Erro:** o backend não subia (`FlywayValidateException: Migrations have failed validation`); o `contextLoads` falhava e o Tomcat/JWT/repositórios caíam em cascata.

**Causa:** o banco local tinha parado numa versão antiga das migrations:
- a V16 registrada nele era `created_at_contracts`, que no repositório foi renomeada para **V24**; a V16 atual é `LAT_LONG_ADDRESS`
- a V14 foi editada depois de já ter rodado (checksum diferente)
- V17 até V24 nunca tinham rodado

`flyway repair` **não resolve** esse caso: ele só atualiza checksum, não cria a estrutura que falta.

**Correção:** banco local `vanroute_db` recriado; as migrations V1–V24 rodaram limpas.

---

## 2. Precisa mudar (ainda não feito)

### 2.1 Endereço do aluno — apagar
Ninguém usa. Só é gravado no cadastro do aluno, geocodificado (gasta chamada na API de mapas) e devolvido uma vez na resposta.
`StudentAddressRepository` não tem nenhuma consulta, nenhum service lê, o front só envia e nunca lê, e os pontos da rota são digitados à mão.

Remover:
- [ ] migration nova: `DROP TABLE student_addresses`
- [ ] `models/student/StudentAddress.java`
- [ ] `repositories/StudentAddressRepository.java`
- [ ] `dtos/student/StudentAddressResponseDTO.java` e o campo `studentAddressResponseDTO` do `StudentResponseDTO`
- [ ] campo `address` do `StudentRequestDTO` e o trecho de endereço em `StudentService.createStudentWithRelation`
- [ ] `StudentResponsibleMapper` (parte do endereço do aluno)
- [ ] `StudentServiceTest`
- [ ] `api-tests/run-api-tests.sh` (linhas que leem `studentAddressResponseDTO.id` e a checagem `coordenadas do aluno persistidas`)
- [ ] front: campos de endereço em `add-student` e `address` em `services/dto/student/student.dto.ts`

### 2.2 Endereço do responsável — manter, mas num lugar só
Hoje fica **gravado duas vezes**:
- `responsibles.address_id` (V17) — é o que o código lê; **manter**, serve pra cobrança/pagamento depois
- tabela `responsible_addresses` — só é gravada, ninguém lê; **remover**

Remover:
- [ ] migration nova: `DROP TABLE responsible_addresses`
- [ ] `models/address/ResponsibleAddress.java`
- [ ] `repositories/ResponsibleAddressRepository.java`
- [ ] gravação em `ResponsibleService` e o mock em `ResponsibleServiceTest`
- [ ] `dtos/user/AddressRequestResponsibleDTO.java` (não é usado em lugar nenhum)

Obs.: o pagamento (`Payment` / `ContractService`) ainda não usa endereço nenhum.

### 2.3 Escola na rota duplica o endereço
A escola já tem endereço e coordenadas, mas pra colocá-la na rota o motorista cria um ponto digitando o endereço de novo e depois liga a escola nele.
As coordenadas do ponto podem não bater com as da escola, e o `checkNearStop` da trip usa as do ponto.
**Sugestão:** ao ligar a escola, criar/usar o ponto com o endereço da própria escola.

### 2.4 Ponto `HOME`/`OTHER` aceita o ponto da escola
Só o tipo `SCHOOL` é validado (`checkSchoolLocation`). Um aluno marcado como `HOME` pode ser ligado ao ponto de uma escola.
**Sugestão:** rejeitar `HOME`/`OTHER` em ponto que tenha escola, ou pelo menos avisar no front.

### 2.5 `application.properties`
- [ ] e-mail fixo no arquivo (`spring.mail.username=felipeacfornazeiro@gmail.com`) e chaves `spring.mail.username`/`spring.mail.password` **duplicadas** — a segunda sobrescreve o `${SENDER_EMAIL}`. Deixar só a versão com variável de ambiente.
- [ ] espaços no fim de `spring.profiles.active=local`; o Spring costuma ignorar, mas é bom limpar.

---

## 3. Avisos pro grupo

- **Migration que já rodou não se edita nem se renumera.** Mudança de banco sempre numa migration nova com o próximo número.
- Quem estiver com o banco local antigo vai ter o mesmo erro do Flyway. Pra resolver no ambiente de dev, recriar o banco:
  ```sh
  docker exec vanroute-db psql -U postgres -d postgres -c "DROP DATABASE vanroute_db;" -c "CREATE DATABASE vanroute_db;"
  ```
  Depois é só subir o backend (as migrations rodam do zero). Pra ter dados de exemplo: `backend/api-tests/demo/seed.sh`.
