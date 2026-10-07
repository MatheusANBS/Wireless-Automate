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
  <Block id="minecraft:blast_furnace" x="6" y="0" z="0" />
  <Block id="wirelessautomate:router" x="6" y="1" z="0" p:facing="up" p:tier="ultimate" />
  <BlockAnnotation x="0" y="1" z="0" color="#c8ccd2">
    {BASIC}
  </BlockAnnotation>
  <BlockAnnotation x="2" y="1" z="0" color="#e2b347">
    {ADVANCED}
  </BlockAnnotation>
  <BlockAnnotation x="4" y="1" z="0" color="#45d6cc">
    {ELITE}
  </BlockAnnotation>
  <BlockAnnotation x="6" y="1" z="0" color="#a06bff">
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

TIER_TABLE = {
    'pt': '''| Tier | Itens/s | Fluido e químico (mB/s) | Energia (FE/t) | Alcance |
| --- | --- | --- | --- | --- |
| **Básico** | 512 | 32.000 | 16.000 | 128 blocos |
| **Avançado** | 8.192 | 512.000 | 256.000 | 1.024 blocos |
| **Elite** | 131.072 | 8.000.000 | 4.000.000 | A dimensão inteira |
| **Ultimate** | Sem limite | Sem limite | Sem limite | Todas as dimensões |''',
    'en': '''| Tier | Items/s | Fluid and chemical (mB/s) | Energy (FE/t) | Range |
| --- | --- | --- | --- | --- |
| **Basic** | 512 | 32,000 | 16,000 | 128 blocks |
| **Advanced** | 8,192 | 512,000 | 256,000 | 1,024 blocks |
| **Elite** | 131,072 | 8,000,000 | 4,000,000 | The whole dimension |
| **Ultimate** | Unlimited | Unlimited | Unlimited | Every dimension |''',
}

PAGES = []


def page(name, pt, en):
    PAGES.append((name, pt, en))


