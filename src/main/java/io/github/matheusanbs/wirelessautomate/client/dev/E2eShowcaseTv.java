package io.github.matheusanbs.wirelessautomate.client.dev;

import static io.github.matheusanbs.wirelessautomate.client.dev.DevEndToEnd.*;
import static io.github.matheusanbs.wirelessautomate.client.dev.E2eShowcaseWorld.*;
import static io.github.matheusanbs.wirelessautomate.client.dev.E2eSteps.close;
import static io.github.matheusanbs.wirelessautomate.client.dev.E2eWorld.*;

import io.github.matheusanbs.wirelessautomate.block.RouterBlock;
import io.github.matheusanbs.wirelessautomate.block.RouterBlockEntity;
import io.github.matheusanbs.wirelessautomate.block.RouterTier;
import io.github.matheusanbs.wirelessautomate.Config;
import io.github.matheusanbs.wirelessautomate.client.tv.TvClientValues;
import io.github.matheusanbs.wirelessautomate.client.tv.TvScreen;
import io.github.matheusanbs.wirelessautomate.client.tv.editor.TvLibraryClient;
import io.github.matheusanbs.wirelessautomate.client.tv.editor.TvPixelEditor;
import io.github.matheusanbs.wirelessautomate.client.tv.image.TvImageUploader;
import io.github.matheusanbs.wirelessautomate.client.tv.render.TvPictureTextures;
import io.github.matheusanbs.wirelessautomate.network.NetworkSavedData;
import io.github.matheusanbs.wirelessautomate.network.PortMode;
import io.github.matheusanbs.wirelessautomate.network.ResourceType;
import io.github.matheusanbs.wirelessautomate.network.WaNetwork;
import io.github.matheusanbs.wirelessautomate.registry.ModBlocks;
import io.github.matheusanbs.wirelessautomate.storage.StorageBatteryBlockEntity;
import io.github.matheusanbs.wirelessautomate.storage.StorageKind;
import io.github.matheusanbs.wirelessautomate.tv.TvArea;
import io.github.matheusanbs.wirelessautomate.tv.TvBlock;
import io.github.matheusanbs.wirelessautomate.tv.TvBlockEntity;
import io.github.matheusanbs.wirelessautomate.tv.TvCodes;
import io.github.matheusanbs.wirelessautomate.tv.TvImageHash;
import io.github.matheusanbs.wirelessautomate.tv.TvImageStore;
import io.github.matheusanbs.wirelessautomate.tv.TvLibrary;
import io.github.matheusanbs.wirelessautomate.tv.TvPixelArt;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/**
 * A Tela de Sinal na vitrine do CurseForge: no jardim ao sul, no fim do caminho, uma parede de quartzo com três Telas
 * viradas para a casa. Da esquerda para a direita, vistas de frente: o painel ao vivo ({@link E2eShowcaseTvPanel}, 7 × 4,
 * lendo a Bateria ao lado da parede, o Baú da galeria e a rede da linha de minério), a pixel art animada (8 × 4, a arte
 * de 128 × 64 em 2×) e o GIF de cor livre (4 × 4). A arte, o ícone do painel e o GIF saem dos scripts
 * {@code scripts/curseforge/arte_vitrine.py} e {@code gif_vitrine.py}, que gravam em {@code <WA_SHOWCASE>/entrada}; a arte
 * entra pelo mesmo leitor do Importar ({@link TvCodes#readArt}) e o GIF pelo mesmo envio do botão Enviar arquivo
 * ({@link TvImageUploader}).
 *
 * <p>Fotos: a parede inteira ({@code s24-tela-parede}), cada Tela de perto ({@code s24b} a {@code s24d}), o GIF da parede
 * tocando ({@link E2eShowcaseTvGif}), o editor de pixel art aberto na arte, com a faixa de quadros e o papel vegetal
 * ({@code s24e-tela-editor}), e a aba Artes &gt; Imagens com o GIF tocando na lista ({@code s24f-tela-imagens}). Roda na
 * vitrine inteira ou sozinho, com {@code WA_SHOWCASE_ONLY=tela}.
 */
