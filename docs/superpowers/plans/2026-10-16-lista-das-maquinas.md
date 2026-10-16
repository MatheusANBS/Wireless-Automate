# Lista das máquinas sem a linha Editando, e o Atualizar · plano de implementação

> **Para agentes:** SUB-SKILL OBRIGATÓRIA: use superpowers:subagent-driven-development (recomendado) ou superpowers:executing-plans para executar este plano tarefa por tarefa. Os passos usam caixas (`- [ ]`) para acompanhar.

**Objetivo:** com a lista das máquinas à vista, a linha Editando do painel some. Voltar ao perfil e Desvincular (este em dois cliques) passam para a linha da máquina escolhida na lista. Uma máquina trocada no lugar por outra máquina ganha o estado "trocada" e o botão Atualizar, que a leva para o grupo do bloco novo.

**Arquitetura:** o servidor ganha o estado `REPLACED` no snapshot, a operação de reagrupar no modelo (`RemoteMachines.regroup`) e no roteador (`RouterRange`), e o `UpdateMachinePayload`. Na tela, os botões pequenos da linha escolhida ficam numa parte nova (`RangeRowButtons`), para a `RangeList` não passar do tamanho. O armar do Desvincular é lógica pura (`ArmedClick`, com JUnit), usada pela lista e pela linha Editando.

**Stack:** Java 21, NeoForge 21.1 (Minecraft 1.21.1), ModDevGradle, JUnit 5, GameTests.

**Spec:** [`docs/superpowers/specs/2026-10-16-lista-das-maquinas-design.md`](../specs/2026-10-16-lista-das-maquinas-design.md) (decisões 1 a 8, protocolo e textos). Mockup aprovado: `docs/preview/roteador-sem-editando.html`.

## Restrições globais

- Código, comentários, commits e docs em português; textos nos dois `lang` (`en_us.json`, `pt_br.json`), sempre os dois, na mesma ordem, com os textos da tabela da spec.
- `ModPayloads.VERSION` sobe de `"17"` para `"18"` na Task 1 e fica `"18"`. **Chaves existentes de NBT, componentes e config não mudam**; nenhuma chave de NBT nova.
- Vincule, desvincule e reagrupe só pelos métodos do `RouterBlockEntity`, que mantêm o `RemoteIndex`, os caches (`RouterRemoteCaches`) e as rotas. Nenhuma remota é consultada sem `level.isLoaded(pos)` antes. O `RouterBlockEntity` já tem 990 linhas: só métodos de uma linha que delegam ao `RouterRange`.
- Performance: o estado `REPLACED` só é calculado no snapshot (com a tela aberta), e a consulta de capability só acontece quando o bloco já é diferente do grupo, por `level.getCapability` direto, sem criar `BlockCapabilityCache` nem listener (como o `RouterSnapshot` já faz para as faces da remota mostrada). O motor não muda.
- Tela: cores só pelos tokens do `GuiPaint` na hora de desenhar; todo texto variável pelo `GuiText`; `GuiText.clipCount() == 0` nas capturas. Classes de `client/` nunca referenciadas por código comum. Componente não manda payload direto: a lista devolve a ação a quem a usa (`MachineColumn` → `RouterScreen` → `RouterPanelActions`/menu), como a escolha de alvo faz hoje.
- Tamanho das classes (`wa-modular`): tela até 500 linhas (aviso 600, dividir acima de 800); servidor até 600. `python -I .claude/skills/wa-modular/scripts/medir.py <arquivos>` antes de cada commit. Comentários: `python -I .claude/skills/wa-comentarios/scripts/varrer.py` nos arquivos mudados, resolvendo os candidatos.
- GameTests: `@GameTestHolder(WirelessAutomate.MODID)`, `@PrefixGameTestTemplate(false)`, template `"empty"`; cada teste cria a própria rede; pertinência, não contagem global; nulos com `Objects.equals`; espere o `onLoad` com `startSequence().thenWaitUntil(...)`; jogador só pelo `TestPlayers`. Use o apoio do `RangeTestSupport`. Menus: construa direto e atribua a `player.containerMenu`, como os testes de `RangeScreenGameTests`.
- Antes de cada commit: `./gradlew build runGameTestServer runGameTestServerChemicals runGameTestServerSource runGameTestServerAllthemodium`. Nas Tasks 2 e 3, também o e2e: `WA_E2E="$PWD/run/e2e" ./gradlew runClient` (no Windows, sem `xvfb-run`; o `run/e2e/result.txt` deve dizer `OK`) e **abra as capturas `range-*` novas** (a ferramenta Read mostra imagens) e descreva o que viu. Compile antes (`./gradlew compileJava`); com `BUILD FAILED`, pare e conserte, nunca espere um resultado. Nunca dois jogos ao mesmo tempo; não mexa em `run/options.txt`.
- Commits sem linhas de atribuição (nada de `Co-Authored-By` ou `Claude-Session`); `git add` só dos arquivos da tarefa (nunca `.superpowers/`, nunca `run/`). Não faça push.

