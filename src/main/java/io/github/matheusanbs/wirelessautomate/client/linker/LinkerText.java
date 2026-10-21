package io.github.matheusanbs.wirelessautomate.client.linker;

import io.github.matheusanbs.wirelessautomate.GuiTheme;
import io.github.matheusanbs.wirelessautomate.client.GuiPaint;
import io.github.matheusanbs.wirelessautomate.client.GuiText;
import io.github.matheusanbs.wirelessautomate.client.GuiThemes;
import io.github.matheusanbs.wirelessautomate.client.ResourceStyle;
import io.github.matheusanbs.wirelessautomate.item.LinkerItem;
import io.github.matheusanbs.wirelessautomate.linker.LinkerFaces;
import io.github.matheusanbs.wirelessautomate.linker.LinkerMode;
import io.github.matheusanbs.wirelessautomate.linker.LinkerProblem;
import io.github.matheusanbs.wirelessautomate.linker.LinkerTabs;
import io.github.matheusanbs.wirelessautomate.menu.LinkerSnapshot;
import io.github.matheusanbs.wirelessautomate.menu.LinkerSnapshot.Outcome;
import io.github.matheusanbs.wirelessautomate.menu.RouterSnapshot.NetworkEntry;
import io.github.matheusanbs.wirelessautomate.network.ResourceType;
import io.github.matheusanbs.wirelessautomate.range.RemoteSide;
import java.util.List;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import org.jetbrains.annotations.Nullable;

/**
 * Os textos do Vinculador que dependem do estado: nomes das abas e da rede ativa, a contagem ou o que
 * impede, o resultado do último Vincular, o rótulo do botão e as dicas dos botões.
 */
final class LinkerText {
    private LinkerText() {
    }

    static Component tr(String key, Object... args) {
        return Component.translatable("gui.wirelessautomate.linker." + key, args);
    }

    static Component typeName(@Nullable ResourceType type) {
        return type == null ? tr("type.all")
                : ResourceStyle.name(type);
    }

    static Component modeName(LinkerMode mode) {
        return tr("mode." + mode.getSerializedName());
    }

    /** Modo Máquinas: o Vincular liga as máquinas da área ao roteador escolhido. */
    static boolean machines(LinkerSnapshot s) {
        return s.mode() == LinkerMode.MACHINES;
    }

    /** Todas as abas que existem estão marcadas. */
    static boolean allTabs(LinkerSnapshot s) {
        return s.tabs().isAll(s.available().types());
    }

    /** "Todos" ou "Itens + Fluidos", pelas abas que valem. */
    static Component tabsName(LinkerSnapshot s) {
        return tabsName(s.tabs(), s.available().types());
    }

    static Component tabsName(LinkerTabs tabs, List<ResourceType> available) {
        if (tabs.isAll(available)) {
            return tr("type.all");
        }
        MutableComponent text = Component.empty();
        List<ResourceType> effective = tabs.effective(available);
        for (int i = 0; i < effective.size(); i++) {
            if (i > 0) {
                text.append(" + ");
            }
            text.append(typeName(effective.get(i)));
        }
        return text;
    }

    static Component activeName(LinkerSnapshot s) {
        if (s.unlink()) {
            return tr("network.unlink");
        }
        NetworkEntry entry = s.activeEntry();
        return entry == null ? tr("network.none") : Component.literal(entry.name());
    }

    /** A cor da rede como tinta de texto: no tema claro, as claras (amarelo) escurecem para ler sobre o creme. */
    static int readable(int rgb) {
        int r = rgb >> 16 & 0xFF;
        int g = rgb >> 8 & 0xFF;
        int b = rgb & 0xFF;
        if (GuiThemes.current() == GuiTheme.LIGHT && 0.299 * r + 0.587 * g + 0.114 * b > 140) {
            r = r * 6 / 10;
            g = g * 6 / 10;
            b = b * 6 / 10;
        }
        return 0xFF000000 | r << 16 | g << 8 | b;
    }

    static int activeColor(LinkerSnapshot s) {
        if (s.unlink()) {
            return GuiPaint.DANGER;
        }
        NetworkEntry entry = s.activeEntry();
        return entry == null ? GuiPaint.MUTED : readable(entry.color());
    }

    /** Roteadores que o Vincular mudaria. */
    static int toLink(LinkerSnapshot s) {
        return s.inside() - s.already();
    }

    /** "N roteadores na área" (ou "N roteadores · M já na rede"), ou o que impede. */
    static Component status(LinkerSnapshot s) {
        return switch (s.problem()) {
            case NONE -> s.mode() == LinkerMode.MACHINES ? tr("machines.status")
                    : s.already() > 0
                    ? tr(s.unlink() ? "count.with_unlinked" : "count.with_already", s.inside(), s.already())
                    : s.inside() == 1 ? tr("count.one") : tr("count", s.inside());
            case TOO_BIG -> tr("problem.too_big", s.maxVolume());
            case TOO_FAR -> tr("problem.too_far", s.maxDistance());
            default -> tr("problem." + s.problem().key());
        };
    }

