"""Gera as páginas do livro-guia (GuideME) em inglês e em português.

    python scripts/guide/gerar_guia.py

As páginas vão para src/main/resources/assets/wirelessautomate/guides/wirelessautomate/guide/
(inglês, o padrão do GuideME) e para _pt_br/ (português). Cada página é escrita aqui nos dois
idiomas lado a lado, para não desalinharem; edite este arquivo, não os .md gerados.

Regras do GuideME que valem a pena lembrar:
- anotações de cena (BlockAnnotation, LineAnnotation...) com o texto em linha própria; numa linha
  só, elas não aparecem;
- o comando de teste é /guidemec wirelessautomate:guide open wirelessautomate:<página>.md;
- os números das tabelas são os padrões do ResourceType, do StorageKind e do RouterTier (mantenha em dia).
"""
import os

from PIL import Image

ROOT = os.path.join(os.path.dirname(os.path.abspath(__file__)), '..', '..')
BASE = os.path.join(ROOT, 'src/main/resources/assets/wirelessautomate/guides/wirelessautomate/guide')


def write(lang, name, text):
    folder = BASE if lang == 'en' else os.path.join(BASE, '_pt_br')
    os.makedirs(folder, exist_ok=True)
    with open(os.path.join(folder, name), 'w', encoding='utf-8', newline='\n') as fh:
        fh.write(text.strip('\n') + '\n')


def front(title, icon, position=None, parent='index.md', item_ids=()):
    lines = ['---', 'navigation:', f'  title: {title}', f'  icon: {icon}']
    if parent:
        lines.append(f'  parent: {parent}')
    if position is not None:
        lines.append(f'  position: {position}')
    if item_ids:
        lines.append('item_ids:')
        lines += [f'  - {i}' for i in item_ids]
    lines.append('---')
    return '\n'.join(lines) + '\n\n'


def fill(template, **kw):
    for k, v in kw.items():
        template = template.replace('{' + k + '}', v)
    return template


def item(id_):
    return f'<ItemImage id="wirelessautomate:{id_}" />'


PORT = {m: f'![{m}](images/port_{m}.png)' for m in ('extract', 'insert', 'both', 'none')}

# ------------------------------------------------------------------ cenas 3D

SCENE_TWO_CHESTS = '''<GameScene zoom="4" interactive={true}>
  <Block id="minecraft:chest" x="0" y="0" z="0" />
  <Block id="wirelessautomate:router" x="0" y="1" z="0" p:facing="up" p:tier="basic" />
  <Block id="minecraft:chest" x="4" y="0" z="0" />
  <Block id="wirelessautomate:router" x="4" y="1" z="0" p:facing="up" p:tier="basic" />
  <LineAnnotation from="0.5 1.5 0.5" to="4.5 1.5 0.5" color="#45d6cc" thickness="0.08">
    {LINE}
  </LineAnnotation>
  <BlockAnnotation x="0" y="1" z="0" color="#4a8cff">
    {A}
  </BlockAnnotation>
  <BlockAnnotation x="4" y="1" z="0" color="#ff9a3c">
    {B}
  </BlockAnnotation>
  <IsometricCamera yaw="200" pitch="30" />
</GameScene>'''

SCENE_TIERS = '''<GameScene zoom="3" interactive={true}>
  <Block id="minecraft:chest" x="0" y="0" z="0" />
  <Block id="wirelessautomate:router" x="0" y="1" z="0" p:facing="up" p:tier="basic" />
  <Block id="minecraft:furnace" x="2" y="0" z="0" />
  <Block id="wirelessautomate:router" x="2" y="1" z="0" p:facing="up" p:tier="advanced" />
  <Block id="minecraft:barrel" x="4" y="0" z="0" p:facing="up" />
  <Block id="wirelessautomate:router" x="4" y="1" z="0" p:facing="up" p:tier="elite" />
  <Block id="minecraft:smoker" x="6" y="0" z="0" />
  <Block id="wirelessautomate:router" x="6" y="1" z="0" p:facing="up" p:tier="emerald" />
  <Block id="minecraft:blast_furnace" x="8" y="0" z="0" />
  <Block id="wirelessautomate:router" x="8" y="1" z="0" p:facing="up" p:tier="ultimate" />
  <BlockAnnotation x="0" y="1" z="0" color="#c8ccd2">
    {BASIC}
  </BlockAnnotation>
  <BlockAnnotation x="2" y="1" z="0" color="#e2b347">
    {ADVANCED}
  </BlockAnnotation>
  <BlockAnnotation x="4" y="1" z="0" color="#45d6cc">
    {ELITE}
  </BlockAnnotation>
  <BlockAnnotation x="6" y="1" z="0" color="#2fdc62">
    {EMERALD}
  </BlockAnnotation>
  <BlockAnnotation x="8" y="1" z="0" color="#a06bff">
    {ULTIMATE}
  </BlockAnnotation>
  <IsometricCamera yaw="200" pitch="30" />
</GameScene>'''

SCENE_SIDES = '''<GameScene zoom="4" interactive={true}>
  <Block id="minecraft:furnace" x="0" y="0" z="0" />
  <Block id="wirelessautomate:router" x="0" y="1" z="0" p:facing="up" p:tier="basic" />
  <Block id="wirelessautomate:router" x="0" y="0" z="1" p:facing="south" p:tier="basic" />
  <BlockAnnotation x="0" y="1" z="0" color="#4a8cff">
    {TOP}
  </BlockAnnotation>
  <BlockAnnotation x="0" y="0" z="1" color="#ff9a3c">
    {SIDE}
  </BlockAnnotation>
  <IsometricCamera yaw="200" pitch="30" />
</GameScene>'''

SCENE_STORAGE = '''<GameScene zoom="3" interactive={true}>
  <Block id="minecraft:furnace" x="0" y="0" z="0" />
  <Block id="wirelessautomate:router" x="0" y="1" z="0" p:facing="up" p:tier="advanced" />
  <Block id="minecraft:barrel" x="3" y="0" z="0" p:facing="up" />
  <Block id="wirelessautomate:router" x="3" y="1" z="0" p:facing="up" p:tier="advanced" />
  <Block id="minecraft:barrel" x="3" y="0" z="3" p:facing="up" />
  <Block id="wirelessautomate:router" x="3" y="1" z="3" p:facing="up" p:tier="advanced" />
  <Block id="minecraft:chest" x="0" y="0" z="3" />
  <Block id="wirelessautomate:router" x="0" y="1" z="3" p:facing="up" p:tier="advanced" />
  <LineAnnotation from="0.5 1.5 0.5" to="3.5 1.5 0.5" color="#4a8cff" thickness="0.08">
    {L1}
  </LineAnnotation>
  <LineAnnotation from="0.5 1.5 0.5" to="3.5 1.5 3.5" color="#4a8cff" thickness="0.08">
    {L1}
  </LineAnnotation>
  <LineAnnotation from="3.5 1.5 0.5" to="0.5 1.5 3.5" color="#3fc36b" thickness="0.08">
    {L2}
  </LineAnnotation>
  <LineAnnotation from="3.5 1.5 3.5" to="0.5 1.5 3.5" color="#3fc36b" thickness="0.08">
    {L2}
  </LineAnnotation>
  <BlockAnnotation x="0" y="1" z="0" color="#4a8cff">
    {FURNACE}
  </BlockAnnotation>
  <BlockAnnotation x="3" y="1" z="0" color="#3fc36b">
    {BARREL}
  </BlockAnnotation>
  <BlockAnnotation x="3" y="1" z="3" color="#3fc36b">
    {BARREL}
  </BlockAnnotation>
  <BlockAnnotation x="0" y="1" z="3" color="#ff9a3c">
    {CHEST}
  </BlockAnnotation>
  <IsometricCamera yaw="210" pitch="35" />
</GameScene>'''

SCENE_AREA = '''<GameScene zoom="3" interactive={true}>
  <Block id="minecraft:furnace" x="0" y="0" z="0" />
  <Block id="wirelessautomate:router" x="0" y="1" z="0" p:facing="up" p:tier="basic" />
  <Block id="minecraft:furnace" x="2" y="0" z="0" />
  <Block id="wirelessautomate:router" x="2" y="1" z="0" p:facing="up" p:tier="basic" />
  <Block id="minecraft:furnace" x="4" y="0" z="0" />
  <Block id="wirelessautomate:router" x="4" y="1" z="0" p:facing="up" p:tier="basic" />
  <Block id="minecraft:chest" x="2" y="0" z="2" />
  <Block id="wirelessautomate:router" x="2" y="1" z="2" p:facing="up" p:tier="basic" />
  <BoxAnnotation min="0 0 0" max="5 2 3" color="#45d6cc" thickness="0.05">
    {BOX}
  </BoxAnnotation>
  <BlockAnnotation x="2" y="1" z="2" color="{OTHER_COLOR}">
    {OTHER}
  </BlockAnnotation>
  <IsometricCamera yaw="200" pitch="35" />
</GameScene>'''

# ------------------------------------------------------------------ textos repetidos

# Tiers na ordem do RouterTier: id, nome pt, nome en, só com o Allthemodium. Os números abaixo são os padrões da
# config (ResourceType e StorageKind); 0 = sem limite.
TIERS = [
    ('basic', 'Básico', 'Basic', False),
    ('advanced', 'Avançado', 'Advanced', False),
    ('elite', 'Elite', 'Elite', False),
    ('emerald', 'Esmeralda', 'Emerald', False),
    ('allthemodium', 'Allthemodium', 'Allthemodium', True),
    ('vibranium', 'Vibranium', 'Vibranium', True),
    ('unobtainium', 'Unobtainium', 'Unobtainium', True),
    ('ultimate', 'Ultimate', 'Ultimate', False),
]
RATES = {  # por tier: itens/s, fluido e químico mB/s, energia FE/t, Source/s
    'item': [32, 256, 2_048, 16_384, 131_072, 1_048_576, 8_388_608, 0],
    'fluid': [2_000, 16_000, 128_000, 1_024_000, 8_192_000, 65_536_000, 524_288_000, 0],
    'energy': [1_000, 8_000, 64_000, 512_000, 4_096_000, 32_768_000, 2_097_152_000, 0],
    'source': [100, 800, 6_400, 51_200, 409_600, 3_276_800, 26_214_400, 0],
}
RANGE = {
    'pt': ['64 blocos', '512 blocos', 'A dimensão inteira'] + ['Todas as dimensões'] * 5,
    'en': ['64 blocks', '512 blocks', 'The whole dimension'] + ['Every dimension'] * 5,
}
CAPACITY = {
    'chest': [32_768, 262_144, 2_097_152, 16_777_216, 134_217_728, 1_073_741_824, 8_589_934_592, 0],
    'tank': [256_000, 2_048_000, 16_384_000, 131_072_000, 1_048_576_000, 8_388_608_000, 67_108_864_000, 0],
    'battery': [1_000_000, 8_000_000, 64_000_000, 512_000_000, 4_096_000_000, 32_768_000_000,
                262_144_000_000, 0],
    'source_tank': [10_000, 80_000, 640_000, 5_120_000, 40_960_000, 327_680_000, 2_621_440_000, 0],
}
UNLIMITED = {'pt': 'Sem limite', 'en': 'Unlimited'}
ATM_NOTE = {'pt': '¹ Só com o mod Allthemodium.', 'en': '¹ Only with the Allthemodium mod.'}


def num(lang, n):
    """Número com o separador de milhar do idioma, ou "sem limite" para 0."""
    if n == 0:
        return UNLIMITED[lang]
    return f'{n:,}'.replace(',', '.' if lang == 'pt' else ',')


def tier_name(lang, i):
    _, pt, en, atm = TIERS[i]
    return (pt if lang == 'pt' else en) + ('¹' if atm else '')


def tier_table(lang):
    head = {'pt': '| Tier | Itens/s | Fluido e químico (mB/s) | Energia (FE/t) | Source/s | Alcance |',
            'en': '| Tier | Items/s | Fluid and chemical (mB/s) | Energy (FE/t) | Source/s | Range |'}[lang]
    rows = [head, '| --- | --- | --- | --- | --- | --- |']
    for i in range(len(TIERS)):
        rows.append(f'| **{tier_name(lang, i)}** | ' + ' | '.join(num(lang, RATES[k][i]) for k in RATES)
                    + f' | {RANGE[lang][i]} |')
    tail = {'pt': 'Source só com o Ars Nouveau.', 'en': 'Source only with Ars Nouveau.'}[lang]
    return '\n'.join(rows) + '\n\n' + ATM_NOTE[lang] + ' ' + tail


def capacity_table(lang, kind, unit):
    rows = [f'| Tier | {unit} |', '| --- | --- |']
    for i in range(len(TIERS)):
        rows.append(f'| **{tier_name(lang, i)}** | {num(lang, CAPACITY[kind][i])} |')
    return '\n'.join(rows) + '\n\n' + ATM_NOTE[lang]


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


TIER_TABLE = {'pt': tier_table('pt'), 'en': tier_table('en')}
CARD_IMAGES = '\n'.join(f'  <ItemImage id="wirelessautomate:tier_core_{t[0]}" scale="2" />' for t in TIERS[1:])

PAGES = []


def page(name, pt, en):
    PAGES.append((name, pt, en))


# =====================================================================================
# Início
# =====================================================================================
page('index.md', front('Wireless Automate', 'wirelessautomate:router', parent=None) + '''
# Wireless Automate

<ItemImage id="wirelessautomate:router" scale="2" float="left" />

Transporte **sem fios** de itens, fluidos, energia, químicos do Mekanism e Source do Ars Nouveau. Prenda um roteador em
cada máquina, diga o que cada face dela faz e pronto: todos os roteadores da mesma rede trocam
recursos entre si, sem canos.

<br clear="all" />

## Em 30 segundos

| Conceito | O que é |
| --- | --- |
| **Roteador** | Preso numa máquina, dá à rede acesso às seis faces dela. |
| **Face** | Cada face da máquina, para cada tipo, tem um modo: Extrai, Insere, Armazém ou Nenhum. |
| **Rede** | Roteadores da mesma rede trocam recursos do mesmo tipo, a qualquer distância dentro do alcance. |
| **Tier** | Define a vazão e o alcance do roteador. Sobe com os cartões de upgrade. |

## Itens do mod

| Item | Para que serve |
| --- | --- |
| ''' + item('router') + ''' [Roteador Wireless](router.md) | Liga uma máquina à rede. |
| ''' + item('tier_core_advanced') + ''' [Cartões de Upgrade](upgrade-cards.md) | Sobem o tier do roteador: mais vazão e alcance. |
| ''' + item('filter_card') + ''' [Cartão de Filtro](filter-card.md) | Um filtro pronto para usar em várias faces. |
| ''' + item('linker') + ''' [Vinculador](linker.md) | Escolhe a sua rede e põe roteadores nela, um a um ou por área. |
| ''' + item('configurator') + ''' [Configurador](configurator.md) | Copia a configuração de um roteador e cola em outros. |
| ''' + item('network_tablet') + ''' [Tablet de Rede](network-tablet.md) | Vê e gerencia todas as suas redes de qualquer lugar. |
| ''' + item('chunk_loader_upgrade') + ''' [Upgrade de Chunk Loading](chunk-loading.md) | Mantém o roteador trabalhando com você longe. |
| ''' + item('storage_chest') + ''' [Baú Wireless](wireless-chest.md) | Guarda milhões de itens por tipo; entre dois deles, um tipo inteiro passa de uma vez. |
| ''' + item('storage_tank') + ''' [Tanque Wireless](wireless-tank.md) | Vários fluidos num tanque só, com bilhões de mB. |
| ''' + item('storage_battery') + ''' [Bateria Wireless](wireless-battery.md) | Energia sem o teto de um `int`. |
| ''' + item('storage_chemical_tank') + ''' [Tanque Químico Wireless](wireless-chemical-tank.md) | O tanque para os químicos do Mekanism. |
| ''' + item('storage_source_tank') + ''' [Tanque de Source Wireless](wireless-source-tank.md) | Source do Ars Nouveau em grande quantidade. |
| ''' + item('signal_crystal') + ''' [Quartzo de Sinal e cristais](signal-quartz.md) | O minério e os cristais que movem o Ressonador. |
| ''' + item('resonance_core') + ''' [Ressonador de Sinal](signal-resonator.md) | Um gerador de energia: cristais, sintonia e FE. |
| [Editor de receitas](recipe-editor.md) | Operadores ajustam as receitas de bancada do mod dentro do jogo. |

## Por onde começar

1. Leia [Primeiros passos](getting-started.md): dois baús trocando itens em um minuto.
2. Entenda as [redes](networks.md) e os [filtros](filters.md).
3. Algo não funciona? Veja [Problemas comuns](troubleshooting.md).

## Este guia

Você recebe este livro ao entrar no mundo pela primeira vez, e ele também está na aba criativa do
mod. Para fazer outro: livro + redstone, na bancada. Com o mouse sobre um item do mod, no
inventário ou no JEI, segure **G** para abrir a página dele.

<Recipe id="wirelessautomate:guide" />

## Todas as páginas

<SubPages icons={true} />
''', front('Wireless Automate', 'wirelessautomate:router', parent=None) + '''
# Wireless Automate

<ItemImage id="wirelessautomate:router" scale="2" float="left" />

**Wireless** transport of items, fluids, energy, Mekanism chemicals and Ars Nouveau Source. Attach a router to each
machine, say what each of its faces does and you're done: every router on the same network trades
resources with the others, with no pipes.

<br clear="all" />

## In 30 seconds

| Concept | What it is |
| --- | --- |
| **Router** | Attached to a machine, it gives the network access to all six of its faces. |
| **Face** | Each machine face, for each type, has a mode: Extract, Insert, Storage or None. |
| **Network** | Routers on the same network trade resources of the same type, at any distance within range. |
| **Tier** | Sets the router's throughput and range. Raised with upgrade cards. |

## Items

| Item | What it's for |
| --- | --- |
| ''' + item('router') + ''' [Wireless Router](router.md) | Connects a machine to the network. |
| ''' + item('tier_core_advanced') + ''' [Upgrade Cards](upgrade-cards.md) | Raise the router's tier: more throughput and range. |
| ''' + item('filter_card') + ''' [Filter Card](filter-card.md) | A ready-made filter to use on many faces. |
| ''' + item('linker') + ''' [Linker](linker.md) | Picks your network and puts routers in it, one by one or by area. |
| ''' + item('configurator') + ''' [Configurator](configurator.md) | Copies a router's configuration and pastes it on others. |
| ''' + item('network_tablet') + ''' [Network Tablet](network-tablet.md) | See and manage all your networks from anywhere. |
| ''' + item('chunk_loader_upgrade') + ''' [Chunk Loading Upgrade](chunk-loading.md) | Keeps the router working while you're away. |
| ''' + item('storage_chest') + ''' [Wireless Chest](wireless-chest.md) | Stores millions of items by type; between two of them, a whole type moves at once. |
| ''' + item('storage_tank') + ''' [Wireless Tank](wireless-tank.md) | Many fluids in one tank, with billions of mB. |
| ''' + item('storage_battery') + ''' [Wireless Battery](wireless-battery.md) | Energy without an `int`'s cap. |
| ''' + item('storage_chemical_tank') + ''' [Wireless Chemical Tank](wireless-chemical-tank.md) | The tank for Mekanism chemicals. |
| ''' + item('storage_source_tank') + ''' [Wireless Source Tank](wireless-source-tank.md) | Ars Nouveau Source in large amounts. |
| ''' + item('signal_crystal') + ''' [Signal Quartz and crystals](signal-quartz.md) | The ore and the crystals that power the Resonator. |
| ''' + item('resonance_core') + ''' [Signal Resonator](signal-resonator.md) | A power generator: crystals, tuning and FE. |
| [Recipe editor](recipe-editor.md) | Operators tweak the mod's crafting recipes in-game. |

## Where to start

1. Read [Getting started](getting-started.md): two chests trading items in a minute.
2. Learn about [networks](networks.md) and [filters](filters.md).
3. Something not working? See [Troubleshooting](troubleshooting.md).

## This guide

You get this book the first time you join the world, and it's also in the mod's creative tab. To
make another: book + redstone, in a crafting table. With the mouse over one of the mod's items, in
your inventory or in JEI, hold **G** to open its page.

<Recipe id="wirelessautomate:guide" />

## All pages

<SubPages icons={true} />
''')