## Mapa de arquivos

Base `B/` = `src/main/java/io/github/matheusanbs/wirelessautomate/`, `T/` = `src/test/java/io/github/matheusanbs/wirelessautomate/`, `R/` = `src/main/resources/`.

| Arquivo | Papel |
| --- | --- |
| `B/menu/RangeView.java` (mudar) | `MachineState.REPLACED`; `MachineView.replacedBy` (`@Nullable ResourceLocation`) no buffer |
| `B/menu/RouterSnapshot.java` (mudar) | O estado `REPLACED` (decisão 4 da spec) e o `replacedBy` |
| `B/range/MachineGroup.java` (mudar) | `block` mutável (setter de pacote) e a cópia funda do perfil (`MachineConfig`, se ainda não houver) |
| `B/range/RemoteMachines.java` (mudar) | `regroup(Map<Integer, ResourceLocation>)`: as regras da decisão 6; devolve os cartões dos grupos que saíram e, para o alvo, o grupo de destino de cada máquina |
| `B/block/RouterRange.java` (mudar) | `updateReplaced(indices)`: confere de novo cada máquina no servidor, chama o `regroup`, relê a frente, solta os cartões, `pruneCaches`, `remotesChanged`, `changed(ALL_TYPES)` |
| `B/block/RouterBlockEntity.java` (mudar) | `updateReplaced(...)` e `replacedBy(RemoteMachine)` (o teste de "trocada" num lugar só, usado pelo snapshot e pelo `RouterRange`), delegando |
| `B/packet/UpdateMachinePayload.java` (novo), `B/packet/ModPayloads.java` (mudar) | O payload, o handler validado e o alvo depois (decisão 7); `VERSION = "18"` |
| `B/gametest/RangeReplaceGameTests.java` (novo) | GameTests do estado e do Atualizar |
| `B/client/ArmedClick.java` (novo, puro), `T/client/ArmedClickTest.java` | O clique armado: arma, confirma no prazo, desarma |
| `B/client/router/RangeRowButtons.java` (novo) | Os botões pequenos da linha escolhida (⟳, ↺, ✕ e o "Desvincular?"): posição, desenho, dica e clique |
| `B/client/router/RangeList.java` (mudar) | Usa o `RangeRowButtons` na linha escolhida; o estado "trocada" (cor, texto, linha do grupo); o clique num botão não escolhe a linha |
| `B/client/router/MachineColumn.java`, `RouterScreen.java` (mudar) | Repassam as ações da lista; o rótulo do visor para a trocada |
| `B/client/router/PanelLayout.java`, `TargetLine.java`, `NodePanel.java`, `PanelModel.java`, `PanelTooltips.java` (mudar) | Sem a linha Editando com a lista à vista; sem a lista, o Atualizar e o Desvincular em dois cliques |
| `R/assets/wirelessautomate/lang/en_us.json`, `pt_br.json` (mudar) | Os textos da spec |
| `B/client/dev/E2eRangeSteps.java` (mudar) | Os passos com a lista (botões na linha) e sem ela, o Desvincular em dois cliques, uma máquina trocada e o Atualizar |
| `CLAUDE.md`, `docs/progresso.md`, `docs/superpowers/specs/2026-10-11-roteador-de-alcance-design.md` (mudar) | Docs |

---

### Task 1: O servidor (estado trocada, reagrupar e o payload)

Modelo: padrão.

