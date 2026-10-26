package io.github.matheusanbs.wirelessautomate.client.dev;

import static io.github.matheusanbs.wirelessautomate.client.dev.E2eTabletSteps.*;
import static io.github.matheusanbs.wirelessautomate.client.dev.E2eUi.*;
import static io.github.matheusanbs.wirelessautomate.client.dev.E2eWorld.*;

import io.github.matheusanbs.wirelessautomate.block.RouterBlock;
import io.github.matheusanbs.wirelessautomate.block.RouterBlockEntity;
import io.github.matheusanbs.wirelessautomate.block.RouterTier;
import io.github.matheusanbs.wirelessautomate.filter.Filter;
import io.github.matheusanbs.wirelessautomate.filter.FilterEntry;
import io.github.matheusanbs.wirelessautomate.range.EditTarget;
import io.github.matheusanbs.wirelessautomate.network.NetworkSavedData;
import io.github.matheusanbs.wirelessautomate.network.PortMode;
import io.github.matheusanbs.wirelessautomate.network.RedstoneMode;
import io.github.matheusanbs.wirelessautomate.network.ResourceType;
import io.github.matheusanbs.wirelessautomate.network.WaNetwork;
import io.github.matheusanbs.wirelessautomate.registry.ModBlocks;
import io.github.matheusanbs.wirelessautomate.registry.ModItems;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.fml.ModList;
import io.github.matheusanbs.wirelessautomate.storage.StorageBatteryBlockEntity;
import io.github.matheusanbs.wirelessautomate.storage.StorageChestBlockEntity;
import io.github.matheusanbs.wirelessautomate.storage.StorageKind;
import net.minecraft.world.level.block.state.BlockState;
import io.github.matheusanbs.wirelessautomate.storage.StorageSourceTankBlockEntity;
import io.github.matheusanbs.wirelessautomate.storage.StorageChemicalTankBlockEntity;
import io.github.matheusanbs.wirelessautomate.storage.StorageTankBlockEntity;
import net.neoforged.neoforge.fluids.FluidStack;

/**
 * O mundo da vitrine do CurseForge, montado no servidor: a casa de quartzo, a linha de fornalhas, o roteador de
 * alcance, os armazenamentos, a vitrine dos itens, o salão dos tiers e o laboratório do Ars.
 */
final class E2eShowcaseWorld {
    private E2eShowcaseWorld() {
    }

    static BlockPos showBase;
    static final List<BlockPos> showRouters = new ArrayList<>();
    static BlockPos showInput;
    private static BlockPos showOutput;
    static BlockPos showFurnace;
    static final List<BlockPos> showStorage = new ArrayList<>();
    static BlockPos showSourceJar;
    static final List<BlockPos> showTanks = new ArrayList<>();
    static BlockPos showRange;

    /** Metade da largura da casa (paredes em x = ±12) e as paredes norte e sul (o salão dos tiers no fundo). */
    private static final int HOUSE_X = 12;
    private static final int HOUSE_NORTH = -27;
    /** Linha do salão dos tiers: os oito roteadores em x = -7..7, cada um sobre o bloco do seu material. */
    private static final int TIER_HALL_Z = -24;

    /** O tier do Allthemodium se o mod está no cliente de dev, senão o substituto (a vitrine roda sem ele também). */
    private static RouterTier atm(RouterTier tier, RouterTier fallback) {
        return tier.loaded() ? tier : fallback;
    }
    private static final int HOUSE_SOUTH = -2;

    static void showcaseWorld() throws Exception {
        UUID playerId = Minecraft.getInstance().player.getUUID();
        onServer(server -> {
            ServerPlayer player = server.getPlayerList().getPlayer(playerId);
            ServerLevel level = player.serverLevel();
            showBase = player.blockPosition();
            showRouters.clear();
            showStorage.clear();
            showTanks.clear();
            showSourceJar = null;
            level.setDayTime(6000);
            NetworkSavedData data = NetworkSavedData.get(server);
            WaNetwork smelting = data.create(player.getUUID(), "Smelting Line");
            WaNetwork storage = data.create(player.getUUID(), "Storage");
            WaNetwork arcane = StorageKind.SOURCE_TANK.loaded() ? data.create(player.getUUID(), "Arcane Lab") : null;
            data.create(player.getUUID(), "Base Power");
            data.setActiveNetwork(player.getUUID(), smelting.id());
            showcaseHouse(server, level);
            showcaseSmelting(server, level, smelting.id());
            showcaseStorage(server, level, storage.id());
            // Rede por face: as fornalhas recebem o minério da linha por cima e mandam os lingotes para a
            // rede dos armazenamentos por baixo.
            for (int i = 0; i < 5; i++) {
                router(server, showBase.offset(-11, 1, -15 + 2 * i)).setFaceNetwork(ResourceType.ITEM, Direction.DOWN,
                        storage.id());
            }
            showcaseRange(server, level, smelting.id());
            showcaseDisplay(server);
            showcaseCascade(server, level);
            showcaseRepairerStand(level);
            showcaseTiers(server, level, data.create(player.getUUID(), "Tier Hall").id());
            if (StorageKind.SOURCE_TANK.loaded()) {
                showcaseArcane(server, level, arcane.id());
            }
            return null;
        });
    }

    /** Um bloco relativo à base, sem avisar os vizinhos (as conexões saem no {@link #settleShapes}). */
    private static void put(ServerLevel level, int x, int y, int z, BlockState state) {
        level.setBlock(showBase.offset(x, y, z), state, net.minecraft.world.level.block.Block.UPDATE_CLIENTS);
    }

    /** Roda um comando como o servidor (blocos de outros mods com estado, entidades com NBT), sem saída. */
    private static void command(MinecraftServer server, String command) {
        server.getCommands().performPrefixedCommand(server.createCommandSourceStack().withSuppressedOutput(), command);
    }

    static String at(int x, int y, int z) {
        BlockPos pos = showBase.offset(x, y, z);
        return pos.getX() + " " + pos.getY() + " " + pos.getZ();
    }

    private static BlockState leaves(net.minecraft.world.level.block.Block block) {
        return block.defaultBlockState().setValue(net.minecraft.world.level.block.LeavesBlock.PERSISTENT, true);
    }

