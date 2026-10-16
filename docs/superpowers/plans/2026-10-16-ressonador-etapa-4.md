# Ressonador de Sinal, etapa 4: JEI, guia e o GIF · plano de implementação

> **Para agentes:** SUB-SKILL OBRIGATÓRIA: use superpowers:subagent-driven-development (recomendado) ou superpowers:executing-plans para executar este plano tarefa por tarefa. Os passos usam caixas (`- [ ]`) para acompanhar.

**Objetivo:** fechar o Ressonador para publicar: o JEI mostra a estrutura em 3D (Vitrine e Faixa), a recarga no Núcleo e a tabela por tier; o guia ganha duas páginas (Quartzo de Sinal e cristais; Ressonador de Sinal) nos dois idiomas; a vitrine grava um GIF da câmara saindo do chiado e entrando em sintonia. Sem Tablet.

**Arquitetura:** a lista dos blocos de cada montagem e os estados montados saem da lógica pura que já existe (`ResonatorBlueprint.expected`, `ResonatorLayout.check`), com uma classe pura nova para as contagens e as camadas. O JEI fica todo em `compat/jei/` (nada fora dele referencia o JEI), em classes por categoria e uma para a vista 3D. O guia continua gerado pelo Python; a cena da Vitrine montada vem de um arquivo escrito e conferido por um teste JUnit (o Python não repete a regra das texturas conectadas). O GIF é um passo novo da vitrine, numa classe própria, mais um script PIL.

**Stack:** Java 21, NeoForge 21.1 (Minecraft 1.21.1), JEI 19.57 (`compileOnly` da API; `IRecipeCategory`, `IRecipeExtrasBuilder` com `addInputHandler`/`addGuiEventListener` em `mezz.jei.api.gui.inputs`), GuideME 21.1.19, JUnit 5, Python 3 + PIL.

**Spec:** [`docs/superpowers/specs/2026-10-15-ressonador-de-sinal-design.md`](../specs/2026-10-15-ressonador-de-sinal-design.md), seção **"Etapa 4: decisões de implementação"** (a fonte dos valores). Mockup aprovado: `docs/preview/ressonador-etapa4.html` (fixa o conteúdo, não o visual). Etapas 1 a 3 prontas: `resonator/` (`ResonatorBlueprint`, `ResonatorLayout`, `ResonatorFormation.formedState`, `CoreMath`, `ResonanceCoreItem.withTier`/`tierOf`, `SignalCrystalItem`), `Config.resonatorFePerCrystal(tier)` e `Config.rechargeTicks()`, `compat/jei/WirelessAutomateJeiPlugin`, `client/dev/E2eResonatorSteps` (constrói e monta uma Vitrine no mundo do e2e), `client/dev/E2eShowcase`/`E2eShowcaseWorld` (a vitrine) e `client/dev/E2eUpgradeCardSteps` (o modelo de captura do JEI: mouse no slot, tecla R ou U pelo `keyboardHandler.keyPress`, captura quando a tela do `mezz.jei` abre).

## Restrições globais

