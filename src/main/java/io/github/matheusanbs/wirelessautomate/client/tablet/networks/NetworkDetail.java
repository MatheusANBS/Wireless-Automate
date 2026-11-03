package io.github.matheusanbs.wirelessautomate.client.tablet.networks;

import static io.github.matheusanbs.wirelessautomate.client.tablet.networks.NetworksText.plural;
import static io.github.matheusanbs.wirelessautomate.client.tablet.networks.NetworksText.tr;

import io.github.matheusanbs.wirelessautomate.client.ArmedClick;
import io.github.matheusanbs.wirelessautomate.client.FlatButton;
import io.github.matheusanbs.wirelessautomate.client.GuiPaint;
import io.github.matheusanbs.wirelessautomate.client.GuiText;
import io.github.matheusanbs.wirelessautomate.client.PanelPaint;
import io.github.matheusanbs.wirelessautomate.client.ResourceStyle;
import io.github.matheusanbs.wirelessautomate.client.tablet.TabletLayout;
import io.github.matheusanbs.wirelessautomate.client.tablet.TabletScreen;
import io.github.matheusanbs.wirelessautomate.client.tablet.kit.PanelHeader;
import io.github.matheusanbs.wirelessautomate.client.tablet.kit.Segments;
import io.github.matheusanbs.wirelessautomate.menu.TabletSnapshot.GroupView;
import io.github.matheusanbs.wirelessautomate.menu.TabletSnapshot.NetworkView;
import io.github.matheusanbs.wirelessautomate.menu.TabletSnapshot.TypeCount;
import java.util.List;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

/**
 * A rede escolhida na aba Redes (mockup {@code rede_detalhe}): o cabeçalho com o slot da cor, o nome editável, as
 * máquinas e o estado; o seletor "Grupo"; o "Acesso" (Todos ou Convidados, com as fichas); "Nesta rede", uma linha por
 * tipo com quantas máquinas dão e recebem; e o rodapé com Pausar (o grupo da rede), Usar e "Apagar rede" de dois
 * cliques. Os textos são montados no {@link #set}; os cliques abrem as janelas ({@link NetworksWindows}) ou
 * mandam pelas {@link NetworkActions}.
 */
final class NetworkDetail {
    static final int X0 = TabletLayout.PANEL_X0;
    /** Onde começam os valores das linhas "Grupo" e "Acesso". */
    static final int VX = X0 + 58;
    private static final int TYPE_STEP = 20;

    private final TabletLayout layout;
    private final NetworksWindows windows;
    private final NetworkActions send;
    private final PanelHeader header = new PanelHeader();
    private final GuestChips chips = new GuestChips();
    private final ArmedClick removeArm = new ArmedClick();
    private final Component groupLabel = tr("nets.group");
    private final Component accessLabel = tr("nets.access");
    private final Component hereLabel = tr("nets.here");
    private final Component hereEmpty = tr("nets.here.empty");
    private final Component foreign = tr("network.foreign");
    private final Component noGroup = tr("networks.no_group");
    private final Component remove = tr("nets.remove_network");
    private final Component removeArmed = tr("nets.remove.armed");
    private final Component pauseText = tr("nets.pause");
    private final Component resumeText = tr("nets.resume");
    private final Component useText = tr("nets.use");
    private final Component inUseText = tr("nets.use.active");
    private final Component useTip = tr("use.tooltip");
    private final Component renameTip = tr("rename.tooltip");
    private final Component removeTip = tr("network.remove.tooltip");
    private Segments access;
    private int removeW;
    private int removeArmedW;
    private int pauseW;
    private int resumeW;
    private int useW;
    private int inUseW;
    private @Nullable FlatButton pause;
    private @Nullable FlatButton use;

    // montados no set
    private @Nullable NetworkView view;
    private @Nullable GroupView group;
    private ItemStack groupIcon = ItemStack.EMPTY;
    private GuiText.Fitted groupName;
    private boolean inUse;
    private int titleW;
    private Component pauseTip = Component.empty();
    private TypeCount[] types = new TypeCount[0];
    private Component[] typeNames = new Component[0];
    private Component[] typeLines = new Component[0];
    private int[] typeLineW = new int[0];