    /**
     * A casa: piso de quartzo liso e calcita em xadrez com borda de ardósia polida, paredes de tijolo
     * de quartzo com colunas e janelas, sem teto (a luz do dia entra toda), a parede sul baixa para a
     * câmera ver por cima, lanternas no alto das colunas, um tapete sob a vitrine, plantas e, fora, um
     * caminho com postes, cerca viva, flores e cerejeiras.
     */
    private static void showcaseHouse(MinecraftServer server, ServerLevel level) {
        // Jardim: grama em volta, caminho até a porta e postes de luz.
        for (int z = HOUSE_SOUTH + 1; z <= 9; z++) {
            for (int x = -1; x <= 1; x++) {
                put(level, x, -1, z, Blocks.DIRT_PATH.defaultBlockState());
            }
        }
        for (int[] post : new int[][] {{-3, 1}, {3, 1}, {-3, 6}, {3, 6}}) {
            put(level, post[0], 0, post[1], Blocks.POLISHED_DEEPSLATE_WALL.defaultBlockState());
            put(level, post[0], 1, post[1], Blocks.POLISHED_DEEPSLATE_WALL.defaultBlockState());
            put(level, post[0], 2, post[1], Blocks.LANTERN.defaultBlockState());
        }
        for (int[] tree : new int[][] {{-17, -4}, {17, -4}, {-17, -15}, {17, -15}, {-17, -26}, {17, -26}}) {
            cherryTree(level, tree[0], tree[1]);
        }
        net.minecraft.world.level.block.Block[] flowers = {Blocks.ALLIUM, Blocks.OXEYE_DAISY, Blocks.CORNFLOWER,
                Blocks.LILY_OF_THE_VALLEY, Blocks.PINK_TULIP};
        for (int x = -HOUSE_X - 1; x <= HOUSE_X + 1; x++) {
            for (int z = HOUSE_NORTH - 1; z <= HOUSE_SOUTH + 1; z++) {
                boolean ring = x == -HOUSE_X - 1 || x == HOUSE_X + 1 || z == HOUSE_NORTH - 1 || z == HOUSE_SOUTH + 1;
                if (!ring) {
                    continue;
                }
                if (z == HOUSE_SOUTH + 1) {
                    if (Math.abs(x) > 1) {
                        put(level, x, 0, z, flowers[Math.floorMod(x * 7 + 3, flowers.length)].defaultBlockState());
                    }
                } else {
                    put(level, x, 0, z, leaves(Math.floorMod(x + z, 3) == 0 ? Blocks.FLOWERING_AZALEA_LEAVES
                            : Blocks.AZALEA_LEAVES));
                }
            }
        }
        // Piso.
        for (int x = -HOUSE_X; x <= HOUSE_X; x++) {
            for (int z = HOUSE_NORTH; z <= HOUSE_SOUTH; z++) {
                boolean wall = Math.abs(x) == HOUSE_X || z == HOUSE_NORTH || z == HOUSE_SOUTH;
                boolean border = Math.abs(x) == HOUSE_X - 1 || z == HOUSE_NORTH + 1 || z == HOUSE_SOUTH - 1;
                BlockState floor = wall ? Blocks.QUARTZ_BRICKS.defaultBlockState()
                        : border ? Blocks.POLISHED_DEEPSLATE.defaultBlockState()
                        : Math.floorMod(x + z, 2) == 0 ? Blocks.SMOOTH_QUARTZ.defaultBlockState()
                        : Blocks.CALCITE.defaultBlockState();
                put(level, x, -1, z, floor);
            }
        }
        // Entrada sem parede, com o piso do caminho.
        for (int x = -1; x <= 1; x++) {
            put(level, x, -1, HOUSE_SOUTH, Blocks.SMOOTH_QUARTZ.defaultBlockState());
            put(level, x, -1, HOUSE_SOUTH - 1, Blocks.SMOOTH_QUARTZ.defaultBlockState());
        }
        // Paredes: colunas a cada 4 blocos; norte, leste e oeste com janelas, a sul baixa.
        for (int x = -HOUSE_X; x <= HOUSE_X; x++) {
            for (int z = HOUSE_NORTH; z <= HOUSE_SOUTH; z++) {
                boolean onWall = Math.abs(x) == HOUSE_X || z == HOUSE_NORTH || z == HOUSE_SOUTH;
                if (!onWall) {
                    continue;
                }
                boolean south = z == HOUSE_SOUTH;
                if (south && Math.abs(x) <= 1) {
                    continue;
                }
                boolean pillar = (Math.abs(x) == HOUSE_X || z == HOUSE_NORTH || south)
                        && Math.floorMod(x, 4) == 0 && Math.floorMod(z - HOUSE_NORTH, 4) == 0;
                if (south) {
                    if (pillar) {
                        put(level, x, 0, z, Blocks.QUARTZ_PILLAR.defaultBlockState());
                        put(level, x, 1, z, Blocks.CHISELED_QUARTZ_BLOCK.defaultBlockState());
                        put(level, x, 2, z, Blocks.LANTERN.defaultBlockState());
                    } else {
                        put(level, x, 0, z, Blocks.QUARTZ_BRICKS.defaultBlockState());
                        put(level, x, 1, z, Blocks.SMOOTH_QUARTZ_SLAB.defaultBlockState());
                    }
                } else if (pillar) {
                    for (int y = 0; y <= 3; y++) {
                        put(level, x, y, z, Blocks.QUARTZ_PILLAR.defaultBlockState());
                    }
                    put(level, x, 4, z, Blocks.CHISELED_QUARTZ_BLOCK.defaultBlockState());
                    put(level, x, 5, z, Blocks.LANTERN.defaultBlockState());
                } else {
                    put(level, x, 0, z, Blocks.QUARTZ_BRICKS.defaultBlockState());
                    put(level, x, 1, z, Blocks.GLASS_PANE.defaultBlockState());
                    put(level, x, 2, z, Blocks.GLASS_PANE.defaultBlockState());
                    put(level, x, 3, z, Blocks.QUARTZ_BRICKS.defaultBlockState());
                    put(level, x, 4, z, Blocks.SMOOTH_QUARTZ_SLAB.defaultBlockState());
                }
            }
        }
        // Tapete sob a vitrine e pedestais de quartzo.
        for (int x = -5; x <= 5; x++) {
            for (int z = -13; z <= -8; z++) {
                boolean edge = Math.abs(x) == 5 || z == -13 || z == -8;
                put(level, x, 0, z, (edge ? Blocks.CYAN_CARPET : Blocks.LIGHT_GRAY_CARPET).defaultBlockState());
            }
        }
        for (int x = -4; x <= 4; x += 2) {
            for (int z : new int[] {-12, -9}) {
                put(level, x, 0, z, Blocks.QUARTZ_PILLAR.defaultBlockState());
            }
        }
        // Luz no piso nos cantos do tapete e plinto sob a galeria.
        for (int[] light : new int[][] {{-6, -14}, {6, -14}, {-6, -7}, {6, -7}}) {
            put(level, light[0], -1, light[1], Blocks.SEA_LANTERN.defaultBlockState());
        }
        for (int x = -5; x <= 5; x++) {
            put(level, x, -1, -16, Blocks.CHISELED_QUARTZ_BLOCK.defaultBlockState());
        }
        // Bancos de quartzo junto à parede sul, dos dois lados da entrada (a leste só até os tanques).
        for (int x = -9; x <= -4; x++) {
            put(level, x, 0, HOUSE_SOUTH - 1, Blocks.QUARTZ_STAIRS.defaultBlockState()
                    .setValue(net.minecraft.world.level.block.StairBlock.FACING, Direction.SOUTH));
        }
        // Plantas.
        for (int[] pot : new int[][] {{-11, -17}, {-11, -3}, {-3, -3}, {3, -3}, {-6, -17}, {6, -17}, {-10, -3}, {-11, -5}}) {
            put(level, pot[0], 0, pot[1], (Math.floorMod(pot[0] + pot[1], 2) == 0 ? Blocks.POTTED_FLOWERING_AZALEA
                    : Blocks.POTTED_FERN).defaultBlockState());
        }
        settleShapes(level, -20, 20, -1, 8, HOUSE_NORTH - 6, 10);
    }

