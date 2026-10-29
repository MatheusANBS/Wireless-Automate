package io.github.matheusanbs.wirelessautomate.tv;

import io.github.matheusanbs.wirelessautomate.menu.TabletAccess;
import io.github.matheusanbs.wirelessautomate.menu.TabletSnapshot.AccessView;
import io.github.matheusanbs.wirelessautomate.menu.TabletSnapshot.Guest;
import io.github.matheusanbs.wirelessautomate.network.KnownPlayers;
import io.github.matheusanbs.wirelessautomate.network.NetworkSavedData;
import io.github.matheusanbs.wirelessautomate.network.TeamAccess;
import io.github.matheusanbs.wirelessautomate.packet.NetworkSuggestPayload;
import io.github.matheusanbs.wirelessautomate.packet.NetworkSuggestionsPayload;
import io.github.matheusanbs.wirelessautomate.packet.NetworkSuggestionsPayload.Suggestion;
import io.github.matheusanbs.wirelessautomate.packet.TvAccessActionPayload;
import io.github.matheusanbs.wirelessautomate.packet.TvAccessPayload;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.Nullable;

/**
 * As ações na lista de acesso da Tela de Sinal, pela aba Acesso: convidar e tirar jogadores (pelo id da sugestão ou
 * pelo nome digitado, como nas redes) e times do FTB Teams, e as sugestões dos campos. Só com o menu aberto, só o dono
 * ou um operador ({@link TvBlockEntity#canManage}), no máximo {@link TvRateGate#PER_SECOND} por segundo. Os tetos são
 * os da rede ({@code maxMembersPerNetwork} e {@code maxTeamsPerNetwork}). A resposta a cada ação, aceita ou recusada,
 * é a lista inteira com o aviso ({@link TvAccessPayload}), só a quem agiu. Tudo por evento: nada por tick.
 */
public final class TvAccessActions {
    private static final TvRateGate GATE = new TvRateGate();

    private TvAccessActions() {
    }

    public static void forget(UUID player) {
        GATE.forget(player);
    }

    public static void reset() {
        GATE.clear();
    }

    /** A lista que o dono vê: o nome de cada convidado (o que o servidor sabe, senão o guardado) e os times. */
    public static AccessView view(MinecraftServer server, TvBlockEntity tv) {
        TvAccess access = tv.access();
        if (access.isEmpty()) {
            return AccessView.EMPTY;
        }
        List<Guest> guests = new ArrayList<>(access.players().size());
        for (TvAccess.Guest guest : access.players()) {
            String name = KnownPlayers.knows(server, guest.id()) ? KnownPlayers.name(server, guest.id()) : guest.name();
            guests.add(new Guest(guest.id(), name, KnownPlayers.isOnline(server, guest.id())));
        }
        return new AccessView(guests, TabletAccess.teamInfos(access.teams()));
    }

    /**
     * Trata o pacote. Devolve a resposta mandada (a lista com o aviso, ou as sugestões), ou {@code null} quando nada
     * foi respondido: sem o menu aberto, quem não gerencia a Tela ou acima do limite por segundo.
     */
    public static @Nullable CustomPacketPayload handle(@Nullable ServerPlayer player, TvAccessActionPayload payload) {
        TvBlockEntity tv = TvEditing.open(player, payload.containerId());
        if (tv == null || !tv.canManage(player) || !GATE.allow(player.getUUID(), player.level().getGameTime())) {
            return null;
        }
        CustomPacketPayload reply = switch (payload.action()) {
            case SUGGEST_PLAYER -> new NetworkSuggestionsPayload(NetworkSuggestPayload.Kind.PLAYER, payload.text(),
                    TabletAccess.players(player.server, id -> taken(tv, id), payload.text()));
            case SUGGEST_TEAM -> new NetworkSuggestionsPayload(NetworkSuggestPayload.Kind.TEAM, payload.text(),
                    TabletAccess.teams(tv.access().teams()::contains, payload.text()));
            default -> apply(player, tv, payload);
        };
        // jogadores falsos (GameTests) não negociam os canais do mod
        if (player.connection != null && player.connection.hasChannel(reply)) {
            PacketDistributor.sendToPlayer(player, reply);
        }
        return reply;
    }

    /** O dono e quem já está na lista aparecem apagados nas sugestões. */
    private static boolean taken(TvBlockEntity tv, UUID id) {
        return id.equals(tv.owner()) || tv.access().hasPlayer(id);
    }

    private static TvAccessPayload apply(ServerPlayer player, TvBlockEntity tv, TvAccessActionPayload payload) {
        MinecraftServer server = player.server;
        TvAccess access = tv.access();
        String notice;
        String arg = "";
        switch (payload.action()) {
            case ADD_PLAYER -> {
                UUID id = TabletAccess.resolvePlayer(server, payload.id(), payload.text());
                if (id == null) {
                    notice = "refused.known";
                } else if (id.equals(tv.owner())) {
                    notice = "refused.owner";
                } else if (access.hasPlayer(id)) {
                    notice = "refused.exists";
                } else if (NetworkSavedData.memberCapReached(player, access.players().size())) {
                    notice = "refused.limit";
                } else {
                    arg = KnownPlayers.name(server, id);
                    tv.setAccess(access.withPlayer(id, arg));
                    notice = "member.added";
                }
            }
            case REMOVE_PLAYER -> {
                UUID id = payload.id().orElse(null);
                if (id == null || !access.hasPlayer(id)) {
                    notice = "refused.known";
                } else {
                    arg = nameOf(server, access, id);
                    tv.setAccess(access.withoutPlayer(id));
                    notice = "member.removed";
                }
            }
            case ADD_TEAM -> {
                UUID id = TabletAccess.resolveTeam(payload.id(), payload.text());
                if (id == null) {
                    notice = "refused.team";
                } else if (access.teams().contains(id)) {
                    notice = "refused.exists";
                } else if (NetworkSavedData.teamCapReached(player, access.teams().size())) {
                    notice = "refused.limit";
                } else {
                    arg = teamName(id);
                    tv.setAccess(access.withTeam(id));
                    notice = "team.added";
                }
            }
            case REMOVE_TEAM -> {
                UUID id = payload.id().orElse(null);
                if (id == null || !access.teams().contains(id)) {
                    notice = "refused.team";
                } else {
                    arg = teamName(id);
                    tv.setAccess(access.withoutTeam(id));
                    notice = "team.removed";
                }
            }
            default -> throw new IllegalArgumentException(payload.action().name());
        }
        return new TvAccessPayload(payload.containerId(), view(server, tv), notice, clip(arg));
    }

    private static String nameOf(MinecraftServer server, TvAccess access, UUID id) {
        if (KnownPlayers.knows(server, id)) {
            return KnownPlayers.name(server, id);
        }
        for (TvAccess.Guest guest : access.players()) {
            if (guest.id().equals(id)) {
                return guest.name();
            }
        }
        return id.toString().substring(0, 8);
    }

    private static String teamName(UUID id) {
        TeamAccess.TeamInfo info = TeamAccess.info(id);
        return info == null ? "" : info.name();
    }

    /** O pacote leva o nome em até 64 caracteres. */
    private static String clip(String text) {
        return text.length() > 64 ? text.substring(0, 64) : text;
    }
}
