package io.github.matheusanbs.wirelessautomate.client.linker;

import static io.github.matheusanbs.wirelessautomate.client.linker.LinkerText.tr;

import io.github.matheusanbs.wirelessautomate.client.FlatButton;
import io.github.matheusanbs.wirelessautomate.client.GuiPaint;
import io.github.matheusanbs.wirelessautomate.client.GuiText;
import io.github.matheusanbs.wirelessautomate.client.ResourceStyle;
import io.github.matheusanbs.wirelessautomate.item.LinkerItem;
import io.github.matheusanbs.wirelessautomate.menu.LinkerSnapshot;
import io.github.matheusanbs.wirelessautomate.network.ResourceType;
import io.github.matheusanbs.wirelessautomate.range.RemoteSide;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

/** O desenho dos botões do Vinculador: modos, chips dos tipos, Todos, linha Faces, texto e Vincular. */
final class LinkerPaint {
    private LinkerPaint() {
    }

    private static Font font() {
        return Minecraft.getInstance().font;
    }

    static void paintChoice(GuiGraphics g, FlatButton b, boolean hovered, Component text, boolean selected) {
        if (selected) {
            GuiPaint.box(g, b.getX(), b.getY(), b.getWidth(), b.getHeight(), GuiPaint.SELECTED, GuiPaint.SELECTED);
        } else {
            GuiPaint.box(g, b.getX(), b.getY(), b.getWidth(), b.getHeight(), GuiPaint.BUTTON,
                    hovered ? GuiPaint.BUTTON_HOVER_BORDER : GuiPaint.BUTTON_BORDER);
        }
        GuiPaint.textCentered(g, font(), text, b.getX() + b.getWidth() / 2 + 1, b.getY() + (b.getHeight() - 8) / 2,
                selected ? GuiPaint.SELECTED_TEXT : GuiPaint.FG);
    }

    /** O botão de um tipo, só o ícone: marcado, borda e fundo na cor do tipo (ou do desvincular). */
    static void paintType(GuiGraphics g, FlatButton b, boolean hovered, LinkerSnapshot s, ResourceType t) {
        boolean checked = s.tabs().contains(t);
        int color = s.unlink() ? GuiPaint.DANGER : ResourceStyle.color(t);
        GuiPaint.box(g, b.getX(), b.getY(), b.getWidth(), b.getHeight(),
                checked ? GuiPaint.mix(GuiPaint.INSET, color, 0.18f) : hovered ? GuiPaint.BUTTON : GuiPaint.INSET,
                checked ? color : hovered ? GuiPaint.BUTTON_HOVER_BORDER : GuiPaint.BUTTON_BORDER);
        ResourceStyle.drawIcon(g, t, b.getX() + (b.getWidth() - ResourceStyle.ICON) / 2,
                b.getY() + (b.getHeight() - ResourceStyle.ICON) / 2);
    }

    /** Um botão do cubo das faces: marcada, coral claro com a letra; desmarcada, afundada com a letra apagada. */
    static void paintFace(GuiGraphics g, FlatButton b, boolean hovered, LinkerSnapshot s, RemoteSide side) {
        paintMarked(g, b, hovered, LinkerItem.sideLetter(side), s.faces().contains(side));
    }

    /** "Todas" do cubo: coral claro com as seis marcadas. */
    static void paintAllFaces(GuiGraphics g, FlatButton b, boolean hovered, LinkerSnapshot s) {
        paintMarked(g, b, hovered, tr("faces.all"), s.faces().isAll());
    }

    private static void paintMarked(GuiGraphics g, FlatButton b, boolean hovered, Component text, boolean on) {
        Font font = font();
        if (on) {
            GuiPaint.box(g, b.getX(), b.getY(), b.getWidth(), b.getHeight(),
                    GuiPaint.mix(GuiPaint.BUTTON, GuiPaint.ACCENT, 0.25f), GuiPaint.ACCENT);
        } else {
            GuiPaint.box(g, b.getX(), b.getY(), b.getWidth(), b.getHeight(), hovered ? GuiPaint.BUTTON : GuiPaint.INSET,
                    hovered ? GuiPaint.BUTTON_HOVER_BORDER : GuiPaint.BEVEL_DARK);
        }
        int textW = Math.min(font.width(text), b.getWidth() - 2);
        GuiText.draw(g, font, text, b.getX() + (b.getWidth() - textW) / 2 + 1, b.getY() + (b.getHeight() - 8) / 2,
                textW, on ? GuiPaint.FG : hovered ? GuiPaint.FG : GuiPaint.MUTED);
    }

    /** Todos: botão de texto, na cor do Vinculador quando todas as abas estão marcadas. */
    static void paintAll(GuiGraphics g, FlatButton b, boolean hovered, LinkerSnapshot s) {
        boolean checked = LinkerText.allTabs(s);
        int color = s.unlink() ? GuiPaint.DANGER : GuiPaint.ACCENT;
        GuiPaint.box(g, b.getX(), b.getY(), b.getWidth(), b.getHeight(),
                checked ? GuiPaint.mix(GuiPaint.INSET, color, 0.18f) : hovered ? GuiPaint.BUTTON : GuiPaint.INSET,
                checked ? color : hovered ? GuiPaint.BUTTON_HOVER_BORDER : GuiPaint.BUTTON_BORDER);
        GuiText.draw(g, font(), tr("type.all"), b.getX() + 4, b.getY() + (b.getHeight() - 8) / 2, b.getWidth() - 8,
                checked || hovered ? GuiPaint.FG : GuiPaint.MUTED);
    }

    static void paintText(GuiGraphics g, FlatButton b, boolean hovered, Component text, int color) {
        int border = hovered ? GuiPaint.BUTTON_HOVER_BORDER : GuiPaint.BUTTON_BORDER;
        GuiPaint.box(g, b.getX(), b.getY(), b.getWidth(), b.getHeight(), b.active ? GuiPaint.BUTTON : GuiPaint.INSET,
                b.active ? border : GuiPaint.LINE);
        GuiPaint.textCentered(g, font(), text, b.getX() + b.getWidth() / 2 + 1, b.getY() + (b.getHeight() - 8) / 2,
                b.active ? color : GuiPaint.DISABLED);
    }

    /** Vincular: botão principal na cor da rede ativa, com o ponto dela. */
    static void paintLink(GuiGraphics g, FlatButton b, boolean hovered, LinkerSnapshot s) {
        Font font = font();
        int color = LinkerText.activeColor(s);
        if (b.active) {
            int fill = hovered ? GuiPaint.mix(color, GuiPaint.BEVEL_LIGHT, 0.15f) : color;
            GuiPaint.box(g, b.getX(), b.getY(), b.getWidth(), b.getHeight(), fill, GuiPaint.mix(color, GuiPaint.RING, 0.3f));
        } else {
            GuiPaint.box(g, b.getX(), b.getY(), b.getWidth(), b.getHeight(), GuiPaint.INSET, GuiPaint.LINE);
        }
        Component text = LinkerText.linkLabel(s);
        int textW = Math.min(font.width(text), b.getWidth() - 6);
        GuiText.draw(g, font, text, b.getX() + (b.getWidth() - textW) / 2, b.getY() + 4, textW,
                b.active ? GuiPaint.DARK_TEXT : GuiPaint.DISABLED);
    }
}
