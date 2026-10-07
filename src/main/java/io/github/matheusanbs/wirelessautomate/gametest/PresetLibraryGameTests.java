package io.github.matheusanbs.wirelessautomate.gametest;

import io.github.matheusanbs.wirelessautomate.WirelessAutomate;
import io.github.matheusanbs.wirelessautomate.block.RouterBlock;
import io.github.matheusanbs.wirelessautomate.block.RouterBlockEntity;
import io.github.matheusanbs.wirelessautomate.filter.Filter;
import io.github.matheusanbs.wirelessautomate.filter.FilterEntry;
import io.github.matheusanbs.wirelessautomate.menu.ConfiguratorMenu;
import io.github.matheusanbs.wirelessautomate.menu.ConfiguratorView;
import io.github.matheusanbs.wirelessautomate.network.NetworkSavedData;
import io.github.matheusanbs.wirelessautomate.network.PortMode;
import io.github.matheusanbs.wirelessautomate.network.RedstoneMode;
import io.github.matheusanbs.wirelessautomate.network.RelativeSide;
import io.github.matheusanbs.wirelessautomate.network.ResourceType;
import io.github.matheusanbs.wirelessautomate.network.RouterPreset;
import io.github.matheusanbs.wirelessautomate.network.WaNetwork;
import io.github.matheusanbs.wirelessautomate.packet.ConfiguratorActionPayload;
import io.github.matheusanbs.wirelessautomate.packet.ConfiguratorActionPayload.Op;
import io.github.matheusanbs.wirelessautomate.preset.AreaActions;
import io.github.matheusanbs.wirelessautomate.preset.AreaClipboard;
import io.github.matheusanbs.wirelessautomate.preset.AreaOps;
import io.github.matheusanbs.wirelessautomate.preset.AreaSelection;
import io.github.matheusanbs.wirelessautomate.preset.PresetCode;
import io.github.matheusanbs.wirelessautomate.preset.PresetCodes;
import io.github.matheusanbs.wirelessautomate.preset.PresetLibrary;
import io.github.matheusanbs.wirelessautomate.registry.ModBlocks;
import io.github.matheusanbs.wirelessautomate.registry.ModDataComponents;
import io.github.matheusanbs.wirelessautomate.registry.ModItems;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * Configurador completo: biblioteca de presets por jogador, código {@code WA1:} e as operações de
 * área (copiar e colar, aplicar com filtro por máquina), pela tela ({@link ConfiguratorMenu#handle})
 * e pelos cliques no mundo. Cada teste usa um jogador falso novo, então a biblioteca dele é só dele.
 */
@GameTestHolder(WirelessAutomate.MODID)
@PrefixGameTestTemplate(false)
public final class PresetLibraryGameTests {
    private static final int CONTAINER_ID = 57;
    private static final ResourceType[] TYPES = ResourceType.values();
    private static final RelativeSide[] SIDES = RelativeSide.values();

    @SuppressWarnings("removal")
    private static ServerPlayer player(GameTestHelper helper) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        player.moveTo(Vec3.atCenterOf(helper.absolutePos(new BlockPos(1, 2, 1))));
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ModItems.CONFIGURATOR.get()));
        return player;
    }

    private static ConfiguratorMenu open(ServerPlayer player) {
        ConfiguratorMenu menu = new ConfiguratorMenu(CONTAINER_ID, player, InteractionHand.MAIN_HAND,
                ConfiguratorMenu.initialView(player, InteractionHand.MAIN_HAND));
        player.containerMenu = menu;
        return menu;
    }

    private static boolean act(ServerPlayer player, Op op, int index, String text) {
        return ConfiguratorMenu.handle(player, new ConfiguratorActionPayload(CONTAINER_ID, op, index, text));
    }

    private static ItemStack wand(ServerPlayer player) {
        return player.getMainHandItem();
    }

    /** Roteador preso na face {@code facing} de {@code machine} (posições relativas ao teste). */
    private static RouterBlockEntity place(GameTestHelper helper, BlockPos machine, Block block, Direction facing) {
        helper.setBlock(machine, block);
        BlockPos pos = machine.relative(facing);
        helper.setBlock(pos, ModBlocks.ROUTER.get().defaultBlockState().setValue(RouterBlock.FACING, facing));
        return helper.getBlockEntity(pos);
    }

    private static Filter ingots() {
        return new Filter(Filter.ListMode.WHITELIST, false, List.of(
                new FilterEntry.ItemEntry(new ItemStack(Items.IRON_INGOT), 0),
                new FilterEntry.ItemEntry(new ItemStack(Items.GOLD_INGOT), 32)));
    }

    /** Preset variado: itens extrai em cima com filtro, energia insere embaixo com redstone. */
    private static void configure(RouterBlockEntity router) {
        router.setMode(ResourceType.ITEM, Direction.UP, PortMode.EXTRACT);
        router.setFilter(ResourceType.ITEM, Direction.UP, ingots());
        router.setPriority(ResourceType.ITEM, Direction.NORTH, 4);
        router.setMode(ResourceType.ENERGY, Direction.DOWN, PortMode.INSERT);
        router.setRedstone(ResourceType.ENERGY, Direction.DOWN, RedstoneMode.HIGH);
    }

    private static void assertSameFaces(GameTestHelper helper, RouterBlockEntity expected, RouterBlockEntity actual,
            String what) {
        for (ResourceType type : TYPES) {
            for (RelativeSide side : SIDES) {
                helper.assertValueEqual(actual.face(type, side), expected.face(type, side), what + " " + type + " " + side);
            }
        }
    }

    private static RouterPreset presetOf(GameTestHelper helper, Direction facing) {
        RouterBlockEntity source = place(helper, new BlockPos(1, 1, 1), Blocks.STONE, facing);
        configure(source);
        return RouterPreset.copyOf(source);
    }

    // ------------------------------------------------------------------ biblioteca

    @GameTest(template = "empty")
    public static void librarySaveRenameLoadDelete(GameTestHelper helper) {
        ServerPlayer player = player(helper);
        PresetLibrary library = PresetLibrary.get(helper.getLevel().getServer());
        UUID id = player.getUUID();
        RouterPreset preset = presetOf(helper, Direction.UP);
        ConfiguratorMenu menu = open(player);

        helper.assertFalse(act(player, Op.SAVE, 0, "Nada"), "salvou sem cópia na varinha");
        wand(player).set(ModDataComponents.PRESET.get(), preset);
        helper.assertTrue(act(player, Op.SAVE, 0, "  Fornalha  "), "não salvou");
        helper.assertFalse(act(player, Op.SAVE, 0, "   "), "aceitou nome vazio");
        helper.assertFalse(act(player, Op.SAVE, 0, "x".repeat(PresetLibrary.MAX_NAME + 1)), "aceitou nome longo");
        wand(player).set(ModDataComponents.PRESET.get(), RouterPreset.EMPTY);
        helper.assertTrue(act(player, Op.SAVE, 0, "Vazio"), "não salvou o segundo");
        helper.assertValueEqual(library.presets(id).size(), 2, "presets salvos");
        helper.assertValueEqual(library.preset(id, 0).name(), "Fornalha", "nome limpo");
        helper.assertValueEqual(library.preset(id, 0).preset(), preset, "preset salvo");

        ConfiguratorView view = menu.pollView();
        helper.assertTrue(view != null, "a visão não mudou depois de salvar");
        helper.assertValueEqual(view.library().size(), 2, "biblioteca na visão");
        helper.assertValueEqual(view.library().get(0).faces(), preset.configuredFaces(), "faces na visão");
        helper.assertValueEqual(view.notice().isPresent(), true, "sem aviso");

        helper.assertTrue(act(player, Op.RENAME, 1, "Limpo"), "não renomeou");
        helper.assertValueEqual(library.preset(id, 1).name(), "Limpo", "renomear");
        helper.assertFalse(act(player, Op.RENAME, 5, "Fora"), "renomeou índice inexistente");

        helper.assertTrue(act(player, Op.LOAD, 0, ""), "Aplicar não agiu");
        helper.assertValueEqual(wand(player).get(ModDataComponents.PRESET.get()), preset, "Aplicar não pôs o preset na varinha");

        helper.assertTrue(act(player, Op.DELETE, 1, ""), "não apagou");
        helper.assertValueEqual(library.presets(id).size(), 1, "depois de apagar");
        helper.assertFalse(act(player, Op.DELETE, 1, ""), "apagou índice inexistente");

        // a biblioteca de outro jogador não é afetada e o limite vale por jogador
        UUID other = UUID.randomUUID();
        for (int i = 0; i < PresetLibrary.MAX_PRESETS; i++) {
            helper.assertValueEqual(library.add(other, "P" + i, preset, player.registryAccess()), PresetLibrary.Result.OK,
                    "preset " + i);
        }
        helper.assertValueEqual(library.add(other, "Demais", preset, player.registryAccess()), PresetLibrary.Result.FULL,
                "limite de presets");
        helper.assertValueEqual(library.presets(id).size(), 1, "biblioteca do jogador mudou");
        // ida e volta pelo NBT do SavedData
        PresetLibrary loaded = PresetLibrary.load(library.save(new CompoundTag(), player.registryAccess()),
                player.registryAccess());
        helper.assertValueEqual(loaded.presets(id), library.presets(id), "salvar e carregar a biblioteca");

        helper.assertFalse(ConfiguratorMenu.handle(player, new ConfiguratorActionPayload(CONTAINER_ID + 1, Op.LOAD, 0, "")),
                "aceitou outra tela");
        player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
        helper.assertFalse(act(player, Op.LOAD, 0, ""), "aceitou sem a varinha na mão");
        player.containerMenu = player.inventoryMenu;
        helper.succeed();
    }

    // ------------------------------------------------------------------ código WA1

    @GameTest(template = "empty")
    public static void codeRoundTripThroughScreen(GameTestHelper helper) {
        ServerPlayer player = player(helper);
        PresetLibrary library = PresetLibrary.get(helper.getLevel().getServer());
        NetworkSavedData data = NetworkSavedData.get(helper.getLevel().getServer());
        WaNetwork own = data.create(player.getUUID(), "Código " + player.getUUID());
        RouterPreset preset = presetOf(helper, Direction.NORTH);
        RouterBlockEntity source = helper.getBlockEntity(new BlockPos(1, 1, 1).relative(Direction.NORTH));
        source.setNetworkId(ResourceType.ITEM, own.id());
        source.setNetworkId(ResourceType.ENERGY, UUID.randomUUID()); // rede de "outro mundo"
        preset = RouterPreset.copyOf(source);
        library.add(player.getUUID(), "Linha 5x", preset, player.registryAccess());
        ConfiguratorMenu menu = open(player);

        helper.assertTrue(act(player, Op.EXPORT, 0, ""), "exportar não agiu");
        ConfiguratorView view = menu.pollView();
        helper.assertTrue(view != null && view.export().isPresent(), "sem código na visão");
        String code = view.export().get().code();
        helper.assertTrue(code.startsWith(PresetCode.PREFIX), "sem prefixo: " + code);

        helper.assertTrue(act(player, Op.IMPORT, 0, "  " + code + "\n"), "importar não agiu");
        helper.assertValueEqual(library.presets(player.getUUID()).size(), 2, "importado não entrou na biblioteca");
        PresetLibrary.Entry imported = library.preset(player.getUUID(), 1);
        helper.assertValueEqual(imported.name(), "Linha 5x", "nome do código");
        helper.assertValueEqual(imported.preset(), preset.withoutNetwork(ResourceType.ENERGY),
                "ida e volta (a rede inexistente cai, a própria fica)");
        helper.assertValueEqual(imported.preset().network(ResourceType.ITEM), own.id(), "rede própria");

        int before = library.presets(player.getUUID()).size();
        helper.assertFalse(act(player, Op.IMPORT, 0, "WA2:" + code.substring(4)), "aceitou prefixo errado");
        helper.assertFalse(act(player, Op.IMPORT, 0, "WA1:isto-nao-e-um-preset"), "aceitou código corrompido");
        helper.assertFalse(act(player, Op.IMPORT, 0, ""), "aceitou código vazio");
        helper.assertValueEqual(library.presets(player.getUUID()).size(), before, "código inválido entrou");
        view = menu.pollView();
        helper.assertTrue(view != null && view.notice().isPresent(), "sem aviso do código inválido");
        player.containerMenu = player.inventoryMenu;
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void codeIgnoresEntriesOfMissingMods(GameTestHelper helper) throws Exception {
        ServerPlayer player = player(helper);
        RouterPreset preset = presetOf(helper, Direction.UP);
        CompoundTag tag = PresetCodes.toTag("Com mod ausente", preset, player.registryAccess());
        int replaced = replaceStrings(tag, "minecraft:gold_ingot", "nosuchmod:widget");
        helper.assertValueEqual(replaced, 1, "troca do id no NBT");
        String code = PresetCode.encode(PresetCodes.bytes(tag));

        PresetCodes.Imported imported = PresetCodes.importCode(code, player.registryAccess());
        helper.assertValueEqual(imported.ignoredEntries(), 1, "entradas ignoradas");
        Filter filter = imported.preset().face(ResourceType.ITEM, RelativeSide.FRONT).filter();
        helper.assertValueEqual(filter.entries().size(), 1, "entradas que sobraram");
        helper.assertTrue(filter.entries().get(0) instanceof FilterEntry.ItemEntry e && e.stack().is(Items.IRON_INGOT),
                "a entrada legível sumiu: " + filter);
        helper.assertValueEqual(imported.preset().face(ResourceType.ENERGY, RelativeSide.BACK).redstone(), RedstoneMode.HIGH,
                "o resto do preset");

        // pela tela, o aviso cita as entradas ignoradas
        ConfiguratorMenu menu = open(player);
        helper.assertTrue(act(player, Op.IMPORT, 0, code), "importar não agiu");
        ConfiguratorView view = menu.pollView();
        helper.assertTrue(view != null && view.notice().isPresent()
                && view.notice().get().toString().contains("imported_ignored"), "aviso sem as entradas ignoradas");
        player.containerMenu = player.inventoryMenu;
        helper.succeed();
    }

    private static int replaceStrings(Tag tag, String from, String to) {
        int count = 0;
        if (tag instanceof CompoundTag compound) {
            for (String key : compound.getAllKeys()) {
                Tag child = compound.get(key);
                if (child instanceof StringTag string && string.getAsString().equals(from)) {
                    compound.putString(key, to);
                    count++;
                } else {
                    count += replaceStrings(child, from, to);
                }
            }
        } else if (tag instanceof ListTag list) {
            for (Tag child : list) {
                count += replaceStrings(child, from, to);
            }
        }
        return count;
    }

    // ------------------------------------------------------------------ área

    private static void click(ServerPlayer player, BlockPos pos, boolean sneak) {
        player.setShiftKeyDown(sneak);
        UseOnContext context = new UseOnContext(player, InteractionHand.MAIN_HAND,
                new BlockHitResult(Vec3.atCenterOf(pos), Direction.UP, pos, false));
        ModItems.CONFIGURATOR.get().onItemUseFirst(wand(player), context);
        player.setShiftKeyDown(false);
    }

    @GameTest(template = "empty")
    public static void copyAreaAndPasteAtOrigin(GameTestHelper helper) {
        ServerPlayer player = player(helper);
        // linha de cima: dois roteadores configurados de jeitos diferentes, z = 0
        RouterBlockEntity a = place(helper, new BlockPos(0, 0, 0), Blocks.STONE, Direction.UP);
        RouterBlockEntity b = place(helper, new BlockPos(2, 0, 0), Blocks.STONE, Direction.UP);
        configure(a);
        b.setMode(ResourceType.FLUID, Direction.UP, PortMode.INSERT);
        // a linha clonada, z = 2: roteadores virados para outro lado num deles, e sem roteador na
        // posição de b
        RouterBlockEntity a2 = place(helper, new BlockPos(0, 0, 2), Blocks.STONE, Direction.UP);
        helper.setBlock(new BlockPos(2, 1, 2), Blocks.AIR);

        ItemStack wand = wand(player);
        // no modo pincel, clicar num bloco não marca nada
        click(player, helper.absolutePos(new BlockPos(0, 0, 0)), true);
        helper.assertValueEqual(AreaActions.selection(wand).corner1().isPresent(), false, "pincel marcou canto");

        ConfiguratorMenu menu = open(player);
        helper.assertTrue(act(player, Op.SET_MODE, 1, ""), "modo área");
        player.containerMenu = player.inventoryMenu;
        helper.assertValueEqual(AreaActions.selection(wand).mode(), AreaSelection.Mode.AREA, "modo");
        click(player, helper.absolutePos(new BlockPos(0, 0, 0)), true);
        click(player, helper.absolutePos(new BlockPos(2, 1, 0)), true);
        AreaSelection selection = AreaActions.selection(wand);
        helper.assertTrue(selection.complete(), "cantos não marcados: " + selection);
        helper.assertValueEqual(AreaSelection.volume(selection.box()), 6L, "volume");

        open(player);
        helper.assertTrue(act(player, Op.COPY_AREA, 0, ""), "copiar não agiu");
        AreaClipboard clipboard = AreaActions.clipboard(wand);
        helper.assertTrue(clipboard != null && clipboard.size() == 2, "cópia: " + clipboard);
        helper.assertValueEqual(clipboard.entries().get(0).offset(), new BlockPos(0, 1, 0), "posição relativa de a");
        helper.assertValueEqual(clipboard.entries().get(1).offset(), new BlockPos(2, 1, 0), "posição relativa de b");
        ConfiguratorView view = open(player).view();
        helper.assertValueEqual(view.wand().clipboardSize(), 2, "cópia na visão");
        helper.assertValueEqual(view.area().routers().size(), 2, "roteadores da área na visão");
        player.containerMenu = player.inventoryMenu;

        // primeiro clique escolhe a origem (canto 1 da linha clonada), o segundo cola
        BlockPos anchor = helper.absolutePos(new BlockPos(0, 0, 2));
        click(player, anchor, false);
        helper.assertValueEqual(AreaActions.selection(wand).anchor().map(GlobalPos::pos).orElse(null), anchor, "origem");
        helper.assertValueEqual(AreaOps.hits(helper.getLevel(), clipboard, anchor), 1, "posições com roteador");
        helper.assertTrue(a2.face(ResourceType.ITEM, Direction.UP).mode() == PortMode.NONE, "colou antes da hora");
        click(player, anchor, false);
        assertSameFaces(helper, a, a2, "colado em a2");
        helper.assertTrue(helper.getLevel().getBlockState(helper.absolutePos(new BlockPos(2, 1, 2))).isAir(),
                "colar colocou bloco");

        // a cópia vazia some com Descartar
        open(player);
        helper.assertTrue(act(player, Op.CLEAR_CLIPBOARD, 0, ""), "descartar");
        helper.assertTrue(AreaActions.clipboard(wand) == null, "cópia não descartada");
        player.containerMenu = player.inventoryMenu;
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void applyToAreaWithMachineFilter(GameTestHelper helper) {
        ServerPlayer player = player(helper);
        NetworkSavedData data = NetworkSavedData.get(helper.getLevel().getServer());
        WaNetwork own = data.create(player.getUUID(), "Área " + player.getUUID());
        WaNetwork foreign = data.create(UUID.randomUUID(), "Alheia " + player.getUUID());
        RouterBlockEntity stone1 = place(helper, new BlockPos(0, 0, 0), Blocks.STONE, Direction.UP);
        RouterBlockEntity stone2 = place(helper, new BlockPos(2, 0, 2), Blocks.STONE, Direction.UP);
        RouterBlockEntity dirt = place(helper, new BlockPos(2, 0, 0), Blocks.DIRT, Direction.UP);
        // referência, fora da área (y = 2), na rede própria para itens e na alheia para energia
        RouterBlockEntity reference = place(helper, new BlockPos(1, 2, 0), Blocks.STONE, Direction.SOUTH);
        configure(reference);
        reference.setNetworkId(ResourceType.ITEM, own.id());
        reference.setNetworkId(ResourceType.ENERGY, foreign.id());
        ItemStack wand = wand(player);
        wand.set(ModDataComponents.PRESET.get(), RouterPreset.copyOf(reference));
        wand.set(ModDataComponents.CONFIGURATOR_AREA.get(), AreaSelection.EMPTY.withMode(AreaSelection.Mode.AREA)
                .mark(GlobalPos.of(helper.getLevel().dimension(), helper.absolutePos(new BlockPos(0, 0, 0))))
                .mark(GlobalPos.of(helper.getLevel().dimension(), helper.absolutePos(new BlockPos(2, 1, 2)))));

        ConfiguratorMenu menu = open(player);
        ConfiguratorView view = menu.view();
        helper.assertTrue(view.area().usable(), "área inválida: " + view.area().problem());
        helper.assertValueEqual(view.area().routers().size(), 3, "roteadores na área");
        helper.assertTrue(view.area().machines().contains(ResourceLocation.withDefaultNamespace("stone"))
                && view.area().machines().contains(ResourceLocation.withDefaultNamespace("dirt")), "máquinas da área");

        helper.assertTrue(act(player, Op.APPLY_AREA, -1, "minecraft:stone"), "aplicar não agiu");
        assertSameFaces(helper, reference, stone1, "pedra 1");
        assertSameFaces(helper, reference, stone2, "pedra 2");
        helper.assertValueEqual(dirt.face(ResourceType.ITEM, Direction.UP).mode(), PortMode.NONE,
                "aplicou na máquina filtrada");
        helper.assertValueEqual(stone1.networkId(ResourceType.ITEM), own.id(), "rede própria não aplicada");
        helper.assertTrue(!foreign.id().equals(stone1.networkId(ResourceType.ENERGY)), "rede alheia aplicada");

        // sem filtro, todos; da biblioteca, pelo índice
        PresetLibrary.get(helper.getLevel().getServer()).add(player.getUUID(), "Vazio", RouterPreset.EMPTY,
                player.registryAccess());
        helper.assertTrue(act(player, Op.APPLY_AREA, 0, ""), "aplicar da biblioteca não agiu");
        for (RouterBlockEntity router : List.of(stone1, stone2, dirt)) {
            helper.assertValueEqual(router.face(ResourceType.ITEM, Direction.UP).mode(), PortMode.NONE,
                    "preset vazio não aplicado em " + router.getBlockPos());
        }
        helper.assertFalse(act(player, Op.APPLY_AREA, 7, ""), "aceitou preset inexistente");
        player.containerMenu = player.inventoryMenu;
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void areaLimitsAndReach(GameTestHelper helper) {
        ServerPlayer player = player(helper);
        RouterBlockEntity router = place(helper, new BlockPos(1, 0, 1), Blocks.STONE, Direction.UP);
        ItemStack wand = wand(player);
        BlockPos base = helper.absolutePos(BlockPos.ZERO);
        // grande demais: 300 de lado
        wand.set(ModDataComponents.CONFIGURATOR_AREA.get(), AreaSelection.EMPTY.withMode(AreaSelection.Mode.AREA)
                .mark(GlobalPos.of(helper.getLevel().dimension(), base))
                .mark(GlobalPos.of(helper.getLevel().dimension(), base.offset(300, 0, 0))));
        open(player);
        act(player, Op.COPY_AREA, 0, "");
        helper.assertTrue(AreaActions.clipboard(wand) == null, "copiou área grande demais");
        helper.assertValueEqual(open(player).view().area().problem().orElse(""), "too_large", "problema na visão");

        // longe demais: a área serve, mas o jogador está a mais de 64 blocos
        wand.set(ModDataComponents.CONFIGURATOR_AREA.get(), AreaSelection.EMPTY.withMode(AreaSelection.Mode.AREA)
                .mark(GlobalPos.of(helper.getLevel().dimension(), base))
                .mark(GlobalPos.of(helper.getLevel().dimension(), base.offset(2, 2, 2))));
        player.moveTo(Vec3.atCenterOf(base.offset(100, 0, 0)));
        open(player);
        act(player, Op.COPY_AREA, 0, "");
        helper.assertTrue(AreaActions.clipboard(wand) == null, "copiou de longe");
        player.moveTo(Vec3.atCenterOf(base.above(2)));
        open(player);
        act(player, Op.COPY_AREA, 0, "");
        helper.assertTrue(AreaActions.clipboard(wand) != null && AreaActions.clipboard(wand).size() == 1,
                "não copiou de perto");

        // colar longe demais também é recusado
        router.setMode(ResourceType.ITEM, Direction.UP, PortMode.EXTRACT);
        wand.set(ModDataComponents.CONFIGURATOR_AREA.get(), AreaActions.selection(wand)
                .withAnchor(GlobalPos.of(helper.getLevel().dimension(), base.offset(200, 0, 0))));
        act(player, Op.PASTE, 0, "");
        helper.assertValueEqual(router.face(ResourceType.ITEM, Direction.UP).mode(), PortMode.EXTRACT, "colou de longe");
        player.containerMenu = player.inventoryMenu;
        helper.succeed();
    }
}
