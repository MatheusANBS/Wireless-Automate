package io.github.matheusanbs.wirelessautomate.client.dev;

import static io.github.matheusanbs.wirelessautomate.client.dev.DevEndToEnd.*;
import static io.github.matheusanbs.wirelessautomate.client.dev.E2eUi.*;
import static io.github.matheusanbs.wirelessautomate.client.dev.E2eWorld.*;

import io.github.matheusanbs.wirelessautomate.client.resonator.ResonatorProjection;
import io.github.matheusanbs.wirelessautomate.registry.ModBlocks;
import io.github.matheusanbs.wirelessautomate.resonator.ProjectionDiff;
import io.github.matheusanbs.wirelessautomate.resonator.ResonanceCoreBlock;
import io.github.matheusanbs.wirelessautomate.resonator.ResonatorBlueprint;
import io.github.matheusanbs.wirelessautomate.resonator.ResonatorLayout.Face;
import io.github.matheusanbs.wirelessautomate.resonator.ResonatorLayout.Part;
import io.github.matheusanbs.wirelessautomate.resonator.ResonatorPartBlock;
import java.lang.reflect.Field;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * A projeção do Ressonador no e2e: uma Vitrine pela metade (o Núcleo virado para o leste, as duas camadas de baixo
 * das carcaças, a janela sem dois vidros e uma terra numa aresta), o clique de mão vazia no Núcleo, a dica que fica
 * na action bar mesmo com a linha do problema que o servidor manda no clique, a contagem e a
 * captura dos fantasmas; depois a estrutura completa, que monta e apaga a projeção, e a segunda captura.
 */
final class E2eResonatorSteps {
    private static final Part[][][] VITRINE = ResonatorBlueprint.expected(Face.EAST, ResonatorBlueprint.Layout.VITRINE);
    /** A terra fica na aresta vertical entre as faces leste e sul, de frente para a câmera. */
    private static final int[] DIRT = {4, 1, 4};
    private static final String HINT = "block.wirelessautomate.resonance_core.projection.hint";
    /** Os dois vidros da janela que ficam faltando. */
    private static final int[][] MISSING_GLASS = {{2, 3, 4}, {3, 2, 4}};

    private E2eResonatorSteps() {
    }

