# Vinculador e Configurador novos: plano

> **Para agentes:** use `superpowers:subagent-driven-development` (um implementador por tarefa, revisão entre elas). Os passos usam caixas `- [ ]`.

**Meta:** o Vinculador vira "Vincular máquinas" + "Abrir máquina", o Configurador fica só com o Pincel (roda com um anel), e o código dos modos que saem é apagado.

**Arquitetura:** o Configurador é simplificado primeiro (ele deixa de depender do `LinkerMode` e do `LinkerArea`); depois o servidor do Vinculador (modo, vínculo de máquinas para todos os tipos, Abrir com a lista curta); depois a tela; por fim textos, e2e, guia e docs. A spec é `docs/superpowers/specs/2026-10-22-vinculador-configurador-novos-design.md`: leia antes de qualquer tarefa.

**Stack:** NeoForge 1.21.1, Java 21, JUnit e GameTests.

## Restrições globais (cada tarefa herda)

- Conversa, código, comentários, commits e docs em **português**. Todo texto novo nos dois `lang` (`en_us.json` e `pt_br.json`); chave que sai dos dois.
- **Código morto é apagado**, não deixado ao lado do novo. Depois de cada remoção, `grep -r` pelo nome tirado em `src/` (inclusive `gametest/`, `client/dev/` e `src/test/`) tem de dar zero.
- **Componentes de item não somem do registro** (perderia o item): `linker_tabs`, `linker_faces`, `linker_type`, `configurator_mode`, `configurator_any_machine` e `configurator_area` ficam registrados como legado com `Codec.PASSTHROUGH`, sem stream codec e sem nenhum leitor. `linker_mode` fica, com codec que aceita `single`, `area`, `machines` (viram `LINK`), `link` e `open`.
- `ModPayloads.VERSION` de `"29"` para `"30"` (uma vez, na tarefa 2).
- **Mod opcional**: tipos do Mekanism e do Ars só em `compat/`; nada novo em assinatura de classe `@EventBusSubscriber` ou `@GameTestHolder`. Classes de `client/` nunca referenciadas por código comum.
- **Performance (skill `wa-performance`)**: nada por tick, nada alocado em caminho quente (não há caminho quente aqui); o Abrir faz uma consulta ao índice por clique e não guarda estado no servidor; a lista é limitada a 16. Antes do commit: `python -I .claude/skills/wa-performance/scripts/varrer_perf.py` e resolver ou justificar cada candidato.
- **Texto de tela** passa por `GuiText` (o e2e confere `GuiText.clipCount()==0`).
- **Tamanho (skill `wa-modular`)**: `python -I .claude/skills/wa-modular/scripts/medir.py` nos arquivos mudados; tela até 500 linhas. O `LinkerScreen` (553 hoje) tem de sair abaixo de 500.
- **Comentários (skill `wa-comentarios`)**: `python -I .claude/skills/wa-comentarios/scripts/varrer.py` nos arquivos mudados antes do commit; nada de "antes era".
- Teste de GameTest: classe com `@GameTestHolder(WirelessAutomate.MODID)` e `@PrefixGameTestTemplate(false)`, template `"empty"`, cada teste cria a própria rede, `timeoutTicks` até 300, jogador só por `TestPlayers.inLevel`.
- Compile antes de rodar jogo (`./gradlew compileJava`); se aparecer `BUILD FAILED`, pare e conserte.
- Verificação de cada tarefa: `./gradlew build runGameTestServer`. Na tarefa 5 também `runGameTestServerChemicals runGameTestServerSource runGameTestServerAllthemodium` e o e2e.
- Commits terminam com a linha `Co-Authored-By: Claude Sonnet 5.5 <noreply@anthropic.com>`. Só commitar, nunca dar push.

---

## Tarefa 1: Configurador só com o Pincel

**Custo:** só remove código; nada novo por tick, por visita ou por quadro.

