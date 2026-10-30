"""Página wireless-source-tank.md do guia."""
from guia_base import *  # noqa: F401,F403


PAGINA = page('wireless-source-tank.md', front('Tanque de Source Wireless', 'wirelessautomate:storage_source_tank', 17,
                                      item_ids=['wirelessautomate:storage_source_tank']) + '''
# Tanque de Source Wireless

<ItemImage id="wirelessautomate:storage_source_tank" scale="2" float="left" />

Guarda muito mais **Source** que uma Source Jar. Só existe com o Ars Nouveau.

<br clear="all" />

''' + fill(SCENE_STORAGE_PAIR, BLOCK='storage_source_tank', LINE='De um Tanque para outro pela aba Source') + '''

## Como usar

- **Sourcelinks** num raio de 5 blocos depositam nele.
- **Máquinas do Ars** por perto (Enchanting Apparatus, Imbuement Chamber, rituais, Spell Turrets e Relays de
  depósito) tiram dele como de uma Source Jar, sem encostar.
- **Relays** e outros blocos do Ars ligam nele direto.
- **Roteador:** na aba **Source**, funciona como qualquer face. Entre dois tanques, tudo passa de uma vez.

Clique no Tanque para ver quanto ele tem e a vazão por segundo; a coluna de vidro do bloco também mostra o nível.
Não tem filtro: Source é uma só.

## Capacidade

''' + capacity_table('pt', 'source_tank', 'Source') + '''

## Em comum com o Baú

- **Tiers:** sobe com os [Cartões de Upgrade](upgrade-cards.md), no mundo ou na bancada, sem perder o conteúdo.
- **Quebrar:** o item leva o conteúdo. Cheio, sempre vira item (no criativo, sem picareta ou numa explosão).
- **Comparador:** o sinal sobe com a ocupação.
- **Faces:** as seis são iguais.

## Receita

Um Tanque Wireless com gemas de Source e ferro (só com o Ars Nouveau).

<RecipeFor id="wirelessautomate:storage_source_tank" />
''', front('Wireless Source Tank', 'wirelessautomate:storage_source_tank', 17,
           item_ids=['wirelessautomate:storage_source_tank']) + '''
# Wireless Source Tank

<ItemImage id="wirelessautomate:storage_source_tank" scale="2" float="left" />

Holds far more **Source** than a Source Jar. It only exists with Ars Nouveau.

<br clear="all" />

''' + fill(SCENE_STORAGE_PAIR, BLOCK='storage_source_tank', LINE='From one Tank to another through the Source tab') + '''

## How to use it

- **Sourcelinks** within 5 blocks deposit into it.
- **Ars machines** nearby (Enchanting Apparatus, Imbuement Chamber, rituals, Spell Turrets and deposit Relays)
  draw from it as from a Source Jar, without touching it.
- **Relays** and other Ars blocks connect to it directly.
- **Router:** on the **Source** tab, it works like any face. Between two tanks, everything moves at once.

Click the Tank to see how much it holds and the rate per second; the block's glass column also shows the level.
It has no filter: Source is one thing.

## Capacity

''' + capacity_table('en', 'source_tank', 'Source') + '''

## Shared with the Chest

- **Tiers:** raised with the [Upgrade Cards](upgrade-cards.md), in the world or in a crafting table, keeping the contents.
- **Breaking:** the item takes the contents. A full one always becomes an item (in creative, without a pickaxe or in an explosion).
- **Comparator:** the signal rises with how full it is.
- **Faces:** all six are the same.

## Recipe

A Wireless Tank with Source gems and iron (only with Ars Nouveau).

<RecipeFor id="wirelessautomate:storage_source_tank" />
''')
