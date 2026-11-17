package io.github.matheusanbs.wirelessautomate.client.dev.vitrine;

import io.github.matheusanbs.wirelessautomate.block.RouterBlock;
import io.github.matheusanbs.wirelessautomate.block.RouterTier;
import io.github.matheusanbs.wirelessautomate.client.dev.DevEndToEnd.Step;
import io.github.matheusanbs.wirelessautomate.client.dev.E2eWorld;
import io.github.matheusanbs.wirelessautomate.client.dock.DockScreen;
import io.github.matheusanbs.wirelessautomate.dock.PatternDockBlockEntity;
import io.github.matheusanbs.wirelessautomate.network.PortMode;
import io.github.matheusanbs.wirelessautomate.network.ResourceType;
import io.github.matheusanbs.wirelessautomate.range.RemoteSide;
import io.github.matheusanbs.wirelessautomate.registry.ModBlocks;
import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import org.lwjgl.glfw.GLFW;

/**
 * A Doca de Padrões com o AE2 de verdade: um terminal de crafting num cabo, o Pattern Provider encostado na Doca Elite
 * (no Leste), o drive com o minério, a energia e a CPU, e nove altos-fornos vinculados ao Leste. A história: o terminal
 * abre, o cursor pede nove lingotes de ferro (a tela de quantidade, a de confirmação e o Start), a tela de status do
 * crafting mostra o pedido andando; corte para a Doca no mundo mandando o padrão às máquinas (os altos-fornos acendem), a
 * tela da Doca com o lado Leste trabalhando e, de volta ao terminal, os lingotes prontos.
 */
final class CenaDoca {
    static final String NOME = "doca";
    private static final Direction LADO = Direction.EAST;
    private static final Direction FACE_TERMINAL = Direction.SOUTH;
    private static final long IR_MS = 420;
    private static final long DEPOIS_MS = 260;
    private static final int PEDIDO = 9;

    private CenaDoca() {
    }

    static void passos(List<Step> lista, int indice) {
        Supplier<BlockPos> doca = () -> Palco.base(indice);
        Supplier<BlockPos> provedor = () -> doca.get().relative(LADO);
        Supplier<BlockPos> cabo = () -> doca.get().offset(1, 0, 1);
        Supplier<double[]> alvo = () -> new double[] {doca.get().getX() + 3.0, doca.get().getY() + 0.7, doca.get().getZ() + 0.6};
        lista.add(Palco.montar(NOME, nivel -> montar(nivel, doca.get()), () -> doca.get().offset(6, 0, 1)));
        lista.add(Passos.ate("a rede do AE2 ativa e com o padrão", () -> E2eWorld.onServer(servidor ->
                Ae2Rede.ativo(servidor.overworld(), provedor.get()) && Ae2Rede.pronta(servidor.overworld(), provedor.get(),
                        Items.IRON_INGOT))));
        lista.add(Passos.agir("minério na rede", () -> E2eWorld.onServer(servidor -> {
            Ae2Rede.estoque(servidor.overworld(), provedor.get(), Items.RAW_IRON, 64);
            return null;
        })));
        Palco.chao(lista, NOME, () -> doca.get().offset(3, -1, 0), 26);
        Supplier<double[][]> longe = () -> new double[][] {Camera.orbita(alvo.get(), 7.4, 40, 20), alvo.get()};
        Supplier<double[][]> perto = () -> new double[][] {Camera.orbita(alvo.get(), 5.6, 26, 16), alvo.get()};
        Supplier<double[][]> junto = () -> new double[][] {Camera.orbita(new double[] {doca.get().getX() + 0.5,
                doca.get().getY() + 0.5, doca.get().getZ() + 0.5}, 4.4, 24, 26), alvo.get()};
        lista.add(Passos.ate("câmera na Doca", () -> Camera.saltar(longe.get()[0], longe.get()[1]),
                () -> Camera.em(longe.get()[0])));
        lista.add(Passos.esperar(1200));
        lista.add(Gravador.iniciar(NOME));
        lista.add(Camera.mover(1800, t -> Camera.entre(longe.get(), perto.get(), t)));
        lista.add(Passos.esperar(500));
        pedido(lista, cabo);
        mundo(lista, perto, junto);
        telaDaDoca(lista, doca);
        lista.add(abrirTerminal(cabo));
        lista.add(Passos.ate("os lingotes prontos", () -> Ae2Telas.aberta("MEStorageScreen")
                && Ae2Telas.temEspaco(Items.IRON_INGOT)));
        lista.add(Cursor.centralizar());
        lista.add(Cursor.ir(() -> Ae2Telas.espaco(Items.IRON_INGOT), 500));
        lista.add(Passos.esperar(1700));
        lista.add(Gravador.parar());
    }

