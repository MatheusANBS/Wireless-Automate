package io.github.matheusanbs.wirelessautomate.client.tv.image;

import io.github.matheusanbs.wirelessautomate.client.tv.render.TvPictureTextures;
import io.github.matheusanbs.wirelessautomate.packet.TvImageDataPayload;
import io.github.matheusanbs.wirelessautomate.packet.TvImageHavePayload;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.Nullable;

/**
 * A chegada das imagens enviadas no cliente: os pedaços do {@link TvImageDataPayload} montados na ordem, até
 * {@link #MAX_BYTES} por imagem e {@link #MAX_ASSEMBLIES} ao mesmo tempo (a mais velha sai). Inteira, a imagem é
 * conferida (resumo e cabeçalho) e guardada no cache em disco, fora da thread de render ({@link TvImageDisk}), e só
 * então vai às texturas ({@link TvPictureTextures#arrived}). Um pedaço fora de ordem descarta a montagem (o servidor
 * manda na ordem, pela mesma conexão). Guarda também o autor de cada imagem que chegou, que o pacote traz. Na entrada
 * no mundo, manda ao servidor a lista do que o cache já tem ({@link #sendHave}), uma vez por conexão.
 */
public final class TvImageReceiver {
    private static final int MAX_BYTES = 32 << 20;
    private static final int MAX_ASSEMBLIES = 8;
    private static final LinkedHashMap<String, Assembly> ASSEMBLIES = new LinkedHashMap<>();
    private static final Map<String, UUID> AUTHORS = new HashMap<>();
    /** Quantas imagens chegaram inteiras e valeram (o e2e confere a entrega). */
    public static int receivedForTests;

    private TvImageReceiver() {
    }

    private static final class Assembly {
        final byte[] data;
        int received;

        Assembly(int total) {
            data = new byte[total];
        }
    }

    public static void receive(TvImageDataPayload payload) {
        String hash = payload.hash();
        int total = payload.total();
        byte[] chunk = payload.data();
        if (total <= 0 || total > MAX_BYTES) {
            return;
        }
        Assembly assembly = ASSEMBLIES.get(hash);
        if (payload.offset() == 0) {
            assembly = new Assembly(total);
            ASSEMBLIES.remove(hash);
            ASSEMBLIES.put(hash, assembly);
            if (ASSEMBLIES.size() > MAX_ASSEMBLIES) {
                Iterator<String> oldest = ASSEMBLIES.keySet().iterator();
                oldest.next();
                oldest.remove();
            }
        }
        if (assembly == null || assembly.data.length != total || payload.offset() != assembly.received
                || assembly.received + chunk.length > total) {
            ASSEMBLIES.remove(hash);
            return;
        }
        System.arraycopy(chunk, 0, assembly.data, assembly.received, chunk.length);
        assembly.received += chunk.length;
        payload.author().ifPresent(author -> AUTHORS.put(hash, author));
        if (assembly.received < total) {
            return;
        }
        ASSEMBLIES.remove(hash);
        byte[] data = assembly.data;
        TvImageDisk.verifyAndStore(hash, data).thenAccept(ok -> {
            if (ok) {
                Minecraft.getInstance().execute(() -> {
                    receivedForTests++;
                    TvPictureTextures.arrived(hash, data);
                });
            }
        });
    }

    /** O autor da imagem, se ela chegou nesta sessão. */
    public static @Nullable UUID author(String hash) {
        return AUTHORS.get(hash);
    }

    /** A entrada no mundo: a lista do cache vai ao servidor (as mais recentes, até {@link TvImageHavePayload#MAX}). */
    public static void sendHave() {
        TvImageDisk.recent(TvImageHavePayload.MAX).thenAccept(hashes -> Minecraft.getInstance().execute(() -> {
            ClientPacketListener connection = Minecraft.getInstance().getConnection();
            if (!hashes.isEmpty() && connection != null && connection.hasChannel(TvImageHavePayload.TYPE)) {
                PacketDistributor.sendToServer(new TvImageHavePayload(hashes));
            }
        }));
    }

    /** Ao sair do mundo. */
    public static void clear() {
        ASSEMBLIES.clear();
        AUTHORS.clear();
    }
}