**Arquivos:**
- Modificar: `item/ConfiguratorItem.java`, `client/ConfiguratorWheelScreen.java`, `client/ConfiguratorWheelKeys.java`, `client/WheelLayout.java` (e `src/test/.../WheelLayoutTest.java` se existir), `packet/ConfiguratorWheelPayload.java`, `packet/ModPayloads.java` (`handleConfiguratorWheel`), `registry/ModDataComponents.java`, `client/AreaRenderer.java` (só se ainda referenciar a área do Configurador), `client/dev/ScreenshotWheel.java`, `client/dev/E2eConfiguratorSteps.java`.
- Apagar: `preset/ConfiguratorArea.java`, `preset/PasteMode.java`.
- Testes a reescrever: `gametest/ConfiguratorGameTests.java` (some tudo de Área e "qualquer máquina"; ficam copiar, colar, limpar, o seletor de tipo, a regra das redes do `PresetApplier` e o `canLeave`), `gametest/TabletClickGameTests.java` e `gametest/FaceNetworkGameTests.java` se citarem o que saiu.

**Interfaces:**
- Produz `ConfiguratorItem.type(ItemStack)`, `setType`, `cycleType` (já existem) e `ConfiguratorItem.clear(ItemStack)` agora só apaga cópia e máquina da cópia.
- Produz `ConfiguratorWheelPayload(Optional<ResourceType> pasteType)`.
- `PresetPaste.copy`, `PresetPaste.paste` e `PresetPaste.canLeave` não mudam. `PresetPaste.copy` deixa de usar `ConfiguratorArea.machine`: o bloco da máquina copiada passa a ser lido por um método estático novo `ConfiguratorItem.machineOf(ServerLevel, RouterBlockEntity)` (corpo igual ao `ConfiguratorArea.machine` de hoje, copiado antes de apagar a classe).

- [ ] **Passo 1: escrever o teste que falha.** Em `ConfiguratorGameTests`, um teste `pincelCopiaECola` que copia um roteador com uma face configurada, cola em outro e confere a face; e `modoAreaNaoExiste` não é necessário (o enum some). Rodar `./gradlew runGameTestServer` e ver o novo teste ainda passando antes de remover (ele cobre o que fica).
- [ ] **Passo 2: simplificar o item.** Em `ConfiguratorItem`: apagar `mode`, `setMode`, `pasteMode`, `setPasteMode`, `modeName`, `area`, `setArea`, `onItemUseFirst`, os ramos de Área do `use` e do `appendHoverText`, e os imports de `LinkerArea`, `LinkerMode`, `ConfiguratorArea`, `PasteMode`. O `use` passa a ser só: `return InteractionResultHolder.pass(stack)` (o Shift + clique no ar não troca mais de modo). `clear` apaga `PRESET` e `CONFIGURATOR_MACHINE`. O tooltip fica com: o que está copiado, o tipo colado e as linhas copiar, colar, limpar, roda e tipo. A javadoc da classe é reescrita para o Pincel.
- [ ] **Passo 3: apagar `ConfiguratorArea` e `PasteMode`.** Mover antes o método `machine` para `ConfiguratorItem.machineOf`. Atualizar `copy` do item para usá-lo. Em `ModDataComponents`, registrar `CONFIGURATOR_MODE`, `CONFIGURATOR_ANY_MACHINE` e `CONFIGURATOR_AREA` como legado (`Codec.PASSTHROUGH`), com o comentário `// Legado: só registrado para ler itens antigos; nada os usa.`
- [ ] **Passo 4: roda com um anel.** `ConfiguratorWheelScreen`: um anel só (Tudo + os tipos carregados), o centro mostra o nome do tipo e a dica; sai o anel de modos. `WheelLayout` perde a geometria do segundo anel (e seus testes), mantendo a do anel único com os mesmos nomes que acompanham a curva. `ConfiguratorWheelPayload` e `ModPayloads.handleConfiguratorWheel` passam a levar só o tipo; o servidor confere o Configurador na mão principal e que o tipo está carregado.
- [ ] **Passo 5: textos.** Apagar dos dois `lang` as chaves `item.wirelessautomate.configurator.mode.*`, `tooltip.mode`, `tooltip.mode_area`, `tooltip.mark`, `tooltip.paste_area`, `tooltip.paste_area_any`, `tooltip.next_mode`, `area.*`, `corner*`, `problem.*` do Configurador, `other_machine`, `protected`, `unloaded` e `networks_dropped` que só a área usava (conferir cada uma com `grep` antes de apagar). Atualizar a dica da roda para falar só do tipo colado.
- [ ] **Passo 6: apagar o que ficou sem uso** e rodar `grep -rE "ConfiguratorArea|PasteMode|CONFIGURATOR_MODE|CONFIGURATOR_ANY_MACHINE|CONFIGURATOR_AREA" src/` (só os três registros legados podem aparecer, em `ModDataComponents`). Ajustar `ScreenshotWheel` e `E2eConfiguratorSteps` (some o passo da área; fica o da roda de um anel).
- [ ] **Passo 7: verificar.** `./gradlew build runGameTestServer`. Esperado: passa, sem referência ao que saiu.
- [ ] **Passo 8: varreduras e commit.** `varrer_perf.py`, `varrer.py` (comentários) e `medir.py` nos arquivos mudados. `git add -A` e `git commit -m "Configurador: só o Pincel, roda com um anel e sem a área"`.

