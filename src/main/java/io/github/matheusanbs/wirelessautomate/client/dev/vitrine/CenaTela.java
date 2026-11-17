package io.github.matheusanbs.wirelessautomate.client.dev.vitrine;

import io.github.matheusanbs.wirelessautomate.Config;
import io.github.matheusanbs.wirelessautomate.block.RouterBlock;
import io.github.matheusanbs.wirelessautomate.block.RouterTier;
import io.github.matheusanbs.wirelessautomate.client.dev.DevEndToEnd.StepFailure;
import io.github.matheusanbs.wirelessautomate.client.tv.TvScreen;
import io.github.matheusanbs.wirelessautomate.client.tv.editor.TvEditorTab;
import io.github.matheusanbs.wirelessautomate.client.tv.editor.TvLibraryClient;
import io.github.matheusanbs.wirelessautomate.client.dev.DevEndToEnd.Step;
import io.github.matheusanbs.wirelessautomate.network.NetworkSavedData;
import io.github.matheusanbs.wirelessautomate.network.PortMode;
import io.github.matheusanbs.wirelessautomate.network.ResourceType;
import io.github.matheusanbs.wirelessautomate.registry.ModBlocks;
import io.github.matheusanbs.wirelessautomate.storage.StorageBatteryBlockEntity;
import io.github.matheusanbs.wirelessautomate.storage.StorageKind;
import io.github.matheusanbs.wirelessautomate.tv.TvArea;
import io.github.matheusanbs.wirelessautomate.tv.TvBlock;
import io.github.matheusanbs.wirelessautomate.tv.TvBlockEntity;
import io.github.matheusanbs.wirelessautomate.tv.TvCodes;
import io.github.matheusanbs.wirelessautomate.tv.TvLibrary;
import io.github.matheusanbs.wirelessautomate.tv.TvPiece;
import io.github.matheusanbs.wirelessautomate.tv.TvPixelArt;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;

/**
 * A parede de Telas de Sinal ao fim da tarde: o painel ao vivo da Bateria (números e gráfico andando, porque um
 * roteador tira energia dela para um cofre sob o piso), a pixel art animada (a noite do sinal, 128 × 64 em 12 quadros) e
 * o ícone animado numa Tela quadrada. A arte vem do {@code scripts/curseforge/arte_vitrine.py}, em
 * {@code <WA_VITRINE>/entrada} (o {@code e2e.sh --vitrine} gera antes). A história: a câmera chega ao painel (só a metade esquerda), a tela abre no
 * Editar, o cursor insere o componente das estatísticas da Bateria da biblioteca, arrasta o cartão para o lugar,
 * salva e fecha; a câmera recua e a parede aparece completa e ao vivo.
 */
final class CenaTela {
    static final String NOME = "tela";
    private static final int ALTURA = 4;
    /** A âncora (a coluna da esquerda vista de frente) e a largura de cada Tela, no eixo x relativo à base. */
    private static final int PAINEL_X = 11;
    private static final int PAINEL_L = 7;
    private static final int ARTE_X = 3;
    private static final int ARTE_L = 8;
    private static final int ICONE_X = -6;
    private static final int ICONE_L = 4;
    private static final int BATERIA_X = 14;
    private static final long IR_MS = 380;
    private static final long DEPOIS_MS = 220;

    private CenaTela() {
    }

