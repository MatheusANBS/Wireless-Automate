package io.github.matheusanbs.wirelessautomate.gametest;

import io.github.matheusanbs.wirelessautomate.WirelessAutomate;
import io.github.matheusanbs.wirelessautomate.registry.ModBlocks;
import io.github.matheusanbs.wirelessautomate.tv.TvArea;
import io.github.matheusanbs.wirelessautomate.tv.TvBackground;
import io.github.matheusanbs.wirelessautomate.tv.TvBlock;
import io.github.matheusanbs.wirelessautomate.tv.TvBlockEntity;
import io.github.matheusanbs.wirelessautomate.tv.TvComponent;
import io.github.matheusanbs.wirelessautomate.tv.TvFormat;
import io.github.matheusanbs.wirelessautomate.tv.TvImageConfig;
import io.github.matheusanbs.wirelessautomate.tv.TvImageFormat;
import io.github.matheusanbs.wirelessautomate.tv.TvImageHash;
import io.github.matheusanbs.wirelessautomate.tv.TvImageState;
import io.github.matheusanbs.wirelessautomate.tv.TvImageStore;
import io.github.matheusanbs.wirelessautomate.tv.TvLayout;
import io.github.matheusanbs.wirelessautomate.tv.TvLibrary;
import io.github.matheusanbs.wirelessautomate.tv.TvPartStyle;
import io.github.matheusanbs.wirelessautomate.tv.TvPicture;
import io.github.matheusanbs.wirelessautomate.tv.TvPiece;
import io.github.matheusanbs.wirelessautomate.tv.TvPieceType;
import io.github.matheusanbs.wirelessautomate.tv.TvSavedLayouts;
import io.github.matheusanbs.wirelessautomate.tv.TvStyle;
import io.github.matheusanbs.wirelessautomate.tv.TvTheme;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * As imagens enviadas na parte 4b: os componentes e os layouts salvos da biblioteca seguram a imagem no armazém (a
 * limpeza não a apaga enquanto um deles a usa, e a imagem que o armazém não tem sai ao salvar), o componente antigo
 * lê sem imagens, e desligar {@code tv.images.enabled} com o servidor rodando avisa os clientes na hora.
 */
@GameTestHolder(WirelessAutomate.MODID)
@PrefixGameTestTemplate(false)
public final class TvImageHoldGameTests {
    private static final BlockPos A = new BlockPos(1, 1, 1);
    private static final String CONFIG = "wirelessautomate_config_tv";

    private TvImageHoldGameTests() {
    }

