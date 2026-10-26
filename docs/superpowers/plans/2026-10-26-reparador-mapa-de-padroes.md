# Reparador de Cristais: mapa dos padrões do código (26/10/2026)

Levantado por leitura do código no ramo `reparador-de-cristais`, para o plano da etapa 2. Caminhos com base em `J/` = `src/main/java/io/github/matheusanbs/wirelessautomate/`, `R/` = `src/main/resources/`. Linhas são do dia; confira antes de usar. "Não existe" marca o que foi procurado e não achado.

## Surpresas e riscos (leia primeiro)

1. **Não existe "tick agendado recorrente" no mod.** `scheduleTick` só aparece one-shot (delay 1, sem reagendar): `J/resonator/ResonanceCoreBlockEntity.java:520`, `ResonatorManager.java:142,157`, `J/storage/StorageSourceTankBlockEntity.java:31`. O trabalho contínuo roda em gerenciadores estáticos por servidor (`ResonatorManager`, `SourceTankLevels`), registrados no `onLoad` e retirados em `setRemoved`/`onChunkUnloaded`, com `reset()` em `WirelessAutomate.onServerStopped` (linhas 104-116). Não há `BlockEntityTicker` (`getTicker` não existe no mod).
2. **Modelos:** `scripts/textures/blocos.py` apaga todo `models/block/*.json` (nível raiz) que ele não gerou (`blocos.py:1655-1658`) e `gerar_texturas.limpar` (linha 1868) apaga PNG de `block/`, `item/` e `gui/` que não saíram. A classe `Modelo` (`blocos.py:896`) proíbe elementos que se atravessam e faces coplanares (`validar_geometria`, 1507), aceita rotação só de -45 a 45 em passos de 22,5 (`ANGULOS`, 1363), tem um `render_type` por modelo inteiro (933-942) e sprites de 16 px. Não existe `light_emission`, `multipart` nem bloco com `HORIZONTAL_FACING` (as peças e o roteador usam `FACING` de 6 direções).
3. **Render do cristal:** `CrystalGem`, `CrystalMesh`, `CrystalParam`, `RibbonQuad`, `FlowConduit`, `ChamberRenderTypes`, `ChamberScene` são `final class` package-private em `J/client/resonator/render/` (públicos só `ResonatorRenderer`, `CrystalShader`, `PlasmaShader`, `RibbonShader`). Os efeitos da animação (progresso nas rachaduras, dissolução, modo 3 da fita) não existem: é trabalho da etapa 3.
4. `StreamCodec.composite` só vai até 6 campos (`J/packet/ResonatorStatePayload.java:19-26` usa `StreamCodec.of`); payload novo exige bump de `ModPayloads.VERSION` (hoje `"32"`, `ModPayloads.java:67`).
5. `RecastCrystalRecipe` (`J/resonator/RecastCrystalRecipe.java:34-50`) conta o cristal de Sinal comum (`ModItems.SIGNAL_CRYSTAL`) com `SignalCrystalItem.isFull` (só a carga, não a integridade); o rachado volta sem os componentes `crystal_charge` e `crystal_integrity`.
6. `ItemStackHandler`/`InvWrapper` do NeoForge não são usados em lugar nenhum do mod; o `CoreInventory` é acoplado ao Núcleo. O Reparador precisa de um `Container` próprio.
7. Um destino de roteador "cheio" dorme: quem esvazia um slot ou consome energia precisa avisar os vizinhos nas transições (padrão `ResonanceCoreBlockEntity.notifyPorts`, `~429-433`, e `StorageNeighbors`), senão roteadores ficam dormindo.

## 1. Registro (bloco, BlockItem, block entity, aba criativa)

