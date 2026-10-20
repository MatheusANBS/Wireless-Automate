# Ressonador: todas as animações da câmara (parte 2) · plano de implementação

> **Para agentes:** SUB-SKILL OBRIGATÓRIA: use superpowers:subagent-driven-development (recomendado) ou superpowers:executing-plans para executar este plano tarefa por tarefa. Os passos usam caixas (`- [ ]`) para acompanhar.

**Objetivo:** o conduto de energia que só acende com vazão real por Porta, a recarga nova (espiral, enchimento e anel), a aura dos cristais e o plasma sob pressão com o buffer cheio, todos por shader; e as faíscas antigas (modo do Iris) seguindo a vazão real.

**Arquitetura:** o servidor conta o que sai por cada Porta de Energia (o índice da Porta vai na visão de capability, calculado na montagem) e, no lote de 20 ticks, guarda uma faixa de vazão por Porta e o "cheio" na `ChamberView`, que só sincroniza quando muda. No cliente, um segundo core shader `resonator_ribbon` desenha fitas (conduto e espiral) e quadrados de brilho (aura e anel), com o modo e os parâmetros nos vértices; partes novas do renderer montam a geometria sem alocar.

**Stack:** Java 21, NeoForge 21.1 (Minecraft 1.21.1), GLSL 150, JUnit 5, GameTests.

**Spec:** [`docs/superpowers/specs/2026-10-20-ressonador-camara-animacoes-design.md`](../specs/2026-10-20-ressonador-camara-animacoes-design.md). Protótipo aprovado: `docs/preview/ressonador-camara-completa.html`. Parte 1 pronta no ramo: `PlasmaShader`, `ChamberRenderTypes.plasma()`, `PlasmaCore`, `ShaderPackCheck`, `ResonatorRenderer.forceLegacyForTests`, o filtro `WA_E2E_ONLY=ressonador` e a medida `camara antiga/plasma` do e2e.

## Restrições globais

- Código, comentários, commits e docs em português; textos novos (se houver) nos dois `lang`.
- **Servidor:** chaves de NBT do Núcleo não mudam (a vazão e o "cheio" não vão para o NBT). A `ChamberView` ganha `byte[] flows` e `boolean full`, chaves do update tag `flows` e `full`; `equals`/`hashCode`/`matches` passam a considerá-las. `ModPayloads.VERSION` não muda (o update tag não é payload do mod).
- **Faixa de vazão:** `CoreMath.flowBand(long extracted, int ticks, long maxOutput)`: 0 se `extracted <= 0` ou `maxOutput <= 0`; senão `f = extracted / (ticks × maxOutput)` (em `double`, saturado em 1) e `min(7, 1 + floor(7 × sqrt(f)))`.
- **Índice da Porta de Energia:** a ordem das Portas de Energia na casca por x, depois y, depois z, a partir da origem da caixa, a mesma do `ChamberScene.readPorts` do cliente. Uma função comum em `resonator/` faz a ordem para os dois lados.
- **Só o que sai de verdade conta:** extração com `simulate == true` não soma.
- **Cliente:** só em `client/resonator/render/` e `assets/wirelessautomate/shaders/core/`; nada referenciado por código comum. Com `PlasmaShader.get() == null`, `RibbonShader.get() == null` ou `ShaderPackCheck.active()`, o desenho antigo (com as faíscas seguindo a vazão).
- **Performance (`wa-performance`):** um `long +=` por extração real; uma conta por Porta por lote; nada por tick novo; o update da câmara só quando a vista muda; nada alocado por quadro no cliente (nem `Vec3`, nem `Quaternionf`, nem lista); até ~14 quads por Núcleo. Leia `.claude/skills/wa-performance/SKILL.md` e rode `python -I .claude/skills/wa-performance/scripts/varrer_perf.py` nos arquivos mudados.
- Lógica pura com JUnit; o que depende do jogo, com GameTest (`timeoutTicks` ≤ 300; template `empty7` e o apoio `ResonatorTestSupport`).
- Tamanho (`wa-modular`): partes até 500 linhas; `ResonanceCoreBlockEntity` já tem 631: o que crescer nele vai para classe de pacote nova. `python -I .claude/skills/wa-modular/scripts/medir.py`; comentários: `python -I .claude/skills/wa-comentarios/scripts/varrer.py`.
- Antes de cada commit: `./gradlew build runGameTestServer runGameTestServerChemicals runGameTestServerSource runGameTestServerAllthemodium`; nas tarefas de cliente, também `WA_E2E="$PWD/run/e2e" WA_E2E_ONLY=ressonador ./gradlew runClient` (`run/e2e/result.txt` OK) e **abrir as capturas** com a ferramenta Read. Compile antes; com `BUILD FAILED`, conserte. Nunca dois jogos ao mesmo tempo; não mexa em `run/options.txt`.
- Commits sem linhas de atribuição; `git add` só dos arquivos da tarefa (nunca `run/`, `.superpowers/`, `docs/curseforge/perfil-400.png`). Não faça push.