    private static TvPiece image(UUID id) {
        return new TvPiece(TvPieceType.IMAGE, 0, 0, 32, 32, 0, "", Optional.empty(), TvFormat.DEFAULT,
                TvStyle.DEFAULT.withPart(TvPartStyle.DEFAULT.withImage(Optional.of(id), 0, TvPartStyle.VAlign.TOP)));
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = CONFIG)
    public static void componenteELayoutSalvoSeguramAImagem(GameTestHelper helper) {
        ServerPlayer player = TestPlayers.inLevel(helper);
        UUID id = player.getUUID();
        MinecraftServer server = helper.getLevel().getServer();
        AtomicLong now = new AtomicLong(System.currentTimeMillis());
        TvImageStore.setClockForTests(now::get);
        try {
            TvImageStore store = TvImageStore.get(server);
            TvLibrary library = TvLibrary.get(server);
            byte[] inComponent = TvImageGameTests.png(12, 12, 41);
            byte[] inLayout = TvImageGameTests.png(12, 12, 42);
            store.putForTests(inComponent, id);
            store.putForTests(inLayout, id);
            TvPicture a = store.picture(TvImageHash.of(inComponent));
            TvPicture b = store.picture(TvImageHash.of(inLayout));
            TvPicture missing = new TvPicture(TvImageHash.of(TvImageGameTests.png(4, 4, 43)), TvImageFormat.PNG, 4,
                    4, 1, Optional.empty());
            TvComponent component = new TvComponent("Com imagem", List.of(image(a.id()), image(missing.id())),
                    Optional.empty(), List.of(a, missing));
            helper.assertValueEqual(library.saveComponent(id, component.withPictures(
                    store.resolve(component.pictures()))), TvLibrary.Result.OK, "o componente salvo");
            TvLayout screen = TvLayout.of(List.of(image(b.id())), TvTheme.DEFAULT, TvBackground.DEFAULT, false,
                    List.of(), List.of(b));
            TvComponent saved = TvSavedLayouts.of("Layout com imagem", screen, TvArea.SINGLE);
            helper.assertValueEqual(saved.pictures(), List.of(b), "o layout salvo leva a imagem que usa");
            helper.assertValueEqual(library.saveComponent(id, saved), TvLibrary.Result.OK, "o layout salvo");
            TvImageStore.syncLibrary(server, id);
            helper.assertValueEqual(library.components(id).stream().filter(c -> c.name().equals("Com imagem"))
                    .findFirst().orElseThrow().pictures(), List.of(a), "a imagem que o armazém não tem saiu");
            helper.assertTrue(library.pictureRefs().get(id).containsAll(List.of(a.hash(), b.hash())),
                    "a recontagem da carga do mundo vê as duas");
            helper.assertValueEqual(store.entry(a.hash()).libraries(), 1, "o componente conta como uso");
            helper.assertValueEqual(store.entry(b.hash()).libraries(), 1, "o layout salvo conta como uso");
            now.addAndGet(TvImageStore.GRACE_MS + 1);
            store.cleanupNowForTests();
            helper.assertTrue(store.contains(a.hash()) && store.contains(b.hash()),
                    "a limpeza não apaga o que um componente ou layout salvo usa");
            library.removeComponent(id, "Com imagem");
            library.removeComponent(id, "Layout com imagem");
            TvImageStore.syncLibrary(server, id);
            now.addAndGet(TvImageStore.GRACE_MS + 1);
            store.cleanupNowForTests();
            helper.assertFalse(store.contains(a.hash()) || store.contains(b.hash()),
                    "sem componente nem layout, sai depois do prazo");
        } finally {
            TvImageStore.setClockForTests(null);
        }
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void componenteAntigoLeSemImagens(GameTestHelper helper) {
        TvComponent old = new TvComponent("Antigo", List.of(image(UUID.randomUUID())));
        CompoundTag encoded = (CompoundTag) TvComponent.CODEC.encodeStart(NbtOps.INSTANCE, old).getOrThrow();
        helper.assertTrue(TvComponent.CODEC.parse(NbtOps.INSTANCE, encoded).getOrThrow().pictures().isEmpty(),
                "o componente sem a chave lê sem imagens");
        byte[] png = TvImageGameTests.png(8, 8, 44);
        TvPicture picture = new TvPicture(TvImageHash.of(png), TvImageFormat.PNG, 8, 8, 1, Optional.empty());
        TvComponent with = new TvComponent("Novo", List.of(image(picture.id())), Optional.empty(), List.of(picture));
        CompoundTag tag = (CompoundTag) TvComponent.CODEC.encodeStart(NbtOps.INSTANCE, with).getOrThrow();
        helper.assertValueEqual(TvComponent.CODEC.parse(NbtOps.INSTANCE, tag).getOrThrow(), with,
                "ida e volta com a imagem");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = CONFIG)
    public static void recargaDaConfigDesligaAsImagensNaHora(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        byte[] png = TvImageGameTests.png(16, 16, 45);
        TvPicture picture = new TvPicture(TvImageHash.of(png), TvImageFormat.PNG, 16, 16, 1, Optional.empty());
        TvBlockEntity tv = new TvBlockEntity(helper.absolutePos(A), ModBlocks.SIGNAL_SCREEN.get().defaultBlockState()
                .setValue(TvBlock.FACING, Direction.NORTH));
        tv.setLayout(TvLayout.of(List.of(image(picture.id())), TvTheme.DEFAULT, TvBackground.DEFAULT, false,
                List.of(), List.of(picture)));
        int before = TvImageState.broadcastsForTests();
        try {
            TvImageConfig.enabledValue().set(false);
            TvImageState.configChanged();
            TvImageState.tick(server);
            helper.assertFalse(TvImageState.sentForTests(), "o aviso de desligadas foi aos clientes");
            helper.assertValueEqual(TvImageState.broadcastsForTests(), before + 1, "uma vez");
            helper.assertTrue(tv.getUpdateTag(helper.getLevel().registryAccess()).getBoolean("pictures_off"),
                    "e o update tag também diz");
            TvImageState.configChanged();
            TvImageState.tick(server);
            helper.assertValueEqual(TvImageState.broadcastsForTests(), before + 1,
                    "recarregar sem mudar não manda de novo");
        } finally {
            TvImageConfig.enabledValue().set(true);
            TvImageState.configChanged();
            TvImageState.tick(server);
        }
        helper.assertTrue(TvImageState.sentForTests(), "religadas: o aviso volta");
        helper.assertValueEqual(TvImageState.broadcastsForTests(), before + 2, "o segundo aviso");
        helper.succeed();
    }
}
