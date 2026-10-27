package io.github.matheusanbs.wirelessautomate.client.tv.editor;

import io.github.matheusanbs.wirelessautomate.packet.TvStorageContentsPayload;
import java.util.List;
import net.minecraft.core.GlobalPos;
import org.jetbrains.annotations.Nullable;

/**
 * Os itens do último Baú pedido pela aba Dado: o último {@link TvStorageContentsPayload}, que o servidor manda só com
 * o editor aberto e depois do pedido. Some ao fechar o editor. A {@link #version} sobe a cada resposta e a cada
 * limpeza, e a grade de itens se refaz só quando ela muda.
 */
public final class TvStorageClient {
    private static @Nullable GlobalPos pos;
    private static boolean available;
    private static int total;
    private static List<TvStorageContentsPayload.Entry> items = List.of();
    private static int version;

    private TvStorageClient() {
    }

    /** O gancho do payload (preenchido no {@code ClientSetup}); uma resposta atrasada, sem o editor aberto, é ignorada. */
    public static void receive(TvStorageContentsPayload payload) {
        if (!TvEditorAck.isOpen(payload.containerId())) {
            return;
        }
        pos = payload.pos();
        available = payload.available();
        total = payload.total();
        items = payload.items();
        version++;
    }

    /** Solta os itens (o editor fechou ou o jogador saiu do mundo). */
    public static void clear() {
        if (pos != null) {
            pos = null;
            available = false;
            total = 0;
            items = List.of();
        }
        version++;
    }

    /** Os itens do Baú em {@code wanted}, ou vazio se o último pedido foi de outro. */
    static List<TvStorageContentsPayload.Entry> itemsOf(@Nullable GlobalPos wanted) {
        return wanted != null && wanted.equals(pos) ? items : List.of();
    }

    /** Quantos tipos casaram com a última busca do Baú em {@code wanted}; 0 se o último pedido foi de outro. */
    static int totalOf(@Nullable GlobalPos wanted) {
        return wanted != null && wanted.equals(pos) ? total : 0;
    }

    /** Se o Baú em {@code wanted} respondeu e estava carregado. */
    static boolean availableAt(@Nullable GlobalPos wanted) {
        return wanted != null && wanted.equals(pos) && available;
    }

    /** Se já chegou alguma resposta para o Baú em {@code wanted}. */
    static boolean answeredAt(@Nullable GlobalPos wanted) {
        return wanted != null && wanted.equals(pos);
    }

    /** Sobe a cada payload recebido e a cada {@link #clear}. */
    public static int version() {
        return version;
    }
}
