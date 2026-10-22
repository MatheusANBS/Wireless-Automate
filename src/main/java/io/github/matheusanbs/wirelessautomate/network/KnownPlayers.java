package io.github.matheusanbs.wirelessautomate.network;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.github.matheusanbs.wirelessautomate.WirelessAutomate;
import io.github.matheusanbs.wirelessautomate.network.AccessSuggestions.Candidate;
import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.GameProfileCache;
import org.jetbrains.annotations.Nullable;

/**
 * Os jogadores que o servidor conhece, para convidar para uma rede e mostrar o nome de um convidado: os que estão
 * online e os do cache de perfis do servidor ({@code usercache.json}, quem já entrou). Só consultado quando alguém
 * mexe na lista de acesso de uma rede; nunca por tick. O arquivo do cache é lido no máximo a cada
 * {@link #REFRESH_TICKS} ticks, e nunca se consulta o Mojang: um nome que o servidor não conhece não existe aqui.
 */
public final class KnownPlayers {
    /** O cache do arquivo vale por 30 segundos. */
    static final int REFRESH_TICKS = 600;

    /** Quem está no arquivo, por id, na ordem dele. Só a thread do servidor mexe. */
    private static final Map<UUID, String> FILE = new LinkedHashMap<>();
    private static @Nullable MinecraftServer fileOwner;
    private static long loadedAt;

    private KnownPlayers() {
    }

    /** Esquece o arquivo lido e o servidor dono dele (ao parar o servidor). */
    public static void clear() {
        FILE.clear();
        fileOwner = null;
    }

    /** Os jogadores do arquivo do cache, relido se passou o prazo ou se o servidor é outro. */
    private static Map<UUID, String> file(MinecraftServer server) {
        long now = server.getTickCount();
        if (fileOwner != server || now - loadedAt >= REFRESH_TICKS || now < loadedAt) {
            FILE.clear();
            fileOwner = server;
            loadedAt = now;
            read(server);
        }
        return FILE;
    }

    private static void read(MinecraftServer server) {
        Path cache = server.getFile("usercache.json");
        if (!Files.isRegularFile(cache)) {
            return;
        }
        try (Reader reader = Files.newBufferedReader(cache, StandardCharsets.UTF_8)) {
            JsonElement root = JsonParser.parseReader(reader);
            if (!root.isJsonArray()) {
                return;
            }
            JsonArray array = root.getAsJsonArray();
            for (JsonElement element : array) {
                if (element.isJsonObject()) {
                    JsonObject entry = element.getAsJsonObject();
                    if (entry.has("uuid") && entry.has("name")) {
                        try {
                            FILE.put(UUID.fromString(entry.get("uuid").getAsString()), entry.get("name").getAsString());
                        } catch (IllegalArgumentException | UnsupportedOperationException | IllegalStateException e) {
                            // uma entrada com UUID ou nome ruim só fica de fora
                        }
                    }
                }
            }
        } catch (IOException | RuntimeException e) {
            // um arquivo ilegível só deixa a lista de quem já entrou vazia; o resto do mod segue
            WirelessAutomate.LOGGER.warn("Não foi possível ler o cache de jogadores: {}", e.toString());
        }
    }

    /** O nome do jogador: online, ou do cache de perfis, ou do arquivo; senão o começo do id. */
    public static String name(MinecraftServer server, UUID id) {
        ServerPlayer online = server.getPlayerList().getPlayer(id);
        if (online != null) {
            return online.getGameProfile().getName();
        }
        GameProfileCache cache = server.getProfileCache();
        if (cache != null) {
            var profile = cache.get(id);
            if (profile.isPresent()) {
                return profile.get().getName();
            }
        }
        String known = file(server).get(id);
        return known != null ? known : id.toString().substring(0, 8);
    }

    public static boolean isOnline(MinecraftServer server, UUID id) {
        return server.getPlayerList().getPlayer(id) != null;
    }

    /** O servidor sabe quem é este jogador (está online ou já entrou). */
    public static boolean knows(MinecraftServer server, UUID id) {
        if (isOnline(server, id) || file(server).containsKey(id)) {
            return true;
        }
        GameProfileCache cache = server.getProfileCache();
        return cache != null && cache.get(id).isPresent();
    }

    /** O jogador com esse nome (sem diferenciar maiúsculas), online ou conhecido; {@code null} se não há. */
    public static @Nullable UUID byName(MinecraftServer server, String name) {
        ServerPlayer online = server.getPlayerList().getPlayerByName(name);
        if (online != null) {
            return online.getUUID();
        }
        String wanted = name.toLowerCase(Locale.ROOT);
        for (Map.Entry<UUID, String> entry : file(server).entrySet()) {
            if (entry.getValue().toLowerCase(Locale.ROOT).equals(wanted)) {
                return entry.getKey();
            }
        }
        return null;
    }

    /** Todos os jogadores conhecidos como candidatos; {@code taken} marca quem já tem o acesso. */
    public static List<Candidate> candidates(MinecraftServer server, java.util.function.Predicate<UUID> taken) {
        Map<UUID, Candidate> all = new LinkedHashMap<>();
        for (Map.Entry<UUID, String> entry : file(server).entrySet()) {
            all.put(entry.getKey(), new Candidate(entry.getKey(), entry.getValue(), false, taken.test(entry.getKey())));
        }
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            UUID id = player.getUUID();
            all.put(id, new Candidate(id, player.getGameProfile().getName(), true, taken.test(id)));
        }
        return new ArrayList<>(all.values());
    }
}