# =====================================================================================
# Primeiros passos
# =====================================================================================
page('getting-started.md', front('Primeiros passos', 'minecraft:chest', 1) + '''
# Primeiros passos

Vamos levar itens de um baú para outro, sem canos. Leva um minuto.

''' + fill(SCENE_TWO_CHESTS, LINE='Mesma rede: os itens vão de A para B sem fio nenhum',
           A='Roteador A · face de cima em **Extrai**', B='Roteador B · face de cima em **Insere**') + '''

| Passo | O que fazer |
| --- | --- |
| **1** | Faça dois roteadores e um [Vinculador](linker.md) (receitas abaixo). |
| **2** | Clique com um roteador em qualquer face do baú A. Ele fica **preso** ao baú, ainda **sem rede**. Faça o mesmo no baú B. |
| **3** | Com o Vinculador na mão, clique no roteador A e depois no B: as faces dos dois entram na sua rede ativa (o primeiro clique cria uma, se você não tiver). |
| **4** | Clique no roteador A. Na aba **Itens**, escolha a face **Cima** e o modo **Extrai**. |
| **5** | No roteador B, mesma face, modo **Insere**. |
| **6** | Coloque itens no baú A: eles aparecem no baú B. |

Depois de configurar o primeiro roteador de uma linha, use o [Configurador](configurator.md) para
copiar a configuração (e as redes) para os outros.

As telas do mod têm um botão com uma lua no canto de cima, à direita: ele troca todas para o modo escuro (e o sol volta ao claro).

## Receitas

<RecipeFor id="wirelessautomate:router" />

<RecipeFor id="wirelessautomate:linker" />

## Próximos passos

| Quero... | Leia |
| --- | --- |
| Entender cada parte da tela do roteador | [Roteador Wireless](router.md) |
| Mandar só alguns itens | [Filtros](filters.md) |
| Separar fábricas diferentes | [Redes](networks.md) |
| Mais vazão ou mais alcance | [Cartões de Upgrade](upgrade-cards.md) |
| Saber por que não funcionou | [Problemas comuns](troubleshooting.md) |
''', front('Getting started', 'minecraft:chest', 1) + '''
# Getting started

Let's move items from one chest to another, without pipes. It takes a minute.

''' + fill(SCENE_TWO_CHESTS, LINE='Same network: items go from A to B with no wire at all',
           A='Router A · top face set to **Extract**', B='Router B · top face set to **Insert**') + '''

| Step | What to do |
| --- | --- |
| **1** | Craft two routers and a [Linker](linker.md) (recipes below). |
| **2** | Use a router on any face of chest A. It **attaches** to the chest, with **no network** yet. Do the same on chest B. |
| **3** | Holding the Linker, click router A and then router B: the faces of both join your active network (the first click creates one if you have none). |
| **4** | Right-click router A. On the **Items** tab, pick the **Up** face and the **Extract** mode. |
| **5** | On router B, same face, **Insert** mode. |
| **6** | Put items in chest A: they show up in chest B. |

Once the first router of a line is set up, use the [Configurator](configurator.md) to copy its
configuration (and networks) to the others.

Every screen of the mod has a moon button in the top-right corner: it switches all of them to dark mode (the sun switches back).

## Recipes

<RecipeFor id="wirelessautomate:router" />

<RecipeFor id="wirelessautomate:linker" />

## Next steps

| I want to... | Read |
| --- | --- |
| Understand every part of the router screen | [Wireless Router](router.md) |
| Send only some items | [Filters](filters.md) |
| Keep different factories apart | [Networks](networks.md) |
| More throughput or more range | [Upgrade Cards](upgrade-cards.md) |
| Find out why it didn't work | [Troubleshooting](troubleshooting.md) |
''')

# =====================================================================================
# Roteador
# =====================================================================================
page('router.md', front('Roteador Wireless', 'wirelessautomate:router', 2, item_ids=['wirelessautomate:router']) + '''
# Roteador Wireless

<BlockImage id="wirelessautomate:router" p:facing="up" p:tier="basic" scale="3" float="left" />

O roteador se prende a uma face de qualquer bloco (a máquina) e dá à rede acesso a ela, como um
cano encostado faria, só que nas **seis faces de uma vez**: você escolhe na tela o que cada face da
máquina faz.

<br clear="all" />

## Ficha

| | |
| --- | --- |
| **Prende em** | Qualquer face de qualquer bloco (precisa de um bloco atrás). |
| **Abre com** | Clique direito no roteador, de mãos livres ou com um item comum. |
| **Gira com** | Shift + clique direito com a mão vazia: gira o roteador 90°. A configuração das faces não muda. |
| **Tipos** | Itens, Fluidos, Energia e, com o Mekanism, Químicos; com o Ars Nouveau, Source. |
| **Faces configuráveis** | As seis faces da máquina, cada uma com modo, prioridade, redstone, filtro e rede por tipo. |
| **Upgrades** | [Cartões de Upgrade](upgrade-cards.md) (Shift + clique no roteador) e o [Upgrade de Chunk Loading](chunk-loading.md) (no slot do topo da tela). |
| **Tier inicial** | Básico. Sobe com os [Cartões de Upgrade](upgrade-cards.md). |

## Receita

<RecipeFor id="wirelessautomate:router" />

## Onde prender

''' + fill(SCENE_SIDES, TOP='Preso em cima da fornalha', SIDE='Preso na lateral: funciona igual') + '''

Tanto faz em qual face ele fica: pela tela, ele acessa todas as seis. Quebrar o bloco de trás solta
o roteador.

## Configurar uma face

| Passo | Como |
| --- | --- |
| **1. Escolha o tipo** | Clique na aba: Itens, Fluidos, Energia, Químicos ou Source. |
| **2. Escolha a face** | Clique na face da máquina no modelo 3D (arraste para girar) ou no **cubo desdobrado** do painel: as duas escolhas andam juntas, e a face sob o mouse se acende nos dois. |
| **3. Escolha o modo** | Extrai, Insere, Armazém ou Nenhum (tabela abaixo). Escolher um modo numa face sem rede, com uma [rede](networks.md) ativa, põe a face nela; o aviso na base do painel traz o botão **Desfazer**. |
| **4. Ajustes (opcional)** | Prioridade e redstone ficam sempre à vista no painel. **Editar** abre o [filtro](filters.md) da face. |
| **5. Rede** | Na linha **Rede**, escolha a [rede](networks.md) da face (ou **Sem rede**). |

Arraste a borda direita, a de baixo ou o canto para aumentar a tela; o visor 3D cresce. Com pouco espaço, as abas mostram só o ícone (o nome aparece ao passar o mouse).

## O painel e o cubo

O painel à direita é o mesmo que o [Tablet de Rede](network-tablet.md) mostra para um nó. No alto,
o **cubo desdobrado** mostra as seis faces da máquina de uma vez: cada uma com a cor do modo e uma
bolinha na cor da rede. Uma face sem modo tem a borda tracejada, e uma com modo e sem rede ganha um
contorno coral e o convite **Escolha a rede desta face**. O olho marca a face onde o roteador está
preso.

Os nomes são os lados da **máquina**, vistos de frente para ela: Frente, Trás, Esquerda, Direita,
Cima e Baixo. Máquina sem frente, ou com a frente para cima ou para baixo, como um barril:
**Frente = Norte**.

| No painel | O que mostra |
| --- | --- |
| **Bolinha na face** | A cor da rede daquela face (sem bolinha: sem rede). |
| **Linha Rede** | A rede da face escolhida; clique para trocar. |
| **Resumo ao lado das abas** | Em quantas redes estão as faces da aba (**1 rede**, **2 redes**, **Sem rede**); passe o mouse para ver qual face está em qual. |
| **Tirar redes** | Com o Upgrade de Alcance, editando um grupo de máquinas ou uma máquina com exceção: tira a rede das faces com modo Nenhum, em todas as abas (as que movem algo ficam como estão). Fica na linha **Editando**; com a lista das máquinas à vista, é o ícone ⊘ na linha escolhida. O aviso traz **Desfazer**. |

## Máquinas à distância

Com o Upgrade de Alcance, o roteador atende várias máquinas ao redor, e a lista delas aparece ao lado do visor. Cada linha mostra o nome, as coordenadas e o estado da máquina.

| Na lista | O que faz |
| --- | --- |
| **Nome** | Clique no ✎ da linha (ou dê dois cliques no nome) para dar um nome à máquina ou ao grupo. **Enter** salva e **Esc** cancela. |
| **Olho ◉** | Acende a máquina no mundo, através dos blocos, com uma linha até o roteador. Ela fica acesa até você apagar ou sair do mundo, até 64 de uma vez. Clique de novo no olho para apagar. |
| **Apagar todas** | Apaga de uma vez todas as máquinas acesas. |

## Modos da face

| Modo | O que faz | Use para |
| --- | --- | --- |
| ''' + PORT['extract'] + ''' **Extrai** | Tira recursos da máquina por esta face. | Saídas: fornalha pronta, gerador, minerador. |
| ''' + PORT['insert'] + ''' **Insere** | Coloca recursos na máquina por esta face. | Entradas: fornalha, máquina de processar, baú final. |
| ''' + PORT['both'] + ''' **Armazém** | Recebe de quem extrai e entrega para quem insere, mas não troca com outro Armazém. | Baús de buffer e armazenamento. |
| ''' + PORT['none'] + ''' **Nenhum** | A face fica de fora. | Faces que não interessam. |

## Prioridade e redstone

| Ajuste | Valores | Efeito |
| --- | --- | --- |
| **Prioridade** | −999 a 999 (padrão 0) | Destinos com prioridade maior recebem primeiro; empates se revezam. Os botões − e + mudam de 1 em 1; com Shift, de 10 em 10. |
| **Redstone: Ignorar** | Padrão | A face funciona sempre. |
| **Redstone: Com sinal** | | A face só funciona com sinal de redstone no roteador. |
| **Redstone: Sem sinal** | | A face só funciona sem sinal. |

## Especificações por tier

<Row gap="12">
  <ItemImage id="wirelessautomate:router" scale="2" components="minecraft:block_state={tier:'basic'}" />
  <ItemImage id="wirelessautomate:router" scale="2" components="minecraft:block_state={tier:'advanced'}" />
  <ItemImage id="wirelessautomate:router" scale="2" components="minecraft:block_state={tier:'elite'}" />
  <ItemImage id="wirelessautomate:router" scale="2" components="minecraft:block_state={tier:'emerald'}" />
  <ItemImage id="wirelessautomate:router" scale="2" components="minecraft:block_state={tier:'ultimate'}" />
</Row>

A vazão vale **por face e por tipo**, contada em quem envia. Químicos usam o limite de fluido.
O servidor pode ter valores diferentes: o tooltip do cartão de upgrade mostra os do seu.

''' + TIER_TABLE['pt'] + '''

''' + fill(SCENE_TIERS, BASIC='Básico', ADVANCED='Avançado', ELITE='Elite', EMERALD='Esmeralda', ULTIMATE='Ultimate') + '''

No criativo, o clique do meio pega o roteador já no tier do bloco.
''', front('Wireless Router', 'wirelessautomate:router', 2, item_ids=['wirelessautomate:router']) + '''
# Wireless Router

<BlockImage id="wirelessautomate:router" p:facing="up" p:tier="basic" scale="3" float="left" />

The router attaches to a face of any block (the machine) and gives the network access to it, like
a pipe touching it would, but on **all six faces at once**: you choose on its screen what each
machine face does.

<br clear="all" />

## Spec sheet

| | |
| --- | --- |
| **Attaches to** | Any face of any block (it needs a block behind it). |
| **Opens with** | Right-click the router, with an empty hand or a regular item. |
| **Turns with** | Shift + right-click with an empty hand: turns the router 90°. The face settings don't change. |
| **Types** | Items, Fluids, Energy and, with Mekanism, Chemicals; with Ars Nouveau, Source. |
| **Configurable faces** | All six machine faces, each with mode, priority, redstone, filter and network per type. |
| **Upgrades** | [Upgrade Cards](upgrade-cards.md) (Shift + use on the router) and the [Chunk Loading Upgrade](chunk-loading.md) (in the slot at the top of the screen). |
| **Starting tier** | Basic. Raised with [Upgrade Cards](upgrade-cards.md). |

## Recipe

<RecipeFor id="wirelessautomate:router" />

## Where to attach it

''' + fill(SCENE_SIDES, TOP='Attached on top of the furnace', SIDE='Attached to the side: works the same') + '''

It doesn't matter which face it sits on: from its screen it reaches all six. Breaking the block
behind it drops the router.

## Setting up a face

| Step | How |
| --- | --- |
| **1. Pick the type** | Click the tab: Items, Fluids, Energy, Chemicals or Source. |
| **2. Pick the face** | Click the machine face on the 3D model (drag to rotate) or on the panel's **unfolded cube**: both choices move together, and the face under the mouse lights up in both. |
| **3. Pick the mode** | Extract, Insert, Storage or None (table below). Picking a mode on a face with no network, while you have an active [network](networks.md), puts the face on it; the notice at the bottom of the panel has an **Undo** button. |
| **4. Settings (optional)** | Priority and redstone are always in view on the panel. **Edit** opens the face's [filter](filters.md). |
| **5. Network** | On the **Network** row, pick the face's [network](networks.md) (or **No network**). |

Drag the right edge, the bottom edge or the corner to make the screen bigger; the 3D view grows. When space is short, the tabs show only their icon (hover for the name).

## The panel and the cube

The panel on the right is the same one the [Network Tablet](network-tablet.md) shows for a node. At
the top, the **unfolded cube** shows the machine's six faces at once: each in its mode's color with
a dot in its network's color. A face with no mode has a dashed border, and one with a mode but no
network gets a coral outline and the prompt **Pick this face's network**. The eye marks the face the
router is attached to.

The names are the **machine's** sides, as seen facing it: Front, Back, Left, Right, Top and Bottom.
A machine without a front, or facing up or down, like a barrel: **Front = North**.

| On the panel | What it shows |
| --- | --- |
| **Dot on a face** | The color of that face's network (no dot: no network). |
| **Network row** | The selected face's network; click to change it. |
| **Summary next to the tabs** | How many networks the tab's faces are in (**1 network**, **2 networks**, **No network**); hover to see which face is in which. |
| **Clear networks** | With the Range Upgrade, while editing a machine group or a machine with an exception: removes the network from the faces set to None, on every tab (faces that move something stay as they are). It sits on the **Editing** row; with the machine list in view, it's the ⊘ icon on the chosen row. The notice has an **Undo** button. |

## Remote machines

With the Range Upgrade, the router serves several machines around it, and their list sits next to the viewer. Each row shows the machine's name, its coordinates and its state.

| In the list | What it does |
| --- | --- |
| **Name** | Click the ✎ on the row (or double-click the name) to name the machine or the group. **Enter** saves and **Esc** cancels. |
| **Eye ◉** | Lights the machine up in the world, through the blocks, with a line to the router. It stays lit until you turn it off or leave the world, up to 64 at a time. Click the eye again to turn it off. |
| **Clear all** | Turns off every lit machine at once. |

## Face modes

| Mode | What it does | Use it for |
| --- | --- | --- |
| ''' + PORT['extract'] + ''' **Extract** | Takes resources out of the machine through this face. | Outputs: finished furnace, generator, miner. |
| ''' + PORT['insert'] + ''' **Insert** | Puts resources into the machine through this face. | Inputs: furnace, processing machine, final chest. |
| ''' + PORT['both'] + ''' **Storage** | Receives from extractors and delivers to inserters, but doesn't trade with another Storage. | Buffer and storage chests. |
| ''' + PORT['none'] + ''' **None** | The face is left out. | Faces you don't need. |

## Priority and redstone

| Setting | Values | Effect |
| --- | --- | --- |
| **Priority** | −999 to 999 (default 0) | Higher-priority destinations receive first; ties take turns. The − and + buttons step by 1; hold Shift to step by 10. |
| **Redstone: Ignore** | Default | The face always works. |
| **Redstone: With signal** | | The face only works with a redstone signal on the router. |
| **Redstone: No signal** | | The face only works without a signal. |

## Specs per tier

<Row gap="12">
  <ItemImage id="wirelessautomate:router" scale="2" components="minecraft:block_state={tier:'basic'}" />
  <ItemImage id="wirelessautomate:router" scale="2" components="minecraft:block_state={tier:'advanced'}" />
  <ItemImage id="wirelessautomate:router" scale="2" components="minecraft:block_state={tier:'elite'}" />
  <ItemImage id="wirelessautomate:router" scale="2" components="minecraft:block_state={tier:'emerald'}" />
  <ItemImage id="wirelessautomate:router" scale="2" components="minecraft:block_state={tier:'ultimate'}" />
</Row>

Throughput is **per face and per type**, counted at the sender. Chemicals use the fluid limit.
Your server may use different values: the upgrade card's tooltip shows yours.

''' + TIER_TABLE['en'] + '''

''' + fill(SCENE_TIERS, BASIC='Basic', ADVANCED='Advanced', ELITE='Elite', EMERALD='Emerald', ULTIMATE='Ultimate') + '''

In creative, middle-click picks the router in the block's tier.
''')

# =====================================================================================
# Cartões de upgrade
# =====================================================================================
page('upgrade-cards.md', front('Cartões de Upgrade', 'wirelessautomate:tier_core_advanced', 3, item_ids=[
    f'wirelessautomate:tier_core_{t[0]}' for t in TIERS[1:]]) + '''
# Cartões de Upgrade

<Row gap="8">
''' + CARD_IMAGES + '''
</Row>

Todo roteador nasce **Básico**. A escada é Básico → Avançado → Elite → Esmeralda → Ultimate. Com
o mod **Allthemodium**, entram três degraus entre a Esmeralda e o Ultimate: Allthemodium → Vibranium
→ Unobtainium.

Cada degrau tem um **anel**: oito itens em volta do centro da bancada. Subir um degrau custa o anel
daquele tier, por qualquer um dos três caminhos:

| Centro do anel | Resultado |
| --- | --- |
| **Redstone** | O cartão de um degrau, por exemplo **Avançado → Elite**. |
| **Um cartão** (por exemplo Básico → Avançado) | O cartão que pula: **Básico → Elite**. Junta os anéis dos degraus que ele pula. |
| **O roteador ou o armazenamento** do degrau anterior | O mesmo bloco, já no tier novo (a receita direta). |

O cartão tem **origem**: o nome diz de onde ele sobe ("Cartão de Upgrade: Básico → Elite"), e ele só
vale num bloco daquele tier. Cartões antigos, sem origem, continuam subindo de qualquer tier abaixo.

## O que cada cartão aumenta

Por face e por tipo. O tooltip do cartão mostra os valores do seu servidor. Químicos usam o
limite de fluido. Source só com o Ars Nouveau.

''' + card_table('pt') + '''

Cada passo multiplica a vazão por 8; só a energia do Unobtainium sobe 64 vezes sobre o Vibranium. Alcance e dimensões contam pelo tier de quem **envia**.

## Como usar

| Onde | Como |
| --- | --- |
| **No mundo** | Shift + clique com o cartão num roteador ou armazenamento já colocado, do tier da origem do cartão. Toda a configuração (faces, filtros, redes, cartões) continua. |
| **Na bancada, com o cartão** | O bloco + o cartão, em qualquer posição. |
| **Na bancada, sem cartão** | O bloco no centro do anel do tier seguinte. |

Na bancada, o bloco sobe como item: o roteador mantém o nome, e o armazenamento, o nome e o conteúdo.
Num bloco de outro tier, o cartão não é gasto e a barra acima do inventário avisa de qual tier ele sobe.

Se o mod Allthemodium entrar no pack depois, um cartão feito antes dele (por exemplo Esmeralda →
Ultimate) continua valendo e pula os degraus do Allthemodium.

Os mesmos cartões sobem os armazenamentos ([Baú](wireless-chest.md), [Tanque](wireless-tank.md),
[Bateria](wireless-battery.md), [Tanque Químico](wireless-chemical-tank.md) e
[Tanque de Source](wireless-source-tank.md)), do mesmo jeito e sem
perder o conteúdo. A capacidade de cada tier está na página de cada um.

## Receitas

<RecipeFor id="wirelessautomate:tier_core_advanced" />
<RecipeFor id="wirelessautomate:tier_core_elite" />
<RecipeFor id="wirelessautomate:tier_core_emerald" />
<RecipeFor id="wirelessautomate:tier_core_ultimate" />

As receitas mostram o anel com a Redstone no centro. Troque a Redstone por um cartão ou pelo bloco
do degrau anterior para os outros dois caminhos.

Com o Allthemodium, os anéis dele usam os materiais do próprio metal: lingotes e blocos de
Allthemodium; lingotes, blocos e liga Vibranium-Allthemodium; lingotes, blocos e liga
Unobtainium-Vibranium. O degrau antes do Ultimate passa a ser o Unobtainium e, no ATM10 (com o All
The Tweaks), o anel do Ultimate leva fragmentos de ATM Star, ovo do dragão e blocos de liga
Unobtainium-Allthemodium. O JEI mostra os três caminhos de cada anel do seu pack.
''', front('Upgrade Cards', 'wirelessautomate:tier_core_advanced', 3, item_ids=[
    f'wirelessautomate:tier_core_{t[0]}' for t in TIERS[1:]]) + '''
# Upgrade Cards

<Row gap="8">
''' + CARD_IMAGES + '''
</Row>

Every router starts as **Basic**. The ladder is Basic → Advanced → Elite → Emerald → Ultimate.
With the **Allthemodium** mod, three steps go between Emerald and Ultimate: Allthemodium →
Vibranium → Unobtainium.

Each step has a **ring**: eight items around the middle of the crafting grid. Going up one step costs
that tier's ring, by any of three paths:

| Middle of the ring | Result |
| --- | --- |
| **Redstone** | The one-step card, for example **Advanced → Elite**. |
| **A card** (for example Basic → Advanced) | The skipping card: **Basic → Elite**. It adds up the rings of the steps it skips. |
| **The router or storage** one step below | The same block, already at the new tier (the direct recipe). |

A card has an **origin**: its name says where it starts ("Upgrade Card: Basic → Elite"), and it only
works on a block of that tier. Old cards, with no origin, still raise any lower tier.

## What each card raises

Per face and per type. The card's tooltip shows your server's values. Chemicals use the fluid
limit. Source only with Ars Nouveau.

''' + card_table('en') + '''

Each step multiplies throughput by 8; only Unobtainium energy jumps 64 times over Vibranium. Range and dimensions follow the **sender's** tier.

## How to use

| Where | How |
| --- | --- |
| **In the world** | Shift + use the card on a placed router or storage of the card's origin tier. All of its configuration (faces, filters, networks, cards) is kept. |
| **In a crafting table, with the card** | The block + the card, in any slots. |
| **In a crafting table, no card** | The block in the middle of the next tier's ring. |

In a crafting table, the block goes up as an item: a router keeps its name, and a storage its name and
contents. On a block of another tier, the card isn't used up and the bar above the hotbar says which tier
it raises.

If the Allthemodium mod is added to the pack later, a card made before it (for example Emerald →
Ultimate) still works and skips the Allthemodium steps.

The same cards raise the storages ([Chest](wireless-chest.md), [Tank](wireless-tank.md),
[Battery](wireless-battery.md), [Chemical Tank](wireless-chemical-tank.md) and
[Source Tank](wireless-source-tank.md)), the same way and
keeping their contents. Each tier's capacity is on each one's page.

## Recipes

<RecipeFor id="wirelessautomate:tier_core_advanced" />
<RecipeFor id="wirelessautomate:tier_core_elite" />
<RecipeFor id="wirelessautomate:tier_core_emerald" />
<RecipeFor id="wirelessautomate:tier_core_ultimate" />

The recipes show the ring with Redstone in the middle. Swap the Redstone for a card or for the block one
step below for the other two paths.

With Allthemodium, its rings use the metal's own materials: Allthemodium ingots and blocks; Vibranium
ingots, blocks and Vibranium-Allthemodium alloy; Unobtainium ingots, blocks and Unobtainium-Vibranium
alloy. The step before Ultimate becomes Unobtainium and, in ATM10 (with All The Tweaks), the Ultimate
ring takes ATM Star shards, a dragon egg and Unobtainium-Allthemodium alloy blocks. JEI shows the three
paths of each ring in your pack.
''')

