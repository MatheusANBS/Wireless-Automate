package io.github.matheusanbs.wirelessautomate.client.dev.vitrine;

import io.github.matheusanbs.wirelessautomate.client.dev.DevEndToEnd.Check;
import io.github.matheusanbs.wirelessautomate.client.dev.DevEndToEnd.Step;
import io.github.matheusanbs.wirelessautomate.client.dev.E2eUi;
import io.github.matheusanbs.wirelessautomate.client.dev.E2eWorld;
import io.github.matheusanbs.wirelessautomate.client.dev.DevEndToEnd.StepFailure;
import java.util.ArrayList;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/**
 * O chão das cenas: cada uma tem a sua clareira, a 80 blocos da anterior no eixo x, montada no servidor, e a hora do
 * mundo é o fim de tarde (a luz baixa e dourada que o shaderpack aproveita).
 */
final class Palco {
    /** O pôr do sol do mundo, em ticks do dia. */
    private static final long FIM_DE_TARDE = 11_800;
    private static BlockPos origem;
    /** As praças montadas ({x, y, z do centro, meio em x, meio em z}), para conferir o piso célula a célula. */
    private static final List<int[]> PRACAS = new ArrayList<>();
    /** Os chunks forçados em volta da cena: o quadrado de 2 * 8 + 1 chunks de lado cobre as praças de até 64 blocos de meia largura. */
    private static final int RAIO_CHUNKS = 8;

    private Palco() {
    }

    /** A clareira da cena {@code indice}: o pé do jogador no começo, deslocado. */
    static BlockPos base(int indice) {
        return origem.offset(indice * 80, 0, 0);
    }

    /** Guarda de onde o jogador partiu, põe o fim de tarde e deixa a tela do jogo limpa. */
    static Step abrir() {
        return Passos.agir("abrir a vitrine", () -> {
            Minecraft jogo = Minecraft.getInstance();
            origem = jogo.player.blockPosition();
            jogo.options.hideGui = true;
            jogo.getToasts().clear();
            jogo.gui.getChat().clearMessages(false);
            E2eWorld.onServer(servidor -> {
                servidor.getPlayerList().getPlayers().forEach(p -> p.getInventory().clearContent());
                servidor.overworld().setDayTime(FIM_DE_TARDE);
                return null;
            });
        });
    }

    /**
     * Monta a cena no servidor: força os chunks em volta de {@code sentinela} (bloco posto em chunk que ainda não carregou
     * se perde), espera todos carregarem, roda {@code obra}, confere cada célula do piso das praças no servidor (falta
     * alguma: a vitrine falha) e espera o cliente ver o bloco {@code sentinela}.
     */
    static Step montar(String nome, Consumer<ServerLevel> obra, Supplier<BlockPos> sentinela) {
        int[] fase = new int[1];
        return Passos.ate("montar " + nome, () -> {
            fase[0] = 0;
            PRACAS.clear();
            E2eWorld.onServer(servidor -> {
                forcar(servidor.overworld(), sentinela.get(), true);
                return null;
            });
        }, () -> {
            if (fase[0] == 0) {
                fase[0] = E2eWorld.onServer(servidor -> carregados(servidor.overworld(), sentinela.get())) ? 1 : 0;
                return false;
            }
            if (fase[0] == 1) {
                E2eWorld.onServer(servidor -> {
                    servidor.getPlayerList().getPlayers().forEach(p -> p.getInventory().clearContent());
                    obra.accept(servidor.overworld());
                    return null;
                });
                fase[0] = 2;
                int faltam = E2eWorld.onServer(servidor -> faltas(servidor.overworld()));
                if (faltam > 0) {
                    throw new StepFailure("faltam " + faltam + " células do piso no servidor");
                }
            }
            return !Minecraft.getInstance().level.getBlockState(sentinela.get()).isAir();
        });
    }

    /** Liga ou desliga o carregamento forçado dos chunks num quadrado de {@link #RAIO_CHUNKS} em volta de {@code centro}. */
    private static void forcar(ServerLevel nivel, BlockPos centro, boolean ligado) {
        for (int x = -RAIO_CHUNKS; x <= RAIO_CHUNKS; x++) {
            for (int z = -RAIO_CHUNKS; z <= RAIO_CHUNKS; z++) {
                nivel.setChunkForced((centro.getX() >> 4) + x, (centro.getZ() >> 4) + z, ligado);
            }
        }
    }

    private static boolean carregados(ServerLevel nivel, BlockPos centro) {
        for (int x = -RAIO_CHUNKS; x <= RAIO_CHUNKS; x++) {
            for (int z = -RAIO_CHUNKS; z <= RAIO_CHUNKS; z++) {
                if (nivel.getChunkSource().getChunkNow((centro.getX() >> 4) + x, (centro.getZ() >> 4) + z) == null) {
                    return false;
                }
            }
        }
        return true;
    }

