package io.github.matheusanbs.wirelessautomate.gametest;

import static io.github.matheusanbs.wirelessautomate.gametest.RangeTestSupport.*;

import io.github.matheusanbs.wirelessautomate.Config;
import io.github.matheusanbs.wirelessautomate.WirelessAutomate;
import io.github.matheusanbs.wirelessautomate.block.RouterBlock;
import io.github.matheusanbs.wirelessautomate.block.RouterBlockEntity;
import io.github.matheusanbs.wirelessautomate.block.RouterTier;
import io.github.matheusanbs.wirelessautomate.item.ConfiguratorItem;
import io.github.matheusanbs.wirelessautomate.item.LinkerItem;
import io.github.matheusanbs.wirelessautomate.linker.LinkerActions;
import io.github.matheusanbs.wirelessautomate.linker.LinkerArea;
import io.github.matheusanbs.wirelessautomate.linker.LinkerFaces;
import io.github.matheusanbs.wirelessautomate.linker.LinkerMode;
import io.github.matheusanbs.wirelessautomate.linker.LinkerProblem;
import io.github.matheusanbs.wirelessautomate.linker.LinkerTabs;
import io.github.matheusanbs.wirelessautomate.menu.LinkerMenu;
import io.github.matheusanbs.wirelessautomate.menu.LinkerSnapshot;
import io.github.matheusanbs.wirelessautomate.network.ResourceType;
import io.github.matheusanbs.wirelessautomate.packet.LinkerActionPayload.Op;
import io.github.matheusanbs.wirelessautomate.packet.LinkerActionPayload;
import io.github.matheusanbs.wirelessautomate.preset.PasteMode;
import io.github.matheusanbs.wirelessautomate.range.RangeLinks;
import io.github.matheusanbs.wirelessautomate.range.RangeRules.LinkRefusal;
import io.github.matheusanbs.wirelessautomate.range.RemoteIndex;
import io.github.matheusanbs.wirelessautomate.range.RemoteMachines;
import io.github.matheusanbs.wirelessautomate.registry.ModBlocks;
import io.github.matheusanbs.wirelessautomate.registry.ModDataComponents;
import io.github.matheusanbs.wirelessautomate.registry.ModItems;
import io.github.matheusanbs.wirelessautomate.storage.StorageKind;
import java.util.function.Consumer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * Roteador de alcance, o Vinculador no modo Máquinas: vincular e desvincular pelo clique e pela área, as recusas
 * e o Configurador, que nunca usa esse modo.
 */
@GameTestHolder(WirelessAutomate.MODID)
@PrefixGameTestTemplate(false)
public final class RangeLinkerGameTests {
    private static final BlockPos SECOND_ROUTER_MACHINE = new BlockPos(2, 1, 2);
    private static final BlockPos STONE = new BlockPos(0, 1, 1);
    private static final BlockPos COMPOSTER = new BlockPos(2, 1, 1);

    private static void assertRefused(GameTestHelper helper, RangeLinks.Result result, LinkRefusal refusal, String what) {
        helper.assertTrue(result.status() == RangeLinks.Status.REFUSED && result.refusal() == refusal,
                what + ": esperava " + refusal + ", veio " + result);
    }

