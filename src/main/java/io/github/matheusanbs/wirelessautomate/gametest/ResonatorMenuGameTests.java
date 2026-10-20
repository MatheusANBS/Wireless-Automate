package io.github.matheusanbs.wirelessautomate.gametest;

import static io.github.matheusanbs.wirelessautomate.gametest.ResonatorTestSupport.crystal;
import static io.github.matheusanbs.wirelessautomate.gametest.ResonatorTestSupport.formed;
import static io.github.matheusanbs.wirelessautomate.gametest.ResonatorTestSupport.hit;
import static io.github.matheusanbs.wirelessautomate.gametest.ResonatorTestSupport.survivor;

import io.github.matheusanbs.wirelessautomate.Config;
import io.github.matheusanbs.wirelessautomate.WirelessAutomate;
import io.github.matheusanbs.wirelessautomate.block.RouterTier;
import io.github.matheusanbs.wirelessautomate.gametest.ResonatorTestSupport.Layout;
import io.github.matheusanbs.wirelessautomate.menu.ResonatorMenu;
import io.github.matheusanbs.wirelessautomate.packet.ResonatorDialPayload;
import io.github.matheusanbs.wirelessautomate.packet.ResonatorModePayload;
import io.github.matheusanbs.wirelessautomate.packet.ResonatorPayloads;
import io.github.matheusanbs.wirelessautomate.packet.ResonatorStatePayload;
import io.github.matheusanbs.wirelessautomate.registry.ModBlocks;
import io.github.matheusanbs.wirelessautomate.registry.ModItems;
import io.github.matheusanbs.wirelessautomate.resonator.CrystalCharge;
import io.github.matheusanbs.wirelessautomate.resonator.ResonanceCoreBlock;
import io.github.matheusanbs.wirelessautomate.resonator.ResonanceCoreBlockEntity;
import io.github.matheusanbs.wirelessautomate.resonator.TuneSignal;
import io.netty.buffer.Unpooled;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * Etapa 3 do Ressonador de Sinal: o menu do Núcleo (o clique que abre, os slots das câmaras e da redstone, o Shift
 * + clique), o estado que vai só quando muda e os payloads do dial e do modo, validados no servidor.
 */
@GameTestHolder(WirelessAutomate.MODID)
@PrefixGameTestTemplate(false)
public final class ResonatorMenuGameTests {
    private static final int CONTAINER_ID = 42;

    private ResonatorMenuGameTests() {
    }

    /** Um jogador em sobrevivência ao lado do Núcleo, dentro do alcance do menu. */
    private static ServerPlayer near(GameTestHelper helper, ResonanceCoreBlockEntity core) {
        ServerPlayer player = survivor(helper);
        player.moveTo(Vec3.atBottomCenterOf(core.getBlockPos().east()));
        return player;
    }

    private static ResonatorMenu openMenu(ServerPlayer player, ResonanceCoreBlockEntity core) {
        ResonatorMenu menu = new ResonatorMenu(CONTAINER_ID, player.getInventory(), core);
        player.containerMenu = menu;
        return menu;
    }

    private static void close(ServerPlayer player) {
        player.containerMenu = player.inventoryMenu;
    }

    /** Um jogador em sobrevivência que abre telas sem o pacote da NeoForge, ao lado do Núcleo. */
    private static TestPlayers.MenuPlayer openerNear(GameTestHelper helper, ResonanceCoreBlockEntity core) {
        TestPlayers.MenuPlayer player = TestPlayers.survivalMenuOpener(helper);
        player.moveTo(Vec3.atBottomCenterOf(core.getBlockPos().east()));
        return player;
    }

    @GameTest(template = "empty7")
    public static void cliqueNoMontadoAbreOMenu(GameTestHelper helper) {
        formed(helper, RouterTier.ELITE, core -> {
            TestPlayers.MenuPlayer player = openerNear(helper, core);
            try {
                core.getBlockState().useWithoutItem(helper.getLevel(), player, hit(core.getBlockPos()));
                helper.assertTrue(player.containerMenu instanceof ResonatorMenu menu
                        && menu.pos().equals(core.getBlockPos()), "o clique não abriu o menu: " + player.containerMenu);
                helper.assertTrue(player.openData != null, "a abertura não escreveu os dados");
                ResonatorMenu client = new ResonatorMenu(CONTAINER_ID, player.getInventory(), player.openData);
                helper.assertValueEqual(client.pos(), core.getBlockPos(), "posição no cliente");
                helper.assertValueEqual(client.tier(), RouterTier.ELITE, "tier no cliente");
                helper.assertValueEqual(client.chambers(), 3, "câmaras no cliente");
                helper.assertValueEqual(client.slots.size(), 3 + 1 + 36, "slots no cliente");
                helper.assertTrue(client.stillValid(player), "o menu do cliente não vale");
            } finally {
                close(player);
            }
        });
    }

