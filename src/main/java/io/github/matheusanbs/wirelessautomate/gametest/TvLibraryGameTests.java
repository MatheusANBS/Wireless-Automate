package io.github.matheusanbs.wirelessautomate.gametest;

import io.github.matheusanbs.wirelessautomate.Config;
import io.github.matheusanbs.wirelessautomate.WirelessAutomate;
import io.github.matheusanbs.wirelessautomate.menu.TvMenu;
import io.github.matheusanbs.wirelessautomate.packet.TvLibraryActionPayload;
import io.github.matheusanbs.wirelessautomate.packet.TvLibraryActionPayload.Kind;
import io.github.matheusanbs.wirelessautomate.packet.TvLibraryPayload;
import io.github.matheusanbs.wirelessautomate.registry.ModBlocks;
import io.github.matheusanbs.wirelessautomate.tv.TvBlock;
import io.github.matheusanbs.wirelessautomate.tv.TvBlockEntity;
import io.github.matheusanbs.wirelessautomate.tv.TvComponent;
import io.github.matheusanbs.wirelessautomate.tv.TvFormat;
import io.github.matheusanbs.wirelessautomate.tv.TvLibrary;
import io.github.matheusanbs.wirelessautomate.tv.TvLibraryActions;
import io.github.matheusanbs.wirelessautomate.tv.TvPiece;
import io.github.matheusanbs.wirelessautomate.tv.TvPieceType;
import io.github.matheusanbs.wirelessautomate.tv.TvStyle;
import io.github.matheusanbs.wirelessautomate.tv.TvTemplates;
import io.github.matheusanbs.wirelessautomate.tv.TvTheme;
import io.netty.buffer.Unpooled;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** A biblioteca do jogador (componentes e temas): salvar, renomear, remover, os tetos, o NBT e as ações do menu. */
@GameTestHolder(WirelessAutomate.MODID)
@PrefixGameTestTemplate(false)
public final class TvLibraryGameTests {
    private static final BlockPos A = new BlockPos(1, 1, 1);

    private TvLibraryGameTests() {
    }

