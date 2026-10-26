package io.github.matheusanbs.wirelessautomate.client.dev;

import static io.github.matheusanbs.wirelessautomate.client.dev.DevEndToEnd.*;
import static io.github.matheusanbs.wirelessautomate.client.dev.E2eSteps.*;
import static io.github.matheusanbs.wirelessautomate.client.dev.E2eUi.*;
import static io.github.matheusanbs.wirelessautomate.client.dev.E2eWorld.*;

import io.github.matheusanbs.wirelessautomate.GuiTheme;
import io.github.matheusanbs.wirelessautomate.block.RouterTier;
import io.github.matheusanbs.wirelessautomate.client.repairer.RepairerScreen;
import io.github.matheusanbs.wirelessautomate.packet.RepairerStatePayload;
import io.github.matheusanbs.wirelessautomate.registry.ModBlocks;
import io.github.matheusanbs.wirelessautomate.registry.ModItems;
import io.github.matheusanbs.wirelessautomate.repairer.RepairerBlockEntity;
import io.github.matheusanbs.wirelessautomate.repairer.RepairerSlots;
import java.util.List;
import java.util.UUID;
import java.util.function.Predicate;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;

/**
 * A tela do Reparador de Cristais em três estados, nos dois temas e em pt_br: parado (o estoque e dois reparados, sem
 * rachado), sem energia (um Sinal, um Lapidado e um Afinado rachados esperando, a câmara escura) e reparando (a
 * energia chega e as três linhas andam). Cada idioma começa com um Reparador novo, ao lado do jogador; no fim ele sai.
 */
final class E2eRepairerSteps {
    /** A energia do estado parado: paga qualquer um dos três reparos, para a câmara não escurecer. */
    private static final long IDLE_ENERGY = 20_000;
    /** A energia que liga as três linhas juntas (432 + 13.824 + 20.000 FE com os padrões). */
    private static final long WORK_ENERGY = 50_000;
    private static final int FEED = 16;
    /** Ticks de reparo antes da foto, para as barras aparecerem. */
    private static final int WORK_TICKS = 30;
    private static final int OPEN_RETRY_TICKS = 40;

    private E2eRepairerSteps() {
    }

    static void repairerSteps(List<Step> list) {
        UUID playerId = Minecraft.getInstance().player.getUUID();
        BlockPos[] pos = new BlockPos[1];
        list.add(close("Reparador: fechar a tela de antes"));
        round(list, playerId, pos, "");
        list.add(language("pt_br"));
        round(list, playerId, pos, "-pt");
        list.add(language("en_us"));
        list.add(new Step("Reparador: tirar o bloco", STEP_TIMEOUT_MS, () -> onServer(server -> {
            remove(server.overworld(), pos[0]);
            return null;
        }), () -> Minecraft.getInstance().level.getBlockState(pos[0]).isAir(), () -> "bloco no cliente"));
    }

    /** Um idioma: o Reparador novo, os três estados com uma foto cada e, em inglês, a foto no escuro também. */
    private static void round(List<Step> list, UUID playerId, BlockPos[] pos, String suffix) {
        boolean withDark = suffix.isEmpty();
        list.add(new Step("Reparador: bloco novo" + suffix, STEP_TIMEOUT_MS, () -> onServer(server -> {
            ServerPlayer player = server.getPlayerList().getPlayer(playerId);
            ServerLevel level = player.serverLevel();
            if (pos[0] == null) {
                pos[0] = player.blockPosition().offset(-3, 0, 0);
            }
            player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
            remove(level, pos[0]);
            level.setBlockAndUpdate(pos[0], ModBlocks.CRYSTAL_REPAIRER.get().defaultBlockState());
            return null;
        }), () -> Minecraft.getInstance().level.getBlockEntity(pos[0]) instanceof RepairerBlockEntity
                && onServer(server -> server.overworld().getBlockEntity(pos[0]) instanceof RepairerBlockEntity),
                () -> "Reparador no cliente"));
        list.add(new Step("Reparador: parado" + suffix, STEP_TIMEOUT_MS, () -> onServer(server -> {
            RepairerBlockEntity repairer = repairer(server, pos[0]);
            repairer.items().set(RepairerSlots.FEED, new ItemStack(ModItems.SIGNAL_CRYSTAL.get(), FEED));
            repairer.items().set(RepairerSlots.OUTPUT_FIRST, new ItemStack(ModItems.SIGNAL_CRYSTAL.get()));
            repairer.items().set(RepairerSlots.OUTPUT_FIRST + 1, new ItemStack(ModItems.CUT_SIGNAL_CRYSTAL.get()));
            repairer.itemsChanged();
            repairer.bulkEnergyView().insert(IDLE_ENERGY, false);
            return null;
        }), () -> true, () -> ""));
        list.add(open("Reparador: abrir" + suffix, pos, state -> state.energy() == IDLE_ENERGY));
        shot(list, "reparador-tela-parado" + suffix, withDark);
        list.add(new Step("Reparador: sem energia" + suffix, STEP_TIMEOUT_MS, () -> onServer(server -> {
            RepairerBlockEntity repairer = repairer(server, pos[0]);
            repairer.energy().set(0);
            repairer.items().set(0, E2eResonatorScreenSteps.cracked(new ItemStack(ModItems.SIGNAL_CRYSTAL.get())));
            repairer.items().set(1, E2eResonatorScreenSteps.cracked(new ItemStack(ModItems.CUT_SIGNAL_CRYSTAL.get())));
            repairer.items().set(2, E2eResonatorScreenSteps.cracked(
                    new ItemStack(ModItems.TUNED_CRYSTALS.get(RouterTier.ELITE).get())));
            repairer.itemsChanged();
            return null;
        }), () -> ready(state -> state.energy() == 0 && state.cost0() > 0) && screen().getMenu().getSlot(2).hasItem(),
                () -> "estado " + describeState()));
        shot(list, "reparador-tela-sem-energia" + suffix, withDark);
        list.add(new Step("Reparador: reparando" + suffix, STEP_TIMEOUT_MS, () -> onServer(server -> {
            repairer(server, pos[0]).bulkEnergyView().insert(WORK_ENERGY, false);
            return null;
        }), () -> stepTicks >= WORK_TICKS && ready(state -> state.ends()[0] > 0 && state.ends()[1] > 0
                && state.ends()[2] > 0), () -> "estado " + describeState()));
        shot(list, "reparador-tela-reparando" + suffix, withDark);
        if (withDark) {
            hovered(list, "reparador-tela-dica", 1);
        }
        list.add(close("Reparador: fechar" + suffix));
    }

