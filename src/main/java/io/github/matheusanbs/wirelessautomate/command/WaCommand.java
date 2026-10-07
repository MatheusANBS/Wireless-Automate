package io.github.matheusanbs.wirelessautomate.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.DynamicCommandExceptionType;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import io.github.matheusanbs.wirelessautomate.bench.BenchCommand;
import io.github.matheusanbs.wirelessautomate.block.RouterBlockEntity;
import io.github.matheusanbs.wirelessautomate.network.NetworkManager;
import io.github.matheusanbs.wirelessautomate.network.NetworkSavedData;
import io.github.matheusanbs.wirelessautomate.network.NetworkStats;
import io.github.matheusanbs.wirelessautomate.network.PortMode;
import io.github.matheusanbs.wirelessautomate.network.ResourceType;
import io.github.matheusanbs.wirelessautomate.network.TickBudget;
import io.github.matheusanbs.wirelessautomate.network.WaNetwork;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.coordinates.BlockPosArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import org.jetbrains.annotations.Nullable;

/**
 * {@code /wa}: profiler embutido ({@code profile}), gerência de redes ({@code network}) e
 * configuração de faces sem tela ({@code face}), usada em testes manuais antes das telas.
 */
public final class WaCommand {
    public static final int MAX_NAME_LENGTH = 32;

    private static final Map<String, ResourceType> TYPES = new LinkedHashMap<>();
    private static final Map<String, Direction> FACES = new LinkedHashMap<>();
    private static final Map<String, PortMode> MODES = new LinkedHashMap<>();

    static {
        TYPES.put("item", ResourceType.ITEM);
        TYPES.put("fluid", ResourceType.FLUID);
        TYPES.put("energy", ResourceType.ENERGY);
        for (Direction direction : Direction.values()) {
            FACES.put(direction.getName(), direction);
        }
        for (PortMode mode : PortMode.values()) {
            MODES.put(mode.name().toLowerCase(Locale.ROOT), mode);
        }
    }

    private static final DynamicCommandExceptionType INVALID_VALUE = new DynamicCommandExceptionType(
            value -> Component.translatable("command.wirelessautomate.invalid_value", value));
    private static final SimpleCommandExceptionType INVALID_NAME = new SimpleCommandExceptionType(
            Component.translatable("command.wirelessautomate.network.invalid_name", MAX_NAME_LENGTH));
    private static final DynamicCommandExceptionType NAME_TAKEN = new DynamicCommandExceptionType(
            name -> Component.translatable("command.wirelessautomate.network.exists", name));
    private static final DynamicCommandExceptionType NOT_FOUND = new DynamicCommandExceptionType(
            name -> Component.translatable("command.wirelessautomate.network.not_found", name));
    private static final DynamicCommandExceptionType NOT_OWNER = new DynamicCommandExceptionType(
            name -> Component.translatable("command.wirelessautomate.network.not_owner", name));
    private static final DynamicCommandExceptionType NOT_ROUTER = new DynamicCommandExceptionType(
            pos -> Component.translatable("command.wirelessautomate.face.not_router", pos));

