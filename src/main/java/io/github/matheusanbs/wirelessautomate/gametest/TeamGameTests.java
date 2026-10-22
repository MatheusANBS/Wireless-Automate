package io.github.matheusanbs.wirelessautomate.gametest;

import io.github.matheusanbs.wirelessautomate.menu.TabletAccess;
import io.github.matheusanbs.wirelessautomate.menu.TabletMenu;
import io.github.matheusanbs.wirelessautomate.menu.TabletMenu.Action;
import io.github.matheusanbs.wirelessautomate.menu.TabletSnapshot.NetworkView;
import io.github.matheusanbs.wirelessautomate.network.NetworkSavedData;
import io.github.matheusanbs.wirelessautomate.network.TeamAccess;
import io.github.matheusanbs.wirelessautomate.network.TeamAccess.TeamInfo;
import io.github.matheusanbs.wirelessautomate.network.WaNetwork;
import io.github.matheusanbs.wirelessautomate.packet.NetworkSuggestPayload;
import io.github.matheusanbs.wirelessautomate.packet.NetworkSuggestionsPayload.Suggestion;
import io.github.matheusanbs.wirelessautomate.packet.TabletActionPayload;
import io.github.matheusanbs.wirelessautomate.packet.TabletPayloads;
import io.github.matheusanbs.wirelessautomate.registry.ModItems;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * Os times do FTB Teams como acesso a uma rede privada. Rodam só na run {@code runGameTestServerTeams}, que tem o
 * FTB Teams na pasta mods e liga o namespace {@value #NAMESPACE} (o template é
 * {@code data/wirelessautomate_teams/structure/empty.nbt}). Sem o FTB, só passam.
 *
 * <p>Nenhum tipo do FTB nas assinaturas: o que usa a API fica em {@link TeamTestSupport}.
 */
@GameTestHolder(TeamGameTests.NAMESPACE)
@PrefixGameTestTemplate(false)
public final class TeamGameTests {
    static final String NAMESPACE = "wirelessautomate_teams";

    private static boolean enabled() {
        return Boolean.getBoolean("wirelessautomate.teamTests");
    }

    private static WaNetwork networkWith(UUID team) {
        return new WaNetwork(UUID.randomUUID(), "times", 0x336699, UUID.randomUUID()).withTeam(team);
    }

    @GameTest(template = "empty")
    public static void ftbTeamsIsLoaded(GameTestHelper helper) {
        if (!enabled()) {
            helper.succeed();
            return;
        }
        helper.assertTrue(ModList.get().isLoaded("ftbteams"), "o FTB Teams não carregou na run de times");
        helper.assertTrue(TeamAccess.LOADED, "a ponte não viu o FTB Teams");
        helper.assertTrue(TeamTestSupport.managerLoaded(), "o gerenciador de times não carregou");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 300)
    public static void teamMembersGetAccessLiveAndLoseItWhenTheyLeave(GameTestHelper helper) throws Exception {
        if (!enabled()) {
            helper.succeed();
            return;
        }
        ServerPlayer owner = TestPlayers.inLevel(helper);
        UUID team = TeamTestSupport.createParty(owner, "mineiros-teste");
        ServerPlayer friendPlayer = TestPlayers.menuOpener(helper);
        UUID friend = friendPlayer.getUUID();
        UUID stranger = UUID.randomUUID();
        try {
            WaNetwork network = networkWith(team);
            helper.assertTrue(network.canUse(owner.getUUID(), false), "o dono do time não usa a rede do time");
            helper.assertTrue(network.canEdit(owner.getUUID(), false), "o dono do time não edita a rede do time");
            helper.assertFalse(network.canManage(owner.getUUID(), false), "membro de time gerencia a rede");
            helper.assertFalse(network.canUse(friend, false), "o amigo usa antes de entrar no time");

            TeamTestSupport.join(team, friend, friendPlayer.getGameProfile().getName());
            helper.assertTrue(network.canUse(friend, false), "quem entrou no time não ganhou acesso");
            helper.assertFalse(network.canUse(stranger, false), "um estranho ao time usa a rede privada");

            TeamTestSupport.leave(team, friend);
            helper.assertFalse(network.canUse(friend, false), "quem saiu do time manteve o acesso");
            helper.succeed();
        } finally {
            if (TeamAccess.info(team) != null && !TeamAccess.info(team).gone()) {
                TeamTestSupport.disband(helper.getLevel().getServer(), team);
            }
        }
    }