    private static void withScreen(GameTestHelper helper, UUID owner, Consumer<TvBlockEntity> body) {
        helper.setBlock(A.south(), Blocks.STONE);
        helper.setBlock(A, ModBlocks.SIGNAL_SCREEN.get().defaultBlockState().setValue(TvBlock.FACING, Direction.NORTH));
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(helper.getBlockEntity(A) instanceof TvBlockEntity, "sem block entity"))
                .thenExecute(() -> {
                    TvBlockEntity tv = (TvBlockEntity) helper.getBlockEntity(A);
                    tv.setOwner(owner);
                    body.accept(tv);
                })
                .thenSucceed();
    }

    private static TvMenu opened(GameTestHelper helper, TestPlayers.MenuPlayer player) {
        player.setPos(helper.absoluteVec(Vec3.atCenterOf(A)));
        BlockPos pos = helper.absolutePos(A);
        helper.getLevel().getBlockState(pos).useWithoutItem(helper.getLevel(), player,
                new BlockHitResult(Vec3.atCenterOf(pos), Direction.NORTH, pos, false));
        helper.assertTrue(player.containerMenu instanceof TvMenu, "o menu não abriu");
        return (TvMenu) player.containerMenu;
    }

    private static TvComponent component(String name, String text) {
        return new TvComponent(name, List.of(new TvPiece(TvPieceType.TEXT, 0, 0, 20, 8, 1, "", Optional.empty(),
                TvFormat.DEFAULT, new TvStyle(TvStyle.DEFAULT.color(), 0, 0, 0, 8, TvStyle.DEFAULT.align(), true,
                        text))));
    }

    private static TvTheme theme(String name, int bg) {
        return new TvTheme(name, bg, 0xFF111111, 0xFFEEEEEE, 0xFF888888, 0xFF00FF00, 0xFFFF0000);
    }

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void salvarRenomearERemoverPeloMenu(GameTestHelper helper) {
        TestPlayers.MenuPlayer player = TestPlayers.menuOpener(helper);
        withScreen(helper, player.getUUID(), tv -> {
            TvMenu menu = opened(helper, player);
            TvLibrary library = TvLibrary.get(helper.getLevel().getServer());
            UUID id = player.getUUID();
            TvLibraryActions.handle(player, TvLibraryActionPayload.open(menu.containerId));
            TvLibraryActions.handle(player, TvLibraryActionPayload.saveComponent(menu.containerId,
                    component("Cartão", "um")));
            TvLibraryActions.handle(player, TvLibraryActionPayload.saveTheme(menu.containerId, theme("Meu tema", 0xFF000000)));
            helper.assertValueEqual(library.components(id), List.of(component("Cartão", "um")), "componentes");
            helper.assertValueEqual(library.themes(id), List.of(theme("Meu tema", 0xFF000000)), "temas");

            TvLibraryActions.handle(player, TvLibraryActionPayload.rename(menu.containerId, Kind.COMPONENT, "Cartão",
                    "Cartão novo"));
            TvLibraryActions.handle(player, TvLibraryActionPayload.rename(menu.containerId, Kind.THEME, "Meu tema",
                    "Tema novo"));
            helper.assertValueEqual(library.components(id), List.of(component("Cartão novo", "um")), "renomeado");
            helper.assertValueEqual(library.themes(id), List.of(theme("Tema novo", 0xFF000000)), "tema renomeado");
            TvLibraryPayload sent = TvLibraryActions.payload(player, menu.containerId, library);
            helper.assertValueEqual(sent.components().size(), 1, "o payload leva os componentes");
            helper.assertValueEqual(sent.themes().size(), 1, "o payload leva os temas");

            TvLibraryActions.handle(player, TvLibraryActionPayload.remove(menu.containerId, Kind.COMPONENT, "Cartão novo"));
            TvLibraryActions.handle(player, TvLibraryActionPayload.remove(menu.containerId, Kind.THEME, "Tema novo"));
            helper.assertTrue(library.components(id).isEmpty() && library.themes(id).isEmpty(), "não removeu");
        });
    }

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void nomeRepetidoSubstituiERenomearParaNomeUsadoERecusado(GameTestHelper helper) {
        TvLibrary library = new TvLibrary();
        UUID id = UUID.randomUUID();
        helper.assertValueEqual(library.saveComponent(id, component("A", "um")), TvLibrary.Result.OK, "salvar");
        library.saveComponent(id, component("A", "dois"));
        helper.assertValueEqual(library.components(id), List.of(component("A", "dois")), "substituiu");
        library.saveTheme(id, theme("T", 0xFF010101));
        library.saveTheme(id, theme("T", 0xFF020202));
        helper.assertValueEqual(library.themes(id), List.of(theme("T", 0xFF020202)), "tema substituído");

        library.saveComponent(id, component("B", "tres"));
        helper.assertValueEqual(library.renameComponent(id, "A", "B"), TvLibrary.Result.EXISTS, "nome usado");
        helper.assertValueEqual(library.renameComponent(id, "A", "  "), TvLibrary.Result.INVALID, "nome vazio");
        helper.assertValueEqual(library.renameComponent(id, "X", "Y"), TvLibrary.Result.MISSING, "inexistente");
        helper.assertValueEqual(library.removeTheme(id, "X"), TvLibrary.Result.MISSING, "remover inexistente");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "wirelessautomate_config_tv")
    public static void tetosDeComponentesETemas(GameTestHelper helper) {
        int components = Config.tvMaxComponents();
        int themes = Config.tvMaxThemes();
        try {
            Config.TV_MAX_COMPONENTS.set(2);
            Config.TV_MAX_THEMES.set(1);
            TvLibrary library = new TvLibrary();
            UUID id = UUID.randomUUID();
            helper.assertValueEqual(library.saveComponent(id, component("A", "a")), TvLibrary.Result.OK, "A");
            helper.assertValueEqual(library.saveComponent(id, component("B", "b")), TvLibrary.Result.OK, "B");
            helper.assertValueEqual(library.saveComponent(id, component("C", "c")), TvLibrary.Result.FULL, "C");
            helper.assertValueEqual(library.saveComponent(id, component("A", "novo")), TvLibrary.Result.OK,
                    "substituir no teto");
            helper.assertValueEqual(library.components(id).size(), 2, "componentes");
            helper.assertValueEqual(library.saveTheme(id, theme("T", 0xFF000000)), TvLibrary.Result.OK, "T");
            helper.assertValueEqual(library.saveTheme(id, theme("U", 0xFF000000)), TvLibrary.Result.FULL, "U");
            helper.assertValueEqual(library.themes(id).size(), 1, "temas");
            helper.succeed();
        } finally {
            Config.TV_MAX_COMPONENTS.set(components);
            Config.TV_MAX_THEMES.set(themes);
        }
    }

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void idaEVoltaDoNbt(GameTestHelper helper) {
        TvLibrary library = new TvLibrary();
        UUID id = UUID.randomUUID();
        library.saveComponent(id, component("A", "um"));
        library.saveComponent(id, TvTemplates.components().get(1));
        library.saveTheme(id, theme("T", 0xFF123456));
        CompoundTag tag = library.save(new CompoundTag(), helper.getLevel().registryAccess());
        TvLibrary loaded = TvLibrary.load(tag, helper.getLevel().registryAccess());
        helper.assertValueEqual(loaded.components(id), library.components(id), "componentes");
        helper.assertValueEqual(loaded.themes(id), library.themes(id), "temas");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void acaoSemOMenuAbertoERecusada(GameTestHelper helper) {
        TestPlayers.MenuPlayer player = TestPlayers.menuOpener(helper);
        TvLibrary library = TvLibrary.get(helper.getLevel().getServer());
        TvLibraryActions.handle(player, TvLibraryActionPayload.saveComponent(100, component("Sem menu", "x")));
        TvLibraryActions.handle(player, TvLibraryActionPayload.saveTheme(100, theme("Sem menu", 0xFF000000)));
        helper.assertTrue(library.components(player.getUUID()).isEmpty(), "salvou sem o menu");
        helper.assertTrue(library.themes(player.getUUID()).isEmpty(), "salvou o tema sem o menu");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void acimaDeDezPorSegundoEIgnorado(GameTestHelper helper) {
        TestPlayers.MenuPlayer player = TestPlayers.menuOpener(helper);
        withScreen(helper, player.getUUID(), tv -> {
            TvMenu menu = opened(helper, player);
            TvLibrary library = TvLibrary.get(helper.getLevel().getServer());
            for (int i = 0; i < 12; i++) {
                TvLibraryActions.handle(player, TvLibraryActionPayload.saveTheme(menu.containerId,
                        theme("t" + i, 0xFF000000 + i)));
            }
            helper.assertValueEqual(library.themes(player.getUUID()).size(), 10, "temas salvos no mesmo segundo");
        });
    }

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void payloadsIdaEVolta(GameTestHelper helper) {
        RegistryFriendlyByteBuf buf = new RegistryFriendlyByteBuf(Unpooled.buffer(), helper.getLevel().registryAccess());
        TvLibraryPayload payload = new TvLibraryPayload(7, List.of(component("A", "um")), List.of(theme("T", 0xFF000000)));
        TvLibraryPayload.STREAM_CODEC.encode(buf, payload);
        helper.assertValueEqual(TvLibraryPayload.STREAM_CODEC.decode(buf), payload, "biblioteca");

        TvLibraryActionPayload action = TvLibraryActionPayload.saveComponent(9, component("A", "um"));
        TvLibraryActionPayload.STREAM_CODEC.encode(buf, action);
        TvLibraryActionPayload back = TvLibraryActionPayload.STREAM_CODEC.decode(buf);
        helper.assertValueEqual(back.action(), TvLibraryActionPayload.Action.SAVE_COMPONENT, "ação");
        helper.assertValueEqual(back.component(), component("A", "um"), "conteúdo");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void componentesProntosSoTemAsTresPecas(GameTestHelper helper) {
        List<TvComponent> ready = TvTemplates.components();
        helper.assertValueEqual(ready.size(), 3, "quantos prontos");
        for (TvComponent component : ready) {
            helper.assertTrue(!component.pieces().isEmpty() && component.pieces().size() <= Config.tvMaxElements(),
                    "peças de " + component.name());
            for (TvPiece piece : component.pieces()) {
                helper.assertTrue(piece.binding().isEmpty(), "pronto com ligação: " + component.name());
            }
        }
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void componenteGrandeDemaisTemAvisoProprioEOTetoDaConfigCabeNoPayload(GameTestHelper helper) {
        TestPlayers.MenuPlayer player = TestPlayers.menuOpener(helper);
        withScreen(helper, player.getUUID(), tv -> {
            TvMenu menu = opened(helper, player);
            List<TvPiece> pieces = new java.util.ArrayList<>();
            for (int i = 0; i < 100; i++) {
                pieces.add(component("x", "t".repeat(120)).pieces().get(0));
            }
            TvLibraryActions.handle(player, TvLibraryActionPayload.saveComponent(menu.containerId,
                    new TvComponent("Grande", pieces)));
            helper.assertTrue(TvLibrary.get(helper.getLevel().getServer()).components(player.getUUID()).isEmpty(),
                    "salvou o grande");
            helper.assertTrue(player.actionBar != null && player.actionBar.getContents()
                    instanceof net.minecraft.network.chat.contents.TranslatableContents key
                    && key.getKey().equals("message.wirelessautomate.tv.library_component_too_big"), "aviso");
            helper.assertTrue((Integer) Config.TV_MAX_COMPONENTS.getSpec().getRange().getMax() * (long) TvLibraryActions
                    .MAX_COMPONENT_BYTES + 64L * 1024 < TvLibraryPayload.MAX_BYTES, "o teto não cabe no payload");
        });
    }
}
