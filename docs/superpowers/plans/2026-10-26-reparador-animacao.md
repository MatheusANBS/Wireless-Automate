# Reparador de Cristais, etapa 3 (a animação nativa) · plano de implementação

> **Para agentes:** SUB-SKILL OBRIGATÓRIA: use superpowers:subagent-driven-development (recomendado) ou superpowers:executing-plans para executar este plano tarefa por tarefa. Os passos usam caixas (`- [ ]`) para acompanhar.

**Objetivo:** o Reparador de Cristais animado no mundo (BER) e na tela, pela linha do tempo de 7 s aprovada: emissor de plasma, runas e pedestais acesos, raio fractal até o rachado, rachaduras fechando de dentro para fora com o ponto de solda, os 4 cristais cheios em órbita que se dissolvem, convergência, clarão e onda de choque; parado e sem energia; contingência com shaderpack do Iris; tudo 100% nativo (sem Veil).

**Arquitetura:** o servidor só manda ao cliente o que muda pouco: uma `RepairerView` (tipo do rachado, fim e travada de cada linha, duração) no update tag do block entity, só quando ela muda, e um blockstate `lit` (energia no buffer ou linha andando) que troca as luzes do modelo entre acesas e apagadas no próprio chunk. O cliente tira o progresso de `gameTime` pela `RepairTimeline` (pura, JUnit) e desenha num BER sem estado e sem alocação, reaproveitando os core shaders do Ressonador (`resonator_plasma`, `resonator_ribbon` com dois modos novos e `resonator_crystal` com um canal de efeito novo, os três com o padrão antigo inalterado). A tela troca o centro estático pelo do mockup, em pixels da interface.

**Stack:** Java 21, NeoForge 21.1 (Minecraft 1.21.1), GLSL 150 (core shaders do vanilla), JUnit 5, GameTests, Python 3 (gerador do modelo e as referências do mockup), e2e do cliente.

**Spec:** [`docs/superpowers/specs/2026-10-26-reparador-de-cristais-design.md`](../specs/2026-10-26-reparador-de-cristais-design.md), seção Animação (a linha do tempo, a tabela de técnicas, a decisão nativa e o custo). **Mockup aprovado:** `docs/preview/reparador-de-cristais.html` (cores, formas e a câmara da tela). **Modelo:** `docs/preview/reparador-de-cristais.bbmodel`, convertido pelo `scripts/textures/reparador_modelo.py`. **Skills:** `wa-shaders` (método: decompor, e2e só do efeito com o tempo parado, lado a lado, portão do dono), `wa-performance`, `wa-comentarios`, `wa-modular`.

**Pré-requisito:** o trabalho do e2e em suítes que está sem commit no ramo (`E2eScript`, `DevEndToEnd`, `E2eResonatorSteps`, `E2eTiming`, `scripts/e2e.sh`) tem de estar commitado antes da Tarefa 6. Confira `git status` e o `E2eSuites` antes de começar; os trechos citados aqui são do estado de 26/10/2026 e podem ter andado.

## Decisões deste plano (o dono delegou as técnicas; a aparência aprovada no mockup vale)

1. **O dado da animação no cliente.** Hoje o Reparador não tem update tag (o `RepairerStatePayload` só existe com a tela aberta). O cliente precisa, por linha, de: o tipo do rachado (para a malha e a cor da faixa), o tick de fim (o progresso sai de `gameTime`), se está travada (terminou sem saída) e a duração do reparo. Vai numa `RepairerView` no update tag do block entity, montada e mandada só quando muda (um reparo começa ou termina, uma entrada ganha ou perde o rachado), como o `CoreSync` do Núcleo: ~100 bytes por transição, no pior caso ~2,3 pacotes por segundo com as 8 linhas trocando; nada por tick, nada com o bloco parado. A comparação (`matches`) só roda quando algo marcou a view como suja. Descartados: o `RepairerStatePayload` (só com a tela aberta) e qualquer pacote periódico.
2. **Ligado e apagado pelo blockstate `lit`** (`BlockStateProperties.LIT`): aceso com energia no buffer ou com alguma linha andando. As luzes do modelo (`*_glow`, `led_glow`) saem do modelo sólido para dois modelos: o aceso (o atlas como foi pintado, com `neoforge_data` `block_light`/`sky_light` 15, que as deixa com luz cheia) e o apagado (uma textura `repairer_off` gerada pelo script: as mesmas luzes puxadas metade para o cinza e a 30% da luz). Custo: um `setBlock` com `UPDATE_CLIENTS | UPDATE_KNOWN_SHAPE` por transição (sem aviso aos vizinhos) e um remesh da seção no cliente; zero por quadro e visível a qualquer distância. Descartados: quads escuros pelo BER (z-fighting, custo por quadro, sem efeito além de 64 blocos) e repintar o atlas à mão. A tela segue com a regra própria dela ("sem energia para o próximo reparo"); o mundo usa "sem energia nenhuma e nada andando". Sem luz de bloco (`lightEmission`): ela recalcularia a luz a cada transição.
3. **Rachaduras fechando: o `resonator_crystal` com um canal de efeito, não texturas em estágios.** O cristal da câmara é procedural (sem textura), então 8 texturas não teriam onde entrar sem outra malha; e 8 estágios em 4,5 s pulam a cada 0,56 s. O canal é o UV0.y do vértice (um `float` que hoje vai 0): 0 = nada (o Núcleo fica idêntico), 1 a 2 = rachado em reparo com energia (progresso = fx − 1), 3 a 4 = rachado do Reparador sem energia, 5 a 6 = dissolução (fração sumida = fx − 5). A máscara são 6 segmentos (o tronco e os dois galhos do mockup) no plano x-y do cristal; uma rachadura some onde o ponto mais perto dela está a menos de `progresso × 0,5` do centro (de dentro para fora), com o ponto de solda creme na frente. Custo: 6 distâncias a segmento por fragmento, só no cristal em reparo (~1 a 3 mil pixels de perto).
4. **Clarão e onda de choque: modo 3 do `resonator_ribbon`** (um disco de luz: brilho redondo ou anel, pelo parâmetro), num quad virado para a câmera ou deitado no chão. Descartado o sprite do atlas: resolução fixa e a espessura do anel cresceria com o raio. As runas e os pedestais usam o mesmo disco, deitado sobre cada peça.
5. **Raio: modo 1 novo do `resonator_ribbon`**, o modo 0 aprovado com a diferença de que os 4 bits da sintonia viram a semente da linha (senão os 8 raios do mesmo quadro teriam o mesmo desenho) e a sintonia fica inteira. O modo 0 do Ressonador não muda.
6. **Emissor: o `PlasmaCore` reaproveitado**, meio quad de 6,9 px (a esfera do shader fica com ~1,6 px de raio, a largura do `emitter_glow`), pendurado em (8; 11; 8) px, com um canal novo de apagamento no UV2.x (milésimos; 0 = luz inteira, então o Núcleo fica igual).
7. **Cristais em órbita:** 4 por linha que trabalha, num anel só em volta do emissor (raio 3,2 px, a 10,6 px de altura), intercalados por linha (ângulo `2π(k/4 + linha/32)`), malha do aglomerado a 1,1 px por unidade; só a menos de 24 blocos. A dissolução é pelo canal de efeito (limiar de ruído preso ao cristal, com a borda quente). Na contingência, escalam até 0 (a alternativa do spec).
8. **Geometria em pixels do modelo** (o BER escala o `PoseStack` por 1/16) e **o `facing` aplicado nos pontos** (tabelas por quarto de volta, `RepairerGeometry`), não no `PoseStack`: assim fitas, billboards e o plasma usam os eixos da câmera do mundo sem conversão. A linha i é o pedestal `ped_i` do modelo (a 22,5° + 45°·i, raio 4 px).
9. **Tempo:** as fases do spec são de um reparo de 7 s; com outro `repairTicks` elas escalam juntas (`s = 7 × fração andada`). O "reparado gira e sai" é: entre 6,7 e 7,0 s o cristal gira duas voltas, sobe 1,5 px e encolhe a 0; a saída de verdade é o servidor movendo o item no fim.
10. **Tela:** segue o mockup aprovado (núcleo de forja pulsando, filetes com 12 partículas por linha, losangos que vão do osso à cor do tipo enquanto as rachaduras somem e a solda corre o tronco, cristais do estoque em órbita, clarão de 0,8 s quando uma linha termina, véu sem energia), desenhado com `fill` por linha de pixels, sem textura; o mundo segue a linha do tempo do spec. As cores da câmara da tela são as do mockup, iguais nos dois temas (como as do osciloscópio do Núcleo), em constantes `GuiPaint.CHAMBER_*`.
11. **Reaproveitar sem mover:** as classes do render do Ressonador usadas aqui ficam em `client/resonator/render/` e ganham visibilidade pública (lista exata na Tarefa 4). Mover para um pacote comum mexeria em dezenas de imports do Ressonador sem ganho.
12. **Referências lado a lado:** o mockup ganha um modo de quadro (`#quadro=<progresso>&energia=<0|1>&parado=<0|1>`) que só esconde o resto da página e congela o relógio, sem mudar o desenho; o `scripts/preview/quadros_reparador.py` tira as referências por fase com o Edge (ou Chrome) sem janela; o `lado_a_lado.py` da `wa-shaders` passa a aceitar uma pasta de PNGs como referência.
13. **e2e:** a suíte `reparador-animacao` entra no conjunto padrão (as capturas só saem pelo `WA_E2E_CAPTURE`; os asserts de contagem e de shader sempre rodam); a `reparador-desempenho` (tempo de quadro) é opcional. O tempo é congelado pelo gancho `RepairClock.freezeForTests`; para as linhas não terminarem durante a suíte, o `repairTicks` vai a 1.000.000 e volta no fim.
14. **Portão do dono (`wa-shaders`, passo 6):** as tarefas visuais (5 e 6 juntas, 7 a 13) terminam com as capturas lado a lado mostradas ao dono, e só commitam depois do "aprovado"; uma tarefa reprovada volta ao passo de ajuste (e, se as rodadas pararem de reduzir a diferença, à escolha da técnica). As tarefas 1 a 4 e 14 commitam ao fim, como de costume.

## Restrições globais

