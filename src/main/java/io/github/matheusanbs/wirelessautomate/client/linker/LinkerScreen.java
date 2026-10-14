package io.github.matheusanbs.wirelessautomate.client.linker;

import static io.github.matheusanbs.wirelessautomate.client.linker.LinkerLayout.BASE_ROWS;
import static io.github.matheusanbs.wirelessautomate.client.linker.LinkerLayout.BODY_Y;
import static io.github.matheusanbs.wirelessautomate.client.linker.LinkerLayout.EDGE;
import static io.github.matheusanbs.wirelessautomate.client.linker.LinkerLayout.FACES_ALL_W;
import static io.github.matheusanbs.wirelessautomate.client.linker.LinkerLayout.FACES_H;
import static io.github.matheusanbs.wirelessautomate.client.linker.LinkerLayout.FACE_STEP;
import static io.github.matheusanbs.wirelessautomate.client.linker.LinkerLayout.FACE_W;
import static io.github.matheusanbs.wirelessautomate.client.linker.LinkerLayout.GRIP;
import static io.github.matheusanbs.wirelessautomate.client.linker.LinkerLayout.HEAD_SHIFT;
import static io.github.matheusanbs.wirelessautomate.client.linker.LinkerLayout.HEAD_Y;
import static io.github.matheusanbs.wirelessautomate.client.linker.LinkerLayout.LIST_Y;
import static io.github.matheusanbs.wirelessautomate.client.linker.LinkerLayout.LW;
import static io.github.matheusanbs.wirelessautomate.client.linker.LinkerLayout.MAP_H;
import static io.github.matheusanbs.wirelessautomate.client.linker.LinkerLayout.MIN_H;
import static io.github.matheusanbs.wirelessautomate.client.linker.LinkerLayout.MIN_W;
import static io.github.matheusanbs.wirelessautomate.client.linker.LinkerLayout.ROW;
import static io.github.matheusanbs.wirelessautomate.client.linker.LinkerLayout.RX;
import static io.github.matheusanbs.wirelessautomate.client.linker.LinkerLayout.SEP_Y;
import static io.github.matheusanbs.wirelessautomate.client.linker.LinkerLayout.SIDES;
import static io.github.matheusanbs.wirelessautomate.client.linker.LinkerLayout.TABS;
import static io.github.matheusanbs.wirelessautomate.client.linker.LinkerLayout.TYPE_H;
import static io.github.matheusanbs.wirelessautomate.client.linker.LinkerLayout.WINDOW_MARGIN;
import static io.github.matheusanbs.wirelessautomate.client.linker.LinkerLayout.X0;
import static io.github.matheusanbs.wirelessautomate.client.linker.LinkerText.tr;

import io.github.matheusanbs.wirelessautomate.client.FlatButton;
import io.github.matheusanbs.wirelessautomate.client.GuiPaint;
import io.github.matheusanbs.wirelessautomate.client.GuiText;
import io.github.matheusanbs.wirelessautomate.client.GuiThemes;
import io.github.matheusanbs.wirelessautomate.client.ResizeGrip;
import io.github.matheusanbs.wirelessautomate.client.ResizeHandle;
import io.github.matheusanbs.wirelessautomate.client.ThemeToggle;
import io.github.matheusanbs.wirelessautomate.item.LinkerItem;
import io.github.matheusanbs.wirelessautomate.linker.LinkerMode;
import io.github.matheusanbs.wirelessautomate.linker.LinkerProblem;
import io.github.matheusanbs.wirelessautomate.menu.LinkerMenu;
import io.github.matheusanbs.wirelessautomate.menu.LinkerSnapshot;
import io.github.matheusanbs.wirelessautomate.network.ResourceType;
import io.github.matheusanbs.wirelessautomate.range.RemoteSide;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import org.lwjgl.glfw.GLFW;