    /** A foto no tema claro e, com {@code withDark}, a do escuro, cada uma com a conferência dos textos cortados. */
    private static void shot(List<Step> list, String name, boolean withDark) {
        list.add(capture(name));
        list.add(clipCheck(name));
        if (withDark) {
            list.add(E2eDarkSteps.themeClick(name + ": tema escuro", GuiTheme.DARK));
            list.add(capture(name + "-escuro"));
            list.add(clipCheck(name + "-escuro"));
            list.add(E2eDarkSteps.themeClick(name + ": tema claro", GuiTheme.LIGHT));
        }
    }

    /** A foto com o mouse num slot do Reparador (a dica aberta). */
    private static void hovered(List<Step> list, String name, int slot) {
        list.add(new Step("captura " + name, STEP_TIMEOUT_MS, () -> {
            int[] point = screen().repairerSlotCenter(slot);
            moveMouse(point[0], point[1]);
        }, () -> {
            if (stepTicks < 8) {
                return false;
            }
            capture(Minecraft.getInstance(), name);
            return true;
        }, () -> name + ".png"));
        list.add(clipCheck(name));
    }

    private static Step open(String name, BlockPos[] pos, Predicate<RepairerStatePayload> ready) {
        return new Step(name, STEP_TIMEOUT_MS, () -> useOn(pos[0]), () -> {
            if (Minecraft.getInstance().screen == null && stepTicks > 0 && stepTicks % OPEN_RETRY_TICKS == 0) {
                useOn(pos[0]);
            }
            return Minecraft.getInstance().screen instanceof RepairerScreen screen
                    && screen.getMenu().pos().equals(pos[0]) && ready(ready);
        }, () -> "tela " + describe(Minecraft.getInstance().screen) + ", estado " + describeState());
    }

    private static RepairerScreen screen() throws StepFailure {
        if (Minecraft.getInstance().screen instanceof RepairerScreen screen) {
            return screen;
        }
        throw new StepFailure("sem a tela do Reparador: " + describe(Minecraft.getInstance().screen));
    }

    private static boolean ready(Predicate<RepairerStatePayload> test) {
        return Minecraft.getInstance().screen instanceof RepairerScreen screen && screen.getMenu().state() != null
                && test.test(screen.getMenu().state());
    }

    private static String describeState() {
        if (!(Minecraft.getInstance().screen instanceof RepairerScreen screen) || screen.getMenu().state() == null) {
            return "nenhum";
        }
        RepairerStatePayload state = screen.getMenu().state();
        return "energia " + state.energy() + ", custo " + state.cost0() + ", fins " + java.util.Arrays.toString(state.ends());
    }

    /** Tira o bloco sem largar o conteúdo no chão. */
    private static void remove(ServerLevel level, BlockPos pos) {
        if (level.getBlockEntity(pos) instanceof RepairerBlockEntity repairer) {
            repairer.container().clearContent();
        }
        level.setBlockAndUpdate(pos, Blocks.AIR.defaultBlockState());
    }

    private static RepairerBlockEntity repairer(MinecraftServer server, BlockPos pos) {
        return (RepairerBlockEntity) server.overworld().getBlockEntity(pos);
    }
}
