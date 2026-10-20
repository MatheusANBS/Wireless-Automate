# Plano da etapa 3b da central da rede: as máquinas na Lista e as abas novas

Spec: `docs/superpowers/specs/2026-10-20-central-etapa-3b-maquinas-e-abas-design.md`. Mockup aprovado: `docs/preview/central-etapa-3b.html`, com as telas da v6 em `docs/preview/central-da-rede.html`. Ramo: `central-etapa-3b`, a partir do `main` com a 3a (`c69380e`).

## Restrições globais

- **Idioma:** código, comentários, commits e docs em português; as traduções vão sempre nos dois arquivos, `en_us.json` e `pt_br.json`.
- **NBT e protocolo:**
  - as chaves existentes de NBT, componentes e config não mudam;
  - as únicas chaves novas no mundo são as do papel e das redes por grupo, por exceção e da presa no índice (`remote_group_roles`, `remote_group_nets`, `remote_exception_roles`, `remote_exception_nets`, `attached_roles`, `attached_nets`), gravadas só quando há algum valor diferente de zero;
  - o único campo novo de componente é a frente da presa de origem no `profile_source` (`ProfileSource`), gravado só quando existe (`optionalFieldOf`);
  - o formato da 3a lê sem elas: o índice com papéis e redes zero até o próximo aviso do roteador, o perfil sem a frente colando só na presa;
  - `ModPayloads.VERSION` sobe de `"24"` para `"25"`, uma vez, na Tarefa 4 (a que muda a página). As Tarefas 2 e 3 mudam o sentido do número da aba e o formato do snapshot dentro do mesmo ramo, que só vai ao `main` inteiro, com a 4.
- **Performance é requisito (skill `wa-performance`, leia o `SKILL.md`):**
  - o motor não muda: nada no laço, nada por tick além do que a amostra do Tablet já faz;
  - parado custa zero: com ninguém em Máquinas, a lista por bloco não existe e nada é montado;
  - o índice só lê papéis e redes no aviso de mudança do roteador, na mesma leitura do `FaceSummary.scan`;
  - a lista por bloco é compartilhada, em arrays paralelos, sem objeto por máquina, refeita só quando o `dataVersion` muda, e solta duas amostras depois do último olhar;
  - o servidor manda ao cliente só as linhas à vista e só quando mudam; com o Tablet fechado, nada;
  - a vazão por máquina só pelo `TabletMachineSample`, para as linhas à vista, uma vez por amostra;
  - Colar e Mover: um `batchChanges` por roteador, nunca uma remontagem por máquina; limite por jogador pelo `TabletPlayers.takeBulk`;
  - por quadro, só desenhar: as linhas, as seções e os textos são montados quando a página ou as redes mudam (`LineCache`), e o layout usa o `FrameCache`;
  - remota nunca é consultada sem `level.isLoaded(pos)`;
  - antes do commit, rode `python -I .claude/skills/wa-performance/scripts/varrer_perf.py` nos arquivos mudados, com cada candidato resolvido ou justificado no relatório.
- **Telas:**
  - telas só em `client/`, nunca referenciadas por código comum;
  - cores pelo `GuiPaint`;
  - texto variável pelo `GuiText` (o e2e confere `GuiText.clipCount()==0`);
  - tema claro e escuro;
  - não poluir a tela: só o essencial e o que pede atenção, o detalhe vai na dica; nada de soma de vazão no grupo nem no bloco (M7).
- **Tamanho (`wa-modular`):**
  - a Tarefa 1 tira os textos do `RangeList` e põe a `TabletScreen` (574) abaixo de 500;
  - depois dela, `TabletMenu` (687), `TabletSnapshot` (579), `NodeIndex` (574), `TabletScreen`, `TabletList` (466), `ListLayout` (487) e `IndexRemotes` (374) não crescem além de um repasse ou do que a tarefa diz: o que é novo vai em classe própria;
  - antes do commit, `python -I .claude/skills/wa-modular/scripts/medir.py` nos arquivos mudados.
- **Comentários (`wa-comentarios`):** `python -I .claude/skills/wa-comentarios/scripts/varrer.py` antes do commit.
- **GameTests:**
  - `@GameTestHolder(WirelessAutomate.MODID)`, `@PrefixGameTestTemplate(false)` e template `"empty"`;
  - cada teste cria a própria rede;
  - teste pertinência, não contagem global (o `NodeIndex`, a soma das máquinas, o `TabletSample` e a lista por bloco são globais);
  - jogador só por `TestPlayers`;
  - espere o `onLoad` com `thenWaitUntil`;
  - redes com `Objects.equals`;
  - `timeoutTicks` de no máximo 300.