# =====================================================================================
# Redes
# =====================================================================================
page('networks.md', front('Redes', 'wirelessautomate:linker', 4) + '''
# Redes

Roteadores não se ligam uns aos outros: cada **face** de um roteador, em cada aba, entra numa
**rede** (ou em nenhuma), e tudo do mesmo tipo na mesma rede troca entre si. Quem envia e quem
recebe vem do modo das faces.

''' + fill(SCENE_STORAGE, L1='A fornalha (Extrai) entrega nos barris (Armazém)',
           L2='Os barris (Armazém) entregam no baú (Insere)', FURNACE='Fornalha · Extrai',
           BARREL='Barril · Armazém', CHEST='Baú · Insere') + '''

## Como funciona

| Regra | Explicação |
| --- | --- |
| **Sem rede ao colocar** | Um roteador novo não está em rede nenhuma: ponha as faces dele numa rede (abaixo) para ele trabalhar. |
| **Rede ativa** | Cada jogador tem uma rede ativa (a primeira leva o nome dele). É nela que o Vinculador põe os roteadores. |
| **Rede por face** | Numa fornalha, a face de **Cima** (entrada) pode estar na rede "Mina" e a de **Baixo** (saída) na rede "Armazém": o minério chega pela Mina e o lingote sai pelo Armazém. Cada aba tem as suas: a energia pode ir para a rede "Base". |
| **Ordem de entrega** | Prioridade maior primeiro; empates se revezam (round-robin). |
| **Armazém** | Recebe de quem extrai e entrega para quem insere, sem ficar trocando com outro Armazém. |
| **Dono** | Só o dono da rede (ou um operador) pode pôr roteadores nela. |

## Como mudar a rede

| Jeito | Quantos de uma vez |
| --- | --- |
| Linha **Rede** da face, na tela do roteador | Uma face de um roteador, numa aba |
| <ItemLink id="wirelessautomate:linker" />, modo Único | Um roteador: as faces e as abas marcadas |
| <ItemLink id="wirelessautomate:linker" />, modo Área | Todos os roteadores carregados de uma área (as faces e as abas marcadas) |
| <ItemLink id="wirelessautomate:linker" />, rede **Nenhuma (desvincular)** | Tira as faces e as abas marcadas da rede, num roteador ou numa área |
| <ItemLink id="wirelessautomate:network_tablet" />, Selecionar | Os nós que você marcar na lista: todas as faces deles no tipo escolhido (ou em todos) |
| <ItemLink id="wirelessautomate:configurator" /> | A rede de cada face vai junto com a configuração colada (todas as abas ou só um tipo) |

## Alcance e chunks

| Situação | O que acontece |
| --- | --- |
| Destino longe demais | Fica de fora daquela origem. O alcance depende do tier de quem envia ([Roteador](router.md)). |
| Outra dimensão | Só com origem **Esmeralda** ou acima. |
| Chunk descarregado | A rota pausa e volta sozinha quando o chunk carrega. |
| Quer manter trabalhando longe | Use o [Upgrade de Chunk Loading](chunk-loading.md). |
''', front('Networks', 'wirelessautomate:linker', 4) + '''
# Networks

Routers don't link to each other: each **face** of a router, on each tab, joins a **network** (or
none), and everything of the same type on the same network trades. Who sends and who receives comes
from the face modes.

''' + fill(SCENE_STORAGE, L1='The furnace (Extract) delivers to the barrels (Storage)',
           L2='The barrels (Storage) deliver to the chest (Insert)', FURNACE='Furnace · Extract',
           BARREL='Barrel · Storage', CHEST='Chest · Insert') + '''

## How it works

| Rule | Explanation |
| --- | --- |
| **No network when placed** | A new router isn't in any network: put its faces in one (below) so it starts working. |
| **Active network** | Every player has an active network (the first one is named after them). It's where the Linker puts routers. |
| **Network per face** | On a furnace, the **Top** face (input) can be on the "Mine" network and the **Bottom** face (output) on the "Storage" network: ore comes in through Mine and ingots leave through Storage. Each tab has its own: the energy can go to the "Base" network. |
| **Delivery order** | Higher priority first; ties take turns (round-robin). |
| **Storage** | Receives from extractors and delivers to inserters, without bouncing between two Storage faces. |
| **Owner** | Only the network's owner (or an operator) can put routers in it. |

## Changing the network

| Way | How many at once |
| --- | --- |
| The face's **Network** row, on the router screen | One face of one router, on one tab |
| <ItemLink id="wirelessautomate:linker" />, Single mode | One router: the checked faces and tabs |
| <ItemLink id="wirelessautomate:linker" />, Area mode | Every loaded router in an area (the checked faces and tabs) |
| <ItemLink id="wirelessautomate:linker" />, network **None (unlink)** | Takes the checked faces and tabs out of their network, on one router or an area |
| <ItemLink id="wirelessautomate:network_tablet" />, Select | The nodes you check in the list: all their faces of the chosen type (or of every type) |
| <ItemLink id="wirelessautomate:configurator" /> | Each face's network goes with the pasted configuration (every tab or one type) |

## Range and chunks

| Situation | What happens |
| --- | --- |
| Destination too far | Left out for that source. Range depends on the sender's tier ([Router](router.md)). |
| Another dimension | Only from an **Emerald** or higher source. |
| Unloaded chunk | The route pauses and resumes on its own when the chunk loads. |
| Keep it working far away | Use the [Chunk Loading Upgrade](chunk-loading.md). |
''')

# =====================================================================================
# Filtros
# =====================================================================================
page('filters.md', front('Filtros', 'minecraft:hopper', 5) + '''
# Filtros

Cada face da máquina, para cada tipo, tem um **filtro embutido** sem limite de entradas, mais dois
slots de [Cartão de Filtro](filter-card.md). Abra pelo botão **Editar**, na tela do roteador.

## Regra de ouro

| Situação | Resultado |
| --- | --- |
| Nenhum filtro com entradas | **Passa tudo.** |
| Filtro embutido ou algum cartão aceita | Passa. |
| Lista branca | Só passa o que casa com uma entrada. |
| Lista negra | Passa tudo, menos o que casa. |

Na origem, o filtro decide o que **sai**; no destino, o que **entra**.

## Tipos de entrada

| Regra | Exemplo | Itens | Fluidos | Químicos |
| --- | --- | --- | --- | --- |
| **Exato** | Lingote de ferro, água, hidrogênio | Sim | Sim | Sim |
| **Tag** | `#c:ingots`, `#c:ores` | Sim | Sim | Não |
| **Mod** | `@mekanism` | Sim | Sim | Sim |
| **Regra** | Qualquer item encantado | Sim | Não | Não |

## A tela

À esquerda ficam as entradas, uma por linha, com a busca em cima e o seu inventário embaixo. À
direita, quatro abas. A janela cresce pelas bordas e pela alça do canto, como a do Baú.

| Aba | Para quê |
| --- | --- |
| **Entrada** | A entrada selecionada: o que ela pega, o estoque, Remover e, num item, **Ver tags**. |
| **Tags** | Achar e marcar tags e mods (ver abaixo). Nos químicos, vira **Adicionar**: id ou `@mod`. |
| **Regra** | Montar uma regra por propriedade (só itens). |
| **Mais** | Cartão de Filtro, componentes e Limpar. |

## Como adicionar

| Jeito | Como |
| --- | --- |
| **Inventário** | Shift + clique no item (para fluidos e químicos, num balde ou tanque cheio). |
| **JEI** | Arraste da lista do JEI para a lista de entradas, ou Shift + clique nele. Não precisa ter o item. |
| **Tags de um item** | Na aba **Tags**, clique no slot do inspetor com o item no cursor (ele continua com você), ou Ctrl + clique no item do inventário. Marque as tags e clique em **Adicionar**. |
| **Buscando** | Na aba **Tags**, digite parte do nome (`ingots`) para ver todas as tags do jogo, ou `@` para mods. Passe o mouse numa linha para ver os itens dela. |

## Regras por propriedade

Uma regra pega o item pelo que ele **é**, não por qual item é. Cada condição tem **- / Sim / Não**,
e a regra pega o item que cumpre **todas** as marcadas. O inventário acende no que ela pega antes
de você adicionar.

| Condição | Exemplo |
| --- | --- |
| **Encantado** | Qualquer item com encantamento, inclusive livro encantado. |
| **Danificado** | Com algum desgaste. **Não** = intacto. |
| **Renomeado** | Com nome dado na bigorna. |
| **Com poção** | Poções, flechas com efeito. |
| **Empilhável** | **Não** = ferramentas, armaduras e o que fica sozinho no slot. |
| **Com conteúdo** | Caixa de shulker ou bundle com algo dentro. |
| **Encantamento** | Um encantamento com nível mínimo: Fortuna ≥ III. Digite o nome ou o id e escolha na lista; nível de 1 a 255. |
| **Durabilidade** | Restante ≥ ou < uma porcentagem: < 25% manda para o reparo. |
| **Só em** | Limita a uma tag ou mod: Encantado + `#c:armors` = só armadura encantada. |

Para editar uma regra, selecione-a na lista e clique em **Editar**.

## Opções

| Opção | O que faz |
| --- | --- |
| **Lista branca / negra** | Inverte o filtro. |
| **Componentes** (itens) | Ignorar: picareta encantada = picareta. Exigir: só iguais. Para pegar só os encantados, use uma regra. |
| **Estoque** | No destino, aceita só até N. Na origem, mantém sempre N. Selecione a entrada para definir. |

''', front('Filters', 'minecraft:hopper', 5) + '''
# Filters

Each machine face, for each type, has a **built-in filter** with no entry limit, plus two
[Filter Card](filter-card.md) slots. Open it with the **Edit** button on the router screen.

## Golden rule

| Situation | Result |
| --- | --- |
| No filter has entries | **Everything passes.** |
| The built-in filter or any card accepts | It passes. |
| Whitelist | Only what matches an entry passes. |
| Blacklist | Everything passes except what matches. |

At the source, the filter decides what **leaves**; at the destination, what **enters**.

## Entry types

| Rule | Example | Items | Fluids | Chemicals |
| --- | --- | --- | --- | --- |
| **Exact** | Iron ingot, water, hydrogen | Yes | Yes | Yes |
| **Tag** | `#c:ingots`, `#c:ores` | Yes | Yes | No |
| **Mod** | `@mekanism` | Yes | Yes | Yes |
| **Rule** | Any enchanted item | Yes | No | No |

## The screen

The entries are on the left, one per row, with the search on top and your inventory below. On the
right, four tabs. The window grows from its edges and the corner grip, like the Chest's.

| Tab | What for |
| --- | --- |
| **Entry** | The selected entry: what it matches, stock, Remove and, on an item, **See tags**. |
| **Tags** | Find and check tags and mods (see below). For chemicals it becomes **Add**: id or `@mod`. |
| **Rule** | Build a property rule (items only). |
| **More** | Filter Card, components and Clear. |

## How to add

| Way | How |
| --- | --- |
| **Inventory** | Shift + click the item (for fluids and chemicals, a full bucket or tank). |
| **JEI** | Drag from the JEI list onto the entry list, or Shift + click it. You don't need the item. |
| **An item's tags** | In the **Tags** tab, click the inspector slot with the item on the cursor (you keep it), or Ctrl + click the item in your inventory. Check the tags and click **Add**. |
| **Searching** | In the **Tags** tab, type part of the name (`ingots`) to see every tag in the game, or `@` for mods. Hover a row to see its items. |

## Property rules

A rule matches an item by what it **is**, not by which item it is. Each condition has
**- / Yes / No**, and the rule matches items that meet **all** the set ones. Your inventory lights up
on what it matches before you add it.

| Condition | Example |
| --- | --- |
| **Enchanted** | Any item with an enchantment, enchanted books included. |
| **Damaged** | Has some wear. **No** = undamaged. |
| **Renamed** | Named on an anvil. |
| **Has potion** | Potions, tipped arrows. |
| **Stackable** | **No** = tools, armor and anything that sits alone in a slot. |
| **Has contents** | A shulker box or bundle with something inside. |
| **Enchantment** | One enchantment with a minimum level: Fortune ≥ III. Type the name or the id and pick it from the list; level from 1 to 255. |
| **Durability** | Remaining ≥ or < a percentage: < 25% sends it to repair. |
| **Only in** | Limits it to a tag or mod: Enchanted + `#c:armors` = enchanted armor only. |

To edit a rule, select it in the list and click **Edit**.

## Options

| Option | What it does |
| --- | --- |
| **Whitelist / blacklist** | Inverts the filter. |
| **Components** (items) | Ignore: enchanted pickaxe = pickaxe. Require: only identical. To match only enchanted ones, use a rule. |
| **Stock** | At a destination, accept only up to N. At a source, always keep N. Select the entry to set it. |

''')

# =====================================================================================
# Cartão de filtro
# =====================================================================================
page('filter-card.md', front('Cartão de Filtro', 'wirelessautomate:filter_card', 6, item_ids=['wirelessautomate:filter_card']) + '''
# Cartão de Filtro

<ItemImage id="wirelessautomate:filter_card" scale="2" float="left" />

Um filtro completo num item, para reaproveitar em várias faces. Vale para itens ou para fluidos.

<br clear="all" />

## Ficha

| | |
| --- | --- |
| **Vai em** | Os dois slots de cartão da face, na tela do roteador (por tipo: itens ou fluidos). |
| **Receita** | Rende 2 cartões. |

## Comandos

| Gesto | O que faz |
| --- | --- |
| Clique direito no ar | Edita o filtro do cartão. |
| Shift + clique direito no ar (cartão vazio) | Alterna entre itens e fluidos. |
| Tela de filtro da face, na aba **Mais** | Exporta o filtro da face para o cartão, ou importa do cartão. |
| Bancada: cartão configurado + cartões vazios | Cópias iguais (o original volta). |

## Receita

<RecipeFor id="wirelessautomate:filter_card" />

Químicos usam só o filtro embutido da face: não há cartão de químicos.
''', front('Filter Card', 'wirelessautomate:filter_card', 6, item_ids=['wirelessautomate:filter_card']) + '''
# Filter Card

<ItemImage id="wirelessautomate:filter_card" scale="2" float="left" />

A full filter in an item, to reuse on many faces. Works for items or for fluids.

<br clear="all" />

## Spec sheet

| | |
| --- | --- |
| **Goes in** | The face's two card slots, on the router screen (per type: items or fluids). |
| **Recipe** | Makes 2 cards. |

## Actions

| Action | What it does |
| --- | --- |
| Right-click the air | Edit the card's filter. |
| Shift + right-click the air (empty card) | Switch between items and fluids. |
| Face filter screen, under **More** | Export the face's filter to the card, or import from it. |
| Crafting table: configured card + blank cards | Identical copies (the original comes back). |

## Recipe

<RecipeFor id="wirelessautomate:filter_card" />

Chemicals only use the face's built-in filter: there is no chemical card.
''')

