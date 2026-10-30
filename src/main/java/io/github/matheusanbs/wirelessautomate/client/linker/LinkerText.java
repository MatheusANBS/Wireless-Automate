package io.github.matheusanbs.wirelessautomate.client.linker;

import io.github.matheusanbs.wirelessautomate.client.GuiPaint;
import io.github.matheusanbs.wirelessautomate.linker.LinkerProblem;
import io.github.matheusanbs.wirelessautomate.menu.LinkerSnapshot;
import io.github.matheusanbs.wirelessautomate.menu.LinkerSnapshot.Outcome;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentUtils;
import org.jetbrains.annotations.Nullable;

/**
 * Os textos do Vinculador que dependem do estado: a contagem ou o que impede, o resultado do último
 * Vincular, o rótulo do botão e as dicas.
 */
final class LinkerText {
    private LinkerText() {
    }

    static Component tr(String key, Object... args) {
        return Component.translatable("gui.wirelessautomate.linker." + key, args);
    }

    /** Uma contagem: a chave {@code <key>.one} (o "1" no singular, já escrito) quando é uma, senão {@code <key>} com o número. */
    static Component count(String key, int n) {
        return n == 1 ? tr(key + ".one") : tr(key, n);
    }

    /** O texto de uma recusa do roteador escolhido (sem upgrade, raio 0). */
    static Component refusal(LinkerSnapshot.RouterInfo router) {
        return Component.translatable("item.wirelessautomate.linker." + router.refusal().key());
    }

    /** "N máquinas vinculadas na área", "Vincular junta as máquinas da área ao roteador", ou o que impede. */
    static Component status(LinkerSnapshot s) {
        if (s.problem() != LinkerProblem.NONE) {
            return switch (s.problem()) {
                case TOO_BIG -> tr("problem.too_big", s.maxVolume());
                case TOO_FAR -> tr("problem.too_far", s.maxDistance());
                default -> tr("problem." + s.problem().key());
            };
        }
        if (s.unlink()) {
            return s.linkedInside() == 1 ? tr("count.one") : tr("count", s.linkedInside());
        }
        return s.linkedInside() > 0 ? count("area.ready.with_linked", s.linkedInside()) : tr("area.ready");
    }

    static int statusColor(LinkerSnapshot s) {
        return switch (s.problem()) {
            case NONE -> GuiPaint.FG;
            case NO_AREA, INCOMPLETE -> GuiPaint.MUTED;
            default -> GuiPaint.WARN;
        };
    }

    /** Os chunks descarregados da área, que ficariam de fora; {@code null} se não há. */
    static @Nullable Component detail(LinkerSnapshot s) {
        if (s.unloadedChunks() == 0) {
            return null;
        }
        return s.unloadedChunks() == 1 ? tr("outcome.unloaded.one") : tr("outcome.unloaded", s.unloadedChunks());
    }

    /** O resultado do último Vincular ou Desvincular. */
    static Component outcomeText(Outcome o) {
        return count(o.unlink() ? "outcome.unlinked" : "outcome.linked", o.linked());
    }

    /** Embaixo do resultado: o que ficou de fora (até duas razões, a mais importante primeiro); {@code null} se nada. */
    static @Nullable Component outcomeDetail(Outcome o) {
        List<Component> parts = new ArrayList<>(2);
        if (o.outOfRange() > 0) {
            parts.add(tr("outcome.out_of_range", o.outOfRange()));
        }
        if (o.overLimit() > 0) {
            parts.add(tr("outcome.over_limit", o.overLimit()));
        }
        if (o.protectedCount() > 0) {
            parts.add(tr("outcome.protected", o.protectedCount()));
        }
        if (o.unloadedChunks() > 0) {
            parts.add(tr(o.unloadedChunks() == 1 ? "outcome.unloaded.one" : "outcome.unloaded", o.unloadedChunks()));
        }
        if (o.already() > 0) {
            parts.add(count("outcome.already", o.already()));
        }
        if (parts.isEmpty()) {
            return null;
        }
        return ComponentUtils.formatList(parts.subList(0, Math.min(2, parts.size())), Component.literal(" · "));
    }

    /** Se o botão Vincular (ou Desvincular) pode agir agora. */
    static boolean canLink(LinkerSnapshot s) {
        if (s.problem() != LinkerProblem.NONE || !s.router().ready()) {
            return false;
        }
        // desvincular não exige o upgrade valendo: dá para limpar a lista depois de tirá-lo
        return s.unlink() ? s.linkedInside() > 0 : s.router().links();
    }

    /** "Vincular área" ou "Desvincular N". */
    static Component linkLabel(LinkerSnapshot s) {
        return s.unlink() ? tr("unlink.count", s.linkedInside()) : tr("link.label");
    }

    static Component linkTooltip(LinkerSnapshot s) {
        if (s.problem() != LinkerProblem.NONE) {
            return status(s);
        }
        if (!s.router().ready()) {
            return tr(s.router().status() == LinkerSnapshot.Status.NONE ? "router.none.hint" : "router.missing");
        }
        if (s.unlink()) {
            return s.linkedInside() == 0 ? tr("unlink.none") : tr("unlink.tooltip");
        }
        return s.router().links() ? tr("link.tooltip") : refusal(s.router());
    }
}