    /** No terminal: o lingote de ferro (craftável), a quantidade, a confirmação e o Start; depois a tela de status. */
    private static void pedido(List<Step> lista, Supplier<BlockPos> cabo) {
        lista.add(abrirTerminal(cabo));
        lista.add(Passos.ate("o terminal com o lingote craftável", () -> Ae2Telas.aberta("MEStorageScreen")
                && Ae2Telas.temEspaco(Items.IRON_INGOT)));
        lista.add(Cursor.centralizar());
        Cursor.tocar(lista, () -> Ae2Telas.espaco(Items.IRON_INGOT), GLFW.GLFW_MOUSE_BUTTON_MIDDLE, 600, DEPOIS_MS);
        lista.add(Passos.ate("a tela de quantidade", () -> Ae2Telas.aberta("CraftAmountScreen")));
        lista.add(Passos.agir("digitar a quantidade", () -> {
            var tela = Minecraft.getInstance().screen;
            for (int i = 0; i < 3; i++) {
                tela.keyPressed(GLFW.GLFW_KEY_BACKSPACE, 0, 0);
            }
            tela.charTyped(Character.forDigit(PEDIDO, 10), 0);
        }));
        lista.add(Passos.esperar(500));
        Cursor.tocar(lista, () -> Ae2Telas.botao("Next"), IR_MS, DEPOIS_MS);
        lista.add(Passos.ate("a tela de confirmação com o Start pronto", () -> Ae2Telas.aberta("CraftConfirmScreen")
                && Ae2Telas.temBotao("Start")));
        Cursor.tocar(lista, () -> Ae2Telas.botao("Start"), IR_MS, DEPOIS_MS);
        lista.add(Passos.ate("o pedido seguiu", () -> Minecraft.getInstance().screen == null
                || Ae2Telas.aberta("MEStorageScreen")));
        Cursor.tocar(lista, () -> Ae2Telas.botao("Crafting Status"), IR_MS, DEPOIS_MS);
        lista.add(Passos.ate("a tela de status do crafting", () -> Ae2Telas.aberta("CraftingStatusScreen")));
        lista.add(Passos.esperar(1700));
    }

    /** O corte para o mundo: a câmera vai da cena do terminal para junto da Doca (ao alcance do clique), com os altos-fornos acendendo. */
    private static void mundo(List<Step> lista, Supplier<double[][]> perto, Supplier<double[][]> junto) {
        lista.add(Passos.agir("fechar o terminal", () -> {
            if (Minecraft.getInstance().screen != null) {
                Minecraft.getInstance().screen.keyPressed(GLFW.GLFW_KEY_ESCAPE, 0, 0);
            }
        }));
        lista.add(Passos.ate("terminal fechado", () -> Minecraft.getInstance().screen == null));
        lista.add(Camera.mover(2800, t -> Camera.entre(perto.get(), junto.get(), t)));
        lista.add(Passos.esperar(2800));
    }