---

## Tarefa 2: Vinculador no servidor (Vincular e Abrir)

**Custo:** nada por tick. O Abrir faz uma consulta a `RemoteIndex.routersAt` e aos seis vizinhos por clique, sem estado no servidor; a lista tem no máximo 16 linhas.

**Arquivos:**
- Modificar: `linker/LinkerMode.java`, `linker/LinkerActions.java`, `item/LinkerItem.java`, `range/RangeLinks.java`, `menu/LinkerMenu.java`, `menu/LinkerSnapshot.java`, `packet/LinkerActionPayload.java`, `packet/LinkerSnapshotPayload.java` (se o snapshot encolher ou sair), `packet/ModPayloads.java`, `registry/ModDataComponents.java`.
- Criar: `linker/LinkerOpen.java`, `packet/LinkerOpenListPayload.java`, `packet/LinkerOpenPickPayload.java`, `src/test/java/.../linker/LinkerModeTest.java`.
- Apagar: `linker/LinkerTabs.java`, `linker/LinkerFaces.java`, `linker/LinkerScan.java` (e os testes JUnit deles).
- Testes: reescrever `gametest/LinkerAreaGameTests.java` (só a área de máquinas; a de roteadores sai), `gametest/RangeLinkerGameTests.java`, `gametest/NetworkGameTests.java`, `gametest/TypeNetworkGameTests.java`, `gametest/FaceNetworkGameTests.java`, `gametest/ChemicalGameTests.java`, `gametest/RangeFixGameTests.java`, `gametest/RangeNetworkGameTests.java`, `gametest/RangeTestSupport.java`: apagar o que testava rede, abas e faces do Vinculador, trocar o que só usava o `LinkerActions.apply` para o caminho que a regra nova usa. Criar `gametest/LinkerOpenGameTests.java`.

**Interfaces:**

```java
// linker/LinkerMode.java
public enum LinkerMode implements StringRepresentable {
    /** Clique num roteador o escolhe; num bloco vincula ou desvincula a máquina; Shift + clique marca a área. */
    LINK,
    /** Clique numa máquina abre a tela do roteador que chega nela. */
    OPEN;

    /** Aceita os nomes antigos: single, area e machines viram LINK. */
    public static final Codec<LinkerMode> CODEC = Codec.STRING.xmap(LinkerMode::byName, LinkerMode::getSerializedName);
    public static final StreamCodec<RegistryFriendlyByteBuf, LinkerMode> STREAM_CODEC =
            NeoForgeStreamCodecs.enumCodec(LinkerMode.class);

    static LinkerMode byName(String name) { return "open".equals(name) ? OPEN : LINK; }
    public LinkerMode next() { return this == LINK ? OPEN : LINK; }
    public boolean marksArea() { return this == LINK; }
    @Override public String getSerializedName() { return name().toLowerCase(Locale.ROOT); }
}
```

