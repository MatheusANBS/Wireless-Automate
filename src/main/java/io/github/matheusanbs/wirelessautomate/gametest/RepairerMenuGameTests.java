package io.github.matheusanbs.wirelessautomate.gametest;

import io.github.matheusanbs.wirelessautomate.Config;
import io.github.matheusanbs.wirelessautomate.WirelessAutomate;
import io.github.matheusanbs.wirelessautomate.menu.RepairerMenu;
import io.github.matheusanbs.wirelessautomate.packet.RepairerStatePayload;
import io.github.matheusanbs.wirelessautomate.repairer.RepairMath;
import io.github.matheusanbs.wirelessautomate.repairer.RepairerBlockEntity;
import io.github.matheusanbs.wirelessautomate.repairer.RepairerCosts;
import io.github.matheusanbs.wirelessautomate.repairer.RepairerSlots;
import io.github.matheusanbs.wirelessautomate.resonator.CrystalCharge;
import io.netty.buffer.Unpooled;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * A tela do Reparador: o clique que abre, os slots na ordem e o que cada um aceita, a entrada que não sai durante o
 * reparo, o Shift + clique, o estado que vai só quando muda (e a ida e volta pelo pacote) e o progresso no cliente.
 */
@GameTestHolder(WirelessAutomate.MODID)
@PrefixGameTestTemplate(false)
public final class RepairerMenuGameTests {
    private static final int CONTAINER_ID = 1;

    private RepairerMenuGameTests() {
    }

    /** Um jogador em sobrevivência ao lado do Reparador, dentro do alcance do menu. */
    private static ServerPlayer near(GameTestHelper helper, RepairerBlockEntity repairer) {
        ServerPlayer player = ResonatorTestSupport.survivor(helper);
        player.moveTo(Vec3.atBottomCenterOf(repairer.getBlockPos().east()));
        return player;
    }

    private static RepairerMenu openMenu(ServerPlayer player, RepairerBlockEntity repairer) {
        RepairerMenu menu = new RepairerMenu(CONTAINER_ID, player.getInventory(), repairer);
        player.containerMenu = menu;
        return menu;
    }

    private static void close(ServerPlayer player) {
        player.containerMenu = player.inventoryMenu;
    }

    private static ItemStack full(int count) {
        return ResonatorTestSupport.crystal(false, CrystalCharge.FULL).copyWithCount(count);
    }

