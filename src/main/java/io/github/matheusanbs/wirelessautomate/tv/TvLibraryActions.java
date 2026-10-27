package io.github.matheusanbs.wirelessautomate.tv;

import io.github.matheusanbs.wirelessautomate.packet.TvLibraryActionPayload;
import io.github.matheusanbs.wirelessautomate.packet.TvLibraryPayload;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.Nullable;

/**
 * As ações na biblioteca do jogador pela tela da Tela de Sinal: só com o menu aberto, no máximo 10 por segundo, tudo
 * validado aqui. Depois de cada ação a biblioteca inteira volta ao cliente, que é quem desenha a lista.
 */
public final class TvLibraryActions {
    /** O maior componente guardado, em bytes de NBT: 64 deles cabem no payload da biblioteca. */
    public static final int MAX_COMPONENT_BYTES = 12 * 1024;

    private TvLibraryActions() {
    }

    public static void handle(@Nullable ServerPlayer player, TvLibraryActionPayload payload) {
        if (TvEditing.open(player, payload.containerId()) == null || !TvEditing.allowAction(player)) {
            return;
        }
        TvLibrary library = TvLibrary.get(player.server);
        TvLibrary.Result result;
        try {
            result = switch (payload.action()) {
                case OPEN -> TvLibrary.Result.OK;
                case SAVE_COMPONENT -> payload.content().length > MAX_COMPONENT_BYTES ? TvLibrary.Result.INVALID
                        : library.saveComponent(player.getUUID(), payload.component());
                case SAVE_THEME -> library.saveTheme(player.getUUID(), payload.theme());
                case RENAME -> payload.kind() == TvLibraryActionPayload.Kind.COMPONENT
                        ? library.renameComponent(player.getUUID(), payload.name(), payload.newName())
                        : library.renameTheme(player.getUUID(), payload.name(), payload.newName());
                case REMOVE -> payload.kind() == TvLibraryActionPayload.Kind.COMPONENT
                        ? library.removeComponent(player.getUUID(), payload.name())
                        : library.removeTheme(player.getUUID(), payload.name());
            };
        } catch (RuntimeException e) {
            // Conteúdo que não decodifica: a ação é recusada, sem derrubar nada.
            result = TvLibrary.Result.INVALID;
        }
        String key = switch (result) {
            case FULL -> "library_full";
            case EXISTS -> "library_exists";
            case INVALID -> "library_invalid";
            default -> null;
        };
        if (key != null) {
            player.displayClientMessage(Component.translatable("message.wirelessautomate.tv." + key), true);
        }
        send(player, payload.containerId(), library);
    }

    /** A biblioteca do jogador como payload; se passar do teto do payload, os últimos componentes ficam de fora. */
    public static TvLibraryPayload payload(ServerPlayer player, int containerId, TvLibrary library) {
        List<TvComponent> components = new ArrayList<>(library.components(player.getUUID()));
        TvLibraryPayload payload = new TvLibraryPayload(containerId, components, library.themes(player.getUUID()));
        while (payload.encodedSize() > TvLibraryPayload.MAX_BYTES && !components.isEmpty()) {
            components.remove(components.size() - 1);
            payload = new TvLibraryPayload(containerId, components, payload.themes());
        }
        return payload;
    }

    /** Manda a biblioteca a quem tem o canal negociado (sem conexão de verdade, como nos testes, não manda). */
    public static void send(ServerPlayer player, int containerId, TvLibrary library) {
        TvLibraryPayload payload = payload(player, containerId, library);
        if (player.connection != null && player.connection.hasChannel(payload)) {
            PacketDistributor.sendToPlayer(player, payload);
        }
    }
}
