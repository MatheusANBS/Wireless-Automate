# Wireless Automate · guia para agentes

Mod NeoForge 1.21.1 (Java 21) de transporte wireless de itens, fluidos, energia e químicos, feito para o ATM10. A conversa com o dono é em português; código, comentários e docs também.

## Leia antes de começar

1. **`docs/progresso.md`**: o que está pronto, o que falta e o **próximo passo**. Quando pedirem para "continuar de onde parou", comece por ali.
2. **`docs/especificacao.md`**: a fonte da verdade do design (componentes, tiers, telas, filtros, redes, arquitetura de performance, roadmap).
3. `docs/pacote-de-design.md`: convenções dos sprites e do modelo do roteador.

Ao terminar uma sessão, **atualize `docs/progresso.md`**: a tabela de estado, o próximo passo e uma linha no histórico.

## Skills do projeto (`.claude/skills/`)

- `wa-feature`: feature, tela ou item novo, do mockup aprovado ao commit, com um subagente por tarefa.
- `wa-mod-opcional`: integrar outro mod opcional (como Mekanism e Ars), sem quebrar o mod quando ele não está instalado.
- `wa-release`: fechar uma versão (changelog separado e unificado, tag, release com o jar).
- `wa-curseforge-imagens`: imagens da página do CurseForge pela vitrine e troca dos links na descrição.

## Mapa do código

Pacote base: `src/main/java/io/github/matheusanbs/wirelessautomate/`