    static void passos(List<Step> lista, int indice) {
        Supplier<BlockPos> base = () -> Palco.base(indice);
        Supplier<BlockPos> painel = () -> base.get().offset(PAINEL_X, 1, 0);
        lista.add(Palco.montar(NOME, nivel -> montar(nivel, base.get()), () -> base.get().offset(PAINEL_X, 1, 0)));
        Palco.chao(lista, NOME, () -> base.get().offset(1, -1, -6), 30);
        Supplier<double[][]> aberta = () -> {
            BlockPos b = base.get();
            return new double[][] {{b.getX() + 1.5, b.getY() + 2.4, b.getZ() - 12.5}, {b.getX() + 1.5, b.getY() + 3.0, b.getZ() + 0.8}};
        };
        Supplier<double[][]> junto = () -> {
            BlockPos b = base.get();
            return new double[][] {{b.getX() + PAINEL_X + 3.5, b.getY() + 3.0, b.getZ() - 4.6},
                    {b.getX() + PAINEL_X + 3.5, b.getY() + 3.0, b.getZ() + 0.5}};
        };
        lista.add(Passos.ate("câmera na parede", () -> Camera.saltar(aberta.get()[0], aberta.get()[1]),
                () -> Camera.em(aberta.get()[0])));
        // meio minuto de números para o gráfico da Bateria e a pixel art terem história
        lista.add(Passos.esperar(20_000));
        lista.add(Gravador.iniciar(NOME));
        lista.add(Camera.mover(2800, t -> Camera.entre(aberta.get(), junto.get(), t)));
        lista.add(Passos.esperar(2600));
        lista.add(Palco.usar("abrir a Tela do painel", painel, () -> Minecraft.getInstance().screen instanceof TvScreen tela
                && tela.getMenu().pos().equals(painel.get())));
        lista.add(Cursor.centralizar());
        Cursor.tocar(lista, () -> tela().tabCenter(TvScreen.Tab.EDIT), IR_MS, DEPOIS_MS);
        Cursor.tocar(lista, () -> editor().probe().leftTabCenter("COMPONENTS"), IR_MS, DEPOIS_MS);
        lista.add(Passos.ate("componente na biblioteca", () -> TvLibraryClient.components().stream()
                .anyMatch(c -> c.name().equals(TelaPainel.ESTATISTICAS))));
        int[] antes = new int[1];
        lista.add(Passos.agir("peças de antes", () -> antes[0] = editor().draft().pieces().size()));
        Cursor.tocar(lista, () -> editor().probe().componentCenter(TelaPainel.ESTATISTICAS), IR_MS, DEPOIS_MS);
        lista.add(Passos.ate("componente inserido", () -> editor().draft().pieces().size() > antes[0]));
        // arrasta o cartão (e o grupo) até o lugar dele, à direita do medidor
        lista.add(Cursor.arrastar(() -> editor().probe().pieceCenter(antes[0]), () -> {
            TvPiece cartao = editor().draft().pieces().get(antes[0]);
            int[] de = editor().probe().pieceCenter(antes[0]);
            float escala = editor().probe().canvasScale();
            return new int[] {de[0] + Math.round((TelaPainel.ESTATISTICAS_X - cartao.x()) * escala),
                    de[1] + Math.round((TelaPainel.ESTATISTICAS_Y - cartao.y()) * escala)};
        }, 900));
        lista.add(Passos.esperar(DEPOIS_MS));
        Cursor.tocar(lista, () -> editor().probe().barCenter("SAVE"), IR_MS, 500);
        lista.add(Passos.ate("salvo", () -> !editor().dirty()));
        Cursor.tocar(lista, () -> tela().closeCenter(), IR_MS, DEPOIS_MS);
        lista.add(Passos.ate("tela fechada", () -> Minecraft.getInstance().screen == null));
        lista.add(Camera.mover(2400, t -> Camera.entre(junto.get(), aberta.get(), t)));
        lista.add(Passos.esperar(3400));
        lista.add(Gravador.parar());
    }

    private static TvScreen tela() {
        return (TvScreen) Minecraft.getInstance().screen;
    }

    private static TvEditorTab editor() {
        return tela().editor();
    }

    private static void montar(ServerLevel nivel, BlockPos b) {
        var servidor = nivel.getServer();
        UUID dono = servidor.getPlayerList().getPlayers().get(0).getUUID();
        Palco.praca(nivel, b.offset(1, -1, -6), 17, 12);
        Palco.caixa(nivel, b.offset(-10, 0, 1), b.offset(12, 5, 1), Blocks.QUARTZ_BRICKS.defaultBlockState());
        Palco.caixa(nivel, b.offset(-10, 6, 1), b.offset(12, 6, 1), Blocks.SMOOTH_QUARTZ_SLAB.defaultBlockState());
        for (int x : new int[] {12, 4, -5, -10}) {
            Palco.caixa(nivel, b.offset(x, 0, 0), b.offset(x, 5, 0), Blocks.QUARTZ_PILLAR.defaultBlockState());
            nivel.setBlock(b.offset(x, 6, 0), Blocks.CHISELED_QUARTZ_BLOCK.defaultBlockState(), 2);
            nivel.setBlock(b.offset(x, 7, 0), Blocks.LANTERN.defaultBlockState(), 2);
        }
        Palco.caixa(nivel, b.offset(-9, 0, 0), b.offset(11, 0, 0), Blocks.SMOOTH_QUARTZ_SLAB.defaultBlockState());
        TvPixelArt cena = arte("arte-vitrine.txt");
        TvPixelArt icone = arte("icone-vitrine.txt");
        TvLibrary biblioteca = TvLibrary.get(servidor);
        biblioteca.saveArt(dono, cena);
        biblioteca.saveArt(dono, icone);
        GlobalPos bateria = GlobalPos.of(nivel.dimension(), bateria(nivel, b, dono));
        tela(nivel, b, PAINEL_X, PAINEL_L, dono).link(bateria);
        tela(nivel, b, PAINEL_X, PAINEL_L, dono).setLayout(TelaPainel.painel(icone, bateria));
        biblioteca.saveComponent(dono, TelaPainel.estatisticas(bateria));
        tela(nivel, b, ARTE_X, ARTE_L, dono).setLayout(TelaPainel.arte(cena, ARTE_L, ALTURA));
        tela(nivel, b, ICONE_X, ICONE_L, dono).setLayout(TelaPainel.arte(icone, ICONE_L, ALTURA));
    }

