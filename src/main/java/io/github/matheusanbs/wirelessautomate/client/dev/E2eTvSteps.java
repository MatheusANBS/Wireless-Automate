package io.github.matheusanbs.wirelessautomate.client.dev;

import static io.github.matheusanbs.wirelessautomate.client.dev.DevEndToEnd.*;
import static io.github.matheusanbs.wirelessautomate.client.dev.E2eSteps.*;
import static io.github.matheusanbs.wirelessautomate.client.dev.E2eUi.*;
import static io.github.matheusanbs.wirelessautomate.client.dev.E2eWorld.*;

import io.github.matheusanbs.wirelessautomate.GuiTheme;
import io.github.matheusanbs.wirelessautomate.client.GuiText;
import io.github.matheusanbs.wirelessautomate.client.tv.TvClientValues;
import io.github.matheusanbs.wirelessautomate.client.tv.TvScreen;
import io.github.matheusanbs.wirelessautomate.network.ResourceType;
import io.github.matheusanbs.wirelessautomate.registry.ModBlocks;
import io.github.matheusanbs.wirelessautomate.repairer.RepairerBlock;
import io.github.matheusanbs.wirelessautomate.tv.TvArea;
import io.github.matheusanbs.wirelessautomate.tv.TvAreaCheck;
import io.github.matheusanbs.wirelessautomate.tv.TvBinding;
import io.github.matheusanbs.wirelessautomate.tv.TvBlock;
import io.github.matheusanbs.wirelessautomate.tv.TvBlockEntity;
import io.github.matheusanbs.wirelessautomate.tv.TvFormat;
import io.github.matheusanbs.wirelessautomate.tv.TvLayout;
import io.github.matheusanbs.wirelessautomate.tv.TvPiece;
import io.github.matheusanbs.wirelessautomate.tv.TvPieceType;
import io.github.matheusanbs.wirelessautomate.tv.TvStyle;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.WallTorchBlock;
import net.minecraft.world.phys.Vec3;
import org.lwjgl.glfw.GLFW;

/**
 * A Tela de Sinal no e2e: uma parede atrás do jogador com a Tela (para o norte), uma tocha na parede e, ao lado, uma
 * Tela 1 × 1 vazia junto de um Reparador. As máquinas e a rede são as dos Fluxos ({@link E2eFlowsSteps#machines}).
 * Pede 4 × 2 com ↗ e aplica (o servidor confere), mostra o problema da tocha com 6 × 2, aplica o Painel da rede em
 * Itens, espera um número maior que zero chegar e fotografa a Tela no mundo de frente, de longe e de lado, a pequena
 * em 3/4 e, com um layout de branco opaco, branco a 50% e cinza 50% opaco, a transparência de perto.
 */
final class E2eTvSteps {
    /** O olho a 4 blocos da frente da Tela; a frente fica a 13/16 do bloco, como a forma do bloco. */
    private static final double WORLD_DISTANCE = 4.0;
    private static final double FRONT = 13.0 / 16.0;
    private static final int SETTLE_TICKS = 20;
    private static final long VALUES_TIMEOUT_MS = 60_000;

    private static BlockPos origin = BlockPos.ZERO;
    private static BlockPos tv = BlockPos.ZERO;
    private static BlockPos small = BlockPos.ZERO;
    private static BlockPos repairer = BlockPos.ZERO;
    private static BlockPos torch = BlockPos.ZERO;
    private static final boolean[] SAVED = new boolean[2];
    private static Vec3 savedPos = Vec3.ZERO;

    private E2eTvSteps() {
    }