    static void resonatorSteps(List<Step> list) {
        UUID playerId = Minecraft.getInstance().player.getUUID();
        BlockPos[] origin = new BlockPos[1];
        Vec3[] back = new Vec3[1];
        float[] backRot = new float[2];
        list.add(new Step("Ressonador: Vitrine pela metade", STEP_TIMEOUT_MS, () -> {
            Minecraft minecraft = Minecraft.getInstance();
            back[0] = minecraft.player.position();
            backRot[0] = minecraft.player.getYRot();
            backRot[1] = minecraft.player.getXRot();
            minecraft.options.hideGui = false;
            minecraft.options.keyShift.setDown(false);
            onServer(server -> {
                ServerPlayer player = server.getPlayerList().getPlayer(playerId);
                ServerLevel level = player.serverLevel();
                origin[0] = player.blockPosition().offset(12, 0, -14);
                player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
                clear(level, origin[0]);
                place(level, origin[0], true);
                level.setBlockAndUpdate(origin[0].offset(DIRT[0], DIRT[1], DIRT[2]), Blocks.DIRT.defaultBlockState());
                return null;
            });
        }, () -> {
            Minecraft minecraft = Minecraft.getInstance();
            return minecraft.level.getBlockState(core(origin[0])).getBlock() instanceof ResonanceCoreBlock
                    && minecraft.level.getBlockState(origin[0].offset(DIRT[0], DIRT[1], DIRT[2])).is(Blocks.DIRT)
                    && minecraft.player.getMainHandItem().isEmpty();
        }, () -> "Núcleo e terra no cliente, mão " + Minecraft.getInstance().player.getMainHandItem()));
        list.add(camera("Ressonador: câmera", playerId, () -> origin[0]));
        list.add(new Step("Ressonador: clique no Núcleo liga a projeção", STEP_TIMEOUT_MS, () -> useOn(core(origin[0])),
                () -> ResonatorProjection.isOn() && ResonatorProjection.result() != null,
                () -> "projeção " + ResonatorProjection.isOn()));
        list.add(new Step("Ressonador: a dica fica na action bar", STEP_TIMEOUT_MS, () -> {
        }, () -> stepTicks >= 10 && HINT.equals(overlayKey()), () -> "action bar com " + overlayKey()));
        list.add(new Step("Ressonador: contagem da projeção", STEP_TIMEOUT_MS, () -> {
        }, () -> {
            ProjectionDiff.Result result = ResonatorProjection.result();
            // a dica fica 40 ticks na action bar antes da contagem
            return stepTicks >= 50 && result != null && result.wrong() == 1 && result.missing().equals(Map.of(
                    Part.CASING, 47, Part.GLASS, 2, Part.ENERGY_PORT, 1, Part.CRYSTAL_PORT, 1, Part.TUNER, 1));
        }, () -> {
            ProjectionDiff.Result result = ResonatorProjection.result();
            return result == null ? "sem projeção" : "faltam " + result.missing() + ", errados " + result.wrong();
        }));
        list.add(capture("ressonador-projecao"));
        list.add(new Step("Ressonador: completar monta e desliga a projeção", STEP_TIMEOUT_MS, () -> onServer(server -> {
            place(server.overworld(), origin[0], false);
            return null;
        }), () -> {
            BlockState core = Minecraft.getInstance().level.getBlockState(core(origin[0]));
            return core.getBlock() instanceof ResonanceCoreBlock && core.getValue(ResonatorPartBlock.FORMED)
                    && !ResonatorProjection.isOn();
        }, () -> "Núcleo " + Minecraft.getInstance().level.getBlockState(core(origin[0])) + ", projeção "
                + ResonatorProjection.isOn()));
        list.add(DevEndToEnd.wait("Ressonador montado", 10));
        list.add(capture("ressonador-montado"));
        list.add(new Step("Ressonador: limpar", STEP_TIMEOUT_MS, () -> onServer(server -> {
            ServerPlayer player = server.getPlayerList().getPlayer(playerId);
            clear(player.serverLevel(), origin[0]);
            player.getAbilities().flying = false;
            player.onUpdateAbilities();
            player.teleportTo(player.serverLevel(), back[0].x, back[0].y, back[0].z, backRot[0], backRot[1]);
            return null;
        }), () -> Minecraft.getInstance().level.getBlockState(core(origin[0])).isAir()
                && Minecraft.getInstance().player.position().distanceTo(back[0]) < 0.5,
                () -> "jogador em " + Minecraft.getInstance().player.position()));
    }

    /** Põe as peças da primeira foto ({@code half}) ou as que faltam para completar a Vitrine (a terra vira Carcaça). */
    private static void place(ServerLevel level, BlockPos origin, boolean half) {
        for (int x = 0; x < 5; x++) {
            for (int y = 0; y < 5; y++) {
                for (int z = 0; z < 5; z++) {
                    if (VITRINE[x][y][z] != Part.AIR && halfBuilt(x, y, z) == half) {
                        level.setBlockAndUpdate(origin.offset(x, y, z), looseState(VITRINE[x][y][z], x, y, z));
                    }
                }
            }
        }
    }

    /**
     * A chave do texto na action bar agora, ou o texto cru se ele não é traduzível. O campo do {@link Gui} é privado
     * e só serve a este teste (o jogo de dev roda com os nomes da Mojang).
     */
    private static String overlayKey() {
        try {
            Field field = Gui.class.getDeclaredField("overlayMessageString");
            field.setAccessible(true);
            Component message = (Component) field.get(Minecraft.getInstance().gui);
            if (message == null) {
                return "nada";
            }
            return message.getContents() instanceof TranslatableContents contents ? contents.getKey()
                    : message.getString();
        } catch (ReflectiveOperationException e) {
            return "erro: " + e;
        }
    }

    private static BlockPos core(BlockPos origin) {
        return origin.offset(4, 2, 2);
    }

