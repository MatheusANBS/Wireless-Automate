"""Página chunk-loading.md do guia."""
from guia_base import *  # noqa: F401,F403


PAGINA = page('chunk-loading.md', front('Upgrade de Chunk Loading', 'wirelessautomate:chunk_loader_upgrade', 10, item_ids=['wirelessautomate:chunk_loader_upgrade']) + '''
# Upgrade de Chunk Loading

<ItemImage id="wirelessautomate:chunk_loader_upgrade" scale="2" float="left" />

Mantém o chunk do roteador e da máquina carregado. O roteador continua trabalhando com você longe.

<br clear="all" />

## Como usar

1. Abra a tela do roteador.
2. Ponha o upgrade no slot do topo da tela.
3. Confira o estado no topo da tela.

Cada jogador mantém até 16 chunks. Vários roteadores no mesmo chunk contam uma vez.

## Estados

| Estado | Significa |
| --- | --- |
| **Ativo: chunks carregados** | O roteador e a máquina trabalham longe dos jogadores. |
| **Inativo: limite de chunks do dono** | Quem pôs o upgrade já está no limite de chunks. |
| **Inativo: desligado no servidor** | O servidor desativou este upgrade. |

## Receita

<RecipeFor id="wirelessautomate:chunk_loader_upgrade" />
''', front('Chunk Loading Upgrade', 'wirelessautomate:chunk_loader_upgrade', 10, item_ids=['wirelessautomate:chunk_loader_upgrade']) + '''
# Chunk Loading Upgrade

<ItemImage id="wirelessautomate:chunk_loader_upgrade" scale="2" float="left" />

Keeps the router's and the machine's chunk loaded. The router keeps working while you're away.

<br clear="all" />

## How to use it

1. Open the router's screen.
2. Put the upgrade in the slot at the top of the screen.
3. Check the state at the top of the screen.

Each player keeps up to 16 chunks. Several routers in one chunk count once.

## States

| State | Meaning |
| --- | --- |
| **Active: chunks loaded** | The router and the machine work away from players. |
| **Inactive: owner's chunk limit** | Whoever placed the upgrade is already at the chunk limit. |
| **Inactive: disabled on the server** | The server has turned this upgrade off. |

## Recipe

<RecipeFor id="wirelessautomate:chunk_loader_upgrade" />
''')