    static void tvSteps(List<Step> list) {
        UUID playerId = Minecraft.getInstance().player.getUUID();
        list.add(close("Tela: fechar a tela de antes"));
        list.add(E2eFlowsSteps.machines("Tela: máquinas e roteadores dos Fluxos", true));
        list.add(build(playerId));
        list.add(open("Tela: abrir"));
        sizeSteps(list, "");
        contentSteps(list, "");
        E2eVariants.escuro(list, () -> {
            list.add(E2eDarkSteps.themeClick("Tela: escuro", GuiTheme.DARK));
            list.add(tab("Tela: aba Tamanho no escuro", TvScreen.Tab.SIZE));
            shot(list, "tela-tamanho-escuro");
            problemSteps(list, "-escuro");
            list.add(tab("Tela: aba Conteúdo no escuro", TvScreen.Tab.CONTENT));
            shot(list, "tela-conteudo-escuro");
            searchSteps(list, "-escuro");
            list.add(E2eDarkSteps.themeClick("Tela: claro de novo", GuiTheme.LIGHT));
        });
        list.add(close("Tela: fechar"));
        list.add(values());
        list.add(hideGui(playerId, true));
        list.add(view("tela-bloco", playerId, () -> blockTarget().add(-1.6, 1.0, -2.3), E2eTvSteps::blockTarget));
        list.add(view("tela-mundo-lado", playerId, () -> worldTarget().add(-4.5, 0.3, -1.2), E2eTvSteps::worldTarget));
        list.add(view("tela-mundo-longe", playerId, () -> worldTarget().add(0, 2, -16), E2eTvSteps::worldTarget));
        // a última foto deixa o jogador de frente para a Tela, ao alcance do clique que a abre em pt
        list.add(view("tela-mundo", playerId, E2eTvSteps::worldEye, E2eTvSteps::worldTarget));
        list.add(hideGui(playerId, false));
        E2eVariants.pt(list, () -> {
            list.add(language("pt_br"));
            list.add(open("Tela: abrir em pt"));
            list.add(new Step("Tela: 4 × 2 aberta em pt", STEP_TIMEOUT_MS, () -> {
            }, () -> screen().requested().equals(new TvArea(4, 2, 0, 0)) && screen().problem().ok(),
                    () -> "pedido " + screen().requested()));
            shot(list, "tela-tamanho-pt");
            problemSteps(list, "-pt");
            list.add(tab("Tela: aba Conteúdo em pt", TvScreen.Tab.CONTENT));
            shot(list, "tela-conteudo-pt");
            searchSteps(list, "-pt");
            list.add(close("Tela: fechar em pt"));
            list.add(hideGui(playerId, true));
            list.add(view("tela-mundo-pt", playerId, E2eTvSteps::worldEye, E2eTvSteps::worldTarget));
            list.add(hideGui(playerId, false));
            list.add(language("en_us"));
        });
        list.add(halfAlpha());
        list.add(hideGui(playerId, true));
        list.add(view("tela-transparencia", playerId, () -> smallTarget().add(0, 0, -1.3), E2eTvSteps::smallTarget));
        list.add(hideGui(playerId, false));
        list.add(cleanUp(playerId));
        list.add(E2eFlowsSteps.machines("Tela: tirar os roteadores dos Fluxos", false));
    }

    /** Pede 4 × 2 com ↗ pelos ‹ ›, fotografa, aplica e confere a área no servidor e no cliente. */
    private static void sizeSteps(List<Step> list, String suffix) {
        list.add(tab("Tela: aba Tamanho" + suffix, TvScreen.Tab.SIZE));
        list.add(new Step("Tela: pedir 4 × 2 com ↗" + suffix, STEP_TIMEOUT_MS, () -> {
            TvScreen s = screen();
            for (int i = 0; i < 3; i++) {
                clickAt(s.stepCenter(true, true));
            }
            clickAt(s.stepCenter(false, true));
            clickAt(s.growCenter(TvArea.Grow.UP_RIGHT));
        }, () -> screen().requested().equals(new TvArea(4, 2, 0, 0)) && screen().requestedGrow() == TvArea.Grow.UP_RIGHT
                && screen().problem().ok(), () -> "pedido " + screen().requested() + ", problema " + screen().problem()));
        shot(list, "tela-tamanho" + suffix);
        list.add(new Step("Tela: aplicar 4 × 2" + suffix, STEP_TIMEOUT_MS, () -> clickAt(screen().applyCenter()),
                () -> area(true).equals(new TvArea(4, 2, 0, 0)) && area(false).equals(new TvArea(4, 2, 0, 0)),
                () -> "servidor " + area(true) + ", cliente " + area(false)));
        problemSteps(list, suffix);
    }