- [ ] `RangeView`: `MachineState.REPLACED` (javadoc: outro bloco com capability no lugar). `MachineView` ganha `@Nullable ResourceLocation replacedBy` no fim do record, escrito como opcional no buffer. Corrija todos os construtores.
- [ ] `RouterBlockEntity.replacedBy(RemoteMachine)` delega ao `RouterRange`: com o upgrade valendo e o chunk carregado, devolve o id do bloco na posição se ele é diferente do bloco do grupo, não é ar e expõe a capability de algum tipo carregado (`LoadedTypes`) no lado nulo ou num dos seis, por `level.getCapability` direto. Senão, `null`. Ache como cada `ResourceType` dá a capability de bloco (o `RouterSnapshot` já consulta as faces da remota mostrada sem cache: reuse esse caminho).
- [ ] `RouterSnapshot.state`: no ramo do bloco diferente, `REPLACED` se `replacedBy != null`, senão `MISSING`. O bloco igual sem block entity continua `MISSING`. Preencha o `replacedBy` do `MachineView`.
- [ ] `MachineGroup`: `block` deixa de ser `final`, com um setter de pacote. Se o `MachineConfig` não tiver cópia funda, crie uma (`copy()`), com JUnit se der para testar sem Minecraft, senão coberta pelo GameTest.
- [ ] `RemoteMachines.regroup(Map<Integer, ResourceLocation> moves)` (índice → bloco novo), com as regras da decisão 6 da spec, nesta ordem: (a) para cada grupo cujas máquinas estão todas no lote e vão todas para o mesmo bloco, sem grupo desse bloco, troque o `block` do grupo; (b) as outras entram no primeiro grupo do bloco novo, ou num grupo novo criado uma vez por bloco no lote, com a cópia do perfil do grupo antigo, nome vazio e sem cartões; (c) os grupos que esvaziaram saem e os cartões deles voltam. Devolva um resultado com os cartões e o grupo de destino de cada índice. A exceção da máquina não muda.
- [ ] `RouterRange.updateReplaced(List<Integer> indices)` e o delegado no `RouterBlockEntity`: filtra os índices que ainda estão trocados (`replacedBy`), chama o `regroup`, relê a frente de cada máquina movida (`storeFront`), solta os cartões no roteador (`dropAtRouter`), `pruneCaches`, `remotesChanged`, `router.changed(NetworkManager.ALL_TYPES)`. Devolve o resultado (vazio se nada mudou).
- [ ] `UpdateMachinePayload(containerId, EditTarget.Kind kind, int id, Optional<BlockPos> pos, Optional<ResourceLocation> block)`, no estilo do `SelectTargetPayload`: máquina pelo índice e a posição (conferidos como o `sameMachine`), grupo pelo id e o bloco do grupo. Handler `handleUpdateMachine` no `ModPayloads`, por qualquer `RouterEditHost`: para o grupo, os índices das máquinas trocadas dele. Depois, o alvo da decisão 7 (`host.selectTarget` com o grupo de destino quando o grupo escolhido saiu ou trocou de bloco; senão `host.validTarget()`), e `host.editApplied()`. `VERSION = "18"`.
- [ ] `RangeReplaceGameTests` (pelo `RangeTestSupport`; uma fornalha trocada por um alto-forno é "trocada", por pedra é "sumida"): o estado no snapshot e o `replacedBy`; atualizar uma máquina sozinha num grupo (o grupo troca de bloco, mantém id, nome, perfil e cartões); atualizar uma de duas (nasce o grupo novo com a cópia do perfil, o antigo fica); atualizar para um bloco que já tem grupo (entra nele, segue o perfil dele, a exceção continua, o grupo antigo que esvaziou sai e os cartões caem); atualizar o grupo inteiro; a frente relida; uma sumida não atualiza; o payload com a posição errada é recusado; o alvo depois (decisão 7); o motor move para a máquina atualizada na face certa. Nomes curtos e descritivos.
- [ ] Verificação completa e commit: "Roteador: estado trocada e o Atualizar no servidor".

### Task 2: A tela (lista com os botões, sem a linha Editando)

Modelo: padrão.

