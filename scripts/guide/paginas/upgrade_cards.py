"""Página upgrade-cards.md do guia."""
from guia_base import *  # noqa: F401,F403


def card_table(lang):
    """O antes e o depois de cada cartão, na escada com o Allthemodium."""
    head = {'pt': '| Cartão | Itens/s | Fluido e químico (mB/s) | Energia (FE/t) | Source/s | Alcance |',
            'en': '| Card | Items/s | Fluid and chemical (mB/s) | Energy (FE/t) | Source/s | Range |'}[lang]
    rows = [head, '| --- | --- | --- | --- | --- | --- |']
    for i in range(1, len(TIERS)):
        cells = [f'{num(lang, RATES[k][i - 1])} → **{num(lang, RATES[k][i])}**' for k in RATES]
        before, after = RANGE[lang][i - 1].lower(), RANGE[lang][i].lower()
        reach = after if before == after else f'{before} → **{after}**'
        rows.append(f"| {item('tier_core_' + TIERS[i][0])} **{tier_name(lang, i)}** | " + ' | '.join(cells)
                    + f' | {reach} |')
    without = {'pt': ' Sem ele, o Cartão Ultimate vem depois do Cartão Esmeralda ({} → sem limite).',
               'en': ' Without it, the Ultimate Card comes after Emerald ({} → unlimited).'}[lang]
    return '\n'.join(rows) + '\n\n' + ATM_NOTE[lang] + without.format(num(lang, RATES['item'][3]) + (
        ' itens/s' if lang == 'pt' else ' items/s'))


CARD_IMAGES = '\n'.join(f'  <ItemImage id="wirelessautomate:tier_core_{t[0]}" scale="2" />' for t in TIERS[1:])


PAGINA = page('upgrade-cards.md', front('Cartões de Upgrade', 'wirelessautomate:tier_core_advanced', 3, item_ids=[
    f'wirelessautomate:tier_core_{t[0]}' for t in TIERS[1:]]) + '''
# Cartões de Upgrade

<Row gap="8">
''' + CARD_IMAGES + '''
</Row>

Os cartões sobem o tier: Básico → Avançado → Elite → Esmeralda → Ultimate. Com o **Allthemodium**,
entram Allthemodium → Vibranium → Unobtainium antes do Ultimate.

## Como usar

| Onde | Como |
| --- | --- |
| **No mundo** | Shift + clique com o cartão no roteador ou armazenamento. Toda a configuração continua. |
| **Na bancada, com o cartão** | O bloco + o cartão, em qualquer posição. |
| **Na bancada, sem cartão** | O bloco no centro do anel do tier seguinte. |

O cartão só vale no tier escrito no nome ("Básico → Elite"). Em outro tier, ele não é gasto e um
aviso diz de qual tier ele sobe.

Também sobem os armazenamentos ([Baú](wireless-chest.md), [Tanque](wireless-tank.md),
[Bateria](wireless-battery.md), [Tanque Químico](wireless-chemical-tank.md) e
[Tanque de Source](wireless-source-tank.md)) sem perder o conteúdo.

## O que cada cartão aumenta

Por face e por tipo. O tooltip do cartão mostra os valores do seu servidor.

''' + card_table('pt') + '''

Cada passo multiplica a vazão por 8 (a energia do Unobtainium, por 64). Alcance e dimensões contam pelo tier de quem **envia**.

## Receitas

Cada degrau tem um **anel** de oito itens na bancada. O centro decide o resultado:

| Centro do anel | Resultado |
| --- | --- |
| **Redstone** | O cartão de um degrau, por exemplo **Avançado → Elite**. |
| **Um cartão** (ex.: Básico → Avançado) | O cartão que pula: **Básico → Elite**. Junta os anéis dos degraus pulados. |
| **O roteador ou armazenamento** do degrau anterior | O mesmo bloco, já no tier novo. |

<RecipeFor id="wirelessautomate:tier_core_advanced" />
<RecipeFor id="wirelessautomate:tier_core_elite" />
<RecipeFor id="wirelessautomate:tier_core_emerald" />
<RecipeFor id="wirelessautomate:tier_core_ultimate" />

As receitas mostram a Redstone no centro; o JEI mostra os três caminhos do seu pack.

Os anéis do Allthemodium usam o próprio metal. No ATM10, o do Ultimate leva fragmentos de ATM Star,
ovo do dragão e blocos de liga Unobtainium-Allthemodium. Um cartão feito antes de o Allthemodium
entrar no pack (ex.: Esmeralda → Ultimate) continua valendo e pula esses degraus.
''', front('Upgrade Cards', 'wirelessautomate:tier_core_advanced', 3, item_ids=[
    f'wirelessautomate:tier_core_{t[0]}' for t in TIERS[1:]]) + '''
# Upgrade Cards

<Row gap="8">
''' + CARD_IMAGES + '''
</Row>

Cards raise the tier: Basic → Advanced → Elite → Emerald → Ultimate. With **Allthemodium**,
Allthemodium → Vibranium → Unobtainium come before Ultimate.

## How to use it

| Where | How |
| --- | --- |
| **In the world** | Shift + use the card on the router or storage. All its configuration is kept. |
| **In a crafting table, with the card** | The block + the card, in any slots. |
| **In a crafting table, no card** | The block in the middle of the next tier's ring. |

A card only works on the tier in its name ("Basic → Elite"). On another tier, it isn't used up and
a message says which tier it raises.

They also raise the storages ([Chest](wireless-chest.md), [Tank](wireless-tank.md),
[Battery](wireless-battery.md), [Chemical Tank](wireless-chemical-tank.md) and
[Source Tank](wireless-source-tank.md)) keeping their contents.

## What each card raises

Per face and per type. The card's tooltip shows your server's values.

''' + card_table('en') + '''

Each step multiplies throughput by 8 (Unobtainium energy, by 64). Range and dimensions follow the **sender's** tier.

## Recipes

Each step has a **ring** of eight items in the crafting grid. The middle decides the result:

| Middle of the ring | Result |
| --- | --- |
| **Redstone** | The one-step card, for example **Advanced → Elite**. |
| **A card** (e.g. Basic → Advanced) | The skipping card: **Basic → Elite**. It adds up the rings of the skipped steps. |
| **The router or storage** one step below | The same block, already at the new tier. |

<RecipeFor id="wirelessautomate:tier_core_advanced" />
<RecipeFor id="wirelessautomate:tier_core_elite" />
<RecipeFor id="wirelessautomate:tier_core_emerald" />
<RecipeFor id="wirelessautomate:tier_core_ultimate" />

The recipes show Redstone in the middle; JEI shows the three paths in your pack.

Allthemodium's rings use the metal itself. In ATM10, Ultimate's takes ATM Star shards, a dragon egg
and Unobtainium-Allthemodium alloy blocks. A card made before Allthemodium joined the pack (e.g.
Emerald → Ultimate) still works and skips those steps.
''')