    NetworkDetail(TabletLayout layout, NetworksWindows windows, NetworkActions send) {
        this.layout = layout;
        this.windows = windows;
        this.send = send;
    }

    void addButtons(TabletScreen.Buttons buttons, Font font) {
        access = new Segments(font, tr("nets.access.all"), tr("nets.access.guests"));
        removeW = font.width(remove);
        removeArmedW = font.width(removeArmed);
        pauseW = font.width(pauseText) + 16;
        resumeW = font.width(resumeText) + 16;
        useW = font.width(useText) + 16;
        inUseW = font.width(inUseText) + 16;
        int y = layout.top + layout.footerY();
        pause = buttons.add(TabletScreen.Tab.NETWORKS, new FlatButton(layout.left + X0, y, pauseW, 16, pauseText,
                (g, b, hovered) -> PanelPaint.button(g, font, b.getX(), b.getY(), b.getWidth(), b.getHeight(),
                        b.getMessage(), false, hovered, b.active), send::pauseNetworkGroup).tooltip(() -> pauseTip));
        use = buttons.add(TabletScreen.Tab.NETWORKS, new FlatButton(layout.left + X0 + pauseW + 4, y, useW, 16, useText,
                (g, b, hovered) -> PanelPaint.button(g, font, b.getX(), b.getY(), b.getWidth(), b.getHeight(),
                        b.getMessage(), false, hovered, b.active), send::useNetwork)
                .tooltip(() -> useTip));
    }

    /** A rede, o grupo em que ela aparece (ou nenhum) com o ícone dele, e se é a rede ativa do jogador. */
    void set(Font font, NetworkView network, @Nullable GroupView in, ItemStack icon, boolean active) {
        if (view == null || !view.id().equals(network.id())) {
            removeArm.disarm();
        }
        view = network;
        group = in;
        groupIcon = icon;
        inUse = active;
        int x1 = layout.panelX1();
        Component state = tr(network.paused() ? "nets.state.paused" : "nets.state.active");
        int stateW = font.width(state);
        titleW = x1 - stateW - 7 - 4 - (X0 + 21);
        header.set(font, Component.literal(network.name()), subtitle(network), state,
                network.paused() ? GuiPaint.DISABLED : GuiPaint.OK, network.manageable(),
                titleW - (network.manageable() ? 11 : 0));
        groupName = in == null ? null : GuiText.fit(font, Component.literal(in.name()), 78);
        pauseTip = in == null ? tr("nets.pause.no_group")
                : tr(in.paused() ? "nets.resume.tooltip" : "nets.pause.tooltip", in.name());
        if (network.manageable() && !network.isPublic()) {
            chips.set(font, network.access(), VX, x1);
        }
        List<TypeCount> counts = network.types();
        types = counts.toArray(new TypeCount[0]);
        typeNames = new Component[types.length];
        typeLines = new Component[types.length];
        typeLineW = new int[types.length];
        for (int i = 0; i < types.length; i++) {
            typeNames[i] = ResourceStyle.name(types[i].type());
            typeLines[i] = tr("nets.here.line", types[i].givers(), types[i].receivers());
            typeLineW[i] = font.width(typeLines[i]);
        }
    }

    /** "N máquinas", com o dono quando a rede é de outro jogador e os roteadores descarregados, se houver. */
    private static Component subtitle(NetworkView network) {
        MutableComponent sub = plural("nets.machines", network.machines()).copy();
        if (!network.owned()) {
            sub.append(" · ").append(tr("nets.owner", network.owner()));
        }
        if (network.unloaded() > 0) {
            sub.append(" · ").append(plural("nets.unloaded", network.unloaded()));
        }
        return sub;
    }

    void clear() {
        view = null;
        removeArm.disarm();
    }