## Mapa de arquivos

Base `B/` = `src/main/java/io/github/matheusanbs/wirelessautomate/`, `T/` = `src/test/java/io/github/matheusanbs/wirelessautomate/`.

| Arquivo | Papel |
| --- | --- |
| `B/resonator/CoreMath.java` (mudar) + JUnit | `flowBand` |
| `B/resonator/PortOrder.java` (novo, comum) | A ordem das Portas na casca, para o servidor e o cliente |
| `B/resonator/CoreFlow.java` (novo) | As somas por Porta e as faixas, do lado do Núcleo |
| `B/resonator/ResonatorPorts.java`, `ResonatorCapabilities.java`, `ResonatorFormation.java`, `ResonatorBatch.java`, `ResonanceCoreBlockEntity.java`, `ChamberView.java` (mudar) | Índice na visão, contagem, faixas no lote, a vista |
| `B/gametest/ResonatorFlowGameTests.java` (novo) | Vazão real, faixa, "cheio" e sincronização |
| `assets/wirelessautomate/shaders/core/resonator_ribbon.{json,vsh,fsh}` (novos), `B/client/resonator/render/RibbonShader.java` (novo), `ChamberRenderTypes.java` (mudar) | O segundo core shader |
| `B/client/resonator/render/FlowConduit.java`, `RechargeSpiral.java`, `CrystalAura.java` (novos) | As animações novas |
| `B/client/resonator/render/ResonatorRenderer.java`, `ChamberScene.java`, `ChamberParticles.java`, `PlasmaCore.java`, `resonator_plasma.fsh` (mudar) | A troca, a ordem das Portas, as faíscas pela vazão, a pressão |
| `B/client/dev/E2eResonatorSteps.java` (mudar) | Capturas novas |

---

### Task 1: Vazão por Porta e "cheio" no servidor

**Files:** `B/resonator/CoreMath.java`, `T/resonator/CoreMathTest.java`, `B/resonator/PortOrder.java`, `B/resonator/CoreFlow.java`, `B/resonator/ResonatorPorts.java`, `B/resonator/ResonatorCapabilities.java`, `B/resonator/ResonatorFormation.java`, `B/resonator/ResonatorBatch.java`, `B/resonator/ResonanceCoreBlockEntity.java`, `B/resonator/ChamberView.java`, `B/gametest/ResonatorFlowGameTests.java`.

**Interfaces:**
- Produces: `CoreMath.flowBand(long, int, long)`; `PortOrder.energyIndex(int[] origin, List<BlockPos> energyPorts, BlockPos port)` e `PortOrder.sort(...)` (a ordem x, y, z relativa à origem); `ChamberView.flows()` (`byte[]`, uma faixa por Porta de Energia na ordem) e `ChamberView.full()`; `ResonatorPorts.CoreEnergy(core, int port)` e `CoreEnergyHandler(core, int port)`.
- Custo: ver as Restrições globais.

