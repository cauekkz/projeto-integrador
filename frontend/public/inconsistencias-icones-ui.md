# Relatório de Inconsistências Visuais — Ícones e Badges

## 1. Ícones de Formas de Pagamento

### 1.1 Grupo de botões (Pix / Cartão / Boleto)

| Ícone atual | Problema |
|---|---|
| **Pix** — grade de pontinhos dentro de um círculo | Não é o símbolo oficial do Pix. O usuário reconhece o Pix pelo seu logotipo específico (um "laço"/infinito estilizado formado por dois triângulos e um X central), não por um ícone genérico de grade. Isso quebra o reconhecimento imediato da marca. |
| **Cartão** — retângulo com uma linha horizontal | Correto. É a metáfora universalmente usada para cartão de crédito/débito (retângulo com a tarja). Manter. |
| **Boleto** — três barras verticais dentro de um retângulo | Aceitável, mas pode ficar mais claro. Hoje lembra um ícone de "colunas" genérico. Um ícone com barras de código de barras mais finas e numerosas (como um código de barras real) comunica boleto de forma mais direta. |

### 1.2 Seção "Formas de Pagamento" (topo da tela)

Aqui aparece um segundo conjunto de ícones — um losango (◇), um cartão preenchido e um ícone de QR code — para representar, presumivelmente, os mesmos três métodos (Pix, Cartão, Boleto).

**Problema central:** esse conjunto usa um estilo visual diferente do primeiro (outline fino vs. preenchido, tamanhos e pesos de traço diferentes) e ainda troca os símbolos: o losango não comunica "Pix" e o ícone de QR code normalmente é associado a "escanear/pagar via QR", não a boleto. Resultado: o mesmo conceito (formas de pagamento) é representado por dois vocabulários visuais diferentes em telas próximas, o que gera ruído cognitivo — o usuário precisa reaprender o significado dos ícones a cada tela.

### 1.3 Recomendação de padronização

Usar **um único conjunto de ícones**, no mesmo estilo (mesmo peso de traço, mesmo grid, mesma cor de marca), em todas as telas:

- **Pix** → o símbolo oficial do Pix (disponível no [Manual de Marca do Pix](https://www.bcb.gov.br/estabilidadefinanceira/pix) do Banco Central) ou, como alternativa neutra, um ícone de raio (⚡) — amplamente adotado no mercado para representar "pagamento instantâneo".
- **Cartão** → manter o ícone atual do primeiro grupo (retângulo com tarja) — já está correto e é o padrão de mercado.
- **Boleto** → ícone de código de barras (várias barras verticais finas, não três blocos largos) — é a metáfora mais reconhecida para boleto bancário.

Importante: **não usar o ícone de QR code** para representar boleto — QR code é sua própria forma de pagamento (leitura de código), e misturá-lo com boleto confunde o usuário.

## 2. Inconsistência de cores nas badges de serviço

As tags "Vidraceiro" aparecem em pelo menos duas cores diferentes:

- Um badge em tom **vermelho/bordô escuro** com texto branco.
- Outro badge em **rosa claro** com texto bordô.

Duas cores distintas para a mesma categoria de serviço passam a impressão de que são coisas diferentes, ou de que houve um erro de implementação (uso de states diferentes — ex: "normal" vs "erro/alerta" — sem essa intenção).

Além disso, tons de vermelho carregam, por convenção universal de UI, uma conotação de **erro, alerta ou perigo** (campos inválidos, saldo negativo, cancelamento). Usar vermelho para uma badge neutra de categoria de serviço ("Vidraceiro") transmite uma sensação involuntária de que algo está errado, mesmo quando não está.

### Recomendação

- Definir **uma cor única** por tipo de badge (ex: badge de categoria de serviço sempre na mesma cor, badge de tag/tópico como "#MERCO" sempre em outra cor fixa).
- Reservar o vermelho exclusivamente para estados de erro/alerta real.
- Para badges de categoria (como "Vidraceiro"), usar uma cor neutra da paleta da marca — por exemplo um tom de cinza-azulado, ou a cor primária da marca em versão "soft" (fundo claro + texto na cor primária escura), mantendo o mesmo par claro/escuro em todas as telas.

## 3. Ícone do serviço "Colocação de espelhos"

O ícone usado é um quadrado dividido em 4 quadrados menores (grade 2x2), sobre fundo bordô arredondado.

**Problema:** essa forma é a metáfora visual padrão de mercado para **janela** (grade de vidraça), não para **espelho**. Um espelho costuma ser representado por um retângulo ou oval com um brilho/reflexo diagonal, às vezes com uma base/moldura — algo que remeta a reflexo, não a divisão em painéis. Como o ícone atual é ambíguo, o usuário pode associar o serviço a "instalação de janela" em vez de "instalação de espelho", especialmente numa lista com vários serviços de vidraceiro (onde janela e espelho podem ser categorias vizinhas).

**Nota: 3/10** — a forma é limpa e se encaixa bem no padrão visual dos outros ícones (quadrado arredondado + glifo simples), mas falha no critério mais importante de um ícone, que é comunicar o conceito certo sem depender do texto ao lado. Hoje ele só "funciona" porque o rótulo "Colocação de espelhos" está escrito do lado — sem o texto, a leitura natural seria "janela".

**Sugestão:** trocar por um glifo de espelho (retângulo/oval com uma linha diagonal de brilho no canto, ou um espelho de corpo inteiro com moldura), mantendo o mesmo fundo bordô arredondado para preservar a consistência do sistema de ícones de categoria.

## 4. Resumo das ações

1. Substituir o ícone de Pix genérico pelo símbolo oficial (ou ícone de raio) em todas as telas.
2. Unificar o estilo dos ícones de pagamento (mesmo peso de traço, mesmo grid) entre o grupo de botões e a seção "Formas de Pagamento".
3. Trocar o ícone de boleto por um ícone de código de barras.
4. Remover o ícone de QR code do contexto de boleto.
5. Padronizar a cor da badge "Vidraceiro" (e demais badges de categoria) em uma única cor, fora da paleta de vermelho/erro.
6. Substituir o ícone de "janela" (grade 2x2) usado em "Colocação de espelhos" por um glifo que remeta a reflexo/espelho.
