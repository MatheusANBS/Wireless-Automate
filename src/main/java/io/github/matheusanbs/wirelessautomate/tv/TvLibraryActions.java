package io.github.matheusanbs.wirelessautomate.tv;

import io.github.matheusanbs.wirelessautomate.WirelessAutomate;
import io.github.matheusanbs.wirelessautomate.packet.TvLibraryActionPayload;
import io.github.matheusanbs.wirelessautomate.packet.TvLibraryPayload;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.Nullable;

/**
 * As ações na biblioteca do jogador pela tela da Tela de Sinal: só com o menu aberto, no máximo 10 por segundo, tudo
 * validado aqui. A biblioteca inteira só volta ao cliente quando mudou (uma ação que valeu) e, no {@code OPEN}, uma
 * vez por menu aberto: a recusa só avisa na barra de ação, e repetir o pedido não custa nada. Assim um cliente que
 * insiste não vira amplificação de banda e de codificação.
 */
public final class TvLibraryActions {
    /** O maior componente guardado, em bytes de NBT: 64 deles cabem no payload da biblioteca. */
    public static final int MAX_COMPONENT_BYTES = 12 * 1024;

    /**
     * O menu em que cada jogador já recebeu a biblioteca por um {@code OPEN} (a instância, não o {@code containerId},
     * que se repete de um menu para o outro).
     */
    private static final Map<UUID, AbstractContainerMenu> OPENED = new HashMap<>();

    private TvLibraryActions() {
    }

    /** Solta o jogador que saiu. */
    public static void forget(UUID player) {
        OPENED.remove(player);
    }

    /** Esquece todos (parada do servidor). */
    public static void reset() {
        OPENED.clear();
    }

    /** Trata a ação; devolve se a biblioteca foi (ou iria, sem o canal dos testes) ao cliente. */
    public static boolean handle(@Nullable ServerPlayer player, TvLibraryActionPayload payload) {
        if (TvEditing.open(player, payload.containerId()) == null || !TvEditing.allowAction(player)) {
            return false;
        }
        if (payload.action() == TvLibraryActionPayload.Action.OPEN) {
            if (OPENED.put(player.getUUID(), player.containerMenu) == player.containerMenu) {
                return false;
            }
            send(player, payload.containerId(), TvLibrary.get(player.server));
            return true;
        }
        TvLibrary library = TvLibrary.get(player.server);
        TvLibrary.Result result;
        try {
            result = switch (payload.action()) {
                case OPEN -> TvLibrary.Result.OK;
                case SAVE_COMPONENT -> payload.content().length > MAX_COMPONENT_BYTES ? TvLibrary.Result.TOO_BIG
                        : library.saveComponent(player.getUUID(), payload.component());
                case SAVE_THEME -> library.saveTheme(player.getUUID(), payload.theme());
                case RENAME -> payload.kind() == TvLibraryActionPayload.Kind.COMPONENT
                        ? library.renameComponent(player.getUUID(), payload.name(), payload.newName())
                        : library.renameTheme(player.getUUID(), payload.name(), payload.newName());
                case REMOVE -> payload.kind() == TvLibraryActionPayload.Kind.COMPONENT
                        ? library.removeComponent(player.getUUID(), payload.name())
                        : library.removeTheme(player.getUUID(), payload.name());
                case SAVE_ART -> library.saveArt(player.getUUID(), payload.art());
                case RENAME_ART -> library.renameArt(player.getUUID(), payload.artId(), payload.newName());
                case REMOVE_ART -> library.removeArt(player.getUUID(), payload.artId());
            };
        } catch (RuntimeException e) {
            // Conteúdo que não decodifica (ou um id de arte que não é UUID): a ação é recusada, sem derrubar nada.
            result = TvLibrary.Result.INVALID;
        }
        String key = switch (result) {
            case FULL -> "library_full";
            case EXISTS -> "library_exists";
            case INVALID -> "library_invalid";
            case TOO_BIG -> "library_component_too_big";
            default -> null;
        };
        if (key != null) {
            player.displayClientMessage(Component.translatable("message.wirelessautomate.tv." + key), true);
        }
        if (result != TvLibrary.Result.OK) {
            return false;
        }
        send(player, payload.containerId(), library);
        return true;
    }

    /** A biblioteca do jogador como payload. */
    public static TvLibraryPayload payload(ServerPlayer player, int containerId, TvLibrary library) {
        return new TvLibraryPayload(containerId, library.components(player.getUUID()), library.themes(player.getUUID()),
                library.arts(player.getUUID()));
    }

    /**
     * Manda a biblioteca a quem tem o canal negociado (sem conexão de verdade, como nos testes, não manda). Os tetos
     * da config já garantem que cabe; a conferência é uma passada só e, se algum dia passar, avisa em vez de largar
     * componentes.
     */
    public static void send(ServerPlayer player, int containerId, TvLibrary library) {
        TvLibraryPayload payload = payload(player, containerId, library);
        if (player.connection == null || !player.connection.hasChannel(payload)) {
            return;
        }
        if (payload.encodedSize() > TvLibraryPayload.MAX_BYTES) {
            WirelessAutomate.LOGGER.warn("Tela de Sinal: a biblioteca de {} passa de {} bytes e não foi enviada",
                    player.getGameProfile().getName(), TvLibraryPayload.MAX_BYTES);
            player.displayClientMessage(Component.translatable("message.wirelessautomate.tv.library_too_big"), true);
            return;
        }
        PacketDistributor.sendToPlayer(player, payload);
    }
}
