package io.github.matheusanbs.wirelessautomate.command;

import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.DynamicCommandExceptionType;
import io.github.matheusanbs.wirelessautomate.item.TierCoreItem;
import io.github.matheusanbs.wirelessautomate.resonator.ResonanceCoreBlockEntity;
import io.github.matheusanbs.wirelessautomate.resonator.ResonatorStatus;
import io.github.matheusanbs.wirelessautomate.resonator.Tuning;
import java.util.Locale;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.coordinates.BlockPosArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;

/**
 * {@code /wa resonator <pos>}: {@code info} mostra a sintonia, a geração, o buffer, os cristais, a redstone e o modo
 * de um Núcleo de Ressonância; {@code dial <mhz>} gira o dial dele (no passo de 0,05 MHz). Só para operadores.
 */
public final class ResonatorCommand {
    private static final String KEY = "command.wirelessautomate.resonator.";
    private static final DynamicCommandExceptionType NOT_CORE = new DynamicCommandExceptionType(
            pos -> Component.translatable(KEY + "not_core", pos));

    private ResonatorCommand() {
    }

    static LiteralArgumentBuilder<CommandSourceStack> node() {
        return Commands.literal("resonator")
                .requires(source -> source.hasPermission(2))
                .then(Commands.argument("pos", BlockPosArgument.blockPos())
                        .then(Commands.literal("info").executes(context -> info(context.getSource(), core(context))))
                        .then(Commands.literal("dial")
                                .then(Commands.argument("mhz", DoubleArgumentType.doubleArg(Tuning.MIN_MHZ, Tuning.MAX_MHZ))
                                        .executes(context -> dial(context.getSource(), core(context),
                                                DoubleArgumentType.getDouble(context, "mhz"))))));
    }

    private static ResonanceCoreBlockEntity core(CommandContext<CommandSourceStack> context)
            throws CommandSyntaxException {
        ServerLevel level = context.getSource().getLevel();
        BlockPos pos = BlockPosArgument.getLoadedBlockPos(context, level, "pos");
        if (!(level.getBlockEntity(pos) instanceof ResonanceCoreBlockEntity core)) {
            throw NOT_CORE.create(pos.toShortString());
        }
        return core;
    }

    private static int info(CommandSourceStack source, ResonanceCoreBlockEntity core) {
        Component tuning = Component.translatable(KEY + "info.tuning", mhz(core.station()), mhz(core.dial()),
                mhz(Math.abs(core.station() - core.dial())), Math.round(core.efficiency() * 100));
        Component output = Component.translatable(KEY + "info.output", TierCoreItem.grouped(core.generationPerTick()),
                TierCoreItem.grouped(core.energy()), TierCoreItem.grouped(core.capacity()));
        Component contents = Component.translatable(KEY + "info.contents", ResonatorStatus.crystals(core), core.chambers(),
                core.redstone(), ResonanceCoreBlockEntity.MAX_REDSTONE);
        Component mode = Component.translatable(KEY + "info.mode", ResonatorStatus.mode(core),
                Component.translatable(KEY + (core.hasTuner() ? "yes" : "no")));
        source.sendSuccess(() -> Component.translatable(KEY + "info.header",
                Component.translatable(core.tier().translationKey()), core.getBlockPos().toShortString(),
                Component.translatable(core.formed() ? KEY + "formed" : KEY + "loose")), false);
        source.sendSuccess(() -> tuning, false);
        source.sendSuccess(() -> output, false);
        source.sendSuccess(() -> contents, false);
        source.sendSuccess(() -> mode, false);
        return 1;
    }

    private static int dial(CommandSourceStack source, ResonanceCoreBlockEntity core, double mhz) {
        core.setDial(mhz);
        source.sendSuccess(() -> Component.translatable(KEY + "dial", mhz(core.dial())), true);
        return 1;
    }

    private static String mhz(double value) {
        return String.format(Locale.ROOT, "%.2f", value);
    }
}
