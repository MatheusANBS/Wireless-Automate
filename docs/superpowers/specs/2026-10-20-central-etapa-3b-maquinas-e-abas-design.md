# Central da rede, etapa 3b: as máquinas na Lista e as abas novas

Data: 20/10/2026. Ramo: `central-etapa-3b`, a partir do `main` com a etapa 3a (merge `c69380e`). Mockup aprovado: `docs/preview/central-etapa-3b.html` (com as telas da versão 6, `docs/preview/central-da-rede.html`).

**Decisões do dono (as seis na opção recomendada):**
- **D1:** Mover com uma máquina marcada muda a rede só das faces com modo daquela máquina e grava a exceção dela. Se o roteador dela também está marcado, ela já vai junto e não conta duas vezes.
- **D2:** com um grupo de máquinas inteiro marcado (todas as fornalhas de um roteador, ou o cabeçalho do bloco), Colar e Mover gravam o perfil do grupo, uma vez por roteador. As exceções que já existem ficam, e o aviso conta quantas.
- **D3:** os filtros por tipo e por rede valem por máquina na visão por máquinas. O índice ganha os papéis e as redes de cada grupo e de cada exceção (~12 B cada), calculados no aviso de mudança que já existe.
- **D4:** o seletor Roteadores · Máquinas é lembrado no servidor, no `TabletMenu.State`, com os blocos abertos em Máquinas.
- **D5:** na aba Redes, os grupos de redes viram seções de uma lista só. Clicar no cabeçalho de um grupo mostra o detalhe dele (o que a `GroupsTab` faz hoje). Uma rede em dois grupos aparece nas duas seções, com a dica.
- **D6:** a ordem das máquinas por bloco é montada sob demanda, numa lista compartilhada que só existe enquanto alguém está em Máquinas.

**Já decidido e mantido:**
- a linha do grupo ou do bloco não mostra soma de vazão (M7);
- a vazão só aparece quando a máquina move;
- 8 máquinas por grupo ou bloco antes de "Mostrar todas";
- o Mapa mostra só as máquinas do roteador escolhido;
- abas Lista · Fluxos · Mapa · Redes (a aba Problemas é da etapa 5);
- o Copiar guarda a frente da presa de origem (1 byte no `profile_source`), para colar numa máquina convertendo os lados pela frente dela (`RemoteSide`);
- o protocolo sobe de `"24"` para `"25"` uma vez.

## O que entrega

- **Visão por máquinas na Lista.** Um seletor "Roteadores · Máquinas" embaixo da busca. Em Máquinas, a base inteira aparece pelo tipo de bloco:
  - um cabeçalho por bloco ("Fornalha · 23", e "3 roteadores" à direita, apagado);
  - dentro dele, as máquinas, na ordem roteador e depois #N, com "via <roteador>" na vinculada e "presa" na presa;
  - até 8 por bloco e "Mostrar todas";
  - os blocos começam recolhidos quando a visão inteira passa de 256 linhas.
- **Selecionar marca roteadores e máquinas juntos.** A caixa aparece em toda linha: roteador, presa, grupo, máquina e cabeçalho de bloco. Um roteador, grupo ou bloco com só parte marcada mostra a caixa pela metade.
- **Colar nos marcados** atinge roteadores e máquinas e diz o que vai atingir ("2 roteadores, 3 máquinas"):
  - no roteador, cola na presa, como hoje;
  - na máquina, grava a exceção dela, com os lados convertidos pela frente da máquina;
  - no grupo inteiro, grava o perfil do grupo (D2).
- **Mover** leva os roteadores marcados com as máquinas deles (como hoje) e as máquinas marcadas pela regra da D1.
- **O aviso e o Desfazer** contam e desfazem roteadores, grupos e máquinas.
- **Abas Lista · Fluxos · Mapa · Redes.** A aba Grupos sai; os grupos de redes viram seções da aba Redes (D5).
- **O cabeçalho do Tablet** diz também quantas máquinas a base tem ("14 roteadores · 63 máquinas").
- **O que ficou da 3a:** os textos do `RangeList` vão para uma classe própria e a `TabletScreen` volta abaixo de 500 linhas. A segunda linha do nó e o cabeçalho de grupo montados quando a página chega (T7 da auditoria) já vieram num commit anterior deste ramo (`LineCache`).