    /** Os chunks descarregados da área, que ficariam de fora; {@code null} se não há. */
    static @Nullable Component detail(LinkerSnapshot s) {
        if (s.unloadedChunks() == 0) {
            return null;
        }
        return s.unloadedChunks() == 1 ? tr("outcome.unloaded.one") : tr("outcome.unloaded", s.unloadedChunks());
    }

    static int statusColor(LinkerSnapshot s) {
        return switch (s.problem()) {
            case NONE -> GuiPaint.FG;
            case NO_AREA, INCOMPLETE -> GuiPaint.MUTED;
            default -> GuiPaint.WARN;
        };
    }

    static Component outcomeText(LinkerSnapshot s, Outcome o) {
        boolean someFaces = !o.faces().isAll();
        if (o.unlink()) {
            Component tabs = tabsName(o.tabs(), s.available().types());
            return someFaces ? tr("outcome.unlinked_faces", o.linked(), LinkerItem.facesLetters(o.faces()), tabs)
                    : tr("outcome.unlinked", o.linked(), tabs);
        }
        Component network = Component.literal(o.network()).withColor(readable(o.color()));
        return someFaces ? tr("outcome.faces", o.linked(), LinkerItem.facesLetters(o.faces()), network)
                : tr("outcome", o.linked(), network);
    }

    /**
     * Embaixo do resultado: os chunks descarregados que ficaram de fora ou, sem eles, os protegidos
     * ou os que já estavam (não cabem todos numa linha).
     */
    static @Nullable Component outcomeDetail(Outcome o) {
        if (o.unloadedChunks() > 0) {
            return o.unloadedChunks() == 1 ? tr("outcome.unloaded.one") : tr("outcome.unloaded", o.unloadedChunks());
        }
        if (o.protectedCount() > 0) {
            return tr("outcome.protected", o.protectedCount());
        }
        if (o.already() > 0) {
            return tr(o.unlink() ? "outcome.already_unlinked" : "outcome.already", o.already());
        }
        return null;
    }

    static Component linkTooltip(LinkerSnapshot s) {
        if (s.problem() != LinkerProblem.NONE) {
            return status(s);
        }
        if (machines(s)) {
            return tr(s.unlink() ? "machines.unlink.tooltip" : "machines.link.tooltip");
        }
        if (toLink(s) == 0) {
            return s.inside() == 0 ? tr("link.none") : tr(s.unlink() ? "unlink.all_out" : "link.all_in");
        }
        LinkerFaces faces = s.faces();
        if (!faces.isAll()) {
            Component names = LinkerItem.facesNames(faces);
            return s.unlink() ? tr("unlink.tooltip.faces", toLink(s), names, tabsName(s))
                    : tr("link.tooltip.faces", toLink(s), names, activeName(s), tabsName(s));
        }
        if (s.unlink()) {
            return allTabs(s) ? tr("unlink.tooltip", toLink(s)) : tr("unlink.tooltip.tabs", toLink(s), tabsName(s));
        }
        return allTabs(s) ? tr("link.tooltip", toLink(s), activeName(s))
                : tr("link.tooltip.tabs", toLink(s), tabsName(s), activeName(s));
    }

    /**
     * "Vincular 9" ou "Desvincular 9"; com só algumas faces, "Vincular 9 · T, B" ou "Desvincular T, B ·
     * 9" (abreviado pelo {@link GuiText}; o tooltip diz tudo). Quais abas entram aparece nas caixas e
     * no tooltip: pôr o número delas aqui não cabe no botão (82 px) em português.
     */
    static Component linkLabel(LinkerSnapshot s) {
        boolean unlink = s.unlink();
        if (machines(s)) {
            return tr(unlink ? "machines.unlink" : "machines.link");
        }
        if (s.faces().isEmpty()) {
            return tr(unlink ? "unlink.label" : "link.label");
        }
        if (!s.faces().isAll()) {
            return tr(unlink ? "unlink.count.faces" : "link.count.faces", toLink(s),
                    LinkerItem.facesLetters(s.faces()));
        }
        return tr(unlink ? "unlink.count" : "link.count", toLink(s));
    }

    static Component faceTooltip(LinkerSnapshot s, RemoteSide side) {
        return LinkerItem.sideName(side).copy().append("\n")
                .append(tr(s.faces().contains(side) ? "faces.side.on" : "faces.side.off").copy()
                        .withColor(GuiPaint.TOOLTIP_MUTED))
                .append("\n").append(tr("faces.side.frame").copy().withColor(GuiPaint.TOOLTIP_MUTED))
                .append("\n").append(tr("faces.side.no_front").copy().withColor(GuiPaint.TOOLTIP_MUTED));
    }

    static Component allFacesTooltip(LinkerSnapshot s) {
        return tr(s.faces().isAll() ? "faces.all.tooltip.on" : "faces.all.tooltip");
    }

    static Component tabTooltip(LinkerSnapshot s, ResourceType t) {
        boolean checked = s.tabs().contains(t);
        if (checked && s.tabs().toggle(t).isEmpty(s.available().types())) {
            return tr("tab.last", typeName(t));
        }
        return tr(checked ? "tab.on" : "tab.off", typeName(t));
    }

    static Component allTooltip(LinkerSnapshot s) {
        return allTabs(s) ? tr("type.all.tooltip.one") : tr("type.all.tooltip");
    }
}
