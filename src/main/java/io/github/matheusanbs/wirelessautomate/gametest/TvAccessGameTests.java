package io.github.matheusanbs.wirelessautomate.gametest;

import io.github.matheusanbs.wirelessautomate.Config;
import io.github.matheusanbs.wirelessautomate.WirelessAutomate;
import io.github.matheusanbs.wirelessautomate.item.LinkerItem;
import io.github.matheusanbs.wirelessautomate.menu.TvMenu;
import io.github.matheusanbs.wirelessautomate.network.NetworkSavedData;
import io.github.matheusanbs.wirelessautomate.network.WaNetwork;
import io.github.matheusanbs.wirelessautomate.packet.NetworkSuggestionsPayload;
import io.github.matheusanbs.wirelessautomate.packet.TvAccessActionPayload;
import io.github.matheusanbs.wirelessautomate.packet.TvAccessActionPayload.Action;
import io.github.matheusanbs.wirelessautomate.packet.TvAccessPayload;
import io.github.matheusanbs.wirelessautomate.packet.TvLayoutAckPayload;
import io.github.matheusanbs.wirelessautomate.packet.TvSizePayload;
import io.github.matheusanbs.wirelessautomate.registry.ModBlocks;
import io.github.matheusanbs.wirelessautomate.registry.ModItems;
import io.github.matheusanbs.wirelessautomate.tv.TvAccess;
import io.github.matheusanbs.wirelessautomate.tv.TvAccessActions;
import io.github.matheusanbs.wirelessautomate.tv.TvArea;
import io.github.matheusanbs.wirelessautomate.tv.TvBlock;
import io.github.matheusanbs.wirelessautomate.tv.TvBlockEntity;
import io.github.matheusanbs.wirelessautomate.tv.TvEditing;
import io.github.matheusanbs.wirelessautomate.tv.TvFormat;
import io.github.matheusanbs.wirelessautomate.tv.TvLayout;
import io.github.matheusanbs.wirelessautomate.tv.TvLinks;
import io.github.matheusanbs.wirelessautomate.tv.TvPiece;
import io.github.matheusanbs.wirelessautomate.tv.TvPieceType;
import io.github.matheusanbs.wirelessautomate.tv.TvStyle;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import org.jetbrains.annotations.Nullable;

/**
 * A lista de acesso da Tela de Sinal: o convidado edita (menu, tamanho, layout e Vinculador), o removido e o não
 * convidado não, só o dono mexe na lista pela aba Acesso, os tetos da rede, a lista na abertura do menu, a leitura de
 * uma rede do convidado na Tela do mundo e o NBT antigo sem a chave. Os times ficam no {@code TeamGameTests}, na run
 * com o FTB Teams.
 */
@GameTestHolder(WirelessAutomate.MODID)
@PrefixGameTestTemplate(false)
public final class TvAccessGameTests {
    private static final BlockPos A = new BlockPos(1, 1, 1);

    private TvAccessGameTests() {
    }

    private static BlockState screen() {
        return ModBlocks.SIGNAL_SCREEN.get().defaultBlockState().setValue(TvBlock.FACING, Direction.NORTH);
    }

