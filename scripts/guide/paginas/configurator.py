"""Página configurator.md do guia."""
from guia_base import *  # noqa: F401,F403


PAGINA = page('configurator.md', front('Configurador', 'wirelessautomate:configurator', 8, item_ids=['wirelessautomate:configurator']) + '''
# Configurador

<ItemImage id="wirelessautomate:configurator" scale="2" float="left" />

O **Afinador**: descobre sozinho o que cada face de uma máquina aceita e o que ela dá, e propõe os
modos (Insere, Extrai, Armazém) para você conferir antes de aplicar.

<br clear="all" />

## Como operar

1. Vincule a máquina a um roteador (preso nela ou de alcance, pelo [Vinculador](linker.md)).
2. Clique na máquina com o Configurador. As faces com modo ganham uma moldura na cor do modo, e a
   action bar resume a proposta.
3. Confira a proposta. Se estiver boa, clique de novo na mesma máquina em até 10 segundos: os modos
   são aplicados nela.
4. Com **Shift**, o segundo clique aplica no perfil do grupo inteiro.
5. Escolha a rede das faces na tela do roteador, se ainda não tiverem.

Clicar em outra máquina refaz a análise para ela. Clicar no ar, mirar outro bloco por 2 segundos ou
esperar 10 segundos apaga a proposta, e o próximo clique na máquina faz uma análise nova. Segurar o
botão não aplica nem apaga: solte e clique de novo.

## O que muda

- Só os **modos** dos tipos que a análise achou. Prioridade, redstone, filtro e rede de cada face
  ficam como estão.
- Numa máquina de um grupo com outras, a mudança vira exceção dela; com Shift, vai para o perfil.
- Baús, barris e tanques viram **Armazém** numa face só; as outras faces ficam em Nenhum. Uma máquina
  só de energia ou de Source vira Armazém só se for a Bateria ou o Tanque de Source do mod: nas de
  outros mods ela recebe Insere, porque uma máquina que consome lê igual a uma bateria, e Insere não a
  esvazia.
- Não há Desfazer: a moldura mostra a proposta antes, e o segundo clique é a confirmação.

## Limites

- A análise olha até **27 slots por face** e até 64 itens de prova (alguns comuns, os da máquina e os
  do seu inventário). Uma máquina com mais slots, ou que só aceita um item raro, pode receber uma
  proposta incompleta.
- É uma proposta: confira antes de aplicar, principalmente em máquinas de outros mods.
- Você precisa poder mexer na máquina e editar o roteador.
- Numa máquina sem roteador aparece "Vincule a máquina a um roteador primeiro" e nada é analisado.

Num roteador, o clique com o Configurador abre a tela dele.

## Receita

<RecipeFor id="wirelessautomate:configurator" />
''', front('Configurator', 'wirelessautomate:configurator', 8, item_ids=['wirelessautomate:configurator']) + '''
# Configurator

<ItemImage id="wirelessautomate:configurator" scale="2" float="left" />

The **Auto-Setup**: it finds out on its own what each face of a machine takes and what it gives, and
proposes the modes (Insert, Extract, Storage) for you to check before applying them.

<br clear="all" />

## How to operate it

1. Link the machine to a router (attached to it, or a range router through the [Linker](linker.md)).
2. Click the machine with the Configurator. The faces with a mode get a frame in the mode's color, and
   the action bar sums up the proposal.
3. Check the proposal. If it looks right, click the same machine again within 10 seconds: the modes
   are applied to it.
4. With **Shift**, the second click applies them to the whole group's profile.
5. Pick the faces' network on the router screen, if they don't have one yet.

Clicking another machine analyzes that one instead. Clicking the air, aiming at another block for 2
seconds or waiting 10 seconds clears the proposal, and the next click on the machine analyzes it
again. Holding the button neither applies nor clears: let go and click again.

## What changes

- Only the **modes** of the types the analysis found. Each face's priority, redstone, filter and
  network stay as they are.
- On a machine in a group with others, the change becomes its exception; with Shift, it goes to the
  profile.
- Chests, barrels and tanks get **Storage** on a single face; the other faces go to None. A machine
  with only energy or Source gets Storage only if it is this mod's Battery or Source Tank: on other
  mods' it gets Insert, because a machine that consumes reads just like a battery, and Insert doesn't
  drain it.
- There is no Undo: the frame shows the proposal first, and the second click is the confirmation.

## Limits

- The analysis looks at up to **27 slots per face** and up to 64 probe items (a few common ones, the
  machine's own and your inventory's). A machine with more slots, or one that only takes a rare item,
  may get an incomplete proposal.
- It is a proposal: check it before applying, especially on other mods' machines.
- You must be able to touch the machine and edit the router.
- On a machine with no router, "Link the machine to a router first" shows up and nothing is analyzed.

On a router, clicking with the Configurator opens its screen.

## Recipe

<RecipeFor id="wirelessautomate:configurator" />
''')
