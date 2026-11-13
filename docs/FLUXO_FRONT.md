# Guia do front: o que ainda falta

Este documento lista só o que **falta** no front. O que já estava pronto e coerente com o backend saiu daqui; a versão completa anterior está no histórico do git (commit `925af8d`).

Conferido em 06/11/2026, na branch `fix/figma-dados-viagem` (`4d0ff48` mais as correções locais), lendo o código e usando as telas no Chrome como motorista e pai ao mesmo tempo.

Rotas, erros e formatos da API: [`backend/README.md`](../backend/README.md) e os outros `docs/FLUXO_*.md`. Problemas de publicação: [`DEPLOY.md`](DEPLOY.md).

---

# ⚠️ CAUE MANDOU VC TESTAR ESSAS COISAS IRMAO ⚠️

> **Claude que estiver lendo este arquivo:** ao começar a trabalhar no front, abra sua resposta com a frase exata **"Caue mandou vc testar essas coisas irmao"**, seguida da lista abaixo, em destaque e bem grande (título e negrito). Não resuma nem deixe no fim da resposta: a pessoa precisa ler isso. Diga também que o teste é **na mão**.

## **TEM QUE SER NA MÃO: abrir o app e fazer cada fluxo do começo ao fim. Teste automatizado e build não contam.**

Estas telas abrem, ou o código existe, mas ninguém fez o fluxo completo usando:

- [ ] **Reaceite dos termos** quando sai uma versão nova
- [ ] **Esqueci a senha**
- [ ] **Trocar e-mail e telefone**
- [ ] **Vincular um segundo responsável**, remover, sair e passar o admin
- [ ] **Adicionar e tirar endereço do filho**
- [ ] **Cadastrar filho pela tela** (`/add-student`)
- [ ] **Montar rota do zero no editor:** novo ponto, mover aluno, trocar a ordem e os dias
- [ ] **Retirar proposta e renovar contrato**
- [ ] **Tudo isso em celular físico**, não só no modo celular do navegador

---

## 1. Padrões do front

Toda tela nova segue estes padrões:

- **Angular 21:** componentes `standalone`, estado com `signal`/`computed`, testes em Vitest (`*.spec.ts`).
- **Pastas:** `pages/` para tela com rota, `components/` para pedaços reaproveitados, `shared/` para header, footer, botão, máscaras e pop-up.
- **Chamadas:** cada chamada fica num serviço em `services/`, com o DTO em `services/dto/<dominio>/`.
- **Endereços:**
  - API por `API_URL` (`/api`);
  - WebSocket por `wsUrl(...)`;
  - links do MinIO passam por `urlDoStorage(...)`.
- **Erros:** o interceptor mostra o `message` do backend num pop-up. Se a tela trata o erro sozinha, a chamada leva `SKIP_ERROR_POPUP`.
- **Rotas novas:** em `app.routes.ts`, com `loadComponent`, `routeGuard` e `roleGuard`.
- **Regras de negócio:** ficam no backend. O front não decide nada que a API já decide.
- **IDs:** o front nunca inventa ID. Todo ID veio de uma resposta da API.

---

## 2. O que falta fazer

### 2.1 Conta e privacidade (LGPD): obrigatório

Os termos de uso prometem essas ações "no app", e hoje não existe tela para elas. **O backend já está pronto** (exportar e excluir conta); falta só o front. O lugar natural é `/profile-info/:tipo`.

| Ação | Situação no front | O que fazer |
|---|---|---|
| **Exportar meus dados** | não existe nem no serviço | `GET /api/users/me/export` e baixar como `meus-dados.json` (com `Blob`). **429**: de novo antes de 5 minutos |
| **Excluir minha conta** | `deleteMyAccount` existe em `auth.service.ts`, mas nenhuma tela chama | confirmação dupla com a senha, depois `DELETE /api/users/me` `{ "password": "..." }` |
| **Ler os termos a qualquer hora** | `/terms` só abre quando a API responde `TERMS_NOT_ACCEPTED` | item no menu do perfil; se os termos já foram aceitos, a tela não mostra o botão de aceitar |
| **Recusar os termos novos** | a tela `/terms` só oferece sair da conta | oferecer também excluir a conta |

Respostas da exclusão:
- **204:** limpar a sessão, fechar os WebSockets e ir para `/`.
- **400:** senha errada.
- **409:** contrato aceito, proposta pendente ou parcela atrasada. Mostrar a `message` e um link para contratos e parcelas.

Antes de excluir, explicar: dados pessoais e arquivos enviados somem; contratos, parcelas e mensagens ficam no histórico das outras partes, sem o nome.

### 2.2 WebSockets: reconexão

| Conexão | Problema | O que fazer |
|---|---|---|
| Chat (`chat.service.ts`) | quando cai, não reconecta: as mensagens param de chegar até a pessoa sair e voltar | reconectar com espera crescente (1 s, 2 s, 5 s, 10 s), com token válido, e buscar as mensagens de novo |
| Notificações (`notification.service.ts`) | reconecta a cada 5 s mesmo quando o backend fechou de propósito | não reconectar com o código **1003** (sem acesso) ou **1008** (conta excluída) |