    /** Uma Tela virada para o norte em {@code A}, com parede atrás dela e da célula à esquerda de quem olha. */
    private static void withScreen(GameTestHelper helper, UUID owner, Consumer<TvBlockEntity> body) {
        helper.setBlock(A.south(), Blocks.STONE);
        helper.setBlock(A.south().west(), Blocks.STONE);
        helper.setBlock(A, screen());
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(helper.getBlockEntity(A) instanceof TvBlockEntity, "sem block entity"))
                .thenExecute(() -> {
                    TvBlockEntity tv = (TvBlockEntity) helper.getBlockEntity(A);
                    tv.setOwner(owner);
                    body.accept(tv);
                })
                .thenSucceed();
    }

    private static void click(GameTestHelper helper, TestPlayers.MenuPlayer player) {
        BlockPos pos = helper.absolutePos(A);
        player.setPos(helper.absoluteVec(Vec3.atCenterOf(A)));
        helper.getLevel().getBlockState(pos).useWithoutItem(helper.getLevel(), player,
                new BlockHitResult(Vec3.atCenterOf(pos), Direction.NORTH, pos, false));
    }

    private static TvMenu opened(GameTestHelper helper, TestPlayers.MenuPlayer player) {
        click(helper, player);
        helper.assertTrue(player.containerMenu instanceof TvMenu, "o menu não abriu");
        return (TvMenu) player.containerMenu;
    }

    /** O menu como o cliente o leria do buffer de abertura (uma vez por abertura: a leitura consome o buffer). */
    private static TvMenu asClient(TestPlayers.MenuPlayer player, TvMenu server) {
        return new TvMenu(server.containerId, player.getInventory(), player.openData);
    }

    private static TvLayout oneText(String text) {
        return TvLayout.of(List.of(new TvPiece(TvPieceType.TEXT, 0, 0, 20, 10, 0, "", Optional.empty(),
                TvFormat.DEFAULT, new TvStyle(TvStyle.DEFAULT.color(), 0, 0, 0, 8, TvStyle.DEFAULT.align(), true,
                        text))));
    }

    private static @Nullable CustomPacketPayload act(TestPlayers.MenuPlayer player,
            TvMenu menu, Action action, @Nullable UUID id, String text) {
        return TvAccessActions.handle(player, new TvAccessActionPayload(menu.containerId, action, Optional.ofNullable(id),
                text));
    }

    private static String notice(@Nullable CustomPacketPayload reply) {
        return reply instanceof TvAccessPayload payload ? payload.notice() : "<" + reply + ">";
    }

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void convidadoEditaComoODono(GameTestHelper helper) {
        TestPlayers.MenuPlayer owner = TestPlayers.menuOpener(helper);
        TestPlayers.MenuPlayer guest = TestPlayers.menuOpener(helper);
        withScreen(helper, owner.getUUID(), tv -> {
            tv.setAccess(tv.access().withPlayer(guest.getUUID(), "Convidado"));
            TvMenu menu = opened(helper, guest);
            helper.assertTrue(menu.stillValid(guest), "o menu do convidado devia valer");
            helper.assertFalse(menu.manage(), "o convidado não gerencia a lista");
            TvMenu client = asClient(guest, menu);
            helper.assertFalse(client.manage(), "a aba Acesso apareceria para o convidado");
            helper.assertTrue(client.access().isEmpty(), "o convidado recebeu a lista de acesso");
            TvEditing.handleSize(guest, new TvSizePayload(menu.containerId, 2, 1, 0, 0));
            helper.assertValueEqual(tv.area(), new TvArea(2, 1, 0, 0), "o convidado não mudou o tamanho");
            helper.assertTrue(TvEditing.applyLayout(guest, tv, oneText("convidado")).ok(), "o convidado não salvou");
            ItemStack stack = new ItemStack(ModItems.LINKER.get());
            TvLinks.choose(guest, stack, tv);
            helper.assertTrue(LinkerItem.tv(stack) != null, "o convidado não escolheu a Tela no Vinculador");
            helper.assertTrue(act(guest, menu, Action.ADD_PLAYER, owner.getUUID(), "") == null,
                    "o convidado mexeu na lista");
            helper.assertTrue(act(guest, menu, Action.SUGGEST_PLAYER, null, "") == null,
                    "o convidado recebeu sugestões");
            helper.assertTrue(tv.access().players().size() == 1, "a lista mudou pelo convidado");
        });
    }

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void removidoENaoConvidadoNaoEditam(GameTestHelper helper) {
        TestPlayers.MenuPlayer owner = TestPlayers.menuOpener(helper);
        TestPlayers.MenuPlayer guest = TestPlayers.menuOpener(helper);
        TestPlayers.MenuPlayer stranger = TestPlayers.menuOpener(helper);
        withScreen(helper, owner.getUUID(), tv -> {
            click(helper, stranger);
            helper.assertFalse(stranger.containerMenu instanceof TvMenu, "quem não está na lista abriu a tela");
            helper.assertValueEqual(TvEditing.applyLayout(stranger, tv, oneText("x")).reason(),
                    TvLayoutAckPayload.Result.DENIED, "quem não está na lista salvou");

            tv.setAccess(tv.access().withPlayer(guest.getUUID(), "Convidado"));
            TvMenu menu = opened(helper, guest);
            tv.setAccess(tv.access().withoutPlayer(guest.getUUID()));
            helper.assertFalse(menu.stillValid(guest), "o menu do removido continuou valendo");
            TvEditing.handleSize(guest, new TvSizePayload(menu.containerId, 2, 1, 0, 0));
            helper.assertValueEqual(tv.area(), TvArea.SINGLE, "o removido mudou o tamanho");
            helper.assertValueEqual(TvEditing.applyLayout(guest, tv, oneText("x")).reason(),
                    TvLayoutAckPayload.Result.DENIED, "o removido salvou");
            ItemStack stack = new ItemStack(ModItems.LINKER.get());
            TvLinks.choose(guest, stack, tv);
            helper.assertTrue(LinkerItem.tv(stack) == null, "o removido escolheu a Tela no Vinculador");
        });
    }

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void soODonoMexeNaLista(GameTestHelper helper) {
        TestPlayers.MenuPlayer owner = TestPlayers.menuOpener(helper);
        TestPlayers.MenuPlayer guest = TestPlayers.menuOpener(helper);
        withScreen(helper, owner.getUUID(), tv -> {
            TvMenu menu = opened(helper, owner);
            helper.assertTrue(menu.manage() && asClient(owner, menu).manage(), "o dono não vê a aba Acesso");
            helper.assertValueEqual(notice(act(owner, menu, Action.ADD_PLAYER, guest.getUUID(), "")), "member.added",
                    "convidar pelo id");
            helper.assertTrue(tv.access().hasPlayer(guest.getUUID()), "o convidado não entrou");
            helper.assertTrue(tv.canEdit(guest), "o convidado não edita");
            helper.assertFalse(tv.canManage(guest), "o convidado gerencia");
            CustomPacketPayload reply = act(owner, menu, Action.ADD_PLAYER, guest.getUUID(), "");
            helper.assertValueEqual(notice(reply), "refused.exists", "convidar de novo");
            helper.assertTrue(reply instanceof TvAccessPayload payload && payload.access().guests().size() == 1
                    && payload.access().guests().get(0).id().equals(guest.getUUID()),
                    "a resposta não traz a lista atualizada");
            helper.assertValueEqual(notice(act(owner, menu, Action.ADD_PLAYER, owner.getUUID(), "")), "refused.owner",
                    "convidar o dono");
            helper.assertValueEqual(notice(act(owner, menu, Action.ADD_PLAYER, UUID.randomUUID(), "")),
                    "refused.known", "convidar um desconhecido");
            helper.assertValueEqual(notice(act(owner, menu, Action.ADD_TEAM, null, "um-time")), "refused.team",
                    "um time sem o FTB Teams");
            CustomPacketPayload suggestions = act(owner, menu, Action.SUGGEST_PLAYER, null, "");
            helper.assertTrue(suggestions instanceof NetworkSuggestionsPayload found
                    && found.suggestions().stream().anyMatch(s -> s.id().equals(guest.getUUID()) && s.taken()),
                    "a sugestão do convidado não veio marcada");

            TvMenu reopened = opened(helper, owner);
            helper.assertTrue(asClient(owner, reopened).access().guests().size() == 1,
                    "a lista não foi no buffer de abertura do dono");
            helper.assertValueEqual(notice(act(owner, reopened, Action.REMOVE_PLAYER, guest.getUUID(), "")),
                    "member.removed", "tirar o convidado");
            helper.assertFalse(tv.canEdit(guest), "o removido ainda edita");
            helper.assertTrue(TvAccessActions.handle(owner, new TvAccessActionPayload(reopened.containerId + 1,
                    Action.ADD_PLAYER, Optional.of(guest.getUUID()), "")) == null, "agiu com outro menu");
            helper.assertTrue(tv.access().isEmpty(), "a lista mudou com outro menu");
        });
    }

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void asAcoesTemLimitePorSegundo(GameTestHelper helper) {
        TestPlayers.MenuPlayer owner = TestPlayers.menuOpener(helper);
        withScreen(helper, owner.getUUID(), tv -> {
            TvMenu menu = opened(helper, owner);
            int answered = 0;
            for (int i = 0; i < 30; i++) {
                if (act(owner, menu, Action.SUGGEST_PLAYER, null, "") != null) {
                    answered++;
                }
            }
            helper.assertTrue(answered > 0 && answered < 30, "o limite por segundo não valeu: " + answered);
        });
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "wirelessautomate_config_tv")
    public static void osTetosSaoOsDaRede(GameTestHelper helper) {
        TestPlayers.MenuPlayer owner = TestPlayers.menuOpener(helper);
        TestPlayers.MenuPlayer first = TestPlayers.menuOpener(helper);
        TestPlayers.MenuPlayer second = TestPlayers.menuOpener(helper);
        int members = Config.MAX_MEMBERS_PER_NETWORK.get();
        int teams = Config.MAX_TEAMS_PER_NETWORK.get();
        withScreen(helper, owner.getUUID(), tv -> {
            try {
                Config.MAX_MEMBERS_PER_NETWORK.set(1);
                Config.MAX_TEAMS_PER_NETWORK.set(1);
                TvMenu menu = opened(helper, owner);
                helper.assertValueEqual(notice(act(owner, menu, Action.ADD_PLAYER, first.getUUID(), "")),
                        "member.added", "o primeiro cabe");
                helper.assertValueEqual(notice(act(owner, menu, Action.ADD_PLAYER, second.getUUID(), "")),
                        "refused.limit", "o segundo passa do teto");
                helper.assertFalse(tv.access().hasPlayer(second.getUUID()), "passou do teto de convidados");
                helper.assertTrue(NetworkSavedData.teamCapReached(owner, 1), "o teto de times não valeu");
                helper.assertFalse(NetworkSavedData.teamCapReached(owner, 0), "o teto de times com zero times");
                Config.MAX_MEMBERS_PER_NETWORK.set(0);
                helper.assertValueEqual(notice(act(owner, menu, Action.ADD_PLAYER, second.getUUID(), "")),
                        "member.added", "0 é sem limite");
            } finally {
                Config.MAX_MEMBERS_PER_NETWORK.set(members);
                Config.MAX_TEAMS_PER_NETWORK.set(teams);
            }
        });
    }

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void aTelaLeARedeDeUmConvidado(GameTestHelper helper) {
        UUID owner = UUID.randomUUID();
        UUID guest = UUID.randomUUID();
        NetworkSavedData data = NetworkSavedData.get(helper.getLevel().getServer());
        WaNetwork ofGuest = data.create(guest, "teste-tv-acesso-rede-do-convidado");
        try {
            helper.assertFalse(TvAccess.EMPTY.canRead(ofGuest, owner), "o dono lê a rede privada de outro");
            TvAccess access = TvAccess.EMPTY.withPlayer(guest, "Convidado");
            helper.assertTrue(access.canRead(ofGuest, owner), "a Tela não lê a rede do convidado");
            helper.assertFalse(access.withoutPlayer(guest).canRead(ofGuest, owner),
                    "a Tela lê a rede de quem saiu da lista");
            helper.assertFalse(TvAccess.EMPTY.withTeam(UUID.randomUUID()).canRead(ofGuest, null),
                    "um time sem o FTB Teams deu leitura");
            helper.succeed();
        } finally {
            data.remove(ofGuest.id());
        }
    }

    @GameTest(template = "empty")
    public static void nbtAntigoLeSemAcessoENovoGuardaTudo(GameTestHelper helper) {
        HolderLookup.Provider registries = helper.getLevel().registryAccess();
        UUID owner = UUID.randomUUID();
        UUID guest = UUID.randomUUID();
        UUID team = UUID.randomUUID();
        TvBlockEntity original = new TvBlockEntity(helper.absolutePos(A), screen());
        original.setOwner(owner);
        CompoundTag old = original.saveWithFullMetadata(registries);
        helper.assertFalse(old.contains("access"), "uma lista vazia foi ao disco");
        TvBlockEntity fromOld = new TvBlockEntity(helper.absolutePos(A), screen());
        fromOld.loadWithComponents(old, registries);
        helper.assertTrue(fromOld.access().isEmpty(), "a Tela antiga leu uma lista");

        original.setAccess(TvAccess.EMPTY.withPlayer(guest, "Ana").withTeam(team));
        CompoundTag saved = original.saveWithFullMetadata(registries);
        TvBlockEntity back = new TvBlockEntity(helper.absolutePos(A), screen());
        back.loadWithComponents(saved, registries);
        helper.assertTrue(back.access().equals(original.access()), "a lista não voltou igual: " + back.access());
        helper.assertTrue(back.access().teams().contains(team), "o time sumiu sem o FTB Teams");
        helper.assertFalse(original.getUpdateTag(registries).contains("access"), "o update tag leva a lista");

        CompoundTag bad = new CompoundTag();
        ListTag players = new ListTag();
        CompoundTag repeated = new CompoundTag();
        repeated.putUUID("id", guest);
        players.add(repeated);
        players.add(repeated.copy());
        players.add(new CompoundTag());
        bad.put("players", players);
        ListTag teams = new ListTag();
        teams.add(NbtUtils.createUUID(team));
        teams.add(NbtUtils.createUUID(team));
        bad.put("teams", teams);
        TvAccess read = TvAccess.load(bad);
        helper.assertTrue(read.players().size() == 1 && read.teams().size() == 1, "repetido ou inválido entrou: " + read);
        helper.assertTrue(TvAccess.load(null).isEmpty(), "sem a chave");
        helper.succeed();
    }
}
