package io.github.matheusanbs.wirelessautomate.tv;

import io.github.matheusanbs.wirelessautomate.Config;
import io.github.matheusanbs.wirelessautomate.menu.TvMenu;
import io.github.matheusanbs.wirelessautomate.network.LoadedTypes;
import io.github.matheusanbs.wirelessautomate.network.NetworkSavedData;
import io.github.matheusanbs.wirelessautomate.network.WaNetwork;
import io.github.matheusanbs.wirelessautomate.packet.TvLayoutAckPayload;
import io.github.matheusanbs.wirelessautomate.packet.TvLayoutPayload;
import io.github.matheusanbs.wirelessautomate.packet.TvSizePayload;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.Nullable;

/** As edições da Tela de Sinal pela tela do bloco: o tamanho e o layout do editor. Servidor, sempre validando tudo. */
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

    /** O resultado de salvar um layout: se valeu, quantas ligações saíram e o que o cliente fica sabendo. */
    public record LayoutResult(boolean ok, int dropped, TvLayoutAckPayload.Result reason) {
        static final LayoutResult DENIED = new LayoutResult(false, 0, TvLayoutAckPayload.Result.DENIED);
        static final LayoutResult TOO_BIG = new LayoutResult(false, 0, TvLayoutAckPayload.Result.TOO_BIG);
    }

    /**
     * Salva o layout do editor: só dono ou operador; peças e ligações dentro dos tetos da config (senão recusa);
     * cada caixa cortada à área; ligação a rede que o jogador não pode usar ou a tipo não carregado sai, a peça
     * fica. Avisa na barra de ação.
     */
    public static LayoutResult applyLayout(ServerPlayer player, TvBlockEntity tv, TvLayout layout) {
        if (!tv.canEdit(player)) {
            player.displayClientMessage(Component.translatable("message.wirelessautomate.tv.layout_denied"), true);
            return LayoutResult.DENIED;
        }
        if (layout.pieces().size() > Config.tvMaxElements() || layout.bindings().size() > Config.tvMaxBindings()
                || TvLayoutPayload.encodedSize(layout) > Config.tvMaxLayoutBytes()) {
            player.displayClientMessage(Component.translatable("message.wirelessautomate.tv.layout_too_big"), true);
            return LayoutResult.TOO_BIG;
        }
        NetworkSavedData data = NetworkSavedData.get(player.server);
        int[] dropped = {0};
        List<TvPiece> pieces = new ArrayList<>(layout.pieces().size());
        for (TvPiece piece : layout.pieces()) {
            Optional<TvBinding> own = allowed(data, player, piece.binding(), dropped);
            List<TvRule> rules = new ArrayList<>(piece.rules().size());
            boolean rulesChanged = false;
            for (TvRule rule : piece.rules()) {
                Optional<TvBinding> bind = allowed(data, player, rule.bind(), dropped);
                if (bind.equals(rule.bind())) {
                    rules.add(rule);
                } else {
                    // Sem a ligação própria a regra leria o dado da peça e esconderia ou pintaria por outro valor.
                    rulesChanged = true;
                }
            }
            TvPiece fixed = own.equals(piece.binding()) ? piece : piece.withBinding(own);
            pieces.add(rulesChanged ? fixed.withRules(rules) : fixed);
        }
        int before = tv.revision();
        tv.setLayout(layout.withPieces(pieces));
        boolean changed = tv.revision() != before;
        Component message = dropped[0] == 0
                ? Component.translatable("message.wirelessautomate.tv.layout_saved")
                : Component.translatable("message.wirelessautomate.tv.layout_saved_dropped"
                        + (dropped[0] == 1 ? ".one" : ""), dropped[0]);
        player.displayClientMessage(message, true);
        return new LayoutResult(true, dropped[0],
                changed ? TvLayoutAckPayload.Result.SAVED : TvLayoutAckPayload.Result.UNCHANGED);
    }

    /** A ligação se o jogador pode usar a rede e o tipo está carregado; senão vazia, contando uma saída. */
    private static Optional<TvBinding> allowed(NetworkSavedData data, ServerPlayer player,
            Optional<TvBinding> binding, int[] dropped) {
        if (binding.isEmpty()) {
            return binding;
        }
        TvBinding value = binding.get();
        WaNetwork found = data.network(value.network());
        if (found != null && found.canUse(player.getUUID(), false) && LoadedTypes.contains(value.type())) {
            return binding;
        }
        dropped[0]++;
        return Optional.empty();
    }

    /** O limite de ações na biblioteca por jogador, 10 por segundo. */
    private static final TvRateGate GATE = new TvRateGate();

    /**
     * O limite de salvar o layout, 2 por segundo: cada save aceito manda o update tag (até o teto de bytes do layout)
     * a todos que veem a Tela, e um salvar legítimo é um clique.
     */
    private static final TvRateGate SAVE_GATE = new TvRateGate(2);

    /** Conta uma ação da biblioteca do jogador; falso se passou de 10 no segundo. */
    static boolean allowAction(ServerPlayer player) {
        return GATE.allow(player.getUUID(), player.level().getGameTime());
    }

    /** Solta a janela do jogador que saiu. */
    public static void forget(UUID player) {
        GATE.forget(player);
        SAVE_GATE.forget(player);
        TvLibraryActions.forget(player);
    }

    /** Esquece todas as janelas (parada do servidor). */
    public static void reset() {
        GATE.clear();
        SAVE_GATE.clear();
        TvLibraryActions.reset();
    }

    /**
     * O handler do layout: só com a tela aberta; a validação é a do {@link #applyLayout}. Responde sempre, aceito,
     * recusado ou barrado pelo limite, para o editor nunca esperar uma revisão que não vem. Devolve a resposta (nula
     * sem a tela aberta, quando não há a quem responder).
     */
    public static @Nullable TvLayoutAckPayload handleLayout(@Nullable ServerPlayer player, TvLayoutPayload payload) {
        TvBlockEntity tv = open(player, payload.containerId());
        if (tv == null) {
            return null;
        }
        TvLayoutAckPayload ack;
        if (!SAVE_GATE.allow(player.getUUID(), player.level().getGameTime())) {
            ack = new TvLayoutAckPayload(payload.containerId(), TvLayoutAckPayload.Result.THROTTLED, 0);
        } else {
            LayoutResult result = applyLayout(player, tv, payload.layout());
            ack = new TvLayoutAckPayload(payload.containerId(), result.reason(), result.dropped());
        }
        if (player.connection != null && player.connection.hasChannel(ack)) {
            PacketDistributor.sendToPlayer(player, ack);
        }
        return ack;
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

    static @Nullable TvBlockEntity open(@Nullable ServerPlayer player, int containerId) {
        if (player != null && player.containerMenu instanceof TvMenu menu && menu.containerId == containerId
                && menu.stillValid(player)) {
            return menu.tv();
        }
        return null;
    }
}