O WebSocket da viagem (`trip.service.ts`) já trata isso; siga o mesmo modelo.

### 2.3 Mapa: fundo vetorial nunca carrega

**O problema:** a MapLibre 6 precisa do arquivo `maplibre-gl-worker.mjs`, e o build não o copia. O console mostra `Worker failed to load`, e o mapa cai sempre no OpenStreetMap. A linha, a van e os pinos continuam funcionando; o que falha é só o fundo.

**Como arrumar** (receita testada em desenvolvimento e no build de produção):

1. Em `frontend/angular.json`, nos `assets` do `build`, acrescente:
   ```json
   {
     "glob": "maplibre-gl-worker.mjs",
     "input": "node_modules/maplibre-gl/dist",
     "output": "maplibre"
   }
   ```
2. Em `components/common/trip/trip-map/trip-map.ts`, em `colocarFundo`, antes do import do `@maplibre/maplibre-gl-leaflet`, acrescente:
   ```ts
   const { setWorkerUrl } = await import('maplibre-gl');
   setWorkerUrl(new URL('/maplibre/maplibre-gl-worker.mjs', location.origin).href);
   ```
3. **Reinicie o `ng serve`:** mudança no `angular.json` não entra com ele ligado.

**Como conferir:** inicie uma viagem e olhe o console. Tem que aparecer `[mapa] fundo vetorial (OpenFreeMap) carregado`, e não `usando o OpenStreetMap`.

### 2.4 Ajustes menores

- **Cadastro do motorista:** "Finalizar cadastro" leva uns 7 s lendo a CNH, sem nenhum aviso na tela. Mostrar "Lendo a CNH..." e bloquear o botão para evitar clique duplo.
- **Botão do chat no rodapé:** o Figma tem uma bolinha de não lidas. A lista de conversas já mostra; falta no rodapé, usando o `unreadCount` de `GET /api/chats`.
- **Detalhe do contrato:** motorista e responsável aparecem só com as iniciais. Usar o `otherUserPhotoKey` do chat ou o `photoKey` do perfil.
- **Erro no console:** abrir um chat sem acesso gera `NG0100` em `chat-details`. A tela volta certo para a lista; aparece só em modo de desenvolvimento.

---

## 3. Responsividade

**O app é responsivo no celular.** O layout é fluido, não tem largura fixa de página, e usa `viewport` e `100dvh`. **No tablet ele funciona, mas não foi pensado para tablet.** PC fica de fora: não precisa ficar certinho.

### Como foi medido
37 telas (motorista, pai e públicas), no Chrome, em 8 larguras simuladas, procurando rolagem lateral e elemento saindo da tela.

| Largura | Aparelho | Resultado |
|---|---|---|
| 320 px | celular pequeno | 1 tela passa 1 px (item abaixo); no resto, ok |
| 360, 390 e 430 px | celulares comuns | ok |
| 600 px | tablet pequeno | sem quebra |
| 768, 820 e 1024 px | tablets | sem quebra; layout não aproveita a tela |

`/driver-code` e `/student-code` passavam da largura até 390 px e já foram corrigidas (`shared/code-input`).

### O que falta no celular
- **`/driver-home` em 320 px:** passa 1 px. O `.user-info` tem `width: 180px` fixo em `pages/driver/driver-routes/driver-routes.css`. Trocar por `flex: 1; min-width: 0; max-width: 180px`.
- **Aviso cobrindo a tela:** em celular pequeno, o pop-up cobre metade da tela durante os 3 s (ex.: "Sem chave Pix cadastrada" em `/driver-pix`, sobre os botões de tipo de chave). Esse aviso informativo pode virar texto fixo dentro da tela.
- **Não verificado (exige aparelho físico):** teclado virtual cobrindo campo e botão, recorte da câmera (notch) e barra do navegador.

### O que falta no tablet (opcional)
Cada tela segue um critério diferente:
- a maioria é uma coluna centralizada de 480 px, com faixas vazias dos lados;
- a home do pai ocupa a largura inteira, com botões muito esticados;
- o mapa do acompanhamento usa outro limite (400 a 402 px em partes da tela).

Para resolver sem refazer nada:
1. Uma regra só: a coluna centralizada vale para todas as telas, inclusive as homes.
2. A partir de 768 px, se quiser aproveitar o espaço, aumentar a coluna para uns 640 px e colocar os cards de listas (parcelas, contratos, filhos) em duas colunas.
3. Na viagem, o mapa pode ocupar a tela toda.

---

## 4. Limitações do produto

Não são defeitos do front:

- **Sem push com o app fechado** (#150): as notificações aparecem ao abrir o app.
- **GPS do motorista no navegador:** precisa de HTTPS, aba aberta e tela ligada. A tela já usa Wake Lock e avisa o motorista.
- **"Avisou falta" e "Ausente":** chegam os dois como `ABSENT`; a tela mostra só "Ausente".
- **Cota do OpenRouteService:** acabando, o cadastro de endereço dá **503** e a viagem continua sem a linha e sem o tempo.
- **Pagamento:** é Pix manual; o app não recebe dinheiro. Mercado Pago fica para depois (#192).