| Arquivo | Papel |
| --- | --- |
| `WirelessAutomate.java` | Classe `@Mod`: registra os DeferredRegisters e a config, e escuta os eventos de tick, parada do servidor e comandos |
| `Config.java` | Config do servidor, por seção: `performance` (`tickBudgetMs`, `adaptiveBudget`), `tiers` (vazão, alcance e dimensão por tier), `chunkLoading` (`enabled`, `maxChunksPerPlayer`), `linker` (`maxAreaVolume`, `maxDistance`, valem também para o Configurador) `guide.giveOnFirstJoin` e `migration.balanceVersion` (na primeira carga, `migrateBalance()` passa para o balanceamento ×8 os valores ainda no padrão antigo) |
| `block/RouterBlock.java` | Bloco: `FACING` (face da máquina onde foi preso), `SPIN` (giro 0 a 3 em torno do eixo do `FACING`; Shift + clique direito com as mãos vazias gira 90° pelo `RouterBlockEntity.spinTo`, que remapeia os lados para a configuração das faces absolutas não mudar), `TIER`, `canSurvive`, `tryUpgrade` |
| `block/RouterBlockEntity.java` | Dados do nó: rede por tipo (`networkId(type)`), cartões por face, `FaceConfig` por tipo e lado relativo, `powered` e os `BlockCapabilityCache` da máquina. Não faz tick: se registra no `NetworkManager` em `onLoad` e avisa `nodeChanged` quando muda |
| `block/RouterShapes.java` | Formas de colisão por `facing` e `spin` (`get(facing, spin)`), pela mesma convenção do blockstate |
| `block/RouterTier.java`, `TierLadder.java` | Enum dos oito tiers (Básico, Avançado, Elite, Esmeralda, Allthemodium, Vibranium, Unobtainium, Ultimate) com alcance, dimensão e o mod exigido; `next()`/`previous()` pulam os do Allthemodium sem o mod (lógica pura na `TierLadder`, com JUnit). Gravado pelo nome; a posição indexa as vazões e capacidades padrão |
| `network/NetworkManager.java` | Gerenciador central, um por servidor: remonta as rotas sujas e roda o laço de transferência dentro do orçamento |
| `network/NetworkRoutes.java`, `Port.java`, `NodePorts.java` | Rotas de uma rede: portas (nó, face, tipo) com vazão, cursor e sono que sobrevivem às remontagens |
| `network/ItemTransfer.java`, `FluidTransfer.java`, `ScalarTransfer.java` | Uma visita de uma origem, por tipo de recurso (energia e Source pelo `ScalarTransfer`, com um `ScalarAccess` cada: `EnergyAccess` e o da Source) |
| `network/NetworkSavedData.java`, `WaNetwork.java`, `WaGroup.java` | Redes, rede ativa por jogador e grupos de redes (só para organizar no Tablet, com pausar e retomar), salvos no overworld |
| `network/FaceConfig.java`, `RelativeSide.java`, `RouterPreset.java` | Configuração de uma face (modo, prioridade, redstone), lados relativos ao `facing` + `spin` e o preset copiável |
| `network/TickBudget.java`, `RateLimiter.java`, `RoundRobinOrder.java`, `Backoff.java`, `EnergySplit.java`, `ReachBox.java`, `SourceCursor.java` | Lógica pura, testada por JUnit; `SourceSleep` (ao lado) faz a origem sem destino acordado dormir até o primeiro destino acordar e grava o motivo do sono (vazia × espera destino) (também `network/ResourceType.java`, `client/RateFormat.java`, `client/TabLayout.java`, `client/CardGrid.java`, `client/ResizeHandle.java`, `linker/LinkerBox.java`, `linker/LinkerTabs.java`, `filter/StockLimit.java`, `preset/PasteTypes.java` e `client/EnchantSearch.java`) |
| `network/ResourceType.java`, `LoadedTypes.java` | O registro dos tipos de recurso (Itens, Fluidos, Energia, Químicos, Source): chave, se tem filtro e cartões, chave da vazão na config, vazão padrão e mod exigido. Tela, Vinculador, Configurador, Tablet e config leem daqui; um tipo novo é uma entrada nova, mais o seu caso no `switch` sem `default` do `NetworkManager.visit`. `LoadedTypes` diz quais estão carregados (Químicos só com o Mekanism, Source só com o Ars Nouveau). As chaves de NBT, componentes e config não mudam |
| `network/PortMode.java`, `RedstoneMode.java` | Modo de face e controle por redstone |
| `filter/` | `Filter`/`FilterEntry` (modelo imutável com codecs), `ItemRule` (regra de item por propriedade: encantado, danificado, encantamento com nível, durabilidade, "só em" tag ou mod; vira a `FilterEntry.RuleEntry`), `FilterSet` (embutido + cartões), matchers compilados com cache (as regras numa lista, perguntadas em ordem), `FilterTags` (recarga de tags), `StockLimit` e `FilterCodecs.LENIENT` |
| `item/` | `TierCoreItem`, `RouterBlockItem`, `LinkerItem`, `ConfiguratorItem`, `FilterCardItem`, `NetworkTabletItem`, `ChunkLoaderUpgradeItem` e `GuideBook` (o livro do GuideME: aba criativa e entrega no primeiro login, só pelos registros) |
| `linker/` | Vinculador: modo (`LinkerMode`, Único ou Área), abas marcadas (`LinkerTabs`, lógica pura com JUnit), a área (`LinkerArea`, `LinkerBox`, `LinkerScan`) e as regras de vincular e desvincular (`LinkerActions`) |
| `preset/` | Configurador: regra das redes ao colar (`PresetApplier`), colar em área na mesma máquina (`ConfiguratorArea`, usa a área do Vinculador) e o seletor do tipo colado (`PasteTypes`, lógica pura com JUnit) |
| `storage/` | Armazenamentos do mod (0.2): Baú, Tanque, Bateria, Tanque Químico e Tanque de Source (`StorageKind`; o de Source é sempre registrado e some da aba criativa, do JEI e da receita sem o Ars), um bloco (`StorageBlock`), um item (`StorageBlockItem`) e um block entity por tipo sobre a base `StorageBlockEntity` (tier, capacidade pela config, filtro de entrada, quebrar e colocar pelo `StorageSavedData` com o tipo e o tier, comparador). Conteúdo por tipo em `KeyedStorage<K>` (`ItemStorage`, `FluidStorage`, `ChemicalStorage` por id, sem tipos do Mekanism) ou `ScalarStore` (energia e Source, em `long`; a base dos dois block entities é `ScalarStorageBlockEntity`); visões comuns `ItemStorageHandler`, `FluidStorageHandler`, `EnergyStoreHandler` (a de químico em `compat/mekanism/MekanismStorage`); `BulkItems`, `BulkFluids`, `BulkEnergy` e `BulkSource` (capability `wirelessautomate:bulk_source`) para o roteador (as APIs do NeoForge tiram uma pilha, ou até 2^31 mB/FE, por chamada; a de químico já é `long`); `StorageMath` com JUnit (inclusive o nível `fill` 0 a 10 que o Tanque de Source mostra na coluna de vidro; o nível se corrige no tick seguinte à carga e, com o jogo rodando, quando a config muda, pelo `SourceTankLevels`, o conjunto dos tanques carregados no servidor; o tick é agendado no `onLoad`) |
| `chunk/` | Upgrade de chunk loading: tickets do NeoForge (`RouterChunkLoader`), eventos de tick, config e parada (`ChunkLoaderEvents`) e o estado mostrado na tela (`ChunkLoadState`) |
| `network/NodeIndex.java`, `NodeProbe.java` | Índice persistente dos nós (Tablet) e status de cada nó |
| `registry/` | `ModBlocks`, `ModItems`, `ModBlockEntities`, `ModCreativeTabs`, `ModDataComponents`, `ModMenus`, `ModRecipes` |
| `recipe/` | `FilterCardCopyRecipe` (cartão configurado + vazio = dois iguais) e `RouterUpgradeRecipe` (roteador + Cartão de Upgrade do tier seguinte na bancada, também no JEI) |
| `compat/jei/` | Plugin opcional do JEI; nada fora desse pacote referencia o JEI |
| `compat/mekanism/`, `network/Chemicals.java`, `network/ChemicalTransfer.java` | Químicos do Mekanism (só a API dele). O resto do mod passa pela ponte `Chemicals` (`Chemicals.LOADED`), sem tipos do Mekanism |
| `bench/` | `/wa bench` e o modo automático do `scripts/bench.sh` |
| `compat/arsnouveau/`, `network/Sources.java` | Source do Ars Nouveau (só a API dele, em `ArsSources`: a capability `ars_nouveau:source` e o `ScalarAccess` da Source). O resto do mod passa pela ponte `Sources` (`Sources.LOADED`), sem tipos do Ars. `ArsStorage` dá ao Tanque de Source a capability `ISourceCap` (roteador e Relays) e o provider no `SourceManager` do Ars (Sourcelinks a 5 blocos depositam, máquinas do Ars tiram); a visão `int` é limitada a `Integer.MAX_VALUE` |
| `command/WaCommand.java` | `/wa profile`, `/wa network ...`, `/wa face ...` (os tipos são os do registro, inclusive `chemical` com o Mekanism), `/wa bench ...` e `/wa storage list` e `recover` (`StorageCommand`: devolve como item o conteúdo de um armazenamento que sumiu, no tier gravado) |
| `menu/RouterMenu.java`, `RouterSnapshot.java`, `RemoteRouterMenu.java` | Menu da tela do roteador (slots de cartão da face selecionada e o do upgrade de chunk loading), o snapshot que o servidor manda só com a tela aberta e a mesma tela aberta à distância pelo Tablet |
| `menu/Tablet*`, `Linker*` | Menus e snapshots das telas do Tablet e do Vinculador (o Configurador não tem tela) |
| `menu/StorageList*`, `ListKind`, `StorageScalarMenu`, `StorageFilterTarget` | Telas dos armazenamentos: a lista genérica do Baú e dos Tanques (`StorageListMenu<K>` com a visão `StorageListView<K>`; só o inventário do jogador como slots, só as diferenças por tipo a cada 5 ticks e com a tela aberta, cliques validados no servidor; `ListKind` diz como cada chave viaja e se compara), a da Bateria e do Tanque de Source (`StorageScalarMenu`, estado pelo `ScalarStatePayload`) e o filtro de entrada na tela de filtro |
| `menu/FilterMenu.java`, `FilterView.java`, `FilterTarget.java` | Tela de filtro: de uma face (`RouterFaceFilterTarget`) ou de um cartão (`CardFilterTarget`); Shift + clique no inventário adiciona; o inventário muda de lugar com a tela (`placeInventory`). Tags, mods e regras chegam pelo `FilterEntriesPayload` (vários de uma vez, ou trocar uma regra editada) |
| `packet/` | Payloads cliente↔servidor da tela e o registro com os handlers (`ModPayloads`, protocolo `9`); o servidor valida tudo |
| `client/` | Só cliente. O roteador, o Tablet e o Vinculador são redimensionáveis pela borda e pela alça do canto (mínimo 300 × 240, o do Vinculador 300 × 204, máximo a janela menos 8 px; no roteador a largura extra vai para o visor 3D e as abas), como o Filtro e o Baú: `RouterScreen`, `MachineView3D` (visor 3D), `FilterScreen` (lista redimensionável com abas Entrada, Tags com inspetor e busca, Regra e Mais; na Regra, o encantamento por nome com sugestões pelo `EnchantSearch`, puro com JUnit, e o nível de 1 a 255), `StorageListScreen` (lista do Baú e dos Tanques com busca e ordenação, redimensionável em torno do centro), `StorageScalarScreen` (Bateria e Tanque de Source), `TabletScreen` (Estatísticas com um cartão por tipo; clicar num cartão filtra a Lista por tipo), `LinkerScreen`, `ResourceStyle` (cor, ícone, nome e vazão de cada tipo, a partir do registro), `GuiText` (todo texto variável das telas passa por ele: abrevia ou quebra e dá o tooltip com o texto inteiro), `TabLayout` e `CardGrid` (lógica pura com JUnit: abas adaptáveis e grade de cartões), `ResizeHandle` (pura, com JUnit) e `ResizeGrip` (o desenho da alça), `AreaRenderer` (contorno da área do Vinculador e do Configurador), `LinkerScrollHandler` (Shift + roda do Vinculador e do Configurador), widgets, `ClientSetup`, `DevScreenshot` (capturas com `WA_SCREENSHOT`; `WA_SCREENSHOT_ONLY=<prefixo>` filtra as capturas) e `DevEndToEnd` (teste num mundo real com `WA_E2E`) |
| `gametest/` | GameTests (template `empty`), 165 na run comum: roteador, configuração, giro (`RouterSpinGameTests`), redes por tipo, Armazém, transferência, sono e acordar (`WakeGameTests`), filtros, slots de cartão, menus, payloads do JEI, Configurador, área do Vinculador, Tablet, chunk loading, receitas e os armazenamentos do mod (`StorageGameTests`) e as regras por propriedade (`RuleFilterGameTests`); `TestMachines` liga máquinas de teste (pilha enorme, 20 tanques, tanque que devolve a pilha interna como o Mekanism) só com `-Dwirelessautomate.gameTests=true`. `ChemicalGameTests` (7 testes, com o Tanque Químico) fica no namespace `wirelessautomate_chemicals` e só roda na run `gameTestServerChemicals`. `SourceGameTests` (15 testes, Source Jars de verdade e o Tanque de Source) fica no namespace `wirelessautomate_source` e só roda na run `gameTestServerSource`. `AtmGameTests` (2 testes: a escada de oito degraus e as receitas do ATM, com os itens reais) fica no namespace `wirelessautomate_atm` e só roda na run `gameTestServerAllthemodium` |
| `scripts/textures/gerar_texturas.py` | Gera todas as texturas (PIL) e a folha `docs/preview/folha-de-sprites.png`; edite as paletas ali, não os PNGs; gera também os modelos de bloco e de item e os blockstates do roteador (com os modelos `router_<tier>_spin1..3` por giro, pela transformação raiz do NeoForge), dos armazenamentos e dos cartões por tier (o `router_basic.json` é o molde escrito à mão) e os do Tanque de Source |
| `scripts/guide/gerar_guia.py` | Gera as páginas do livro-guia (GuideME) em inglês e português, lado a lado; edite ali, não os `.md` |
| `scripts/curseforge/` | Capa (`gerar_capa.py`, 400x400) e banner e imagens da descrição (`gerar_imagens.py`) do CurseForge, em `docs/curseforge/`. As fotos vêm da vitrine: `WA_SHOWCASE=run/showcase ./gradlew runClient` (modo do `DevEndToEnd`) |