    /**
     * Modo Máquinas pelo clique: sem roteador escolhido não vincula; o clique no roteador o escolhe;
     * sem o upgrade recusa; com ele, o clique na fornalha vincula (e entra no índice) e o segundo
     * desvincula. Shift + clique marca um canto.
     */
    @GameTest(template = "empty")
    public static void linkerClickLinksAndUnlinks(GameTestHelper helper) {
        RouterBlockEntity router = routerWithMachines(helper);
        ItemStack linker = machinesLinker();
        ServerPlayer player = linkerPlayer(helper, linker);
        BlockPos furnace = helper.absolutePos(FURNACE_A);
        helper.startSequence()
                .thenWaitUntil(() -> waitLoaded(helper, router))
                .thenExecute(() -> {
                    try {
                        helper.assertValueEqual(toggle(helper, player, FURNACE_A).status(), RangeLinks.Status.NO_ROUTER,
                                "sem roteador escolhido");
                        click(helper, player, ROUTER, false);
                        helper.assertValueEqual(LinkerItem.router(linker),
                                GlobalPos.of(helper.getLevel().dimension(), router.getBlockPos()), "roteador escolhido");
                        assertRefused(helper, toggle(helper, player, FURNACE_A), LinkRefusal.NO_UPGRADE, "sem upgrade");
                        router.setRangeUpgrade(rangeUpgrade());
                        click(helper, player, FURNACE_A, false);
                        helper.assertTrue(router.remoteMachines().contains(furnace), "o clique não vinculou");
                        helper.assertTrue(RemoteIndex.get().isIndexed(router, furnace), "fora do índice");
                        click(helper, player, FURNACE_A, false);
                        helper.assertFalse(router.remoteMachines().contains(furnace), "o segundo clique não desvinculou");
                        helper.assertFalse(RemoteIndex.get().isIndexed(router, furnace), "ficou no índice");
                        click(helper, player, FURNACE_A, true);
                        helper.assertTrue(LinkerItem.area(linker) != null, "Shift + clique não marcou o canto");
                        helper.assertFalse(router.remoteMachines().contains(furnace), "Shift + clique vinculou");
                    } finally {
                        removePlayer(helper, player);
                    }
                })
                .thenSucceed();
    }

    /**
     * Recusas do clique: o próprio roteador, outro roteador, a máquina presa, um bloco sem capability,
     * ar e uma máquina fora do raio. Um bloco com capability sem block entity (composteira) vincula.
     * Com o roteador escolhido quebrado, a resposta é própria e o componente fica.
     */
    @GameTest(template = "empty")
    public static void linkerClickRefusals(GameTestHelper helper) {
        RouterBlockEntity router = routerWithMachines(helper);
        helper.setBlock(SECOND_ROUTER_MACHINE, Blocks.CHEST);
        helper.setBlock(SECOND_ROUTER_MACHINE.above(), ModBlocks.ROUTER.get().defaultBlockState()
                .setValue(RouterBlock.FACING, Direction.UP).setValue(RouterBlock.TIER, RouterTier.ADVANCED));
        helper.setBlock(STONE, Blocks.STONE);
        helper.setBlock(COMPOSTER, Blocks.COMPOSTER);
        int radius = Config.machineRadius(RouterTier.ADVANCED);
        BlockPos outside = new BlockPos(1, 3 + radius, 1);
        helper.setBlock(outside, Blocks.BARREL);
        ItemStack linker = machinesLinker();
        ServerPlayer player = linkerPlayer(helper, linker);
        helper.startSequence()
                .thenWaitUntil(() -> waitLoaded(helper, router))
                .thenExecute(() -> {
                    try {
                        router.setRangeUpgrade(rangeUpgrade());
                        LinkerItem.setRouter(linker, GlobalPos.of(helper.getLevel().dimension(), router.getBlockPos()));
                        assertRefused(helper, toggle(helper, player, ROUTER), LinkRefusal.SELF, "o próprio roteador");
                        assertRefused(helper, toggle(helper, player, SECOND_ROUTER_MACHINE.above()), LinkRefusal.ROUTER,
                                "outro roteador");
                        assertRefused(helper, toggle(helper, player, CHEST), LinkRefusal.ATTACHED, "a máquina presa");
                        assertRefused(helper, toggle(helper, player, STONE), LinkRefusal.NOT_A_MACHINE, "pedra");
                        assertRefused(helper, toggle(helper, player, new BlockPos(0, 2, 0)), LinkRefusal.NOT_A_MACHINE, "ar");
                        assertRefused(helper, toggle(helper, player, outside), LinkRefusal.OUT_OF_RANGE, "fora do raio");
                        helper.assertTrue(router.remoteMachines().isEmpty(), "uma recusa vinculou");
                        helper.assertValueEqual(toggle(helper, player, COMPOSTER).status(), RangeLinks.Status.LINKED,
                                "a composteira (capability sem block entity) no clique");

                        helper.destroyBlock(ROUTER);
                        helper.assertValueEqual(toggle(helper, player, FURNACE_A).status(),
                                RangeLinks.Status.ROUTER_MISSING, "roteador quebrado");
                        helper.assertTrue(LinkerItem.router(linker) != null, "limpou o roteador escolhido");
                    } finally {
                        removePlayer(helper, player);
                    }
                })
                .thenSucceed();
    }

