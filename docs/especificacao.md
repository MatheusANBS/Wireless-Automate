# Wireless Automate — Especificação

Versão de 7 de outubro de 2026 · Matheus Araújo

## Visão geral

Wireless Automate é um mod de transporte wireless de itens, fluidos, energia e químicos para NeoForge 1.21.1, feito para o ATM10. A meta é ser o transporte mais rápido do pack e, ao mesmo tempo, o mais leve em TPS.

- **Plataforma:** Minecraft 1.21.1, NeoForge, Java 21.
- **Público:** modpacks grandes de tecnologia, com ATM10 como referência.
- **Pilar 1, vazão:** o tier Ultimate não tem limite de transferência além do orçamento de TPS.
- **Pilar 2, TPS:** o mod tem um teto fixo de tempo por tick e não deve gastar nada quando não há o que mover.
- **Pilar 3, configuração:** configurar dezenas de nós deve ser rápido, com presets, cópia por área e edição à distância.

## Componentes

O mod tem um bloco e oito itens (Configurador, Tablet, Vinculador, Cartão de filtro, Upgrade de chunk loading e três Cartões de Upgrade), mais o livro-guia quando o GuideME está instalado. Todo o resto é configuração.

| Componente | Tipo | Função |
| --- | --- | --- |
| Roteador Wireless | Bloco direcional | Gruda na face da máquina onde é colocado: em cima, embaixo (de cabeça para baixo) ou de lado. Shift + clique direito com as mãos vazias gira o roteador 90° em torno do eixo da face (horário para quem olha a face de frente); a configuração das faces da máquina não muda. Corpo de 14×6×12 px, duas antenas e quatro LEDs decorativos na frente. |
| Cartões de Upgrade (Avançado, Elite, Esmeralda, Ultimate; Allthemodium, Vibranium e Unobtainium com o mod) | Item | Têm origem (componente `upgrade_from`, "Básico → Elite") e só valem num bloco exatamente daquele tier (`RouterTier.acceptsCard`); os cartões antigos, sem origem, sobem de qualquer tier abaixo. Shift + clique no roteador ou armazenamento colocado (sem perder a configuração) ou bloco + cartão na bancada. Não há cartão Básico. |
| Configurador | Item (varinha) | Copia a configuração de um roteador e cola em outro (o Pincel), com a roda do tipo colado. |
| Tablet de rede | Item | Gerencia nós, redes e grupos à distância. |
| Vinculador | Item (controle) | Liga máquinas a um roteador de alcance (uma a uma ou por área) e abre o roteador que chega numa máquina. |
| Cartão de filtro | Item | Guarda um filtro reutilizável entre roteadores. |
| Upgrade de chunk loading | Item | Mantém carregado o chunk do roteador. |

Sem partículas de item nem animação no bloco: o modelo fica fixo (decisão do dono). O estado aparece na tela do roteador e no Tablet, e o servidor só avisa o cliente quando o estado muda.

## Tiers

Cinco tiers (oito com o Allthemodium), ×8 por degrau. Os tiers feitos com itens vanilla são de propósito modestos, para que os do Allthemodium sejam um salto evidente (decisão do dono, 8/10/2026; ver `docs/superpowers/specs/2026-10-08-tiers-esmeralda-e-atm-design.md`). Os tiers diferem só em vazão, alcance e dimensões; filtros não têm limite de slots em nenhum tier. O enum `RouterTier` tem sempre os oito; `next()` e `previous()` pulam os do Allthemodium sem o mod.

| Tier | Itens/s | Fluido e químico (mB/s) | Energia (FE/t) | Source/s | Alcance |
| --- | --- | --- | --- | --- | --- |
| Básico | 32 | 2.000 | 1.000 | 100 | 64 blocos |
| Avançado | 256 | 16.000 | 32.768² | 800 | 512 blocos |
| Elite | 2.048 | 128.000 | 64.000 | 6.400 | Dimensão inteira |
| Esmeralda | 16.384 | 1.024.000 | 2.097.152² | 51.200 | Entre dimensões |
| Allthemodium¹ | 131.072 | 8.192.000 | 67.108.864² | 409.600 | Entre dimensões |
| Vibranium¹ | 1.048.576 | 65.536.000 | 134.217.728² | 3.276.800 | Entre dimensões |
| Unobtainium¹ | 8.388.608 | 524.288.000 | 4.294.967.296² | 26.214.400 | Entre dimensões |
| Ultimate | Sem limite | Sem limite | Sem limite | Sem limite | Entre dimensões |

¹ Só com o mod Allthemodium; sem ele, a Esmeralda sobe direto para o Ultimate.

² A energia não segue o ×8: cada tier escoa por uma face a geração cheia do Ressonador de Sinal do mesmo tier (rebalanceamento de 21/10/2026, migração 3 da config; o Básico e o Elite já cobriam e ficaram).

A Source (Ars Nouveau) é opcional: sem filtro e sem cartões, como a energia; a vazão é a chave `sourcePerSecond` da config.

A vazão vale por face e por tipo. Os números ficam no arquivo de config do servidor, para o modpack ajustar. No Ultimate, o único limite é o orçamento de TPS.

## Telas da interface

São quatro telas (Roteador, Filtro, Tablet e Vinculador; o Configurador não tem tela), todas com a mesma hierarquia: o essencial à vista, ajustes secundários recolhidos e um botão principal na cor do tier.

**Tema claro e escuro:** todas as telas têm um botão de 11 × 11 px no canto direito do cabeçalho (uma lua no claro, um sol no escuro; a dica diz "Modo escuro" ou "Modo claro"). Clicar troca o tema de todas as telas de uma vez, na hora, sem reabrir; a escolha fica salva na config do cliente (`config/wirelessautomate-client.toml`, `gui.theme`, `LIGHT` ou `DARK`, padrão `LIGHT`). A roda do Configurador, que não tem cabeçalho, segue o tema. As paletas estão em `docs/identidade-visual.md`.

### Roteador

