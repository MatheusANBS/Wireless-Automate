"""Página source.md do guia."""
from guia_base import *  # noqa: F401,F403


PAGINA = page('source.md', front('Source (Ars Nouveau)', 'minecraft:amethyst_shard', 16) + '''
# Source do Ars Nouveau

Com o **Ars Nouveau**, o roteador ganha a aba **Source**. Sem ele, a aba some.

## Como usar

Exemplo: dos Sourcelinks até o Enchanting Apparatus.

1. Perto dos Sourcelinks, uma **Source Jar** com roteador em **Extrair**, na aba **Source**.
2. Perto da máquina do Ars, outra Source Jar com roteador em **Inserir**, na mesma rede.
3. Os Sourcelinks enchem a primeira jarra; o roteador leva a Source para a segunda, e a máquina tira dela.

O roteador também liga direto nos Relays e na Imbuement Chamber. Um
[Tanque de Source Wireless](wireless-source-tank.md) guarda muito mais que as Source Jars e funciona igual.

## O que funciona

| Recurso | Source |
| --- | --- |
| Modo, prioridade, redstone e rede | Sim. |
| Configurador | Sim: o tipo **Source** na roda e no atalho do mouse. |
| Vazão | Básico 100/s, Avançado 800/s, Elite 6.400/s, Esmeralda 51.200/s, Ultimate sem limite ([Roteador](router.md)). |
| Filtro e Cartão de Filtro | Não: a Source não tem tipos. |
''', front('Source (Ars Nouveau)', 'minecraft:amethyst_shard', 16) + '''
# Ars Nouveau Source

With **Ars Nouveau**, the router gets a **Source** tab. Without it, the tab is hidden.

## How to use it

Example: from the Sourcelinks to the Enchanting Apparatus.

1. Near the Sourcelinks, a **Source Jar** with a router set to **Extract**, on the **Source** tab.
2. Near the Ars machine, another Source Jar with a router set to **Insert**, on the same network.
3. The Sourcelinks fill the first jar; the router carries the Source to the second, and the machine draws from it.

The router also connects straight to Relays and the Imbuement Chamber. A
[Wireless Source Tank](wireless-source-tank.md) holds far more than Source Jars and works the same.

## What works

| Feature | Source |
| --- | --- |
| Mode, priority, redstone and network | Yes. |
| Configurator | Yes: the **Source** type on the wheel and the mouse wheel shortcut. |
| Throughput | Basic 100/s, Advanced 800/s, Elite 6,400/s, Emerald 51,200/s, Ultimate unlimited ([Router](router.md)). |
| Filter and Filter Card | No: Source has no types. |
''')