- **Verificação:**
  - no fim da tarefa: `./gradlew build runGameTestServer runGameTestServerChemicals runGameTestServerSource runGameTestServerAllthemodium`;
  - nas tarefas de tela (1, 2, 5, 6 e 7), também o e2e, rodado sozinho, sem outro cliente aberto na mesma `run/`: `WA_E2E="$PWD/run/e2e" ./gradlew runClient --console=plain > run/e2e/client.log 2>&1`, com `run/e2e/result.txt` começando por `OK`. Olhe as capturas;
  - compile antes (`./gradlew compileJava`); com `BUILD FAILED`, pare e conserte, nunca espere o `result.txt`;
  - um Gradle ou jogo por vez;
  - não mexa em `run/options.txt`.
- **Commits:** um por tarefa, com o autor do dono, **sem nenhuma linha de atribuição** (`Co-Authored-By`, `Claude-Session` ou outra; regra do dono, o repositório é público), sem push.

## Mapa de arquivos

| Arquivo | Tarefa | Papel |
| --- | --- | --- |
| `client/router/MachineTexts.java` (nova), `client/router/RangeList.java`, `client/tablet/MapMachines.java`, `client/tablet/TabletScreen.java` e uma parte nova de `client/tablet/` | 1 | Textos das máquinas numa classe própria; `TabletScreen` abaixo de 500 |
| `client/tablet/TabletScreen.java` (`Tab`), `menu/TabletMenu.java` (`TAB_COUNT`, `FLOWS_TAB`), `client/tablet/NetworksTab.java`, `client/tablet/GroupsTab.java` (vira o detalhe do grupo dentro de Redes), `client/tablet/NetworkSections.java` (nova, pura com JUnit: as seções), `client/dev/ScreenshotTablet.java`, `client/dev/E2e*` | 2 | Abas Lista · Fluxos · Mapa · Redes; Redes com os grupos como seções |
| `network/IndexRemotes.java`, `network/FaceSummary.java`, `network/NodeIndex.java` (só o repasse e a soma) e uma parte nova se o `IndexRemotes` passar de 600 | 3 | Papéis e redes por grupo, exceção e presa; a soma das máquinas |
| `menu/MachineOrder.java` (nova: a lista compartilhada por bloco), `menu/TabletMachines.java` (nova: o estado da visão e a montagem da página Máquinas), `menu/TabletMachineRows.java` (nova: as linhas da página Máquinas e o codec), `packet/TabletMachinesPayload.java` (novo), `menu/TabletSample.java`, `menu/TabletSnapshot.java` (os campos e a soma no cabeçalho), `menu/TabletMenu.java` (`State` e repasses), `menu/TabletQuery.java` (a regra por máquina), `packet/ModPayloads.java`, `bench/` (cenário `tablet-machines`) | 4 | A visão por máquinas no servidor |
| `client/tablet/` partes novas: o seletor, o modelo das linhas por máquina (puro com JUnit, com a navegação), o desenho e os cliques; `client/tablet/ListSearch.java` e `ListLayout.java` só no que o seletor pede | 5 | A visão por máquinas no cliente |
| `preset/ProfileSource.java` (a frente), `preset/ProfileSides.java` (nova, pura com JUnit: a conversão dos lados), `menu/TabletProfile.java` e uma parte nova para o colar em máquinas e o Desfazer, `menu/TabletMenuActions.java` (o Mover) e uma parte nova se passar de 600, `packet/TabletProfilePayload.java`, `packet/TabletMoveNodesPayload.java`, `client/tablet/SelectBar.java`, `TabletList.java` (só o conjunto marcado, por repasse) e uma parte nova para a seleção | 6 | Marcar, colar e mover em máquinas |
| `docs/benchmark.md`, `scripts/guide/gerar_guia.py`, `docs/especificacao.md`, `CLAUDE.md`, `docs/progresso.md` | 7 | Bench, guia e docs |

Os nomes das classes novas são sugestões; o implementador pode trocar, dizendo no relatório, desde que cada uma tenha uma responsabilidade e fique dentro do alvo da `wa-modular`.

---

### Tarefa 1: divisões e o que ficou da 3a

**Nota:** a segunda linha do nó e o cabeçalho de grupo montados quando a página chega (T7 da auditoria, `client/tablet/LineCache`, `ListTexts`, `ListRows`, `GuiText`) já vieram num commit anterior deste ramo. Esta tarefa fica com o resto.

