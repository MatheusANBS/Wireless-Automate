"""Página linker.md do guia."""
from guia_base import *  # noqa: F401,F403


PAGINA = page('linker.md', front('Vinculador', 'wirelessautomate:linker', 'ferramentas', 7, item_ids=['wirelessautomate:linker']) + '''
# Vinculador

<ItemImage id="wirelessautomate:linker" scale="2" float="left" />

Liga **máquinas** a um roteador de alcance, uma a uma ou todas as do mesmo bloco. Também abre o
roteador que chega numa máquina.

<br clear="all" />

## Como usar

1. Ponha o Upgrade de Alcance num roteador e clique nele com o Vinculador. Clique nele de novo para
   soltá-lo. Com o Vinculador na mão, o cubo do raio do roteador aparece no mundo, e as máquinas
   vinculadas acendem na cor do grupo; as que estão fora do raio ou além do limite ficam cinza.
2. Clique numa máquina para vinculá-la. Clique nela de novo para desvincular. Se o grupo dela fica
   vazio, os cartões dele voltam para o seu inventário. Uma máquina é de um roteador só: se ela já é de
   outro (vinculada ou presa nele), a action bar diz de qual.
3. Para vincular todas as máquinas iguais de uma vez, segure **Ctrl** (**Cmd** no Mac) e clique numa
   delas: entram as do mesmo bloco **encostadas nela ou umas nas outras** (pela face, pela aresta ou
   pela ponta), como uma fila ou uma parede, e dentro do raio do roteador, as mais perto **da clicada**
   primeiro, até o limite do tier. A action bar diz quantas, por exemplo
   **Vinculou 7 Fornalhas**; se o limite cortou, **Vinculou 32 Fornalhas · limite do tier**. Correndo, o Ctrl é o da
   corrida e o clique vale como um clique normal.
4. Para desvincular as encostadas de uma vez, faça o mesmo gesto numa máquina que já está
   vinculada.
5. Clique no ar para ver o roteador escolhido. **Shift** + clique no ar o solta.
6. Passe para o modo **Abrir** (Shift + roda do mouse) e clique numa máquina para configurar a face
   dela no roteador.

O Vinculador não mexe nas redes: escolha a rede de cada face na linha **Rede** da tela do roteador ou
pelo [Tablet](network-tablet.md).

## Gestos

| Gesto | Vincular | Abrir |
| --- | --- | --- |
| Clique num roteador | Escolhe o roteador | — |
| Clique numa máquina | Vincula ou desvincula | Abre o roteador que chega nela |
| Ctrl (Cmd no Mac) + clique numa máquina | Vincula as do mesmo bloco encostadas nela, no raio; se ela já está vinculada, desvincula as encostadas | — |
| Clique numa Tela de Sinal | Escolhe a Tela | — |
| Clique num Baú, Bateria ou Tanque do mod | Com uma Tela escolhida, liga ou desliga | Abre o roteador que chega nele |
| Clique no ar | Mostra o roteador escolhido | Mostra a dica do modo |
| Shift + clique no ar | Solta o roteador e a Tela escolhidos | Mostra a dica do modo |
| Shift + roda do mouse | Passa para **Abrir** | Passa para **Vincular** |

## Modo Abrir

Se mais de um roteador chega na máquina (o preso nela e os de alcance), aparece uma lista com o nome,
se ele está **preso** ou chega por **alcance**, e a distância. Clique numa linha para abrir. Só entram
roteadores ao seu alcance e que você pode tocar.

## Ligar armazenamentos a uma Tela

1. Clique numa [Tela de Sinal](signal-screen.md). A action bar mostra quantos armazenamentos ela já tem.
2. Clique num **Baú, Bateria ou Tanque** do mod para ligá-lo. Clique de novo para desligar.

Sem limite de distância nem de dimensão. Você precisa poder editar a Tela. Se o chunk do
armazenamento estiver descarregado, a Tela mantém o último número, apagado.

## Receita

<RecipeFor id="wirelessautomate:linker" />
''', front('Linker', 'wirelessautomate:linker', 'ferramentas', 7, item_ids=['wirelessautomate:linker']) + '''
# Linker

<ItemImage id="wirelessautomate:linker" scale="2" float="left" />

Links **machines** to a range router, one at a time or all the ones of the same block. It also opens
the router that reaches a machine.

<br clear="all" />

## How to use it

1. Put the Range Upgrade in a router and click it with the Linker. Click it again to release it.
   With the Linker in hand, the router's range cube shows in the world, and the linked machines light
   up in their group's color; the ones out of range or over the limit show in gray.
2. Click a machine to link it. Click it again to unlink it. If its group ends up empty, the group's
   cards go back to your inventory. A machine belongs to one router only: if it already belongs to
   another (linked or attached to it), the action bar says which.
3. To link all the matching machines at once, hold **Ctrl** (**Cmd** on Mac) and click one of them:
   the ones of the same block **touching it or each other** (by a face, an edge or a corner), like a
   row or a wall, and within the router's radius go in, nearest **to the clicked one** first, up to the
   tier's limit. The action bar says how many, for example
   **Linked 7 Furnaces**; if the limit cut it, **Linked 32 Furnaces · tier limit**. While sprinting, Ctrl is
   the sprint key and the click counts as a normal click.
4. To unlink the touching ones at once, do the same gesture on a machine that is already linked.
5. Click the air to see the chosen router. **Shift** + click the air releases it.
6. Switch to **Open** mode (Shift + mouse wheel) and click a machine to set up its face in the
   router.

The Linker doesn't touch networks: pick each face's network in the router screen's **Network** row or
with the [Tablet](network-tablet.md).

## Gestures

| Gesture | Link | Open |
| --- | --- | --- |
| Click a router | Picks the router | — |
| Click a machine | Links or unlinks it | Opens the router that reaches it |
| Ctrl (Cmd on Mac) + click a machine | Links the ones of the same block touching it, in range; if it is already linked, unlinks the touching ones | — |
| Click a Signal Screen | Picks the Screen | — |
| Click a mod Chest, Battery or Tank | With a Screen picked, links or unlinks it | Opens the router that reaches it |
| Click the air | Shows the chosen router | Shows the mode's hint |
| Shift + click the air | Releases the chosen router and Screen | Shows the mode's hint |
| Shift + mouse wheel | Goes to **Open** | Goes to **Link** |

## Open mode

If more than one router reaches the machine (the one attached to it and the range ones), a list shows
the name, whether it is **attached** or reaches by **range**, and the distance. Click a row to open it.
Only routers within your reach that you can touch are listed.

## Linking storages to a Screen

1. Click a [Signal Screen](signal-screen.md). The action bar shows how many storages it already has.
2. Click a mod **Chest, Battery or Tank** to link it. Click it again to unlink.

No limit on distance or dimension. You must be allowed to edit the Screen. If the storage's chunk is
unloaded, the Screen keeps the last number, dimmed.

## Recipe

<RecipeFor id="wirelessautomate:linker" />
''')
