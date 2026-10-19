# Plano da etapa 2 da central da rede: identificar máquinas

Spec: `docs/superpowers/specs/2026-10-19-central-etapa-2-identificar-maquinas-design.md`. Mockup aprovado: `docs/preview/central-etapa-2.html`. Ramo: `central-etapa-2`.

## Restrições globais

- **Idioma e textos:**
  - código, comentários, mensagens de commit e docs em português;
  - traduções em `en_us.json` e `pt_br.json`, sempre as duas.
- **Chaves e protocolo:**
  - as chaves existentes de NBT, componentes e config não mudam;
  - a única chave nova no mundo é `label`, na máquina vinculada, gravada só quando não é vazia;
  - `ModPayloads.VERSION` sobe de `"22"` para `"23"`, só na Tarefa 1.
- **Performance é requisito (skill `wa-performance`, leia o `SKILL.md`):**
  - nada desta etapa entra no laço do motor;
  - renomear não chama `changed()`: sobe o `changeVersion`, faz o `setChanged()` e o `NodeIndex.track`;
  - nada é sincronizado com a tela fechada;
  - o destaque é só do cliente: sem nada aceso, o render volta na primeira linha; aceso, nada é alocado por quadro;
  - máquina remota nunca é consultada sem `level.isLoaded(pos)`;
  - o clique do Tablet consulta o índice e os 6 vizinhos, nunca varre o mundo;
  - antes do commit, `python -I .claude/skills/wa-performance/scripts/varrer_perf.py` nos arquivos mudados, com cada candidato resolvido ou justificado no relatório.
- **Telas:**
  - telas só em `client/`, nunca referenciadas por código comum;
  - cores pelo `GuiPaint` (o ciano do destaque é constante, igual nos dois temas, como o do osciloscópio);
  - texto variável pelo `GuiText` (o e2e confere `GuiText.clipCount()==0`);
  - tema claro e escuro;
  - regra do dono: não poluir a tela, o detalhe vai na dica.
- **Tamanho (`wa-modular`):** `RouterBlockEntity`, `RouterMenu`, `TabletScreen`, `RouterScreen` e `NodePanel` não crescem além do mínimo de ligação (um método de entrada ou um repasse). O que é novo vai em classe própria. O `RangeList` está em 500 linhas: o desenho da linha da máquina sai para uma parte nova antes de crescer. Antes do commit, `python -I .claude/skills/wa-modular/scripts/medir.py` nos arquivos mudados.
- **Comentários (`wa-comentarios`):** antes do commit, `python -I .claude/skills/wa-comentarios/scripts/varrer.py`.
- **GameTests:**
  - `@GameTestHolder(WirelessAutomate.MODID)`, `@PrefixGameTestTemplate(false)` e template `"empty"`;
  - cada teste cria a própria rede;
  - teste pertinência, não contagem global;
  - jogador só por `TestPlayers`;
  - espere o `onLoad` com `thenWaitUntil`;
  - compare redes com `Objects.equals`;
  - `timeoutTicks` de no máximo 300.
- **Verificação:**
  - durante a tarefa, rode só o que a mudança toca;
  - uma vez no fim da tarefa, `./gradlew build runGameTestServer runGameTestServerChemicals runGameTestServerSource runGameTestServerAllthemodium`;
  - nas tarefas de tela, também o e2e, olhando as capturas. No Windows não há Xvfb: `WA_E2E="$PWD/run/e2e" ./gradlew runClient --console=plain > run/e2e/client.log 2>&1`, e `run/e2e/result.txt` tem de começar com `OK`;
  - compile antes de rodar jogo (`./gradlew compileJava`); com `BUILD FAILED`, conserte em vez de esperar;
  - um Gradle ou jogo por vez;
  - não mexa em `run/options.txt`.
- **Commits:** um por tarefa, sem linhas de atribuição (nada de `Co-Authored-By`), sem push.

## Mapa de arquivos