**Custo:** nenhum no servidor nem na rede; o jogo não muda. **Prova:** as capturas do e2e iguais pelo `comparar.py` e os GameTests verdes.

1. **`client/router/MachineTexts` (nova):** os textos de máquina e grupo que hoje estão no `RangeList` (`groupName`, `number`, `machineName`, `blockName`, `distance`, `exceptions`, `stateColor`, `stateText` e o `tr` deles). Mova, não reescreva.
   - O `MapMachines` do Tablet e as partes de `client/router/` passam a chamar o `MachineTexts`.
   - O `RangeList` volta a package-private se nada fora de `client/router/` o usar mais (confira com `grep -rn "RangeList" src`). Se algo ainda precisar dele, diga o quê no relatório.
   - O `MachineTexts` é público (o Tablet usa) e vai ser a fonte dos textos da visão por máquinas na Tarefa 5.
2. **`TabletScreen` abaixo de 500 linhas** (roteiro da `wa-modular`: medir, planejar as partes, extrair uma por vez). Tire um pedaço coeso para uma parte nova de `client/tablet/`, como o repasse por aba (refresh, botões de cada aba, remover), pensando que a Tarefa 2 vai mudar as abas. A anfitriã fica com tamanho, cabeçalho, abas e o repasse dos eventos.
3. **e2e:** rode antes de mudar (`run/e2e-antes`) e depois (`run/e2e`), e compare com `python -I .claude/skills/wa-modular/scripts/comparar.py run/e2e-antes run/e2e`. Só pode mudar o que anima (o mundo e as vazões).
4. **`CLAUDE.md`:** o `MachineTexts` e a parte nova no mapa do código.
5. **Commit:** `Modular: textos das máquinas no MachineTexts e a TabletScreen em partes`.

### Tarefa 2: abas Lista · Fluxos · Mapa · Redes

**Custo:** zero no servidor e na rede: só muda o sentido do número da aba, que vive só na memória do servidor (sem migração). No cliente, as seções da aba Redes são montadas quando as redes ou os grupos mudam, não por quadro. **Prova:** GameTests das abas e do amostrador, e2e.

1. **A ordem:** `TabletScreen.Tab { LIST, FLOWS, MAP, NETWORKS }`; `TabletMenu.TAB_COUNT = 4`, `TabletMenu.FLOWS_TAB = 1`, `LIST_TAB` continua 0. Confira tudo que usa `Tab.GROUPS`, `Tab.MAP`, `Tab.NETWORKS`, `FLOWS_TAB` e `TAB_COUNT` (`ScreenshotTablet`, `E2eTabletTreeSteps`, `TabletFlows`, GameTests dos Fluxos).
2. **Redes com os grupos como seções (D5):**
   - `NetworkSections` (nova, pura com JUnit): a partir das redes e dos grupos que o jogador vê, monta as seções na ordem (os grupos, depois "Sem grupo"), com uma rede em vários grupos em todas as seções e a lista dos outros grupos dela (para a dica "também em …" e a marca "em …"), e as seções recolhidas;
   - `NetworksTab` usa as seções na lista da esquerda, sobre a `SideList` (uma rolagem só). O cabeçalho da seção tem ▾/▸ e Pausar ("pausado" quando está);
   - clicar numa rede mostra o detalhe de hoje; clicar no cabeçalho mostra o detalhe do grupo, vindo da `GroupsTab` (nome com renomear, pausar, marcar as redes dele, com "em <grupo>" ao lado da rede que já está noutro, e remover de dois cliques). A `GroupsTab` deixa de ser aba: vira o detalhe do grupo (renomeie se ficar mais claro, como `GroupDetail`);
   - "+ Nova rede" e "+ Novo grupo" embaixo da lista, pela `NameFields`;
   - as ações são as de hoje (`TabletActions.groupPause`, `groupNetwork`, `create`, `rename`, `remove`).
3. **GameTests:** `setTab` recusa 4; o amostrador dos Fluxos entra e sai da lista de quem olha pela aba 1 (`TabletFlowsGameTests`).
4. **e2e:** as capturas das abas mudam (a barra de abas é outra); confira que nada corta. Capturas novas: `7o-tablet-redes-grupos` (uma rede escolhida, com uma rede em dois grupos), `7o-tablet-redes-grupos-escuro`, `7o-tablet-redes-grupos-pt` e `7p-tablet-redes-grupo` (o cabeçalho de um grupo escolhido). O `t8-tablet-grupos` do `ScreenshotTablet` passa a mostrar o detalhe do grupo em Redes.
5. **Commit:** `Tablet: abas Lista, Fluxos, Mapa e Redes, com os grupos de redes como seções de Redes`.