    /** 6 × 2 alcança a tocha: a prévia acende a célula dela em vermelho e o Aplicar desliga; depois volta a 4 × 2. */
    private static void problemSteps(List<Step> list, String suffix) {
        list.add(tab("Tela: aba Tamanho para o problema" + suffix, TvScreen.Tab.SIZE));
        list.add(new Step("Tela: 6 × 2 bate na tocha" + suffix, STEP_TIMEOUT_MS, () -> {
            TvScreen s = screen();
            clickAt(s.stepCenter(true, true));
            clickAt(s.stepCenter(true, true));
        }, () -> screen().requested().width() == 6 && screen().problem().kind() == TvAreaCheck.Kind.BLOCKED
                && torch.equals(screen().problem().pos()), () -> "pedido " + screen().requested() + ", problema "
                + screen().problem()));
        shot(list, "tela-tamanho-problema" + suffix);
        list.add(new Step("Tela: de volta a 4 × 2" + suffix, STEP_TIMEOUT_MS, () -> {
            TvScreen s = screen();
            clickAt(s.stepCenter(true, false));
            clickAt(s.stepCenter(true, false));
        }, () -> screen().requested().width() == 4 && screen().problem().ok(), () -> "pedido " + screen().requested()));
    }

    /** A rede dos Fluxos e Itens na aba Conteúdo, o Painel aplicado e conferido no servidor. */
    private static void contentSteps(List<Step> list, String suffix) {
        list.add(tab("Tela: aba Conteúdo" + suffix, TvScreen.Tab.CONTENT));
        list.add(new Step("Tela: escolher a rede dos Fluxos e Itens" + suffix, STEP_TIMEOUT_MS, () -> {
            TvScreen s = screen();
            UUID network = E2eFlowsSteps.flowsNetworkId();
            int[] row = network == null ? null : s.networkCenter(network);
            if (row == null) {
                throw new StepFailure("a rede dos Fluxos não está à vista na lista");
            }
            clickAt(row);
            clickAt(s.typeCenter(ResourceType.ITEM));
        }, () -> E2eFlowsSteps.flowsNetworkId().equals(screen().selectedNetwork())
                && screen().selectedType() == ResourceType.ITEM, () -> "rede " + screen().selectedNetwork()
                + ", tipo " + screen().selectedType()));
        list.add(new Step("Tela: aplicar o Painel de rede" + suffix, STEP_TIMEOUT_MS,
                () -> clickAt(screen().templateCenter()), () -> {
                    TvBinding first = onServer(server -> {
                        TvBlockEntity entity = (TvBlockEntity) server.overworld().getBlockEntity(tv);
                        return entity.layout().bindings().isEmpty() ? null : entity.layout().bindings().get(0);
                    });
                    return first != null && first.network().equals(E2eFlowsSteps.flowsNetworkId())
                            && first.type() == ResourceType.ITEM
                            && E2eFlowsSteps.flowsNetworkId().equals(screen().currentNetwork());
                }, () -> "atual na tela " + screen().currentNetwork()));
        shot(list, "tela-conteudo" + suffix);
        searchSteps(list, suffix);
    }

    /**
     * A busca das redes: "flu" deixa só a rede dos Fluxos (o trecho em coral e o rodapé "1 de N"), Enter a escolhe e Esc
     * limpa a busca sem fechar a tela.
     */
    private static void searchSteps(List<Step> list, String suffix) {
        list.add(new Step("Tela: buscar \"flu\"" + suffix, STEP_TIMEOUT_MS, () -> type("flu"),
                () -> "flu".equals(screen().networkQuery()) && screen().networksShown() == 1
                        && screen().networkCenter(E2eFlowsSteps.flowsNetworkId()) != null,
                () -> "busca \"" + screen().networkQuery() + "\", " + screen().networksShown() + " rede(s)"));
        shot(list, "tela-conteudo-busca" + suffix);
        list.add(new Step("Tela: Enter escolhe e Esc limpa a busca" + suffix, STEP_TIMEOUT_MS, () -> {
            TvScreen s = screen();
            s.keyPressed(GLFW.GLFW_KEY_ENTER, 0, 0);
            s.keyPressed(GLFW.GLFW_KEY_ESCAPE, 0, 0);
        }, () -> Minecraft.getInstance().screen instanceof TvScreen s && s.networkQuery().isEmpty()
                && E2eFlowsSteps.flowsNetworkId().equals(s.selectedNetwork()),
                () -> "tela " + describe(Minecraft.getInstance().screen)));
    }