| Arquivo | Tarefa | Papel |
| --- | --- | --- |
| `range/RemoteMachine.java`, `range/MachineGroup.java`, `block/RouterRange.java`, `block/RouterBlockEntity.java` (um método de entrada) | 1 | Rótulo e nome do grupo no servidor |
| `packet/RenameTargetPayload.java` (novo), `packet/ModPayloads.java` | 1 | O pacote e o handler |
| `menu/RangeView.java`, `menu/RouterSnapshot.java` | 1 | O rótulo no snapshot |
| `gametest/MachineLabelGameTests.java` (novo) | 1, 2 | GameTests |
| `range/RemoteIndex.java` (`routersAt`), `item/NetworkTabletItem.java` (`useOn`), `menu/TabletMenu.java` (um `open` com nó e alvo) e uma parte de pacote em `menu/` se precisar, `range/RangeLinks.java`, lang | 2 | Do mundo para a tela |
| `client/router/RangeList.java`, `client/router/MachineRow.java` (novo: o desenho da linha da máquina), `client/router/RangeRowButtons.java`, `client/router/MachineColumn.java`, `client/router/TargetLine.java`, `client/router/PanelLayout.java`, `client/router/NodePanel.java` (só o repasse), `client/router/LabelEditor.java` (novo: a caixa de texto do nome), lang | 3 | Nomes e edição nas telas |
| `client/highlight/MachineHighlights.java` (novo, puro) + JUnit, `client/highlight/HighlightRenderer.java` (novo), `client/highlight/HighlightRenderTypes.java` (novo), `client/highlight/DashedLine.java` (novo, puro) + JUnit, o Olho e o "No mundo" nas telas, o "Apagar todas" na tela do roteador e no Tablet | 4 | Destaque no mundo |
| `client/dev/E2eRangeSteps.java` e `E2eTabletSteps.java` | 3, 4 | Capturas |
| `docs/benchmark.md`, `scripts/guide/gerar_guia.py`, `docs/especificacao.md`, `CLAUDE.md`, `docs/progresso.md` | 5 | Bench e documentação |

---

### Tarefa 1: rótulo e nome do grupo no servidor

**Custo:** nada no motor nem por tick. Um pacote só ao salvar. No snapshot, +1 byte por máquina sem rótulo. **Prova:** GameTests e o bench da Tarefa 5.

1. **`RemoteMachine`:** campo `String label` (vazio por padrão), `label()` e `setLabel(String)`. No `save`, a chave `label` só quando não é vazia; no `load`, sem a chave, vazio.
2. **`MachineGroup.setName`:** limpa o texto como o `RouterBlockEntity.sanitizeName` (`StringUtil.filterText`, `strip`, corte em `RenameRouterPayload.MAX_LENGTH`). Se o `sanitizeName` for privado, extraia um método estático comum, por exemplo `RouterBlockEntity.cleanName(String)` ou um utilitário pequeno, sem duplicar a regra.
3. **`RouterRange`:** `boolean rename(EditTarget target, String name)`.
   - `GROUP`: o grupo pelo id; `MACHINE`: a máquina pelo índice.
   - Devolve se mudou. Ao mudar, sobe o `changeVersion`, faz `setChanged()` e o `NodeIndex.track(router)`, como o `setName` do roteador, **sem** `changed()`.
   - O `RouterBlockEntity` ganha só o método público de entrada que delega.
4. **`RenameTargetPayload`:** `(int containerId, EditTarget.Kind kind, int id, Optional<BlockPos> machinePos, String name)`, `stringUtf8(64)` no nome e registrado em `ModPayloads.register`. O handler:
   - resolve pelo `host(player, containerId)`;
   - recusa `ATTACHED`;
   - recusa nome com mais de `MAX_LENGTH` depois do `strip`;
   - numa `MACHINE`, recusa se a `machinePos` não bate com a máquina do índice (o mesmo `sameMachine` do `UnlinkMachinePayload`);
   - aplica pelo roteador e chama `host.editApplied()`.
   
   `VERSION = "23"`.
5. **`RangeView.MachineView`:** campo `String label`, com o `encode`/`decode` pelo `RouterSnapshot.NAME_LENGTH` e preenchido no `RouterSnapshot.captureRange`.
6. **GameTests (`MachineLabelGameTests`):**
   - ida e volta do NBT, inclusive a máquina sem a chave;
   - rótulo de máquina e nome de grupo pelo handler, com um jogador num `RouterMenu` aberto;
   - texto vazio tira o rótulo;
   - mais de 32 caracteres recusado;
   - `machinePos` errada recusada;
   - o snapshot seguinte leva o rótulo;
   - renomear não suja as rotas: use o contador de remontagens ou o diagnóstico do `NetworkManager` que os GameTests já usam.
7. **Commit:** `Alcance: rótulo da máquina vinculada e nome do grupo no servidor`.

### Tarefa 2: do mundo para a tela

**Custo:** só no clique, uma consulta O(1) ao índice e aos 6 vizinhos. Nada por tick. **Prova:** GameTests.