```java
// linker/LinkerOpen.java
public final class LinkerOpen {
    public static final int MAX_CHOICES = 16;

    /** Como a máquina chega no roteador: preso nela ou vinculado pelo alcance. */
    public enum Kind { ATTACHED, REMOTE }

    public record Choice(BlockPos router, Kind kind, int distance, String name) {}

    /** Os roteadores que chegam em {@code pos}, no alcance do jogador e que ele pode tocar; os presos primeiro, depois por distância. */
    public static List<RouterBlockEntity> candidates(ServerPlayer player, BlockPos pos);

    /** Clique do item no modo OPEN: 0 candidatos avisa na action bar; 1 abre; mais de 1 manda a lista. */
    public static void click(ServerPlayer player, ItemStack stack, BlockPos pos);

    /** Escolha na lista: revalida (item na mão em OPEN, roteador entre os candidatos) e abre. */
    public static boolean pick(ServerPlayer player, BlockPos machine, BlockPos router);
}
```

- `LinkerOpen.candidates` usa `TabletClick.attachedRouters(level, pos)` e `RemoteIndex.get().routersAt(level, pos)` (sem repetir), filtra com `TabletMenu.inReach(player, router)`, `level.mayInteract(player, router.getBlockPos())` e o evento de clique nas proteções de outros mods no **roteador** (`RangeLinks.clickAllowed(player, hand, router.getBlockPos())`, que passa de `private` para público nesta tarefa), e corta em `MAX_CHOICES`. O clique na máquina já passou pelo evento do jogo, mas o roteador pode estar longe dela, num claim de outro jogador, e a escolha na lista chega por pacote, sem clique: por isso o `pick` roda a mesma filtragem do zero.
- Abrir um roteador: preso → `RemoteRouterMenu.open(player, router)`; remoto → `RemoteRouterMenu.open(player, router, EditTargetRef.resolve(router, EditTarget.machine(router.remoteMachines().index(pos)), pos, null))` (o mesmo que o `TabletClick.open` faz; se o alvo não resolve, abre na presa).
- `LinkerOpenListPayload(BlockPos machine, List<LinkerOpen.Choice> choices)` (servidor → cliente) e `LinkerOpenPickPayload(BlockPos machine, BlockPos router)` (cliente → servidor), registrados em `ModPayloads` com o padrão do `LinkerActionPayload` (`TYPE`, `STREAM_CODEC`, `type()`); o handler do pick chama `LinkerOpen.pick`.

