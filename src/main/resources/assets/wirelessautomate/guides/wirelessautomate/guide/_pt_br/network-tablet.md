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

## Ficha

| | |
| --- | --- |
| **Abre com** | Clique direito, ou a tecla **Abrir o Tablet de Rede** com o tablet no inventário (sem tecla padrão: escolha em Opções › Controles). |

## Abas

| Aba | O que mostra e faz |
| --- | --- |
| **Lista** | Todos os nós, agrupados pela máquina, com busca, filtros rápidos e o painel do nó escolhido (veja abaixo). Um roteador com máquinas vinculadas abre em árvore, com as máquinas dele. **Selecionar** move vários nós de rede de uma vez: todas as faces deles no tipo escolhido (ou em todos). Um nó com faces em várias redes mostra "faces em N redes"; passe o mouse para ver quais. |
| **Mapa** | Vista de cima com cores por status. Clique num ponto para escolher o nó. O roteador escolhido mostra as máquinas vinculadas a ele, cada uma ligada a ele por uma linha (veja abaixo). |
| **Fluxos** | Quanto entra e sai de cada tipo e de cada rede, o saldo dos Armazéns e o que precisa de atenção (veja abaixo). |
| **Redes** | Criar redes, cor, membros e privacidade. |
| **Grupos** | Juntam as redes de um sistema para pausar e retomar tudo de uma vez. |

Se uma rede for apagada, as faces que estavam nela ficam sem rede e o nó mostra o aviso **faces sem rede: rede removida**: escolha outra rede para elas.

## Fluxos

Cada linha mostra **entra → sai**, a média dos últimos 5 segundos na unidade do tipo (itens/s, mB/s ou B/s, FE/t, Source/s):

- **entra** é o que as faces **Extrai** entregam: a produção da rede;
- **sai** é o que as faces **Insere** recebem: o consumo;
- à direita, o **saldo** dos Armazéns: **+N enchendo** quando eles recebem mais do que dão, **-N esvaziando** quando dão mais.

**Por tipo** mostra uma linha por tipo, somando as redes; a seta abre as redes daquele tipo, da que mais move para a que menos move. **Por rede** mostra cada rede com uma linha por tipo. Uma rede pausada num grupo aparece apagada e fica fora dos totais.

Ao lado, só o que precisa de atenção: **esperando** (faces Extrai com o que dar e sem destino), **cheios** (destinos sem espaço), **por redstone** (faces paradas pelo sinal) e **sem rede** (faces com modo e sem rede). Passe o mouse numa linha para ver a contagem completa. Uma rede que acabou de começar mostra a média dos segundos que já tem.

Clique numa linha para ver na Lista só os roteadores daquele tipo, daquela rede ou dos dois; o chip "Só …" e a etiqueta da rede na Lista tiram o filtro.

## Buscar na Lista

Escreva na caixa de busca: as sugestões se dividem em **Máquinas**, **Redes** e **Nomes**. Escolher uma máquina ou uma rede vira uma **etiqueta** (por exemplo "Máquina: Fornalha ✕"); o ✕ a tira. Você pode juntar várias etiquetas e um texto.

Ao lado da busca ficam os **filtros rápidos**, cada um com a contagem: **Com problema**, **Sem rede** e **Descarregados**. Um filtro com zero nós some. Embaixo, "N de M nós" e **Limpar** voltam à lista inteira.

Cada máquina é um grupo que você recolhe e abre pelo cabeçalho. A linha de cada nó mostra o estado em palavras (**descarregada**, **sem rede**, **destino cheio**, **pausada** ou a rede e a vazão) e seis quadradinhos, um por lado da máquina, na cor do modo da aba escolhida.

| Tecla | O que faz |
| --- | --- |
| **Digitar** | Procura. |
| **↑ ↓** | Anda pelas sugestões abertas ou pelas linhas da lista: os nós e, num roteador aberto, as máquinas. |
| **Enter** | Aceita a sugestão, procura, escolhe a máquina marcada pelas setas, ou abre a tela completa do nó escolhido. |
| **← →** | Com a busca vazia: na linha de um nó, → abre o roteador em árvore e ← o fecha (ou recolhe o grupo dele); num grupo de máquinas, recolhe e abre; numa máquina, ← sobe para o grupo; no "… mais N", → mostra todas. |
| **Esc** | Fecha as sugestões; de novo, limpa a busca. |
| **Backspace** | Com a busca vazia, tira a última etiqueta. |
| **Duplo clique num nó** | Abre a tela completa do roteador. |