# =====================================================================================
# Vinculador
# =====================================================================================
page('linker.md', front('Vinculador', 'wirelessautomate:linker', 7, item_ids=['wirelessautomate:linker']) + '''
# Vinculador

<ItemImage id="wirelessautomate:linker" scale="2" float="left" />

Escolhe a sua **rede ativa** e coloca roteadores nela, um a um ou uma área inteira de uma vez.
Também tira roteadores da rede.

<br clear="all" />

## Ficha

| | |
| --- | --- |
| **Modos** | Único, Área e Máquinas. No modo **Máquinas**, escolha um roteador com o Upgrade de Alcance e clique nas máquinas (um clique numa vinculada a desvincula); a mensagem diz a máquina e o grupo ou marque uma área. Uma máquina de um tipo novo entra sem rede; a face entra na sua rede ativa quando você escolhe um modo nela. Com **Nenhuma (desvincular)** na tela, o botão da área desvincula do roteador as máquinas dentro dela. |
| **Abas** | Itens, Fluidos, Energia e, com o Mekanism, Químicos; com o Ars Nouveau, Source. Marque quantas quiser. |
| **Faces** | As seis (padrão) ou só algumas: Frente, Trás, Esquerda, Direita, Cima e Baixo, lados da máquina de cada roteador. |
| **Rede** | Uma das suas redes, ou **Nenhuma (desvincular)**. |

## Comandos

| Gesto | O que faz |
| --- | --- |
| Clique num roteador | Põe as faces marcadas do roteador, nas abas marcadas, na rede ativa (cria uma, se você não tiver). Em **Nenhuma (desvincular)**, tira essas faces da rede. |
| Clique no ar | Abre a tela: rede, abas, faces, modo e, em Área, a prévia e o botão **Vincular** (ou **Desvincular**). |
| Shift + clique no ar | Passa para o próximo modo: **Único**, **Área**, **Máquinas** e de volta ao **Único**. |
| Shift + roda do mouse | Troca as abas pelos atalhos: **Todos**, Itens, Fluidos, Energia, Químicos (com o Mekanism), Source (com o Ars Nouveau). Uma combinação marcada na tela volta para **Todos**. |
| Shift + clique em dois blocos (Área) | Marca os cantos da área. |

## Na tela

| Parte | Como usar |
| --- | --- |
| **Rede** | Clique numa rede para torná-la a ativa, ou em **+ Nova rede** para criar uma. |
| **Nenhuma (desvincular)** | A primeira linha da lista. Com ela escolhida, os mesmos gestos tiram as faces e as abas marcadas da rede em vez de pôr. Escolha uma rede para voltar a vincular. |
| **Abas** | Um botão colorido por tipo: clique para marcar ou desmarcar (marcado = borda e fundo na cor do tipo). **Todos** marca todos os tipos; com todos marcados, deixa só o primeiro. Só as abas marcadas mudam; as outras ficam como estão. Pelo menos uma fica marcada. |
| **Faces** | **Todas** e uma letra por lado da máquina (F, T, E, D, C, B), vistos de frente para ela: clique para marcar ou desmarcar. Só as faces marcadas mudam. O botão mostra quais vão, como **Vincular 3 · T, B**. Máquina sem frente: Frente = Norte. Não aparece no modo Máquinas. |
| **Mais dicas ›** | No modo Único, quando as dicas não cabem na tela: passe o mouse para ver as que ficaram de fora. |

Arraste a borda ou o canto para aumentar a tela; o mapa da área cresce.

''' + fill(SCENE_AREA, BOX='Área marcada: todos os roteadores carregados dentro dela entram na rede',
           OTHER='Também entra: para o Vinculador, a máquina não importa', OTHER_COLOR='#3fc36b') + '''

A área pode ter até 262.144 blocos (por exemplo 64 × 64 × 64), e você precisa estar a até 64
blocos dela.

## Exemplo: entrada e saída em redes diferentes

| Passo | O que fazer |
| --- | --- |
| **1** | Na tela do roteador de cada fornalha, ponha a face **Cima** em **Insere** e a **Baixo** em **Extrai**. |
| **2** | No Vinculador, escolha a rede **Mina** e, em Faces, deixe só **C** (Cima) marcada. Clique nos roteadores das fornalhas. |
| **3** | Escolha a rede **Armazém**, marque só **B** (Baixo) e clique nos roteadores de novo. |
| **4** | O minério chega pela Mina e os lingotes saem pelo Armazém. Volte a **Todas** quando terminar. |

## Exemplo: tirar uma área da rede, menos a energia

| Passo | O que fazer |
| --- | --- |
| **1** | Clique no ar para abrir a tela e escolha **Nenhuma (desvincular)**. |
| **2** | Deixe marcadas **Itens**, **Fluidos** e **Químicos** e desmarque **Energia**. |
| **3** | Shift + clique no ar para o modo **Área** e marque os dois cantos com Shift + clique. |
| **4** | Abra a tela de novo e clique em **Desvincular**: as faces de itens, fluidos e químicos saem da rede e a energia continua ligada. |

## Receita

<RecipeFor id="wirelessautomate:linker" />
''', front('Linker', 'wirelessautomate:linker', 7, item_ids=['wirelessautomate:linker']) + '''
# Linker

<ItemImage id="wirelessautomate:linker" scale="2" float="left" />

Picks your **active network** and puts routers in it, one at a time or a whole area at once. It
also takes routers out of their network.

<br clear="all" />

## Spec sheet

| | |
| --- | --- |
| **Modes** | Single, Area and Machines. In **Machines** mode, pick a router with the Range Upgrade and click the machines (clicking a linked one unlinks it); the message names the machine and the group or mark an area. A machine of a new kind joins with no network; a face joins your active network when you pick a mode on it. With **None (unlink)** chosen on the screen, the area button unlinks the machines inside it from the router. |
| **Tabs** | Items, Fluids, Energy and, with Mekanism, Chemicals; with Ars Nouveau, Source. Check as many as you like. |
| **Faces** | All six (default) or just some: Front, Back, Left, Right, Top and Bottom, the sides of each router's machine. |
| **Network** | One of your networks, or **None (unlink)**. |

## Actions

| Action | What it does |
| --- | --- |
| Click a router | Puts the router's checked faces, on the checked tabs, in the active network (creates one if you have none). On **None (unlink)**, takes those faces out of their network. |
| Click the air | Opens the screen: network, tabs, faces, mode and, in Area, the preview and the **Link** (or **Unlink**) button. |
| Shift + click the air | Goes to the next mode: **Single**, **Area**, **Machines** and back to **Single**. |
| Shift + mouse wheel | Changes the tabs through the shortcuts: **All**, Items, Fluids, Energy, Chemicals (with Mekanism), Source (with Ars Nouveau). A combination checked on the screen goes back to **All**. |
| Shift + click two blocks (Area) | Marks the area corners. |

## On the screen

| Part | How to use it |
| --- | --- |
| **Network** | Click a network to make it active, or **+ New network** to create one. |
| **None (unlink)** | The first row of the list. While it's chosen, the same actions take the checked faces and tabs out of their network instead. Pick a network to go back to linking. |
| **Tabs** | One colored button per type: click to mark or unmark it (marked = border and tint in the type's color). **All** marks every type; when all are marked, it leaves only the first. Only the marked tabs change; the others stay as they are. At least one stays marked. |
| **Faces** | **All** and one letter per machine side (F, Bk, L, R, T, Bt), seen facing it: click to check or uncheck. Only the checked faces change. The button shows which ones go, like **Link 3 · Bk, Bt**. Machine without a front: Front = North. Hidden in Machines mode. |
| **More tips ›** | In Single mode, when the tips don't fit on the screen: hover it to see the ones left out. |

Drag the edge or the corner to make the screen bigger; the area map grows.

''' + fill(SCENE_AREA, BOX='Marked area: every loaded router inside joins the network',
           OTHER='Joins too: for the Linker, the machine does not matter', OTHER_COLOR='#3fc36b') + '''

The area can be up to 262,144 blocks (for example 64 × 64 × 64), and you need to be within 64
blocks of it.

## Example: input and output on different networks

| Step | What to do |
| --- | --- |
| **1** | On each furnace's router screen, set the **Top** face to **Insert** and the **Bottom** one to **Extract**. |
| **2** | In the Linker, pick the **Mine** network and, under Faces, keep only **T** (Top) checked. Click the furnaces' routers. |
| **3** | Pick the **Storage** network, check only **Bt** (Bottom) and click the routers again. |
| **4** | Ore comes in through Mine and ingots leave through Storage. Go back to **All** when you're done. |

## Example: unlink an area, keep the energy

| Step | What to do |
| --- | --- |
| **1** | Click the air to open the screen and pick **None (unlink)**. |
| **2** | Keep **Items**, **Fluids** and **Chemicals** checked and uncheck **Energy**. |
| **3** | Shift + click the air for **Area** mode and mark both corners with Shift + click. |
| **4** | Open the screen again and click **Unlink**: the items, fluids and chemicals faces leave their network and the energy stays connected. |

## Recipe

<RecipeFor id="wirelessautomate:linker" />
''')

# =====================================================================================
# Configurador
# =====================================================================================
page('configurator.md', front('Configurador', 'wirelessautomate:configurator', 8, item_ids=['wirelessautomate:configurator']) + '''
# Configurador

<ItemImage id="wirelessautomate:configurator" scale="2" float="left" />

Copia a configuração de um roteador e cola em outros, um a um ou numa área inteira.

<br clear="all" />

## Ficha

| | |
| --- | --- |
| **Copia** | Faces, filtros, prioridades, redstone e a rede de cada face. |
| **Guarda** | Uma cópia só, no próprio item. O tooltip mostra o que está copiado e os comandos do modo. |
| **Modos** | Pincel (padrão), Área (mesma máquina) e Área (qualquer máquina). |
| **Tipo colado** | Todos (padrão), Itens, Fluidos, Energia, Químicos (com o Mekanism) ou Source (com o Ars Nouveau). |

## Comandos

| Gesto | Pincel | Área |
| --- | --- | --- |
| Shift + clique num roteador | Copia | Copia |
| Clique num roteador | Cola nele | Marca um canto |
| Clique num bloco | — | Marca um canto (o 3º recomeça) |
| Clique no ar | — | Cola nos roteadores da área: só nos presos à **mesma máquina** da cópia, ou em **qualquer máquina** |
| Shift + clique num bloco sem roteador | Limpa a varinha | Limpa a varinha |
| Shift + clique no ar | Próximo modo | Próximo modo |
| Shift + roda do mouse | Troca o tipo colado | Troca o tipo colado |
| Segurar Alt esquerdo | Abre a roda | Abre a roda |

## A roda

Com o Configurador na mão, **segure Alt esquerdo** (a tecla muda em Controles, na categoria Wireless
Automate): abre uma roda no meio da tela, com o anel de dentro para o **modo** (Pincel, Área na mesma
máquina, Área em qualquer máquina) e o anel de fora para o **tipo colado**. A opção de agora fica com a
borda laranja e o centro mostra o que faz a fatia sob o mouse.

| | |
| --- | --- |
| **Soltar a tecla** | Escolhe a fatia sob o mouse e fecha. |
| **Clique** | Escolhe sem fechar: dá para trocar o modo e o tipo de uma vez. |
| **Soltar no centro ou Esc** | Fecha sem mudar nada. |

Copiar sempre copia tudo; o **tipo colado** escolhe o que vai para o roteador. Em **Todos**, todas as
abas. Num tipo só, apenas as faces (com as redes delas) daquela aba: as outras abas do roteador ficam como
estavam. Por exemplo, copie um roteador configurado só para fluidos, passe para **Fluidos** e cole
nos outros sem mexer nos itens nem na energia deles.

''' + fill(SCENE_AREA, BOX='Área marcada com o Configurador',
           OTHER='Fica como estava: está num baú, e a cópia veio de uma fornalha', OTHER_COLOR='#ff6b5e') + '''

## Bom saber

| | |
| --- | --- |
| **Mesma ou qualquer máquina** | Na mesma máquina, a área ignora os roteadores de outras máquinas (um baú no meio de uma linha de fornalhas fica como estava). Em qualquer máquina, cola em todos. |
| **Orientação** | A cópia é relativa ao roteador: funciona com ele preso em qualquer face. |
| **Redes** | A rede de uma face só é colada se você puder usá-la; senão, a face fica com a de antes. Uma face sem rede na cópia não mexe na rede do destino. A rede vale para as faces da máquina presa; as máquinas do Upgrade de Alcance ficam como estão. |
| **Replicar uma linha** | Copie o roteador de cada tipo de máquina e cole numa área que cubra a linha inteira. |

## Receita

<RecipeFor id="wirelessautomate:configurator" />
''', front('Configurator', 'wirelessautomate:configurator', 8, item_ids=['wirelessautomate:configurator']) + '''
# Configurator

<ItemImage id="wirelessautomate:configurator" scale="2" float="left" />

Copies a router's configuration and pastes it on others, one at a time or over a whole area.

<br clear="all" />

## Spec sheet

| | |
| --- | --- |
| **Copies** | Faces, filters, priorities, redstone and each face's network. |
| **Holds** | A single copy, in the item itself. The tooltip shows what's copied and the mode's actions. |
| **Modes** | Brush (default), Area (same machine) and Area (any machine). |
| **Pasted type** | All (default), Items, Fluids, Energy, Chemicals (with Mekanism) or Source (with Ars Nouveau). |

## Actions

| Action | Brush | Area |
| --- | --- | --- |
| Shift + click a router | Copy | Copy |
| Click a router | Paste on it | Mark a corner |
| Click a block | — | Mark a corner (the 3rd starts over) |
| Click the air | — | Paste into the area's routers: only those on the **same machine** as the copy, or on **any machine** |
| Shift + click a block without a router | Clear the wand | Clear the wand |
| Shift + click the air | Next mode | Next mode |
| Shift + mouse wheel | Change the pasted type | Change the pasted type |
| Hold Left Alt | Open the wheel | Open the wheel |

## The wheel

With the Configurator in hand, **hold Left Alt** (change the key in Controls, under Wireless
Automate): a wheel opens in the middle of the screen, with the inner ring for the **mode** (Brush, Area
on the same machine, Area on any machine) and the outer ring for the **pasted type**. The current
choice has an orange border and the center tells what the slice under the mouse does.

| | |
| --- | --- |
| **Release the key** | Picks the slice under the mouse and closes. |
| **Click** | Picks without closing: change the mode and the type in one go. |
| **Release in the center or Esc** | Closes without changing anything. |

Copying always copies everything; the **pasted type** picks what goes to the router. On **All**,
every tab. On a single type, only that tab's faces (with their networks): the router's other tabs stay as they
were. For example, copy a router set up only for fluids, switch to **Fluids** and paste on the others
without touching their items or energy.

''' + fill(SCENE_AREA, BOX='Area marked with the Configurator',
           OTHER='Left unchanged: it sits on a chest, and the copy came from a furnace', OTHER_COLOR='#ff6b5e') + '''

## Good to know

| | |
| --- | --- |
| **Same or any machine** | On the same machine, the area skips routers on other machines (a chest in the middle of a furnace line stays as it was). On any machine, it pastes on all of them. |
| **Orientation** | The copy is relative to the router: it works with the router on any face. |
| **Networks** | A face's network is only pasted if you can use it; otherwise the face keeps its own. A face with no network in the copy leaves the target's network alone. The network applies to the attached machine's faces; Range Upgrade machines stay as they are. |
| **Replicating a line** | Copy the router of each machine type and paste over an area covering the whole line. |

## Recipe

<RecipeFor id="wirelessautomate:configurator" />
''')

