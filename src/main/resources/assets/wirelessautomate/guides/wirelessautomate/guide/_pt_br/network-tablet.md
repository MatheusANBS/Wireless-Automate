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
| **Lista** | Todos os nós, com busca, filtros rápidos e o painel do nó escolhido (veja abaixo). O seletor **Roteadores · Máquinas · Grade**, embaixo da busca, mostra a lista pelos roteadores (uma linha para cada um, com a máquina presa embaixo do nome, abrindo em árvore com as máquinas dele), por todas as máquinas, agrupadas pelo bloco, ou na **Grade**: uma linha por roteador e uma coluna por tipo, com uma tarja na cor do modo para cada face que faz algo, mais o estado e a rede; o clique numa célula abre o nó na aba daquele tipo. O botão **! N** na ponta filtra os nós com problema. **Selecionar** marca roteadores, grupos e máquinas para colar um perfil ou mudar de rede de uma vez. Um nó com faces em várias redes mostra "faces em N redes"; passe o mouse para ver quais. Para dar nome a um roteador ou à máquina presa, clique no ✎ da linha (com o mouse em cima) ou dê dois cliques no nome; Enter salva e Esc desiste. |
| **Fluxos** | Quanto entra e sai de cada tipo e de cada rede, o saldo dos Armazéns e o que precisa de atenção (veja abaixo). |
| **Mapa** | Vista de cima com cores por status, com todos os roteadores que você vê (sem depender da busca nem dos filtros da Lista). Arraste para mover o mapa (**Centralizar** volta para você), role para dar zoom e clique num ponto para escolher o nó. O roteador escolhido mostra as máquinas vinculadas a ele, cada uma ligada a ele por uma linha (veja abaixo). |
| **Redes** | Criar redes, cor, membros e privacidade, e os grupos de redes, que pausam e retomam um sistema inteiro de uma vez (veja abaixo). |

O cabeçalho do Tablet conta a sua base: nós, máquinas e redes.

Se uma rede for apagada, as faces que estavam nela ficam sem rede e o nó mostra o aviso **faces sem rede: rede removida**: escolha outra rede para elas.

## Redes e grupos

A aba **Redes** é uma lista só. Cada grupo de redes é uma seção, com **▾** para recolher e **Pausar** no cabeçalho (**pausado** quando está); as redes fora de grupo vêm no fim, em **Sem grupo**. Uma rede em dois grupos aparece nas duas seções, e a dica diz **Também em …**.

- Clique numa rede para ver à direita a cor, se é pública ou privada, usá-la e removê-la.
- Numa rede privada que é sua, a coluna mostra os **convidados** e os **times do FTB Teams** (só com o mod). Digite um nome para ver sugestões, escolha com as setas e Enter (Tab completa) e use o ✕ duas vezes para tirar. Só aparecem jogadores que já entraram no servidor, e um time vale por inteiro: quem entra ou sai dele ganha ou perde o acesso sozinho.
- Clique no cabeçalho de um grupo para ver o grupo à direita: o nome, pausar, as redes dele (marque ou desmarque cada uma; **em …** avisa a rede que já está noutro grupo) e remover.
- **Nova rede** e **Novo grupo**, embaixo da lista, criam uma rede ou um grupo: digite o nome e confirme (ou Enter).

Um grupo pausado para todas as redes dele de uma vez; retomar as solta.

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

## A Lista por máquinas

No seletor embaixo da busca, **Máquinas** mostra todas as máquinas da base, as presas e as vinculadas, agrupadas pelo tipo de bloco; **Roteadores** volta à lista de sempre. Os filtros rápidos ficam à direita do seletor.