    @GameTest(template = "empty7")
    public static void tierQueMudaReabreATela(GameTestHelper helper) {
        formed(helper, RouterTier.BASIC, core -> {
            TestPlayers.MenuPlayer player = openerNear(helper, core);
            try {
                core.insertCrystal(crystal(false, CrystalCharge.FULL));
                core.getBlockState().useWithoutItem(helper.getLevel(), player, hit(core.getBlockPos()));
                helper.assertTrue(player.containerMenu instanceof ResonatorMenu, "o clique não abriu o menu");
                ResonatorMenu before = (ResonatorMenu) player.containerMenu;
                before.setCarried(new ItemStack(Items.REDSTONE, 5));
                helper.assertTrue(ResonanceCoreBlock.tryUpgrade(helper.getLevel(), core.getBlockPos(),
                        RouterTier.ADVANCED, RouterTier.BASIC), "o upgrade foi recusado");
                before.broadcastChanges();
                helper.assertTrue(player.containerMenu instanceof ResonatorMenu after && after != before
                        && after.chambers() == 2 && after.slots.size() == 2 + 1 + 36 && after.stillValid(player),
                        "a tela não reabriu com as câmaras novas: " + player.containerMenu);
                ResonatorMenu after = (ResonatorMenu) player.containerMenu;
                helper.assertTrue(after.getSlot(0).hasItem() && !after.getSlot(1).hasItem(), "câmaras na tela nova");
                helper.assertTrue(before.getCarried().isEmpty() && after.getCarried().isEmpty(), "o cursor ficou cheio");
                helper.assertValueEqual(player.getInventory().countItem(Items.REDSTONE), 5, "redstone do cursor");
                helper.assertValueEqual(core.crystals().stream().filter(stack -> !stack.isEmpty()).count(), 1L,
                        "cristais no Núcleo");
                after.broadcastChanges();
                helper.assertTrue(player.containerMenu == after, "reabriu de novo sem o tier mudar");
            } finally {
                close(player);
            }
        });
    }

    @GameTest(template = "empty7")
    public static void tierQueMudaLongeFechaSemReabrir(GameTestHelper helper) {
        formed(helper, RouterTier.BASIC, core -> {
            TestPlayers.MenuPlayer player = openerNear(helper, core);
            try {
                core.getBlockState().useWithoutItem(helper.getLevel(), player, hit(core.getBlockPos()));
                helper.assertTrue(player.containerMenu instanceof ResonatorMenu, "o clique não abriu o menu");
                ResonatorMenu before = (ResonatorMenu) player.containerMenu;
                player.moveTo(Vec3.atBottomCenterOf(core.getBlockPos().east(20)));
                helper.assertTrue(ResonanceCoreBlock.tryUpgrade(helper.getLevel(), core.getBlockPos(),
                        RouterTier.ADVANCED, RouterTier.BASIC), "o upgrade foi recusado");
                before.broadcastChanges();
                helper.assertTrue(player.containerMenu == before && !before.stillValid(player),
                        "longe do Núcleo a tela reabriu ou continuou valendo");
            } finally {
                close(player);
            }
        });
    }

    @GameTest(template = "empty7")
    public static void cliqueNoSoltoNaoAbre(GameTestHelper helper) {
        BlockPos corePos = new BlockPos(3, 2, 3);
        helper.setBlock(corePos, ModBlocks.RESONANCE_CORE.get());
        helper.startSequence().thenWaitUntil(() -> {
            ResonanceCoreBlockEntity core = ResonatorTestSupport.core(helper, corePos);
            helper.assertTrue(core != null && core.lastProblem() != null, "ainda não revalidou");
        }).thenExecute(() -> {
            ResonanceCoreBlockEntity core = ResonatorTestSupport.core(helper, corePos);
            ServerPlayer player = near(helper, core);
            core.getBlockState().useWithoutItem(helper.getLevel(), player, hit(core.getBlockPos()));
            helper.assertTrue(player.containerMenu == player.inventoryMenu, "o Núcleo solto abriu um menu");
        }).thenSucceed();
    }