- **Abre com:** clique direito no roteador, ou à distância pelo Tablet. Shift + clique com as mãos vazias gira o roteador em vez de abrir a tela.
- **Cabeçalho:** nome do nó, vazão atual da aba selecionada e tier.
- **Abas:** uma por tipo do registro (Itens, Fluidos, Energia e Químicos; Químicos só existe com Mekanism), cada uma com o ícone e a cor do tipo; a aba é só o tipo de recurso (a rede é de cada face). **Abas adaptáveis:** com espaço, nome inteiro em todas; mais apertado, o nome só na aba ativa; sem espaço, só o ícone (o nome aparece no tooltip).
- **Redimensionável** pela borda direita, a de baixo e a alça do canto, de 300 × 240 até a janela menos 8 px, com o tamanho lembrado na sessão; a largura e a altura extras vão para o visor 3D e a linha das abas, e a coluna da direita e o inventário acompanham a borda. Nomes longos (de rede, de nó) são abreviados e mostram o texto inteiro no tooltip.
- **Resumo das redes:** no canto direito da linha das abas, "N redes" ("1 rede", "Sem rede"): as redes distintas das faces da aba no alvo editado, com a dica listando face → rede ("Cima: Mina · Baixo: Armazém").
- **Linha Rede:** na coluna da direita, abaixo da linha da face, a rede da face selecionada numa pílula com a borda e o Olho na cor da rede (cinza em "Sem rede"). Clicar abre a lista "Rede da face Cima em Itens" com as redes que o jogador pode usar e "Sem rede"; escolher muda só aquela face, naquele tipo (`SetNetworkPayload`, face no quadro do alvo). No grupo e na exceção do roteador de alcance, edita a rede da face do perfil ou da exceção.
- **Lados relativos à máquina:** o cubo e as dicas mostram Frente, Trás, Esquerda, Direita, Cima e Baixo (Esquerda e Direita vistas de frente para a máquina), também na máquina presa, pela frente dela (`HORIZONTAL_FACING` ou `FACING` horizontal do `BlockState`, lida só no snapshot). Máquina sem frente ou com frente vertical: Frente = Norte. A dica de cada face dá o lado absoluto ("Trás · Norte"), o modo e "Rede: X". A configuração da presa continua salva relativa ao roteador (`RelativeSide`); se a máquina girar, os nomes acompanham a frente nova.
- **Visor 3D:** a máquina conectada e o roteador, na posição em que ele foi colocado, com as faces da máquina tocáveis; a face selecionada ganha o contorno na cor da rede dela. O visor e o cubo do painel andam juntos: clicar numa face em um seleciona no outro, e a face sob o mouse se acende nos dois. Embaixo do visor ficam os slots de cartão da face (num tipo sem filtro, a vazão do tier).
- **Painel do nó (`client/router/NodePanel`):** a coluna da direita é um componente só, o mesmo da tela do roteador e do Tablet (Tablet: o nó escolhido; a lógica pura do cubo está em `client/FaceNet`). Do alto para baixo: as abas por tipo com o resumo "N redes"; a linha **Editando** (com o Upgrade de Alcance, a lista da presa, dos grupos e das máquinas, com "Voltar a igual às outras" e Desvincular, e o **Tirar redes** na ponta da linha da pílula); o **cubo desdobrado**, as seis faces da máquina em Cima, Frente, Esquerda / Baixo, Trás, Direita, cada uma com a tarja do modo, o nome (inteiro, curto ou só a letra, pelo espaço), a bolinha na cor da rede e o Olho na face onde o roteador está preso (a face sem modo tem a borda tracejada); a linha "<Lado> · <acesso>" com os quatro modos Extrai, Insere, Armazém e Nenhum e a frase do modo; a linha **Rede** (a lista com as redes que o jogador pode usar e "Sem rede"); o filtro resumido em uma linha com Editar, ou "Sem filtro"; **Prioridade** (− valor +, Shift muda de 10 em 10) e **Redstone** (Ignorar, Com sinal, Sem sinal), sempre à vista (sai o botão Mais). A dica do Armazém: recebe de quem extrai e entrega para quem insere; não troca com outro armazém.
- **Telas mais limpas (21/10/2026; spec `docs/superpowers/specs/2026-10-21-telas-limpas-design.md`, desenhos em `docs/preview/interfaces-limpas/`):** o editor de faces é um quadro afundado com o cubo desdobrado, um botão de 30 × 22 por face que é o modo (clique avança Extrai → Insere → Armazém → Nenhum, direito volta, Shift põe em Nenhum, com o sprite da porta, a letra do lado, a bolinha da rede e o Olho do roteador) e, ao lado, Limpar (dois cliques; Shift no segundo, todos os tipos), Copiar e Colar (`FaceActionPayload`, protocolo 28). Embaixo, a linha da face ("Cima ▪ Extrai · 27 slots · roteador aqui") no lugar dos quatro botões de modo e da frase (que foi para a dica), e as linhas Rede, Filtro (os dois slots de cartão e o Editar), Redstone (‹ ›) e Prioridade (‹ ›, ou a roda), com rótulo. Na tela do roteador o visor ocupa a coluna da esquerda e, com o Upgrade de Alcance, a lista das máquinas fica embaixo dele (uma linha por máquina: estado, #N, "· exceção", distância e o Olho), com a barra Editando e o Igualar na exceção à direita. O Tablet ganha a visão **Grade** (`client/tablet/GridRows`) e o botão "! N"; o filtro e o Vinculador seguem os mesmos desenhos (o Vinculador leva os tipos em ícones e as faces no cubo para o quadro Vincula).
- **Aba sem filtro (Energia):** não mostra Editar; mostra "Sem filtro…" e a vazão do tier.
- **Modo e rede da face:** abrir o roteador ou o painel do Tablet e escolher Extrai, Insere, Armazém ou Nenhum não atribui nem troca a rede. A rede é escolhida explicitamente na linha Rede, pelo Tablet ou ao colar uma configuração. Uma face com modo e sem rede mostra o contorno coral e o convite "Escolha a rede desta face". Prioridade e redstone também preservam a rede existente.
- **Tirar redes (`client/router/IdleNetworks`):** com o Upgrade de Alcance, quando o alvo de edição é um grupo ou uma máquina com exceção, o painel não é só leitura e o snapshot traz ao menos uma face (de qualquer tipo carregado) com modo Nenhum e com rede. Na linha Editando, na ponta direita da linha da pílula (a pílula encolhe; nada de linha nova): o rótulo curto "Tirar redes", ou só o ícone ⊘ quando a pílula deixaria de mostrar o nome do alvo inteiro; com a lista das máquinas à vista na tela do roteador (sem a linha Editando), é o ⊘ entre os botões da linha do alvo na lista (`RangeRowButtons.Action.CLEAR_NETWORKS`). A dica traz o nome inteiro ("Tirar a rede das faces sem modo") e o que ele faz. O `ClearIdleNetworksPayload(containerId, undo)` (pelo `RouterEditHost`, com as validações de sempre) tira a rede de todas as faces do alvo, em todos os tipos, com modo Nenhum e rede, numa mudança só (`RouterBlockEntity.clearIdleNetworks`; modo, prioridade, redstone e filtro não mudam; na presa não faz nada). Com alguma face limpa, o aviso "Rede tirada de N faces" (uma linha) com **Desfazer** (`PanelNoticePayload.Undo.RESTORE_IDLE_NETWORKS`). O Desfazer (`menu/IdleNetworkUndo`, só na memória do servidor: o último limpar por jogador, apagado ao limpar de novo, ao fechar o menu, ao sair e ao parar o servidor) devolve a rede só às faces que continuam com modo Nenhum e sem rede, numa rede que ainda existe, e vale uma vez, no mesmo menu, roteador e alvo; avisa "Redes devolvidas a N faces", ou nada se nenhuma voltou.
- **Cartões e upgrade:** dois slots de Cartão de filtro por face e por tipo, abaixo da face selecionada; o slot do Upgrade de chunk loading fica no cabeçalho (é do roteador, não da face). Os upgrades de tier não usam slot: entram por clique ou na bancada.

### Filtro

- **Abre com:** Editar no roteador, o botão Filtro dos armazenamentos, ou clique direito no ar segurando um Cartão de filtro.
- **Janela redimensionável** pelas bordas e pela alça do canto, em torno do centro (como a do Baú), com o tamanho lembrado na sessão.
- Lista branca ou negra e, para itens, ignorar ou exigir componentes iguais.
- **À esquerda:** as entradas em lista (ícone, nome, tipo, estoque), sem limite e com busca por nome, `#tag` ou `@mod`; embaixo, o inventário do jogador (Shift + clique adiciona o item exato; com o JEI, arrastar para a lista ou Shift + clique na lista dele adiciona mesmo sem ter o item).
- **À direita, quatro abas:**
  - **Entrada:** a selecionada, com o que ela pega (os itens da tag ou do mod), o estoque, Remover, e Ver tags (item) ou Editar (regra).
  - **Tags:** o inspetor (um item no slot, pelo cursor, pelo JEI, por Ver tags ou por Ctrl + clique no inventário, mostra todas as tags dele para marcar, mais o mod) e a busca em todas as tags do jogo, com quantos itens cada uma pega e a prévia dos itens sob o mouse; `@texto` busca mods. Nos químicos, que não têm tags, vira Adicionar: id do químico ou `@mod`.
  - **Regra** (só itens): monta uma regra por propriedade; o inventário acende no que ela pega antes de adicionar. O encantamento é um campo de texto: digita o nome ou o id (sem diferença de acento ou maiúscula) e escolhe numa lista de sugestões de todos os encantamentos do jogo; o nível mínimo é digitado, de 1 a 255 (romanos até X, algarismos depois).
  - **Mais:** Cartão de Filtro (importar e exportar), componentes e Limpar.

### Tablet de rede

- **Abre com:** clique direito no ar segurando o Tablet, ou tecla de atalho.
- **Abas (etapa 3b da central):** Lista · Fluxos · Mapa · Redes (`TabletScreen.Tab`, `TabletMenu.TAB_COUNT = 4`, `FLOWS_TAB = 1`; a aba Grupos saiu, os grupos de redes viraram seções de Redes). O cabeçalho conta a base que o jogador vê: "N nós · M máquinas · K redes" (as máquinas são as dos nós visíveis, a presa de cada um mais as vinculadas, somadas na mesma passada pelo índice que conta os nós).
- **Lista (busca única, grupos e painel):** componente `client/tablet/TabletList` (a `TabletScreen` só hospeda). A tela abre em 420 × 280, lista e painel lado a lado, e o Tablet lembra a aba e o nó escolhido ao reabrir (também na volta do filtro à distância).
  - **Busca:** uma caixa só, com o cursor ao abrir. As sugestões (`client/SearchSuggestions`, a lógica pura em `client/TabletSearch`: sem acento nem maiúsculas, começa-com primeiro) vêm em grupos **Máquinas**, **Redes** e **Nomes**, até 3 por grupo, com o trecho casado sublinhado. Máquina e rede viram **etiquetas** ("Máquina: X ✕", "Rede: Y ✕", na cor da rede) e entram na consulta (`TabletSnapshot.Query`: `text`, `textMachines`, `machines`, `networks`, `quick`, `type`); o servidor responde com as facetas e as contagens. A consulta sai com atraso de 6 ticks.
  - **Filtros rápidos** com contagem (`QuickFilter`): **Com problema** (descarregado, sem rede ou destino cheio), **Sem rede** e **Descarregados**; somem com contagem 0 e desligados. O "Só <tipo> ✕" dos Fluxos é o primeiro chip da mesma linha. Embaixo, "N de M nós" e **Limpar**; sem resultado, "Nenhum nó com ..." e "Limpar a busca"; com mais de 256, "Mostrando 256 de N · refine a busca".
  - **Grupos:** a lista é agrupada pela máquina (`client/tablet/ListTree`, puro: ordem do servidor, id da máquina e depois o nome), com o cabeçalho ▾/▸ recolhível e a contagem real; a linha do nó tem a bolinha de estado, o nome, o estado em palavras (descarregada, sem rede, destino cheio, pausada ou "<rede> · <vazão>") e seis quadradinhos 3×2 com o modo de cada lado, na cor do modo da aba do painel. Um nó aparece em cada rede em que tem alguma face; com faces em mais de uma, "faces em N redes" (a dica lista as redes por tipo), e com uma face numa rede apagada, o aviso "faces sem rede: rede removida". Mover nós para a rede B (Selecionar) põe nela todas as faces do tipo escolhido (presa, perfis e exceções do alcance); sem tipo, de todos os tipos.
  - **Árvore das máquinas (etapa 3a da central):** um roteador com máquinas vinculadas tem ▸/▾ na frente e "N máquinas" na linha; aberto, mostra embaixo a linha da presa ("Máquina presa · <bloco> · <lado> do roteador"), as dos grupos ("<nome> ×N", "N exceções" só quando há, ▸/▾ recolhe) e, num grupo aberto, as máquinas: até 8 (`TreeOrder.FEW`), com "… mais N · Mostrar todas" e "Mostrar menos". A máquina tem o nome (o rótulo, ou "#N"), as coordenadas e o estado em palavras (movendo, dormindo, dormindo: entrada cheia, descarregada, sem o Upgrade de Alcance, fora do raio, acima do limite, trocada, sumiu), a vazão (↓ recebe, ↑ dá, só quando move) na cor do tipo, a marca "exceção" e o Olho ◉ (`MachineHighlights`). Clicar numa máquina escolhe o nó (`TabletSelectNodePayload`) e, logo depois, a máquina como alvo do painel (`SelectTargetPayload`); na presa, a presa; no grupo, o grupo. No modo Selecionar, que marca só nós, o clique numa linha da árvore não faz nada. No servidor, o estado da árvore de cada Tablet aberto fica no `menu/TabletTree` (abertos, grupos recolhidos e "Mostrar todas"; a presa e os grupos de um roteador aberto começam abertos), mudado pelo `TabletTreePayload` (`OPEN`, `CLOSE`, `COLLAPSE_GROUP`, `EXPAND_GROUP`, `SHOW_ALL`, `SHOW_FEW`; valida a visibilidade do nó e os limites de 64 abertos e 256 grupos por jogador; cada ação refaz só a página daquele jogador). A página é uma lista de linhas na ordem da tela (`TabletRows`, montada pelo `TreeOrder` a partir do índice; o limite de 256 conta linhas, os nós cortados seguem contados), e as linhas das máquinas funcionam com o roteador descarregado ("descarregada", sem vazão). O "live" de uma linha de máquina (estado e vazão pela diferença dos totais das portas dela entre amostras) é medido pelo `TabletMachineSample` só para as máquinas à vista na página de alguém, uma vez por amostra para todos os Tablets abertos. Protocolo `24`.
  - **Visão por máquinas (etapa 3b da central):** o seletor "Roteadores · Máquinas" fica embaixo da busca (`client/tablet/ViewBar`), com os filtros rápidos à direita (descem para a linha de baixo quando não cabem). Em Máquinas, a base inteira aparece pelo tipo de bloco: um cabeçalho por bloco (▸/▾, ícone, "Fornalha · 23" e, apagado à direita, "N roteadores"; sem soma de vazão) e, dentro dele, as máquinas na ordem roteador e #N, com "via <roteador>" na vinculada e "presa" na presa, as coordenadas ou o estado em palavras (a presa também "sem rede" e "pausada"), a vazão só quando move, "exceção" e o Olho ◉; até 8 por bloco com "… mais N · Mostrar todas" e "Mostrar menos"; o "#N" é a posição da máquina no grupo, o mesmo da árvore e da tela do roteador. Os blocos começam recolhidos quando a visão inteira passa de 256 linhas; o pé conta "N blocos · M máquinas" ou "X de Y linhas · refine a busca". A consulta vale por máquina: o texto pelo rótulo, pelo bloco e pelo nome do roteador; as etiquetas de máquina pelo bloco; o tipo e as etiquetas de rede pelos papéis e pela máscara de redes da máquina (presa, exceção ou o perfil do grupo; uma fornalha de um roteador que também move fluidos não aparece em "Só Fluidos"); os filtros rápidos e a visibilidade pelo roteador. Clicar numa máquina escolhe o roteador e a põe como alvo do painel (na presa, a presa); ↑ ↓ andam, ← → recolhem e abrem o bloco, Enter escolhe. No servidor: o estado de cada Tablet no `menu/TabletMachines` (a visão; blocos abertos, recolhidos à mão e com "Mostrar todas", até 256 cada; padrão: aberto se a visão inteira cabe em 256 linhas), mudado pelo `TabletMachinesPayload` (`ROUTERS`, `MACHINES`, `OPEN_BLOCK`, `CLOSE_BLOCK`, `SHOW_ALL`, `SHOW_FEW`; cada ação refaz só a página daquele jogador); a ordem por bloco, roteador e #N no `MachineOrder`, compartilhada por todos os Tablets, um `long` por máquina, que só existe enquanto alguém está em Máquinas (refeita quando o índice muda, solta duas amostras depois do último); as linhas no `TabletMachineRows` (bloco, máquina, "mais N", com a paleta de roteadores), que dividem o teto de 256 da página; o "live" das máquinas à vista pelo `TabletMachineSample`, como na árvore; a presa pelo estado e a vazão do nó. A página por bloco só é montada com a aba Lista aberta; nas outras abas, a página leva os nós de sempre (`TabletMachineRows.LIST_CLOSED`: a visão continua em Máquinas, sem linhas), e o Mapa mostra todos os roteadores. Protocolo `25`.
  - **Marcar, colar e mover em máquinas (etapa 3b da central):** no Selecionar, toda linha tem caixa nas duas visões (roteador, presa, grupo, máquina e cabeçalho de bloco), pela metade quando só parte está marcada (`client/tablet/ListMarks` e `MarkRows`). Marcar um grupo ou um bloco marca as máquinas dele; **Todos** marca o que a consulta mostra na visão aberta (roteadores ou blocos), até 1.024 máquinas. O botão diz o que o Colar atinge ("Colar nos marcados (2 roteadores, 3 máquinas)"; sem espaço, "Colar (5)" e depois "Colar", com o texto inteiro na dica; selecionando, o "Concluir" encolhe ao texto dele e a sobra vai para o Colar). O `TabletProfilePayload` (`PASTE_SELECTED`) e o `TabletMoveNodesPayload` levam também as máquinas (`TabletMarks.MachineKey(node, pos)`), os grupos e os blocos, com os tetos de 1.024, 256 e 256; o bloco marcado é reavaliado no servidor pela última página da visão por máquinas daquele jogador (`MarkedTargets`), com a passada refeita se o índice mudou desde ela e a máquina que saiu do bloco pulada. **Colar** (`MarkedPaste`, um `batchChanges` por roteador, regra das redes pelo `PresetApplier.check`): no roteador, na presa; na máquina, como exceção, com os lados convertidos pela `ProfileSides` (o `RelativeSide` do roteador de origem, pelo quadro da presa de origem, para o `RemoteSide` da máquina pela frente dela), e a exceção igual ao perfil some; no grupo inteiro (decidido no roteador carregado: todas as máquinas do grupo marcadas, pelo grupo, pelo bloco ou uma a uma), no perfil do grupo, e as exceções ficam e são contadas (D2). A presa da origem fica de fora; roteador descarregado ou fora do alcance fica de fora com as máquinas. O Copiar guarda no `profile_source` o quadro da presa de origem, frente e "para cima" num byte (chave `frame`; só a frente não basta, há quatro giros para a mesma frente); um perfil sem ele cola só em roteadores e é recusado em máquinas e grupos ("copie o perfil de novo"). **Mover** (`MarkedMove`): os roteadores inteiros como antes; a máquina marcada só nas faces com modo, gravadas como exceção (D1); o grupo inteiro no perfil (D2); a máquina de um roteador também marcado não conta duas vezes, e o que já estava na rede não conta. O aviso numa linha ("Colado em 3 grupos e 1 máquina · 2 exceções mantidas", e o que ficou de fora; `MarkedNotice`) traz **Desfazer**, que volta o antes da presa, de cada perfil e de cada exceção mudados (inclusive a que não existia), só o que ainda está como ficou (`PasteUndo`, na memória até fechar o menu); um colar que atinge algo troca o Desfazer, e sem mudança nenhuma o aviso sai sem ele. O limite por jogador (`TabletPlayers.takeBulk`) vale por clique.
  - **Lembrar ao reabrir:** o `TabletMenu.State` guarda também a árvore (`TabletTree.Saved`: os roteadores abertos, os grupos recolhidos e os com "Mostrar todas") e a visão por máquinas (`TabletMachines.Saved`: a visão e os conjuntos de blocos), gravados ao fechar e restaurados antes da primeira montagem, que já vai no buffer de abertura; ficam na memória do servidor, limpos ao sair e ao parar o servidor.
  - **Teclado:** digitar procura; ↑ ↓ andam pelas sugestões ou pelas linhas (os nós são escolhidos e o painel acompanha; as linhas da árvore só ficam marcadas); Enter aceita a sugestão, procura, escolhe a linha da árvore marcada ou abre a tela completa; ← → (busca vazia) seguem o `ListTree`: no nó, → abre a árvore e ← a fecha (ou recolhe o grupo de máquina do nó); num grupo de alcance, recolhem e abrem; na presa, numa máquina ou no "mais N", ← sobe para a linha de cima; no "mais N", → mostra todas; Esc fecha as sugestões, depois limpa a busca; Backspace com a busca vazia tira a última etiqueta; Ctrl+C, Ctrl+V e Ctrl+Z copiam, colam e desfazem o perfil. Duplo clique num nó abre a tela completa do roteador.
- **Painel do nó:** clicar num nó o escolhe (`TabletSelectNodePayload`) e o servidor passa a mandar o `TabletPanelPayload` (o `RouterSnapshot` do nó, só com o Tablet aberto e um nó escolhido; reenvia quando o roteador, as redes ou o alcance mudam). O `TabletMenu` é um `RouterEditHost`: os mesmos payloads da tela do roteador (`SetFacePayload`, `SetNetworkPayload`, renomear, filtro) valem pelo Tablet, com a validação nesta ordem: menu aberto, nó visível, roteador carregado (sem carregar chunk) e alcance. Três acessos (`PanelAccess`): **EDIT** (carregado e ao alcance do tier do Tablet, em blocos, ou em qualquer lugar da dimensão quando o tier tem `crossDimension`), **READ_UNLOADED** (o último estado lido, só leitura) e **READ_OUT_OF_REACH** (só leitura com a distância e o alcance). O cabeçalho do painel tem o nome, "Tela completa" e "<Máquina> · <Tier> · N blocos daqui".
- **Clique na máquina (etapa 2 da central):** com o Tablet na mão, o clique direito numa máquina com roteador, ou vinculada a um pelo Upgrade de Alcance, abre a central com o nó dela escolhido (`menu/TabletClick`, pelo `onItemUseFirst` do Tablet, antes da interface da máquina); o painel tem o **No mundo**, que acende a máquina, e a Lista o **Apagar todas (N)**.
- **Filtro à distância (D5):** **Editar** abre a tela de filtro de qualquer lugar ao alcance do Tablet, e em outra dimensão com o tier `crossDimension` (`FilterOrigin`: `NEAR` fecha a 8 blocos, `REMOTE_ROUTER` e `TABLET` valem enquanto o Tablet está no inventário e o roteador ao alcance). **Voltar** reabre a tela de origem: o roteador (com o alvo), o roteador remoto ou o Tablet, no mesmo nó, na mesma busca e na mesma aba.
- **Perfil (copiar e colar):** **Copiar perfil** (Ctrl+C) grava no Tablet o `RouterPreset` do nó (componente `preset`, com `profile_source` guardando o nome, a máquina e a posição da origem); a faixa "Perfil de <nó>" tem "esquecer ✕". **Colar perfil** (Ctrl+V) em um nó; **Colar nas N** no cabeçalho de um grupo (a origem fica de fora); **Colar nos marcados** no modo Selecionar (em roteadores, máquinas e grupos, veja acima). O servidor confere cada nó (visível, carregado, ao alcance) e as redes pela mesma regra do Configurador (`PresetPaste` e `PresetApplier`: só as redes que o jogador pode usar). O aviso mostra quantos nós foram atingidos, com **Desfazer** (Ctrl+Z; o desfazer vive no menu e `RouterPreset.restoreTo` devolve cada nó ao estado de antes). Cada nó do resultado mostra "a origem do perfil", "igual ao perfil" ou "diferente: <Lado> ..." (`ProfileDiff`: modo, rede, filtro, prioridade e redstone, nessa ordem), e o grupo "N diferentes do perfil". O índice de nós guarda os modos por lado e tipo (`face_modes`, 2 bits por lado, no NBT do `NodeIndex`; ausente = Nenhum).
- **Mapa:** vista de cima com cores por status; tocar num ponto escolhe o nó, o mesmo nó escolhido da Lista (o painel vem dele), com Abrir. O roteador escolhido mostra as máquinas dele (`client/tablet/MapMachines`), das posições do `RangeView` do painel, sem nada novo do servidor: um quadradinho por máquina ligado ao roteador por uma linha tracejada (`MapLines`), as acesas pelo Olho em ciano e a máquina-alvo com o contorno; só com o roteador na dimensão do mapa. Clicar numa máquina a escolhe como alvo do painel (`SelectTargetPayload`; o ponto mais perto do mouse pelo `MapPick`, puro). Com a Lista na visão por máquinas, o Mapa mostra todos os roteadores, como na visão por roteadores (a página por bloco só vai com a aba Lista aberta).
- **Índice de nós com as máquinas (etapa 3a):** cada `NodeIndex.Entry` guarda as máquinas vinculadas do roteador, compactas (`network/IndexRemotes`): por máquina a posição, o id do grupo, o rótulo e se tem exceção; por grupo o id, o bloco, o nome e quantas máquinas tem; na ordem de vínculo (a do "#N"). Lidas só no aviso de mudança do roteador (comparadas antes, sem alocar). NBT só com máquinas: `remote_pos` (`long[]`), `remote_group` (`int[]`), `remote_exception` (os índices), `remote_labels` (índice → rótulo, só os com rótulo) e `remote_groups` (`id`, `block`, `name`); o formato antigo lê sem máquinas. Teto de 512 máquinas por roteador (`IndexRemotes.CAP`): acima dele, as 512 primeiras com `remote_total` e o `count` de cada grupo. Os papéis e os modos por lado do filtro por tipo (`network/FaceSummary`, numa passada, sem alocar) juntam a presa, os perfis dos grupos (só se alguma máquina segue o perfil) e as exceções, com o lado relativo à frente de cada máquina; as remotas só contam com o Upgrade de Alcance valendo. **Papéis e redes por máquina (etapa 3b):** na mesma passada, o `FaceSummary` dá os papéis (`int`, formato do `Entry.roles`) e a máscara de redes (`long`) da presa, de cada grupo e de cada exceção, guardados no `IndexRemotes` em arrays paralelos e comparados sem alocar; o bit `k` é a `k`-ésima rede distinta do nó, na ordem de `LoadedTypes.LIST` e `Entry.networks(type)` (`network/NetworkOrder`), e o bit 63 quer dizer "alguma das outras" (passa em qualquer filtro de rede). NBT só com algum valor diferente de zero: `remote_group_roles`/`remote_group_nets`, `remote_exception_roles`/`remote_exception_nets`, `attached_roles`/`attached_nets`; o formato da 3a lê zero e a consulta usa os papéis do roteador até o próximo aviso. Sem o Upgrade de Alcance, os papéis de grupos e exceções ficam gravados como zero (as redes continuam). O `NodeIndex` mantém a soma das máquinas da base (as vinculadas e uma presa por roteador), atualizada quando uma entrada entra, sai ou muda.
- **Fluxos** (no lugar das Estatísticas; `client/tablet/FlowsTab`, com os textos no `FlowsText` e as contas puras no `FlowLines`): no topo, o tempo do mod por tick com a barra do orçamento, a legenda "entra → sai · média de 5 s" e o seletor **Por tipo | Por rede** (lembrados na sessão). Cada linha mostra **entra → sai** na unidade do tipo (itens/s, mB/s ou B/s pelo mesmo corte de 1.000 da tela do roteador e da Lista, FE/t, Source/s): **entra** é o que as faces Extrai entregam, **sai** o que as faces Insere recebem, e o **saldo** dos Armazéns (recebem − dão) aparece à direita como "+N enchendo" ou "-N esvaziando". Ao lado, só o que pede atenção: "N esperando" (só faces Extrai com o que dar e sem destino; um Armazém nunca espera), "N cheios", "N por redstone" e "N sem rede"; movendo e vazias ficam na dica, com os Armazéns. **Por tipo** soma as redes não pausadas; a seta abre as redes do tipo, da maior vazão para a menor, e as pausadas vêm no fim, apagadas ("pausada · fora dos totais"). **Por rede** dá o cabeçalho da rede com os estados e uma linha por tipo. A média é a da janela de 5 s; uma janela ainda curta (logo depois de o servidor subir, ou numa rede nova) mostra a média dos segundos que tem, e só sem nenhum segundo aparece "—". Clicar numa linha abre a Lista filtrada: o tipo, a rede (como a única etiqueta de rede) ou os dois. As faces "sem rede" do jogador comum só contam os nós que ele vê na Lista; o operador vê o total. O servidor manda o `TabletFlowsPayload` só com a aba aberta, no máximo uma vez por segundo e só se mudou (`menu/TabletFlows`).
- **Redimensionável** como o roteador (mínimo 300 × 240, máximo a janela menos 8 px, tamanho lembrado na sessão).
- **Redes (com os grupos como seções, etapa 3b):** uma lista só (`client/tablet/NetworkList`, seções pelo `NetworkSections`, puro): cada grupo de redes é uma seção com ▾/▸ que recolhe (só no cliente, enquanto a tela vive) e Pausar no cabeçalho ("pausado" quando está); as redes sem grupo vêm por último, em "Sem grupo"; uma rede em dois grupos aparece nas duas seções, com a dica "Também em <grupo>". Clicar numa rede mostra à direita cor, membros, privacidade, usar e remover; clicar no cabeçalho mostra o grupo (`GroupDetail`): nome, pausar e retomar o grupo todo, marcar as redes dele ("em <grupo>" ao lado da rede que já está noutro) e remover. "Nova rede" e "Novo grupo" ficam embaixo da lista. As ações no servidor são as de antes (`TabletMenu.Action`, `GROUP_*` e `NETWORK_*`).

### Vinculador

Dois modos (`LinkerMode`, componente `linker_mode`; sem o componente, Vincular). Shift + clique direito no ar alterna. Os nomes antigos do componente (`single`, `area`, `machines`) valem como Vincular, e os componentes `linker_tabs`, `linker_faces` e `linker_type` de itens antigos ficam registrados só para o item carregar.

- **Vincular:** clique num roteador o escolhe (componente `linker_router`); clique em outro bloco vincula a máquina a ele ou, se já estava vinculada, desvincula (`range/RangeLinks`). Shift + clique em dois blocos marca os cantos de uma área (contorno no mundo); clique no ar abre a tela. Vincula sempre sem rede: uma máquina de um tipo novo abre um grupo com as faces de todos os tipos sem rede, e a rede de cada face é escolhida explicitamente no painel (para os grupos antigos, que nasceram com rede em tudo, o painel tem o **Tirar redes**). O Vinculador não mexe em redes.
- **Tela (modo Vincular):** redimensionável como o roteador (mínimo 300 × 204, máximo a janela menos 8 px, tamanho lembrado na sessão; a largura extra vai para a coluna da direita e a altura para o mapa). À esquerda, o roteador escolhido (nome, posição, vagas `usadas/limite` ou o que impede de vincular: sem o Upgrade de Alcance, raio 0, descarregado) e a ajuda dos gestos. À direita, a prévia de cima da área com as máquinas do roteador dentro dela, os cantos, a contagem ou o resultado do último Vincular, e os botões **Vincular área** (ou **Desvincular N**) e **Limpar**. O seletor **Vincular / Desvincular** no cabeçalho é o componente `linker_unlink`: com ele, os cliques e o botão da área tiram as máquinas do roteador em vez de pôr. O Vincular da área liga as máquinas de todos os tipos carregados, as mais perto do roteador primeiro, até o raio e o limite do tier; o desvincular da área não exige o upgrade valendo (dá para limpar a lista depois de tirá-lo).
- **Abrir:** clique numa máquina abre a tela do roteador que chega nela (`linker/LinkerOpen`), já editando a máquina. Os candidatos são o roteador preso nela e os que a têm vinculada pelo alcance (`TabletClick.attachedRouters` e `RemoteIndex.routersAt`), no máximo 16, os presos primeiro e depois por distância. Cada um passa pelo alcance do tier (`TabletMenu.inReach`), por `mayInteract` e pelo evento de clique das proteções de outros mods, conferidos **no roteador** (ele pode estar longe da máquina, no claim de outro jogador). Com um candidato abre direto; com mais de um o servidor manda a lista (`LinkerOpenListPayload`), a tela `LinkerOpenScreen` mostra uma linha por roteador (nome, preso ou alcance, distância) e a escolha volta no `LinkerOpenPickPayload`, que o servidor recalcula do zero, sem guardar estado entre os dois. A tela à distância fica válida enquanto o jogador tem o Tablet (e vê o nó) ou um Vinculador em modo Abrir na mão (`RemoteRouterMenu.mayStayOpen`); o Abrir não exige que o jogador veja a rede do roteador, como o clique normal no bloco dele.
- **Rótulo e nome (etapa 2 da central):** cada `RemoteMachine` tem um `label` opcional (NBT `label`) e cada `MachineGroup` um nome; o `range/MachineNames` valida e resolve o texto mostrado (rótulo, ou o nome do bloco com o número). A lista do alcance mostra, por linha, o nome, as coordenadas e o estado, e o ✎ (ou dois cliques no nome) edita o rótulo da máquina ou o nome do grupo (Enter salva, Esc cancela) pelo `RenameTargetPayload`. A mensagem do Vinculador ao vincular diz a máquina e o grupo.
- **Destaque no mundo (só cliente, `client/highlight/`):** o Olho ◉ da linha (e o **No mundo** do painel do Tablet) acende a máquina através dos blocos, com uma linha contínua até o roteador (por isso o snapshot traz a dimensão do roteador); fica aceso até o jogador apagar ou sair do mundo, até 64 de uma vez, e o **Apagar todas** (no pé da lista do alcance e da Lista do Tablet, com a contagem) apaga todas. Nada é guardado nem mandado ao servidor.
- **Proteção ao vincular:** o clique num roteador ou numa máquina passa pela checagem do jogo (proteção do spawn, borda do mundo e as proteções de outros mods); na área, cada máquina passa pela mesma checagem e as protegidas ficam de fora (a action bar e a tela contam).

### Configurador

Sem tela: tudo é feito com cliques e com a roda. É só o Pincel; o tooltip mostra o estado (cópia, máquina e tipo colado) e os comandos.

| Gesto | O que faz |
| --- | --- |
| Shift + clique direito num roteador | Copia a configuração |
| Clique direito num roteador | Cola nele |
| Shift + clique direito num bloco sem roteador | Limpa a varinha (a cópia) |
| Shift + roda do mouse | Troca o tipo colado |
| Segurar Alt esquerdo | Abre a roda |

- **Tipo colado:** Todos (padrão, sem componente), Itens, Fluidos, Energia e, só com o Mekanism ou o Ars Nouveau, Químicos e Source (componente `configurator_type`; a action bar mostra o tipo). Copiar sempre copia tudo; o tipo vale ao colar. Em Todos, todas as abas (faces, com a rede de cada uma). Num tipo, só as faces daquela aba, ainda sob a regra das redes; as outras abas do roteador ficam como estavam. Exemplo: copiar um roteador configurado só para fluidos e colar em Fluidos sem mexer nos itens e na energia dos outros.
- **Roda:** segurar a tecla da roda (Alt esquerdo por padrão, `key.wirelessautomate.configurator_wheel`) com o Configurador na mão principal abre uma roda radial animada, sem pausar o jogo, com um anel: o tipo colado (Todos e os tipos carregados). A opção atual tem borda laranja, a fatia sob o mouse cresce e o centro mostra o nome e a descrição dela. Soltar a tecla escolhe e fecha; clique escolhe sem fechar; soltar no centro ou Esc fecha sem mudar. A escolha vai ao servidor (`ConfiguratorWheelPayload`, só o tipo), que confere a varinha na mão. Os componentes `configurator_mode`, `configurator_any_machine` e `configurator_area` de itens antigos ficam registrados só para o item carregar.
- **Redes ao colar:** a rede de cada face só é colada se o jogador pode usá-la (dono ou operador, ou rede pública) e só sai de uma rede que ele gerencia (`PresetPaste.canLeave`); senão, a face fica com a rede de antes. A cópia vale para as faces da máquina presa; perfis e exceções do alcance ficam de fora.

## Filtros

Cada face da máquina, para cada tipo, tem um filtro embutido sem limite de entradas, mais slots para cartões. O recurso passa se for aceito pelo conjunto.

| Regra | Exemplo | Vale para |
| --- | --- | --- |
| Exato | Lingote de ferro, água | Itens, fluidos, químicos |
| Tag | `#c:ingots`, `#c:ores` | Itens, fluidos |
| Mod | `@mekanism` | Itens, fluidos, químicos |
| Regra por propriedade | Qualquer item encantado | Itens |

- **Adicionar:** Shift + clique num item do inventário, ou clique no JEI mesmo sem ter o item. Arrastar do JEI para a lista também funciona. Tags e mods pelo inspetor ou pela busca, várias de uma vez. Duplicados são ignorados.
- **Regra por propriedade:** pega o item que cumpre **todas** as condições marcadas, cada uma com tanto faz, sim ou não: encantado (inclusive livro), danificado, renomeado, com poção, empilhável e com conteúdo (caixa de shulker, bundle); mais um encantamento com nível mínimo de 1 a 255 (no item ou no livro; escolhido pelo nome ou pelo id), a durabilidade restante (≥ ou < uma porcentagem) e o "só em" (uma tag ou mod: encantado + `#c:armors` = só armadura encantada). Aceita estoque e lista negra como qualquer entrada, e é editável depois.
- **Tamanho:** sem limite na tela, com rolagem. Um teto interno de 4.096 entradas protege o dado salvo e o pacote de rede.
- **Custo:** o filtro é compilado em conjuntos de hash, então conferir um item custa o mesmo com 9 ou com milhares de entradas. As regras por propriedade não cabem num mapa: são perguntadas em ordem, só as que vêm antes da melhor resposta dos mapas, então o custo cresce com o número de regras (poucas, na prática).
- **Modo:** lista branca ou negra, por filtro.
- **Componentes:** ignorar (picareta encantada = picareta) ou exigir iguais.
- **Estoque:** ao inserir, aceitar só até N no destino; ao extrair, manter sempre N na origem.
- **Cartões:** carregam o filtro completo e podem ser duplicados.

## Redes, distribuição e redstone

Não se liga um roteador a outro: cada face da máquina, em cada tipo de recurso, entra numa rede (ou em nenhuma), e tudo do mesmo tipo na mesma rede troca entre si. Quem envia e quem recebe vem da configuração das faces da máquina; a ordem de entrega é por prioridade e, empatando, por round-robin.

- **Rede por face:** a rede é só da face (`FaceConfig.network`), sem rede da aba nem herança. Exemplo: uma fornalha com um roteador só, na aba Itens a face Cima insere pela rede "Mina" e a face Baixo extrai pela rede "Armazém"; a Energia dela pode ir para a "Base". Vale para a máquina presa, para o perfil de grupo e para a exceção do roteador de alcance. Ao colocar, nenhuma face entra em rede: o jogador configura o primeiro roteador e replica com o Configurador; quem não quer separar nada vincula tudo de uma vez (Vinculador em Todos, com as seis faces).
- **Membro de uma rede:** o nó é membro de toda rede que aparece em alguma face dele (presa, perfis e exceções), sem olhar o modo; cada porta entra só nas rotas da rede da sua face. Qualquer mudança nas faces de um tipo (inclusive a rede de uma face) suja esse tipo em todas as redes do nó nele, as de antes e as de agora.
- **Rede apagada:** as faces que estavam nela ficam sem rede (guardam o `UUID`, lido como "sem rede" na tela e no Tablet), sem varredura do mundo.
- **Mundos antigos:** a rede da aba (chave `networks`, ou a única `network`) vai, na carga, para as seis faces daquele tipo da presa, dos perfis e das exceções que ainda não têm rede, e deixa de ser escrita.
- **Faces da máquina:** o roteador acessa a máquina por qualquer face, não só pela que está encostado, porque o NeoForge consulta inventários informando a face. Cada face, por tipo, fica em Extrai, Insere, Armazém ou Nenhum.
- **Armazém:** a face recebe de quem extrai e entrega para quem insere, mas não troca com outra face Armazém (assim os recursos não vão e voltam entre dois baús). Serve para buffers e baús de armazenamento.
- **Entrar numa rede:** o roteador nasce sem rede (o item que traz dados do block entity, como um roteador quebrado e pego de volta, mantém as redes que trouxe). Uma face entra pela linha Rede da tela do roteador, pelo Vinculador (as faces e as abas marcadas na tela dele), colando com o Configurador ou por `/wa face <pos> <tipo> <face> network <nome|none>` (operador). Sai pela mesma linha ("Sem rede"), pelo Vinculador em "Nenhuma (desvincular)" ou pelo `none` do comando. Um grupo novo do roteador de alcance, vinculado pelo Vinculador (clique ou área), nasce com as faces sem rede; a rede de cada face deve ser escolhida explicitamente no painel. Um grupo que já existe não muda.
- **Em massa:** seleção múltipla no Tablet (todas as faces do tipo), ou o Configurador copiando a rede de cada face junto com o preset.
- **Grupos:** juntam várias redes de um mesmo sistema para ver, pausar e retomar tudo de uma vez.
- **Redstone:** por face e por tipo: ignorar, ativo com sinal ou ativo sem sinal.
- **Chunks:** origem ou destino descarregado pausa a rota sem custo; o upgrade de chunk loading mantém o chunk do roteador carregado.
- **Visualização (planejado):** segurando o Configurador ou o Tablet, linhas de conexão saem de quem extrai para quem insere.

### Exemplo: processamento 5x do Mekanism

Redes divididas por fluxo, não por máquina: cria-se uma rede nova quando o mesmo recurso pode ir para destinos que precisam ficar separados.

| Rede | Tipo | Extrai | Insere |
| --- | --- | --- | --- |
| Minério | Itens | Buffer do minerador | Dissolução (`#c:ores`) |
| Slurries | Químicos | Dissolução e lavadoras | Lavadoras (suja) e cristalizadores (limpa) |
| Intermediários | Itens | Cada máquina da cadeia | A próxima, filtrada por tag; lingotes vão para o armazenamento |
| Utilidades | Fluidos e químicos | Separadores, infusores, bomba | Água, O₂, HCl e ácido sulfúrico para os consumidores |
| Energia | Energia | Geradores | Todas as máquinas |

As cinco redes formam o grupo Linha 5x. Para replicar a linha, copie com o Configurador o roteador de cada estágio e cole nos outros, um a um (ou, para muitos de uma vez, cole o perfil nos nós marcados do Tablet).

## Presets e replicação

O Configurador guarda uma cópia só, no próprio item (decisão do dono: sem biblioteca, sem código de texto e sem tela, para ficar simples).

- **Cópia:** faces, filtros, prioridades, redstone e a rede de cada face (dentro do `FaceConfig`), mais o tipo de bloco da máquina do roteador copiado. Só a máquina presa: os perfis e as exceções do roteador de alcance não entram na cópia nem mudam ao colar. O preset antigo, com `networks` por tipo, vale como essa rede nas seis faces do tipo. Copiar de novo substitui a cópia.
- **Tipo colado:** Shift + roda do mouse escolhe Todos ou uma aba só (Itens, Fluidos, Energia, Químicos com o Mekanism); colar num tipo só não toca nas outras abas do destino (faces e as redes delas).
- **Pincel:** Shift + clique copia de um roteador, clique cola em outro (em qualquer máquina).
- **Aplicação relativa:** as faces são salvas em relação à orientação do roteador, então a cópia funciona com o bloco virado para qualquer lado.
- **Redes:** a regra vale por rede distinta da cópia: uma rede que o jogador não pode usar não é aplicada, e as faces que a teriam ficam com a de antes, com aviso. Uma face sem rede na cópia não mexe na rede do destino.

## Integrações

A base são as capabilities padrão do NeoForge, que cobrem quase todo mod do ATM10 sem código específico. As integrações diretas existem só para ganhar vazão.

| Alvo | Caminho | Ganho | Dependência |
| --- | --- | --- | --- |
| Qualquer inventário, tanque ou máquina | Capabilities de item, fluido e energia do NeoForge | Compatibilidade geral | Nenhuma |
| Químicos do Mekanism | API de químicos do Mekanism | Suporte a gases e afins | Opcional |
| Source do Ars Nouveau | Capability `ars_nouveau:source` (só em `compat/arsnouveau`) | Aba Source: jarras, relays e máquinas do Ars | Opcional |
| AE2 (planejado) | Armazenamento por chave e quantidade `long` | Uma chamada move milhões de itens | Opcional |
| Refined Storage 2 (planejado) | API de armazenamento por recurso | Mesmo ganho do AE2 | Opcional |
| Sophisticated Storage (planejado) | Handler de itens com cache de slots | Varredura incremental de baús grandes | Nenhuma |
| JEI | Ingredientes fantasmas e a receita de upgrade na bancada | Arrastar e clicar para os filtros | Opcional |
| GuideME | Livro-guia data-driven | Guia no jogo, em inglês e português | Opcional |

Hoje existem as capabilities do NeoForge, os químicos do Mekanism, a Source do Ars Nouveau, o JEI e o GuideME; os atalhos de AE2, RS2 e Sophisticated Storage são planejados (o Sophisticated já funciona pelas capabilities). Sem um mod opcional instalado, a parte correspondente simplesmente não carrega e o resto funciona normal.

## Armazenamento do mod

Motivação: no teste do dono (8/10/2026), 12 milhões de pedregulhos entre dois barris do Sophisticated Storage no Ultimate pararam em ~274 mil itens/s, com o orçamento de 1 ms cheio. O teto não é do motor: o `IItemHandler` extrai no máximo uma pilha (64) por chamada, então cada 64 itens custam quatro chamadas ao outro mod. Um armazenamento do próprio mod guarda **quantidades `long` por tipo** e conversa com o roteador por uma API interna, sem esse limite.

Decisões do dono (8/10/2026):

- **Cinco blocos separados**, cada um com tiers: Baú (itens), Tanque (fluidos), Bateria (energia), Tanque Químico (só com o Mekanism, pela ponte `Chemicals`) e Tanque de Source (só com o Ars Nouveau, pela ponte `Sources`).
- **Capacidade por tier**, com os mesmos Cartões de Upgrade do roteador e as mesmas receitas de anel (Básico → Avançado → Elite → Esmeralda → [Allthemodium → Vibranium → Unobtainium] → Ultimate; o cartão vale no tier da origem dele, e a receita direta sobe o item do degrau anterior), sem perder o conteúdo. O Ultimate não tem limite (satura em `Long.MAX_VALUE`).
- **Tipos ilimitados:** o limite é só a quantidade total do tier.
- **Tela em lista com busca** (estilo terminal do AE2), redimensionável pelas bordas e pela alça do canto, sempre centralizada: grade rolável de tipos com a contagem abreviada (12,6M), busca por nome e `@mod`, ordenação por quantidade, nome ou mod. Clique tira uma pilha, Shift + clique no inventário guarda.
- **O roteador continua sendo colocado na face, como em qualquer máquina.** Ele reconhece o armazenamento do mod e usa o atalho; a configuração por face, as redes e os filtros não mudam.
- **Versão 0.2**, depois do envio da 0.1.1.

Capacidade proposta por tier (ajustável na config do servidor, seção `storage`):

| Tier | Baú (itens) | Tanque e Tanque Químico | Bateria | Tanque de Source |
| --- | --- | --- | --- | --- |
| Básico | 32.768 | 256.000 mB | 1.000.000 FE | 10.000 |
| Avançado | 262.144 | 2.048.000 mB | 8.000.000 FE | 80.000 |
| Elite | 2.097.152 | 16.384.000 mB | 64.000.000 FE | 640.000 |
| Esmeralda | 16.777.216 | 131.072.000 mB | 512.000.000 FE | 5.120.000 |
| Allthemodium¹ | 134.217.728 | 1.048.576.000 mB | 4.096.000.000 FE | 40.960.000 |
| Vibranium¹ | 1.073.741.824 | 8.388.608.000 mB | 32.768.000.000 FE | 327.680.000 |
| Unobtainium¹ | 8.589.934.592 | 67.108.864.000 mB | 262.144.000.000 FE | 2.621.440.000 |
| Ultimate | Sem limite | Sem limite | Sem limite | Sem limite |

Como a transferência usa o atalho:

- **Capability própria** (`wirelessautomate:bulk_items` e as equivalentes de fluido, energia, químico e `wirelessautomate:bulk_source`), lida pelo mesmo `BlockCapabilityCache` da face. Nada de `instanceof` nem busca por tick. API por chave e quantidade: `insert(chave, long, simular)` e `extract(chave, long, simular)`, mais a lista de chaves com quantidade.
- **Do mod para o mod:** uma operação por tipo de item, qualquer que seja a quantidade. Os 12 milhões viram uma chamada; o limite volta a ser o tier do roteador (ou nenhum, no Ultimate).
- **Do mod para outro mod:** a origem entrega em blocos maiores que uma pilha, e o destino aceita o que a inserção dele aceitar. A sobra volta para o nosso baú.
- **De outro mod para o mod:** limitado pela extração da origem (64 por chamada), mas a inserção do nosso lado é O(1).
- Filtros da face (embutido e cartões), estoque, prioridade, round-robin e sono valem igual: a regra é a mesma do `ItemTransfer`, só a chamada muda.

Compatibilidade: o bloco também expõe as capabilities padrão do NeoForge (`IItemHandler` com um slot virtual por tipo mais um vazio, extração de no máximo uma pilha; `IFluidHandler`, `IEnergyStorage`), então funis, AE2, RS e outros mods o veem como um inventário comum. O atalho é só para o nosso roteador.

Também no desenho:

- **Filtro de entrada no bloco**, reaproveitando `FilterSet`, a tela de filtro e o Cartão de Filtro: o que pode entrar, por qualquer caminho.
- **Sem tick:** o block entity só guarda dados e avisa o `NetworkManager` quando o conteúdo muda (acorda as origens e os destinos presos a ele). A tela recebe diferenças e só enquanto está aberta.

Decididos pelo dono depois (8/10/2026):

- **Quebrar o bloco:** o conteúdo fica no servidor (um `SavedData` com um id); o item leva só o id e um resumo (tipos e total). Não estoura o limite de pacote. O item é uma referência: duplicá-lo (criativo, dupe) não duplica o conteúdo, os dois apontam para o mesmo.
- **Capacidades:** a tabela acima é o padrão, ajustável na config.
- **Receitas:** vanilla, só do tier Básico de cada bloco; os tiers seguintes vêm dos anéis (Cartões de Upgrade ou a receita direta), como no roteador.
- **Texturas:** prontas em `scripts/textures/gerar_texturas.py` (ver "Armazenamento do mod" no pacote de design).
- **Tanque, Bateria e Tanque Químico prontos (8/10/2026):** no mesmo molde do Baú. As APIs de fluido e energia do NeoForge são em `int` (até ~2,1 bilhões por chamada, e o motor faz uma por tipo por tick), então o Tanque e a Bateria têm capabilities próprias em `long` (`BulkFluids`, `BulkEnergy`) que o roteador usa quando a origem ou o destino é do mod; a de químico do Mekanism já é em `long`. O Tanque troca baldes no bloco e pela tela; a Bateria tem uma tela própria com a carga e a variação por tick, e não tem filtro.
- **Tanque de Source pronto (8/10/2026, etapa 2):** guarda Source em `long` na base `ScalarStorageBlockEntity` (a mesma da Bateria, com o `ScalarStore`). Capacidade por tier na tabela acima (config `sourceTankCapacity`; de 10.000 no Básico, sem limite no Ultimate). Sem filtro nem lista: a tela (`StorageScalarScreen`) tem a barra roxa, a vazão em Source/s e uma linha de dica. O bloco mostra o nível na coluna de vidro (propriedade `fill`, 0 a 10, forma fina como a Source Jar). Para o roteador, a capability `wirelessautomate:bulk_source` (em `long`, entre dois tanques tudo passa de uma vez). Para o Ars, `ArsStorage` dá a `ISourceCap` (roteador e Relays) e registra o provider no `SourceManager`: Sourcelinks a 5 blocos depositam e as máquinas do Ars tiram, como de uma Source Jar; a visão em `int` fica limitada a `Integer.MAX_VALUE`. Receita vanilla `IGI/GTG/IGI` com ferro, gema de Source e um Tanque Wireless, só com o Ars. Sempre registrado; sem o Ars some da aba criativa e do JEI, e o tooltip diz que precisa do Ars.
- **Cores dos tipos:** Químicos `#97C853` (o verde do Tanque Químico) e Source `#B36DE0` (o roxo do Ars). Na barra do Tanque de Source: corpo `#9749C2`, sombra `#6B2F8F`, brilho `#EA8EF3`.
- **Baú pronto (8/10/2026):** tudo o que está acima para o Baú, mais o filtro de entrada indo junto no item quebrado e o upgrade na bancada (a mesma receita do roteador). Na tela: clique pega uma pilha, botão direito meia, Shift + clique manda para o inventário; com item no cursor, clicar na lista guarda (botão direito, um). A tela recebe só as diferenças por tipo, no máximo a cada 5 ticks, e só aberta.

## Ressonador de Sinal

Gerador multibloco (estrutura 5×5×5 de Carcaça e Vidro com o Núcleo de Ressonância, Portas de Energia e de Cristais e o Sintonizador). O Núcleo gera FE pelas câmaras de cristal, uma por tier (Básico 1 … Ultimate 8), pela sintonia do dial com a estação que deriva. Specs: `docs/superpowers/specs/2026-10-15-ressonador-de-sinal-design.md` (base), `2026-10-20-ressonador-rebalanceamento-design.md` e `2026-10-21-ressonador-cascata-design.md` (rebalanceamento; a de 21/10 vale onde divergem).

**Escada.** FE/t = soma dos rendimentos das câmaras × FE por cristal × eficiência × fator da interferência × fator do Eco. FE por cristal padrão (config `resonator.<tier>.fePerCrystal`) e o máximo com o Afinado do tier em todas as câmaras: Básico 216 (432 FE/t), Avançado 6.912 (27.648), Elite 10.000 (60.000), Esmeralda 160.000 (1.280.000), Allthemodium 6.400.000 (64M), Vibranium 6.700.000 (80,4M), Unobtainium 10.700.000 (149,8M; era 184M até a reavaliação de 22/10), Ultimate 268.435.456 (4,29B, mais o Eco). A reavaliação mediu a cascata inteira dos rivais e pôs o custo de cada tier na reta dos itens de base × FE/t: do Esmeralda ao Ultimate os Corações têm uma receita com itens de MEGA Cells, Draconic Evolution e Modern Industrialization (`resonator_heart_<tier>_cascade`, só com os mods; a de antes vale sem eles), e a construção completa custa ~4,5 mil (Esmeralda), 42,5 mil (Allthemodium), 49,4 mil (Vibranium), 70,9 mil (Unobtainium) e 650 mil (Ultimate) itens de base; Básico a Elite seguem em 356, 562 e 868.

**Cristais e rendimento.** O rendimento conta em quartos. No Básico e no Avançado: Cristal de Sinal 1, Lapidado 2, Afinado 2. Do Elite para cima: só o Cristal Afinado de tier igual ou maior que o do Núcleo rende 2; o Lapidado e o Afinado de tier menor rendem 1/2, o Cristal de Sinal 1/4. Os Afinados (Elite ao Ultimate) saem de um Lapidado cheio, ou de um Afinado de tier menor, com o material do tier (receita `tune_crystal`). A vida da carga a 100% depende do tier do Núcleo (`crystalLifeMinutes`, padrão 20, 20, 15, 10, 5, 5, 4 e 3 minutos).

**Fadiga.** Cada recarga gasta 1 de integridade (`crystalIntegrity`, padrão 8 cargas por cristal); com 0, o cristal racha: não gera, não recarrega e a Porta de Cristais o tira como o Opaco. Rachado + 4 Cristais de Sinal cheios (`recastCrystals`) refazem o mesmo cristal na bancada (`recast_crystal`).

**Catalisador da recarga.** Básico e Avançado: redstone ou Pó de Ressonância. Elite e Esmeralda: só o Pó. Allthemodium ao Ultimate: só o Pó Carregado do tier (8 Pós + 1 lingote do tier, rende 8). O catalisador que o tier não aceita mais (gravado antes da regra, ou de um Núcleo que subiu) é gasto até acabar.

**Coração do Ressonador.** Um por tier, feito pela cascata de itens próprios (Liga de Sinal, Placa, Fio, Bobina, Lente Harmônica, Circuito e Matriz de Fase, Estabilizador de Campo, Núcleo Condensado e, no Ultimate, a Moldura de Liga). O Básico vai na receita do Núcleo; subir o Núcleo pelo Cartão de Upgrade gasta o Coração do tier de destino do inventário (sem ele, nada muda).

**Interferência.** Cada outro Núcleo montado e carregado da mesma dimensão a até 32 blocos (Chebyshev, `interferenceRadius`, 0 desliga) tira 15% da eficiência (`interferencePenalty`), até 60% (`interferenceMax`), depois do teto do Sintonizador. A conta só roda ao montar, desmontar, carregar e descarregar um Núcleo.

**Eco Ressonante (só Ultimate).** Um Afinado Ultimate rachado posto no slot Eco (ou entregue pela Porta de Cristais) é queimado e soma 1 de Eco: +5% da geração base cada (`ecoBonus`), sem teto por padrão (`ecoMax`); o buffer acompanha. O Eco fica no Núcleo quebrado (componente `resonator_eco`).

**Tela do Núcleo** (352 × 244): osciloscópio, dial e modo, câmaras com a barra de integridade, o catalisador pelo tier, eficiência, geração, buffer, a linha da interferência, a Estrutura e, no Ultimate, a caixa do Eco. Sem comparador, sem redstone de controle e sem Tablet (decisões do dono).

## Arquitetura de performance

Regra de ouro: uma operação que não move nada não pode custar nada. O custo vem de verificar, filtrar e tentar inserir em destinos cheios, então a arquitetura evita cada um desses passos.

Ciclo do gerenciador a cada tick: pega a próxima rota acordada (em ordem de prioridade) → se o orçamento acabou, salva o cursor e segue no próximo tick → senão, move o lote com o filtro já em cache → se o destino recusou, ele dorme até o vizinho avisar uma mudança; se aceitou, segue para a próxima rota.

### Técnicas

1. **Gerenciador central:** os nós não fazem tick. Um único gerenciador por servidor processa todas as redes, com rotas pré-ordenadas por prioridade e reconstruídas só quando a rede muda, e só no tipo de recurso que mudou. Sinal de redstone só remonta se alguma face usa redstone; invalidação de capability e mudança de estado da máquina não remontam.
2. **Orçamento de tempo:** o mod tem um teto de tempo por tick (padrão 1 ms, configurável em `config/wirelessautomate-server.toml`). Se o trabalho não couber, continua no tick seguinte a partir de cursores salvos. O teto se reduz sozinho quando o MSPT do servidor sobe.
3. **Destinos dormindo:** um destino que recusa um recurso entra numa lista negativa por tipo de recurso e é pulado. Ele acorda quando o inventário vizinho avisa uma mudança ao nó, com custo zero enquanto nada muda. Como reserva, há checagens com intervalo crescente, de 1 tick até alguns segundos. As origens também dormem, com o motivo guardado: a vazia acorda quando a própria máquina muda, a que espera destino acorda junto com o primeiro destino que acordar. As entregas do próprio mod não acordam as outras origens, e um destino sem máquina dorme até a máquina aparecer.
4. **Lotes:** em vez de mover pouco a cada tick, move a quantia de vários ticks numa operação só. A vazão é a mesma e o custo fixo cai. A energia é distribuída num único passe, com contas em `long`.
5. **Filtros compilados:** ao salvar, o filtro vira conjuntos de hash, com as tags já expandidas num mapa imutável, refeito só quando as tags recarregam. Cada slot passa pelo filtro uma vez só (passa e estoque juntos), e o destino que atingiu o estoque para a varredura na hora.
6. **Caches de capability:** cada face usa `BlockCapabilityCache`, que guarda a referência ao vizinho e avisa quando ela muda. Nenhuma busca de capability por tick.
7. **Varredura incremental (planejado):** em inventários grandes, um cursor lembra onde parou e um índice guarda os slots com espaço ou com o recurso desejado.
8. **Custo por vizinho (planejado):** o mod mede o tempo de cada destino. Destinos lentos de outros mods passam a ser chamados com menos frequência, sem frear o resto da rede.
9. **Sem trabalho no cliente:** a tela recebe só diferenças, e só enquanto está aberta. Nada é sincronizado com a tela fechada.
10. **Profiler embutido:** `/wa profile` mostra ms/tick por rede, operações, destinos dormindo e acordados, e o custo do amostrador dos Fluxos.
11. **Fluxos medidos sem custo no caminho quente:** cada porta tem dois contadores `long` (`gave` e `got`), somados junto do que a transferência já faz, sem alocação, mapa ou chamada nova por item. O amostrador (`network/FlowSampler`, um por gerenciador, fora do orçamento do transporte) roda sempre, espalhado em 20 baldes de fase pela posição do nó: a cada tick lê e zera os contadores das portas só dos nós do balde da vez, e a volta pelos 20 baldes é o segundo; soma por rede × tipo numa janela de 5 baldes (`FlowWindow`) e conta os estados (pelo sono das portas) e as faces paradas por redstone ou sem rede (pela configuração do nó, contada uma vez e guardada até o roteador, as redes ou a config mudarem, sem ler o mundo). Custa O(portas + nós) por segundo em todo servidor, com ou sem Tablet, dividido em 20 ticks (sem pico), para a aba Fluxos abrir já com os números; o resultado só é montado quando alguém olha. O custo (média por segundo, último e o tick mais caro) sai no `/wa profile`. Só o pacote depende de alguém olhar: ele sai só para quem está com a aba Fluxos aberta.
12. **Destaque no mundo só no cliente:** as máquinas acesas ficam num conjunto na memória do cliente (`MachineHighlights`), sem pacote, sem tick e sem trabalho com nada aceso; com acesas, o desenho é uma passada por quadro (até 64).

## Plano de benchmark

O Sophisticated Storage é o banco de testes: baús com centenas de slots e slots com quantidades enormes forçam os dois piores casos ao mesmo tempo. A meta é manter o mod abaixo do orçamento em todos os cenários.

| Cenário | Montagem | O que mede | Meta |
| --- | --- | --- | --- |
| Vazão bruta | 1 baú cheio enviando para 1 baú vazio, tier Ultimate | Itens/s máximos | Maior número possível dentro do orçamento |
| Destino cheio | 50 origens enviando para destinos já cheios | Custo de tentar e falhar | Perto de 0 ms/tick após dormir |
| Muitos tipos | Baú com centenas de itens diferentes e filtros grandes | Custo de filtro e varredura | Abaixo do orçamento |
| Muitos nós | 500 nós ativos numa rede | Escala do gerenciador | Abaixo do orçamento |
| Rede ociosa | 500 nós sem nada para mover | Custo de existir | Perto de 0 ms/tick |
| Misto | Itens, fluidos e energia ao mesmo tempo | Comportamento realista | Abaixo do orçamento |

- **Medição:** profiler embutido mais o Spark, com MSPT antes e depois de colocar a rede.
- **Comparação:** os mesmos cenários com outros mods de transporte do ATM10, para sustentar o "mais eficiente".
- **Regressão (planejado):** um cenário automatizado em GameTest rodará a cada mudança de código para detectar perdas de performance.

## Receitas e progressão

As receitas da escada vanilla usam só itens vanilla; as dos tiers do Allthemodium usam as tags `c:` dele e só carregam com o mod (`neoforge:conditions`). Todas ficam em data packs (`data/wirelessautomate/recipe/`), então o modpack pode trocar tudo sem mexer no código. O roteador nasce Básico. Cada tier tem **um anel** (`tier_core_<tier>.json`, tipo `wirelessautomate:tier_upgrade`, `TierUpgradeRecipe`): oito itens em volta do centro, e o centro decide a receita. Redstone dá o cartão de um degrau (origem no degrau anterior da escada da instância); um cartão com origem k, um degrau abaixo, dá o cartão que pula (k → este tier); o roteador ou armazenamento do degrau anterior sobe direto, com os componentes do item. Como o anel é o mesmo nas três, subir pela bancada degrau a degrau e montar o cartão custam o mesmo (o cartão, mais uma Redstone), e o cartão só vale no tier da origem. A escada não fica no cartão: um cartão feito sem o Allthemodium continua valendo (e pula os anéis dele) se o mod entrar depois. O editor de receitas não lista os cartões (tipo próprio). Decisão do dono, 12/10/2026; ver `docs/superpowers/specs/2026-10-12-subir-tier-design.md` (substitui a regra de custo de `2026-10-09-upgrade-direto-design.md`).

| Item | Receita |
| --- | --- |
| Roteador (Básico) | Olho de ender em cima, ferro + redstone + ferro no meio, três ferros embaixo |
| Anel do Avançado | Lingotes de ouro nos cantos, diamantes nas bordas |
| Anel do Elite | Lingotes de netherita nos cantos, obsidiana nas bordas |
| Anel do Esmeralda | Blocos de esmeralda nos cantos, estrela do Nether em cima, olho do ender embaixo, obsidiana chorona dos lados |
| Anel do Allthemodium (com o mod) | Lingotes de Allthemodium em volta, blocos em cima e embaixo |
| Anel do Vibranium (com o mod) | Lingotes de Vibranium nos cantos, liga Vibranium-Allthemodium em cima e embaixo, blocos dos lados |
| Anel do Unobtainium (com o mod) | Lingotes de Unobtainium nos cantos, liga Unobtainium-Vibranium em cima e embaixo, blocos dos lados |
| Anel do Ultimate | Ovo do dragão em cima, três blocos de netherita e quatro fragmentos de eco (sem o Allthemodium e com ele sem o All The Tweaks). No ATM10 (Allthemodium + All The Tweaks): fragmentos de ATM Star nos cantos, ovo do dragão em cima, blocos de liga Unobtainium-Allthemodium |
| Upgrade na bancada com o cartão | Bloco + Cartão de Upgrade com a origem no tier do bloco (ou antigo, de qualquer tier acima), sem forma. No JEI: um par por origem e destino, a receita direta e um exemplo do cartão que pula por tier |
| Cartão de filtro (2) | Papel, redstone e comparador (`PRP` / `PCP`) |
| Cópia de Cartão de filtro | Cartão configurado + cartão vazio, sem forma = dois iguais |
| Upgrade de chunk loading | Olho de ender e diamante cercados de obsidiana, com duas redstones |
| Configurador | Fragmento de ametista na ponta de dois gravetos, na diagonal |
| Vinculador | Pérola do ender em cima, redstone entre cobres e três lingotes de cobre embaixo |
| Tablet de rede | Lingotes de ferro dos lados; no meio, de cima para baixo, painel de vidro, olho de ender e redstone |
| Livro-guia (só com o GuideME) | Livro + redstone, sem forma |

Balanceamento feito em 8/10/2026: escada ×8 com os tiers vanilla modestos e os do Allthemodium com materiais do ATM10 (fragmento de ATM Star no Ultimate).

## Editor de receitas

`/wa recipes` (só operador) abre uma tela para editar as receitas de bancada do mod sem arquivos: lista com busca e estado (padrão, editada, desativada), grade 3×3 de slots fantasmas (inventário, cursor ou JEI), Item/Tag, quantidade do resultado, Salvar, Restaurar padrão e Desativar. As edições vão para `config/wirelessautomate/recipes/`, um datapack global (vale em todos os mundos da instância e sobrevive a atualizações); a recarga vale para todos os jogadores. Outro datapack ou o KubeJS na mesma receita ganha, e a tela avisa. Fora: receitas de outros mods, as especiais e criar receita nova. Design completo em `docs/superpowers/specs/2026-10-10-editor-de-receitas-design.md`.

## Roadmap

O v1 entrega o motor de transferência e a configuração essencial; o v2 completa a experiência.

**v1, motor e essencial**

- [x] Projeto NeoForge 1.21.1 e gerenciador central com orçamento de tempo
- [x] Roteador direcional com itens, fluidos e energia, configurado por face da máquina
- [x] Redes, rede ativa, prioridade, round-robin e redstone
- [x] Filtros sem limite (inventário, JEI, tags, mod, estoque) e cartões
- [x] Filtro v2 (1.0): tela em lista redimensionável, inspetor e busca de tags e regras por propriedade
- [x] Tiers e Cartões de Upgrade
- [x] Configurador como pincel e Vinculador modo Único (o Vinculador virou Vincular e Abrir máquinas em 22/10/2026)
- [x] Profiler embutido e benchmark com Sophisticated Storage

**v2, escala e integrações**

- [x] Ressonador de Sinal (gerador multibloco): etapas 1 (minério, cristais e a receita do Lapidado), 2 (estrutura, Núcleo, Portas, comando e projeção), 3 (tela do Núcleo, Sintonizador automático e câmara animada) e 4 (JEI, guia e GIF da vitrine; sem Tablet, por decisão do dono) prontas no ramo `ressonador`. Spec em `docs/superpowers/specs/2026-10-15-ressonador-de-sinal-design.md`. Rebalanceamento (21/10): etapas 1 (escada, quartos, Afinados, fadiga, Pó, catalisador por tier), 1b (vida por tier, Pó Carregado, cascata e Coração) e 2 (interferência, Eco e tela) prontas; falta a etapa 3 (JEI e guia)
- [x] Químicos do Mekanism (aba Químicos com modo, prioridade, redstone, rede e filtro exato ou por mod; sem cartões de filtro)
- [x] Source do Ars Nouveau (aba Source, sem filtro e sem cartões, vazão por tier)
- [x] Tablet: lista, mapa, estatísticas, redes e grupos
- [x] Configurador colando em área (mesma máquina) e Vinculador modo Área (saíram em 22/10/2026: o Configurador ficou só com o Pincel)
- [ ] Atalhos para AE2 e Refined Storage 2
- [x] Upgrade de chunk loading
- [x] Texturas finais (geradas por `scripts/textures/gerar_texturas.py`)
- [x] Livro-guia no GuideME (opcional)
- [ ] Balanceamento das receitas

**0.2, armazenamento do mod**

- [ ] Baú, Tanque, Bateria e Tanque Químico com tiers, tela em lista e atalho no roteador (ver "Armazenamento do mod")

### Decisões tomadas (7 de outubro de 2026)

- **Canais:** só redes mais filtros no v1. Fluxos separados usam redes diferentes.
- **Receitas dos tiers altos:** vanilla até a Esmeralda (ouro e diamante, netherita e estrela do Nether, esmeralda e obsidiana chorona); com o Allthemodium, os metais dele e, no ATM10, o fragmento de ATM Star no Ultimate. O modpack ajusta por datapack ou KubeJS.
- **Orçamento padrão:** 1 ms/tick (era 0,5 ms; o dono trocou por mais vazão em 7/10/2026, depois do benchmark).
- **Cartões no roteador:** slots de cartão por face e por tipo, além do filtro embutido. O recurso passa se o filtro embutido ou algum cartão aceitar.
- **Duplicar cartões:** receita sem forma, cartão configurado + cartão vazio = dois cartões iguais.
- **JEI:** integração opcional já no v1 (dependência só de compilação, plugin carregado só com o JEI).
- **Modo Armazém** (antes "Ambos"): uma face Armazém não entrega para outra face Armazém; extrai para faces que só inserem e recebe de faces que só extraem.