- Código, comentários, commits e docs em português; textos nos dois `lang` (`en_us.json`, `pt_br.json`), na mesma ordem.
- **Nada de payload novo:** `ModPayloads.VERSION` continua `"33"`. O update tag é o do vanilla (`ClientboundBlockEntityDataPacket`).
- **Chaves existentes não mudam** (NBT `items`, `energy`, `lines`; config `repairer.*`). Novas: blockstate `lit` do `crystal_repairer`; update tag `kinds` (byte[8]), `ends` (long[8]), `blocked` (byte, um bit por linha), `duration` (int), fora do NBT salvo; lang `command.wirelessautomate.profile.repairer`; textura `block/repairer/repairer_off`; modelos `block/repairer/crystal_repairer_glow` e `crystal_repairer_glow_off`.
- **Performance (skill `wa-performance`; leia `.claude/skills/wa-performance/SKILL.md`):** parado custa zero (servidor: nada novo por tick; cliente: o BER sem luz e sem cristal sai na primeira linha, e parado aceso desenha 1 quad mais os rachados que esperam); por quadro só desenhar: nada de `new`, lambda, `String`, `values()` de enum ou `List.of` em `render` (rascunhos estáticos, uma `RepairFrame` reusada); os tipos de desenho agrupados por chamada (cristais, depois o plasma, depois todas as fitas), porque trocar de `RenderType` no `BufferSource` fecha o lote; `setChanged` só nas transições; `setBlock` do `lit` só quando o valor muda. Rode `python -I .claude/skills/wa-performance/scripts/varrer_perf.py` nos arquivos mudados e resolva ou justifique cada candidato.
- **Shaders (`.claude/skills/wa-shaders/references/armadilhas.md`):** `#version 150`; UV2 é `short` (< 32768: os parâmetros novos cabem em 15 bits); nada de `packed`, `input`, `output`, `sample`, `filter` como nome; nome repetido na mesma função derruba o shader; o tempo do Java é `(gameTime % 24000 + partialTick) / 20`, o do shader `GameTime × 1200`. Depois de cada e2e, `grep -c "Couldn't compile\|não compilou" run/logs/latest.log` tem de dar 0. O padrão antigo de cada canal (UV0.y = 0, UV2.x = 0 no plasma, modos 0 e 2 da fita) desenha exatamente como hoje: a câmara do Ressonador não muda (conferido na Tarefa 4).
- Lógica pura com JUnit (`src/test/java`); o que depende do jogo, com GameTest (`@GameTestHolder(WirelessAutomate.MODID)`, `@PrefixGameTestTemplate(false)`, `template = "empty"`, `timeoutTicks` ≤ 300; o `onLoad` só roda no tick seguinte; testes do mesmo lote rodam em paralelo: confira pertinência, não contagens globais).
- Lado do cliente: tudo de render e tela em `client/` (`client/repairer/` e `client/repairer/render/`), nunca referenciado por código comum.
- Tamanho (`wa-modular`): tela até 500 linhas, servidor dividir acima de 1000; `python -I .claude/skills/wa-modular/scripts/medir.py` nos arquivos mudados. Comentários (`wa-comentarios`): `python -I .claude/skills/wa-comentarios/scripts/varrer.py` nos arquivos mudados; comentário no presente, sem histórico.
- Texturas e modelos: edite `scripts/textures/reparador_modelo.py`, nunca os JSON ou PNG gerados; o `gerar_texturas.py` reescreve dezenas de PNGs nesta máquina: rode só o `reparador_modelo.py` e commite só o que ele gera.
- e2e no Windows: escreva o comando num script do scratchpad e rode-o; o cliente roda em `run/`, então `WA_E2E=run/e2e-reparador` grava em `run/run/e2e-reparador`. Um jogo, Gradle ou bench por vez (~3 GB cada). Não mexa em `run/options.txt`.
- Antes de cada commit de código: `./gradlew build runGameTestServer runGameTestServerChemicals runGameTestServerSource runGameTestServerAllthemodium runGameTestServerTeams` (Git Bash, na raiz). Com `BUILD FAILED`, pare e conserte.
- Commits sem linhas de atribuição (nada de `Co-Authored-By` nem `Claude-Session`), com o autor do dono; `git add` só dos arquivos da tarefa (nunca `run/`). Não faça push.

## Mapa de arquivos

`B/` = `src/main/java/io/github/matheusanbs/wirelessautomate/`, `T/` = `src/test/java/io/github/matheusanbs/wirelessautomate/`, `R/` = `src/main/resources/assets/wirelessautomate/`.

| Arquivo | Papel | Tarefa |
| --- | --- | --- |
| `B/repairer/RepairerView.java` (novo) | O que o cliente anima: tipo, fim, travada, duração | 1 |
| `B/repairer/RepairerSync.java` (novo) | Manda a view só quando muda; guarda a recebida | 1 |
| `B/repairer/RepairerBlock.java`, `RepairerBlockEntity.java` (mudar) | `lit`, update tag | 1 |
| `B/gametest/RepairerSyncGameTests.java` (novo) | `lit`, view, update tag, perfil | 1, 14 |
| `scripts/textures/reparador_modelo.py` (mudar) | Luzes acesas e apagadas, blockstate com `lit` | 2 |
| `B/client/repairer/render/RepairTimeline.java`, `RepairerGeometry.java`, `RepairClock.java` (novos, puros) | Fases, posições, gancho do tempo | 3 |
| `T/client/repairer/render/RepairTimelineTest.java`, `RepairerGeometryTest.java` (novos) | JUnit | 3 |
| `B/client/resonator/render/*` (visibilidade), `RibbonParam.java` (novo, puro), `PlasmaCore`, `CrystalGem`, `CrystalParam`, `RibbonQuad` (mudar) | API comum | 4 |
| `T/client/resonator/render/RibbonParamTest.java`, `CrystalParamFxTest.java` (novos) | JUnit | 4 |
| `R/shaders/core/resonator_plasma.fsh` (mudar) | Apagamento pelo UV2.x | 4 |
| `B/client/repairer/render/RepairerRenderer.java`, `RepairFrame.java`, `RepairerCrystals.java`, `RepairerLights.java` (novos), `B/client/ClientSetup.java` (mudar) | O BER | 5, 7, 9, 10, 11 |
| `B/client/dev/E2eRepairerAnimSteps.java` (novo), `E2eSuites.java`, `E2eRepairerSteps.java`, `E2eResonatorSteps.java` (mudar) | e2e do efeito | 6, 7–13 |
| `docs/preview/reparador-de-cristais.html` (mudar, aditivo), `scripts/preview/quadros_reparador.py` (novo), `.claude/skills/wa-shaders/scripts/lado_a_lado.py` (mudar) | Referências lado a lado | 6 |
| `R/shaders/core/resonator_ribbon.fsh` (mudar) | Modo 3 (disco), modo 1 (raio com semente) | 7, 8 |
| `B/client/repairer/render/RepairerBeams.java` (novo) | O raio | 8 |
| `R/shaders/core/resonator_crystal.vsh`, `.fsh` (mudar) | Canal de efeito: rachaduras e dissolução | 9, 10 |
| `B/client/repairer/render/RepairerLegacy.java` (novo), `ChamberLens`, `LissajousTrace` (visibilidade) | Contingência | 12 |
| `B/client/repairer/ChamberMotion.java` (novo, puro), `ChamberKite.java` (novo), `RepairerChamberPanel.java`, `RepairerScreen.java`, `B/client/GuiPaint.java` (mudar) | Tela animada | 13 |
| `T/client/repairer/ChamberMotionTest.java` (novo) | JUnit | 13 |
| `B/repairer/RepairerManager.java`, `B/command/WaCommand.java`, os dois `lang` (mudar) | `/wa profile` | 14 |
| `CLAUDE.md`, `docs/progresso.md`, a spec, `docs/benchmark.md` (mudar) | Docs e medidas | 14 |

## Ordem e paralelismo

- **Onda A (independentes entre si):** Tarefa 1 (servidor), Tarefa 3 (lógica pura), Tarefa 4 (API comum do render e o plasma). A parte de código da Tarefa 13 (tela) depende só da 3 e pode correr junto das tarefas 5 a 12; o passo de e2e e o portão dela vêm depois da 6.
- **Depois da 1:** Tarefa 2 (o blockstate do modelo usa a propriedade `lit`).
- **Depois de 1, 3 e 4:** Tarefa 5 (BER); **depois da 5 e da 2:** Tarefa 6 (e2e e referências; o portão da 5 é aqui, e as duas commitam juntas).
- **Sequenciais, nesta ordem (mexem nos mesmos arquivos: o BER, os shaders e a tabela do e2e):** 7 → 8 → 9 → 10 → 11 → 12.
- **Por último:** Tarefa 14 (perfil, medidas e docs).

---

### Tarefa 1: o dado da animação no servidor (`RepairerView`, update tag e `lit`)

**Arquivos:**
- Criar: `B/repairer/RepairerView.java`, `B/repairer/RepairerSync.java`, `B/gametest/RepairerSyncGameTests.java`
- Mudar: `B/repairer/RepairerBlock.java`, `B/repairer/RepairerBlockEntity.java`

**Interfaces (produz):**
- `RepairerView` (público, imutável): constantes `NONE = 0`, `SIGNAL = 1`, `CUT = 2`, `TUNED = 3` (o Afinado do tier de posição t é `TUNED + t`), `EMPTY`; `static byte kindOf(ItemStack)` (só rachados, pelo `RepairerSlots.acceptsCracked`, `SignalCrystalItem.tunedTier()` e `isCut()`); `static RepairerView of(RepairerBlockEntity)`; `boolean matches(RepairerBlockEntity)` (compara campo a campo sem montar outra); `void save(CompoundTag)` (chaves `kinds`, `ends`, `blocked`, `duration`); `static @Nullable RepairerView load(CompoundTag)` (null sem `kinds` ou com tamanho errado); leitores `byte kind(int)`, `long end(int)`, `boolean blocked(int)`, `int duration()`, `boolean running(int)` (`end > 0 && !blocked`), `boolean empty()`; `equals`/`hashCode`. A duração é `(int) min(Integer.MAX_VALUE, Config.repairTicks())`.
- `RepairerSync` (pacote): `markDirty()`, `boolean sendIfChanged(RepairerBlockEntity)` (só com `dirty`; monta a view só se `!matches`; `level.sendBlockUpdated(pos, state, state, Block.UPDATE_CLIENTS)`; conta `syncCount`), `CompoundTag tag(RepairerBlockEntity)` (a view de agora; guarda como mandada se nada foi mandado ainda), `receive(CompoundTag)` (troca a view do cliente só se `load` não for null), `RepairerView clientView()`, `int syncCount()`.
- `RepairerBlock.LIT = BlockStateProperties.LIT` (padrão `false`, no `createBlockStateDefinition`).
- `RepairerBlockEntity`: `RepairerView clientView()`, `int syncCount()`, `getUpdateTag`/`getUpdatePacket` (`ClientboundBlockEntityDataPacket.create(this)`)/`handleUpdateTag` (só `sync.receive`)/`onDataPacket` (só `handleUpdateTag`, nunca o `loadWithComponents`, que zeraria os slots), como no `ResonanceCoreBlockEntity`.

