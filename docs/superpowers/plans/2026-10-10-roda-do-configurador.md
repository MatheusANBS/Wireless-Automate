# Roda do Configurador: plano de implementação

**Objetivo:** o modo Área do Configurador ganha a opção "qualquer máquina", e uma roda radial (segurar Alt esquerdo) escolhe o modo (Pincel, Área mesma máquina, Área qualquer máquina) e o tipo colado.

**Spec:** [`docs/superpowers/specs/2026-10-10-roda-do-configurador-design.md`](../specs/2026-10-10-roda-do-configurador-design.md). Ramo `roda-configurador`.

Pacote base: `src/main/java/io/github/matheusanbs/wirelessautomate/` (abreviado `B/`).

## Restrições globais

- Código, comentários, mensagens de commit e docs em português; traduções em `en_us.json` e `pt_br.json`, sempre as duas.
- Chaves existentes de NBT, componentes e config **não mudam** (`configurator_mode`, `configurator_type`, `configurator_area`, `configurator_machine`, `preset`). Componente novo: `configurator_any_machine`. Ausente = mesma máquina.
- Lógica pura sem classes do Minecraft vai com JUnit em `src/test/java` (`PasteMode`, `WheelLayout`).
- Classes de tela só em `client/`, nunca referenciadas por código comum (o `runGameTestServer` é servidor dedicado). O tooltip do item usa `Component.keybind(...)` em vez de classe de cliente.
- Todo texto variável de tela passa por `GuiText` (o e2e confere `GuiText.clipCount()==0`).
- Performance: nada de tick por bloco; o tick de cliente da tecla só lê `consumeClick`.
- `ModPayloads.VERSION` passa de `"10"` para `"11"` (tarefa 3, payload novo). O servidor valida tudo.
- Verificação antes de cada commit (Git Bash, na raiz, `JAVA_HOME` no JDK 21): `./gradlew build runGameTestServer runGameTestServerChemicals runGameTestServerSource runGameTestServerAllthemodium`. Compile antes (`./gradlew compileJava`); com `BUILD FAILED`, conserte em vez de esperar. Nunca rode dois jogos ao mesmo tempo. Não mexa em `run/options.txt`.
- Commits sem linhas de atribuição (nada de `Co-Authored-By` ou `Claude-Session`).

---

### Tarefa 1: modo "qualquer máquina" no item e na área (sem a roda)

**Arquivos:** `B/preset/PasteMode.java` (novo), `src/test/java/.../preset/PasteModeTest.java` (novo), `B/registry/ModDataComponents.java`, `B/item/ConfiguratorItem.java`, `B/preset/ConfiguratorArea.java`, os dois `lang`, `B/gametest/ConfiguratorGameTests.java`.

