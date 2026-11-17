package io.github.matheusanbs.wirelessautomate.client.dev.vitrine;

import io.github.matheusanbs.wirelessautomate.client.dev.DevEndToEnd.Step;
import io.github.matheusanbs.wirelessautomate.client.dev.E2eWorld;
import io.github.matheusanbs.wirelessautomate.client.router.RouterScreen;
import io.github.matheusanbs.wirelessautomate.network.NetworkSavedData;
import io.github.matheusanbs.wirelessautomate.network.PortMode;
import io.github.matheusanbs.wirelessautomate.network.ResourceType;
import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import org.lwjgl.glfw.GLFW;

/**
 * O roteador preso numa fornalha, numa fábrica de fornalhas de ferro: a câmera chega perto, a tela abre, o cursor
 * clica numa face do modelo 3D, no botão dela (o modo vai a Extrai) e na rede da lista; depois o mundo, com as
 * fornalhas da rede acesas. O baú de entrada tira o ferro cru e as fornalhas ligadas à rede recebem; a fornalha do
 * foco, com o roteador solto, é a que o jogador configura.
 */
final class CenaRoteador {
    static final String NOME = "roteador";
    /** A face em que o roteador está preso (o de cima da fornalha): a que ele configura. */
    private static final Direction FACE = Direction.UP;
    private static final long IR_MS = 330;
    private static final long DEPOIS_MS = 200;
    private static final UUID[] REDE = new UUID[1];

    private CenaRoteador() {
    }

    static void passos(List<Step> lista, int indice) {
        Supplier<BlockPos> base = () -> Palco.base(indice);
        Supplier<BlockPos> foco = () -> base.get().offset(1, 0, 0);
        lista.add(Palco.montar(NOME, nivel -> montar(nivel, base.get()), () -> foco.get().above()));
        Palco.chao(lista, NOME, () -> base.get().offset(-2, -1, 3), 30);
        lista.add(Passos.ate("câmera chegando ao roteador", () -> Camera.saltar(afastada(base.get(), 1)[0], afastada(base.get(), 1)[1]),
                () -> Camera.em(afastada(base.get(), 1)[0])));
        lista.add(Passos.esperar(1800));
        lista.add(Gravador.iniciar(NOME));
        lista.add(Camera.mover(700, t -> afastada(base.get(), 1 - Passos.suave(t))));
        lista.add(Passos.esperar(750));
        lista.add(Palco.usar("abrir o roteador", () -> foco.get().above(),
                () -> Minecraft.getInstance().screen instanceof RouterScreen tela
                        && tela.getMenu().snapshot().pos().equals(foco.get().above())));
        lista.add(Cursor.centralizar());
        Cursor.tocar(lista, () -> tela().previewViewFaceCenter(FACE), IR_MS, DEPOIS_MS);
        Cursor.tocar(lista, () -> tela().previewFaceCenter(FACE), IR_MS, DEPOIS_MS);
        lista.add(Passos.ate("modo Extrai", () -> tela().getMenu().snapshot().face(ResourceType.ITEM, FACE)
                .mode() == PortMode.EXTRACT));
        Cursor.tocar(lista, () -> tela().previewNetworkCenter(), IR_MS, DEPOIS_MS);
        lista.add(Passos.ate("lista de redes", () -> tela().networkListShown()));
        Cursor.tocar(lista, () -> {
            var redes = tela().getMenu().snapshot().networks();
            for (int i = 0; i < redes.size(); i++) {
                if (redes.get(i).id().equals(REDE[0])) {
                    return tela().panel().networkRowCenter(i);
                }
            }
            throw new IllegalStateException("a rede da fábrica não está na lista");
        }, IR_MS, 500);
        lista.add(Passos.agir("fechar a tela", () -> tela().keyPressed(GLFW.GLFW_KEY_ESCAPE, 0, 0)));
        lista.add(Passos.ate("tela fechada", () -> Minecraft.getInstance().screen == null));
        lista.add(Camera.mover(1600, t -> Camera.entre(perto(base.get()), new double[][] {{base.get().getX() - 2.5,
                base.get().getY() + 3.2, base.get().getZ() + 9.5}, {base.get().getX() - 1.5, base.get().getY() + 1.3,
                base.get().getZ()}}, t)));
        lista.add(Passos.esperar(1700));
        lista.add(Gravador.parar());
    }

    /** A câmera da abertura: a uns 4 blocos do roteador do foco, de cima e de lado. */
    private static double[][] perto(BlockPos base) {
        return new double[][] {{base.getX() + 4.4, base.getY() + 2.5, base.getZ() + 3.6},
                {base.getX() + 1.5, base.getY() + 1.4, base.getZ() + 0.5}};
    }

    /** A câmera da abertura {@code k} (1 = começo) de um passo para trás e para cima da de perto. */
    private static double[][] afastada(BlockPos base, double k) {
        double[][] p = perto(base);
        return new double[][] {{p[0][0] + 1.8 * k, p[0][1] + 0.6 * k, p[0][2] + 2.0 * k}, p[1]};
    }

    private static RouterScreen tela() {
        return (RouterScreen) Minecraft.getInstance().screen;
    }

    /** O pátio de ardósia, a parede de fundo com lanternas, o baú de entrada, três fornalhas na rede, o foco e o baú de saída. */
    private static void montar(net.minecraft.server.level.ServerLevel nivel, BlockPos b) {
        var servidor = nivel.getServer();
        Palco.praca(nivel, b.offset(-2, -1, 3), 14, 9);
        Palco.caixa(nivel, b.offset(-10, 0, -3), b.offset(6, 2, -3), Blocks.DEEPSLATE_BRICKS.defaultBlockState());
        Palco.caixa(nivel, b.offset(-10, 3, -3), b.offset(6, 3, -3), Blocks.POLISHED_DEEPSLATE_SLAB.defaultBlockState());
        for (int x = -10; x <= 6; x += 4) {
            nivel.setBlock(b.offset(x, 4, -3), Blocks.LANTERN.defaultBlockState(), 2);
        }
        UUID dono = servidor.getPlayerList().getPlayers().get(0).getUUID();
        var redes = NetworkSavedData.get(servidor);
        UUID fabrica = redes.create(dono, "Smelting Line").id();
        redes.create(dono, "Storage");
        REDE[0] = fabrica;
        BlockPos entrada = b.offset(-8, 0, 0);
        nivel.setBlockAndUpdate(entrada, Blocks.CHEST.defaultBlockState());
        ((Container) nivel.getBlockEntity(entrada)).setItem(0, new ItemStack(Items.RAW_IRON, 64));
        Fabrica.roteador(servidor, entrada, fabrica, "Input", PortMode.EXTRACT);
        for (int x : new int[] {-5, -3, -1}) {
            Fabrica.fornalha(nivel, b.offset(x, 0, 0));
            Fabrica.roteador(servidor, b.offset(x, 0, 0), fabrica, "Furnace", PortMode.INSERT);
        }
        Fabrica.fornalha(nivel, b.offset(1, 0, 0));
        Fabrica.roteador(servidor, b.offset(1, 0, 0), null, "Furnace", PortMode.NONE);
        BlockPos saida = b.offset(4, 0, 0);
        nivel.setBlockAndUpdate(saida, Blocks.CHEST.defaultBlockState());
        Fabrica.roteador(servidor, saida, fabrica, "Output", PortMode.INSERT);
    }
}