    @GameTest(template = "empty", timeoutTicks = 300)
    public static void aDisbandedTeamGrantsNothingAndShowsAsGone(GameTestHelper helper) throws Exception {
        if (!enabled()) {
            helper.succeed();
            return;
        }
        ServerPlayer owner = TestPlayers.inLevel(helper);
        UUID team = TeamTestSupport.createParty(owner, "efemero-teste");
        WaNetwork network = networkWith(team);
        helper.assertTrue(network.canUse(owner.getUUID(), false), "o dono do time não usa a rede do time");
        TeamInfo before = TeamAccess.info(team);
        helper.assertTrue(before != null && !before.gone() && before.members() >= 1
                && before.name().equals("efemero-teste"), "informação do time viva");

        TeamTestSupport.disband(helper.getLevel().getServer(), team);
        helper.assertFalse(network.canUse(owner.getUUID(), false), "um time desfeito ainda dá acesso");
        TeamInfo after = TeamAccess.info(team);
        helper.assertTrue(after != null && after.gone() && after.members() == 0, "o time desfeito não aparece como removido");
        helper.assertTrue(TeamAccess.teams().stream().noneMatch(t -> t.id().equals(team)),
                "o time desfeito continua na lista");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 300)
    public static void teamListHasPartyTeamsOnly(GameTestHelper helper) throws Exception {
        if (!enabled()) {
            helper.succeed();
            return;
        }
        ServerPlayer owner = TestPlayers.inLevel(helper);
        UUID team = TeamTestSupport.createParty(owner, "lista-teste");
        try {
            helper.assertTrue(TeamAccess.teams().stream().anyMatch(t -> t.id().equals(team) && !t.gone()),
                    "o time de grupo não está na lista");
            UUID personal = TeamTestSupport.personalTeam(owner.getUUID());
            helper.assertTrue(TeamAccess.teams().stream().noneMatch(t -> t.id().equals(personal)),
                    "o time pessoal entrou na lista");
            helper.assertFalse(TeamAccess.isMember(personal, owner.getUUID()),
                    "o time pessoal valeu como time de rede");
            helper.succeed();
        } finally {
            TeamTestSupport.disband(helper.getLevel().getServer(), team);
        }
    }
    private static ServerPlayer playerWithTablet(GameTestHelper helper) {
        ServerPlayer player = TestPlayers.menuOpener(helper);
        player.moveTo(Vec3.atCenterOf(helper.absolutePos(new BlockPos(0, 1, 0)).above(2)));
        player.getInventory().setItem(0, new ItemStack(ModItems.NETWORK_TABLET.get()));
        player.containerMenu = new TabletMenu(321, player.getInventory(), player);
        return player;
    }

    private static boolean act(ServerPlayer player, Action action, UUID network, UUID other, String text) {
        return TabletPayloads.handleAction(player, new TabletActionPayload(321, action, Optional.of(network),
                Optional.ofNullable(other), text, 0));
    }

    @GameTest(template = "empty", timeoutTicks = 300)
    public static void theOwnerAddsATeamByIdOrByNameAndSeesItInTheList(GameTestHelper helper) throws Exception {
        if (!enabled()) {
            helper.succeed();
            return;
        }
        ServerPlayer manager = playerWithTablet(helper);
        ServerPlayer leader = TestPlayers.menuOpener(helper);
        UUID team = TeamTestSupport.createParty(leader, "acao-teste");
        NetworkSavedData data = NetworkSavedData.get(helper.getLevel().getServer());
        WaNetwork network = data.create(manager.getUUID(), "teste-times-acao");
        try {
            helper.assertTrue(TabletAccess.resolveTeam(Optional.empty(), "  ACAO-teste ").equals(team),
                    "o nome do time não resolveu sem diferenciar maiúsculas");
            helper.assertTrue(TabletAccess.resolveTeam(Optional.empty(), "acao") == null,
                    "um pedaço do nome resolveu");
            helper.assertTrue(act(manager, Action.NETWORK_TEAM_ADD, network.id(), null, "acao-teste"),
                    "adicionar o time pelo nome");
            helper.assertFalse(act(manager, Action.NETWORK_TEAM_ADD, network.id(), team, ""), "adicionou o time de novo");
            helper.assertTrue(data.network(network.id()).teams().equals(List.of(team)), "o time não entrou");
            helper.assertTrue(data.network(network.id()).canUse(leader.getUUID(), false),
                    "o líder do time não ganhou acesso");

            ((TabletMenu) manager.containerMenu).setQuery(((TabletMenu) manager.containerMenu).query());
            ((TabletMenu) manager.containerMenu).pollSnapshot();
            NetworkView view = ((TabletMenu) manager.containerMenu).snapshot().network(network.id()).orElseThrow();
            helper.assertTrue(view.access().teams().size() == 1 && view.access().teams().get(0).id().equals(team)
                    && !view.access().teams().get(0).gone() && view.access().teams().get(0).members() >= 1,
                    "o time não aparece na lista de acesso do dono");

            List<Suggestion> found = TabletAccess.suggestions(manager, NetworkSuggestPayload.Kind.TEAM, "acao",
                    network.id());
            helper.assertTrue(found != null && found.size() == 1 && found.get(0).id().equals(team)
                    && found.get(0).taken() && found.get(0).detail() >= 1, "sugestão do time");

            TeamTestSupport.disband(helper.getLevel().getServer(), team);
            helper.assertFalse(data.network(network.id()).canUse(leader.getUUID(), false),
                    "um time desfeito ainda dá acesso");
            helper.assertTrue(act(manager, Action.NETWORK_TEAM_REMOVE, network.id(), team, ""),
                    "não tirou o time que já foi desfeito");
            helper.assertTrue(data.network(network.id()).teams().isEmpty(), "o time desfeito ficou na rede");
            helper.succeed();
        } finally {
            manager.containerMenu = manager.inventoryMenu;
            data.remove(network.id());
            if (TeamAccess.info(team) != null && !TeamAccess.info(team).gone()) {
                TeamTestSupport.disband(helper.getLevel().getServer(), team);
            }
        }
    }
}
