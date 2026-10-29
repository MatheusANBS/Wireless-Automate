package io.github.matheusanbs.wirelessautomate.client.tv.image;

import io.github.matheusanbs.wirelessautomate.packet.TvImageReplyPayload;
import io.github.matheusanbs.wirelessautomate.packet.TvImageUploadPayload;
import io.github.matheusanbs.wirelessautomate.tv.TvImageHash;
import io.github.matheusanbs.wirelessautomate.tv.TvImageHeader;
import io.github.matheusanbs.wirelessautomate.tv.TvImageRefusal;
import io.github.matheusanbs.wirelessautomate.tv.TvPicture;
import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.Nullable;

/**
 * O envio de uma imagem do cliente ao servidor, em partes ({@link TvImageUploadPayload}): o resumo e o cabeçalho são
 * calculados fora da thread de render (o cabeçalho que não vale nem sai daqui); o começo declara tudo e, se o servidor
 * pede os bytes ({@link TvImageReplyPayload.Status#SEND}), um pedaço de até {@link TvImageUploadPayload#MAX_CHUNK} sai
 * por tick do cliente, e o fim fecha. A resposta final ({@code OK} ou a recusa com o motivo) volta a quem pediu; a
 * imagem aceita fica no cache em disco, porque o servidor não a manda de volta a quem a enviou. Um envio por vez, os
 * outros esperam na fila; sem resposta em {@link #TIMEOUT_MS}, desiste.
 */
public final class TvImageUploader {
    private static final long TIMEOUT_MS = 60_000L;
    private static final ArrayDeque<Job> QUEUE = new ArrayDeque<>();
    private static @Nullable Job current;

    private TvImageUploader() {
    }

    /** O resultado de um envio: aceito (com o resumo) ou recusado (com o motivo). */
    public record Outcome(String hash, boolean ok, @Nullable TvImageRefusal refusal) {
    }

    private static final class Job {
        final byte[] data;
        final String name;
        final Consumer<Outcome> done;
        final String hash;
        final TvImageHeader.Info info;
        int sent;
        boolean sending;
        long last;

        Job(byte[] data, String name, Consumer<Outcome> done, String hash, TvImageHeader.Info info) {
            this.data = data;
            this.name = name;
            this.done = done;
            this.hash = hash;
            this.info = info;
        }
    }

    /**
     * Envia os bytes de uma imagem com o nome que ela terá na biblioteca; {@code done} roda na thread de render com o
     * resultado. O cabeçalho é conferido nos tetos de qualquer imagem; os do servidor, ele confere.
     */
    public static void upload(byte[] data, String name, Consumer<Outcome> done) {
        CompletableFuture.supplyAsync(() -> {
            TvImageHeader.Result header = TvImageHeader.read(data, TvPicture.HARD_SIZE, TvPicture.HARD_FRAMES);
            return header.ok() ? new Job(data, name, done, TvImageHash.of(data), header.info()) : null;
        }, Util.backgroundExecutor()).thenAccept(job -> Minecraft.getInstance().execute(() -> {
            if (job == null) {
                done.accept(new Outcome("", false, TvImageRefusal.BAD_FORMAT));
                return;
            }
            QUEUE.add(job);
            if (current == null) {
                next();
            }
        }));
    }

    private static void next() {
        current = QUEUE.poll();
        if (current == null) {
            return;
        }
        ClientPacketListener connection = Minecraft.getInstance().getConnection();
        if (connection == null || !connection.hasChannel(TvImageUploadPayload.TYPE)) {
            finish(new Outcome(current.hash, false, TvImageRefusal.DISABLED));
            return;
        }
        current.last = Util.getMillis();
        TvImageHeader.Info info = current.info;
        PacketDistributor.sendToServer(TvImageUploadPayload.start(current.hash, current.data.length,
                info.format().ordinal(), info.width(), info.height(), info.frames(), current.name));
    }

    /** A resposta do servidor ao envio da vez. */
    public static void onReply(TvImageReplyPayload reply) {
        Job job = current;
        if (job == null || !job.hash.equals(reply.hash())) {
            return;
        }
        job.last = Util.getMillis();
        switch (reply.status()) {
            case SEND -> job.sending = true;
            case OK -> {
                TvImageDisk.store(job.hash, job.data);
                finish(new Outcome(job.hash, true, null));
            }
            case REFUSED -> {
                TvImageRefusal refusal = TvImageRefusal.byName(reply.reason());
                finish(new Outcome(job.hash, false, refusal == null ? TvImageRefusal.FAILED : refusal));
            }
        }
    }

    private static void finish(Outcome outcome) {
        Job job = current;
        current = null;
        if (job != null) {
            job.done.accept(outcome);
        }
        next();
    }

    /** A cada tick do cliente, só com envio: um pedaço por tick e, depois do último, o fim; e o prazo da resposta. */
    public static void onClientTick() {
        Job job = current;
        if (job == null) {
            return;
        }
        if (job.sending) {
            int length = Math.min(TvImageUploadPayload.MAX_CHUNK, job.data.length - job.sent);
            PacketDistributor.sendToServer(TvImageUploadPayload.chunk(job.hash, job.sent,
                    Arrays.copyOfRange(job.data, job.sent, job.sent + length)));
            job.sent += length;
            job.last = Util.getMillis();
            if (job.sent >= job.data.length) {
                job.sending = false;
                PacketDistributor.sendToServer(TvImageUploadPayload.end(job.hash));
            }
        } else if (Util.getMillis() - job.last > TIMEOUT_MS) {
            finish(new Outcome(job.hash, false, TvImageRefusal.TIMEOUT));
        }
    }

    /** Se há envio em curso ou na fila. */
    public static boolean busy() {
        return current != null || !QUEUE.isEmpty();
    }

    /** Ao sair do mundo: os envios somem sem resposta. */
    public static void clear() {
        current = null;
        QUEUE.clear();
    }
}
