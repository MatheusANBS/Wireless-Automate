package io.github.matheusanbs.wirelessautomate.client.dev;

import static io.github.matheusanbs.wirelessautomate.client.dev.DevEndToEnd.*;
import static io.github.matheusanbs.wirelessautomate.client.dev.E2eShowcaseWorld.*;
import static io.github.matheusanbs.wirelessautomate.client.dev.E2eSteps.close;
import static io.github.matheusanbs.wirelessautomate.client.dev.E2eWorld.*;

import io.github.matheusanbs.wirelessautomate.block.RouterTier;
import io.github.matheusanbs.wirelessautomate.registry.ModBlocks;
import io.github.matheusanbs.wirelessautomate.registry.ModItems;
import io.github.matheusanbs.wirelessautomate.repairer.RepairerBlock;
import io.github.matheusanbs.wirelessautomate.repairer.RepairerBlockEntity;
import io.github.matheusanbs.wirelessautomate.repairer.RepairerSlots;
import java.util.List;
import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;

/**
 * O Reparador de Cristais na vitrine do CurseForge: o bloco no jardim, a leste da Vitrine do Ressonador, com os oito
 * pedestais cheios de cristais rachados (Sinal, Lapidado e Afinados) e energia de sobra; fotos do mundo com as linhas
 * reparando e da tela aberta. Roda no fim da vitrine inteira ou sozinho, com {@code WA_SHOWCASE_ONLY=reparador}.
 */
final class E2eShowcaseRepairer {
    /** A posição do bloco, relativa à base da vitrine: no topo do altar do {@link E2eShowcaseWorld}, a leste da Vitrine do Ressonador. */
    private static final int[] AT = {E2eShowcaseWorld.REPAIRER_X, 1, E2eShowcaseWorld.REPAIRER_Z};
    private static final long ENERGY = 100_000_000_000L;

    private E2eShowcaseRepairer() {
    }

    static void steps(List<Step> list) {
        BlockPos[] pos = new BlockPos[1];
        list.add(new Step("Reparador: fechar a tela", STEP_TIMEOUT_MS, () -> {
            Minecraft minecraft = Minecraft.getInstance();
            if (minecraft.screen != null) {
                minecraft.screen.onClose();
            }
        }, () -> Minecraft.getInstance().screen == null, () -> "tela " + describe(Minecraft.getInstance().screen)));
        list.add(new Step("Reparador: montar o bloco", STEP_TIMEOUT_MS, () -> {
            pos[0] = showBase.offset(AT[0], AT[1], AT[2]);
            UUID playerId = Minecraft.getInstance().player.getUUID();
            onServer(server -> {
                ServerPlayer player = server.getPlayerList().getPlayer(playerId);
                player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
                server.overworld().setBlockAndUpdate(pos[0], ModBlocks.CRYSTAL_REPAIRER.get().defaultBlockState()
                        .setValue(RepairerBlock.FACING, Direction.SOUTH));
                return null;
            });
        }, () -> Minecraft.getInstance().level.getBlockEntity(pos[0]) instanceof RepairerBlockEntity
                && onServer(server -> server.overworld().getBlockEntity(pos[0]) instanceof RepairerBlockEntity),
                () -> "Reparador no cliente"));
        list.add(E2eShowcase.showLook("Reparador: câmera no jardim", AT[0] + 0.5, 0.4, AT[2] + 2.7,
                AT[0], 1.65, AT[2], false));
        list.add(new Step("Reparador: oito cristais rachados e energia", STEP_TIMEOUT_MS, () -> onServer(server -> {
            RepairerBlockEntity repairer = E2eRepairerSteps.repairer(server, pos[0]);
            repairer.items().set(RepairerSlots.FEED, new ItemStack(ModItems.SIGNAL_CRYSTAL.get(), 64));
            for (int line = 0; line < 8; line++) {
                ItemStack crystal = switch (line % 4) {
                    case 0 -> new ItemStack(ModItems.SIGNAL_CRYSTAL.get());
                    case 1 -> new ItemStack(ModItems.CUT_SIGNAL_CRYSTAL.get());
                    case 2 -> new ItemStack(ModItems.TUNED_CRYSTALS.get(RouterTier.ELITE).get());
                    default -> new ItemStack(ModItems.TUNED_CRYSTALS.get(RouterTier.EMERALD).get());
                };
                repairer.items().set(line, E2eResonatorScreenSteps.cracked(crystal));
            }
            repairer.itemsChanged();
            return null;
        }), () -> true, () -> ""));
        list.add(DevEndToEnd.wait("Reparador: parado, rachaduras apagadas", 30));
        list.add(capture("s22a-reparador-parado"));
        list.add(new Step("Reparador: ligar a energia", STEP_TIMEOUT_MS, () -> onServer(server -> {
            E2eRepairerSteps.repairer(server, pos[0]).bulkEnergyView().insert(ENERGY, false);
            return null;
        }), () -> onServer(server -> E2eRepairerSteps.repairer(server, pos[0]).running(0)),
                () -> "linhas andando"));
        list.add(DevEndToEnd.wait("Reparador: rachaduras fechando", 45));
        list.add(capture("s22b-reparador-fechando"));
        list.add(DevEndToEnd.wait("Reparador: convergência", 30));
        list.add(capture("s22c-reparador-convergencia"));
        list.add(DevEndToEnd.wait("Reparador: a onda e a saída", 25));
        list.add(capture("s22d-reparador-saida"));
        list.add(DevEndToEnd.wait("Reparador: reparado", 40));
        list.add(new Step("Reparador: de novo, para a tela", STEP_TIMEOUT_MS, () -> onServer(server -> {
            RepairerBlockEntity repairer = E2eRepairerSteps.repairer(server, pos[0]);
            for (int line = 0; line < 8; line++) {
                if (repairer.items().get(line).isEmpty()) {
                    repairer.items().set(line, E2eResonatorScreenSteps.cracked(
                            new ItemStack(ModItems.SIGNAL_CRYSTAL.get())));
                }
            }
            repairer.itemsChanged();
            return null;
        }), () -> true, () -> ""));
        list.add(E2eShowcase.showLook("Reparador: câmera na tela", AT[0] + 0.5, 2.5, AT[2] + 2.4, AT[0] + 0.5, 1.9,
                AT[2], true));
        list.add(E2eRepairerSteps.open("Reparador: abrir a tela", pos, state -> state.ends()[0] > 0));
        list.add(new Step("Reparador: mouse fora", STEP_TIMEOUT_MS, () -> E2eUi.moveMouse(0, 0),
                () -> stepTicks >= 40, () -> "esperando"));
        list.add(capture("s23-reparador-tela"));
        list.add(close("Reparador: fechar a tela"));
    }
}
