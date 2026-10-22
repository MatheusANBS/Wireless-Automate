package io.github.matheusanbs.wirelessautomate.command;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.DynamicCommandExceptionType;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import io.github.matheusanbs.wirelessautomate.menu.TabletAccess;
import io.github.matheusanbs.wirelessautomate.network.KnownPlayers;
import io.github.matheusanbs.wirelessautomate.network.NetworkSavedData;
import io.github.matheusanbs.wirelessautomate.network.WaNetwork;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
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
    private static final DynamicCommandExceptionType UNKNOWN_PLAYER = new DynamicCommandExceptionType(
            name -> Component.translatable("command.wirelessautomate.network.access.unknown_player", name));

    private NetworkAccessCommand() {
    }

    /** Os três literais para pendurar em {@code /wa network}; os nomes de rede vêm do {@code networkNames}. */
    static LiteralArgumentBuilder<CommandSourceStack>[] nodes(SuggestionProvider<CommandSourceStack> networkNames) {
        @SuppressWarnings("unchecked")
        LiteralArgumentBuilder<CommandSourceStack>[] nodes = new LiteralArgumentBuilder[3];
        nodes[0] = Commands.literal("invite")
                .then(Commands.argument("player", StringArgumentType.word())
                        .then(Commands.argument("name", StringArgumentType.greedyString())
                                .suggests(networkNames)
                                .executes(context -> player(context, true))));
        nodes[1] = Commands.literal("uninvite")
                .then(Commands.argument("player", StringArgumentType.word())
                        .then(Commands.argument("name", StringArgumentType.greedyString())
                                .suggests(networkNames)
                                .executes(context -> player(context, false))));
        nodes[2] = Commands.literal("team")
                .then(Commands.literal("add")
                        .then(Commands.argument("team", StringArgumentType.string())
                                .then(Commands.argument("name", StringArgumentType.greedyString())
                                        .suggests(networkNames)
                                        .executes(context -> team(context, true)))))
                .then(Commands.literal("remove")
                        .then(Commands.argument("team", StringArgumentType.string())
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
        WaNetwork network = managed(context);
        CommandSourceStack source = context.getSource();
        NetworkSavedData data = NetworkSavedData.get(source.getServer());
        String typed = StringArgumentType.getString(context, "player").strip();
        UUID id = KnownPlayers.byName(source.getServer(), typed);
        if (id == null) {
            throw UNKNOWN_PLAYER.create(typed);
        }
        String name = KnownPlayers.name(source.getServer(), id);
        if (add) {
            if (network.owner().equals(id)) {
                throw OWNER_HAS_ACCESS.create(name);
            }
            if (network.members().contains(id)) {
                throw ALREADY.create(name);
            }
            ServerPlayer player = source.getPlayer();
            if (player != null && data.memberLimitReached(player, network)) {
                throw LIMIT.create(network.name());
            }
            data.update(network.withMember(id));
        } else {
            if (!network.members().contains(id)) {
                throw NOT_IN.create(name);
            }
            data.update(network.withoutMember(id));
        }
        source.sendSuccess(() -> Component.translatable(
                "command.wirelessautomate.network.access.member." + (add ? "added" : "removed"),
                name, network.displayName()), true);
        return 1;
    }

    private static int team(CommandContext<CommandSourceStack> context, boolean add) throws CommandSyntaxException {
        String teamName = StringArgumentType.getString(context, "team").strip();
        WaNetwork network = managed(context);
        CommandSourceStack source = context.getSource();
        NetworkSavedData data = NetworkSavedData.get(source.getServer());
        // o mesmo critério da tela: nome exato e único; um nome repetido não escolhe um time ao acaso
        UUID id = TabletAccess.resolveTeam(Optional.empty(), teamName);
        if (add) {
            if (id == null) {
                throw NO_TEAM.create(teamName);
            }
            if (network.teams().contains(id)) {
                throw ALREADY.create(teamName);
            }
            ServerPlayer player = source.getPlayer();
            if (player != null && data.teamLimitReached(player, network)) {
                throw LIMIT.create(network.name());
            }
            data.update(network.withTeam(id));
        } else {
            // um time já desfeito não tem mais nome para casar: ele só sai pela tela, onde aparece como removido
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