**Regras no block entity:**
- `itemsChanged()` marca a view suja antes do `wake()`; `tick(now)` marca suja quando `changed` e, no fim, chama `updateLit()` e `sync.sendIfChanged(this)`.
- `updateLit()`: `lit = energy.stored() > 0 || anyRunning()`; se difere do estado, `level.setBlock(pos, state.setValue(LIT, lit), Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE)`. Chamado também no `markUnsaved()` (o aviso do `ScalarStore` a cada mudança de energia). Nunca no cliente, nunca removido, e **nunca durante o `onLoad`** (uma flag `loading` em volta do `wake()` do `onLoad` pula `updateLit` e `sendIfChanged`: mexer em bloco na carga do chunk é proibido, e o `lit` já vem salvo no chunk; o update tag da carga leva a view).
- O `RepairerBlock.onRemove` já compara o bloco (`!state.is(newState.getBlock())`), então trocar o `lit` não larga nada: confira.

**Custo:** servidor: o `sendIfChanged` sem sujeira é um `if`; com sujeira, 8 leituras de slot; o `updateLit` no aviso de energia é `getBlockState` (cache do block entity) e um laço de 8. Rede: ~100 bytes por início ou fim de reparo. Cliente: nada.

- [ ] **Passo 1: GameTests que falham** (`RepairerSyncGameTests`, usando `RepairerGameTests.repairer(helper, body)`, `RepairerGameTests.cracked(boolean)` e `ResonatorTestSupport.crystal(false, CrystalCharge.FULL)` para o estoque):
  - `acendeComEnergiaEApagaSemEla`: nasce com `lit` falso; `bulkEnergyView().insert(100, false)` acende; `energy().extract(100, false)` apaga.
  - `continuaAcesoComLinhaAndandoSemEnergia`: rachado + 4 cheios + exatamente o custo: a linha anda, buffer 0, `lit` verdadeiro; `tick(gameTime + repairTicks)` termina e apaga.
  - `viewSoMudaNoInicioENoFim`: com rachado e estoque postos direto nos slots, o `insert` do custo começa a linha e soma 1 ao `syncCount`; `RepairerView.of` tem o tipo `CUT` e o fim da linha; mais energia e um `itemsChanged()` só do estoque não somam; o `tick` do fim soma 1 e a view volta a `NONE` e fim 0.
  - `updateTagNaoApagaOsSlots`: `handleUpdateTag(getUpdateTag(...))` no próprio block entity mantém o slot 0 e dá `clientView().kind(0) == SIGNAL`; o tag completo do disco (`saveWithoutMetadata`) não troca a view.
  - `tipoDeCadaRachado`: `kindOf` dá 1, 2 e `TUNED + RouterTier.ELITE.ordinal()` para Sinal, Lapidado e Afinado Elite rachados (o Afinado por `ModItems.TUNED_CRYSTALS.get(RouterTier.ELITE).get()` com `setCharge(0)` e `setIntegrity(0)`), e 0 para cheio e vazio.
- [ ] **Passo 2:** `./gradlew compileJava` falha (as classes não existem).
- [ ] **Passo 3:** implemente `RepairerView`, `RepairerSync`, o `LIT` e as mudanças do block entity.
- [ ] **Passo 4:** `./gradlew build runGameTestServer` passa (os GameTests do Reparador da etapa 2 também).
- [ ] **Passo 5:** varreduras (`varrer_perf.py`, `varrer.py`, `medir.py`) nos arquivos mudados; a suíte inteira de GameTests; commit `Reparador: a view da animação no update tag e o blockstate lit`.

**Aceite:** os 5 GameTests passam; nenhum pacote sai sem mudança da view; `lit` segue a energia e as linhas; o `onLoad` não chama `setBlock`.

---

### Tarefa 2: as luzes do modelo acesas e apagadas

**Arquivos:** mudar `scripts/textures/reparador_modelo.py`; gerados: `R/models/block/repairer/crystal_repairer.json` (sem as luzes), `crystal_repairer_glow.json`, `crystal_repairer_glow_off.json`, `R/textures/block/repairer/repairer_off.png`, `R/blockstates/crystal_repairer.json`, `R/models/item/crystal_repairer.json`.

**Contrato:**
- As luzes são os elementos de `_sem_sombra(nome)` (`*_glow`, `*_glow_*`, `led_glow`). Saem do modelo sólido; o total continua `ELEMENTOS_ESPERADOS` (sólidos + vidro + luzes) e as luzes também passam pela checagem de alfa opaco.
- `crystal_repairer_glow.json`: as luzes com `"shade": false` e `"neoforge_data": {"block_light": 15, "sky_light": 15}` em cada elemento (é o `ExtraFaceData` do NeoForge 21.1, lido pelo `BlockElement`; confira o nome da chave no `BlockElement.Deserializer` decompilado; se só valer por face, ponha em cada face), textura `repairer`.
- `crystal_repairer_glow_off.json`: as mesmas luzes, sem `shade: false` e sem `neoforge_data`, com a textura `block/repairer/repairer_off` na chave `repairer` (o `_modelo` ganha o parâmetro da textura).
- `repairer_off.png`: o atlas com os retângulos das faces das luzes apagados (cada canal: `(c + (cinza − c) × 0,5) × 0,30`, cinza = `0,3r + 0,59g + 0,11b`, alfa igual) e o `com_folga` aplicado, como o aceso.
- Blockstate `multipart`, por `facing`: sólido e vidro sempre; `glow` com `"lit": "true"`; `glow_off` com `"lit": "false"`. O item composto ganha o filho `brilho` (aceso) entre o sólido e o vidro.
- `gerar()` devolve `{TEXTURA, TEXTURA_OFF}` (o `limpar` do `gerar_texturas.py` preserva as duas).

- [ ] **Passo 1:** implemente; rode `python scripts/textures/reparador_modelo.py` (só ele) e confira os seis arquivos gerados e que nada mais mudou no `git status`.
- [ ] **Passo 2:** `./gradlew build runGameTestServer`; no próximo cliente (o e2e da Tarefa 6), `grep -c "Exception loading blockstate\|Unable to load model\|missing model" run/logs/latest.log` dá 0.
- [ ] **Passo 3:** commit `Reparador: luzes do modelo acesas e apagadas pelo lit` (o portão visual do apagado fica com o da Tarefa 6).

**Aceite:** com `lit` falso as luzes ficam escuras; com verdadeiro, como o atlas aprovado, com luz cheia à noite; o item aparece aceso.

---

### Tarefa 3: a linha do tempo, a geometria e o relógio (lógica pura)

**Arquivos:** criar `B/client/repairer/render/RepairTimeline.java`, `RepairerGeometry.java`, `RepairClock.java`, `T/client/repairer/render/RepairTimelineTest.java`, `RepairerGeometryTest.java`. Nenhuma classe do Minecraft.

**Interfaces (produz):** as abaixo, públicas; os segundos `s` são da referência de 7 s.

- [ ] **Passo 1: o código** (`RepairTimeline`):