    /** Cerejeira simples: tronco de 4 e uma copa arredondada (folhas que não caem). */
    private static void cherryTree(ServerLevel level, int x, int z) {
        for (int y = 0; y <= 3; y++) {
            put(level, x, y, z, Blocks.CHERRY_LOG.defaultBlockState());
        }
        for (int dx = -3; dx <= 3; dx++) {
            for (int dy = 3; dy <= 6; dy++) {
                for (int dz = -3; dz <= 3; dz++) {
                    double d = dx * dx + dz * dz + (dy - 4.5) * (dy - 4.5) * 2.2;
                    if (d <= 9.5 && !(dx == 0 && dz == 0 && dy <= 3)) {
                        put(level, x + dx, dy, z + dz, leaves(Blocks.CHERRY_LEAVES));
                    }
                }
            }
        }
    }

    /** Recalcula a forma dos blocos que se ligam aos vizinhos (vidros, muros) depois de montar. */
    private static void settleShapes(ServerLevel level, int x0, int x1, int y0, int y1, int z0, int z1) {
        for (int x = x0; x <= x1; x++) {
            for (int y = y0; y <= y1; y++) {
                for (int z = z0; z <= z1; z++) {
                    BlockPos pos = showBase.offset(x, y, z);
                    BlockState state = level.getBlockState(pos);
                    if (state.isAir()) {
                        continue;
                    }
                    BlockState settled = net.minecraft.world.level.block.Block.updateFromNeighbourShapes(state, level, pos);
                    if (settled != state) {
                        level.setBlock(pos, settled, net.minecraft.world.level.block.Block.UPDATE_CLIENTS);
                    }
                }
            }
        }
    }

    /**
     * Ala oeste: cinco fornalhas acesas contra a parede, cada uma com um tier (Básico ao Allthemodium), com
     * filtro de minérios e carvão, o baú de entrada cheio, um barril e o baú de saída.
     */
    private static void showcaseSmelting(MinecraftServer server, ServerLevel level, UUID network) {
        String[] tiers = {"basic", "advanced", "elite", "emerald",
                atm(RouterTier.ALLTHEMODIUM, RouterTier.ULTIMATE).getSerializedName()};
        Filter ores = new Filter(Filter.ListMode.WHITELIST, false, List.of(
                new FilterEntry.TagEntry(ResourceLocation.parse("c:ores"), 0),
                new FilterEntry.ItemEntry(new ItemStack(Items.RAW_IRON), 0),
                new FilterEntry.ItemEntry(new ItemStack(Items.RAW_GOLD), 0),
                new FilterEntry.ItemEntry(new ItemStack(Items.RAW_COPPER), 0),
                new FilterEntry.ItemEntry(new ItemStack(Items.ANCIENT_DEBRIS), 16)));
        for (int i = 0; i < tiers.length; i++) {
            BlockPos furnace = showBase.offset(-11, 0, -15 + 2 * i);
            level.setBlockAndUpdate(furnace, Blocks.FURNACE.defaultBlockState()
                    .setValue(net.minecraft.world.level.block.FurnaceBlock.FACING, Direction.EAST)
                    .setValue(net.minecraft.world.level.block.FurnaceBlock.LIT, true));
            ((Container) level.getBlockEntity(furnace)).setItem(1, new ItemStack(Items.COAL_BLOCK, 64));
            placeShowRouter(server, level, furnace, tiers[i], "Furnace " + (i + 1), network);
            RouterBlockEntity router = router(server, furnace.above());
            router.setMode(ResourceType.ITEM, Direction.UP, PortMode.INSERT);
            router.setFilter(ResourceType.ITEM, Direction.UP, ores);
            router.setMode(ResourceType.ITEM, Direction.DOWN, PortMode.EXTRACT);
            if (i == 2) {
                showFurnace = furnace;
            }
        }
        showInput = showBase.offset(-8, 0, -13);
        showOutput = showBase.offset(-8, 0, -9);
        level.setBlockAndUpdate(showInput, Blocks.CHEST.defaultBlockState().setValue(ChestBlock.FACING, Direction.EAST));
        level.setBlockAndUpdate(showBase.offset(-8, 0, -11), Blocks.BARREL.defaultBlockState()
                .setValue(net.minecraft.world.level.block.BarrelBlock.FACING, Direction.UP));
        level.setBlockAndUpdate(showOutput, Blocks.CHEST.defaultBlockState().setValue(ChestBlock.FACING, Direction.EAST));
        placeShowRouter(server, level, showInput, "basic", "Ore Input", network);
        placeShowRouter(server, level, showOutput, "advanced", "Output", network);
        router(server, showInput.above()).setMode(ResourceType.ITEM, Direction.UP, PortMode.EXTRACT);
        router(server, showOutput.above()).setMode(ResourceType.ITEM, Direction.UP, PortMode.INSERT);
        refillShowcase(level);
    }