# =====================================================================================
# Início
# =====================================================================================
page('index.md', front('Wireless Automate', 'wirelessautomate:router', parent=None) + '''
# Wireless Automate

<ItemImage id="wirelessautomate:router" scale="2" float="left" />

Transporte **sem fios** de itens, fluidos, energia e químicos do Mekanism. Prenda um roteador em
cada máquina, diga o que cada face dela faz e pronto: todos os roteadores da mesma rede trocam
recursos entre si, sem canos e sem lag.

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

## Por onde começar

1. Leia [Primeiros passos](getting-started.md): dois baús trocando itens em um minuto.
2. Entenda as [redes](networks.md) e os [filtros](filters.md).
3. Algo não funciona? Veja [Problemas comuns](troubleshooting.md).

## Este guia

Faça outro para um amigo: livro + redstone, na bancada. Com o mouse sobre um item do mod, no
inventário ou no JEI, segure **G** para abrir a página dele.

<Recipe id="wirelessautomate:guide" />

## Todas as páginas

<SubPages icons={true} />
''', front('Wireless Automate', 'wirelessautomate:router', parent=None) + '''
# Wireless Automate

<ItemImage id="wirelessautomate:router" scale="2" float="left" />

**Wireless** transport of items, fluids, energy and Mekanism chemicals. Attach a router to each
machine, say what each of its faces does and you're done: every router on the same network trades
resources with the others, with no pipes and no lag.

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

## Where to start

1. Read [Getting started](getting-started.md): two chests trading items in a minute.
2. Learn about [networks](networks.md) and [filters](filters.md).
3. Something not working? See [Troubleshooting](troubleshooting.md).

## This guide

Make one for a friend: book + redstone, in a crafting table. With the mouse over one of the mod's
items, in your inventory or in JEI, hold **G** to open its page.

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
| **2** | Clique com um roteador em qualquer face do baú A. Ele fica **preso** ao baú e já entra na sua rede ativa. Faça o mesmo no baú B. |
| **3** | Clique no roteador A. Na aba **Itens**, escolha a face **Cima** e o modo **Extrai**. |
| **4** | No roteador B, mesma face, modo **Insere**. |
| **5** | Coloque itens no baú A: eles aparecem no baú B. |

## Receita

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
| **2** | Use a router on any face of chest A. It **attaches** to the chest and joins your active network. Do the same on chest B. |
| **3** | Right-click router A. On the **Items** tab, pick the **Up** face and the **Extract** mode. |
| **4** | On router B, same face, **Insert** mode. |
| **5** | Put items in chest A: they show up in chest B. |

## Recipe

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
| **Tipos** | Itens, Fluidos, Energia e, com o Mekanism, Químicos. |
| **Faces configuráveis** | As seis faces da máquina, cada uma com modo, prioridade, redstone e filtro por tipo. |
| **Tier inicial** | Básico. Sobe com os [Cartões de Upgrade](upgrade-cards.md). |
| **Empilha** | 64 (o tier vai junto com o item ao quebrar). |

## Receita

<RecipeFor id="wirelessautomate:router" />

## Onde prender

''' + fill(SCENE_SIDES, TOP='Preso em cima da fornalha', SIDE='Preso na lateral: funciona igual') + '''

Tanto faz em qual face ele fica: pela tela, ele acessa todas as seis. Quebrar o bloco de trás solta
o roteador.

## A tela

| Parte | O que faz |
| --- | --- |
| **Abas** | Itens, Fluidos, Energia e Químicos. Cada aba tem a própria [rede](networks.md), escolhida no seletor ao lado das abas. |
| **Visor 3D** | Arraste para girar, use a roda para zoom e clique numa face da máquina para escolhê-la. |
| **Botões C N L B S O** | Escolhem a face: Cima, Norte, Leste, Baixo, Sul e Oeste. |
| **Modo** | O que a face escolhida faz (tabela abaixo). |
| **Mais** | Prioridade e redstone da face. |
| **Filtro** | O botão **Editar** abre o [filtro](filters.md) da face; ao lado, dois slots de [Cartão de Filtro](filter-card.md). |
| **Cabeçalho** | Nome, vazão atual, tier e o slot do [Upgrade de Chunk Loading](chunk-loading.md). |

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

A vazão vale **por face e por tipo**, contada em quem envia. Químicos usam o limite de fluido.

''' + TIER_TABLE['pt'] + '''

''' + fill(SCENE_TIERS, BASIC='Básico', ADVANCED='Avançado', ELITE='Elite', ULTIMATE='Ultimate') + '''

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
| **Types** | Items, Fluids, Energy and, with Mekanism, Chemicals. |
| **Configurable faces** | All six machine faces, each with mode, priority, redstone and filter per type. |
| **Starting tier** | Basic. Raised with [Upgrade Cards](upgrade-cards.md). |
| **Stacks to** | 64 (the tier stays on the item when broken). |

## Recipe

<RecipeFor id="wirelessautomate:router" />

## Where to attach it

''' + fill(SCENE_SIDES, TOP='Attached on top of the furnace', SIDE='Attached to the side: works the same') + '''

It doesn't matter which face it sits on: from its screen it reaches all six. Breaking the block
behind it drops the router.

## The screen

| Part | What it does |
| --- | --- |
| **Tabs** | Items, Fluids, Energy and Chemicals. Each tab has its own [network](networks.md), picked in the selector next to the tabs. |
| **3D viewer** | Drag to rotate, scroll to zoom and click a machine face to select it. |
| **U N E D S W buttons** | Pick the face: Up, North, East, Down, South and West. |
| **Mode** | What the selected face does (table below). |
| **More** | The face's priority and redstone. |
| **Filter** | The **Edit** button opens the face's [filter](filters.md); next to it are two [Filter Card](filter-card.md) slots. |
| **Header** | Name, current throughput, tier and the [Chunk Loading Upgrade](chunk-loading.md) slot. |

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

Throughput is **per face and per type**, counted at the sender. Chemicals use the fluid limit.

''' + TIER_TABLE['en'] + '''

''' + fill(SCENE_TIERS, BASIC='Basic', ADVANCED='Advanced', ELITE='Elite', ULTIMATE='Ultimate') + '''

In creative, middle-click picks the router in the block's tier.
''')