```java
package io.github.matheusanbs.wirelessautomate.client.repairer.render;

/**
 * A linha do tempo de um reparo, em segundos da referência de 7 s (a do spec e do mockup aprovado); com outro
 * repairTicks as fases escalam juntas (s = 7 × fração andada). Cada envelope vai de 0 a 1 (ou diz a unidade no nome)
 * e é contínuo nas bordas das fases. Lógica pura, com JUnit.
 */
public final class RepairTimeline {
    public static final double TOTAL = 7.0;
    public static final double PULL_END = 0.5;
    public static final double BEAM_START = 1.0;
    public static final double CONVERGE = 5.5;
    public static final double FLASH = 6.3;
    /** O emissor parado e o pico da convergência, na escala em que 1 é o emissor trabalhando. */
    public static final double IDLE = 0.30;
    public static final double PEAK = 1.3;
    /** As runas enquanto há linha andando, fora da onda. */
    public static final double RUNES_WORKING = 0.35;

    private RepairTimeline() {
    }

    /** Os segundos da linha que termina em {@code end}, com {@code duration} ticks, no instante {@code now}. */
    public static double seconds(long end, int duration, double now) {
        if (duration <= 0) {
            return TOTAL;
        }
        return TOTAL * clamp(1 - (end - now) / duration);
    }

    public static double clamp(double x) {
        return x < 0 ? 0 : x > 1 ? 1 : x;
    }

    public static double smooth(double a, double b, double x) {
        double t = clamp((x - a) / (b - a));
        return t * t * (3 - 2 * t);
    }

    /** O brilho do emissor: do parado ao cheio na puxada, sobe na convergência e volta ao parado no fim. */
    public static double emitter(double s) {
        if (s < CONVERGE) {
            return IDLE + (1 - IDLE) * smooth(0, PULL_END, s);
        }
        if (s < FLASH) {
            return 1 + (PEAK - 1) * smooth(CONVERGE, FLASH, s);
        }
        return PEAK + (IDLE - PEAK) * smooth(FLASH, TOTAL, s);
    }

    /** O emissor sem linha andando, pulsando devagar; {@code t} em segundos do relógio do mundo. */
    public static double idleEmitter(double t) {
        return IDLE + 0.08 * Math.sin(t * 1.1);
    }

    /** A onda de luz pelas runas na puxada; {@code angle} é a distância da runa ao pedestal da linha (0 a π). */
    public static double runeWave(double s, double angle) {
        if (s <= 0 || s >= 0.7) {
            return 0;
        }
        double d = (angle - Math.PI * smooth(0, PULL_END, s)) / 0.45;
        return Math.exp(-d * d) * (1 - smooth(PULL_END, 0.7, s));
    }

    /** O pedestal da linha: acende no fim da puxada e apaga depois do clarão. */
    public static double pedestal(double s) {
        return s < FLASH ? smooth(0.3, PULL_END, s) : 1 - smooth(FLASH, TOTAL, s);
    }

    /** O cristal {@code k} (0 a 3) em órbita aparecendo, um depois do outro, entre 0,3 e 1,2 s. */
    public static double orbitAppear(double s, int k) {
        double a = 0.3 + 0.15 * k;
        return smooth(a, a + 0.3, s);
    }

    /** Quanto do cristal {@code k} em órbita já se dissolveu, de 5,5 a 6,3 s, um depois do outro. */
    public static double dissolve(double s, int k) {
        double a = CONVERGE + 0.1 * k;
        return smooth(a, a + 0.5, s);
    }

    /** O ângulo da órbita, em radianos: 1,4 rad/s, acelerando na convergência. */
    public static double orbitAngle(double s) {
        double extra = s > CONVERGE ? 3.0 * (s - CONVERGE) * (s - CONVERGE) : 0;
        return 1.4 * s + extra;
    }

    /** O raio da órbita sobre o de base: encolhe 35% na convergência. */
    public static double orbitShrink(double s) {
        return 1 - 0.35 * smooth(CONVERGE, FLASH, s);
    }

    /** A força do raio: acende de 1,0 a 1,3 s e corta no clarão (6,3 a 6,45 s). */
    public static double beam(double s) {
        return s < FLASH ? smooth(BEAM_START, 1.3, s) : 1 - smooth(FLASH, 6.45, s);
    }

    /** A faixa do raio no shader (1 a 7): 4 no reparo, engrossando até 7 na convergência. */
    public static int beamBand(double s) {
        return 4 + (int) Math.round(3 * smooth(CONVERGE, 6.0, s));
    }

    /** A largura do raio sobre a de base: de 1 a 1,7 na convergência. */
    public static double beamWidth(double s) {
        return 1 + 0.7 * smooth(CONVERGE, FLASH, s);
    }

    /** Quanto as rachaduras já fecharam, de dentro para fora: 0 até 1,0 s, 1 em 5,5 s. */
    public static double cracks(double s) {
        return clamp((s - BEAM_START) / (CONVERGE - BEAM_START));
    }

    /** A amplitude da vibração do cristal, em pixels do modelo: sobe na convergência e para no clarão. */
    public static double vibration(double s) {
        return s < CONVERGE || s >= FLASH ? 0 : 0.25 * smooth(CONVERGE, FLASH, s);
    }

    /** O clarão do próprio cristal (o alfa da cor no shader do cristal): sobe na convergência, pico em 6,4 s, some em 7 s. */
    public static double crystalGlow(double s) {
        if (s < CONVERGE) {
            return 0;
        }
        if (s < FLASH) {
            return 0.6 * smooth(CONVERGE, FLASH, s);
        }
        if (s < 6.4) {
            return 0.6 + 0.4 * smooth(FLASH, 6.4, s);
        }
        return 1 - smooth(6.4, TOTAL, s);
    }

    /** O clarão em volta do cristal, de 6,3 a 7 s: estoura e cai ao quadrado. */
    public static double flash(double s) {
        if (s < FLASH) {
            return 0;
        }
        double f = clamp(1 - (s - FLASH) / (TOTAL - FLASH));
        return f * f;
    }

    /** O tamanho do clarão em pixels do modelo: de 2 a 6. */
    public static double flashSize(double s) {
        return 2 + 4 * clamp((s - FLASH) / (TOTAL - FLASH));
    }

    /** O raio da onda de choque no chão em volta do pedestal, em pixels do modelo: de 0,6 a 3 (ease-out). */
    public static double shockRadius(double s) {
        double u = clamp((s - FLASH) / (TOTAL - FLASH));
        return 0.6 + 2.4 * (1 - (1 - u) * (1 - u));
    }

    public static double shockAlpha(double s) {
        if (s < FLASH || s >= TOTAL) {
            return 0;
        }
        return Math.pow(1 - (s - FLASH) / (TOTAL - FLASH), 1.5);
    }

    /** A saída do reparado: 0 até 6,7 s e 1 em 7 s (gira duas voltas, sobe 1,5 px e encolhe a 0). */
    public static double exit(double s) {
        return smooth(6.7, TOTAL, s);
    }
}
```

- [ ] **Passo 2: o código** (`RepairClock` e `RepairerGeometry`):

```java
package io.github.matheusanbs.wirelessautomate.client.repairer.render;

/** O tempo da animação de cada linha, com o gancho do e2e que o congela numa fase. Puro. */
public final class RepairClock {
    /** Só do e2e: o segundo (0 a 7) em que todas as linhas que andam ficam paradas, ou −1. */
    public static double freezeForTests = -1;

    private RepairClock() {
    }

    public static boolean frozen() {
        return freezeForTests >= 0;
    }

    public static double seconds(long end, int duration, long gameTime, float partialTick) {
        return frozen() ? freezeForTests : RepairTimeline.seconds(end, duration, gameTime + (double) partialTick);
    }
}
```

```java
package io.github.matheusanbs.wirelessautomate.client.repairer.render;

/**
 * Onde ficam as peças do Reparador, em pixels do modelo (0 a 16), lidas do .bbmodel aprovado (o modelo olha para o
 * norte). O facing gira os pontos em quartos de volta no sentido horário visto de cima, em torno de (8, 8), como o
 * "y" do blockstate; as tabelas por quarto são montadas uma vez. A linha i é o pedestal ped_i, a 22,5° + 45°·i; a runa
 * r fica a 45°·r e o ponto d a 22,5° + 45°·d (ângulos de atan2(z − 8, x − 8)). Puro, com JUnit.
 */
public final class RepairerGeometry {
    public static final float CENTER = 8f;
    public static final float EMITTER_Y = 11f;
    public static final float EMITTER_RADIUS = 1.6f;
    public static final float PEDESTAL_TOP_Y = 7.2f;
    public static final float RUNE_Y = 6.62f;
    public static final float DOT_Y = 6.64f;
    public static final float PEDESTAL_GLOW_Y = 7.22f;
    public static final float FLOOR_Y = 6.70f;
    public static final float ORBIT_Y = 10.6f;
    public static final float ORBIT_RADIUS = 3.2f;
    private static final float[] PEDESTALS = {11.69552f, 9.53073f, 9.53073f, 11.69552f, 6.46927f, 11.69552f,
        4.30448f, 9.53073f, 4.30448f, 6.46927f, 6.46927f, 4.30448f, 9.53073f, 4.30448f, 11.69552f, 6.46927f};
    private static final float[] RUNES = {13.2f, 8f, 11.67696f, 11.67696f, 8f, 13.2f, 4.32304f, 11.67696f, 2.8f, 8f,
        4.32304f, 4.32304f, 8f, 2.8f, 11.67696f, 4.32304f};
    private static final float[] DOTS = {12.89656f, 10.02822f, 10.02822f, 12.89656f, 5.97178f, 12.89656f, 3.10344f,
        10.02822f, 3.10344f, 5.97178f, 5.97178f, 3.10344f, 10.02822f, 3.10344f, 12.89656f, 5.97178f};
    private static final float[][] PED_BY_QUARTER = byQuarter(PEDESTALS);
    private static final float[][] RUNE_BY_QUARTER = byQuarter(RUNES);
    private static final float[][] DOT_BY_QUARTER = byQuarter(DOTS);

    private RepairerGeometry() {
    }

    /** O quarto de volta pelo {@code Direction.get2DDataValue()} (sul 0, oeste 1, norte 2, leste 3): norte 0, leste 1, sul 2, oeste 3. */
    public static int quarterFrom2D(int data2d) {
        return (data2d + 2) & 3;
    }

    /** Gira (x, z) {@code quarter} quartos de volta em torno do centro; escreve em {@code out}. */
    public static void rotate(float x, float z, int quarter, float[] out) {
        float dx = x - CENTER;
        float dz = z - CENTER;
        for (int q = 0; q < (quarter & 3); q++) {
            float t = dx;
            dx = -dz;
            dz = t;
        }
        out[0] = CENTER + dx;
        out[1] = CENTER + dz;
    }

    private static float[][] byQuarter(float[] north) {
        float[][] table = new float[4][north.length];
        float[] p = new float[2];
        for (int q = 0; q < 4; q++) {
            for (int i = 0; i < north.length; i += 2) {
                rotate(north[i], north[i + 1], q, p);
                table[q][i] = p[0];
                table[q][i + 1] = p[1];
            }
        }
        return table;
    }

    public static float pedestalX(int quarter, int line) { return PED_BY_QUARTER[quarter & 3][2 * line]; }
    public static float pedestalZ(int quarter, int line) { return PED_BY_QUARTER[quarter & 3][2 * line + 1]; }
    public static float runeX(int quarter, int rune) { return RUNE_BY_QUARTER[quarter & 3][2 * rune]; }
    public static float runeZ(int quarter, int rune) { return RUNE_BY_QUARTER[quarter & 3][2 * rune + 1]; }
    public static float dotX(int quarter, int dot) { return DOT_BY_QUARTER[quarter & 3][2 * dot]; }
    public static float dotZ(int quarter, int dot) { return DOT_BY_QUARTER[quarter & 3][2 * dot + 1]; }

    /** A distância angular (0 a π) da runa {@code rune} ao pedestal da linha {@code line}. */
    public static double runeDistance(int rune, int line) {
        return angle(45.0 * rune, 22.5 + 45.0 * line);
    }

    /** A distância angular (0 a π) do ponto {@code dot} ao pedestal da linha {@code line}. */
    public static double dotDistance(int dot, int line) {
        return angle(22.5 + 45.0 * dot, 22.5 + 45.0 * line);
    }

    private static double angle(double a, double b) {
        double d = Math.abs(a - b) % 360;
        return Math.toRadians(d > 180 ? 360 - d : d);
    }
}
```

(Formate os leitores de uma linha no estilo do projeto, um método por bloco.)

- [ ] **Passo 3: os testes** (`RepairTimelineTest`, `RepairerGeometryTest`), escritos antes de rodar:
  - `seconds`: fim 1140, duração 140: em 1000 dá 0, em 1070 dá 3,5, em 1140 e 2000 dá 7, em 900 dá 0; duração 0 dá 7.
  - Bordas: `beam(0,99) == 0`, `beam(3) == 1`, `beam(6,45) == 0`; `cracks(1) == 0`, `cracks(5,5) == 1`; `dissolve(6,3, k) == 1` para k de 0 a 3 e `dissolve(5,5, k) == 0`; `orbitAppear(0,3, 0) == 0` e `orbitAppear(1,2, k) == 1`; `flash(6,29) == 0`, `flash(6,3) == 1`, `flash(7) == 0`; `crystalGlow(5,5) == 0`, `crystalGlow(6,4) == 1`, `crystalGlow(7) == 0`; `beamBand(3) == 4`, `beamBand(6,1) == 7`; `vibration(6,3) == 0`; `exit(6,7) == 0`, `exit(7) == 1`; `shockRadius` crescente de 6,3 a 7 e `shockAlpha(7) == 0`.
  - Continuidade: para cada função, `|f(b − 1e−6) − f(b + 1e−6)| < 1e−3` nas bordas 0,5, 1,0, 5,5, 6,3, 6,4 e 6,45 (a `beam` em 6,3 inclusive).
  - Faixa: todos os envelopes em [0, 1] (o `emitter` em [0,3; 1,3]) numa varredura de 0 a 7 em passos de 0,01.
  - Geometria: quarto 0 devolve as tabelas; `pedestalX/Z(1, 0)` = (6,46927; 11,69552) (o `ped_2` do norte); quatro quartos voltam ao original; `quarterFrom2D(2) == 0`, `(3) == 1`, `(0) == 2`, `(1) == 3`; `runeDistance(0, 0)` = 22,5° e `(4, 0)` = 157,5° em radianos; `dotDistance(3, 3) == 0`.