    /** Quantas células do piso das praças estão vazias no servidor. */
    private static int faltas(ServerLevel nivel) {
        return faltas(nivel::getBlockState);
    }

    /** Quantas células do piso das praças estão vazias no cliente. */
    private static int faltasCliente() {
        return faltas(Minecraft.getInstance().level::getBlockState);
    }

    private static int faltas(Function<BlockPos, BlockState> leitor) {
        int faltam = 0;
        for (int[] p : PRACAS) {
            for (int dx = -p[3]; dx <= p[3]; dx++) {
                for (int dz = -p[4]; dz <= p[4]; dz++) {
                    faltam += leitor.apply(new BlockPos(p[0] + dx, p[1], p[2] + dz)).isAir() ? 1 : 0;
                }
            }
        }
        return faltam;
    }

    /** Põe {@code estado} em toda a caixa entre {@code a} e {@code b}, sem avisar os vizinhos (só os clientes). */
    static void caixa(ServerLevel nivel, BlockPos a, BlockPos b, BlockState estado) {
        for (BlockPos p : BlockPos.betweenClosed(a, b)) {
            nivel.setBlock(p, estado, Block.UPDATE_CLIENTS);
        }
    }

    /**
     * A praça: do bloco {@code centro} (a altura é a do piso) {@code meioX} e {@code meioZ} blocos para cada lado, ou
     * seja {@code 2 * meio + 1} de largura, simétrica em torno do bloco do meio. Pela distância de xadrez {@code k}
     * ao centro: a moldura de ardósia polida nas duas últimas fileiras, o friso de arenito liso na seguinte e, por
     * dentro, ardósia polida lisa (sem desenho, para o GIF comprimir); nos quatro cantos, um poste com lanterna.
     */
    static void praca(ServerLevel nivel, BlockPos centro, int meioX, int meioZ) {
        PRACAS.add(new int[] {centro.getX(), centro.getY(), centro.getZ(), meioX, meioZ});
        for (int dx = -meioX; dx <= meioX; dx++) {
            for (int dz = -meioZ; dz <= meioZ; dz++) {
                int folga = Math.min(meioX - Math.abs(dx), meioZ - Math.abs(dz));
                BlockState estado = folga <= 1 ? Blocks.POLISHED_DEEPSLATE.defaultBlockState()
                        : folga == 2 ? Blocks.SMOOTH_SANDSTONE.defaultBlockState()
                        : Blocks.POLISHED_DEEPSLATE.defaultBlockState();
                nivel.setBlock(centro.offset(dx, 0, dz), estado, Block.UPDATE_CLIENTS);
            }
        }
        for (int sx : new int[] {-1, 1}) {
            for (int sz : new int[] {-1, 1}) {
                BlockPos poste = centro.offset(sx * (meioX - 1), 1, sz * (meioZ - 1));
                nivel.setBlock(poste, Blocks.POLISHED_DEEPSLATE_WALL.defaultBlockState(), Block.UPDATE_CLIENTS);
                nivel.setBlock(poste.above(), Blocks.POLISHED_DEEPSLATE_WALL.defaultBlockState(), Block.UPDATE_CLIENTS);
                nivel.setBlock(poste.above(2), Blocks.LANTERN.defaultBlockState(), Block.UPDATE_CLIENTS);
            }
        }
    }

    /** Sobe a câmera {@code altura} blocos sobre {@code centro}, olhando reto para baixo, e tira {@code chao-<cena>.png}. */
    static void chao(List<Step> lista, String cena, Supplier<BlockPos> centro, double altura) {
        Supplier<double[]> olho = () -> new double[] {centro.get().getX() + 0.5, centro.get().getY() + altura,
                centro.get().getZ() + 0.5};
        lista.add(Passos.ate("chão visto de cima", () -> Camera.saltar(olho.get(), new double[] {olho.get()[0],
                centro.get().getY(), olho.get()[2]}), () -> Camera.em(olho.get())));
        lista.add(Passos.ate("o piso inteiro no cliente", () -> faltasCliente() == 0));
        lista.add(Passos.esperar(1500));
        lista.add(Gravador.foto("chao-" + cena));
    }

    /** Clique direito no bloco {@code pos}, repetido a cada 1,5 s até {@code aberto} valer (o servidor às vezes ainda não vê o jogador). */
    static Step usar(String nome, Supplier<BlockPos> pos, Check aberto) {
        long[] ultimo = new long[1];
        return Passos.ate(nome, () -> ultimo[0] = 0, () -> {
            if (aberto.done()) {
                return true;
            }
            long agora = System.nanoTime();
            if (agora - ultimo[0] > 1_500_000_000L) {
                ultimo[0] = agora;
                E2eUi.useOn(pos.get());
            }
            return false;
        });
    }
}