- [ ] **Passo 1: JUnit do modo (falha primeiro).** `LinkerModeTest`: `byName("single")`, `("area")`, `("machines")` e `("link")` dão `LINK`; `("open")` dá `OPEN`; um nome desconhecido dá `LINK`; `next()` alterna; `CODEC` ida e volta de `OPEN`. Rodar `./gradlew test --tests "*LinkerModeTest"` e ver falhar (a classe ainda tem `SINGLE/AREA/MACHINES`).
- [ ] **Passo 2: o enum.** Escrever `LinkerMode` como acima; rodar o teste (passa).
- [ ] **Passo 3: o item.** `LinkerItem`: apagar abas, faces, tipo antigo, `cycleTabs`, `tabsName`, `facesLetters`, `facesNames`, `sideLetter`, `sideName`, `TYPE_CODEC`, `TABS_*`, `FACES_*`. `onItemUseFirst`: modo `LINK` e sem Shift: num roteador `RangeLinks.choose`, em outro bloco `RangeLinks.toggle`; modo `OPEN` e sem Shift: `LinkerOpen.click(player, stack, pos)`. `useOn`: Shift + clique num bloco marca o canto só em `LINK`. `use`: Shift + clique no ar alterna `LINK`/`OPEN` com a mensagem `KEY + "mode"`; clique abre a tela (só em `LINK`; em `OPEN` não há tela). Tooltip: modo, roteador escolhido, cantos, desvincular. Reescrever a javadoc.
- [ ] **Passo 4: o vínculo de máquinas sem abas.** `RangeLinks.linkArea`: trocar `LinkerItem.effectiveTabs(stack)` por `LoadedTypes.LIST` e apagar o ramo `NO_TABS`; `LinkerProblem.NO_TABS` sai do enum. `RangeLinks.toggle` já usa `LoadedTypes.LIST`.
- [ ] **Passo 5: o `LinkerActions`.** Apagar `single`, `link`, `resultMessage`, `inTarget`, `apply`, `applyChecked`, `withKept`, `createNetwork`, `LinkResult` e os ramos de rede do `handle`; ficam `checkArea`, `markCorner`, `position`, `maxVolume`, `maxDistance` e o `handle` só com `SET_UNLINK`, `CLEAR_AREA` e `LINK` (este chama `RangeLinks.linkArea`). Apagar `LinkerScan`, `LinkerTabs`, `LinkerFaces` e seus JUnit.
- [ ] **Passo 6: o payload e o snapshot.** `LinkerActionPayload`: `Op` com `SET_UNLINK`, `CLEAR_AREA`, `LINK`; o record fica `LinkerActionPayload(int containerId, Op op, int value)`. `LinkerSnapshot`/`LinkerMenu`: tirar redes, abas, faces e modo; o snapshot leva o necessário para a tela da tarefa 3 (roteador escolhido com nome, vagas `used/max` e o motivo de não vincular; os cantos; o desvincular; o resultado do último Vincular). Se isso couber no estado do item, apagar `LinkerSnapshotPayload`.
- [ ] **Passo 7: `LinkerOpen`.** Implementar `candidates`, `click` e `pick` como na seção Interfaces, mais o `LinkerOpenListPayload`/`LinkerOpenPickPayload` e o registro em `ModPayloads`. A mensagem sem candidatos: `item.wirelessautomate.linker.open.none`. `ModPayloads.VERSION = "30"`.
- [ ] **Passo 8: GameTests do Abrir (`LinkerOpenGameTests`).** Cenários: (a) um roteador preso na máquina abre direto; (b) um roteador de alcance com a máquina vinculada abre já com o alvo da máquina (`menu.target()` é a máquina); (c) preso mais remoto: `candidates` devolve os dois, o preso primeiro; (d) fora do alcance (config `range` baixo, lote `wirelessautomate_config_linker_open`, restaurar em `try/finally`) não é candidato; (e) o `pick` de um roteador que não está entre os candidatos é recusado; (f) o `pick` com outro item na mão é recusado; (g) mais de 16 roteadores corta em 16; (h) um roteador num claim que cancela o `RightClickBlock` (um listener de teste que cancela o evento na posição dele) não é candidato e o `pick` dele é recusado. Cada teste cria a própria rede e espera o `onLoad` com `thenWaitUntil`.
- [ ] **Passo 9: reescrever os GameTests do Vinculador** (lista em Arquivos): manter os de vínculo de máquinas, de área de máquinas, do desvincular e das recusas (`RangeLinkerGameTests`, `RangeFixGameTests`); apagar os de rede, abas e faces. `grep -rE "LinkerTabs|LinkerFaces|LinkerScan|LinkerActions\.(single|link|apply|createNetwork)|SET_ACTIVE|CREATE_NETWORK|TOGGLE_TAB|SET_TABS|SET_FACES" src/` tem de dar zero (menos a tela, que a tarefa 3 trata).
- [ ] **Passo 10: componentes legado.** `ModDataComponents`: `LINKER_TABS`, `LINKER_FACES`, `LINKER_TYPE` como legado (`Codec.PASSTHROUGH`, comentário `// Legado: só registrado para ler itens antigos; nada os usa.`); `LINKER_MODE` com o `LinkerMode.CODEC` novo. Um GameTest `LinkerLegacyItemGameTests` lê um `ItemStack` do Vinculador com `linker_mode: "area"` e `linker_tabs: ["item"]` e confere que o item continua lido, no modo `LINK`.
- [ ] **Passo 11: verificar o servidor.** O `client/linker/` ainda referencia o que saiu e não compila até a tarefa 3. Esta tarefa não tem commit próprio: confira o servidor com `./gradlew compileJava` filtrando os erros fora de `client/linker/` e `client/dev/`, rode as varreduras (`varrer_perf.py`, `varrer.py`, `medir.py`) nos arquivos do servidor e siga direto para a tarefa 3, que fecha as duas com **um commit só**.

