"""Página index.md do guia."""
from guia_base import *  # noqa: F401,F403


PAGINA = page('index.md', front('Wireless Automate', 'wirelessautomate:router', parent=None) + '''
# Wireless Automate

<ItemImage id="wirelessautomate:router" scale="2" float="left" />

Itens, fluidos, energia, químicos do Mekanism e Source do Ars Nouveau, **sem fios**. Prenda um
roteador em cada máquina e diga o que cada face faz. Os roteadores da mesma rede trocam recursos
entre si, sem canos.

<br clear="all" />

## Por onde começar

1. Leia [Primeiros passos](getting-started.md): dois baús trocando itens em um minuto.
2. Entenda as [redes](networks.md) e os [filtros](filters.md).
3. Algo não funciona? Veja [Problemas comuns](troubleshooting.md).

## Em 30 segundos

| Conceito | O que é |
| --- | --- |
| **Roteador** | Preso numa máquina, liga as seis faces dela à rede. |
| **Face** | Cada face, em cada tipo, tem um modo: Extrai, Insere, Armazém ou Nenhum. |
| **Rede** | Roteadores da mesma rede trocam recursos do mesmo tipo, dentro do alcance. |
| **Tier** | Dá a vazão e o alcance. Sobe com os cartões de upgrade. |

## Itens do mod

| Item | Para que serve |
| --- | --- |
| ''' + item('router') + ''' [Roteador Wireless](router.md) | Liga uma máquina à rede. |
| ''' + item('tier_core_advanced') + ''' [Cartões de Upgrade](upgrade-cards.md) | Sobem o tier do roteador: mais vazão e alcance. |
| ''' + item('filter_card') + ''' [Cartão de Filtro](filter-card.md) | Um filtro pronto para várias faces. |
| ''' + item('linker') + ''' [Vinculador](linker.md) | Liga máquinas a um roteador de alcance e abre o roteador de uma máquina. |
| ''' + item('configurator') + ''' [Configurador](configurator.md) | Copia a configuração de um roteador para outro. |
| ''' + item('network_tablet') + ''' [Tablet de Rede](network-tablet.md) | Vê e gerencia suas redes de qualquer lugar. |
| ''' + item('chunk_loader_upgrade') + ''' [Upgrade de Chunk Loading](chunk-loading.md) | O roteador trabalha com você longe. |
| ''' + item('storage_chest') + ''' [Baú Wireless](wireless-chest.md) | Milhões de itens por tipo; entre dois deles, um tipo inteiro passa de uma vez. |
| ''' + item('storage_tank') + ''' [Tanque Wireless](wireless-tank.md) | Vários fluidos num tanque só, com bilhões de mB. |
| ''' + item('storage_battery') + ''' [Bateria Wireless](wireless-battery.md) | Energia em grande quantidade; no Ultimate, sem limite. |
| ''' + item('storage_chemical_tank') + ''' [Tanque Químico Wireless](wireless-chemical-tank.md) | O tanque dos químicos do Mekanism. |
| ''' + item('storage_source_tank') + ''' [Tanque de Source Wireless](wireless-source-tank.md) | Muito Source do Ars Nouveau. |
| ''' + item('signal_crystal') + ''' [Quartzo de Sinal e cristais](signal-quartz.md) | O minério e os cristais que movem o Ressonador. |
| ''' + item('resonance_core') + ''' [Ressonador de Sinal](signal-resonator.md) | Gerador de energia: cristais, sintonia e FE. |
| ''' + item('crystal_repairer') + ''' [Reparador de Cristais](crystal-repairer.md) | Conserta cristais rachados, até oito de uma vez. |
| ''' + item('signal_screen') + ''' [Tela de Sinal](signal-screen.md) | Painel de parede, do tamanho que quiser, com os números das redes. |
| [Editor de receitas](recipe-editor.md) | Operadores ajustam as receitas do mod no jogo. |

## Este guia

Você ganha o livro ao entrar no mundo pela primeira vez; ele também está na aba criativa. Para fazer
outro: livro + redstone, na bancada. Com o mouse sobre um item do mod (inventário ou JEI), segure
**G** para abrir a página dele.

<Recipe id="wirelessautomate:guide" />

## Todas as páginas

<SubPages icons={true} />
''', front('Wireless Automate', 'wirelessautomate:router', parent=None) + '''
# Wireless Automate

<ItemImage id="wirelessautomate:router" scale="2" float="left" />

Items, fluids, energy, Mekanism chemicals and Ars Nouveau Source, **wirelessly**. Attach a router
to each machine and say what each face does. Routers on the same network trade resources with each
other, with no pipes.

<br clear="all" />

## Where to start

1. Read [Getting started](getting-started.md): two chests trading items in a minute.
2. Learn about [networks](networks.md) and [filters](filters.md).
3. Something not working? See [Troubleshooting](troubleshooting.md).

## In 30 seconds

| Concept | What it is |
| --- | --- |
| **Router** | Attached to a machine, it connects all six of its faces to the network. |
| **Face** | Each face, for each type, has a mode: Extract, Insert, Storage or None. |
| **Network** | Routers on the same network trade resources of the same type, within range. |
| **Tier** | Sets throughput and range. Raised with upgrade cards. |

## Items

| Item | What it's for |
| --- | --- |
| ''' + item('router') + ''' [Wireless Router](router.md) | Connects a machine to the network. |
| ''' + item('tier_core_advanced') + ''' [Upgrade Cards](upgrade-cards.md) | Raise the router's tier: more throughput and range. |
| ''' + item('filter_card') + ''' [Filter Card](filter-card.md) | A ready-made filter for many faces. |
| ''' + item('linker') + ''' [Linker](linker.md) | Links machines to a range router and opens the router of a machine. |
| ''' + item('configurator') + ''' [Configurator](configurator.md) | Copies a router's configuration to another. |
| ''' + item('network_tablet') + ''' [Network Tablet](network-tablet.md) | See and manage your networks from anywhere. |
| ''' + item('chunk_loader_upgrade') + ''' [Chunk Loading Upgrade](chunk-loading.md) | The router keeps working while you're away. |
| ''' + item('storage_chest') + ''' [Wireless Chest](wireless-chest.md) | Millions of items by type; between two of them, a whole type moves at once. |
| ''' + item('storage_tank') + ''' [Wireless Tank](wireless-tank.md) | Many fluids in one tank, with billions of mB. |
| ''' + item('storage_battery') + ''' [Wireless Battery](wireless-battery.md) | Huge amounts of energy; unlimited on Ultimate. |
| ''' + item('storage_chemical_tank') + ''' [Wireless Chemical Tank](wireless-chemical-tank.md) | The tank for Mekanism chemicals. |
| ''' + item('storage_source_tank') + ''' [Wireless Source Tank](wireless-source-tank.md) | Lots of Ars Nouveau Source. |
| ''' + item('signal_crystal') + ''' [Signal Quartz and crystals](signal-quartz.md) | The ore and the crystals that power the Resonator. |
| ''' + item('resonance_core') + ''' [Signal Resonator](signal-resonator.md) | Power generator: crystals, tuning and FE. |
| ''' + item('crystal_repairer') + ''' [Crystal Repairer](crystal-repairer.md) | Fixes cracked crystals, up to eight at a time. |
| ''' + item('signal_screen') + ''' [Signal Screen](signal-screen.md) | Wall panel, as big as you want, with your networks' numbers. |
| [Recipe editor](recipe-editor.md) | Operators tweak the mod's recipes in-game. |

## This guide

You get this book the first time you join the world; it's also in the creative tab. To make
another: book + redstone, in a crafting table. With the mouse over one of the mod's items (inventory
or JEI), hold **G** to open its page.

<Recipe id="wirelessautomate:guide" />

## All pages

<SubPages icons={true} />
''')