    /**
     * A parede de pedra (x + 2 a x + 12 do lugar do jogador, 9 blocos para o sul, 5 de altura), a Tela em x + 10, um
     * acima do chão, a tocha em x + 5 na altura da segunda linha, a Tela 1 × 1 em x + 3 no chão e o Reparador em x + 4.
     * O jogador vai para a frente da Tela, olhando para ela, com a mão vazia; o lugar de antes volta no fim.
     */
    private static Step build(UUID playerId) {
        return new Step("Tela: parede, Telas, tocha e Reparador", STEP_TIMEOUT_MS, () -> onServer(server -> {
            ServerPlayer player = server.getPlayerList().getPlayer(playerId);
            ServerLevel level = player.serverLevel();
            savedPos = player.position();
            origin = chestA.offset(1, 0, 3);
            tv = origin.offset(10, 1, 8);
            small = origin.offset(3, 0, 8);
            repairer = origin.offset(4, 0, 8);
            torch = origin.offset(5, 2, 8);
            clear(level);
            for (int x = 2; x <= 12; x++) {
                for (int y = 0; y <= 4; y++) {
                    level.setBlockAndUpdate(origin.offset(x, y, 9), Blocks.STONE.defaultBlockState());
                }
            }
            level.setBlockAndUpdate(tv, ModBlocks.SIGNAL_SCREEN.get().defaultBlockState()
                    .setValue(TvBlock.FACING, Direction.NORTH));
            level.setBlockAndUpdate(small, ModBlocks.SIGNAL_SCREEN.get().defaultBlockState()
                    .setValue(TvBlock.FACING, Direction.NORTH));
            ((TvBlockEntity) level.getBlockEntity(tv)).setOwner(playerId);
            ((TvBlockEntity) level.getBlockEntity(small)).setOwner(playerId);
            level.setBlockAndUpdate(repairer, ModBlocks.CRYSTAL_REPAIRER.get().defaultBlockState()
                    .setValue(RepairerBlock.FACING, Direction.NORTH));
            level.setBlockAndUpdate(torch, Blocks.WALL_TORCH.defaultBlockState()
                    .setValue(WallTorchBlock.FACING, Direction.NORTH));
            player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
            player.teleportTo(level, origin.getX() + 8.5, origin.getY(), origin.getZ() + 5.5, 0f, 10f);
            return null;
        }), () -> Minecraft.getInstance().level.getBlockEntity(tv) instanceof TvBlockEntity
                && Minecraft.getInstance().level.getBlockEntity(small) instanceof TvBlockEntity
                && Minecraft.getInstance().level.getBlockState(torch).is(Blocks.WALL_TORCH)
                && Minecraft.getInstance().player.position().distanceToSqr(origin.getX() + 8.5, origin.getY(),
                        origin.getZ() + 5.5) < 0.01, () -> "Telas no cliente");
    }

    /** Tira o que a suíte pôs (sem largar itens) e devolve o jogador ao lugar de antes. */
    private static Step cleanUp(UUID playerId) {
        return new Step("Tela: tirar a parede e voltar", STEP_TIMEOUT_MS, () -> onServer(server -> {
            ServerPlayer player = server.getPlayerList().getPlayer(playerId);
            ServerLevel level = player.serverLevel();
            E2eRepairerSteps.remove(level, repairer);
            clear(level);
            for (int x = 2; x <= 12; x++) {
                for (int y = 0; y <= 4; y++) {
                    level.setBlockAndUpdate(origin.offset(x, y, 9), Blocks.AIR.defaultBlockState());
                }
            }
            player.teleportTo(level, savedPos.x, savedPos.y, savedPos.z, 180f, 30f);
            return null;
        }), () -> Minecraft.getInstance().level.getBlockState(tv).isAir()
                && Minecraft.getInstance().level.getBlockState(origin.offset(2, 0, 9)).isAir(), () -> "parede no cliente");
    }

