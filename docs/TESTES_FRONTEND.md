# Testes do Front-end

Teste completo do front (Angular) feito em 07/11/2026, na branch `fix/figma-dados-viagem`.
Foram duas partes: os testes automáticos (Vitest) e um teste manual de ponta a ponta no navegador, com dois usuários ao mesmo tempo (responsável e motorista).

## Resumo

| | Resultado |
|---|---|
| Testes automáticos | **215 de 215 passando** (48 arquivos). Depois das correções: **227 de 227** (51 arquivos) |
| Cobertura | Linhas 72,2% · Comandos 66,7% · Ramos 59,3% · Funções 50,3% |
| Fluxos manuais testados | 70+ passos, do cadastro até a viagem terminada |
| Bugs encontrados | **4 que quebram ou atrapalham o uso**, mais 9 menores (textos e visual). **Corrigidos:** B1–B4, M2, M3, M4, M5, M8, M10 (texto da tela), M11. **Ainda abertos:** M6, M7, M9, M12 e M13 (achado no reteste) |
| Não deu para testar | cadastro de motorista (CNH real), cadastro de van (CRLV real), push no celular |

---

## 1. Como montar o ambiente de teste

O teste roda num ambiente separado, sem mexer no backend do IntelliJ (porta 9090) nem no front de desenvolvimento (porta 4200). E-mail e mapas são simulados: nenhum e-mail sai de verdade e nenhuma chamada vai para o OpenRouteService.

1. **Simuladores** (`backend/api-tests/stubs.py`): servidor de e-mail falso em `127.0.0.1:2525`, que grava tudo em `backend/api-tests/out/mail.log`, e mapas falsos em `127.0.0.1:8089`.
   ```bash
   cd backend/api-tests && python -I stubs.py
   ```
2. **Backend de teste na porta 9091**, apontando para os simuladores:
   ```bash
   cd backend
   SERVER_PORT=9091 SPRING_MAIL_HOST=127.0.0.1 SPRING_MAIL_PORT=2525 \
   SPRING_MAIL_USERNAME=stub SPRING_MAIL_PASSWORD=stub \
   SPRING_MAIL_PROPERTIES_MAIL_SMTP_STARTTLS_ENABLE=false \
   ORS_GEOCODE_URL=http://127.0.0.1:8089/pelias/v1/search \
   ORS_DIRECTIONS_URL=http://127.0.0.1:8089/v2/directions/driving-car \
   TRIP_ETA_REFRESH_SECONDS=3 TRIP_OFF_ROUTE_MIN_SECONDS=0 \
   java -jar target/backend-0.0.1-SNAPSHOT.jar
   ```
3. **Proxy de teste** (`proxy-teste.json`): igual ao `proxy.conf.json`, mas `/api` e `/ws` vão para `localhost:9091`, com o cabeçalho `Origin: http://localhost:4200` (o CORS do backend só libera a 4200).
4. **Dois fronts**: um na 4300 (responsável) e outro na 4301 (motorista). Como são origens diferentes, cada um guarda seu próprio login no `localStorage`, e dá para usar os dois perfis ao mesmo tempo no mesmo navegador.
   ```bash
   cd frontend
   npx ng serve --port 4300 --proxy-config proxy-teste.json
   npx ng serve --port 4301 --proxy-config proxy-teste.json
   ```
5. **Testes automáticos com cobertura** (o pacote de cobertura é instalado sem entrar no `package.json`):
   ```bash
   cd frontend
   npm i --no-save @vitest/coverage-v8
   npx ng test --watch=false --coverage
   ```

### Dados que foram criados direto no banco (e por quê)

| O quê | Por quê |
|---|---|
| Motorista aprovado (`users` + `drivers` + `terms_acceptances`) | O cadastro exige uma CNH digital real, assinada pelo ICP-Brasil. O SQL é o mesmo do `seed_driver` em `backend/api-tests/run-api-tests.sh`. |
| Van (`vehicles` + `driver_vehicles`) | O cadastro exige um CRLV digital real. |
| Contrato passado de `SCHEDULED` para `ACTIVE` com início hoje | Rota e viagem só aceitam aluno com contrato ativo, e o contrato começava dois dias depois. |
| Aceite dos termos voltado para a versão 2 | Para forçar a tela de "os termos mudaram" (reaceite). |

