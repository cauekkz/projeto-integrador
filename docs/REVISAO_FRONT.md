# Revisão geral do front (27/11/2026)

Revisão do frontend Angular em três partes:
1. **Código:** build, testes e cruzamento de rotas, arquivos e chamadas da API.
2. **Uso real no Chrome:** o app foi usado como usuário, do cadastro do filho até a viagem encerrada.
3. **Modo escuro:** o contraste de texto e ícones foi medido tela por tela.

Ambiente: branch `chore/front-imagem-pronta` (com a `develop` mesclada), tudo no Docker (`http://localhost:8080`), Node 22.23.5, Java 21, Chrome 158 em modo celular (390×844, toque).

**Resultado:** nada quebrado nos fluxos principais. Corrigi 3 problemas (seção 4), e a seção 5 lista o que ficou para decidir.

---

## 1. Verificação do código

| O quê | Resultado |
|---|---|
| `ng build` (produção) | compila sem erro. Avisos só de tamanho (seção 5.4) |
| `ng test` | 55 arquivos e **258 testes passando** (no Node 22; no Node 26 falhavam 138 por causa do `localStorage` do próprio Node) |
| Navegações (`router.navigate` e `routerLink`) | todas apontam para rotas que existem em `app.routes.ts` |
| Chamadas `/api/...` do front | todas existem num controller do backend (método e caminho) |
| Arquivos de `public/` usados no código | todos existem |
| Arquivos de `public/` **sem uso** | 16. Removidos (seção 4.3) |

---

## 2. Teste no Chrome, como usuário

O script abre o Chrome, toca nas coordenadas da tela, digita tecla por tecla e envia arquivos pelo seletor. Em cada passo registra erros do console e toda chamada `/api` com erro. Cada passo tem print.

Para não mexer nos dados de quem estava testando pelo celular, os fluxos completos rodaram num par de contas só da revisão: **Motorista E2E** (van `E2E2026`) e **Responsavel E2E** (filha **Lia E2E**).

### 2.1 Responsável (22 passos)

| Passo | Resultado |
|---|---|
| Tela inicial, depois "Sou responsável", depois login | ok |
| Login com senha errada | ok: continua no login e o backend responde 401 "Credenciais inválidas" |
| Login pelo formulário | ok: vai para `/responsible-route` |
| Aba Transporte lista os filhos | ok |
| Tocar no nome abre o menu do perfil | ok |
| Adicionar dependente (nome, nascimento, CEP, número, tipo de vínculo) | ok: o CEP preencheu a rua sozinho, e depois aparece a tela "Código do dependente" |
| Lista de dependentes e detalhe do filho | ok |
| Gerar código de convite para outro responsável | ok: código de 9 caracteres |
| Adicionar outro endereço ao filho ("Casa da avó", CEP) | ok. Repetir o mesmo endereço dá 409 "O aluno já tem esse endereço", que é o esperado |
| Vincular motorista pelo código, depois "Confirmar" e "Abrir conversa" | ok. Resgatar de novo abre a mesma conversa |
| Mandar mensagem no chat | ok |
| Conversas, Contratos, Pagamentos, Histórico, Notificações, Perfil, Início, Termos, Privacidade | todas abrem sem erro |
| "Sair da conta" (pede confirmação) | ok: apaga o token |
| Abrir rota protegida sem login | ok: volta para `/` |

### 2.2 Motorista e responsável juntos (23 passos)

| Passo | Resultado |
|---|---|
| Login do motorista pelo formulário | ok |
| Abrir a conversa e responder | ok: a mensagem do responsável aparece |
| Cadastrar escola (CEP preenche o endereço) | ok |
| Vans, Pix, Grupos, Código do motorista, Início, Perfil | todas abrem, e a van aparece |
| Propor contrato pelo chat: aluno e escola, valor e datas, PDF, "Salvar e enviar" | ok: o cartão "Proposta" aparece no chat |
| Responsável: "Decisão" e "Aceitar contrato" | ok: o cartão vira "Proposta aceita" e as parcelas de R$ 350 aparecem |
| Criar rota "Ida Teste" | ok |
| Pôr a aluna na rota (embarque em casa, ponto novo) | ok |
| Adicionar desembarque (a escola do contrato já vem marcada) | ok |
| Escolher a van da rota | ok |
| "Iniciar rota" | ok |
| Responsável acompanha | ok: mapa com o caminho e "Chega em 4 min" |
| Motorista: "Abrir mapa", depois "Embarcou" e "Encerrar rota" | ok |
| Histórico mostra a viagem | ok |
| Parcelas: "Recebi" e confirmar | ok: a parcela fica `PAID` |

Os erros registrados no console nos dois roteiros foram só estes:
- o 401 do login errado, que é proposital;
- o 409 do endereço repetido;
- o `404 /favicon.ico`, já corrigido (seção 4.2);
- `404 /api/trip/route/{id}/today` antes de a viagem começar (seção 5.2).

---

## 3. Modo escuro

Abri 37 telas em modo escuro e medi o contraste de cada texto e ícone visível contra o fundo real, mínimo 3:1:
- públicas;
- todas do responsável e do motorista;
- menu do perfil aberto;
- responsável sem filhos.

