"""Página pattern-dock.md do guia."""
from guia_base import *  # noqa: F401,F403


TABLE_REACH_PT = '''| Tier | Raio | Máquinas |
| --- | --- | --- |
| Básico | 6 | 8 |
| Avançado | 12 | 16 |
| Elite | 16 | 32 |
| Esmeralda | 24 | 48 |
| Allthemodium | 32 | 64 |
| Vibranium | 48 | 96 |
| Unobtainium | 64 | 128 |
| Ultimate | 96 | 256 |'''


TABLE_REACH_EN = '''| Tier | Radius | Machines |
| --- | --- | --- |
| Basic | 6 | 8 |
| Advanced | 12 | 16 |
| Elite | 16 | 32 |
| Emerald | 24 | 48 |
| Allthemodium | 32 | 64 |
| Vibranium | 48 | 96 |
| Unobtainium | 64 | 128 |
| Ultimate | 96 | 256 |'''


PAGINA = page('pattern-dock.md', front('Doca de Padrões', 'wirelessautomate:pattern_dock', 'integracoes', 17, item_ids=[
    'wirelessautomate:pattern_dock']) + '''
# Doca de Padrões

<ItemImage id="wirelessautomate:pattern_dock" scale="2" float="left" />

Leva os pedidos de um **Pattern Provider** do AE2 ou de um **Autocrafter** do Refined Storage 2 até as máquinas que
fazem a receita. Cada **lado** da Doca cuida de **um tipo de máquina**. Precisa do **AE2** ou do **RS2** para receber
pedidos.

<br clear="all" />

## Como usar

1. Coloque a Doca e encoste um **Pattern Provider** do AE2 (ou um **Autocrafter** do RS2, virado para ela) num lado
   dela.
2. Com o [Vinculador](linker.md), clique **nesse lado** da Doca. Depois dê **Ctrl + clique** numa máquina: as iguais
   que estão por perto entram naquele lado. Um clique simples vincula só uma máquina; numa já vinculada, desvincula.
3. Ponha no provedor **os padrões dessas máquinas**. Pronto: a CPU de crafting do AE2 (ou a rede do RS2) já pode
   pedir.

Quando você vincula a primeira máquina de um lado, a Doca descobre sozinha em que face entram os ingredientes e de qual
sai o resultado. A action bar mostra o que ela achou.

## O que a Doca faz sozinha

- Entrega **cada pedido inteiro** a uma máquina livre do lado.
- As máquinas **se revezam**: o próximo pedido vai para a seguinte.
- O resultado vai **direto para a rede** do provedor (a ME do AE2 ou a do RS2), sem passar pelo inventário dele, e o
  pedido o conta ali.
- Quando **todas as máquinas estão cheias**, o pedido espera. **Nada se perde**: se você quebrar a Doca com um resto
  de pedido esperando, ele vai junto no item ("Pedido pendente") e volta ao mesmo lado quando você a recoloca.

## Quem faz o quê

A Doca **não gasta energia nem combustível**. Ela só leva os pedidos.

- **Energia:** vincule as máquinas também a um [roteador](router.md). É ele que leva a energia até elas.
- **Combustível:** o que a receita gasta (carvão numa fornalha, por exemplo) vai **dentro do padrão**, como um
  ingrediente.

## Dicas

- **Multiplique os padrões até um lote que a máquina faz num tempo bom** (×8, ×64). Cada empurrão da CPU vai inteiro
  para uma máquina e a enche: com o padrão de 1 minério, cada máquina recebe um de cada vez; com o de 64, recebe um
  lote que a mantém ocupada. **Mais coprocessadores** na CPU continuam ajudando: são mais empurrões por vez, e mais
  máquinas trabalhando juntas. Um pedido pequeno ocupa poucas máquinas.
- **Não use a trava "até o resultado"** do Pattern Provider: o resultado não passa por ele, então a trava nunca solta.
  A linha do lado avisa ("Tire a trava do provedor").
- **Mais padrões no RS2:** encadeie **Autocrafters** (um virado para o outro, o último virado para a Doca). Os padrões
  de todos chegam ao mesmo lado.
- **Padrões de bancada** (bancada, cortador de pedra, forja) são do Molecular Assembler. A Doca os recusa.
- **Outro tipo de máquina?** Use **outro lado**. Um lado só cuida de um tipo, e a Doca tem seis lados.
- **Se a Doca errar as faces** de uma máquina, use o [Configurador](configurator.md) numa máquina do lado, com o gesto
  de sempre. O resultado vale para o lado inteiro.

## Alcance

O raio e o máximo de máquinas dependem do tier da Doca. As máquinas ficam dentro do raio, medido em cada eixo, e o
máximo conta a **Doca inteira**, somando os **seis lados**. O Cartão de Upgrade sobe o tier.

''' + TABLE_REACH_PT + '''

Os números são os padrões; o JEI mostra os do seu pack. Se a config baixar, as máquinas de fora continuam vinculadas,
mas não recebem pedidos. Máquina em área descarregada também espera.

## O que a tela mostra

Clique direito na Doca. A tela tem uma linha por lado, com o nome da máquina, quantas são e quantas estão trabalhando,
e o estado:

| Estado | O que quer dizer |
| --- | --- |
| Sem provedor neste lado | Há máquinas, mas nada encostado. Encoste um provedor |
| Nenhuma máquina vinculada | Há provedor, mas nenhuma máquina. Vincule com o Vinculador |
| Tire a trava do provedor | O provedor está em "travar até o resultado", que nunca solta com a Doca |
| Padrão de bancada recusado | O último pedido era de bancada, cortador ou forja |
| Pedido com item e fluido recusado | O último pedido misturava itens e fluidos |
| Provedor cheio | O resultado espera lugar: a rede cheia, sem energia ou sem canal |
| Todas cheias | Nenhuma máquina aceitou o último pedido; ele espera |
| Pedido pendente | A máquina ainda não recebeu o resto do último pedido; o lado espera por ele |
| Trabalhando | Uma máquina recebeu um pedido ou devolveu um resultado há pouco |
| Pronto | Tudo certo, esperando pedidos |
| livre | Nada encostado e nenhuma máquina |

Ao lado do estado a tela avisa quantas máquinas estão **fora do alcance** ou **em área descarregada**.

## Limites

- **Um tipo de máquina por lado.** Um lado de Fornalha não aceita Alto-forno.
- **Pedidos só de itens ou só de fluidos**, nesta versão. Um pedido com os dois é recusado.
- **Mesma dimensão** e dentro do raio do tier.
- Uma máquina pertence a **um lado de uma Doca só**.
- A Doca precisa do **AE2** ou do **RS2** para receber pedidos.
''', front('Pattern Dock', 'wirelessautomate:pattern_dock', 'integracoes', 17, item_ids=[
    'wirelessautomate:pattern_dock']) + '''
# Pattern Dock

<ItemImage id="wirelessautomate:pattern_dock" scale="2" float="left" />

Takes the orders of an AE2 **Pattern Provider** or a Refined Storage 2 **Autocrafter** to the machines that make the
recipe. Each **side** of the Dock handles **one kind of machine**. It needs **AE2** or **RS2** to receive orders.

<br clear="all" />

## How to use

1. Place the Dock and put an AE2 **Pattern Provider** (or an RS2 **Autocrafter**, facing it) against one of its
   sides.
2. With the [Linker](linker.md), click **that side** of the Dock. Then **Ctrl + click** a machine: the matching ones
   nearby join that side. A plain click links just one machine; on an already linked one, it unlinks it.
3. Put **the patterns for those machines** in the provider. Done: the AE2 crafting CPU (or the RS2 network) can now
   ask for them.

When you link the first machine of a side, the Dock works out on its own which face takes the ingredients and which one
gives the result. The action bar shows what it found.

## What the Dock does by itself

- It hands **each whole order** to a free machine of the side.
- The machines **take turns**: the next order goes to the next one.
- The result goes **straight to the provider's network** (AE2's ME or RS2's), without passing through its inventory,
  and the order counts it there.
- When **all the machines are full**, the order waits. **Nothing is lost**: if you break the Dock while the rest of an
  order is waiting, it goes along in the item ("Order pending") and returns to the same side when you place it again.

## Who does what

The Dock **spends no energy and no fuel**. It only carries the orders.

- **Energy:** also link the machines to a [router](router.md). The router brings the energy to them.
- **Fuel:** what the recipe burns (coal in a furnace, for example) goes **inside the pattern**, as an ingredient.

## Tips

- **Multiply the patterns up to a batch a machine finishes in good time** (×8, ×64). Each push of the CPU goes whole
  to one machine and fills it: with a 1-ore pattern, each machine gets one at a time; with a 64 one, it gets a batch
  that keeps it busy. **More co-processors** on the CPU still help: more pushes at once, more machines working
  together. A small order keeps few machines busy.
- **Don't use the provider's "lock until result"**: the result doesn't pass through it, so the lock never releases.
  The side's row warns you ("Remove the provider lock").
- **More patterns on RS2:** chain **Autocrafters** (one facing the other, the last one facing the Dock). The patterns
  of all of them reach the same side.
- **Crafting patterns** (crafting table, stonecutter, smithing) belong to the Molecular Assembler. The Dock refuses
  them.
- **Another kind of machine?** Use **another side**. A side handles one kind, and the Dock has six sides.
- **If the Dock gets the faces wrong** for a machine, use the [Configurator](configurator.md) on a machine of that side,
  with the usual gesture. The result holds for the whole side.

## Reach

The radius and the maximum number of machines depend on the Dock's tier. Machines must be inside the radius, measured on
each axis, and the maximum counts the **whole Dock**, adding up the **six sides**. The Upgrade Card raises the tier.

''' + TABLE_REACH_EN + '''

The numbers are the defaults; JEI shows your pack's. If the config goes down, the machines left outside stay linked but
get no orders. A machine in an unloaded area waits too.

## What the screen shows

Right-click the Dock. The screen has one row per side, with the machine's name, how many there are and how many are
working, and the state:

| State | What it means |
| --- | --- |
| No provider on this side | There are machines, but nothing attached. Place a provider |
| No machine linked | There is a provider, but no machine. Link them with the Linker |
| Remove the provider lock | The provider is set to "lock until result", which never releases with the Dock |
| Crafting pattern refused | The last order was a crafting, stonecutting or smithing one |
| Item and fluid order refused | The last order mixed items and fluids |
| Provider full | The result waits for room: the network is full, unpowered or without a channel |
| All full | No machine took the last order; it waits |
| Order pending | The machine has not taken the rest of the last order yet; the side waits for it |
| Working | A machine got an order or gave back a result a moment ago |
| Ready | All good, waiting for orders |
| free | Nothing attached and no machine |

Next to the state, the screen warns how many machines are **out of reach** or **in an unloaded area**.

## Limits

- **One kind of machine per side.** A Furnace side doesn't take a Blast Furnace.
- **Orders of items only or fluids only**, in this version. An order with both is refused.
- **Same dimension** and inside the tier's radius.
- A machine belongs to **one side of one Dock**.
- The Dock needs **AE2** or **RS2** to receive orders.
''')