- [ ] **Step 1: JUnit que falha** em `CoreMathTest`: `flowBand(0, 20, 1000) == 0`; `flowBand(5, 20, 0) == 0`; `flowBand(20_000, 20, 1_000) == 7` (f = 1); `flowBand(20 * 1_000 / 100, 20, 1_000) == 1 + (int) Math.floor(7 * Math.sqrt(0.01))` (= 1); `flowBand(20 * 250, 20, 1_000) == 4` (f = 0,25: 1 + floor(3,5)); `flowBand(Long.MAX_VALUE, 20, 1) == 7`.
- [ ] **Step 2: GameTests que falham** (`ResonatorFlowGameTests`):
  - `vazaoRealAcendeAFaixa`: Núcleo montado com geração; tirar energia pela capability da Porta de Energia (`BulkEnergy` da posição da Porta) todo tick por 40 ticks; depois de um lote, `core.clientViewForTests()` (ou a vista montada no servidor) tem `flows()[0] > 0`.
  - `simulacaoNaoConta`: só extrações com `simulate = true` → faixa 0.
  - `semVazaoApaga`: parar de tirar → no lote seguinte a faixa volta a 0.
  - `duasPortasSeparadas`: com duas Portas de Energia (montagem com duas), tirar só da segunda acende só o índice dela, na ordem x, y, z.
  - `cheioAparece`: sem extração até encher o buffer → `full()` verdadeiro; tirar um pouco → falso no lote seguinte.
  - `vistaEstavelNaoReenvia`: vazão constante por 3 lotes não muda a vista depois do primeiro (a `ChamberView` igual; use o contador ou o `matches` como os testes do `ResonatorSyncGameTests`).
- [ ] **Step 3: Implementar.**
  - `CoreMath.flowBand` como nas Restrições globais.
  - `PortOrder`: ordena as Portas de Energia pela tupla (x − origemX, y − origemY, z − origemZ) e dá o índice de uma posição.
  - `ResonatorFormation.updatePorts`/`form`: ao registrar as Portas, guarda no Núcleo a lista ordenada das Portas de Energia (pelo `PortOrder`), e o `CoreFlow` do Núcleo é dimensionado para elas (array de `long` criado na montagem, não por lote).
  - `ResonatorCapabilities`: a lambda da Porta de Energia procura o índice da `pos` na lista do Núcleo (busca linear numa lista pequena, só na criação da visão, que o `BlockCapabilityCache` guarda) e cria `new ResonatorPorts.CoreEnergy(core, index)` / `CoreEnergyHandler(core, index)`; sem índice (Porta não registrada), −1, que não soma.
  - `extract`/`extractEnergy` das visões: quando `!simulate` e saiu algo, `core.flow().add(port, saiu)`.
  - `ResonatorBatch.run`: no fim, `core.flow().closeBatch(ticks, CoreMath.maxOutput(chambers, fePerCrystal))` converte as somas em faixas e zera; o "cheio" é `energy >= capacity`. A `ChamberView` lê as faixas e o "cheio" do `CoreFlow`, e o `syncIfChanged` do lote manda o update só se a vista mudou.
  - `ChamberView`: os dois campos novos, as chaves `flows` e `full` no `save`/`load`, e o `matches`/`equals`/`hashCode` com eles.
- [ ] **Step 4: Rodar:** `./gradlew test build runGameTestServer runGameTestServerChemicals runGameTestServerSource runGameTestServerAllthemodium` → PASS (inclusive os `ResonatorSyncGameTests` e `ResonatorPortGameTests` de antes).
- [ ] **Step 5: Commit:** `Ressonador: vazão real por Porta de Energia e buffer cheio na câmara`.

---

### Task 2: O core shader das fitas

**Files:** `assets/wirelessautomate/shaders/core/resonator_ribbon.{json,vsh,fsh}`, `B/client/resonator/render/RibbonShader.java`, `B/client/resonator/render/ChamberRenderTypes.java`, o registro no `B/client/ClientSetup.java` (como o `PlasmaShader`).