**O único problema** era o ícone de adicionar o primeiro dependente, um SVG com cor fixa `#343941`, escuro sobre fundo escuro. Ele foi corrigido (seção 4.1). Os únicos alertas que sobraram são falsos positivos de ícones feitos com `mask`, em que a cor do ícone é o próprio fundo do elemento.

O modo claro não foi alterado.

---

## 4. O que foi corrigido

### 4.1 Topo do responsável no celular
- **Tocar no nome abria a pesquisa do Google** ("Toque para pesquisar" do Chrome no Android). Os chips clicáveis eram `div` com texto selecionável. Mudanças:
  - em `styles.css`, `user-select: none` e `-webkit-touch-callout: none` em todo clicável que não é `<button>`;
  - `role="button"`, `tabindex="0"` e Enter em `topo-responsavel.html` (nome e dependentes) e `header.html` (motorista e chat).
- **Ícone de adicionar o primeiro dependente**: virou máscara (`.icone-add-dependente`). No claro mantém `#343941`, e no escuro usa `--cor-texto-2`.

### 4.2 Favicon quebrado
O `index.html` pedia `favicon.ico`, que não existia, e dava 404 em toda tela. Agora usa um ícone vazio (`<link rel="icon" href="data:,">`). O projeto ainda não tem um ícone da marca (seção 5.1).

### 4.3 Arquivos sem uso em `public/` (tudo nessa pasta vai para o site)
- **Removidos** (nenhuma referência no código, nos testes ou na documentação):
  - `AddFile.png`, `Add Image.svg`, `Back.png`, `Back.svg`, `cadastroconcluido.png`, `Camera.png`, `Driver.png`
  - `Home Page.svg`, `Icon.png`, `mapa-placeholder.png` (1,8 MB), `PDF.png`, `testee.jpg`, `up.svg`, `van.jpg`, `Vector.png`
- **Removida** a regra `.mapa-img` de `styles.css`, que era da imagem de placeholder.
- **Movido** `inconsistencias-icones-ui.docx` de `public/` para `docs/`. Ele era servido no site para qualquer pessoa.

---

## 5. Pontos de atenção (não corrigidos, para o grupo decidir)

### 5.1 Ícone da aba
Não existe um ícone da marca (a `vanroute-marca.svg` é só o texto "VanRoute", larga demais para a aba). Falta desenhar um `favicon.svg` ou um `favicon.png` de 32×32 e trocar a linha do `index.html`.

### 5.2 404 como resposta normal
`GET /api/trip/route/{id}/today` responde 404 "Nenhuma trip hoje nessa rota" sempre que a rota ainda não começou, e o front usa isso para saber que não há viagem. Funciona, mas suja o console com erro vermelho em toda tela de rota. Uma alternativa é 200 com corpo vazio, ou 204.

### 5.3 Nomes trocados nas rotas do motorista
Em `app.routes.ts`, `/driver-home` carrega o componente `DriverRoutes` ("Suas rotas"), e `/driver-routes` carrega o `DriverHome` ("Qual rota você vai fazer agora?"). Funciona, mas confunde quem mexe no código. Na tela vazia de `/driver-routes`, o botão "Criar rota" leva para `/driver-home`, onde fica a folha de nova rota.

### 5.4 Tamanho do pacote
- O pacote inicial tem **844 kB**, para um limite de 500 kB. React e react-dom (tab-bar, vidro e mapa) não são ESM, e o Angular avisa que isso atrapalha a otimização.
- 13 CSS de componente passam do limite de 4 kB. O maior é `driver-routes.css`, com 6,8 kB.

### 5.5 Encerrar rota com aluno dentro da van
O aviso de encerrar diz só "Quem ainda não foi atendido fica como está", mesmo com aluno embarcado e sem desembarque. Vale dizer quantos alunos ainda estão na van, por exemplo "1 aluno ainda está na van".

### 5.6 Formatação de nome
A diretiva `appNameFormat` deixa só a primeira letra de cada palavra maiúscula e trata "de/da/do/dos/das/e". Efeito colateral:
- "Ana-Maria" vira "Ana-maria";
- "D'Ávila" vira "D'ávila";
- siglas são reduzidas.

### 5.7 Não coberto nesta revisão
- telas em largura de computador;
- modo claro (não foi alterado);
- grupos de alunos;
- ocorrência durante a viagem;
- cancelamento de contrato;
- troca de foto;
- "esqueci a senha" e cadastro de motorista (precisa de CNH digital real);
- envio de mídia no chat.

---

## 6. Dados de teste no banco local

| Conta | Para quê |
|---|---|
| Motorista Teste | testes manuais (van `TST2026`) |
| Responsavel Teste | testes manuais (Ana, Bruno, Carla, Davi e Elisa Teste) |
| Motorista E2E | esta revisão (van `E2E2026`, rotas "Ida Teste", escola "Escola Teste") |
| Responsavel E2E | esta revisão (Lia E2E e Theo E2e, contrato aceito, 1 viagem encerrada) |

Os CPFs e a senha ficam fora deste arquivo, que é público. Para achar as contas no banco: `docker exec vanroute-db psql -U postgres -d vanroute_db -c "select name, cpf from users where name like '%Teste' or name like '%E2E'"`.

Todos ficam só no banco local do Docker (`vanroute_db`). Para apagar tudo, use `docker compose down -v`, que também apaga o resto do banco.