- Bloco: `J/registry/ModBlocks.java`. Peça do Ressonador: linha 100-102 (`BLOCKS.registerBlock("resonator_energy_port", props -> new ResonatorPartBlock(...), resonatorPart())`); `resonatorPart()` 86-88 = `stone().pushReaction(PushReaction.BLOCK)`; `stone()` 77-80 (`mapColor(QUARTZ).strength(3,6).sound(STONE).requiresCorrectToolForDrops()`). Armazenamentos 42-55 com `.noOcclusion()` (modelo que não preenche o cubo).
- Item: `J/registry/ModItems.java:137-142` (`ITEMS.registerSimpleBlockItem(ModBlocks.X)`); com classe própria, 139-140 (`ITEMS.register("resonance_core", () -> new ResonanceCoreItem(...))`).
- Block entity: `J/registry/ModBlockEntities.java:39-41` (`BLOCK_ENTITY_TYPES.register("resonance_core", () -> BlockEntityType.Builder.of(ResonanceCoreBlockEntity::new, ModBlocks.RESONANCE_CORE.get()).build(null))`).
- Aba criativa: `J/registry/ModCreativeTabs.java`, aba única `MAIN` (21-92), peças do Ressonador em 80-89 (`output.accept(ModItems.RESONATOR_TUNER.get())` na 89), guia fecha na 90.
- Menus: `J/registry/ModMenus.java:44-45` (`MENU_TYPES.register("resonator", () -> IMenuTypeExtension.create(ResonatorMenu::new))`).
- Dados: loot table `R/data/wirelessautomate/loot_table/blocks/<id>.json` (pool `minecraft:item` + `survives_explosion`, `random_sequence "wirelessautomate:blocks/<id>"`) e o id em `R/data/minecraft/tags/block/mineable/pickaxe.json` (lista manual).
- Registro dos DeferredRegisters e listeners: `J/WirelessAutomate.java:51-61` (`modEventBus.addListener(StorageCapabilities::register)` linha 59, `ResonatorCapabilities::register` linha 60).

## 2. Block entity com inventário, NBT, update tag

- Análogo: `J/resonator/ResonanceCoreBlockEntity.java` (599 linhas) com `CoreNbt` (`saveCrystals`/`loadCrystals`, `CoreNbt.java:134-160`: `ListTag`, `entry.putByte("slot", ...)`, `stack.save(registries, entry)`, `ItemStack.parse(registries, entry).orElse(EMPTY)`).
- `saveAdditional` 543-560, `loadAdditional` 580-598. `setChanged` econômico: `markUnsaved()` 354-358 chama só `level.blockEntityChanged(worldPosition)` (não avisa vizinhos); `setChanged()` só nas transições.
- Update tag só quando muda: `syncIfChanged` 467-469, `getUpdateTag` 491, `getUpdatePacket` (`ClientboundBlockEntityDataPacket.create(this)`), `handleUpdateTag`, `onDataPacket`; lógica em `J/resonator/CoreSync.java:23-42` e view imutável `J/resonator/ChamberView.java`.
- `onLoad` 512-522 e `setRemoved` 524-528/`onChunkUnloaded` 530-534 registram e retiram do gerenciador (`CorePresence`).
- Drop ao quebrar: `J/resonator/ResonanceCoreBlock.java:212-231` (`onRemove` faz `Containers.dropItemStack` por pilha). Bloco: `BaseEntityBlock` com `MapCodec` (`simpleCodec`), `getRenderShape -> MODEL`, `newBlockEntity`, `useWithoutItem` (abre a tela só no servidor).
- Facing: `J/resonator/ResonatorPartBlock.java:35` (`FACING = BlockStateProperties.FACING`), `getStateForPlacement` 68-71.

## 3. Gerenciador com trabalho só quando há (padrão a seguir)

- `J/storage/SourceTankLevels.java` (53 linhas): conjunto por identidade, `tick()` barato sem mudança, `add`/`remove`, `reset()`. O BE se registra no `onLoad` (`StorageSourceTankBlockEntity.java:23-33`) e sai em `setRemoved` (36-41) e `onChunkUnloaded` (44-49). Chamado em `WirelessAutomate.onServerTick` (linha ~89) e limpo em `onServerStopped` (~109).
- `J/resonator/ResonatorManager.java:179-215`: `if (CORES.isEmpty()) return;`, `server.overworld().getGameTime()`, `level.shouldTickBlocksAt(ChunkPos.asLong(pos))`. Perfil no `/wa profile`: `J/resonator/ResonatorProfile.java`, `J/command/WaCommand.java:172-173`.
- Cliente anima por `gameTime` de início sem pacote por tick: `CoreSync.resolveRechargeStart` (`CoreSync.java:49-58`), `ChamberCrystals.recharge(...)` (`J/client/resonator/render/ChamberCrystals.java`: `Math.clamp((ticks - view.rechargeStart()) / view.rechargeTicks(), 0.0, 1.0)`).

## 4. Capabilities