    /**
     * Canto noroeste: o roteador de alcance. Um baú com um roteador Elite e o Upgrade de Alcance, e dez
     * máquinas vinculadas sem roteador (cinco altos-fornos e cinco defumadores em cima, contra a parede
     * oeste), em dois grupos: minérios para os altos-fornos, carne para os defumadores.
     */
    private static void showcaseRange(MinecraftServer server, ServerLevel level, UUID network) {
        BlockPos chest = showBase.offset(-8, 0, -20);
        showRange = chest.above();
        level.setBlockAndUpdate(chest, Blocks.CHEST.defaultBlockState().setValue(ChestBlock.FACING, Direction.WEST));
        Container input = (Container) level.getBlockEntity(chest);
        Item[] items = {Items.RAW_IRON, Items.RAW_GOLD, Items.BEEF, Items.PORKCHOP, Items.RAW_COPPER, Items.CHICKEN};
        for (int slot = 0; slot < input.getContainerSize(); slot++) {
            input.setItem(slot, new ItemStack(items[slot % items.length], 64));
        }
        placeShowRouter(server, level, chest, "elite", "Kitchen & Forge", network);
        showRangeNetwork = network;
        for (int z = -22; z <= -18; z++) {
            BlockPos blast = showBase.offset(-11, 0, z);
            level.setBlockAndUpdate(blast, Blocks.BLAST_FURNACE.defaultBlockState()
                    .setValue(net.minecraft.world.level.block.AbstractFurnaceBlock.FACING, Direction.EAST)
                    .setValue(net.minecraft.world.level.block.AbstractFurnaceBlock.LIT, true));
            level.setBlockAndUpdate(blast.above(), Blocks.SMOKER.defaultBlockState()
                    .setValue(net.minecraft.world.level.block.AbstractFurnaceBlock.FACING, Direction.EAST)
                    .setValue(net.minecraft.world.level.block.AbstractFurnaceBlock.LIT, true));
            for (BlockPos machine : List.of(blast, blast.above())) {
                ((Container) level.getBlockEntity(machine)).setItem(1, new ItemStack(Items.COAL_BLOCK, 64));
            }
        }
    }

    private static UUID showRangeNetwork;

    /**
     * Upgrade, vínculos e perfis do roteador de alcance, depois do {@code onLoad} dele (como nos GameTests):
     * antes disso o motor não conhece as máquinas remotas.
     */
    static void showcaseRangeLink(MinecraftServer server) {
        RouterBlockEntity router = router(server, showRange);
        router.setRangeUpgrade(new ItemStack(ModItems.RANGE_UPGRADE.get()));
        router.setMode(ResourceType.ITEM, Direction.UP, PortMode.EXTRACT);
        for (int z = -22; z <= -18; z++) {
            BlockPos blast = showBase.offset(-11, 0, z);
            router.linkMachine(blast, showRangeNetwork);
            router.linkMachine(blast.above(), showRangeNetwork);
        }
        Filter ores = new Filter(Filter.ListMode.WHITELIST, false, List.of(
                new FilterEntry.ItemEntry(new ItemStack(Items.RAW_IRON), 0),
                new FilterEntry.ItemEntry(new ItemStack(Items.RAW_GOLD), 0),
                new FilterEntry.ItemEntry(new ItemStack(Items.RAW_COPPER), 0)));
        Filter meat = new Filter(Filter.ListMode.WHITELIST, false, List.of(
                new FilterEntry.ItemEntry(new ItemStack(Items.BEEF), 0),
                new FilterEntry.ItemEntry(new ItemStack(Items.PORKCHOP), 0),
                new FilterEntry.ItemEntry(new ItemStack(Items.CHICKEN), 0)));
        for (var group : router.remoteMachines().groups()) {
            EditTarget target = EditTarget.group(group.id());
            router.configureFace(target, ResourceType.ITEM, Direction.UP, PortMode.INSERT, 0,
                    io.github.matheusanbs.wirelessautomate.network.RedstoneMode.IGNORE);
            router.setFilter(target, ResourceType.ITEM, Direction.UP,
                    group.block().getPath().equals("smoker") ? meat : ores);
        }
    }

