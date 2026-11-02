# Pendências e erros encontrados

Levantamento da branch `feature/frontend-contracts` (26/10/2026, atualizado em 28/10/2026).
Separado em: o que já foi corrigido, o que ainda precisa mudar, proposta de melhoria dos endereços e avisos pro grupo.

---

## 1. Já corrigido

### 1.1 Embarque tem que vir antes do desembarque na rota (26/10)
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

**Front (pendente):** tratar esse 400 nas telas de configuração da rota (mostrar a mensagem pro motorista).

### 1.2 `createRoute` sem transação (26/10)
**Erro:** a rota era salva antes de buscar o motorista; se o motorista não existisse ficava uma rota órfã no banco.
**Correção:** `@Transactional` no `RouteService.createRoute`.

### 1.3 Banco local quebrado pelo Flyway (26/10)
**Erro:** o backend não subia (`FlywayValidateException: Migrations have failed validation`); o `contextLoads` falhava e o Tomcat/JWT/repositórios caíam em cascata.

**Causa:** o banco local tinha parado numa versão antiga das migrations:
- a V16 registrada nele era `created_at_contracts`, que no repositório foi renomeada para **V24**; a V16 atual é `LAT_LONG_ADDRESS`
- a V14 foi editada depois de já ter rodado (checksum diferente)
- V17 até V24 nunca tinham rodado

`flyway repair` **não resolve** esse caso: ele só atualiza checksum, não cria a estrutura que falta.

**Correção:** banco local `vanroute_db` recriado; as migrations rodaram limpas.

### 1.4 `application.properties` com e-mail fixo e chaves duplicadas
**Erro:** `spring.mail.username=felipeacfornazeiro@gmail.com` fixo no arquivo e `spring.mail.username`/`spring.mail.password` duplicados (a segunda sobrescrevia o `${SENDER_EMAIL}`).
**Correção:** ficou só a versão com variável de ambiente (`${SENDER_EMAIL}` / `${SENDER_PASSWORD}`).

### 1.5 Ponto `HOME`/`OTHER` aceitava o ponto da escola (28/10)
**Erro:** só o tipo `SCHOOL` era validado. Um aluno marcado como `HOME` podia ser ligado (ou movido) pro ponto de uma escola.

**Correção:** a regra vale nos dois sentidos, sempre 400:
- ligar aluno `HOME`/`OTHER` num ponto que tem escola → `"Ponto de escola só aceita aluno marcado como SCHOOL"` (`StudentStopService.checkSchoolLocation`)
- mover aluno `HOME`/`OTHER` pra um ponto que tem escola → mesma mensagem (antes o `move` só checava `SCHOOL`)
- ligar escola num ponto que já tem aluno `HOME`/`OTHER` → `"Esse ponto tem aluno marcado como HOME/OTHER, não pode virar ponto de escola"` (`SchoolStopService.addSchoolStop`) — sem isso dava pra burlar a regra ligando a escola depois

Nova query: `StudentStopRepository.existsByStopIdAndLocationTypeIn`.

### 1.6 Endereço do responsável gravado duas vezes (28/10)
**Erro:** o endereço ficava em `responsibles.address_id` (V17, o que o código lê) **e** na tabela `responsible_addresses` (só gravada, ninguém lia).

**Correção:** fica só `responsibles.address_id` (endereço de cobrança, um por responsável).
- migration `V25__drop_responsible_addresses.sql`
- removidos `ResponsibleAddress`, `ResponsibleAddressRepository` e `AddressRequestResponsibleDTO` (não era usado)
- `ResponsibleService` não grava mais na tabela; mock removido do `ResponsibleServiceTest`

> Quem tiver `target/` antigo pode ver `Schema validation: missing table [responsible_addresses]` no `contextLoads` — é classe velha compilada. Rodar `./mvnw clean test`.

### 1.7 Testes
- `RouteServiceTest`: ordem errada rejeitada, ordem certa aceita, swap que inverte rejeitado
- `StudentStopServiceTest`: valida junto com os outros pontos do aluno; `HOME` no ponto da escola rejeitado; mover `HOME` pro ponto da escola rejeitado
- `SchoolStopServiceTest`: escola num ponto com aluno `HOME`/`OTHER` rejeitada

Suíte do backend: **184 testes passando**.

---

## 2. Precisa mudar (ainda não feito)

### 2.1 Endereço do aluno — apagar
Ninguém usa. Só é gravado no cadastro do aluno, geocodificado (gasta chamada na API de mapas) e devolvido uma vez na resposta.
`StudentAddressRepository` não tem nenhuma consulta, nenhum service lê, o front só envia e nunca lê, e os pontos da rota são digitados à mão.
Também não serve de base pra proposta da seção 3, porque fica preso a um aluno só (irmãos teriam que cadastrar o mesmo endereço duas vezes).

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

### 2.2 Escola na rota duplica o endereço
A escola já tem endereço e coordenadas, mas pra colocá-la na rota o motorista cria um ponto digitando o endereço de novo e depois liga a escola nele.
As coordenadas do ponto podem não bater com as da escola, e o `checkNearStop` da trip usa as do ponto.
**Sugestão:** ao ligar a escola, criar/usar o ponto com o endereço da própria escola (ver seção 3, passo 2).