# =====================================================================================
# Tablet
# =====================================================================================
page('network-tablet.md', front('Tablet de Rede', 'wirelessautomate:network_tablet', 9, item_ids=['wirelessautomate:network_tablet']) + '''
# Tablet de Rede

<ItemImage id="wirelessautomate:network_tablet" scale="2" float="left" />

Uma visão de todas as suas redes, de qualquer lugar.

<br clear="all" />

## Ficha

| | |
| --- | --- |
| **Abre com** | Clique direito, ou a tecla **Abrir o Tablet de Rede** com o tablet no inventário (sem tecla padrão: escolha em Opções › Controles). |

## Abas

| Aba | O que mostra e faz |
| --- | --- |
| **Lista** | Todos os nós, com busca, filtros rápidos e o painel do nó escolhido (veja abaixo). O seletor **Roteadores · Máquinas**, embaixo da busca, mostra a lista pelos roteadores (uma linha para cada um, com a máquina presa embaixo do nome, abrindo em árvore com as máquinas dele) ou por todas as máquinas, agrupadas pelo bloco. **Selecionar** marca roteadores, grupos e máquinas para colar um perfil ou mudar de rede de uma vez. Um nó com faces em várias redes mostra "faces em N redes"; passe o mouse para ver quais. Para dar nome a um roteador ou à máquina presa, clique no ✎ da linha (com o mouse em cima) ou dê dois cliques no nome; Enter salva e Esc desiste. |
| **Fluxos** | Quanto entra e sai de cada tipo e de cada rede, o saldo dos Armazéns e o que precisa de atenção (veja abaixo). |
| **Mapa** | Vista de cima com cores por status. Clique num ponto para escolher o nó. O roteador escolhido mostra as máquinas vinculadas a ele, cada uma ligada a ele por uma linha (veja abaixo). |
| **Redes** | Criar redes, cor, membros e privacidade, e os grupos de redes, que pausam e retomam um sistema inteiro de uma vez (veja abaixo). |

O cabeçalho do Tablet conta a sua base: nós, máquinas e redes.

Se uma rede for apagada, as faces que estavam nela ficam sem rede e o nó mostra o aviso **faces sem rede: rede removida**: escolha outra rede para elas.

## Redes e grupos

A aba **Redes** é uma lista só. Cada grupo de redes é uma seção, com **▾** para recolher e **Pausar** no cabeçalho (**pausado** quando está); as redes fora de grupo vêm no fim, em **Sem grupo**. Uma rede em dois grupos aparece nas duas seções, e a dica diz **Também em …**.

- Clique numa rede para ver à direita a cor, se é pública ou privada, usá-la e removê-la.
- Clique no cabeçalho de um grupo para ver o grupo à direita: o nome, pausar, as redes dele (marque ou desmarque cada uma; **em …** avisa a rede que já está noutro grupo) e remover.
- **Nova rede** e **Novo grupo**, embaixo da lista, criam uma rede ou um grupo: digite o nome e confirme (ou Enter).

Um grupo pausado para todas as redes dele de uma vez; retomar as solta.

## Fluxos

Cada linha mostra **entra → sai**, a média dos últimos 5 segundos na unidade do tipo (itens/s, mB/s ou B/s, FE/t, Source/s):

- **entra** é o que as faces **Extrai** entregam: a produção da rede;
- **sai** é o que as faces **Insere** recebem: o consumo;
- à direita, o **saldo** dos Armazéns: **+N enchendo** quando eles recebem mais do que dão, **-N esvaziando** quando dão mais.

**Por tipo** mostra uma linha por tipo, somando as redes; a seta abre as redes daquele tipo, da que mais move para a que menos move. **Por rede** mostra cada rede com uma linha por tipo. Uma rede pausada num grupo aparece apagada e fica fora dos totais.

Ao lado, só o que precisa de atenção: **esperando** (faces Extrai com o que dar e sem destino), **cheios** (destinos sem espaço), **por redstone** (faces paradas pelo sinal) e **sem rede** (faces com modo e sem rede). Passe o mouse numa linha para ver a contagem completa. Uma rede que acabou de começar mostra a média dos segundos que já tem.

Clique numa linha para ver na Lista só os roteadores daquele tipo, daquela rede ou dos dois; o chip "Só …" e a etiqueta da rede na Lista tiram o filtro.

## Buscar na Lista

Escreva na caixa de busca: as sugestões se dividem em **Máquinas**, **Redes** e **Nomes**. Escolher uma máquina ou uma rede vira uma **etiqueta** (por exemplo "Máquina: Fornalha ✕"); o ✕ a tira. Você pode juntar várias etiquetas e um texto.

Ao lado da busca ficam os **filtros rápidos**, cada um com a contagem: **Com problema**, **Sem rede** e **Descarregados**. Um filtro com zero nós some. Embaixo, "N de M nós" e **Limpar** voltam à lista inteira.

Cada máquina é um grupo que você recolhe e abre pelo cabeçalho. A linha de cada nó mostra o estado em palavras (**descarregada**, **sem rede**, **destino cheio**, **pausada** ou a rede e a vazão) e seis quadradinhos, um por lado da máquina, na cor do modo da aba escolhida.

| Tecla | O que faz |
| --- | --- |
| **Digitar** | Procura. |
| **↑ ↓** | Anda pelas sugestões abertas ou pelas linhas da lista: os nós e, num roteador aberto, as máquinas. |
| **Enter** | Aceita a sugestão, procura, escolhe a máquina marcada pelas setas, ou abre a tela completa do nó escolhido. |
| **← →** | Com a busca vazia: na linha de um nó, → abre o roteador em árvore e ← o fecha (ou recolhe o grupo dele); num grupo de máquinas, recolhe e abre; numa máquina, ← sobe para o grupo; no "… mais N", → mostra todas. |
| **Esc** | Fecha as sugestões; de novo, limpa a busca. |
| **Backspace** | Com a busca vazia, tira a última etiqueta. |
| **Duplo clique num nó** | Abre a tela completa do roteador. |

## As máquinas de um roteador

Um roteador com máquinas vinculadas tem um **▸** na frente do nome, e a linha diz quantas máquinas ele atende. Clique no **▸** para abrir o roteador em árvore; o **▾** fecha. Embaixo dele aparecem:

| Linha | O que mostra |
| --- | --- |
| **Máquina presa** | A máquina onde o roteador está preso, com o bloco e o lado do roteador. |
| **Grupo** | O nome do grupo e quantas máquinas tem (**×N**), e quantas exceções, se houver. O **▸**/**▾** do grupo esconde e mostra as máquinas dele. |
| **Máquina** | O nome (ou **#N**), as coordenadas e o estado em palavras: **movendo**, **dormindo**, **dormindo: entrada cheia**, **descarregada**, **sem o Upgrade de Alcance** (o roteador sem o upgrade não move de nenhuma), **fora do raio**, **acima do limite**, **trocada** ou **sumiu** (o bloco foi quebrado ou não tem mais o que mover). À direita, a vazão (**↓** recebe, **↑** dá), a marca **exceção** e o Olho **◉**. |

Cada grupo mostra as 8 primeiras máquinas; **… mais N · Mostrar todas** mostra o resto, e **Mostrar menos** volta às 8.

Clique numa máquina para editá-la no painel: ela vira o alvo da linha **Editando**, como na lista da [tela do roteador](router.md). Clique na máquina presa ou num grupo para editar a presa ou o perfil do grupo. O Olho **◉** acende a máquina no mundo, como na tela do roteador.

O Tablet lembra, ao reabrir, os roteadores abertos, os grupos escondidos e os que mostram todas.

## A Lista por máquinas

No seletor embaixo da busca, **Máquinas** mostra todas as máquinas da base, as presas e as vinculadas, agrupadas pelo tipo de bloco; **Roteadores** volta à lista de sempre. Os filtros rápidos ficam à direita do seletor.

| Linha | O que mostra |
| --- | --- |
| **Bloco** | O **▸**/**▾**, o ícone, o nome e quantas máquinas (por exemplo "Fornalha · 23") e, apagado à direita, de quantos roteadores. Clique para abrir ou recolher o bloco. |
| **Máquina** | O nome (ou **#N**, o mesmo número da árvore e da tela do roteador) e, embaixo, **via** o roteador que a atende, ou **presa** na máquina onde o roteador está preso, com as coordenadas ou o estado em palavras. À direita, a vazão quando ela move, a marca **exceção** e o Olho **◉**. |

Cada bloco mostra as 8 primeiras máquinas; **… mais N · Mostrar todas** mostra o resto e **Mostrar menos** volta às 8. Com uma base grande, os blocos começam recolhidos: abra o que quiser ver. O pé da lista conta os blocos e as máquinas.

A busca acha a máquina pelo nome, pelo bloco e pelo nome do roteador. Os filtros por tipo e por rede valem por máquina: em **Só Fluidos**, uma fornalha não aparece, mesmo que o roteador dela também mova fluidos.

Clique numa máquina: o Tablet escolhe o roteador dela e põe a máquina como alvo do painel, para configurá-la ali; na presa, o alvo é a presa. As setas **↑ ↓** andam nas linhas, **← →** recolhem e abrem o bloco, e **Enter** escolhe. O Tablet lembra, ao reabrir, a visão e os blocos abertos.

## As máquinas no Mapa

No Mapa, o nó escolhido é o mesmo da Lista. Se ele tiver máquinas vinculadas, elas aparecem como quadradinhos ligados ao roteador por uma linha; as acesas pelo Olho ficam em ciano. Clique numa máquina para escolhê-la como alvo do painel do nó, o mesmo da Lista. Com a Lista por máquinas, o Mapa continua com todos os roteadores; o escolhido é o da máquina escolhida, com as máquinas dele.

## Clicar numa máquina

Com o Tablet na mão, clique direito numa máquina com roteador (ou numa vinculada pelo Upgrade de Alcance): o Tablet abre a central já com o nó dela escolhido, antes da interface da máquina. No painel, **No mundo** acende a máquina através dos blocos, como o Olho da tela do roteador, e o **Apagar todas (N)**, no pé da Lista, apaga as que estão acesas.

## O painel do nó

Clique num nó da lista: o painel à direita é o mesmo da [tela do roteador](router.md), com o cubo desdobrado das faces, modo, rede, filtro, prioridade e redstone, e cada mudança vale na hora. Pelo Tablet você edita um nó **dentro do alcance do tier do Tablet**; com o nó descarregado, ou fora do alcance, o painel mostra o último estado só para leitura e diz o motivo.

**Editar** o filtro abre a tela de filtro mesmo à distância (em outra dimensão também, se o tier permitir), e **Voltar** leva você de volta ao Tablet, no mesmo nó e na mesma busca. O Tablet lembra a aba e o nó ao reabrir.

## Copiar e colar um perfil

Com um nó escolhido, **Copiar perfil** guarda a configuração das faces dele no Tablet (a faixa **Perfil de …** mostra a origem; **esquecer ✕** a larga). Depois:

| Para | Como |
| --- | --- |
| **Um nó** | Escolha-o e clique em **Colar perfil**. |
| **Vários roteadores e máquinas** | Em **Selecionar**, marque o que quiser e clique em **Colar nos marcados**; o botão diz o que vai atingir ("2 roteadores, 3 máquinas") ou, sem espaço, só quantos, e a dica diz tudo. |

Os nós já iguais ao perfil dizem "igual ao perfil"; os outros mostram "N diferentes do perfil" e o primeiro ajuste que falta. O aviso depois de colar traz **Desfazer**. Atalhos: **Ctrl+C** copia o perfil do nó escolhido, **Ctrl+V** cola e **Ctrl+Z** desfaz. As redes valem pela regra de sempre: só entram as que você pode usar.

## Marcar roteadores e máquinas

Em **Selecionar**, toda linha tem uma caixa, nas duas visões: o roteador, a máquina presa, o grupo, a máquina e o cabeçalho do bloco. Marcar um grupo ou um bloco marca as máquinas dele; com só uma parte marcada, a caixa fica pela metade. **Todos** marca o que a busca mostra: os roteadores em **Roteadores**, os blocos em **Máquinas**.

**Colar nos marcados** cola o perfil copiado:

- no **roteador**, na máquina presa, como no colar de sempre;
- na **máquina**, como uma exceção dela, com os lados virados pela frente da máquina: cada face vai para o mesmo lado da máquina, mesmo que ela esteja virada para outra direção. Se ficar igual ao perfil do grupo, a exceção some;
- no **grupo inteiro** (o grupo marcado, o bloco marcado ou todas as máquinas do grupo uma a uma), no perfil do grupo; as exceções que já existem ficam, e o aviso diz quantas.

O roteador de onde o perfil veio fica de fora (as máquinas vinculadas a ele recebem); roteadores descarregados ou fora do alcance ficam de fora com as máquinas deles, e o aviso conta. Um perfil copiado numa versão anterior só cola em roteadores: copie-o de novo para colar em máquinas e grupos. O aviso ("Colado em 3 grupos e 1 máquina · 2 exceções mantidas") traz **Desfazer**, que volta o que ainda está como ficou.

**Mover** põe os marcados na rede escolhida: os roteadores inteiros, como sempre; numa máquina marcada, só as faces com modo dela (vira uma exceção); num grupo inteiro, o perfil do grupo. Uma máquina de um roteador também marcado já vai junto com ele.

## Receita

<RecipeFor id="wirelessautomate:network_tablet" />
''', front('Network Tablet', 'wirelessautomate:network_tablet', 9, item_ids=['wirelessautomate:network_tablet']) + '''
# Network Tablet

<ItemImage id="wirelessautomate:network_tablet" scale="2" float="left" />

An overview of all your networks, from anywhere.

<br clear="all" />

## Spec sheet

| | |
| --- | --- |
| **Opens with** | Right-click, or the **Open Network Tablet** key with the tablet in your inventory (unbound by default: set it in Options › Controls). |

## Tabs

| Tab | What it shows and does |
| --- | --- |
| **List** | Every node, with search, quick filters and the panel of the chosen node (see below). The **Routers · Machines** switch, below the search, shows the list by router (one row each, with the attached machine under the name, opening as a tree with its machines) or by every machine, grouped by block. **Select** marks routers, groups and machines to paste a profile or change networks at once. A node with faces on several networks shows "faces in N networks"; hover to see which. To name a router or its attached machine, click the ✎ on its row (with the mouse over it) or double-click the name; Enter saves and Esc cancels. |
| **Flows** | How much of each type comes in and goes out, per type and per network, the balance of Storage faces and what needs attention (see below). |
| **Map** | Top-down view colored by status. Click a point to choose the node. The chosen router shows the machines linked to it, each joined to it by a line (see below). |
| **Networks** | Create networks, color, members and privacy, and the network groups, which pause and resume a whole system at once (see below). |

The Tablet's header counts your base: nodes, machines and networks.

If a network is deleted, the faces that were on it are left without a network and the node shows the warning **faces without network: network removed**: pick another network for them.

## Networks and groups

The **Networks** tab is a single list. Each network group is a section, with **▾** to collapse it and **Pause** on its header (**paused** when it is); networks outside any group come last, under **No group**. A network in two groups shows up in both sections, and the tooltip says **Also in …**.

- Click a network to see its color and whether it is public or private on the right, use it or remove it.
- Click a group's header to see the group on the right: its name, pause, its networks (check or uncheck each one; **in …** flags a network that is already in another group) and remove.
- **New network** and **New group**, below the list, create a network or a group: type the name and confirm (or Enter).

A paused group stops all its networks at once; resuming lets them go.

## Flows

Each row shows **in → out**, the average of the last 5 seconds in the type's unit (items/s, mB/s or B/s, FE/t, Source/s):

- **in** is what the **Extract** faces deliver: the network's production;
- **out** is what the **Insert** faces receive: the consumption;
- on the right, the **balance** of Storage faces: **+N filling** when they receive more than they give, **-N draining** when they give more.

**By type** shows one row per type, adding up the networks; the arrow opens that type's networks, from the one that moves most to the one that moves least. **By network** shows each network with one row per type. A network paused in a group is greyed out and left out of the totals.

Next to it, only what needs attention: **waiting** (Extract faces with something to give and no destination), **full** (destinations with no room), **by redstone** (faces stopped by the signal) and **without network** (faces with a mode and no network). Hover a row for the full count. A network that has just started shows the average of the seconds it has so far.

Click a row to list only the routers of that type, that network or both; the "Only …" chip and the network label in the List remove the filter.

## Searching the List

Type in the search box: suggestions are split into **Machines**, **Networks** and **Names**. Picking a machine or a network turns it into a **label** (for example "Machine: Furnace ✕"); the ✕ removes it. You can combine several labels and some text.

Next to the search sit the **quick filters**, each with its count: **With problems**, **No network** and **Unloaded**. A filter with zero nodes disappears. At the bottom, "N of M nodes" and **Clear** bring back the whole list.

Each machine is a group you collapse and open from its header. Each node's row states its condition in words (**unloaded**, **no network**, **destination full**, **paused**, or the network and rate) and six small squares, one per machine side, in the color of the selected tab's mode.

| Key | What it does |
| --- | --- |
| **Type** | Searches. |
| **↑ ↓** | Moves through the open suggestions or the rows of the list: the nodes and, in an open router, its machines. |
| **Enter** | Accepts the suggestion, searches, chooses the machine marked by the arrows, or opens the chosen node's full screen. |
| **← →** | With an empty search: on a node's row, → opens the router as a tree and ← closes it (or collapses its group); on a machine group, collapses and opens it; on a machine, ← goes up to the group; on "… N more", → shows them all. |
| **Esc** | Closes the suggestions; again, clears the search. |
| **Backspace** | With an empty search, removes the last label. |
| **Double-click a node** | Opens the router's full screen. |

## A router's machines

A router with linked machines has a **▸** in front of its name, and its row says how many machines it serves. Click the **▸** to open the router as a tree; the **▾** closes it. Below it you see:

| Row | What it shows |
| --- | --- |
| **Attached machine** | The machine the router is attached to, with the block and the side of the router. |
| **Group** | The group's name and how many machines it has (**×N**), and how many exceptions, if any. The group's **▸**/**▾** hides and shows its machines. |
| **Machine** | The name (or **#N**), the coordinates and the state in words: **moving**, **asleep**, **asleep: input full**, **unloaded**, **no Range Upgrade** (without the upgrade the router moves from none of them), **out of range**, **over the limit**, **replaced** or **missing** (the block was broken or has nothing left to move). On the right, the rate (**↓** receives, **↑** gives), the **exception** mark and the Eye **◉**. |

Each group shows its first 8 machines; **… N more · Show all** shows the rest, and **Show fewer** goes back to 8.

Click a machine to edit it in the panel: it becomes the target of the **Editing** row, as in the list of the [router screen](router.md). Click the attached machine or a group to edit the attached machine or the group's profile. The Eye **◉** lights the machine up in the world, as on the router screen.

When you reopen it, the Tablet remembers the open routers, the hidden groups and the ones showing all.

## The List by machine

In the switch below the search, **Machines** shows every machine of your base, attached and linked, grouped by block type; **Routers** goes back to the usual list. The quick filters sit to the right of the switch.

| Row | What it shows |
| --- | --- |
| **Block** | The **▸**/**▾**, the icon, the name and how many machines (for example "Furnace · 23") and, dimmed on the right, from how many routers. Click it to open or collapse the block. |
| **Machine** | The name (or **#N**, the same number as in the tree and on the router screen) and, below, **via** the router that serves it, or **attached** on the machine the router is attached to, with the coordinates or the state in words. On the right, the rate when it moves, the **exception** mark and the Eye **◉**. |

Each block shows its first 8 machines; **… N more · Show all** shows the rest and **Show fewer** goes back to 8. With a large base, the blocks start collapsed: open the one you want to see. The foot of the list counts the blocks and machines.

The search finds a machine by its name, its block and its router's name. The type and network filters apply per machine: under **Only Fluids**, a furnace does not show up, even if its router also moves fluids.

Click a machine: the Tablet chooses its router and makes the machine the panel's target, to set it up there; on the attached machine, the target is the attached machine. **↑ ↓** walk the rows, **← →** collapse and open the block, and **Enter** chooses. When you reopen it, the Tablet remembers the view and the open blocks.

## Machines on the Map

On the Map, the chosen node is the same as in the List. If it has linked machines, they show up as small squares joined to the router by a line; the ones lit by the Eye are cyan. Click a machine to choose it as the target of the node's panel, the same one as in the List. With the List by machine, the Map still shows every router; the chosen one is the chosen machine's router, with its machines.

## Clicking a machine

With the Tablet in hand, right-click a machine with a router (or one linked through the Range Upgrade): the Tablet opens the hub with its node already chosen, before the machine's own interface. In the panel, **In the world** lights the machine up through the blocks, like the Eye on the router screen, and **Clear all (N)**, at the foot of the List, turns off the ones that are lit.

## The node panel

Click a node in the list: the panel on the right is the same as the [router screen](router.md), with the unfolded face cube, mode, network, filter, priority and redstone, and every change takes effect at once. From the Tablet you edit a node **within the Tablet tier's range**; with the node unloaded, or out of range, the panel shows its last state read-only and says why.

**Edit** on the filter opens the filter screen even from a distance (across dimensions too, if the tier allows it), and **Back** returns you to the Tablet, on the same node and the same search. The Tablet remembers its tab and node when you reopen it.

## Copying and pasting a profile

With a node chosen, **Copy profile** saves its face settings in the Tablet (the **Profile of …** strip shows the source; **forget ✕** drops it). Then:

| To | How |
| --- | --- |
| **One node** | Choose it and click **Paste profile**. |
| **Several routers and machines** | In **Select**, mark what you want and click **Paste on marked**; the button says what it will reach ("2 routers, 3 machines") or, without room, just how many, and the tooltip says it all. |

Nodes already equal to the profile say "same as the profile"; the others show "N different from the profile" and the first setting that is missing. The notice after pasting has an **Undo** button. Shortcuts: **Ctrl+C** copies the chosen node's profile, **Ctrl+V** pastes and **Ctrl+Z** undoes. Networks follow the usual rule: only the ones you can use are applied.

## Marking routers and machines

In **Select**, every row has a box, in both views: the router, the attached machine, the group, the machine and the block header. Marking a group or a block marks its machines; with only part of it marked, the box is half filled. **All** marks what the search shows: the routers under **Routers**, the blocks under **Machines**.

**Paste on marked** pastes the copied profile:

- on a **router**, on its attached machine, like the usual paste;
- on a **machine**, as an exception of its own, with the sides turned by the machine's front: each face goes to the same side of the machine, even if it faces another direction. If it ends up equal to the group's profile, the exception goes away;
- on a **whole group** (the marked group, the marked block or every machine of the group one by one), on the group's profile; the exceptions already there stay, and the notice says how many.

The router the profile came from is left out (the machines linked to it do receive); unloaded or out-of-range routers are left out with their machines, and the notice counts them. A profile copied in an earlier version only pastes on routers: copy it again to paste on machines and groups. The notice ("Pasted on 3 groups and 1 machine · 2 exceptions kept") has **Undo**, which brings back what is still as it was left.

**Move** puts the marked ones in the chosen network: whole routers, as always; on a marked machine, only its faces with a mode (it becomes an exception); on a whole group, the group's profile. A machine of a router that is also marked already goes along with it.

## Recipe

<RecipeFor id="wirelessautomate:network_tablet" />
''')

# =====================================================================================
# Chunk loading
# =====================================================================================
page('chunk-loading.md', front('Upgrade de Chunk Loading', 'wirelessautomate:chunk_loader_upgrade', 10, item_ids=['wirelessautomate:chunk_loader_upgrade']) + '''
# Upgrade de Chunk Loading

<ItemImage id="wirelessautomate:chunk_loader_upgrade" scale="2" float="left" />

Mantém o chunk do roteador (e da máquina) carregado, para ele continuar trabalhando com você longe.

<br clear="all" />

## Ficha

| | |
| --- | --- |
| **Vai em** | O slot do cabeçalho da tela do roteador. |
| **Limite** | Cada jogador mantém até 16 chunks; vários roteadores no mesmo chunk contam uma vez. |

## Estados

| Estado no cabeçalho | Significa |
| --- | --- |
| **Ativo: chunks carregados** | O roteador e a máquina continuam trabalhando longe dos jogadores. |
| **Inativo: limite de chunks do dono** | Quem pôs o upgrade já força o máximo de chunks permitido. |
| **Inativo: desligado no servidor** | O servidor desativou este upgrade. |

## Receita

<RecipeFor id="wirelessautomate:chunk_loader_upgrade" />
''', front('Chunk Loading Upgrade', 'wirelessautomate:chunk_loader_upgrade', 10, item_ids=['wirelessautomate:chunk_loader_upgrade']) + '''
# Chunk Loading Upgrade

<ItemImage id="wirelessautomate:chunk_loader_upgrade" scale="2" float="left" />

Keeps the router's chunk (and its machine) loaded, so it keeps working while you're away.

<br clear="all" />

## Spec sheet

| | |
| --- | --- |
| **Goes in** | The slot in the router screen's header. |
| **Limit** | Each player keeps up to 16 chunks; several routers in one chunk count once. |

## States

| State in the header | Meaning |
| --- | --- |
| **Active: chunks loaded** | The router and the machine keep working away from players. |
| **Inactive: owner's chunk limit** | Whoever placed the upgrade already forces as many chunks as allowed. |
| **Inactive: disabled on the server** | The server has turned this upgrade off. |

## Recipe

<RecipeFor id="wirelessautomate:chunk_loader_upgrade" />
''')

# =====================================================================================
# Baú Wireless
# =====================================================================================
SCENE_CHESTS = '''<GameScene zoom="4" interactive={true}>
  <Block id="wirelessautomate:storage_chest" x="0" y="0" z="0" p:tier="elite" />
  <Block id="wirelessautomate:router" x="0" y="1" z="0" p:facing="up" p:tier="ultimate" />
  <Block id="wirelessautomate:storage_chest" x="4" y="0" z="0" p:tier="ultimate" />
  <Block id="wirelessautomate:router" x="4" y="1" z="0" p:facing="up" p:tier="ultimate" />
  <LineAnnotation from="0.5 1.5 0.5" to="4.5 1.5 0.5" color="#a46cff" thickness="0.08">
    {LINE}
  </LineAnnotation>
  <BlockAnnotation x="0" y="0" z="0" color="#45d6cc">
    {A}
  </BlockAnnotation>
  <BlockAnnotation x="4" y="0" z="0" color="#a46cff">
    {B}
  </BlockAnnotation>
  <IsometricCamera yaw="200" pitch="30" />
</GameScene>'''