1. **`RemoteIndex.routersAt(Level, BlockPos)`:** os roteadores com aquela posição como remota ativa, numa lista nova só no clique. Documente que as fora do raio ou do limite não aparecem.
2. **Os roteadores presos numa máquina:** para cada `Direction d`, o bloco em `pos.relative(d)` é `RouterBlock` com `FACING == d` (confira com o `RouterBlock.attachedPos`). Ponha isso num método estático pequeno, perto do `RouterBlock` ou da parte nova.
3. **`TabletMenu`:** um `open(ServerPlayer, NodeKey, EditTargetRef)` que abre o Tablet na aba Lista com o nó escolhido e o alvo, pelo mesmo caminho do `restore`, sem crescer o menu além do método. Pode gravar o estado pelo `TabletPlayers.save` antes do `open`, se for o caminho mais curto.
4. **`NetworkTabletItem.useOn`:** só no servidor.
   - Junta os presos (primeiro) e os que vinculam, e fica com o primeiro que o jogador vê (`TabletNodes.visible`, com a mesma regra de operador).
   - Achou: abre com o nó e, se for remota, o alvo `MACHINE` daquela máquina (`EditTargetRef.resolve`), e devolve `SUCCESS`.
   - Não achou: se o bloco tem capability de item, fluido ou energia (`level.getCapability` uma vez, no clique), mostra na barra de ação `item.wirelessautomate.network_tablet.not_linked` ("This machine is not on any of your routers" / "Esta máquina não está em nenhum roteador seu"), e devolve `PASS`, para o Tablet abrir como hoje.
   - Clicar num roteador continua abrindo a tela dele: confira que o `RouterBlock.useItemOn` vem antes.
5. **`RangeLinks`:** as mensagens `linked` e `unlinked` ganham a máquina e o grupo: "%s (%s) vinculada a %s · %s/%s máquinas". O nome da máquina é o rótulo, ou "<bloco> #N" sem rótulo, pela mesma regra do cliente, montada no servidor. O nome do grupo é o dado, ou o do bloco. Ao desvincular, leia antes de tirar. Atualize os dois `lang` e qualquer GameTest que confira o texto.
6. **GameTests:**
   - Tablet numa remota: o estado do Tablet tem o nó e o alvo `MACHINE` da máquina;
   - Tablet numa presa: o nó, com o alvo da presa;
   - um jogador sem acesso: nada abre com o nó alheio;
   - a mensagem do Vinculador leva o rótulo e o grupo (os argumentos do `Component`).
7. **Commit:** `Tablet e Vinculador: clicar na máquina abre o nó dela, e o Vinculador diz a máquina e o grupo`.

### Tarefa 3: nomes e edição nas telas

**Custo:** nada por quadro além de desenhar. O texto da linha é montado quando o snapshot muda, e as medidas ficam no cache do quadro (`FrameCache`) que o painel já usa. **Prova:** e2e e `varrer_perf.py`.

1. **`RangeList.machineName`:** o rótulo, ou o "%s #%s" de hoje. Ele é a única fonte do nome: pílula, dica, lista do alvo e a linha.
2. **A linha da máquina:** passa para duas linhas, com a altura da linha do grupo.
   - Primeira, o nome.
   - Segunda, as coordenadas ("−118 65 231"), a distância e o estado em palavras quando não é o normal (as chaves de estado que já existem).
   - À direita, "exc." e o lugar do Olho (que a Tarefa 4 liga).
   - O desenho vai para a parte nova `client/router/MachineRow`, para o `RangeList` não crescer.
3. **O ✎:** um `Action` novo `RENAME` no `RangeRowButtons`, na linha escolhida de máquina ou de grupo. O duplo clique no nome da linha faz o mesmo.
   - No painel, onde a linha Editando aparece (Tablet, ou o roteador com o visor no lugar da lista), um `Kind.RENAME` ✎ ao lado da pílula, quando o alvo é grupo ou máquina e o painel é editável.
4. **`LabelEditor`:** uma caixa de texto (`EditBox`) que a tela anfitriã põe no lugar do nome, nos moldes do `RouterHeader.createRenameBox`/`finishRename`.
   - Máximo 32 caracteres, começa com o rótulo atual.
   - Enter salva, Esc cancela, clicar fora salva.
   - Manda `RenameTargetPayload` só se mudou.
   - Na prévia ou captura, muda só o snapshot local.
   - O `RouterScreen` e o `TabletScreen` só a criam e repassam teclas e cliques.
   - Se uma tela anfitriã já está no limite, a ligação vai numa parte nova.
5. **Lang:**
   - a dica do ✎ ("Rename" / "Dar nome");
   - a da caixa ("Enter saves · Esc cancels" / "Enter salva · Esc cancela");
   - a das coordenadas, se precisar de chave.
