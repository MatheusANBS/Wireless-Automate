package io.github.matheusanbs.wirelessautomate.client.tablet;

import static io.github.matheusanbs.wirelessautomate.client.tablet.ListLayout.BANNER_H;
import static io.github.matheusanbs.wirelessautomate.client.tablet.TabletText.tr;

import io.github.matheusanbs.wirelessautomate.client.GuiText;
import io.github.matheusanbs.wirelessautomate.client.router.NodePanel;
import io.github.matheusanbs.wirelessautomate.client.tablet.ListLayout.Hot;
import io.github.matheusanbs.wirelessautomate.client.tablet.ListLayout.Kind;
import io.github.matheusanbs.wirelessautomate.menu.TabletSnapshot.ProfileSummary;
import io.github.matheusanbs.wirelessautomate.menu.TabletSnapshot.QuickFilter;
import io.github.matheusanbs.wirelessautomate.packet.TabletPanelPayload;
import java.util.Locale;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

/** As dicas da Lista do Tablet: as do painel, da faixa do perfil e de cada área, com o texto cortado inteiro. */
final class ListTooltips {
    private final TabletList list;
    private final ListLayout layout;

    ListTooltips(TabletList list, ListLayout layout) {
        this.list = list;
        this.layout = layout;
    }

    /**
     * A dica do ponto, ou {@code null}. Um texto cortado sob o mouse ({@link GuiText#clipAt}) vem inteiro
     * primeiro e a dica da área depois: a segunda linha de um nó e o cabeçalho do grupo cortados ficam legíveis.
     */
    @Nullable Component tooltip(double mx, double my) {
        layout.arrangeForFrame();
        NodePanel p = list.chosenPanel.current();
        if (p != null && p.popupOpen()) {
            return null;
        }
        Component area = areaTooltip(p, mx, my);
        Component clipped = GuiText.clipAt(mx, my);
        if (clipped == null || (area != null && area.getString().contains(clipped.getString()))) {
            return area;
        }
        return area == null ? clipped : clipped.copy().append("\n").append(area);
    }

    private @Nullable Component areaTooltip(@Nullable NodePanel p, double mx, double my) {
        if (p != null && mx >= layout.panelX) {
            Component tip = p.tooltip(mx, my);
            if (tip != null) {
                return tip;
            }
        }
        Component banner = bannerTooltip(mx, my);
        if (banner != null) {
            return banner;
        }
        Hot hot = layout.hotAt(mx, my);
        if (hot == null) {
            return null;
        }
        return switch (hot.kind()) {
            case LABEL -> tr("list.label.tooltip", list.texts.labelText(list.search.labels.get(hot.arg())));
            case QUICK -> tr("list.quick." + QuickFilter.values()[hot.arg()].name().toLowerCase(Locale.ROOT) + ".tooltip");
            case TYPE_CHIP -> tr("list.type.tooltip");
            case FORGET -> tr("list.profile.forget.tooltip");
            case HEADER -> list.model.collapsed(list.model.groups().rows().get(hot.arg()).group()) ? tr("list.group.open")
                    : tr("list.group.close");
            case PASTE_GROUP -> pasteGroupTooltip(list.model.groups().rows().get(hot.arg()).group());
            case ROW -> list.nodes.nodeTooltip(list.snapshot().nodes().get(hot.arg()), !list.multi);
            case FULL_SCREEN -> tr("open.tooltip");
            case COPY -> list.chosenPanel.canEdit() ? tr("list.copy.tooltip") : readOnlyTip();
            case PASTE -> !list.chosenPanel.canEdit() ? readOnlyTip()
                    : list.profile().map(s -> tr("list.paste.tooltip", s.sourceName())).orElse(null);
            case CLEAR, UNDO -> null;
        };
    }

    private Component readOnlyTip() {
        TabletPanelPayload payload = list.chosenPanel.payload();
        Component reason = payload == null ? null : ChosenPanel.readOnlyReason(payload);
        return reason == null ? tr("list.copy.tooltip") : reason;
    }

    private Component pasteGroupTooltip(ResourceLocation machine) {
        ProfileSummary s = list.profile().orElseThrow();
        return tr("list.paste_group.tooltip", s.sourceName(), list.profileActions.pasteCount(machine), list.texts.machineName(machine));
    }

    /** A dica da faixa: de onde veio o perfil, a máquina e o que ele tem. */
    private @Nullable Component bannerTooltip(double mx, double my) {
        ProfileSummary s = list.profile().orElse(null);
        Hot forget = layout.hot(Kind.FORGET, 0);
        if (s == null || forget == null || mx < layout.x0 || mx >= forget.x() || my < forget.y()
                || my >= forget.y() + BANNER_H) {
            return null;
        }
        return tr("list.profile.banner", list.texts.sourceName(s), list.texts.machineName(s.machine()),
                ListTexts.profileSummary(s));
    }
}