final class E2eShowcaseTv {
    /** A parede em z 20 (relativo à base) e as Telas em z 19, viradas para o norte, de y 1 a 4. */
    private static final int WALL_Z = 20;
    private static final int ROW_Y = 1;
    private static final int HEIGHT = 4;
    /** A parede vai de x -10 a 12; colunas em 12, 4, -5 e -10. Vista do norte, o leste fica à esquerda. */
    private static final int WALL_WEST = -10;
    private static final int WALL_EAST = 12;
    private static final int[] PILLARS = {12, 4, -5, -10};
    /** A âncora de cada Tela é a coluna da esquerda vista de frente (o maior x), e a área cresce para o oeste. */
    private static final int PANEL_X = 11;
    private static final int ART_X = 3;
    private static final int GIF_X = -6;
    private static final int PANEL_W = 7;
    private static final int ART_W = 8;
    private static final int GIF_W = 4;
    /** A Bateria que o painel lê, ao lado da parede, com um roteador que tira para um cofre sob o piso. */
    private static final int BATTERY_X = 14;
    /** A frente das Telas, no sistema do {@link E2eShowcase#showLook} (o centro do bloco é o inteiro). */
    private static final double FRONT_Z = WALL_Z - 1 + 13.0 / 16.0 - 0.5;
    private static final double MID_Y = ROW_Y + HEIGHT / 2.0;
    static final String ART_NAME = "Signal Night";
    static final String GIF_NAME = "Signal Crystal";

    private static String gifHash = "";
    private static final String[] UPLOAD = {""};

    private E2eShowcaseTv() {
    }

    static void steps(List<Step> list) {
        list.add(new Step("Tela: fechar a tela", STEP_TIMEOUT_MS, () -> {
            Minecraft minecraft = Minecraft.getInstance();
            if (minecraft.screen != null) {
                minecraft.screen.onClose();
            }
        }, () -> Minecraft.getInstance().screen == null, () -> "tela " + describe(Minecraft.getInstance().screen)));
        list.add(build());
        list.add(upload());
        list.add(gifLayout());
        // o gráfico mostra o último minuto: um minuto de números antes das fotos, com a entrada da linha de minério
        // reposta a cada 15 s (sem isso ela esvazia em um minuto e a vazão cai a zero)
        list.add(new Step("Tela: números no painel e o gráfico com história", 120_000, () -> {
        }, () -> {
            if (stepTicks % 300 == 1) {
                onServer(server -> {
                    refillShowcase(server.overworld());
                    return null;
                });
            }
            return TvClientValues.maxForTests(pos(PANEL_X).asLong()) > 0 && stepTicks >= 1300;
        },
                () -> "maior número " + TvClientValues.maxForTests(pos(PANEL_X).asLong())));
        photos(list);
        E2eShowcaseTvGif.steps(list, FRONT_Z, MID_Y);
        editorSteps(list);
    }

    static BlockPos pos(int x) {
        return showBase.offset(x, ROW_Y, WALL_Z - 1);
    }

    /** As fotos paradas, sem a interface: a parede inteira e cada Tela de perto. */
    private static void photos(List<Step> list) {
        list.add(E2eShowcase.showLook("Tela: câmera na parede", 2.0, 1.4, 9.5, 2.0, MID_Y, FRONT_Z, false));
        list.add(DevEndToEnd.wait("Tela: parede", 20));
        list.add(capture("s24-tela-parede"));
        double art = ART_X - (ART_W - 1) / 2.0;
        list.add(E2eShowcase.showLook("Tela: câmera na arte", art, 1.4, 13.5, art, MID_Y, FRONT_Z, false));
        list.add(DevEndToEnd.wait("Tela: arte", 20));
        list.add(capture("s24b-tela-arte"));
        double panel = PANEL_X - (PANEL_W - 1) / 2.0;
        list.add(E2eShowcase.showLook("Tela: câmera no painel", panel, 1.4, 14.5, panel, MID_Y, FRONT_Z, false));
        list.add(DevEndToEnd.wait("Tela: painel", 20));
        list.add(capture("s24c-tela-painel"));
        double gif = GIF_X - (GIF_W - 1) / 2.0;
        list.add(E2eShowcase.showLook("Tela: câmera no GIF", gif, 1.4, 15.5, gif, MID_Y, FRONT_Z, false));
        list.add(DevEndToEnd.wait("Tela: GIF", 20));
        list.add(capture("s24d-tela-gif"));
    }