O código de verificação de e-mail foi lido do `mail.log` do simulador. Para simular o GPS, o `navigator.geolocation` foi trocado por uma posição controlada por script, e a van foi "andando" até a casa e depois até a escola.

---

## 2. Testes automáticos

`npx ng test --watch=false --coverage`: **48 arquivos, 215 testes, todos passando.**

Depois das correções (seção 4.1): **51 arquivos, 227 testes, todos passando**, e o build (`npx ng build`) compila. Testes novos:

| Arquivo | O que confere |
|---|---|
| `shared/button/button.spec.ts` | O botão é `type="button"` por padrão e vira `submit` quando pedido (B1) |
| `components/login-signup/login-form/login-form.spec.ts` | "Entrar" manda o login uma vez só; "Esqueceu sua senha?" não envia o formulário (B1) |
| `components/login-signup/forgot-password/forgot-password.spec.ts` | Depois da resposta do e-mail, a tela passa sozinha para o código (B2) |
| `shared/pop-up/pop-up.service.spec.ts` | Lê a mensagem do JSON que chega como texto; o 429 vira "Aguarde" (B4) |
| `shared/code-input/code-input.spec.ts` | Digitar por cima de uma caixa preenchida troca o número (M2) |
| `shared/markdown-text/markdown-text.spec.ts` | Nenhum espaço entre o negrito e a pontuação (M3) |
| `services/dto/driver/pix.dto.spec.ts` | Chave Pix com máscara (CPF, CNPJ, telefone); e-mail e aleatória ficam como vieram (M8) |
| `pages/driver-groups/driver-groups.spec.ts` | Ajustado para o texto novo do botão "Avisar que não vou buscar hoje" (M10) |

A cobertura não foi medida de novo depois das correções.

| Medida | Cobertura |
|---|---|
| Linhas | 72,18% (5265/7294) |
| Comandos | 66,68% (7422/11130) |
| Ramos | 59,34% (2974/5011) |
| Funções | 50,31% (1054/2095) |

Partes com menos cobertura, que pedem testes novos:

| Arquivo | Linhas cobertas |
|---|---|
| `pages/profile-info` | ~25% |
| `shared/code-input` | ~28% |
| `components/login-signup/email-code` | ~37% |
| `components/chat/chat-list` | ~40% |
| `components/student/add-student` | ~51% |
| `components/student/student-photo` | ~51% |
| `pages/terms` | ~61% |
| `components/login-signup/forgot-password` | sem teste próprio (veja o bug B2) |

---

## 3. Testes manuais, fluxo por fluxo

Legenda: ✅ funcionou · ⚠️ funcionou com problema · ❌ quebrado · ⛔ não deu para testar

### 3.1 Entrada e cadastro (responsável)

| Passo | Resultado |
|---|---|
| Tela inicial (escolher Motorista ou Responsável) | ✅ |
| Login com senha errada → "Acesso negado · Credenciais inválidas." | ✅ |
| Cadastro: máscaras de CPF, telefone e CEP | ✅ |
| CEP preenche rua, bairro, cidade e UF (ViaCEP) e pula para o Número | ✅ |
| Criar conta sem aceitar os termos → aviso "Termos de Uso · Aceite os Termos…" e não envia | ✅ |
| Link dos termos abre a página cheia, formatada, "Versão 3", com "Li e aceito" e "Fechar" | ✅ |
| "Li e aceito" marca a caixinha e mantém o formulário preenchido | ✅ |
| Cadastro enviado → tela de código do e-mail | ✅ |
| Código errado → "Código incorreto. Restam 4 tentativas." | ✅ |
| Reenviar código antes de 1 minuto | ⚠️ aviso genérico (bug B4) |
| Corrigir o código depois de errar | ⚠️ (bug M2) |
| Código certo → volta para o login | ✅ |
| Login → home do responsável vazia, com "Adicione seu filho" | ✅ |
| Login manda **duas** requisições `POST /auth/login` | ❌ (bug B1) |
| Tela protegida sem login (`/driver-home`) → volta para `/` | ✅ |
| Endereço que não existe → página 404 com "Voltar ao início" | ✅ |