- `J/storage/StorageCapabilities.java:19-36`: `event.registerBlockEntity(Capabilities.EnergyStorage.BLOCK, ModBlockEntities.BATTERY.get(), (battery, side) -> battery.handler())` e `BulkEnergy.BLOCK` (`(battery, side) -> battery.store()`); item: `Capabilities.ItemHandler.BLOCK` do Baú. Listener em `WirelessAutomate.java:59`.
- `BulkEnergy` (`J/storage/BulkEnergy.java`): `BlockCapability<BulkEnergy, @Nullable Direction> BLOCK`; `long insert(long, boolean simulate)`, `long extract(long, boolean simulate)`. É o que o roteador consulta antes da energia do NeoForge (sem ela, 2^31 FE por chamada).
- `J/storage/EnergyStoreHandler.java`: `IEnergyStorage` sobre um `ScalarStore` (`receiveEnergy`: `(int) store.insert(toReceive, simulate)`; `getEnergyStored` e `getMaxEnergyStored` cortados em `Integer.MAX_VALUE`; `canExtract`/`canReceive`). `J/storage/ScalarStore.java`: `stored`/`capacity` em `long` (`LongSupplier capacity`, `<= 0` = sem limite), `insert`/`extract`/`set`/`version()`, `onChange` por mudança real.
- `IItemHandler` com regra por slot: `J/resonator/ResonatorPorts.java:146-226` (`record CoreItems(core) implements IItemHandler`): `isItemValid` 211-225, `insertItem` 169-190 (devolve `stack` se inválido, `copyWithCount`; respeita `simulate`), `extractItem` 193-202, `getSlotLimit` 205-207.
- Capability só de saída (inverso do que o Reparador precisa): `ResonatorPorts.CoreEnergyHandler` 98-137.

## 5. Menu e tela

- `J/menu/ResonatorMenu.java` (396 linhas): construtor servidor (67-69), cliente por buffer (72-74), cliente com `SimpleContainer` (77-80); slots em loops (91-106) com `ChamberSlot extends Slot` (`mayPlace`, `getMaxStackSize`, 336-350); `open` 113-122 (`player.openMenu(new SimpleMenuProvider(...), buf -> { BlockPos.STREAM_CODEC.encode(buf, pos); ... })`); `pollState(long now)` 174-185 (não manda se `now - lastSent < StorageListMenu.SYNC_INTERVAL` (=5) nem se `current.equals(sent)`); `broadcastChanges` 300-314 (`viewer.connection.hasChannel(payload)`, `PacketDistributor.sendToPlayer`); `onState` 329-333; `stillValid` 296-298; `quickMoveStack` 219-266.
- Variante sem slots: `J/menu/StorageScalarMenu.java:119-142`.
- Tela: `J/client/resonator/screen/ResonatorScreen.java` (216 linhas; `AbstractContainerScreen`, `renderBg` 117-148 com `GuiPaint.panel(g, x, y, W, H, trim)`, `GuiText.beginFrame()` no `render`, `GuiPaint.slot(g, x + slot.x - 1, y + slot.y - 1)`); layout em `CoreScreenLayout` (record `Rect`); barra sobre o slot em `CrystalSlots.renderBar`; limite de tamanho de classe de tela 500 linhas (`wa-modular`).
- Registro da tela: `J/client/ClientSetup.java:88-105` (`event.register(ModMenus.RESONATOR.get(), (ResonatorMenu menu, Inventory inventory, Component title) -> new ResonatorScreen(...))`).
- Payloads: `J/packet/ModPayloads.java:67` (`VERSION`), `register` 69-139 (`registrar.playToClient(ScalarStatePayload.TYPE, ScalarStatePayload.STREAM_CODEC, StorageScalarMenu::onState)` linha 126; `ResonatorPayloads.register(registrar)` 137). Payload simples: `J/packet/ScalarStatePayload.java:34-41` (`StreamCodec.composite` com `VAR_INT`/`VAR_LONG`).
- Tema: `J/client/GuiPaint.java` (tokens estáticos, nenhuma tela guarda cor), `GuiThemes`, botão `J/client/ThemeToggle.java` (`SIZE = 11`, `render(g, x, y, mouseX, mouseY)`, `mouseClicked(x, y, mx, my, button)`, `tooltip()`), posição `themeX()`/`themeY()` da tela; todo texto variável por `GuiText` (`draw(g, font, Component, x, y, width, color)`); o e2e confere nenhum texto cortado sem tooltip (`DevEndToEnd.clipCheck`). Tela nova entra também na cadeia de `instanceof` de `J/client/dev/E2eDarkSteps.java:~191-209` (`themeButton`).

## 6. Renderer (BER), para a etapa 3

