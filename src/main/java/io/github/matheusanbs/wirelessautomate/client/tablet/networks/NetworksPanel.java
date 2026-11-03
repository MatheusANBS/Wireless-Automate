package io.github.matheusanbs.wirelessautomate.client.tablet.networks;

import static io.github.matheusanbs.wirelessautomate.client.tablet.networks.NetworksText.tr;

import io.github.matheusanbs.wirelessautomate.client.GuiPaint;
import io.github.matheusanbs.wirelessautomate.client.GuiText;
import io.github.matheusanbs.wirelessautomate.client.GuiThemes;
import io.github.matheusanbs.wirelessautomate.client.access.AccessPanel;
import io.github.matheusanbs.wirelessautomate.client.tablet.TabletActions;
import io.github.matheusanbs.wirelessautomate.client.tablet.TabletLayout;
import io.github.matheusanbs.wirelessautomate.client.tablet.TabletScreen;
import io.github.matheusanbs.wirelessautomate.client.tablet.kit.ModalLayer;
import io.github.matheusanbs.wirelessautomate.client.tablet.kit.TabletModal;
import io.github.matheusanbs.wirelessautomate.client.tablet.kit.TitleEditor;
import io.github.matheusanbs.wirelessautomate.menu.TabletMenu;
import io.github.matheusanbs.wirelessautomate.menu.TabletSnapshot;
import io.github.matheusanbs.wirelessautomate.menu.TabletSnapshot.GroupView;
import io.github.matheusanbs.wirelessautomate.menu.TabletSnapshot.NetworkView;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.glfw.GLFW;

/**
 * A aba Redes do Tablet (mockups {@code redes} e {@code grupo} de {@code docs/preview/tablet-do-zero}): à esquerda a
 * árvore de grupos e redes ({@link NetworkColumn}); à direita a rede escolhida ({@link NetworkDetail}) ou o grupo
 * ({@link GroupPanel}); as janelas sobre o véu e o nome editável ({@link NetworksWindows}). É a anfitriã: guarda o
 * escolhido, monta tudo uma vez por snapshot ({@link NetworksModel}), repassa o mouse e o teclado e manda as ações pelas
 * {@link NetworkActions}; os ganchos do e2e ficam no {@link NetworksProbe}. "+ Rede" e "+ Grupo" criam com
 * um nome livre, escolhem o novo quando ele chega e abrem o nome para edição.
 */
public final class NetworksPanel {
    private final TabletLayout layout;
    private final Supplier<TabletSnapshot> snapshot;
    private final ModalLayer modals = new ModalLayer();
    private final NetworksModel model = new NetworksModel();
    private final NetworkActions send;
    private final TitleEditor title;
    private final AccessPanel access;
    private final NetworkColumn column;
    private final NetworkDetail detail;
    private final GroupPanel groupPanel;
    private final NetworksWindows windows;
    private final Component empty = tr("nets.empty");
    private @Nullable UUID network;
    private @Nullable UUID group;
    private int builtTheme = -1;
    /** A aba estava à vista no último {@link #refresh}: ao sair dela, o acesso larga o que foi digitado. */
    private boolean shownBefore;
    /** Quantos snapshots montaram a aba: o e2e confere que uma amostra ao vivo não conta. */
    private int snapshotBuilds;
    private final NetworksProbe probe;

    public NetworksPanel(TabletLayout layout, TabletMenu menu, Supplier<TabletSnapshot> snapshot, TabletActions actions) {
        this.layout = layout;
        this.snapshot = snapshot;
        this.send = new NetworkActions(actions, menu, snapshot, model, () -> network, () -> group);
        this.title = new TitleEditor(send::rename);
        this.access = new AccessPanel(AccessWindow.frame(layout), send.accessHost());
        this.column = new NetworkColumn(layout);
        this.windows = new NetworksWindows(layout, modals, model, send, title, access, () -> network, () -> group);
        this.detail = new NetworkDetail(layout, windows, send);
        this.groupPanel = new GroupPanel(layout, this, windows, send);
        windows.attach(detail, groupPanel);
        this.probe = new NetworksProbe(this, modals, column, detail, groupPanel);
    }

    private static Font font() {
        return Minecraft.getInstance().font;
    }

    public AccessPanel access() {
        return access;
    }

    private @Nullable NetworkView selectedView() {
        return network == null ? null : model.network(network);
    }