    @GameTest(template = "empty7")
    public static void slotsSaoAsCamarasDoTierMaisRedstone(GameTestHelper helper) {
        formed(helper, RouterTier.ELITE, core -> {
            ServerPlayer player = near(helper, core);
            ResonatorMenu menu = openMenu(player, core);
            try {
                helper.assertValueEqual(menu.slots.size(), 3 + 1 + 36, "slots do menu");
                helper.assertValueEqual(menu.redstoneSlot(), 3, "slot da redstone");
                helper.assertValueEqual(menu.inventoryStart(), 4, "início do inventário");
                ItemStack crystal = crystal(true, CrystalCharge.FULL);
                ItemStack redstone = new ItemStack(Items.REDSTONE);
                ItemStack dust = new ItemStack(ModItems.RESONANCE_DUST.get());
                for (int i = 0; i < 3; i++) {
                    Slot slot = menu.slots.get(i);
                    helper.assertTrue(slot.mayPlace(crystal), "a câmara " + i + " recusou o cristal");
                    helper.assertTrue(!slot.mayPlace(redstone), "a câmara " + i + " aceitou redstone");
                    helper.assertValueEqual(slot.getMaxStackSize(), 1, "pilha da câmara " + i);
                }
                Slot slot = menu.slots.get(3);
                // No Elite o catalisador é só o Pó de Ressonância.
                helper.assertTrue(slot.mayPlace(dust), "o slot do catalisador recusou o Pó");
                helper.assertTrue(!slot.mayPlace(redstone), "o slot do catalisador do Elite aceitou redstone");
                helper.assertTrue(!slot.mayPlace(crystal), "o slot da redstone aceitou cristal");
            } finally {
                close(player);
            }
        });
    }

