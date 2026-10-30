"""Página wireless-chemical-tank.md do guia."""
from guia_base import *  # noqa: F401,F403


PAGINA = page('wireless-chemical-tank.md', front('Tanque Químico Wireless', 'wirelessautomate:storage_chemical_tank', 14,
                                        item_ids=['wirelessautomate:storage_chemical_tank']) + '''
# Tanque Químico Wireless

<ItemImage id="wirelessautomate:storage_chemical_tank" scale="2" float="left" />

O [Tanque Wireless](wireless-tank.md) dos **químicos do Mekanism**, vários de uma vez. Só existe com o Mekanism.
Na aba **Químicos** do roteador, um tipo inteiro passa de uma vez.

<br clear="all" />

''' + fill(SCENE_STORAGE_PAIR, BLOCK='storage_chemical_tank', LINE='De um Tanque para outro pela aba Químicos') + '''

## Capacidade

A do [Tanque](wireless-tank.md), em mB, somando todos os químicos.

''' + capacity_table('pt', 'tank', 'mB') + '''

## Recipientes do Mekanism

| Gesto | O que faz |
| --- | --- |
| **Clique** num químico, com tanque ou cilindro no cursor | Enche o recipiente (ou esvazia, se cheio). |
| **Clique direito** | Esvazia um. |
| **Shift + clique** no recipiente do inventário | Esvazia no Tanque. |

## Filtro de entrada

**Filtro** abre o filtro de químicos (por químico ou por mod). O **estoque** vira "guardar até N mB".
''' + fill(COMMON_PT, FILTER_PT=' e o filtro', WHAT_PT='um tanque de químicos') + '''
## Receita

Um Tanque Wireless com frascos de vidro e ferro (só com o Mekanism).

<RecipeFor id="wirelessautomate:storage_chemical_tank" />
''', front('Wireless Chemical Tank', 'wirelessautomate:storage_chemical_tank', 14,
           item_ids=['wirelessautomate:storage_chemical_tank']) + '''
# Wireless Chemical Tank

<ItemImage id="wirelessautomate:storage_chemical_tank" scale="2" float="left" />

The [Wireless Tank](wireless-tank.md) for **Mekanism chemicals**, many at once. It only exists with Mekanism. On
the router's **Chemicals** tab, a whole type moves at once.

<br clear="all" />

''' + fill(SCENE_STORAGE_PAIR, BLOCK='storage_chemical_tank', LINE='From one Tank to another through the Chemicals tab') + '''

## Capacity

The [Tank](wireless-tank.md)'s, in mB, all chemicals added up.

''' + capacity_table('en', 'tank', 'mB') + '''

## Mekanism containers

| Gesture | What it does |
| --- | --- |
| **Click** a chemical, with a tank or canister on the cursor | Fills the container (or empties it, if full). |
| **Right-click** | Empties one. |
| **Shift + click** the container in the inventory | Empties it into the Tank. |

## Input filter

**Filter** opens the chemical filter (by chemical or by mod). The **stock** means "store up to N mB".
''' + fill(COMMON_EN, FILTER_EN=' and the filter', WHAT_EN='chemical tank') + '''
## Recipe

A Wireless Tank with glass bottles and iron (only with Mekanism).

<RecipeFor id="wirelessautomate:storage_chemical_tank" />
''')
