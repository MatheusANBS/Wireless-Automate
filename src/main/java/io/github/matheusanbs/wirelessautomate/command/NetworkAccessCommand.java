package io.github.matheusanbs.wirelessautomate.command;

import com.mojang.authlib.GameProfile;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.DynamicCommandExceptionType;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import io.github.matheusanbs.wirelessautomate.network.NetworkSavedData;
import io.github.matheusanbs.wirelessautomate.network.TeamAccess;
import io.github.matheusanbs.wirelessautomate.network.TeamAccess.TeamInfo;
import io.github.matheusanbs.wirelessautomate.network.WaNetwork;
import java.util.Collection;
import java.util.Locale;
import java.util.UUID;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.GameProfileArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

/**
 * Os comandos de acesso às redes privadas, dentro do {@code /wa network}: {@code invite} e {@code uninvite} um
 * jogador, {@code team add} e {@code team remove} um time do FTB Teams. Valem para o dono da rede e para
 * operadores; as mesmas regras e limites das ações da tela.
 */
final class NetworkAccessCommand {
    private static final DynamicCommandExceptionType NOT_FOUND = new DynamicCommandExceptionType(
            name -> Component.translatable("command.wirelessautomate.network.not_found", name));
    private static final DynamicCommandExceptionType NOT_OWNER = new DynamicCommandExceptionType(
            name -> Component.translatable("command.wirelessautomate.network.not_owner", name));
    private static final DynamicCommandExceptionType OWNER_HAS_ACCESS = new DynamicCommandExceptionType(
            name -> Component.translatable("command.wirelessautomate.network.access.owner", name));
    private static final DynamicCommandExceptionType ALREADY = new DynamicCommandExceptionType(
            name -> Component.translatable("command.wirelessautomate.network.access.exists", name));
    private static final DynamicCommandExceptionType NOT_IN = new DynamicCommandExceptionType(
            name -> Component.translatable("command.wirelessautomate.network.access.absent", name));
    private static final DynamicCommandExceptionType LIMIT = new DynamicCommandExceptionType(
            name -> Component.translatable("command.wirelessautomate.network.access.limit", name));
    private static final DynamicCommandExceptionType NO_TEAM = new DynamicCommandExceptionType(
            name -> Component.translatable("command.wirelessautomate.network.access.team_not_found", name));
    private static final SimpleCommandExceptionType ONE_PLAYER = new SimpleCommandExceptionType(
            Component.translatable("command.wirelessautomate.network.access.one_player"));

    private static final SuggestionProvider<CommandSourceStack> TEAM_NAMES = (context, builder) ->
            SharedSuggestionProvider.suggest(TeamAccess.teams().stream().map(team -> '"' + team.name() + '"'), builder);

    private NetworkAccessCommand() {
    }

    /** Os três literais para pendurar em {@code /wa network}; os nomes de rede vêm do {@code networkNames}. */
    static LiteralArgumentBuilder<CommandSourceStack>[] nodes(SuggestionProvider<CommandSourceStack> networkNames) {
        @SuppressWarnings("unchecked")
        LiteralArgumentBuilder<CommandSourceStack>[] nodes = new LiteralArgumentBuilder[3];
        nodes[0] = Commands.literal("invite")
                .then(Commands.argument("player", GameProfileArgument.gameProfile())
                        .then(Commands.argument("name", StringArgumentType.greedyString())
                                .suggests(networkNames)
                                .executes(context -> player(context, true))));
        nodes[1] = Commands.literal("uninvite")
                .then(Commands.argument("player", GameProfileArgument.gameProfile())
                        .then(Commands.argument("name", StringArgumentType.greedyString())
                                .suggests(networkNames)
                                .executes(context -> player(context, false))));
        nodes[2] = Commands.literal("team")
                .then(Commands.literal("add")
                        .then(Commands.argument("team", StringArgumentType.string())
                                .suggests(TEAM_NAMES)
                                .then(Commands.argument("name", StringArgumentType.greedyString())
                                        .suggests(networkNames)
                                        .executes(context -> team(context, true)))))
                .then(Commands.literal("remove")
                        .then(Commands.argument("team", StringArgumentType.string())
                                .suggests(TEAM_NAMES)
                                .then(Commands.argument("name", StringArgumentType.greedyString())
                                        .suggests(networkNames)
                                        .executes(context -> team(context, false)))));
        return nodes;
    }

