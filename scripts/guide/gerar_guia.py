"""Gera as páginas do livro-guia (GuideME) em inglês e em português.

    python scripts/guide/gerar_guia.py

As páginas vão para src/main/resources/assets/wirelessautomate/guides/wirelessautomate/guide/
(inglês, o padrão do GuideME) e para _pt_br/ (português). Cada página é escrita aqui nos dois
idiomas lado a lado, para não desalinharem; edite este arquivo, não os .md gerados.

Regras do GuideME que valem a pena lembrar:
- anotações de cena (BlockAnnotation, LineAnnotation...) com o texto em linha própria; numa linha
  só, elas não aparecem;
- o comando de teste é /guidemec wirelessautomate:guide open wirelessautomate:<página>.md;
- os números da tabela de tiers são os padrões de RouterTier (mantenha em dia).
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
    'energy': [1_000, 8_000, 64_000, 512_000, 4_096_000, 32_768_000, 262_144_000, 0],
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
| **3** | Com o Vinculador na mão, clique no roteador A e depois no B: os dois entram na sua rede ativa (o primeiro clique cria uma, se você não tiver). |
| **4** | Clique no roteador A. Na aba **Itens**, escolha a face **Cima** e o modo **Extrai**. |
| **5** | No roteador B, mesma face, modo **Insere**. |
| **6** | Coloque itens no baú A: eles aparecem no baú B. |

Depois de configurar o primeiro roteador de uma linha, use o [Configurador](configurator.md) para
copiar a configuração (e as redes) para os outros.

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
| **3** | Holding the Linker, click router A and then router B: both join your active network (the first click creates one if you have none). |
| **4** | Right-click router A. On the **Items** tab, pick the **Up** face and the **Extract** mode. |
| **5** | On router B, same face, **Insert** mode. |
| **6** | Put items in chest A: they show up in chest B. |

Once the first router of a line is set up, use the [Configurator](configurator.md) to copy its
configuration (and networks) to the others.

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
| **Faces configuráveis** | As seis faces da máquina, cada uma com modo, prioridade, redstone e filtro por tipo. |
| **Upgrades** | [Cartões de Upgrade](upgrade-cards.md) (clique no roteador) e o [Upgrade de Chunk Loading](chunk-loading.md) (no slot do topo da tela). |
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
| **2. Escolha a face** | Clique na face da máquina no modelo 3D (arraste para girar), ou use os botões C N L B S O (Cima, Norte, Leste, Baixo, Sul, Oeste). |
| **3. Escolha o modo** | Extrai, Insere, Armazém ou Nenhum (tabela abaixo). |
| **4. Ajustes (opcional)** | Em **Mais**: prioridade e redstone. Em **Editar**: o [filtro](filters.md) da face. |
| **5. Rede (opcional)** | No seletor ao lado das abas, escolha a [rede](networks.md) desta aba. |

Arraste a borda direita, a de baixo ou o canto para aumentar a tela; o visor 3D cresce. Com pouco espaço, as abas mostram só o ícone (o nome aparece ao passar o mouse).

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
| **Prioridade** | −999 a 999 (padrão 0) | Destinos com prioridade maior recebem primeiro; empates se revezam. |
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
| **Configurable faces** | All six machine faces, each with mode, priority, redstone and filter per type. |
| **Upgrades** | [Upgrade Cards](upgrade-cards.md) (use on the router) and the [Chunk Loading Upgrade](chunk-loading.md) (in the slot at the top of the screen). |
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
| **2. Pick the face** | Click the machine face on the 3D model (drag to rotate), or use the U N E D S W buttons (Up, North, East, Down, South, West). |
| **3. Pick the mode** | Extract, Insert, Storage or None (table below). |
| **4. Settings (optional)** | Under **More**: priority and redstone. Under **Edit**: the face's [filter](filters.md). |
| **5. Network (optional)** | In the selector next to the tabs, pick this tab's [network](networks.md). |

Drag the right edge, the bottom edge or the corner to make the screen bigger; the 3D view grows. When space is short, the tabs show only their icon (hover for the name).

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
| **Priority** | −999 to 999 (default 0) | Higher-priority destinations receive first; ties take turns. |
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
→ Unobtainium. Cada cartão leva o roteador direto ao tier dele, de **qualquer tier abaixo**: um
roteador Básico com o Cartão Esmeralda vira Esmeralda. A configuração do roteador (faces, filtros,
redes) não se perde.

## O que cada cartão aumenta

Por face e por tipo. O tooltip do cartão mostra os valores do seu servidor. Químicos usam o
limite de fluido. Source só com o Ars Nouveau.

''' + card_table('pt') + '''

Cada passo multiplica a vazão por 8. Alcance e dimensões contam pelo tier de quem **envia**.

## Como usar

| Onde | Como |
| --- | --- |
| **No mundo** | Clique com o cartão do tier desejado num roteador já colocado. |
| **Na bancada** | Um roteador + o cartão do tier desejado, em qualquer posição. O nome do roteador continua. |

O cartão só sobe: não aceita um roteador do mesmo tier ou de um tier acima dele. Como cada cartão leva o
anterior na receita, ir direto ao tier final é o caminho mais barato.

Os mesmos cartões sobem os armazenamentos ([Baú](wireless-chest.md), [Tanque](wireless-tank.md),
[Bateria](wireless-battery.md), [Tanque Químico](wireless-chemical-tank.md) e
[Tanque de Source](wireless-source-tank.md)), do mesmo jeito e sem
perder o conteúdo. A capacidade de cada tier está na página de cada um.

## Receitas

<RecipeFor id="wirelessautomate:tier_core_advanced" />
<RecipeFor id="wirelessautomate:tier_core_elite" />
<RecipeFor id="wirelessautomate:tier_core_emerald" />
<RecipeFor id="wirelessautomate:tier_core_ultimate" />

Com o Allthemodium, os cartões dele pedem o cartão anterior no centro e os materiais do próprio
metal: lingotes e blocos de Allthemodium; lingotes, blocos e liga Vibranium-Allthemodium; lingotes,
blocos e liga Unobtainium-Vibranium. O Ultimate passa a pedir o Cartão Unobtainium e, no ATM10 (com
o All The Tweaks), fragmentos de ATM Star, ovo do dragão e blocos de liga Unobtainium-Allthemodium.
O JEI mostra as receitas do seu pack.
''', front('Upgrade Cards', 'wirelessautomate:tier_core_advanced', 3, item_ids=[
    f'wirelessautomate:tier_core_{t[0]}' for t in TIERS[1:]]) + '''
# Upgrade Cards

<Row gap="8">
''' + CARD_IMAGES + '''
</Row>

Every router starts as **Basic**. The ladder is Basic → Advanced → Elite → Emerald → Ultimate.
With the **Allthemodium** mod, three steps go between Emerald and Ultimate: Allthemodium →
Vibranium → Unobtainium. Each card takes the router straight to its tier from **any lower tier**: a
Basic router with the Emerald Card becomes Emerald. The router's configuration (faces, filters,
networks) is kept.

## What each card raises

Per face and per type. The card's tooltip shows your server's values. Chemicals use the fluid
limit. Source only with Ars Nouveau.

''' + card_table('en') + '''

Each step multiplies throughput by 8. Range and dimensions follow the **sender's** tier.

## How to use

| Where | How |
| --- | --- |
| **In the world** | Use the card of the tier you want on a placed router. |
| **In a crafting table** | A router + the card of the tier you want, in any slots. The router's name is kept. |

Cards only go up: they won't take a router of the same tier or a higher one. Since each card takes the
previous one in its recipe, going straight to the final tier is the cheapest path.

The same cards raise the storages ([Chest](wireless-chest.md), [Tank](wireless-tank.md),
[Battery](wireless-battery.md), [Chemical Tank](wireless-chemical-tank.md) and
[Source Tank](wireless-source-tank.md)), the same way and
keeping their contents. Each tier's capacity is on each one's page.

## Recipes

<RecipeFor id="wirelessautomate:tier_core_advanced" />
<RecipeFor id="wirelessautomate:tier_core_elite" />
<RecipeFor id="wirelessautomate:tier_core_emerald" />
<RecipeFor id="wirelessautomate:tier_core_ultimate" />

With Allthemodium, its cards take the previous card in the middle and the metal's own materials:
Allthemodium ingots and blocks; Vibranium ingots, blocks and Vibranium-Allthemodium alloy;
Unobtainium ingots, blocks and Unobtainium-Vibranium alloy. Ultimate then takes the Unobtainium Card
and, in ATM10 (with All The Tweaks), ATM Star shards, a dragon egg and Unobtainium-Allthemodium alloy
blocks. JEI shows your pack's recipes.
''')

# =====================================================================================
# Redes
# =====================================================================================
page('networks.md', front('Redes', 'wirelessautomate:linker', 4) + '''
# Redes

Roteadores não se ligam uns aos outros: cada **aba** de um roteador entra numa **rede**, e tudo do
mesmo tipo na mesma rede troca entre si. Quem envia e quem recebe vem do modo das faces.

''' + fill(SCENE_STORAGE, L1='A fornalha (Extrai) entrega nos barris (Armazém)',
           L2='Os barris (Armazém) entregam no baú (Insere)', FURNACE='Fornalha · Extrai',
           BARREL='Barril · Armazém', CHEST='Baú · Insere') + '''

## Como funciona

| Regra | Explicação |
| --- | --- |
| **Sem rede ao colocar** | Um roteador novo não está em rede nenhuma: ponha-o numa rede (abaixo) para ele trabalhar. |
| **Rede ativa** | Cada jogador tem uma rede ativa (a primeira leva o nome dele). É nela que o Vinculador põe os roteadores. |
| **Rede por aba** | Os itens de uma fornalha podem ir para a rede "Linha de minério" e a energia dela para a rede "Base". |
| **Ordem de entrega** | Prioridade maior primeiro; empates se revezam (round-robin). |
| **Armazém** | Recebe de quem extrai e entrega para quem insere, sem ficar trocando com outro Armazém. |
| **Dono** | Só o dono da rede (ou um operador) pode pôr roteadores nela. |

## Como mudar a rede

| Jeito | Quantos de uma vez |
| --- | --- |
| Seletor de rede da aba, na tela do roteador | Uma aba de um roteador |
| <ItemLink id="wirelessautomate:linker" />, modo Único | Um roteador (as abas marcadas) |
| <ItemLink id="wirelessautomate:linker" />, modo Área | Todos os roteadores carregados de uma área |
| <ItemLink id="wirelessautomate:linker" />, rede **Nenhuma (desvincular)** | Tira as abas marcadas da rede, num roteador ou numa área |
| <ItemLink id="wirelessautomate:network_tablet" />, Selecionar | Os nós que você marcar na lista |
| <ItemLink id="wirelessautomate:configurator" /> | A rede vai junto com a configuração colada (todas as abas ou só um tipo) |

## Alcance e chunks

| Situação | O que acontece |
| --- | --- |
| Destino longe demais | Fica de fora daquela origem. O alcance depende do tier de quem envia ([Roteador](router.md)). |
| Outra dimensão | Só com origem **Esmeralda** ou acima. |
| Chunk descarregado | A rota pausa e volta sozinha quando o chunk carrega. |
| Quer manter trabalhando longe | Use o [Upgrade de Chunk Loading](chunk-loading.md). |
''', front('Networks', 'wirelessautomate:linker', 4) + '''
# Networks

Routers don't link to each other: each **tab** of a router joins a **network**, and everything of
the same type on the same network trades. Who sends and who receives comes from the face modes.

''' + fill(SCENE_STORAGE, L1='The furnace (Extract) delivers to the barrels (Storage)',
           L2='The barrels (Storage) deliver to the chest (Insert)', FURNACE='Furnace · Extract',
           BARREL='Barrel · Storage', CHEST='Chest · Insert') + '''

## How it works

| Rule | Explanation |
| --- | --- |
| **No network when placed** | A new router isn't in any network: put it in one (below) so it starts working. |
| **Active network** | Every player has an active network (the first one is named after them). It's where the Linker puts routers. |
| **Network per tab** | A furnace's items can go to the "Ore line" network and its energy to the "Base" network. |
| **Delivery order** | Higher priority first; ties take turns (round-robin). |
| **Storage** | Receives from extractors and delivers to inserters, without bouncing between two Storage faces. |
| **Owner** | Only the network's owner (or an operator) can put routers in it. |

## Changing the network

| Way | How many at once |
| --- | --- |
| The tab's network selector, on the router screen | One tab of one router |
| <ItemLink id="wirelessautomate:linker" />, Single mode | One router (the checked tabs) |
| <ItemLink id="wirelessautomate:linker" />, Area mode | Every loaded router in an area |
| <ItemLink id="wirelessautomate:linker" />, network **None (unlink)** | Takes the checked tabs out of their network, on one router or an area |
| <ItemLink id="wirelessautomate:network_tablet" />, Select | The nodes you check in the list |
| <ItemLink id="wirelessautomate:configurator" /> | The network goes with the pasted configuration (every tab or one type) |

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
| **Modos** | Único e Área. |
| **Abas** | Itens, Fluidos, Energia e, com o Mekanism, Químicos; com o Ars Nouveau, Source. Marque quantas quiser. |
| **Rede** | Uma das suas redes, ou **Nenhuma (desvincular)**. |

## Comandos

| Gesto | O que faz |
| --- | --- |
| Clique num roteador | Põe as abas marcadas do roteador na rede ativa (cria uma, se você não tiver). Em **Nenhuma (desvincular)**, tira essas abas da rede. |
| Clique no ar | Abre a tela: rede, abas, modo e, em Área, a prévia e o botão **Vincular** (ou **Desvincular**). |
| Shift + clique no ar | Alterna entre **Único** e **Área**. |
| Shift + roda do mouse | Troca as abas pelos atalhos: **Todos**, Itens, Fluidos, Energia, Químicos (com o Mekanism), Source (com o Ars Nouveau). Uma combinação marcada na tela volta para **Todos**. |
| Shift + clique em dois blocos (Área) | Marca os cantos da área. |

## Na tela

| Parte | Como usar |
| --- | --- |
| **Rede** | Clique numa rede para torná-la a ativa, ou em **+ Nova rede** para criar uma. |
| **Nenhuma (desvincular)** | A primeira linha da lista. Com ela escolhida, os mesmos gestos tiram as abas marcadas da rede em vez de pôr. Escolha uma rede para voltar a vincular. |
| **Abas** | Um botão colorido por tipo: clique para marcar ou desmarcar (marcado = borda e fundo na cor do tipo). **Todos** marca todos os tipos; com todos marcados, deixa só o primeiro. Só as abas marcadas mudam; as outras ficam como estão. Pelo menos uma fica marcada. |

Arraste a borda ou o canto para aumentar a tela; o mapa da área cresce.

''' + fill(SCENE_AREA, BOX='Área marcada: todos os roteadores carregados dentro dela entram na rede',
           OTHER='Também entra: para o Vinculador, a máquina não importa', OTHER_COLOR='#3fc36b') + '''

A área pode ter até 262.144 blocos (por exemplo 64 × 64 × 64), e você precisa estar a até 64
blocos dela.

## Exemplo: tirar uma área da rede, menos a energia

| Passo | O que fazer |
| --- | --- |
| **1** | Clique no ar para abrir a tela e escolha **Nenhuma (desvincular)**. |
| **2** | Deixe marcadas **Itens**, **Fluidos** e **Químicos** e desmarque **Energia**. |
| **3** | Shift + clique no ar para o modo **Área** e marque os dois cantos com Shift + clique. |
| **4** | Abra a tela de novo e clique em **Desvincular**: as abas de itens, fluidos e químicos saem da rede e a energia continua ligada. |

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
| **Modes** | Single and Area. |
| **Tabs** | Items, Fluids, Energy and, with Mekanism, Chemicals; with Ars Nouveau, Source. Check as many as you like. |
| **Network** | One of your networks, or **None (unlink)**. |

## Actions

| Action | What it does |
| --- | --- |
| Click a router | Puts the router's checked tabs in the active network (creates one if you have none). On **None (unlink)**, takes those tabs out of their network. |
| Click the air | Opens the screen: network, tabs, mode and, in Area, the preview and the **Link** (or **Unlink**) button. |
| Shift + click the air | Switches between **Single** and **Area**. |
| Shift + mouse wheel | Changes the tabs through the shortcuts: **All**, Items, Fluids, Energy, Chemicals (with Mekanism), Source (with Ars Nouveau). A combination checked on the screen goes back to **All**. |
| Shift + click two blocks (Area) | Marks the area corners. |

## On the screen

| Part | How to use it |
| --- | --- |
| **Network** | Click a network to make it active, or **+ New network** to create one. |
| **None (unlink)** | The first row of the list. While it's chosen, the same actions take the checked tabs out of their network instead. Pick a network to go back to linking. |
| **Tabs** | One colored button per type: click to mark or unmark it (marked = border and tint in the type's color). **All** marks every type; when all are marked, it leaves only the first. Only the marked tabs change; the others stay as they are. At least one stays marked. |

Drag the edge or the corner to make the screen bigger; the area map grows.

''' + fill(SCENE_AREA, BOX='Marked area: every loaded router inside joins the network',
           OTHER='Joins too: for the Linker, the machine does not matter', OTHER_COLOR='#3fc36b') + '''

The area can be up to 262,144 blocks (for example 64 × 64 × 64), and you need to be within 64
blocks of it.

## Example: unlink an area, keep the energy

| Step | What to do |
| --- | --- |
| **1** | Click the air to open the screen and pick **None (unlink)**. |
| **2** | Keep **Items**, **Fluids** and **Chemicals** checked and uncheck **Energy**. |
| **3** | Shift + click the air for **Area** mode and mark both corners with Shift + click. |
| **4** | Open the screen again and click **Unlink**: the items, fluids and chemicals tabs leave their network and the energy stays connected. |

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
| **Copia** | Faces, filtros, prioridades, redstone e a rede de cada aba. |
| **Guarda** | Uma cópia só, no próprio item. O tooltip mostra o que está copiado e os comandos do modo. |
| **Modos** | Pincel (padrão) e Área. |
| **Tipo colado** | Todos (padrão), Itens, Fluidos, Energia, Químicos (com o Mekanism) ou Source (com o Ars Nouveau). |

## Comandos

| Gesto | Pincel | Área |
| --- | --- | --- |
| Shift + clique num roteador | Copia | Copia |
| Clique num roteador | Cola nele | Marca um canto |
| Clique num bloco | — | Marca um canto (o 3º recomeça) |
| Clique no ar | — | Cola nos roteadores da área presos à **mesma máquina** da cópia |
| Shift + clique num bloco sem roteador | Limpa a varinha | Limpa a varinha |
| Shift + clique no ar | Vai para Área | Vai para Pincel |
| Shift + roda do mouse | Troca o tipo colado | Troca o tipo colado |

Copiar sempre copia tudo; o **tipo colado** escolhe o que vai para o roteador. Em **Todos**, todas as
abas. Num tipo só, apenas as faces e a rede daquela aba: as outras abas do roteador ficam como
estavam. Por exemplo, copie um roteador configurado só para fluidos, passe para **Fluidos** e cole
nos outros sem mexer nos itens nem na energia deles.

''' + fill(SCENE_AREA, BOX='Área marcada com o Configurador',
           OTHER='Fica como estava: está num baú, e a cópia veio de uma fornalha', OTHER_COLOR='#ff6b5e') + '''

## Bom saber

| | |
| --- | --- |
| **Orientação** | A cópia é relativa ao roteador: funciona com ele preso em qualquer face. |
| **Redes** | A rede de uma aba só é colada se você puder usá-la; senão, a aba fica com a de antes. |
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
| **Copies** | Faces, filters, priorities, redstone and each tab's network. |
| **Holds** | A single copy, in the item itself. The tooltip shows what's copied and the mode's actions. |
| **Modes** | Brush (default) and Area. |
| **Pasted type** | All (default), Items, Fluids, Energy, Chemicals (with Mekanism) or Source (with Ars Nouveau). |

## Actions

| Action | Brush | Area |
| --- | --- | --- |
| Shift + click a router | Copy | Copy |
| Click a router | Paste on it | Mark a corner |
| Click a block | — | Mark a corner (the 3rd starts over) |
| Click the air | — | Paste into the area's routers attached to the **same machine** as the copy |
| Shift + click a block without a router | Clear the wand | Clear the wand |
| Shift + click the air | Switch to Area | Switch to Brush |
| Shift + mouse wheel | Change the pasted type | Change the pasted type |

Copying always copies everything; the **pasted type** picks what goes to the router. On **All**,
every tab. On a single type, only that tab's faces and network: the router's other tabs stay as they
were. For example, copy a router set up only for fluids, switch to **Fluids** and paste on the others
without touching their items or energy.

''' + fill(SCENE_AREA, BOX='Area marked with the Configurator',
           OTHER='Left unchanged: it sits on a chest, and the copy came from a furnace', OTHER_COLOR='#ff6b5e') + '''

## Good to know

| | |
| --- | --- |
| **Orientation** | The copy is relative to the router: it works with the router on any face. |
| **Networks** | A tab's network is only pasted if you can use it; otherwise the tab keeps its own. |
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
| **Lista** | Todos os nós, com busca e filtro por papel (extrai, insere...). **Selecionar** move vários nós de rede de uma vez. Clique num nó para abrir a tela dele à distância. |
| **Mapa** | Vista de cima com cores por status. Clique num ponto para abrir o nó. |
| **Estatísticas** | Quanto cada rede move por tipo, destinos cheios e roteadores em chunks descarregados. |
| **Redes** | Criar redes, cor, membros e privacidade. |
| **Grupos** | Juntam as redes de um sistema para pausar e retomar tudo de uma vez. |

A aba Estatísticas tem um cartão por tipo. Clique num cartão para ver na Lista só os roteadores daquele tipo; o chip "Só …" na Lista tira o filtro.

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
| **List** | Every node, with search and a role filter (extracts, inserts...). **Select** moves many nodes between networks at once. Click a node to open its screen remotely. |
| **Map** | Top-down view colored by status. Click a point to open the node. |
| **Statistics** | How much each network moves per type, full destinations and routers in unloaded chunks. |
| **Networks** | Create networks, color, members and privacy. |
| **Groups** | Bundle the networks of one system to pause and resume them all at once. |

The Statistics tab has one card per type. Click a card to list only the routers of that type; the "Only …" chip in the List removes the filter.

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
| Achar um item | Digite na busca. **@mod** procura pelo mod (ex.: **@mekanism**). |
| Mudar a ordem | O botão ao lado da busca alterna: quantidade, nome e mod. |
| Pegar uma pilha | Clique no item. Botão direito: meia pilha. |
| Mandar direto para o inventário | **Shift** + clique no item. |
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
| Find an item | Type in the search box. **@mod** searches by mod (e.g. **@mekanism**). |
| Change the order | The button next to the search box cycles: amount, name and mod. |
| Take a stack | Click the item. Right click: half a stack. |
| Send it straight to the inventory | **Shift** + click the item. |
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
| Rede própria na aba | Sim. |
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
| Its own network on the tab | Yes. |
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
| Rede própria na aba | Sim. |
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
| Its own network on the tab | Yes. |
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
# Problemas comuns
# =====================================================================================
page('troubleshooting.md', front('Problemas comuns', 'minecraft:barrier', 18) + '''
# Problemas comuns

Algo não se move? Confira na ordem: quase sempre é uma destas.

| Sintoma | Causa provável | O que fazer |
| --- | --- | --- |
| Nada sai da origem | A face está em **Nenhum** ou **Insere**. | Na aba certa, ponha a face em **Extrai**. |
| Nada sai da origem | As abas estão em redes diferentes. | Confira o seletor de rede da aba nos dois roteadores. |
| Nada chega no destino | O destino está longe demais para o tier de quem envia. | Suba o tier da origem com um [Cartão de Upgrade](upgrade-cards.md). |
| Nada chega no destino | O destino está cheio. | Ele dorme até ter espaço e volta sozinho. |
| Só alguns itens passam | Um [filtro](filters.md) ou estoque está barrando. | Veja o filtro da face nos dois lados; filtro vazio passa tudo. |
| A face funciona e para | Redstone em **Com sinal** ou **Sem sinal**. | Ponha em **Ignorar** ou ajuste o sinal. |
| Para quando você se afasta | O chunk descarregou. | Use o [Upgrade de Chunk Loading](chunk-loading.md). |
| O modo aparece indisponível | A máquina não dá acesso àquele tipo por aquela face. | Escolha outra face no visor 3D, ou configure a máquina. |
| Químicos não se movem | A face da máquina do Mekanism está desligada nos lados dela. | Ligue a face com a ferramenta de configuração do Mekanism. |
| Não consigo pôr na rede | A rede é de outro jogador. | Peça ao dono, ou use uma rede sua. |
| Vazão menor que a do tier | Muitas máquinas movendo ao mesmo tempo. | Normal: a vazão se divide entre elas, sem travar o jogo. |

Ainda com dúvida? Abra o [Tablet de Rede](network-tablet.md): a aba **Estatísticas** mostra o que
cada rede está movendo e quais destinos estão cheios.
''', front('Troubleshooting', 'minecraft:barrier', 18) + '''
# Troubleshooting

Something not moving? Check in order: it's almost always one of these.

| Symptom | Likely cause | What to do |
| --- | --- | --- |
| Nothing leaves the source | The face is on **None** or **Insert**. | On the right tab, set the face to **Extract**. |
| Nothing leaves the source | The tabs are on different networks. | Check the tab's network selector on both routers. |
| Nothing reaches the destination | It's too far for the sender's tier. | Raise the source's tier with an [Upgrade Card](upgrade-cards.md). |
| Nothing reaches the destination | The destination is full. | It sleeps until there's room and comes back on its own. |
| Only some items pass | A [filter](filters.md) or stock limit is blocking. | Check the face filter on both sides; an empty filter lets everything through. |
| The face works, then stops | Redstone set to **With signal** or **No signal**. | Set it to **Ignore** or fix the signal. |
| It stops when you walk away | The chunk unloaded. | Use the [Chunk Loading Upgrade](chunk-loading.md). |
| The mode shows as unavailable | The machine gives no access to that type through that face. | Pick another face in the 3D viewer, or configure the machine. |
| Chemicals don't move | The Mekanism machine's face is disabled in its side config. | Enable the face with Mekanism's configurator tool. |
| Can't put it in a network | The network belongs to another player. | Ask the owner, or use one of yours. |
| Less throughput than the tier | Many machines moving at once. | Normal: throughput is shared between them, without freezing the game. |

Still stuck? Open the [Network Tablet](network-tablet.md): the **Statistics** tab shows what each
network is moving and which destinations are full.
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
<Recipe id="wirelessautomate:guide" />'''

page('recipes.md', front('Todas as receitas', 'minecraft:crafting_table', 19) + '''
# Todas as receitas

Todas com itens vanilla, na bancada.

| Também na bancada | Como |
| --- | --- |
| **Subir o tier** | Roteador ou armazenamento (Baú, Tanque, Bateria, Tanque Químico, Tanque de Source) + o cartão de um tier acima, em qualquer posição. |
| **Copiar um Cartão de Filtro** | Cartão configurado + cartões vazios: o original volta. |
| **Este guia** | Livro + redstone. |

''' + RECIPES + '''
''', front('All recipes', 'minecraft:crafting_table', 19) + '''
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