page('wireless-chest.md', front('Baú Wireless', 'wirelessautomate:storage_chest', 11,
                                item_ids=['wirelessautomate:storage_chest']) + '''
# Baú Wireless

<ItemImage id="wirelessautomate:storage_chest" scale="2" float="left" />

Um baú sem slots: guarda **milhões** de itens, por tipo, quantos tipos você quiser. O limite é só o
total de itens do tier. Preso a um roteador, ele é o parceiro ideal da rede: entre dois Baús
Wireless, um tipo inteiro passa **de uma vez**, sem o teto de uma pilha por vez dos baús comuns.

<br clear="all" />

''' + fill(SCENE_CHESTS, LINE='Um tipo inteiro por vez: milhões de itens num instante',
           A='Baú Elite · roteador com a face de cima em **Extrai**',
           B='Baú Ultimate · roteador com a face de cima em **Insere**') + '''

## Capacidade

Total de itens, todos os tipos somados. O tooltip do item mostra o valor do seu servidor.

''' + capacity_table('pt', 'chest', 'Itens') + '''

Sobe de tier com os mesmos [Cartões de Upgrade](upgrade-cards.md) do roteador, no mundo ou na
bancada, **sem perder o conteúdo**.

## A tela

Clique com a mão vazia no Baú. A lista mostra cada tipo com a quantidade; passe o mouse para ver o
número exato.

| Quero... | Como |
| --- | --- |
| Achar um item | Digite na busca. **@mod** procura pelo mod (ex.: **@mekanism**) e **#** pelas tags e pelo texto do tooltip (ex.: **#ingots**, **#not yet eaten**). A busca começa vazia cada vez que abre. |
| Mudar a ordem | O botão ao lado da busca alterna: quantidade, nome e mod. |
| Pegar uma pilha | Clique no item. Botão direito: meia pilha (com menos de uma pilha guardada, a metade do que tem). |
| Mandar direto para o inventário | **Shift** + clique no item. Segure **Shift** e arraste pela lista: uma pilha de cada item por onde passar. Enquanto o **Shift** estiver apertado, os itens não trocam de lugar. |
| Encher o inventário com um item | Com qualquer item no cursor, **Shift** + clique duas vezes no item. |
| Mover um item por vez | Rodinha sobre o item: para baixo tira um, para cima guarda um do inventário. Sobre um item do inventário: para baixo guarda um, para cima puxa um do baú. |
| Guardar o que está no cursor | Clique em qualquer lugar da lista. Botão direito: um só. |
| Guardar do inventário | **Shift** + clique no item do inventário. |

A barra embaixo da lista mostra quanto do tier já está ocupado.

## Filtro de entrada

O botão **Filtro**, no alto da tela, abre a mesma tela de [filtros](filters.md) do roteador. Ele
decide o que pode **entrar** no Baú, por qualquer caminho: roteador, funil, outro mod ou a tela.

| Na entrada do filtro | No Baú |
| --- | --- |
| Lista branca | Só entra o que está na lista. |
| Lista negra | Entra tudo, menos o que está na lista. |
| **Estoque** N | Guarda **até N** daquele item; o resto fica onde estava. |

A bolinha do botão acende na cor do tier quando há um filtro. Cartões de Filtro também servem:
importe e exporte pela tela do filtro.

## Quebrar e levar

Quebre o Baú: o item leva **todo o conteúdo e o filtro**, e o tooltip mostra o total e os tipos.
Coloque de novo e está tudo lá. Cheio, ele sempre vira item: no criativo, sem picareta ou numa
explosão, nada se perde.

## Com outros blocos

- **Funis, AE2, Refined Storage e outros mods** veem o Baú como um inventário comum, um slot por tipo.
- **Comparador**: o sinal sobe com a ocupação (no Ultimate, 1 com qualquer coisa dentro).
- Entre um Baú Wireless e um baú comum o roteador também é rápido, mas o baú comum continua
  andando uma pilha por vez.

## Receita

<RecipeFor id="wirelessautomate:storage_chest" />
''', front('Wireless Chest', 'wirelessautomate:storage_chest', 11,
           item_ids=['wirelessautomate:storage_chest']) + '''
# Wireless Chest

<ItemImage id="wirelessautomate:storage_chest" scale="2" float="left" />

A chest without slots: it stores **millions** of items, by type, as many types as you like. The
only limit is the tier's total item count. Attached to a router it's the network's best friend:
between two Wireless Chests, a whole type moves **at once**, with no one-stack-at-a-time cap like
regular chests.

<br clear="all" />

''' + fill(SCENE_CHESTS, LINE='A whole type at a time: millions of items in an instant',
           A='Elite chest · router with the top face on **Extract**',
           B='Ultimate chest · router with the top face on **Insert**') + '''

## Capacity

Total items, all types added up. The item's tooltip shows your server's value.

''' + capacity_table('en', 'chest', 'Items') + '''

Raise the tier with the same [Upgrade Cards](upgrade-cards.md) as the router, in the world or in a
crafting table, **keeping the contents**.

## The screen

Click the chest with an empty hand. The list shows each type with its amount; hover to see the
exact number.

| I want to... | How |
| --- | --- |
| Find an item | Type in the search box. **@mod** searches by mod (e.g. **@mekanism**) and **#** by tags and tooltip text (e.g. **#ingots**, **#not yet eaten**). The search starts empty every time you open it. |
| Change the order | The button next to the search box cycles: amount, name and mod. |
| Take a stack | Click the item. Right click: half a stack (with less than a stack stored, half of what is there). |
| Send it straight to the inventory | **Shift** + click the item. Hold **Shift** and drag across the list: one stack of each item you pass over. While **Shift** is held, items keep their places. |
| Fill the inventory with an item | With any item on the cursor, **Shift** + double-click the item. |
| Move one item at a time | Mouse wheel over the item: down takes one, up stores one from your inventory. Over an item in your inventory: down stores one, up pulls one from the chest. |
| Store what's on the cursor | Click anywhere on the list. Right click: just one. |
| Store from the inventory | **Shift** + click the item in your inventory. |

The bar under the list shows how much of the tier is used.

## Input filter

The **Filter** button, at the top of the screen, opens the same [filter](filters.md) screen as the
router. It decides what can **get in** the chest, by any path: router, hopper, another mod or the
screen.

| In the filter entry | In the chest |
| --- | --- |
| Whitelist | Only what's on the list gets in. |
| Blacklist | Everything gets in except what's on the list. |
| **Stock** N | Stores **up to N** of that item; the rest stays where it was. |

The button's dot lights up in the tier color when there's a filter. Filter Cards work too: import
and export them from the filter screen.

## Break and carry

Break the chest: the item takes **all the contents and the filter** with it, and the tooltip shows
the total and the types. Place it again and everything is there. A full chest always becomes an
item: in creative, without a pickaxe or in an explosion, nothing is lost.

## With other blocks

- **Hoppers, AE2, Refined Storage and other mods** see the chest as a regular inventory, one slot per type.
- **Comparator**: the signal rises with how full it is (on Ultimate, 1 with anything inside).
- Between a Wireless Chest and a regular chest the router is fast too, but the regular chest still
  moves one stack at a time.

## Recipe

<RecipeFor id="wirelessautomate:storage_chest" />
''')

# =====================================================================================
# Tanque, Bateria e Tanque Químico
# =====================================================================================
SCENE_STORAGE_PAIR = '''<GameScene zoom="4" interactive={true}>
  <Block id="wirelessautomate:{BLOCK}" x="0" y="0" z="0" p:tier="elite" />
  <Block id="wirelessautomate:router" x="0" y="1" z="0" p:facing="up" p:tier="ultimate" />
  <Block id="wirelessautomate:{BLOCK}" x="4" y="0" z="0" p:tier="ultimate" />
  <Block id="wirelessautomate:router" x="4" y="1" z="0" p:facing="up" p:tier="ultimate" />
  <LineAnnotation from="0.5 1.5 0.5" to="4.5 1.5 0.5" color="#a46cff" thickness="0.08">
    {LINE}
  </LineAnnotation>
  <IsometricCamera yaw="200" pitch="30" />
</GameScene>'''

COMMON_PT = '''
## Em comum com o Baú

- **Tiers:** sobe com os mesmos [Cartões de Upgrade](upgrade-cards.md), no mundo ou na bancada, sem perder o conteúdo.
- **Quebrar:** o item leva o conteúdo{FILTER_PT}, e o tooltip mostra o total. Cheio, ele sempre vira item (no criativo, sem picareta ou numa explosão).
- **Comparador:** o sinal sobe com a ocupação.
- **Outros mods** o veem como {WHAT_PT} comum.
- **Faces:** as seis são iguais. Ele não empurra nem puxa sozinho: quem move é o roteador, ou um cabo ou cano de outro mod. Uma máquina só encostada nele não recebe nada.
'''
COMMON_EN = '''
## Shared with the Chest

- **Tiers:** raised with the same [Upgrade Cards](upgrade-cards.md), in the world or in a crafting table, keeping the contents.
- **Breaking:** the item takes the contents{FILTER_EN}, and the tooltip shows the total. A full one always becomes an item (in creative, without a pickaxe or in an explosion).
- **Comparator:** the signal rises with how full it is.
- **Other mods** see it as a regular {WHAT_EN}.
- **Faces:** all six are the same. It doesn't push or pull by itself: the router moves things, or another mod's cable or pipe. A machine just placed against it gets nothing.
'''

page('wireless-tank.md', front('Tanque Wireless', 'wirelessautomate:storage_tank', 12,
                               item_ids=['wirelessautomate:storage_tank']) + '''
# Tanque Wireless

<ItemImage id="wirelessautomate:storage_tank" scale="2" float="left" />

Um tanque para **vários fluidos ao mesmo tempo**, quantos couberem na capacidade do tier. Cada
fluido aparece numa lista, como os itens do [Baú Wireless](wireless-chest.md). Preso a um roteador,
ele troca bilhões de mB de uma vez.

<br clear="all" />

''' + fill(SCENE_STORAGE_PAIR, BLOCK='storage_tank', LINE='Fluido de um Tanque para outro pela aba Fluidos') + '''

## Capacidade

Total em mB, todos os fluidos somados.

''' + capacity_table('pt', 'tank', 'mB') + '''

## Baldes e recipientes

| Quero... | Como |
| --- | --- |
| Esvaziar um balde | Clique no Tanque com o balde cheio na mão, ou Shift + clique nele no inventário da tela. |
| Encher um balde | Clique no Tanque com o balde vazio (pega o primeiro fluido), ou, na tela, clique no fluido com o balde no cursor. |
| Outros recipientes | Tanques e células de outros mods funcionam igual, pela tela. |

Na tela, com um recipiente no cursor: clique num fluido enche o recipiente com ele, ou o esvazia se
ele já estiver cheio; o botão direito esvazia um. A busca, a ordem e o redimensionar são os do Baú.

## Filtro de entrada

O botão **Filtro** da tela abre a [tela de filtros](filters.md) de fluidos: só entra o que ele
aceita, e o **estoque** de uma entrada vira "guardar até N mB".
''' + fill(COMMON_PT, FILTER_PT=' e o filtro', WHAT_PT='um tanque') + '''
## Receita

<RecipeFor id="wirelessautomate:storage_tank" />
''', front('Wireless Tank', 'wirelessautomate:storage_tank', 12,
           item_ids=['wirelessautomate:storage_tank']) + '''
# Wireless Tank

<ItemImage id="wirelessautomate:storage_tank" scale="2" float="left" />

A tank for **many fluids at once**, as many as fit in the tier's capacity. Each fluid shows up in a
list, like the items in the [Wireless Chest](wireless-chest.md). Attached to a router, it trades
billions of mB at once.

<br clear="all" />

''' + fill(SCENE_STORAGE_PAIR, BLOCK='storage_tank', LINE='Fluid from one Tank to another through the Fluids tab') + '''

## Capacity

Total in mB, all fluids added up.

''' + capacity_table('en', 'tank', 'mB') + '''

## Buckets and containers

| I want to... | How |
| --- | --- |
| Empty a bucket | Click the Tank with the full bucket in hand, or Shift + click it in the screen's inventory. |
| Fill a bucket | Click the Tank with an empty bucket (takes the first fluid), or, on the screen, click the fluid with the bucket on the cursor. |
| Other containers | Tanks and cells from other mods work the same way, through the screen. |

On the screen, with a container on the cursor: clicking a fluid fills the container with it, or
empties it if it's already full; right click empties one. Search, order and resizing are the
Chest's.

## Input filter

The screen's **Filter** button opens the fluid [filter screen](filters.md): only what it accepts
gets in, and an entry's **stock** means "store up to N mB".
''' + fill(COMMON_EN, FILTER_EN=' and the filter', WHAT_EN='tank') + '''
## Recipe

<RecipeFor id="wirelessautomate:storage_tank" />
''')

page('wireless-battery.md', front('Bateria Wireless', 'wirelessautomate:storage_battery', 13,
                                  item_ids=['wirelessautomate:storage_battery']) + '''
# Bateria Wireless

<ItemImage id="wirelessautomate:storage_battery" scale="2" float="left" />

Guarda energia (FE) muito além de um `int`: no Ultimate, sem limite. Preso a um roteador na aba
**Energia**, ela carrega e descarrega bilhões de FE por tick.

<br clear="all" />

''' + fill(SCENE_STORAGE_PAIR, BLOCK='storage_battery', LINE='Energia de uma Bateria para outra pela aba Energia') + '''

## Capacidade

''' + capacity_table('pt', 'battery', 'FE') + '''

## A tela

Clique na Bateria: a barra mostra a carga e a porcentagem, e embaixo a variação por tick
(**carregando**, **descarregando** ou **estável**). Passe o mouse na barra para o valor exato.
A Bateria não tem filtro: energia é uma só.
''' + fill(COMMON_PT, FILTER_PT='', WHAT_PT='uma bateria') + '''
## Receita

<RecipeFor id="wirelessautomate:storage_battery" />
''', front('Wireless Battery', 'wirelessautomate:storage_battery', 13,
           item_ids=['wirelessautomate:storage_battery']) + '''
# Wireless Battery

<ItemImage id="wirelessautomate:storage_battery" scale="2" float="left" />

Stores energy (FE) far beyond an `int`: on Ultimate, unlimited. Attached to a router on the
**Energy** tab, it charges and discharges billions of FE per tick.

<br clear="all" />

''' + fill(SCENE_STORAGE_PAIR, BLOCK='storage_battery', LINE='Energy from one Battery to another through the Energy tab') + '''

## Capacity

''' + capacity_table('en', 'battery', 'FE') + '''

## The screen

Click the Battery: the bar shows the charge and the percentage, and below it the change per tick
(**charging**, **draining** or **idle**). Hover the bar for the exact value. The Battery has no
filter: energy is just one thing.
''' + fill(COMMON_EN, FILTER_EN='', WHAT_EN='battery') + '''
## Recipe

<RecipeFor id="wirelessautomate:storage_battery" />
''')

page('wireless-chemical-tank.md', front('Tanque Químico Wireless', 'wirelessautomate:storage_chemical_tank', 14,
                                        item_ids=['wirelessautomate:storage_chemical_tank']) + '''
# Tanque Químico Wireless

<ItemImage id="wirelessautomate:storage_chemical_tank" scale="2" float="left" />

O [Tanque Wireless](wireless-tank.md) para os **químicos do Mekanism** (gases, líquidos de
infusão, pigmentos, slurries): vários ao mesmo tempo, na capacidade do tier. Só existe com o
Mekanism instalado. Preso a um roteador na aba **Químicos**, um tipo inteiro passa de uma vez.

<br clear="all" />

''' + fill(SCENE_STORAGE_PAIR, BLOCK='storage_chemical_tank', LINE='Químico de um Tanque para outro pela aba Químicos') + '''

## Capacidade

A mesma do [Tanque](wireless-tank.md), em mB, todos os químicos somados.

''' + capacity_table('pt', 'tank', 'mB') + '''

## Recipientes do Mekanism

Na tela, com um tanque ou cilindro de químico do Mekanism no cursor: clique num químico enche o
recipiente com ele, ou o esvazia se ele já estiver cheio; o botão direito esvazia um. Shift +
clique num recipiente do inventário esvazia ele no Tanque.

## Filtro de entrada

O botão **Filtro** abre a tela de filtros de químicos (por químico ou por mod); o **estoque** vira
"guardar até N mB".
''' + fill(COMMON_PT, FILTER_PT=' e o filtro', WHAT_PT='um tanque de químicos') + '''
## Receita

Um Tanque Wireless com frascos de vidro e ferro (só com o Mekanism).

<RecipeFor id="wirelessautomate:storage_chemical_tank" />
''', front('Wireless Chemical Tank', 'wirelessautomate:storage_chemical_tank', 14,
           item_ids=['wirelessautomate:storage_chemical_tank']) + '''
# Wireless Chemical Tank

<ItemImage id="wirelessautomate:storage_chemical_tank" scale="2" float="left" />

The [Wireless Tank](wireless-tank.md) for **Mekanism chemicals** (gases, infuse types, pigments,
slurries): many at once, within the tier's capacity. It only exists with Mekanism installed.
Attached to a router on the **Chemicals** tab, a whole type moves at once.

<br clear="all" />

''' + fill(SCENE_STORAGE_PAIR, BLOCK='storage_chemical_tank', LINE='Chemical from one Tank to another through the Chemicals tab') + '''

## Capacity

The same as the [Tank](wireless-tank.md), in mB, all chemicals added up.

''' + capacity_table('en', 'tank', 'mB') + '''

## Mekanism containers

On the screen, with a Mekanism chemical tank or canister on the cursor: clicking a chemical fills
the container with it, or empties it if it's already full; right click empties one. Shift + click
a container in the inventory empties it into the Tank.

## Input filter

The **Filter** button opens the chemical filter screen (by chemical or by mod); the **stock** means
"store up to N mB".
''' + fill(COMMON_EN, FILTER_EN=' and the filter', WHAT_EN='chemical tank') + '''
## Recipe

A Wireless Tank with glass bottles and iron (only with Mekanism).

<RecipeFor id="wirelessautomate:storage_chemical_tank" />
''')

# =====================================================================================
# Químicos
# =====================================================================================
page('chemicals.md', front('Químicos (Mekanism)', 'minecraft:glass_bottle', 15) + '''
# Químicos do Mekanism

Com o **Mekanism** instalado, o roteador ganha a aba **Químicos**: gases, líquidos de infusão,
pigmentos e slurries. Sem o Mekanism, a aba não aparece e o resto do mod funciona igual.

## O que funciona

| Recurso | Químicos |
| --- | --- |
| Modo, prioridade e redstone por face | Sim, como nas outras abas. |
| Rede por face | Sim. |
| Vinculador | Sim: marque a caixa **Químicos** na tela dele. |
| Vazão | O limite de fluido do tier (veja [Roteador](router.md)). |
| Filtro exato e por mod (`@mod`) | Sim, com estoque. |
| Filtro por tag | Não. |
| Cartão de Filtro | Não: use o filtro embutido da face. |

## Adicionar químicos ao filtro

| Jeito | Como |
| --- | --- |
| **Inventário** | Shift + clique num tanque que tenha o químico. |
| **JEI** | Arraste o químico da lista para a grade. |
| **Digitando** | Em **Mais**, o id: `mekanism:hydrogen`, `mekanism:oxygen`... |

## Importante: faces das máquinas do Mekanism

As máquinas e tanques do Mekanism vêm com as faces **desligadas** na configuração de lados do
próprio Mekanism. Ligue a face em que o roteador vai trabalhar com a ferramenta de configuração do
Mekanism, como faria para um tubo; senão o roteador não enxerga o químico, e o modo da face aparece
indisponível.
''', front('Chemicals (Mekanism)', 'minecraft:glass_bottle', 15) + '''
# Mekanism chemicals

With **Mekanism** installed, the router gets a **Chemicals** tab: gases, infuse types, pigments
and slurries. Without Mekanism the tab doesn't show and the rest of the mod works the same.

## What works

| Feature | Chemicals |
| --- | --- |
| Mode, priority and redstone per face | Yes, like the other tabs. |
| Network per face | Yes. |
| Linker | Yes: check the **Chemicals** box on its screen. |
| Throughput | The tier's fluid limit (see [Router](router.md)). |
| Exact and mod (`@mod`) filter | Yes, with stock. |
| Tag filter | No. |
| Filter Card | No: use the face's built-in filter. |

## Adding chemicals to the filter

| Way | How |
| --- | --- |
| **Inventory** | Shift + click a tank holding the chemical. |
| **JEI** | Drag the chemical from the list onto the grid. |
| **Typing** | Under **More**, the id: `mekanism:hydrogen`, `mekanism:oxygen`... |

## Important: Mekanism machine faces

Mekanism machines and tanks come with their faces **disabled** in Mekanism's own side
configuration. Enable the face the router will use with Mekanism's configurator tool, just like
you would for a tube; otherwise the router can't see the chemical and the face mode shows as
unavailable.
''')