    @GameTest(template = "empty7")
    public static void shiftCliqueDoInventarioPoeNoNucleo(GameTestHelper helper) {
        formed(helper, RouterTier.ELITE, core -> {
            ServerPlayer player = near(helper, core);
            ResonatorMenu menu = openMenu(player, core);
            try {
                player.getInventory().setItem(9, crystal(true, CrystalCharge.FULL));
                menu.quickMoveStack(player, menu.inventoryStart());
                helper.assertTrue(core.crystals().get(0).is(ModItems.CUT_SIGNAL_CRYSTAL.get()),
                        "o Lapidado não foi para a câmara 0");
                helper.assertTrue(player.getInventory().getItem(9).isEmpty(), "o Lapidado ficou no inventário");

                // No Elite o catalisador é o Pó de Ressonância.
                player.getInventory().setItem(0, new ItemStack(ModItems.RESONANCE_DUST.get(), 10));
                menu.quickMoveStack(player, menu.inventoryStart() + 27);
                helper.assertValueEqual(core.redstone(), 10, "Pó no Núcleo");
                helper.assertTrue(player.getInventory().getItem(0).isEmpty(), "o Pó ficou na barra");

                player.getInventory().setItem(0, new ItemStack(ModItems.RESONANCE_DUST.get(), 5));
                menu.quickMoveStack(player, menu.inventoryStart() + 27);
                helper.assertValueEqual(core.redstone(), 15, "Pó somado no Núcleo");

                menu.quickMoveStack(player, 0);
                helper.assertTrue(core.crystals().get(0).isEmpty(), "a câmara 0 não esvaziou");
                boolean back = false;
                for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
                    back |= player.getInventory().getItem(i).is(ModItems.CUT_SIGNAL_CRYSTAL.get());
                }
                helper.assertTrue(back, "o Lapidado não voltou ao inventário");
            } finally {
                close(player);
            }
        });
    }

    @GameTest(template = "empty7")
    public static void estadoVaiSoQuandoMuda(GameTestHelper helper) {
        formed(helper, RouterTier.BASIC, core -> {
            ServerPlayer player = near(helper, core);
            ResonatorMenu menu = openMenu(player, core);
            try {
                core.setStationForTest(98.0);
                core.setDial(98.0);
                helper.assertTrue(menu.pollState(100) != null, "o primeiro estado não foi");
                helper.assertTrue(menu.pollState(102) == null, "foi sem mudar");
                core.setDial(97.0);
                helper.assertTrue(menu.pollState(103) == null, "foi antes dos 5 ticks");
                ResonatorStatePayload state = menu.pollState(105);
                helper.assertTrue(state != null, "a mudança não foi depois dos 5 ticks");
                helper.assertValueEqual(state.dial(), 97.0, "dial no estado");
                helper.assertTrue(state.tuner(), "o estado não traz o Sintonizador");
                helper.assertValueEqual(state.energyPorts(), 1, "Portas de Energia");
                helper.assertValueEqual(state.crystalPorts(), 1, "Portas de Cristais");
                helper.assertValueEqual(state.layout(), 1, "montagem");
                helper.assertValueEqual(state.deviationSteps(), 20, "desvio em passos");
                helper.assertTrue(Math.abs(state.efficiencyPermille() - 720) <= 5,
                        "eficiência: " + state.efficiencyPermille());
                helper.assertValueEqual(state.ceilingPermille(), 850, "teto do Básico");
                helper.assertValueEqual(state.containerId(), CONTAINER_ID, "containerId");
            } finally {
                close(player);
            }
        });
    }

    @GameTest(template = "empty7")
    public static void desvioForaDeDoisMhzEhSemSinal(GameTestHelper helper) {
        formed(helper, RouterTier.BASIC, core -> {
            ServerPlayer player = near(helper, core);
            ResonatorMenu menu = openMenu(player, core);
            try {
                core.setStationForTest(98.0);
                core.setDial(94.0);
                ResonatorStatePayload state = menu.pollState(100);
                helper.assertTrue(state != null, "o primeiro estado não foi");
                helper.assertValueEqual(state.deviationSteps(), TuneSignal.NO_SIGNAL, "desvio fora de ±2 MHz");
            } finally {
                close(player);
            }
        });
    }

    @GameTest(template = "empty7")
    public static void lampadaApagaNaBordaForaDoPlato(GameTestHelper helper) {
        formed(helper, RouterTier.BASIC, core -> {
            ServerPlayer player = near(helper, core);
            ResonatorMenu menu = openMenu(player, core);
            try {
                core.setStationForTest(98.02);
                core.setDial(97.75);
                ResonatorStatePayload state = menu.pollState(100);
                helper.assertTrue(state != null, "o primeiro estado não foi");
                helper.assertValueEqual(state.deviationSteps(), TuneSignal.PLATEAU_STEPS, "desvio de 0,27 em passos");
                helper.assertTrue(!state.tuned(), "a lâmpada acendeu com 0,27 MHz de desvio");
            } finally {
                close(player);
            }
        });
    }

    @GameTest(template = "empty7")
    public static void lampadaAcendeDentroDoPlato(GameTestHelper helper) {
        formed(helper, RouterTier.BASIC, core -> {
            ServerPlayer player = near(helper, core);
            ResonatorMenu menu = openMenu(player, core);
            try {
                core.setStationForTest(98.02);
                core.setDial(97.80);
                ResonatorStatePayload state = menu.pollState(100);
                helper.assertTrue(state != null, "o primeiro estado não foi");
                helper.assertValueEqual(state.deviationSteps(), 4, "desvio de 0,22 em passos");
                helper.assertTrue(state.tuned(), "a lâmpada não acendeu com 0,22 MHz de desvio");
            } finally {
                close(player);
            }
        });
    }

    @GameTest(template = "empty7")
    public static void estadoTrazAGeracaoMaximaComOpacos(GameTestHelper helper) {
        formed(helper, RouterTier.ADVANCED, core -> {
            ServerPlayer player = near(helper, core);
            ResonatorMenu menu = openMenu(player, core);
            try {
                core.setCrystalAt(0, crystal(true, CrystalCharge.FULL));
                core.setCrystalAt(1, crystal(false, 0));
                ResonatorStatePayload state = menu.pollState(100);
                helper.assertTrue(state != null, "o primeiro estado não foi");
                long fe = Config.resonatorFePerCrystal(RouterTier.ADVANCED);
                helper.assertValueEqual(state.maxGeneration(), fe * 3, "geração máxima com o Opaco");
                helper.assertValueEqual(state.capacity(), core.capacity(), "capacidade");
            } finally {
                close(player);
            }
        });
    }

    @GameTest(template = "empty7")
    public static void payloadDoDialValida(GameTestHelper helper) {
        formed(helper, RouterTier.BASIC, core -> {
            ServerPlayer player = near(helper, core);
            openMenu(player, core);
            try {
                helper.assertTrue(ResonatorPayloads.handleDial(player, new ResonatorDialPayload(CONTAINER_ID, 97.42)),
                        "o dial válido foi recusado");
                helper.assertValueEqual(core.dial(), 97.40, "dial no passo");
                helper.assertTrue(!ResonatorPayloads.handleDial(player,
                        new ResonatorDialPayload(CONTAINER_ID + 1, 96.0)), "aceitou outro containerId");
                helper.assertTrue(!ResonatorPayloads.handleDial(player,
                        new ResonatorDialPayload(CONTAINER_ID, Double.NaN)), "aceitou NaN");
                helper.assertTrue(core.setAuto(true), "o automático foi recusado com Sintonizador");
                helper.assertTrue(!ResonatorPayloads.handleDial(player, new ResonatorDialPayload(CONTAINER_ID, 96.0)),
                        "aceitou o dial no automático");
                helper.assertValueEqual(core.dial(), 97.40, "o dial mudou no automático");
                core.setAuto(false);
                player.teleportTo(player.getX() + 20, player.getY(), player.getZ());
                helper.assertTrue(!ResonatorPayloads.handleDial(player, new ResonatorDialPayload(CONTAINER_ID, 96.0)),
                        "aceitou o dial a 20 blocos");
                helper.assertValueEqual(core.dial(), 97.40, "o dial mudou de longe");
            } finally {
                close(player);
            }
        });
    }

    @GameTest(template = "empty7")
    public static void payloadDoModoExigeSintonizador(GameTestHelper helper) {
        formed(helper, RouterTier.BASIC, core -> {
            ServerPlayer player = near(helper, core);
            openMenu(player, core);
            try {
                helper.assertTrue(ResonatorPayloads.handleMode(player, new ResonatorModePayload(CONTAINER_ID, true)),
                        "o automático foi recusado com Sintonizador");
                helper.assertTrue(core.auto(), "o Núcleo não ficou no automático");
            } finally {
                close(player);
            }
        });
    }

    @GameTest(template = "empty7")
    public static void payloadDoModoSemSintonizadorRecusa(GameTestHelper helper) {
        formed(helper, Layout.ENERGY_ON_TOP, RouterTier.BASIC, core -> {
            ServerPlayer player = near(helper, core);
            openMenu(player, core);
            try {
                helper.assertTrue(!ResonatorPayloads.handleMode(player, new ResonatorModePayload(CONTAINER_ID, true)),
                        "o automático foi aceito sem Sintonizador");
                helper.assertTrue(!core.auto(), "o Núcleo ficou no automático sem Sintonizador");
            } finally {
                close(player);
            }
        });
    }

    @GameTest(template = "empty")
    public static void estadoIdaEVoltaPeloCodec(GameTestHelper helper) {
        ResonatorStatePayload state = new ResonatorStatePayload(CONTAINER_ID, 1_234_567_890_123L, 9_000_000_000L, 77,
                1L << 40, 97.45, 720, 980, true, false, -13, true, 300, 600, 2, 1, 2);
        RegistryFriendlyByteBuf buf = new RegistryFriendlyByteBuf(Unpooled.buffer(), helper.getLevel().registryAccess());
        ResonatorStatePayload.STREAM_CODEC.encode(buf, state);
        helper.assertValueEqual(ResonatorStatePayload.STREAM_CODEC.decode(buf), state, "estado depois do codec");
        helper.assertValueEqual(buf.readableBytes(), 0, "bytes sobrando");
        ResonatorDialPayload dial = new ResonatorDialPayload(CONTAINER_ID, 94.05);
        ResonatorDialPayload.STREAM_CODEC.encode(buf, dial);
        helper.assertValueEqual(ResonatorDialPayload.STREAM_CODEC.decode(buf), dial, "dial depois do codec");
        ResonatorModePayload mode = new ResonatorModePayload(CONTAINER_ID, true);
        ResonatorModePayload.STREAM_CODEC.encode(buf, mode);
        helper.assertValueEqual(ResonatorModePayload.STREAM_CODEC.decode(buf), mode, "modo depois do codec");
        helper.succeed();
    }
}
