"""Página filter-card.md do guia."""
from guia_base import *  # noqa: F401,F403


PAGINA = page('filter-card.md', front('Cartão de Filtro', 'wirelessautomate:filter_card', 6, item_ids=['wirelessautomate:filter_card']) + '''
# Cartão de Filtro

<ItemImage id="wirelessautomate:filter_card" scale="2" float="left" />

Um filtro completo num item, para reaproveitar em várias faces. Vale para itens ou para fluidos.

<br clear="all" />

## Como usar

1. Clique direito no ar para editar o filtro do cartão.
2. Ponha o cartão num dos dois slots de cartão da face, na tela do roteador.

| Gesto | O que faz |
| --- | --- |
| Shift + clique direito no ar (cartão vazio) | Alterna entre itens e fluidos. |
| Aba **Mais** do filtro da face | Exporta o filtro da face para o cartão, ou importa dele. |
| Bancada: cartão configurado + cartões vazios | Cópias iguais (o original volta). |

Não há cartão de químicos: eles usam só o filtro embutido da face.

## Receita

Rende 2 cartões.

<RecipeFor id="wirelessautomate:filter_card" />
''', front('Filter Card', 'wirelessautomate:filter_card', 6, item_ids=['wirelessautomate:filter_card']) + '''
# Filter Card

<ItemImage id="wirelessautomate:filter_card" scale="2" float="left" />

A full filter in an item, to reuse on many faces. Works for items or for fluids.

<br clear="all" />

## How to use it

1. Right-click the air to edit the card's filter.
2. Put the card in one of the face's two card slots, on the router screen.

| Gesture | What it does |
| --- | --- |
| Shift + right-click the air (empty card) | Switch between items and fluids. |
| **More** tab of the face's filter | Export the face's filter to the card, or import from it. |
| Crafting table: configured card + blank cards | Identical copies (the original comes back). |

There is no chemical card: chemicals only use the face's built-in filter.

## Recipe

Makes 2 cards.

<RecipeFor id="wirelessautomate:filter_card" />
''')