### 3.2 Filho, endereços e responsáveis

| Passo | Resultado |
|---|---|
| Adicionar filho vazio → "Campos Incompletos · Preencha nome, nascimento, tipo de relação, CEP, rua e cidade." | ✅ |
| Adicionar filho completo → código gerado, com "Copiar" | ⚠️ texto errado (M4) e botões por cima dos campos (M5) |
| Lista de dependentes e detalhe do aluno | ✅ |
| Tirar o **único** endereço → "O aluno precisa de pelo menos um endereço" (409) | ✅ |
| Adicionar endereço (CEP preenche os campos) → "Casa da avó foi adicionado." | ⚠️ o foco não pula para o Número (M6) |
| Tirar o segundo endereço → "Endereço tirado · O motorista foi avisado." | ✅ |
| Convidar responsável (papel Financeiro) → código de 9 letras, válido por 15 min | ✅ |
| Segundo responsável usa o código → passa a ver o filho | ✅ (texto confuso na tela, M7) |
| Passar o admin → a tela muda na hora (quem passou perde os controles e ganha "Deixar de ser responsável") | ✅ |
| Devolver o admin e remover o outro responsável → "Bruno… não é mais responsável de Lucas." | ✅ |
| O responsável removido volta a ver a home vazia | ✅ |
| Avisar falta (escolher a data) → "O motorista foi avisado que Lucas não vai em 09/11." | ✅ |
| Desfazer falta ("Lucas vai sim") | ✅ |

### 3.3 Motorista: cadastros

| Passo | Resultado |
|---|---|
| Cadastro de motorista | ⛔ precisa de CNH digital real |
| Login do motorista → home "Você ainda não tem rotas" | ✅ |
| Escolas: lista vazia → adicionar (o CEP preenche) → aparece na lista | ✅ |
| Vans: a van aparece com placa, modelo, lugares e licenciamento | ✅ |
| Cadastrar van nova | ⛔ precisa de CRLV digital real |
| Pix: chave CPF inválida → "Chave Pix inválida para o tipo CPF" | ✅ (o campo não aplica a máscara de CPF, M8) |
| Pix: salvar chave e-mail → mostra a chave, "Trocar chave" e "Remover chave" | ✅ |
| Código de conversa (`TST-D79-93K`) | ✅ |

### 3.4 Conversa (chat)

| Passo | Resultado |
|---|---|
| Lista vazia → "Vincular motorista" | ✅ |
| Código errado → "Código inválido ou não encontrado." | ✅ |
| Colar o código com traços → preenche as 9 caixas | ✅ |
| Código certo → "Motorista vinculado!" com "Abrir conversa" e "Ver conversas" | ✅ |
| Mensagem chega na hora do outro lado (WebSocket), nos dois sentidos | ✅ |
| ✓✓ azul quando a mensagem é lida | ✅ |
| Anexo de imagem → aparece dos dois lados | ✅ |
| Ícone de contrato no topo da conversa | ❌ não faz nada (bug B3) |

### 3.5 Contrato

| Passo | Resultado |
|---|---|
| Clipe → "Criar contrato" → etapa 1 (dependente e escola) | ✅ |
| Etapa 2 vazia → erros em valor e datas | ✅ |
| Etapa 2 preenchida (R$ 450,00 mensal, 09/11/2026 a 09/05/2027) | ✅ |
| Etapa 3 sem PDF → "Anexe o PDF do contrato." | ✅ |
| PDF anexado → "Proposta enviada", com "Retirar proposta" | ✅ |
| A proposta chega na hora para o responsável → "Decisão" → Aceitar | ✅ |
| Os dois lados mudam para "Proposta aceita · Agendado: começa em 09/11" | ✅ |
| Lista de contratos (responsável) | ✅ |
| Detalhe do contrato: dados, PDF (link de 15 min), assinaturas, responsáveis | ✅ |
| Pedir cancelamento (motivo obrigatório) → "Cancela em 07/12" + histórico | ✅ |
| Desistir do cancelamento → contrato volta a ficar ativo | ✅ |