    /**
     * Ala norte: os armazenamentos do mod numa galeria, em tiers variados (Baú Unobtainium com dezenas de tipos e milhões de
     * itens, Tanque, Bateria, Tanque Químico com o Mekanism e Tanque de Source com o Ars), cada um com
     * um roteador Avançado que tira para um igual Ultimate sob o piso: o fluxo dura a sessão inteira e
     * dá números de verdade ao Tablet.
     */
    private static void showcaseStorage(MinecraftServer server, ServerLevel level, UUID network) {
        StorageKind[] kinds = StorageKind.values();
        RouterTier[] tiers = {atm(RouterTier.UNOBTAINIUM, RouterTier.ULTIMATE), atm(RouterTier.VIBRANIUM, RouterTier.EMERALD),
                RouterTier.ULTIMATE, atm(RouterTier.ALLTHEMODIUM, RouterTier.EMERALD), RouterTier.EMERALD};
        String[] names = {"Main Storage", "Water Tank", "Base Battery", "Hydrogen", "Source Reserve"};
        for (int i = 0; i < kinds.length; i++) {
            if (!kinds[i].loaded()) {
                continue;
            }
            BlockPos pos = showBase.offset(-4 + 2 * i, 0, -16);
            level.setBlockAndUpdate(pos, ModBlocks.STORAGE.get(kinds[i]).get().defaultBlockState()
                    .setValue(RouterBlock.TIER, tiers[i]));
            showStorage.add(pos);
            switch (level.getBlockEntity(pos)) {
                case StorageChestBlockEntity chest -> {
                    Item[] items = {Items.COBBLESTONE, Items.IRON_INGOT, Items.GOLD_INGOT, Items.COPPER_INGOT, Items.DIAMOND,
                            Items.EMERALD, Items.REDSTONE, Items.LAPIS_LAZULI, Items.COAL, Items.QUARTZ, Items.OAK_LOG,
                            Items.SPRUCE_LOG, Items.BIRCH_LOG, Items.DIRT, Items.SAND, Items.GRAVEL, Items.GLASS,
                            Items.RAW_IRON, Items.RAW_GOLD, Items.RAW_COPPER, Items.NETHERITE_INGOT, Items.ANCIENT_DEBRIS,
                            Items.OBSIDIAN, Items.GLOWSTONE_DUST, Items.AMETHYST_SHARD, Items.BONE, Items.STRING,
                            Items.GUNPOWDER, Items.ENDER_PEARL, Items.BLAZE_ROD, Items.SLIME_BALL, Items.WHEAT,
                            Items.CARROT, Items.POTATO, Items.SUGAR_CANE, Items.BAMBOO, Items.KELP, Items.CLAY_BALL,
                            Items.FLINT, Items.LEATHER, Items.FEATHER, Items.EGG, Items.APPLE, Items.TORCH};
                    long amount = 126_000_000L;
                    for (Item item : items) {
                        chest.storage().insert(new ItemStack(item), amount, false);
                        amount = Math.max(1, amount * 6 / 10);
                    }
                }
                case StorageTankBlockEntity tank -> {
                    tank.storage().insert(new FluidStack(Fluids.WATER, 1), 80_000_000L, false);
                    tank.storage().insert(new FluidStack(Fluids.LAVA, 1), 48_000_000L, false);
                }
                case StorageBatteryBlockEntity battery -> battery.store().insert(60_000_000_000L, false);
                case StorageChemicalTankBlockEntity chemical ->
                        chemical.storage().insert(ResourceLocation.parse("mekanism:hydrogen"), 100_000_000L, false);
                case StorageSourceTankBlockEntity source -> source.store().insert(4_000_000L, false);
                default -> {
                }
            }
            ResourceType type = kinds[i].resource;
            placeShowRouter(server, level, pos, "advanced", names[i], network);
            router(server, pos.above()).setMode(type, Direction.UP, PortMode.EXTRACT);
            // O cofre sob o piso, que recebe.
            BlockPos vault = pos.below(3);
            level.setBlockAndUpdate(vault, ModBlocks.STORAGE.get(kinds[i]).get().defaultBlockState()
                    .setValue(RouterBlock.TIER, RouterTier.ULTIMATE));
            placeShowRouter(server, level, vault, "ultimate", "Vault " + (i + 1), network);
            router(server, vault.above()).setMode(type, Direction.UP, PortMode.INSERT);
        }
    }

    /**
     * Centro: a vitrine dos itens do mod, cada um flutuando sobre um pedestal de quartzo (entidades
     * de exibição, sempre de frente para a câmera e com luz cheia).
     */
    private static void showcaseDisplay(MinecraftServer server) {
        String guide = ModList.get().isLoaded("guideme")
                ? "{id:\"guideme:guide\",count:1,components:{\"guideme:guide_id\":\"wirelessautomate:guide\"}}"
                : "{id:\"wirelessautomate:router\",count:1}";
        String[][] rows = {
                {"{id:\"wirelessautomate:linker\",count:1}", "{id:\"wirelessautomate:configurator\",count:1}",
                        "{id:\"wirelessautomate:network_tablet\",count:1}", "{id:\"wirelessautomate:filter_card\",count:1}",
                        guide},
                {cardSnbt(RouterTier.EMERALD), cardSnbt(atm(RouterTier.VIBRANIUM, RouterTier.ELITE)),
                        cardSnbt(RouterTier.ULTIMATE),
                        "{id:\"wirelessautomate:chunk_loader_upgrade\",count:1}", "{id:\"wirelessautomate:router\",count:1}"}};
        int[] zs = {-12, -9};
        for (int r = 0; r < rows.length; r++) {
            for (int i = 0; i < rows[r].length; i++) {
                BlockPos pos = showBase.offset(-4 + 2 * i, 1, zs[r]);
                command(server, String.format(java.util.Locale.ROOT,
                        "summon minecraft:item_display %.2f %.2f %.2f {item:%s,billboard:\"center\","
                                + "brightness:{sky:15,block:15},transformation:{left_rotation:[0f,0f,0f,1f],"
                                + "right_rotation:[0f,0f,0f,1f],translation:[0f,0f,0f],scale:[0.8f,0.8f,0.8f]}}",
                        pos.getX() + 0.5, pos.getY() + 0.45, pos.getZ() + 0.5, rows[r][i]));
            }
        }
    }

    /**
     * O centro, em x, do estande da cascata no jardim, e a fileira da frente em z (a câmera olha de dentro da casa,
     * por cima da mureta sul, para o estande ficar contra a grama e o céu; as outras duas fileiras sobem para o sul).
     */
    static final int CASCADE_X = -9;
    static final int CASCADE_Z = 2;
    /** Meia largura do estande: 11 blocos, uma peça da cascata por bloco. */
    private static final int CASCADE_HALF = 5;

