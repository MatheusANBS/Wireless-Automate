# Wireless Automate · guia para agentes

Mod NeoForge 1.21.1 (Java 21) de transporte wireless de itens, fluidos, energia e químicos, feito para o ATM10. A conversa com o dono é em português; código, comentários e docs também.

## Leia antes de começar

1. **`docs/progresso.md`**: o que está pronto, o que falta e o **próximo passo**. Quando pedirem para "continuar de onde parou", comece por ali.
2. **`docs/especificacao.md`**: a fonte da verdade do design (componentes, tiers, telas, filtros, redes, arquitetura de performance, roadmap).
3. `docs/pacote-de-design.md`: convenções dos sprites e do modelo do roteador.

Ao terminar uma sessão, **atualize `docs/progresso.md`**: a tabela de estado, o próximo passo e uma linha no histórico.

## Mapa do código

Pacote base: `src/main/java/io/github/matheusanbs/wirelessautomate/`

| Arquivo | Papel |
| --- | --- |
| `WirelessAutomate.java` | Classe `@Mod`: registra os DeferredRegisters e a config, e escuta os eventos de tick, parada do servidor e comandos |
| `Config.java` | Config do servidor: `TICK_BUDGET_MS`, `ADAPTIVE_BUDGET` e `TIERS` (vazão e alcance por tier) |
| `block/RouterBlock.java` | Bloco: `FACING` (face da máquina onde foi preso), `TIER`, `canSurvive`, `tryUpgrade` |
| `block/RouterBlockEntity.java` | Dados do nó: `networkId`, `FaceConfig` por tipo e lado relativo, `powered` e os `BlockCapabilityCache` da máquina. Não faz tick: se registra no `NetworkManager` em `onLoad` e avisa `nodeChanged` quando muda |
| `block/RouterShapes.java` | Formas de colisão rotacionadas pela mesma convenção do blockstate |
| `block/RouterTier.java` | Enum com os valores padrão da tabela de tiers |
| `network/NetworkManager.java` | Gerenciador central, um por servidor: remonta as rotas sujas e roda o laço de transferência dentro do orçamento |
| `network/NetworkRoutes.java`, `Port.java`, `NodePorts.java` | Rotas de uma rede: portas (nó, face, tipo) com vazão, cursor e sono que sobrevivem às remontagens |
| `network/ItemTransfer.java`, `FluidTransfer.java`, `EnergyTransfer.java` | Uma visita de uma origem, por tipo de recurso |
| `network/NetworkSavedData.java`, `WaNetwork.java` | Redes e rede ativa por jogador, salvas no overworld |
| `network/FaceConfig.java`, `RelativeSide.java`, `RouterPreset.java` | Configuração de uma face (modo, prioridade, redstone), lados relativos ao `facing` e o preset copiável |
| `network/TickBudget.java`, `RateLimiter.java`, `RoundRobinOrder.java`, `Backoff.java`, `EnergySplit.java` | Lógica pura, testada por JUnit |
| `network/PortMode.java`, `ResourceType.java`, `RedstoneMode.java` | Modo de face, tipo de recurso e controle por redstone |
| `filter/` | `Filter`/`FilterEntry` (modelo imutável com codecs), matchers compilados com cache, `FilterTags` (recarga de tags), `StockLimit` e `FilterCodecs.LENIENT` |
| `item/` | `TierCoreItem`, `RouterBlockItem`, `LinkerItem` (modo Único), `ConfiguratorItem` (pincel) e `FilterCardItem` funcionam; Tablet e Chunk loader são stubs |
| `registry/` | `ModBlocks`, `ModItems`, `ModBlockEntities`, `ModCreativeTabs`, `ModDataComponents`, `ModMenus` |
| `command/WaCommand.java` | `/wa profile`, `/wa network ...` e `/wa face ...` |
| `menu/RouterMenu.java`, `RouterSnapshot.java` | Menu da tela do roteador (sem slots) e o snapshot que o servidor manda só com a tela aberta |
| `menu/FilterMenu.java`, `FilterView.java`, `FilterTarget.java` | Tela de filtro: de uma face (`RouterFaceFilterTarget`) ou de um cartão (`CardFilterTarget`); Shift + clique no inventário adiciona |
| `packet/` | Payloads cliente↔servidor da tela e o registro com os handlers (`ModPayloads`); o servidor valida tudo |
| `client/` | Só cliente: `RouterScreen`, `MachineView3D` (visor 3D), `FilterScreen`, widgets, `ClientSetup` e `DevScreenshot` (capturas sem monitor com `WA_SCREENSHOT`) |
| `gametest/` | GameTests (template `empty`): roteador, configuração, redes, Configurador, transferência, menus e filtros |