### 3.6 Parcelas

| Passo | Resultado |
|---|---|
| 7 parcelas geradas, dos dois lados | ✅ (datas sem ano, M9) |
| Motorista: "Recebi" (escolher Pix) → "Paga em 07/11 · Pix" | ✅ |
| Motorista: "Dispensar" → parcela dispensada | ✅ |
| Filtro "Pagas" | ✅ |
| Responsável vê "Paga" e "Dispensada" | ✅ |
| "Copiar chave Pix" → "Chave Pix copiada" | ✅ |
| "Avisar no chat" → abre a conversa | ✅ |

### 3.7 Rota e viagem (GPS simulado)

| Passo | Resultado |
|---|---|
| Nova rota "Ida manhã" | ✅ |
| Escolher van padrão | ✅ |
| Adicionar aluno (3 passos): embarque em casa | ✅ |
| Sem desembarque → "A viagem ainda não pode iniciar · falta desembarque…" | ✅ |
| Adicionar desembarque na escola → "Rota pronta · A viagem já pode iniciar." | ✅ |
| Iniciar rota → aviso "Deixe esta tela aberta" → mapa, "Próximo ponto em 5 min" | ✅ |
| Responsável: "Acompanhar" → mapa com a van, "A caminho", "Chega em 5 min" | ✅ |
| A van anda → no mapa do responsável a van se mexe e o ETA cai para 4 min | ✅ |
| "Embarcou" longe do ponto → "A van está a 1,5 km do ponto; chegue a menos de 1,0 km" | ✅ |
| "Embarcou" perto do ponto → o próximo ponto vira a escola, dos dois lados | ✅ |
| Ocorrência (Trânsito) → "Ocorrência registrada · Os responsáveis foram avisados." | ✅ |
| "Desceu" na escola → responsável vê "Lucas foi entregue às 18:25" | ✅ |
| Finalizar rota → "Rota de hoje finalizada" | ✅ |
| Histórico: "Entregue · Embarcou às 18:25 · Chegou às 18:25 · 1 ocorrência" | ✅ |
| Tela de ocorrências da viagem | ✅ |

### 3.8 Grupos e notificações

| Passo | Resultado |
|---|---|
| Criar grupo → adicionar aluno → "Avisar o grupo" → "1 responsável avisado." | ✅ |
| O aviso chega nas notificações do responsável | ⚠️ texto vem do servidor como "não vai buscar … hoje" (M10) |
| Notificações: todas as etapas da viagem, parcela, proposta | ✅ |
| "Marcar todas como lidas" → contador zera | ✅ |

### 3.9 Perfil, senha, privacidade e termos

| Passo | Resultado |
|---|---|
| Menu do perfil pelo topo (nome e e-mail certos) | ✅ |
| Abrir `/profile-select` direto pelo endereço | ⚠️ mostra "Usuário / exemplo@example.com" (M11) |
| Trocar senha pelo perfil (código no e-mail) → sai da conta | ✅ |
| Senha antiga recusada, senha nova entra | ✅ |
| "Esqueceu sua senha?" no login | ❌ (bugs B1 e B2) |
| Exportar meus dados → `meus-dados.json` + item desligado "Disponível de novo em 5 minutos" | ✅ |
| Exportar de novo antes de 5 min → servidor responde 429 | ✅ |
| Excluir conta com senha errada → "Dados inválidos · Senha incorreta" | ✅ |
| Excluir conta com contrato ativo → bloqueia e mostra "Ver contratos" | ✅ |
| Excluir conta sem pendências → "Conta excluída" e volta ao início | ✅ |
| Termos mudaram → o app manda para `/terms` | ✅ |
| "Não aceito" → "Sem aceitar, não dá para usar o app" | ✅ |
| Sem aceitar, outra tela (`/payments`) volta para `/terms` | ✅ |
| "Aceito" → grava a versão 3 e volta para a home | ✅ |

