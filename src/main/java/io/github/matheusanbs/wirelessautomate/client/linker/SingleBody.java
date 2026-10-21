package io.github.matheusanbs.wirelessautomate.client.linker;

import static io.github.matheusanbs.wirelessautomate.client.linker.LinkerText.tr;

import io.github.matheusanbs.wirelessautomate.client.GuiPaint;
import io.github.matheusanbs.wirelessautomate.client.GuiText;
import io.github.matheusanbs.wirelessautomate.item.LinkerItem;
import io.github.matheusanbs.wirelessautomate.linker.LinkerFaces;
import io.github.matheusanbs.wirelessautomate.menu.LinkerSnapshot;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import org.jetbrains.annotations.Nullable;

/**
 * O corpo do modo Único, na coluna da direita: a explicação do clique e as dicas que cabem acima da
 * linha Faces; as que não cabem vão para a dica do mouse de uma linha "Mais dicas".
 */
final class SingleBody {
    private final LinkerScreen screen;
    /** As dicas que não couberam acima da linha Faces e onde fica o "Mais dicas" (x, y, l, a). */
    private @Nullable Component moreHints;
    private int[] moreArea = new int[4];

    SingleBody(LinkerScreen screen) {
        this.screen = screen;
    }

    /** A dica do "Mais dicas" com o mouse em cima dele; {@code null} fora dele ou sem dicas de fora. */
    @Nullable Component tooltip(int mouseX, int mouseY) {
        if (moreHints != null && mouseX >= moreArea[0] && mouseX < moreArea[0] + moreArea[2] && mouseY >= moreArea[1]
                && mouseY < moreArea[1] + moreArea[3]) {
            return moreHints;
        }
        return null;
    }

    void render(GuiGraphics g, int x, int y) {
        Font font = Minecraft.getInstance().font;
        LinkerSnapshot s = screen.snapshot();
        boolean unlink = s.unlink();
        LinkerFaces faces = s.faces();
        int rw = screen.rw();
        int lineY = y;
        Component main = unlink ? tr("single.unlink.body")
                : tr("single.body", Component.literal(LinkerText.activeName(s).getString())
                        .withColor(LinkerText.activeColor(s)));
        lineY += GuiText.wrap(g, font, main, x, lineY, rw, 4, GuiPaint.FG);
        lineY += 6;
        boolean allTabs = LinkerText.allTabs(s);
        Component typeHint = unlink
                ? (allTabs ? tr("single.unlink.all") : tr("single.unlink.tabs", LinkerText.tabsName(s)))
                : (allTabs ? tr("single.all") : tr("single.tabs", LinkerText.tabsName(s)));
        List<Component> hints = new ArrayList<>();
        List<Integer> colors = new ArrayList<>();
        hints.add(typeHint);
        colors.add(GuiPaint.MUTED);
        if (faces.isEmpty()) {
            hints.add(tr("problem.no_faces"));
            colors.add(GuiPaint.WARN);
        } else if (!faces.isAll()) {
            hints.add(tr(unlink ? "single.unlink.faces" : "single.faces", LinkerItem.facesNames(faces)));
            colors.add(GuiPaint.MUTED);
        }
        hints.add(tr("single.toggle"));
        colors.add(GuiPaint.MUTED);
        hints.add(tr("single.open"));
        colors.add(GuiPaint.MUTED);
        // as dicas param antes do pé da coluna
        int limit = screen.top() + screen.bodyBottomY() - 4;
        boolean[] shown = fit(font, hints, lineY, limit, rw);
        MutableComponent more = Component.empty();
        moreHints = null;
        for (int i = 0; i < hints.size(); i++) {
            if (shown[i]) {
                lineY += GuiText.wrap(g, font, hints.get(i), x, lineY, rw, 4, colors.get(i));
                lineY += 4;
            } else {
                if (!more.getSiblings().isEmpty()) {
                    more.append(Component.literal("\n"));
                }
                more.append(hints.get(i));
            }
        }
        if (!more.getSiblings().isEmpty()) {
            Component label = tr("single.more");
            GuiText.draw(g, font, label, x, lineY, rw, GuiPaint.MUTED);
            moreHints = more;
            moreArea = new int[] {x, lineY - 1, Math.min(rw, font.width(label)), 10};
        }
    }

    /**
     * Quais dicas cabem entre {@code top} e {@code limit}. Cabem pela prioridade (a das abas, a das
     * faces, Shift + roda e por último a do Shift + clique); as que não cabem vão para a dica do mouse
     * de uma linha "Mais dicas", nunca somem sem rastro.
     */
    private static boolean[] fit(Font font, List<Component> hints, int top, int limit, int rw) {
        int last = hints.size() - 1;
        List<Integer> priority = new ArrayList<>();
        for (int i = 0; i < last - 1; i++) {
            priority.add(i);
        }
        priority.add(last);
        priority.add(last - 1);
        int[] height = new int[hints.size()];
        for (int i = 0; i < hints.size(); i++) {
            height[i] = GuiText.lineCount(font, hints.get(i), rw, 4) * 10 + 4;
        }
        boolean[] shown = new boolean[hints.size()];
        int used = top;
        boolean dropped = false;
        for (int i : priority) {
            if (used + height[i] - 4 > limit) {
                dropped = true;
                continue;
            }
            shown[i] = true;
            used += height[i];
        }
        // com alguma de fora, a linha "Mais dicas" também precisa caber: tira as menos importantes
        for (int k = priority.size() - 1; dropped && k >= 0 && used + 10 - 4 > limit; k--) {
            int i = priority.get(k);
            if (shown[i]) {
                shown[i] = false;
                used -= height[i];
            }
        }
        return shown;
    }
}
