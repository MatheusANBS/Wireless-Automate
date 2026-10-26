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
import io.github.matheusanbs.wirelessautomate.repairer.RepairerBlock;
import io.github.matheusanbs.wirelessautomate.repairer.RepairerBlockEntity;
import io.github.matheusanbs.wirelessautomate.repairer.RepairerSlots;
import java.util.List;
import java.util.UUID;
import java.util.function.Predicate;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;

/**
 * A tela do Reparador de Cristais em três estados, nos dois temas e em pt_br: parado (o estoque e dois reparados, sem
 * rachado), sem energia (um Sinal, um Lapidado e um Afinado rachados esperando, a câmara escura) e reparando (a
 * energia chega e as três linhas andam). Cada idioma começa com um Reparador novo, ao lado do jogador. Depois, o bloco
 * no mundo nos quatro {@code facing}, de frente (a porta de energia) e de lado; no fim ele sai.
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
    private static final int SMALL_W = 1366;
    private static final int SMALL_H = 768;
    /** As fotos do bloco: o olho a 2 blocos do centro, na altura de quem está de pé no chão do bloco. */
    private static final double VIEW_DISTANCE = 2.0;
    private static final double VIEW_TARGET_Y = 0.45;
    /** Ticks com a câmera parada antes da foto, para o chunk refazer a malha com o estado novo. */
    private static final int BLOCK_SETTLE_TICKS = 20;
    private static final Direction[] FACINGS = {Direction.NORTH, Direction.EAST, Direction.SOUTH, Direction.WEST};

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
        blockShots(list, playerId, pos);
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
            smallWindow(list);
        }
        list.add(close("Reparador: fechar" + suffix));
    }

    /**
     * O modelo no mundo: para cada {@code facing}, a foto de frente (de onde o jogador que o pôs estava, com a porta de
     * energia para a câmera) e a de lado (vindo do horário do {@code facing}), sem a interface. O mundo do e2e tem o
     * ciclo do dia desligado, então a luz é a mesma em todas. No fim, o voo e a interface voltam ao que eram, porque na
     * rodada completa outros passos vêm depois (uma falha encerra o e2e inteiro, então só o caminho que passa precisa
     * restaurar).
     */
    private static void blockShots(List<Step> list, UUID playerId, BlockPos[] pos) {
        boolean[] before = new boolean[2];
        list.add(new Step("Reparador: sem interface", STEP_TIMEOUT_MS, () -> {
            before[0] = Minecraft.getInstance().options.hideGui;
            before[1] = onServer(server -> server.getPlayerList().getPlayer(playerId).getAbilities().flying);
            Minecraft.getInstance().options.hideGui = true;
        }, () -> true, () -> ""));
        for (Direction facing : FACINGS) {
            list.add(new Step("Reparador: bloco para " + facing, STEP_TIMEOUT_MS, () -> onServer(server -> {
                ServerLevel level = server.overworld();
                level.setBlockAndUpdate(pos[0], level.getBlockState(pos[0]).setValue(RepairerBlock.FACING, facing));
                return null;
            }), () -> Minecraft.getInstance().level.getBlockState(pos[0]).getValue(RepairerBlock.FACING) == facing,
                    () -> "estado " + Minecraft.getInstance().level.getBlockState(pos[0])));
            String name = "reparador-bloco-" + facing.getSerializedName();
            list.add(blockShot(name + "-frente", playerId, pos, facing));
            list.add(blockShot(name + "-lado", playerId, pos, facing.getClockWise()));
        }
        list.add(new Step("Reparador: voo e interface de volta", STEP_TIMEOUT_MS, () -> {
            Minecraft.getInstance().options.hideGui = before[0];
            onServer(server -> {
                ServerPlayer player = server.getPlayerList().getPlayer(playerId);
                player.getAbilities().flying = before[1];
                player.onUpdateAbilities();
                return null;
            });
        }, () -> Minecraft.getInstance().player.getAbilities().flying == before[1],
                () -> "voo " + Minecraft.getInstance().player.getAbilities().flying));
    }

    /**
     * O jogador voando com os pés no chão do bloco, a {@link #VIEW_DISTANCE} do centro do lado {@code from}, olhando o
     * bloco. A janela é real e o mouse gira a câmera: o ângulo volta a cada tick até a foto.
     */
    private static Step blockShot(String file, UUID playerId, BlockPos[] pos, Direction from) {
        double[] cam = new double[3];
        float[] ang = new float[2];
        return new Step("captura " + file, STEP_TIMEOUT_MS, () -> {
            BlockPos p = pos[0];
            cam[0] = p.getX() + 0.5 + from.getStepX() * VIEW_DISTANCE;
            cam[1] = p.getY();
            cam[2] = p.getZ() + 0.5 + from.getStepZ() * VIEW_DISTANCE;
            double ex = p.getX() + 0.5 - cam[0];
            double ey = p.getY() + VIEW_TARGET_Y - (cam[1] + 1.62);
            double ez = p.getZ() + 0.5 - cam[2];
            ang[0] = (float) Math.toDegrees(Math.atan2(-ex, ez));
            ang[1] = (float) -Math.toDegrees(Math.atan2(ey, Math.sqrt(ex * ex + ez * ez)));
            Minecraft.getInstance().mouseHandler.releaseMouse();
            look(ang);
            onServer(server -> {
                ServerPlayer player = server.getPlayerList().getPlayer(playerId);
                player.getAbilities().flying = true;
                player.onUpdateAbilities();
                player.teleportTo(player.serverLevel(), cam[0], cam[1], cam[2], ang[0], ang[1]);
                return null;
            });
        }, () -> {
            look(ang);
            if (stepTicks < BLOCK_SETTLE_TICKS
                    || Minecraft.getInstance().player.position().distanceToSqr(cam[0], cam[1], cam[2]) > 0.01) {
                return false;
            }
            capture(Minecraft.getInstance(), file);
            return true;
        }, () -> "câmera em " + Minecraft.getInstance().player.position());
    }

    private static void look(float[] ang) {
        Minecraft minecraft = Minecraft.getInstance();
        minecraft.player.setYRot(ang[0]);
        minecraft.player.setXRot(ang[1]);
        minecraft.player.yRotO = ang[0];
        minecraft.player.xRotO = ang[1];
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

    /**
     * A tela numa janela de 1366 × 768 na escala 3 da interface, a da escala automática nesse tamanho: 256 px de
     * altura útil, e a tela tem de caber inteira. A janela muda primeiro (o e2e escolhe a escala dele ao ver o tamanho
     * novo) e a escala 3 vem no passo seguinte; no fim a janela volta a 1280 × 800 e o e2e refaz a escala dele.
     */
    private static void smallWindow(List<Step> list) {
        list.add(new Step("Reparador: janela 1366x768", STEP_TIMEOUT_MS,
                () -> Minecraft.getInstance().getWindow().setWindowed(SMALL_W, SMALL_H),
                () -> stepTicks >= 10 && Minecraft.getInstance().getWindow().getWidth() == SMALL_W
                        && Minecraft.getInstance().getWindow().getHeight() == SMALL_H,
                () -> "janela " + Minecraft.getInstance().getWindow().getWidth() + "x"
                        + Minecraft.getInstance().getWindow().getHeight()));
        list.add(new Step("Reparador: escala 3", STEP_TIMEOUT_MS, () -> {
            Minecraft minecraft = Minecraft.getInstance();
            minecraft.options.guiScale().set(3);
            minecraft.resizeDisplay();
        }, () -> stepTicks >= 5 && Minecraft.getInstance().getWindow().getGuiScale() == 3 && fits(),
                E2eRepairerSteps::describeFit));
        list.add(capture("reparador-tela-1366x768"));
        list.add(clipCheck("reparador-tela-1366x768"));
        list.add(new Step("Reparador: janela de volta", STEP_TIMEOUT_MS,
                () -> Minecraft.getInstance().getWindow().setWindowed(1280, 800),
                () -> stepTicks >= 10 && Minecraft.getInstance().getWindow().getGuiScale() != 3,
                E2eRepairerSteps::describeFit));
    }

    /** A tela inteira dentro da área da interface, sem corte em cima nem embaixo. */
    private static boolean fits() {
        return Minecraft.getInstance().screen instanceof RepairerScreen screen && screen.getGuiTop() >= 0
                && screen.getGuiTop() + screen.getYSize() <= screen.height && screen.getGuiLeft() >= 0
                && screen.getGuiLeft() + screen.getXSize() <= screen.width;
    }

    private static String describeFit() {
        if (!(Minecraft.getInstance().screen instanceof RepairerScreen screen)) {
            return "tela " + describe(Minecraft.getInstance().screen);
        }
        return "escala " + Minecraft.getInstance().getWindow().getGuiScale() + ", interface " + screen.width + "x"
                + screen.height + ", tela em " + screen.getGuiLeft() + "," + screen.getGuiTop() + " com "
                + screen.getXSize() + "x" + screen.getYSize();
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