- Registro: `ClientSetup.java:108-111`. `J/client/resonator/render/ResonatorRenderer.java:34-138` (cena por `SceneKey(dimension, pos)`, varredura a cada 200 ticks, `getViewDistance` 64). Cristal: `CrystalGem.render(PoseStack.Pose, MultiBufferSource, int bandColor, int flash, int state, int seed, boolean diamond)`; estados em `CrystalParam` (`LIVE=0`, `DULL=1`, `RECHARGE=2 + progresso 0..1000`, `CRACKED=1003`). Raio: `RibbonQuad.strip(pose, buffer, ax, ay, az, bx, by, bz, cx, cy, cz, width, rgb, alpha, mode, param)` e `billboard(...)`. `FlowConduit.render` é o exemplo do modo 0.

## 7. Config do servidor (`J/Config.java`, seção `resonator`)

- Campo `public static final ModConfigSpec.IntValue RECAST_CRYSTALS;` (58); dentro do `static {}` (97), `builder.push("resonator")` (191): `RECAST_CRYSTALS = builder.comment("...").defineInRange("recastCrystals", 4, 1, 9);` (215-217); long: `RECHARGE_TICKS ... defineInRange("rechargeTicks", 600L, 1L, Long.MAX_VALUE)` (209-211); getter `public static int recastCrystals() { return read(RECAST_CRYSTALS); }` (448-450); `read(...)` 93-95 (`SPEC.isLoaded() ? value.get() : value.getDefault()`).
- FE por cristal por tier: `RESONATOR_FE_PER_CRYSTAL` (`EnumMap`, 65; loop 234-240; getter `resonatorFePerCrystal(tier)` 422-424); padrões em `J/resonator/CoreMath.java:15-25` (`DEFAULT_FE_PER_CRYSTAL = {216, 6_912, 10_000, 160_000, 6_400_000, 6_700_000, 10_700_000L, 268_435_456L}`). Cuidado: nome novo na seção `resonator` não pode colidir com os nomes de tier (`basic`...), que são subtabelas.
- Recarga em jogo: `J/network/ConfigReloadListener.java:44-55` (só marca flags; o tick refaz). Getters leem a cada chamada, então valor direto não precisa de listener.

## 8. Receitas (JSON de dados)

- Pasta `R/data/wirelessautomate/recipe/` (singular). Exemplos: `resonance_core.json` (shaped `KKK/KLK/KDK`, K=`resonator_casing`, L=`cut_signal_crystal`, D=`resonator_heart_basic`), `resonator_casing.json` (`TIT/ICI/TIT`, count 4), `resonator_energy_port.json` (sem forma: `resonator_casing` + `minecraft:redstone_block`), `resonator_crystal_port.json` (`resonator_casing` + `minecraft:hopper`), `resonator_tuner.json` (`resonator_casing` + `minecraft:clock` + `minecraft:comparator`), `storage_battery.json` (`IRI/RCR/IPI`). Formato do resultado `"result": {"id": "wirelessautomate:...", "count": N}`, ingredientes `{"item": "..."}` ou `{"tag": "c:..."}`, `"category"`.
- Itens da cascata (`J/registry/ModItems.java:103-114`): `signal_alloy_raw`, `signal_alloy_ingot`, `resonance_plate`, `resonance_wire`, `resonance_coil`, `harmonic_lens`, `phase_circuit`, `phase_matrix`, `field_stabilizer`, `condensed_core`, `alloy_frame`; `resonance_dust`, `cut_signal_crystal`.

## 9. Modelos e texturas

- `scripts/textures/gerar_texturas.py` `main()` (~2021-2050): `gerar_itens()`, `modelos_itens()`, `blocos.sprites()`, `blocos.modelos()`, depois `limpar(set(todos), ["item","gui","block"])`. `blocos.py`: `sprites_ressonador()` 808-834, `Modelo` 896-950, `construir_modelos()` 1348-1360, `blockstates_ressonador()` 1318-1345 (peça com `facing`: `ROTACOES_PECA` 1216-1217 `{"north": {}, "south": {"y": 180}, "east": {"y": 90}, "west": {"y": 270}, ...}`; item model é só `{"parent": "wirelessautomate:block/<modelo>"}`).
- Modelo gerado (ex.: `R/assets/wirelessautomate/models/block/resonator_energy_port.json`): `"parent": "minecraft:block/block", "render_type": "minecraft:solid", "ambientocclusion": true, "textures": {...}, "elements": [...]`; vidro com `"render_type": "minecraft:translucent"`. Hitbox: bloco que não é cubo cheio precisa de `getShape` (`J/storage/StorageShapes.java`, "mude aqui e no `HITBOXES` do `blocos.py`").