Recursos em `src/main/resources/`:
- `assets/wirelessautomate/`: blockstates, modelos, texturas e `lang/` (en_us e pt_br; mantenha os dois em dia).
- `assets/wirelessautomate/guideme_guides/guide.json` e `guides/wirelessautomate/guide/`: o livro-guia do GuideME (opcional, sem código), páginas em inglês na pasta e em português em `_pt_br/`, geradas por `scripts/guide/gerar_guia.py`. O guia é para o jogador: só como operar (nada de config, ms/tick ou detalhes internos). Mantenha as especificações (tabela de tiers, gestos) em dia com o código. O e2e abre cada página nos dois idiomas, rola e salva `guia-<página>[-pt][-2|-3].png` quando o GuideME está presente.
- `data/`: loot table, receita, tags e `wirelessautomate/structure/empty.nbt` (estrutura 3×3×3 vazia dos GameTests).
- `src/main/templates/META-INF/neoforge.mods.toml`: preenchido pelo Gradle a partir do `gradle.properties`.

## Comandos

```bash
./scripts/setup.sh          # instala o JDK 21 etc. e compila (use --gametest / --no-build)
./gradlew build             # compila + JUnit; jar em build/libs/
./gradlew test              # só JUnit
./gradlew runGameTestServer # GameTests headless (sem o Mekanism); falha o build se algum teste falhar
./gradlew runGameTestServerChemicals # GameTests de químicos, num servidor com o Mekanism na pasta mods
./gradlew runGameTestServerSource # GameTests de Source, num servidor com o Ars Nouveau (e GeckoLib e Curios) na pasta mods
./gradlew runGameTestServerAllthemodium # GameTests dos tiers do ATM, com o Allthemodium, o All The Tweaks e o GeckoLib (cursemaven)
./gradlew runClient         # cliente de dev com JEI, Sophisticated Storage, Observable, Mekanism, Ars Nouveau (com GeckoLib e Curios), Allthemodium, All The Tweaks e GuideME, para testar à mão
./gradlew runData           # datagen para src/generated/resources/
WA_SCREENSHOT=run/shots xvfb-run -a -s "-screen 0 1280x800x24" ./gradlew runClient  # capturas das telas (roteador, visor 3D, filtro), sem monitor
./scripts/e2e.sh            # teste de ponta a ponta num mundo real (precisa de Xvfb; alguns minutos, com as capturas do guia; força a escala 2 da interface quando a janela é pequena e faz uma volta em pt_br com rede de nome longo, conferindo o tooltip dos textos cortados)
./scripts/bench.sh <cenários> # benchmark num servidor dedicado com Sophisticated Storage (ver docs/benchmark.md)
```

