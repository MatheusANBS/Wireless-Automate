---
navigation:
  title: Tablet de Rede
  icon: wirelessautomate:network_tablet
  parent: index.md
  position: 9
item_ids:
  - wirelessautomate:network_tablet
---


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
