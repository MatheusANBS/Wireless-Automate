package io.github.matheusanbs.wirelessautomate.gametest;

import io.github.matheusanbs.wirelessautomate.Config;
import io.github.matheusanbs.wirelessautomate.WirelessAutomate;
import io.github.matheusanbs.wirelessautomate.block.RouterBlock;
import io.github.matheusanbs.wirelessautomate.block.RouterTier;
import io.github.matheusanbs.wirelessautomate.dock.DockMachine;
import io.github.matheusanbs.wirelessautomate.dock.DockSide;
import io.github.matheusanbs.wirelessautomate.dock.PatternDockBlock;
import io.github.matheusanbs.wirelessautomate.dock.PatternDockBlockEntity;
import io.github.matheusanbs.wirelessautomate.item.TierCoreItem;
import io.github.matheusanbs.wirelessautomate.network.PortMode;
import io.github.matheusanbs.wirelessautomate.network.ResourceType;
import io.github.matheusanbs.wirelessautomate.range.RemoteIndex;
import io.github.matheusanbs.wirelessautomate.range.RemoteSide;
import io.github.matheusanbs.wirelessautomate.registry.ModBlocks;
import io.github.matheusanbs.wirelessautomate.storage.StorageBlockItem;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * O bloco da Doca de Padrões: o tier, o NBT dos lados, o Cartão de Upgrade, o que cai ao quebrar, o alcance pela
 * config e o registro das máquinas no índice das remotas.
 */
@GameTestHolder(WirelessAutomate.MODID)
@PrefixGameTestTemplate(false)
public final class DockBlockGameTests {
    private static final BlockPos DOCK = new BlockPos(2, 1, 2);
    private static final int NORTH = Direction.NORTH.ordinal();
    private static final ResourceLocation FURNACE = BuiltInRegistries.BLOCK.getKey(Blocks.FURNACE);

    private DockBlockGameTests() {
    }

    private static PatternDockBlockEntity dock(GameTestHelper helper, RouterTier tier) {
        helper.setBlock(DOCK, ModBlocks.PATTERN_DOCK.get().defaultBlockState().setValue(RouterBlock.TIER, tier));
        return helper.getBlockEntity(DOCK);
    }