Antes de commitar, rode `./gradlew build runGameTestServer runGameTestServerChemicals runGameTestServerSource runGameTestServerAllthemodium` (e o `./scripts/e2e.sh` se mexeu em tela ou payload). O CI (`.github/workflows/build.yml`) roda os GameTests.

## Convenções e armadilhas

- **Pastas de dados do 1.21.1 são no singular:** `recipe/`, `loot_table/`, `structure/`, `tags/block/`, `tags/item/`.
- **GameTests:** a classe leva `@GameTestHolder(WirelessAutomate.MODID)` e `@PrefixGameTestTemplate(false)`, e o template é `"empty"`. Os testes do mesmo lote rodam em paralelo e dividem o `NetworkManager`, então teste pertinência (`contains`) e não contagem. Também dividem o `NetworkSavedData`: cada teste cria a própria rede (`NetworkSavedData.get(server).create(...)`), porque um `networkId` que não existe lá deixa o roteador parado. O `onLoad` de um block entity recém-colocado só roda no tick seguinte: use `startSequence().thenWaitUntil(...)`.
- **Lógica pura sem classes do Minecraft** (como `TickBudget`) vai com teste JUnit em `src/test/java`. Lógica que depende do jogo vai com GameTest.
- **Performance é requisito**, não detalhe (ver "Arquitetura de performance" na especificação):
  - nada de tick por bloco;
  - nada de busca de capability por tick, use `BlockCapabilityCache`;
  - nada de sincronizar o cliente com a tela fechada.