    /** O ar na frente da parede, onde a suíte põe as coisas. */
    private static void clear(ServerLevel level) {
        for (int x = 2; x <= 12; x++) {
            for (int y = 0; y <= 4; y++) {
                level.setBlockAndUpdate(origin.offset(x, y, 8), Blocks.AIR.defaultBlockState());
            }
        }
    }

    private static Step open(String name) {
        return new Step(name, STEP_TIMEOUT_MS, () -> {
            if (Minecraft.getInstance().screen != null) {
                throw new StepFailure("ainda há uma tela aberta: " + describe(Minecraft.getInstance().screen));
            }
            useOn(tv);
        }, () -> Minecraft.getInstance().screen instanceof TvScreen s && s.getMenu().pos().equals(tv),
                () -> "tela " + describe(Minecraft.getInstance().screen));
    }

    private static Step tab(String name, TvScreen.Tab wanted) {
        return new Step(name, STEP_TIMEOUT_MS, () -> {
            if (screen().currentTab() != wanted) {
                clickAt(screen().tabCenter(wanted));
            }
        }, () -> screen().currentTab() == wanted, () -> "aba " + screen().currentTab());
    }

    /** A foto da tela aberta, sem nenhum texto cortado (o {@code GuiText} não abreviou nada). */
    private static void shot(List<Step> list, String name) {
        list.add(capture(name));
        list.add(new Step(name + ": nenhum texto cortado", STEP_TIMEOUT_MS, () -> {
        }, () -> stepTicks >= 2 && GuiText.clipCount() == 0, () -> GuiText.clipCount() + " texto(s) cortado(s)"));
    }

    /** Espera a Tela receber do servidor um número maior que zero (os itens dos Fluxos andando). */
    private static Step values() {
        return new Step("Tela: um número maior que zero chegou", VALUES_TIMEOUT_MS, () -> {
        }, () -> TvClientValues.maxForTests(tv.asLong()) > 0, () -> "maior número " + TvClientValues.maxForTests(tv.asLong()));
    }

    /**
     * Na Tela pequena, em cima o branco opaco, embaixo à esquerda o branco a 50% e à direita o cinza {@code 0x80}
     * opaco: com o alfa certo, o branco a 50% sobre o vidro escuro fica perto do cinza, não mais escuro que ele.
     */
    private static Step halfAlpha() {
        return new Step("Tela: layout de transparência na Tela pequena", STEP_TIMEOUT_MS, () -> onServer(server -> {
            TvBlockEntity entity = (TvBlockEntity) server.overworld().getBlockEntity(small);
            entity.setLayout(TvLayout.of(List.of(shape(0, 0, 32, 10, 0xFFFFFFFF), shape(0, 10, 16, 22, 0x80FFFFFF),
                    shape(16, 10, 16, 22, 0xFF808080))));
            return null;
        }), () -> Minecraft.getInstance().level.getBlockEntity(small) instanceof TvBlockEntity entity
                && entity.layout().pieces().size() == 3, () -> "layout no cliente");
    }

    private static TvPiece shape(int x, int y, int w, int h, int background) {
        return new TvPiece(TvPieceType.SHAPE, x, y, w, h, 0, "", Optional.empty(), TvFormat.DEFAULT,
                new TvStyle(TvStyle.DEFAULT.color(), background, 0, 0, TvStyle.DEFAULT.size(), TvStyle.DEFAULT.align(),
                        true, ""));
    }