    /**
     * O editor de pixel art aberto na arte da vitrine, no quadro 5 (o papel vegetal mostra o 4 por baixo), e a parte
     * Imagens da aba Artes com o GIF escolhido.
     */
    private static void editorSteps(List<Step> list) {
        BlockPos[] art = new BlockPos[1];
        list.add(E2eShowcase.showLook("Tela: perto da arte", ART_X - 1.5, 0.5, WALL_Z - 3.5, ART_X - 1.5, MID_Y,
                FRONT_Z, true));
        list.add(new Step("Tela: abrir a Tela da arte", STEP_TIMEOUT_MS, () -> {
            art[0] = pos(ART_X);
            E2eUi.useOn(art[0]);
        }, () -> Minecraft.getInstance().screen instanceof TvScreen s && s.getMenu().pos().equals(art[0]),
                () -> "tela " + describe(Minecraft.getInstance().screen)));
        list.add(new Step("Tela: aba Editar e a aba Artes", STEP_TIMEOUT_MS, () -> {
            clickAt(screen().tabCenter(TvScreen.Tab.EDIT));
        }, () -> {
            if (screen().currentTab() != TvScreen.Tab.EDIT || stepTicks < 4) {
                return false;
            }
            clickAt(screen().editor().probe().leftTabCenter("ARTS"));
            return pixel().artRowCenter(ART_NAME) != null;
        }, () -> "artes " + TvLibraryClient.arts().size()));
        list.add(new Step("Tela: Editar a arte", STEP_TIMEOUT_MS, () -> {
            clickAt(pixel().artRowCenter(ART_NAME));
            clickAt(pixel().artsButtonCenter("EDIT"));
        }, () -> pixel().isOpen() && pixel().session() != null && pixel().session().canvas().frames() == 12,
                () -> "aberto " + pixel().isOpen()));
        list.add(new Step("Tela: o quadro 5, com o 4 no papel vegetal", STEP_TIMEOUT_MS,
                () -> clickAt(pixel().frameCenter(4)),
                () -> pixel().session().canvas().current() == 4 && pixel().session().frames().onionFrame() == 3
                        && stepTicks >= 6,
                () -> "quadro " + pixel().session().canvas().current()));
        list.add(capture("s24e-tela-editor"));
        list.add(new Step("Tela: Cancelar o editor de pixel art", STEP_TIMEOUT_MS,
                () -> clickAt(pixel().buttonCenter("CANCEL")), () -> !pixel().isOpen(), () -> "aberto"));
        list.add(new Step("Tela: a parte Imagens com o GIF", STEP_TIMEOUT_MS,
                () -> clickAt(screen().editor().probe().librarySegmentCenter("PICTURES")),
                () -> {
                    int[] row = screen().editor().probe().pictureRowCenter(GIF_NAME);
                    if (row == null) {
                        return false;
                    }
                    if (stepTicks == 3) {
                        clickAt(row);
                    }
                    return stepTicks >= 20;
                }, () -> "imagens " + TvLibraryClient.pictures().size()));
        list.add(capture("s24f-tela-imagens"));
        list.add(close("Tela: fechar"));
    }

    /**
     * A parede, as três Telas com área e dono, a Bateria e o cofre, a arte e o ícone na biblioteca do jogador e os
     * layouts da arte e do painel. O GIF entra depois, pelo envio do cliente.
     */
    private static Step build() {
        Path input = Path.of(SHOWCASE, "entrada");
        return new Step("Tela: montar a parede", STEP_TIMEOUT_MS, () -> {
            for (String file : List.of("arte-vitrine.txt", "icone-vitrine.txt", "sinal.gif")) {
                if (!Files.exists(input.resolve(file))) {
                    throw new StepFailure("falta " + input.resolve(file) + ": rode scripts/curseforge/arte_vitrine.py"
                            + " e gif_vitrine.py antes");
                }
            }
            TvPixelArt scene = art(input.resolve("arte-vitrine.txt"));
            TvPixelArt icon = art(input.resolve("icone-vitrine.txt"));
            UUID playerId = Minecraft.getInstance().player.getUUID();
            onServer(server -> {
                ServerPlayer player = server.getPlayerList().getPlayer(playerId);
                player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
                ServerLevel level = player.serverLevel();
                wall(level);
                BlockPos battery = battery(server, level, playerId);
                TvLibrary library = TvLibrary.get(server);
                library.saveArt(playerId, scene);
                library.saveArt(playerId, icon);
                place(level, PANEL_X, PANEL_W, playerId);
                place(level, ART_X, ART_W, playerId);
                place(level, GIF_X, GIF_W, playerId);
                TvBlockEntity panel = entity(level, PANEL_X);
                GlobalPos[] links = {GlobalPos.of(level.dimension(), battery),
                        GlobalPos.of(level.dimension(), showStorage.get(0))};
                for (GlobalPos link : links) {
                    panel.link(link);
                }
                WaNetwork ores = NetworkSavedData.get(server).byName(playerId, "Smelting Line");
                panel.setLayout(E2eShowcaseTvPanel.layout(icon, links[0], links[1],
                        ores == null ? null : ores.id()));
                entity(level, ART_X).setLayout(E2eShowcaseTvPanel.art(scene, ART_W, HEIGHT));
                return null;
            });
        }, () -> {
            for (int x : new int[] {PANEL_X, ART_X, GIF_X}) {
                if (!(Minecraft.getInstance().level.getBlockEntity(pos(x)) instanceof TvBlockEntity entity)
                        || entity.area().cells() == 1) {
                    return false;
                }
            }
            return clientEntity(ART_X).layout().images().size() == 1 && clientEntity(PANEL_X).layout().images().size() == 1;
        }, () -> "Telas no cliente");
    }