    /** Põe um rachado na entrada 0, os cristais de um reparo e a energia: a linha 0 começa. */
    private static void startLine(RepairerBlockEntity repairer) {
        ItemStack cracked = RepairerGameTests.cracked();
        repairer.items().set(0, cracked);
        repairer.items().set(RepairerSlots.FEED, full(Config.recastCrystals()));
        repairer.bulkEnergyView().insert(RepairerCosts.of(cracked), false);
    }

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void cliqueAbreOMenu(GameTestHelper helper) {
        RepairerGameTests.repairer(helper, repairer -> {
            TestPlayers.MenuPlayer player = TestPlayers.survivalMenuOpener(helper);
            player.moveTo(Vec3.atBottomCenterOf(repairer.getBlockPos().east()));
            try {
                repairer.getBlockState().useWithoutItem(helper.getLevel(), player,
                        ResonatorTestSupport.hit(repairer.getBlockPos()));
                helper.assertTrue(player.containerMenu instanceof RepairerMenu menu
                        && menu.pos().equals(repairer.getBlockPos()), "o clique não abriu o menu: " + player.containerMenu);
                helper.assertTrue(player.openData != null, "a abertura não escreveu os dados");
                RepairerMenu client = new RepairerMenu(CONTAINER_ID, player.getInventory(), player.openData);
                helper.assertValueEqual(client.pos(), repairer.getBlockPos(), "posição no cliente");
                helper.assertValueEqual(client.slots.size(), RepairerSlots.TOTAL + 36, "slots no cliente");
            } finally {
                close(player);
            }
        });
    }

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void temOsSlotsNaOrdem(GameTestHelper helper) {
        RepairerGameTests.repairer(helper, repairer -> {
            ServerPlayer player = near(helper, repairer);
            try {
                RepairerMenu menu = openMenu(player, repairer);
                helper.assertValueEqual(menu.slots.size(), RepairerSlots.TOTAL + 36, "slots");
                ItemStack cracked = RepairerGameTests.cracked();
                ItemStack fullOne = full(1);
                for (int i = 0; i < RepairerSlots.TOTAL; i++) {
                    helper.assertValueEqual(menu.getSlot(i).getContainerSlot(), i, "ordem do slot " + i);
                }
                for (int i = 0; i < RepairerSlots.INPUTS; i++) {
                    Slot slot = menu.getSlot(i);
                    helper.assertTrue(slot.mayPlace(cracked) && !slot.mayPlace(fullOne)
                            && !slot.mayPlace(new ItemStack(Items.STONE)), "entrada " + i + " aceita errado");
                    helper.assertValueEqual(slot.getMaxStackSize(cracked), 1, "limite da entrada " + i);
                }
                Slot feed = menu.getSlot(RepairerSlots.FEED);
                helper.assertTrue(feed.mayPlace(fullOne) && !feed.mayPlace(cracked)
                        && !feed.mayPlace(ResonatorTestSupport.crystal(false, 1)), "o estoque aceita errado");
                helper.assertValueEqual(feed.getMaxStackSize(fullOne), 64, "limite do estoque");
                for (int i = RepairerSlots.OUTPUT_FIRST; i < RepairerSlots.TOTAL; i++) {
                    Slot slot = menu.getSlot(i);
                    helper.assertTrue(!slot.mayPlace(cracked) && !slot.mayPlace(fullOne), "a saída " + i + " aceita");
                }
                repairer.items().set(RepairerSlots.OUTPUT_FIRST, full(1));
                menu.clicked(RepairerSlots.OUTPUT_FIRST, 0, ClickType.PICKUP, player);
                helper.assertTrue(menu.getCarried().getCount() == 1 && repairer.items().get(RepairerSlots.OUTPUT_FIRST)
                        .isEmpty(), "o jogador não tirou da saída");
                menu.clicked(RepairerSlots.OUTPUT_FIRST, 0, ClickType.PICKUP, player);
                helper.assertTrue(menu.getCarried().getCount() == 1 && repairer.items().get(RepairerSlots.OUTPUT_FIRST)
                        .isEmpty(), "o jogador pôs na saída");
                menu.setCarried(cracked.copy());
                menu.clicked(0, 0, ClickType.PICKUP, player);
                menu.setCarried(RepairerGameTests.cracked());
                menu.clicked(1, 0, ClickType.PICKUP, player);
                helper.assertTrue(repairer.items().get(0).getCount() == 1 && repairer.items().get(1).getCount() == 1,
                        "o clique não pôs um rachado por entrada");
            } finally {
                close(player);
            }
        });
    }

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void rachadoEmReparoNaoSai(GameTestHelper helper) {
        RepairerGameTests.repairer(helper, repairer -> {
            ServerPlayer player = near(helper, repairer);
            try {
                RepairerMenu menu = openMenu(player, repairer);
                repairer.items().set(0, RepairerGameTests.cracked());
                helper.assertTrue(menu.getSlot(0).mayPickup(player), "a linha parada não deixa tirar");
                startLine(repairer);
                helper.assertTrue(repairer.running(0), "a linha 0 não começou");
                Slot input = menu.getSlot(0);
                helper.assertTrue(!input.mayPickup(player), "a linha andando deixa tirar");
                helper.assertTrue(!input.mayPlace(RepairerGameTests.cracked()), "a linha andando deixa trocar");
                menu.clicked(0, 0, ClickType.PICKUP, player);
                helper.assertTrue(menu.getCarried().isEmpty() && !repairer.items().get(0).isEmpty(),
                        "o clique tirou o rachado em reparo");
                menu.setCarried(RepairerGameTests.cracked());
                menu.clicked(0, 0, ClickType.PICKUP, player);
                helper.assertTrue(menu.getCarried().getCount() == 1 && repairer.items().get(0).getCount() == 1,
                        "o clique trocou o rachado em reparo");
                menu.setCarried(ItemStack.EMPTY);
                helper.assertTrue(menu.quickMoveStack(player, 0).isEmpty() && !repairer.items().get(0).isEmpty(),
                        "o Shift + clique tirou o rachado em reparo");
                for (int i = RepairerSlots.OUTPUT_FIRST; i < RepairerSlots.TOTAL; i++) {
                    repairer.items().set(i, full(1));
                }
                repairer.tick(helper.getLevel().getGameTime() + Config.repairTicks());
                helper.assertTrue(repairer.lineBlocked(0) && !menu.getSlot(0).mayPickup(player),
                        "a linha travada deixa tirar");
            } finally {
                close(player);
            }
        });
    }

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void estadoSoQuandoMuda(GameTestHelper helper) {
        RepairerGameTests.repairer(helper, repairer -> {
            ServerPlayer player = near(helper, repairer);
            try {
                RepairerMenu menu = openMenu(player, repairer);
                RepairerStatePayload first = menu.pollState(0);
                helper.assertTrue(first != null, "o primeiro estado não saiu");
                helper.assertTrue(menu.pollState(1) == null, "saiu de novo dentro de 5 ticks");
                helper.assertTrue(menu.pollState(10) == null, "saiu sem mudar");
                repairer.bulkEnergyView().insert(100, false);
                RepairerStatePayload energy = menu.pollState(12);
                helper.assertTrue(energy != null && energy.energy() == 100, "a energia nova não saiu: " + energy);
                repairer.bulkEnergyView().insert(1, false);
                helper.assertTrue(menu.pollState(14) == null, "saiu antes do intervalo");
                startLine(repairer);
                RepairerStatePayload started = menu.pollState(20);
                helper.assertTrue(started != null && started.ends()[0] > 0 && !started.blocked()[0],
                        "a linha que começou não saiu");
                helper.assertValueEqual(started.duration(), (int) Config.repairTicks(), "duração");
                helper.assertValueEqual(started.needed(), Config.recastCrystals(), "cristais por reparo");
                helper.assertTrue(menu.pollState(40) == null, "saiu de novo sem mudar");
                RegistryFriendlyByteBuf buf = new RegistryFriendlyByteBuf(Unpooled.buffer(),
                        helper.getLevel().registryAccess());
                RepairerStatePayload.STREAM_CODEC.encode(buf, started);
                helper.assertTrue(started.equals(RepairerStatePayload.STREAM_CODEC.decode(buf)),
                        "a ida e volta pelo pacote mudou o estado");
            } finally {
                close(player);
            }
        });
    }

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void custoDoPrimeiroRachadoQueEspera(GameTestHelper helper) {
        RepairerGameTests.repairer(helper, repairer -> {
            ServerPlayer player = near(helper, repairer);
            try {
                RepairerMenu menu = openMenu(player, repairer);
                ItemStack cut = RepairerGameTests.cracked(true);
                repairer.items().set(2, cut);
                RepairerStatePayload state = menu.pollState(0);
                helper.assertTrue(state != null && state.cost0() == RepairerCosts.of(cut), "custo da espera: " + state);
            } finally {
                close(player);
            }
        });
    }

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void shiftClickMoveParaOsSlotsCertos(GameTestHelper helper) {
        RepairerGameTests.repairer(helper, repairer -> {
            ServerPlayer player = near(helper, repairer);
            try {
                RepairerMenu menu = openMenu(player, repairer);
                Inventory inventory = player.getInventory();
                inventory.clearContent();
                int bag = RepairerMenu.INVENTORY_START;
                inventory.setItem(9, RepairerGameTests.cracked().copyWithCount(3));
                menu.quickMoveStack(player, bag);
                for (int i = 0; i < 3; i++) {
                    helper.assertValueEqual(repairer.items().get(i).getCount(), 1, "rachado na entrada " + i);
                }
                helper.assertTrue(inventory.getItem(9).isEmpty(), "sobrou rachado no inventário");
                inventory.setItem(10, full(10));
                menu.quickMoveStack(player, bag + 1);
                helper.assertValueEqual(repairer.items().get(RepairerSlots.FEED).getCount(), 10, "estoque");
                inventory.setItem(11, new ItemStack(Items.STONE, 5));
                menu.quickMoveStack(player, bag + 2);
                for (int i = 0; i < RepairerSlots.TOTAL; i++) {
                    helper.assertTrue(!repairer.items().get(i).is(Items.STONE), "a pedra entrou no slot " + i);
                }
                helper.assertValueEqual(inventory.countItem(Items.STONE), 5, "pedra no inventário");
                repairer.items().set(RepairerSlots.OUTPUT_FIRST + 3, full(2));
                menu.quickMoveStack(player, RepairerSlots.OUTPUT_FIRST + 3);
                helper.assertTrue(repairer.items().get(RepairerSlots.OUTPUT_FIRST + 3).isEmpty(), "a saída não esvaziou");
                helper.assertTrue(inventory.countItem(full(1).getItem()) >= 2, "a saída não chegou ao inventário");
                for (int i = RepairerSlots.OUTPUT_FIRST; i < RepairerSlots.TOTAL; i++) {
                    helper.assertTrue(repairer.items().get(i).isEmpty(), "algo entrou na saída " + i);
                }
            } finally {
                close(player);
            }
        });
    }

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void stillValidSoPertoEComBlocoNoLugar(GameTestHelper helper) {
        RepairerGameTests.repairer(helper, repairer -> {
            ServerPlayer player = near(helper, repairer);
            try {
                RepairerMenu menu = openMenu(player, repairer);
                helper.assertTrue(menu.stillValid(player), "perto não vale");
                player.moveTo(Vec3.atBottomCenterOf(repairer.getBlockPos().east(20)));
                helper.assertTrue(!menu.stillValid(player), "longe ainda vale");
                player.moveTo(Vec3.atBottomCenterOf(repairer.getBlockPos().east()));
                helper.setBlock(RepairerGameTests.A, Blocks.AIR);
                helper.assertTrue(!menu.stillValid(player), "sem o bloco ainda vale");
            } finally {
                close(player);
            }
        });
    }

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void clienteProgressoPeloRelogio(GameTestHelper helper) {
        ServerPlayer player = ResonatorTestSupport.survivor(helper);
        RepairerMenu client = new RepairerMenu(CONTAINER_ID, player.getInventory(), BlockPos.ZERO);
        helper.assertValueEqual(client.progress(0, 100), 0, "progresso sem estado");
        helper.assertTrue(client.getSlot(0).mayPickup(player), "sem estado a entrada trava");
        long[] ends = new long[RepairMath.LINES];
        boolean[] blocked = new boolean[RepairMath.LINES];
        ends[0] = 1_000;
        ends[1] = 900;
        blocked[1] = true;
        client.apply(new RepairerStatePayload(CONTAINER_ID, 0, 0, ends, blocked, 140, 4, 0));
        for (long now : new long[] {800, 860, 930, 999, 1_000, 1_200}) {
            helper.assertValueEqual(client.progress(0, now), RepairMath.progressPermille(now, 1_000, 140),
                    "progresso em " + now);
        }
        helper.assertValueEqual(client.progress(0, 930), 500, "metade");
        helper.assertValueEqual(client.progress(1, 0), 1000, "travada");
        helper.assertValueEqual(client.progress(2, 930), 0, "parada");
        helper.assertTrue(!client.getSlot(0).mayPickup(player) && !client.getSlot(1).mayPickup(player)
                && client.getSlot(2).mayPickup(player), "a trava no cliente não segue o estado");
        helper.succeed();
    }
}