- [ ] **Passo 4:** `./gradlew test --tests "*RepairTimelineTest" --tests "*RepairerGeometryTest"` passa.
- [ ] **Passo 5:** varredura de comentários; commit `Reparador: linha do tempo, geometria e relógio da animação (puros)`.

**Custo:** nenhum em execução até o BER usar; funções sem alocação.

---

### Tarefa 4: a API comum do render (Ressonador) e o apagamento do plasma

**Arquivos:** mudar em `B/client/resonator/render/`: `ChamberRenderTypes`, `RibbonQuad`, `PlasmaCore`, `CrystalGem`, `CrystalParam`, `CrystalMesh`, `ShaderPackCheck`; criar `RibbonParam.java` (puro); mudar `R/shaders/core/resonator_plasma.fsh`; criar `T/client/resonator/render/RibbonParamTest.java`, `CrystalParamFxTest.java`.

**Interfaces (produz):**
- Públicos (classe e membros citados): `ChamberRenderTypes.ribbon()`, `crystal()`, `plasma()`, `glow()`; `CrystalMesh.BOTTOM`, `TOP`; `CrystalParam.LIVE`, `CRACKED`, `pack(...)`; `ShaderPackCheck.active()`; `CrystalShader`, `PlasmaShader`, `RibbonShader` já são.
- `RibbonParam` (puro): `MODE_BOLT = 0`, `MODE_REPAIR_BOLT = 1`, `MODE_TENDRIL = 2`, `MODE_DISC = 3`; `static int bolt(int band, int seed, boolean full, double aspectLength)` = `clamp(band,0,7) | (seed & 15) << 3 | (full ? 128 : 0) | clamp(round(aspectLength × 10), 1, 127) << 8` (o formato do modo 0: o shader lê o "comprimento" como comprimento / 0,35 de meia largura, então quem chama passa `0,7 × comprimento / largura`); `static int disc(double radius, boolean ring, double width)` = `clamp(round(radius × 127)) | (ring ? 128 : 0) | clamp(round(width × 127)) << 8`. Ambos < 32768.
- `RibbonQuad` (público): `strip` e `billboard` públicos; novo `flat(PoseStack.Pose, MultiBufferSource, float x, float y, float z, float half, int rgb, int alpha, int mode, int param)`: um quadrado deitado (normal +y), UV0 de 0 a 1 nos cantos, no `ribbon()`.
- `PlasmaCore` (público): novo `billboard(PoseStack, MultiBufferSource, float half, int rgb, int alpha, int detailBits, int dimPermille)` (o corpo do `render` de hoje com o meio lado e o apagamento no UV2.x, `clamp(dim, 0, 1000)`); `render` passa a chamá-lo com `HALF` e 0; `detail(double)` público.
- `CrystalGem.render(..., boolean diamond, float fx)` público (o fx no UV0.y, `setUv(hull, fx)`); o de hoje chama com 0.
- `CrystalParam`: `static float repairing(double progress, boolean lit)` = `(lit ? 1 : 3) + clamp(progress)`; `static float dissolving(double amount)` = `5 + clamp(amount)`; `static int fxKind(float fx)` (0 se < 0,5; 1 se < 2,5; 2 se < 4,5; senão 3) e `static double fxValue(float fx)`, espelho do que o shader decodifica.
- `resonator_plasma.fsh`: `float gain = 1.0 - clamp(float(extra.x) / 1000.0, 0.0, 1.0); if (gain <= 0.0) discard;` no começo e a saída `vec4(wa_dither(wa_tonemap(col) * gain), inside * gain)`, com o comentário do canal. O `.vsh` já passa o UV2 inteiro.

- [ ] **Passo 1: JUnit que falha:** `RibbonParamTest` (o máximo de `bolt` e de `disc` < 32768; `bolt(4, 3, true, 2.7)` decodifica 4, 3, cheio e 27 pelos deslocamentos do shader; `disc(0.5, true, 0.1)` dá 64, anel e 13) e `CrystalParamFxTest` (`fxKind/fxValue` de `repairing(0|0,5|1, true|false)` e `dissolving(0|1)`; `fxKind(0) == 0`).
- [ ] **Passo 2:** implemente; `./gradlew test --tests "*RibbonParamTest" --tests "*CrystalParamFxTest"` passa; `./gradlew build`.
- [ ] **Passo 3: o Ressonador fica igual:** e2e `WA_E2E_ONLY=ressonador WA_E2E_CAPTURE=ressonador-camara` antes (no commit anterior) e depois; `python -I .claude/skills/wa-modular/scripts/comparar.py <antes> <depois>` sem diferença acima do ruído do dither nas capturas `ressonador-camara*`; log sem shader que não compilou.
- [ ] **Passo 4:** varreduras; commit `Render do Ressonador: API pública para o Reparador e o apagamento do plasma`.

**Custo:** nenhum novo no Ressonador (o canal novo vai 0; um `clamp` e uma multiplicação por fragmento no plasma).

---

### Tarefa 5: o BER do Reparador (registro, quadro, cristais nos pedestais, emissor; parado e sem energia)

**Arquivos:** criar em `B/client/repairer/render/`: `RepairerRenderer.java`, `RepairFrame.java`, `RepairerCrystals.java`, `RepairerLights.java`; mudar `B/client/ClientSetup.java`.

**Contrato:**
- `ClientSetup.registerRenderers`: `event.registerBlockEntityRenderer(ModBlockEntities.CRYSTAL_REPAIRER.get(), RepairerRenderer::new)`.
- `RepairerRenderer` (público): `getViewDistance()` = 64; `shouldRenderOffScreen` = false; `getRenderBoundingBox` = `new AABB(pos)` (tudo cabe no bloco: o vidro vai de 2,6 a 13,4 px). Ganchos do e2e: `public static boolean forceLegacyForTests`, `public static int lastPrimitivesForTests` (quantos cristais e quads o último `render` pôs). O `render`:
  1. `view = repairer.clientView()`, `lit = state.getValue(LIT)`; sem luz e `view.empty()`: zera o contador e sai (o custo do Reparador apagado e vazio).
  2. Preenche a `RepairFrame.INSTANCE` (uma só, reusada; o render é só na thread de render): `seconds[i]` pela `RepairClock` para as linhas que andam (−1 nas outras), `running`, `emitter` (o máximo do `RepairTimeline.emitter` das linhas, ou `idleEmitter(time)` sem nenhuma), `time = (gameTime % 24000 + partialTick) / 20`, `quarter` pelo `RepairerGeometry.quarterFrom2D(FACING.get2DDataValue())`, a câmera relativa ao canto do bloco em pixels (`ex, ey, ez`), a distância ao centro ao quadrado e o `light`.
  3. `shader = !forceLegacyForTests && os três shaders != null && !ShaderPackCheck.active()`; sem shader, nesta tarefa, sai (a contingência é a Tarefa 12).
  4. `poseStack.scale(1/16)` e desenha **agrupado por tipo**: `RepairerCrystals.render` (o `crystal()`), `RepairerLights.emitter` (o `plasma()`), `RepairerLights.ribbons` (todas as fitas; vazio nesta tarefa). Soma os retornos no contador.
- `RepairerCrystals.render`: para cada linha com `kind != NONE`, o cristal em pé no pedestal: posição `RepairerGeometry.pedestalX/Z(quarter, linha)`, base em `PEDESTAL_TOP_Y` (origem da malha em `PEDESTAL_TOP_Y − CrystalMesh.BOTTOM × MAIN`, `MAIN = 2,6` px por unidade, ~2,7 px de altura), giro fixo `−(22,5 + 45·linha + 90·quarter)`°, `CrystalGem.render` com: malha losango para `CUT` e `TUNED+t` e aglomerado para `SIGNAL`; faixa dourada `0xDDBC5C` no `CUT` e `GuiPaint.tierColor(tier)` no Afinado; semente = linha; estado `LIVE` na travada e `CRACKED` nas outras; fx 0 nesta tarefa (a Tarefa 9 liga o reparo). Sem alocar (rascunhos estáticos; `RouterTier.values()` numa constante).
- `RepairerLights.emitter`: só com `lit`; `PlasmaCore.billboard` transladado para (8; `EMITTER_Y`; 8) px, meio lado 6,9 px, cor `0xF0603E` (o coral do mockup), alfa 255, detalhe pela distância, apagamento `round(1000 × (1 − clamp(emitter / PEAK)))`.
- **Parado** = luzes acesas do chunk (Tarefa 2) + emissor pulsando devagar + os rachados que esperam; **sem energia** = luzes apagadas do chunk, sem emissor, os rachados sem brilho (a Tarefa 9 dá o fx "sem energia").

**Custo:** apagado e vazio: uma leitura de blockstate e um `if`. Parado aceso sem cristal: 1 quad. Por cristal: a malha do `CrystalGem` (96 ou 24 triângulos, mais o casco). Nada alocado por quadro.

- [ ] **Passo 1:** implemente; `./gradlew build`.
- [ ] **Passo 2:** o e2e desta tarefa vem com a Tarefa 6; **não commite ainda** (o portão visual do parado, do sem energia e dos cristais nos pedestais é o da Tarefa 6).

---

### Tarefa 6: o e2e do efeito e as referências lado a lado

**Arquivos:** criar `B/client/dev/E2eRepairerAnimSteps.java`, `scripts/preview/quadros_reparador.py`; mudar `B/client/dev/E2eSuites.java`, `E2eRepairerSteps.java` (os ajudantes `cameraShot`, `look`, `open`, `screen`, `remove` e `repairer` passam a pacote), `E2eResonatorSteps.java`, `docs/preview/reparador-de-cristais.html` (só aditivo), `.claude/skills/wa-shaders/scripts/lado_a_lado.py`.