    /** Os botões do rodapé: à vista com uma rede escolhida, rótulo e largura pelo estado. */
    void refresh(boolean shown) {
        if (pause == null || use == null) {
            return;
        }
        boolean on = shown && view != null;
        pause.visible = on;
        use.visible = on;
        if (!on) {
            return;
        }
        boolean paused = group != null && group.paused();
        pause.setMessage(paused ? resumeText : pauseText);
        pause.setWidth(paused ? resumeW : pauseW);
        pause.active = group != null && group.manageable();
        use.setX(pause.getX() + pause.getWidth() + 4);
        use.setMessage(inUse ? inUseText : useText);
        use.setWidth(inUse ? inUseW : useW);
        use.active = !inUse;
    }

    // ------------------------------------------------------------------ desenho

    void render(GuiGraphics g, Font font, int mx, int my) {
        NetworkView v = view;
        if (v == null) {
            return;
        }
        int left = layout.left;
        int top = layout.top;
        int x1 = layout.panelX1();
        PanelPaint.slot(g, left + X0 - 1, top + 29, 18, false);
        g.fill(left + X0 + 1, top + 31, left + X0 + 15, top + 45, 0xFF000000 | v.color());
        header.render(g, font, left, top, X0, x1);
        GuiPaint.text(g, font, groupLabel, left + X0, top + 65, GuiPaint.MUTED);
        renderPicker(g, font, v.manageable() && overPicker(mx, my));
        int line = 110;
        if (v.manageable()) {
            GuiPaint.text(g, font, accessLabel, left + X0, top + 89, GuiPaint.MUTED);
            access.render(g, font, left + VX, top + 84, v.isPublic() ? 0 : 1, mx, my);
            if (!v.isPublic()) {
                chips.render(g, font, left, top, mx, my);
                line = 130;
            }
        } else {
            GuiText.draw(g, font, foreign, left + X0, top + 89, x1 - X0, GuiPaint.DISABLED);
        }
        PanelPaint.rule(g, left + X0, top + line, x1 - X0 + 1);
        GuiPaint.text(g, font, hereLabel, left + X0, top + line + 7, GuiPaint.MUTED);
        renderTypes(g, font, left, top, line, x1);
        if (v.manageable()) {
            boolean armed = removeArm.armed(System.currentTimeMillis());
            int w = armed ? removeArmedW : removeW;
            int color = armed ? GuiPaint.DANGER : overRemove(mx, my) ? GuiPaint.FG : GuiPaint.MUTED;
            GuiPaint.text(g, font, armed ? removeArmed : remove, left + x1 - w, top + layout.h - 16, color);
        }
    }

    private void renderTypes(GuiGraphics g, Font font, int left, int top, int line, int x1) {
        if (types.length == 0) {
            GuiPaint.text(g, font, hereEmpty, left + X0, top + line + 20, GuiPaint.MUTED);
            return;
        }
        int bottom = top + layout.footerRuleY();
        for (int i = 0; i < types.length; i++) {
            int y = top + line + 20 + i * TYPE_STEP;
            if (y + 16 > bottom) {
                break;
            }
            ResourceStyle.drawIcon(g, types[i].type(), left + X0 + 4, y + 4);
            GuiPaint.text(g, font, typeNames[i], left + X0 + 20, y + 4, GuiPaint.FG);
            GuiPaint.text(g, font, typeLines[i], left + x1 - typeLineW[i], y + 4, GuiPaint.MUTED);
        }
    }

    /** O seletor "Grupo"; também a âncora que a lista aberta redesenha por cima do véu. */
    void renderPicker(GuiGraphics g, Font font, boolean hovered) {
        NetworkView v = view;
        if (v == null) {
            return;
        }
        int x = layout.left + VX;
        int y = layout.top + 60;
        PanelPaint.reliefFace(g, x, y, GroupPicker.W, 18, hovered);
        if (group != null) {
            g.renderItem(groupIcon, x + 2, y + 1);
            GuiText.draw(g, font, groupName, x + 21, y + 5, v.manageable() ? GuiPaint.FG : GuiPaint.DISABLED);
        } else {
            GuiPaint.text(g, font, noGroup, x + 6, y + 5, GuiPaint.MUTED);
        }
        PanelPaint.caret(g, x + 102, y + 8, GuiPaint.MUTED);
    }

    // ------------------------------------------------------------------ entrada

    private boolean overPicker(double mx, double my) {
        int x = layout.left + VX;
        int y = layout.top + 60;
        return mx >= x && mx < x + GroupPicker.W && my >= y && my < y + 18;
    }

