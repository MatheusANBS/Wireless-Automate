"""Página signal-screen.md do guia."""
from guia_base import *  # noqa: F401,F403


PAGINA = page('signal-screen.md', front('Tela de Sinal', 'wirelessautomate:signal_screen', 20, item_ids=[
    'wirelessautomate:signal_screen']) + '''
# Tela de Sinal

<ItemImage id="wirelessautomate:signal_screen" scale="2" float="left" />

Um painel de parede, de **1 × 1 até 32 × 32 blocos**, que mostra os números das suas [redes](networks.md) ao vivo: o
que entra, o que sai e quantas faces estão movendo.

<br clear="all" />

## Como usar

1. Clique com a Tela na **lateral de um bloco sólido**. Não vai no chão nem no teto, e cai se a parede sair.
2. Clique nela com a **mão vazia** para abrir a configuração.
3. Na aba **Tamanho**, escolha a largura e a altura e clique em **Aplicar**.
4. Na aba **Editar**, aplique um layout pronto ou monte o seu com peças.
5. Clique em **Salvar** (ou **Ctrl+S**). Nada vai para a parede antes disso.

Só quem colocou a Tela, os operadores e quem está na aba **Acesso** configuram; os outros só olham.

## Tamanho

| Parte | Como usar |
| --- | --- |
| **Largura** e **Altura** | ‹ e › mudam de 1 em 1 bloco; com **Shift**, de 4 em 4. |
| **Cresce a partir do bloco para** | ↗, ↖ ou ✥: onde o bloco que você colocou fica dentro da tela. |
| **Prévia** | A célula vermelha é a que impede o tamanho. |

A tela grande **não ocupa blocos**: é desenhada sobre a parede. Cada bloco da área precisa de **parede sólida atrás**
e **espaço livre na frente** (ar ou grama servem; uma tocha impede), e a área não pode cruzar outra Tela.

## O editor

Na aba **Editar** você monta o que a Tela mostra com **peças**. Se a janela não couber, diminua a escala da interface.

| Peça | Para quê |
| --- | --- |
| **Valor** | Um número ao vivo: o que entra, sai ou está guardado, quantas faces movem... |
| **Texto** | Uma frase fixa. |
| **Forma** | Um retângulo: cartão, moldura, divisória. |
| **Ícone de item** | O desenho de um item. |
| **Barra** | Enche conforme um número. |
| **Medidor** | Um mostrador em arco com ponteiro. |
| **Gráfico** | O histórico de um número nos últimos minutos. |
| **Seta** | Sobe, desce ou fica parada conforme o número. |
| **Imagem** | Uma pixel art sua ou uma imagem enviada (PNG, JPG ou GIF). |

| Gesto | O que faz |
| --- | --- |
| **Clique** | Escolhe a peça (num grupo, o grupo inteiro; dois cliques entram nele). |
| **Shift + clique** | Soma ou tira da escolha. |
| **Arrastar** | Move. A alça do canto redimensiona. |
| **Alt** ao arrastar | Ajusta de 1 em 1 px (o normal é uma grade de 4 px). |
| **Camadas** | Arraste uma linha para mudar a ordem; o olho esconde. |

| Atalho | O que faz |
| --- | --- |
| **Ctrl+Z**, **Ctrl+Y** | Desfaz e refaz. |
| **Ctrl+C**, **Ctrl+X**, **Ctrl+V** | Copia, recorta e cola, até de uma Tela para outra. |
| **Ctrl+D**, **Delete** | Duplica e remove. |
| **Ctrl+A**, **Ctrl+G**, **Ctrl+Shift+G** | Escolhe tudo, agrupa e desagrupa. |
| **[** e **]** | Sobe e desce a camada. |
| **Setas** | Movem 4 px; com **Shift**, 32 px; com **Alt**, 1 px. |

As abas da direita ajustam a peça escolhida: **Dado** (de onde vem o número), **Formato** (casas, k/M/G, prefixo,
unidade), **Estilo** (posição, cores, borda, fonte) e **Regras** (muda a cor, esconde ou pisca quando o número passa
de um valor).

### De onde vem o número

Na aba **Dado**, a **Fonte** pode ser:

- a **Rede**: o que entra, sai ou está guardado, e quantas faces movem. **Guardado** soma os Armazéns da rede;
- um **Armazém** (Baú, Bateria ou Tanque) ligado à Tela com o [Vinculador](linker.md): guardado, capacidade,
  enchimento e o que entra, sai ou sobra por segundo. Num Baú, dá para ler um item só, escolhido na grade dele (ou
  arrastado do JEI);
- o **Relógio** do jogo: hora do dia e dia.

Os números chegam uma vez por segundo enquanto há alguém perto. Uma rede privada a que quem colocou a Tela perdeu
acesso mostra **—**.

Fechar com mudanças pede dois cliques no ✕ (**Descartar?**).

### Base, temas e layouts

Com **nada escolhido**, a coluna da direita mostra a **base** da Tela:

- **Tema:** uma paleta de seis cores. Há quatro prontos (Grafite, Fósforo, Âmbar e Porcelana) e você salva os seus.
  As cores "do tema" (bolinhas redondas) acompanham a troca; as quadradas são fixas. A Tela guarda a paleta: apagar
  um tema seu depois não muda nenhuma Tela.
- **Fundo:** o do tema, uma cor, um degradê ou uma arte em mosaico.
- **Layout pronto:** **Energia**, **Armazenamento**, **Rede** ou **Relógio**. **Aplicar layout** troca o que está na
  Tela e se ajusta ao tamanho (**Ctrl+Z** desfaz antes de salvar). **Salvar layout atual** guarda o seu; **Remover
  layout** apaga, em dois cliques.

### Componentes

Na coluna da esquerda, **Componentes** são grupos de peças prontos (**Painel de rede**, **Cartão de número**,
**Título** e os seus). Clique para inserir. Para guardar o seu, escolha as peças e clique em **Salvar como
componente** (⊕). Componentes, temas e layouts valem em qualquer Tela sua.

## Pixel art

Na aba **Artes**, parte **Pixel art**:

1. Clique em **Nova** (ou escolha uma arte e clique em **Editar**).
2. Dê o nome e o **Tamanho**, de 8 a 128 pixels de lado.
3. Desenhe e clique em **Salvar** (ou **Ctrl+S**). Se a arte não couber na biblioteca, o desenho fica aberto com o
   aviso.

Cada arte tem até **16 cores** e **128 × 128** pixels. O clique esquerdo usa a ferramenta e o **direito apaga**.
**Ctrl** + clique pega a cor de um pixel.

| Tecla | Ferramenta |
| --- | --- |
| **B** | Lápis. |
| **E** | Borracha. |
| **G** | Balde. Com **Shift**, troca a cor na arte inteira. |
| **I** | Conta-gotas. |
| **L** | Linha. **Shift** trava em 0°, 45° e 90°. |
| **R** | Retângulo. **R** de novo alterna contorno e cheio; **Shift** faz quadrado. |
| **O** | Elipse. **O** de novo alterna contorno e cheia; **Shift** faz círculo. |
| **D** | Clarear (esquerdo) e escurecer (direito). |
| **T** | Pontilhado em xadrez. |
| **S** | Seleção. |

| Tecla | Ajuda |
| --- | --- |
| **[** e **]** | Pincel de 1 a 8 px. |
| **M** e **Shift+M** | Espelho horizontal e vertical. |
| **P** | Pixel perfect no lápis de 1 px. |
| **X** | Volta para a cor anterior. |
| **Ctrl+G** | Liga e desliga a grade. |
| **Ctrl+Z**, **Ctrl+Y** | Desfaz e refaz, até 100 passos. |
| **Shift+H**, **Shift+V** | Vira na horizontal e na vertical. |
| Roda do mouse | Zoom. |
| **Espaço** + arrastar | Move a vista. |
| **0** | Enquadra a arte. |

- **Paleta:** 16 cores em 4 linhas; a cor 0 é sempre transparente. Cada linha é uma **rampa** do escuro ao claro, que
  o clarear e o escurecer seguem. Trocar uma cor muda todos os pixels dela. **Criar rampa** gera a linha a partir da
  cor escolhida.
- **Seleção (S):** com ela, as ferramentas só pintam dentro. Arraste de dentro para mover; **Enter** assenta, **Esc**
  desfaz.

### Animação

Uma arte pode ter **até 64 quadros**, do mesmo tamanho e com a mesma paleta. A faixa embaixo do desenho mostra os
quadros: clique para escolher, arraste para reordenar.

- **Velocidade** de 1 a 20 quadros por segundo; **Loop** ou **Vai e volta**.
- **Papel vegetal:** o quadro anterior aparece apagado por baixo.
- Os quadros contam no teto de pixels da Tela: uma arte de 32 × 32 com 10 quadros conta 10.240.

| Tecla | O que faz |
| --- | --- |
| **,** e **.** | Quadro anterior e seguinte. |
| **Alt+N**, **Alt+D** | Quadro novo e duplicar. |
| **Alt+O** | Papel vegetal. |
| **Alt+P** | Toca e pausa a prévia. |

### Pôr a arte na Tela

1. Clique em **Imagem** na lista de **Peças**, ou arraste a arte da aba **Artes** até a miniatura.
2. Na aba **Estilo**, ajuste **Escala** (1× a 16×, ou **Esticar**), **Alinhar** e **Opacidade**.

A Tela guarda uma **cópia** da arte: mudar ou apagar a arte depois não muda a Tela. **Atualizar da biblioteca** traz
a versão atual. A linha **Pixels** da base mostra quanto as artes da Tela somam e o teto.

### Exportar e importar

| Botão | O que faz |
| --- | --- |
| **Exportar** | Copia o código do layout (na base) ou da arte escolhida (na aba **Artes**). |
| **Importar** | Lê o código: aplica o layout (**Ctrl+Z** desfaz) ou cria uma arte nova. |
| **Copiar como texto** | Uma forma legível, para editar à mão ou pedir a uma IA. **Importar** lê as duas. |

O layout leva as artes junto. Ligações a redes ou armazenamentos que você não tem saem: ligue de novo na aba **Dado**.
Há um guia para gerar artes e Telas com IA no GitHub do mod: `docs/guia-pixel-art-com-ia.md`.

## Imagens e GIFs

Na aba **Artes**, parte **Imagens**:

1. Clique em **Enviar arquivo** e escolha um PNG, JPG ou GIF (ou importe um código **WA-IMAGEM1**).
2. A imagem vai para o servidor e entra na sua biblioteca.
3. Arraste-a até a miniatura, ou escolha-a na grade da aba **Estilo** (o GIF tem a marca ▶).

| Limite | Padrão |
| --- | --- |
| Arquivo | Até **8 MB** |
| Tamanho | Até **1024 × 1024** pixels |
| GIF | Até **240** quadros |
| Por Tela | Até **8** imagens diferentes |

O servidor pode mudar esses números. A imagem fica guardada no mundo, nunca é um link da internet. O GIF só toca
quando alguém olha para a Tela.

- **Esconder as imagens dos outros:** na config do cliente, `showOthersImages = false` troca as imagens de outros
  jogadores por um quadro cinza.
- **Operadores:** `/wa tela imagens` lista as imagens do mundo; `/wa tela imagem remover <resumo>` tira uma de todas
  as Telas; `/wa tela imagem banir <jogador>` impede alguém de enviar (`desbanir` desfaz).

## Acesso

Na aba **Acesso** (só o dono e os operadores a veem):

1. Escreva o nome em **Convidar jogador…** e aperte **Enter**. Só vale quem já entrou no servidor.
2. Com o FTB Teams, adicione um time em **Adicionar time…**.
3. Para tirar alguém, clique duas vezes no **✕**.

Quem está na lista edita como o dono, mas uma ligação nova só vale a uma rede de quem a faz. As ligações que a Tela já
tem ficam quando qualquer um salva.

## Receita

<RecipeFor id="wirelessautomate:signal_screen" />
''', front('Signal Screen', 'wirelessautomate:signal_screen', 20, item_ids=[
    'wirelessautomate:signal_screen']) + '''
# Signal Screen

<ItemImage id="wirelessautomate:signal_screen" scale="2" float="left" />

A wall panel, from **1 × 1 up to 32 × 32 blocks**, that shows your [networks'](networks.md) numbers live: what comes
in, what goes out and how many faces are moving.

<br clear="all" />

## How to use it

1. Click with the Screen on the **side of a solid block**. It can't go on the floor or the ceiling, and it drops if
   the wall goes away.
2. Click it with an **empty hand** to open its settings.
3. In the **Size** tab, pick the width and height and click **Apply**.
4. In the **Edit** tab, apply a ready layout or build your own out of pieces.
5. Click **Save** (or **Ctrl+S**). Nothing reaches the wall before that.

Only whoever placed the Screen, operators and those in the **Access** tab can configure it; everyone else just
watches.

## Size

| Part | How to use |
| --- | --- |
| **Width** and **Height** | ‹ and › change it 1 block at a time; with **Shift**, 4 at a time. |
| **Grows from the block toward** | ↗, ↖ or ✥: where the block you placed sits inside the screen. |
| **Preview** | The red cell is the one in the way. |

The big screen **takes no blocks**: it is drawn over the wall. Every block of the area needs a **solid wall behind
it** and **free space in front** (air or grass work; a torch is in the way), and the area can't cross another Screen.

## The editor

In the **Edit** tab you build what the Screen shows out of **pieces**. If the window doesn't fit, lower the interface
scale.

| Piece | What for |
| --- | --- |
| **Value** | A live number: what comes in, goes out or is stored, how many faces move... |
| **Text** | A fixed phrase. |
| **Shape** | A rectangle: card, frame, divider. |
| **Item icon** | The picture of an item. |
| **Bar** | Fills with a number. |
| **Gauge** | An arc dial with a needle. |
| **Graph** | A number's history over the last minutes. |
| **Arrow** | Points up, down or stays flat with the number. |
| **Image** | A pixel art of yours or an uploaded image (PNG, JPG or GIF). |

| Gesture | What it does |
| --- | --- |
| **Click** | Picks the piece (in a group, the whole group; two clicks enter it). |
| **Shift + click** | Adds to or removes from the pick. |
| **Drag** | Moves. The corner handle resizes. |
| **Alt** while dragging | Adjusts 1 px at a time (normally a 4 px grid). |
| **Layers** | Drag a row to change the order; the eye hides. |

| Shortcut | What it does |
| --- | --- |
| **Ctrl+Z**, **Ctrl+Y** | Undo and redo. |
| **Ctrl+C**, **Ctrl+X**, **Ctrl+V** | Copy, cut and paste, even from one Screen to another. |
| **Ctrl+D**, **Delete** | Duplicate and remove. |
| **Ctrl+A**, **Ctrl+G**, **Ctrl+Shift+G** | Pick all, group and ungroup. |
| **[** and **]** | Raise and lower the layer. |
| **Arrow keys** | Move 4 px; with **Shift**, 32 px; with **Alt**, 1 px. |

The tabs on the right set up the picked piece: **Data** (where the number comes from), **Format** (decimals, k/M/G,
prefix, unit), **Style** (position, colors, border, font) and **Rules** (changes the color, hides or blinks when the
number crosses a value).

### Where the number comes from

In the **Data** tab, the **Source** can be:

- the **Network**: what comes in, goes out or is stored, and how many faces move. **Held** adds up the network's
  Storages;
- a **Storage** (Chest, Battery or Tank) linked to the Screen with the [Linker](linker.md): stored, capacity, fill,
  and what comes in, goes out or is left over per second. On a Chest you can read a single item, picked from its grid
  (or dragged from JEI);
- the game **Clock**: time of day and day.

The numbers arrive once a second while someone is nearby. A private network that whoever placed the Screen lost
access to shows **—**.

Closing with changes takes two clicks on the ✕ (**Discard?**).

### Base, themes and layouts

With **nothing picked**, the right column shows the Screen's **base**:

- **Theme:** a palette of six colors. There are four ready ones (Graphite, Phosphor, Amber and Porcelain) and you can
  save your own. "From the theme" colors (round dots) follow the swap; square ones are fixed. The Screen keeps the
  palette: deleting one of your themes later changes no Screen.
- **Background:** the theme's, a color, a gradient or a tiled art.
- **Ready layout:** **Energy**, **Storage**, **Network** or **Clock**. **Apply layout** replaces what is on the Screen
  and fits its size (**Ctrl+Z** undoes before saving). **Save current layout** keeps your own; **Remove layout**
  deletes it, in two clicks.

### Components

In the left column, **Components** are ready groups of pieces (**Network panel**, **Number card**, **Title** and your
own). Click one to insert it. To keep your own, pick the pieces and click **Save as component** (⊕). Components,
themes and layouts work on any Screen of yours.

## Pixel art

In the **Art** tab, **Pixel art** part:

1. Click **New** (or pick an art and click **Edit**).
2. Give it a name and a **Size**, 8 to 128 pixels per side.
3. Draw and click **Save** (or **Ctrl+S**). If the art doesn't fit in your library, the drawing stays open with the
   warning.

Each art has up to **16 colors** and **128 × 128** pixels. Left click uses the tool and **right click erases**.
**Ctrl** + click picks a pixel's color.

| Key | Tool |
| --- | --- |
| **B** | Pencil. |
| **E** | Eraser. |
| **G** | Bucket. With **Shift**, swaps the color in the whole art. |
| **I** | Eyedropper. |
| **L** | Line. **Shift** locks to 0°, 45° and 90°. |
| **R** | Rectangle. **R** again toggles outline and filled; **Shift** makes a square. |
| **O** | Ellipse. **O** again toggles outline and filled; **Shift** makes a circle. |
| **D** | Lighten (left) and darken (right). |
| **T** | Checkerboard dither. |
| **S** | Selection. |

| Key | Helper |
| --- | --- |
| **[** and **]** | Brush from 1 to 8 px. |
| **M** and **Shift+M** | Horizontal and vertical mirror. |
| **P** | Pixel perfect on the 1 px pencil. |
| **X** | Back to the previous color. |
| **Ctrl+G** | Turns the grid on and off. |
| **Ctrl+Z**, **Ctrl+Y** | Undo and redo, up to 100 steps. |
| **Shift+H**, **Shift+V** | Flip horizontally and vertically. |
| Mouse wheel | Zoom. |
| **Space** + drag | Moves the view. |
| **0** | Fits the art. |

- **Palette:** 16 colors in 4 rows; color 0 is always transparent. Each row is a **ramp** from dark to light, which
  lighten and darken follow. Changing a color changes all its pixels. **Make ramp** builds the row from the chosen
  color.
- **Selection (S):** with one, tools only paint inside it. Drag from inside to move; **Enter** settles, **Esc**
  undoes.

### Animation

An art can have **up to 64 frames**, all the same size and with the same palette. The strip under the canvas shows
the frames: click to choose, drag to reorder.

- **Speed** from 1 to 20 frames per second; **Loop** or **Ping-pong**.
- **Onion skin:** the previous frame shows faded underneath.
- Frames count toward the Screen's pixel limit: a 32 × 32 art with 10 frames counts 10,240.

| Key | What it does |
| --- | --- |
| **,** and **.** | Previous and next frame. |
| **Alt+N**, **Alt+D** | New frame and duplicate. |
| **Alt+O** | Onion skin. |
| **Alt+P** | Plays and pauses the preview. |

### Putting the art on the Screen

1. Click **Image** in the **Pieces** list, or drag the art from the **Art** tab onto the preview.
2. In the **Style** tab, set **Scale** (1× to 16×, or **Stretch**), **Align** and **Opacity**.

The Screen keeps a **copy** of the art: changing or deleting the art later doesn't change the Screen. **Update from
library** brings the current version. The **Pixels** line in the base shows how much the Screen's art adds up to, and
the cap.

### Export and import

| Button | What it does |
| --- | --- |
| **Export** | Copies the code of the layout (in the base) or of the picked art (in the **Art** tab). |
| **Import** | Reads the code: applies the layout (**Ctrl+Z** undoes) or makes a new art. |
| **Copy as text** | A readable form, to edit by hand or ask an AI. **Import** reads both. |

The layout takes its art along. Links to networks or storages you don't have are dropped: link them again in the
**Data** tab. There is a guide to making art and Screens with an AI on the mod's GitHub: `docs/pixel-art-ai-guide.md`.

## Images and GIFs

In the **Art** tab, **Images** part:

1. Click **Upload file** and pick a PNG, JPG or GIF (or import a **WA-IMAGEM1** code).
2. The image goes to the server and into your library.
3. Drag it onto the preview, or pick it in the **Style** tab's grid (a GIF has the ▶ mark).

| Limit | Default |
| --- | --- |
| File | Up to **8 MB** |
| Size | Up to **1024 × 1024** pixels |
| GIF | Up to **240** frames |
| Per Screen | Up to **8** different images |

The server can change these numbers. An image is kept in the world, never a link to the internet. A GIF only plays
while someone looks at the Screen.

- **Hide other players' images:** in the client config, `showOthersImages = false` swaps other players' images for a
  gray box.
- **Operators:** `/wa tela imagens` lists the world's images; `/wa tela imagem remover <hash>` takes one off every
  Screen; `/wa tela imagem banir <player>` stops someone from uploading (`desbanir` undoes it).

## Access

In the **Access** tab (only the owner and operators see it):

1. Type the name in **Invite player…** and press **Enter**. Only players who have joined the server count.
2. With FTB Teams, add a team in **Add team…**.
3. To remove someone, click the row's **✕** twice.

Whoever is on the list edits like the owner, but a new link only works to a network of whoever makes it. The links
the Screen already has stay when anyone saves.

## Recipe

<RecipeFor id="wirelessautomate:signal_screen" />
''')