# =====================================================================================
# Cartões de upgrade
# =====================================================================================
page('upgrade-cards.md', front('Cartões de Upgrade', 'wirelessautomate:tier_core_advanced', 3, item_ids=[
    'wirelessautomate:tier_core_advanced', 'wirelessautomate:tier_core_elite', 'wirelessautomate:tier_core_ultimate']) + '''
# Cartões de Upgrade

<Row gap="8">
  <ItemImage id="wirelessautomate:tier_core_advanced" scale="2" />
  <ItemImage id="wirelessautomate:tier_core_elite" scale="2" />
  <ItemImage id="wirelessautomate:tier_core_ultimate" scale="2" />
</Row>

Todo roteador nasce **Básico**. Cada cartão sobe **um** tier: Básico → Avançado → Elite → Ultimate.
A configuração do roteador (faces, filtros, redes) não se perde.

## O que cada cartão aumenta

Valores padrão, **por face e por tipo**. O servidor pode mudá-los na config; o tooltip do cartão
mostra os valores do seu servidor. Químicos usam o limite de fluido.

| Cartão | Itens/s | Fluido e químico (mB/s) | Energia (FE/t) | Alcance |
| --- | --- | --- | --- | --- |
| ''' + item('tier_core_advanced') + ''' **Avançado** | 512 → **8.192** | 32.000 → **512.000** | 16.000 → **256.000** | 128 → **1.024 blocos** |
| ''' + item('tier_core_elite') + ''' **Elite** | 8.192 → **131.072** | 512.000 → **8.000.000** | 256.000 → **4.000.000** | 1.024 → **a dimensão inteira** |
| ''' + item('tier_core_ultimate') + ''' **Ultimate** | 131.072 → **sem limite** | 8.000.000 → **sem limite** | 4.000.000 → **sem limite** | **todas as dimensões** |

Cada passo multiplica a vazão por 16. "Sem limite" quer dizer que só o orçamento de tempo do mod
segura a vazão (veja [Desempenho e config](performance.md)). Alcance e dimensões contam pelo tier
de quem **envia**.

## Como usar

| Onde | Como |
| --- | --- |
| **No mundo** | Clique com o cartão do tier seguinte num roteador já colocado. |
| **Na bancada** | Um roteador + o cartão do tier seguinte, em qualquer posição. O nome do roteador continua. |

Não dá para pular tier: um roteador Básico não aceita o cartão Elite.

## Receitas

<RecipeFor id="wirelessautomate:tier_core_advanced" />
<RecipeFor id="wirelessautomate:tier_core_elite" />
<RecipeFor id="wirelessautomate:tier_core_ultimate" />
''', front('Upgrade Cards', 'wirelessautomate:tier_core_advanced', 3, item_ids=[
    'wirelessautomate:tier_core_advanced', 'wirelessautomate:tier_core_elite', 'wirelessautomate:tier_core_ultimate']) + '''
# Upgrade Cards

<Row gap="8">
  <ItemImage id="wirelessautomate:tier_core_advanced" scale="2" />
  <ItemImage id="wirelessautomate:tier_core_elite" scale="2" />
  <ItemImage id="wirelessautomate:tier_core_ultimate" scale="2" />
</Row>

Every router starts as **Basic**. Each card raises **one** tier: Basic → Advanced → Elite →
Ultimate. The router's configuration (faces, filters, networks) is kept.

## What each card raises

Default values, **per face and per type**. The server can change them in the config; the card's
tooltip shows your server's values. Chemicals use the fluid limit.

| Card | Items/s | Fluid and chemical (mB/s) | Energy (FE/t) | Range |
| --- | --- | --- | --- | --- |
| ''' + item('tier_core_advanced') + ''' **Advanced** | 512 → **8,192** | 32,000 → **512,000** | 16,000 → **256,000** | 128 → **1,024 blocks** |
| ''' + item('tier_core_elite') + ''' **Elite** | 8,192 → **131,072** | 512,000 → **8,000,000** | 256,000 → **4,000,000** | 1,024 → **the whole dimension** |
| ''' + item('tier_core_ultimate') + ''' **Ultimate** | 131,072 → **unlimited** | 8,000,000 → **unlimited** | 4,000,000 → **unlimited** | **every dimension** |

Each step multiplies throughput by 16. "Unlimited" means only the mod's time budget limits
throughput (see [Performance and config](performance.md)). Range and dimensions follow the
**sender's** tier.

## How to use

| Where | How |
| --- | --- |
| **In the world** | Use the next tier's card on a placed router. |
| **In a crafting table** | A router + the next tier's card, in any slots. The router's name is kept. |

Tiers can't be skipped: a Basic router won't take the Elite card.

## Recipes

<RecipeFor id="wirelessautomate:tier_core_advanced" />
<RecipeFor id="wirelessautomate:tier_core_elite" />
<RecipeFor id="wirelessautomate:tier_core_ultimate" />
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
| **Rede ativa** | Cada jogador tem uma rede ativa (a primeira leva o nome dele). Roteador colocado entra nela, em todas as abas. |
| **Rede por aba** | Os itens de uma fornalha podem ir para a rede "Linha de minério" e a energia dela para a rede "Base". |
| **Ordem de entrega** | Prioridade maior primeiro; empates se revezam (round-robin). |
| **Armazém** | Recebe de quem extrai e entrega para quem insere, sem ficar trocando com outro Armazém. |
| **Dono** | Só o dono da rede (ou um operador) pode pôr roteadores nela. |

## Como mudar a rede

| Jeito | Quantos de uma vez |
| --- | --- |
| Seletor de rede da aba, na tela do roteador | Uma aba de um roteador |
| <ItemLink id="wirelessautomate:linker" />, modo Único | Um roteador (todas as abas ou só um tipo) |
| <ItemLink id="wirelessautomate:linker" />, modo Área | Todos os roteadores carregados de uma área |
| <ItemLink id="wirelessautomate:network_tablet" />, Selecionar | Os nós que você marcar na lista |
| <ItemLink id="wirelessautomate:configurator" /> | A rede vai junto com a configuração colada |

## Alcance e chunks

| Situação | O que acontece |
| --- | --- |
| Destino longe demais | Fica de fora daquela origem. O alcance depende do tier de quem envia ([Roteador](router.md)). |
| Outra dimensão | Só com origem **Ultimate**. |
| Chunk descarregado | A rota pausa, sem custo, e volta quando o chunk carrega. |
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
| **Active network** | Every player has an active network (the first one is named after them). A placed router joins it, on every tab. |
| **Network per tab** | A furnace's items can go to the "Ore line" network and its energy to the "Base" network. |
| **Delivery order** | Higher priority first; ties take turns (round-robin). |
| **Storage** | Receives from extractors and delivers to inserters, without bouncing between two Storage faces. |
| **Owner** | Only the network's owner (or an operator) can put routers in it. |

## Changing the network

| Way | How many at once |
| --- | --- |
| The tab's network selector, on the router screen | One tab of one router |
| <ItemLink id="wirelessautomate:linker" />, Single mode | One router (every tab or one type) |
| <ItemLink id="wirelessautomate:linker" />, Area mode | Every loaded router in an area |
| <ItemLink id="wirelessautomate:network_tablet" />, Select | The nodes you check in the list |
| <ItemLink id="wirelessautomate:configurator" /> | The network goes with the pasted configuration |

## Range and chunks

| Situation | What happens |
| --- | --- |
| Destination too far | Left out for that source. Range depends on the sender's tier ([Router](router.md)). |
| Another dimension | Only from an **Ultimate** source. |
| Unloaded chunk | The route pauses, at no cost, and resumes when the chunk loads. |
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

## Como adicionar

| Jeito | Como |
| --- | --- |
| **Inventário** | Shift + clique no item (para fluidos e químicos, num balde ou tanque cheio). |
| **JEI** | Arraste da lista para a grade, ou Shift + clique na lista. Não precisa ter o item. |
| **Digitando** | Em **Mais**: `#tag`, `@mod` ou, nos químicos, o id (`mekanism:hydrogen`). |

## Opções

| Opção | O que faz |
| --- | --- |
| **Lista branca / negra** | Inverte o filtro. |
| **Componentes** (itens) | Ignorar: picareta encantada = picareta. Exigir: só iguais. |
| **Estoque** | No destino, aceita só até N. Na origem, mantém sempre N. Clique numa entrada para definir. |

Conferir um item custa o mesmo com 9 ou com milhares de entradas: o filtro é compilado.
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

## How to add

| Way | How |
| --- | --- |
| **Inventory** | Shift + click the item (for fluids and chemicals, a full bucket or tank). |
| **JEI** | Drag from the list onto the grid, or Shift + click in the list. You don't need the item. |
| **Typing** | Under **More**: `#tag`, `@mod` or, for chemicals, the id (`mekanism:hydrogen`). |

## Options

| Option | What it does |
| --- | --- |
| **Whitelist / blacklist** | Inverts the filter. |
| **Components** (items) | Ignore: enchanted pickaxe = pickaxe. Require: only identical. |
| **Stock** | At a destination, accept only up to N. At a source, always keep N. Click an entry to set it. |

Checking an item costs the same with 9 or thousands of entries: the filter is compiled.
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
| **Empilha** | 16 |
| **Receita** | Rende 2 cartões. |

## Comandos

| Gesto | O que faz |
| --- | --- |
| Clique direito no ar | Edita o filtro do cartão. |
| Shift + clique direito no ar (cartão vazio) | Alterna entre itens e fluidos. |
| Tela de filtro da face, em **Mais** | Exporta o filtro da face para o cartão, ou importa do cartão. |
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
| **Stacks to** | 16 |
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

<br clear="all" />

## Ficha

| | |
| --- | --- |
| **Modos** | Único e Área. |
| **Tipo** | Todos, Itens, Fluidos ou Energia. |
| **Empilha** | 1 |

## Comandos

| Gesto | O que faz |
| --- | --- |
| Clique num roteador | Põe o roteador na rede ativa (cria uma, se você não tiver). |
| Clique no ar | Abre a tela: rede ativa, tipo, modo e, em Área, a prévia e o botão **Vincular**. |
| Shift + clique no ar | Alterna entre **Único** e **Área**. |
| Shift + roda do mouse | Troca o tipo. Em **Todos**, todas as abas entram na rede; num tipo, só aquela aba. |
| Shift + clique em dois blocos (Área) | Marca os cantos da área. |

''' + fill(SCENE_AREA, BOX='Área marcada: todos os roteadores carregados dentro dela entram na rede',
           OTHER='Também entra: para o Vinculador, a máquina não importa', OTHER_COLOR='#3fc36b') + '''

## Limites da área

| Limite | Padrão |
| --- | --- |
| Tamanho máximo | 262.144 blocos |
| Distância máxima até você | 64 blocos |

O servidor pode mudar os dois na config (`linker`).

## Receita

<RecipeFor id="wirelessautomate:linker" />
''', front('Linker', 'wirelessautomate:linker', 7, item_ids=['wirelessautomate:linker']) + '''
# Linker

<ItemImage id="wirelessautomate:linker" scale="2" float="left" />

Picks your **active network** and puts routers in it, one at a time or a whole area at once.

<br clear="all" />

## Spec sheet

| | |
| --- | --- |
| **Modes** | Single and Area. |
| **Type** | All, Items, Fluids or Energy. |
| **Stacks to** | 1 |

## Actions

| Action | What it does |
| --- | --- |
| Click a router | Puts the router in the active network (creates one if you have none). |
| Click the air | Opens the screen: active network, type, mode and, in Area, the preview and the **Link** button. |
| Shift + click the air | Switches between **Single** and **Area**. |
| Shift + mouse wheel | Changes the type. On **All**, every tab joins the network; with a type, only that tab. |
| Shift + click two blocks (Area) | Marks the area corners. |

''' + fill(SCENE_AREA, BOX='Marked area: every loaded router inside joins the network',
           OTHER='Joins too: for the Linker, the machine does not matter', OTHER_COLOR='#3fc36b') + '''

## Area limits

| Limit | Default |
| --- | --- |
| Maximum size | 262,144 blocks |
| Maximum distance from you | 64 blocks |

The server can change both in the config (`linker`).

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
| **Empilha** | 1 |

## Comandos

| Gesto | Pincel | Área |
| --- | --- | --- |
| Shift + clique num roteador | Copia | Copia |
| Clique num roteador | Cola nele | Marca um canto |
| Clique num bloco | — | Marca um canto (o 3º recomeça) |
| Clique no ar | — | Cola nos roteadores da área presos à **mesma máquina** da cópia |
| Shift + clique num bloco sem roteador | Limpa a varinha | Limpa a varinha |
| Shift + clique no ar | Vai para Área | Vai para Pincel |

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
| **Stacks to** | 1 |

## Actions

| Action | Brush | Area |
| --- | --- | --- |
| Shift + click a router | Copy | Copy |
| Click a router | Paste on it | Mark a corner |
| Click a block | — | Mark a corner (the 3rd starts over) |
| Click the air | — | Paste into the area's routers attached to the **same machine** as the copy |
| Shift + click a block without a router | Clear the wand | Clear the wand |
| Shift + click the air | Switch to Area | Switch to Brush |

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
| **Empilha** | 1 |

## Abas

| Aba | O que mostra e faz |
| --- | --- |
| **Lista** | Todos os nós, com busca e filtro por papel (extrai, insere...). **Selecionar** move vários nós de rede de uma vez. Clique num nó para abrir a tela dele à distância. |
| **Mapa** | Vista de cima com cores por status. Clique num ponto para abrir o nó. |
| **Estatísticas** | Vazão por tipo, tempo do mod por tick, destinos cheios e chunks descarregados. |
| **Redes** | Criar redes, cor, membros e privacidade. |
| **Grupos** | Juntam as redes de um sistema para pausar e retomar tudo de uma vez. |

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
| **Stacks to** | 1 |

## Tabs

| Tab | What it shows and does |
| --- | --- |
| **List** | Every node, with search and a role filter (extracts, inserts...). **Select** moves many nodes between networks at once. Click a node to open its screen remotely. |
| **Map** | Top-down view colored by status. Click a point to open the node. |
| **Statistics** | Throughput per type, mod time per tick, full destinations and unloaded chunks. |
| **Networks** | Create networks, color, members and privacy. |
| **Groups** | Bundle the networks of one system to pause and resume them all at once. |

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
| **Limite** | 16 chunks por jogador (padrão); vários roteadores no mesmo chunk contam uma vez. |
| **Empilha** | 1 |

## Estados

| Estado no cabeçalho | Significa |
| --- | --- |
| **Ativo: chunks carregados** | O roteador e a máquina continuam trabalhando longe dos jogadores. |
| **Inativo: limite de chunks do dono** | Quem pôs o upgrade já força o máximo de chunks permitido. |
| **Inativo: desligado no servidor** | A config do servidor desligou o upgrade. |

## Config do servidor

| Chave | Padrão | O que faz |
| --- | --- | --- |
| `chunkLoading.enabled` | `true` | Liga ou desliga o upgrade. |
| `chunkLoading.maxChunksPerPlayer` | `16` | Chunks forçados por jogador (0 = sem limite). |

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
| **Limit** | 16 chunks per player (default); several routers in one chunk count once. |
| **Stacks to** | 1 |

## States

| State in the header | Meaning |
| --- | --- |
| **Active: chunks loaded** | The router and the machine keep working away from players. |
| **Inactive: owner's chunk limit** | Whoever placed the upgrade already forces as many chunks as allowed. |
| **Inactive: disabled on the server** | The server config turned the upgrade off. |

## Server config

| Key | Default | What it does |
| --- | --- | --- |
| `chunkLoading.enabled` | `true` | Turns the upgrade on or off. |
| `chunkLoading.maxChunksPerPlayer` | `16` | Chunks forced per player (0 = unlimited). |

## Recipe

<RecipeFor id="wirelessautomate:chunk_loader_upgrade" />
''')

