package io.github.matheusanbs.wirelessautomate.tv;

import io.github.matheusanbs.wirelessautomate.WirelessAutomate;
import io.github.matheusanbs.wirelessautomate.packet.TvLibraryActionPayload;
import io.github.matheusanbs.wirelessautomate.packet.TvLibraryPayload;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
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
 * insiste não vira amplificação de banda e de codificação. Um {@code SAVE_ART} que faria a biblioteca passar do teto do
 * pacote ({@link TvLibraryPayload#MAX_BYTES}) é recusado com aviso: com 64 artes de 128 × 128 e os componentes no teto,
 * ela chegaria perto.
 */
public final class TvLibraryActions {
    /** O maior componente guardado, em bytes de NBT: 64 deles cabem no payload da biblioteca. */
    public static final int MAX_COMPONENT_BYTES = 12 * 1024;

    /**
     * O menu em que cada jogador já recebeu a biblioteca por um {@code OPEN} (a instância, não o {@code containerId},
     * que se repete de um menu para o outro).
     */
    private static final Map<UUID, AbstractContainerMenu> OPENED = new HashMap<>();

    /** O teto da biblioteca no pacote; os testes o baixam para provar a recusa sem montar 900 KB. */
    private static int maxLibraryBytes = TvLibraryPayload.MAX_BYTES;

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
        TvLibraryPayload withArt = null;
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
                case SAVE_ART -> {
                    TvPixelArt art = payload.art();
                    withArt = withArt(player, payload.containerId(), library, art);
                    yield withArt.encodedSize() > maxLibraryBytes ? TvLibrary.Result.TOO_BIG
                            : library.saveArt(player.getUUID(), art);
                }
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
            case TOO_BIG -> payload.action() == TvLibraryActionPayload.Action.SAVE_ART ? "library_art_too_big"
                    : "library_component_too_big";
            default -> null;
        };
        if (key != null) {
            player.displayClientMessage(Component.translatable("message.wirelessautomate.tv." + key), true);
        }
        if (result != TvLibrary.Result.OK) {
            return false;
        }
        if (withArt != null && withArt.arts().equals(library.arts(player.getUUID()))) {
            send(player, withArt);
        } else {
            send(player, payload.containerId(), library);
        }
        return true;
    }

    /** A biblioteca como ficaria com a arte (no lugar da de mesmo id, ou no fim): medida antes de salvar. */
    private static TvLibraryPayload withArt(ServerPlayer player, int containerId, TvLibrary library, TvPixelArt art) {
        List<TvPixelArt> arts = new ArrayList<>(library.arts(player.getUUID()));
        boolean replaced = false;
        for (int i = 0; i < arts.size() && !replaced; i++) {
            if (arts.get(i).id().equals(art.id())) {
                arts.set(i, art);
                replaced = true;
            }
        }
        if (!replaced) {
            arts.add(art);
        }
        return new TvLibraryPayload(containerId, library.components(player.getUUID()),
                library.themes(player.getUUID()), arts);
    }

    /** Baixa o teto da biblioteca no pacote (só os testes; {@code -1} volta ao de verdade). */
    public static void setMaxLibraryBytesForTests(int max) {
        maxLibraryBytes = max < 0 ? TvLibraryPayload.MAX_BYTES : max;
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
        send(player, payload(player, containerId, library));
    }

    private static void send(ServerPlayer player, TvLibraryPayload payload) {
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