---

## 4. Bugs encontrados

### Que quebram ou atrapalham o uso

**B1. Botões dentro de formulário disparam duas vezes** ✅ corrigido
- **Onde:** `shared/button/button.html`. O `<button>` não recebe `type`, então dentro de um `<form (ngSubmit)>` ele vira `submit`.
- **Efeito:** o clique chama `(clickEvent)` e o `(ngSubmit)` também.
  - No login saem **duas** `POST /auth/login`.
  - "Esqueceu sua senha?" envia o formulário de login e mostra "Campos Obrigatórios · Por favor, preencha o CPF e a senha para entrar."
- **Afeta:** qualquer tela que usa `app-button` com `(clickEvent)` dentro de um `<form (ngSubmit)>`.
- **Correção:** o `<button>` agora recebe `[type]` (padrão `"button"`). "Entrar" e "Enviar código" usam `type="submit"` e só o `(ngSubmit)`.

**B2. "Esqueci a senha" trava em "Enviando..."** ✅ corrigido
- **Onde:** `components/login-signup/forgot-password/forgot-password.ts`.
- **Causa:** o componente usa campos comuns (`enviando`, `etapa`), e o app roda sem zone.js. Quando a resposta chega, a tela não atualiza e o console mostra `NG0100 ExpressionChangedAfterItHasBeenChecked ('Enviando...' → 'Enviar')`.
- **Efeito:** com resposta 200 ou 429, a tela nunca passa para o código, e não dá para recuperar a senha pelo login.
- **Correção:** `markForCheck()` quando a resposta chega, no `forgot-password`, no `login-form` e no `profile-info`.
- **Mesmo risco:** o `login-form` (`enviandoCodigo`, `etapaEmail`, `mostrarForgotPassword`) e o `profile-info` (`mensagem`, `salvando`) usam o mesmo padrão.

**B3. Ícone de contrato no topo do chat não faz nada** ✅ corrigido
- **Onde:** `components/chat/chat-details/chat-details.html:11`. O `<button class="contrato-btn">` não tem `(click)`.
- **Efeito:** o motorista só acha "Criar contrato" pelo clipe.
- **Correção:** o ícone abre a lista de contratos (`/contracts`), que existe para os dois perfis. "Criar contrato" continua no clipe.

**B4. Reenviar código cedo demais: aviso genérico** ✅ corrigido
- **Onde:** `authService.sendCode` usa `responseType: 'text'`, e o interceptor não lê o JSON que vem como texto.
- **Efeito:** o servidor manda "Aguarde 39 segundos para pedir outro código", mas a tela mostra "Erro · Ocorreu um erro ao processar a requisição."
- **Afeta:** as outras chamadas com `responseType: 'text'` (verify-email, forgot-password).
- **Correção:** `mensagemDoBackend` lê o JSON que chega como texto, e o 429 vira o aviso "Aguarde" com a mensagem do servidor.

### Menores (textos e visual)

