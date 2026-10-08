package io.github.matheusanbs.wirelessautomate.command;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.exceptions.DynamicCommandExceptionType;
import io.github.matheusanbs.wirelessautomate.Config;
import io.github.matheusanbs.wirelessautomate.block.RouterTier;
import io.github.matheusanbs.wirelessautomate.item.TierCoreItem;
import io.github.matheusanbs.wirelessautomate.registry.ModDataComponents;
import io.github.matheusanbs.wirelessautomate.registry.ModItems;
import io.github.matheusanbs.wirelessautomate.storage.StorageBlock;
import io.github.matheusanbs.wirelessautomate.storage.StorageBlockItem;
import io.github.matheusanbs.wirelessautomate.storage.StorageContents;
import io.github.matheusanbs.wirelessautomate.storage.StorageSavedData;
import java.util.Map;
import java.util.UUID;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.UuidArgument;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NumericTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

/**
 * {@code /wa storage}: o conteúdo dos armazenamentos do mod guardado no servidor enquanto eles são item.
 * {@code list} mostra cada um (tipo, total e tipos); {@code recover <id>} dá ao jogador o bloco que aponta
 * para aquele conteúdo, para recuperar o de um bloco que sumiu sem virar item (quebrado sem drop
 * antes da correção, {@code /setblock}, outro mod). É seguro: colocar tira o conteúdo do servidor,
 * então se o item original também existir, o primeiro a ser colocado leva tudo e o outro nasce
 * vazio, sem duplicar. Só para operadores.
 */
public final class StorageCommand {
    private static final DynamicCommandExceptionType NOT_FOUND = new DynamicCommandExceptionType(
            id -> Component.translatable("command.wirelessautomate.storage.not_found", id));

    static LiteralArgumentBuilder<CommandSourceStack> node() {
        return Commands.literal("storage")
                .requires(source -> source.hasPermission(2))
                .then(Commands.literal("list").executes(context -> list(context.getSource())))
                .then(Commands.literal("recover")
                        .then(Commands.argument("id", UuidArgument.uuid())
                                .executes(context -> recover(context.getSource(), UuidArgument.getUuid(context, "id")))));
    }

    private static int list(CommandSourceStack source) {
        Map<UUID, StorageSavedData.Stored> all = StorageSavedData.get(source.getServer()).contents();
        if (all.isEmpty()) {
            source.sendSuccess(() -> Component.translatable("command.wirelessautomate.storage.list.empty"), false);
            return 0;
        }
        source.sendSuccess(() -> Component.translatable("command.wirelessautomate.storage.list.header", all.size()), false);
        all.forEach((id, stored) -> {
            long[] summary = summary(stored.data());
            String command = "/wa storage recover " + id;
            Component line = Component.translatable("command.wirelessautomate.storage.list.entry",
                    id.toString().substring(0, 8), Component.translatable("block.wirelessautomate." + stored.kind().id),
                    StorageBlock.summary(stored.kind(), summary[1], (int) summary[0], 0))
                    .withStyle(style -> style.withClickEvent(new ClickEvent(ClickEvent.Action.SUGGEST_COMMAND, command))
                            .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Component.literal(command))));
            source.sendSuccess(() -> line, false);
        });
        return all.size();
    }

    private static int recover(CommandSourceStack source, UUID id) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        StorageSavedData.Stored stored = StorageSavedData.get(source.getServer()).contents().get(id);
        if (stored == null) {
            throw NOT_FOUND.create(id);
        }
        ServerPlayer player = source.getPlayerOrException();
        long[] summary = summary(stored.data());
        ItemStack block = StorageBlockItem.withTier(ModItems.STORAGE.get(stored.kind()).get(),
                tierFor(stored, summary[1]));
        block.set(ModDataComponents.STORAGE_CONTENTS.get(), new StorageContents(id, (int) summary[0], summary[1]));
        if (!player.getInventory().add(block)) {
            player.drop(block, false);
        }
        source.sendSuccess(() -> Component.translatable("command.wirelessautomate.storage.recovered",
                Component.translatable("block.wirelessautomate." + stored.kind().id),
                StorageBlock.summary(stored.kind(), summary[1], (int) summary[0], 0)), true);
        return 1;
    }

    /**
     * O tier do bloco que se perdeu. Nos conteúdos guardados antes de o tier ser gravado, o menor
     * tier que comporta tudo o que está guardado (pela config), para nada ficar além da capacidade.
     */
    public static RouterTier tierFor(StorageSavedData.Stored stored, long total) {
        if (stored.tier() != null) {
            return stored.tier();
        }
        for (RouterTier tier : RouterTier.values()) {
            long capacity = Config.storageCapacity(stored.kind(), tier);
            if (capacity <= 0 || capacity >= total) {
                return tier;
            }
        }
        return RouterTier.ULTIMATE;
    }

    /** {tipos, total} do conteúdo salvo, sem reler as chaves (a Bateria guarda só o total). */
    private static long[] summary(Tag stored) {
        if (stored instanceof NumericTag energy) {
            return new long[] {0, energy.getAsLong()};
        }
        if (!(stored instanceof ListTag list)) {
            return new long[] {0, 0};
        }
        long total = 0;
        for (int i = 0; i < list.size(); i++) {
            CompoundTag entry = list.getCompound(i);
            total += Math.max(0, entry.getLong("count"));
        }
        return new long[] {list.size(), total};
    }

    private StorageCommand() {
    }
}