**Contrato:**
- `E2eSuites.ALL`: `suite("reparador-animacao", "a animação do Reparador no mundo e na tela, com o tempo parado em cada fase", Prereq.NENHUM, E2eRepairerAnimSteps::animationSteps)` e `optional("reparador-desempenho", "tempo de quadro do Reparador (mede, não afirma)", Prereq.NENHUM, E2eRepairerAnimSteps::perfSteps)`, depois da suíte `reparador`.
- `animationSteps`: fecha a tela; no servidor guarda o `repairTicks` e põe 1.000.000 (`Config.REPAIR_TICKS.set`); põe um Reparador novo 3 blocos a oeste do jogador (`facing` norte); 8 rachados na ordem do mockup (Sinal, Lapidado, Afinado Elite, Sinal, Afinado Esmeralda, Lapidado, Sinal, Afinado Elite; rachado por `E2eResonatorScreenSteps.cracked`), 64 Cristais de Sinal no estoque, `itemsChanged()` e 1.000.000 FE pelo `bulkEnergyView`; espera as 8 linhas andando na `clientView()` do bloco do cliente. Interface escondida, voo ligado. Para cada fase `{puxa 0,25; orbita 0,8; raio 2,0; solda 3,5; fecha 5,0; converge 5,9; clarao 6,45; onda 6,75}`: `RepairClock.freezeForTests = s`, um passo que confere `RepairerRenderer.lastPrimitivesForTests == EXPECTED[fase]` (depois de 5 quadros) e as capturas `reparador-anim-<fase>-alto` (olho em +1,0; +0,6; −1,2 bloco do centro, mirando 10/16 de altura) e `reparador-anim-<fase>-lado` (rente ao vidro pelo leste, como o `GRAZE` da suíte `reparador`), pelo `cameraShot`. Depois os estados: bloco novo só com energia (`parado`, conta 1), mais 3 rachados sem estoque (`parado-rachados`, conta 4), energia tirada por `energy().extract` (`sem-energia`, conta 3, `lit` falso no cliente), sem os rachados (conta 0, sem captura). No fim (e em qualquer saída, pelo `restoreFrameSettings`): `repairTicks` de volta, `freezeForTests = −1`, `forceLegacyForTests = false`, bloco tirado, interface e voo de volta.
- `static int[] EXPECTED`: nesta tarefa `{9, 9, 9, 9, 9, 9, 9, 9}` (emissor + 8 cristais); cada tarefa seguinte dá o valor novo.
- `E2eResonatorSteps`: extraia `frameTimeStep(String name, String label, Runnable setup, Tick perTick)` do `frameTime` (o do Ressonador passa a chamá-lo, com o mesmo rótulo); `restoreFrameSettings` zera também os ganchos do Reparador.
- `perfSteps`: com o mesmo cenário, `frameTimeStep` (120 quadros, sem vsync) em: sem Reparador (antes de pôr), Reparador fora de vista (olhando para o lado oposto, `look` a cada tick), parado com 3 rachados, 8 linhas em 3,5 s, 8 linhas em 5,9 s, 8 linhas com `forceLegacyForTests` em 3,5 s, e a tela aberta com 8 linhas em 3,5 s (a partir da Tarefa 13). As linhas vão para o `result.txt` com o prefixo `reparador`.
- Mockup: o modo de quadro. CSS `.quadro` que esconde `header`, `.cols` e `figcaption`, zera margens e põe as duas telas uma sobre a outra em 1056 px de largura (a tela em y 0 a 816, o mundo em 816 a 1716); no JS, `window.waQuadro(p, energia, parado)` (todas as linhas no progresso `p`, ou rachadas paradas, relógio fixo em `1 + p × 7`, um quadro desenhado) e, com `#quadro=` no endereço, ele é chamado depois da fonte e o laço de animação não começa. O desenho normal não muda.
- `quadros_reparador.py SAIDA_DIR`: para cada fase (as 8 acima e `parado`, `sem-energia`), o Edge (`C:\Program Files (x86)\Microsoft\Edge\Application\msedge.exe`, ou o Chrome) com `--headless=new --hide-scrollbars --force-device-scale-factor=1 --window-size=1056,1716 --virtual-time-budget=4000 --screenshot=...` no `file:///...#quadro=<s/7>&energia=..&parado=..`, cortado em `<fase>-tela.png` (0, 0, 1056, 816) e `<fase>-mundo.png` (0, 816, 1056, 1716). Saída em `run/reparador-quadros/` (fora do git).
- `lado_a_lado.py`: se o primeiro argumento for uma pasta, cada par é `nome_da_referência:captura` (o PNG `nome.png` da pasta) em vez de `número_do_quadro:captura`; o resto igual. Recortes úteis: a câmara da tela do mockup é `24,78,600,474` do `-tela.png`; o vidro do mundo, `240,186,816,642` do `-mundo.png`.

- [ ] **Passo 1:** implemente; `./gradlew build`.
- [ ] **Passo 2:** `python -I scripts/preview/quadros_reparador.py run/reparador-quadros` gera 20 PNGs.
- [ ] **Passo 3:** e2e `WA_E2E=run/e2e-reparador WA_E2E_ONLY=reparador-animacao WA_E2E_CAPTURE=reparador-anim ./gradlew runClient`: `result.txt` sem FAIL, log sem shader que não compilou e sem erro de modelo; e `WA_E2E_ONLY=reparador,ressonador` continua OK.
- [ ] **Passo 4: portão do dono (Tarefas 5 e 6):** monte `lado_a_lado.py` das fases `parado`, `sem-energia` e `raio` (mundo) e mostre com as capturas `reparador-anim-parado*`, `-sem-energia` e `-raio-*`. Depois do "aprovado", varreduras e commit `Reparador: BER (cristais e emissor), estados parado e sem energia, e2e e referências da animação`.

---

### Tarefa 7: runas e pedestais acesos (e o modo 3 da fita)

**Arquivos:** mudar `R/shaders/core/resonator_ribbon.fsh`, `B/client/repairer/render/RepairerLights.java`, `E2eRepairerAnimSteps.java`.

**Contrato:**
- Shader: novo ramo `else if (mode == 3)` antes do `else` do tentáculo: o disco de luz. Parâmetro pelo `RibbonParam.disc`: raio (7 bits, fração do quad), anel (bit 7), largura do anel (7 bits). `p = quad × 2 − 1`, `r = length(p)`. Anel: núcleo gaussiano em volta de `|r − raio|` com a largura, um halo exponencial fraco e um grão de ruído leve (`wa_noise`), na cor `mix(tier, WHITE, 0,55)`. Brilho (sem o bit): miolo creme `exp(−r² / (k² × 0,08))` mais halo `exp(−r / (k × 0,35))` em `mix(tier, WHITE, 0,3)`, `k = max(raio, 0,02)`. Os dois somem a zero de 0,85 a 1 do raio do quad. O `col *= power`, o descarte e o tonemap do fim valem como nos outros modos.
- `RepairerLights.ribbons`: só com linha andando. Para as 8 runas (`RUNE_Y`, meio lado 1,6 px, `0xFF825A`) e os 8 pontos (`DOT_Y`, 0,8 px), a intensidade `max(RUNES_WORKING, RUNES_WORKING + runeWave(s, distância))` sobre as linhas que andam, limitada a 1 (distâncias pelo `RepairerGeometry`); para o topo do pedestal de cada linha que anda (`PEDESTAL_GLOW_Y`, 1,2 px, `0xFF9664`), `pedestal(s)`. Cada um é um `RibbonQuad.flat` com `MODE_DISC` e `disc(0,45, false, 0)`, alfa `round(255 × intensidade)`, desenhado só acima de 0,01. Até 24 quads.
- `EXPECTED = {25, 33, 33, 33, 33, 33, 33, 33}`.

**Custo:** até 24 quads pequenos por Reparador trabalhando, um laço de 8 × 8 em `double`, nada alocado.

- [ ] **Passo 1:** implemente; `./gradlew build`; e2e da suíte com `WA_E2E_CAPTURE=reparador-anim-puxa,reparador-anim-raio`.
- [ ] **Passo 2: portão do dono:** lado a lado de `puxa` e `raio` (mundo). Ajuste até o "aprovado"; commit `Reparador: runas e pedestais acesos (disco de luz no shader das fitas)`.

---

### Tarefa 8: o raio fractal emissor → rachado

**Arquivos:** criar `B/client/repairer/render/RepairerBeams.java`; mudar `R/shaders/core/resonator_ribbon.fsh`, `RepairerLights.java` (chama os raios dentro do `ribbons`), `E2eRepairerAnimSteps.java`.

**Contrato:**
- Shader: o primeiro ramo vira `if (mode == 0 || mode == 1)`. No modo 1, `seedShift = float((bits >> 3) & 15) × 7.0` soma à semente do canal principal e à dos arcos, e a sintonia vale 1; no modo 0 nada muda (`seedShift = 0`, sintonia pelos bits).
- `RepairerBeams.render(pose, buffer, frame)`: para cada linha que anda com `beam(s) > 0,02`, a fita do emissor ao centro do cristal: começo = centro do emissor + direção × `EMITTER_RADIUS`; fim = a origem do cristal + 0,06 × `MAIN` em y (a Tarefa 11 soma a vibração, pelo `RepairerCrystals.center`); largura `1,2 × beamWidth(s)` px; cor `0xFF9664`; alfa `round(255 × beam(s))`; `RibbonQuad.strip` com a câmera da `RepairFrame` e `MODE_REPAIR_BOLT`, parâmetro `RibbonParam.bolt(beamBand(s), 2 × linha + 1, s ≥ CONVERGE, 0,7 × comprimento / largura)`. Os pacotes de luz são os do modo 0 (o termo `packet`), mais rápidos na faixa maior.
- `EXPECTED = {25, 33, 41, 41, 41, 41, 33, 33}`.

**Custo:** 1 quad por linha que anda; o fragment do modo 0 já aprovado.

- [ ] **Passo 1:** implemente; build; e2e com `WA_E2E_CAPTURE=reparador-anim-raio,reparador-anim-converge`; o Ressonador (`WA_E2E_ONLY=ressonador`, capturas `ressonador-camara-fluxo*`) igual ao de antes.
- [ ] **Passo 2: portão do dono:** lado a lado `raio` e `converge`; commit `Reparador: raio fractal do emissor ao rachado (modo 1 das fitas)`.

---

### Tarefa 9: as rachaduras fechando (canal de efeito do `resonator_crystal`)