# =====================================================================================
# Source (Ars Nouveau)
# =====================================================================================
page('source.md', front('Source (Ars Nouveau)', 'minecraft:amethyst_shard', 16) + '''
# Source do Ars Nouveau

Com o **Ars Nouveau** instalado, o roteador ganha a aba **Source**. Sem o Ars, a aba não aparece e
o resto do mod funciona igual.

## O que funciona

| Recurso | Source |
| --- | --- |
| Modo, prioridade e redstone por face | Sim, como nas outras abas. |
| Rede por face | Sim. |
| Vinculador e Configurador | Sim: o chip **Source** e o atalho na roda do mouse. |
| Vazão | Por tier: Básico 100/s, Avançado 800/s, Elite 6.400/s, Esmeralda 51.200/s, Ultimate sem limite (tabela completa no [Roteador](router.md)). |
| Filtro e Cartão de Filtro | Não: a Source não tem tipos, como a energia. |

## Exemplo: Source dos Sourcelinks até o Enchanting Apparatus

| Passo | O que fazer |
| --- | --- |
| **1** | Perto dos Sourcelinks, uma **Source Jar** com um roteador em cima, na aba **Source**, em **Extrair**. |
| **2** | Perto do Enchanting Apparatus (ou de qualquer máquina do Ars), outra Source Jar com um roteador em **Inserir**, na mesma rede. |
| **3** | Os Sourcelinks enchem a primeira jarra; o roteador leva a Source para a segunda, e a máquina tira dela. |

O roteador também liga direto nos Relays do Ars e na Imbuement Chamber.

No lugar das Source Jars, um [Tanque de Source Wireless](wireless-source-tank.md) guarda muito mais e as máquinas do Ars tiram dele do mesmo jeito.
''', front('Source (Ars Nouveau)', 'minecraft:amethyst_shard', 16) + '''
# Ars Nouveau Source

With **Ars Nouveau** installed, the router gets a **Source** tab. Without Ars, the tab doesn't show
and the rest of the mod works the same.

## What works

| Feature | Source |
| --- | --- |
| Mode, priority and redstone per face | Yes, like the other tabs. |
| Network per face | Yes. |
| Linker and Configurator | Yes: the **Source** chip and the mouse wheel shortcut. |
| Throughput | Per tier: Basic 100/s, Advanced 800/s, Elite 6,400/s, Emerald 51,200/s, Ultimate unlimited (full table on the [Router](router.md) page). |
| Filter and Filter Card | No: Source has no types, like energy. |

## Example: Source from the Sourcelinks to the Enchanting Apparatus

| Step | What to do |
| --- | --- |
| **1** | Near the Sourcelinks, a **Source Jar** with a router on it, on the **Source** tab, set to **Extract**. |
| **2** | Near the Enchanting Apparatus (or any Ars machine), another Source Jar with a router set to **Insert**, on the same network. |
| **3** | The Sourcelinks fill the first jar; the router carries the Source to the second, and the machine draws from it. |

The router also connects straight to Ars Relays and the Imbuement Chamber.

Instead of Source Jars, a [Wireless Source Tank](wireless-source-tank.md) holds far more and Ars machines draw from it the same way.
''')

# =====================================================================================
# Tanque de Source
# =====================================================================================
page('wireless-source-tank.md', front('Tanque de Source Wireless', 'wirelessautomate:storage_source_tank', 17,
                                      item_ids=['wirelessautomate:storage_source_tank']) + '''
# Tanque de Source Wireless

<ItemImage id="wirelessautomate:storage_source_tank" scale="2" float="left" />

Guarda **Source** do Ars Nouveau em grande quantidade, muito além de uma Source Jar. Só existe com o
Ars Nouveau instalado. Preso a um roteador na aba **Source**, ele recebe e entrega Source como os
outros armazenamentos.

<br clear="all" />

''' + fill(SCENE_STORAGE_PAIR, BLOCK='storage_source_tank', LINE='Source de um Tanque para outro pela aba Source') + '''

## Para o Ars

- Os **Sourcelinks** num raio de 5 blocos depositam nele.
- As máquinas do Ars por perto (Enchanting Apparatus, Imbuement Chamber, rituais, Spell Turrets e Relays de depósito) tiram dele, como de uma Source Jar.

## Com o roteador

Na aba **Source** ele funciona como qualquer outra face. Entre dois tanques, tudo passa de uma
vez.

## A tela

Clique no Tanque: a barra roxa mostra quanto ele tem, a porcentagem e a vazão em Source por
segundo. A coluna de vidro do bloco também mostra o nível, de vazio a cheio. O Tanque não tem
filtro: Source é uma só.

## Capacidade

''' + capacity_table('pt', 'source_tank', 'Source') + '''

## Em comum com o Baú

- **Tiers:** sobe com os mesmos [Cartões de Upgrade](upgrade-cards.md), no mundo ou na bancada, sem perder o conteúdo.
- **Quebrar:** o item leva o conteúdo, e o tooltip mostra o total. Cheio, ele sempre vira item (no criativo, sem picareta ou numa explosão).
- **Comparador:** o sinal sobe com a ocupação.
- **Outros blocos do Ars** que usam a Source, como os Relays, ligam nele direto.
- **Faces:** as seis são iguais. Além do roteador, os Sourcelinks e as máquinas do Ars por perto o usam pelo alcance, sem precisar encostar nele.

## Receita

Um Tanque Wireless com gemas de Source e ferro (só com o Ars Nouveau).

<RecipeFor id="wirelessautomate:storage_source_tank" />
''', front('Wireless Source Tank', 'wirelessautomate:storage_source_tank', 17,
           item_ids=['wirelessautomate:storage_source_tank']) + '''
# Wireless Source Tank

<ItemImage id="wirelessautomate:storage_source_tank" scale="2" float="left" />

Stores Ars Nouveau **Source** in large amounts, far beyond a Source Jar. It only exists with Ars
Nouveau installed. Attached to a router on the **Source** tab, it takes and gives Source like the
other storages.

<br clear="all" />

''' + fill(SCENE_STORAGE_PAIR, BLOCK='storage_source_tank', LINE='Source from one Tank to another through the Source tab') + '''

## For Ars

- **Sourcelinks** within 5 blocks deposit into it.
- Ars machines nearby (Enchanting Apparatus, Imbuement Chamber, rituals, Spell Turrets and deposit Relays) draw from it, as from a Source Jar.

## With the router

On the **Source** tab it works like any other face. Between two tanks, everything moves at once.

## The screen

Click the Tank: the purple bar shows how much it holds, the percentage and the rate in Source per
second. The block's glass column also shows the level, from empty to full. The Tank has no
filter: Source is one thing.

## Capacity

''' + capacity_table('en', 'source_tank', 'Source') + '''

## Shared with the Chest

- **Tiers:** raised with the same [Upgrade Cards](upgrade-cards.md), in the world or in a crafting table, keeping the contents.
- **Breaking:** the item takes the contents, and the tooltip shows the total. A full one always becomes an item (in creative, without a pickaxe or in an explosion).
- **Comparator:** the signal rises with how full it is.
- **Other Ars blocks** that use Source, like Relays, connect to it directly.
- **Faces:** all six are the same. Besides the router, nearby Sourcelinks and Ars machines use it by range, without touching it.

## Recipe

A Wireless Tank with Source gems and iron (only with Ars Nouveau).

<RecipeFor id="wirelessautomate:storage_source_tank" />
''')


# =====================================================================================
# Quartzo de Sinal e Ressonador de Sinal
# =====================================================================================
def _scene_blocks():
    with open(os.path.join(ROOT, 'scripts/guide/resonator_scene.txt'), encoding='utf-8') as fh:
        return '\n'.join('  ' + line.strip() for line in fh if line.strip())


SCENE_RESONATOR = '''<GameScene zoom="3" interactive={true}>
''' + _scene_blocks() + '''
  <BlockAnnotation x="2" y="2" z="4" color="#f0603e">
    {CORE}
  </BlockAnnotation>
  <BlockAnnotation x="2" y="2" z="0" color="#45d6cc">
    {ENERGY}
  </BlockAnnotation>
  <BlockAnnotation x="4" y="2" z="2" color="#e2b347">
    {CRYSTALS}
  </BlockAnnotation>
  <BlockAnnotation x="2" y="4" z="2" color="#a06bff">
    {TUNER}
  </BlockAnnotation>
  <IsometricCamera yaw="{YAW}" pitch="30" />
</GameScene>'''

# Tiers do Ressonador na ordem do RouterTier: câmaras (tier + 1), FE/t por Cristal de Sinal (padrões do CoreMath) e
# teto do Sintonizador em % (constantes do Tuning.ceiling). O Cristal Lapidado rende o dobro.
RESONATOR_FE = [128, 1_024, 8_192, 32_768, 262_144, 2_097_152, 134_217_728, 1_073_741_824]
RESONATOR_CEILING = [85, 88, 90, 92, 94, 95, 96, 98]


def resonator_table(lang):
    head = {'pt': '| Tier do Núcleo | Câmaras | FE/t por Cristal de Sinal | Teto do Sintonizador |',
            'en': '| Core tier | Chambers | FE/t per Signal Crystal | Tuner ceiling |'}[lang]
    rows = [head, '| --- | --- | --- | --- |']
    for i in range(len(TIERS)):
        rows.append(f'| **{tier_name(lang, i)}** | {i + 1} | {num(lang, RESONATOR_FE[i])} | {RESONATOR_CEILING[i]}% |')
    return '\n'.join(rows) + '\n\n' + ATM_NOTE[lang]


RESONATOR_ANNOTATIONS = {
    'pt': dict(CORE='Núcleo de Ressonância: a tela e os cristais',
               ENERGY='Porta de Energia: o roteador tira a energia daqui',
               CRYSTALS='Porta de Cristais: entram cristais e redstone, saem Opacos', TUNER='Sintonizador (opcional)',
               YAW='35'),
    'en': dict(CORE='Resonance Core: the screen and the crystals',
               ENERGY='Resonator Energy Port: a router pulls the energy here',
               CRYSTALS='Resonator Crystal Port: crystals and redstone in, Dull ones out',
               TUNER='Resonator Tuner (optional)', YAW='35'),
}

page('signal-quartz.md', front('Quartzo de Sinal e cristais', 'wirelessautomate:signal_crystal', 18, item_ids=[
    'wirelessautomate:signal_quartz_ore', 'wirelessautomate:deepslate_signal_quartz_ore',
    'wirelessautomate:signal_crystal', 'wirelessautomate:cut_signal_crystal']) + '''
# Quartzo de Sinal e cristais

<Row gap="8">
  ''' + item('signal_quartz_ore') + '''
  ''' + item('deepslate_signal_quartz_ore') + '''
  ''' + item('signal_crystal') + '''
  ''' + item('cut_signal_crystal') + '''
</Row>

Os cristais de quartzo são o combustível do [Ressonador de Sinal](signal-resonator.md), o gerador de energia do mod.

## O minério

O **Minério de Quartzo de Sinal** aparece no Overworld, em pedra e em ardósia (a versão de ardósia
é mais dura). Fica em veios pequenos, mais comum perto de **y = −16**. Quebre com **picareta de ferro** ou melhor:

| Ferramenta | O que cai |
| --- | --- |
| Picareta de ferro ou melhor | 1 a 3 **Cristais de Sinal** e um pouco de experiência. **Fortuna** aumenta a quantidade. |
| Toque de Seda | O próprio minério. |

## Os cristais

| Cristal | Rende | Dura gerando |
| --- | --- | --- |
| ''' + item('signal_crystal') + ''' **Cristal de Sinal** | 1× | 10 minutos |
| ''' + item('cut_signal_crystal') + ''' **Cristal Lapidado** | 2× | 20 minutos |

A duração da tabela vale no Núcleo Básico e no Avançado. Quanto maior o tier do Núcleo, menos a carga dura: no
Ultimate, o que duraria 20 minutos dura 3.

O Cristal de Sinal só vem do minério. O **Cristal Lapidado** sai da bancada com quatro Cristais de Sinal em
cruz em volta de um Fragmento de Ametista, e só aceita cristais **cheios**: cristais gastos não valem.

<RecipeFor id="wirelessautomate:cut_signal_crystal" />

O Cristal de Sinal também entra nas receitas da Carcaça de Porcelana e do Vidro de Ressonância, e o Lapidado
na do Núcleo de Ressonância.

## Carga e cristal Opaco

A **barra do item** mostra quanto falta para o cristal acabar: ela só aparece depois que o cristal começa a
gastar. Dentro do Núcleo, a carga cai enquanto ele gera energia.

Sem carga, o cristal fica **Opaco**: é o mesmo item, só que apagado. Um Opaco não gera nada, mas dá para
**recarregar**:

1. Deixe o Opaco numa câmara do Núcleo.
2. Ponha **Pó de Redstone** no Núcleo (clique com a redstone na mão, pela tela ou pela Porta de Cristais).
3. O Núcleo recarrega **um Opaco por vez**: leva 30 segundos e gasta 1 pó de redstone, e o cristal volta cheio.

A Porta de Cristais só devolve os Opacos quando o Núcleo está sem redstone; com redstone, o Opaco fica
lá dentro esperando a vez de recarregar.

Os números são os padrões; o JEI mostra os do seu pack.

Veja como montar o gerador em [Ressonador de Sinal](signal-resonator.md).
''', front('Signal Quartz and crystals', 'wirelessautomate:signal_crystal', 18, item_ids=[
    'wirelessautomate:signal_quartz_ore', 'wirelessautomate:deepslate_signal_quartz_ore',
    'wirelessautomate:signal_crystal', 'wirelessautomate:cut_signal_crystal']) + '''
# Signal Quartz and crystals

<Row gap="8">
  ''' + item('signal_quartz_ore') + '''
  ''' + item('deepslate_signal_quartz_ore') + '''
  ''' + item('signal_crystal') + '''
  ''' + item('cut_signal_crystal') + '''
</Row>

Quartz crystals are the fuel of the [Signal Resonator](signal-resonator.md), the mod's power generator.

## The ore

**Signal Quartz Ore** generates in the Overworld, in stone and in deepslate (the deepslate version is
harder to mine). It sits in small veins, most common near **y = −16**. Mine it with an **iron pickaxe** or better:

| Tool | Drop |
| --- | --- |
| Iron pickaxe or better | 1 to 3 **Signal Crystals** and a bit of experience. **Fortune** raises the amount. |
| Silk Touch | The ore itself. |

## The crystals

| Crystal | Yields | Lasts while generating |
| --- | --- | --- |
| ''' + item('signal_crystal') + ''' **Signal Crystal** | 1× | 10 minutes |
| ''' + item('cut_signal_crystal') + ''' **Cut Crystal** | 2× | 20 minutes |

These times are for a Basic or Advanced Core. The higher the Core's tier, the shorter the charge lasts: in an
Ultimate Core, what would last 20 minutes lasts 3.

The Signal Crystal only comes from the ore. The **Cut Crystal** is crafted from four Signal Crystals in a
cross around an Amethyst Shard, and only accepts **full** crystals: spent ones don't count.

<RecipeFor id="wirelessautomate:cut_signal_crystal" />

The Signal Crystal is also in the recipes for the Porcelain Casing and the Resonance Glass, and the Cut Crystal
in the one for the Resonance Core.

## Charge and Dull crystals

The item's **durability bar** shows how much is left before the crystal runs out: it only appears once the
crystal starts to wear. Inside the Core, the charge drops while it generates power.

With no charge left, the crystal turns **Dull**: it's the same item, just dimmed. A Dull crystal generates
nothing, but you can **recharge** it:

1. Leave the Dull crystal in a Core chamber.
2. Put **Redstone Dust** in the Core (click with redstone in hand, through the screen or through the Crystal Port).
3. The Core recharges **one Dull crystal at a time**: it takes 30 seconds and uses 1 redstone dust, and the crystal comes back full.

The Crystal Port only hands the Dull crystals back when the Core has no redstone; with redstone in it, the Dull
crystal stays inside, waiting its turn to recharge.

The numbers are the defaults; JEI shows your pack's.

See how to build the generator in [Signal Resonator](signal-resonator.md).
''')

