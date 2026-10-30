"""Página network-tablet.md do guia."""
from guia_base import *  # noqa: F401,F403


PAGINA = page('network-tablet.md', front('Tablet de Rede', 'wirelessautomate:network_tablet', 9, item_ids=['wirelessautomate:network_tablet']) + '''
# Tablet de Rede

<ItemImage id="wirelessautomate:network_tablet" scale="2" float="left" />

Uma visão de todas as suas redes, de qualquer lugar.

<br clear="all" />

## Como usar

1. Clique direito com o Tablet. Ou use a tecla **Abrir o Tablet de Rede** com ele no inventário (sem
   tecla padrão: escolha em Opções › Controles).
2. Na aba **Lista**, procure o nó e clique nele.
3. Mude as faces no painel à direita, como na [tela do roteador](router.md). Vale na hora.

Atalho: com o Tablet na mão, clique direito numa máquina com roteador (ou vinculada pelo Upgrade de
Alcance). O Tablet abre já com o nó dela escolhido, antes da interface da máquina.

## Abas

| Aba | O que faz |
| --- | --- |
| **Lista** | Todos os nós, com busca, filtros e o painel do nó escolhido. |
| **Fluxos** | Quanto entra e sai de cada tipo e de cada rede, e o que precisa de atenção. |
| **Mapa** | Vista de cima, com cor por status e todos os roteadores que você vê. |
| **Redes** | Criar redes, cor, membros, privacidade e grupos de redes. |

O cabeçalho conta a sua base: nós, máquinas e redes.

## O painel do nó

O painel é o mesmo da [tela do roteador](router.md): faces, modo, rede, filtro, prioridade e redstone.
Você só edita um nó **dentro do alcance do tier do Tablet**. Descarregado ou fora do alcance, o painel
mostra o último estado só para leitura e diz o motivo.

**Editar** o filtro abre a tela de filtro mesmo à distância (em outra dimensão também, se o tier
permitir). **Voltar** traz você ao Tablet, no mesmo nó e na mesma busca. O Tablet lembra a aba e o nó
ao reabrir.

**No mundo** acende a máquina através dos blocos, como o Olho da tela do roteador. **Apagar todas
(N)**, no pé da Lista, apaga as acesas.

## A Lista

O seletor embaixo da busca troca a visão:

| Visão | O que mostra |
| --- | --- |
| **Roteadores** | Uma linha por roteador, com a máquina presa embaixo do nome. Abre em árvore com as máquinas dele. |
| **Máquinas** | Todas as máquinas da base, presas e vinculadas, agrupadas pelo bloco. |
| **Grade** | Uma linha por roteador e uma coluna por tipo, com uma tarja na cor do modo de cada face ativa. O clique numa célula abre o nó naquele tipo. |

Cada linha diz o estado em palavras (**descarregada**, **sem rede**, **destino cheio**, **pausada**,
ou a rede e a vazão) e mostra seis quadradinhos, um por lado da máquina, na cor do modo.

Na busca, as sugestões se dividem em **Máquinas**, **Redes** e **Nomes**. Escolher uma máquina ou rede
vira uma **etiqueta** ("Máquina: Fornalha ✕"); o ✕ a tira. Dá para juntar várias etiquetas e um
texto. Ao lado ficam os filtros rápidos **Com problema**, **Sem rede** e **Descarregados**, com a
contagem; o **! N** mostra só os nós com problema. **Limpar** volta à lista inteira.

Para dar nome a um roteador ou à máquina presa, clique no ✎ da linha ou dê dois cliques no nome.

Um nó com faces em várias redes mostra "faces em N redes"; passe o mouse para ver quais. Se uma rede
for apagada, as faces dela ficam com o aviso **faces sem rede: rede removida**: escolha outra rede.

| Gesto | O que faz |
| --- | --- |
| **↑ ↓** | Anda pelas sugestões ou pelas linhas. |
| **Enter** | Aceita a sugestão, escolhe a linha, ou abre a tela completa do nó. |
| **← →** | Com a busca vazia: abre e fecha o roteador, o grupo ou o bloco. |
| **Esc** | Fecha as sugestões; de novo, limpa a busca. |
| **Backspace** | Com a busca vazia, tira a última etiqueta. |
| **Duplo clique num nó** | Abre a tela completa do roteador. |

O Tablet lembra, ao reabrir, a visão e o que estava aberto.

## As máquinas de um roteador

Um roteador com máquinas vinculadas tem um **▸** na frente do nome. Clique para abrir a árvore:

| Linha | O que mostra |
| --- | --- |
| **Máquina presa** | Onde o roteador está preso, com o bloco e o lado. |
| **Grupo** | O nome, quantas máquinas (**×N**) e quantas exceções. |
| **Máquina** | O nome (ou **#N**), as coordenadas, o estado, a vazão (**↓** recebe, **↑** dá), a marca **exceção** e o Olho **◉**. |

Estados de uma máquina: **movendo**, **dormindo**, **dormindo: entrada cheia**, **descarregada**,
**sem o Upgrade de Alcance** (sem ele o roteador não move de nenhuma), **fora do raio**, **acima do
limite**, **trocada** ou **sumiu** (o bloco foi quebrado ou não tem mais o que mover).

Cada grupo ou bloco mostra as 8 primeiras máquinas; **Mostrar todas** mostra o resto. Numa base
grande, os blocos da visão **Máquinas** começam recolhidos.

Clique numa máquina para editá-la no painel: ela vira o alvo da linha **Editando**. Clique na máquina
presa ou num grupo para editar a presa ou o perfil do grupo.

Na visão **Máquinas**, os filtros por tipo e rede valem por máquina: em **Só Fluidos**, uma fornalha
não aparece, mesmo que o roteador dela também mova fluidos.

## O Mapa

Arraste para mover (**Centralizar** volta para você), role para dar zoom e clique num ponto para
escolher o nó. O Mapa mostra todos os roteadores, sem depender da busca nem dos filtros da Lista.

O nó escolhido é o mesmo da Lista. As máquinas vinculadas a ele aparecem como quadradinhos ligados
por uma linha; as acesas pelo Olho ficam em ciano. Clique numa para torná-la o alvo do painel.

## Redes e grupos

A aba **Redes** é uma lista só. Cada grupo de redes é uma seção com **Pausar** no cabeçalho; as redes
fora de grupo vêm no fim, em **Sem grupo**. Uma rede pode estar em dois grupos.

- Clique numa rede para ver a cor, se é pública ou privada, usá-la e removê-la.
- Numa rede privada sua, adicione **convidados** e **times do FTB Teams** (só com o mod). Digite um
  nome e escolha com as setas e Enter; o ✕ duas vezes tira. Só aparecem jogadores que já entraram no
  servidor. Um time vale por inteiro: quem entra ou sai dele ganha ou perde o acesso sozinho.
- Clique no cabeçalho de um grupo para mudar o nome, pausar, marcar as redes dele e removê-lo.
- **Nova rede** e **Novo grupo**, embaixo da lista, criam uma rede ou um grupo.

Um grupo pausado para todas as redes dele de uma vez; retomar as solta.

## Fluxos

Cada linha mostra **entra → sai**, a média dos últimos 5 segundos (itens/s, mB/s ou B/s, FE/t,
Source/s):

- **entra**: o que as faces **Extrai** entregam (a produção);
- **sai**: o que as faces **Insere** recebem (o consumo);
- à direita, o **saldo** dos Armazéns: **+N enchendo** ou **-N esvaziando**.

**Por tipo** soma as redes; a seta abre as redes daquele tipo. **Por rede** mostra cada rede com uma
linha por tipo. Uma rede pausada aparece apagada e fica fora dos totais.

Ao lado, só o que precisa de atenção: **esperando** (faces Extrai com o que dar e sem destino),
**cheios** (destinos sem espaço), **por redstone** (paradas pelo sinal) e **sem rede** (faces com modo e
sem rede).

Clique numa linha para ver na Lista só os roteadores daquele tipo ou rede; o chip "Só …" tira o filtro.

## Copiar e colar um perfil

1. Escolha um nó e clique em **Copiar perfil**. A faixa **Perfil de …** mostra a origem; **esquecer ✕**
   a larga.
2. Para um nó: escolha-o e clique em **Colar perfil**.
3. Para vários: clique em **Selecionar**, marque roteadores, grupos, blocos ou máquinas e clique em
   **Colar nos marcados**.

| Gesto | O que faz |
| --- | --- |
| **Ctrl+C** | Copia o perfil do nó escolhido. |
| **Ctrl+V** | Cola. |
| **Ctrl+Z** | Desfaz (o aviso depois de colar também traz **Desfazer**). |

Os nós mostram "igual ao perfil" ou "N diferentes do perfil". Só entram as redes que você pode usar.

O que o colar faz em cada coisa marcada:

- **Roteador:** cola na máquina presa.
- **Máquina:** vira uma exceção dela, com os lados pela frente da máquina. Se ficar igual ao perfil do
  grupo, a exceção some.
- **Grupo ou bloco inteiro:** cola no perfil do grupo; as exceções que já existem ficam.

Ficam de fora o roteador de onde o perfil veio (as máquinas vinculadas a ele recebem) e os roteadores
descarregados ou fora do alcance. Um perfil copiado numa versão anterior só cola em roteadores: copie
de novo para colar em máquinas e grupos.

**Mover** põe os marcados na rede escolhida: o roteador inteiro; numa máquina, só as faces com modo
dela (vira uma exceção); num grupo, o perfil do grupo.

## Receita

<RecipeFor id="wirelessautomate:network_tablet" />
''', front('Network Tablet', 'wirelessautomate:network_tablet', 9, item_ids=['wirelessautomate:network_tablet']) + '''
# Network Tablet

<ItemImage id="wirelessautomate:network_tablet" scale="2" float="left" />

An overview of all your networks, from anywhere.

<br clear="all" />

## How to use it

1. Right-click with the Tablet. Or use the **Open Network Tablet** key with it in your inventory
   (unbound by default: set it in Options › Controls).
2. On the **List** tab, find the node and click it.
3. Change its faces in the panel on the right, as on the [router screen](router.md). It takes effect
   at once.

Shortcut: with the Tablet in hand, right-click a machine with a router (or one linked through the
Range Upgrade). The Tablet opens with its node already chosen, before the machine's own interface.

## Tabs

| Tab | What it does |
| --- | --- |
| **List** | Every node, with search, filters and the chosen node's panel. |
| **Flows** | How much of each type comes in and goes out, per network, and what needs attention. |
| **Map** | Top-down view, colored by status, with every router you can see. |
| **Networks** | Create networks, color, members, privacy and network groups. |

The header counts your base: nodes, machines and networks.

## The node panel

The panel is the same as the [router screen](router.md): faces, mode, network, filter, priority and
redstone. You can only edit a node **within the Tablet tier's range**. Unloaded or out of range, the
panel shows its last state read-only and says why.

**Edit** on the filter opens the filter screen even from a distance (across dimensions too, if the
tier allows it). **Back** returns you to the Tablet, on the same node and search. The Tablet remembers
its tab and node when you reopen it.

**In the world** lights the machine up through the blocks, like the Eye on the router screen. **Clear
all (N)**, at the foot of the List, turns off the lit ones.

## The List

The switch below the search changes the view:

| View | What it shows |
| --- | --- |
| **Routers** | One row per router, with the attached machine under the name. Opens as a tree with its machines. |
| **Machines** | Every machine of your base, attached and linked, grouped by block. |
| **Grid** | One row per router and one column per type, with a stripe in the mode's color for each active face. Clicking a cell opens the node on that type. |

Each row states its condition in words (**unloaded**, **no network**, **destination full**,
**paused**, or the network and rate) and shows six small squares, one per machine side, in the mode's
color.

In the search, suggestions are split into **Machines**, **Networks** and **Names**. Picking a machine or
network turns it into a **label** ("Machine: Furnace ✕"); the ✕ removes it. You can combine several
labels and some text. Next to it sit the quick filters **With problems**, **No network** and
**Unloaded**, with their counts; **! N** shows only the nodes with problems. **Clear** brings back the
whole list.

To name a router or its attached machine, click the ✎ on its row or double-click the name.

A node with faces on several networks shows "faces in N networks"; hover to see which. If a network is
deleted, its faces get the warning **faces without network: network removed**: pick another network.

| Gesture | What it does |
| --- | --- |
| **↑ ↓** | Moves through the suggestions or the rows. |
| **Enter** | Accepts the suggestion, chooses the row, or opens the node's full screen. |
| **← →** | With an empty search: opens and closes the router, group or block. |
| **Esc** | Closes the suggestions; again, clears the search. |
| **Backspace** | With an empty search, removes the last label. |
| **Double-click a node** | Opens the router's full screen. |

When you reopen it, the Tablet remembers the view and what was open.

## A router's machines

A router with linked machines has a **▸** in front of its name. Click it to open the tree:

| Row | What it shows |
| --- | --- |
| **Attached machine** | Where the router is attached, with the block and the side. |
| **Group** | The name, how many machines (**×N**) and how many exceptions. |
| **Machine** | The name (or **#N**), the coordinates, the state, the rate (**↓** receives, **↑** gives), the **exception** mark and the Eye **◉**. |

A machine's states: **moving**, **asleep**, **asleep: input full**, **unloaded**, **no Range
Upgrade** (without it the router moves from none of them), **out of range**, **over the limit**,
**replaced** or **missing** (the block was broken or has nothing left to move).

Each group or block shows its first 8 machines; **Show all** shows the rest. In a large base, the
blocks in the **Machines** view start collapsed.

Click a machine to edit it in the panel: it becomes the target of the **Editing** row. Click the
attached machine or a group to edit the attached machine or the group's profile.

In the **Machines** view, the type and network filters apply per machine: under **Only Fluids**, a
furnace does not show up, even if its router also moves fluids.

## The Map

Drag to move (**Center** brings it back to you), scroll to zoom and click a point to choose the node.
The Map shows every router, independent of the List's search and filters.

The chosen node is the same as in the List. Its linked machines show up as small squares joined by a
line; the ones lit by the Eye are cyan. Click one to make it the panel's target.

## Networks and groups

The **Networks** tab is a single list. Each network group is a section with **Pause** on its header;
networks outside any group come last, under **No group**. A network can be in two groups.

- Click a network to see its color and whether it is public or private, use it or remove it.
- On a private network you own, add **guests** and **FTB Teams teams** (only with that mod). Type a
  name and pick with the arrows and Enter; click the ✕ twice to remove. Only players who already joined
  the server show up. A team counts as a whole: whoever joins or leaves it gains or loses access on
  their own.
- Click a group's header to rename it, pause it, check its networks and remove it.
- **New network** and **New group**, below the list, create a network or a group.

A paused group stops all its networks at once; resuming lets them go.

## Flows

Each row shows **in → out**, the average of the last 5 seconds (items/s, mB/s or B/s, FE/t,
Source/s):

- **in**: what the **Extract** faces deliver (production);
- **out**: what the **Insert** faces receive (consumption);
- on the right, the Storage faces' **balance**: **+N filling** or **-N draining**.

**By type** adds up the networks; the arrow opens that type's networks. **By network** shows each
network with one row per type. A paused network is greyed out and left out of the totals.

Next to it, only what needs attention: **waiting** (Extract faces with something to give and no
destination), **full** (destinations with no room), **by redstone** (stopped by the signal) and
**without network** (faces with a mode and no network).

Click a row to list only the routers of that type or network; the "Only …" chip removes the filter.

## Copying and pasting a profile

1. Choose a node and click **Copy profile**. The **Profile of …** strip shows the source; **forget ✕**
   drops it.
2. For one node: choose it and click **Paste profile**.
3. For several: click **Select**, mark routers, groups, blocks or machines and click **Paste on
   marked**.

| Gesture | What it does |
| --- | --- |
| **Ctrl+C** | Copies the chosen node's profile. |
| **Ctrl+V** | Pastes. |
| **Ctrl+Z** | Undoes (the notice after pasting also has **Undo**). |

Nodes show "same as the profile" or "N different from the profile". Only networks you can use are
applied.

What pasting does to each marked thing:

- **Router:** pastes on its attached machine.
- **Machine:** becomes an exception of its own, with the sides by the machine's front. If it ends up
  equal to the group's profile, the exception goes away.
- **Whole group or block:** pastes on the group's profile; the exceptions already there stay.

Left out: the router the profile came from (the machines linked to it do receive) and unloaded or
out-of-range routers. A profile copied in an earlier version only pastes on routers: copy it again to
paste on machines and groups.

**Move** puts the marked ones in the chosen network: the whole router; on a machine, only its faces
with a mode (it becomes an exception); on a group, the group's profile.

## Recipe

<RecipeFor id="wirelessautomate:network_tablet" />
''')
