"""Página linker.md do guia."""
from guia_base import *  # noqa: F401,F403


PAGINA = page('linker.md', front('Vinculador', 'wirelessautomate:linker', 7, item_ids=['wirelessautomate:linker']) + '''
# Vinculador

<ItemImage id="wirelessautomate:linker" scale="2" float="left" />

Liga **máquinas** a um roteador de alcance, uma a uma ou uma área inteira. Também abre o roteador que
chega numa máquina.

<br clear="all" />

## Como usar

1. Ponha o Upgrade de Alcance num roteador e clique nele com o Vinculador.
2. Clique numa máquina para vinculá-la. Clique de novo para desvincular.
3. Para uma sala inteira: com Shift, clique em dois cantos, clique no ar para abrir a tela e clique
   em **Vincular área**.
4. Passe para o modo **Abrir** (Shift + roda do mouse) e clique numa máquina para configurar a face
   dela no roteador.

O Vinculador não mexe nas redes: escolha a rede de cada face na linha **Rede** da tela do roteador ou
pelo [Tablet](network-tablet.md).

## Gestos

| Gesto | Vincular | Abrir |
| --- | --- | --- |
| Clique num roteador | Escolhe o roteador | — |
| Clique numa máquina | Vincula ou desvincula | Abre o roteador que chega nela |
| Clique numa Tela de Sinal | Escolhe a Tela | — |
| Clique num Baú, Bateria ou Tanque do mod | Com uma Tela escolhida, liga ou desliga | Abre o roteador que chega nele |
| Clique no ar | Abre a tela | Mostra a dica do modo |
| Shift + clique em dois blocos | Marca os cantos da área | — |
| Shift + roda do mouse | Passa para **Abrir** | Passa para **Vincular** |

## A tela (modo Vincular)

- Mostra o roteador escolhido e as vagas usadas (por exemplo **7/32 máquinas**). Se ele ainda não
  vincula (sem o Upgrade de Alcance), o motivo aparece ali.
- **Vincular área** liga as máquinas da área que o roteador alcança, as mais perto primeiro, até o
  limite do tier.
- **Desvincular**, no cabeçalho, faz os cliques e o botão da área tirarem máquinas em vez de pôr.
- **Limpar** apaga os cantos.

A área pode ter até 262.144 blocos (por exemplo 64 × 64 × 64), e você precisa estar a até 64
blocos dela.

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
''', front('Linker', 'wirelessautomate:linker', 7, item_ids=['wirelessautomate:linker']) + '''
# Linker

<ItemImage id="wirelessautomate:linker" scale="2" float="left" />

Links **machines** to a range router, one at a time or a whole area. It also opens the router that
reaches a machine.

<br clear="all" />

## How to use it

1. Put the Range Upgrade in a router and click it with the Linker.
2. Click a machine to link it. Click it again to unlink.
3. For a whole room: holding Shift, click two corners, click the air to open the screen and click
   **Link area**.
4. Switch to **Open** mode (Shift + mouse wheel) and click a machine to set up its face in the
   router.

The Linker doesn't touch networks: pick each face's network in the router screen's **Network** row or
with the [Tablet](network-tablet.md).

## Gestures

| Gesture | Link | Open |
| --- | --- | --- |
| Click a router | Picks the router | — |
| Click a machine | Links or unlinks it | Opens the router that reaches it |
| Click a Signal Screen | Picks the Screen | — |
| Click a mod Chest, Battery or Tank | With a Screen picked, links or unlinks it | Opens the router that reaches it |
| Click the air | Opens the screen | Shows the mode's hint |
| Shift + click two blocks | Marks the area corners | — |
| Shift + mouse wheel | Goes to **Open** | Goes to **Link** |

## The screen (Link mode)

- Shows the chosen router and the slots used (for example **7/32 machines**). If it doesn't link yet
  (no Range Upgrade), the reason shows there.
- **Link area** links the area's machines the router reaches, nearest first, up to the tier's limit.
- **Unlink**, in the header, makes the clicks and the area button take machines out instead of
  putting them in.
- **Clear** erases the corners.

The area can be up to 262,144 blocks (for example 64 × 64 × 64), and you need to be within 64
blocks of it.

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