    /** O que a primeira foto já tem: o Núcleo, as carcaças das duas camadas de baixo e a janela menos dois vidros. */
    private static boolean halfBuilt(int x, int y, int z) {
        Part part = VITRINE[x][y][z];
        if (part == Part.CORE) {
            return true;
        }
        if (part == Part.CASING) {
            return y <= 1 && !isDirt(x, y, z);
        }
        if (part == Part.GLASS) {
            for (int[] gap : MISSING_GLASS) {
                if (gap[0] == x && gap[1] == y && gap[2] == z) {
                    return false;
                }
            }
            return true;
        }
        return false;
    }

    private static boolean isDirt(int x, int y, int z) {
        return x == DIRT[0] && y == DIRT[1] && z == DIRT[2];
    }

    /** O bloco solto de cada peça; o Núcleo e as peças de face nascem virados para fora, como a montagem os deixa. */
    private static BlockState looseState(Part part, int x, int y, int z) {
        Direction out = x == 0 ? Direction.WEST : x == 4 ? Direction.EAST : y == 0 ? Direction.DOWN
                : y == 4 ? Direction.UP : z == 0 ? Direction.NORTH : Direction.SOUTH;
        return switch (part) {
            case CASING -> ModBlocks.RESONATOR_CASING.get().defaultBlockState();
            case GLASS -> ModBlocks.RESONATOR_GLASS.get().defaultBlockState();
            case CORE -> ModBlocks.RESONANCE_CORE.get().defaultBlockState().setValue(ResonatorPartBlock.FACING, out);
            case ENERGY_PORT -> ModBlocks.RESONATOR_ENERGY_PORT.get().defaultBlockState().setValue(ResonatorPartBlock.FACING, out);
            case CRYSTAL_PORT -> ModBlocks.RESONATOR_CRYSTAL_PORT.get().defaultBlockState()
                    .setValue(ResonatorPartBlock.FACING, out);
            case TUNER -> ModBlocks.RESONATOR_TUNER.get().defaultBlockState().setValue(ResonatorPartBlock.FACING, out);
            case AIR, OTHER -> Blocks.AIR.defaultBlockState();
        };
    }

    /** Ar na caixa e numa volta de dois blocos em torno dela (o chão fica). */
    private static void clear(ServerLevel level, BlockPos origin) {
        for (BlockPos pos : BlockPos.betweenClosed(origin.offset(-2, 0, -2), origin.offset(6, 6, 6))) {
            if (!level.getBlockState(pos).isAir()) {
                level.setBlockAndUpdate(pos, Blocks.AIR.defaultBlockState());
            }
        }
    }

    /** O jogador voando a leste e ao sul da caixa, a uns 7 blocos do Núcleo, olhando o centro dela. */
    private static Step camera(String name, UUID playerId, java.util.function.Supplier<BlockPos> origin) {
        double[] cam = new double[3];
        float[] ang = new float[2];
        return new Step(name, STEP_TIMEOUT_MS, () -> {
            BlockPos o = origin.get();
            cam[0] = o.getX() + 8.5;
            cam[1] = o.getY() + 3.2;
            cam[2] = o.getZ() + 7.5;
            double ex = o.getX() + 2.5 - cam[0];
            double ey = o.getY() + 2.3 - (cam[1] + 1.62);
            double ez = o.getZ() + 2.5 - cam[2];
            ang[0] = (float) Math.toDegrees(Math.atan2(-ex, ez));
            ang[1] = (float) -Math.toDegrees(Math.atan2(ey, Math.sqrt(ex * ex + ez * ez)));
            Minecraft minecraft = Minecraft.getInstance();
            minecraft.player.setYRot(ang[0]);
            minecraft.player.setXRot(ang[1]);
            minecraft.player.yRotO = ang[0];
            minecraft.player.xRotO = ang[1];
            onServer(server -> {
                ServerPlayer player = server.getPlayerList().getPlayer(playerId);
                player.getAbilities().flying = true;
                player.onUpdateAbilities();
                player.teleportTo(player.serverLevel(), cam[0], cam[1], cam[2], ang[0], ang[1]);
                return null;
            });
        }, () -> Math.abs(Minecraft.getInstance().player.getXRot() - ang[1]) < 0.5f
                && Minecraft.getInstance().player.position().distanceToSqr(cam[0], cam[1], cam[2]) < 0.5,
                () -> "câmera em " + Minecraft.getInstance().player.position());
    }
}
