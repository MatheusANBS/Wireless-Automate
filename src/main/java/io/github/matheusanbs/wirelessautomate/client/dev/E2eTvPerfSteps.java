package io.github.matheusanbs.wirelessautomate.client.dev;

import static io.github.matheusanbs.wirelessautomate.client.dev.DevEndToEnd.*;
import static io.github.matheusanbs.wirelessautomate.client.dev.E2eSteps.*;
import static io.github.matheusanbs.wirelessautomate.client.dev.E2eWorld.*;

import io.github.matheusanbs.wirelessautomate.client.tv.render.TvCanvases;
import io.github.matheusanbs.wirelessautomate.network.NetworkSavedData;
import io.github.matheusanbs.wirelessautomate.network.ResourceType;
import io.github.matheusanbs.wirelessautomate.network.WaNetwork;
import io.github.matheusanbs.wirelessautomate.registry.ModBlocks;
import io.github.matheusanbs.wirelessautomate.tv.TvArea;
import io.github.matheusanbs.wirelessautomate.tv.TvBlock;
import io.github.matheusanbs.wirelessautomate.tv.TvBlockEntity;
import io.github.matheusanbs.wirelessautomate.tv.TvColor;
import io.github.matheusanbs.wirelessautomate.tv.TvFormat;
import io.github.matheusanbs.wirelessautomate.tv.TvLayout;
import io.github.matheusanbs.wirelessautomate.tv.TvPiece;
import io.github.matheusanbs.wirelessautomate.tv.TvPieceType;
import io.github.matheusanbs.wirelessautomate.tv.TvStyle;
import io.github.matheusanbs.wirelessautomate.tv.TvTemplates;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * O custo da Tela de Sinal no cliente (suíte opcional {@code tela-desempenho}; mede, não afirma): uma parede de
 * 128 × 64 blocos no céu, 40 blocos ao sul do jogador, com 8 Telas 32 × 32 (4 × 2, o bloco no centro de cada uma, para
 * todas ficarem a menos de 64 blocos da câmera) com o Painel de rede. A câmera fica a 34 blocos da parede, com o campo
 * de visão em 110 para as 8 caberem na foto. Mede o tempo de quadro sem nenhuma Tela (só a parede) e com as 8, e o
 * tempo de um redesenho da imagem (o primeiro de cada Tela e os seguintes, quando os números mudam). Escreve tudo no
 * {@code result.txt}; no fim, a parede, as Telas, o campo de visão e o voo voltam ao que eram.
 */
final class E2eTvPerfSteps {
    private static final int COLS = 4;
    private static final int ROWS = 2;
    private static final int SIZE = 32;
    private static final int LIFT = 20;
    private static final int SOUTH = 40;
    private static final double CAMERA_DISTANCE = 34;
    private static final int PERF_FOV = 110;
    /** Quanto o redesenho fica medido com as Telas no ar, para os números mudarem algumas vezes. */
    private static final int REPAINT_TICKS = 200;

    private static BlockPos base = BlockPos.ZERO;
    private static final boolean[] SAVED = new boolean[2];
    private static int savedFov;
    private static Vec3 savedPos = Vec3.ZERO;
    private static final float[] LOOK = {0f, 0f};
    private static String firstPaints = "";
    private static String laterPaints = "";

    private E2eTvPerfSteps() {
    }

    static void perfSteps(List<Step> list) {
        UUID playerId = Minecraft.getInstance().player.getUUID();
        list.add(close("Tela desempenho: fechar a tela de antes"));
        list.add(begin(playerId));
        list.add(wall(true));
        list.add(E2eTvSteps.view("tela-perf-sem-tela", playerId, E2eTvPerfSteps::eye, E2eTvPerfSteps::target));
        list.add(measure("tela nenhuma"));
        list.add(new Step("Tela desempenho: zerar antes das Telas", STEP_TIMEOUT_MS, E2eTvPerfSteps::resetPaints,
                () -> true, () -> ""));
        list.add(screens(playerId));
        list.add(new Step("Tela desempenho: as 8 imagens desenhadas", STEP_TIMEOUT_MS, () -> {
        }, () -> TvCanvases.paintsForTests >= COLS * ROWS && stepTicks >= 40, () -> {
            firstPaints = paints();
            return "primeiros redesenhos: " + firstPaints;
        }));
        list.add(E2eTvSteps.view("tela-perf-8-telas", playerId, E2eTvPerfSteps::eye, E2eTvPerfSteps::target));
        list.add(new Step("Tela desempenho: zerar os redesenhos", STEP_TIMEOUT_MS, E2eTvPerfSteps::resetPaints,
                () -> true, () -> ""));
        list.add(measure("tela 8 x 32x32 com o Painel"));
        list.add(new Step("Tela desempenho: redesenhos com os números mudando", 30_000, () -> {
        }, () -> {
            E2eRepairerSteps.look(LOOK);
            return stepTicks >= REPAINT_TICKS;
        }, () -> {
            laterPaints = paints();
            return "redesenhos depois: " + laterPaints;
        }));
        list.add(end(playerId));
        list.add(wall(false));
    }

