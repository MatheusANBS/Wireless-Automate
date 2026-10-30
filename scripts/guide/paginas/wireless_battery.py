"""Página wireless-battery.md do guia."""
from guia_base import *  # noqa: F401,F403


PAGINA = page('wireless-battery.md', front('Bateria Wireless', 'wirelessautomate:storage_battery', 13,
                                  item_ids=['wirelessautomate:storage_battery']) + '''
# Bateria Wireless

<ItemImage id="wirelessautomate:storage_battery" scale="2" float="left" />

Guarda muita energia (FE); no Ultimate, sem limite. Presa a um roteador na aba **Energia**, carrega e
descarrega bilhões de FE de uma vez. Cada tier guarda **uma hora** da geração cheia do
[Ressonador](signal-resonator.md) do mesmo tier.

<br clear="all" />

''' + fill(SCENE_STORAGE_PAIR, BLOCK='storage_battery', LINE='Energia de uma Bateria para outra pela aba Energia') + '''

## Capacidade

''' + capacity_table('pt', 'battery', 'FE') + '''

## A tela

Clique na Bateria: a barra mostra a carga e se ela está **carregando**, **descarregando** ou
**estável**. Passe o mouse na barra para o valor exato. Não tem filtro: energia é uma só.
''' + fill(COMMON_PT, FILTER_PT='', WHAT_PT='uma bateria') + '''
## Receita

<RecipeFor id="wirelessautomate:storage_battery" />
''', front('Wireless Battery', 'wirelessautomate:storage_battery', 13,
           item_ids=['wirelessautomate:storage_battery']) + '''
# Wireless Battery

<ItemImage id="wirelessautomate:storage_battery" scale="2" float="left" />

Stores a lot of energy (FE); on Ultimate, unlimited. Attached to a router on the **Energy** tab, it
charges and discharges billions of FE at once. Each tier holds **one hour** of the same tier's
[Resonator](signal-resonator.md) at full output.

<br clear="all" />

''' + fill(SCENE_STORAGE_PAIR, BLOCK='storage_battery', LINE='Energy from one Battery to another through the Energy tab') + '''

## Capacity

''' + capacity_table('en', 'battery', 'FE') + '''

## The screen

Click the Battery: the bar shows the charge and whether it's **charging**, **draining** or **idle**.
Hover the bar for the exact value. No filter: energy is just one thing.
''' + fill(COMMON_EN, FILTER_EN='', WHAT_EN='battery') + '''
## Recipe

<RecipeFor id="wirelessautomate:storage_battery" />
''')
