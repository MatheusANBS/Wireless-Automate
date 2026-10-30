"""Página wireless-tank.md do guia."""
from guia_base import *  # noqa: F401,F403


PAGINA = page('wireless-tank.md', front('Tanque Wireless', 'wirelessautomate:storage_tank', 12,
                               item_ids=['wirelessautomate:storage_tank']) + '''
# Tanque Wireless

<ItemImage id="wirelessautomate:storage_tank" scale="2" float="left" />

Um tanque para **vários fluidos ao mesmo tempo**, numa lista como a do [Baú Wireless](wireless-chest.md).
Preso a um roteador, troca bilhões de mB de uma vez.

<br clear="all" />

''' + fill(SCENE_STORAGE_PAIR, BLOCK='storage_tank', LINE='Fluido de um Tanque para outro pela aba Fluidos') + '''

## Capacidade

Total em mB, todos os fluidos somados.

''' + capacity_table('pt', 'tank', 'mB') + '''

## Baldes e recipientes

| Gesto | O que faz |
| --- | --- |
| **Clique** no Tanque com balde cheio | Esvazia o balde (ou Shift + clique nele, na tela). |
| **Clique** no Tanque com balde vazio | Enche com o primeiro fluido. |
| Na tela, **clique** num fluido com recipiente no cursor | Enche o recipiente, ou o esvazia se já estiver cheio. Botão direito: esvazia um. |

Tanques e células de outros mods funcionam igual, pela tela.

## Filtro de entrada

O botão **Filtro** abre a [tela de filtros](filters.md) de fluidos: só entra o que ele aceita, e o
**estoque** vira "guardar até N mB".
''' + fill(COMMON_PT, FILTER_PT=' e o filtro', WHAT_PT='um tanque') + '''
## Receita

<RecipeFor id="wirelessautomate:storage_tank" />
''', front('Wireless Tank', 'wirelessautomate:storage_tank', 12,
           item_ids=['wirelessautomate:storage_tank']) + '''
# Wireless Tank

<ItemImage id="wirelessautomate:storage_tank" scale="2" float="left" />

A tank for **many fluids at once**, in a list like the [Wireless Chest](wireless-chest.md)'s.
Attached to a router, it trades billions of mB at once.

<br clear="all" />

''' + fill(SCENE_STORAGE_PAIR, BLOCK='storage_tank', LINE='Fluid from one Tank to another through the Fluids tab') + '''

## Capacity

Total in mB, all fluids added up.

''' + capacity_table('en', 'tank', 'mB') + '''

## Buckets and containers

| Gesture | What it does |
| --- | --- |
| **Click** the Tank with a full bucket | Empties the bucket (or Shift + click it, on the screen). |
| **Click** the Tank with an empty bucket | Fills it with the first fluid. |
| On the screen, **click** a fluid with a container on the cursor | Fills the container, or empties it if it's already full. Right click: empties one. |

Tanks and cells from other mods work the same way, through the screen.

## Input filter

The **Filter** button opens the fluid [filter screen](filters.md): only what it accepts gets in, and
**stock** means "store up to N mB".
''' + fill(COMMON_EN, FILTER_EN=' and the filter', WHAT_EN='tank') + '''
## Recipe

<RecipeFor id="wirelessautomate:storage_tank" />
''')
