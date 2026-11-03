package io.github.matheusanbs.wirelessautomate.client.tablet.networks;

import static io.github.matheusanbs.wirelessautomate.client.tablet.networks.NetworksText.tr;

import io.github.matheusanbs.wirelessautomate.client.GuiPaint;
import io.github.matheusanbs.wirelessautomate.client.GuiText;
import io.github.matheusanbs.wirelessautomate.client.PanelPaint;
import io.github.matheusanbs.wirelessautomate.client.tablet.kit.ChipLayout;
import io.github.matheusanbs.wirelessautomate.menu.TabletSnapshot.AccessView;
import io.github.matheusanbs.wirelessautomate.menu.TabletSnapshot.Guest;
import io.github.matheusanbs.wirelessautomate.network.TeamAccess;
import java.util.UUID;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

/**
 * As fichas de quem tem acesso a uma rede privada (mockup {@code rede_detalhe}): os convidados e depois os times, cada
 * um com o "x" que o tira (um clique: a ficha é pequena e a pessoa pode ser convidada de novo), o "+N" do que não coube
 * e o link "+ convidar". Montadas no snapshot ({@link #set}): nomes cortados, larguras e posições; o desenho só lê.
 */
final class GuestChips {
    private static final int Y = 107;
    private static final int NAME_W = 60;
    private static final Component X = Component.literal("x");

    /** Uma ficha: quem ela tira ({@code team}: um time do FTB Teams), o nome cortado e a largura toda. */
    record Chip(UUID id, boolean team, GuiText.Fitted name, int w) {
    }

    private final Component invite = tr("nets.invite");
    private int inviteW;
    private Chip[] chips = new Chip[0];
    private int[] xs = new int[0];
    private Component more = Component.empty();
    private int moreX = -1;
    private int moreW;
    private int linkX;

    /** Monta as fichas da lista de acesso entre {@code vx} e {@code x1} (relativos ao painel). */
    void set(Font font, AccessView view, int vx, int x1) {
        inviteW = font.width(invite);
        int n = view.guests().size() + view.teams().size();
        Chip[] built = new Chip[n];
        int[] widths = new int[n];
        int i = 0;
        for (Guest guest : view.guests()) {
            built[i] = chip(font, guest.id(), false, guest.name());
            widths[i] = built[i].w() - 16;
            i++;
        }
        for (TeamAccess.TeamInfo team : view.teams()) {
            built[i] = chip(font, team.id(), true, team.name());
            widths[i] = built[i].w() - 16;
            i++;
        }
        chips = built;
        ChipLayout.Placed placed = ChipLayout.place(widths, vx, x1, inviteW, k -> font.width("+" + k));
        xs = placed.xs();
        moreX = placed.moreX();
        if (moreX >= 0) {
            more = Component.literal("+" + (n - placed.shown()));
            moreW = font.width(more) + 16;
        }
        linkX = placed.linkX();
    }

    private static Chip chip(Font font, UUID id, boolean team, String name) {
        GuiText.Fitted fitted = GuiText.fit(font, Component.literal(name), NAME_W);
        return new Chip(id, team, fitted, font.width(fitted.shown()) + 16);
    }

    void render(GuiGraphics g, Font font, int left, int top, int mx, int my) {
        int y = top + Y;
        for (int i = 0; i < xs.length; i++) {
            Chip chip = chips[i];
            int x = left + xs[i];
            GuiPaint.outline(g, x, y, chip.w(), 12, GuiPaint.BEVEL_DARK);
            GuiText.draw(g, font, chip.name(), x + 4, y + 2, GuiPaint.FG);
            boolean over = overX(x, chip.w(), y, mx, my);
            GuiPaint.text(g, font, X, x + chip.w() - 9, y + 2, over ? GuiPaint.DANGER : GuiPaint.FG);
        }
        if (moreX >= 0) {
            GuiPaint.outline(g, left + moreX, y, moreW, 12, GuiPaint.BEVEL_DARK);
            GuiPaint.text(g, font, more, left + moreX + 8, y + 2, GuiPaint.MUTED);
        }
        PanelPaint.link(g, font, invite, left + linkX, y + 2);
    }

    private static boolean overX(int x, int w, int y, double mx, double my) {
        return mx >= x + w - 11 && mx < x + w - 1 && my >= y && my < y + 12;
    }

    /** A ficha cujo "x" está sob o mouse, ou {@code null}. */
    @Nullable Chip removeAt(int left, int top, double mx, double my) {
        for (int i = 0; i < xs.length; i++) {
            if (overX(left + xs[i], chips[i].w(), top + Y, mx, my)) {
                return chips[i];
            }
        }
        return null;
    }

    /** Se o mouse está no "+N" ou no "+ convidar" (os dois abrem a janela de acesso). */
    boolean openAt(int left, int top, double mx, double my) {
        if (my < top + Y || my >= top + Y + 12) {
            return false;
        }
        boolean onMore = moreX >= 0 && mx >= left + moreX && mx < left + moreX + moreW;
        boolean onLink = mx >= left + linkX && mx < left + linkX + inviteW;
        return onMore || onLink;
    }

    int[] inviteCenter(int left, int top) {
        return new int[] {left + linkX + inviteW / 2, top + Y + 6};
    }

    /** Centro do "x" da ficha {@code index}, ou {@code null} se ela não está à vista. */
    int @Nullable [] removeCenter(int left, int top, int index) {
        if (index < 0 || index >= xs.length) {
            return null;
        }
        return new int[] {left + xs[index] + chips[index].w() - 6, top + Y + 6};
    }
}
