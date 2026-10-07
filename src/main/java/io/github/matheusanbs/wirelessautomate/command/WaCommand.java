package io.github.matheusanbs.wirelessautomate.command;

import com.mojang.brigadier.CommandDispatcher;
import io.github.matheusanbs.wirelessautomate.network.NetworkManager;
import io.github.matheusanbs.wirelessautomate.network.TickBudget;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;

/** {@code /wa profile}: profiler embutido do gerenciador. */
public final class WaCommand {
    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("wa")
                .requires(source -> source.hasPermission(2))
                .then(Commands.literal("profile").executes(context -> {
                    NetworkManager manager = NetworkManager.get();
                    TickBudget budget = manager.budget();
                    context.getSource().sendSuccess(() -> Component.translatable(
                            "command.wirelessautomate.profile",
                            manager.nodeCount(),
                            ms(budget.averageUsedNanos()),
                            ms(budget.lastUsedNanos()),
                            ms(budget.limitNanos())), false);
                    return manager.nodeCount();
                })));
    }

    private static String ms(double nanos) {
        return String.format("%.3f", nanos / 1_000_000.0);
    }

    private WaCommand() {
    }
}