    /** A Doca colocada num tier tem o block entity e lê o tier do estado. */
    @GameTest(template = "empty", timeoutTicks = 100)
    public static void placedDockReadsTier(GameTestHelper helper) {
        PatternDockBlockEntity dock = dock(helper, RouterTier.ELITE);
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertBlockProperty(DOCK, RouterBlock.TIER, RouterTier.ELITE))
                .thenExecute(() -> {
                    helper.assertTrue(dock.tier() == RouterTier.ELITE, "tier lido: " + dock.tier());
                    for (int side = 0; side < PatternDockBlockEntity.SIDES; side++) {
                        helper.assertTrue(dock.side(side) == null, "lado " + side + " existe sem vínculo");
                    }
                })
                .thenSucceed();
    }

    /** Um lado com duas máquinas, perfil, cursor e um pendente maior que um stack salvo volta igual num block entity novo. */
    @GameTest(template = "empty", timeoutTicks = 100)
    public static void sideRoundTrip(GameTestHelper helper) {
        PatternDockBlockEntity dock = dock(helper, RouterTier.BASIC);
        BlockPos first = helper.absolutePos(new BlockPos(4, 1, 2));
        BlockPos second = helper.absolutePos(new BlockPos(4, 1, 4));
        helper.assertTrue(dock.link(NORTH, first, FURNACE, Direction.EAST.ordinal()), "não vinculou a primeira");
        helper.assertTrue(dock.link(NORTH, second, FURNACE, -1), "não vinculou a segunda");
        helper.assertFalse(dock.link(NORTH, second, FURNACE, -1), "vinculou duas vezes a mesma");
        helper.assertFalse(dock.link(NORTH, helper.absolutePos(new BlockPos(0, 1, 0)),
                BuiltInRegistries.BLOCK.getKey(Blocks.CHEST), -1), "vinculou um bloco que não é o do lado");
        DockSide side = dock.side(NORTH);
        side.profile().face(ResourceType.ITEM, RemoteSide.TOP).setMode(PortMode.INSERT);
        side.setCursor(1);
        side.setPending(List.of(new ItemStack(Items.COBBLESTONE, 150)), List.of(), first);

        HolderLookup.Provider registries = helper.getLevel().registryAccess();
        CompoundTag tag = dock.saveWithoutMetadata(registries);
        PatternDockBlockEntity copy = new PatternDockBlockEntity(dock.getBlockPos(), dock.getBlockState());
        copy.loadWithComponents(tag, registries);

        DockSide loaded = copy.side(NORTH);
        helper.assertTrue(loaded != null, "o lado norte não voltou");
        helper.assertTrue(FURNACE.equals(loaded.block()), "bloco do lado: " + loaded.block());
        helper.assertTrue(loaded.machines().size() == 2, "máquinas: " + loaded.machines().size());
        DockMachine a = loaded.machines().get(0);
        DockMachine b = loaded.machines().get(1);
        helper.assertTrue(a.pos().equals(first) && a.front() == Direction.EAST.ordinal(), "primeira: " + a.pos() + " " + a.front());
        helper.assertTrue(b.pos().equals(second) && b.front() == -1, "segunda: " + b.pos() + " " + b.front());
        helper.assertTrue(copy.machineAt(second) == b, "a segunda não está no mapa por posição");
        helper.assertTrue(loaded.profile().face(ResourceType.ITEM, RemoteSide.TOP).mode() == PortMode.INSERT,
                "o perfil não voltou");
        helper.assertTrue(loaded.cursor() == 1, "cursor: " + loaded.cursor());
        int total = 0;
        for (ItemStack stack : loaded.pendingItems()) {
            helper.assertTrue(stack.is(Items.COBBLESTONE), "pendente com outro item: " + stack);
            total += stack.getCount();
        }
        helper.assertTrue(total == 150, "pendente: " + total);
        helper.assertTrue(loaded.pendingAt().equals(first), "pending_at: " + loaded.pendingAt());
        helper.assertTrue(copy.side(Direction.SOUTH.ordinal()) == null, "apareceu um lado sul");

        // Fluido no pendente: salva e volta pela chave fluid.
        side.setPending(List.of(), List.of(new FluidStack(Fluids.WATER, 1_000)), second);
        PatternDockBlockEntity fluidCopy = new PatternDockBlockEntity(dock.getBlockPos(), dock.getBlockState());
        fluidCopy.loadWithComponents(dock.saveWithoutMetadata(registries), registries);
        DockSide fluidSide = fluidCopy.side(NORTH);
        helper.assertTrue(fluidSide.pendingItems().isEmpty() && fluidSide.pendingFluids().size() == 1
                && fluidSide.pendingFluids().get(0).getAmount() == 1_000, "o pendente de fluido não voltou");
        helper.succeed();
    }

    /** O {@code tryUpgrade} e o Cartão de Upgrade na mão sobem o tier no lugar, sem trocar o block entity. */
    @GameTest(template = "empty", timeoutTicks = 100)
    public static void upgradeCardRaisesTierInPlace(GameTestHelper helper) {
        PatternDockBlockEntity dock = dock(helper, RouterTier.BASIC);
        BlockPos machine = helper.absolutePos(new BlockPos(4, 1, 2));
        dock.link(NORTH, machine, FURNACE, -1);
        BlockPos absolute = helper.absolutePos(DOCK);
        helper.assertTrue(PatternDockBlock.tryUpgrade(helper.getLevel(), absolute, RouterTier.ADVANCED, RouterTier.BASIC),
                "tryUpgrade não subiu o Básico");
        helper.assertBlockProperty(DOCK, RouterBlock.TIER, RouterTier.ADVANCED);
        helper.assertFalse(PatternDockBlock.tryUpgrade(helper.getLevel(), absolute, RouterTier.ULTIMATE, RouterTier.BASIC),
                "o cartão Básico → Ultimate subiu um Avançado");

        ServerPlayer player = TestPlayers.inLevel(helper);
        player.setGameMode(GameType.SURVIVAL);
        ItemStack card = TierCoreItem.card(RouterTier.ELITE, RouterTier.ADVANCED);
        player.setItemInHand(InteractionHand.MAIN_HAND, card);
        InteractionResult result = card.getItem().useOn(new UseOnContext(player, InteractionHand.MAIN_HAND,
                new BlockHitResult(Vec3.atCenterOf(absolute), Direction.UP, absolute, false)));
        helper.assertTrue(result.consumesAction(), "o cartão Avançado → Elite não subiu a Doca: " + result);
        helper.assertBlockProperty(DOCK, RouterBlock.TIER, RouterTier.ELITE);
        helper.assertTrue(card.isEmpty(), "o cartão não foi gasto");
        PatternDockBlockEntity after = helper.getBlockEntity(DOCK);
        helper.assertTrue(after == dock, "o block entity mudou");
        helper.assertTrue(after.machineAt(machine) != null, "o vínculo sumiu");
        helper.succeed();
    }

    /** A dica do cartão "Básico → Avançado" mostra o raio e as máquinas da Doca: "raio 6 → 12, máquinas 8 → 16". */
    @GameTest(template = "empty", timeoutTicks = 100)
    public static void upgradeCardTooltipShowsDockReach(GameTestHelper helper) {
        ItemStack card = TierCoreItem.card(RouterTier.ADVANCED, RouterTier.BASIC);
        List<Component> lines = new ArrayList<>();
        card.getItem().appendHoverText(card, Item.TooltipContext.EMPTY, lines, TooltipFlag.NORMAL);
        List<String> texts = lines.stream().map(Component::getString).toList();
        String key = "item.wirelessautomate.tier_core.";
        String expected = Component.translatable(key + "dock",
                Component.translatable(key + "change", TierCoreItem.grouped(6), TierCoreItem.grouped(12)),
                Component.translatable(key + "change", TierCoreItem.grouped(8), TierCoreItem.grouped(16))).getString();
        helper.assertTrue(texts.contains(expected), "sem a linha da Doca (" + expected + "): " + texts);
        helper.succeed();
    }

    /** Quebrada, a Doca cai como item no tier dela. */
    @GameTest(template = "empty", timeoutTicks = 100)
    public static void brokenDockKeepsTier(GameTestHelper helper) {
        dock(helper, RouterTier.EMERALD);
        BlockPos absolute = helper.absolutePos(DOCK);
        helper.getLevel().destroyBlock(absolute, true);
        List<ItemEntity> drops = helper.getLevel().getEntitiesOfClass(ItemEntity.class, new AABB(absolute).inflate(2),
                entity -> entity.getItem().is(ModBlocks.PATTERN_DOCK.get().asItem()));
        helper.assertTrue(drops.size() == 1, "Docas no chão: " + drops.size());
        RouterTier tier = StorageBlockItem.tierOf(drops.get(0).getItem());
        drops.get(0).discard();
        helper.assertTrue(tier == RouterTier.EMERALD, "o item caiu no tier " + tier);
        helper.succeed();
    }

    /**
     * Quebrada sem drop (o {@code destroyBlock} sem loot, como o {@code /setblock}), sem um item que leve o pendente, os
     * itens pendentes caem no chão.
     */
    @GameTest(template = "empty", timeoutTicks = 100)
    public static void pendingDropsOnBreak(GameTestHelper helper) {
        PatternDockBlockEntity dock = dock(helper, RouterTier.BASIC);
        BlockPos machine = helper.absolutePos(new BlockPos(4, 1, 2));
        dock.link(NORTH, machine, FURNACE, -1);
        dock.side(NORTH).setPending(List.of(new ItemStack(Items.IRON_ORE, 70), new ItemStack(Items.COAL, 3)), List.of(),
                machine);
        BlockPos absolute = helper.absolutePos(DOCK);
        helper.getLevel().destroyBlock(absolute, false);
        int ore = 0;
        int coal = 0;
        for (ItemEntity entity : helper.getLevel().getEntitiesOfClass(ItemEntity.class, new AABB(absolute).inflate(2))) {
            if (entity.getItem().is(Items.IRON_ORE)) {
                ore += entity.getItem().getCount();
                entity.discard();
            } else if (entity.getItem().is(Items.COAL)) {
                coal += entity.getItem().getCount();
                entity.discard();
            }
        }
        helper.assertTrue(ore == 70 && coal == 3, "no chão: " + ore + " minérios e " + coal + " carvões");
        helper.succeed();
    }

    /** O alcance da Doca é a linha do tier seguinte na config, e o Ultimate tem a dele: 6/8, 24/48 e 96/256. */
    @GameTest(template = "empty", timeoutTicks = 100)
    public static void dockReachFromConfig(GameTestHelper helper) {
        helper.assertTrue(Config.dockRadius(RouterTier.BASIC) == 6 && Config.dockMaxMachines(RouterTier.BASIC) == 8,
                "Básico: " + Config.dockRadius(RouterTier.BASIC) + "/" + Config.dockMaxMachines(RouterTier.BASIC));
        helper.assertTrue(Config.dockRadius(RouterTier.EMERALD) == 24 && Config.dockMaxMachines(RouterTier.EMERALD) == 48,
                "Esmeralda: " + Config.dockRadius(RouterTier.EMERALD) + "/" + Config.dockMaxMachines(RouterTier.EMERALD));
        helper.assertTrue(Config.dockRadius(RouterTier.ULTIMATE) == 96
                        && Config.dockMaxMachines(RouterTier.ULTIMATE) == 256,
                "Ultimate: " + Config.dockRadius(RouterTier.ULTIMATE) + "/" + Config.dockMaxMachines(RouterTier.ULTIMATE));
        helper.succeed();
    }

    /**
     * A máquina vinculada no alcance entra no índice das remotas com a Doca como dono; a de fora do raio não entra até o
     * tier subir; desvinculada, sai. Depois do {@code onLoad}, a carga refaz o mesmo registro.
     */
    @GameTest(template = "empty", timeoutTicks = 100)
    public static void dockIndexesMachinesInReach(GameTestHelper helper) {
        PatternDockBlockEntity dock = dock(helper, RouterTier.BASIC);
        BlockPos near = helper.absolutePos(DOCK.east(3));
        // Básico: raio 6; Elite: raio 24 (a linha da Esmeralda).
        BlockPos far = helper.absolutePos(DOCK.east(10));
        RemoteIndex index = RemoteIndex.get();
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertBlockPresent(ModBlocks.PATTERN_DOCK.get(), DOCK))
                .thenIdle(1)
                .thenExecute(() -> {
                    dock.link(NORTH, near, FURNACE, -1);
                    dock.link(NORTH, far, FURNACE, -1);
                    helper.assertTrue(index.listenersAt(helper.getLevel(), near).contains(dock),
                            "a máquina no alcance não está no índice");
                    helper.assertFalse(index.listenersAt(helper.getLevel(), far).contains(dock),
                            "a máquina fora do raio entrou no índice");
                    helper.assertTrue(dock.machineAt(near).active() && !dock.machineAt(far).active(),
                            "marca de ativa errada");

                    dock.onChunkUnloaded();
                    helper.assertFalse(index.isIndexed(dock, near), "ficou no índice com o chunk descarregado");
                    dock.onLoad();
                    helper.assertTrue(index.isIndexed(dock, near), "o onLoad não registrou de novo");

                    PatternDockBlock.tryUpgrade(helper.getLevel(), helper.absolutePos(DOCK), RouterTier.ELITE, null);
                    helper.assertTrue(index.listenersAt(helper.getLevel(), far).contains(dock),
                            "o tier subiu e a máquina de longe não entrou");

                    dock.unlink(NORTH, near);
                    helper.assertFalse(index.listenersAt(helper.getLevel(), near).contains(dock),
                            "a máquina desvinculada ficou no índice");
                    helper.assertTrue(dock.side(NORTH).block() != null, "o lado esqueceu o bloco com uma máquina");
                    dock.unlink(NORTH, far);
                    helper.assertTrue(dock.side(NORTH) == null, "o lado vazio continuou existindo");
                    helper.assertFalse(index.isIndexed(dock, far), "ficou no índice sem máquinas");
                })
                .thenSucceed();
    }
}