| # | Onde | O quê |
|---|---|---|
| M2 | `shared/code-input` | Depois de um código errado, digitar por cima não troca os números: precisa apagar caixa por caixa. ✅ corrigido |
| M3 | `shared/markdown-text` | Sobra um espaço antes do ponto depois de negrito ("não repassa dinheiro ."). ✅ corrigido (quebra de linha do template) |
| M4 | `add-student.html:125` | Depois de criar o filho: "Envie este código para o **motorista** vincular o dependente". O código é para outro **responsável** (usado em "Tenho um código"). ✅ corrigido |
| M5 | `add-student` | Os botões "Pronto" e "Tenho um código" ficam presos por cima dos campos (Cidade e Estado ficam escondidos até rolar). O título da tela é "Suas informações", mas é o cadastro do filho. ✅ corrigido (botões no fim do formulário; títulos "Adicionar dependente" e "Código do dependente") |
| M6 | Novo endereço (detalhe do aluno) | Depois do CEP, o foco não pula para o Número (no cadastro pula). |
| M7 | `/student-code` | O texto diz "Digite seu código para vincular ao seu responsável", mas quem usa é o responsável vinculando o filho. |
| M8 | `/driver-pix` | Com o tipo CPF, o campo mostra a máscara no placeholder mas não formata o que é digitado. ✅ corrigido (CPF e CNPJ formatam enquanto digita e vão só com os números; a chave salva aparece com máscara no cartão do motorista e nas parcelas do responsável) |
| M9 | `/payments` | Vencimentos sem ano ("09/01", "09/02"): num contrato que passa de um ano para outro, fica ambíguo. |
| M10 | Servidor (só documentado) | O aviso de grupo vira a notificação "avisou que não vai buscar Lucas Teste hoje. Motivo: …", mesmo quando o aviso é sobre outro assunto ou outro dia. ✅ o aviso é só para "não vou buscar hoje": o botão e a folha agora dizem isso ("Avisar que não vou buscar hoje"; recados vão pela conversa). O servidor não mudou. |
| M11 | `/profile-select` | Aberto direto pelo endereço, mostra "Usuário / exemplo@example.com" (pelo menu do topo funciona). ✅ corrigido (busca os dados do usuário; fechar volta para a home) |
| M12 | Email-code | O e-mail aparece **acima** de "digite o código que enviamos agora para:" (a ordem está invertida). |
| M13 | `/profile-info` | Achado no reteste: a mensagem "Código para trocar a senha enviado..." aparece abaixo do botão "Atualizar senha", fora da parte visível da tela. Só dá para ver rolando. |

---

## 4.1 Reteste depois das correções (07/11/2026)

Mesmo ambiente de teste da seção 1, com a Ana (responsável, porta 4300) e o Carlos (motorista, porta 4301). Cada item corrigido foi refeito no navegador, olhando as chamadas ao servidor e o console.

| Item | O que foi feito | Resultado |
|---|---|---|
| B1 | "Esqueceu sua senha?" no login | ✅ Abre a recuperação de senha. Não aparece o aviso "preencha o CPF" e nada é enviado ao servidor |
| B1 | Login com CPF e senha ("Entrar") | ✅ **Uma** `POST /auth/login` (200), vai para a home. No motorista, apertar Enter também entra |
| B2 | Esqueci a senha: e-mail → código → nova senha | ✅ Passa para o código assim que o servidor responde (200), sem `NG0100` no console. Código aceito e senha trocada: "Tudo certo · Senha alterada!" e volta para o login |
| B4 | "Reenviar código" logo em seguida | ✅ O 429 agora mostra "**Aguarde** · Aguarde 52 segundos para pedir outro código" |
| M2 | Digitei um código errado (111111), cliquei na primeira caixa e digitei o certo | ✅ As caixas ficaram 560024: o teclado trocou número por número |
| B3 | Ícone no topo do chat (responsável) | ✅ Abre `/contracts` com o contrato desse motorista |
| M3 | `/terms` | ✅ "não repassa dinheiro. O responsável...": nenhum espaço antes de pontuação no texto todo |
| M4/M5 | Cadastrar filho ("Bia Teste Reteste") | ✅ Título "Adicionar dependente". "Pronto" e "Tenho um código" ficam depois de "Tipo de relação" e não cobrem mais os campos. Depois de salvar: título "Código do dependente" e o texto "Envie este código para outro responsável de **Bia Teste Reteste**. Ele digita o código em 'Tenho um código'..." |
| M8 | Chave Pix: troquei para CPF e digitei só os números | ✅ O campo formata enquanto digita (805.260.606-38) e o servidor recebe só os números (200). **Acrescentado no reteste:** a chave salva também aparece com máscara no cartão do motorista e nas parcelas do responsável (antes aparecia `80526060638`). "Copiar chave Pix" continua copiando como veio do servidor |
| M10 | Grupo do motorista | ✅ Botão "Avisar que não vou buscar hoje" e folha "Não vou buscar hoje", que manda outros recados para a conversa. Enviado com motivo: "1 responsável avisado", e a notificação chegou para a Ana |
| M11 | `/profile-select` aberto pelo endereço | ✅ Mostra "Ana Teste Responsavel" e o e-mail real, com o menu de responsável. Tocar fora volta para `/responsible-home`. Aberto pela home, fecha normal e continua na home |
| B2 (perfil) | Perfil: dados e "Enviar código por e-mail" | ✅ Os dados carregam sozinhos. Depois do envio (200) aparece "Código para trocar a senha enviado para o seu e-mail." |

