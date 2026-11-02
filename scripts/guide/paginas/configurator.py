"""Página configurator.md do guia."""
from guia_base import *  # noqa: F401,F403


PAGINA = page('configurator.md', front('Configurador', 'wirelessautomate:configurator', 8, item_ids=['wirelessautomate:configurator']) + '''
# Configurador

<ItemImage id="wirelessautomate:configurator" scale="2" float="left" />

Sem função por enquanto: uma ferramenta nova chega numa próxima versão.

<br clear="all" />

O item continua existindo, com a mesma receita. Para copiar e colar perfis de roteadores, máquinas e
grupos, use o [Tablet](network-tablet.md).

## Receita

<RecipeFor id="wirelessautomate:configurator" />
''', front('Configurator', 'wirelessautomate:configurator', 8, item_ids=['wirelessautomate:configurator']) + '''
# Configurator

<ItemImage id="wirelessautomate:configurator" scale="2" float="left" />

No function for now: a new tool arrives in a future version.

<br clear="all" />

The item is still here, with the same recipe. To copy and paste profiles of routers, machines and
groups, use the [Tablet](network-tablet.md).

## Recipe

<RecipeFor id="wirelessautomate:configurator" />
''')