| Linha | O que mostra |
| --- | --- |
| **Bloco** | O **▸**/**▾**, o ícone, o nome e quantas máquinas (por exemplo "Fornalha · 23") e, apagado à direita, de quantos roteadores. Clique para abrir ou recolher o bloco. |
| **Máquina** | O nome (ou **#N**, o mesmo número da árvore e da tela do roteador) e, embaixo, **via** o roteador que a atende, ou **presa** na máquina onde o roteador está preso, com as coordenadas ou o estado em palavras. À direita, a vazão quando ela move, a marca **exceção** e o Olho **◉**. |

Cada bloco mostra as 8 primeiras máquinas; **… mais N · Mostrar todas** mostra o resto e **Mostrar menos** volta às 8. Com uma base grande, os blocos começam recolhidos: abra o que quiser ver. O pé da lista conta os blocos e as máquinas.

A busca acha a máquina pelo nome, pelo bloco e pelo nome do roteador. Os filtros por tipo e por rede valem por máquina: em **Só Fluidos**, uma fornalha não aparece, mesmo que o roteador dela também mova fluidos.

Clique numa máquina: o Tablet escolhe o roteador dela e põe a máquina como alvo do painel, para configurá-la ali; na presa, o alvo é a presa. As setas **↑ ↓** andam nas linhas, **← →** recolhem e abrem o bloco, e **Enter** escolhe. O Tablet lembra, ao reabrir, a visão e os blocos abertos.

## As máquinas no Mapa

No Mapa, o nó escolhido é o mesmo da Lista. Se ele tiver máquinas vinculadas, elas aparecem como quadradinhos ligados ao roteador por uma linha; as acesas pelo Olho ficam em ciano. Clique numa máquina para escolhê-la como alvo do painel do nó, o mesmo da Lista. Com a Lista por máquinas, o Mapa continua com todos os roteadores; o escolhido é o da máquina escolhida, com as máquinas dele.

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
| **Vários roteadores e máquinas** | Em **Selecionar**, marque o que quiser e clique em **Colar nos marcados**; o botão diz o que vai atingir ("2 roteadores, 3 máquinas") ou, sem espaço, só quantos, e a dica diz tudo. |

Os nós já iguais ao perfil dizem "igual ao perfil"; os outros mostram "N diferentes do perfil" e o primeiro ajuste que falta. O aviso depois de colar traz **Desfazer**. Atalhos: **Ctrl+C** copia o perfil do nó escolhido, **Ctrl+V** cola e **Ctrl+Z** desfaz. As redes valem pela regra de sempre: só entram as que você pode usar.

## Marcar roteadores e máquinas

Em **Selecionar**, toda linha tem uma caixa, nas duas visões: o roteador, a máquina presa, o grupo, a máquina e o cabeçalho do bloco. Marcar um grupo ou um bloco marca as máquinas dele; com só uma parte marcada, a caixa fica pela metade. **Todos** marca o que a busca mostra: os roteadores em **Roteadores**, os blocos em **Máquinas**.

**Colar nos marcados** cola o perfil copiado:

- no **roteador**, na máquina presa, como no colar de sempre;
- na **máquina**, como uma exceção dela, com os lados virados pela frente da máquina: cada face vai para o mesmo lado da máquina, mesmo que ela esteja virada para outra direção. Se ficar igual ao perfil do grupo, a exceção some;
- no **grupo inteiro** (o grupo marcado, o bloco marcado ou todas as máquinas do grupo uma a uma), no perfil do grupo; as exceções que já existem ficam, e o aviso diz quantas.

O roteador de onde o perfil veio fica de fora (as máquinas vinculadas a ele recebem); roteadores descarregados ou fora do alcance ficam de fora com as máquinas deles, e o aviso conta. Um perfil copiado numa versão anterior só cola em roteadores: copie-o de novo para colar em máquinas e grupos. O aviso ("Colado em 3 grupos e 1 máquina · 2 exceções mantidas") traz **Desfazer**, que volta o que ainda está como ficou.

**Mover** põe os marcados na rede escolhida: os roteadores inteiros, como sempre; numa máquina marcada, só as faces com modo dela (vira uma exceção); num grupo inteiro, o perfil do grupo. Uma máquina de um roteador também marcado já vai junto com ele.

## Receita

<RecipeFor id="wirelessautomate:network_tablet" />