### Tarefa 3: o índice com o papel por máquina

**Custo:** ~12 B por grupo, por exceção e por presa no save (só as entradas que mudaram regravadas, pelo cache da R3), calculados no aviso de mudança que já existe; zero por tick. A soma das máquinas é mantida no mesmo aviso, sem passada pelo índice. **Prova:** GameTests; o tamanho do arquivo do índice no `range:200:3` da Tarefa 7.

1. **`IndexRemotes`** ganha, por grupo e por exceção, os papéis (`int`, formato do `NodeIndex.Entry.roles`, por `NodeIndex.role`) e a máscara de redes (`long`), e a entrada ganha os mesmos dois da presa, como na spec (bit 63 = "alguma das outras" acima de 63 redes). Em arrays paralelos, comparados no `matches` sem alocar.
2. **Calculados no `FaceSummary.scan`,** na mesma passada pela presa, perfis e exceções. A ordem dos bits segue `LoadedTypes.LIST` e `Entry.networks(type)`.
3. **NBT** com as chaves da spec, só com algum valor diferente de zero. O formato da 3a lê com tudo zero; a regra da consulta trata papéis zero como "pelo roteador" até o próximo aviso.
4. **A soma das máquinas** no `NodeIndex` (vinculadas por `remotes().total()` mais uma presa por roteador), mantida quando uma entrada entra, sai ou muda. API de leitura para a Tarefa 4, sem cópia: os papéis e a máscara da máquina `i`, do grupo `g` e da presa, e a soma.
5. **GameTests:**
   - papéis e redes de um grupo, de uma exceção e da presa, inclusive uma rede em duas abas;
   - ida e volta do NBT e o formato da 3a sem as chaves novas;
   - mudar o modo ou a rede de uma face do perfil atualiza a entrada e sobe a `dataVersion`;
   - vincular, desvincular e quebrar o roteador mudam a soma.
6. **Commit:** `Índice de nós: papéis e redes por grupo, exceção e presa, e a soma das máquinas`.

### Tarefa 4: a visão por máquinas no servidor

**Custo:** ~1 ms por mudança do índice com 12.800 máquinas, só com alguém em Máquinas e no máximo a cada 5 ticks; ~150 KB enquanto isso; zero com ninguém olhando. A página, ~3 KB cheia, uma vez, e depois só o pacote pequeno do estado. Cada clique nos blocos refaz só a página daquele jogador. **Prova:** GameTests e o cenário novo `tablet-machines`.

1. **`TabletMachinesPayload`** (novo) e o estado da visão numa parte nova de `menu/` (`TabletMachines`): a visão, os blocos abertos e recolhidos à mão e os com "Mostrar todas" (até 256 cada), a regra padrão (abertos se a visão inteira cabe em 256 linhas) e a validação. Cada ação refaz só a página daquele jogador.
2. **`MachineOrder`** (nova): a lista compartilhada por bloco (D6), dona do `TabletSample` como o `sorted`. Arrays paralelos (bloco, nó, índice ou presa), ordenados por bloco, roteador (a ordem de `TabletSample.sorted`) e #N. Existe só enquanto alguém está em Máquinas; refeita só quando o `dataVersion` muda; solta duas amostras depois do último.
3. **A página de Máquinas** (`TabletMachineRows`, nova): cabeçalho por bloco, máquina com o roteador pela paleta e "mais N", como na spec, numa passada pela `MachineOrder` com a consulta do jogador. O teto de 256 (`TabletSnapshot.MAX_NODES`) conta as linhas. A regra da consulta por máquina (texto pelo rótulo, bloco e nome do roteador; tipo e rede pelos papéis e redes da máquina) fica numa função do `TabletQuery` ou numa parte dele, sem crescer o `TabletSnapshot`.
4. **O "live"** das linhas de máquina pelo `TabletMachineSample` e pelo `TabletLivePayload`, na ordem das linhas; a presa pelo estado e a vazão do nó. O pacote pequeno sai sozinho quando só o estado ou a vazão mudam.
5. **O cabeçalho** (`TabletSnapshot`) leva a soma das máquinas.
6. **Lembrar (D4):** a visão e os conjuntos entram no `TabletMenu.State` e no buffer de abertura.
7. **`ModPayloads.VERSION = "25"`.** Atualize o `BenchScene.encodedSize`, se a página nova entrar na soma.
8. **Bench:** cenário `tablet-machines`, a mesma cena do `tablet-range`, com um jogador falso com a Lista em Máquinas, os blocos recolhidos e um aberto. Atualize a tabela de cenários do `docs/benchmark.md`.
9. **GameTests:**
   - as ações do payload, os limites e só a página daquele jogador;
   - a ordem por bloco, roteador e #N, com a presa;
   - os blocos recolhidos acima de 256 linhas e o teto de 256;
   - a busca pelo rótulo, pelo bloco e pelo nome do roteador;
   - o filtro por tipo e por rede por máquina (uma fornalha de um roteador que também move fluidos não aparece em "Só Fluidos");
   - o "live" de uma máquina movendo, com vazão;
   - a `MachineOrder` não existe sem ninguém em Máquinas e some duas amostras depois;
   - lembrar a visão e os blocos ao reabrir; o logout limpa.