---

## Tarefa 3: Vinculador no cliente (a tela e a lista do Abrir)

**Custo:** layout e texto só quando o estado muda (chave suja), nada por quadro além de desenhar.

**Arquivos:**
- Modificar: `client/linker/LinkerScreen.java` (abaixo de 500 linhas), `LinkerLayout.java`, `LinkerPaint.java`, `LinkerText.java`, `LinkerScreenActions.java`, `AreaMap.java`, `client/AreaRenderer.java`, `client/dev/ScreenshotLinker.java`, `client/dev/E2eLinkerSteps.java`, `client/dev/E2ePortugueseSteps.java`, `client/dev/E2eShowcase.java`, `client/ClientSetup.java` (registro do handler da lista).
- Apagar: `client/linker/NetworkColumn.java`, `client/linker/SingleBody.java`.
- Criar: `client/linker/LinkerOpenScreen.java` (a lista curta: tela simples, sem menu de contêiner).

**Interfaces:**
- Consome `LinkerOpenListPayload(BlockPos machine, List<LinkerOpen.Choice> choices)` e manda `LinkerOpenPickPayload(machine, router)`.
- A tela do Vinculador segue a maquete aprovada: cabeçalho com o título e o botão de tema; a linha "Roteador escolhido" (nome, posição, vagas `used/max` ou o motivo de não vincular); o seletor **Vincular | Desvincular**; a prévia da área (`AreaMap`) com os cantos e a contagem; o botão **Vincular N** (ou **Desvincular N**) e **Limpar**. Sem coluna de redes, sem chips de tipo, sem linha Faces.

- [ ] **Passo 1: apagar `NetworkColumn` e `SingleBody`** e tirar de `LinkerScreen`, `LinkerLayout`, `LinkerPaint`, `LinkerText` e `LinkerScreenActions` tudo o que era rede, aba, face e modo da tela. O `LinkerScreen` não tem mais botões de modo (o modo troca no ar).
- [ ] **Passo 2: a tela de Vincular.** Montar o corpo com o `AreaMap` (que já mostra a prévia de cima, os cantos e a contagem); a linha do roteador escolhido; o seletor Vincular/Desvincular mandando `SET_UNLINK`; o botão mandando `LINK`; Limpar mandando `CLEAR_AREA`. Todo texto variável por `GuiText`. A largura extra de uma tela redimensionada vai para o mapa.
- [ ] **Passo 3: `LinkerOpenScreen`.** Lista de linhas (nome, "preso" ou "alcance", distância em blocos), com rolagem se passar de 8; clique manda o `LinkerOpenPickPayload` e fecha; Esc fecha. Linhas e textos montados uma vez ao abrir (a lista não muda com a tela aberta).
- [ ] **Passo 4: o handler do pacote da lista** em `ClientSetup` (abre a `LinkerOpenScreen` no thread do cliente).
- [ ] **Passo 5: capturas e e2e.** `ScreenshotLinker` e `E2eLinkerSteps` trocam os passos de Único, Área e rede pelos de Vincular (roteador escolhido, área marcada, Vincular, Desvincular) e de Abrir (lista com dois roteadores, `linker-abrir`), nos dois idiomas e temas. `E2ePortugueseSteps` e `E2eShowcase` tiram o que citava rede ou aba do Vinculador.
- [ ] **Passo 6: medir e verificar.** `python -I .claude/skills/wa-modular/scripts/medir.py` (nenhuma classe acima do alvo), `./gradlew build runGameTestServer`.
- [ ] **Passo 7: varreduras e commit das tarefas 2 e 3.** `varrer_perf.py`, `varrer.py` e `medir.py` nos arquivos das duas tarefas; `./gradlew build runGameTestServer` verde; `git commit -m "Vinculador: modos Vincular e Abrir, sem redes, abas e faces"`.