**Arquivos:** mudar `R/shaders/core/resonator_crystal.vsh`, `.fsh`, `RepairerCrystals.java`, `E2eRepairerAnimSteps.java`.

**Contrato:**
- `.vsh`: `flat out float fx; ... fx = UV0.y;` (o casco usa só o UV0.x, que não muda).
- `.fsh`: `flat in float fx;` decodifica `fxKind`/`fxValue` como o `CrystalParam` (0 nada, 1 reparo com energia, 2 sem energia, 3 dissolução). Com 1 ou 2:
  - O risco do rachado comum não é desenhado; no lugar, as 6 rachaduras do mockup no plano x-y do cristal (`obj.xy`), segmentos de `CRACK_A[k]` a `CRACK_B[k]`: (−0,028; 0,536)→(0,083; 0,339)→(−0,069; 0,175)→(0,055; −0,087)→(−0,014; −0,306) (o tronco) e os galhos (0,083; 0,339)→(0,290; 0,219) e (−0,069; 0,175)→(−0,276; 0,077) (o mockup em unidades de s, x × 0,69 e y = 0,58 − (y_mockup + 1) × 0,547).
  - Distância ao segmento pela fórmula de Inigo Quilez ("distfunctions2d"), guardando o ponto mais perto; `reach = length(ponto − (0; 0,06))`; a rachadura está aberta onde `reach > progresso × 0,50` (fecha de dentro para fora). Aberta: `CRACK` a menos de 0,022; com energia, o miolo `vec3(1,0; 0,471; 0,314)` a menos de 0,009 (as rachaduras acesas do mockup). Com energia e progresso entre 0 e 1, o ponto de solda creme `vec3(1,0; 0,957; 0,824)`: `exp(−((reach − frente)/0,03)²) × exp(−d²/0,0016)`, misturado até 1,4×.
  - A paleta vai do osso ao coral por `heal = clamp((progresso − 0,15)/0,75)` (só com energia), como o `crystalColors` do mockup; as pintas do Opaco só com `heal < 0,5`; a faixa do Afinado vai do cinza esverdeado à cor do tier pelo mesmo `heal`.
  - Com fx 0, tudo como hoje (o Núcleo).
- `RepairerCrystals`: linha que anda com `s < CONVERGE`: `CRACKED` e `fx = CrystalParam.repairing(cracks(s), true)`; de 5,5 s em diante, `LIVE` com fx 0; parada: `CRACKED` e `repairing(0, frame.lit)`; travada: `LIVE`.
- `EXPECTED` não muda.

**Custo:** 6 distâncias a segmento por fragmento, só nos cristais com fx 1 ou 2.

- [ ] **Passo 1:** implemente; build; e2e com `WA_E2E_CAPTURE=reparador-anim-solda,reparador-anim-fecha,reparador-anim-sem-energia,reparador-anim-parado`; o Ressonador com um rachado (`ressonador-camara-rachado`) igual ao de antes.
- [ ] **Passo 2: portão do dono:** lado a lado `solda`, `fecha` e `sem-energia` (de perto: a captura `-lado`). Se as rodadas de ajuste pararem de aproximar a referência, volte à decisão 3 (a alternativa são texturas em estágios numa malha à parte). Commit `Reparador: rachaduras que fecham de dentro para fora (canal de efeito do cristal)`.

---

### Tarefa 10: os 4 cristais cheios em órbita, com a dissolução

**Arquivos:** mudar `R/shaders/core/resonator_crystal.fsh`, `RepairerCrystals.java`, `E2eRepairerAnimSteps.java`.

**Contrato:**
- `.fsh`, antes do teste do casco: com `fxKind == 3`, `n = clamp((wa_fbm(crystal × 5 + semente × 3,1, 2) − 0,2) / 0,6)`; `n < fxValue` descarta (o casco some junto, então o contorno não fica sozinho); a borda `burn = 1 − smoothstep(fxValue, fxValue + 0,08, n)` (só com `fxValue > 0`) mistura a cor no fim com `mix(vec3(1,0; 0,96; 0,86), CORAL[3], 0,35)`.
- `RepairerCrystals.orbit` (no mesmo bloco do `crystal()`, depois dos pedestais): só com `distanceSq < 24²`; para cada linha que anda e k de 0 a 3, com `appear = orbitAppear(s, k) > 0` e `dissolve(s, k) < 1`: ângulo `2π(k/4 + linha/32) + orbitAngle(s)`, posição (8 + cos × `ORBIT_RADIUS × orbitShrink(s)`; `ORBIT_Y` + 0,25 × sen(tempo × 1,6 + k + linha); 8 + sen × raio), escala `1,1 × appear` px por unidade, giro no próprio eixo de 90°/s, aglomerado, `LIVE`, semente `(4 × linha + k) & 15`, `fx = dissolve > 0 ? dissolving(dissolve) : 0`.
- `EXPECTED = {25, 65, 73, 73, 73, 73, 33, 33}`.

**Custo:** até 32 aglomerados pequenos (192 triângulos com o casco cada) com 8 linhas, só de perto; medido na Tarefa 14 (a convergência com 8 linhas é o pior caso).

- [ ] **Passo 1:** implemente; build; e2e com `WA_E2E_CAPTURE=reparador-anim-orbita,reparador-anim-raio,reparador-anim-converge`.
- [ ] **Passo 2: portão do dono:** lado a lado `orbita` e `converge`; commit `Reparador: cristais cheios em órbita que se dissolvem`.

---

### Tarefa 11: convergência, clarão, onda de choque e saída

**Arquivos:** mudar `RepairerCrystals.java`, `RepairerLights.java`, `RepairerBeams.java`, `E2eRepairerAnimSteps.java`.

**Contrato:**
- `RepairerCrystals.center(frame, linha, float[] out)`: a origem do cristal da linha com a vibração `vibration(s)` (x e z por senos a ~30 Hz com fase por linha, y pela metade); usado pelo cristal e pelo fim do raio.
- Cristal da linha que anda: alfa da cor (o clarão do shader) = `round(255 × crystalGlow(s))`; de 6,7 s em diante, giro extra `exit(s) × 720`°, sobe `1,5 × exit(s)` px e escala `× (1 − exit(s))`.
- `RepairerLights.ribbons`, por linha que anda: o clarão, `RibbonQuad.billboard` no centro do cristal, lado `flashSize(s)`, cor `0xFFBE78`, alfa `round(255 × flash(s))`, `MODE_DISC`, `disc(0,5, false, 0)`; a onda, `RibbonQuad.flat` em (pedestal; `FLOOR_Y`), meio lado 3,6 px, mesma cor, alfa `round(255 × shockAlpha(s))`, `disc(shockRadius(s)/3,6, true, 0,5/3,6)`. Cada um só com alfa ≥ 1.
- O emissor já sobe pela `emitter(s)` e o raio engrossa pela `beamBand`/`beamWidth` (Tarefas 5 e 8).
- `EXPECTED = {25, 65, 73, 73, 73, 73, 49, 49}`.

**Custo:** 2 quads por linha só entre 6,3 e 7 s.

- [ ] **Passo 1:** implemente; build; e2e com `WA_E2E_CAPTURE=reparador-anim-converge,reparador-anim-clarao,reparador-anim-onda`.
- [ ] **Passo 2: portão do dono:** lado a lado `converge`, `clarao` e `onda`; commit `Reparador: convergência, clarão, onda de choque e saída do reparado`.

---

### Tarefa 12: contingência (shaderpack do Iris ou shader que não compilou)

**Arquivos:** criar `B/client/repairer/render/RepairerLegacy.java`; mudar `RepairerRenderer.java`, `B/client/resonator/render/ChamberLens.java` (`halo` público), `LissajousTrace.java` (`segment` e `CORAL_LIGHT` públicos), `E2eRepairerAnimSteps.java`.

**Contrato (o caminho do Ressonador sem shader: sprites e traços, sem core shader):**
- `RepairerRenderer`: sem shader, `RepairerLegacy.render(poseStack, buffer, frame)` em vez de sair.
- `RepairerLegacy`, agrupado por tipo: (1) os cristais pelo modelo do item (`ItemRenderer.renderStatic`, `ItemDisplayContext.FIXED`, com o `light` da `RepairFrame`, escala 3,2 no referencial de pixels): o rachado do tipo até `cracks(s) ≥ 0,85` e o reparado depois; as pilhas por tipo (rachada e cheia) criadas uma vez, na primeira chamada; os da órbita, Cristal de Sinal cheio escalado por `appear × (1 − dissolve)` (escalar até 0, a alternativa do spec); (2) os halos (`ChamberLens.halo` no `glow()`): o emissor (5 px × brilho, coral), as 24 runas, pontos e pedestais (mesmas intensidades da Tarefa 7, pela função de intensidade que o `RepairerLights` expõe ao pacote) e o clarão (`flashSize × 2`, alfa `flash`); (3) os raios retos (`LissajousTrace.segment` no `RenderType.lightning()`, meia largura 0,12 px, alfa `beam × 0,8`). A onda de choque fica de fora (não há sprite deitado).
- O e2e ganha, depois das fases, `forceLegacyForTests = true` nas fases `raio` e `clarao`: capturas `reparador-anim-legado-<fase>-alto` e contagens 73 e 41.

**Custo:** o mesmo número de primitivas, sem core shader.

- [ ] **Passo 1:** implemente; build; e2e com `WA_E2E_CAPTURE=reparador-anim-legado`. Se houver um shaderpack do Iris à mão, confira à mão no `runClient` e anote no relatório; senão, diga que só o gancho foi testado.
- [ ] **Passo 2: portão do dono:** as duas capturas ao lado das do shader; commit `Reparador: contingência sem shader (Iris)`.

---

### Tarefa 13: a câmara animada da tela

**Arquivos:** criar `B/client/repairer/ChamberMotion.java` (puro), `B/client/repairer/ChamberKite.java`, `T/client/repairer/ChamberMotionTest.java`; mudar `RepairerChamberPanel.java`, `RepairerScreen.java` (passa o `partialTick` ao painel), `B/client/GuiPaint.java`, `E2eRepairerAnimSteps.java`.

**Contrato:** o `drawChamber` do mockup (linhas 211 a 328) em pixels da interface, na câmara de 192 × 128 de hoje (as posições dos pés, o centro e o núcleo do `RepairerChamberPanel` ficam).
- `ChamberMotion` (puro, com JUnit; o código vai inteiro aqui):