- Código, comentários, commits e docs em português; textos nos dois `lang` (`en_us.json`, `pt_br.json`), sempre os dois, na mesma ordem. Chaves novas do JEI em `jei.wirelessautomate.resonator.*`.
- **Sem Tablet** e sem os mineradores do ATM10 (decisão do dono).
- **Nada muda** em chaves de NBT, componentes, config ou protocolo: `ModPayloads.VERSION` fica `"18"`. Nenhum payload novo.
- JEI opcional: tipos do JEI só em `compat/jei/`; nada fora desse pacote referencia o JEI. O plugin continua só registrando; cada categoria e a vista 3D em classes próprias.
- Números sempre da config (`Config.resonatorFePerCrystal`, `Config.rechargeTicks`) e do `CoreMath` (câmaras), com os tetos do `Tuning.ceiling`; os tiers só os carregados (`RouterTier.loaded()`).
- Estados montados: sempre pelo `ResonatorLayout.check` + `ResonatorFormation.formedState` (nada de repetir a regra da Carcaça e do Vidro).
- Performance: a vista 3D do JEI monta a lista de estados uma vez por receita (cache), não por quadro; nada no servidor por tick.
- Classes de `client/` nunca referenciadas por código comum.
- Guia: edite `scripts/guide/gerar_guia.py`, nunca os `.md` gerados; só "como operar" (nada de config, ms/tick ou detalhes internos).
- Tamanho das classes (`wa-modular`): tela, componente e renderer até 500 linhas (dividir acima de 800); servidor até 600 (dividir acima de 1000); rode `python -I .claude/skills/wa-modular/scripts/medir.py` nos arquivos mudados antes de cada commit. Comentários (`wa-comentarios`): `python -I .claude/skills/wa-comentarios/scripts/varrer.py` nos arquivos mudados e resolva os candidatos.
- Antes de cada commit: `./gradlew build runGameTestServer runGameTestServerChemicals runGameTestServerSource runGameTestServerAllthemodium`. Nas tarefas com JEI, guia ou vitrine (2 a 5), também o e2e: `WA_E2E="$PWD/run/e2e" ./gradlew runClient` (no Windows, sem `xvfb-run`; `run/e2e/result.txt` deve dizer `OK`) e **abra as capturas novas** (a ferramenta Read mostra imagens) e descreva o que viu. Compile antes (`./gradlew compileJava`); com `BUILD FAILED`, pare e conserte, nunca espere o `result.txt`. Nunca dois jogos ao mesmo tempo; não mexa em `run/options.txt`.
- Commits sem linhas de atribuição, com o autor do dono; `git add` só dos arquivos da tarefa (nunca `.superpowers/`, nunca `run/`). Não faça push.

## Mapa de arquivos

Base `B/` = `src/main/java/io/github/matheusanbs/wirelessautomate/`, `T/` = `src/test/java/io/github/matheusanbs/wirelessautomate/`, `R/` = `src/main/resources/`.

| Arquivo | Papel |
| --- | --- |
| `B/resonator/ResonatorBill.java` (novo, puro) | A lista de uma montagem de exemplo: as peças por posição (pela `ResonatorBlueprint.expected(Face.SOUTH, layout)`), a contagem por `Part`, os membros montados (pelo `ResonatorLayout.check`) e o filtro por camada |
| `T/resonator/ResonatorBillTest.java` (novo) | JUnit: contagens (Vitrine 85/9, Faixa 82/12, 1 de cada peça), camadas, membros = 98 |
| `T/resonator/ResonatorGuideSceneTest.java` (novo) | JUnit "golden": gera as linhas `<Block>` da Vitrine montada e compara com `scripts/guide/resonator_scene.txt`; com `-Dwa.regen=true` (ou o arquivo ausente) escreve o arquivo |
| `scripts/guide/resonator_scene.txt` (novo, gerado pelo teste) | As linhas da cena do guia |
| `B/compat/jei/WirelessAutomateJeiPlugin.java` (mudar) | Subtipo do Núcleo, registra as categorias, as receitas, os catalisadores e a informação (delegando a `ResonatorJei`) |
| `B/compat/jei/ResonatorJei.java` (novo) | O registro do Ressonador no JEI: as receitas das duas categorias, os catalisadores e o texto de informação por tier |
| `B/compat/jei/ResonatorRechargeCategory.java` (novo) | Categoria Recarga no Núcleo |
| `B/compat/jei/ResonatorStructureCategory.java` (novo) | Categoria Estrutura do Ressonador: slots com as contagens, botões de camada, linha de rodapé, o repasse de mouse para a vista |
| `B/compat/jei/StructureView.java` (novo) | A vista 3D: estados montados em cache, desenho pelo `BlockRenderDispatcher`, yaw/pitch/zoom e camada |
| `R/assets/wirelessautomate/lang/en_us.json`, `pt_br.json` (mudar) | Textos do JEI |
| `scripts/guide/gerar_guia.py` (mudar) e os `.md` gerados | As páginas `signal-quartz` e `signal-resonator`, o índice e as receitas |
| `B/client/dev/E2eGuideSteps.java` (mudar) | As duas páginas novas na lista |
| `B/client/dev/E2eJeiResonatorSteps.java` (novo) | Capturas do JEI: estrutura (Vitrine, Faixa, camada 2), recarga e informação |
| `B/client/dev/E2eScript.java` (mudar) | Chama os passos novos |
| `B/client/dev/E2eShowcaseResonator.java` (novo) | Vitrine: monta o Ressonador na casa, posiciona a câmera, congela o tempo e captura os 120 quadros do GIF |
| `B/client/dev/E2eShowcase.java` (mudar) | Chama os passos novos |
| `scripts/curseforge/gerar_gif.py` (novo) | Monta o GIF com o PIL |
| `docs/curseforge/ressonador.gif` (novo, gerado) | O GIF |
| `CLAUDE.md`, `docs/progresso.md`, `docs/especificacao.md` (mudar) | Docs |

