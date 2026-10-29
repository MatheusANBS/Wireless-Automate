package io.github.matheusanbs.wirelessautomate.command;

import com.mojang.authlib.GameProfile;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.DynamicCommandExceptionType;
import io.github.matheusanbs.wirelessautomate.network.KnownPlayers;
import io.github.matheusanbs.wirelessautomate.tv.TvImageHash;
import io.github.matheusanbs.wirelessautomate.tv.TvImageIndex;
import io.github.matheusanbs.wirelessautomate.tv.TvImageModeration;
import io.github.matheusanbs.wirelessautomate.tv.TvImageStore;
import io.github.matheusanbs.wirelessautomate.tv.TvImageUploads;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.GameProfileArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;

/**
 * {@code /wa tela}: a moderação das imagens enviadas à Tela de Sinal, só para operadores. {@code imagens [página]}
 * lista o armazém (resumo curto, tamanho, autor, quantas Telas e bibliotecas usam), as recusas por jogador e os
 * banidos; {@code imagem remover <resumo>} tira a imagem de todas as Telas carregadas e das bibliotecas e a apaga (o
 * começo do resumo basta, se só uma começa assim); {@code imagem banir|desbanir <jogador>} controla quem não envia.
 */
public final class TvImageCommand {
    private static final String KEY = "command.wirelessautomate.tv.images.";
    private static final int PAGE = 8;
    private static final DynamicCommandExceptionType NOT_FOUND = new DynamicCommandExceptionType(
            hash -> Component.translatable(KEY + "not_found", hash));

    private TvImageCommand() {
    }

    static LiteralArgumentBuilder<CommandSourceStack> node() {
        return Commands.literal("tela")
                .requires(source -> source.hasPermission(2))
                .then(Commands.literal("imagens")
                        .executes(context -> list(context.getSource(), 1))
                        .then(Commands.argument("page", IntegerArgumentType.integer(1))
                                .executes(context -> list(context.getSource(),
                                        IntegerArgumentType.getInteger(context, "page")))))
                .then(Commands.literal("imagem")
                        .then(Commands.literal("remover")
                                .then(Commands.argument("hash", StringArgumentType.word())
                                        .suggests((context, builder) -> SharedSuggestionProvider.suggest(
                                                hashes(context.getSource().getServer()), builder))
                                        .executes(TvImageCommand::remove)))
                        .then(Commands.literal("banir")
                                .then(Commands.argument("player", GameProfileArgument.gameProfile())
                                        .executes(context -> ban(context, true))))
                        .then(Commands.literal("desbanir")
                                .then(Commands.argument("player", GameProfileArgument.gameProfile())
                                        .executes(context -> ban(context, false)))));
    }

    private static List<String> hashes(MinecraftServer server) {
        List<String> out = new ArrayList<>();
        for (TvImageIndex.Entry entry : TvImageStore.get(server).entries()) {
            out.add(TvImageHash.brief(entry.hash));
        }
        return out;
    }

    /** A página da lista, das mais novas para as mais velhas. */
    private static int list(CommandSourceStack source, int page) {
        MinecraftServer server = source.getServer();
        TvImageStore store = TvImageStore.get(server);
        List<TvImageIndex.Entry> entries = new ArrayList<>(store.entries());
        Collections.reverse(entries);
        int pages = Math.max(1, (entries.size() + PAGE - 1) / PAGE);
        int shown = Math.min(page, pages);
        source.sendSuccess(() -> Component.translatable(KEY + "header", entries.size(), size(store.totalBytes()),
                shown, pages), false);
        if (entries.isEmpty()) {
            source.sendSuccess(() -> Component.translatable(KEY + "empty"), false);
        }
        for (int i = (shown - 1) * PAGE; i < Math.min(entries.size(), shown * PAGE); i++) {
            TvImageIndex.Entry entry = entries.get(i);
            String author = entry.author == null ? "?" : KnownPlayers.name(server, entry.author);
            String frames = entry.frames > 1 ? " · " + entry.frames + "q" : "";
            source.sendSuccess(() -> Component.translatable(KEY + "line", TvImageHash.brief(entry.hash),
                    entry.width + "×" + entry.height + frames, size(entry.bytes), author, entry.screens(),
                    entry.libraries()), false);
        }
        Map<UUID, Integer> refusals = TvImageUploads.refusals();
        if (!refusals.isEmpty()) {
            source.sendSuccess(() -> Component.translatable(KEY + "refusals", names(server, refusals)), false);
        }
        if (!store.bannedView().isEmpty()) {
            source.sendSuccess(() -> Component.translatable(KEY + "banned_list", names(server, store.bannedView())),
                    false);
        }
        return entries.size();
    }

    private static String names(MinecraftServer server, Map<UUID, Integer> counts) {
        StringBuilder out = new StringBuilder();
        for (Map.Entry<UUID, Integer> entry : counts.entrySet()) {
            if (!out.isEmpty()) {
                out.append(", ");
            }
            out.append(KnownPlayers.name(server, entry.getKey())).append(" (").append(entry.getValue()).append(')');
        }
        return out.toString();
    }

    private static String names(MinecraftServer server, Collection<UUID> players) {
        StringBuilder out = new StringBuilder();
        for (UUID player : players) {
            if (!out.isEmpty()) {
                out.append(", ");
            }
            out.append(KnownPlayers.name(server, player));
        }
        return out.toString();
    }

    /** O tamanho em KB ou MB, com uma casa. */
    static String size(long bytes) {
        if (bytes < 1024L * 1024) {
            return String.format(Locale.ROOT, "%.1f KB", bytes / 1024.0);
        }
        return String.format(Locale.ROOT, "%.1f MB", bytes / (1024.0 * 1024));
    }

    private static int remove(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        String prefix = StringArgumentType.getString(context, "hash");
        MinecraftServer server = context.getSource().getServer();
        String hash = TvImageModeration.find(TvImageStore.get(server), prefix);
        TvImageModeration.Removed removed = hash == null ? null : TvImageModeration.remove(server, hash);
        if (removed == null) {
            throw NOT_FOUND.create(prefix);
        }
        context.getSource().sendSuccess(() -> Component.translatable(KEY + "removed", TvImageHash.brief(hash),
                removed.screens(), removed.libraries()), true);
        return 1;
    }

    private static int ban(CommandContext<CommandSourceStack> context, boolean ban) throws CommandSyntaxException {
        TvImageStore store = TvImageStore.get(context.getSource().getServer());
        int changed = 0;
        for (GameProfile profile : GameProfileArgument.getGameProfiles(context, "player")) {
            boolean done = ban ? store.ban(profile.getId()) : store.unban(profile.getId());
            String key = ban ? done ? "banned" : "already_banned" : done ? "unbanned" : "not_banned";
            context.getSource().sendSuccess(() -> Component.translatable(KEY + key, profile.getName()), done);
            changed += done ? 1 : 0;
        }
        return changed;
    }
}