    /**
     * Jardim, a oeste do caminho (o Ressonador fica a leste): a cascata do Ressonador num estande de quartzo em
     * três degraus. Atrás, no alto, as onze peças na ordem da fabricação (da Liga de Sinal crua à Moldura de Liga);
     * no meio os Corações do Ressonador, um por tier; na frente o Pó de Ressonância, os Pós Carregados e os Cristais
     * Afinados. Sem o Allthemodium, os itens dos tiers dele ficam de fora e cada fileira se centra sozinha.
     */
    private static void showcaseCascade(MinecraftServer server, ServerLevel level) {
        int x0 = CASCADE_X - CASCADE_HALF - 1, x1 = CASCADE_X + CASCADE_HALF + 1;
        for (int x = x0; x <= x1; x++) {
            for (int z = CASCADE_Z - 1; z <= CASCADE_Z + 3; z++) {
                boolean border = x == x0 || x == x1 || z == CASCADE_Z - 1 || z == CASCADE_Z + 3;
                put(level, x, -1, z, (border ? Blocks.POLISHED_DEEPSLATE : Blocks.SMOOTH_QUARTZ).defaultBlockState());
            }
        }
        // Três degraus: a fileira de trás (ao sul) com 3 blocos de altura, a do meio com 2 e a da frente com 1.
        for (int step = 0; step < 3; step++) {
            for (int x = CASCADE_X - CASCADE_HALF; x <= CASCADE_X + CASCADE_HALF; x++) {
                for (int y = 0; y < 3 - step; y++) {
                    put(level, x, y, CASCADE_Z + 2 - step, Blocks.SMOOTH_QUARTZ.defaultBlockState());
                }
            }
        }
        for (int[] light : new int[][] {{x0, CASCADE_Z - 1}, {x1, CASCADE_Z - 1}, {x0, CASCADE_Z + 3}, {x1, CASCADE_Z + 3}}) {
            put(level, light[0], -1, light[1], Blocks.SEA_LANTERN.defaultBlockState());
        }
        List<String> cascade = new ArrayList<>();
        for (var item : ModItems.CASCADE) {
            cascade.add(itemSnbt(item.getId()));
        }
        List<String> hearts = new ArrayList<>();
        for (RouterTier tier : RouterTier.values()) {
            if (tier.loaded()) {
                hearts.add(itemSnbt(ModItems.RESONATOR_HEARTS.get(tier).getId()));
            }
        }
        List<String> front = new ArrayList<>();
        front.add(itemSnbt(ModItems.RESONANCE_DUST.getId()));
        for (RouterTier tier : RouterTier.values()) {
            if (tier.loaded() && ModItems.CHARGED_DUSTS.containsKey(tier)) {
                front.add(itemSnbt(ModItems.CHARGED_DUSTS.get(tier).getId()));
            }
        }
        for (RouterTier tier : RouterTier.values()) {
            if (tier.loaded() && ModItems.TUNED_CRYSTALS.containsKey(tier)) {
                front.add(itemSnbt(ModItems.TUNED_CRYSTALS.get(tier).getId()));
            }
        }
        List<List<String>> rows = List.of(cascade, hearts, front);
        for (int step = 0; step < rows.size(); step++) {
            List<String> row = rows.get(step);
            double y = showBase.getY() + (3 - step) + 0.45;
            double z = showBase.getZ() + CASCADE_Z + 2 - step + 0.5;
            for (int i = 0; i < row.size(); i++) {
                // Vista de dentro da casa (olhando para o sul), a primeira peça fica à esquerda, no leste.
                double x = showBase.getX() + CASCADE_X + 0.5 + (row.size() - 1) / 2.0 - i;
                command(server, String.format(java.util.Locale.ROOT,
                        "summon minecraft:item_display %.2f %.2f %.2f {item:%s,billboard:\"center\","
                                + "brightness:{sky:15,block:15},transformation:{left_rotation:[0f,0f,0f,1f],"
                                + "right_rotation:[0f,0f,0f,1f],translation:[0f,0f,0f],scale:[0.8f,0.8f,0.8f]}}",
                        x, y, z, row.get(i)));
            }
        }
    }

    /** O centro do altar do Reparador: no jardim, a leste da Vitrine do Ressonador. */
    static final int REPAIRER_X = 13, REPAIRER_Z = 4;

    /**
     * Jardim, a leste da Vitrine do Ressonador: o altar do Reparador de Cristais. Um piso de quartzo com borda de
     * ardósia polida, um pódio de quartzo, 3 por 2, com o bloco no topo (em y 1), quatro
     * colunas de quartzo com lanternas nos cantos, tapete ciano na frente e flores em volta.
     */
    private static void showcaseRepairerStand(ServerLevel level) {
        int r = 4;
        for (int x = REPAIRER_X - r; x <= REPAIRER_X + r; x++) {
            for (int z = REPAIRER_Z - r; z <= REPAIRER_Z + r; z++) {
                boolean border = Math.abs(x - REPAIRER_X) == r || Math.abs(z - REPAIRER_Z) == r;
                put(level, x, -1, z, (border ? Blocks.POLISHED_DEEPSLATE : Blocks.SMOOTH_QUARTZ).defaultBlockState());
            }
        }
        for (int x = REPAIRER_X - 1; x <= REPAIRER_X + 1; x++) {
            for (int z = REPAIRER_Z - 1; z <= REPAIRER_Z; z++) {
                put(level, x, 0, z, Blocks.QUARTZ_BLOCK.defaultBlockState());
            }
        }
        put(level, REPAIRER_X, 0, REPAIRER_Z, Blocks.CHISELED_QUARTZ_BLOCK.defaultBlockState());
        for (int[] corner : new int[][] {{-3, -3}, {3, -3}, {-3, 3}, {3, 3}}) {
            int x = REPAIRER_X + corner[0], z = REPAIRER_Z + corner[1];
            put(level, x, 0, z, Blocks.QUARTZ_PILLAR.defaultBlockState());
            put(level, x, 1, z, Blocks.QUARTZ_PILLAR.defaultBlockState());
            put(level, x, 2, z, Blocks.CHISELED_QUARTZ_BLOCK.defaultBlockState());
            put(level, x, 3, z, Blocks.LANTERN.defaultBlockState());
            put(level, x, -1, z, Blocks.SEA_LANTERN.defaultBlockState());
        }
        for (int x = REPAIRER_X - 1; x <= REPAIRER_X + 1; x++) {
            put(level, x, 0, REPAIRER_Z + 2, Blocks.CYAN_CARPET.defaultBlockState());
            put(level, x, 0, REPAIRER_Z + 3, Blocks.CYAN_CARPET.defaultBlockState());
        }
        net.minecraft.world.level.block.Block[] flowers = {Blocks.AZURE_BLUET, Blocks.CORNFLOWER, Blocks.ALLIUM,
            Blocks.OXEYE_DAISY};
        for (int i = 0; i < 4; i++) {
            for (int side = -1; side <= 1; side += 2) {
                put(level, REPAIRER_X + side * 2, 0, REPAIRER_Z - 2 + i, flowers[i].defaultBlockState());
            }
        }
    }