---

### Task 1: lista pura das montagens e a cena do guia

**Arquivos:** criar `B/resonator/ResonatorBill.java`, `T/resonator/ResonatorBillTest.java`, `T/resonator/ResonatorGuideSceneTest.java`, `scripts/guide/resonator_scene.txt`.

- [ ] **Step 1:** leia `ResonatorBlueprint`, `ResonatorLayout` (`Part`, `Face`, `Probe`, `Member`, `Formed`, `check(cx, cy, cz, front, probe)`) e `ResonatorFormation.formedState`/`outward` para entender como um `Member` vira estado.
- [ ] **Step 2 (teste primeiro):** `ResonatorBillTest`:
  - `Vitrine`: `count(Part.CASING) == 85`, `count(GLASS) == 9`, `CORE`, `ENERGY_PORT`, `CRYSTAL_PORT` e `TUNER` == 1 cada; `Faixa`: 82 e 12.
  - `members()` tem 98 elementos e é um `Formed` (a montagem de exemplo monta sem problema).
  - `upTo(layer)`: com `layer = 1` só `y == 0`; com 5 (Tudo) os 98; a ordem é estável (x, y, z).
- [ ] **Step 3:** `ResonatorBill` (puro, sem Minecraft): `static ResonatorBill of(ResonatorBlueprint.Layout layout)` com o Núcleo na face `SOUTH` (`expected(Face.SOUTH, layout)`); `Part at(x, y, z)`; `int count(Part)`; `List<ResonatorLayout.Member> members()` (rodando `ResonatorLayout.check` com o `Probe` das peças e o Núcleo na posição dele; se não der `Formed`, `IllegalStateException`, que o teste pega); `List<Member> upTo(int layer)`. Javadoc curto dizendo que é a montagem de exemplo do JEI e do guia. `./gradlew test --tests '*ResonatorBillTest'` passa.
- [ ] **Step 4:** `ResonatorGuideSceneTest`: monta, pela `ResonatorBill.of(VITRINE).members()`, uma linha `<Block id="wirelessautomate:<id>" x=".." y=".." z=".." p:...="..." />` por membro, com as mesmas propriedades do `formedState` (Carcaça: `p:position`; Vidro: `p:face` e `p:connections`; Portas, Sintonizador e Núcleo: `p:facing` para fora e `p:formed="true"`; o Núcleo também `p:tier="emerald"`). Os nomes e valores das propriedades vêm dos `getSerializedName` dos enums puros (`CasingPosition.byName`, `GlassFace.of`) e da mesma regra do `outward`; se `outward` for privado e precisar de reuso, torne-o de pacote ou público com Javadoc, sem mudar comportamento. Compara com `scripts/guide/resonator_scene.txt` (caminho a partir de `user.dir`, a raiz do projeto no Gradle); com `-Dwa.regen=true` ou o arquivo ausente, escreve e passa. Diferente: falha dizendo para rodar `./gradlew test --tests '*ResonatorGuideSceneTest' -Dwa.regen=true` (passe a propriedade ao teste no `build.gradle` se preciso: `systemProperty 'wa.regen', System.getProperty('wa.regen')` no bloco `test`, só se ainda não houver forma melhor).
- [ ] **Step 5:** gere o arquivo, confira à mão 3 linhas (um canto, um Vidro do miolo, o Núcleo), rode a verificação completa e commite: "Ressonador: lista pura das montagens e a cena do guia".

### Task 2: JEI, subtipo do Núcleo, Recarga e Informações