1. `PasteMode` (enum puro: `BRUSH`, `AREA_SAME`, `AREA_ANY`): `static PasteMode of(LinkerMode mode, boolean anyMachine)` (SINGLE → BRUSH, qualquer `anyMachine`), `LinkerMode linkerMode()`, `boolean anyMachine()`, `PasteMode next()` (BRUSH → AREA_SAME → AREA_ANY → BRUSH), `String key()` (`brush`, `area_same`, `area_any`). `LinkerMode` é enum puro, pode importar. JUnit cobrindo `of`, `next` dando a volta e ida e volta `of(m.linkerMode(), m.anyMachine()) == m`.
2. Componente `CONFIGURATOR_ANY_MACHINE` (`"configurator_any_machine"`, `Codec.BOOL` / `ByteBufCodecs.BOOL`, persistente e sincronizado), ao lado dos outros do Configurador.
3. `ConfiguratorItem`: `static PasteMode pasteMode(ItemStack)` e `static void setPasteMode(ItemStack, PasteMode)` (grava `configurator_mode` como hoje pelo `setMode` e o `any_machine` só quando `true`; remove quando falso). `modeName(PasteMode)` com as chaves `mode.brush`, `mode.area_same`, `mode.area_any` (a `mode.area` sai). Shift + clique no ar usa `pasteMode(stack).next()`. Mantenha `mode(stack)` (outros lugares e o e2e usam).
4. `ConfiguratorArea`: com `anyMachine`, cola em todo roteador da área (a contagem `otherMachine` fica 0) e o canto 2 usa a chave nova `area.corner2_any` (`Área de %s: %s roteadores · clique direito no ar para colar`). O texto de `area.other_machine` passa a `%s em outra máquina ficaram como estavam (Área: qualquer máquina, na roda)` (en: `%s on another machine left unchanged (Area: any machine, on the wheel)`).
5. Tooltip (`appendHoverText`): `Modo: <nome do PasteMode>` (com a área como hoje); no AREA_ANY a linha de colar vira `tooltip.paste_area_any` (`Clique no ar: colar em todos os roteadores da área`); linha nova `tooltip.wheel` = `Segure %s: roda de modos e tipos` com `Component.keybind("key.wirelessautomate.configurator_wheel")` como argumento; `tooltip.to_area`/`tooltip.to_brush` saem e entra `tooltip.next_mode` = `Shift + clique no ar: modo %s` com o nome do próximo modo. Ordem: copiar, (marcar), colar, limpar, roda, próximo modo, Shift + roda. A mensagem de troca (`KEY + "mode"`) continua `Configurador: modo %s`.
6. Lang en e pt: as chaves novas, a tecla `key.wirelessautomate.configurator_wheel` (`Configurator wheel` / `Roda do Configurador`) já aqui, e remova as chaves que saíram.
7. GameTests em `ConfiguratorGameTests`: ajuste o `areaPastesOnlyOnSameMachine` ao ciclo novo (para voltar ao Pincel são dois Shift + cliques; confira `AREA_ANY` no meio). Novo `areaAnyMachinePastesEverywhere`: cópia de um roteador num barril (`Blocks.BARREL`), outro num baú (`Blocks.CHEST`) na área, modo AREA_ANY por `setPasteMode`, colar: o do baú fica igual ao copiado em todas as faces e abas. Novo teste de que uma varinha sem o componente vale AREA_SAME/BRUSH.

### Tarefa 2: geometria da roda (pura)

**Arquivos:** `B/client/WheelLayout.java` (novo, puro, sem classes do Minecraft, como `TabLayout`), `src/test/java/.../client/WheelLayoutTest.java`.

- `record WheelLayout(double innerRadius, double ringSplit, double outerRadius, double outerLimit)` com valores relativos ao raio externo e um `static WheelLayout forRadius(double outer)` (proporções do mockup: centro 46, anel de dentro até 112, anel de fora de 118 a 182, aceita até 192, para raio externo 182).
- `record Hit(Ring ring, int index)`, `enum Ring { INNER, OUTER }`. `@Nullable Hit hit(double dx, double dy, int innerCount, int outerCount)`: `null` no centro (`< innerRadius`) e fora (`> outerLimit`); fatia 0 centrada no topo (dy negativo), sentido horário; o limite entre os anéis é o meio do vão.
- `static double sliceStart(int index, int count)` e `sliceEnd` (radianos, 0 no topo, horário) para o desenho.
- JUnit: centro, fora, topo = 0, direita = 1/4 do anel, bordas de fatia, vão entre anéis, contagens 3, 4, 5 e 6.

### Tarefa 3: payload da roda

**Arquivos:** `B/packet/ConfiguratorWheelPayload.java` (novo), `B/packet/ModPayloads.java`, `B/gametest/ConfiguratorGameTests.java` (ou um teste de payload que já exista, no mesmo molde dos outros handlers).

- `record ConfiguratorWheelPayload(PasteMode mode, Optional<ResourceType> type)`, id `configurator_wheel`, `StreamCodec` por enum (`NeoForgeStreamCodecs.enumCodec`) e `ByteBufCodecs.optional`.
- Handler `handleConfiguratorWheel(@Nullable ServerPlayer, payload)`: recusa sem Configurador na mão principal e tipo fora de `LoadedTypes`; grava `setPasteMode` e `setType`; action bar `item.wirelessautomate.configurator.wheel` = `Configurador: modo %s · cola %s` (en: `Configurator: %s mode · pastes %s`). Devolve `boolean` como os irmãos.
- `VERSION` = `"11"`.
- GameTest do handler: grava modo e tipo; recusa com outra coisa na mão (sem mudar o item); `Optional.empty()` = Todos.

