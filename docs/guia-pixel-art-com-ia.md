# Pixel art e Telas com uma IA

*[English version](pixel-art-ai-guide.md)*

Este guia ensina a uma IA de chat (Claude, ChatGPT e outras) o texto que a **Tela de Sinal** do Wireless Automate lê.
Com ele, a IA desenha uma pixel art ou monta uma Tela inteira, e você cola o resultado no jogo.

## Como usar

1. Copie este arquivo inteiro e cole na conversa com a IA, antes do pedido.
2. Peça o que quer: "uma espada de 32 × 32" ou "uma Tela de 4 × 2 blocos com o título Base e um relógio".
3. Copie **só o texto do código** da resposta, da linha `WA-ARTE1` ou `WA-TELA1` até o fim. Sem as crases (```) e sem
   o texto em volta.
4. No jogo, abra a Tela, aba **Editar**:
   - **uma arte:** aba **Pixel art**, botão **Importar**. A arte entra na sua biblioteca.
   - **um layout:** com nada escolhido, botão **Importar** na base, embaixo de **Aplicar layout**. O layout entra no
     rascunho (**Ctrl+Z** desfaz, um importar de cada vez) e só vai para a parede no **Salvar**.
5. Se o código tiver um erro, a barra de baixo do editor diz qual e onde, e nada muda: numa arte solta, a linha do
   texto; numa arte dentro do JSON, o nome da arte e a fileira de pixels. Mande o aviso para a IA corrigir.

**Dica:** o botão **Copiar como texto** exporta a sua arte ou a sua Tela nesta mesma forma. Cole isso na conversa para a
IA partir do que você já tem (com as suas redes já ligadas).

A primeira linha que não está em branco precisa ser exatamente `WA-ARTE1` ou `WA-TELA1`. O texto pode ter até 524.288
caracteres (512 K).

## Pixel art (`WA-ARTE1`)

```
WA-ARTE1
nome: Logo
tamanho: 16x16
paleta: - 1B1920 7A7366 ECE5D6
# um comentário
......3333......
(mais 15 linhas de 16 caracteres)
```

- **Primeira linha:** `WA-ARTE1`.
- **Chaves**, uma por linha, `chave: valor`, antes das linhas de pixels. Valem em português ou em inglês, maiúscula ou
  minúscula:

  | Chave | Em inglês | Valor |
  | --- | --- | --- |
  | `nome` | `name` | O nome da arte, até 32 caracteres (o resto é cortado). Sem nome, a arte importada entra como "Nova arte". |
  | `tamanho` | `size` | `LARGURAxALTURA`, como `16x16` (também `16 x 16`, `16×16` ou `16*16`). De **8 a 128** por lado. Obrigatório. |
  | `paleta` | `palette` | Até **16 cores**, separadas por espaço. |

  Outra chave dá erro ("chave desconhecida").
- **Paleta:**
  - a posição na lista é o índice da cor: a primeira é a 0, a segunda é a 1, até a 15 (`f`);
  - a **cor 0 é sempre transparente**: escreva `-` (também vale `.`, `transparente`, `transparent` ou uma cor, que é
    ignorada);
  - as outras são `RRGGBB` (opaca) ou `RRGGBBAA` (o alfa no fim, `00` transparente e `FF` opaco), com `#` opcional:
    `C2442A`, `#C2442A`, `C2442A80`;
  - as cores que faltam (com menos de 16 na lista) ficam pretas.
- **Pixels:** depois das chaves, **uma linha por fileira**, de cima para baixo:
  - **um caractere por pixel**, da esquerda para a direita: o índice da cor em hexadecimal, `0`–`9` e `a`–`f`
    (maiúscula também vale);
  - `.` é o mesmo que `0`, o transparente; use-o para o fundo ficar fácil de ler;
  - espaços dentro da linha não contam;
  - cada linha tem exatamente a **largura** em pixels, e são exatamente **altura** linhas.
- **Comentários:** uma linha que começa com `#` (depois da primeira) é ignorada, assim como as linhas em branco. Não
  ponha `#` nem `:` numa linha de pixels.

Ao importar, a arte ganha um id novo e entra na sua biblioteca (que tem um limite de artes; 32 por padrão).

## Tela (`WA-TELA1`)

A primeira linha é `WA-TELA1` e o resto é **um objeto JSON** com o layout. É o mesmo JSON que o **Copiar como texto**
da base exporta.

### A área

A Tela é desenhada em **pixels de desenho: 32 por bloco**. Uma Tela de 3 × 2 blocos tem 96 × 64; o canto (0, 0) é o de
cima à esquerda, `x` cresce para a direita e `y` para baixo.

| Chave | Valor |
| --- | --- |
| `area_w`, `area_h` | A área para a qual o layout foi feito (blocos × 32). Se a Tela de quem cola tem outro tamanho, as peças escalam. Sem elas, nada escala, e cada peça que sai da área é encolhida e trazida para dentro. |
| `keep_theme` | `true` deixa a Tela com o tema que ela já tem. Sem ela (ou `false`), vale o `theme` do JSON, ou o tema Grafite se o JSON não tiver um. |
| `theme` | O tema (abaixo). |
| `background` | O fundo (abaixo). |
| `scanlines` | `true` desenha linhas de varredura por cima de tudo. |
| `pieces` | A lista de peças. A primeira fica embaixo e a última por cima. |
| `images` | As pixel arts que as peças Imagem e o fundo usam (abaixo). |

### As cores

Os campos de cor (`color`, `background`, `border`, `track`, `upColor`, `downColor` e `flatColor` no `style`; `color` e
`background` nas regras; as seis cores do tema; `top` e `bottom` do fundo) aceitam:

- `"#RRGGBB"` ou `"#RRGGBBAA"` (o alfa no fim);
- **o papel do tema**, para a cor seguir o tema da Tela: `"tema:fundo"`, `"tema:cartao"`, `"tema:texto"`,
  `"tema:apagado"`, `"tema:alta"`, `"tema:baixa"`, ou em inglês `"theme:background"` (ou `"theme:bg"`),
  `"theme:card"`, `"theme:text"`, `"theme:muted"`, `"theme:high"`, `"theme:low"`;
- `"transparente"` (ou `"transparent"`).

Uma cor que não lê dá o erro "Cor inválida". Prefira os papéis do tema: a Tela continua bonita quando o jogador troca o
tema.

### A peça

Toda peça tem:

| Campo | Valor |
| --- | --- |
| `type` | `text`, `value`, `shape`, `icon`, `bar`, `gauge`, `graph`, `arrow` ou `image`. Obrigatório. |
| `x`, `y`, `w`, `h` | A caixa, em pixels de desenho. |
| `name` | O nome na lista de Camadas (até 32 caracteres). |
| `group` | Um número: as peças com o mesmo número (maior que 0) formam um grupo. |
| `hidden` | `true` esconde a peça. |
| `bind` | De onde vem o número (só `value`, `bar`, `gauge`, `graph` e `arrow`). |
| `format` | Como o Valor escreve o número. |
| `style` | A aparência. |
| `rules` | Até 8 regras. |

Uma peça que não lê (tipo errado, campo com o tipo de valor errado) dá o erro "A peça N não lê", com o número dela na
lista, a partir de 1.

**`style`, comum a todas:**

| Campo | Valor (padrão) |
| --- | --- |
| `color` | A cor do texto, do número ou do desenho (`#ECE5D6`). |
| `background` | O fundo da caixa (transparente). |
| `border`, `border_width` | A cor e a espessura da borda, de 0 a 4 (sem borda). |
| `radius` | Os cantos arredondados, de 0 a 8 (0). |
| `opacity` | De 0 a 100 (100). |
| `size` | A altura do texto em pixels de desenho, de 1 a 128 (8). |
| `fit` | `true` encolhe o texto para caber na caixa; `false` corta (`true`). |
| `align` | `left`, `center` ou `right` (`left`). Na Imagem, o alinhamento horizontal. |
| `font` | `minecraft`, `pixel`, `terminal`, `mono` ou `sans` (`minecraft`). |
| `text` | O texto fixo da peça Texto (até 128 caracteres). |

**Os tipos:**

| Tipo | Para quê | Campos próprios no `style` (padrão) |
| --- | --- | --- |
| `text` | Uma frase fixa. | `text`. |
| `value` | Um número ao vivo. | Usa o `format` e o `bind`. |
| `shape` | Um retângulo: cartão, moldura, divisória. | Só `background`, `border`, `border_width` e `radius`. |
| `icon` | Um item, fluido ou químico. | `item`: o id, como `"minecraft:diamond"`; `icon`: `{"kind": "item"}`, `"fluid"` ou `"chemical"`. |
| `bar` | Uma barra que enche. | `direction`: `horizontal` ou `vertical`; `reverse`; `min` e `max` (com `max` 0, o cheio vem do dado); `track`: a cor da trilha. |
| `gauge` | Um medidor em arco. | `sweep`: de 90 a 360 graus (270); `thickness`: de 1 a 32 (5); `min`, `max` e `track`, como na barra. |
| `graph` | O histórico do número. | `window`: de 1 a 10 minutos (1); `graphMode`: `line` ou `columns`; `area`: a área sob a linha (`true`). |
| `arrow` | Sobe, desce ou fica parada. | `upColor` (`tema:alta`), `downColor` (`tema:baixa`), `flatColor` (`tema:apagado`); `deadZone`; `reverse`. |
| `image` | Uma pixel art. | `image`: o `id` da arte em `images`; `scale`: de 1 a 16, ou 0 para esticar até a caixa (1); `valign`: `top`, `middle` ou `bottom` (`middle`). |

Na Imagem, a arte aparece no tamanho dela vezes a `scale`, alinhada por `align` e `valign` e cortada pela caixa. Uma
arte de 16 × 16 em `scale` 2 pede uma caixa de 32 × 32.

**`format` (só o Valor):** `decimals` de 0 a 3 (1); `abbreviate`, que escreve k, M e G (`true`); `sign`, o + nos
positivos (`false`); `prefix` e `suffix`, até 32 caracteres; `unit`: `none`, `second`, `tick` ou `minute` (`second`).

**`rules`:** cada regra é `{"op": "lt", "value": 10, "color": "tema:baixa"}`, com `op` `lt` (menor), `gt` (maior) ou
`eq` (igual), `value` (o número), e o efeito: `color` (a cor do texto ou do desenho), `background`, `blink: true` ou `hide: true`. Uma regra
pode ter `bind` próprio; sem ele, ela lê o número da peça.

### As ligações (`bind`)

O número de uma peça vem de uma fonte:

- **Relógio do jogo**, que sempre funciona: `{"source": "clock", "metric": "time"}` (a hora, HH:MM) ou
  `"metric": "day"` (o dia).
- **Rede:** `{"source": "network", "network": "<id da rede>", "type": "energy", "metric": "in"}`, com `type` `item`,
  `fluid`, `energy`, `chemical` ou `source` e `metric` `in`, `out`, `stored`, `released`, `balance`, `moving`,
  `empty`, `waiting`, `full`, `redstone`, `held`, `held_capacity` ou `held_fill`.
- **Armazenamento** ligado à Tela pelo Vinculador: `{"source": "storage", "pos": ..., "metric": "stored"}`.

A IA não sabe os ids das suas redes nem as posições dos seus armazenamentos. **Ao importar, as ligações a redes e
armazenamentos que você não tem saem** (a peça fica, sem dado), e o editor avisa quantas saíram. Ligue-as de novo na
aba **Dado**. Por isso, peça à IA as peças de dado sem `bind` (ou com o relógio), ou parta de um **Copiar como texto**
da sua Tela, que já traz os ids certos.

### O tema e o fundo

- **`theme`:** `{"name": "Meu tema", "bg": "#0F1117", "card": "#1A1C24", "text": "#ECE5D6", "muted": "#8D8577",
  "high": "#3DB56E", "low": "#F26A48"}`. Uma cor que falta vem do tema Grafite. O nome tem até 24 caracteres. As cores
  do tema são cores fixas: um papel do tema (`"tema:texto"`) num campo do `theme` é erro.
- **`background`:** `{"kind": "theme"}` (o fundo do tema), `{"kind": "solid", "top": "#1B1920"}`,
  `{"kind": "gradient", "top": "...", "bottom": "..."}` ou o mosaico,
  `{"kind": "tiled", "image": "<id da arte>", "scale": 2, "opacity": 100}`, com `scale` de 1 a 8 e `opacity` de 0
  a 100. O mosaico repete a arte pela Tela inteira, por baixo das peças.

### As artes dentro do JSON (`images`)

Cada arte é um objeto com as mesmas partes da forma `WA-ARTE1`:

```
{"id": "coracao", "nome": "Coração", "tamanho": "16x16",
 "paleta": ["-", "1B1920", "C2442A"],
 "linhas": ["................", "..."]}
```

- `id`: um texto qualquer. A Imagem (`style.image`) e o mosaico (`background.image`) apontam a arte por ele. Sem `id`,
  vale o nome.
- `nome`/`name`, `tamanho`/`size`, `paleta`/`palette` (uma lista, ou um texto com as cores separadas por espaço) e
  `linhas`/`rows` (uma lista com uma fileira por texto), com as mesmas regras da arte.
- Nas linhas do JSON não há comentário: cada texto é uma fileira.
- Uma arte que nenhuma peça nem o fundo usa sai ao importar. Uma Imagem que aponta um `id` que não está em `images`
  fica sem arte (o retângulo tracejado no editor): escolha a arte dela na aba **Estilo**.

### Os tetos

| O quê | Teto (padrão do servidor) |
| --- | --- |
| Arte | De 8 × 8 a 128 × 128 pixels, 16 cores. |
| Pixels das artes de uma Tela | 131.072 somados, contando cada arte distinta uma vez (oito artes de 128 × 128). |
| Peças por Tela | 128. |
| Ligações distintas por Tela | 64. |
| Layout sem os pixels | 16 KB de NBT. |
| Regras por peça | 8. |
| Artes distintas por Tela | 256. |
| Texto do código | 524.288 caracteres (512 K). |

Acima de um teto, o editor avisa "Não cabe nesta Tela" e nada muda (os tetos de peças e ligações são conferidos no
**Salvar**).

## Dicas para ficar bonito

**Na arte:**

- **Poucas cores.** 4 a 8 já dão uma arte bonita; 16 é o máximo.
- **Rampas de 4 tons.** Organize a paleta em linhas de 4 cores, do escuro ao claro: as cores 0 a 3, 4 a 7, 8 a 11 e 12
  a 15 (a primeira linha começa no transparente). É como o editor do jogo organiza a paleta, e o clarear e o escurecer
  andam por essas linhas.
- **Desvio de matiz.** Na rampa, o tom escuro puxa para o azul ou o roxo e fica mais saturado; o claro puxa para o
  amarelo e fica menos saturado. Fica mais vivo do que só escurecer a mesma cor.
- **Contorno.** Uma linha de 1 px em volta da forma, num tom escuro da rampa (não preto puro), separa a arte do fundo.
- **Pontilhado.** Para um degradê com poucas cores, alterne duas cores em xadrez na faixa entre elas.
- **Luz de um lado só.** Escolha de onde vem a luz (em cima à esquerda, por exemplo) e ponha os tons claros desse lado.
- **Sem pixel solto.** Linhas de 1 px sem degraus duplos e curvas em degraus regulares.

**Na Tela:**

- **Alinhe na grade de 32 px** (um bloco) e use múltiplos de 4 nas posições e tamanhos, como o editor.
- **Cores do tema:** use os papéis (`tema:texto`, `tema:cartao`...) no lugar de cores fixas.
- **Hierarquia de tamanhos:** um título grande, os números médios e os rótulos pequenos; poucos tamanhos de fonte.
- **Cartões:** uma `shape` com `background` `tema:cartao` e `radius` atrás de um grupo de peças organiza a Tela.
- **Margem:** deixe uns 4 px entre as peças e a borda da Tela.
- **Imagem em escala inteira:** escolha a caixa igual ao tamanho da arte vezes a `scale`, para nada borrar nem cortar.

## Exemplos testados

Os dois são colados pelos testes automáticos do mod e leem sem erro.

**Uma arte de 16 × 16:**

```
WA-ARTE1
nome: Coração
tamanho: 16x16
paleta: - 1B1920 7A7366 ECE5D6 8E2A35 C2442A F0603E FFB08A
# 1 é o contorno, 4 e 5 o vermelho, 6 e 7 o brilho
................
................
...1111..1111...
..155551155551..
.15677555555551.
.15775555555551.
.15755555555551.
.15555555555541.
..155555555541..
...1555555541...
....15555541....
.....155541.....
......1541......
.......11.......
................
................
```

**Um layout de 3 × 2 blocos com Texto, Valor (a hora do relógio) e a Imagem do coração:**

```
WA-TELA1
{
  "area_w": 96,
  "area_h": 64,
  "background": {"kind": "solid", "top": "#1B1920"},
  "pieces": [
    {"type": "text", "x": 4, "y": 4, "w": 88, "h": 10,
     "style": {"text": "Base norte", "color": "tema:texto", "size": 8}},
    {"type": "value", "x": 4, "y": 20, "w": 56, "h": 16,
     "bind": {"source": "clock", "metric": "time"},
     "style": {"color": "#FFB08A", "size": 12}},
    {"type": "image", "x": 60, "y": 28, "w": 32, "h": 32,
     "style": {"image": "coracao", "scale": 2}}
  ],
  "images": [
    {"id": "coracao", "nome": "Coração", "tamanho": "16x16",
     "paleta": ["-", "1B1920", "7A7366", "ECE5D6", "8E2A35", "C2442A", "F0603E", "FFB08A"],
     "linhas": [
       "................",
       "................",
       "...1111..1111...",
       "..155551155551..",
       ".15677555555551.",
       ".15775555555551.",
       ".15755555555551.",
       ".15555555555541.",
       "..155555555541..",
       "...1555555541...",
       "....15555541....",
       ".....155541.....",
       "......1541......",
       ".......11.......",
       "................",
       "................"
     ]}
  ]
}
```

## Prompt pronto

Cole este guia e depois:

> Usando o formato acima, faça uma pixel art `WA-ARTE1` de **[tamanho, como 32x32]** de **[o que você quer]**, com no
> máximo **[8]** cores organizadas em rampas de 4 tons com desvio de matiz e contorno escuro. Confira que cada linha
> tem exatamente a largura e que há exatamente a altura em linhas. Responda só com o código, num bloco de código.

Para uma Tela:

> Usando o formato acima, monte um layout `WA-TELA1` para uma Tela de **[largura × altura]** blocos com
> **[o que mostrar]**. Use as cores por papel do tema, alinhe na grade de 32 px e deixe as peças de dado sem `bind`
> (eu ligo no jogo). Responda só com o código, num bloco de código.

A pixel art animada vem numa versão futura do mod; este formato ainda não tem quadros.
