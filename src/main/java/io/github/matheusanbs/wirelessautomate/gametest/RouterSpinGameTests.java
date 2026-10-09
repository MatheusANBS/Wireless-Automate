package io.github.matheusanbs.wirelessautomate.gametest;

import io.github.matheusanbs.wirelessautomate.WirelessAutomate;
import io.github.matheusanbs.wirelessautomate.block.RouterBlock;
import io.github.matheusanbs.wirelessautomate.block.RouterBlockEntity;
import io.github.matheusanbs.wirelessautomate.filter.Filter;
import io.github.matheusanbs.wirelessautomate.filter.FilterEntry;
import io.github.matheusanbs.wirelessautomate.item.FilterCardItem;
import io.github.matheusanbs.wirelessautomate.network.FaceConfig;
import io.github.matheusanbs.wirelessautomate.network.NetworkManager;
import io.github.matheusanbs.wirelessautomate.network.NetworkSavedData;
import io.github.matheusanbs.wirelessautomate.network.PortMode;
import io.github.matheusanbs.wirelessautomate.network.RelativeSide;
import io.github.matheusanbs.wirelessautomate.network.ResourceType;
import io.github.matheusanbs.wirelessautomate.registry.ModBlocks;
import io.github.matheusanbs.wirelessautomate.registry.ModItems;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * Giro do roteador por Shift + clique com as mãos vazias: o {@code spin} avança e a configuração de
 * cada face absoluta da máquina (modo, prioridade e cartões) continua a mesma.
 */
@GameTestHolder(WirelessAutomate.MODID)
@PrefixGameTestTemplate(false)
public final class RouterSpinGameTests {
    private static final BlockPos MACHINE = new BlockPos(1, 1, 1);
    private static final BlockPos ROUTER = MACHINE.above();

    /** Baú com o roteador em cima, numa rede criada pelo teste; face norte em EXTRACT, prioridade 3 e um cartão. */
    private static RouterBlockEntity configured(GameTestHelper helper, String name) {
        UUID network = NetworkSavedData.get(helper.getLevel().getServer()).create(UUID.randomUUID(), name).id();
        helper.setBlock(MACHINE, Blocks.CHEST);
        helper.setBlock(ROUTER, ModBlocks.ROUTER.get().defaultBlockState().setValue(RouterBlock.FACING, Direction.UP));
        RouterBlockEntity router = helper.getBlockEntity(ROUTER);
        router.setNetworkId(network);
        router.setMode(ResourceType.ITEM, Direction.NORTH, PortMode.EXTRACT);
        router.setPriority(ResourceType.ITEM, Direction.NORTH, 3);
        router.setMode(ResourceType.FLUID, Direction.EAST, PortMode.INSERT);
        router.setCard(ResourceType.ITEM, side(router, Direction.NORTH), 0, card());
        return router;
    }

    private static RelativeSide side(RouterBlockEntity router, Direction face) {
        return RelativeSide.fromAbsolute(router.facing(), router.spin(), face);
    }

    private static ItemStack card() {
        ItemStack card = new ItemStack(ModItems.FILTER_CARD.get());
        FilterCardItem.setContents(card, new FilterCardItem.Contents(ResourceType.ITEM, new Filter(
                Filter.ListMode.WHITELIST, false, List.of(new FilterEntry.ItemEntry(new ItemStack(Items.DIAMOND), 0)))));
        return card;
    }

    private static Player sneaking(GameTestHelper helper, GameType mode) {
        Player player = helper.makeMockPlayer(mode);
        player.setShiftKeyDown(true);
        return player;
    }

    private static int spin(GameTestHelper helper) {
        return helper.getBlockState(ROUTER).getValue(RouterBlock.SPIN);
    }

    /** A configuração montada em {@link #configured}, conferida pelas faces absolutas. */
    private static void assertConfig(GameTestHelper helper, RouterBlockEntity router, String when) {
        FaceConfig north = router.face(ResourceType.ITEM, Direction.NORTH);
        helper.assertValueEqual(north.mode(), PortMode.EXTRACT, "modo da face norte " + when);
        helper.assertValueEqual(north.priority(), 3, "prioridade da face norte " + when);
        helper.assertValueEqual(router.face(ResourceType.FLUID, Direction.EAST).mode(), PortMode.INSERT,
                "fluido na face leste " + when);
        for (Direction face : Direction.values()) {
            if (face != Direction.NORTH) {
                helper.assertTrue(router.face(ResourceType.ITEM, face).isDefault(), "item na face " + face + " " + when);
            }
            if (face != Direction.EAST) {
                helper.assertTrue(router.face(ResourceType.FLUID, face).isDefault(), "fluido na face " + face + " " + when);
            }
            int expectedCards = face == Direction.NORTH ? 1 : 0;
            helper.assertValueEqual(router.cardCount(ResourceType.ITEM, face), expectedCards, "cartões na face " + face + " " + when);
        }
        helper.assertTrue(ItemStack.matches(router.card(ResourceType.ITEM, side(router, Direction.NORTH), 0), card()),
                "cartão saiu do slot da face norte " + when);
    }

    @GameTest(template = "empty")
    public static void sneakClickSpinsAndKeepsFaces(GameTestHelper helper) {
        RouterBlockEntity router = configured(helper, "giro");
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(NetworkManager.get().contains(router), "sem onLoad"))
                .thenExecute(() -> {
                    Player player = sneaking(helper, GameType.SURVIVAL);
                    RelativeSide before = side(router, Direction.NORTH);
                    int version = router.changeVersion();
                    helper.useBlock(ROUTER, player);
                    helper.assertValueEqual(spin(helper), 1, "spin depois do primeiro clique");
                    helper.assertTrue(helper.getBlockEntity(ROUTER) == router, "block entity trocado");
                    helper.assertTrue(router.changeVersion() != version, "tela não soube do giro");
                    helper.assertTrue(side(router, Direction.NORTH) != before, "lado relativo da face norte não mudou");
                    assertConfig(helper, router, "com spin 1");
                    for (int i = 2; i <= 4; i++) {
                        helper.useBlock(ROUTER, player);
                        helper.assertValueEqual(spin(helper), i % RelativeSide.SPINS, "spin no clique " + i);
                        assertConfig(helper, router, "com spin " + (i % RelativeSide.SPINS));
                    }
                    helper.assertValueEqual(side(router, Direction.NORTH), before, "quatro giros não voltaram");
                })
                .thenSucceed();
    }

    @GameTest(template = "empty")
    public static void clickWithoutSneakDoesNotSpin(GameTestHelper helper) {
        RouterBlockEntity router = configured(helper, "sem-giro");
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(NetworkManager.get().contains(router), "sem onLoad"))
                .thenExecute(() -> {
                    helper.useBlock(ROUTER, helper.makeMockPlayer(GameType.SURVIVAL));
                    helper.assertValueEqual(spin(helper), 0, "girou sem Shift");
                    // Sem poder construir (modo aventura; o jogador de teste não ajusta as habilidades
                    // pelo modo, então é direto): não pode mexer no bloco, então nem gira.
                    Player adventure = sneaking(helper, GameType.ADVENTURE);
                    adventure.getAbilities().mayBuild = false;
                    helper.useBlock(ROUTER, adventure);
                    helper.assertValueEqual(spin(helper), 0, "girou sem poder construir");
                    assertConfig(helper, router, "sem giro");
                })
                .thenSucceed();
    }
}
