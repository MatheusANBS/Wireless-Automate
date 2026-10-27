package io.github.matheusanbs.wirelessautomate.tv;

import io.github.matheusanbs.wirelessautomate.Config;
import io.github.matheusanbs.wirelessautomate.menu.TvMenu;
import io.github.matheusanbs.wirelessautomate.network.LoadedTypes;
import io.github.matheusanbs.wirelessautomate.network.NetworkSavedData;
import io.github.matheusanbs.wirelessautomate.network.ResourceType;
import io.github.matheusanbs.wirelessautomate.network.WaNetwork;
import io.github.matheusanbs.wirelessautomate.packet.TvSizePayload;
import io.github.matheusanbs.wirelessautomate.packet.TvTemplatePayload;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import org.jetbrains.annotations.Nullable;

/** As edições da Tela de Sinal pela tela do bloco: tamanho e modelo. Servidor, sempre validando tudo. */
public final class TvEditing {
    private TvEditing() {
    }

    /**
     * Aplica a área se o mundo a aceita; senão avisa o problema na barra de ação e não muda nada.
     * Devolve o problema achado ({@link TvAreaCheck.Problem#NONE} quando aplicou).
     */
    public static TvAreaCheck.Problem applySize(ServerPlayer player, TvBlockEntity tv, TvArea area) {
        if (tv.getLevel() == null) {
            return new TvAreaCheck.Problem(TvAreaCheck.Kind.BLOCKED, tv.getBlockPos());
        }
        int maxWidth = Config.tvMaxWidth();
        int maxHeight = Config.tvMaxHeight();
        TvAreaCheck.Problem problem = TvAreaCheck.check(tv.getLevel(), tv.getBlockPos(),
                tv.getBlockState().getValue(TvBlock.FACING), area, maxWidth, maxHeight, TvLoaded.of(tv.getLevel()));
        if (problem.ok()) {
            tv.setArea(area);
            return problem;
        }
        String key = "message.wirelessautomate.tv.problem." + kindKey(problem.kind());
        BlockPos at = problem.pos();
        Component message = at == null
                ? Component.translatable(key, maxWidth, maxHeight)
                : Component.translatable(key, at.getX() + " " + at.getY() + " " + at.getZ());
        player.displayClientMessage(message, true);
        return problem;
    }

    private static String kindKey(TvAreaCheck.Kind kind) {
        return switch (kind) {
            case TOO_BIG -> "too_big";
            case NO_WALL -> "no_wall";
            case BLOCKED -> "blocked";
            case OVERLAP -> "overlap";
            case NONE -> "none";
        };
    }

    /**
     * Sem rede, limpa o layout; com rede, exige que o jogador possa usá-la e o tipo carregado e aplica o Painel de
     * rede dentro dos tetos da config. Devolve se aplicou; recusas avisam na barra de ação.
     */
    public static boolean applyTemplate(ServerPlayer player, TvBlockEntity tv, @Nullable UUID network,
            @Nullable ResourceType type) {
        if (network == null) {
            tv.setLayout(TvLayout.EMPTY);
            return true;
        }
        WaNetwork found = NetworkSavedData.get(player.server).network(network);
        if (found == null || !found.canUse(player) || type == null || !LoadedTypes.contains(type)) {
            player.displayClientMessage(Component.translatable("message.wirelessautomate.tv.template_denied"), true);
            return false;
        }
        TvLayout layout = TvTemplates.networkPanel(found.name(), found.color(), network, type, tv.area());
        if (layout.pieces().size() > Config.tvMaxElements() || layout.bindings().size() > Config.tvMaxBindings()) {
            player.displayClientMessage(Component.translatable("message.wirelessautomate.tv.template_too_big"), true);
            return false;
        }
        tv.setLayout(layout);
        return true;
    }

    /** O handler do tamanho: só com a tela aberta, e a estrutura da área validada antes de construí-la. */
    public static void handleSize(@Nullable ServerPlayer player, TvSizePayload payload) {
        TvBlockEntity tv = open(player, payload.containerId());
        if (tv == null) {
            return;
        }
        int w = payload.width();
        int h = payload.height();
        int col = payload.anchorCol();
        int row = payload.anchorRow();
        if (w < 1 || h < 1 || col < 0 || col >= w || row < 0 || row >= h) {
            return;
        }
        applySize(player, tv, new TvArea(w, h, col, row));
    }

    /** O handler do modelo: o tipo vem pela chave, e uma desconhecida conta como tipo ausente. */
    public static void handleTemplate(@Nullable ServerPlayer player, TvTemplatePayload payload) {
        TvBlockEntity tv = open(player, payload.containerId());
        if (tv == null) {
            return;
        }
        applyTemplate(player, tv, payload.network().orElse(null), ResourceType.byKey(payload.typeKey()));
    }

    private static @Nullable TvBlockEntity open(@Nullable ServerPlayer player, int containerId) {
        if (player != null && player.containerMenu instanceof TvMenu menu && menu.containerId == containerId
                && menu.stillValid(player)) {
            return menu.tv();
        }
        return null;
    }
}