**Interfaces:**
- Produces: `RibbonShader.get()`; `ChamberRenderTypes.ribbon()` (`POSITION_COLOR_TEX_LIGHTMAP`, `QUADS`, aditivo `LIGHTNING_TRANSPARENCY`, sem gravar profundidade, sem cull).
- **Contrato dos vértices:** `Color` = (R, G, B, A = intensidade 0..1); `UV0` = (u ao longo da fita 0..1, v através 0..1), ou o quadrado 0..1 nos modos radiais; `UV2` = (modo, parâmetro). Modos: **0 conduto** (parâmetro = faixa de vazão 1..7: pacotes `pow(0.5 + 0.5·sin(u·28 − t·(6 + 2·faixa)), 6)` correndo de u = 0 para u = 1, largura do miolo pela faixa, cor do coral em u = 0 ao RGB do vértice em u = 1); **1 espiral** (parâmetro = 0: 10 grãos subindo de u = 0 a u = 1 com `fract(k/10 + t·0,45)`, vermelho `#D6453B` para coral, girando em v); **2 aura** (radial; parâmetro = sintonia × 100: brilho `exp(−r²·k)` que treme pelo ruído quanto menor a sintonia); **3 anel** (radial; parâmetro = progresso × 100: anel que abre e some). Tempo pelo uniform `GameTime` (× 1200), como o plasma.
- O GLSL dos quatro modos sai do protótipo (`docs/preview/ressonador-camara-completa.html`, funções do conduto, da espiral, da aura e do anel), portado para GLSL 150 como o `resonator_plasma.fsh` da parte 1; descarte (`discard`) onde a contribuição for desprezível.

- [ ] **Step 1:** JSON (atributos `Position`, `Color`, `UV0`, `UV2`; uniforms `ModelViewMat`, `ProjMat`, `GameTime`), `.vsh` igual ao do plasma, `.fsh` com os quatro modos.
- [ ] **Step 2:** `RibbonShader` e o registro; `ChamberRenderTypes.ribbon()`.
- [ ] **Step 3: Verificar:** build e GameTests; o e2e filtrado e o log sem `Could not compile`/`resonator_ribbon`.
- [ ] **Step 4: Commit:** `Câmara do Ressonador: core shader das fitas`.

---

### Task 3: O conduto de energia, a pressão e as faíscas pela vazão

**Files:** `B/client/resonator/render/FlowConduit.java` (novo), `ChamberScene.java`, `ResonatorRenderer.java`, `PlasmaCore.java`, `ChamberParticles.java`, `assets/.../resonator_plasma.fsh`.

**Interfaces:**
- Consumes: `ChamberView.flows()`/`full()` (Task 1), `ChamberRenderTypes.ribbon()` e o contrato (Task 2), `ChamberScene.energyInner()` (as faces de dentro das Portas, já na ordem x, y, z do `readPorts`; confira e use o `PortOrder` se a ordem não for a mesma).
- Produces: `FlowConduit.render(PoseStack, MultiBufferSource, ChamberScene, float partialTick, int tierColor)`.

