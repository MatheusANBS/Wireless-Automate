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
import java.util.function.Supplier;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.item.ItemEntity;
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
    private static final double EYE_HEIGHT = 1.62;
    /** As oblíquas: (giro a partir da frente, inclinação) em graus; inclinação positiva é a câmera acima do alvo. */
    private static final double[][] OBLIQUE_VIEWS = {{45, 35}, {45, -15}, {135, 35}, {135, -15}};
    private static final double OBLIQUE_DISTANCE = 1.8;
    private static final int OBLIQUE_LIFT = 3;
    /**
     * O centro da coluna de vidro (de 6,3 a 13,7 px) e a câmera rente a ela, do lado leste, a 0,9 bloco do centro: mais
     * perto, a caixa do jogador encosta no bloco e o jogo o empurra para fora.
     */
    private static final double GLASS_CENTER_Y = 10.0 / 16.0;
    private static final double GRAZE_DISTANCE = 0.9;
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
        E2eVariants.pt(list, () -> {
            list.add(language("pt_br"));
            round(list, playerId, pos, "-pt");
            list.add(language("en_us"));
        });
        blockShots(list, playerId, pos);
        list.add(new Step("Reparador: tirar o bloco", STEP_TIMEOUT_MS, () -> onServer(server -> {
            remove(server.overworld(), pos[0]);
            return null;
        }), () -> Minecraft.getInstance().level.getBlockState(pos[0]).isAir(), () -> "bloco no cliente"));
    }

    /** Um idioma: o Reparador novo, os três estados com uma foto cada e, em inglês, a foto no escuro também. */
    private static void round(List<Step> list, UUID playerId, BlockPos[] pos, String suffix) {
        boolean withDark = suffix.isEmpty() && E2eVariants.darkOn();
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
        if (suffix.isEmpty()) {
            hovered(list, "reparador-tela-dica", 1);
            smallWindow(list);
        }
        list.add(close("Reparador: fechar" + suffix));
    }

    /**
     * O modelo no mundo: para cada {@code facing}, a foto de frente (de onde o jogador que o pôs estava, com a porta de
     * energia para a câmera) e a de lado (vindo do horário do {@code facing}), sem a interface; depois as oblíquas, com
     * o bloco no ar, e o item. O mundo do e2e tem o ciclo do dia desligado, então a luz é a mesma em todas. No fim, o
     * voo e a interface voltam ao que eram, porque na rodada completa outros passos vêm depois (uma falha encerra o e2e
     * inteiro, então só o caminho que passa precisa restaurar).
     */
    private static void blockShots(List<Step> list, UUID playerId, BlockPos[] pos) {
        boolean[] before = new boolean[2];
        list.add(new Step("Reparador: sem interface", STEP_TIMEOUT_MS, () -> {
            before[0] = Minecraft.getInstance().options.hideGui;
            before[1] = onServer(server -> server.getPlayerList().getPlayer(playerId).getAbilities().flying);
            Minecraft.getInstance().options.hideGui = true;
        }, () -> true, () -> ""));
        for (Direction facing : FACINGS) {
            list.add(setFacing(pos, facing));
            String name = "reparador-bloco-" + facing.getSerializedName();
            list.add(blockShot(name + "-frente", playerId, pos, facing));
            list.add(blockShot(name + "-lado", playerId, pos, facing.getClockWise()));
        }
        obliqueShots(list, playerId, pos);
        itemShot(list, playerId, pos);
        list.add(new Step("Reparador: voo e interface de volta", STEP_TIMEOUT_MS, () -> {
            Minecraft.getInstance().options.hideGui = before[0];
            onServer(server -> {
                ServerPlayer player = server.getPlayerList().getPlayer(playerId);
                player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
                player.getAbilities().flying = before[1];
                player.onUpdateAbilities();
                return null;
            });
        }, () -> Minecraft.getInstance().player.getAbilities().flying == before[1]
                && Minecraft.getInstance().player.getMainHandItem().isEmpty(),
                () -> "voo " + Minecraft.getInstance().player.getAbilities().flying));
    }

    private static Step setFacing(BlockPos[] pos, Direction facing) {
        return new Step("Reparador: bloco para " + facing, STEP_TIMEOUT_MS, () -> onServer(server -> {
            ServerLevel level = server.overworld();
            level.setBlockAndUpdate(pos[0], level.getBlockState(pos[0]).setValue(RepairerBlock.FACING, facing));
            return null;
        }), () -> Minecraft.getInstance().level.getBlockState(pos[0]).getValue(RepairerBlock.FACING) == facing,
                () -> "estado " + Minecraft.getInstance().level.getBlockState(pos[0]));
    }

    /**
     * O interior visto pelo vidro de vários ângulos, para conferir que nada some atrás dele: o bloco (para o norte)
     * sobe {@link #OBLIQUE_LIFT} blocos, para a câmera poder olhar de baixo, e a câmera fica a {@link #OBLIQUE_DISTANCE}
     * do centro do vidro em cada par (giro a partir da frente, inclinação) de {@link #OBLIQUE_VIEWS}; a última é rente
     * ao vidro, do lado leste. O bloco do ar sai no fim.
     */
    private static void obliqueShots(List<Step> list, UUID playerId, BlockPos[] pos) {
        BlockPos[] high = new BlockPos[1];
        list.add(new Step("Reparador: bloco no ar", STEP_TIMEOUT_MS, () -> onServer(server -> {
            ServerLevel level = server.overworld();
            high[0] = pos[0].above(OBLIQUE_LIFT);
            level.setBlockAndUpdate(high[0], ModBlocks.CRYSTAL_REPAIRER.get().defaultBlockState()
                    .setValue(RepairerBlock.FACING, Direction.NORTH));
            return null;
        }), () -> Minecraft.getInstance().level.getBlockState(high[0]).is(ModBlocks.CRYSTAL_REPAIRER.get()),
                () -> "bloco no ar no cliente"));
        int n = 1;
        for (double[] view : OBLIQUE_VIEWS) {
            double yaw = Math.toRadians(view[0]);
            double pitch = Math.toRadians(view[1]);
            double horizontal = OBLIQUE_DISTANCE * Math.cos(pitch);
            double[] offset = {Math.sin(yaw) * horizontal, OBLIQUE_DISTANCE * Math.sin(pitch),
                    -Math.cos(yaw) * horizontal};
            list.add(cameraShot("reparador-bloco-obliquo-" + n++, playerId, () -> high[0], offset, GLASS_CENTER_Y));
        }
        list.add(cameraShot("reparador-bloco-obliquo-" + n, playerId, () -> high[0],
                new double[] {GRAZE_DISTANCE, 0, 0.15}, GLASS_CENTER_Y));
        list.add(new Step("Reparador: bloco do ar fora", STEP_TIMEOUT_MS, () -> onServer(server -> {
            remove(server.overworld(), high[0]);
            return null;
        }), () -> Minecraft.getInstance().level.getBlockState(high[0]).isAir(), () -> "bloco no ar no cliente"));
    }

    /**
     * O item na mão (em primeira pessoa e na barra, com a interface) e solto sobre o bloco, sem gravidade e sem poder
     * ser pego, para o vidro do modelo composto aparecer; a entidade sai depois da foto.
     */
    private static void itemShot(List<Step> list, UUID playerId, BlockPos[] pos) {
        ItemEntity[] dropped = new ItemEntity[1];
        list.add(setFacing(pos, Direction.NORTH));
        list.add(new Step("Reparador: item na mão e no ar", STEP_TIMEOUT_MS, () -> {
            Minecraft.getInstance().options.hideGui = false;
            onServer(server -> {
                ServerPlayer player = server.getPlayerList().getPlayer(playerId);
                ItemStack stack = new ItemStack(ModItems.CRYSTAL_REPAIRER.get());
                player.setItemInHand(InteractionHand.MAIN_HAND, stack.copy());
                ServerLevel level = player.serverLevel();
                ItemEntity entity = new ItemEntity(level, pos[0].getX() + 0.5, pos[0].getY() + 1.05,
                        pos[0].getZ() + 0.5, stack, 0, 0, 0);
                entity.setNoGravity(true);
                entity.setPickUpDelay(Short.MAX_VALUE);
                level.addFreshEntity(entity);
                dropped[0] = entity;
                return null;
            });
        }, () -> Minecraft.getInstance().player.getMainHandItem().is(ModItems.CRYSTAL_REPAIRER.get())
                && itemPasses() == 3, () -> "mão " + Minecraft.getInstance().player.getMainHandItem() + ", "
                + itemPasses() + " passes do modelo do item"));
        list.add(blockShot("reparador-item", playerId, pos, Direction.NORTH));
        list.add(cameraShot("reparador-item-perto", playerId, () -> pos[0], new double[] {0, 0.35, -1.1}, 1.3));
        list.add(new Step("Reparador: item do ar fora", STEP_TIMEOUT_MS, () -> onServer(server -> {
            dropped[0].discard();
            return null;
        }), () -> true, () -> ""));
    }

    /** Quantos modelos o item desenha: 3 (o sólido, as luzes acesas e o vidro) quando o modelo composto do item carregou. */
    private static int itemPasses() {
        ItemStack stack = new ItemStack(ModItems.CRYSTAL_REPAIRER.get());
        return Minecraft.getInstance().getItemRenderer().getModel(stack, null, null, 0).getRenderPasses(stack, true)
                .size();
    }

    /** A câmera de frente ou de lado: o olho na altura de quem está de pé no chão do bloco, a {@link #VIEW_DISTANCE}. */
    private static Step blockShot(String file, UUID playerId, BlockPos[] pos, Direction from) {
        return cameraShot(file, playerId, () -> pos[0], new double[] {from.getStepX() * VIEW_DISTANCE,
                EYE_HEIGHT - VIEW_TARGET_Y, from.getStepZ() * VIEW_DISTANCE}, VIEW_TARGET_Y);
    }

    /**
     * O jogador voando com o olho em {@code alvo + offset}, onde o alvo é o centro do bloco na altura {@code targetY},
     * olhando o alvo. A janela é real e o mouse gira a câmera: o ângulo volta a cada tick até a foto.
     */
    private static Step cameraShot(String file, UUID playerId, Supplier<BlockPos> block,
                                   double[] offset, double targetY) {
        double[] cam = new double[3];
        float[] ang = new float[2];
        return new Step("captura " + file, STEP_TIMEOUT_MS, () -> {
            BlockPos p = block.get();
            cam[0] = p.getX() + 0.5 + offset[0];
            cam[1] = p.getY() + targetY + offset[1] - EYE_HEIGHT;
            cam[2] = p.getZ() + 0.5 + offset[2];
            ang[0] = (float) Math.toDegrees(Math.atan2(offset[0], -offset[2]));
            ang[1] = (float) Math.toDegrees(Math.atan2(offset[1], Math.sqrt(offset[0] * offset[0]
                    + offset[2] * offset[2])));
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