10. **Commit:** `Tablet: a visão por máquinas no servidor, com a ordem por bloco sob demanda`.

### Tarefa 5: a visão por máquinas no cliente

**Custo:** nada por quadro além de desenhar: linhas e textos montados quando a página chega (`LineCache`), layout no `FrameCache`. Um pacote pequeno por clique no seletor ou num bloco. **Prova:** e2e e `varrer_perf.py`.

1. **O seletor** "Roteadores · Máquinas" embaixo da busca, com os filtros rápidos à direita (passam para a linha de baixo quando não cabem). Manda o `TabletMachinesPayload`.
2. **O modelo das linhas por máquina,** puro e com JUnit (cabeçalho de bloco, máquina, "mais N" e a navegação ↑ ↓ ← →), ao lado do `ListTree` da 3a, sem crescer `TabletList`, `ListLayout` nem `TabletScreen`.
3. **O desenho** como no mockup e na spec, pelos textos do `MachineTexts` e do `NodeText`: cabeçalho com ▸/▾, ícone, nome, contagem e "N roteadores" apagado; a máquina com "via <roteador>" ou "presa", a vazão quando move, "exceção" e o Olho ◉ (`MachineHighlights`). O detalhe vai na dica (acima do teto do índice).
4. **Cliques:** ▸/▾ e "Mostrar todas" mandam o `TabletMachinesPayload`; a máquina escolhe o roteador e a põe como alvo do painel pelo mesmo caminho do clique da 3a (`TreeNav.choose`); a presa põe a presa; o Olho acende no mundo. O Mapa mostra as máquinas do roteador escolhido, como na 3a.
5. **O estado vazio** de Máquinas é o da Lista.
6. **e2e** (num `E2eTabletMachinesSteps` novo):
   - a Lista em Máquinas com um bloco aberto e os outros recolhidos, "Mostrar todas" e o clique numa máquina, conferindo o alvo do painel;
   - fechar e reabrir o Tablet em Máquinas;
   - capturas `7q-tablet-maquinas`, `7q-tablet-maquinas-escuro`, `7q-tablet-maquinas-pt` e `7r-tablet-maquinas-recolhidas`;
   - conferir que nada corta.
7. **Commit:** `Tablet: a Lista por máquinas, com os blocos e o seletor`.

### Tarefa 6: marcar, colar e mover em máquinas

**Custo:** só no clique: um lote (`batchChanges`) por roteador, pelo limite por jogador que já existe; ~8 B por máquina marcada no pacote, uma vez. Uma exceção por máquina que ficou diferente do perfil; o Desfazer só na memória até fechar o menu. **Prova:** GameTests (inclusive uma remontagem por roteador) e e2e.