**Visto no reteste (não corrigido):**
- No perfil, a mensagem "Código enviado" aparece **abaixo** do botão "Atualizar senha", fora da parte visível da tela. Só dá para ver rolando.

**Arquivos alterados** (todos em `frontend/src/app/`; o servidor não foi alterado):

| Correção | Arquivos |
|---|---|
| B1 | `shared/button/button.html`, `components/login-signup/login-form/login-form.html` |
| B2 | `components/login-signup/forgot-password/forgot-password.ts`, `components/login-signup/login-form/login-form.ts`, `pages/profile-info/profile-info.ts` |
| B3 | `components/chat/chat-details/chat-details.html` e `.ts` |
| B4 | `shared/pop-up/pop-up.service.ts` |
| M2 | `shared/code-input/code-input.ts` |
| M3 | `shared/markdown-text/markdown-text.html` |
| M4/M5 | `components/student/add-student/add-student.html` e `.css` |
| M8 | `services/dto/driver/pix.dto.ts`, `pages/driver-pix/driver-pix.ts` e `.html`, `pages/payments/payments.ts` e `.html` |
| M10 | `pages/driver-group/driver-group.html` |
| M11 | `components/profile-select/profile-select.ts` e `.html` |

**Dados de teste que mudaram:** a senha da Ana foi trocada para a mesma `Senha@123`, foi criado o filho "Bia Teste Reteste", a chave Pix do Carlos virou CPF (antes era e-mail) e foi enviado um aviso para o grupo "Paulista manhã".

---

## 5. O que não deu para testar

| O quê | Por quê |
|---|---|
| Cadastro de motorista | Exige CNH digital real, assinada pelo ICP-Brasil. O motorista foi criado direto no banco. |
| Cadastro de van | Exige CRLV digital real. A van foi criada direto no banco. |
| Contrato ficar ativo sozinho na data de início | Depende do agendamento do servidor. A data foi adiantada no banco. |
| GPS de verdade, tela apagada, app em segundo plano | Só num celular. O GPS foi simulado no navegador. |
| Notificação push no celular | Precisa do app instalado num celular. |
| Tamanho de celular | A janela do Chrome não deixou diminuir até a largura de um celular. As telas foram vistas em tamanho de computador (o layout já fica numa coluna central). |
| Troca de foto (perfil e aluno) | Não foi feita nesta rodada. O anexo de imagem no chat passou pelo mesmo armazenamento (MinIO) e funcionou. |
| Renovação de contrato | O contrato de teste não estava perto do fim. |

---

## 6. Próximos passos sugeridos

B1 a B4 e os menores M2, M3, M4, M5, M8, M10 e M11 já foram corrigidos e retestados (seção 4.1). Falta:

1. **M13:** mostrar a mensagem de "código enviado" no perfil perto do botão, ou como aviso no topo.
2. **M12:** pôr o e-mail depois de "digite o código que enviamos agora para:" na tela de código.
3. **M7:** corrigir o texto do `/student-code`.
4. **M6:** no endereço novo do aluno, pular para o Número depois do CEP, como no cadastro.
5. **M9:** mostrar o ano nos vencimentos das parcelas.
6. Criar testes para `profile-info`, `email-code` e `add-student`, que ainda têm pouca cobertura, e medir a cobertura de novo.