## Custo (skill `wa-performance`)

O motor não muda. Base de referência: a da 3a, 12.800 máquinas em 200 roteadores de alcance.

| Parte | Servidor | Rede | Memória, save e quadro |
| --- | --- | --- | --- |
| Ordem por bloco (D6) | Uma lista de todas as máquinas, ordenada por bloco, roteador e #N, compartilhada por todos os Tablets. Refeita só quando o `dataVersion` do índice muda e alguém está com a Lista em Máquinas: ~1 ms com 12.800 máquinas, no máximo a cada 5 ticks. Por tick: zero | 0 | ~12 B por máquina (~150 KB com a base inteira), só enquanto alguém olha; solta duas amostras depois do último. Zero no save |
| Página de Máquinas | Uma passada pela lista com a consulta do jogador, só quando a consulta, os blocos abertos ou o índice mudam, no máximo a cada 5 ticks, como a página de hoje. Conta máquinas e roteadores por bloco na mesma passada | Só as linhas da página, até 256: cabeçalho ~4 B, máquina ~12 B, ~3 KB com a página cheia, uma vez. Depois, só o pacote pequeno do estado | Cliente: textos e linhas montados quando a página chega |
| Estado e vazão das linhas | O `TabletMachineSample` que já existe: só as máquinas à vista, uma vez por amostra (20 ticks) | 2 a 6 B por linha à vista por segundo, no pacote pequeno | Um `long[]` por linha à vista |
| Blocos abertos | Um conjunto por jogador no estado do Tablet (até 256 blocos). Cada clique refaz só a página dele | Um pacote pequeno por clique | Alguns bytes por jogador, só na memória |
| Papel por máquina (D3) | Um `int` de papéis e uma máscara de redes por grupo e por exceção, calculados no mesmo aviso de mudança que já atualiza o índice | 0 | ~12 B por grupo e por exceção (~29 KB a mais no arquivo com 200 roteadores, 2 grupos e 10 exceções cada) |
| Marcar máquinas | 0 até o clique | No clique: o roteador e as posições, ~8 B por máquina (1.024 marcadas: ~9 KB, uma vez) | Cliente: o conjunto marcado |
| Colar e Mover em máquinas | Só no clique, pelo limite por jogador que já existe (`TabletPlayers.takeBulk`). Um lote (`batchChanges`) por roteador: uma remontagem por roteador, nunca uma por máquina | Só o aviso | Uma exceção por máquina que ficou diferente do perfil (o mesmo de uma exceção feita à mão). O Desfazer guarda antes e depois por máquina e por grupo, só até fechar o menu |
| Frente no perfil copiado | Lida no Copiar | 0 | 1 byte no componente `profile_source` |
| Lembrar o seletor (D4) | 0 | Uma vez, na abertura | 1 byte e os conjuntos de blocos, por jogador, na memória |
| Abas e Redes com grupos | 0. O número da aba muda de sentido; o estado da aba só vive na memória do servidor, sem migração | 0 | Cliente: as seções montadas quando as redes ou os grupos mudam, não por quadro |
| "63 máquinas" no cabeçalho | Uma soma mantida no índice, atualizada no aviso. Zero na montagem | Um varint no snapshot | Um `int` |

**Descartado:** manter o agrupamento por bloco dentro do índice, sempre. Custaria trabalho em todo aviso de mudança de roteador, mesmo com todos os Tablets fechados, e 150 a 300 KB fixos na memória do servidor (D6).

## O papel por máquina no índice (servidor)

