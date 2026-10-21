# Telas mais limpas: plano

> Execução: uma tarefa por vez, na ordem. Cada tarefa termina compilando, com os testes passando e com as capturas do e2e conferidas contra o desenho de referência.

**Objetivo:** aplicar o design aprovado a todas as telas, sem perder nenhuma função.

**Spec:** [`docs/superpowers/specs/2026-10-21-telas-limpas-design.md`](../specs/2026-10-21-telas-limpas-design.md). **Desenho de referência:** [`docs/preview/interfaces-limpas.html`](../../preview/interfaces-limpas.html) e as imagens em [`docs/preview/interfaces-limpas/`](../../preview/interfaces-limpas/).

## Restrições globais

- Código, comentários, commits e docs em português; textos novos em `en_us.json` e `pt_br.json`, sempre os dois.
- Commits com o autor do dono e **sem** linhas de co-autoria.
- Nenhuma função some. Se algo não couber, pare e pergunte; não corte.
- Chaves de NBT, componentes e config não mudam. O motor não muda.
- `ModPayloads.VERSION` sobe uma vez só, de `27` para `28`, na Tarefa 2.
- Cor só pelo `GuiPaint` (os dois temas); todo texto variável pelo `GuiText`; classe de tela só em `client/`.
- Performance (skill `wa-performance`): nada alocado no `render`, layout no `FrameCache`, nada novo sincronizado com a tela fechada. Rode `python -I .claude/skills/wa-performance/scripts/varrer_perf.py` antes de cada commit.
- Tamanho (skill `wa-modular`): tela até 500 linhas; rode `python -I .claude/skills/wa-modular/scripts/medir.py`. Comentários: `python -I .claude/skills/wa-comentarios/scripts/varrer.py`.
- Verificação antes de cada commit: `./gradlew build runGameTestServer runGameTestServerChemicals runGameTestServerSource runGameTestServerAllthemodium` e `./scripts/e2e.sh` (toda tarefa mexe em tela). Nunca dois jogos ao mesmo tempo.
- Decisões assumidas (o dono pode trocar antes de começar): letra do lado no botão do cubo; Limpar, Copiar e Colar no quadro das faces.

## Tarefas

### 1. O cubo do Mekanism no editor de faces

Arquivos: `client/router/FaceCube.java`, `PanelLayout.java`, `PanelPainter.java`, `PanelModel.java`, `PanelTooltips.java`, `NodePanel.java`, `RouterPanelActions.java`; `client/router/FaceNet.java` só se a geometria pura mudar (com JUnit).

- [ ] Botões de 30 × 22 no quadro afundado, com sprite, letra, bolinha, Olho, tracejado e seleção (spec, "Quadro das faces").
- [ ] Cliques: esquerdo avança, direito volta, Shift zera, todos escolhem a face; mandam o `SetFacePayload` que já existe. Só leitura não muda nada.
- [ ] Linha da face no lugar dos botões de modo e da frase; a frase vai para a dica do botão.
- [ ] Rede, Filtro (com os slots de cartão), Redstone (‹ › com o pó) e Prioridade (‹ ›, roda) em linhas com rótulo.
- [ ] Ajustar `RouterSlots` para os slots de cartão na linha do Filtro (só posição; os slots do menu não mudam).
- [ ] Teste: GameTest dos cliques no menu (o modo avança, volta e zera pelo payload) em `RouterEditHostGameTests`.

### 2. Limpar, Copiar e Colar

Arquivos: `packet/FaceActionPayload.java` (novo), `packet/ModPayloads.java`, `block/RouterTargets.java` ou `RouterFaces.java`, `client/router/` (botões).