### Tarefa 4: tecla e tela da roda

**Arquivos:** `B/client/ConfiguratorWheelKeys.java` (novo, no molde do `TabletKeys`), `B/client/ConfiguratorWheelScreen.java` (novo), `lang` se faltar texto.

- `KeyMapping` `key.wirelessautomate.configurator_wheel`, `GLFW_KEY_LEFT_ALT`, `KeyConflictContext.IN_GAME`, categoria do mod; registrada no `RegisterKeyMappingsEvent`. No `ClientTickEvent.Post`: `while (consumeClick())`, abre a tela se `screen == null` e há Configurador na mão principal.
- `ConfiguratorWheelScreen extends Screen`: `isPauseScreen()` falso, sem fundo escurecido de tela inteira (só um disco translúcido atrás da roda). Estado local inicial do item (`ConfiguratorItem.pasteMode`, `type`); itens de fora = `null` (Todos) + `LoadedTypes.LIST`.
  - Desenho com triângulos (`Tesselator`/`BufferBuilder` `POSITION_COLOR`, shader `GameRenderer::getPositionColorShader`), cada fatia em ~12 subdivisões, com contorno (fatia um pouco maior por trás na cor da tinta, ou da destaque se é a atual). Raio externo ~ 0,38 × min(largura, altura) da tela, limitado entre 90 e 180 px.
  - Ícones: tipos por `ResourceStyle.drawIcon` (ou o ícone escalado) e nome curto embaixo; modos: o item do Configurador (`renderItem`) no Pincel e grade 3×2 de quadradinhos nos dois de Área (mesma: cor `ResourceStyle.color(ITEM)` em todos; qualquer: cores variadas). Rótulos curtos dos modos: chaves `gui.wirelessautomate.configurator_wheel.short.<key>` (Pincel / Mesma máq. / Qualquer; en Brush / Same / Any).
  - Centro: nome e descrição do item sob o mouse por `GuiText.wrap`; sem nada, `gui.wirelessautomate.configurator_wheel.cancel` (`Soltar aqui fecha` / `Release here to close`). Descrições: `gui.wirelessautomate.configurator_wheel.desc.<key>` dos modos e reaproveite `item.wirelessautomate.configurator.only_tab` / um texto "todas as abas" para os tipos.
  - Animação de abertura: `t` por `Util.getMillis()` desde a abertura, 150 ms, ease-out-back; escala 0,6→1 e giro −20°→0 na `PoseStack` em torno do centro. Fatia sob o mouse: escala 1,06 e cor mais clara.
  - Entrada: `keyReleased` / `mouseReleased` que casam com a tecla (`KeyMapping.matches` / `matchesMouse`) escolhem o hit sob o mouse e fecham; clique esquerdo escolhe sem fechar; Esc fecha. Escolher manda o `ConfiguratorWheelPayload` com o estado completo (só se `connection.hasChannel`) e atualiza o estado local.
- Verificação: build e as runs; olhe a tela no `runClient` se puder (sem monitor, deixe para a tarefa 5).

### Tarefa 5: e2e, guia e docs

- `DevEndToEnd.configuratorSteps`: depois do modo Área, abrir a `ConfiguratorWheelScreen` direto (`minecraft.setScreen`), captura `10b-configurador-roda`, escolher o AREA_ANY pela mesma rotina do clique (método de pacote da tela, por exemplo `choose(Hit)`) e conferir no servidor; seguir o roteiro com o modo novo onde fizer sentido. `GuiText.clipCount()==0` na roda.
- Guia (`scripts/guide/gerar_guia.py`, nos dois idiomas): página do Configurador com os três modos, a roda (segurar a tecla, anéis, soltar escolhe) e o Shift + clique no ar em ciclo. Rode o script.
- `CLAUDE.md` (mapa: `PasteMode`, `WheelLayout`, `ConfiguratorWheelScreen`/`Keys`, payload, `VERSION` 11), `docs/especificacao.md` (Configurador) e `docs/progresso.md` (tabela, próximo passo, histórico com os números de testes).
- e2e: `WA_E2E="$PWD/run/e2e" ./gradlew runClient`, `run/e2e/result.txt` = `OK`; olhar as capturas novas.