- [ ] **Step 1: `ChamberScene`:** guarda as faixas da vista (`flows`, com a suavização por tick que o plasma usa para a sintonia, para o conduto acender e apagar em ~1 s) e o `full`.
- [ ] **Step 2: `FlowConduit`:** para cada Porta de Energia com faixa > 0, uma fita do centro da câmara (a borda do plasma, raio 0,35) até a face de dentro da Porta, larguras de 0,08 a 0,3 bloco pela faixa, virada para a câmera (lado = normalizado de `direção × (câmera − meio)`, em `float`, sem objeto novo), modo 0, intensidade = faixa suavizada / 7, RGB do tier.
- [ ] **Step 3: Pressão:** o `PlasmaCore` passa o `full` no `UV2.y` junto do detalhe (`detail | (full ? 4 : 0)`), e o `resonator_plasma.fsh` lê `extra.y & 3` como detalhe e `extra.y >= 4` como cheio: o raio encolhe 0,05 e a borda do tier pulsa (`0.5 + 0.5·sin(t·7)`), como no protótipo.
- [ ] **Step 4: Faíscas antigas (modo do Iris):** o `ChamberParticles` passa a soltar faíscas só para Portas com faixa > 0 (frequência pela faixa, não pela geração).
- [ ] **Step 5: `ResonatorRenderer`:** com o modo shader, o `FlowConduit` substitui as faíscas (`ChamberSparks` e as faíscas do `ChamberParticles` não são desenhadas nem criadas).
- [ ] **Step 6: Verificar:** build, GameTests e o e2e filtrado; acrescente ao `E2eResonatorSteps` uma captura `ressonador-camara-fluxo.png` com energia saindo (um roteador ou um consumidor de teste tirando da Porta; se o mundo do e2e já tem roteador ligado à Porta, use-o) e confira que a `ressonador-camara.png` sem vazão não mostra conduto. Abra as duas e descreva.
- [ ] **Step 7: Commit:** `Câmara do Ressonador: conduto de energia pela vazão real e plasma sob pressão`.

---

### Task 4: Recarga nova e aura dos cristais

**Files:** `B/client/resonator/render/RechargeSpiral.java`, `CrystalAura.java` (novos), `ResonatorRenderer.java`, `ChamberParticles.java`, `ChamberCrystals.java` (só o que for preciso para ler a posição dos cristais sem duplicar a conta).

**Interfaces:**
- Consumes: `ChamberCrystals` (a posição de cada cristal e o progresso da recarga: `recharging(...)` e a fração `recharge(...)`), `ChamberScene.crystalInner()`, `ChamberRenderTypes.ribbon()`.
- Produces: `RechargeSpiral.render(...)`, `CrystalAura.render(...)`.

- [ ] **Step 1: `RechargeSpiral`:** com um Opaco em recarga, uma fita modo 1 da face de dentro da Porta de Cristais mais perto até o cristal, largura 0,25; quando a recarga termina (o progresso passa de 1 ou o cristal deixa de ser Opaco), um quadrado modo 3 no cristal por 0,7 s (o anel). O enchimento: a aura do cristal em recarga vai do cinza ao coral com o progresso.
- [ ] **Step 2: `CrystalAura`:** um quadrado modo 2 (0,5 bloco) virado para a câmera em cada cristal vivo, intensidade pela sintonia; o Opaco sem aura, salvo o enchimento da recarga.
- [ ] **Step 3: `ResonatorRenderer`:** no modo shader, a espiral substitui os grãos de redstone (o `ChamberParticles` não cria grãos nem partículas de pó nesse modo); a aura vai depois dos cristais e antes do plasma.
- [ ] **Step 4: Verificar:** build, GameTests e o e2e filtrado com uma captura `ressonador-camara-recarga.png` no meio de uma recarga (o e2e já monta um Opaco? se não, esvazie um cristal com `SignalCrystalItem.setCharge(stack, 0)` no servidor integrado e dê redstone). Abra e descreva.
- [ ] **Step 5: Commit:** `Câmara do Ressonador: recarga em espiral e aura dos cristais`.

---

### Task 6: O cristal em 3D (Ponta dupla)

Acrescentada depois da Task 4, pelo protótipo `docs/preview/ressonador-cristal-3d.html` (aprovado na forma Ponta dupla; a seção "O cristal em 3D" da spec tem os valores). Roda antes da Task 5.

**Files:** `assets/wirelessautomate/shaders/core/resonator_crystal.{json,vsh,fsh}` (novos), `B/client/resonator/render/CrystalShader.java`, `CrystalMesh.java` (novos), `ChamberRenderTypes.java`, `ChamberCrystals.java`, `CrystalAura.java`, `ResonatorRenderer.java`, `ClientSetup.java` (registro do shader), `B/client/dev/E2eResonatorSteps.java` (captura).