**Arquivos:** mudar `B/compat/jei/WirelessAutomateJeiPlugin.java`; criar `B/compat/jei/ResonatorJei.java`, `B/compat/jei/ResonatorRechargeCategory.java`; `lang` (dois); criar `B/client/dev/E2eJeiResonatorSteps.java` (parte da recarga e da informação); mudar `B/client/dev/E2eScript.java`.

- [ ] **Step 1:** subtipo: `registerItemSubtypes` registra `ModItems` do Núcleo (veja o nome em `registry/ModItems`) com o `TierSubtype` (o tier fica no mesmo `block_state`; se o `StorageBlockItem.tierOf` não servir para o Núcleo, use `ResonanceCoreItem.tierOf` num interpretador próprio).
- [ ] **Step 2:** `ResonatorRechargeCategory implements IRecipeCategory<ResonatorRechargeCategory.Recharge>` com `record Recharge(ItemStack dull, ItemStack full)`; `RecipeType` `wirelessautomate:resonator_recharge`; fundo em branco pelo `IGuiHelper` (largura ~ 120, altura ~ 36); slots: o Opaco (entrada), 1 Pó de Redstone (entrada), o cheio (saída); seta animada ou estática do `IGuiHelper` e o texto "30 s" (`jei.wirelessautomate.resonator.recharge.time`, com `Config.rechargeTicks() / 20` formatado sem casas quando inteiro); uma linha curta embaixo ("Um Opaco por vez, numa câmara do Núcleo montado"). Ícone: o Núcleo Básico. O Opaco é o item com `crystal_charge` 0 (use o construtor que a `SignalCrystalItem` oferece; se não houver, `stack.set(ModDataComponents.CRYSTAL_CHARGE, 0)` como o código da etapa 1).
- [ ] **Step 3:** `ResonatorJei` (estático, só chamado pelo plugin): `registerCategories(IRecipeCategoryRegistration)`, `registerRecipes(IRecipeRegistration)` (as duas recargas e, a partir da Task 3, as duas estruturas), `registerCatalysts(IRecipeCatalystRegistration)` (o Núcleo de cada tier carregado nas duas categorias) e a informação: `registration.addItemStackInfo(List<ItemStack> núcleos, Component...)` com a frase do que faz e uma linha por tier carregado: "Básico: 1 câmara · 128 FE/t por cristal · teto 85%" (`jei.wirelessautomate.resonator.info.*`; números com o separador do idioma como o resto do mod faz nas dicas: procure o formatador usado em `TierCoreItem.grouped`). O plugin ganha `registerCategories` e `registerRecipeCatalysts` e delega.
- [ ] **Step 4:** textos nos dois `lang`: nomes das categorias ("Recarga no Núcleo" / "Core recharge"; "Estrutura do Ressonador" / "Resonator structure"), o tempo, a linha de rodapé e a informação.
- [ ] **Step 5:** confira que a receita do Lapidado aparece na bancada com cristais cheios (R no Cristal Lapidado); anote no relatório. Sem mudança se estiver certa.
- [ ] **Step 6:** `E2eJeiResonatorSteps.jeiResonatorSteps(List<Step>)`, no padrão do `E2eUpgradeCardSteps` (só com `ModList.get().isLoaded("jei")`; sem JEI, nada): põe no inventário um Cristal de Sinal Opaco e um Núcleo Básico; R no Opaco com captura `ressonador-jei-recarga`; U no Núcleo e a aba da informação com captura `ressonador-jei-info` (se a informação abrir em outra aba, troque pela tecla de página do JEI ou registre no log, como o passo dos cartões faz). Chame em `E2eScript` depois dos passos do Ressonador. Rode o e2e, abra as capturas e descreva.
- [ ] **Step 7:** verificação completa e commit: "Ressonador: recarga e informação no JEI".

### Task 3: JEI, Estrutura do Ressonador em 3D

**Arquivos:** criar `B/compat/jei/ResonatorStructureCategory.java`, `B/compat/jei/StructureView.java`; mudar `B/compat/jei/ResonatorJei.java`, `lang` (dois), `B/client/dev/E2eJeiResonatorSteps.java`.