    private static Step measure(String label) {
        return E2eResonatorSteps.frameTimeStep("Tela desempenho: " + label + " (tempo de quadro)", label, () -> {
        }, () -> E2eRepairerSteps.look(LOOK));
    }

    private static void resetPaints() {
        TvCanvases.paintsForTests = 0;
        TvCanvases.paintNanosForTests = 0;
        TvCanvases.maxPaintNanosForTests = 0;
        TvCanvases.maxFrameNanosForTests = 0;
        TvCanvases.maxFrameWorkNanosForTests = 0;
    }

    private static String paints() {
        long n = TvCanvases.paintsForTests;
        double avg = n == 0 ? 0 : TvCanvases.paintNanosForTests / (double) n / 1_000_000.0;
        return String.format(Locale.ROOT,
                "%d redesenho(s), média %.3f ms, maior %.3f ms; maior trabalho num quadro %.3f ms; maior quadro %.3f ms;"
                        + " memória de vídeo %.1f MB",
                n, avg, TvCanvases.maxPaintNanosForTests / 1_000_000.0,
                TvCanvases.maxFrameWorkNanosForTests / 1_000_000.0, TvCanvases.maxFrameNanosForTests / 1_000_000.0,
                TvCanvases.allocatedBytesForTests() / (1024.0 * 1024.0));
    }

    private static Vec3 target() {
        return new Vec3(base.getX(), base.getY() + LIFT + ROWS * SIZE / 2.0, base.getZ() + SOUTH - 1 + 13.0 / 16.0);
    }

    private static Vec3 eye() {
        return target().add(0, 0, -CAMERA_DISTANCE);
    }

    /** Sem a interface, o campo de visão largo; guarda o que havia, e o lugar do jogador. */
    private static Step begin(UUID playerId) {
        return new Step("Tela desempenho: sem interface e campo de visão 110", STEP_TIMEOUT_MS, () -> {
            Minecraft minecraft = Minecraft.getInstance();
            SAVED[0] = minecraft.options.hideGui;
            savedFov = minecraft.options.fov().get();
            minecraft.options.hideGui = true;
            minecraft.options.fov().set(PERF_FOV);
            onServer(server -> {
                ServerPlayer player = server.getPlayerList().getPlayer(playerId);
                SAVED[1] = player.getAbilities().flying;
                savedPos = player.position();
                base = player.blockPosition();
                return null;
            });
            resetPaints();
            TvCanvases.measureFramesForTests = true;
        }, () -> Minecraft.getInstance().options.fov().get() == PERF_FOV, () -> "campo de visão");
    }

    /** A parede de pedra (ou o ar, com {@code place} falso), só com aviso ao cliente. */
    private static Step wall(boolean place) {
        return new Step(place ? "Tela desempenho: parede" : "Tela desempenho: parede fora", STEP_TIMEOUT_MS,
                () -> onServer(server -> {
                    ServerLevel level = server.overworld();
                    BlockState state = place ? Blocks.STONE.defaultBlockState() : Blocks.AIR.defaultBlockState();
                    BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
                    for (int x = -COLS * SIZE / 2; x < COLS * SIZE / 2; x++) {
                        for (int y = 0; y < ROWS * SIZE; y++) {
                            pos.set(base.getX() + x, base.getY() + LIFT + y, base.getZ() + SOUTH);
                            level.setBlock(pos, state, Block.UPDATE_CLIENTS);
                        }
                    }
                    return null;
                }), () -> Minecraft.getInstance().level.getBlockState(corner()).is(Blocks.STONE) == place,
                () -> "canto da parede no cliente");
    }

