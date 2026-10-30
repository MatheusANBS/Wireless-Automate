"""Página networks.md do guia."""
from guia_base import *  # noqa: F401,F403


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


PAGINA = page('networks.md', front('Redes', 'wirelessautomate:linker', 4) + '''
# Redes

Roteadores não se ligam uns aos outros: cada **face**, em cada aba, entra numa **rede**. Tudo do
mesmo tipo na mesma rede troca entre si; o modo da face diz quem envia e quem recebe.

''' + fill(SCENE_STORAGE, L1='A fornalha (Extrai) entrega nos barris (Armazém)',
           L2='Os barris (Armazém) entregam no baú (Insere)', FURNACE='Fornalha · Extrai',
           BARREL='Barril · Armazém', CHEST='Baú · Insere') + '''

## Como usar

1. Um roteador novo não está em rede nenhuma. Abra a tela dele e escolha a aba e a face.
2. Na linha **Rede**, escolha a rede.
3. Repita nas outras máquinas.

## Regras

| Regra | Explicação |
| --- | --- |
| **Rede por face** | A face de **Cima** da fornalha pode estar na rede "Mina" e a de **Baixo** na "Armazém". A energia pode ir para a "Base". |
| **Ordem de entrega** | Prioridade maior primeiro; empates se revezam. |
| **Rede ativa** | A que o [Tablet](network-tablet.md) usa como destino do **Mover**. Cada jogador tem a sua. |
| **Quem usa** | Pública: todos. Privada: o dono, os operadores, os **convidados** e, com o FTB Teams, os times adicionados. Só o dono (ou um operador) renomeia, convida e remove. |

## Como mudar a rede

| Jeito | Quantos de uma vez |
| --- | --- |
| Linha **Rede** da face, na tela do roteador | Uma face, numa aba |
| <ItemLink id="wirelessautomate:network_tablet" />, Selecionar | Todas as faces dos nós marcados, no tipo escolhido (ou em todos) |
| <ItemLink id="wirelessautomate:configurator" /> | A rede vai junto com a configuração colada |

## Alcance e chunks

| Situação | O que acontece |
| --- | --- |
| Destino longe demais | Fica de fora. O alcance depende do tier de quem envia ([Roteador](router.md)). |
| Outra dimensão | Só com origem **Esmeralda** ou acima. |
| Chunk descarregado | A rota pausa e volta sozinha quando o chunk carrega. |
| Manter trabalhando longe | Use o [Upgrade de Chunk Loading](chunk-loading.md). |
''', front('Networks', 'wirelessautomate:linker', 4) + '''
# Networks

Routers don't link to each other: each **face**, on each tab, joins a **network**. Everything of the
same type on the same network trades; the face mode says who sends and who receives.

''' + fill(SCENE_STORAGE, L1='The furnace (Extract) delivers to the barrels (Storage)',
           L2='The barrels (Storage) deliver to the chest (Insert)', FURNACE='Furnace · Extract',
           BARREL='Barrel · Storage', CHEST='Chest · Insert') + '''

## How to use it

1. A new router isn't in any network. Open its screen and pick the tab and the face.
2. On the **Network** row, pick the network.
3. Repeat on the other machines.

## Rules

| Rule | Explanation |
| --- | --- |
| **Network per face** | A furnace's **Top** face can be on the "Mine" network and its **Bottom** face on "Storage". The energy can go to "Base". |
| **Delivery order** | Higher priority first; ties take turns. |
| **Active network** | The one the [Tablet](network-tablet.md) uses as the target of **Move**. Every player has their own. |
| **Who can use it** | Public: everyone. Private: the owner, operators, **guests** and, with FTB Teams, the added teams. Only the owner (or an operator) renames, invites and removes. |

## Changing the network

| Way | How many at once |
| --- | --- |
| The face's **Network** row, on the router screen | One face, on one tab |
| <ItemLink id="wirelessautomate:network_tablet" />, Select | All faces of the checked nodes, in the chosen type (or every type) |
| <ItemLink id="wirelessautomate:configurator" /> | The network goes with the pasted configuration |

## Range and chunks

| Situation | What happens |
| --- | --- |
| Destination too far | Left out. Range depends on the sender's tier ([Router](router.md)). |
| Another dimension | Only from an **Emerald** or higher source. |
| Unloaded chunk | The route pauses and resumes on its own when the chunk loads. |
| Keep it working far away | Use the [Chunk Loading Upgrade](chunk-loading.md). |
''')