/**
 * Tela do Vinculador (especificação, "Telas da interface › Vinculador"): à esquerda a rede ativa
 * (a linha "Nenhuma (desvincular)", a lista das redes do jogador e Nova rede, na {@link NetworkColumn}) e
 * as caixas das abas (uma caixa por tipo carregado: Itens, Fluidos, Energia e, com os mods, Químicos e
 * Source); no cabeçalho o modo (Único, Área ou Máquinas). Em Único, só a explicação do clique
 * ({@link SingleBody}); em Área, a prévia de cima da caixa marcada com os roteadores dentro dela, os
 * cantos e a contagem ({@link AreaMap}), e o botão Vincular (ou Desvincular).
 *
 * <p>Linha Faces ({@code [Todas] [F] [T] [E] [D] [C] [B]}, lados relativos à máquina presa): em Área,
 * logo abaixo do mapa (que fica {@link LinkerLayout#FACES_H} px mais baixo); em Único, no pé da coluna da
 * direita; some no modo Máquinas. "Todas" só marca as seis; as letras marcam e desmarcam uma a uma.
 *
 * <p>Tudo vem de {@link LinkerMenu#snapshot()}; os botões leem o estado a cada quadro, então um
 * estado novo aparece sem recriar widgets. A tela não muda nada sozinha: manda a ação
 * ({@link LinkerScreenActions}) e espera o servidor (na captura de desenvolvimento, {@code preview}, muda só o
 * estado local).
 */
public class LinkerScreen extends AbstractContainerScreen<LinkerMenu> {
    /** Tamanho lembrado na sessão (as telas do jogo são recriadas a cada abertura). */
    private static int savedW = MIN_W;
    private static int savedH = MIN_H;

    private final ResizeHandle resizeHandle = new ResizeHandle(GRIP, EDGE);
    private int w = MIN_W;
    private int h = MIN_H;
    private final List<FlatButton> buttons = new ArrayList<>();
    private final List<FlatButton> tabButtons = new ArrayList<>();
    private final List<FlatButton> faceButtons = new ArrayList<>();
    private final LinkerScreenActions actions;
    private final NetworkColumn column;
    private final SingleBody single = new SingleBody(this);
    private final AreaMap area = new AreaMap(this);

    private FlatButton linkButton;
    private FlatButton clearButton;
    private FlatButton allButton;
    private FlatButton allFacesButton;

    public LinkerScreen(LinkerMenu menu, Inventory inventory, Component title) {
        this(menu, inventory, title, false);
    }

    /** @param preview sem servidor (captura de desenvolvimento): as ações valem só no estado local */
    public LinkerScreen(LinkerMenu menu, Inventory inventory, Component title, boolean preview) {
        super(menu, inventory, title);
        this.actions = new LinkerScreenActions(menu, preview, this::refresh);
        this.column = new NetworkColumn(this, actions);
        this.imageWidth = MIN_W;
        this.imageHeight = MIN_H;
    }

    // tamanho: a largura extra vai para a coluna da direita, a altura para o mapa e a lista de redes

    public int themeX() {
        return leftPos + w - 4 - ThemeToggle.SIZE;
    }

    public int themeY() {
        return topPos + HEAD_Y + 1;
    }

    int left() {
        return leftPos;
    }

    int top() {
        return topPos;
    }

    private int x1() {
        return w - 9;
    }

    int rw() {
        return x1() - RX;
    }

    private int extraH() {
        return h - MIN_H;
    }

    /** O mapa: no modo Máquinas, a altura inteira; nos outros, {@link LinkerLayout#FACES_H} a menos para a linha Faces. */
    int mapH() {
        return MAP_H + extraH() - (LinkerText.machines(snapshot()) ? 0 : FACES_H);
    }

    /** Topo da linha Faces: abaixo do mapa em Área, no pé da coluna em Único. */
    int facesY() {
        return snapshot().mode() == LinkerMode.SINGLE ? actionY() + 2 : BODY_Y + mapH() + 2;
    }

    private int actionY() {
        return h - 9 - 16;
    }

    private int maxW() {
        return Math.max(MIN_W, width - WINDOW_MARGIN);
    }

    private int maxH() {
        return Math.max(MIN_H, height - WINDOW_MARGIN);
    }

    /** Botões de linha criados: o máximo de linhas que a maior altura comporta. */
    int maxRows() {
        return BASE_ROWS + (maxH() - MIN_H) / ROW;
    }

    LinkerSnapshot snapshot() {
        return menu.snapshot();
    }

    /** Largura de cada botão de modo: 44 px, ou o nome mais largo com folga (todos iguais). */
    private int modeW() {
        int width = 44;
        for (LinkerMode mode : LinkerMode.values()) {
            width = Math.max(width, font.width(LinkerText.modeName(mode)) + 8);
        }
        return width;
    }

    // layout da coluna da esquerda: a lista encolhe quando os tipos precisam de mais linhas

    /** Linhas de tipo (dois chips por linha) dos tipos disponíveis. */
    private int typeRows() {
        return (snapshot().available().types().size() + 1) / 2;
    }

