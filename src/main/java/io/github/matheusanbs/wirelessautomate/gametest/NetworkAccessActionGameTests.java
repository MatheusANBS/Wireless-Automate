package io.github.matheusanbs.wirelessautomate.gametest;

import io.github.matheusanbs.wirelessautomate.Config;
import io.github.matheusanbs.wirelessautomate.WirelessAutomate;
import io.github.matheusanbs.wirelessautomate.menu.TabletAccess;
import io.github.matheusanbs.wirelessautomate.menu.TabletMenu;
import io.github.matheusanbs.wirelessautomate.menu.TabletMenu.Action;
import io.github.matheusanbs.wirelessautomate.menu.TabletSnapshot;
import io.github.matheusanbs.wirelessautomate.menu.TabletSnapshot.NetworkView;
import io.github.matheusanbs.wirelessautomate.network.NetworkSavedData;
import io.github.matheusanbs.wirelessautomate.network.WaNetwork;
import io.github.matheusanbs.wirelessautomate.packet.NetworkSuggestPayload;
import io.github.matheusanbs.wirelessautomate.packet.NetworkSuggestionsPayload.Suggestion;
import io.github.matheusanbs.wirelessautomate.packet.TabletActionPayload;
import io.github.matheusanbs.wirelessautomate.packet.TabletPayloads;
import io.github.matheusanbs.wirelessautomate.registry.ModItems;
import io.netty.buffer.Unpooled;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * As ações de acesso do Tablet (convidar, tirar, time), quem pode fazê-las, o que cada um vê no snapshot e as
 * sugestões do campo. Os times de verdade ficam em {@link TeamGameTests} (run com o FTB Teams).
 */
@GameTestHolder(WirelessAutomate.MODID)
@PrefixGameTestTemplate(false)
public final class NetworkAccessActionGameTests {
    private static final int OWNER_MENU = 311;
    private static final int GUEST_MENU = 312;

    private static ServerPlayer playerWithTablet(GameTestHelper helper) {
        ServerPlayer player = TestPlayers.menuOpener(helper);
        player.moveTo(Vec3.atCenterOf(helper.absolutePos(new BlockPos(0, 1, 0)).above(2)));
        player.getInventory().setItem(0, new ItemStack(ModItems.NETWORK_TABLET.get()));
        return player;
    }

    private static TabletMenu open(ServerPlayer player, int containerId) {
        TabletMenu menu = new TabletMenu(containerId, player.getInventory(), player);
        player.containerMenu = menu;
        return menu;
    }

    private static void close(ServerPlayer player) {
        player.containerMenu = player.inventoryMenu;
    }

    private static boolean act(ServerPlayer player, int container, Action action, UUID network, UUID other,
            String text) {
        return TabletPayloads.handleAction(player, new TabletActionPayload(container, action, Optional.of(network),
                Optional.ofNullable(other), text, 0));
    }

    private static TabletSnapshot fresh(TabletMenu menu) {
        menu.setQuery(menu.query());
        menu.pollSnapshot();
        return menu.snapshot();
    }

