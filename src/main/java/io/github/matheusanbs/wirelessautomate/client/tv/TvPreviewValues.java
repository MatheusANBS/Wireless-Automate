package io.github.matheusanbs.wirelessautomate.client.tv;

import io.github.matheusanbs.wirelessautomate.packet.TvPreviewPayload;
import io.github.matheusanbs.wirelessautomate.packet.TvPreviewValuesPayload;
import io.github.matheusanbs.wirelessautomate.tv.TvBinding;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * A prévia dos números no editor, do lado do cliente: manda as ligações do rascunho quando elas mudam e guarda os
 * números que voltam, só os da última lista pedida. Todas as ligações do rascunho vão, não só as que a Tela salva
 * ainda não tem: assim o número vem de uma fonte só e não some ao salvar. O editor chama {@link #request} uma vez por
 * tick (a comparação é por referência, sem custo enquanto o rascunho não muda as ligações), lê pelo {@link #value} e
 * limpa ao fechar ({@link #stop}). Se o pedido não for respondido (o servidor o descartou pelo limite), repete
 * depois de {@link #RETRY_MS}. Só na thread do cliente.
 */
public final class TvPreviewValues {
    private static final long RETRY_MS = 1500;

    private static List<TvBinding> asked = List.of();
    private static int containerId = -1;
    private static int askedVersion;
    private static double[] values = new double[0];
    private static boolean answered;
    private static long sentAt;
    /** Sobe a cada chegada: o editor compara para saber se a miniatura precisa redesenhar. */
    private static int version;

    private TvPreviewValues() {
    }

    /** Pede a prévia das ligações do rascunho; não manda nada se a lista é a mesma do último pedido. */
    public static void request(int menuId, List<TvBinding> bindings) {
        if (menuId == containerId && (bindings == asked || bindings.equals(asked))) {
            if (!answered && !asked.isEmpty() && System.currentTimeMillis() - sentAt >= RETRY_MS) {
                send(menuId, asked);
            }
            return;
        }
        if (bindings.isEmpty() && asked.isEmpty()) {
            containerId = menuId;
            return;
        }
        List<TvBinding> list = bindings.size() > TvPreviewPayload.MAX_BINDINGS
                ? bindings.subList(0, TvPreviewPayload.MAX_BINDINGS) : bindings;
        send(menuId, List.copyOf(list));
    }

    private static void send(int menuId, List<TvBinding> list) {
        TvPreviewPayload payload = new TvPreviewPayload(menuId, askedVersion + 1, list);
        ClientPacketListener connection = Minecraft.getInstance().getConnection();
        if (connection == null || !connection.hasChannel(payload)) {
            return;
        }
        askedVersion++;
        containerId = menuId;
        asked = list;
        values = new double[0];
        answered = list.isEmpty();
        sentAt = System.currentTimeMillis();
        version++;
        PacketDistributor.sendToServer(payload);
    }

    public static void receive(TvPreviewValuesPayload payload) {
        if (payload.containerId() != containerId || payload.version() != askedVersion) {
            return;
        }
        values = payload.values();
        answered = true;
        version++;
    }

    /** O número da ligação na prévia, ou {@code NaN} se ela não foi pedida ou o número ainda não chegou. */
    public static double value(TvBinding binding) {
        if (asked.isEmpty()) {
            return Double.NaN;
        }
        int index = asked.indexOf(binding);
        return index >= 0 && index < values.length ? values[index] : Double.NaN;
    }

    public static int version() {
        return version;
    }

    /** O editor fechou: esquece a prévia (o servidor para sozinho quando o menu fecha). */
    public static void stop() {
        asked = List.of();
        containerId = -1;
        values = new double[0];
        answered = false;
        version++;
    }
}