    private static String itemSnbt(ResourceLocation id) {
        return "{id:\"" + id + "\",count:1}";
    }

    /** O Cartão de Upgrade de um degrau para {@code tier} (origem no degrau anterior), em SNBT para o summon. */
    private static String cardSnbt(RouterTier tier) {
        RouterTier from = tier.previous();
        return "{id:\"wirelessautomate:tier_core_" + tier.getSerializedName() + "\",count:1"
                + (from == null ? "" : ",components:{\"wirelessautomate:upgrade_from\":\"" + from.getSerializedName()
                        + "\"}") + "}";
    }

    /**
     * Fundo da casa: o salão dos tiers. Os oito roteadores em fila (Básico à esquerda), cada um sobre um
     * pedestal com o bloco do material do tier (ferro, ouro, diamante, esmeralda, os três metais do
     * Allthemodium e netherita) e o Cartão de Upgrade flutuando em cima; tapete laranja sob os do ATM.
     * Sem o Allthemodium, os três do meio ficam de fora.
     */
    private static void showcaseTiers(MinecraftServer server, ServerLevel level, UUID network) {
        String[] materials = {"minecraft:iron_block", "minecraft:gold_block", "minecraft:diamond_block",
                "minecraft:emerald_block", "allthemodium:allthemodium_block", "allthemodium:vibranium_block",
                "allthemodium:unobtainium_block", "minecraft:netherite_block"};
        String[] names = {"Basic", "Advanced", "Elite", "Emerald", "Allthemodium", "Vibranium", "Unobtainium", "Ultimate"};
        RouterTier[] tiers = RouterTier.values();
        for (int x = -9; x <= 9; x++) {
            for (int z = TIER_HALL_Z - 1; z <= TIER_HALL_Z + 1; z++) {
                boolean atmBand = x >= 0 && x <= 6 && tiers[4].loaded();
                put(level, x, 0, z, (atmBand ? Blocks.ORANGE_CARPET : Blocks.GRAY_CARPET).defaultBlockState());
            }
        }
        for (int i = 0; i < tiers.length; i++) {
            if (!tiers[i].loaded()) {
                continue;
            }
            int x = -7 + 2 * i;
            put(level, x, 0, TIER_HALL_Z, Blocks.CHISELED_QUARTZ_BLOCK.defaultBlockState());
            command(server, "setblock " + at(x, 1, TIER_HALL_Z) + " " + materials[i]);
            BlockPos block = showBase.offset(x, 1, TIER_HALL_Z);
            placeShowRouter(server, level, block, tiers[i].getSerializedName(), names[i] + " Router", network);
            if (ModItems.TIER_CORES.containsKey(tiers[i])) {
                command(server, String.format(java.util.Locale.ROOT,
                        "summon minecraft:item_display %.2f %.2f %.2f {item:%s,"
                                + "billboard:\"center\",brightness:{sky:15,block:15},transformation:{left_rotation:[0f,0f,0f,1f],"
                                + "right_rotation:[0f,0f,0f,1f],translation:[0f,0f,0f],scale:[0.7f,0.7f,0.7f]}}",
                        block.getX() + 0.5, block.getY() + 2.5, block.getZ() + 0.5, cardSnbt(tiers[i])));
            }
        }
        // Lanternas no piso nas pontas da fila.
        for (int x : new int[] {-9, 9}) {
            put(level, x, -1, TIER_HALL_Z, Blocks.SEA_LANTERN.defaultBlockState());
        }
    }