    @GameTest(template = "empty", timeoutTicks = 300)
    public static void theOwnerInvitesAndRemovesGuests(GameTestHelper helper) {
        ServerPlayer owner = playerWithTablet(helper);
        ServerPlayer friend = TestPlayers.menuOpener(helper);
        NetworkSavedData data = NetworkSavedData.get(helper.getLevel().getServer());
        WaNetwork network = data.create(owner.getUUID(), "teste-acesso-convite");
        try {
            open(owner, OWNER_MENU);
            helper.assertTrue(act(owner, OWNER_MENU, Action.NETWORK_MEMBER_ADD, network.id(), friend.getUUID(), ""),
                    "o dono não convidou um jogador online");
            helper.assertTrue(data.network(network.id()).members().equals(List.of(friend.getUUID())), "convidado");
            helper.assertFalse(act(owner, OWNER_MENU, Action.NETWORK_MEMBER_ADD, network.id(), friend.getUUID(), ""),
                    "convidou duas vezes");
            helper.assertFalse(act(owner, OWNER_MENU, Action.NETWORK_MEMBER_ADD, network.id(), owner.getUUID(), ""),
                    "convidou o próprio dono");
            helper.assertFalse(act(owner, OWNER_MENU, Action.NETWORK_MEMBER_ADD, network.id(), UUID.randomUUID(), ""),
                    "convidou um jogador que o servidor não conhece");
            helper.assertFalse(act(owner, OWNER_MENU, Action.NETWORK_MEMBER_REMOVE, network.id(), UUID.randomUUID(),
                    ""), "tirou quem não era convidado");
            helper.assertTrue(act(owner, OWNER_MENU, Action.NETWORK_MEMBER_REMOVE, network.id(), friend.getUUID(), ""),
                    "o dono não tirou o convidado");
            helper.assertTrue(data.network(network.id()).members().isEmpty(), "o convidado ficou");
            helper.succeed();
        } finally {
            close(owner);
            data.remove(network.id());
        }
    }

    @GameTest(template = "empty", timeoutTicks = 300)
    public static void aGuestOrAStrangerCannotChangeAccess(GameTestHelper helper) {
        ServerPlayer owner = playerWithTablet(helper);
        ServerPlayer guest = playerWithTablet(helper);
        ServerPlayer stranger = playerWithTablet(helper);
        NetworkSavedData data = NetworkSavedData.get(helper.getLevel().getServer());
        WaNetwork network = data.create(owner.getUUID(), "teste-acesso-quem").withMember(guest.getUUID());
        data.update(network);
        try {
            open(guest, GUEST_MENU);
            open(stranger, OWNER_MENU);
            helper.assertFalse(act(guest, GUEST_MENU, Action.NETWORK_MEMBER_ADD, network.id(), stranger.getUUID(), ""),
                    "o convidado convidou alguém");
            helper.assertFalse(act(guest, GUEST_MENU, Action.NETWORK_MEMBER_REMOVE, network.id(), guest.getUUID(), ""),
                    "o convidado se tirou pela ação de gerência");
            helper.assertFalse(act(stranger, OWNER_MENU, Action.NETWORK_MEMBER_ADD, network.id(), stranger.getUUID(),
                    ""), "o estranho se convidou");
            helper.assertFalse(act(stranger, OWNER_MENU, Action.NETWORK_TEAM_ADD, network.id(), null, "qualquer"),
                    "o estranho adicionou um time");
            helper.assertTrue(data.network(network.id()).members().equals(List.of(guest.getUUID())),
                    "a lista mudou por quem não gerencia");
            helper.succeed();
        } finally {
            close(guest);
            close(stranger);
            data.remove(network.id());
        }
    }