- [ ] **Step 1:** `StructureView` (cliente, só JEI): recebe a `ResonatorBill`; monta uma vez a lista `(BlockPos local, BlockState)` dos 98 membros pelo `ResonatorFormation.formedState(defaultState do bloco da Part, member)` (o Núcleo com `TIER` Esmeralda, para a lente ter cor); estado da vista: `yaw` (começa 225°), `pitch` (30°), `zoom` (1), `layer` (5 = Tudo). `draw(GuiGraphics, x, y, w, h)`: `guiGraphics.enableScissor` na área, `PoseStack` com translação ao centro, escala pelo tamanho da área e `zoom`, rotações `pitch` e `yaw` em torno do centro da caixa (2,5; 2,5; 2,5), luz de item (`Lighting.setupFor3DItems`), e cada estado com `Minecraft.getInstance().getBlockRenderer().renderSingleBlock(state, pose, bufferSource, LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY, ModelData.EMPTY, RenderType)` (confira a assinatura do NeoForge 21.1), o Vidro no passe translúcido por último; `bufferSource.endBatch()`; desfaz o scissor. Arrastar: `drag(dx, dy)` muda yaw e pitch (pitch preso entre −10° e 80°); `scroll(delta)` muda o zoom entre 0,6 e 2,5. Sem alocação por quadro além do que o renderer do jogo faz.
- [ ] **Step 2:** `ResonatorStructureCategory implements IRecipeCategory<ResonatorBill>` (ou um `record Structure(Layout, ResonatorBill)` se precisar do nome), `RecipeType` `wirelessautomate:resonator_structure`, largura ~ 160 e altura ~ 150: a vista à esquerda (~ 118 × 118), à direita a coluna de slots (2 colunas de 18 px) com Carcaça, Vidro, Núcleo (Esmeralda), Porta de Energia, Porta de Cristais e Sintonizador, cada um com a quantidade da `count` (`IRecipeSlotBuilder.addItemStack(new ItemStack(item, n))` e, no Sintonizador, `addRichTooltipCallback` com "opcional"); os slots como `RecipeIngredientRole.INPUT` para o U achar a categoria. Embaixo, a linha dos botões de camada (1 a 5 e "Tudo", desenhados pela categoria, o pressionado em destaque) e a linha "Outros arranjos também montam: vidro em qualquer miolo de face, mais Portas" (`GuiText` não existe no JEI: quebre com `font.split` na largura). O título da receita mostra "Vitrine" ou "Faixa". `createRecipeExtras`: `addInputHandler` para os cliques nos botões de camada e `addGuiEventListener` (ou o que a API 19.57 oferecer para arrastar e rodar) que repassa à `StructureView` daquela receita (uma `StructureView` por receita, guardada num mapa da categoria). Confira as interfaces em `mezz.jei.api.gui.inputs` e `mezz.jei.api.gui.widgets` no jar `jei-1.21.1-common-api-19.57.0.451` antes de escrever.
- [ ] **Step 3:** `ResonatorJei` registra a categoria e as duas receitas (`ResonatorBill.of(VITRINE)`, `of(FAIXA)`) e o Núcleo como catalisador; textos nos dois `lang` (título com o nome da montagem, "Camada", "Tudo", "opcional", a linha de rodapé, uma dica na vista: "Arraste para girar · roda: zoom").
- [ ] **Step 4:** `E2eJeiResonatorSteps`: U numa Carcaça de Porcelana no inventário → captura `ressonador-jei-estrutura` (Vitrine); clique na seta da receita seguinte → `ressonador-jei-faixa`; clique no botão de camada 2 → `ressonador-jei-camada`. Rode o e2e, abra as três e descreva: os blocos montados (texturas conectadas, lente na cor Esmeralda, vidro transparente), as quantidades, a camada.
- [ ] **Step 5:** `medir.py` e `varrer.py` nos arquivos do JEI; verificação completa; commit "Ressonador: estrutura em 3D no JEI".

### Task 4: guia, duas páginas

**Arquivos:** mudar `scripts/guide/gerar_guia.py` e os `.md` gerados (em `R/assets/wirelessautomate/guides/wirelessautomate/guide/` e `_pt_br/`); mudar `B/client/dev/E2eGuideSteps.java`.