### 2.3 Motorista digita o endereço dos pontos à mão
Hoje qualquer ponto é criado com endereço digitado pelo motorista. Problemas:
- erro de digitação → coordenada errada → `checkNearStop` falha na hora da trip
- o motorista pode colocar a criança num endereço que o responsável nunca autorizou
- ninguém confere a coordenada do ponto

**Sugestão:** seção 3.

### 2.4 Pagamento não usa endereço
`Payment` / `ContractService` ainda não usam endereço nenhum. Quando a cobrança for feita, usar `responsibles.address_id`.

### 2.5 `application.properties`
- [ ] espaços no fim de `spring.profiles.active=local`; o Spring costuma ignorar, mas é bom limpar.

### 2.6 Front
- [ ] mostrar a mensagem dos 400 de rota (ordem embarque/desembarque, `HOME`/`OTHER` em ponto de escola) nas telas de configuração da rota
- [ ] tirar os campos de endereço do aluno quando o item 2.1 for feito

---

## 3. Proposta: o responsável define os endereços, o motorista só escolhe entre eles

**Ideia:** o responsável cadastra quantos endereços quiser pra busca/entrega dos filhos. O motorista não digita endereço: só pode colocar o aluno num ponto cujo endereço foi autorizado por um responsável dele.

### 3.1 Dois tipos de endereço
| Tipo | Quantos | Onde | Pra quê |
|---|---|---|---|
| Cobrança | 1 por responsável | `responsibles.address_id` (já existe) | pagamento/contrato |
| Busca/entrega | N por responsável | tabela nova (ex.: `pickup_addresses`) | pontos `HOME`/`OTHER` da rota |

Cada endereço de busca tem um apelido: "Casa", "Casa da avó", "Inglês de terça".

### 3.2 Por que no responsável e não no aluno
- **Irmãos:** dois filhos na mesma casa usam o mesmo endereço, sem cadastro duplicado (e caem naturalmente no mesmo ponto).
- **Pais separados:** o aluno pode ter mais de um responsável; os endereços permitidos são a **união** dos endereços de todos os responsáveis dele. Casa da mãe e casa do pai aparecem sozinhas.

Tabela sugerida (migration nova, **não** reaproveitar `responsible_addresses`):
```sql
CREATE TABLE pickup_addresses (
    id UUID PRIMARY KEY,
    responsible_id UUID NOT NULL REFERENCES responsibles(user_id),
    address_id UUID NOT NULL REFERENCES addresses(id),
    label VARCHAR(60) NOT NULL,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL DEFAULT now()
);
```

### 3.3 Como os pontos passam a nascer
- **Ponto `HOME`/`OTHER`:** o motorista escolhe um dos endereços autorizados do aluno; o ponto é criado com esse endereço e com as coordenadas já geocodificadas no cadastro do responsável.
- **Ponto de escola:** criado com o endereço da própria escola (resolve o item 2.2).
- Some o campo de endereço livre na criação de ponto.

### 3.4 Regras no backend
- Ligar/mover aluno `HOME`/`OTHER`: o endereço do ponto tem que ser um `pickup_address` ativo de algum responsável do aluno. Senão, 400.
- `SCHOOL` continua como está (ponto da escola do contrato).
- `HOME`/`OTHER` em ponto de escola continua bloqueado (item 1.5).

### 3.5 Casos que vão aparecer
- **Responsável apaga um endereço que está em rota:** recomendado **desativar** (`active = false`) e marcar a rota como pendente, em vez de bloquear — o responsável não fica travado e o motorista vê o que mudou.
- **Responsável edita um endereço:** a coordenada do ponto muda; avisar o motorista pela notificação que já existe (V23).
- **Aluno perde o vínculo com um responsável:** os pontos com endereço desse responsável ficam inválidos → rota pendente.

### 3.6 Ordem de implementação
1. Tabela `pickup_addresses` + CRUD pro responsável (`/api/responsible/pickup-addresses`)
2. Criação de ponto a partir de um `pickup_address` ou de uma escola (sem endereço livre)
3. Validação ao ligar/mover aluno `HOME`/`OTHER`
4. Desativar/editar endereço → rota pendente + notificação ao motorista
5. Apagar `student_addresses` (item 2.1)

---

## 4. Avisos pro grupo

- **Migration que já rodou não se edita nem se renumera.** Mudança de banco sempre numa migration nova com o próximo número (a próxima livre é a **V26**).
- Quem estiver com o banco local antigo vai ter o mesmo erro do Flyway. Pra resolver no ambiente de dev, recriar o banco:
  ```sh
  docker exec vanroute-db psql -U postgres -d postgres -c "DROP DATABASE vanroute_db;" -c "CREATE DATABASE vanroute_db;"
  ```
  Depois é só subir o backend (as migrations rodam do zero). Pra ter dados de exemplo: `backend/api-tests/demo/seed.sh`.
- Depois de puxar esta branch, rodar `./mvnw clean test` (classes apagadas podem ficar no `target/`).