    @GameTest(template = "empty", timeoutTicks = 300)
    public static void onlyTheOwnerSeesTheAccessListAndTheGuestSeesTheNetwork(GameTestHelper helper) throws Exception {
        ServerPlayer owner = playerWithTablet(helper);
        ServerPlayer guest = playerWithTablet(helper);
        ServerPlayer stranger = playerWithTablet(helper);
        NetworkSavedData data = NetworkSavedData.get(helper.getLevel().getServer());
        WaNetwork network = data.create(owner.getUUID(), "teste-acesso-snapshot").withMember(guest.getUUID());
        data.update(network);
        try {
            TabletMenu ownerMenu = open(owner, OWNER_MENU);
            TabletMenu guestMenu = open(guest, GUEST_MENU);
            TabletMenu strangerMenu = open(stranger, 313);

            NetworkView mine = fresh(ownerMenu).network(network.id()).orElseThrow();
            helper.assertTrue(mine.manageable() && mine.access().guests().size() == 1
                    && mine.access().guests().get(0).id().equals(guest.getUUID())
                    && mine.access().guests().get(0).online(), "o dono não vê o convidado online");

            NetworkView theirs = fresh(guestMenu).network(network.id()).orElse(null);
            helper.assertTrue(theirs != null, "o convidado não vê a rede dele no Tablet");
            helper.assertTrue(!theirs.manageable() && theirs.access().isEmpty(),
                    "o convidado recebeu a lista de acesso ou pode gerenciar");
            helper.assertTrue(fresh(strangerMenu).network(network.id()).isEmpty(), "o estranho vê a rede privada");

            TabletSnapshot snapshot = fresh(ownerMenu);
            RegistryFriendlyByteBuf buf = new RegistryFriendlyByteBuf(Unpooled.buffer(),
                    helper.getLevel().registryAccess());
            TabletSnapshot.HEADER_CODEC.encode(buf, snapshot);
            TabletSnapshot decoded = TabletSnapshot.HEADER_CODEC.decode(buf);
            helper.assertTrue(decoded.network(network.id()).orElseThrow().equals(mine),
                    "a rede mudou na ida e volta pelo pacote");
            helper.succeed();
        } finally {
            close(owner);
            close(guest);
            close(stranger);
            data.remove(network.id());
        }
    }

    @GameTest(template = "empty", timeoutTicks = 300, batch = "wirelessautomate_config_access_actions")
    public static void theActionFollowsTheConfigLimit(GameTestHelper helper) {
        ServerPlayer owner = playerWithTablet(helper);
        ServerPlayer one = TestPlayers.menuOpener(helper);
        ServerPlayer two = TestPlayers.menuOpener(helper);
        NetworkSavedData data = NetworkSavedData.get(helper.getLevel().getServer());
        WaNetwork network = data.create(owner.getUUID(), "teste-acesso-limite");
        int before = Config.MAX_MEMBERS_PER_NETWORK.get();
        try {
            Config.MAX_MEMBERS_PER_NETWORK.set(1);
            open(owner, OWNER_MENU);
            helper.assertTrue(act(owner, OWNER_MENU, Action.NETWORK_MEMBER_ADD, network.id(), one.getUUID(), ""),
                    "o primeiro convidado foi recusado");
            helper.assertFalse(act(owner, OWNER_MENU, Action.NETWORK_MEMBER_ADD, network.id(), two.getUUID(), ""),
                    "passou do limite de convidados");
            helper.succeed();
        } finally {
            Config.MAX_MEMBERS_PER_NETWORK.set(before);
            close(owner);
            data.remove(network.id());
        }
    }

    @GameTest(template = "empty", timeoutTicks = 300)
    public static void teamActionsAreRefusedWithoutTheTeamMod(GameTestHelper helper) {
        ServerPlayer owner = playerWithTablet(helper);
        NetworkSavedData data = NetworkSavedData.get(helper.getLevel().getServer());
        WaNetwork network = data.create(owner.getUUID(), "teste-acesso-sem-ftb");
        try {
            open(owner, OWNER_MENU);
            helper.assertFalse(act(owner, OWNER_MENU, Action.NETWORK_TEAM_ADD, network.id(), UUID.randomUUID(), ""),
                    "adicionou um time sem o FTB Teams");
            helper.assertFalse(act(owner, OWNER_MENU, Action.NETWORK_TEAM_ADD, network.id(), null, "Qualquer"),
                    "adicionou um time pelo nome sem o FTB Teams");
            helper.assertTrue(data.network(network.id()).teams().isEmpty(), "um time entrou sem o FTB Teams");
            helper.succeed();
        } finally {
            close(owner);
            data.remove(network.id());
        }
    }