    /** A tela da Doca: a linha do Leste com as nove máquinas. */
    private static void telaDaDoca(List<Step> lista, Supplier<BlockPos> doca) {
        lista.add(Palco.usar("abrir a Doca", doca, () -> Minecraft.getInstance().screen instanceof DockScreen));
        lista.add(Cursor.centralizar());
        lista.add(Cursor.ir(() -> ((DockScreen) Minecraft.getInstance().screen).rowCenter(2), 600));
        lista.add(Passos.esperar(1500));
        lista.add(Passos.agir("fechar a tela", () -> Minecraft.getInstance().screen.keyPressed(GLFW.GLFW_KEY_ESCAPE, 0, 0)));
        lista.add(Passos.ate("tela fechada", () -> Minecraft.getInstance().screen == null));
        lista.add(Passos.esperar(400));
    }

    /** Abre o terminal pelo servidor (a parte tem de ser tocada na face dela), de novo a cada 1,5 s até a tela abrir. */
    private static Step abrirTerminal(Supplier<BlockPos> cabo) {
        long[] ultimo = new long[1];
        return Passos.ate("abrir o terminal", () -> ultimo[0] = 0, () -> {
            if (Ae2Telas.aberta("MEStorageScreen")) {
                return true;
            }
            long agora = System.nanoTime();
            if (agora - ultimo[0] > 1_500_000_000L) {
                ultimo[0] = agora;
                E2eWorld.onServer(servidor -> {
                    UUID id = Minecraft.getInstance().player.getUUID();
                    Ae2Rede.abrir(servidor.overworld(), cabo.get(), FACE_TERMINAL, servidor.getPlayerList().getPlayer(id));
                    return null;
                });
            }
            return false;
        });
    }

    /** A praça, a Doca Elite, a rede do AE2 e os nove altos-fornos (3 × 3) vinculados ao Leste, sem minério. */
    private static void montar(ServerLevel nivel, BlockPos b) {
        if (!Ae2Rede.presente()) {
            throw new IllegalStateException("a cena da Doca precisa do AE2 carregado");
        }
        Palco.praca(nivel, b.offset(3, -1, 1), 10, 8);
        nivel.setBlockAndUpdate(b, ModBlocks.PATTERN_DOCK.get().defaultBlockState().setValue(RouterBlock.TIER, RouterTier.ELITE));
        BlockPos provedor = b.relative(LADO);
        Ae2Rede.bloco(nivel, provedor, "pattern_provider");
        Ae2Rede.padrao(nivel, provedor, Items.RAW_IRON, Items.IRON_INGOT);
        Ae2Rede.cabo(nivel, b.offset(1, 0, 1), FACE_TERMINAL);
        Ae2Rede.bloco(nivel, b.offset(0, 0, 1), "drive");
        Ae2Rede.celula(nivel, b.offset(0, 0, 1));
        Ae2Rede.bloco(nivel, b.offset(2, 0, 1), "creative_energy_cell");
        Ae2Rede.bloco(nivel, b.offset(1, 1, 1), "1k_crafting_storage");
        var dock = (PatternDockBlockEntity) nivel.getBlockEntity(b);
        ResourceLocation id = BuiltInRegistries.BLOCK.getKey(Blocks.BLAST_FURNACE);
        dock.batchChanges(() -> {
            for (int x = 4; x <= 6; x++) {
                for (int z = -1; z <= 1; z++) {
                    BlockPos pos = b.offset(x, 0, z);
                    Fabrica.maquina(nivel, pos, Blocks.BLAST_FURNACE, 0);
                    dock.link(LADO.ordinal(), pos, id, -1);
                }
            }
        });
        // o perfil do lado: entra pelo topo do alto-forno, sai por baixo
        dock.side(LADO.ordinal()).profile().face(ResourceType.ITEM, RemoteSide.TOP).setMode(PortMode.INSERT);
        dock.side(LADO.ordinal()).profile().face(ResourceType.ITEM, RemoteSide.BOTTOM).setMode(PortMode.EXTRACT);
    }
}