    /** Com o limite do Avançado cheio, o clique seguinte é recusado com {@code LIMIT}. */
    @GameTest(template = "empty")
    public static void linkerClickRefusesOverLimit(GameTestHelper helper) {
        RouterBlockEntity router = rangeRouter(helper);
        int max = Config.maxMachines(RouterTier.ADVANCED);
        BlockPos[] barrels = new BlockPos[max + 1];
        int n = 0;
        for (int y = 3; y <= 5 && n < barrels.length; y++) {
            for (int x = 0; x <= 2 && n < barrels.length; x += 2) {
                for (int z = 0; z <= 2 && n < barrels.length; z += 2) {
                    barrels[n] = new BlockPos(x, y, z);
                    helper.setBlock(barrels[n++], Blocks.BARREL);
                }
            }
        }
        int placed = n;
        ItemStack linker = machinesLinker();
        ServerPlayer player = linkerPlayer(helper, linker);
        helper.startSequence()
                .thenWaitUntil(() -> waitLoaded(helper, router))
                .thenExecute(() -> {
                    try {
                        helper.assertTrue(placed == max + 1, "a config padrão mudou: limite " + max);
                        router.setRangeUpgrade(rangeUpgrade());
                        LinkerItem.setRouter(linker, GlobalPos.of(helper.getLevel().dimension(), router.getBlockPos()));
                        for (int i = 0; i < max; i++) {
                            helper.assertValueEqual(toggle(helper, player, barrels[i]).status(), RangeLinks.Status.LINKED,
                                    "barril " + i);
                        }
                        assertRefused(helper, toggle(helper, player, barrels[max]), LinkRefusal.LIMIT, "além do limite");
                        helper.assertFalse(router.remoteMachines().contains(helper.absolutePos(barrels[max])),
                                "vinculou além do limite");
                    } finally {
                        removePlayer(helper, player);
                    }
                })
                .thenSucceed();
    }