- **`IndexRemotes`** guarda, além do que já tem:
  - por grupo: os papéis do perfil (`int`, no formato de `NodeIndex.Entry.roles`, pelo `NodeIndex.role(tipo, modo)`) e a máscara das redes das faces do perfil (`long`);
  - por exceção: os mesmos dois valores, da configuração da exceção;
  - da presa: os mesmos dois valores, das faces da presa. Sem isso, a linha da presa na visão por máquinas não teria como passar ou não no filtro por máquina (o `roles` da entrada junta presa, perfis e exceções).
- **A máscara de redes:** o bit `k` é a `k`-ésima rede distinta do nó, na ordem de `LoadedTypes.LIST` e, dentro do tipo, na de `Entry.networks(type)`. Com mais de 63 redes distintas num nó, o bit 63 quer dizer "alguma das outras", e a máquina com ele passa em qualquer filtro de rede (melhor mostrar a mais do que esconder).
- **Calculados no aviso de mudança,** na mesma leitura que o `FaceSummary.scan` já faz da presa, dos perfis e das exceções, e comparados no `IndexRemotes.matches` para só montar outro quando algo mudou.
- **Sem o Upgrade de Alcance,** os papéis de grupos e exceções são guardados, mas a visão por máquinas os trata como zero, como o filtro por tipo da 3a: sem o upgrade, as vinculadas não movem.
- **NBT, chaves novas só quando há algo diferente de zero:**
  - `remote_group_roles` (`IntArrayTag`) e `remote_group_nets` (`LongArrayTag`), na ordem do `remote_groups`;
  - `remote_exception_roles` (`IntArrayTag`) e `remote_exception_nets` (`LongArrayTag`), na ordem do `remote_exception`;
  - `attached_roles` (`int`) e `attached_nets` (`long`).
  
  O formato da 3a, sem essas chaves, lê com tudo zero, e o índice se corrige no próximo aviso de mudança do roteador. Até lá, a máquina com papéis zero passa no filtro por tipo pelo `roles` do roteador (o comportamento da alternativa da D3), para não sumir da Lista.
- **A soma das máquinas:** o `NodeIndex` mantém quantas máquinas a base tem, atualizada só quando uma entrada entra, sai ou muda de `remotes().total()`. Conta as vinculadas (`total()`, inclusive as acima do teto) e uma presa por roteador.
- **`dataVersion`** sobe quando qualquer desses dados muda, como qualquer dado salvo.

## A visão por máquinas (servidor e protocolo)

- **O estado de cada Tablet aberto** (no `TabletMenu`, por uma parte de pacote nova):
  - a visão (Roteadores ou Máquinas);
  - os blocos abertos e os recolhidos à mão (`Set<ResourceLocation>` cada, até 256);
  - os blocos com "Mostrar todas".
  
  Um bloco que não está em nenhum dos dois conjuntos segue a regra padrão: aberto se a visão inteira cabe em 256 linhas, recolhido senão.
