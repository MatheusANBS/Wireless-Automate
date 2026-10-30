"""Página wireless-chest.md do guia."""
from guia_base import *  # noqa: F401,F403


SCENE_CHESTS = '''<GameScene zoom="4" interactive={true}>
  <Block id="wirelessautomate:storage_chest" x="0" y="0" z="0" p:tier="elite" />
  <Block id="wirelessautomate:router" x="0" y="1" z="0" p:facing="up" p:tier="ultimate" />
  <Block id="wirelessautomate:storage_chest" x="4" y="0" z="0" p:tier="ultimate" />
  <Block id="wirelessautomate:router" x="4" y="1" z="0" p:facing="up" p:tier="ultimate" />
  <LineAnnotation from="0.5 1.5 0.5" to="4.5 1.5 0.5" color="#a46cff" thickness="0.08">
    {LINE}
  </LineAnnotation>
  <BlockAnnotation x="0" y="0" z="0" color="#45d6cc">
    {A}
  </BlockAnnotation>
  <BlockAnnotation x="4" y="0" z="0" color="#a46cff">
    {B}
  </BlockAnnotation>
  <IsometricCamera yaw="200" pitch="30" />
</GameScene>'''


PAGINA = page('wireless-chest.md', front('Baú Wireless', 'wirelessautomate:storage_chest', 11,
                                item_ids=['wirelessautomate:storage_chest']) + '''
# Baú Wireless

<ItemImage id="wirelessautomate:storage_chest" scale="2" float="left" />

Guarda **milhões** de itens, de quantos tipos quiser. Entre dois Baús Wireless, um tipo inteiro
passa **de uma vez**.

<br clear="all" />

''' + fill(SCENE_CHESTS, LINE='Um tipo inteiro por vez',
           A='Baú Elite · face de cima em **Extrai**',
           B='Baú Ultimate · face de cima em **Insere**') + '''

## Como usar

1. Coloque o Baú e prenda um roteador nele.
2. Configure a face na aba Itens do roteador.
3. Clique no Baú com a mão vazia para ver o que tem dentro.

## Capacidade

Total de itens, todos os tipos somados.

''' + capacity_table('pt', 'chest', 'Itens') + '''

Sobe de tier com os [Cartões de Upgrade](upgrade-cards.md), no mundo ou na bancada, **sem perder o conteúdo**.

## A tela

| Gesto | O que faz |
| --- | --- |
| Digitar na busca | **@mod** busca por mod (ex.: **@mekanism**); **#** por tag e tooltip (ex.: **#ingots**). |
| Botão ao lado da busca | Muda a ordem: quantidade, nome ou mod. |
| **Clique** no item | Pega uma pilha. Botão direito: meia. |
| **Shift + clique** no item | Manda a pilha para o inventário. Arrastando: uma de cada. |
| **Shift + clique duplo** com item no cursor | Enche o inventário com aquele item. |
| **Rodinha** sobre o item | Para baixo tira um, para cima guarda um. |
| **Clique** na lista com item no cursor | Guarda tudo. Botão direito: um só. |
| **Shift + clique** no inventário | Guarda o item. |

## Filtro de entrada

O botão **Filtro** abre um [filtro](filters.md) que vale para **tudo que entra**: roteador, funil,
outro mod ou a tela.

| Na entrada do filtro | No Baú |
| --- | --- |
| Lista branca | Só entra o que está na lista. |
| Lista negra | Entra tudo, menos o que está na lista. |
| **Estoque** N | Guarda **até N** daquele item; o resto fica onde estava. |

## Upgrades

Dois slots, à esquerda da lista. Clique no slot com o upgrade no cursor; a aba ao lado abre o painel.

| Upgrade | Receita |
| --- | --- |
| ''' + item('crafting_upgrade') + ''' **Upgrade de Mesa de Trabalho** | Mesa de trabalho + Cristal de Sinal. |
| ''' + item('compacting_upgrade') + ''' **Upgrade de Compactação** | Pistão + Cristal de Sinal + bloco de ferro (sem forma). |

### Mesa de Trabalho

Uma mesa 3×3 dentro do Baú.

- A grade **se repõe sozinha** com o que o Baú tem.
- **Shift** + clique no resultado: repete até uma pilha.
- No **JEI**, o **+** monta a receita puxando do Baú e depois do inventário.

### Compactação

Junta e separa sozinha cadeias como pepita, lingote, bloco (ou pedregulho até o 9x do
AllTheCompressed). Itens encantados ou com nome ficam de fora.

1. O upgrade começa **Desligado**.
2. No painel, escolha **Compactar** (forma mais alta) ou **Descompactar** (a base) para todas as cadeias.
3. Para uma cadeia só, use as setas na linha dela. **Global** volta ao modo geral.

Com o alvo em lingote, pepitas **e** blocos viram lingotes.

- A sobra fica na forma de baixo: 10 lingotes com alvo em bloco dão 1 bloco e 1 lingote.
- Descompactar converte **só o que cabe**; o resto fica na forma alta.
- O roteador vê só a forma guardada: para tirar lingotes, deixe o alvo em lingote.
- O **Estoque** conta a cadeia inteira (11 blocos e 1 lingote valem 100 lingotes).
- O mesmo material de mods diferentes é uma linha só: clique no nome do mod para escolher em qual juntar.
- Uma cadeia com regra própria fica na lista, apagada, mesmo vazia.
- Com a Compactação, a Mesa desce uma forma quando falta o item exato.

## Quebrar e levar

O item leva **o conteúdo, o filtro, os upgrades e a grade da Mesa**. Cheio, ele sempre vira item,
até no criativo ou numa explosão.

## Com outros blocos

- **Funis, AE2, Refined Storage e outros mods** o veem como um inventário comum, um slot por tipo.
- **Comparador**: o sinal sobe com a ocupação (no Ultimate, 1 com qualquer coisa dentro).

## Receita

<RecipeFor id="wirelessautomate:storage_chest" />
''', front('Wireless Chest', 'wirelessautomate:storage_chest', 11,
           item_ids=['wirelessautomate:storage_chest']) + '''
# Wireless Chest

<ItemImage id="wirelessautomate:storage_chest" scale="2" float="left" />

Stores **millions** of items, of as many types as you like. Between two Wireless Chests, a whole
type moves **at once**.

<br clear="all" />

''' + fill(SCENE_CHESTS, LINE='A whole type at a time',
           A='Elite chest · top face on **Extract**',
           B='Ultimate chest · top face on **Insert**') + '''

## How to use it

1. Place the chest and attach a router to it.
2. Set up the face on the router's Items tab.
3. Click the chest with an empty hand to see what's inside.

## Capacity

Total items, all types added up.

''' + capacity_table('en', 'chest', 'Items') + '''

Raise the tier with [Upgrade Cards](upgrade-cards.md), in the world or in a crafting table, **keeping the contents**.

## The screen

| Gesture | What it does |
| --- | --- |
| Type in the search box | **@mod** searches by mod (e.g. **@mekanism**); **#** by tag and tooltip (e.g. **#ingots**). |
| Button next to the search | Changes the order: amount, name or mod. |
| **Click** an item | Takes a stack. Right click: half. |
| **Shift + click** an item | Sends the stack to your inventory. Dragging: one of each. |
| **Shift + double-click** with an item on the cursor | Fills your inventory with that item. |
| **Wheel** over an item | Down takes one, up stores one. |
| **Click** the list with an item on the cursor | Stores it all. Right click: just one. |
| **Shift + click** in your inventory | Stores the item. |

## Input filter

The **Filter** button opens a [filter](filters.md) that applies to **everything that gets in**:
router, hopper, another mod or the screen.

| In the filter entry | In the chest |
| --- | --- |
| Whitelist | Only what's on the list gets in. |
| Blacklist | Everything gets in except what's on the list. |
| **Stock** N | Stores **up to N** of that item; the rest stays where it was. |

## Upgrades

Two slots, to the left of the list. Click a slot with the upgrade on the cursor; the tab next to it opens the panel.

| Upgrade | Recipe |
| --- | --- |
| ''' + item('crafting_upgrade') + ''' **Crafting Upgrade** | Crafting table + Signal Crystal. |
| ''' + item('compacting_upgrade') + ''' **Compacting Upgrade** | Piston + Signal Crystal + iron block (shapeless). |

### Crafting

A 3×3 crafting table inside the chest.

- The grid **refills itself** from what the chest has.
- **Shift** + click the result: repeats up to one stack.
- In **JEI**, **+** sets up the recipe from the chest first, then your inventory.

### Compacting

It packs and unpacks chains like nugget, ingot, block (or cobblestone up to AllTheCompressed's 9x)
by itself. Enchanted or named items are left out.

1. The upgrade starts **Off**.
2. In the panel, pick **Compress** (highest form) or **Decompress** (the base) for all chains.
3. For a single chain, use the arrows on its row. **Global** goes back to the general mode.

With the target on ingot, nuggets **and** blocks become ingots.

- The leftover stays in the lower form: 10 ingots with the target on block give 1 block and 1 ingot.
- Decompressing converts **only what fits**; the rest stays in the high form.
- The router only sees the stored form: to pull ingots, leave the target on ingot.
- **Stock** counts the whole chain (11 blocks and 1 ingot are worth 100 ingots).
- The same material from different mods is a single row: click the mod name to choose which one to gather into.
- A chain with its own rule stays in the list, dimmed, even when empty.
- With Compacting, the crafting grid steps down one form when the exact item is missing.

## Break and carry

The item takes **the contents, the filter, the upgrades and the crafting grid**. A full chest always
becomes an item, even in creative or in an explosion.

## With other blocks

- **Hoppers, AE2, Refined Storage and other mods** see it as a regular inventory, one slot per type.
- **Comparator**: the signal rises with how full it is (on Ultimate, 1 with anything inside).

## Recipe

<RecipeFor id="wirelessautomate:storage_chest" />
''')