```java
package io.github.matheusanbs.wirelessautomate.client.repairer;

/** As curvas da câmara da tela, as do mockup aprovado; p é o progresso da linha (0 a 1). Puro, com JUnit. */
final class ChamberMotion {
    private ChamberMotion() {
    }

    static double clamp(double x) {
        return x < 0 ? 0 : x > 1 ? 1 : x;
    }

    /** O fluxo do filete: forte no começo e some quando as rachaduras fecham. */
    static double flow(double p) {
        return p < 0.08 ? p / 0.08 : p > 0.85 ? clamp((1 - p) / 0.15) : 1;
    }

    static double crackAlpha(double p) {
        return 1 - clamp(p / 0.85);
    }

    /** Do osso (0) à cor do tipo (1). */
    static double colorT(double p) {
        return clamp((p - 0.15) / 0.75);
    }

    static double glow(double p) {
        return 0.25 + 0.3 * p;
    }

    static double pulse(double time, boolean powered) {
        return 0.5 + 0.5 * Math.sin(time * (powered ? 2.2 : 1.1));
    }

    /** Quantos cristais do estoque giram em volta do núcleo: um por 8, até 8. */
    static int poolCount(int pool) {
        return pool <= 0 ? 0 : Math.min(8, (pool + 7) / 8);
    }

    /** O clarão de quando a linha termina: 0,8 s. */
    static double flash(long sinceTicks, float partialTick) {
        return Math.max(0, 1 - (sinceTicks + partialTick) / 20.0 / 0.8);
    }

    /** O ponto t da curva quadrática a → k → b; escreve em out. */
    static void quad(double ax, double ay, double kx, double ky, double bx, double by, double t, double[] out) {
        double u = 1 - t;
        out[0] = u * u * ax + 2 * u * t * kx + t * t * bx;
        out[1] = u * u * ay + 2 * u * t * ky + t * t * by;
    }

    /** A mistura de duas cores ARGB. */
    static int lerp(int a, int b, double t) {
        double c = clamp(t);
        int out = 0;
        for (int shift = 0; shift < 32; shift += 8) {
            int x = (a >>> shift) & 0xFF;
            int y = (b >>> shift) & 0xFF;
            out |= ((int) Math.round(x + (y - x) * c) & 0xFF) << shift;
        }
        return out;
    }

    /** A cor ARGB com o RGB vezes k (até 255), o alfa igual. */
    static int shade(int argb, double k) {
        int out = argb & 0xFF000000;
        for (int shift = 0; shift < 24; shift += 8) {
            out |= Math.min(255, (int) Math.round(((argb >>> shift) & 0xFF) * k)) << shift;
        }
        return out;
    }
}
```

  Testes: `flow(0) == 0`, `flow(0,08) == 1`, `flow(0,5) == 1`, `flow(1) == 0`; `crackAlpha(0) == 1`, `(0,85) == 0`; `colorT(0,15) == 0`, `(0,9) == 1`; `poolCount` de 0, 1, 8, 9, 64 e 100 dá 0, 1, 1, 2, 8 e 8; `flash(0, 0) == 1`, `flash(16, 0) == 0`; `quad` nas pontas; `lerp` e `shade` nas pontas e no alfa.
- `ChamberKite` (desenho por `fill`, sem alocar): `kite(g, x, y, s, esquerda, direita, faixa)` (o losango do `drawKite`: metade esquerda, direita, o brilho de cima, a faixa em V quando `faixa != 0`, o contorno escuro), `cracks(g, x, y, s, alfa, aceso, p)` (as 3 polilinhas do mockup amostradas a cada meio pixel, a escura de 2 px e, acesa, o miolo laranja, e a solda que corre o tronco pelo p), `glow`, `flash` (os discos e a elipse do clarão) e `disc` (o do painel de hoje, movido). Cor do tipo: Sinal `CHAMBER_SIGNAL`, Lapidado `CHAMBER_CUT` com a faixa `CHAMBER_CUT_BAND`, Afinado `tierColor` com a faixa a 75% para o branco; o tipo pelo `RepairerView.kindOf` do slot.
- `RepairerChamberPanel`: tamanho de cada pedestal `s = round(128 × 0,13 × (0,8 + 0,3 × profundidade))` e a elipse do pedestal (0,75 s × 0,28 s) calculados uma vez; o núcleo entra na ordem de trás para a frente onde o mockup o põe; por quadro: halo, anel, pedestais com os losangos (balanço `sen(tempo × 1,6 + semente) × 0,12 s`), filetes com 12 partículas por linha que anda (com energia), núcleo pulsando, os cristais do estoque em órbita pelo `poolCount` do slot do estoque, o clarão de 0,8 s quando o fim de uma linha volta a 0 (o painel guarda o último fim visto e o tick do término, por linha), tudo dentro de um `enableScissor` da câmara, e o véu sem energia como hoje. O progresso: `RepairClock.seconds(fim, duração, gameTime, partialTick) / 7`; travada vale 1; com o tempo congelado, o tempo dos pulsos é `1 + freezeForTests`.
- `GuiPaint`: as cores do mockup em constantes `CHAMBER_*` (iguais nos dois temas): osso L `0xFF8D8678` e R `0xFFB5AD9C`, Sinal `0xFFF0603E`, Lapidado `0xFFFF8C6E`, faixa `0xFFD4AA46`, contorno `0xD9140E16`, rachadura `0xE6180C10`, acesa `0xD9FF7850`, solda `0xF2FFF4D2`, fluxo `0xFFFF9664`, faísca `0xFFFFE1AA`, estoque aceso `0xFFC8462D`/`0xFFFF7854`, apagado `0xFF5A5464`/`0xFF807A8A`, clarão `0xFFFFF8E1`.
- e2e: com as 8 linhas da suíte, a tela aberta e o tempo congelado em 0,8, 3,5 e 6,45 s: `reparador-anim-tela-<fase>` e a de 3,5 s no tema escuro.

**Custo:** só com a tela aberta; ~1.500 `fill` por quadro com 8 linhas (medido na `reparador-desempenho`; a regra é até ~1 ms por quadro); nada alocado (rascunho `double[2]`).

- [ ] **Passo 1:** `ChamberMotionTest` falha; implemente o `ChamberMotion`; passa.
- [ ] **Passo 2:** o resto; `medir.py` (o painel e o `ChamberKite` abaixo de 500 linhas); build.
- [ ] **Passo 3:** e2e com `WA_E2E_CAPTURE=reparador-anim-tela,reparador-tela` (a suíte `reparador` da etapa 2 também, sem texto cortado).
- [ ] **Passo 4: portão do dono:** lado a lado com os `<fase>-tela.png` (recorte da câmara `24,78,600,474`); commit `Reparador: câmara animada da tela (a do mockup)`.

---

### Tarefa 14: `/wa profile`, medidas e documentação

**Arquivos:** mudar `B/repairer/RepairerManager.java`, `B/command/WaCommand.java`, `R/lang/en_us.json`, `R/lang/pt_br.json`, `B/gametest/RepairerSyncGameTests.java`, `CLAUDE.md`, `docs/progresso.md`, `docs/superpowers/specs/2026-10-26-reparador-de-cristais-design.md`, `docs/benchmark.md`.

**Contrato:**
- `RepairerManager`: com o conjunto vazio, `lastTickNanos = 0` e sai (como hoje); senão mede o tick com `System.nanoTime()` e guarda o último e a média móvel (peso 0,05, como o `ResonatorProfile`); `public record Profile(int active, double averageNanos, long lastNanos)` e `static Profile profile()`; o `reset()` zera.
- `WaCommand`: depois da linha do Ressonador, `command.wirelessautomate.profile.repairer` com o número trabalhando, a média e o último (pelo `ms(...)`). Textos: en `"  Crystal Repairers: %s working · tick avg %s ms · last %s ms"`, pt `"  Reparadores de Cristais: %s trabalhando · tick média %s ms · último %s ms"`, logo depois da chave do Ressonador nos dois arquivos.
- GameTest `perfilMedeOTickDoGerenciador`: com uma linha andando, espera `RepairerManager.profile().lastNanos() > 0`.
- Medidas: a suíte `reparador-desempenho` (a tabela no `docs/benchmark.md`, seção datada, com a leitura). Aceite: "fora de vista" e "parado" dentro do ruído do "sem Reparador" (parado custa zero), e os números de 8 linhas e da tela levados ao dono (regressão acima de ~1 ms por quadro precisa de razão escrita). O `/wa profile` com 8 linhas andando, anotado.
- Docs: `CLAUDE.md` (o mapa: `client/repairer/render/` e as partes, a `RepairerView`, o `lit`, os canais novos dos três shaders, `RibbonParam`, a suíte e as contagens de GameTests); `docs/progresso.md` (estado, próximo passo, linha no histórico com a verificação); a spec (Estado e um parágrafo "Etapa 3: decisões" com as decisões deste plano).

- [ ] **Passo 1:** GameTest falha; implemente; `./gradlew build` e as seis runs de GameTest.
- [ ] **Passo 2:** e2e completo `./scripts/e2e.sh` (ou o perfil padrão no Windows) OK; a suíte `reparador-desempenho`; as medidas na doc.
- [ ] **Passo 3:** varreduras nos arquivos do ramo (`varrer_perf.py`, `varrer.py`, `medir.py`); commit `Reparador: perfil do gerenciador, medidas e docs da animação`.

---

## Autoconferência

- Spec, linha do tempo: puxada e runas (T7), órbita (T10), raio e rachaduras com solda (T8, T9), convergência e dissolução (T10, T11), clarão, onda e saída (T11), parado e sem energia (T2, T5, T9). Tabela de técnicas: todas as nativas escolhidas, a alternativa das rachaduras e da onda descartada com o motivo (decisões 3 e 4); a órbita escala até 0 só na contingência. Iris: T12. Custo: T1 (servidor, rede), T5 a T11 (linha "Custo" de cada), T14 (medida e perfil). e2e com o tempo parado e lado a lado: T6. Tela: T13.
- Nomes usados entre tarefas: `RepairerView.kind/end/blocked/running/empty/duration`, `RepairerBlock.LIT`, `RepairFrame.INSTANCE/seconds/running/emitter/time/quarter/ex/ey/ez/distanceSq/light/lit/view`, `RepairClock.freezeForTests/seconds/frozen`, `RepairerRenderer.forceLegacyForTests/lastPrimitivesForTests`, `RibbonParam.bolt/disc/MODE_*`, `CrystalParam.repairing/dissolving`, `PlasmaCore.billboard`, `RibbonQuad.flat`, `E2eRepairerAnimSteps.EXPECTED`.