    /** Linhas da lista de redes: 6 com duas linhas de tipo, 5 com três, mais uma por ROW de altura extra. */
    int listRows() {
        return Math.max(1, Math.min(BASE_ROWS, 8 - typeRows()) + extraH() / ROW);
    }

    int listH() {
        return listRows() * ROW + 4;
    }

    int newY() {
        return LIST_Y + listH() + 4;
    }

    private int typeLabelY() {
        return newY() + 20;
    }

    private int typeY() {
        return typeLabelY() + 11;
    }

    // ------------------------------------------------------------------ montagem

    @Override
    protected void init() {
        w = Math.max(MIN_W, Math.min(savedW, maxW()));
        h = Math.max(MIN_H, Math.min(savedH, maxH()));
        imageWidth = w;
        imageHeight = h;
        super.init();
        buttons.clear();
        tabButtons.clear();
        faceButtons.clear();
        int x = leftPos;
        int y = topPos;

        int modeW = modeW();
        LinkerMode[] modes = LinkerMode.values();
        for (int i = 0; i < modes.length; i++) {
            LinkerMode mode = modes[i];
            add(new FlatButton(x + x1() - HEAD_SHIFT - (modes.length - i) * (modeW + 2) + 2, y + HEAD_Y - 1, modeW, 15,
                    LinkerText.modeName(mode),
                    (g, b, hovered) -> LinkerPaint.paintChoice(g, b, hovered, LinkerText.modeName(mode),
                            snapshot().mode() == mode),
                    () -> actions.setMode(mode)).tooltip(() -> tr("mode." + mode.getSerializedName() + ".tooltip")));
        }

        column.addWidgets(x, y);

        // a posição dos chips e do Todos vem de refresh(), pela ordem entre os tipos disponíveis
        int typeW = (LW - 2) / 2;
        for (int i = 0; i < TABS.length; i++) {
            ResourceType t = TABS[i];
            tabButtons.add(add(new FlatButton(x + X0, y + typeY(), typeW, TYPE_H, LinkerText.typeName(t),
                    (g, b, hovered) -> LinkerPaint.paintCheck(g, b, hovered, snapshot(), t), () -> actions.toggleTab(t))
                    .tooltip(() -> LinkerText.tabTooltip(snapshot(), t))));
        }
        allButton = add(new FlatButton(x + X0 + LW - 50, y + typeLabelY() - 3, 50, 12, tr("type.all"),
                (g, b, hovered) -> LinkerPaint.paintAll(g, b, hovered, snapshot()), actions::setAllTabs)
                .tooltip(() -> LinkerText.allTooltip(snapshot())));

        allFacesButton = add(new FlatButton(x + RX, y + facesY(), FACES_ALL_W, 12, tr("faces.all"),
                (g, b, hovered) -> LinkerPaint.paintAllFaces(g, b, hovered, snapshot()), actions::setAllFaces)
                .tooltip(() -> LinkerText.allFacesTooltip(snapshot())));
        for (int i = 0; i < SIDES.length; i++) {
            RemoteSide side = SIDES[i];
            faceButtons.add(add(new FlatButton(x + RX + FACES_ALL_W + 3 + i * FACE_STEP, y + facesY(), FACE_W, 12,
                    LinkerItem.sideLetter(side), (g, b, hovered) -> LinkerPaint.paintFace(g, b, hovered, snapshot(), side),
                    () -> actions.toggleFace(side)).tooltip(() -> LinkerText.faceTooltip(snapshot(), side))));
        }

        linkButton = add(new FlatButton(x + RX, y + actionY(), rw() - 48, 16, tr("link.count", 0),
                (g, b, hovered) -> LinkerPaint.paintLink(g, b, hovered, snapshot()), actions::link)
                .tooltip(() -> LinkerText.linkTooltip(snapshot())));
        clearButton = add(new FlatButton(x + x1() - 46, y + actionY(), 46, 16, tr("clear"),
                (g, b, hovered) -> LinkerPaint.paintText(g, b, hovered, tr("clear"), b.active ? GuiPaint.FG : GuiPaint.DISABLED),
                actions::clear).tooltip(() -> tr("clear.tooltip")));
        refresh();
    }

    FlatButton add(FlatButton button) {
        buttons.add(button);
        return addRenderableWidget(button);
    }

    void addBox(EditBox box) {
        addRenderableWidget(box);
    }

