package io.github.matheusanbs.wirelessautomate.tv;

import io.github.matheusanbs.wirelessautomate.packet.TvImageStatePayload;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Ligar e desligar as imagens enviadas com o servidor rodando: a recarga da config ({@code ConfigReloadListener}, que
 * pode vir da thread que observa o arquivo) só marca {@link #configChanged}, e o tick seguinte do servidor compara o
 * {@code tv.images.enabled} com o último que os clientes receberam; mudou, o {@link TvImageStatePayload} vai a todos,
 * e as Telas à vista trocam a imagem pelo quadro neutro (ou voltam) na hora. Quem entra com as imagens desligadas
 * recebe o mesmo aviso ({@link #onLogin}). Parado, o tick é a leitura de um campo.
 */
public final class TvImageState {
    private static volatile boolean configChanged;
    /** O que os clientes receberam por último (ligadas, o padrão de quem entra sem aviso). */
    private static boolean sent = true;
    private static int broadcasts;

    private TvImageState() {
    }

    /** A config recarregou: o próximo tick confere se as imagens mudaram. */
    public static void configChanged() {
        configChanged = true;
    }

    /** A cada tick do servidor (pelo {@link TvManager}). */
    public static void tick(MinecraftServer server) {
        if (!configChanged) {
            return;
        }
        configChanged = false;
        boolean enabled = TvImageConfig.enabled();
        if (enabled == sent) {
            return;
        }
        sent = enabled;
        broadcasts++;
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            send(player, enabled);
        }
    }

    /** Quem entra com as imagens desligadas fica sabendo já (ligadas é o que o cliente supõe). */
    public static void onLogin(ServerPlayer player) {
        if (!TvImageConfig.enabled()) {
            send(player, false);
        }
    }

    private static void send(ServerPlayer player, boolean enabled) {
        TvImageStatePayload payload = new TvImageStatePayload(enabled);
        if (player.connection != null && player.connection.hasChannel(payload)) {
            PacketDistributor.sendToPlayer(player, payload);
        }
    }

    /** Parada do servidor: o próximo mundo começa como o cliente supõe. */
    public static void reset() {
        configChanged = false;
        sent = true;
    }

    /** O que os clientes receberam por último e quantas vezes o aviso foi a todos (os GameTests leem). */
    public static boolean sentForTests() {
        return sent;
    }

    public static int broadcastsForTests() {
        return broadcasts;
    }
}