    private boolean overSlot(double mx, double my) {
        int x = layout.left + X0 - 1;
        int y = layout.top + 29;
        return mx >= x && mx < x + 18 && my >= y && my < y + 18;
    }

    private boolean overRemove(double mx, double my) {
        int w = removeArm.armed(System.currentTimeMillis()) ? removeArmedW : removeW;
        int right = layout.left + layout.panelX1();
        int y = layout.top + layout.h - 16;
        return mx >= right - w && mx < right && my >= y - 1 && my < y + 9;
    }

    /** Os cliques do painel da rede; devolve se tratou. Um clique fora do "Apagar rede" o desarma. */
    boolean mouseClicked(double mx, double my) {
        NetworkView v = view;
        if (v == null) {
            return false;
        }
        boolean onRemove = overRemove(mx, my);
        if (!onRemove) {
            removeArm.disarm();
        }
        if (!v.manageable()) {
            return false;
        }
        if (overSlot(mx, my)) {
            // o slot da cor: o clique fica reservado ao seletor de cor
            return true;
        }
        if (header.overTitle(mx, my, layout.left, layout.top, X0)) {
            // em edição, o clique é do campo (posiciona o cursor); a tela o repassa
            if (windows.titleEditing()) {
                return false;
            }
            windows.startTitle();
            return true;
        }
        if (overPicker(mx, my)) {
            windows.openGroupPicker();
            return true;
        }
        int segment = access.clicked(layout.left + VX, layout.top + 84, mx, my);
        if (segment >= 0) {
            send.setPublic(segment == 0);
            return true;
        }
        if (!v.isPublic()) {
            GuestChips.Chip chip = chips.removeAt(layout.left, layout.top, mx, my);
            if (chip != null) {
                send.removeAccess(chip.id(), chip.team());
                return true;
            }
            if (chips.openAt(layout.left, layout.top, mx, my)) {
                windows.openAccess();
                return true;
            }
        }
        if (onRemove) {
            if (removeArm.click(System.currentTimeMillis())) {
                send.removeNetwork();
            }
            return true;
        }
        return false;
    }

    @Nullable Component tooltip(int mx, int my) {
        NetworkView v = view;
        if (v == null || !v.manageable()) {
            return null;
        }
        if (header.overTitle(mx, my, layout.left, layout.top, X0)) {
            return renameTip;
        }
        if (overRemove(mx, my)) {
            return removeTip;
        }
        return null;
    }

    // ------------------------------------------------------------------ ganchos

    /** Onde o campo do nome abre: do título até o estado. */
    int titleWidth() {
        return titleW;
    }

    int[] titleCenter() {
        return new int[] {layout.left + X0 + 21 + 10, layout.top + 34};
    }

    int @Nullable [] groupPickerCenter() {
        return view == null ? null : new int[] {layout.left + VX + GroupPicker.W / 2, layout.top + 69};
    }

    int @Nullable [] accessCenter(boolean guests) {
        return view == null || !view.manageable() ? null
                : access.center(layout.left + VX, layout.top + 84, guests ? 1 : 0);
    }

    int @Nullable [] inviteCenter() {
        return view == null || view.isPublic() || !view.manageable() ? null
                : chips.inviteCenter(layout.left, layout.top);
    }

    int @Nullable [] chipRemoveCenter(int index) {
        return view == null || view.isPublic() ? null : chips.removeCenter(layout.left, layout.top, index);
    }

    int @Nullable [] pauseCenter() {
        return pause == null || !pause.visible ? null
                : new int[] {pause.getX() + pause.getWidth() / 2, pause.getY() + 8};
    }

    int @Nullable [] removeCenter() {
        if (view == null || !view.manageable()) {
            return null;
        }
        int w = removeArm.armed(System.currentTimeMillis()) ? removeArmedW : removeW;
        return new int[] {layout.left + layout.panelX1() - w / 2, layout.top + layout.h - 12};
    }

    int[] colorSlotCenter() {
        return new int[] {layout.left + X0 + 8, layout.top + 38};
    }
}