    /**
     * O Vincular da tela no modo Máquinas: só entram block entities com capability de uma aba marcada
     * (a composteira, sem block entity, fica de fora; com a aba Itens, a bateria também), nunca a
     * máquina presa nem o roteador; de novo, as vinculadas contam como já estavam.
     */
    @GameTest(template = "empty")
    public static void linkerAreaLinksBlockEntitiesOfMarkedTabs(GameTestHelper helper) {
        RouterBlockEntity router = rangeRouter(helper);
        BlockPos barrel = new BlockPos(0, 3, 0);
        BlockPos furnace = new BlockPos(2, 3, 2);
        BlockPos composter = new BlockPos(2, 3, 0);
        BlockPos battery = new BlockPos(0, 3, 2);
        helper.setBlock(barrel, Blocks.BARREL);
        helper.setBlock(furnace, Blocks.FURNACE);
        helper.setBlock(composter, Blocks.COMPOSTER);
        helper.setBlock(battery, ModBlocks.STORAGE.get(StorageKind.BATTERY).get().defaultBlockState());
        ItemStack linker = machinesLinker();
        // o modo Máquinas ignora as faces: nenhuma marcada não muda o Vincular
        LinkerItem.setFaces(linker, LinkerFaces.NONE);
        LinkerItem.setTabs(linker, LinkerTabs.of(ResourceType.ITEM));
        ServerPlayer player = linkerPlayer(helper, linker);
        helper.startSequence()
                .thenWaitUntil(() -> waitLoaded(helper, router))
                .thenExecute(() -> {
                    try {
                        router.setRangeUpgrade(rangeUpgrade());
                        LinkerItem.setRouter(linker, GlobalPos.of(helper.getLevel().dimension(), router.getBlockPos()));
                        LinkerItem.setArea(linker, LinkerArea.firstCorner(helper.getLevel().dimension(),
                                helper.absolutePos(new BlockPos(0, 1, 0))).withSecond(helper.absolutePos(new BlockPos(2, 3, 2))));
                        LinkerMenu menu = new LinkerMenu(CONTAINER_ID, player.getInventory(), InteractionHand.MAIN_HAND,
                                LinkerSnapshot.capture(player, linker, null));
                        player.containerMenu = menu;
                        helper.assertValueEqual(menu.snapshot().problem(), LinkerProblem.NONE, "problema na tela");
                        helper.assertTrue(LinkerActions.handle(player, LinkerActionPayload.of(CONTAINER_ID, Op.LINK)),
                                "Vincular pela tela recusou");
                        RemoteMachines remotes = router.remoteMachines();
                        helper.assertTrue(remotes.contains(helper.absolutePos(barrel)), "barril fora");
                        helper.assertTrue(remotes.contains(helper.absolutePos(furnace)), "fornalha fora");
                        helper.assertFalse(remotes.contains(helper.absolutePos(composter)), "composteira sem block entity entrou");
                        helper.assertFalse(remotes.contains(helper.absolutePos(battery)), "bateria entrou com a aba Itens");
                        helper.assertFalse(remotes.contains(helper.absolutePos(CHEST)), "a máquina presa entrou");
                        helper.assertFalse(remotes.contains(router.getBlockPos()), "o roteador entrou");

                        RangeLinks.Result again = RangeLinks.linkArea(player, linker);
                        helper.assertTrue(again.ok() && again.linked() == 0 && again.already() == 2,
                                "de novo: " + again);

                        LinkerItem.setTabs(linker, LinkerTabs.of(ResourceType.ENERGY));
                        RangeLinks.Result energy = RangeLinks.linkArea(player, linker);
                        helper.assertTrue(energy.ok() && energy.linked() == 1, "aba Energia: " + energy);
                        helper.assertTrue(remotes.contains(helper.absolutePos(battery)), "bateria fora com a aba Energia");
                    } finally {
                        removePlayer(helper, player);
                    }
                })
                .thenSucceed();
    }

    /**
     * "Nenhuma (desvincular)" no modo Máquinas: o botão da tela tira do roteador escolhido as máquinas
     * vinculadas dentro da área, e só elas; a de fora da área continua.
     */
    @GameTest(template = "empty")
    public static void linkerAreaUnlinksMachinesInside(GameTestHelper helper) {
        RouterBlockEntity router = rangeRouter(helper);
        BlockPos barrel = new BlockPos(0, 3, 0);
        BlockPos furnace = new BlockPos(2, 3, 2);
        helper.setBlock(barrel, Blocks.BARREL);
        helper.setBlock(furnace, Blocks.FURNACE);
        ItemStack linker = machinesLinker();
        ServerPlayer player = linkerPlayer(helper, linker);
        helper.startSequence()
                .thenWaitUntil(() -> waitLoaded(helper, router))
                .thenExecute(() -> {
                    try {
                        router.setRangeUpgrade(rangeUpgrade());
                        helper.assertTrue(router.linkMachine(helper.absolutePos(barrel)), "vincular o barril");
                        helper.assertTrue(router.linkMachine(helper.absolutePos(furnace)), "vincular a fornalha");
                        LinkerItem.setRouter(linker, GlobalPos.of(helper.getLevel().dimension(), router.getBlockPos()));
                        LinkerItem.setUnlink(linker, true);
                        LinkerItem.setArea(linker, LinkerArea.firstCorner(helper.getLevel().dimension(),
                                helper.absolutePos(new BlockPos(0, 2, 0))).withSecond(helper.absolutePos(new BlockPos(1, 3, 1))));
                        LinkerMenu menu = new LinkerMenu(CONTAINER_ID, player.getInventory(), InteractionHand.MAIN_HAND,
                                LinkerSnapshot.capture(player, linker, null));
                        player.containerMenu = menu;
                        helper.assertTrue(LinkerActions.handle(player, LinkerActionPayload.of(CONTAINER_ID, Op.LINK)),
                                "Desvincular pela tela recusou");
                        RemoteMachines remotes = router.remoteMachines();
                        helper.assertFalse(remotes.contains(helper.absolutePos(barrel)), "o barril, na área, continuou");
                        helper.assertTrue(remotes.contains(helper.absolutePos(furnace)), "a fornalha, fora da área, saiu");

                        RangeLinks.Result again = RangeLinks.linkArea(player, linker);
                        helper.assertTrue(again.ok() && again.linked() == 0, "de novo: " + again);
                    } finally {
                        removePlayer(helper, player);
                    }
                })
                .thenSucceed();
    }