    private static BlockPos corner() {
        return new BlockPos(base.getX() + COLS * SIZE / 2 - 1, base.getY() + LIFT + ROWS * SIZE - 1, base.getZ() + SOUTH);
    }

    /** O bloco de cada Tela, no centro da área dela (o {@code TvArea.Grow.CENTER}). */
    private static BlockPos anchor(int col, int row) {
        int x = base.getX() - COLS * SIZE / 2 + col * SIZE + SIZE / 2;
        int y = base.getY() + LIFT + row * SIZE + (SIZE - 1) / 2;
        return new BlockPos(x, y, base.getZ() + SOUTH - 1);
    }

    /** As 8 Telas, 32 × 32 a partir do centro, com o Painel da rede principal em Itens. */
    private static Step screens(UUID playerId) {
        return new Step("Tela desempenho: 8 Telas 32 × 32 com o Painel", STEP_TIMEOUT_MS, () -> onServer(server -> {
            ServerLevel level = server.overworld();
            WaNetwork network = NetworkSavedData.get(server).network(mainNetwork);
            TvArea area = TvArea.grown(SIZE, SIZE, TvArea.Grow.CENTER);
            for (int col = 0; col < COLS; col++) {
                for (int row = 0; row < ROWS; row++) {
                    BlockPos pos = anchor(col, row);
                    level.setBlockAndUpdate(pos, ModBlocks.SIGNAL_SCREEN.get().defaultBlockState()
                            .setValue(TvBlock.FACING, Direction.NORTH));
                    TvBlockEntity tv = (TvBlockEntity) level.getBlockEntity(pos);
                    tv.setOwner(playerId);
                    tv.setArea(area);
                    TvLayout panel = TvTemplates.networkPanel(network.name(), network.color(), network.id(),
                            ResourceType.ITEM, area);
                    List<TvPiece> pieces = new ArrayList<>(panel.pieces());
                    int h = area.pixelHeight();
                    pieces.add(new TvPiece(TvPieceType.GRAPH, area.pixelWidth() / 8, h * 5 / 8, area.pixelWidth() * 3 / 4,
                            h / 5, 0, "", pieces.get(2).binding(), TvFormat.DEFAULT,
                            TvStyle.DEFAULT.withColor(TvColor.theme(TvColor.HIGH))));
                    tv.setLayout(panel.withPieces(pieces));
                }
            }
            return null;
        }), () -> {
            for (int col = 0; col < COLS; col++) {
                for (int row = 0; row < ROWS; row++) {
                    if (!(Minecraft.getInstance().level.getBlockEntity(anchor(col, row)) instanceof TvBlockEntity tv)
                            || tv.area().width() != SIZE || tv.layout().pieces().isEmpty()) {
                        return false;
                    }
                }
            }
            return true;
        }, () -> "Telas no cliente");
    }

    /** As Telas fora e o campo de visão, a interface, o voo e o lugar do jogador de volta. */
    private static Step end(UUID playerId) {
        return new Step("Tela desempenho: tudo de volta", STEP_TIMEOUT_MS, () -> {
            Minecraft minecraft = Minecraft.getInstance();
            TvCanvases.measureFramesForTests = false;
            minecraft.options.hideGui = SAVED[0];
            minecraft.options.fov().set(savedFov);
            onServer(server -> {
                ServerLevel level = server.overworld();
                for (int col = 0; col < COLS; col++) {
                    for (int row = 0; row < ROWS; row++) {
                        level.setBlockAndUpdate(anchor(col, row), Blocks.AIR.defaultBlockState());
                    }
                }
                ServerPlayer player = server.getPlayerList().getPlayer(playerId);
                player.getAbilities().flying = SAVED[1];
                player.onUpdateAbilities();
                player.teleportTo(level, savedPos.x, savedPos.y, savedPos.z, 180f, 30f);
                return null;
            });
        }, () -> Minecraft.getInstance().level.getBlockState(anchor(0, 0)).isAir()
                && Minecraft.getInstance().player.getAbilities().flying == SAVED[1], () -> "Telas no cliente");
    }
}
