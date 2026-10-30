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
    'energy': [454, 29_100, 63_000, 1_350_000, 67_200_000, 84_500_000, 158_000_000, 0],
    'source': [100, 800, 6_400, 51_200, 409_600, 3_276_800, 26_214_400, 0],
}
RANGE = {
    'pt': ['64 blocos', '512 blocos', 'A dimensão inteira'] + ['Todas as dimensões'] * 5,
    'en': ['64 blocks', '512 blocks', 'The whole dimension'] + ['Every dimension'] * 5,
}
CAPACITY = {
    'chest': [32_768, 262_144, 2_097_152, 16_777_216, 134_217_728, 1_073_741_824, 8_589_934_592, 0],
    'tank': [256_000, 2_048_000, 16_384_000, 131_072_000, 1_048_576_000, 8_388_608_000, 67_108_864_000, 0],
    'battery': [31_104_000, 1_990_656_000, 4_320_000_000, 92_160_000_000, 4_608_000_000_000,
                5_788_800_000_000, 10_785_600_000_000, 0],
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
| ''' + item('linker') + ''' [Vinculador](linker.md) | Liga máquinas a um roteador de alcance e abre o roteador de uma máquina. |
| ''' + item('configurator') + ''' [Configurador](configurator.md) | Copia a configuração de um roteador e cola em outro. |
| ''' + item('network_tablet') + ''' [Tablet de Rede](network-tablet.md) | Vê e gerencia todas as suas redes de qualquer lugar. |
| ''' + item('chunk_loader_upgrade') + ''' [Upgrade de Chunk Loading](chunk-loading.md) | Mantém o roteador trabalhando com você longe. |
| ''' + item('storage_chest') + ''' [Baú Wireless](wireless-chest.md) | Guarda milhões de itens por tipo; entre dois deles, um tipo inteiro passa de uma vez. |
| ''' + item('storage_tank') + ''' [Tanque Wireless](wireless-tank.md) | Vários fluidos num tanque só, com bilhões de mB. |
| ''' + item('storage_battery') + ''' [Bateria Wireless](wireless-battery.md) | Energia sem o teto de um `int`. |
| ''' + item('storage_chemical_tank') + ''' [Tanque Químico Wireless](wireless-chemical-tank.md) | O tanque para os químicos do Mekanism. |
| ''' + item('storage_source_tank') + ''' [Tanque de Source Wireless](wireless-source-tank.md) | Source do Ars Nouveau em grande quantidade. |
| ''' + item('signal_crystal') + ''' [Quartzo de Sinal e cristais](signal-quartz.md) | O minério e os cristais que movem o Ressonador. |
| ''' + item('resonance_core') + ''' [Ressonador de Sinal](signal-resonator.md) | Um gerador de energia: cristais, sintonia e FE. |
| ''' + item('crystal_repairer') + ''' [Reparador de Cristais](crystal-repairer.md) | Conserta cristais rachados, até oito de uma vez. |
| ''' + item('signal_screen') + ''' [Tela de Sinal](signal-screen.md) | Um painel na parede, do tamanho que você quiser, com os números das suas redes. |
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
| ''' + item('linker') + ''' [Linker](linker.md) | Links machines to a range router and opens the router of a machine. |
| ''' + item('configurator') + ''' [Configurator](configurator.md) | Copies a router's configuration and pastes it on another. |
| ''' + item('network_tablet') + ''' [Network Tablet](network-tablet.md) | See and manage all your networks from anywhere. |
| ''' + item('chunk_loader_upgrade') + ''' [Chunk Loading Upgrade](chunk-loading.md) | Keeps the router working while you're away. |
| ''' + item('storage_chest') + ''' [Wireless Chest](wireless-chest.md) | Stores millions of items by type; between two of them, a whole type moves at once. |
| ''' + item('storage_tank') + ''' [Wireless Tank](wireless-tank.md) | Many fluids in one tank, with billions of mB. |
| ''' + item('storage_battery') + ''' [Wireless Battery](wireless-battery.md) | Energy without an `int`'s cap. |
| ''' + item('storage_chemical_tank') + ''' [Wireless Chemical Tank](wireless-chemical-tank.md) | The tank for Mekanism chemicals. |
| ''' + item('storage_source_tank') + ''' [Wireless Source Tank](wireless-source-tank.md) | Ars Nouveau Source in large amounts. |
| ''' + item('signal_crystal') + ''' [Signal Quartz and crystals](signal-quartz.md) | The ore and the crystals that power the Resonator. |
| ''' + item('resonance_core') + ''' [Signal Resonator](signal-resonator.md) | A power generator: crystals, tuning and FE. |
| ''' + item('crystal_repairer') + ''' [Crystal Repairer](crystal-repairer.md) | Fixes cracked crystals, up to eight at a time. |
| ''' + item('signal_screen') + ''' [Signal Screen](signal-screen.md) | A wall panel, as big as you want, showing your networks' numbers. |
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
| **1** | Faça dois roteadores (receita abaixo). |
| **2** | Clique com um roteador em qualquer face do baú A. Ele fica **preso** ao baú, ainda **sem rede**. Faça o mesmo no baú B. |
| **3** | Na tela do roteador A, na aba **Itens**, escolha a face **Cima** e, na linha **Rede**, crie uma rede e escolha-a. Faça o mesmo no roteador B, escolhendo a mesma rede. |
| **4** | No roteador A, na face **Cima**, modo **Extrai**. |
| **5** | No roteador B, mesma face, modo **Insere**. |
| **6** | Coloque itens no baú A: eles aparecem no baú B. |

Depois de configurar o primeiro roteador de uma linha, use o [Configurador](configurator.md) para
copiar a configuração (e as redes) para os outros.

As telas do mod têm um botão com uma lua no canto de cima, à direita: ele troca todas para o modo escuro (e o sol volta ao claro).

## Receitas

<RecipeFor id="wirelessautomate:router" />

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
| **1** | Craft two routers (recipe below). |
| **2** | Use a router on any face of chest A. It **attaches** to the chest, with **no network** yet. Do the same on chest B. |
| **3** | On router A's screen, on the **Items** tab, pick the **Up** face and, in the **Network** row, create a network and pick it. Do the same on router B, picking the same network. |
| **4** | On router A, **Up** face, **Extract** mode. |
| **5** | On router B, same face, **Insert** mode. |
| **6** | Put items in chest A: they show up in chest B. |

Once the first router of a line is set up, use the [Configurator](configurator.md) to copy its
configuration (and networks) to the others.

Every screen of the mod has a moon button in the top-right corner: it switches all of them to dark mode (the sun switches back).

## Recipes

<RecipeFor id="wirelessautomate:router" />

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
| **3. Escolha o modo** | Clique no botão da face no cubo: ele **avança** o modo (Extrai, Insere, Armazém, Nenhum e de volta). Clique direito **volta** o modo e Shift + clique põe em **Nenhum**. A tabela abaixo explica cada modo. |
| **4. Ajustes (opcional)** | Prioridade (‹ ›, ou a roda do mouse) e redstone (‹ ›) ficam sempre à vista no painel. Os dois slots de **Cartão de Filtro** ficam na linha **Filtro**, ao lado do **Editar**, que abre o [filtro](filters.md) da face. |
| **5. Rede** | Na linha **Rede**, escolha a [rede](networks.md) da face (ou **Sem rede**). |

Arraste a borda direita, a de baixo ou o canto para aumentar a tela; o visor 3D cresce. Com pouco espaço, as abas mostram só o ícone (o nome aparece ao passar o mouse).

## O painel e o cubo

O painel à direita é o mesmo que o [Tablet de Rede](network-tablet.md) mostra para um nó. No alto,
o **cubo desdobrado** mostra as seis faces da máquina de uma vez: o botão de cada face é o seu modo,
com o ícone da porta na cor dele, a letra do lado e uma bolinha na cor da rede. Uma face sem modo fica
afundada, só com a letra; uma com modo e sem rede ganha a borda tracejada em coral, e a escolhida tem
a borda dupla. O olho marca a face onde o roteador está preso.

Os nomes são os lados da **máquina**, vistos de frente para ela: Frente, Trás, Esquerda, Direita,
Cima e Baixo. Máquina sem frente, ou com a frente para cima ou para baixo, como um barril:
**Frente = Norte**.

| No painel | O que mostra |
| --- | --- |
| **Bolinha na face** | A cor da rede daquela face (sem bolinha: sem rede). |
| **Linha da face** | Embaixo do cubo, o lado, o modo (na cor dele) e o que a face acessa, como "Cima ▪ Extrai · 27 slots · roteador aqui". O texto de cada modo está na dica do botão. |
| **Linha Rede** | A rede da face escolhida; clique para trocar. |
| **Limpar** | Põe em Nenhum todas as faces do tipo aberto (dois cliques; com Shift no segundo, de todos os tipos). |
| **Copiar e Colar** | Copiar guarda a face escolhida (modo, prioridade, redstone, filtro e rede); escolha outra face do mesmo tipo e **Colar** copia tudo para ela. |
| **Resumo ao lado das abas** | Em quantas redes estão as faces da aba (**1 rede**, **2 redes**, **Sem rede**); passe o mouse para ver qual face está em qual. |
| **Tirar redes** | Com o Upgrade de Alcance, editando um grupo de máquinas ou uma máquina com exceção: tira a rede das faces com modo Nenhum, em todas as abas (as que movem algo ficam como estão). Fica na linha **Editando**; com a lista das máquinas à vista, é o ícone ⊘ na linha escolhida. O aviso traz **Desfazer**. |

## Máquinas à distância

Com o Upgrade de Alcance, o roteador atende várias máquinas ao redor, e a lista delas aparece embaixo do visor, com a barra **Editando** no painel. Cada máquina é uma linha com o estado, o nome e a distância (as coordenadas ficam na dica).

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
| **3. Pick the mode** | Click the face's button on the cube: it **advances** the mode (Extract, Insert, Storage, None and back around). Right click goes **back** and Shift + click sets **None**. The table below explains each mode. |
| **4. Settings (optional)** | Priority (‹ ›, or the mouse wheel) and redstone (‹ ›) are always in view on the panel. The two **Filter Card** slots sit on the **Filter** row, next to **Edit**, which opens the face's [filter](filters.md). |
| **5. Network** | On the **Network** row, pick the face's [network](networks.md) (or **No network**). |

Drag the right edge, the bottom edge or the corner to make the screen bigger; the 3D view grows. When space is short, the tabs show only their icon (hover for the name).

## The panel and the cube

The panel on the right is the same one the [Network Tablet](network-tablet.md) shows for a node. At
the top, the **unfolded cube** shows the machine's six faces at once: each face's button is its mode,
with the port icon in its color, the side's letter and a dot in its network's color. A face with no
mode is sunken, with only the letter; one with a mode but no network gets a dashed coral border, and
the selected one has a double border. The eye marks the face the router is attached to.

The names are the **machine's** sides, as seen facing it: Front, Back, Left, Right, Top and Bottom.
A machine without a front, or facing up or down, like a barrel: **Front = North**.

| On the panel | What it shows |
| --- | --- |
| **Dot on a face** | The color of that face's network (no dot: no network). |
| **Face line** | Under the cube, the side, the mode (in its color) and what the face reaches, like "Top ▪ Extract · 27 slots · router here". Each mode's description is in the button's tooltip. |
| **Network row** | The selected face's network; click to change it. |
| **Clear** | Sets every face of the open type to None (two clicks; with Shift on the second, every type). |
| **Copy and Paste** | Copy keeps the selected face (mode, priority, redstone, filter and network); pick another face of the same type and **Paste** copies it all onto that one. |
| **Summary next to the tabs** | How many networks the tab's faces are in (**1 network**, **2 networks**, **No network**); hover to see which face is in which. |
| **Clear networks** | With the Range Upgrade, while editing a machine group or a machine with an exception: removes the network from the faces set to None, on every tab (faces that move something stay as they are). It sits on the **Editing** row; with the machine list in view, it's the ⊘ icon on the chosen row. The notice has an **Undo** button. |

## Remote machines

With the Range Upgrade, the router serves several machines around it, and their list sits under the viewer, with the **Editing** bar on the panel. Each machine is one row with its state, its name and its distance (the coordinates are in the tooltip).

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
| **Rede ativa** | Cada jogador tem uma rede ativa, a que o [Tablet](network-tablet.md) usa como destino do **Mover**. |
| **Rede por face** | Numa fornalha, a face de **Cima** (entrada) pode estar na rede "Mina" e a de **Baixo** (saída) na rede "Armazém": o minério chega pela Mina e o lingote sai pelo Armazém. Cada aba tem as suas: a energia pode ir para a rede "Base". |
| **Ordem de entrega** | Prioridade maior primeiro; empates se revezam (round-robin). |
| **Armazém** | Recebe de quem extrai e entrega para quem insere, sem ficar trocando com outro Armazém. |
| **Quem usa** | Uma rede pública é de todos. Numa privada, o dono, os operadores e os **convidados** (e, com o FTB Teams, os membros dos times que você adicionar) podem pôr roteadores nela e tirá-los. Só o dono (ou um operador) gerencia: renomeia, convida e remove. |

## Como mudar a rede

| Jeito | Quantos de uma vez |
| --- | --- |
| Linha **Rede** da face, na tela do roteador | Uma face de um roteador, numa aba |
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
| **Active network** | Every player has an active network, the one the [Tablet](network-tablet.md) uses as the target of **Move**. |
| **Network per face** | On a furnace, the **Top** face (input) can be on the "Mine" network and the **Bottom** face (output) on the "Storage" network: ore comes in through Mine and ingots leave through Storage. Each tab has its own: the energy can go to the "Base" network. |
| **Delivery order** | Higher priority first; ties take turns (round-robin). |
| **Storage** | Receives from extractors and delivers to inserters, without bouncing between two Storage faces. |
| **Who can use it** | A public network is open to everyone. On a private one, the owner, operators and **guests** (and, with FTB Teams, the members of the teams you add) can put routers in it and take them out. Only the owner (or an operator) manages it: rename, invite and remove. |

## Changing the network

| Way | How many at once |
| --- | --- |
| The face's **Network** row, on the router screen | One face of one router, on one tab |
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

Liga **máquinas** a um roteador de alcance, uma a uma ou uma área inteira, e abre o roteador que chega
numa máquina.

<br clear="all" />

## Ficha

| | |
| --- | --- |
| **Modos** | **Vincular** (padrão) e **Abrir**. Shift + roda do mouse troca de um para o outro. |
| **Vincular** | Escolha um roteador com o Upgrade de Alcance e clique nas máquinas: cada clique vincula a máquina a ele (um clique numa já vinculada a desvincula). A tela vincula uma área inteira de uma vez. |
| **Abrir** | Clique numa máquina: abre a tela do roteador que chega nela, já na máquina. Se chegam vários (o preso nela e os de alcance), uma lista curta deixa escolher. |
| **Redes** | O Vinculador não mexe nelas: escolha a rede de cada face na linha **Rede** da tela do roteador ou pelo [Tablet](network-tablet.md). |
| **Tela de Sinal** | Clique numa [Tela de Sinal](signal-screen.md) para escolhê-la e depois num Baú, Bateria ou Tanque do mod: o armazenamento fica ligado à Tela (outro clique nele desliga). Sem limite de distância nem de dimensão. |

## Comandos

| Gesto | Vincular | Abrir |
| --- | --- | --- |
| Clique num roteador | Escolhe o roteador | — |
| Clique numa Tela de Sinal | Escolhe a Tela | — |
| Clique num Baú, Bateria ou Tanque do mod | Com uma Tela escolhida, liga ou desliga o armazenamento a ela | Abre o roteador que chega nele |
| Clique numa máquina | Vincula ou desvincula a máquina | Abre o roteador que chega nela |
| Clique no ar | Abre a tela | Mostra a dica do modo |
| Shift + roda do mouse | Passa para **Abrir** | Passa para **Vincular** |
| Shift + clique em dois blocos | Marca os cantos da área | — |

## Na tela (modo Vincular)

| Parte | Como usar |
| --- | --- |
| **Roteador escolhido** | O nome, a posição e as vagas usadas (por exemplo **7/32 máquinas**). Se o roteador ainda não vincula (sem o Upgrade de Alcance), o motivo aparece aqui. |
| **Vincular / Desvincular** | No cabeçalho. Em **Desvincular**, os cliques e o botão da área tiram as máquinas do roteador em vez de pôr. |
| **Prévia** | A área marcada vista de cima, com os cantos e as máquinas do roteador dentro dela. |
| **Vincular área** | Liga ao roteador as máquinas da área que ele alcança, as mais perto primeiro, até o limite do tier. Em **Desvincular**, vira **Desvincular N** e tira as que estão dentro. O resultado aparece embaixo da prévia. |
| **Limpar** | Apaga os cantos. |

Arraste a borda ou o canto para aumentar a tela; a prévia cresce.

A área pode ter até 262.144 blocos (por exemplo 64 × 64 × 64), e você precisa estar a até 64
blocos dela.

## A lista do modo Abrir

Quando mais de um roteador chega na máquina, a lista mostra uma linha por roteador: o nome, se ele está
**preso** na máquina ou chega por **alcance** e a distância. Clique numa linha para abrir. Só entram
roteadores ao seu alcance e que você pode tocar.

## Ligar armazenamentos a uma Tela

Clique com o Vinculador numa [Tela de Sinal](signal-screen.md): ela fica escolhida e a action bar mostra quantos armazenamentos ela já tem. Depois clique num **Baú, Bateria ou Tanque** do mod para ligá-lo à Tela; clique de novo nele para desligar. Não há limite de distância nem de dimensão. Você precisa poder editar a Tela. Com o armazenamento ligado, a Tela mostra o conteúdo dele ao vivo; se o chunk estiver descarregado, ela mantém o último número, apagado.

## Exemplo: ligar as máquinas de uma sala

| Passo | O que fazer |
| --- | --- |
| **1** | Ponha o Upgrade de Alcance num roteador e clique nele com o Vinculador. |
| **2** | Com Shift, clique em dois cantos da sala. |
| **3** | Clique no ar para abrir a tela e em **Vincular área**: as máquinas dentro do alcance entram, até o limite do tier. |
| **4** | No modo **Abrir**, clique numa delas para configurar a face no roteador. |

## Receita

<RecipeFor id="wirelessautomate:linker" />
''', front('Linker', 'wirelessautomate:linker', 7, item_ids=['wirelessautomate:linker']) + '''
# Linker

<ItemImage id="wirelessautomate:linker" scale="2" float="left" />

Links **machines** to a range router, one at a time or a whole area, and opens the router that reaches a
machine.

<br clear="all" />

## Spec sheet

| | |
| --- | --- |
| **Modes** | **Link** (default) and **Open**. Shift + mouse wheel switches between them. |
| **Link** | Pick a router with the Range Upgrade and click machines: each click links the machine to it (clicking one that is already linked unlinks it). The screen links a whole area at once. |
| **Open** | Click a machine: opens the screen of the router that reaches it, already on the machine. If several reach it (the one attached to it and the range ones), a short list lets you choose. |
| **Networks** | The Linker doesn't touch them: pick each face's network in the router screen's **Network** row or with the [Tablet](network-tablet.md). |
| **Signal Screen** | Click a [Signal Screen](signal-screen.md) to pick it, then a mod Chest, Battery or Tank: the storage becomes linked to the Screen (clicking it again unlinks). No limit on distance or dimension. |

## Actions

| Action | Link | Open |
| --- | --- | --- |
| Click a router | Picks the router | — |
| Click a Signal Screen | Picks the Screen | — |
| Click a mod Chest, Battery or Tank | With a Screen picked, links or unlinks the storage to it | Opens the router that reaches it |
| Click a machine | Links or unlinks the machine | Opens the router that reaches it |
| Click the air | Opens the screen | Shows the mode's hint |
| Shift + mouse wheel | Goes to **Open** | Goes to **Link** |
| Shift + click two blocks | Marks the area corners | — |

## On the screen (Link mode)

| Part | How to use it |
| --- | --- |
| **Chosen router** | The name, the position and the slots used (for example **7/32 machines**). If the router doesn't link yet (no Range Upgrade), the reason shows here. |
| **Link / Unlink** | In the header. On **Unlink**, the clicks and the area button take machines out of the router instead of putting them in. |
| **Preview** | The marked area seen from above, with the corners and the router's machines inside it. |
| **Link area** | Links the area's machines the router reaches, nearest first, up to the tier's limit. On **Unlink**, it becomes **Unlink N** and takes out the ones inside. The result shows below the preview. |
| **Clear** | Erases the corners. |

Drag the edge or the corner to make the screen bigger; the preview grows.

The area can be up to 262,144 blocks (for example 64 × 64 × 64), and you need to be within 64
blocks of it.

## The Open mode list

When more than one router reaches the machine, the list shows a row per router: its name, whether it is
**attached** to the machine or reaches it by **range**, and the distance. Click a row to open it. Only
routers within your reach that you can touch are listed.

## Linking storages to a Screen

Click a [Signal Screen](signal-screen.md) with the Linker: it becomes the picked one and the action bar shows how many storages it already has. Then click a mod **Chest, Battery or Tank** to link it to the Screen; click it again to unlink. There is no limit on distance or dimension. You must be allowed to edit the Screen. Once linked, the Screen shows the storage's contents live; if its chunk is unloaded, the Screen keeps the last number, dimmed.

## Example: link the machines of a room

| Step | What to do |
| --- | --- |
| **1** | Put the Range Upgrade in a router and click it with the Linker. |
| **2** | Holding Shift, click two corners of the room. |
| **3** | Click the air to open the screen and **Link area**: the machines within range join, up to the tier's limit. |
| **4** | In **Open** mode, click one of them to set up its face in the router. |

## Recipe

<RecipeFor id="wirelessautomate:linker" />
''')

# =====================================================================================
# Configurador
# =====================================================================================
page('configurator.md', front('Configurador', 'wirelessautomate:configurator', 8, item_ids=['wirelessautomate:configurator']) + '''
# Configurador

<ItemImage id="wirelessautomate:configurator" scale="2" float="left" />

Copia a configuração de um roteador e cola em outro.

<br clear="all" />

## Ficha

| | |
| --- | --- |
| **Copia** | Faces, filtros, prioridades, redstone e a rede de cada face. |
| **Guarda** | Uma cópia só, no próprio item. O tooltip mostra o que está copiado e os comandos. |
| **Tipo colado** | Todos (padrão), Itens, Fluidos, Energia, Químicos (com o Mekanism) ou Source (com o Ars Nouveau). |

## Comandos

| Gesto | O que faz |
| --- | --- |
| Shift + clique num roteador | Copia |
| Clique num roteador | Cola nele |
| Shift + clique num bloco sem roteador | Limpa a varinha |
| Shift + roda do mouse | Troca o tipo colado |
| Segurar Alt esquerdo | Abre a roda |

## A roda

Com o Configurador na mão, **segure Alt esquerdo** (a tecla muda em Controles, na categoria Wireless
Automate): abre uma roda no meio da tela com o **tipo colado**. A opção de agora fica com a borda
laranja e o centro mostra o que faz a fatia sob o mouse.

| | |
| --- | --- |
| **Soltar a tecla** | Escolhe a fatia sob o mouse e fecha. |
| **Clique** | Escolhe sem fechar. |
| **Soltar no centro ou Esc** | Fecha sem mudar nada. |

Copiar sempre copia tudo; o **tipo colado** escolhe o que vai para o roteador. Em **Todos**, todas as
abas. Num tipo só, apenas as faces (com as redes delas) daquela aba: as outras abas do roteador ficam como
estavam. Por exemplo, copie um roteador configurado só para fluidos, passe para **Fluidos** e cole
nos outros sem mexer nos itens nem na energia deles.

## Bom saber

| | |
| --- | --- |
| **Orientação** | A cópia é relativa ao roteador: funciona com ele preso em qualquer face. |
| **Redes** | A rede de uma face só é colada se você puder usá-la, e só sai de uma rede que você gerencia (a sua, ou qualquer uma se for operador); senão, a face fica com a de antes. Uma face sem rede na cópia não mexe na rede do destino. A rede vale para as faces da máquina presa; as máquinas do Upgrade de Alcance ficam como estão. |
| **Muitos de uma vez** | Para colar em vários roteadores, use a seleção do [Tablet](network-tablet.md). |

## Receita

<RecipeFor id="wirelessautomate:configurator" />
''', front('Configurator', 'wirelessautomate:configurator', 8, item_ids=['wirelessautomate:configurator']) + '''
# Configurator

<ItemImage id="wirelessautomate:configurator" scale="2" float="left" />

Copies a router's configuration and pastes it on another.

<br clear="all" />

## Spec sheet

| | |
| --- | --- |
| **Copies** | Faces, filters, priorities, redstone and each face's network. |
| **Holds** | A single copy, in the item itself. The tooltip shows what's copied and the actions. |
| **Pasted type** | All (default), Items, Fluids, Energy, Chemicals (with Mekanism) or Source (with Ars Nouveau). |

## Actions

| Action | What it does |
| --- | --- |
| Shift + click a router | Copy |
| Click a router | Paste on it |
| Shift + click a block without a router | Clear the wand |
| Shift + mouse wheel | Change the pasted type |
| Hold Left Alt | Open the wheel |

## The wheel

With the Configurator in hand, **hold Left Alt** (change the key in Controls, under Wireless
Automate): a wheel opens in the middle of the screen with the **pasted type**. The current choice has an
orange border and the center tells what the slice under the mouse does.

| | |
| --- | --- |
| **Release the key** | Picks the slice under the mouse and closes. |
| **Click** | Picks without closing. |
| **Release in the center or Esc** | Closes without changing anything. |

Copying always copies everything; the **pasted type** picks what goes to the router. On **All**,
every tab. On a single type, only that tab's faces (with their networks): the router's other tabs stay as they
were. For example, copy a router set up only for fluids, switch to **Fluids** and paste on the others
without touching their items or energy.

## Good to know

| | |
| --- | --- |
| **Orientation** | The copy is relative to the router: it works with the router on any face. |
| **Networks** | A face's network is only pasted if you can use it, and a face only leaves a network you manage (your own, or any if you are an operator); otherwise the face keeps its own. A face with no network in the copy leaves the target's network alone. The network applies to the attached machine's faces; Range Upgrade machines stay as they are. |
| **Many at once** | To paste on several routers, use the [Tablet](network-tablet.md) selection. |

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
| **Lista** | Todos os nós, com busca, filtros rápidos e o painel do nó escolhido (veja abaixo). O seletor **Roteadores · Máquinas · Grade**, embaixo da busca, mostra a lista pelos roteadores (uma linha para cada um, com a máquina presa embaixo do nome, abrindo em árvore com as máquinas dele), por todas as máquinas, agrupadas pelo bloco, ou na **Grade**: uma linha por roteador e uma coluna por tipo, com uma tarja na cor do modo para cada face que faz algo, mais o estado e a rede; o clique numa célula abre o nó na aba daquele tipo. O botão **! N** na ponta filtra os nós com problema. **Selecionar** marca roteadores, grupos e máquinas para colar um perfil ou mudar de rede de uma vez. Um nó com faces em várias redes mostra "faces em N redes"; passe o mouse para ver quais. Para dar nome a um roteador ou à máquina presa, clique no ✎ da linha (com o mouse em cima) ou dê dois cliques no nome; Enter salva e Esc desiste. |
| **Fluxos** | Quanto entra e sai de cada tipo e de cada rede, o saldo dos Armazéns e o que precisa de atenção (veja abaixo). |
| **Mapa** | Vista de cima com cores por status, com todos os roteadores que você vê (sem depender da busca nem dos filtros da Lista). Arraste para mover o mapa (**Centralizar** volta para você), role para dar zoom e clique num ponto para escolher o nó. O roteador escolhido mostra as máquinas vinculadas a ele, cada uma ligada a ele por uma linha (veja abaixo). |
| **Redes** | Criar redes, cor, membros e privacidade, e os grupos de redes, que pausam e retomam um sistema inteiro de uma vez (veja abaixo). |

O cabeçalho do Tablet conta a sua base: nós, máquinas e redes.

Se uma rede for apagada, as faces que estavam nela ficam sem rede e o nó mostra o aviso **faces sem rede: rede removida**: escolha outra rede para elas.

## Redes e grupos

A aba **Redes** é uma lista só. Cada grupo de redes é uma seção, com **▾** para recolher e **Pausar** no cabeçalho (**pausado** quando está); as redes fora de grupo vêm no fim, em **Sem grupo**. Uma rede em dois grupos aparece nas duas seções, e a dica diz **Também em …**.

- Clique numa rede para ver à direita a cor, se é pública ou privada, usá-la e removê-la.
- Numa rede privada que é sua, a coluna mostra os **convidados** e os **times do FTB Teams** (só com o mod). Digite um nome para ver sugestões, escolha com as setas e Enter (Tab completa) e use o ✕ duas vezes para tirar. Só aparecem jogadores que já entraram no servidor, e um time vale por inteiro: quem entra ou sai dele ganha ou perde o acesso sozinho.
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
| **List** | Every node, with search, quick filters and the panel of the chosen node (see below). The **Routers · Machines · Grid** switch, below the search, shows the list by router (one row each, with the attached machine under the name, opening as a tree with its machines), by every machine, grouped by block, or as the **Grid**: one row per router and one column per type, with a stripe in the mode's color for every face that does something, plus the state and the network; clicking a cell opens the node on that type's tab. The **! N** button at the end filters the nodes with problems. **Select** marks routers, groups and machines to paste a profile or change networks at once. A node with faces on several networks shows "faces in N networks"; hover to see which. To name a router or its attached machine, click the ✎ on its row (with the mouse over it) or double-click the name; Enter saves and Esc cancels. |
| **Flows** | How much of each type comes in and goes out, per type and per network, the balance of Storage faces and what needs attention (see below). |
| **Map** | Top-down view colored by status, with every router you can see (independent of the List's search and filters). Drag to move the map (**Center** brings it back to you), scroll to zoom and click a point to choose the node. The chosen router shows the machines linked to it, each joined to it by a line (see below). |
| **Networks** | Create networks, color, members and privacy, and the network groups, which pause and resume a whole system at once (see below). |

The Tablet's header counts your base: nodes, machines and networks.

If a network is deleted, the faces that were on it are left without a network and the node shows the warning **faces without network: network removed**: pick another network for them.

## Networks and groups

The **Networks** tab is a single list. Each network group is a section, with **▾** to collapse it and **Pause** on its header (**paused** when it is); networks outside any group come last, under **No group**. A network in two groups shows up in both sections, and the tooltip says **Also in …**.

- Click a network to see its color and whether it is public or private on the right, use it or remove it.
- On a private network you own, the column shows the **guests** and the **FTB Teams teams** (only with that mod). Type a name to see suggestions, pick with the arrows and Enter (Tab completes) and click the ✕ twice to remove. Only players who already joined the server show up, and a team counts as a whole: whoever joins or leaves it gains or loses access on their own.
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

## Upgrades

O Baú tem **dois slots de upgrade**, à esquerda da lista. Cada upgrade entra uma vez só. Clique
num slot com o upgrade no cursor para pôr; clique na aba ao lado do slot ocupado para abrir o painel
dele, à direita da lista. Clique de novo na aba do painel aberto para escondê-lo; ele fica escondido,
mesmo em outros Baús, até você clicar numa aba.

| Upgrade | Receita |
| --- | --- |
| ''' + item('crafting_upgrade') + ''' **Upgrade de Mesa de Trabalho** | Mesa de trabalho + Cristal de Sinal. |
| ''' + item('compacting_upgrade') + ''' **Upgrade de Compactação** | Pistão + Cristal de Sinal + bloco de ferro (sem forma). |

### Mesa de Trabalho

Uma mesa 3×3 dentro do Baú. Monte a receita na grade e pegue o resultado.

- A grade **se repõe sozinha** com o que o Baú tem.
- **Shift** + clique no resultado: repete a receita até uma pilha e manda para o inventário.
- **Devolver**: a grade volta para o Baú.
- No **JEI**, o botão **+** monta a receita puxando do Baú e depois do inventário.

### Compactação

Junta e separa sozinha as cadeias de itens: pepita, lingote, bloco; ou pedregulho até o 9x do
AllTheCompressed. Só entra a cadeia que a mesa de trabalho também desfaz. Itens com encantamento ou
nome não compactam.

1. Pôr o upgrade não muda nada: o modo começa **Desligada**.
2. No painel, escolha o modo para todas as cadeias: **Compactar** (a forma mais alta) ou **Descompactar** (a base).
3. Para uma cadeia só, use as setas ou a rodinha na linha dela; clique no ícone para escolher a forma numa lista. **Global** volta ao modo geral.

O alvo vale para os dois lados: o que está **abaixo** dele é compactado até ele, e o que está **acima** é
descompactado até ele. Com o alvo em lingote, pepitas viram lingotes e blocos viram lingotes.

- A sobra que não fecha um fator fica na forma de baixo: 10 lingotes com alvo em bloco dão 1 bloco e 1 lingote.
- Descompactar converte **só o que cabe** no Baú; o resto fica na forma alta, sem perder nada.
- O roteador vê só a forma guardada: para tirar lingotes, deixe o alvo da cadeia em lingote.
- O limite de **Estoque** de um item da cadeia conta a cadeia inteira (11 blocos e 1 lingote valem 100 lingotes).
- O mesmo material de mods diferentes (a prata crua de dois mods) é uma linha só, com o nome do mod embaixo: clique nele para escolher em qual mod a Compactação junta tudo.
- A linha de uma cadeia com regra própria (alvo ou mod escolhido) fica na lista, apagada, mesmo sem nenhum item no Baú: a regra continua valendo. Clique na etiqueta Própria para voltar a Global: a regra sai e a linha some.
- Com a Compactação, a Mesa desce uma forma quando falta o item exato.

## Quebrar e levar

Quebre o Baú: o item leva **todo o conteúdo e o filtro**, e o tooltip mostra o total e os tipos.
Coloque de novo e está tudo lá. Cheio, ele sempre vira item: no criativo, sem picareta ou numa
explosão, nada se perde.

Os **upgrades e os itens da grade** da Mesa também vão no item, mesmo com o Baú vazio, e voltam quando
ele é colocado. Se o bloco sumir sem virar item (um comando, outro mod), eles caem no chão.

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

## Upgrades

The chest has **two upgrade slots**, to the left of the list. Each upgrade fits once. Click a slot
with the upgrade on the cursor to place it; click the tab next to the filled slot to open its panel,
to the right of the list. Click the open panel's tab again to hide it; it stays hidden, even in other
chests, until you click a tab.

| Upgrade | Recipe |
| --- | --- |
| ''' + item('crafting_upgrade') + ''' **Crafting Upgrade** | Crafting table + Signal Crystal. |
| ''' + item('compacting_upgrade') + ''' **Compacting Upgrade** | Piston + Signal Crystal + iron block (shapeless). |

### Crafting

A 3×3 crafting table inside the chest. Set up the recipe in the grid and take the result.

- The grid **refills itself** from what the chest has.
- **Shift** + click the result: repeats the recipe up to one stack and sends it to your inventory.
- **Return**: the grid goes back into the chest.
- In **JEI**, the **+** button sets up the recipe from the chest first, then your inventory.

### Compacting

It packs and unpacks item chains by itself: nugget, ingot, block; or cobblestone up to
AllTheCompressed's 9x. A chain only counts if the crafting table can also undo it. Enchanted or
named items don't compact.

1. Placing the upgrade changes nothing: the mode starts **Off**.
2. In the panel, pick the mode for all chains: **Compress** (the highest form) or **Decompress** (the base).
3. For a single chain, use the arrows or the wheel on its row; click the icon to pick the form from a list. **Global** goes back to the general mode.

The target works both ways: what is **below** it is compressed up to it, and what is **above** it is
decompressed down to it. With the target on ingot, nuggets become ingots and blocks become ingots.

- The leftover that doesn't make a full factor stays in the lower form: 10 ingots with the target on block give 1 block and 1 ingot.
- Decompressing converts **only what fits** in the chest; the rest stays in the high form, nothing is lost.
- The router only sees the stored form: to pull ingots, leave the chain's target on ingot.
- The **Stock** limit of a chain item counts the whole chain (11 blocks and 1 ingot are worth 100 ingots).
- The same material from different mods (two mods' raw silver) is a single row, with the mod name under it: click it to choose which mod's items Compacting gathers everything into.
- The row of a chain with its own rule (a target or a chosen mod) stays in the list, dimmed, even with no items in the chest: the rule still applies. Click the Custom tag to go back to Global: the rule is removed and the row disappears.
- With Compacting, the crafting grid steps down one form when the exact item is missing.

## Break and carry

Break the chest: the item takes **all the contents and the filter** with it, and the tooltip shows
the total and the types. Place it again and everything is there. A full chest always becomes an
item: in creative, without a pickaxe or in an explosion, nothing is lost.

The **upgrades and the crafting grid items** also go in the item, even with an empty chest, and come
back when it is placed. If the block disappears without becoming an item (a command, another mod),
they drop on the ground.

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
**Energia**, ela carrega e descarrega bilhões de FE por tick. A capacidade de cada tier é **uma hora** da
geração cheia do [Ressonador](signal-resonator.md) do mesmo tier, e o roteador do tier escoa essa geração com 5% de folga.

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
**Energy** tab, it charges and discharges billions of FE per tick. Each tier's capacity is **one hour** of the
same tier's [Resonator](signal-resonator.md) at full output, and the tier's router carries that output with 5% to spare.

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
| Configurador | Sim: o tipo **Source** na roda e no atalho do mouse. |
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
| Configurator | Yes: the **Source** type on the wheel and the mouse wheel shortcut. |
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

# Tiers do Ressonador na ordem do RouterTier: câmaras (tier + 1), FE por cristal (padrões do CoreMath), minutos que a
# carga do Lapidado ou do Afinado dura a 100% (CoreMath.DEFAULT_LIFE_MINUTES; o Cristal de Sinal dura a metade) e teto
# do Sintonizador em % (constantes do Tuning.ceiling). A geração máxima é câmaras × FE por cristal × 2.
RESONATOR_FE = [216, 6_912, 10_000, 160_000, 6_400_000, 6_700_000, 10_700_000, 268_435_456]
RESONATOR_LIFE = [20, 20, 15, 10, 5, 5, 4, 3]
RESONATOR_CEILING = [85, 88, 90, 92, 94, 95, 96, 98]

# O catalisador da recarga por tier do Núcleo (CatalystRule): redstone ou Pó até o Avançado, só Pó até a Esmeralda e
# o Pó Carregado do próprio tier do Allthemodium para cima.
CATALYST = {
    'pt': ['Redstone ou Pó de Ressonância'] * 2 + ['Pó de Ressonância'] * 2
    + ['Pó Carregado de Allthemodium', 'Pó Carregado de Vibranium', 'Pó Carregado de Unobtainium',
       'Pó Carregado Ultimate'],
    'en': ['Redstone or Resonance Dust'] * 2 + ['Resonance Dust'] * 2
    + ['Charged Allthemodium Dust', 'Charged Vibranium Dust', 'Charged Unobtainium Dust', 'Charged Ultimate Dust'],
}


def resonator_table(lang):
    head = {'pt': '| Tier do Núcleo | Câmaras | Geração máxima (FE/t) | Teto do Sintonizador |',
            'en': '| Core tier | Chambers | Max generation (FE/t) | Tuner ceiling |'}[lang]
    rows = [head, '| --- | --- | --- | --- |']
    for i in range(len(TIERS)):
        top = (i + 1) * RESONATOR_FE[i] * 2
        rows.append(f'| **{tier_name(lang, i)}** | {i + 1} | {num(lang, top)} | {RESONATOR_CEILING[i]}% |')
    return '\n'.join(rows) + '\n\n' + ATM_NOTE[lang]


def resonator_upkeep_table(lang):
    head = {'pt': '| Tier do Núcleo | Cristal que rende cheio | A carga dura | Catalisador da recarga |',
            'en': '| Core tier | Crystal at full yield | A charge lasts | Recharge catalyst |'}[lang]
    rows = [head, '| --- | --- | --- | --- |']
    for i in range(len(TIERS)):
        if i < 2:
            crystal = {'pt': 'Lapidado ou Afinado', 'en': 'Cut or Tuned'}[lang]
        elif i == len(TIERS) - 1:
            crystal = {'pt': 'Afinado Ultimate', 'en': 'Tuned Ultimate'}[lang]
        else:
            crystal = {'pt': f'Afinado {TIERS[i][1]} ou acima', 'en': f'Tuned {TIERS[i][2]} or higher'}[lang]
        rows.append(f'| **{tier_name(lang, i)}** | {crystal} | {RESONATOR_LIFE[i]} min | {CATALYST[lang][i]} |')
    return '\n'.join(rows) + '\n\n' + ATM_NOTE[lang]


RESONATOR_ANNOTATIONS = {
    'pt': dict(CORE='Núcleo de Ressonância: a tela e os cristais',
               ENERGY='Porta de Energia: o roteador tira a energia daqui',
               CRYSTALS='Porta de Cristais: entram cristais e catalisador, saem Opacos e rachados',
               TUNER='Sintonizador (opcional)', YAW='35'),
    'en': dict(CORE='Resonance Core: the screen and the crystals',
               ENERGY='Resonator Energy Port: a router pulls the energy here',
               CRYSTALS='Resonator Crystal Port: crystals and catalyst in, Dull and cracked ones out',
               TUNER='Resonator Tuner (optional)', YAW='35'),
}

TUNED_TIERS = ['elite', 'emerald', 'allthemodium', 'vibranium', 'unobtainium', 'ultimate']
CHARGED_TIERS = ['allthemodium', 'vibranium', 'unobtainium', 'ultimate']
CASCADE = ['signal_alloy_raw', 'signal_alloy_ingot', 'resonance_plate', 'resonance_wire', 'resonance_coil',
           'harmonic_lens', 'phase_circuit', 'phase_matrix', 'field_stabilizer', 'condensed_core']
QUARTZ_ITEMS = (['signal_quartz_ore', 'deepslate_signal_quartz_ore', 'signal_crystal', 'cut_signal_crystal']
                + ['tuned_signal_crystal_' + t for t in TUNED_TIERS] + ['resonance_dust']
                + ['charged_dust_' + t for t in CHARGED_TIERS] + CASCADE + ['alloy_frame']
                + ['resonator_heart_' + t[0] for t in TIERS])
TUNED_ROW = '\n'.join('  ' + item('tuned_signal_crystal_' + t) for t in TUNED_TIERS)
HEART_ROW = '\n'.join('  ' + item('resonator_heart_' + t[0]) for t in TIERS)

page('signal-quartz.md', front('Quartzo de Sinal e cristais', 'wirelessautomate:signal_crystal', 18, item_ids=[
    'wirelessautomate:' + i for i in QUARTZ_ITEMS]) + '''
# Quartzo de Sinal e cristais

<Row gap="8">
  ''' + item('signal_quartz_ore') + '''
  ''' + item('deepslate_signal_quartz_ore') + '''
  ''' + item('signal_crystal') + '''
  ''' + item('cut_signal_crystal') + '''
  ''' + item('tuned_signal_crystal_elite') + '''
  ''' + item('resonance_dust') + '''
</Row>

Os cristais de quartzo são o combustível do [Ressonador de Sinal](signal-resonator.md), o gerador de energia do mod.
Esta página mostra de onde eles vêm, como cuidar deles e as peças que o Ressonador pede para subir de tier.

## O minério

O **Minério de Quartzo de Sinal** aparece no Overworld, em pedra e em ardósia (a versão de ardósia
é mais dura). Fica em veios pequenos, mais comum perto de **y = −16**. Quebre com **picareta de ferro** ou melhor:

| Ferramenta | O que cai |
| --- | --- |
| Picareta de ferro ou melhor | 1 a 3 **Cristais de Sinal** e um pouco de experiência. **Fortuna** aumenta a quantidade. |
| Toque de Seda | O próprio minério. |

## Os cristais

| Cristal | De onde vem |
| --- | --- |
| ''' + item('signal_crystal') + ''' **Cristal de Sinal** | Do minério. |
| ''' + item('cut_signal_crystal') + ''' **Cristal Lapidado** | Bancada: quatro Cristais de Sinal em cruz em volta de um Fragmento de Ametista. |
| ''' + item('tuned_signal_crystal_elite') + ''' **Cristal Afinado** | Bancada: um Lapidado com o material do tier (mais abaixo). Um por tier, do Elite ao Ultimate. |

Quanto cada um rende numa câmara depende do tier do Núcleo:

| Cristal | Núcleo Básico ou Avançado | Núcleo Elite ou acima |
| --- | --- | --- |
| **Cristal de Sinal** | 1× | 1/4× |
| **Lapidado** | 2× | 1/2× |
| **Afinado** do tier do Núcleo ou acima | 2× | 2× |
| **Afinado** de tier abaixo | 2× | 1/2× |

O Lapidado e os Afinados duram o dobro do Cristal de Sinal. Quanto maior o tier do Núcleo, menos a carga dura: a
tabela da página do [Ressonador de Sinal](signal-resonator.md) mostra o tempo de cada tier.

As receitas dos cristais só aceitam cristais **cheios** e sem rachadura: cristais gastos não valem.

<RecipeFor id="wirelessautomate:cut_signal_crystal" />

## Afinar um cristal

<Row gap="8">
''' + TUNED_ROW + '''
</Row>

Do **Núcleo Elite** para cima, só o **Cristal Afinado** do tier do Núcleo (ou de um tier acima) rende cheio. Para
afinar, junte na bancada, em qualquer posição, um **Lapidado** (ou um Afinado de tier abaixo, para reafinar) e o
material do tier:

| Afinado | Material, além do cristal |
| --- | --- |
| ''' + item('tuned_signal_crystal_elite') + ''' **Elite** | 2 lingotes de netherite |
| ''' + item('tuned_signal_crystal_emerald') + ''' **Esmeralda** | 2 blocos de esmeralda + 1 Nether Star |
| ''' + item('tuned_signal_crystal_allthemodium') + ''' **Allthemodium¹** | 2 lingotes de Allthemodium + 1 Nether Star |
| ''' + item('tuned_signal_crystal_vibranium') + ''' **Vibranium¹** | 2 lingotes de Vibranium + 1 lingote de liga Vibranium-Allthemodium |
| ''' + item('tuned_signal_crystal_unobtainium') + ''' **Unobtainium¹** | 2 lingotes de Unobtainium + 1 lingote de liga Unobtainium-Vibranium |
| ''' + item('tuned_signal_crystal_ultimate') + ''' **Ultimate** | Com o Allthemodium: 2 lingotes de liga Unobtainium-Vibranium + 1 Nether Star. Sem ele: 1 bloco de netherite + 1 Nether Star + 1 Fragmento de Eco |

''' + ATM_NOTE['pt'] + '''

O cristal afinado sai cheio, mas com a **integridade** que o cristal de entrada tinha: afinar não renova o cristal.

<RecipeFor id="wirelessautomate:tuned_signal_crystal_elite" />

## Carga e cristal Opaco

A **barra do item** mostra quanto falta para o cristal acabar: ela só aparece depois que o cristal começa a
gastar. Dentro do Núcleo, a carga cai enquanto ele gera energia.

Sem carga, o cristal fica **Opaco**: é o mesmo item, só que apagado. Um Opaco não gera nada, mas dá para
**recarregar**:

1. Deixe o Opaco numa câmara do Núcleo.
2. Ponha o **catalisador** do tier no Núcleo (clique com ele na mão, pela tela ou pela Porta de Cristais):
   **redstone** ou **Pó de Ressonância** no Básico e no Avançado, só **Pó de Ressonância** no Elite e na Esmeralda,
   e o **Pó Carregado** do tier do Allthemodium para cima.
3. O Núcleo recarrega **todos os Opacos de uma vez**: leva 30 segundos, gasta 1 catalisador por cristal e cada um volta cheio. Com menos catalisador que Opacos, recarregam os primeiros e os outros esperam o próximo ciclo.

O slot do catalisador guarda um tipo por vez: para trocar de catalisador, espere o que está lá acabar ou tire-o
pela tela. A Porta de Cristais só devolve os Opacos quando o Núcleo está sem catalisador; com ele, o Opaco fica lá
dentro esperando o catalisador e o ciclo de recarga.

## Integridade e cristal rachado

Cada cristal aguenta **8 cargas**: a de fábrica e mais 7 recargas. Cada recarga gasta 1 de **integridade** (a dica
do item mostra "Integridade 5 de 8", e a tela do Núcleo, uma barrinha sob cada câmara). Quando a integridade acaba,
a recarga não enche o cristal: ele **racha**.

Um **cristal rachado** não gera, não recarrega e não entra na câmara. A Porta de Cristais sempre o devolve. Para
**refazer**, junte na bancada, em qualquer posição, o rachado e **4 Cristais de Sinal cheios**: sai o mesmo cristal,
cheio e com a integridade inteira. Vale para os três tipos; num Afinado, o tier fica.

No Núcleo Ultimate, um Afinado Ultimate rachado também pode ser queimado no **Eco**, como na página do
[Ressonador de Sinal](signal-resonator.md).

## Pó de Ressonância e Pó Carregado

<Row gap="8">
  ''' + item('resonance_dust') + '''
''' + '\n'.join('  ' + item('charged_dust_' + t) for t in CHARGED_TIERS) + '''
</Row>

O **Pó de Ressonância** é o catalisador do Núcleo Elite e do Esmeralda (e vale também no Básico e no Avançado).
Na bancada, sem forma: **1 redstone + 1 pó de glowstone + 1 Cristal de Sinal cheio** rendem 4.

<RecipeFor id="wirelessautomate:resonance_dust" />

Do Allthemodium para cima, cada Núcleo pede o **Pó Carregado** do tier dele. Na bancada, sem forma:
**8 Pós de Ressonância + 1 lingote do tier** rendem 8.

| Pó Carregado | Lingote |
| --- | --- |
| ''' + item('charged_dust_allthemodium') + ''' **Allthemodium¹** | 1 lingote de Allthemodium |
| ''' + item('charged_dust_vibranium') + ''' **Vibranium¹** | 1 lingote de Vibranium |
| ''' + item('charged_dust_unobtainium') + ''' **Unobtainium¹** | 1 lingote de Unobtainium |
| ''' + item('charged_dust_ultimate') + ''' **Ultimate** | Com o Allthemodium: 1 lingote de liga Unobtainium-Vibranium. Sem ele: 1 lingote de netherite |

''' + ATM_NOTE['pt'] + '''

## As peças do Coração

Para montar o Núcleo e para subir o tier dele, o Ressonador pede um **Coração do Ressonador** (mais abaixo). Os
Corações saem desta cadeia de peças, feitas na ordem:

| Peça | Receita | Rende |
| --- | --- | --- |
| ''' + item('signal_alloy_raw') + ''' **1. Liga de Sinal crua** | Bancada, sem forma: 2 Cristais de Sinal cheios + 2 lingotes de ferro + 1 redstone | 2 |
| ''' + item('signal_alloy_ingot') + ''' **2. Lingote de Liga de Sinal** | **Alto-forno**: 1 Liga de Sinal crua | 1 |
| ''' + item('resonance_plate') + ''' **3. Placa de Ressonância** | 3 Lingotes de Liga de Sinal em linha | 1 |
| ''' + item('resonance_wire') + ''' **4. Fio de Ressonância** | 1 Lingote de Liga de Sinal entre 2 lingotes de cobre, com 1 lingote de ouro embaixo | 4 |
| ''' + item('resonance_coil') + ''' **5. Bobina de Ressonância** | 8 Fios em volta de 1 bloco de ferro | 1 |
| ''' + item('harmonic_lens') + ''' **6. Lente Harmônica** | Bancada, sem forma: 1 Cristal Lapidado cheio + 2 diamantes + 4 Vidros de Ressonância | 1 |
| ''' + item('phase_circuit') + ''' **7. Circuito de Fase** | 1 Placa no centro, 2 blocos de redstone, 2 lingotes de ouro e 4 quartzos do Nether | 2 |
| ''' + item('phase_matrix') + ''' **8. Matriz de Fase** | 1 Lente no centro, 4 Bobinas nos cantos, 2 Circuitos e 2 Placas | 1 |
| ''' + item('field_stabilizer') + ''' **9. Estabilizador de Campo** | 4 Matrizes + 1 bloco de diamante + 2 obsidianas chorosas + 1 olho do ender | 1 |
| ''' + item('condensed_core') + ''' **10. Núcleo Condensado** | 4 Estabilizadores + 4 blocos de netherite + 1 Nether Star no centro | 1 |

O JEI mostra a posição de cada ingrediente.

<RecipeFor id="wirelessautomate:signal_alloy_ingot" />
<RecipeFor id="wirelessautomate:phase_matrix" />

## O Coração do Ressonador

<Row gap="8">
''' + HEART_ROW + '''
</Row>

Há um **Coração do Ressonador** por tier. O Básico vai na receita do Núcleo de Ressonância; os outros são gastos
ao subir o Núcleo com o Cartão de Upgrade (veja o [Ressonador de Sinal](signal-resonator.md)). Todos são feitos na
bancada, sem forma:

| Coração | Receita |
| --- | --- |
| ''' + item('resonator_heart_basic') + ''' **Básico** | 1 Matriz de Fase + 4 blocos de redstone |
| ''' + item('resonator_heart_advanced') + ''' **Avançado** | 2 Matrizes de Fase + 4 blocos de ouro |
| ''' + item('resonator_heart_elite') + ''' **Elite** | 3 Matrizes de Fase + 4 lingotes de netherite |
| ''' + item('resonator_heart_emerald') + ''' **Esmeralda** | 1 Estabilizador de Campo + 7 blocos de esmeralda + 1 Nether Star; com o Modern Industrialization: 1 Estabilizador de Campo + 6 blocos de esmeralda + 1 Nether Star + 1 Processing Unit |
| ''' + item('resonator_heart_allthemodium') + ''' **Allthemodium¹** | 2 Estabilizadores de Campo + 4 blocos de Allthemodium + 2 Nether Stars; com o MEGA Cells: 1 Estabilizador de Campo + 4 blocos de Allthemodium + 2 Nether Stars + 2 Cell Components 16M |
| ''' + item('resonator_heart_vibranium') + ''' **Vibranium¹** | 1 Núcleo Condensado + 4 blocos de Vibranium + 2 Nether Stars; com o Modern Industrialization e o Draconic Evolution: 1 Núcleo Condensado + 4 blocos de Vibranium + 1 Nether Star + 1 Processing Unit + 2 Chaotic Cores |
| ''' + item('resonator_heart_unobtainium') + ''' **Unobtainium¹** | 2 Núcleos Condensados + 4 blocos de Unobtainium + 3 Nether Stars; com o MEGA Cells e o Draconic Evolution: 1 Núcleo Condensado + 4 blocos de Unobtainium + 2 Nether Stars + 1 Cell Component 16M + 1 Chaotic Core |
| ''' + item('resonator_heart_ultimate') + ''' **Ultimate** | 3 Núcleos Condensados + 1 Moldura de Liga + 5 Nether Stars; com o MEGA Cells, o Draconic Evolution e o Modern Industrialization: 3 Cell Components 256M + 1 Cell Component 64M + 1 Quantum Circuit + 1 Chaotic Core + 1 Awakened Core + 1 Moldura de Liga + 1 Nether Star |

''' + ATM_NOTE['pt'] + '''

A ''' + item('alloy_frame') + ''' **Moldura de Liga** do Coração Ultimate é um anel de 8 blocos em volta de
1 Nether Star: com o Allthemodium, blocos de liga Unobtainium-Vibranium; sem ele, blocos de netherite.

<RecipeFor id="wirelessautomate:resonator_heart_basic" />
<RecipeFor id="wirelessautomate:alloy_frame" />

Os números são os padrões; o JEI mostra os do seu pack.

Veja como montar o gerador em [Ressonador de Sinal](signal-resonator.md).
''', front('Signal Quartz and crystals', 'wirelessautomate:signal_crystal', 18, item_ids=[
    'wirelessautomate:' + i for i in QUARTZ_ITEMS]) + '''
# Signal Quartz and crystals

<Row gap="8">
  ''' + item('signal_quartz_ore') + '''
  ''' + item('deepslate_signal_quartz_ore') + '''
  ''' + item('signal_crystal') + '''
  ''' + item('cut_signal_crystal') + '''
  ''' + item('tuned_signal_crystal_elite') + '''
  ''' + item('resonance_dust') + '''
</Row>

Quartz crystals are the fuel of the [Signal Resonator](signal-resonator.md), the mod's power generator. This page
shows where they come from, how to look after them and the parts the Resonator needs to go up in tier.

## The ore

**Signal Quartz Ore** generates in the Overworld, in stone and in deepslate (the deepslate version is
harder to mine). It sits in small veins, most common near **y = −16**. Mine it with an **iron pickaxe** or better:

| Tool | Drop |
| --- | --- |
| Iron pickaxe or better | 1 to 3 **Signal Crystals** and a bit of experience. **Fortune** raises the amount. |
| Silk Touch | The ore itself. |

## The crystals

| Crystal | Where it comes from |
| --- | --- |
| ''' + item('signal_crystal') + ''' **Signal Crystal** | The ore. |
| ''' + item('cut_signal_crystal') + ''' **Cut Signal Crystal** | Crafting table: four Signal Crystals in a cross around an Amethyst Shard. |
| ''' + item('tuned_signal_crystal_elite') + ''' **Tuned Signal Crystal** | Crafting table: a Cut crystal with the tier's material (below). One per tier, from Elite to Ultimate. |

How much each one yields in a chamber depends on the Core's tier:

| Crystal | Basic or Advanced Core | Elite Core or higher |
| --- | --- | --- |
| **Signal Crystal** | 1× | 1/4× |
| **Cut** | 2× | 1/2× |
| **Tuned** for the Core's tier or higher | 2× | 2× |
| **Tuned** for a lower tier | 2× | 1/2× |

Cut and Tuned crystals last twice as long as a Signal Crystal. The higher the Core's tier, the shorter a charge
lasts: the table on the [Signal Resonator](signal-resonator.md) page shows the time for each tier.

The crystal recipes only accept **full**, uncracked crystals: spent ones don't count.

<RecipeFor id="wirelessautomate:cut_signal_crystal" />

## Tuning a crystal

<Row gap="8">
''' + TUNED_ROW + '''
</Row>

From the **Elite Core** up, only the **Tuned Signal Crystal** for the Core's tier (or a tier above) yields in full.
To tune one, put a **Cut** crystal (or a Tuned one of a lower tier, to retune it) and the tier's material anywhere on
a crafting table:

| Tuned | Material, besides the crystal |
| --- | --- |
| ''' + item('tuned_signal_crystal_elite') + ''' **Elite** | 2 netherite ingots |
| ''' + item('tuned_signal_crystal_emerald') + ''' **Emerald** | 2 emerald blocks + 1 Nether Star |
| ''' + item('tuned_signal_crystal_allthemodium') + ''' **Allthemodium¹** | 2 Allthemodium ingots + 1 Nether Star |
| ''' + item('tuned_signal_crystal_vibranium') + ''' **Vibranium¹** | 2 Vibranium ingots + 1 Vibranium-Allthemodium alloy ingot |
| ''' + item('tuned_signal_crystal_unobtainium') + ''' **Unobtainium¹** | 2 Unobtainium ingots + 1 Unobtainium-Vibranium alloy ingot |
| ''' + item('tuned_signal_crystal_ultimate') + ''' **Ultimate** | With Allthemodium: 2 Unobtainium-Vibranium alloy ingots + 1 Nether Star. Without it: 1 netherite block + 1 Nether Star + 1 Echo Shard |

''' + ATM_NOTE['en'] + '''

The tuned crystal comes out full, but with the **integrity** the input crystal had: tuning doesn't renew the crystal.

<RecipeFor id="wirelessautomate:tuned_signal_crystal_elite" />

## Charge and Dull crystals

The item's **durability bar** shows how much is left before the crystal runs out: it only appears once the
crystal starts to wear. Inside the Core, the charge drops while it generates power.

With no charge left, the crystal turns **Dull**: it's the same item, just dimmed. A Dull crystal generates
nothing, but you can **recharge** it:

1. Leave the Dull crystal in a Core chamber.
2. Put the tier's **catalyst** in the Core (click with it in hand, through the screen or through the Crystal Port):
   **redstone** or **Resonance Dust** on Basic and Advanced, only **Resonance Dust** on Elite and Emerald, and the
   tier's **Charged Dust** from Allthemodium up.
3. The Core recharges **all Dull crystals at once**: it takes 30 seconds, uses 1 catalyst per crystal, and each one comes back full. With less catalyst than Dull crystals, the first ones recharge and the rest wait for the next cycle.

The catalyst slot holds one kind at a time: to switch catalysts, wait for the current one to run out or take it out
through the screen. The Crystal Port only hands the Dull crystals back when the Core has no catalyst; with catalyst
in it, the Dull crystal stays inside, waiting for the catalyst and the recharge cycle.

## Integrity and cracked crystals

Each crystal takes **8 charges**: the one it is made with and 7 recharges. Each recharge uses 1 **integrity** (the
item's tooltip shows "Integrity 5 of 8", and the Core's screen a small bar under each chamber). When integrity runs
out, the recharge doesn't fill the crystal: it **cracks**.

A **cracked crystal** doesn't generate, doesn't recharge and doesn't go into a chamber. The Crystal Port always hands
it back. To **recast** it, put the cracked crystal and **4 full Signal Crystals** anywhere on a crafting table: out
comes the same crystal, full and with its integrity restored. It works for all three kinds; a Tuned one keeps its tier.

In an Ultimate Core, a cracked Tuned Ultimate crystal can also be burned in the **Echo**, as on the
[Signal Resonator](signal-resonator.md) page.

## Resonance Dust and Charged Dust

<Row gap="8">
  ''' + item('resonance_dust') + '''
''' + '\n'.join('  ' + item('charged_dust_' + t) for t in CHARGED_TIERS) + '''
</Row>

**Resonance Dust** is the catalyst of the Elite and Emerald Cores (and it works on Basic and Advanced too). On a
crafting table, shapeless: **1 redstone + 1 glowstone dust + 1 full Signal Crystal** make 4.

<RecipeFor id="wirelessautomate:resonance_dust" />

From Allthemodium up, each Core takes the **Charged Dust** of its own tier. On a crafting table, shapeless:
**8 Resonance Dust + 1 ingot of the tier** make 8.

| Charged Dust | Ingot |
| --- | --- |
| ''' + item('charged_dust_allthemodium') + ''' **Allthemodium¹** | 1 Allthemodium ingot |
| ''' + item('charged_dust_vibranium') + ''' **Vibranium¹** | 1 Vibranium ingot |
| ''' + item('charged_dust_unobtainium') + ''' **Unobtainium¹** | 1 Unobtainium ingot |
| ''' + item('charged_dust_ultimate') + ''' **Ultimate** | With Allthemodium: 1 Unobtainium-Vibranium alloy ingot. Without it: 1 netherite ingot |

''' + ATM_NOTE['en'] + '''

## The Heart's parts

To build the Core and to raise its tier, the Resonator takes a **Resonator Heart** (below). The Hearts come from
this chain of parts, made in order:

| Part | Recipe | Makes |
| --- | --- | --- |
| ''' + item('signal_alloy_raw') + ''' **1. Raw Signal Alloy** | Crafting table, shapeless: 2 full Signal Crystals + 2 iron ingots + 1 redstone | 2 |
| ''' + item('signal_alloy_ingot') + ''' **2. Signal Alloy Ingot** | **Blast furnace**: 1 Raw Signal Alloy | 1 |
| ''' + item('resonance_plate') + ''' **3. Resonance Plate** | 3 Signal Alloy Ingots in a row | 1 |
| ''' + item('resonance_wire') + ''' **4. Resonance Wire** | 1 Signal Alloy Ingot between 2 copper ingots, with 1 gold ingot below | 4 |
| ''' + item('resonance_coil') + ''' **5. Resonance Coil** | 8 Wires around 1 iron block | 1 |
| ''' + item('harmonic_lens') + ''' **6. Harmonic Lens** | Crafting table, shapeless: 1 full Cut Signal Crystal + 2 diamonds + 4 Resonance Glass | 1 |
| ''' + item('phase_circuit') + ''' **7. Phase Circuit** | 1 Plate in the center, 2 redstone blocks, 2 gold ingots and 4 Nether quartz | 2 |
| ''' + item('phase_matrix') + ''' **8. Phase Matrix** | 1 Lens in the center, 4 Coils in the corners, 2 Circuits and 2 Plates | 1 |
| ''' + item('field_stabilizer') + ''' **9. Field Stabilizer** | 4 Matrices + 1 diamond block + 2 crying obsidian + 1 eye of ender | 1 |
| ''' + item('condensed_core') + ''' **10. Condensed Core** | 4 Stabilizers + 4 netherite blocks + 1 Nether Star in the center | 1 |

JEI shows where each ingredient goes.

<RecipeFor id="wirelessautomate:signal_alloy_ingot" />
<RecipeFor id="wirelessautomate:phase_matrix" />

## The Resonator Heart

<Row gap="8">
''' + HEART_ROW + '''
</Row>

There is one **Resonator Heart** per tier. The Basic one goes into the Resonance Core's recipe; the others are used
up when you raise the Core with an Upgrade Card (see the [Signal Resonator](signal-resonator.md)). All of them are
made on a crafting table, shapeless:

| Heart | Recipe |
| --- | --- |
| ''' + item('resonator_heart_basic') + ''' **Basic** | 1 Phase Matrix + 4 redstone blocks |
| ''' + item('resonator_heart_advanced') + ''' **Advanced** | 2 Phase Matrices + 4 gold blocks |
| ''' + item('resonator_heart_elite') + ''' **Elite** | 3 Phase Matrices + 4 netherite ingots |
| ''' + item('resonator_heart_emerald') + ''' **Emerald** | 1 Field Stabilizer + 7 emerald blocks + 1 Nether Star; with Modern Industrialization: 1 Field Stabilizer + 6 emerald blocks + 1 Nether Star + 1 Processing Unit |
| ''' + item('resonator_heart_allthemodium') + ''' **Allthemodium¹** | 2 Field Stabilizers + 4 Allthemodium blocks + 2 Nether Stars; with MEGA Cells: 1 Field Stabilizer + 4 Allthemodium blocks + 2 Nether Stars + 2 Cell Components 16M |
| ''' + item('resonator_heart_vibranium') + ''' **Vibranium¹** | 1 Condensed Core + 4 Vibranium blocks + 2 Nether Stars; with Modern Industrialization and Draconic Evolution: 1 Condensed Core + 4 Vibranium blocks + 1 Nether Star + 1 Processing Unit + 2 Chaotic Cores |
| ''' + item('resonator_heart_unobtainium') + ''' **Unobtainium¹** | 2 Condensed Cores + 4 Unobtainium blocks + 3 Nether Stars; with MEGA Cells and Draconic Evolution: 1 Condensed Core + 4 Unobtainium blocks + 2 Nether Stars + 1 Cell Component 16M + 1 Chaotic Core |
| ''' + item('resonator_heart_ultimate') + ''' **Ultimate** | 3 Condensed Cores + 1 Alloy Frame + 5 Nether Stars; with MEGA Cells, Draconic Evolution and Modern Industrialization: 3 Cell Components 256M + 1 Cell Component 64M + 1 Quantum Circuit + 1 Chaotic Core + 1 Awakened Core + 1 Alloy Frame + 1 Nether Star |

''' + ATM_NOTE['en'] + '''

The ''' + item('alloy_frame') + ''' **Alloy Frame** for the Ultimate Heart is a ring of 8 blocks around
1 Nether Star: with Allthemodium, Unobtainium-Vibranium alloy blocks; without it, netherite blocks.

<RecipeFor id="wirelessautomate:resonator_heart_basic" />
<RecipeFor id="wirelessautomate:alloy_frame" />

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
**sintoniza** o Núcleo na frequência certa para tirar o máximo de FE. Os cristais, os catalisadores e os Corações vêm
do [Quartzo de Sinal](signal-quartz.md).

<br clear="all" />

''' + fill(SCENE_RESONATOR, **RESONATOR_ANNOTATIONS['pt']) + '''

A cena mostra a montagem **Vitrine**, com a janela de vidro numa face e o Núcleo em outra. Gire a cena com o mouse.

## Montar

1. Faça o **Núcleo de Ressonância**: a receita pede um **Coração do Ressonador Básico** (veja o
   [Quartzo de Sinal](signal-quartz.md)). Coloque o Núcleo e clique nele com a mão vazia: a estrutura aparece
   **projetada** no mundo (as peças que faltam e o que está errado). Clique de novo para desligar. **Shift + roda
   do mouse** troca entre as montagens **Vitrine** e **Faixa**.
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
| ''' + item('resonator_crystal_port') + ''' **Porta de Cristais** | Aceita cristais e catalisador e devolve os Opacos e os rachados. |
| ''' + item('resonator_tuner') + ''' **Sintonizador** | Opcional: sintoniza sozinho. |

## Cristais e catalisador

Cada Núcleo tem câmaras para os cristais (1 no Básico, até 8 no Ultimate). Ponha os cristais de um destes jeitos:

| Como | O que fazer |
| --- | --- |
| **Pelo clique** | Clique no Núcleo com o cristal na mão: ele vai para a primeira câmara vazia. Com o catalisador na mão, ele entra no Núcleo. |
| **Pela tela** | Abra a tela do Núcleo e use os slots de cristais e o do catalisador. |
| **Pela Porta de Cristais** | Um roteador (ou outro transporte de itens) põe os cristais e o catalisador na porta e tira os Opacos e os rachados dela. |

Para tirar o último cristal, clique no Núcleo com **Shift e as duas mãos vazias**. Do Elite para cima, use o
**Cristal Afinado** do tier: os outros cristais rendem bem menos. O **catalisador** só serve para recarregar os
Opacos, e cada tier aceita o seu (a tabela de manutenção, mais abaixo); a tela diz qual ele aceita. A recarga
também gasta a **integridade** do cristal, que racha depois de 8 cargas; os detalhes estão no
[Quartzo de Sinal](signal-quartz.md).

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

## Interferência

Ressonadores perto uns dos outros se atrapalham. Cada outro Núcleo **montado** a até **32 blocos** (contando a maior
das distâncias em x, y e z), na mesma dimensão e com o chunk carregado, tira **15%** da eficiência deste, até
**60%** no total. Vale para Núcleos de qualquer tier.

A tela do Núcleo mostra a perda e a distância do vizinho mais perto, como **−15% · Núcleo a 24 blocos**, ou
**Interferência: nenhuma**. Para gerar no máximo, deixe cada Ressonador a mais de 32 blocos dos outros.

## Eco Ressonante

Só no Núcleo **Ultimate**. A tela dele tem um slot **Eco**: ponha ali um **Cristal Afinado Ultimate rachado** e ele é
queimado na hora, somando **1 de Eco**. Cada Eco aumenta a geração em **5%**, sem teto: quanto mais cristais você
queimar, mais o Núcleo gera.

Um Afinado Ultimate rachado que chega pela Porta de Cristais também vai para o Eco. Para refazer o cristal em vez de
queimá-lo, tire-o pela Porta ou pela tela e refaça-o na bancada. O Núcleo **guarda o Eco** quando é quebrado e o
leva de volta quando você o coloca de novo.

## A câmara

Pelo vidro dá para ler a sintonia, sem abrir a tela:

- **Em sintonia**: a figura de luz no ar **fecha**, os cristais sobem e descem **juntos**, cada um com um feixe até a
  luz, e a placa do chão fica nítida.
- **Fora de sintonia**: a figura vira um **rabisco**, cada cristal vai no seu tempo e o chão chia. Com a geração em zero,
  a figura some.
- A lente do Núcleo acende na parede dele, faíscas correm até a Porta de Energia e um fio de redstone chega ao cristal
  Opaco que está recarregando. Um cristal rachado fica escuro.

## Energia

A energia sai pela **Porta de Energia**: prenda um [Roteador](router.md) nela e ponha a face em **Extrai**
na aba **Energia**. Um roteador do mesmo tier do Núcleo dá conta da geração cheia. O Núcleo guarda uma reserva
pequena (uns 10 segundos de geração cheia); com ela cheia, ele **para de gerar e não gasta cristal**. O Núcleo só
trabalha com o chunk carregado: para ele rodar longe de você, use o [Upgrade de Chunk Loading](chunk-loading.md)
no roteador preso à Porta.

## Tiers

O Núcleo sobe de tier com os mesmos [Cartões de Upgrade](upgrade-cards.md) do roteador (Shift + clique com o
cartão no Núcleo), sem desmontar: o conteúdo continua e as câmaras novas aparecem. Para subir, você precisa também
do **Coração do Ressonador do tier de destino** no inventário: o clique gasta o cartão e o Coração. Sem ele, nada
muda e a barra de ação diz **Falta o Coração do Ressonador** com o tier. O cartão sobe um degrau por vez, então cada
degrau pede o seu Coração. As receitas dos Corações estão no [Quartzo de Sinal](signal-quartz.md).

Cada tier tem mais câmaras e gera mais. A geração máxima é com o cristal que rende cheio em todas as câmaras e a
lâmpada **EM SINTONIA**, sem interferência:

''' + resonator_table('pt') + '''

A manutenção também sobe com o tier: o cristal certo, quanto a carga dura (gerando a 100%) e o catalisador que
recarrega os Opacos. O Cristal de Sinal dura a metade do tempo da tabela.

''' + resonator_upkeep_table('pt') + '''

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
frequency to pull the most FE out of them. The crystals, the catalysts and the Hearts come from
[Signal Quartz](signal-quartz.md).

<br clear="all" />

''' + fill(SCENE_RESONATOR, **RESONATOR_ANNOTATIONS['en']) + '''

The scene shows the **Showcase** build, with the glass window on one face and the Core on another. Rotate the scene with the mouse.

## Building it

1. Craft the **Resonance Core**: its recipe takes a **Basic Resonator Heart** (see
   [Signal Quartz](signal-quartz.md)). Place the Core and click it with an empty hand: the structure shows up
   **projected** in the world (the missing parts and what is wrong). Click again to turn it off. **Shift + mouse
   wheel** switches between the **Showcase** and **Band** builds.
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
| ''' + item('resonator_crystal_port') + ''' **Resonator Crystal Port** | Accepts crystals and catalyst and hands Dull and cracked ones back. |
| ''' + item('resonator_tuner') + ''' **Resonator Tuner** | Optional: tunes by itself. |

## Crystals and catalyst

Each Core has chambers for crystals (1 on Basic, up to 8 on Ultimate). Put crystals in one of these ways:

| How | What to do |
| --- | --- |
| **By clicking** | Click the Core with the crystal in hand: it goes to the first empty chamber. With the catalyst in hand, it goes into the Core. |
| **Through the screen** | Open the Core's screen and use the crystal slots and the catalyst slot. |
| **Through the Crystal Port** | A router (or another item transport) puts crystals and catalyst into the port and takes the Dull and cracked ones out. |

To take the last crystal out, click the Core with **Shift and both hands empty**. From Elite up, use the tier's
**Tuned Signal Crystal**: other crystals yield much less. The **catalyst** is only for recharging Dull crystals, and
each tier takes its own (the upkeep table, further down); the screen says which one it takes. Recharging also uses
up the crystal's **integrity**, and it cracks after 8 charges; the details are on
[Signal Quartz](signal-quartz.md).

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

## Interference

Resonators close to each other get in each other's way. Every other **formed** Core within **32 blocks** (counting
the largest of the x, y and z distances), in the same dimension and with its chunk loaded, takes **15%** of this
one's efficiency, up to **60%** in all. Cores of any tier count.

The Core's screen shows the loss and how far the nearest neighbor is, like **−15% · Core 24 blocks away**, or
**Interference: none**. To generate at full, keep each Resonator more than 32 blocks from the others.

## Resonant Echo

Only on the **Ultimate** Core. Its screen has an **Echo** slot: put a **cracked Tuned Ultimate crystal** there and it
burns right away, adding **1 Echo**. Each Echo raises generation by **5%**, with no ceiling: the more crystals you burn,
the more the Core generates.

A cracked Tuned Ultimate crystal that arrives through the Crystal Port goes to the Echo too. To recast the crystal
instead of burning it, take it out through the Port or the screen and recast it on a crafting table. The Core
**keeps its Echo** when broken and brings it back when you place it again.

## The chamber

Through the glass you can read the tuning without opening the screen:

- **In tune**: the figure of light in the air **closes**, the crystals rise and fall **together**, each with a beam to
  the light, and the plate on the floor gets sharp.
- **Out of tune**: the figure turns into a **scribble**, each crystal moves on its own and the floor hisses. With
  generation at zero, the figure disappears.
- The Core's lens lights up on its wall, sparks run to the Energy Port and a thread of redstone reaches the Dull
  crystal that is recharging. A cracked crystal goes dark.

## Power

Power leaves through the **Energy Port**: attach a [Router](router.md) to it and set the face to **Extract** on the
**Energy** tab. A router of the Core's tier keeps up with full generation. The Core keeps a small reserve (about
10 seconds of full generation); when it's full, the Core **stops generating and doesn't wear the crystals**. The
Core only works while its chunk is loaded: to run it while you're away, use the
[Chunk Loading Upgrade](chunk-loading.md) on the router attached to the Port.

## Tiers

The Core goes up in tier with the same [Upgrade Cards](upgrade-cards.md) as the router (Shift + click the Core with
the card), without taking it apart: the contents stay and the new chambers show up. To go up, you also need the
**Resonator Heart of the target tier** in your inventory: the click uses up the card and the Heart. Without it,
nothing changes and the action bar says **Missing Resonator Heart** with the tier. A card goes up one step at a
time, so each step takes its own Heart. The Heart recipes are on [Signal Quartz](signal-quartz.md).

Each tier has more chambers and generates more. Max generation is with a full-yield crystal in every chamber and
the **IN TUNE** lamp lit, with no interference:

''' + resonator_table('en') + '''

Upkeep goes up with the tier too: the right crystal, how long a charge lasts (generating at 100%) and the catalyst
that recharges Dull crystals. A Signal Crystal lasts half the time in the table.

''' + resonator_upkeep_table('en') + '''

The numbers are the defaults; JEI shows your pack's.

## Recipes

<RecipeFor id="wirelessautomate:resonator_casing" />
<RecipeFor id="wirelessautomate:resonator_glass" />
<RecipeFor id="wirelessautomate:resonance_core" />
<RecipeFor id="wirelessautomate:resonator_energy_port" />
<RecipeFor id="wirelessautomate:resonator_crystal_port" />
<RecipeFor id="wirelessautomate:resonator_tuner" />
''')

TABLE_REPAIR_PT = '''| Cristal | FE por reparo |
| --- | --- |
| Cristal de Sinal | 432 |
| Cristal Lapidado | 13.824 |
| Afinado Elite | 20.000 |
| Afinado Esmeralda | 320.000 |
| Afinado Allthemodium | 12.800.000 |
| Afinado Vibranium | 13.400.000 |
| Afinado Unobtainium | 21.400.000 |
| Afinado Ultimate | 536.870.912 |'''

TABLE_REPAIR_EN = '''| Crystal | FE per repair |
| --- | --- |
| Signal Crystal | 432 |
| Cut Crystal | 13,824 |
| Tuned Elite | 20,000 |
| Tuned Emerald | 320,000 |
| Tuned Allthemodium | 12,800,000 |
| Tuned Vibranium | 13,400,000 |
| Tuned Unobtainium | 21,400,000 |
| Tuned Ultimate | 536,870,912 |'''

page('crystal-repairer.md', front('Reparador de Cristais', 'wirelessautomate:crystal_repairer', 20, item_ids=[
    'wirelessautomate:crystal_repairer']) + '''
# Reparador de Cristais

<ItemImage id="wirelessautomate:crystal_repairer" scale="2" float="left" />

Um bloco que **conserta os cristais rachados** do [Ressonador de Sinal](signal-resonator.md): o cristal volta cheio, com
a carga e a integridade cheias. É a mesma coisa que refazer o cristal na bancada, só que sem ficar na bancada e com
**até oito reparos ao mesmo tempo**. Não tem tier e não precisa estar perto de um Ressonador.

<br clear="all" />

## Como usar

1. Coloque o Reparador e dê **energia** a ele. A energia entra por **qualquer face**: um cabo, um roteador no modo
   **Insere** com a Bateria ou o gerador do outro lado, ou uma Bateria Wireless. Nenhum roteador tira energia dele.
2. Ponha os **cristais rachados** nos oito slots de entrada (um por linha) e os **Cristais de Sinal cheios** no slot do
   meio. Pode ser pela tela (clique direito), por um roteador preso ao bloco ou por um funil: cada slot só aceita o
   que cabe nele.
3. Cada linha começa sozinha quando tem um rachado, **4 Cristais de Sinal cheios** do estoque e a energia do reparo.
   Os reparos levam **7 segundos** e as oito linhas andam juntas.
4. O cristal consertado vai para os slots de saída. Tire-os pela tela, por um roteador ou por um funil. Se a saída
   está cheia, a linha espera.

A tela mostra a energia do bloco, o progresso de cada linha e o que falta quando nada anda (**sem energia**,
**faltam cristais** ou **saída cheia**).

## O custo

Cada reparo gasta **4 Cristais de Sinal cheios** (o mesmo da bancada) e uma quantidade de **FE** que depende do tipo
do cristal:

''' + TABLE_REPAIR_PT + '''

Os números são os padrões; o JEI mostra os do seu pack. Com pouca energia, o reparo só começa quando o buffer
tem o custo inteiro. Quebrar o bloco no meio de um reparo **perde os 4 cristais e a energia já gastos** nele.

## Receita

<RecipeFor id="wirelessautomate:crystal_repairer" />
''', front('Crystal Repairer', 'wirelessautomate:crystal_repairer', 20, item_ids=[
    'wirelessautomate:crystal_repairer']) + '''
# Crystal Repairer

<ItemImage id="wirelessautomate:crystal_repairer" scale="2" float="left" />

A block that **fixes the cracked crystals** of the [Signal Resonator](signal-resonator.md): the crystal comes back full,
with full charge and integrity. It does the same as recasting the crystal at a crafting table, without standing at
the table and with **up to eight repairs at the same time**. It has no tier and doesn't need to be near a Resonator.

<br clear="all" />

## How to use

1. Place the Repairer and give it **energy**. Energy enters through **any face**: a cable, a router in **Insert** mode
   with a Battery or a generator on the other side, or a Wireless Battery. No router pulls energy out of it.
2. Put the **cracked crystals** in the eight input slots (one per line) and the **full Signal Crystals** in the middle
   slot. Use the screen (right click), a router attached to the block or a hopper: each slot only accepts what fits.
3. Each line starts by itself when it has a cracked crystal, **4 full Signal Crystals** in the stock and the repair's
   energy. A repair takes **7 seconds** and the eight lines run together.
4. The fixed crystal goes to the output slots. Take them out from the screen, with a router or a hopper. If the output
   is full, the line waits.

The screen shows the block's energy, each line's progress and what is missing when nothing moves (**no energy**,
**missing crystals** or **output full**).

## The cost

Each repair spends **4 full Signal Crystals** (the same as the crafting table) and an amount of **FE** that depends on
the crystal type:

''' + TABLE_REPAIR_EN + '''

The numbers are the defaults; JEI shows your pack's. With little energy, a repair only starts when the buffer
holds its whole cost. Breaking the block in the middle of a repair **loses the 4 crystals and the energy already spent** on it.

## Recipe

<RecipeFor id="wirelessautomate:crystal_repairer" />
''')

page('signal-screen.md', front('Tela de Sinal', 'wirelessautomate:signal_screen', 20, item_ids=[
    'wirelessautomate:signal_screen']) + '''
# Tela de Sinal

<ItemImage id="wirelessautomate:signal_screen" scale="2" float="left" />

Um painel que você prende numa parede e estica pelo tamanho que quiser, de **1 × 1 até 32 × 32 blocos**. Ele mostra os
números das suas [redes](networks.md) ao vivo: o que entra, o que sai e quantas faces estão movendo.

<br clear="all" />

## Como colocar

1. Clique com a Tela na **lateral de um bloco sólido**: ela fica rente à parede, virada para você. Não dá para pôr no
   chão nem no teto, e ela cai se a parede atrás dela sair.
2. Clique nela com a **mão vazia** para abrir a tela de configuração. Só quem a colocou, os operadores e quem está na
   aba **Acesso** podem configurar; os outros só olham.

## Escolher o tamanho

Na aba **Tamanho**:

| Parte | Como usar |
| --- | --- |
| **Largura** e **Altura** | ‹ e › mudam de 1 em 1 bloco; com **Shift**, de 4 em 4. |
| **Cresce a partir do bloco para** | ↗ cima e direita, ↖ cima e esquerda ou ✥ centro: onde o bloco que você colocou fica dentro da tela. |
| **Prévia** | A parede em volta, com a área da tela. A célula vermelha é a que impede o tamanho. |
| **Aplicar** | Estica a tela. Fica desligado enquanto algo impede. |

A tela grande **não ocupa blocos**: ela é desenhada sobre a parede. Para caber, cada bloco da área precisa de
**parede sólida atrás** e de **espaço livre na frente** (ar ou grama, por exemplo; uma tocha impede), e a área não pode
cruzar outra Tela.

## O que mostrar: o editor

Na aba **Editar** você monta o que a Tela mostra, com **peças**. A janela cresce para ocupar a tela do jogo; se não
couber, diminua a escala da interface.

| Peça | Para quê |
| --- | --- |
| **Valor** | Um número ao vivo de uma [rede](networks.md): o que entra, o que sai, o que está guardado, quantas faces movem... |
| **Texto** | Uma frase fixa. |
| **Forma** | Um retângulo com fundo, borda e cantos: cartão, moldura, divisória. |
| **Ícone de item** | O desenho de um item, escolhido pelo id ou na lista de um baú ligado. |
| **Barra** | Uma barra que enche conforme um número, deitada ou em pé. |
| **Medidor** | Um mostrador em arco com um ponteiro. |
| **Gráfico** | O histórico de um número, em linha ou colunas, dos últimos minutos. |
| **Seta** | Sobe, desce ou fica parada conforme o número cresce, cai ou se mantém. |
| **Imagem** | Uma pixel art sua ou uma imagem enviada (PNG, JPG ou GIF), da aba **Artes** (mais abaixo). |

- **Escolher e mexer:** clique numa peça para escolhê-la (numa peça de grupo, o grupo inteiro; dois cliques entram no
  grupo). **Shift** + clique soma ou tira da escolha; clique no vazio solta. Arraste para mover e puxe a alça do canto
  de baixo para redimensionar. Tudo encaixa numa grade de 4 px; segure **Alt** para ajustar de 1 em 1 px.
- **Grupos e camadas:** **Agrupar** junta as peças escolhidas para mover e redimensionar juntas; **Desagrupar** solta.
  Embaixo, as **Camadas** listam as peças de cima para baixo: arraste uma linha para trocar a ordem, use o olho para
  esconder e **[** e **]** para subir e descer.
- **Abas da direita:** **Dado** liga o Valor a uma rede (e escolhe o tipo e o que ler); **Formato** define casas,
  abreviar (k, M, G), sinal, prefixo, sufixo e unidade; **Estilo** muda posição, tamanho, cores, borda, cantos,
  opacidade e fonte; **Regras** muda a cor, esconde ou pisca quando o número passa de um valor.
- **De onde vem o número:** na aba **Dado**, a **Fonte** é a **Rede** (o que entra, sai ou está guardado, e quantas faces movem), um **Armazém** ligado pelo [Vinculador](linker.md) (guardado, capacidade, enchimento e o que entra, sai ou sobra por segundo; num Baú, dá para ler um item só) ou o **Relógio** do jogo (hora do dia e dia). Na Rede, **Guardado** soma o que os Armazéns da rede guardam.
- **Item de um Baú:** com um baú ligado, a aba **Dado** mostra a grade dos itens dele com um campo **Buscar itens...** e um clique escolhe o item. Com o JEI, também dá para **arrastar um item** do JEI para o campo do Ícone ou do filtro do Baú.
- **Salvar:** nada vai para a parede até clicar em **Salvar** (ou **Ctrl+S**). Fechar com mudanças pede dois cliques
  no ✕ (**Descartar?**). A miniatura mostra os números de uma ligação nova antes de salvar.

| Atalho | O que faz |
| --- | --- |
| **Ctrl+Z**, **Ctrl+Y** (ou **Ctrl+Shift+Z**) | Desfaz e refaz. |
| **Ctrl+C**, **Ctrl+X**, **Ctrl+V** | Copia, recorta e cola, inclusive de uma Tela para outra (as artes das Imagens vão junto). |
| **Ctrl+D**, **Delete** | Duplica e remove. |
| **Ctrl+A**, **Ctrl+G**, **Ctrl+Shift+G** | Escolhe tudo, agrupa e desagrupa. |
| **Setas** | Movem 4 px; com **Shift**, 32 px; com **Alt**, 1 px. |

### Temas e fundo

Com **nada escolhido**, a coluna da direita mostra a **base** da Tela. O **Tema** é uma paleta de seis cores (fundo,
cartão, texto, apagado, alta e baixa): há quatro prontos (Grafite, Fósforo, Âmbar e Porcelana) e os seus. As cores das
peças marcadas como "do tema" (as bolinhas redondas) acompanham a troca; as quadradas são fixas. Edite as seis cores
pelo código `#RRGGBB` e use **Salvar tema** para guardar a paleta com um nome. O **Fundo** pode ser o do tema, uma cor
fixa ou um degradê, e dá para ligar as **linhas** de varredura e a **grade** da miniatura. A Tela guarda a paleta
inteira: apagar um tema seu depois não muda nenhuma Tela.

### Layouts prontos e o seu

Com nada escolhido, **Layout pronto** na base oferece **Energia**, **Armazenamento**, **Rede** e **Relógio**: escolha um e clique em **Aplicar layout**; ele se ajusta ao tamanho da Tela e troca o que está nela (dá para desfazer com **Ctrl+Z** antes de salvar). Para guardar o seu, escreva um nome no campo e clique em **Salvar layout atual**; ele passa a aparecer na mesma lista, em qualquer Tela sua. **Remover layout** apaga o seu escolhido, em dois cliques.

### Componentes

Na coluna da esquerda, **Componentes** são grupos de peças prontos para inserir: **Painel de rede**, **Cartão de
número** e **Título** já vêm, e você guarda os seus. Clique num componente para inseri-lo, num grupo novo, no primeiro
lugar livre. Para guardar o seu, escolha as peças, clique em **Salvar como componente** (⊕) e dê um nome; o ✎ renomeia
e o ✕ remove, em dois cliques. Os componentes e os temas ficam na sua biblioteca e valem em qualquer Tela sua. Um
componente ligado a uma rede que você não pode mais usar entra com **—**.

Para mostrar o conteúdo de um Baú, Bateria ou Tanque, ligue-o à Tela com o [Vinculador](linker.md) antes de escolhê-lo na aba **Dado**.

Os números chegam uma vez por segundo enquanto há alguém perto. Uma rede privada da qual quem colocou a Tela perdeu o
acesso mostra **—**.

## Pixel art

Desenhe suas artes na aba **Artes**, parte **Pixel art**, na coluna da esquerda do editor. Elas ficam na sua biblioteca e valem em
qualquer Tela sua.

1. Clique em **Nova** (ou escolha uma arte e clique em **Editar**). O editor de pixel art abre por cima do editor.
2. Escreva o nome e o **Tamanho**: de 8 a 128 pixels de largura e de altura. Mudar o tamanho mantém o canto de cima à
   esquerda.
3. Desenhe e clique em **Salvar** (ou **Ctrl+S**). O editor fecha quando a arte chega à biblioteca; se ela não couber
   (biblioteca cheia ou grande demais), o desenho continua aberto, com o aviso. **Cancelar** volta sem salvar; com
   mudanças, pede dois cliques.

Cada arte tem até **16 cores** e no máximo **128 × 128** pixels. **Duplicar** faz uma cópia e **Remover** apaga, em
dois cliques.

### Ferramentas

O clique esquerdo usa a ferramenta e o **direito apaga** (no clarear, escurece). **Ctrl** + clique pega a cor de um
pixel com qualquer ferramenta.

| Tecla | Ferramenta |
| --- | --- |
| **B** | Lápis. |
| **E** | Borracha. |
| **G** | Balde: pinta a área da mesma cor. Com **Shift**, troca a cor na arte inteira. |
| **I** | Conta-gotas. |
| **L** | Linha. **Shift** trava em 0°, 45° e 90°. |
| **R** | Retângulo. **R** de novo alterna contorno e cheio; **Shift** faz quadrado. |
| **O** | Elipse. **O** de novo alterna contorno e cheia; **Shift** faz círculo. |
| **D** | Clarear (esquerdo) e escurecer (direito), pela rampa da cor. |
| **T** | Pontilhado: pinta um pixel sim e um não, em xadrez. |
| **S** | Seleção. |

| Tecla | Ajuda |
| --- | --- |
| **[** e **]** | Pincel menor e maior, de 1 a 8 px. O botão do rodapé troca quadrado e redondo (redondo a partir de 3 px). |
| **M** e **Shift+M** | Espelho esquerda e direita, e em cima e embaixo. Valem juntos. |
| **P** | Pixel perfect: tira os cantos em L do lápis de 1 px. |
| **X** | Volta para a cor anterior. |
| **Ctrl+G** | Liga e desliga a grade. |
| **Ctrl+Z**, **Ctrl+Y** | Desfaz e refaz, até 100 passos. |

### Paleta e rampas

A paleta tem 16 cores em 4 linhas, e a cor 0 é sempre transparente. Cada linha é uma **rampa**, do tom escuro à
esquerda ao claro à direita: o clarear e o escurecer andam por ela.

- Dois cliques numa cor (ou **Editar cor**) abrem o seletor. Trocar uma cor muda todos os pixels dela.
- **Criar rampa** refaz a linha da cor escolhida como uma rampa dela: mais escura e azulada à esquerda, mais clara e
  amarelada à direita.

### Seleção, virar e girar

- Com **S**, arraste para marcar um retângulo. Arraste de dentro dele para mover o pedaço.
- As **setas** movem 1 px (com **Shift**, 8 px). **Enter** assenta e **Esc** desfaz o mover e tira a seleção.
- **Ctrl+A** seleciona tudo e **Ctrl+D** tira. **Ctrl+C**, **Ctrl+X** e **Ctrl+V** copiam, recortam e colam;
  **Delete** apaga.
- Com uma seleção, as ferramentas só pintam dentro dela.
- **Shift+H** e **Shift+V** viram na horizontal e na vertical, e o botão de girar gira 90° no sentido horário. Com
  uma seleção, só o pedaço; sem ela, todos os quadros de uma arte animada.
- **Limpar** apaga o quadro escolhido inteiro, em dois cliques.

### Navegar no quadro

| Gesto | O que faz |
| --- | --- |
| Roda do mouse | Zoom no ponto do mouse. |
| **Alt**, **Espaço** ou botão do meio + arrastar | Move a vista. |
| **Setas**, sem seleção | Movem a vista. |
| **0** | Enquadra a arte. |
| **+** e **−** | Zoom no centro. |
| Clique na prévia | Leva a vista até aquele ponto. |

O rodapé mostra a posição e a cor sob o mouse. A coluna da direita mostra a arte em 1×, 2× e repetida, como no
mosaico (com pouco espaço ou numa arte grande, só o que cabe inteiro). **Esc** sem seleção faz o mesmo que
**Cancelar**.

### Animação (quadros)

Uma arte pode ter vários quadros, que tocam na Imagem e no mosaico. Todas as Telas com a mesma arte tocam juntas, e a
Tela só troca de quadro quando está à vista.

- A **faixa de quadros**, embaixo do quadro de desenho, mostra as miniaturas; a escolhida é a que você desenha. Clique
  para escolher e arraste para mudar a ordem.
- **+** põe um quadro vazio depois do escolhido, o botão de duplicar copia o escolhido e o de remover o tira.
- **Papel vegetal:** o quadro anterior aparece apagado por baixo, para desenhar o próximo.
- **Velocidade:** de 1 a 20 quadros por segundo, pelo **‹ ›** ou digitando. **Modo:** **Loop** (volta ao primeiro) ou
  **Vai e volta**. O botão de tocar pausa e toca a prévia (pausada, ela mostra o quadro escolhido).
- Até 64 quadros, do mesmo tamanho e com a mesma paleta. Os quadros contam no teto de pixels da Tela: uma arte de
  32 × 32 com 10 quadros conta 10.240.
- Tudo entra no **Ctrl+Z**. A paleta vale para todos os quadros; o **Limpar** e as ferramentas, para o escolhido.

| Tecla | O que faz |
| --- | --- |
| **,** e **.** | Quadro anterior e seguinte. |
| **Alt+N** | Quadro novo vazio, depois do escolhido. |
| **Alt+D** | Duplica o quadro escolhido. |
| **Alt+O** | Liga e desliga o papel vegetal. |
| **Alt+P** | Toca e pausa a prévia. |
| **Ctrl+C** e **Ctrl+V** | Copiam um pedaço de um quadro e colam em outro. |

### Pôr a arte na Tela

- **Imagem:** clique em **Imagem** na lista de **Peças** (ou arraste-a até a miniatura). Ela nasce com a arte escolhida
  na aba **Artes** (uma pixel art ou uma imagem); sem nada escolhido, nasce vazia, com **Escolha a arte ou a imagem**.
- **Escolher a arte:** na aba **Estilo**, seção **Peça**, clique numa miniatura da grade. As marcadas **Da Tela**
  vieram com a Tela, de outro jogador ou de um código. Arrastar uma arte da aba **Artes** até a miniatura também
  serve.
- Na mesma seção: **Escala** (1× a 16×, ou **Esticar**), **Alinhar** (9 posições) e **Opacidade**.
- A Tela guarda uma **cópia** da arte: mudar ou apagar a arte depois não muda a Tela. **Atualizar da biblioteca**
  traz a versão atual.
- **Fundo em mosaico:** com nada escolhido, mude o **Fundo** para **Pixel art em mosaico** e escolha a arte na grade.
  Ela se repete pela Tela inteira, por baixo das peças, com **Escala** (1× a 8×) e **Opacidade**.
- A linha **Pixels** da base mostra quanto as artes da Tela somam e o teto.

### Exportar e importar por código

Leve um layout ou uma arte para outro mundo, ou para um amigo, como texto.

| Botão | O que faz |
| --- | --- |
| **Exportar** | Copia o código: na base (embaixo de **Aplicar layout**), do layout; na aba **Artes**, da arte escolhida. |
| **Importar** | Lê o código copiado. Na base, aplica o layout no rascunho (**Ctrl+Z** desfaz); na aba **Artes**, cria uma arte nova. Um código de imagem (**WA-IMAGEM1**) vira um envio de imagem. |
| **Copiar como texto** | Copia numa forma legível, para editar à mão ou pedir a uma IA. **Importar** lê as duas formas. |

- O layout leva as artes junto. As ligações a redes ou armazenamentos que você não tem saem: ligue de novo na aba
  **Dado**.
- Um código inválido mostra o aviso na barra de baixo, e nada muda.
- Para gerar artes e Telas com uma IA, há um guia no GitHub do mod: `docs/guia-pixel-art-com-ia.md`.

## Imagens e GIFs

Além da pixel art, a Tela mostra fotos e GIFs animados de verdade, na parte **Imagens** da aba **Artes**.

1. Clique em **Enviar arquivo** e escolha um PNG, JPG ou GIF no computador. Ou copie um código **WA-IMAGEM1** e
   clique em **Importar**.
2. A imagem vai ao servidor e entra na sua biblioteca com o nome do arquivo. **Renomear** (ou dois cliques na linha)
   muda o nome, e **Remover**, em dois cliques, tira da biblioteca.
3. Arraste a imagem até a miniatura para criar uma **Imagem**, ou escolha-a na grade da aba **Estilo**: as imagens vêm
   depois das artes, com o rótulo **Imagens**, e o GIF tem a marca ▶. O **Fundo em mosaico** também aceita imagens.

| Limite | Padrão |
| --- | --- |
| Arquivo | Até **8 MB** |
| Tamanho | Até **1024 × 1024** pixels |
| GIF | Até **240** quadros |
| Por Tela | Até **8** imagens diferentes |

O servidor pode mudar esses números. A imagem é sempre um arquivo enviado e guardado no mundo, nunca um link da internet.

- **GIF:** toca no ritmo do arquivo, só quando alguém olha para a Tela. Duas Telas com o mesmo GIF tocam juntas.
- **Copiar código:** põe a imagem na área de transferência como **WA-IMAGEM1**, para levar a outro mundo. O código de
  um layout leva as imagens junto quando cabem; senão leva só as referências, que funcionam no mesmo servidor.
- **Esconder as imagens dos outros:** na config do cliente (`config/wirelessautomate-client.toml`, seção `tv`),
  `showOthersImages = false` troca as imagens enviadas por outros jogadores por um quadro cinza. As suas continuam.
- **Operadores:** `/wa tela imagens` lista as imagens do mundo, com o tamanho, quem enviou e quantas Telas usam.
  `/wa tela imagem remover <resumo>` tira uma imagem de todas as Telas, e `/wa tela imagem banir <jogador>` impede
  alguém de enviar (`desbanir` desfaz). A seção `tv.images` da config do servidor desliga as imagens e limita quem envia
  e o espaço.

## Acesso

Na aba **Acesso** (só o dono e os operadores a veem), dê a outros jogadores o direito de editar a Tela.

1. Escreva o nome em **Convidar jogador…** e escolha uma sugestão, ou aperte **Enter**. Só vale quem já entrou no
   servidor.
2. Com o FTB Teams instalado, adicione um time em **Adicionar time…**.
3. Para tirar alguém, clique duas vezes no **✕** da linha.

Quem está na lista edita como o dono: tamanho, layout e ligações, dentro das redes que cada um pode usar. As ligações
que a Tela já tem ficam quando qualquer um salva, mesmo a uma rede que ele não usa; uma ligação nova só vale a uma rede
de quem a faz. Ver a Tela continua liberado para todos.

## Receita

<RecipeFor id="wirelessautomate:signal_screen" />
''', front('Signal Screen', 'wirelessautomate:signal_screen', 20, item_ids=[
    'wirelessautomate:signal_screen']) + '''
# Signal Screen

<ItemImage id="wirelessautomate:signal_screen" scale="2" float="left" />

A panel you attach to a wall and stretch to any size, from **1 × 1 up to 32 × 32 blocks**. It shows your
[networks'](networks.md) numbers live: what comes in, what goes out and how many faces are moving.

<br clear="all" />

## Placing it

1. Click with the Screen on the **side of a solid block**: it sits flush against the wall, facing you. It can't go on
   the floor or the ceiling, and it drops if the wall behind it goes away.
2. Click it with an **empty hand** to open its settings. Only whoever placed it, operators and those in the **Access**
   tab can configure it; everyone else just watches.

## Choosing the size

In the **Size** tab:

| Part | How to use |
| --- | --- |
| **Width** and **Height** | ‹ and › change it 1 block at a time; with **Shift**, 4 at a time. |
| **Grows from the block toward** | ↗ up and right, ↖ up and left or ✥ center: where the block you placed sits inside the screen. |
| **Preview** | The wall around it, with the screen's area. The red cell is the one in the way. |
| **Apply** | Stretches the screen. It stays off while something is in the way. |

The big screen **takes no blocks**: it is drawn over the wall. To fit, every block of the area needs a **solid wall
behind it** and **free space in front** (air or grass, for example; a torch is in the way), and the area can't cross
another Screen.

## What to show: the editor

In the **Edit** tab you build what the Screen shows out of **pieces**. The window grows to fill the game screen; if it
doesn't fit, lower the interface scale.

| Piece | What for |
| --- | --- |
| **Value** | A live number from a [network](networks.md): what comes in, what goes out, what is stored, how many faces move... |
| **Text** | A fixed phrase. |
| **Shape** | A rectangle with a fill, border and corners: card, frame, divider. |
| **Item icon** | The picture of an item, picked by id or from the list of a linked chest. |
| **Bar** | A bar that fills with a number, lying down or standing up. |
| **Gauge** | An arc dial with a needle. |
| **Graph** | A number's history over the last minutes, as a line or columns. |
| **Arrow** | Points up, down or stays flat as the number rises, falls or holds. |
| **Image** | A pixel art of yours or an uploaded image (PNG, JPG or GIF), from the **Art** tab (see below). |

- **Picking and moving:** click a piece to pick it (on a grouped piece, the whole group; two clicks enter the group).
  **Shift** + click adds or removes from the pick; click the empty space to let go. Drag to move and pull the corner
  handle to resize. Everything snaps to a 4 px grid; hold **Alt** to adjust 1 px at a time.
- **Groups and layers:** **Group** joins the picked pieces to move and resize together; **Ungroup** lets them go.
  Below, **Layers** lists the pieces from top to bottom: drag a row to change the order, use the eye to hide, and
  **[** and **]** to raise and lower.
- **Tabs on the right:** **Data** links a Value to a network (and picks the type and what to read); **Format** sets
  decimals, abbreviating (k, M, G), sign, prefix, suffix and unit; **Style** changes position, size, colors, border,
  corners, opacity and font; **Rules** changes the color, hides or blinks when the number crosses a value.
- **Where the number comes from:** in the **Data** tab, the **Source** is the **Network** (what comes in, goes out or is stored, and how many faces move), a **Storage** linked with the [Linker](linker.md) (stored, capacity, fill, and what comes in, goes out or is left over per second; on a Chest you can read a single item) or the game **Clock** (time of day and day). On the Network, **Held** adds up what the network's Storages hold.
- **A Chest's item:** with a chest linked, the **Data** tab shows a grid of its items with a **Search items...** field, and a click picks the item. With JEI you can also **drag an item** from JEI onto the Icon's field or the Chest filter.
- **Saving:** nothing reaches the wall until you click **Save** (or **Ctrl+S**). Closing with changes takes two clicks
  on the ✕ (**Discard?**). The thumbnail shows the numbers of a new link before saving.

| Shortcut | What it does |
| --- | --- |
| **Ctrl+Z**, **Ctrl+Y** (or **Ctrl+Shift+Z**) | Undo and redo. |
| **Ctrl+C**, **Ctrl+X**, **Ctrl+V** | Copy, cut and paste, even from one Screen to another (the Images' arts come along). |
| **Ctrl+D**, **Delete** | Duplicate and remove. |
| **Ctrl+A**, **Ctrl+G**, **Ctrl+Shift+G** | Pick all, group and ungroup. |
| **Arrow keys** | Move 4 px; with **Shift**, 32 px; with **Alt**, 1 px. |

### Themes and background

With **nothing picked**, the right column shows the Screen's **base**. The **Theme** is a palette of six colors
(background, card, text, muted, high and low): there are four ready ones (Graphite, Phosphor, Amber and Porcelain) and
your own. The colors of pieces set "from the theme" (the round dots) follow the swap; the square ones are fixed. Edit
the six colors by their `#RRGGBB` code and use **Save theme** to keep the palette under a name. The **Background** can
be the theme's, a fixed color or a gradient, and you can turn on the scan **lines** and the preview **grid**. The
Screen keeps the whole palette: deleting one of your themes later changes no Screen.

### Ready layouts and your own

With nothing picked, **Ready layout** in the base offers **Energy**, **Storage**, **Network** and **Clock**: pick one and click **Apply layout**; it fits the Screen's size and replaces what is on it (undo with **Ctrl+Z** before saving). To keep your own, type a name in the field and click **Save current layout**; it then shows up in the same list, on any Screen of yours. **Remove layout** deletes your picked one, in two clicks.

### Components

In the left column, **Components** are groups of pieces ready to insert: **Network panel**, **Number card** and
**Title** come built in, and you keep your own. Click a component to insert it, in a new group, in the first free
spot. To keep your own, pick the pieces, click **Save as component** (⊕) and name it; the ✎ renames and the ✕ removes,
in two clicks. Components and themes live in your library and work on any Screen of yours. A component linked to a
network you can no longer use comes in with **—**.

To show the contents of a Chest, Battery or Tank, link it to the Screen with the [Linker](linker.md) before picking it in the **Data** tab.

The numbers arrive once a second while someone is nearby. A private network that whoever placed the Screen lost access
to shows **—**.

## Pixel art

Draw your art in the **Art** tab, **Pixel art** part, in the editor's left column. It lives in your library and works on any Screen
of yours.

1. Click **New** (or pick an art and click **Edit**). The pixel art editor opens over the Screen editor.
2. Type the name and the **Size**: 8 to 128 pixels wide and tall. Changing the size keeps the top left corner.
3. Draw and click **Save** (or **Ctrl+S**). The editor closes once the art reaches your library; if it doesn't fit
   (library full or too big), the drawing stays open, with the warning. **Cancel** goes back without saving; with
   changes, it takes two clicks.

Each art has up to **16 colors** and at most **128 × 128** pixels. **Duplicate** makes a copy and **Remove** deletes,
in two clicks.

### Tools

Left click uses the tool and **right click erases** (on lighten, it darkens). **Ctrl** + click picks a pixel's color
with any tool.

| Key | Tool |
| --- | --- |
| **B** | Pencil. |
| **E** | Eraser. |
| **G** | Bucket: fills the area of the same color. With **Shift**, swaps the color in the whole art. |
| **I** | Eyedropper. |
| **L** | Line. **Shift** locks to 0°, 45° and 90°. |
| **R** | Rectangle. **R** again toggles outline and filled; **Shift** makes a square. |
| **O** | Ellipse. **O** again toggles outline and filled; **Shift** makes a circle. |
| **D** | Lighten (left) and darken (right), along the color's ramp. |
| **T** | Dither: paints every other pixel, in a checkerboard. |
| **S** | Selection. |

| Key | Helper |
| --- | --- |
| **[** and **]** | Smaller and larger brush, 1 to 8 px. The footer button swaps square and round (round from 3 px). |
| **M** and **Shift+M** | Mirror left and right, and top and bottom. They work together. |
| **P** | Pixel perfect: removes the L corners of the 1 px pencil. |
| **X** | Back to the previous color. |
| **Ctrl+G** | Turns the grid on and off. |
| **Ctrl+Z**, **Ctrl+Y** | Undo and redo, up to 100 steps. |

### Palette and ramps

The palette has 16 colors in 4 rows, and color 0 is always transparent. Each row is a **ramp**, from the dark shade on
the left to the light one on the right: lighten and darken walk along it.

- Two clicks on a color (or **Edit color**) open the picker. Changing a color changes all its pixels.
- **Make ramp** redoes the chosen color's row as its own ramp: darker and bluer to the left, lighter and yellower to
  the right.

### Selection, flip and rotate

- With **S**, drag to mark a rectangle. Drag from inside it to move the piece.
- **Arrow keys** move 1 px (with **Shift**, 8 px). **Enter** settles and **Esc** undoes the move and drops the
  selection.
- **Ctrl+A** selects all and **Ctrl+D** drops it. **Ctrl+C**, **Ctrl+X** and **Ctrl+V** copy, cut and paste;
  **Delete** erases.
- With a selection, tools only paint inside it.
- **Shift+H** and **Shift+V** flip horizontally and vertically, and the rotate button turns 90° clockwise. With a
  selection, only the piece; without one, every frame of an animated art.
- **Clear** erases the whole chosen frame, in two clicks.

### Moving around the canvas

| Gesture | What it does |
| --- | --- |
| Mouse wheel | Zoom at the mouse. |
| **Alt**, **Space** or middle button + drag | Moves the view. |
| **Arrow keys**, with no selection | Move the view. |
| **0** | Fits the art. |
| **+** and **−** | Zoom at the center. |
| Click on the preview | Takes the view to that spot. |

The footer shows the position and color under the mouse. The right column shows the art at 1×, 2× and tiled, as in the
mosaic (with little room or a large art, only what fits whole). **Esc** with no selection does the same as **Cancel**.

### Animation (frames)

An art can have several frames, which play in the Image and the tiled background. Every Screen with the same art plays
in step, and a Screen only changes frame while it is in view.

- The **frame strip**, under the canvas, shows the thumbnails; the chosen one is the one you draw on. Click to choose
  and drag to reorder.
- **+** adds an empty frame after the chosen one, the duplicate button copies the chosen one and the remove button takes
  it out.
- **Onion skin:** the previous frame shows faded underneath, to draw the next one.
- **Speed:** 1 to 20 frames per second, with **‹ ›** or by typing. **Mode:** **Loop** (back to the first) or
  **Ping-pong**. The play button pauses and plays the preview (paused, it shows the chosen frame).
- Up to 64 frames, all the same size and with the same palette. Frames count toward the Screen's pixel limit: a
  32 × 32 art with 10 frames counts 10,240.
- Everything goes into **Ctrl+Z**. The palette applies to every frame; **Clear** and the tools, to the chosen one.

| Key | What it does |
| --- | --- |
| **,** and **.** | Previous and next frame. |
| **Alt+N** | New empty frame, after the chosen one. |
| **Alt+D** | Duplicates the chosen frame. |
| **Alt+O** | Turns the onion skin on and off. |
| **Alt+P** | Plays and pauses the preview. |
| **Ctrl+C** and **Ctrl+V** | Copy a piece of one frame and paste it into another. |

### Putting the art on the Screen

- **Image:** click **Image** in the **Pieces** list (or drag it onto the preview). It starts with the art picked in
  the **Art** tab (a pixel art or an image); with none picked, it starts empty, with **Pick the art or image**.
- **Picking the art:** in the **Style** tab, **Piece** section, click a thumbnail in the grid. The ones marked **On
  this screen** came with the Screen, from another player or from a code. Dragging an art from the **Art** tab
  onto the preview works too.
- In the same section: **Scale** (1× to 16×, or **Stretch**), **Align** (9 spots) and **Opacity**.
- The Screen keeps a **copy** of the art: changing or deleting the art later doesn't change the Screen. **Update from
  library** brings the current version.
- **Tiled background:** with nothing picked, set the **Background** to **Tiled pixel art** and pick the art in the
  grid. It repeats over the whole Screen, under the pieces, with **Scale** (1× to 8×) and **Opacity**.
- The **Pixels** line in the base shows how much the Screen's art adds up to, and the cap.

### Export and import by code

Take a layout or an art to another world, or to a friend, as text.

| Button | What it does |
| --- | --- |
| **Export** | Copies the code: in the base (under **Apply layout**), of the layout; in the **Art** tab, of the picked art. |
| **Import** | Reads the copied code. In the base, applies the layout to the draft (**Ctrl+Z** undoes); in the **Art** tab, makes a new art. An image code (**WA-IMAGEM1**) becomes an image upload. |
| **Copy as text** | Copies in a readable form, to edit by hand or ask an AI. **Import** reads both forms. |

- The layout takes its art along. Links to networks or storages you don't have are dropped: link them again in the
  **Data** tab.
- An invalid code shows a warning in the bottom bar, and nothing changes.
- To make art and Screens with an AI, there is a guide on the mod's GitHub: `docs/pixel-art-ai-guide.md`.

## Images and GIFs

Besides pixel art, the Screen shows real photos and animated GIFs, in the **Images** part of the **Art** tab.

1. Click **Upload file** and pick a PNG, JPG or GIF on your computer. Or copy a **WA-IMAGEM1** code and click
   **Import**.
2. The image goes to the server and into your library with the file's name. **Rename** (or double-click the row)
   changes the name, and **Remove**, with two clicks, takes it out of the library.
3. Drag the image onto the preview to make an **Image**, or pick it in the grid of the **Style** tab: images come
   after the art, under the **Images** label, and a GIF has the ▶ mark. The **Tiled background** takes images too.

| Limit | Default |
| --- | --- |
| File | Up to **8 MB** |
| Size | Up to **1024 × 1024** pixels |
| GIF | Up to **240** frames |
| Per Screen | Up to **8** different images |

The server can change these numbers. An image is always a file uploaded and kept in the world, never a link to the
internet.

- **GIF:** plays at the file's pace, only while someone looks at the Screen. Two Screens with the same GIF play
  together.
- **Copy code:** puts the image on the clipboard as **WA-IMAGEM1**, to take it to another world. A layout's code takes
  its images along when they fit; otherwise only the references, which work on the same server.
- **Hide other players' images:** in the client config (`config/wirelessautomate-client.toml`, `tv` section),
  `showOthersImages = false` swaps the images other players uploaded for a gray box. Yours stay.
- **Operators:** `/wa tela imagens` lists the world's images, with the size, who uploaded and how many Screens use
  them. `/wa tela imagem remover <hash>` takes an image off every Screen, and `/wa tela imagem banir <player>` stops
  someone from uploading (`desbanir` undoes it). The `tv.images` section of the server config turns images off and
  limits who uploads and the space.

## Access

In the **Access** tab (only the owner and operators see it), give other players the right to edit the Screen.

1. Type the name in **Invite player…** and pick a suggestion, or press **Enter**. Only players who have joined the
   server count.
2. With FTB Teams installed, add a team in **Add team…**.
3. To remove someone, click the row's **✕** twice.

Whoever is on the list edits like the owner: size, layout and links, within the networks each one can use. The links
the Screen already has stay when anyone saves, even to a network they can't use; a new link only works to a network
of whoever makes it. Watching the Screen stays open to everyone.

## Recipe

<RecipeFor id="wirelessautomate:signal_screen" />
''')

# =====================================================================================
# Problemas comuns
# =====================================================================================
page('troubleshooting.md', front('Problemas comuns', 'minecraft:barrier', 21) + '''
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
''', front('Troubleshooting', 'minecraft:barrier', 21) + '''
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
<RecipeFor id="wirelessautomate:crystal_repairer" />
<Recipe id="wirelessautomate:guide" />'''

page('recipe-editor.md', front('Editor de receitas', 'minecraft:writable_book', 22) + '''
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
''', front('Recipe editor', 'minecraft:writable_book', 22) + '''
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

page('recipes.md', front('Todas as receitas', 'minecraft:crafting_table', 23) + '''
# Todas as receitas

Todas com itens vanilla, na bancada.

| Também na bancada | Como |
| --- | --- |
| **Subir o tier** | Roteador ou armazenamento (Baú, Tanque, Bateria, Tanque Químico, Tanque de Source) + o cartão de um tier acima, em qualquer posição. |
| **Copiar um Cartão de Filtro** | Cartão configurado + cartões vazios: o original volta. |
| **Este guia** | Livro + redstone. |

''' + RECIPES + '''
''', front('All recipes', 'minecraft:crafting_table', 23) + '''
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
