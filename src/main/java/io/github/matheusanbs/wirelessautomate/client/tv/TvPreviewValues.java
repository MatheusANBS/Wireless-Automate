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
 * números que voltam, só os da última lista pedida. O editor lê pelo {@link #value} e limpa ao fechar
 * ({@link #stop}). Só na thread do cliente.
 */
public final class TvPreviewValues {
    private static List<TvBinding> asked = List.of();
    private static int containerId = -1;
    private static int askedVersion;
    private static double[] values = new double[0];
    /** Sobe a cada chegada: o editor compara para saber se a miniatura precisa redesenhar. */
    private static int version;

    private TvPreviewValues() {
    }

    /** Pede a prévia das ligações do rascunho; não manda nada se a lista é a mesma do último pedido. */
    public static void request(int menuId, List<TvBinding> bindings) {
        if (menuId == containerId && bindings.equals(asked)) {
            return;
        }
        List<TvBinding> list = bindings.size() > TvPreviewPayload.MAX_BINDINGS
                ? bindings.subList(0, TvPreviewPayload.MAX_BINDINGS) : bindings;
        TvPreviewPayload payload = new TvPreviewPayload(menuId, askedVersion + 1, List.copyOf(list));
        ClientPacketListener connection = Minecraft.getInstance().getConnection();
        if (connection == null || !connection.hasChannel(payload)) {
            return;
        }
        askedVersion++;
        containerId = menuId;
        asked = payload.bindings();
        values = new double[0];
        version++;
        PacketDistributor.sendToServer(payload);
    }

    public static void receive(TvPreviewValuesPayload payload) {
        if (payload.containerId() != containerId || payload.version() != askedVersion) {
            return;
        }
        values = payload.values();
        version++;
    }

    /** O número da ligação na prévia, ou {@code NaN} se ela não foi pedida ou o número ainda não chegou. */
    public static double value(TvBinding binding) {
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
        version++;
    }
}
