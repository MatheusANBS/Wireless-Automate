"""Página chemicals.md do guia."""
from guia_base import *  # noqa: F401,F403


PAGINA = page('chemicals.md', front('Químicos (Mekanism)', 'minecraft:glass_bottle', 15) + '''
# Químicos do Mekanism

Com o **Mekanism**, o roteador ganha a aba **Químicos**: gases, líquidos de infusão, pigmentos e slurries. Sem
ele, a aba some.

## Importante: ligue a face no Mekanism

As máquinas e tanques do Mekanism vêm com as faces **desligadas**. Ligue a face com a ferramenta de configuração
do Mekanism, como para um tubo; senão o roteador não vê o químico e o modo da face fica indisponível.

## O que funciona

| Recurso | Químicos |
| --- | --- |
| Modo, prioridade, redstone e rede | Sim. |
| Vazão | O limite de fluido do tier ([Roteador](router.md)). |
| Filtro exato e por mod (`@mod`) | Sim, com estoque. |
| Filtro por tag | Não. |
| Cartão de Filtro | Não: use o filtro da face. |

## Pôr no filtro

| Jeito | Como |
| --- | --- |
| **Inventário** | Shift + clique num tanque com o químico. |
| **JEI** | Arraste o químico para a grade. |
| **Digitando** | Em **Mais**, o id: `mekanism:hydrogen`... |
''', front('Chemicals (Mekanism)', 'minecraft:glass_bottle', 15) + '''
# Mekanism chemicals

With **Mekanism**, the router gets a **Chemicals** tab: gases, infuse types, pigments and slurries. Without it,
the tab is hidden.

## Important: enable the face in Mekanism

Mekanism machines and tanks come with their faces **disabled**. Enable the face with Mekanism's configurator, as
for a tube; otherwise the router can't see the chemical and the face mode is unavailable.

## What works

| Feature | Chemicals |
| --- | --- |
| Mode, priority, redstone and network | Yes. |
| Throughput | The tier's fluid limit ([Router](router.md)). |
| Exact and mod (`@mod`) filter | Yes, with stock. |
| Tag filter | No. |
| Filter Card | No: use the face's filter. |

## Adding to the filter

| Way | How |
| --- | --- |
| **Inventory** | Shift + click a tank holding the chemical. |
| **JEI** | Drag the chemical onto the grid. |
| **Typing** | Under **More**, the id: `mekanism:hydrogen`... |
''')