page('signal-resonator.md', front('Ressonador de Sinal', 'wirelessautomate:resonance_core', 19, item_ids=[
    'wirelessautomate:resonator_casing', 'wirelessautomate:resonator_glass', 'wirelessautomate:resonance_core',
    'wirelessautomate:resonator_energy_port', 'wirelessautomate:resonator_crystal_port',
    'wirelessautomate:resonator_tuner']) + '''
# Ressonador de Sinal

<ItemImage id="wirelessautomate:resonance_core" scale="2" float="left" />

Um gerador de energia de vários blocos: cristais de quartzo vibram dentro de uma caixa, e você
**sintoniza** o Núcleo na frequência certa para tirar o máximo de FE. Os cristais vêm do
[Quartzo de Sinal](signal-quartz.md).

<br clear="all" />

''' + fill(SCENE_RESONATOR, **RESONATOR_ANNOTATIONS['pt']) + '''

A cena mostra a montagem **Vitrine**, com a janela de vidro numa face e o Núcleo em outra. Gire a cena com o mouse.

## Montar

1. Coloque o **Núcleo de Ressonância** e clique nele com a mão vazia: a estrutura aparece **projetada** no mundo
   (as peças que faltam e o que está errado). Clique de novo para desligar. **Shift + roda do mouse** troca entre
   as montagens **Vitrine** e **Faixa**.
2. A estrutura é uma caixa **5×5×5 oca**: **Carcaça de Porcelana** nas arestas e nos cantos; **Vidro de
   Ressonância**, as portas e o Sintonizador nas posições do miolo das faces. O interior 3×3×3 fica vazio.
3. Precisa de **um Núcleo**, **pelo menos uma Porta de Energia** e **pelo menos uma Porta de Cristais**; o
   **Sintonizador** é opcional (no máximo um). O Núcleo e as portas ficam no miolo das faces, nunca nas arestas.
4. Duas montagens de exemplo valem: a **Vitrine** (janela de Vidro 3×3 numa lateral) e a **Faixa** (três vidros na
   linha de cima de cada lateral). Outros arranjos também montam: o Vidro pode ficar em qualquer posição do
   miolo das faces.
5. Uma **parede não pode ser dividida** com outro Ressonador: cada um tem a sua caixa.

Ao completar, a estrutura se acende com um pulso e a barra de ação diz **Ressonador montado**. Se não monta, clique
no Núcleo solto: a barra de ação diz o que impede e o bloco errado fica contornado em coral.

| Bloco | Papel |
| --- | --- |
| ''' + item('resonator_casing') + ''' **Carcaça de Porcelana** | A casca da caixa. |
| ''' + item('resonator_glass') + ''' **Vidro de Ressonância** | A janela para ver a câmara. |
| ''' + item('resonance_core') + ''' **Núcleo de Ressonância** | O cérebro: tela, cristais e a energia gerada. |
| ''' + item('resonator_energy_port') + ''' **Porta de Energia** | Só tira energia: prenda um roteador nela. |
| ''' + item('resonator_crystal_port') + ''' **Porta de Cristais** | Aceita cristais e redstone e devolve os Opacos. |
| ''' + item('resonator_tuner') + ''' **Sintonizador** | Opcional: sintoniza sozinho. |

## Cristais e redstone

Cada Núcleo tem câmaras para os cristais (1 no Básico, até 8 no Ultimate). Ponha um **Cristal de Sinal** ou
**Lapidado** de um destes jeitos:

| Como | O que fazer |
| --- | --- |
| **Pelo clique** | Clique no Núcleo com o cristal na mão: ele vai para a primeira câmara vazia. Com redstone na mão, o pó entra no Núcleo. |
| **Pela tela** | Abra a tela do Núcleo e use os slots de cristais e o de redstone. |
| **Pela Porta de Cristais** | Um roteador (ou outro transporte de itens) põe os cristais e a redstone na porta e tira os Opacos dela. |

Para tirar o último cristal, clique no Núcleo com **Shift e as duas mãos vazias**. A **redstone** só serve
para recarregar os Opacos, como na página do [Quartzo de Sinal](signal-quartz.md).

## Sintonizar

Clique no Núcleo montado, com a mão vazia, para abrir a tela. O Ressonador tem uma **estação**, uma
frequência escondida, e o **dial** precisa estar em cima dela:

- Gire o dial com os botões (**±1 MHz** e **±0,05 MHz**) ou com a **roda do mouse** sobre o dial (±0,05 MHz).
- O ponteiro mostra se está perto; quando acende a lâmpada **EM SINTONIA**, a geração está a 100%.
- Se o ponteiro diz **sem sinal: varra o dial**, o dial está longe: varra-o até o ponteiro aparecer.
- Quanto mais longe da estação, menos energia. Muito longe, a geração vai a **zero**.

A estação **escorrega devagar com o tempo**, e a sintonia se perde aos poucos: volte de vez em quando e acerte o dial
de novo. A estação nunca aparece como número: você a acha pela lâmpada.

## Sintonizador

Com um **Sintonizador** na estrutura, o botão **Sintonizador** da tela liga o **modo automático**: o dial segue a
estação sozinho, sem você voltar. Em troca, a eficiência fica limitada por um **teto** que depende do tier do Núcleo
(da tabela mais abaixo). No modo automático o dial não responde aos botões: volte para **Manual** para girá-lo.

## A câmara

Pelo vidro dá para ler a sintonia, sem abrir a tela:

- **Em sintonia**: a figura de luz no ar **fecha**, os cristais sobem e descem **juntos**, cada um com um feixe até a
  luz, e a placa do chão fica nítida.
- **Fora de sintonia**: a figura vira um **rabisco**, cada cristal vai no seu tempo e o chão chia. Com a geração em zero,
  a figura some.
- A lente do Núcleo acende na parede dele, faíscas correm até a Porta de Energia e um fio de redstone chega ao cristal
  Opaco que está recarregando.

## Energia

A energia sai pela **Porta de Energia**: prenda um [Roteador](router.md) nela e ponha a face em **Extrai**
na aba **Energia**. O Núcleo guarda uma reserva pequena (uns 10 segundos de geração cheia); com ela cheia, ele
**para de gerar e não gasta cristal**. O Núcleo só trabalha com o chunk carregado: para ele rodar longe de você,
use o [Upgrade de Chunk Loading](chunk-loading.md) no roteador preso à Porta.

## Tiers

O Núcleo sobe de tier com os mesmos [Cartões de Upgrade](upgrade-cards.md) do roteador (Shift + clique com o
cartão no Núcleo), sem desmontar: o conteúdo continua e as câmaras novas aparecem. Cada tier tem mais câmaras e
mais FE por cristal. O Lapidado rende o dobro do que a tabela mostra para o Cristal de Sinal.

''' + resonator_table('pt') + '''

Os números são os padrões; o JEI mostra os do seu pack.

## Receitas

<RecipeFor id="wirelessautomate:resonator_casing" />
<RecipeFor id="wirelessautomate:resonator_glass" />
<RecipeFor id="wirelessautomate:resonance_core" />
<RecipeFor id="wirelessautomate:resonator_energy_port" />
<RecipeFor id="wirelessautomate:resonator_crystal_port" />
<RecipeFor id="wirelessautomate:resonator_tuner" />
''', front('Signal Resonator', 'wirelessautomate:resonance_core', 19, item_ids=[
    'wirelessautomate:resonator_casing', 'wirelessautomate:resonator_glass', 'wirelessautomate:resonance_core',
    'wirelessautomate:resonator_energy_port', 'wirelessautomate:resonator_crystal_port',
    'wirelessautomate:resonator_tuner']) + '''
# Signal Resonator

<ItemImage id="wirelessautomate:resonance_core" scale="2" float="left" />

A multiblock power generator: quartz crystals vibrate inside a box, and you **tune** the Core to the right
frequency to pull the most FE out of them. The crystals come from
[Signal Quartz](signal-quartz.md).

<br clear="all" />

''' + fill(SCENE_RESONATOR, **RESONATOR_ANNOTATIONS['en']) + '''

The scene shows the **Showcase** build, with the glass window on one face and the Core on another. Rotate the scene with the mouse.

## Building it

1. Place the **Resonance Core** and click it with an empty hand: the structure shows up **projected** in the world
   (the missing parts and what is wrong). Click again to turn it off. **Shift + mouse wheel** switches between the
   **Showcase** and **Band** builds.
2. The structure is a **hollow 5×5×5 box**: **Porcelain Casing** on the edges and corners; **Resonance Glass**, the
   ports and the Tuner on the face positions in between. The 3×3×3 inside stays empty.
3. It needs **one Core**, **at least one Energy Port** and **at least one Crystal Port**; the **Tuner** is optional
   (at most one). The Core and the ports go on face positions, never on the edges.
4. Two example builds work: the **Showcase** (a 3×3 Glass window on one side) and the **Band** (three glass blocks on
   the top row of each side). Other layouts also build: the Glass can go on any face position.
5. A **wall can't be shared** with another Resonator: each one has its own box.

When it completes, the structure lights up with a pulse and the action bar says **Resonator formed**. If it
doesn't build, click the loose Core: the action bar says what's in the way and the wrong block is outlined in coral.

| Block | Role |
| --- | --- |
| ''' + item('resonator_casing') + ''' **Porcelain Casing** | The shell of the box. |
| ''' + item('resonator_glass') + ''' **Resonance Glass** | The window onto the chamber. |
| ''' + item('resonance_core') + ''' **Resonance Core** | The brain: screen, crystals and the generated power. |
| ''' + item('resonator_energy_port') + ''' **Resonator Energy Port** | Only pulls energy out: attach a router to it. |
| ''' + item('resonator_crystal_port') + ''' **Resonator Crystal Port** | Accepts crystals and redstone and hands Dull ones back. |
| ''' + item('resonator_tuner') + ''' **Resonator Tuner** | Optional: tunes by itself. |

## Crystals and redstone

Each Core has chambers for crystals (1 on Basic, up to 8 on Ultimate). Put a **Signal Crystal** or a
**Cut Crystal** in one of these ways:

| How | What to do |
| --- | --- |
| **By clicking** | Click the Core with the crystal in hand: it goes to the first empty chamber. With redstone in hand, the dust goes into the Core. |
| **Through the screen** | Open the Core's screen and use the crystal slots and the redstone slot. |
| **Through the Crystal Port** | A router (or another item transport) puts crystals and redstone into the port and takes the Dull ones out. |

To take the last crystal out, click the Core with **Shift and both hands empty**. The **redstone** is only for
recharging Dull crystals, as on the [Signal Quartz](signal-quartz.md) page.

## Tuning

Click the formed Core with an empty hand to open its screen. The Resonator has a **station**, a hidden
frequency, and the **dial** has to sit right on it:

- Turn the dial with the buttons (**±1 MHz** and **±0.05 MHz**) or with the **mouse wheel** over the dial (±0.05 MHz).
- The pointer shows how close you are; when the **IN TUNE** lamp lights up, generation is at 100%.
- If the pointer says **no signal: sweep the dial**, the dial is far off: sweep it until the pointer shows up.
- The farther from the station, the less power. Far enough, generation drops to **zero**.

The station **drifts slowly over time**, and the tuning is lost little by little: come back now and then and set
the dial again. The station never shows up as a number: you find it by the lamp.

## Tuner

With a **Tuner** in the structure, the screen's **Tuner** button turns on **automatic mode**: the dial follows the
station by itself, with no need to come back. In exchange, efficiency is capped by a **ceiling** that depends on the
Core's tier (in the table below). In automatic mode the dial ignores the buttons: switch back to **Manual** to turn it.

## The chamber

Through the glass you can read the tuning without opening the screen:

- **In tune**: the figure of light in the air **closes**, the crystals rise and fall **together**, each with a beam to
  the light, and the plate on the floor gets sharp.
- **Out of tune**: the figure turns into a **scribble**, each crystal moves on its own and the floor hisses. With
  generation at zero, the figure disappears.
- The Core's lens lights up on its wall, sparks run to the Energy Port and a thread of redstone reaches the Dull
  crystal that is recharging.

## Power

Power leaves through the **Energy Port**: attach a [Router](router.md) to it and set the face to **Extract** on the
**Energy** tab. The Core keeps a small reserve (about 10 seconds of full generation); when it's full, the Core
**stops generating and doesn't wear the crystals**. The Core only works while its chunk is loaded: to run it while
you're away, use the [Chunk Loading Upgrade](chunk-loading.md) on the router attached to the Port.

## Tiers

The Core goes up in tier with the same [Upgrade Cards](upgrade-cards.md) as the router (Shift + click the Core with
the card), without taking it apart: the contents stay and the new chambers show up. Each tier has more chambers and
more FE per crystal. The Cut Crystal yields twice what the table shows for the Signal Crystal.

''' + resonator_table('en') + '''

The numbers are the defaults; JEI shows your pack's.

## Recipes

<RecipeFor id="wirelessautomate:resonator_casing" />
<RecipeFor id="wirelessautomate:resonator_glass" />
<RecipeFor id="wirelessautomate:resonance_core" />
<RecipeFor id="wirelessautomate:resonator_energy_port" />
<RecipeFor id="wirelessautomate:resonator_crystal_port" />
<RecipeFor id="wirelessautomate:resonator_tuner" />
''')

# =====================================================================================
# Problemas comuns
# =====================================================================================
page('troubleshooting.md', front('Problemas comuns', 'minecraft:barrier', 20) + '''
# Problemas comuns

Algo não se move? Confira na ordem: quase sempre é uma destas.

| Sintoma | Causa provável | O que fazer |
| --- | --- | --- |
| Nada sai da origem | A face está em **Nenhum** ou **Insere**. | Na aba certa, ponha a face em **Extrai**. |
| Nada sai da origem | As faces estão em redes diferentes, ou sem rede. | Confira a linha **Rede** da face nos dois roteadores (o ponto em cima de cada botão mostra a cor da rede). |
| Nada chega no destino | O destino está longe demais para o tier de quem envia. | Suba o tier da origem com um [Cartão de Upgrade](upgrade-cards.md). |
| Nada chega no destino | O destino está cheio. | Ele dorme até ter espaço e volta sozinho. |
| Só alguns itens passam | Um [filtro](filters.md) ou estoque está barrando. | Veja o filtro da face nos dois lados; filtro vazio passa tudo. |
| A face funciona e para | Redstone em **Com sinal** ou **Sem sinal**. | Ponha em **Ignorar** ou ajuste o sinal. |
| Para quando você se afasta | O chunk descarregou. | Use o [Upgrade de Chunk Loading](chunk-loading.md). |
| O modo aparece indisponível | A máquina não dá acesso àquele tipo por aquela face. | Escolha outra face no visor 3D, ou configure a máquina. |
| Químicos não se movem | A face da máquina do Mekanism está desligada nos lados dela. | Ligue a face com a ferramenta de configuração do Mekanism. |
| Não consigo pôr na rede | A rede é de outro jogador. | Peça ao dono, ou use uma rede sua. |
| Vazão menor que a do tier | Muitas máquinas movendo ao mesmo tempo. | Normal: a vazão se divide entre elas, sem travar o jogo. |

Ainda com dúvida? Abra o [Tablet de Rede](network-tablet.md): a aba **Fluxos** mostra o que
cada rede está movendo, quais faces esperam destino e quais destinos estão cheios.
''', front('Troubleshooting', 'minecraft:barrier', 20) + '''
# Troubleshooting

Something not moving? Check in order: it's almost always one of these.

| Symptom | Likely cause | What to do |
| --- | --- | --- |
| Nothing leaves the source | The face is on **None** or **Insert**. | On the right tab, set the face to **Extract**. |
| Nothing leaves the source | The faces are on different networks, or on none. | Check the face's **Network** row on both routers (the dot above each button shows the network's color). |
| Nothing reaches the destination | It's too far for the sender's tier. | Raise the source's tier with an [Upgrade Card](upgrade-cards.md). |
| Nothing reaches the destination | The destination is full. | It sleeps until there's room and comes back on its own. |
| Only some items pass | A [filter](filters.md) or stock limit is blocking. | Check the face filter on both sides; an empty filter lets everything through. |
| The face works, then stops | Redstone set to **With signal** or **No signal**. | Set it to **Ignore** or fix the signal. |
| It stops when you walk away | The chunk unloaded. | Use the [Chunk Loading Upgrade](chunk-loading.md). |
| The mode shows as unavailable | The machine gives no access to that type through that face. | Pick another face in the 3D viewer, or configure the machine. |
| Chemicals don't move | The Mekanism machine's face is disabled in its side config. | Enable the face with Mekanism's configurator tool. |
| Can't put it in a network | The network belongs to another player. | Ask the owner, or use one of yours. |
| Less throughput than the tier | Many machines moving at once. | Normal: throughput is shared between them, without freezing the game. |

Still stuck? Open the [Network Tablet](network-tablet.md): the **Flows** tab shows what each
network is moving, which faces are waiting for a destination and which destinations are full.
''')

# =====================================================================================
# Todas as receitas
# =====================================================================================
RECIPES = '''<RecipeFor id="wirelessautomate:router" />
<RecipeFor id="wirelessautomate:storage_chest" />
<RecipeFor id="wirelessautomate:storage_tank" />
<RecipeFor id="wirelessautomate:storage_battery" />
<RecipeFor id="wirelessautomate:storage_chemical_tank" />
<RecipeFor id="wirelessautomate:storage_source_tank" />
<RecipeFor id="wirelessautomate:tier_core_advanced" />
<RecipeFor id="wirelessautomate:tier_core_elite" />
<RecipeFor id="wirelessautomate:tier_core_ultimate" />
<RecipeFor id="wirelessautomate:filter_card" />
<RecipeFor id="wirelessautomate:linker" />
<RecipeFor id="wirelessautomate:configurator" />
<RecipeFor id="wirelessautomate:network_tablet" />
<RecipeFor id="wirelessautomate:chunk_loader_upgrade" />
<RecipeFor id="wirelessautomate:cut_signal_crystal" />
<RecipeFor id="wirelessautomate:resonator_casing" />
<RecipeFor id="wirelessautomate:resonator_glass" />
<RecipeFor id="wirelessautomate:resonance_core" />
<RecipeFor id="wirelessautomate:resonator_energy_port" />
<RecipeFor id="wirelessautomate:resonator_crystal_port" />
<RecipeFor id="wirelessautomate:resonator_tuner" />
<Recipe id="wirelessautomate:guide" />'''

page('recipe-editor.md', front('Editor de receitas', 'minecraft:writable_book', 21) + '''
# Editor de receitas

Troque os ingredientes e o resultado das receitas de bancada do mod sem sair do jogo e sem mexer em
arquivos. Serve para ajustar o custo dos itens do Wireless Automate ao seu pack.

## Quem pode usar

Só operadores (permissão 2). Num mundo de um jogador só, é preciso ter os comandos ativados (cheats).

## Abrir

Digite `/wa recipes`. A tela mostra, à esquerda, as receitas de bancada do mod que estão carregadas,
com uma busca no topo. A bolinha de cada receita diz o estado:

| Bolinha | Estado |
| --- | --- |
| **Verde** | Receita padrão do mod. |
| **Coral** | Receita editada por você. |
| **Cinza** | Receita desativada. |

## Editar uma receita

Escolha uma receita na lista: a grade 3 × 3 e o resultado aparecem à direita.

| Para... | Faça |
| --- | --- |
| Pôr um item num slot | Clique no slot e depois num item do seu inventário (o item não sai de lá), ou clique no slot com o item no cursor. Com o JEI, arraste o item para o slot. |
| Esvaziar um slot | Clique com o botão direito nele. |
| Aceitar qualquer item de uma tag | Escolha o slot e clique em **Tag**. As setas trocam entre as tags do item. **Item** volta para o item exato. |
| Esvaziar o slot escolhido | **Limpar** (ou clique direito no slot). |
| Mudar a quantidade do resultado | **−** e **+**, até o tamanho da pilha do item (no máximo 64). |
| Mudar o anel de um Cartão de Upgrade | Troque os oito slots em volta. O centro fica travado: nele vai o roteador, o armazenamento, a Redstone ou o cartão, e o anel novo vale para todos esses. |

## Salvar e recarregar

| Botão | O que faz |
| --- | --- |
| **Salvar** | Só grava a receita. Nada muda no jogo ainda. |
| **Recarregar agora** | Aplica as mudanças salvas, para todos os jogadores, na hora. |
| **Restaurar padrão** | Volta a receita ao que o mod traz. |
| **Desativar** / **Reativar** | Tira a receita do jogo, ou a devolve. |

Se você fechar a tela com mudanças salvas ainda não aplicadas, elas são recarregadas ao fechar.

## Bom saber

| | |
| --- | --- |
| **Onde fica** | As edições ficam em `config/wirelessautomate/recipes/`, uma pasta só para a instância. Valem em todos os mundos dela e, num servidor, no servidor. Sobrevivem às atualizações do mod. |
| **Outros datapacks e KubeJS** | Se outro datapack ou o KubeJS mexer na mesma receita, o dele vale, e a tela avisa que a receita é diferente do que está carregado. |
| **O que não aparece** | Receitas de outros mods, a cópia de Cartão de Filtro e o upgrade de roteador na bancada. Também não dá para criar receitas novas. |
| **Voltar tudo** | Apague a pasta `data` dentro de `config/wirelessautomate/recipes/` e recarregue (`/reload`). |
''', front('Recipe editor', 'minecraft:writable_book', 21) + '''
# Recipe editor

Change the ingredients and the result of the mod's crafting-table recipes without leaving the game
and without touching files. Use it to fit the cost of Wireless Automate items to your pack.

## Who can use it

Operators only (permission level 2). In a single-player world, cheats must be enabled.

## Opening it

Type `/wa recipes`. The screen lists, on the left, the mod's crafting recipes that are currently
loaded, with a search box on top. The dot next to each recipe shows its state:

| Dot | State |
| --- | --- |
| **Green** | The mod's default recipe. |
| **Coral** | A recipe you edited. |
| **Gray** | A disabled recipe. |

## Editing a recipe

Pick a recipe in the list: the 3 × 3 grid and the result show up on the right.

| To... | Do this |
| --- | --- |
| Put an item in a slot | Click the slot, then an item in your inventory (the item stays there), or click the slot with the item on your cursor. With JEI, drag the item onto the slot. |
| Empty a slot | Right-click it. |
| Accept any item of a tag | Pick the slot and click **Tag**. The arrows cycle through the item's tags. **Item** goes back to the exact item. |
| Empty the chosen slot | **Clear** (or right click the slot). |
| Change the result's amount | **−** and **+**, up to the item's stack size (64 at most). |
| Change an Upgrade Card's ring | Swap the eight slots around it. The center is locked: it holds the router, the storage, the Redstone or the card, and the new ring applies to all of them. |

## Saving and reloading

| Button | What it does |
| --- | --- |
| **Save** | Only writes the recipe. Nothing changes in the game yet. |
| **Reload now** | Applies the saved changes, for every player, right away. |
| **Restore default** | Puts the recipe back to what the mod ships. |
| **Disable** / **Enable** | Takes the recipe out of the game, or brings it back. |

If you close the screen with saved changes that haven't been applied, they are reloaded on close.

## Good to know

| | |
| --- | --- |
| **Where it lives** | Edits are kept in `config/wirelessautomate/recipes/`, a folder for the instance. They apply to every world in it and, on a server, to the server. They survive mod updates. |
| **Other datapacks and KubeJS** | If another datapack or KubeJS changes the same recipe, theirs wins, and the screen warns that the recipe differs from what is loaded. |
| **What is not listed** | Other mods' recipes, the Filter Card copy and the router upgrade in the crafting table. You can't create new recipes either. |
| **Undo everything** | Delete the `data` folder inside `config/wirelessautomate/recipes/` and reload (`/reload`). |
''')

page('recipes.md', front('Todas as receitas', 'minecraft:crafting_table', 22) + '''
# Todas as receitas

Todas com itens vanilla, na bancada.

| Também na bancada | Como |
| --- | --- |
| **Subir o tier** | Roteador ou armazenamento (Baú, Tanque, Bateria, Tanque Químico, Tanque de Source) + o cartão de um tier acima, em qualquer posição. |
| **Copiar um Cartão de Filtro** | Cartão configurado + cartões vazios: o original volta. |
| **Este guia** | Livro + redstone. |

''' + RECIPES + '''
''', front('All recipes', 'minecraft:crafting_table', 22) + '''
# All recipes

All of them with vanilla items, in a crafting table.

| Also in the crafting table | How |
| --- | --- |
| **Raise the tier** | Router or storage (Chest, Tank, Battery, Chemical Tank, Source Tank) + the card of any higher tier, in any slots. |
| **Copy a Filter Card** | Configured card + blank cards: the original comes back. |
| **This guide** | Book + redstone. |

''' + RECIPES + '''
''')


def main():
    pt_dir = os.path.join(BASE, '_pt_br')
    for folder in (BASE, pt_dir):
        if os.path.isdir(folder):
            for name in os.listdir(folder):
                if name.endswith('.md'):
                    os.remove(os.path.join(folder, name))
    for name, pt, en in PAGES:
        write('pt', name, pt)
        write('en', name, en)
    # Ícones das portas da tela, para as tabelas de modo.
    images = os.path.join(BASE, 'images')
    os.makedirs(images, exist_ok=True)
    gui = os.path.join(ROOT, 'src/main/resources/assets/wirelessautomate/textures/gui')
    for mode in ('extract', 'insert', 'both', 'none'):
        # Ampliado 2x sem suavização: o GuideME desenha a imagem pequena no meio do texto.
        Image.open(os.path.join(gui, f'port_{mode}.png')).resize((32, 32), Image.NEAREST).save(
            os.path.join(images, f'port_{mode}.png'))
    print(f'{len(PAGES)} páginas em inglês e português')


if __name__ == '__main__':
    main()