# =====================================================================================
# Químicos
# =====================================================================================
page('chemicals.md', front('Químicos (Mekanism)', 'minecraft:glass_bottle', 11) + '''
# Químicos do Mekanism

Com o **Mekanism** instalado, o roteador ganha a aba **Químicos**: gases, líquidos de infusão,
pigmentos e slurries. Sem o Mekanism, a aba não aparece e o resto do mod funciona igual.

## O que funciona

| Recurso | Químicos |
| --- | --- |
| Modo, prioridade e redstone por face | Sim, como nas outras abas. |
| Rede própria na aba | Sim. |
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
''', front('Chemicals (Mekanism)', 'minecraft:glass_bottle', 11) + '''
# Mekanism chemicals

With **Mekanism** installed, the router gets a **Chemicals** tab: gases, infuse types, pigments
and slurries. Without Mekanism the tab doesn't show and the rest of the mod works the same.

## What works

| Feature | Chemicals |
| --- | --- |
| Mode, priority and redstone per face | Yes, like the other tabs. |
| Its own network on the tab | Yes. |
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
# Desempenho
# =====================================================================================
page('performance.md', front('Desempenho e config', 'minecraft:clock', 12) + '''
# Desempenho e config

O mod não faz tick por bloco: um gerenciador central move tudo, dentro de um **orçamento de tempo
por tick**. O que não cabe continua no tick seguinte, sem perder nada. Com muitas máquinas, a vazão
se divide, mas o lag nunca passa do teto.

## Como o mod se protege

| Mecanismo | O que faz |
| --- | --- |
| **Orçamento por tick** | Teto de tempo do mod: padrão de 1 ms, de 50 ms do tick. |
| **Orçamento adaptativo** | Se o servidor passar de 40 ms por tick, o teto cai aos poucos (até 25% a partir de 50 ms) e volta sozinho. |
| **Quem dorme não pesa** | Origens vazias e destinos cheios dormem e quase não custam. |
| **Revezamento** | Com o orçamento esgotado, todas as origens continuam movendo, cada uma um pouco menos. |

## Medir

| Comando | Mostra |
| --- | --- |
| `/wa profile` (operador) | ms por tick do mod e de cada rede, operações por segundo, origens e destinos acordados e dormindo. |
| Tablet › Estatísticas | Vazão por tipo e tempo do mod por tick, sem precisar ser operador. |

## Config do servidor

Arquivo `config/wirelessautomate-server.toml`:

| Chave | Padrão | O que faz |
| --- | --- | --- |
| `performance.tickBudgetMs` | `1.0` | Teto de tempo do mod por tick, em ms. Mais = mais vazão com muitas máquinas. |
| `performance.adaptiveBudget` | `true` | Reduz o teto quando o servidor está pesado. |
| `tiers.<tier>.itemsPerSecond` | por tier | Itens por segundo, por face e tipo (0 = sem limite). |
| `tiers.<tier>.fluidPerSecond` | por tier | Fluido e químico em mB/s (0 = sem limite). |
| `tiers.<tier>.energyPerTick` | por tier | Energia em FE/t (0 = sem limite). |
| `tiers.<tier>.range` | por tier | Alcance em blocos (0 = a dimensão inteira). |
| `tiers.<tier>.crossDimension` | só Ultimate | Permite rotas entre dimensões. |
| `chunkLoading.*` | | Veja [Upgrade de Chunk Loading](chunk-loading.md). |
| `linker.maxAreaVolume` / `maxDistance` | `262144` / `64` | Área do Vinculador e do Configurador. |
''', front('Performance and config', 'minecraft:clock', 12) + '''
# Performance and config

The mod doesn't tick per block: a central manager moves everything within a **time budget per
tick**. Whatever doesn't fit continues next tick, nothing is lost. With many machines throughput is
shared, but the lag never goes over the cap.

## How the mod protects your TPS

| Mechanism | What it does |
| --- | --- |
| **Budget per tick** | The mod's time cap: 1 ms by default, of the 50 ms tick. |
| **Adaptive budget** | If the server goes over 40 ms per tick, the cap drops gradually (down to 25% from 50 ms) and comes back on its own. |
| **Sleeping costs nothing** | Empty sources and full destinations sleep and barely cost anything. |
| **Taking turns** | With the budget used up, every source keeps moving, each a little less. |

## Measuring

| Command | Shows |
| --- | --- |
| `/wa profile` (operator) | ms per tick of the mod and of each network, operations per second, sources and destinations awake and sleeping. |
| Tablet › Statistics | Throughput per type and mod time per tick, no operator needed. |

## Server config

File `config/wirelessautomate-server.toml`:

| Key | Default | What it does |
| --- | --- | --- |
| `performance.tickBudgetMs` | `1.0` | The mod's time cap per tick, in ms. More = more throughput with many machines. |
| `performance.adaptiveBudget` | `true` | Lowers the cap when the server is struggling. |
| `tiers.<tier>.itemsPerSecond` | per tier | Items per second, per face and type (0 = unlimited). |
| `tiers.<tier>.fluidPerSecond` | per tier | Fluid and chemical in mB/s (0 = unlimited). |
| `tiers.<tier>.energyPerTick` | per tier | Energy in FE/t (0 = unlimited). |
| `tiers.<tier>.range` | per tier | Range in blocks (0 = the whole dimension). |
| `tiers.<tier>.crossDimension` | Ultimate only | Allows routes between dimensions. |
| `chunkLoading.*` | | See [Chunk Loading Upgrade](chunk-loading.md). |
| `linker.maxAreaVolume` / `maxDistance` | `262144` / `64` | Linker and Configurator area. |
''')

# =====================================================================================
# Problemas comuns
# =====================================================================================
page('troubleshooting.md', front('Problemas comuns', 'minecraft:barrier', 13) + '''
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
| Vazão menor que a do tier | O orçamento do mod está no limite. | Normal com muitas máquinas; veja [Desempenho e config](performance.md). |

Ainda com dúvida? O `/wa profile` mostra, por rede, quantas origens e destinos estão acordados ou
dormindo.
''', front('Troubleshooting', 'minecraft:barrier', 13) + '''
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
| Less throughput than the tier | The mod's budget is at its limit. | Normal with many machines; see [Performance and config](performance.md). |

Still stuck? `/wa profile` shows, per network, how many sources and destinations are awake or
sleeping.
''')

# =====================================================================================
# Todas as receitas
# =====================================================================================
RECIPES = '''<RecipeFor id="wirelessautomate:router" />
<RecipeFor id="wirelessautomate:tier_core_advanced" />
<RecipeFor id="wirelessautomate:tier_core_elite" />
<RecipeFor id="wirelessautomate:tier_core_ultimate" />
<RecipeFor id="wirelessautomate:filter_card" />
<RecipeFor id="wirelessautomate:linker" />
<RecipeFor id="wirelessautomate:configurator" />
<RecipeFor id="wirelessautomate:network_tablet" />
<RecipeFor id="wirelessautomate:chunk_loader_upgrade" />
<Recipe id="wirelessautomate:guide" />'''

page('recipes.md', front('Todas as receitas', 'minecraft:crafting_table', 14) + '''
# Todas as receitas

Todas com itens vanilla, na bancada.

| Também na bancada | Como |
| --- | --- |
| **Subir o tier** | Roteador + o cartão do tier seguinte, em qualquer posição. |
| **Copiar um Cartão de Filtro** | Cartão configurado + cartões vazios: o original volta. |
| **Este guia** | Livro + redstone. |

''' + RECIPES + '''
''', front('All recipes', 'minecraft:crafting_table', 14) + '''
# All recipes

All of them with vanilla items, in a crafting table.

| Also in the crafting table | How |
| --- | --- |
| **Raise the tier** | Router + the next tier's card, in any slots. |
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