    private static TvPixelArt art(Path file) throws Exception {
        TvCodes.Read<TvPixelArt> read = TvCodes.readArt(Files.readString(file));
        if (read.value() == null) {
            throw new StepFailure(file.getFileName() + " não lê: " + read.error());
        }
        return read.value();
    }

    /** O GIF pelo envio em partes do cliente, como o botão Enviar arquivo: entra no armazém e na biblioteca. */
    private static Step upload() {
        return new Step("Tela: enviar o GIF pelo cliente", 60_000, () -> {
            byte[] gif = Files.readAllBytes(Path.of(SHOWCASE, "entrada", "sinal.gif"));
            gifHash = TvImageHash.of(gif);
            UPLOAD[0] = "";
            TvImageUploader.upload(gif, GIF_NAME, outcome -> UPLOAD[0] = outcome.ok() ? "ok"
                    : String.valueOf(outcome.refusal()));
        }, () -> "ok".equals(UPLOAD[0]) && onServer(server -> TvImageStore.get(server).contains(gifHash)),
                () -> "resposta " + UPLOAD[0]);
    }

    private static Step gifLayout() {
        return new Step("Tela: o GIF na Tela", 30_000, () -> onServer(server -> {
            entity(server.overworld(), GIF_X).setLayout(E2eShowcaseTvPanel.gif(
                    TvImageStore.get(server).picture(gifHash), GIF_W, HEIGHT));
            return null;
        }), () -> TvPictureTextures.readyForTests(gifHash) && TvPictureTextures.shownFrameForTests(gifHash) >= 0
                && stepTicks >= 10, () -> "GIF pronto " + TvPictureTextures.readyForTests(gifHash));
    }

    /**
     * A parede de tijolo de quartzo com as colunas salientes (lanterna no alto), um peitoril sob as Telas e a praça
     * na frente, de quartzo e calcita em xadrez com borda de ardósia, ligada ao caminho da casa.
     */
    private static void wall(ServerLevel level) {
        for (int x = WALL_WEST - 2; x <= BATTERY_X + 2; x++) {
            for (int z = 10; z <= WALL_Z + 1; z++) {
                for (int y = 0; y <= 8; y++) {
                    put(level, x, y, z, Blocks.AIR.defaultBlockState());
                }
                boolean border = x == WALL_WEST - 2 || x == BATTERY_X + 2 || z == 10 || z == WALL_Z + 1;
                boolean path = z == 10 && Math.abs(x) <= 1;
                BlockState floor = path ? Blocks.SMOOTH_QUARTZ.defaultBlockState()
                        : border ? Blocks.POLISHED_DEEPSLATE.defaultBlockState()
                        : Math.floorMod(x + z, 2) == 0 ? Blocks.SMOOTH_QUARTZ.defaultBlockState()
                        : Blocks.CALCITE.defaultBlockState();
                put(level, x, -1, z, floor);
            }
        }
        for (int x = WALL_WEST; x <= WALL_EAST; x++) {
            for (int y = 0; y <= 5; y++) {
                put(level, x, y, WALL_Z, Blocks.QUARTZ_BRICKS.defaultBlockState());
            }
            put(level, x, 6, WALL_Z, Blocks.SMOOTH_QUARTZ_SLAB.defaultBlockState());
            put(level, x, 0, WALL_Z - 1, Blocks.SMOOTH_QUARTZ_SLAB.defaultBlockState());
        }
        for (int x : PILLARS) {
            for (int z = WALL_Z - 1; z <= WALL_Z; z++) {
                for (int y = 0; y <= 5; y++) {
                    put(level, x, y, z, Blocks.QUARTZ_PILLAR.defaultBlockState());
                }
                put(level, x, 6, z, Blocks.CHISELED_QUARTZ_BLOCK.defaultBlockState());
            }
            put(level, x, 7, WALL_Z - 1, Blocks.LANTERN.defaultBlockState());
        }
        for (int[] light : new int[][] {{WALL_WEST - 2, 10}, {BATTERY_X + 2, 10}, {WALL_WEST - 2, WALL_Z + 1},
                {BATTERY_X + 2, WALL_Z + 1}}) {
            put(level, light[0], -1, light[1], Blocks.SEA_LANTERN.defaultBlockState());
        }
        for (int[] pot : new int[][] {{WALL_WEST - 1, WALL_Z - 1}, {13, WALL_Z - 3}, {WALL_WEST - 1, 11},
                {BATTERY_X + 1, 11}}) {
            put(level, pot[0], 0, pot[1], Blocks.POTTED_FLOWERING_AZALEA.defaultBlockState());
        }
    }