    // ------------------------------------------------------------------ montagem

    public void addButtons(TabletScreen.Buttons buttons, Font font) {
        modals.close();
        modals.bounds(layout.left + 2, layout.top + 24, layout.w - 4, layout.h - 26);
        column.addButtons(buttons, font, send::newNetwork, send::newGroup);
        detail.addButtons(buttons, font);
        groupPanel.addButtons(buttons, font);
        rebuild(font);
    }

    /** O campo do nome e os dois do acesso, refeitos a cada {@code init}; a tela os põe entre os widgets. */
    public List<EditBox> createBoxes(Font font) {
        List<EditBox> boxes = new ArrayList<>();
        boxes.add(title.createBox(font));
        List<EditBox> accessBoxes = access.createBoxes(font);
        windows.accessBoxes(accessBoxes);
        boxes.addAll(accessBoxes);
        return boxes;
    }

    /**
     * Snapshot novo: a árvore, os textos e os ícones são refeitos; o escolhido que sumiu sai; o que acabou de ser
     * criado fica escolhido com o nome aberto; sem nada escolhido, vale a primeira rede. A janela aberta sobre o
     * escolhido que sumiu fecha sem gravar: a cor de uma rede apagada não vai ao servidor.
     */
    public void onSnapshot(TabletSnapshot s, Font font) {
        snapshotBuilds++;
        model.build(s);
        boolean createdGroup = send.awaitingGroup();
        UUID created = send.takeCreated();
        if (created != null) {
            pick(createdGroup ? null : created, createdGroup ? created : null);
        }
        if (network != null && model.network(network) == null || group != null && model.group(group) == null) {
            modals.drop();
            pick(null, null);
        }
        if (network == null && group == null) {
            network = model.firstNetwork();
        }
        rebuild(font);
        if (created != null) {
            windows.startTitle();
        }
    }

    private void rebuild(Font font) {
        builtTheme = GuiThemes.version();
        if (model.snapshot() == null) {
            model.build(snapshot.get());
        }
        column.rebuild(font, model.rows(), model.networks(), model.groups(), model.icons());
        NetworkView view = selectedView();
        if (view != null) {
            GroupView in = model.groupOf(view.id());
            detail.set(font, view, in, in == null ? ItemStack.EMPTY : model.icon(in.id()),
                    model.snapshot().activeNetwork().filter(view.id()::equals).isPresent());
        } else {
            detail.clear();
        }
        GroupView chosen = network == null && group != null ? model.group(group) : null;
        if (chosen != null) {
            groupPanel.set(font, chosen, model.icon(chosen.id()), model.networks());
        } else {
            groupPanel.clear();
        }
    }

    /** Visibilidade dos botões e dos campos ({@code shown}: a aba Redes está aberta). */
    public void refresh(boolean shown) {
        if (shown && builtTheme != GuiThemes.version()) {
            rebuild(font());
        }
        if (!shown && shownBefore) {
            title.finish(false);
            modals.close();
            access.reset();
        }
        shownBefore = shown;
        detail.refresh(shown && network != null);
        groupPanel.refresh(shown && network == null && group != null);
        access.refresh(shown && modals.current() instanceof AccessWindow);
    }

    // ------------------------------------------------------------------ escolha e janelas

    private void pick(@Nullable UUID networkId, @Nullable UUID groupId) {
        title.finish(false);
        modals.close();
        access.reset();
        network = networkId;
        group = networkId == null ? groupId : null;
    }

    void selectNetwork(UUID id) {
        pick(id, null);
        rebuild(font());
    }

    void selectGroup(UUID id) {
        pick(null, id);
        rebuild(font());
    }

    /** Fecha a janela aberta, que grava o que guardava (a cor); a tela chama antes de fechar o menu. */
    public void closeWindows() {
        modals.close();
    }

    /** A área da grade de ícones aberta ({@code x, y, w, h}), onde o JEI solta um item, ou {@code null}. */
    public int @Nullable [] ghostBounds() {
        return windows.ghostBounds();
    }

    public void acceptGhost(Object ingredient) {
        if (ingredient instanceof ItemStack stack) {
            windows.acceptGhost(stack);
        }
    }

    // ------------------------------------------------------------------ entrada