    /**
     * O Configurador nunca fica no modo Máquinas: gravar Máquinas vira pincel, um componente com
     * Máquinas é lido como pincel e o ciclo dele só passa por Pincel e Área.
     */
    @GameTest(template = "empty")
    public static void configuratorNeverMachines(GameTestHelper helper) {
        ItemStack configurator = new ItemStack(ModItems.CONFIGURATOR.get());
        ConfiguratorItem.setMode(configurator, LinkerMode.MACHINES);
        helper.assertValueEqual(ConfiguratorItem.mode(configurator), LinkerMode.SINGLE, "setMode(MACHINES)");
        helper.assertFalse(configurator.has(ModDataComponents.CONFIGURATOR_MODE.get()), "gravou Máquinas");
        configurator.set(ModDataComponents.CONFIGURATOR_MODE.get(), LinkerMode.MACHINES);
        helper.assertValueEqual(ConfiguratorItem.mode(configurator), LinkerMode.SINGLE, "componente com Máquinas");
        helper.assertValueEqual(ConfiguratorItem.pasteMode(configurator), PasteMode.BRUSH, "modo de colar");
        PasteMode mode = ConfiguratorItem.pasteMode(configurator);
        for (int i = 0; i < PasteMode.values().length * 2; i++) {
            mode = mode.next();
            ConfiguratorItem.setPasteMode(configurator, mode);
            helper.assertTrue(ConfiguratorItem.mode(configurator) != LinkerMode.MACHINES, "o ciclo chegou a Máquinas");
        }
        helper.assertValueEqual(LinkerMode.MACHINES.toggled(), LinkerMode.AREA, "toggled de Máquinas");
        helper.assertValueEqual(LinkerMode.AREA.toggled(), LinkerMode.SINGLE, "toggled de Área");
        helper.succeed();
    }

    /** As chaves de tradução de uma mensagem e das partes dela, em ordem. */
    private static java.util.List<String> keys(Component text) {
        java.util.List<String> keys = new java.util.ArrayList<>();
        if (text.getContents() instanceof TranslatableContents contents) {
            keys.add(contents.getKey());
        }
        for (Component sibling : text.getSiblings()) {
            if (sibling.getContents() instanceof TranslatableContents contents) {
                keys.add(contents.getKey());
            }
        }
        return keys;
    }