    /** Liga ({@code hide}) ou desliga a interface e o voo das fotos do mundo, guardando o que havia. */
    private static Step hideGui(UUID playerId, boolean hide) {
        return new Step(hide ? "Tela: sem interface" : "Tela: interface de volta", STEP_TIMEOUT_MS, () -> {
            if (hide) {
                SAVED[0] = Minecraft.getInstance().options.hideGui;
                SAVED[1] = onServer(server -> server.getPlayerList().getPlayer(playerId).getAbilities().flying);
                Minecraft.getInstance().options.hideGui = true;
                return;
            }
            Minecraft.getInstance().options.hideGui = SAVED[0];
            onServer(server -> {
                ServerPlayer player = server.getPlayerList().getPlayer(playerId);
                player.getAbilities().flying = SAVED[1];
                player.onUpdateAbilities();
                return null;
            });
        }, () -> hide || Minecraft.getInstance().player.getAbilities().flying == SAVED[1], () -> "voo");
    }

    /** O centro da área 4 × 2 na frente da Tela: as colunas vão de x + 7 a x + 10 e as linhas de y + 1 a y + 2. */
    private static Vec3 worldTarget() {
        return new Vec3(tv.getX() - 1.0, tv.getY() + 1.0, tv.getZ() + FRONT);
    }

    private static Vec3 worldEye() {
        return worldTarget().add(0, 0, -WORLD_DISTANCE);
    }

    /** O centro da frente da Tela pequena. */
    private static Vec3 smallTarget() {
        return new Vec3(small.getX() + 0.5, small.getY() + 0.5, small.getZ() + FRONT);
    }

    /** Entre a Tela pequena e o Reparador, na metade da altura. */
    private static Vec3 blockTarget() {
        return new Vec3(small.getX() + 1.0, small.getY() + 0.5, small.getZ() + 0.6);
    }

    /**
     * O jogador voando com o olho em {@code eye}, olhando {@code target}; a janela é real e o mouse gira a câmera, então
     * o ângulo volta a cada tick até a foto.
     */
    static Step view(String file, UUID playerId, Supplier<Vec3> eye, Supplier<Vec3> target) {
        float[] ang = new float[2];
        Vec3[] feet = new Vec3[1];
        return new Step("captura " + file, STEP_TIMEOUT_MS, () -> {
            Vec3 e = eye.get();
            Vec3 d = target.get().subtract(e);
            ang[0] = (float) Math.toDegrees(Math.atan2(-d.x, d.z));
            ang[1] = (float) Math.toDegrees(Math.atan2(-d.y, Math.sqrt(d.x * d.x + d.z * d.z)));
            feet[0] = e.subtract(0, E2eRepairerSteps.EYE_HEIGHT, 0);
            Minecraft.getInstance().mouseHandler.releaseMouse();
            E2eRepairerSteps.look(ang);
            onServer(server -> {
                ServerPlayer player = server.getPlayerList().getPlayer(playerId);
                player.getAbilities().flying = true;
                player.onUpdateAbilities();
                player.teleportTo(player.serverLevel(), feet[0].x, feet[0].y, feet[0].z, ang[0], ang[1]);
                return null;
            });
        }, () -> {
            E2eRepairerSteps.look(ang);
            if (stepTicks < SETTLE_TICKS || Minecraft.getInstance().player.position().distanceToSqr(feet[0]) > 0.01) {
                return false;
            }
            capture(Minecraft.getInstance(), file);
            return true;
        }, () -> "câmera em " + Minecraft.getInstance().player.position());
    }

    private static TvArea area(boolean server) throws Exception {
        if (server) {
            return onServer(s -> ((TvBlockEntity) s.overworld().getBlockEntity(tv)).area());
        }
        return Minecraft.getInstance().level.getBlockEntity(tv) instanceof TvBlockEntity entity ? entity.area() : null;
    }

    private static void clickAt(int[] point) throws StepFailure {
        click(screen(), point[0], point[1]);
    }

    static TvScreen screen() throws StepFailure {
        if (Minecraft.getInstance().screen instanceof TvScreen s) {
            return s;
        }
        throw new StepFailure("sem a tela da Tela: " + describe(Minecraft.getInstance().screen));
    }
}