- [ ] **Step 1:** leia o começo do `gerar_guia.py` (as regras do GuideME, `front`, `item`, `page`, as cenas) e uma página parecida (a da Bateria ou a do Tanque de Source) para seguir o tom e o formato.
- [ ] **Step 2:** `SCENE_RESONATOR` lido de `scripts/guide/resonator_scene.txt` (as 98 linhas) dentro de `<GameScene zoom="3" interactive={true}>` com `<IsometricCamera yaw="..." pitch="30" />` escolhido para a janela de Vitrine e o Núcleo aparecerem, e `BlockAnnotation` (texto em linha própria, regra do GuideME) no Núcleo, numa Porta de Energia, numa Porta de Cristais e no Sintonizador, com os nomes no idioma.
- [ ] **Step 3:** página `signal-quartz.md` (`item_ids`: os dois minérios, os dois cristais): o minério (Overworld, pedra e ardósia, mais comum perto de y = −16, picareta de ferro, 1 a 3 cristais, Fortuna, Toque de Seda), o Cristal de Sinal (10 min gerando), o Lapidado (2× e 2×, só de cristais cheios, com `<RecipeFor id="wirelessautomate:cut_signal_crystal" />`), a carga na barra do item, o Opaco e a recarga (Opaco na câmara + redstone no Núcleo, um por vez, 30 s e 1 pó). Link para `signal-resonator.md`.
- [ ] **Step 4:** página `signal-resonator.md` (`item_ids`: os seis blocos): a cena; Montar (o Núcleo, clique de mão vazia projeta, Shift + roda troca Vitrine e Faixa, a caixa 5×5×5 oca, Carcaça nas arestas, Vidro, Portas e Sintonizador no miolo das faces, pelo menos uma Porta de cada, parede não se divide com outro Ressonador); Sintonizar (clique no Núcleo montado abre a tela, o dial até a lâmpada EM SINTONIA, a estação escorrega e é preciso voltar, roda do mouse no dial); Sintonizador (modo automático, teto por tier); Cristais e redstone (pelo clique, pela tela ou pela Porta de Cristais); a Câmara (como ler a sintonia pelo vidro); Energia (tirar pela Porta de Energia com um roteador; link `router.md`); Tiers (Cartões de Upgrade, link `upgrade-cards.md`, tabela por tier com câmaras, FE/t por cristal e teto, usando a mesma marca ¹ do Allthemodium e os padrões do `CoreMath`, num array no topo do script como os outros); Receitas (os seis blocos por `<RecipeFor>`). Link para `signal-quartz.md`.
- [ ] **Step 5:** índice: duas linhas novas em "Itens do mod" (pt e en), com os ícones do Núcleo e do cristal; página de receitas: as receitas do Ressonador. Posições (`position`) depois das páginas dos armazenamentos.
- [ ] **Step 6:** `python -I scripts/guide/gerar_guia.py`; `E2eGuideSteps.GUIDE_PAGES` ganha `signal-quartz` e `signal-resonator`; rode o e2e e abra `guia-signal-quartz*.png` e `guia-signal-resonator*.png` (pt e en): a cena mostra a estrutura montada, as anotações aparecem, nada cortado. Se a cena sair errada (estado solto, textura preta), corrija a cena (Task 1) antes de seguir.
- [ ] **Step 7:** verificação completa e commit: "Ressonador: páginas do guia".

### Task 5: vitrine e o GIF

**Arquivos:** criar `B/client/dev/E2eShowcaseResonator.java`, `scripts/curseforge/gerar_gif.py`, `docs/curseforge/ressonador.gif`; mudar `B/client/dev/E2eShowcase.java` (e, só se for preciso um lugar livre, `E2eShowcaseWorld.java`).

