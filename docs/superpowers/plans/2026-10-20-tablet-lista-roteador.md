# Tablet: a Lista mostra o roteador · plano de implementação

> **Para agentes:** SUB-SKILL OBRIGATÓRIA: use superpowers:subagent-driven-development (recomendado) ou superpowers:executing-plans para executar este plano tarefa por tarefa. Os passos usam caixas (`- [ ]`) para acompanhar.

**Objetivo:** a linha do roteador na Lista do Tablet mostra o roteador (ícone, tier, nome ou "Roteador &lt;tier&gt;"), com a máquina presa na segunda linha; a máquina presa ganha nome próprio; roteador e presa se renomeiam na própria Lista.

**Arquitetura:** o nome da presa é um campo do `RouterBlockEntity` (NBT `attached_label`), aceito pelo `RenameTargetPayload` com `Kind.ATTACHED`, espelhado no `NodeIndex.Entry`, na página do Tablet (`TabletPageCodec`/`NodeView`), na visão Máquinas e no `RouterSnapshot`. No cliente, as linhas da Lista trocam de texto e ganham o ícone do roteador por tier; o editor de nome que já existe (`LabelEditor`/`RowRename`) passa a valer na Lista e para a presa.

**Stack:** Java 21, NeoForge 21.1 (Minecraft 1.21.1), GameTests, e2e.

**Spec:** [`docs/superpowers/specs/2026-10-20-tablet-lista-roteador-design.md`](../specs/2026-10-20-tablet-lista-roteador-design.md). Mockup aprovado: `docs/preview/tablet-lista-roteador.html`.

## Restrições globais

- Código, comentários, commits e docs em português; textos novos nos dois `lang` (`en_us` e `pt_br`).
- Chaves de NBT antigas não mudam. Novas: `attached_label` no roteador e no índice de nós, gravadas só se não vazias; a ausência lê vazio.
- `ModPayloads.VERSION` vai de `"25"` para `"26"` (a página do Tablet muda de formato). O renomear da presa usa o `RenameTargetPayload` que já existe; nenhum pacote novo.
- Nome da presa: mesmas regras do rótulo de uma máquina vinculada (`MachineNames`/`clean`, até 32 caracteres); vazio apaga. Continua quando a máquina presa é trocada.
- Sem nome, o roteador aparece como "Roteador &lt;nome do tier&gt;" (chave nova no `lang`); a presa sem rótulo aparece com o nome do bloco por `RouterBlock.machineName`, nunca `getName()`.
- Todo texto variável de tela passa por `GuiText` (o e2e confere `GuiText.clipCount()==0`).
- **Performance (`wa-performance`):** nada por tick; o índice só muda no aviso de mudança; a página só sai quando a estrutura muda; nada calculado por quadro além de desenhar (textos e ícones montados quando a página ou o tema mudam). Leia `.claude/skills/wa-performance/SKILL.md` e rode `python -I .claude/skills/wa-performance/scripts/varrer_perf.py` nos arquivos mudados.
- Tamanho (`wa-modular`): telas até 500 linhas; `python -I .claude/skills/wa-modular/scripts/medir.py`. Comentários: `python -I .claude/skills/wa-comentarios/scripts/varrer.py`.
- GameTests: `timeoutTicks` ≤ 300; jogador de teste só pelo `TestPlayers`; compare nomes com `Objects.equals`.
- Antes de cada commit: `./gradlew build runGameTestServer runGameTestServerChemicals runGameTestServerSource runGameTestServerAllthemodium`; nas tarefas de tela, também o e2e completo `WA_E2E="$PWD/run/e2e" ./gradlew runClient` (`run/e2e/result.txt` OK; no Windows, a falha conhecida "painel: mouse numa face do visor tempo esgotado" não é desta tarefa e deve ser dita no relatório) e **abrir as capturas** da Lista com a ferramenta Read. Compile antes; com `BUILD FAILED`, conserte. Nunca dois jogos ao mesmo tempo; não mexa em `run/options.txt`.
- Commits sem linhas de atribuição; `git add` só dos arquivos da tarefa (nunca `run/`, `.superpowers/`, `docs/curseforge/perfil-400.png`). Não faça push.

---

### Task 1: O nome da presa no servidor, no índice e na página

**Files:** `block/RouterBlockEntity.java` (campo, setter, NBT), `block/RouterRange.java` (ramo `ATTACHED` no `rename`), `packet/ModPayloads.java` (`handleRenameTarget` aceita `ATTACHED`; `VERSION` 26), `packet/RenameTargetPayload.java` (doc), `network/NodeIndex.java` (`Entry.attachedLabel`, NBT, busca), `menu/TabletPageCodec.java` e o `NodeView` (o campo na página), `menu/TabletMachines.java` e `menu/TabletMachineRows.java` (o rótulo da presa na visão Máquinas), `menu/RouterSnapshot.java`/`range/RangeView.java` (o rótulo da presa para o painel e a tela), GameTests.