    /**
     * A Bateria Esmeralda que o painel lê, num pedestal ao lado da parede, cheia até 62%, com um roteador que tira
     * para uma Bateria Ultimate sob o piso, numa rede própria: o painel mostra o que sai de verdade.
     */
    private static BlockPos battery(MinecraftServer server, ServerLevel level, UUID playerId) {
        WaNetwork network = NetworkSavedData.get(server).create(playerId, "Screen Wall");
        BlockPos battery = showBase.offset(BATTERY_X, 0, WALL_Z - 1);
        BlockPos vault = battery.below(3);
        for (BlockPos pos : List.of(battery, vault)) {
            level.setBlockAndUpdate(pos, ModBlocks.STORAGE.get(StorageKind.BATTERY).get().defaultBlockState()
                    .setValue(RouterBlock.TIER, pos == vault ? RouterTier.ULTIMATE : RouterTier.EMERALD));
        }
        long capacity = Config.storageCapacity(StorageKind.BATTERY, RouterTier.EMERALD);
        ((StorageBatteryBlockEntity) level.getBlockEntity(battery)).store().insert(capacity * 62 / 100, false);
        router(server, level, battery, RouterTier.EMERALD, "Wall Battery", network.id(), PortMode.EXTRACT);
        router(server, level, vault, RouterTier.ULTIMATE, "Wall Vault", network.id(), PortMode.INSERT);
        return battery;
    }

    private static void router(MinecraftServer server, ServerLevel level, BlockPos machine, RouterTier tier, String name,
            UUID network, PortMode mode) {
        BlockPos pos = machine.above();
        level.setBlockAndUpdate(pos, ModBlocks.ROUTER.get().defaultBlockState().setValue(RouterBlock.FACING, Direction.UP)
                .setValue(RouterBlock.TIER, tier));
        RouterBlockEntity router = E2eWorld.router(server, pos);
        router.setNetworkId(network);
        router.setName(name);
        router.setMode(ResourceType.ENERGY, Direction.UP, mode);
    }

    /** A Tela com a âncora na coluna da esquerda vista de frente, crescendo para cima e para a direita. */
    private static void place(ServerLevel level, int x, int width, UUID owner) {
        BlockPos pos = showBase.offset(x, ROW_Y, WALL_Z - 1);
        level.setBlockAndUpdate(pos, ModBlocks.SIGNAL_SCREEN.get().defaultBlockState()
                .setValue(TvBlock.FACING, Direction.NORTH));
        TvBlockEntity entity = (TvBlockEntity) level.getBlockEntity(pos);
        entity.setOwner(owner);
        entity.setArea(TvArea.grown(width, HEIGHT, TvArea.Grow.UP_RIGHT));
    }

    private static void put(ServerLevel level, int x, int y, int z, BlockState state) {
        level.setBlock(showBase.offset(x, y, z), state, Block.UPDATE_CLIENTS);
    }

    private static TvBlockEntity entity(ServerLevel level, int x) {
        return (TvBlockEntity) level.getBlockEntity(pos(x));
    }

    private static TvBlockEntity clientEntity(int x) {
        return (TvBlockEntity) Minecraft.getInstance().level.getBlockEntity(pos(x));
    }

    private static TvScreen screen() throws StepFailure {
        if (Minecraft.getInstance().screen instanceof TvScreen s) {
            return s;
        }
        throw new StepFailure("sem a tela da Tela: " + describe(Minecraft.getInstance().screen));
    }

    private static TvPixelEditor pixel() throws StepFailure {
        return screen().editor().pixel();
    }

    private static void clickAt(int[] point) throws StepFailure {
        if (point == null) {
            throw new StepFailure("o controle não está à vista");
        }
        E2eUi.click(screen(), point[0], point[1]);
    }
}