    /** A rede, só se o executor a gerencia (dono ou operador). */
    private static WaNetwork managed(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        CommandSourceStack source = context.getSource();
        ServerPlayer player = source.getPlayer();
        String name = StringArgumentType.getString(context, "name").strip();
        NetworkSavedData data = NetworkSavedData.get(source.getServer());
        WaNetwork network = data.find(player == null ? null : player.getUUID(), name);
        if (network == null) {
            throw NOT_FOUND.create(name);
        }
        boolean owner = player != null && network.owner().equals(player.getUUID());
        if (!owner && !source.hasPermission(2)) {
            throw NOT_OWNER.create(network.name());
        }
        return network;
    }

    private static int player(CommandContext<CommandSourceStack> context, boolean add) throws CommandSyntaxException {
        Collection<GameProfile> profiles = GameProfileArgument.getGameProfiles(context, "player");
        if (profiles.size() != 1) {
            throw ONE_PLAYER.create();
        }
        GameProfile profile = profiles.iterator().next();
        WaNetwork network = managed(context);
        CommandSourceStack source = context.getSource();
        NetworkSavedData data = NetworkSavedData.get(source.getServer());
        UUID id = profile.getId();
        if (add) {
            if (network.owner().equals(id)) {
                throw OWNER_HAS_ACCESS.create(profile.getName());
            }
            if (network.members().contains(id)) {
                throw ALREADY.create(profile.getName());
            }
            ServerPlayer player = source.getPlayer();
            if (player != null && data.memberLimitReached(player, network)) {
                throw LIMIT.create(network.name());
            }
            data.update(network.withMember(id));
        } else {
            if (!network.members().contains(id)) {
                throw NOT_IN.create(profile.getName());
            }
            data.update(network.withoutMember(id));
        }
        source.sendSuccess(() -> Component.translatable(
                "command.wirelessautomate.network.access.member." + (add ? "added" : "removed"),
                profile.getName(), network.displayName()), true);
        return 1;
    }

    private static int team(CommandContext<CommandSourceStack> context, boolean add) throws CommandSyntaxException {
        String teamName = StringArgumentType.getString(context, "team").strip();
        WaNetwork network = managed(context);
        CommandSourceStack source = context.getSource();
        NetworkSavedData data = NetworkSavedData.get(source.getServer());
        TeamInfo team = null;
        for (TeamInfo candidate : TeamAccess.teams()) {
            if (candidate.name().toLowerCase(Locale.ROOT).equals(teamName.toLowerCase(Locale.ROOT))) {
                team = candidate;
                break;
            }
        }
        if (add) {
            if (team == null) {
                throw NO_TEAM.create(teamName);
            }
            if (network.teams().contains(team.id())) {
                throw ALREADY.create(team.name());
            }
            ServerPlayer player = source.getPlayer();
            if (player != null && data.teamLimitReached(player, network)) {
                throw LIMIT.create(network.name());
            }
            data.update(network.withTeam(team.id()));
        } else {
            // um time já desfeito não tem mais nome para casar: ele só sai pela tela, onde aparece como removido
            UUID id = team != null ? team.id() : null;
            if (id == null || !network.teams().contains(id)) {
                throw NOT_IN.create(teamName);
            }
            data.update(network.withoutTeam(id));
        }
        source.sendSuccess(() -> Component.translatable(
                "command.wirelessautomate.network.access.team." + (add ? "added" : "removed"),
                teamName, network.displayName()), true);
        return 1;
    }
}