- **Mekanism é opcional:** tipos da API dele (`IChemicalHandler`, `ChemicalStack`...) só em `compat/mekanism`, `ChemicalTransfer` e `gametest/ChemicalTestSupport`, e **nunca na assinatura de um método** de classe `@EventBusSubscriber` ou `@GameTestHolder`: o NeoForge inspeciona essas classes por reflexão e o mod deixa de carregar sem o Mekanism. O `runGameTestServer` roda sem ele e pega isso.
- **Ars Nouveau é opcional:** tipos da API dele só em `compat/arsnouveau` e `gametest/SourceTestSupport`, e nunca na assinatura de um método de classe `@EventBusSubscriber` ou `@GameTestHolder` (mesmo motivo do Mekanism).
- **Allthemodium é opcional e sem código:** os tiers Allthemodium, Vibranium e Unobtainium existem sempre no enum (o bloco continua valendo se o mod sair) e só entram na escada, na aba criativa e nas receitas com o mod (`RouterTier.loaded()`). Receitas por tags `c:` e `neoforge:conditions`; o Ultimate tem três arquivos e só um carrega (sem ATM, com ATM, com ATM + All The Tweaks). Nada de tipos ou `compileOnly` desses mods.
- **Tiers:** vazão e capacidade padrão vêm de arrays pela posição do `RouterTier` (`ResourceType`, `StorageKind`), ×8 por degrau; um tier novo pede um valor em cada array, a cor no `GuiPaint.tierColor` e a paleta no `gerar_texturas.py`, que gera também os modelos e blockstates por tier.
- **Lado do cliente:** classes de tela só em `client/`, nunca referenciadas por código comum (o `runGameTestServer` é um servidor dedicado e quebra se carregar uma).
- **Memória:** cada build/jogo usa ~3 GB; não rode vários clientes ou servidores ao mesmo tempo (um cliente do e2e morreu assim).
- **Rotação:** o `facing` do roteador segue a convenção do para-raios (`up` sem rotação, `down` x=180, laterais x=90 + y). O `spin` (0 a 3) gira em torno do eixo do `facing`, antes da rotação do `facing`: no caso `up`, horário visto de cima (`getClockWise`), `spin` vezes. Os modelos giram pela transformação raiz (`router_<tier>_spinN`, y = −90 × spin) e a colisão pelo `RouterShapes`. Configurações por face devem ser salvas em relação ao `facing` + `spin` (`RelativeSide`).
- **Primeiro build:** leva uns 4 minutos (baixa e decompila o Minecraft). O erro `Failed to load properties from file: server.properties` no `runGameTestServer` é normal. Os GameTests rodam em `run/gametest`, com o mundo apagado a cada rodada.
- **Wrapper:** `gradle-wrapper.properties` usa `validateDistributionUrl=false`, porque a validação falha atrás do proxy do ambiente na nuvem.
- **Versões:** NeoForge, Parchment e mod ficam no `gradle.properties`. O `neo_version` vira a versão **mínima** exigida no `neoforge.mods.toml`: não suba além da que o ATM10 usa (hoje 21.1.251), senão o mod não carrega no pack. O plugin ModDevGradle fica no `build.gradle`.