    /**
     * As recusas do Vincular da área: com o limite 8 do Avançado, as 8 máquinas mais perto vinculam e a
     * mais longe dentro do raio fica além do limite; a de fora do raio é contada como fora do alcance;
     * a que um listener do {@code RightClickBlock} cancela (como um claim) é protegida e não gasta vaga.
     */
    @GameTest(template = "empty")
    public static void linkerAreaRefusals(GameTestHelper helper) {
        RouterBlockEntity router = rangeRouter(helper);
        BlockPos[] near = {
                new BlockPos(0, 3, 0), new BlockPos(2, 3, 0), new BlockPos(0, 3, 2), new BlockPos(2, 3, 2),
                new BlockPos(0, 4, 0), new BlockPos(2, 4, 0), new BlockPos(0, 4, 2), new BlockPos(2, 4, 2)};
        BlockPos claimed = new BlockPos(1, 3, 1);
        int radius = Config.machineRadius(RouterTier.ADVANCED);
        BlockPos farthest = new BlockPos(1, 2 + radius, 1);
        BlockPos outside = new BlockPos(1, 3 + radius, 1);
        for (BlockPos pos : near) {
            helper.setBlock(pos, Blocks.BARREL);
        }
        helper.setBlock(claimed, Blocks.BARREL);
        helper.setBlock(farthest, Blocks.BARREL);
        helper.setBlock(outside, Blocks.BARREL);
        ItemStack linker = machinesLinker();
        ServerPlayer player = linkerPlayer(helper, linker);
        BlockPos claimedAbsolute = helper.absolutePos(claimed);
        int[] posted = new int[1];
        Consumer<PlayerInteractEvent.RightClickBlock> claim = event -> {
            if (event.getEntity() == player) {
                posted[0]++;
                if (event.getPos().equals(claimedAbsolute)) {
                    event.setCanceled(true);
                }
            }
        };
        helper.startSequence()
                .thenWaitUntil(() -> waitLoaded(helper, router))
                .thenExecute(() -> {
                    NeoForge.EVENT_BUS.addListener(claim);
                    try {
                        helper.assertTrue(Config.maxMachines(RouterTier.ADVANCED) == near.length && radius >= 4,
                                "a config padrão mudou: limite " + Config.maxMachines(RouterTier.ADVANCED) + ", raio " + radius);
                        router.setRangeUpgrade(rangeUpgrade());
                        LinkerItem.setRouter(linker, GlobalPos.of(helper.getLevel().dimension(), router.getBlockPos()));
                        LinkerItem.setArea(linker, LinkerArea.firstCorner(helper.getLevel().dimension(),
                                helper.absolutePos(new BlockPos(0, 1, 0))).withSecond(helper.absolutePos(outside.offset(1, 0, 1))));
                        RangeLinks.Result result = RangeLinks.linkArea(player, linker);
                        helper.assertTrue(result.ok() && result.linked() == near.length && result.overLimit() == 1
                                && result.outOfRange() == 1 && result.protectedCount() == 1 && result.already() == 0,
                                "contagens: " + result);
                        RemoteMachines remotes = router.remoteMachines();
                        for (BlockPos pos : near) {
                            helper.assertTrue(remotes.contains(helper.absolutePos(pos)), "a mais perto ficou de fora: " + pos);
                        }
                        helper.assertFalse(remotes.contains(claimedAbsolute), "a protegida pelo evento entrou");
                        helper.assertFalse(remotes.contains(helper.absolutePos(farthest)), "a mais longe passou do limite");
                        helper.assertFalse(remotes.contains(helper.absolutePos(outside)), "a de fora do raio entrou");
                        // o evento só sai para quem seria vinculado: as 8, a protegida e nenhuma recusada antes
                        helper.assertValueEqual(posted[0], near.length + 1, "eventos postados");
                        helper.assertValueEqual(keys(RangeLinks.areaMessage(result)), java.util.List.of(
                                "item.wirelessautomate.linker.range.area.linked",
                                "item.wirelessautomate.linker.range.area.out_of_range",
                                "item.wirelessautomate.linker.range.area.over_limit",
                                "item.wirelessautomate.linker.range.area.protected"), "mensagem");
                    } finally {
                        NeoForge.EVENT_BUS.unregister(claim);
                        removePlayer(helper, player);
                    }
                })
                .thenSucceed();
    }
}