## 10. lang

`R/assets/wirelessautomate/lang/en_us.json` e `pt_br.json`, mesma ordem (1271 linhas cada). Formatos: `"block.wirelessautomate.resonator_energy_port": "Resonator Energy Port"`; telas `"gui.wirelessautomate.resonator.chamber.*"` (1190-1198); JEI `"jei.wirelessautomate.resonator.recast.title"` (1260) e `...recast.note` (1261); comando `"command.wirelessautomate.profile.resonator"` (214). Não há JUnit de paridade: mantenha à mão.

## 11. JEI

`J/compat/jei/WirelessAutomateJeiPlugin.java:59-267` (`registerCategories` 135-137 delega a `ResonatorJei.registerCategories`; `registerRecipes` ... `ResonatorJei.registerRecipes(registration)` linha ~198). `ResonatorJei.java`: `registerCategories` 26-30, `registerRecipes` 32-51, `registerCatalysts` 53-59. Análogo: `ResonatorRecastCategory.java` (107 linhas: `RecipeType.create(WirelessAutomate.MODID, "resonator_recast", Recast.class)`, `record Recast(ItemStack cracked, ItemStack signals, ItemStack full)` com `Recast.of(Item)` usando `SignalCrystalItem.setCharge(cracked, 0)`/`setIntegrity(cracked, 0)` e `Config.recastCrystals()`, `setRecipe`, `createRecipeExtras`, `draw` com `JeiText`).

## 12. Guia

`scripts/guide/gerar_guia.py` (`main()` apaga e reescreve os `.md`; nunca edite os `.md`): `page(nome, pt, en)` 240-241, `front(title, icon, position, parent='index.md', item_ids=())` 30-40. Página do Ressonador `signal-resonator.md` (posição 19; `signal-quartz` 18, `troubleshooting` 20, `recipe-editor` 21, `recipes` 22): uma página nova precisa de posição e renumerar as seguintes. Tabela de itens na página `index` (`item('resonance_core')`), lista `RECIPES` (`<RecipeFor id="..." />`, ~3151-3172). E2e do guia: `J/client/dev/E2eGuideSteps.java:25-28` (`GUIDE_PAGES`).

## 13. GameTests

Classe `@GameTestHolder(WirelessAutomate.MODID) @PrefixGameTestTemplate(false)`, `@GameTest(template = "empty")`. Capability + BE: `J/gametest/StorageGameTests.java:862-885`; capability por `helper.getLevel().getCapability(Capabilities.EnergyStorage.BLOCK, pos, Direction.WEST)` e `BulkEnergy.BLOCK`: `ResonatorPortGameTests.java:56-80`; menu: `ResonatorMenuGameTests.java:56-125` (`survivor(helper)`, `new ResonatorMenu(CONTAINER_ID, player.getInventory(), core)`, `pollState`); `TestPlayers.inLevel`/`menuOpener`/`survivalMenuOpener`/`removedAtEnd` (obrigatórios, CLAUDE.md); `onLoad` só roda no tick seguinte (`startSequence().thenWaitUntil(...)`); teste que muda config vai em `batch = "wirelessautomate_config"` com `try/finally`. `ResonatorTestSupport.crystal(boolean cut, int charge)` reaproveitável.

## 14. E2E e capturas

`J/client/dev/E2eScript.java:42-103` (`script()`, ramos `if ("ressonador".equals(ONLY)) {...}` 65-69; `WA_E2E_ONLY`); `Step(String name, long timeoutMs, Action action, Check check, State state)`; helpers `E2eWorld`, `E2eUi`, `E2eSteps`; `DevEndToEnd.capture(String file)`, `wait`, `clipCheck`; exemplos `E2eResonatorScreenSteps.java` (capturas `ressonador-tela-*`, nos dois temas e em `-pt`); capturas só de tela `ScreenshotResonator.java` entram em `DevScreenshot.SEQUENCE` (`DevScreenshot.java:84-90`, filtro `WA_SCREENSHOT_ONLY`).

## 15. Onde o CLAUDE.md cita cada parte (para atualizar o mapa depois)

Linhas 33 (`Config`), 51 (`storage/`), 52 (`resonator/`, a maior), 57 (`registry/`), 60 (`compat/jei/`), 69-75 (`menu/`, `client/resonator/*`, `packet/`, `client/`), 83 (`gametest/`), 84-85 (`scripts/textures`, `scripts/guide`), 90 (guia).