    /**
     * Ala leste, com o Ars: piso de pedra de Source, um Enchanting Apparatus com quatro pedestais,
     * Sourcelinks, uma Source Jar criativa cujo roteador manda a Source sem fio para um Tanque de
     * Source e uma Source Jar, e quatro tiers do Tanque de Source (vazio, 30%, 60% e cheio) junto à
     * parede sul.
     */
    private static void showcaseArcane(MinecraftServer server, ServerLevel level, UUID network) {
        command(server, "fill " + at(6, -1, -17) + " " + at(10, -1, -6) + " ars_nouveau:smooth_sourcestone_large_bricks");
        command(server, "setblock " + at(9, 0, -11) + " ars_nouveau:arcane_core");
        command(server, "setblock " + at(9, 1, -11) + " ars_nouveau:enchanting_apparatus[facing=up]");
        for (int[] p : new int[][] {{7, -11}, {11, -11}, {9, -13}, {9, -9}}) {
            command(server, "setblock " + at(p[0], 0, p[1]) + " ars_nouveau:arcane_pedestal[facing=up]");
        }
        command(server, "setblock " + at(11, 0, -14) + " ars_nouveau:volcanic_sourcelink");
        command(server, "setblock " + at(11, 0, -8) + " ars_nouveau:agronomic_sourcelink");
        command(server, "setblock " + at(6, 0, -14) + " ars_nouveau:mycelial_sourcelink");
        for (int[] c : new int[][] {{11, -17}, {6, -6}, {11, -6}}) {
            put(level, c[0], 0, c[1], Blocks.PURPLE_CANDLE.defaultBlockState()
                    .setValue(net.minecraft.world.level.block.CandleBlock.CANDLES, 3)
                    .setValue(net.minecraft.world.level.block.CandleBlock.LIT, true));
        }
        put(level, 7, 0, -14, Blocks.AMETHYST_BLOCK.defaultBlockState());
        put(level, 7, 1, -14, Blocks.AMETHYST_CLUSTER.defaultBlockState());
        // Jarra criativa (origem), Tanque de Source e uma Source Jar (destinos) na rede do laboratório.
        BlockPos tank = showBase.offset(7, 0, -16);
        showSourceJar = showBase.offset(9, 0, -16);
        BlockPos jar = showBase.offset(11, 0, -16);
        level.setBlockAndUpdate(tank, ModBlocks.STORAGE.get(StorageKind.SOURCE_TANK).get().defaultBlockState()
                .setValue(RouterBlock.TIER, RouterTier.ADVANCED));
        command(server, "setblock " + at(9, 0, -16) + " ars_nouveau:creative_source_jar");
        command(server, "setblock " + at(11, 0, -16) + " ars_nouveau:source_jar");
        placeShowRouter(server, level, showSourceJar, "advanced", "Source Supply", network);
        placeShowRouter(server, level, tank, "advanced", "Apparatus Tank", network);
        placeShowRouter(server, level, jar, "basic", "Source Jar", network);
        router(server, showSourceJar.above()).setMode(ResourceType.SOURCE, Direction.UP, PortMode.EXTRACT);
        router(server, tank.above()).setMode(ResourceType.SOURCE, Direction.UP, PortMode.INSERT);
        router(server, jar.above()).setMode(ResourceType.SOURCE, Direction.UP, PortMode.INSERT);
        // Quatro tiers, sem roteador, cada um num nível: vazio, 30%, 60% e cheio.
        RouterTier[] tiers = {RouterTier.ELITE, RouterTier.EMERALD, atm(RouterTier.VIBRANIUM, RouterTier.ADVANCED),
                RouterTier.ULTIMATE};
        long[] contents = new long[4];
        for (int i = 1; i < 3; i++) {
            contents[i] = io.github.matheusanbs.wirelessautomate.Config.storageCapacity(StorageKind.SOURCE_TANK, tiers[i]) * 3 * i / 10;
        }
        contents[3] = 3_000_000_000L;
        for (int i = 0; i < 4; i++) {
            // Vistos de dentro (olhando para o sul), o Básico fica à esquerda.
            BlockPos pos = showBase.offset(11 - 2 * i, 0, -5);
            level.setBlockAndUpdate(pos, ModBlocks.STORAGE.get(StorageKind.SOURCE_TANK).get().defaultBlockState()
                    .setValue(RouterBlock.TIER, tiers[i]));
            if (contents[i] > 0) {
                ((StorageSourceTankBlockEntity) level.getBlockEntity(pos)).store().insert(contents[i], false);
            }
            showTanks.add(pos);
        }
    }

    /**
     * Inventário de exemplo para as fotos do filtro: ferramentas encantadas gastas e novas, uma
     * comum gasta, livro encantado e minérios, para a regra "Encantado + durabilidade < 50%" acender
     * só as que vão para o reparo.
     */
    static void showcaseInventory(MinecraftServer server, ServerPlayer player) {
        var enchantments = server.registryAccess().lookupOrThrow(Registries.ENCHANTMENT);
        ItemStack wornPick = new ItemStack(Items.DIAMOND_PICKAXE);
        wornPick.enchant(enchantments.getOrThrow(net.minecraft.world.item.enchantment.Enchantments.FORTUNE), 3);
        wornPick.setDamageValue(1300);
        ItemStack newPick = new ItemStack(Items.DIAMOND_PICKAXE);
        newPick.enchant(enchantments.getOrThrow(net.minecraft.world.item.enchantment.Enchantments.EFFICIENCY), 5);
        ItemStack wornSword = new ItemStack(Items.NETHERITE_SWORD);
        wornSword.enchant(enchantments.getOrThrow(net.minecraft.world.item.enchantment.Enchantments.SHARPNESS), 5);
        wornSword.setDamageValue(1500);
        ItemStack plainWorn = new ItemStack(Items.IRON_PICKAXE);
        plainWorn.setDamageValue(200);
        ItemStack wornChest = new ItemStack(Items.DIAMOND_CHESTPLATE);
        wornChest.enchant(enchantments.getOrThrow(net.minecraft.world.item.enchantment.Enchantments.PROTECTION), 4);
        wornChest.setDamageValue(400);
        ItemStack[] items = {wornPick, newPick, wornSword, plainWorn, wornChest,
                net.minecraft.world.item.EnchantedBookItem.createForEnchantment(new net.minecraft.world.item.enchantment.EnchantmentInstance(
                        enchantments.getOrThrow(net.minecraft.world.item.enchantment.Enchantments.MENDING), 1)),
                new ItemStack(Items.RAW_IRON, 48), new ItemStack(Items.RAW_GOLD, 23), new ItemStack(Items.COAL, 64),
                new ItemStack(Items.IRON_ORE, 12), new ItemStack(Items.DEEPSLATE_DIAMOND_ORE, 3), new ItemStack(Items.TORCH, 40)};
        player.getInventory().clearContent();
        for (int i = 0; i < items.length; i++) {
            player.getInventory().setItem(9 + i, items[i]);
        }
        player.getInventory().setItem(0, new ItemStack(Items.DIAMOND_SWORD));
    }

    /** Entrada cheia de minério e saída vazia, para a tela mostrar itens passando. */
    static void refillShowcase(ServerLevel level) {
        ((Container) level.getBlockEntity(showOutput)).clearContent();
        Container input = (Container) level.getBlockEntity(showInput);
        for (int slot = 0; slot < input.getContainerSize(); slot++) {
            input.setItem(slot, new ItemStack(slot % 3 == 0 ? Items.RAW_IRON : slot % 3 == 1 ? Items.RAW_GOLD
                    : Items.RAW_COPPER, 64));
        }
    }

    private static void placeShowRouter(MinecraftServer server, ServerLevel level, BlockPos machine, String tier, String name,
            UUID network) {
        BlockPos pos = machine.above();
        level.setBlockAndUpdate(pos, ModBlocks.ROUTER.get().defaultBlockState().setValue(RouterBlock.FACING, Direction.UP)
                .setValue(RouterBlock.TIER, io.github.matheusanbs.wirelessautomate.block.RouterTier.valueOf(tier.toUpperCase(java.util.Locale.ROOT))));
        RouterBlockEntity router = router(server, pos);
        router.setNetworkId(network);
        router.setName(name);
        showRouters.add(pos);
    }
}