- [ ] **Step 1:** leia `E2eShowcaseWorld` (a casa, o `showBase` e o que cada ala ocupa) e `E2eResonatorSteps` (como monta uma Vitrine: `place`, `ResonatorBlueprint.expected(Face.EAST, VITRINE)`, cristais e dial pelo servidor). Escolha um lugar livre na casa (ou no jardim) para uma Vitrine Esmeralda com a janela virada para um ponto de câmera aberto.
- [ ] **Step 2:** `E2eShowcaseResonator.steps(List<Step>)`: monta a Vitrine (Núcleo Esmeralda, quatro Cristais de Sinal cheios, buffer com espaço), espera montar; põe o dial a +1,9 MHz da estação (leia a estação no servidor pelo block entity; não mostre); posiciona a câmera pela janela (como o `showLook`, GUI escondida); espera 60 ticks (câmara em chiado, cenas assentadas); `/tick freeze` pelo servidor (`server.tickRateManager().setFrozen(true)` ou o comando); depois 120 passos, cada um: aplica o dial do roteiro para o quadro `i` (`i < 25`: +1,9; `25 ≤ i < 70`: interpolação suave até 0,0 de desvio; depois: a estação), `tickRateManager().requestGameToStep(1)` (ou `/tick step 1`), espera o `ClientLevel.getGameTime()` avançar e um quadro desenhar, captura `gif/ressonador-%03d.png` dentro do diretório da vitrine; no fim, `/tick unfreeze`. Se o tempo congelado não avançar as texturas animadas (compare a placa de Chladni em dois quadros seguidos), use o plano B: sem congelar, capture um quadro por tick do cliente por 120 ticks, mudando o dial a cada tick. Registre no log qual caminho foi usado.
- [ ] **Step 3:** `E2eShowcase.showcase()` chama os passos novos (no fim, para não atrapalhar as outras fotos).
- [ ] **Step 4:** `scripts/curseforge/gerar_gif.py <pasta dos quadros> [saída]`: lê os 120 PNG em ordem, recorta 16:9 centrado, reduz para 640 × 360 (LANCZOS), quantiza cada quadro (`Image.quantize(colors=256, method=Image.Quantize.MEDIANCUT)` ou `ADAPTIVE`), salva GIF com `duration=50`, `loop=0`, `optimize=True`, `disposal=1`; imprime o tamanho; padrão `docs/curseforge/ressonador.gif`. Se passar de 6 MB, reduza a paleta para 192 e tente de novo, e diga o tamanho final.
- [ ] **Step 5:** `WA_SHOWCASE="$PWD/run/showcase" ./gradlew runClient`; rode o script; abra 4 quadros (0, 30, 72, 110) e descreva: chiado (rabisco), dial andando, o clarão, em sintonia (figura fechada, feixes). Confira que o GIF faz loop e o tamanho.
- [ ] **Step 6:** verificação completa (a vitrine não roda no build; o e2e comum deve continuar `OK`) e commit: "Ressonador: GIF da vitrine" (com o `docs/curseforge/ressonador.gif`).

### Task 6: docs e fechamento da etapa

**Arquivos:** `CLAUDE.md`, `docs/progresso.md`, `docs/especificacao.md`.

- [ ] **Step 1:** `CLAUDE.md`: no mapa, `compat/jei/` com as categorias do Ressonador (`ResonatorJei`, `ResonatorStructureCategory`, `StructureView`, `ResonatorRechargeCategory`), `ResonatorBill` na lista de lógica pura com JUnit e no parágrafo do `resonator/`, a cena do guia (`scripts/guide/resonator_scene.txt` pelo `ResonatorGuideSceneTest`), `E2eJeiResonatorSteps` e `E2eShowcaseResonator` no `client/`, `gerar_gif.py` em `scripts/curseforge/`.
- [ ] **Step 2:** `docs/especificacao.md`: o item do Ressonador no roadmap marcado como pronto (etapas 1 a 4).
- [ ] **Step 3:** `docs/progresso.md`: a linha do Ressonador ✅ (etapa 4: JEI, guia e GIF; sem Tablet por decisão do dono), o "Próximo passo" (o Ressonador pronto no ramo `ressonador`; falta o dono testar no jogo, o e2e completo antes do merge e a versão pela `wa-release`, com a nota da energia do Unobtainium no README e na descrição e o GIF na página do CurseForge), a pendência dos mineradores retirada, e uma linha no histórico com os números reais de JUnit e GameTests das cinco runs e "e2e OK".
- [ ] **Step 4:** verificação completa e commit: "Ressonador: docs da etapa 4".