    @GameTest(template = "empty", timeoutTicks = 300)
    public static void playerSuggestionsAreFilteredMarkedAndOnlyForTheManager(GameTestHelper helper) {
        ServerPlayer owner = playerWithTablet(helper);
        ServerPlayer guest = playerWithTablet(helper);
        NetworkSavedData data = NetworkSavedData.get(helper.getLevel().getServer());
        WaNetwork network = data.create(owner.getUUID(), "teste-acesso-sugestoes").withMember(guest.getUUID());
        data.update(network);
        try {
            List<Suggestion> all = TabletAccess.suggestions(owner, NetworkSuggestPayload.Kind.PLAYER, "", network.id());
            helper.assertTrue(all != null && all.size() <= 8, "sugestões sem limite");
            helper.assertTrue(all.stream().anyMatch(s -> s.id().equals(guest.getUUID()) && s.taken() && s.detail() == 1),
                    "o convidado não aparece marcado como já na rede e online");
            helper.assertTrue(all.stream().noneMatch(s -> s.id().equals(owner.getUUID()) && !s.taken()),
                    "o dono aparece como alguém que dá para convidar");
            List<Suggestion> none = TabletAccess.suggestions(owner, NetworkSuggestPayload.Kind.PLAYER,
                    "zzz-ninguem-assim", network.id());
            helper.assertTrue(none != null && none.isEmpty(), "texto sem resultado trouxe linhas");
            helper.assertTrue(TabletAccess.suggestions(guest, NetworkSuggestPayload.Kind.PLAYER, "", network.id()) == null,
                    "o convidado recebeu sugestões de uma rede que ele não gerencia");
            List<Suggestion> teams = TabletAccess.suggestions(owner, NetworkSuggestPayload.Kind.TEAM, "", network.id());
            helper.assertTrue(teams != null && teams.isEmpty(), "times sem o FTB Teams");
            helper.succeed();
        } finally {
            data.remove(network.id());
        }
    }

    @GameTest(template = "empty", timeoutTicks = 300)
    public static void suggestionRequestsAreRateLimited(GameTestHelper helper) {
        ServerPlayer owner = playerWithTablet(helper);
        NetworkSavedData data = NetworkSavedData.get(helper.getLevel().getServer());
        WaNetwork network = data.create(owner.getUUID(), "teste-acesso-ritmo");
        try {
            int answered = 0;
            for (int i = 0; i < TabletMenu.SUGGEST_PER_SECOND + 5; i++) {
                if (TabletAccess.suggestions(owner, NetworkSuggestPayload.Kind.PLAYER, "", network.id()) != null) {
                    answered++;
                }
            }
            helper.assertTrue(answered >= 1 && answered <= TabletMenu.SUGGEST_PER_SECOND + 1,
                    "as consultas não foram limitadas: respondeu " + answered);
            helper.succeed();
        } finally {
            data.remove(network.id());
        }
    }

    @GameTest(template = "empty", timeoutTicks = 300)
    public static void playersResolveByIdOrByNameOnlyWhenKnown(GameTestHelper helper) {
        ServerPlayer friend = TestPlayers.menuOpener(helper);
        var server = helper.getLevel().getServer();
        helper.assertTrue(friend.getUUID().equals(TabletAccess.resolvePlayer(server, Optional.of(friend.getUUID()), "")),
                "um jogador online não resolveu pelo id");
        helper.assertTrue(TabletAccess.resolvePlayer(server, Optional.of(UUID.randomUUID()), "") == null,
                "um id desconhecido resolveu");
        // os jogadores de teste dividem o nome: qualquer um deles serve, o que importa é resolver um online
        helper.assertTrue(TabletAccess.resolvePlayer(server, Optional.empty(),
                "  " + friend.getGameProfile().getName() + " ") != null, "um jogador online não resolveu pelo nome");
        helper.assertTrue(TabletAccess.resolvePlayer(server, Optional.empty(), "zzz-ninguem-assim") == null,
                "um nome desconhecido resolveu");
        helper.assertTrue(TabletAccess.resolvePlayer(server, Optional.empty(), "   ") == null, "texto vazio resolveu");
        helper.succeed();
    }
}