- **Payload novo cliente → servidor:** `TabletMachinesPayload(int containerId, Action action, Optional<ResourceLocation> block)`, com `Action { ROUTERS, MACHINES, OPEN_BLOCK, CLOSE_BLOCK, SHOW_ALL, SHOW_FEW }`. O servidor valida o tamanho dos conjuntos. Cada ação refaz só a página desse jogador.
- **A ordem por bloco (D6):** uma lista compartilhada, parte da amostra do Tablet (`TabletSample`), com todas as máquinas do índice: as vinculadas guardadas (até 512 por roteador, as de `IndexRemotes`) e a presa de cada roteador. Ordenada por bloco, depois roteador (a ordem da Lista de hoje, `TabletSample.sorted`), depois a presa e #N. Arrays paralelos, sem objeto por máquina. Refeita só quando o `dataVersion` muda e alguém está em Máquinas; solta duas amostras depois do último.
- **A página de Máquinas** é uma forma de página sem nó pai, ao lado das linhas da 3a:
  - um cabeçalho por bloco: o bloco (pela paleta de máquinas da página), quantas máquinas e de quantos roteadores, se está recolhido, se mostra todas e quantas ficaram acima do teto do índice (para a dica);
  - uma linha por máquina: o roteador (índice numa paleta de roteadores da página, com a chave e o nome), presa ou vinculada, o índice (#N), a posição, o rótulo e a marca de exceção;
  - "mais N" no fim de um bloco cortado.
  
  O teto de 256 (`TabletSnapshot.MAX_NODES`) conta essas linhas. A página diz quantas linhas e quantas máquinas a consulta acha no total, para o pé ("13 de 46 linhas", "5 blocos · 1.204 máquinas").
- **A consulta em Máquinas:**
  - o texto acha pelo rótulo, pelo nome do bloco e pelo nome do roteador;
  - as etiquetas de máquina filtram pelo bloco da máquina;
  - o tipo e as etiquetas de rede valem por máquina (D3), pela mesma regra do `TabletQuery` de hoje, trocando os papéis e as redes do nó pelos da máquina (presa, exceção ou o perfil do grupo dela);
  - os filtros rápidos (problema, sem rede, descarregado) valem pelo roteador, como hoje;
  - a visibilidade é a do roteador.
- **O estado e a vazão** das linhas de máquina vêm do `TabletMachineSample`, como na 3a, na ordem das linhas, pelo `TabletLivePayload`. A presa usa o estado e a vazão do nó.
- **O cabeçalho** (`TabletSnapshot`) ganha a soma das máquinas do índice.
- **Protocolo:** `ModPayloads.VERSION` passa de `"24"` para `"25"`.

## A visão por máquinas (cliente)

- **O seletor** "Roteadores · Máquinas" fica embaixo da busca. Os filtros rápidos, que só aparecem quando pedem atenção, ficam na mesma linha, à direita dele, e passam para a linha de baixo quando não cabem.
- **Cabeçalho do bloco:** ▸/▾, o ícone do bloco, o nome e quantas máquinas; à direita, apagado, de quantos roteadores. Sem soma de vazão (M7). O resto vai para a dica (quantas ficaram acima do teto do índice).
- **Linha da máquina:** a mesma da árvore da 3a. Na primeira linha, o nome (o rótulo, ou "#N"; a presa leva o nome do roteador). Na segunda, "via <roteador>" na vinculada e "presa" na presa, com as coordenadas ou o estado em palavras. À direita, a vazão quando move, a marca "exceção" e o Olho ◉.
- **"… mais N · Mostrar todas"** no fim de um bloco cortado.
- **Clicar** numa máquina escolhe o roteador dela e põe a máquina como alvo do painel, igual à visão por roteador; na presa, o alvo é a presa. O Mapa passa a mostrar as máquinas desse roteador.
- **O teclado:** ↑ ↓ andam nas linhas e ← → abrem e fecham o bloco.
- **O estado vazio** de Máquinas é o mesmo da Lista: o que fazer para ter a primeira máquina.
- Linhas e textos montados quando a página chega (o `LineCache` da T7); por quadro, só desenhar.

## Marcar, colar e mover em máquinas

- **O que se marca** (cliente): roteadores (`NodeKey`, como hoje), máquinas (o roteador e a posição da máquina, uma chave nova `MachineKey(NodeKey node, long pos)`), grupos de alcance inteiros (`TabletTree.GroupKey`, que já existe) e blocos inteiros na visão por máquinas (o `ResourceLocation`).
  - Marcar um grupo marca as máquinas dele; marcar um bloco, as máquinas do bloco.
  - O bloco marcado vai ao servidor como bloco, e o servidor reavalia a consulta atual do jogador, como o `PASTE_GROUP` de hoje, porque o cliente não conhece as máquinas de um bloco recolhido.
  - **Todos** marca o que a consulta mostra na visão aberta: roteadores em Roteadores, máquinas em Máquinas. Até 1.024 máquinas por clique (`TabletMenu.MAX_MOVE`); os nós seguem os tetos de hoje (256 no colar, 1.024 no mover).
- **Colar nos marcados** (`TabletProfilePayload`, ação `PASTE_SELECTED`, que passa a levar também as máquinas, os grupos e os blocos):
  - no roteador, cola na presa, como hoje (`PresetPaste.paste`);
  - na máquina, grava a exceção dela: as faces do perfil, convertidas dos lados relativos do roteador de origem (`RelativeSide`) para os lados da máquina (`RemoteSide`) pela frente da presa de origem guardada no Copiar e pela frente da máquina. Uma exceção que fica igual ao perfil do grupo some, como na edição de máquina de hoje;
  - num grupo inteiro marcado (todas as máquinas de um grupo do roteador, por grupo marcado, por bloco marcado ou uma a uma), grava o perfil do grupo, uma vez por roteador, e as exceções que já existem ficam (D2). O servidor decide "inteiro" pelo roteador carregado, não pelo índice;
  - a regra das redes é a de hoje (`PresetApplier.check`): a face cuja rede o jogador não pode usar fica com a rede que tinha;
  - a presa do roteador de origem fica de fora, como hoje. As máquinas vinculadas dele podem receber;
  - um roteador descarregado ou fora do alcance fica de fora, com as máquinas dele, e entra na contagem do que ficou de fora.
- **O Copiar** continua só de um roteador e guarda a frente da presa de origem no `profile_source`, no quadro do roteador de origem (um `RelativeSide`, ou "sem frente"). Um perfil copiado antes da 3b, sem a frente, cola na presa como hoje e é recusado em máquinas e grupos, com o aviso "copie o perfil de novo".
- **Mover** (`TabletMoveNodesPayload`, que passa a levar também as máquinas, os grupos e os blocos):
  - os roteadores marcados, como hoje (`setNetworkAllFaces`: presa, perfis e exceções);
  - a máquina marcada (D1): a rede muda só nas faces com modo daquela máquina, gravadas como exceção dela. A face sem modo não ganha rede, como no grupo novo da etapa 0;
  - o grupo inteiro marcado (D2): a rede muda nas faces com modo do perfil do grupo; as exceções que já existem ficam;
  - a máquina de um roteador também marcado já vai junto e não conta duas vezes.
- **Um lote por roteador:** todas as mudanças de um roteador num `batchChanges` só.
- **O limite por jogador** (`TabletPlayers.takeBulk`) vale por clique, como hoje.
- **O aviso** conta numa linha só o que aconteceu ("Colado em 3 grupos e 1 máquina · 2 exceções mantidas"), com o que ficou de fora (descarregado, fora do alcance, sem permissão na rede), como hoje.
- **O Desfazer** do colar guarda, por roteador, o antes e o depois da presa (`RouterPreset`), de cada perfil de grupo mudado e de cada exceção mudada (inclusive a que não existia), só até fechar o menu. Desfaz só o que ainda está como ficou.

## Abas e Redes

- **A ordem:** `TabletScreen.Tab { LIST, FLOWS, MAP, NETWORKS }`. `TabletMenu.TAB_COUNT` passa a 4 e `TabletMenu.FLOWS_TAB` a 1. O estado da aba só vive na memória do servidor: não há migração.
- **Redes com os grupos como seções (D5):**
  - uma lista só: cada grupo é uma seção, com ▾ que recolhe e Pausar no cabeçalho ("pausado" quando está); as redes sem grupo vêm por último, em "Sem grupo";
  - uma rede em dois grupos aparece nas duas seções, com a dica "também em <grupo>";
  - clicar numa rede mostra à direita o detalhe de hoje (cor, pública ou privada, usar, remover);
  - clicar no cabeçalho mostra o grupo à direita: nome, pausar, marcar as redes dele e remover, o que a `GroupsTab` faz hoje. "em <grupo>" aparece ao lado de uma rede que já está noutro grupo;
  - "+ Nova rede" e "+ Novo grupo" embaixo da lista;
  - as seções recolhidas ficam só no cliente, enquanto a tela vive.
- As ações são as de hoje (`TabletMenu.Action`, `GROUP_*` e `NETWORK_*`); nada muda no servidor além da ordem das abas.

## Lembrar ao reabrir

`TabletMenu.State` ganha a visão e os conjuntos de blocos de Máquinas, gravados ao fechar e restaurados ao abrir, e vão no buffer de abertura para o cliente desenhar certo no primeiro quadro. Ficam na memória do servidor, como o resto: limpos no logout e ao parar o servidor.

## Divisões antes de crescer (`wa-modular`)

- Os textos do `RangeList` (`groupName`, `number`, `machineName`, `blockName`, `distance`, `exceptions`, `stateColor`, `stateText`) vão para uma classe nova, `client/router/MachineTexts`. O `MapMachines` do Tablet passa a usá-la, e o `RangeList` volta a package-private se nada mais de fora do pacote o usar.
- A `TabletScreen` (574 linhas) passa uma parte para uma parte nova e fica abaixo de 500.
- Depois disso, `TabletMenu` (687), `TabletSnapshot` (579), `NodeIndex` (574), `TabletList` (466) e `ListLayout` (487) não crescem além de um repasse: o que é novo vai em classe própria.

As capturas do e2e têm de sair iguais na tarefa de divisão (`comparar.py`).

## Guia do jogador

`scripts/guide/gerar_guia.py`, nos dois idiomas, só "como operar": a visão por máquinas, marcar e colar em máquinas e grupos, o Mover com máquinas e a aba Redes com os grupos.

## Fica para depois

- Etapa 4: a busca por conteúdo (Item, Fluido, Tag, Mod), o micro dos Fluxos com "Quem abastece →" e o "Mostrar os N no mundo".
- Etapa 5: a aba Problemas, entre Fluxos e Mapa, com a contagem.
- Copiar o perfil de uma máquina (de uma exceção ou de um grupo) para colar noutra.
- Máquinas acima do teto do índice (512 por roteador): continuam fora das linhas, contadas na dica.

## Testes

- **GameTests:**
  - o índice: papéis e redes por grupo, por exceção e da presa, a ida e volta do NBT, o formato da 3a sem as chaves novas, a soma das máquinas;
  - a visão por máquinas: o `TabletMachinesPayload` (ações, limites, só a página daquele jogador), a ordem das linhas por bloco, roteador e #N, os blocos recolhidos acima de 256 linhas, o teto de 256, a busca pelo rótulo, pelo bloco e pelo nome do roteador, o filtro por tipo e por rede por máquina, o "live" de uma máquina movendo na visão por máquinas;
  - a lista compartilhada só existe com alguém em Máquinas e some duas amostras depois;
  - colar numa máquina (exceção com os lados convertidos pela frente, exceção igual ao perfil some), num grupo inteiro (perfil do grupo, exceções mantidas e contadas), no bloco marcado, o perfil antigo sem a frente recusado em máquinas, a origem de fora, o Desfazer com grupos e máquinas;
  - mover uma máquina (só as faces com modo, exceção gravada), um grupo inteiro, roteador e máquina dele marcados sem contar duas vezes, uma remontagem por roteador;
  - lembrar a visão e os blocos ao reabrir, e o logout limpa;
  - as abas: `TAB_COUNT`, `FLOWS_TAB` e o amostrador dos Fluxos só com a aba nova.
- **JUnit:** a conversão dos lados (`RelativeSide` com a frente de origem → `RemoteSide` com a frente da máquina), as seções da aba Redes, o modelo das linhas da visão por máquinas e a navegação, a ordem por bloco se for pura.
- **e2e:**
  - a Lista em Máquinas, com um bloco aberto, "Mostrar todas" e o clique numa máquina;
  - Selecionar com roteadores e máquinas marcados, Colar nos marcados e o aviso;
  - a aba Redes com os grupos como seções e o detalhe de um grupo;
  - tudo nos dois temas e em pt, sem texto cortado.
- **Bench:**
  - um cenário novo `tablet-machines`: a base inteira em Máquinas, com os blocos recolhidos e um aberto;
  - antes e depois: `tablet-range` (com um jogador falso em Máquinas no depois), `range:200:3` (com o tamanho do índice), `tablet:1000:3` e `many:500:3`.
