package io.github.matheusanbs.wirelessautomate.tv;

import io.github.matheusanbs.wirelessautomate.Config;
import io.github.matheusanbs.wirelessautomate.menu.TvMenu;
import io.github.matheusanbs.wirelessautomate.network.FlowSampler;
import io.github.matheusanbs.wirelessautomate.network.LoadedTypes;
import io.github.matheusanbs.wirelessautomate.network.NetworkManager;
import io.github.matheusanbs.wirelessautomate.network.NetworkSavedData;
import io.github.matheusanbs.wirelessautomate.packet.TvPreviewPayload;
import io.github.matheusanbs.wirelessautomate.packet.TvPreviewValuesPayload;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.Nullable;

/**
 * A prévia dos números no editor: as ligações do rascunho que o jogador mandou, lidas a cada volta nova do
 * amostrador com o acesso de quem edita, e mandadas só a ele e só quando mudam. Existe só enquanto o editor está
 * aberto: fechar o menu, sair ou a lista vazia tiram o jogador. Sem ninguém editando, o {@link #tick} é um
 * {@code isEmpty()}.
 */
public final class TvPreview {
    private static final Map<UUID, State> STATES = new HashMap<>();
    /** O limite próprio da prévia: o do salvar e o das ações da biblioteca não a engolem. */
    private static final TvRateGate GATE = new TvRateGate();

    private TvPreview() {
    }

    private static final class State {
        final int containerId;
        final int version;
        final List<TvBinding> bindings;
        long readSample = Long.MIN_VALUE;
        double @Nullable [] sent;

        State(int containerId, int version, List<TvBinding> bindings) {
            this.containerId = containerId;
            this.version = version;
            this.bindings = bindings;
        }
    }

    /** O pedido do editor: valida o menu e a frequência e troca a lista do jogador. */
    public static void handle(@Nullable ServerPlayer player, TvPreviewPayload payload) {
        if (TvEditing.open(player, payload.containerId()) == null
                || !GATE.allow(player.getUUID(), player.level().getGameTime())) {
            return;
        }
        if (payload.bindings().isEmpty()) {
            STATES.remove(player.getUUID());
            return;
        }
        int max = Math.min(Config.tvMaxBindings(), TvPreviewPayload.MAX_BINDINGS);
        List<TvBinding> bindings = new ArrayList<>(Math.min(payload.bindings().size(), max));
        for (TvBinding binding : payload.bindings()) {
            if (bindings.size() >= max) {
                break;
            }
            bindings.add(binding);
        }
        STATES.put(player.getUUID(), new State(payload.containerId(), payload.version(), List.copyOf(bindings)));
    }

    /** Chamado pelo {@link TvManager} a cada tick: lê e manda só numa volta nova do amostrador ou num pedido novo. */
    static void tick(MinecraftServer server) {
        if (STATES.isEmpty()) {
            return;
        }
        tick(server, NetworkManager.get().flows().report());
    }

    /** O mesmo tick com o relatório dado (os testes montam o relatório à mão). */
    public static void tickForTests(MinecraftServer server, FlowSampler.Report report) {
        tick(server, report);
    }

    private static void tick(MinecraftServer server, FlowSampler.Report report) {
        NetworkSavedData data = NetworkSavedData.get(server);
        Iterator<Map.Entry<UUID, State>> it = STATES.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<UUID, State> entry = it.next();
            State state = entry.getValue();
            ServerPlayer player = server.getPlayerList().getPlayer(entry.getKey());
            if (player == null || !(player.containerMenu instanceof TvMenu menu)
                    || menu.containerId != state.containerId) {
                it.remove();
                continue;
            }
            if (report.sampledAt() == state.readSample) {
                continue;
            }
            state.readSample = report.sampledAt();
            double[] values = new double[state.bindings.size()];
            for (int i = 0; i < values.length; i++) {
                TvBinding binding = state.bindings.get(i);
                values[i] = LoadedTypes.contains(binding.type())
                        ? TvManager.read(binding, report, data, player.getUUID()) : Double.NaN;
            }
            if (state.sent != null && Arrays.equals(values, state.sent)) {
                continue;
            }
            state.sent = values;
            TvPreviewValuesPayload payload = new TvPreviewValuesPayload(state.containerId, state.version, values);
            if (player.connection != null && player.connection.hasChannel(payload)) {
                PacketDistributor.sendToPlayer(player, payload);
            }
        }
    }

    /** Os últimos números calculados para o jogador, ou {@code null} sem prévia (para os testes). */
    public static double @Nullable [] sentForTests(UUID player) {
        State state = STATES.get(player);
        return state == null ? null : state.sent;
    }

    public static boolean activeForTests(UUID player) {
        return STATES.containsKey(player);
    }

    public static void forget(UUID player) {
        STATES.remove(player);
        GATE.forget(player);
    }

    public static void reset() {
        STATES.clear();
        GATE.clear();
    }
}