- [ ] **Step 1:** `RouterBlockEntity`: campo `attachedLabel` (String, vazio por padrão), `attachedLabel()` e `setAttachedLabel(String)` com a limpeza do `MachineNames` e `nameChanged()` só quando muda; NBT `attached_label` só se não vazio.
- [ ] **Step 2:** `RouterRange.rename`/`RouterBlockEntity.renameTarget` aceitam `Kind.ATTACHED`; `ModPayloads.handleRenameTarget` deixa de recusar `ATTACHED` e, para ele, não exige `rangeActive()` nem posição.
- [ ] **Step 3:** `NodeIndex.Entry.attachedLabel` (NBT `attached_label`, só se não vazio), copiado do roteador no `track`, e no texto da busca junto do nome, do bloco e das coordenadas.
- [ ] **Step 4:** a página do Tablet leva o rótulo por nó (`TabletPageCodec`, `NodeView.attachedLabel()`); a visão Máquinas passa o rótulo da presa (hoje `""` em `TabletMachines` perto da linha 309); o `RouterSnapshot`/`RangeView` leva o rótulo da presa. `ModPayloads.VERSION` = `"26"`.
- [ ] **Step 5: GameTests:** `MachineLabelGameTests` (hoje afirma que a presa é recusada) passa a afirmar que a presa aceita nome, limpa e apaga com vazio, e que o nome continua depois de trocar o bloco preso; NBT do roteador e do índice (ida e volta, e o formato antigo sem a chave lê vazio); a busca do Tablet acha pelo nome da presa; o codec da página com e sem rótulo; o `RenameTargetPayload` com `ATTACHED` sem o Upgrade de Alcance.
- [ ] **Step 6: Verificar e commit:** build e as 4 runs; commit `Tablet: nome da máquina presa no roteador, no índice e na página`.

**Custo:** um texto por roteador e por nó do índice, só quando existe; nada por tick.

---

### Task 2: As linhas da Lista mostram o roteador

**Files:** `client/tablet/ListTexts.java`, `NodeText.java`, `ListRows.java`, `ListLayout.java`/`LineCache.java` (se a geometria mudar), `TreeTexts.java`, `TreeRows.java`, `MachineRowTexts.java`, `MachineRows.java`, um `RouterIcons` novo se couber (os `ItemStack` por tier, criados uma vez), `lang/en_us.json` e `pt_br.json`.

- [ ] **Step 1:** nome do roteador: o dado, senão "Roteador &lt;tier&gt;" (chave nova); vale na Lista, no Mapa, na dica e no "via" da visão Máquinas.
- [ ] **Step 2:** linha do roteador na visão Roteadores: ícone do roteador do tier, nome, pílula do tier na cor do tier e, na segunda linha, "presa: &lt;rótulo ou nome do bloco&gt;" antes das coordenadas e do estado. Tudo pelo `GuiText`, montado quando a página ou o tema mudam.
- [ ] **Step 3:** linha da presa na árvore: ícone do bloco, o rótulo ou o nome do bloco, e na segunda linha "presa · &lt;bloco&gt; · &lt;face&gt;".
- [ ] **Step 4:** visão Máquinas: a linha da presa usa o rótulo quando há.
- [ ] **Step 5: Verificar e commit:** build, as 4 runs, o e2e completo; abrir e descrever as capturas da Lista (`7l-tablet-arvore*`, `7o-tablet-maquinas*` e as da aba Lista); ajustar os passos do e2e que acham roteadores pelo texto. Commit `Tablet: a Lista mostra o roteador e a máquina presa`.

**Custo:** 8 `ItemStack` criados uma vez; textos e larguras no cache de linha que já existe.

---

### Task 3: Renomear na Lista e no painel

**Files:** `client/tablet/ListClicks.java`, `ListRows.java`/`TreeClicks.java`/`TreeRows.java` (o ✎ e os dois cliques), `client/router/LabelEditor.java`, `RowRename.java`, `PanelLayout.java`, `TargetLine.java`, `RangeList.java`, `MachineTexts.java`, `client/tablet/TabletActions.java` (o envio), `client/dev/E2e*` (passos e captura nova), `lang`.

- [ ] **Step 1:** na Lista, o ✎ ao passar o mouse e dois cliques no nome abrem o `LabelEditor` na linha do roteador (manda o `RenameRouterPayload` pelo `TabletActions`) e na linha da presa da árvore (manda o `RenameTargetPayload` com `ATTACHED`). Enter salva, Esc cancela; só com o painel editável (`PanelAccess.EDIT`).
- [ ] **Step 2:** painel do nó e tela do roteador: com a presa como alvo, a pílula "Editando" mostra o rótulo e o ✎ funciona (hoje só sem `attached()`); a lista do alcance permite renomear a presa com dois cliques (hoje excluída no `RowRename`).
- [ ] **Step 3: e2e:** passo novo que renomeia um roteador e a presa pela Lista e confere o texto; capturas `7s-tablet-renomear-roteador` e `7s-tablet-renomear-presa`.
- [ ] **Step 4: Verificar e commit:** build, as 4 runs, o e2e completo e as capturas novas abertas. Commit `Tablet: renomear o roteador e a presa na Lista`.

**Custo:** nada novo por quadro (o editor só existe enquanto edita).

---

## Fechamento

Revisão final do ramo no modelo mais capaz; `CLAUDE.md` (as linhas do `RouterBlockEntity`, do `NodeIndex`, do `client/tablet/` e do protocolo `26`) e `docs/progresso.md` (estado, próximo passo, histórico com os números de testes); o guia do jogador (`scripts/guide/gerar_guia.py`, nos dois idiomas) só se ele descreve a Lista ou o renomear.