1. **A frente no Copiar:** `ProfileSource` ganha a frente da presa de origem no quadro do roteador de origem (um `RelativeSide` ou "sem frente", 1 byte), lida do `RouterBlockEntity.attachedFront` no `TabletProfile`. Opcional no codec: o perfil antigo lê sem ela.
2. **`ProfileSides`** (nova, pura com JUnit, nos índices como o `RemoteSide`): converte um lado relativo do roteador de origem (`RelativeSide`), com a frente da presa de origem, para o lado da máquina (`RemoteSide`). Sem frente, vale Norte, como no `RemoteSide`.
3. **As chaves marcadas** no cliente, numa parte nova da seleção: roteadores, máquinas (`MachineKey(NodeKey node, long pos)`, nova), grupos (`TabletTree.GroupKey`) e blocos. A caixa em toda linha, nas duas visões, com a meia caixa no roteador, grupo ou bloco com só parte marcada. **Todos** marca o que a consulta mostra na visão aberta, até 1.024 máquinas. A `SelectBar` diz o que o Colar vai atingir ("2 roteadores, 3 máquinas").
4. **Os payloads:** `TabletProfilePayload` (`PASTE_SELECTED`) e `TabletMoveNodesPayload` passam a levar também as máquinas (até `TabletMenu.MAX_MOVE`), os grupos e os blocos. Os tetos de nós ficam os de hoje.
5. **Colar** (numa parte nova de `menu/`, sem crescer o `TabletProfile` além do repasse): por roteador, num `batchChanges`: a presa como hoje; os grupos inteiros (decididos pelo roteador carregado) com o perfil do grupo, mantendo e contando as exceções (D2); as outras máquinas com a exceção convertida pela `ProfileSides`, que some se ficar igual ao perfil. Regra das redes pelo `PresetApplier.check`. A presa da origem fica de fora; roteador descarregado ou fora do alcance fica de fora com as máquinas. Perfil sem a frente: recusado em máquinas e grupos, com o aviso.
6. **Mover** (no `TabletMenuActions` ou numa parte nova): os roteadores como hoje; a máquina (D1) só nas faces com modo, gravada como exceção; o grupo inteiro (D2) no perfil, mantendo as exceções; a máquina de um roteador também marcado não conta duas vezes. Um `batchChanges` por roteador.
7. **O aviso** numa linha só ("Colado em 3 grupos e 1 máquina · 2 exceções mantidas", e o que ficou de fora), nos dois idiomas.
8. **O Desfazer** do colar com o antes e o depois da presa, de cada perfil e de cada exceção mudados, por roteador; desfaz só o que ainda está como ficou.
9. **GameTests:**
   - a conversão dos lados no jogo (uma máquina virada para leste recebe a face certa);
   - colar numa máquina, num grupo inteiro (perfil, exceções mantidas e contadas), num bloco marcado, com a origem de fora e com o perfil antigo recusado em máquinas;
   - a exceção igual ao perfil some;
   - mover uma máquina (só as faces com modo), um grupo inteiro, e roteador com máquina marcada sem contar duas vezes;
   - uma remontagem por roteador no colar e no mover;
   - o Desfazer com grupos e máquinas, e o que mudou depois não volta;
   - os tetos dos pacotes e o limite por jogador.
10. **e2e:** marcar 2 roteadores e 3 máquinas de outro, Colar nos marcados e o aviso com Desfazer; marcar o cabeçalho de um bloco em Máquinas e colar. Capturas `7s-tablet-marcar-maquinas`, `7s-tablet-marcar-maquinas-escuro`, `7s-tablet-marcar-maquinas-pt` e `7t-tablet-colado-grupos`.
11. **Commit:** `Tablet: marcar, colar e mover em máquinas e grupos, com o aviso e o Desfazer`.

### Tarefa 7: guia, e2e, bench e progresso

1. **Bench:** `./scripts/bench.sh tablet:1000:3,tablet-range:100:3,tablet-machines:100:3,range:200:3,many:500:3`, antes e depois, alternados. Antes: a base da 3a (`c69380e`, ou o commit da Tarefa 1), num worktree à parte; lá o `tablet-machines` não existe, e o "antes" dele é o `tablet-range` com o jogador em Roteadores. No depois, o `tablet-range` também roda com um jogador em Máquinas. Anote o tamanho do `wirelessautomate_nodes.dat` no `range` (o esperado é ~29 KB a mais com grupos e exceções). Registre a tabela no `docs/benchmark.md`. Se algo subir além do ruído, pare e avise.
2. **Guia,** pelo `gerar_guia.py`, nos dois idiomas: a visão por máquinas, marcar e colar em máquinas e grupos, o Mover com máquinas e a aba Redes com os grupos.
3. **e2e** completo, olhando as capturas nos dois temas e em pt.
4. **Docs:**
   - a especificação (Tablet, índice e abas);
   - o `CLAUDE.md` (classes novas, protocolo `25`, abas, contagens de testes);
   - o `progresso.md` (estado, próximo passo, que é a etapa 4, e o histórico).
5. **Revisão final do ramo,** verificação completa e commit `Docs: etapa 3b da central da rede (as máquinas na Lista e as abas novas)`.