Recursos em `src/main/resources/`:
- `assets/wirelessautomate/`: blockstates, modelos, texturas e `lang/` (en_us e pt_br; mantenha os dois em dia).
- `data/`: loot table, receita, tags e `wirelessautomate/structure/empty.nbt` (estrutura 3×3×3 vazia dos GameTests).
- `src/main/templates/META-INF/neoforge.mods.toml`: preenchido pelo Gradle a partir do `gradle.properties`.

## Comandos

```bash
./scripts/setup.sh          # instala o JDK 21 etc. e compila (use --gametest / --no-build)
./gradlew build             # compila + JUnit; jar em build/libs/
./gradlew test              # só JUnit
./gradlew runGameTestServer # GameTests headless; falha o build se algum teste falhar
./gradlew runData           # datagen para src/generated/resources/
WA_SCREENSHOT=run/shots xvfb-run -a -s "-screen 0 1280x800x24" ./gradlew runClient  # capturas das telas (roteador, visor 3D, filtro), sem monitor
```

Antes de commitar, rode `./gradlew build runGameTestServer`. O CI (`.github/workflows/build.yml`) roda os dois.

## Convenções e armadilhas

- **Pastas de dados do 1.21.1 são no singular:** `recipe/`, `loot_table/`, `structure/`, `tags/block/`, `tags/item/`.
- **GameTests:** a classe leva `@GameTestHolder(WirelessAutomate.MODID)` e `@PrefixGameTestTemplate(false)`, e o template é `"empty"`. Os testes do mesmo lote rodam em paralelo e dividem o `NetworkManager`, então teste pertinência (`contains`) e não contagem. Também dividem o `NetworkSavedData`: cada teste cria a própria rede (`NetworkSavedData.get(server).create(...)`), porque um `networkId` que não existe lá deixa o roteador parado. O `onLoad` de um block entity recém-colocado só roda no tick seguinte: use `startSequence().thenWaitUntil(...)`.
- **Lógica pura sem classes do Minecraft** (como `TickBudget`) vai com teste JUnit em `src/test/java`. Lógica que depende do jogo vai com GameTest.
- **Performance é requisito**, não detalhe (ver "Arquitetura de performance" na especificação):
  - nada de tick por bloco;
  - nada de busca de capability por tick, use `BlockCapabilityCache`;
  - nada de sincronizar o cliente com a tela fechada.
- **Lado do cliente:** classes de tela só em `client/`, nunca referenciadas por código comum (o `runGameTestServer` é um servidor dedicado e quebra se carregar uma).
- **Rotação:** o `facing` do roteador segue a convenção do para-raios (`up` sem rotação, `down` x=180, laterais x=90 + y). Configurações por face devem ser salvas em relação ao `facing`.
- **Primeiro build:** leva uns 4 minutos (baixa e decompila o Minecraft). O erro `Failed to load properties from file: server.properties` no `runGameTestServer` é normal.
- **Wrapper:** `gradle-wrapper.properties` usa `validateDistributionUrl=false`, porque a validação falha atrás do proxy do ambiente na nuvem.
- **Versões:** NeoForge, Parchment e mod ficam no `gradle.properties`. O plugin ModDevGradle fica no `build.gradle`.
