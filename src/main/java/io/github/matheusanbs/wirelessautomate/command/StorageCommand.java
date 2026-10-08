package io.github.matheusanbs.wirelessautomate.command;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.exceptions.DynamicCommandExceptionType;
import io.github.matheusanbs.wirelessautomate.block.RouterTier;
import io.github.matheusanbs.wirelessautomate.item.TierCoreItem;
import io.github.matheusanbs.wirelessautomate.registry.ModDataComponents;
import io.github.matheusanbs.wirelessautomate.registry.ModItems;
import io.github.matheusanbs.wirelessautomate.storage.StorageChestBlockItem;
import io.github.matheusanbs.wirelessautomate.storage.StorageContents;
import io.github.matheusanbs.wirelessautomate.storage.StorageSavedData;
import java.util.Map;
import java.util.UUID;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.UuidArgument;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

/**
 * {@code /wa storage}: o conteúdo dos Baús guardado no servidor enquanto eles são item.
 * {@code list} mostra cada um (tipos e total); {@code recover <id>} dá ao jogador um Baú que aponta
 * para aquele conteúdo, para recuperar o de um Baú que sumiu sem virar item (quebrado sem drop
 * antes da correção, {@code /setblock}, outro mod). É seguro: colocar tira o conteúdo do servidor,
 * então se o item original também existir, o primeiro a ser colocado leva tudo e o outro nasce
 * vazio, sem duplicar. Só para operadores.
 */
final class StorageCommand {
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
        Map<UUID, Tag> all = StorageSavedData.get(source.getServer()).contents();
        if (all.isEmpty()) {
            source.sendSuccess(() -> Component.translatable("command.wirelessautomate.storage.list.empty"), false);
            return 0;
        }
        source.sendSuccess(() -> Component.translatable("command.wirelessautomate.storage.list.header", all.size()), false);
        all.forEach((id, stored) -> {
            long[] summary = summary(stored);
            String command = "/wa storage recover " + id;
            Component line = Component.translatable("command.wirelessautomate.storage.list.entry",
                    id.toString().substring(0, 8), TierCoreItem.grouped(summary[1]), TierCoreItem.grouped(summary[0]))
                    .withStyle(style -> style.withClickEvent(new ClickEvent(ClickEvent.Action.SUGGEST_COMMAND, command))
                            .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Component.literal(command))));
            source.sendSuccess(() -> line, false);
        });
        return all.size();
    }

    private static int recover(CommandSourceStack source, UUID id) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        Tag stored = StorageSavedData.get(source.getServer()).contents().get(id);
        if (stored == null) {
            throw NOT_FOUND.create(id);
        }
        ServerPlayer player = source.getPlayerOrException();
        long[] summary = summary(stored);
        ItemStack chest = StorageChestBlockItem.withTier(ModItems.STORAGE_CHEST.get(), RouterTier.BASIC);
        chest.set(ModDataComponents.STORAGE_CONTENTS.get(), new StorageContents(id, (int) summary[0], summary[1]));
        if (!player.getInventory().add(chest)) {
            player.drop(chest, false);
        }
        source.sendSuccess(() -> Component.translatable("command.wirelessautomate.storage.recovered",
                TierCoreItem.grouped(summary[1]), TierCoreItem.grouped(summary[0])), true);
        return 1;
    }

    /** {tipos, total} da lista salva, sem reler os itens. */
    private static long[] summary(Tag stored) {
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