## As máquinas de um roteador

Um roteador com máquinas vinculadas tem um **▸** na frente do nome, e a linha diz quantas máquinas ele atende. Clique no **▸** para abrir o roteador em árvore; o **▾** fecha. Embaixo dele aparecem:

| Linha | O que mostra |
| --- | --- |
| **Máquina presa** | A máquina onde o roteador está preso, com o bloco e o lado do roteador. |
| **Grupo** | O nome do grupo e quantas máquinas tem (**×N**), e quantas exceções, se houver. O **▸**/**▾** do grupo esconde e mostra as máquinas dele. |
| **Máquina** | O nome (ou **#N**), as coordenadas e o estado em palavras: **movendo**, **dormindo**, **dormindo: entrada cheia**, **descarregada**, **sem o Upgrade de Alcance** (o roteador sem o upgrade não move de nenhuma), **fora do raio**, **acima do limite**, **trocada** ou **sumiu** (o bloco foi quebrado ou não tem mais o que mover). À direita, a vazão (**↓** recebe, **↑** dá), a marca **exceção** e o Olho **◉**. |

Cada grupo mostra as 8 primeiras máquinas; **… mais N · Mostrar todas** mostra o resto, e **Mostrar menos** volta às 8.

Clique numa máquina para editá-la no painel: ela vira o alvo da linha **Editando**, como na lista da [tela do roteador](router.md). Clique na máquina presa ou num grupo para editar a presa ou o perfil do grupo. O Olho **◉** acende a máquina no mundo, como na tela do roteador.

O Tablet lembra, ao reabrir, os roteadores abertos, os grupos escondidos e os que mostram todas.

## As máquinas no Mapa

No Mapa, o nó escolhido é o mesmo da Lista. Se ele tiver máquinas vinculadas, elas aparecem como quadradinhos ligados ao roteador por uma linha; as acesas pelo Olho ficam em ciano. Clique numa máquina para escolhê-la como alvo do painel do nó, o mesmo da Lista.

## Clicar numa máquina

Com o Tablet na mão, clique direito numa máquina com roteador (ou numa vinculada pelo Upgrade de Alcance): o Tablet abre a central já com o nó dela escolhido, antes da interface da máquina. No painel, **No mundo** acende a máquina através dos blocos, como o Olho da tela do roteador, e o **Apagar todas (N)**, no pé da Lista, apaga as que estão acesas.

## O painel do nó

Clique num nó da lista: o painel à direita é o mesmo da [tela do roteador](router.md), com o cubo desdobrado das faces, modo, rede, filtro, prioridade e redstone, e cada mudança vale na hora. Pelo Tablet você edita um nó **dentro do alcance do tier do Tablet**; com o nó descarregado, ou fora do alcance, o painel mostra o último estado só para leitura e diz o motivo.

**Editar** o filtro abre a tela de filtro mesmo à distância (em outra dimensão também, se o tier permitir), e **Voltar** leva você de volta ao Tablet, no mesmo nó e na mesma busca. O Tablet lembra a aba e o nó ao reabrir.

## Copiar e colar um perfil

Com um nó escolhido, **Copiar perfil** guarda a configuração das faces dele no Tablet (a faixa **Perfil de …** mostra a origem; **esquecer ✕** a larga). Depois:

| Para | Como |
| --- | --- |
| **Um nó** | Escolha-o e clique em **Colar perfil**. |
| **Todo um grupo** | No cabeçalho do grupo, **Colar nas N** (a origem fica de fora). |
| **Vários nós** | Em **Selecionar**, marque os nós e clique em **Colar nos N marcados**. |

Os nós já iguais ao perfil dizem "igual ao perfil"; os outros mostram "N diferentes do perfil" e o primeiro ajuste que falta. O aviso depois de colar traz **Desfazer**. Atalhos: **Ctrl+C** copia o perfil do nó escolhido, **Ctrl+V** cola e **Ctrl+Z** desfaz. As redes valem pela regra de sempre: só entram as que você pode usar.

## Receita

<RecipeFor id="wirelessautomate:network_tablet" />