    /** A Tela (coloca de novo, igual, se já está) com a âncora em {@code x}, crescendo para cima e para a direita. */
    private static TvBlockEntity tela(ServerLevel nivel, BlockPos b, int x, int largura, UUID dono) {
        BlockPos pos = b.offset(x, 1, 0);
        if (!(nivel.getBlockEntity(pos) instanceof TvBlockEntity)) {
            nivel.setBlockAndUpdate(pos, ModBlocks.SIGNAL_SCREEN.get().defaultBlockState().setValue(TvBlock.FACING,
                    Direction.NORTH));
            TvBlockEntity nova = (TvBlockEntity) nivel.getBlockEntity(pos);
            nova.setOwner(dono);
            nova.setArea(TvArea.grown(largura, ALTURA, TvArea.Grow.UP_RIGHT));
        }
        return (TvBlockEntity) nivel.getBlockEntity(pos);
    }

    /** A Bateria Esmeralda a 62%, ao lado da parede, e um roteador que tira dela para uma Ultimate sob o piso. */
    private static BlockPos bateria(ServerLevel nivel, BlockPos b, UUID dono) {
        var servidor = nivel.getServer();
        UUID rede = NetworkSavedData.get(servidor).create(dono, "Screen Wall").id();
        BlockPos bateria = b.offset(BATERIA_X, 0, 0);
        BlockPos cofre = bateria.below(3);
        for (BlockPos pos : List.of(bateria, cofre)) {
            nivel.setBlockAndUpdate(pos, ModBlocks.STORAGE.get(StorageKind.BATTERY).get().defaultBlockState()
                    .setValue(RouterBlock.TIER, pos == cofre ? RouterTier.ULTIMATE : RouterTier.EMERALD));
        }
        long capacidade = Config.storageCapacity(StorageKind.BATTERY, RouterTier.EMERALD);
        ((StorageBatteryBlockEntity) nivel.getBlockEntity(bateria)).store().insert(capacidade * 62 / 100, false);
        for (BlockPos maquina : List.of(bateria, cofre)) {
            nivel.setBlockAndUpdate(maquina.above(), ModBlocks.ROUTER.get().defaultBlockState()
                    .setValue(RouterBlock.FACING, Direction.UP).setValue(RouterBlock.TIER, RouterTier.EMERALD));
            var roteador = io.github.matheusanbs.wirelessautomate.client.dev.E2eWorld.router(servidor, maquina.above());
            roteador.setNetworkId(rede);
            roteador.setMode(ResourceType.ENERGY, Direction.UP, maquina == bateria ? PortMode.EXTRACT : PortMode.INSERT);
        }
        return bateria;
    }

    private static TvPixelArt arte(String arquivo) {
        try {
            TvCodes.Read<TvPixelArt> lida = TvCodes.readArt(Files.readString(Path.of(Vitrine.DIR, "entrada", arquivo)));
            if (lida.value() == null) {
                throw new StepFailure(arquivo + " não lê: " + lida.error());
            }
            return lida.value();
        } catch (Exception e) {
            throw new IllegalStateException("falta " + arquivo + " em " + Vitrine.DIR + "/entrada (scripts/curseforge/arte_vitrine.py)", e);
        }
    }
}