- [ ] `client/ArmedClick` (puro, sem Minecraft): `arm(nowMs)`, `armed(nowMs)` (até 3000 ms), `disarm()`, e `click(nowMs)`, que devolve `true` só no segundo clique dentro do prazo. JUnit em `T/client/ArmedClickTest.java`. Se o `NameFields` do Tablet puder usar a mesma classe sem mudar o comportamento, troque.
- [ ] `RangeRowButtons`: dado o retângulo da linha escolhida, a máquina (ou o grupo) e o estado, monta os botões de 10 × 10 px da direita para a esquerda, com 2 px entre eles e 3 px da borda: ✕ (máquina), ↺ (máquina com exceção, texto e borda `ACCENT`), ⟳ (máquina trocada, ou grupo com alguma trocada; texto e borda `OK`). O ✕ armado vira o botão de texto "Desvincular?" com fundo `DANGER` e texto claro, cortado pelo `GuiText` se precisar. Desenho com hover (`BUTTON_HOVER_BORDER`), a dica de cada um (textos da spec; o ↺ usa a dica que já existe do Voltar) e o clique, que devolve a ação (`UPDATE`, `REVERT`, `UNLINK`) ou nada. Desarma quando o mouse sai do ✕ ou o alvo muda.
- [ ] `RangeList`: na linha escolhida, o nome encolhe para dar lugar aos botões, e o "exc." some quando o ↺ aparece. A máquina trocada mostra "#n · trocada" e a bolinha em `BRASS`. A segunda linha do grupo com trocadas mostra "1 trocada / N trocadas" em `BRASS`, no lugar das exceções. `stateColor` e `stateText` ganham o `REPLACED`. O clique num botão não escolhe nem abre a linha. A dica da linha trocada diz o bloco novo pelo `RouterBlock.machineName`. Exponha o centro de cada botão para o e2e (`buttonCenter(Action)`).
- [ ] `MachineColumn` e `RouterScreen` repassam a ação para quem manda os payloads (`RevertMachinePayload`, `UnlinkMachinePayload`, `UpdateMachinePayload`), pelo mesmo caminho da escolha de alvo, e o rótulo do visor para a máquina trocada (como o da sumida, com o texto do estado).
- [ ] Painel: com `model.targetFromList()`, o `PanelLayout.arrangeTarget` não reserva a linha Editando nem os botões (`targetY = -1`, sem `Kind.TARGET`, `REVERT` ou `UNLINK`), e o `TargetLine` não desenha nada. Sem a lista, a linha continua e ganha o Atualizar (`Kind.UPDATE`, antes do Desvincular, só com a máquina trocada), e o Desvincular passa a pedir dois cliques pelo `ArmedClick` (o botão armado diz "Desvincular?" em `DANGER`). Mantenha os ganchos `revertCenter`/`unlinkCenter` e crie o `updateCenter`.
- [ ] Textos nos dois `lang`, pela tabela da spec. Medir e varrer comentários.
- [ ] Verificação completa, e2e (os passos `range-*` que clicavam na linha Editando com a lista à vista vão falhar: ajuste o `E2eRangeSteps` só o suficiente para passar, clicando nos botões da lista; a Task 3 completa) e commit: "Roteador: os botões na linha da máquina e sem a linha Editando com a lista".

### Task 3: e2e e docs

Modelo: padrão.

- [ ] `E2eRangeSteps`: com a lista à vista, conferir que a linha Editando não aparece (`panel().targetCenter() == null`); Voltar ao perfil pelo ↺ da linha; Desvincular com dois cliques (o primeiro não desvincula; captura `range-6-desvincular-armado`); sem a lista (visor), a linha Editando com o Desvincular armado; trocar no servidor o bloco de uma máquina vinculada por outro com capability (`E2eWorld`), conferir a linha "trocada" (captura `range-7-trocada`, com a dica do ⟳), clicar no ⟳ e conferir que ela voltou a carregada no grupo novo (captura `range-8-atualizada`). Em pt_br também, se a volta em português já passa pela tela de alcance.
- [ ] e2e completo `OK`; abra e descreva as capturas `range-*`.
- [ ] Docs: o mapa do código no `CLAUDE.md` (`client/router/RangeRowButtons`, `client/ArmedClick`, `packet/UpdateMachinePayload`, o protocolo `18`, o estado trocada); a seção de estados da spec do roteador de alcance (a trocada e o Atualizar); `docs/progresso.md` (a linha do Roteador de alcance na tabela e uma linha no histórico com os números de testes).
- [ ] Commit: "Roteador: e2e e docs da lista das máquinas".