---

## Tarefa 4: Textos, guia, ajuda e o que sobrou

**Arquivos:**
- Modificar: `src/main/resources/assets/wirelessautomate/lang/en_us.json` e `pt_br.json`, `scripts/guide/gerar_guia.py` (as páginas do Vinculador e do Configurador, nos dois idiomas), `scripts/curseforge/` só se citar os modos antigos, `scripts/textures/gerar_texturas.py` só se o sprite citar modo.

- [ ] **Passo 1: varredura de chaves.** Para cada chave `item.wirelessautomate.linker.*` e `gui.wirelessautomate.linker.*` dos dois `lang`, `grep` no código; apagar as sem uso (rede, abas, faces, modos antigos, `kept`, `foreign`, `already*`, `linked*`, `unlinked*` do modo de roteadores). Criar as novas (`mode.link`, `mode.open`, `open.none`, `open.title`, `open.attached`, `open.remote`, textos da tela de Vincular e do tooltip).
- [ ] **Passo 2: o guia.** Reescrever, em `gerar_guia.py` (nunca nos `.md`), a seção do Vinculador (Vincular e Abrir, só como operar) e a do Configurador (Pincel e a roda do tipo), em inglês e português; gerar com `python scripts/guide/gerar_guia.py`.
- [ ] **Passo 3: dados e docs de gerador.** Conferir que nenhuma receita, tag ou lang residual cita os modos que saíram.
- [ ] **Passo 4: verificar e commitar.** `./gradlew build runGameTestServer`; `git commit -m "Textos e guia do Vinculador e do Configurador"`.

---

## Tarefa 5: Verificação final e fechamento

**Arquivos:** `CLAUDE.md`, `docs/progresso.md`, `docs/especificacao.md` (se citar os modos), `docs/superpowers/plans/2026-10-22-vinculador-configurador-novos.md` (marcar o feito).

- [ ] **Passo 1: varredura do que sobrou.** `grep -rE "LinkerTabs|LinkerFaces|LinkerScan|ConfiguratorArea|PasteMode|NetworkColumn|SingleBody|LinkerMode\.(SINGLE|AREA|MACHINES)" src/ docs/ CLAUDE.md` deve dar zero fora dos três registros legados e do histórico do `progresso.md`. Rodar `python -I .claude/skills/wa-comentarios/scripts/varrer.py` e `python -I .claude/skills/wa-modular/scripts/medir.py` no ramo inteiro.
- [ ] **Passo 2: verificação completa.** `./gradlew build runGameTestServer runGameTestServerChemicals runGameTestServerSource runGameTestServerAllthemodium`, uma run por vez.
- [ ] **Passo 3: e2e.** `WA_E2E="$PWD/run/e2e" ./gradlew runClient`; `run/e2e/result.txt` precisa dizer `OK`; olhar as capturas novas (`linker-*`, `wheel-*`), procurando texto cortado.
- [ ] **Passo 4: docs.** `CLAUDE.md`: atualizar a linha do `linker/`, `preset/`, `item/`, `client/linker/`, `packet/` e `gametest/` (o mapa do código) e a nota do protocolo; `docs/progresso.md`: tabela de estado, próximo passo e uma linha no histórico com os números de testes.
- [ ] **Passo 5: revisão final do ramo inteiro** no modelo mais capaz, com o brief de `references/conducao-de-agentes.md` da skill `wa-feature`, conferindo: código morto, comentários, as leis de performance e o tamanho das classes.
- [ ] **Passo 6: commit.** `git commit -m "Vinculador e Configurador novos: verificação e docs"`. Sem push.