- [ ] `FaceActionPayload(containerId, action, type, from, to, allTypes)` com `CLEAR`, `PASTE`; Copiar é só do cliente (guarda a face e o tipo).
- [ ] No servidor: valida pelo `RouterEditHost`, aplica com `batchChanges` no alvo de edição (presa, grupo ou exceção). Colar copia modo, prioridade, redstone, filtro e rede (`FaceConfig.copyFrom`).
- [ ] Limpar em dois cliques (`ArmedClick`); com Shift, todos os tipos.
- [ ] `VERSION` 28. GameTests: limpar um tipo e todos, colar numa face, colar num grupo, payload recusado de longe.

### 3. Tela do roteador

Arquivos: `client/router/RouterLayout.java`, `RouterScreen.java`, `MachineColumn.java`, `RouterSlots.java`.

- [ ] Visor maior no lugar do rótulo dos cartões; inventário embaixo do visor; editor de faces à direita (`roteador.png`).
- [ ] Larga e estreita continuam funcionando; o tamanho mínimo não muda.
- [ ] e2e: capturas do roteador nos dois temas e em pt, sem texto cortado.

### 4. Alcance

Arquivos: `client/router/RangeList.java`, `MachineRow.java`, `MachineColumn.java`, `TargetLine.java`, `MachineTexts.java`.

- [ ] Visor menor no alto, "Máquinas · N/M" e a lista embaixo (`alcance.png`).
- [ ] Uma linha por máquina (estado, #N, "· exceção", distância, Olho; coordenadas na dica); grupo com ícone e "N trocadas".
- [ ] Barra Editando com a pílula do alvo e Igualar na exceção (`alcance-maquina.png`); "difere do grupo" na linha da face.
- [ ] Botões da linha escolhida como hoje. e2e: as capturas do alcance e da máquina trocada.

### 5. Tablet

Arquivos: `client/tablet/ViewBar.java`, `ListRows.java`, `ListPainter.java`, `ChosenPanel.java`, `MachineLines.java`; novo `client/tablet/GridRows.java` (desenho e clique da Grade) e, se precisar, lógica pura com JUnit para as colunas.

- [ ] Seletor Roteadores · Máquinas · Grade e o botão "! N" (`tablet-lista.png`).
- [ ] Painel do nó com o editor da Tarefa 1; "No mundo" com o Olho.
- [ ] Grade pelo `faceModes` da página (`tablet-grade.png`): tarjas por face, estado, rede, legenda; clique abre o nó na aba do tipo. A visão escolhida entra no estado lembrado do Tablet só no cliente, se não houver campo no `State`.
- [ ] e2e: `7*-tablet-*` atualizadas e uma captura nova da Grade.

### 6. Filtro

Arquivos: `client/filter/FilterHeader.java`, `FilterLayout.java`, `FilterFrame.java`, `EntryList.java`, `RuleTab.java`, `EntryTab.java`.

- [ ] Linha do alto com Lista branca · Lista negra e Componentes; abas sobre o quadro do conteúdo (`filtro-*.png`).
- [ ] Tipo da entrada em `DISABLED` no item exato; "—" da regra apagado.
- [ ] Conferir a lista de funções da spec, uma por uma, no e2e (as capturas de filtro de hoje cobrem todas as abas).

### 7. Vinculador

Arquivos: `client/linker/LinkerScreen.java`, `LinkerLayout.java`, `LinkerPaint.java`, `NetworkColumn.java`.

- [ ] Modo no cabeçalho; quadro Vincula com os tipos e o cubo das faces (`vinculador.png`), pela `LinkerFaces` e pela `LinkerTabs`.
- [ ] e2e: capturas do Vinculador nos três modos.

### 8. Fechamento

- [ ] Guia: os gestos do cubo em `scripts/guide/gerar_guia.py`, nos dois idiomas.
- [ ] `CLAUDE.md` (mapa do código: `FaceActionPayload`, `GridRows`, protocolo 28), `docs/especificacao.md` (telas) e `docs/progresso.md`.
- [ ] Capturas do CurseForge novas depois, pela skill `wa-curseforge-imagens`.