    private static final SuggestionProvider<CommandSourceStack> NETWORK_NAMES = (context, builder) ->
            SharedSuggestionProvider.suggest(NetworkSavedData.get(context.getSource().getServer()).networks()
                    .stream().map(WaNetwork::name), builder);

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("wa")
                .then(Commands.literal("profile")
                        .requires(source -> source.hasPermission(2))
                        .executes(context -> profile(context.getSource())))
                .then(Commands.literal("network")
                        .then(Commands.literal("list")
                                .executes(context -> list(context.getSource())))
                        .then(Commands.literal("create")
                                .then(Commands.argument("name", StringArgumentType.greedyString())
                                        .executes(context -> create(context.getSource(), name(context)))))
                        .then(Commands.literal("use")
                                .then(Commands.argument("name", StringArgumentType.greedyString())
                                        .suggests(NETWORK_NAMES)
                                        .executes(context -> use(context.getSource(), name(context)))))
                        .then(Commands.literal("remove")
                                .then(Commands.argument("name", StringArgumentType.greedyString())
                                        .suggests(NETWORK_NAMES)
                                        .executes(context -> remove(context.getSource(), name(context))))))
                .then(Commands.literal("face")
                        .requires(source -> source.hasPermission(2))
                        .then(Commands.argument("pos", BlockPosArgument.blockPos())
                                .then(word("type", TYPES.keySet())
                                        .then(word("face", FACES.keySet())
                                                .then(word("mode", MODES.keySet())
                                                        .executes(context -> face(context, null))
                                                        .then(Commands.argument("priority", IntegerArgumentType.integer())
                                                                .executes(context -> face(context,
                                                                        IntegerArgumentType.getInteger(context, "priority")))))))))
                .then(BenchCommand.node()));
    }

    /**
     * Total de nós e orçamento, depois uma linha por rede. Os nós de uma rede são os que têm algum
     * tipo nela (ver {@link NetworkStats#nodes()}), então a soma das linhas pode passar do total.
     */
    private static int profile(CommandSourceStack source) {
        NetworkManager manager = NetworkManager.get();
        TickBudget budget = manager.budget();
        source.sendSuccess(() -> Component.translatable(
                "command.wirelessautomate.profile",
                manager.nodeCount(),
                ms(budget.averageUsedNanos()),
                ms(budget.lastUsedNanos()),
                ms(budget.limitNanos())), false);
        NetworkSavedData data = NetworkSavedData.get(source.getServer());
        int shown = 0;
        for (NetworkStats stats : manager.stats(source.getServer())) {
            WaNetwork network = data.network(stats.id());
            if (network == null) {
                continue;
            }
            shown++;
            source.sendSuccess(() -> Component.translatable("command.wirelessautomate.profile.network",
                    network.displayName(),
                    stats.nodes(),
                    ms(stats.averageNanos()),
                    ms(stats.lastNanos()),
                    stats.opsLastSecond(),
                    stats.sourcesAwake(),
                    stats.sourcesSleeping(),
                    stats.destinationsAwake(),
                    stats.destinationsSleeping()), false);
        }
        if (shown == 0) {
            source.sendSuccess(() -> Component.translatable("command.wirelessautomate.profile.no_networks"), false);
        }
        return manager.nodeCount();
    }

    private static int list(CommandSourceStack source) {
        NetworkSavedData data = NetworkSavedData.get(source.getServer());
        Collection<WaNetwork> networks = data.networks();
        if (networks.isEmpty()) {
            source.sendSuccess(() -> Component.translatable("command.wirelessautomate.network.list.empty"), false);
            return 0;
        }
        ServerPlayer player = source.getPlayer();
        UUID active = player == null ? null : data.activeNetwork(player.getUUID());
        source.sendSuccess(() -> Component.translatable("command.wirelessautomate.network.list.header",
                networks.size()), false);
        for (WaNetwork network : networks) {
            MutableComponent line = Component.translatable("command.wirelessautomate.network.list.entry",
                    Component.literal("■").withStyle(style -> style.withColor(network.color())),
                    network.displayName(),
                    String.format("#%06X", network.color()));
            if (network.id().equals(active)) {
                line.append(Component.translatable("command.wirelessautomate.network.list.active")
                        .withStyle(ChatFormatting.GREEN));
            }
            source.sendSuccess(() -> line, false);
        }
        return networks.size();
    }

    private static int create(CommandSourceStack source, String name) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        if (name.isEmpty() || name.length() > MAX_NAME_LENGTH) {
            throw INVALID_NAME.create();
        }
        NetworkSavedData data = NetworkSavedData.get(source.getServer());
        if (data.byName(player.getUUID(), name) != null) {
            throw NAME_TAKEN.create(name);
        }
        WaNetwork network = data.create(player.getUUID(), name);
        data.setActiveNetwork(player.getUUID(), network.id());
        source.sendSuccess(() -> Component.translatable("command.wirelessautomate.network.created",
                network.displayName()), false);
        return 1;
    }

    private static int use(CommandSourceStack source, String name) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        NetworkSavedData data = NetworkSavedData.get(source.getServer());
        WaNetwork network = data.find(player.getUUID(), name);
        if (network == null) {
            throw NOT_FOUND.create(name);
        }
        data.setActiveNetwork(player.getUUID(), network.id());
        source.sendSuccess(() -> Component.translatable("command.wirelessautomate.network.active",
                network.displayName()), false);
        return 1;
    }

    private static int remove(CommandSourceStack source, String name) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayer();
        NetworkSavedData data = NetworkSavedData.get(source.getServer());
        WaNetwork network = data.find(player == null ? null : player.getUUID(), name);
        if (network == null) {
            throw NOT_FOUND.create(name);
        }
        boolean owner = player != null && network.owner().equals(player.getUUID());
        if (!owner && !source.hasPermission(2)) {
            throw NOT_OWNER.create(network.name());
        }
        data.remove(network.id());
        source.sendSuccess(() -> Component.translatable("command.wirelessautomate.network.removed",
                network.displayName()), true);
        return 1;
    }

    private static int face(CommandContext<CommandSourceStack> context, @Nullable Integer priority)
            throws CommandSyntaxException {
        ServerLevel level = context.getSource().getLevel();
        BlockPos pos = BlockPosArgument.getLoadedBlockPos(context, level, "pos");
        if (!(level.getBlockEntity(pos) instanceof RouterBlockEntity router)) {
            throw NOT_ROUTER.create(pos.toShortString());
        }
        String typeName = StringArgumentType.getString(context, "type");
        String faceName = StringArgumentType.getString(context, "face");
        String modeName = StringArgumentType.getString(context, "mode");
        ResourceType type = parse(TYPES, typeName);
        Direction face = parse(FACES, faceName);
        PortMode mode = parse(MODES, modeName);

        router.setMode(type, face, mode);
        Component message;
        if (priority == null) {
            message = Component.translatable("command.wirelessautomate.face.set",
                    pos.toShortString(), typeName, faceName, modeName);
        } else {
            router.setPriority(type, face, priority);
            message = Component.translatable("command.wirelessautomate.face.set_priority",
                    pos.toShortString(), typeName, faceName, modeName, priority);
        }
        context.getSource().sendSuccess(() -> message, false);
        return 1;
    }

    private static String name(CommandContext<CommandSourceStack> context) {
        return StringArgumentType.getString(context, "name").trim();
    }

    private static RequiredArgumentBuilder<CommandSourceStack, String> word(String name, Collection<String> values) {
        return Commands.argument(name, StringArgumentType.word())
                .suggests((context, builder) -> SharedSuggestionProvider.suggest(values, builder));
    }

    private static <T> T parse(Map<String, T> values, String key) throws CommandSyntaxException {
        T value = values.get(key.toLowerCase(Locale.ROOT));
        if (value == null) {
            throw INVALID_VALUE.create(key);
        }
        return value;
    }

    private static String ms(double nanos) {
        return String.format("%.3f", nanos / 1_000_000.0);
    }

    private WaCommand() {
    }
}