6. **e2e:**
   - um rótulo dado pela tela (o ✎ e Enter) aparece na lista e na pílula;
   - as capturas `range-7-rotulo` (lista com rótulos e coordenadas), `range-7b-editando` (a caixa aberta) e `7j-tablet-painel-rotulo`;
   - olhe as capturas e confira o `GuiText.clipCount()`.
7. **Commit:** `Telas: nome das máquinas e dos grupos, coordenadas e edição pelo ✎`.

### Tarefa 4: destaque no mundo

**Custo:** por quadro, sem nada aceso, uma conferência. Com N acesas, O(N) vértices sem alocar, com N ≤ 64. Nada na rede nem no save. **Prova:** JUnit, `varrer_perf.py` e o tempo de quadro com 0 e com 64 acesas, por `System.nanoTime` num build local (tire a medição antes do commit e anote os números).

1. **`client/highlight/MachineHighlights` (puro, sem classes de tela; posições em `long` e a dimensão como chave):**
   - `toggle(dim, machinePos, routerPos)` devolve se acendeu, apagou ou apagou a mais antiga;
   - `isLit(dim, pos)`, `count()`, `clear()` e a iteração sem alocar para o desenho;
   - limite de 64, a mais antiga sai;
   - JUnit para tudo isso.
2. **`client/highlight/DashedLine` (puro):** os segmentos de uma linha tracejada entre dois pontos, num array reaproveitado. JUnit.
3. **`HighlightRenderTypes`:** linhas e faces sem teste e sem gravação de profundidade (no modelo do `ChamberRenderTypes`).
4. **`HighlightRenderer`:** `@EventBusSubscriber(value = Dist.CLIENT)`, `RenderLevelStageEvent` no estágio depois dos translúcidos.
   - Volta na primeira linha sem nada aceso.
   - Só a dimensão atual e dentro de `options.getEffectiveRenderDistance() × 16` blocos.
   - Contorno ciano `#3FC9BE`, véu leve nas faces e a linha tracejada até o roteador.
   - Limpa o `MachineHighlights` no `ClientPlayerNetworkEvent.LoggingOut`.
5. **O Olho ◉:** na linha da máquina (`MachineRow`), aceso ciano e apagado cinza, que liga e desliga pelo `MachineHighlights`. O botão "◉ No mundo" fica na linha dos botões da máquina no painel (`PanelLayout.arrangeTargetButtons`, um `Kind.HIGHLIGHT`).
   - Ao acender a 65ª, a barra de ação diz "Limite de 64 máquinas acesas: a mais antiga foi apagada" (en: "64 machines lit at most: the oldest was turned off").
6. **Apagar todas:**
   - embaixo da lista das máquinas do roteador, "N máquinas acesas no mundo · Apagar todas", só com alguma acesa;
   - no pé da Lista do Tablet, ao lado do Selecionar, "Apagar todas (N)", só com alguma acesa (a parte da barra de baixo, sem crescer o `TabletScreen`).
7. **e2e:**
   - acender uma máquina pela tela, fechar a tela e capturar o mundo com ela acesa atrás de um bloco (`range-8-no-mundo`);
   - a lista com o Olho aceso;
   - o "Apagar todas" apaga;
   - olhe as capturas.
8. **Commit:** `Destaque no mundo: o Olho acende a máquina através dos blocos`.

### Tarefa 5: bench e documentação

1. **Bench:** `./scripts/bench.sh tablet:1000:3,many:500:3,range:200:3`. Compare com o relatório de antes (`run/e2-antes.log`, rodado no `main` antes da etapa) e registre a tabela no `docs/benchmark.md`. Se algo subir além do ruído, pare e avise.
2. **Guia** (`scripts/guide/gerar_guia.py`, nunca os `.md`), pt e en:
   - na página do Upgrade de Alcance, dar nome a uma máquina ou a um grupo, o Olho e o "Apagar todas";
   - na do Tablet, clicar numa máquina com o Tablet.
3. **Documentação:**
   - `docs/especificacao.md`: o alcance e o Tablet;
   - `CLAUDE.md`: classes novas, protocolo `23`, números de testes e o pacote `client/highlight/`;
   - `docs/progresso.md`: o estado, o próximo passo (etapa 3, a Lista com as máquinas) e a linha no histórico;
   - o backlog da `wa-performance`, se algum item foi tocado.
4. **Verificação e commit:** a verificação completa uma vez (as quatro runs e o e2e) e o commit `Docs: etapa 2 da central da rede (identificar máquinas)`.