    public boolean mouseClicked(double mx, double my, int button) {
        if (modals.mouseClicked(mx, my, button)) {
            return true;
        }
        EditBox box = title.box();
        if (title.editing() && box != null) {
            if (box.isMouseOver(mx, my)) {
                // o clique é do campo do nome: a tela o repassa e ele põe o cursor
                return false;
            }
            title.finish(true);
        }
        if (button != GLFW.GLFW_MOUSE_BUTTON_LEFT) {
            return false;
        }
        NetworkTree.Row row = column.rowAt(mx, my);
        if (row != null && row.id() != null) {
            if (row.kind() == NetworkTree.Kind.GROUP) {
                selectGroup(row.id());
            } else {
                selectNetwork(row.id());
            }
            return true;
        }
        if (network != null) {
            return detail.mouseClicked(mx, my);
        }
        return group != null && groupPanel.mouseClicked(mx, my);
    }

    public void mouseScrolled(double mx, double my, double delta) {
        if (modals.mouseScrolled(mx, my, delta) || column.mouseScrolled(mx, my, delta)) {
            return;
        }
        if (network == null && group != null) {
            groupPanel.mouseScrolled(delta);
        }
    }

    public boolean mouseDragged(double mx, double my) {
        return modals.mouseDragged(mx, my);
    }

    public boolean mouseReleased(double mx, double my) {
        return modals.mouseReleased(mx, my);
    }

    /**
     * As teclas da aba, antes de tudo: com uma janela aberta, são dela; com o nome em edição, Enter grava e Esc
     * desiste; um campo do acesso com a lista de sugestões trata as setas e o Enter.
     */
    public boolean keyPressed(int keyCode, int scanCode, int modifiers, @Nullable EditBox focused) {
        if (modals.keyPressed(keyCode, scanCode, modifiers)) {
            return true;
        }
        EditBox box = title.box();
        if (title.editing() && box != null) {
            if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) {
                title.finish(true);
            } else if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
                title.finish(false);
            } else {
                box.keyPressed(keyCode, scanCode, modifiers);
            }
            return true;
        }
        return access.keyPressed(focused, keyCode);
    }

    public boolean charTyped(char c, int modifiers) {
        if (modals.charTyped(c, modifiers)) {
            return true;
        }
        EditBox box = title.box();
        return title.editing() && box != null && box.charTyped(c, modifiers);
    }

    public @Nullable Component tooltip(int mx, int my) {
        TabletModal open = modals.current();
        if (open != null) {
            return open.tooltip(mx, my);
        }
        if (network != null) {
            return detail.tooltip(mx, my);
        }
        return group != null ? groupPanel.tooltip(mx, my) : null;
    }

    // ------------------------------------------------------------------ desenho

    public void render(GuiGraphics g, Font font, int mx, int my) {
        column.render(g, font, mx, my, network, group, windows.liveColor());
        g.fill(layout.left + NetworkColumn.RULE + 1, layout.top + layout.footerRuleY(), layout.left + layout.w - 2,
                layout.top + layout.footerRuleY() + 1, GuiPaint.INSET);
        if (network != null) {
            detail.render(g, font, mx, my);
        } else if (group != null) {
            groupPanel.render(g, font, mx, my);
        } else {
            GuiText.draw(g, font, empty, layout.left + NetworkDetail.X0, layout.top + 30,
                    layout.panelX1() - NetworkDetail.X0, GuiPaint.MUTED);
        }
        title.renderFrame(g);
    }

    /** As janelas sobre o véu; a tela chama depois de desenhar os widgets e o aviso do topo. */
    public void renderOverlay(GuiGraphics g, Font font, int mx, int my) {
        modals.render(g, font, mx, my);
    }

    // ------------------------------------------------------------------ ganchos do e2e e das capturas

    /** Os ganchos de clique do e2e (centros na tela) e o estado que eles conferem. */
    public NetworksProbe probe() {
        return probe;
    }

    public void preview(int index) {
        selectNetwork(snapshot.get().networks().get(index).id());
    }

    public void previewGroup(int index) {
        selectGroup(snapshot.get().groups().get(index).id());
    }

    /** Abre a janela de acesso da rede escolhida (vitrine). */
    public void previewAccess() {
        windows.openAccess();
    }

    public boolean modalOpen() {
        return modals.isOpen();
    }

    int snapshotBuilds() {
        return snapshotBuilds;
    }

    @Nullable UUID network() {
        return network;
    }

    @Nullable UUID group() {
        return group;
    }

    public @Nullable EditBox titleBox() {
        return title.box();
    }
}