    /** Visibilidade e mensagens que dependem do estado. */
    void refresh() {
        LinkerSnapshot s = snapshot();
        column.refresh();
        int typeW = (LW - 2) / 2;
        int shown = 0;
        for (int i = 0; i < TABS.length; i++) {
            FlatButton chip = tabButtons.get(i);
            chip.visible = s.available().contains(TABS[i]);
            if (chip.visible) {
                chip.setX(leftPos + X0 + (shown % 2) * (typeW + 2));
                chip.setY(topPos + typeY() + (shown / 2) * (TYPE_H + 2));
                shown++;
            }
        }
        allButton.setY(topPos + typeLabelY() - 3);
        column.refreshCreate(topPos);
        boolean area = s.mode() != LinkerMode.SINGLE;
        linkButton.visible = clearButton.visible = area;
        // a linha Faces some no modo Máquinas; em Único fica no pé da coluna, em Área abaixo do mapa
        boolean showFaces = !LinkerText.machines(s);
        allFacesButton.visible = showFaces;
        allFacesButton.setY(topPos + facesY());
        for (FlatButton face : faceButtons) {
            face.visible = showFaces;
            face.setY(topPos + facesY());
        }
        // em Máquinas a contagem é de roteadores; o servidor decide quais máquinas entram
        linkButton.active = s.problem() == LinkerProblem.NONE && (LinkerText.machines(s) || LinkerText.toLink(s) > 0);
        linkButton.setMessage(LinkerText.linkLabel(s));
        clearButton.active = s.first().isPresent() || s.otherDimension();
    }

