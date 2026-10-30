"""Página router.md do guia."""
from guia_base import *  # noqa: F401,F403


PORT = {m: f'![{m}](images/port_{m}.png)' for m in ('extract', 'insert', 'both', 'none')}


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


def tier_table(lang):
    head = {'pt': '| Tier | Itens/s | Fluido e químico (mB/s) | Energia (FE/t) | Source/s | Alcance |',
            'en': '| Tier | Items/s | Fluid and chemical (mB/s) | Energy (FE/t) | Source/s | Range |'}[lang]
    rows = [head, '| --- | --- | --- | --- | --- | --- |']
    for i in range(len(TIERS)):
        rows.append(f'| **{tier_name(lang, i)}** | ' + ' | '.join(num(lang, RATES[k][i]) for k in RATES)
                    + f' | {RANGE[lang][i]} |')
    tail = {'pt': 'Source só com o Ars Nouveau.', 'en': 'Source only with Ars Nouveau.'}[lang]
    return '\n'.join(rows) + '\n\n' + ATM_NOTE[lang] + ' ' + tail


TIER_TABLE = {'pt': tier_table('pt'), 'en': tier_table('en')}


PAGINA = page('router.md', front('Roteador Wireless', 'wirelessautomate:router', 2, item_ids=['wirelessautomate:router']) + '''
# Roteador Wireless

<BlockImage id="wirelessautomate:router" p:facing="up" p:tier="basic" scale="3" float="left" />

Prenda o roteador numa máquina e ela entra na rede pelas **seis faces de uma vez**. Sem canos.

<br clear="all" />

## Como usar

1. Coloque o roteador em qualquer face da máquina. Tanto faz qual: ele alcança as seis.
2. Clique direito no roteador para abrir a tela.
3. Escolha a aba do tipo: Itens, Fluidos ou Energia (Químicos com o Mekanism, Source com o Ars Nouveau).
4. Clique numa face da máquina, no modelo 3D ou no cubo do painel.
5. Clique no botão da face até chegar no modo certo.
6. Na linha **Rede**, escolha a [rede](networks.md) da face.

Pronto: a face já troca recursos com as outras faces da mesma rede.

## Receita

<RecipeFor id="wirelessautomate:router" />

## Onde prender

''' + fill(SCENE_SIDES, TOP='Preso em cima da fornalha', SIDE='Preso na lateral: funciona igual') + '''

Quebrar o bloco de trás solta o roteador. Shift + clique direito com a mão vazia gira o roteador; as
faces continuam como estavam.

## Modos da face

| Modo | O que faz | Use em |
| --- | --- | --- |
| ''' + PORT['extract'] + ''' **Extrai** | Tira da máquina. | Saídas: fornalha, gerador, minerador. |
| ''' + PORT['insert'] + ''' **Insere** | Põe na máquina. | Entradas: fornalha, máquina, baú final. |
| ''' + PORT['both'] + ''' **Armazém** | Recebe de quem extrai e entrega para quem insere. Não troca com outro Armazém. | Baús de estoque. |
| ''' + PORT['none'] + ''' **Nenhum** | A face fica de fora. | O resto. |

## Na tela

| Gesto | O que faz |
| --- | --- |
| **Clique** no botão da face | Avança o modo. |
| **Clique direito** | Volta o modo. |
| **Shift + clique** | Põe em Nenhum. |
| **Arrastar** o modelo 3D | Gira a máquina. |
| **Arrastar** a borda ou o canto | Aumenta a tela. |
| **Copiar** e **Colar** | Leva tudo de uma face para outra do mesmo tipo. |
| **Limpar** (dois cliques) | Põe em Nenhum as faces da aba. Com Shift no segundo clique, de todas as abas. |

Os lados (Frente, Trás, Esquerda, Direita, Cima, Baixo) são os da máquina, olhando de frente para
ela. Máquina sem frente, como um barril: Frente = Norte.

## Ajustes da face

| Ajuste | Como funciona |
| --- | --- |
| **Prioridade** | De −999 a 999 (padrão 0). Quem tem mais recebe primeiro; empates se revezam. Com Shift, muda de 10 em 10. |
| **Redstone** | Ignorar (padrão), Com sinal ou Sem sinal no roteador. |
| **Filtro** | **Editar** abre o [filtro](filters.md) da face. Ao lado, dois slots para [Cartão de Filtro](filter-card.md). |

## Upgrades

- [Cartões de Upgrade](upgrade-cards.md): Shift + clique no roteador. Mais vazão e alcance.
- [Upgrade de Chunk Loading](chunk-loading.md): no slot do topo da tela. O roteador trabalha com você longe.
- **Upgrade de Alcance:** o roteador atende várias máquinas ao redor. A lista delas aparece embaixo
  do visor: o ✎ dá um nome e o olho ◉ acende a máquina no mundo.

## Vazão por tier

<Row gap="12">
  <ItemImage id="wirelessautomate:router" scale="2" components="minecraft:block_state={tier:'basic'}" />
  <ItemImage id="wirelessautomate:router" scale="2" components="minecraft:block_state={tier:'advanced'}" />
  <ItemImage id="wirelessautomate:router" scale="2" components="minecraft:block_state={tier:'elite'}" />
  <ItemImage id="wirelessautomate:router" scale="2" components="minecraft:block_state={tier:'emerald'}" />
  <ItemImage id="wirelessautomate:router" scale="2" components="minecraft:block_state={tier:'ultimate'}" />
</Row>

A vazão vale **por face e por tipo**. Químicos usam o limite de fluido. O servidor pode mudar esses
números: o tooltip do cartão de upgrade mostra os dele.

''' + TIER_TABLE['pt'] + '''

''' + fill(SCENE_TIERS, BASIC='Básico', ADVANCED='Avançado', ELITE='Elite', EMERALD='Esmeralda', ULTIMATE='Ultimate') + '''

No criativo, o clique do meio pega o roteador já no tier do bloco.
''', front('Wireless Router', 'wirelessautomate:router', 2, item_ids=['wirelessautomate:router']) + '''
# Wireless Router

<BlockImage id="wirelessautomate:router" p:facing="up" p:tier="basic" scale="3" float="left" />

Attach the router to a machine and the machine joins the network through **all six faces at once**.
No pipes.

<br clear="all" />

## How to use it

1. Place the router on any face of the machine. Any face works: it reaches all six.
2. Right-click the router to open its screen.
3. Pick the type tab: Items, Fluids or Energy (Chemicals with Mekanism, Source with Ars Nouveau).
4. Click a machine face, on the 3D model or on the panel's cube.
5. Click the face's button until it shows the mode you want.
6. On the **Network** row, pick the face's [network](networks.md).

Done: the face now trades resources with the other faces on the same network.

## Recipe

<RecipeFor id="wirelessautomate:router" />

## Where to attach it

''' + fill(SCENE_SIDES, TOP='Attached on top of the furnace', SIDE='Attached to the side: works the same') + '''

Breaking the block behind it drops the router. Shift + right-click with an empty hand turns the
router; the faces keep their settings.

## Face modes

| Mode | What it does | Use it on |
| --- | --- | --- |
| ''' + PORT['extract'] + ''' **Extract** | Takes out of the machine. | Outputs: furnace, generator, miner. |
| ''' + PORT['insert'] + ''' **Insert** | Puts into the machine. | Inputs: furnace, machine, final chest. |
| ''' + PORT['both'] + ''' **Storage** | Receives from extractors and delivers to inserters. Doesn't trade with another Storage. | Stock chests. |
| ''' + PORT['none'] + ''' **None** | The face is left out. | Everything else. |

## On the screen

| Gesture | What it does |
| --- | --- |
| **Click** the face's button | Next mode. |
| **Right-click** | Previous mode. |
| **Shift + click** | Sets None. |
| **Drag** the 3D model | Turns the machine. |
| **Drag** the edge or corner | Makes the screen bigger. |
| **Copy** and **Paste** | Copies everything from one face to another of the same type. |
| **Clear** (two clicks) | Sets the tab's faces to None. With Shift on the second click, every tab. |

The sides (Front, Back, Left, Right, Top, Bottom) are the machine's, as seen facing it. A machine
without a front, like a barrel: Front = North.

## Face settings

| Setting | How it works |
| --- | --- |
| **Priority** | From −999 to 999 (default 0). Higher receives first; ties take turns. Hold Shift to step by 10. |
| **Redstone** | Ignore (default), With signal or No signal on the router. |
| **Filter** | **Edit** opens the face's [filter](filters.md). Next to it, two slots for [Filter Cards](filter-card.md). |

## Upgrades

- [Upgrade Cards](upgrade-cards.md): Shift + use on the router. More throughput and range.
- [Chunk Loading Upgrade](chunk-loading.md): in the slot at the top of the screen. The router keeps
  working while you're away.
- **Range Upgrade:** the router serves several machines around it. Their list sits under the viewer:
  ✎ names a machine and the eye ◉ lights it up in the world.

## Throughput per tier

<Row gap="12">
  <ItemImage id="wirelessautomate:router" scale="2" components="minecraft:block_state={tier:'basic'}" />
  <ItemImage id="wirelessautomate:router" scale="2" components="minecraft:block_state={tier:'advanced'}" />
  <ItemImage id="wirelessautomate:router" scale="2" components="minecraft:block_state={tier:'elite'}" />
  <ItemImage id="wirelessautomate:router" scale="2" components="minecraft:block_state={tier:'emerald'}" />
  <ItemImage id="wirelessautomate:router" scale="2" components="minecraft:block_state={tier:'ultimate'}" />
</Row>

Throughput is **per face and per type**. Chemicals use the fluid limit. Your server may change these
numbers: the upgrade card's tooltip shows its values.

''' + TIER_TABLE['en'] + '''

''' + fill(SCENE_TIERS, BASIC='Basic', ADVANCED='Advanced', ELITE='Elite', EMERALD='Emerald', ULTIMATE='Ultimate') + '''

In creative, middle-click picks the router in the block's tier.
''')