**Interfaces:**
- Consumes: `ChamberCrystals.crystalPos`, o estado do Opaco e o progresso da recarga que a `CrystalAura` já lê, a sintonia suavizada do `ChamberScene`, `GuiPaint.tierColor`, `ShaderPackCheck`.
- Produces: `CrystalShader.get()`, `ChamberRenderTypes.crystal()` e `CrystalMesh` (os 12 triângulos em constantes e a escrita no `VertexConsumer` sem alocar).

- [ ] **Step 1: Malha:** `CrystalMesh` com os vértices da spec, montados uma vez num `float[]` estático; escreve os 12 triângulos com a pose do cristal, a normal plana de cada faceta e as coordenadas da faceta (cada triângulo com os cantos (0,0), (1,0), (0,1) no UV0, para a aresta) e a altura relativa no cristal (0 na ponta de baixo, 1 na de cima). JUnit da malha: 12 triângulos, normais para fora, alturas de 0 a 1.
- [ ] **Step 2: Shader e render type:** `resonator_crystal` no formato que couber (o `BLOCK` do vanilla tem a normal; a escolha e o empacotamento dos parâmetros ficam no relatório). Opaco, com profundidade, sem blend, luz cheia. Parâmetros por cristal nos vértices: cor do tier, sintonia, vivo/Opaco, progresso da recarga (ou nenhum), semente. Os veios presos ao cristal: o ruído em coordenadas do cristal (faceta, coordenadas da faceta, altura), nunca na posição relativa à câmera. Registro no `ClientSetup` como os outros dois.
- [ ] **Step 3: `ChamberCrystals`:** no modo shader (`CrystalShader.get() != null`, `!ShaderPackCheck.active()`, `!forceLegacyForTests`), desenha a malha no lugar do `renderStatic`, com a mesma posição, giro e escala; no modo antigo, o sprite como hoje. A `CrystalAura` deixa de fazer o enchimento no modo shader (o cristal enche); a aura dos vivos e o anel continuam.
- [ ] **Step 4: Verificar:** build, GameTests e o e2e filtrado; captura nova `ressonador-camara-cristais.png` com a câmara mais perto dos cristais (vivo e Opaco à vista), e as de antes (`ressonador-camara`, `-fluxo`, `-recarga`). Abra e descreva: facetas visíveis, borda acesa, Opaco cinza, a recarga enchendo.
- [ ] **Step 5: Commit:** `Câmara do Ressonador: cristal em 3D facetado`.

**Custo:** 12 triângulos por cristal, até 96 por Núcleo, um render type; nada alocado por quadro; servidor e rede sem mudança.

---

### Task 5: Medida, docs e fechamento

**Files:** `B/client/dev/E2eResonatorSteps.java` (só se a medida precisar incluir o conduto e a recarga), `CLAUDE.md`, `docs/progresso.md`.

- [ ] **Step 1:** a medida do e2e (`camara antiga` × `camara plasma`) roda com o conduto e uma aura ligados; anote os números com o ruído (duas rodadas). Regressão acima do ruído e de ~0,5 ms pede cortar o detalhe nos modos novos pela distância (como o plasma).
- [ ] **Step 2:** `CLAUDE.md`: a linha do `client/resonator/render/` com `RibbonShader`, `FlowConduit`, `RechargeSpiral`, `CrystalAura`, `CrystalShader`, `CrystalMesh` e o modo antigo; a linha do `resonator/` com `PortOrder`, `CoreFlow` e os campos novos da `ChamberView`. `docs/progresso.md`: estado, histórico com as medidas.
- [ ] **Step 3: Commit:** `Câmara do Ressonador: medida e docs da parte 2`.

## Revisão final (partes 1 e 2)

O revisor final (modelo mais capaz) confere o ramo inteiro das duas partes contra as duas specs e as leis da `wa-performance`, e triagem dos menores do registro (`.superpowers/sdd/2026-10-20-ressonador-camara-plasma/progress.md` e o desta parte). Procura em especial: a ordem das Portas igual nos dois lados, extração simulada que soma, a vista que sincroniza sem mudar, alocação no render, e o modo antigo coerente com o shaderpack.