    // ------------------------------------------------------------------ entrada

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (ThemeToggle.mouseClicked(themeX(), themeY(), mouseX, mouseY, button)) {
            return true;
        }
        if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT && resizeHandle.begin(mouseX, mouseY, leftPos, topPos, w, h)) {
            return true;
        }
        column.clickOutside(mouseX, mouseY);
        boolean wasCreating = column.creating();
        boolean handled = super.mouseClicked(mouseX, mouseY, button);
        if (column.creating() && !wasCreating) {
            // a tela dá o foco ao botão clicado depois do onPress, tirando-o do campo do nome
            setFocused(column.nameBox());
        }
        return handled;
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (resizeHandle.dragging()) {
            dragResize(mouseX, mouseY);
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (resizeHandle.dragging()) {
            resizeHandle.end();
            return true;
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }

    /**
     * Redimensiona em torno do centro, como o roteador e o Tablet: a borda arrastada segue o mouse e
     * a oposta se move igual. Largura e altura sempre pares.
     */
    private void dragResize(double mouseX, double mouseY) {
        int newW = w;
        int newH = h;
        if (resizeHandle.changesWidth()) {
            newW = Math.max(MIN_W, Math.min(maxW(), (int) Math.round(resizeHandle.wantedWidth(mouseX) / 2) * 2));
        }
        if (resizeHandle.changesHeight()) {
            newH = Math.max(MIN_H, Math.min(maxH(), (int) Math.round(resizeHandle.wantedHeight(mouseY) / 2) * 2));
        }
        if (newW != w || newH != h) {
            savedW = newW;
            savedH = newH;
            rebuild();
        }
    }

    /**
     * Refaz os widgets no tamanho lembrado. A rolagem e o modo ficam em campos; o campo de nome de
     * rede nova é fechado ({@link NetworkColumn#close()}).
     */
    private void rebuild() {
        column.close();
        setFocused(null);
        rebuildWidgets();
    }

    @Override
    public void resize(net.minecraft.client.Minecraft minecraft, int width, int height) {
        resizeHandle.end();
        super.resize(minecraft, width, height);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (column.mouseScrolled(mouseX, mouseY, scrollY)) {
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (column.keyPressed(keyCode, scanCode, modifiers)) {
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean charTyped(char codePoint, int modifiers) {
        if (column.creating()) {
            boolean typed = column.nameBox().charTyped(codePoint, modifiers);
            refresh();
            return typed;
        }
        return super.charTyped(codePoint, modifiers);
    }

    // ------------------------------------------------------------------ desenho

    /** Versão do tema com que os campos de texto foram pintados ({@link GuiThemes#version()}). */
    private int inkVersion = -1;

    /** Os campos foram recriados: a cor deles volta a seguir o tema no próximo quadro. */
    void resetInk() {
        inkVersion = -1;
    }

    /** Cor do texto e da dica do campo: o EditBox guarda a cor, então segue o tema por aqui. */
    private void inkBoxes() {
        inkVersion = GuiThemes.version();
        GuiPaint.ink(column.nameBox(), tr("network.name"));
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        if (inkVersion != GuiThemes.version()) {
            inkBoxes();
        }
        refresh();
        GuiText.beginFrame();
        super.render(g, mouseX, mouseY, partialTick);
        if (ThemeToggle.hovered(themeX(), themeY(), mouseX, mouseY)) {
            g.renderComponentTooltip(font, ThemeToggle.tooltip(), mouseX, mouseY);
            return;
        }
        for (FlatButton button : buttons) {
            if (button.visible && button.isHovered()) {
                Component tooltip = button.currentTooltip();
                if (tooltip != null) {
                    setTooltipForNextRenderPass(tooltip);
                    return;
                }
                break;
            }
        }
        Component more = snapshot().mode() == LinkerMode.SINGLE ? single.tooltip(mouseX, mouseY) : null;
        if (more != null) {
            setTooltipForNextRenderPass(more);
            return;
        }
        if (resizeHandle.hover(mouseX, mouseY, leftPos, topPos, w, h) != null) {
            setTooltipForNextRenderPass(Component.translatable("gui.wirelessautomate.resize.tooltip"));
            return;
        }
        // texto abreviado: a dica traz o texto inteiro, se nenhuma outra dica está no ar
        Component clipped = GuiText.clipAt(mouseX, mouseY);
        if (clipped != null) {
            setTooltipForNextRenderPass(clipped);
        }
    }

    @Override
    protected void renderLabels(GuiGraphics g, int mouseX, int mouseY) {
        // os textos são desenhados em renderBg, em coordenadas da tela
    }

    @Override
    protected void renderBg(GuiGraphics g, float partialTick, int mouseX, int mouseY) {
        LinkerSnapshot s = snapshot();
        int x = leftPos;
        int y = topPos;
        GuiPaint.panel(g, x, y, w, h, GuiPaint.ACCENT);

        int titleW = font.width(title);
        GuiText.draw(g, font, title, x + X0, y + HEAD_Y + 3, titleW, GuiPaint.FG);
        Component tabsName = LinkerText.tabsName(s);
        Component typeText = s.unlink() ? tr("head.unlink", tabsName) : tr("head.type", tabsName);
        int headX = X0 + titleW + 8;
        // até os botões de modo, à direita
        int headW = x1() - HEAD_SHIFT - LinkerMode.values().length * (modeW() + 2) - 4 - headX;
        GuiText.draw(g, font, typeText, x + headX, y + HEAD_Y + 3, headW, s.unlink() ? GuiPaint.DANGER : GuiPaint.MUTED);
        ThemeToggle.render(g, themeX(), themeY(), mouseX, mouseY);
        g.fill(x + X0, y + SEP_Y, x + x1(), y + SEP_Y + 1, GuiPaint.LINE);

        // rede ativa
        column.render(g, x, y);

        GuiPaint.text(g, font, tr("type.label"), x + X0, y + typeLabelY(), GuiPaint.MUTED);

        // corpo do modo
        g.fill(x + RX - 5, y + BODY_Y, x + RX - 4, y + h - 9, GuiPaint.LINE);
        if (s.mode() == LinkerMode.SINGLE) {
            single.render(g, x + RX, y + BODY_Y);
        } else {
            area.render(g, s, x + RX, y + BODY_Y);
        }
        ResizeGrip.renderDotted(g, x, y, w, h, resizeHandle.hover(mouseX, mouseY, x, y, w, h), GuiPaint.ACCENT);
    }

    // ------------------------------------------------------------------ captura de desenvolvimento

    public void previewCreate(String name) {
        column.startCreate();
        column.nameBox().setValue(name);
        refresh();
    }

    public void previewCancelCreate() {
        column.finishCreate(false);
        column.forgetDraft();
    }

    /** e2e: o campo do nome da rede nova. */
    public EditBox nameBox() {
        return column.nameBox();
    }

    /** e2e: o campo do nome está aberto. */
    public boolean creating() {
        return column.creating();
    }

    /** e2e: muda o tamanho como se a borda tivesse sido arrastada. */
    public void previewResize(int width, int height) {
        savedW = width;
        savedH = height;
        rebuild();
    }

    /** Tamanho atual do painel, {@code {largura, altura}} (gancho do e2e). */
    public int[] size() {
        return new int[] {w, h};
    }

    public int[] previewLinkCenter() {
        return new int[] {linkButton.getX() + linkButton.getWidth() / 2, linkButton.getY() + 8};
    }
}
