package io.github.matheusanbs.wirelessautomate.client.linker;

import static io.github.matheusanbs.wirelessautomate.client.linker.LinkerLayout.BODY_Y;
import static io.github.matheusanbs.wirelessautomate.client.linker.LinkerLayout.EDGE;
import static io.github.matheusanbs.wirelessautomate.client.linker.LinkerLayout.GRIP;
import static io.github.matheusanbs.wirelessautomate.client.linker.LinkerLayout.HEAD_SHIFT;
import static io.github.matheusanbs.wirelessautomate.client.linker.LinkerLayout.HEAD_Y;
import static io.github.matheusanbs.wirelessautomate.client.linker.LinkerLayout.MAP_H;
import static io.github.matheusanbs.wirelessautomate.client.linker.LinkerLayout.MIN_H;
import static io.github.matheusanbs.wirelessautomate.client.linker.LinkerLayout.MIN_W;
import static io.github.matheusanbs.wirelessautomate.client.linker.LinkerLayout.RX;
import static io.github.matheusanbs.wirelessautomate.client.linker.LinkerLayout.SEP_Y;
import static io.github.matheusanbs.wirelessautomate.client.linker.LinkerLayout.WINDOW_MARGIN;
import static io.github.matheusanbs.wirelessautomate.client.linker.LinkerLayout.X0;
import static io.github.matheusanbs.wirelessautomate.client.linker.LinkerText.tr;

import io.github.matheusanbs.wirelessautomate.client.FlatButton;
import io.github.matheusanbs.wirelessautomate.client.GuiPaint;
import io.github.matheusanbs.wirelessautomate.client.GuiText;
import io.github.matheusanbs.wirelessautomate.client.ResizeGrip;
import io.github.matheusanbs.wirelessautomate.client.ResizeHandle;
import io.github.matheusanbs.wirelessautomate.client.ThemeToggle;
import io.github.matheusanbs.wirelessautomate.menu.LinkerMenu;
import io.github.matheusanbs.wirelessautomate.menu.LinkerSnapshot;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import org.lwjgl.glfw.GLFW;

/**
 * Tela do Vinculador (modo Vincular): à esquerda o roteador escolhido e a ajuda dos gestos ({@link RouterPanel});
 * à direita a prévia de cima da área marcada com as máquinas vinculadas dentro dela, os cantos e a contagem
 * ({@link AreaMap}), e o botão Vincular (ou Desvincular, pelo seletor do cabeçalho) e o Limpar. O modo
 * Abrir não tem tela: só a lista curta ({@link LinkerOpenScreen}).
 *
 * <p>Tudo vem de {@link LinkerMenu#snapshot()}; os botões leem o estado a cada quadro, então um estado novo
 * aparece sem recriar widgets. A tela não muda nada sozinha: manda a ação ({@link LinkerScreenActions}) e
 * espera o servidor (na captura de desenvolvimento, {@code preview}, muda só o estado local).
 */
public class LinkerScreen extends AbstractContainerScreen<LinkerMenu> {
    /** Tamanho lembrado na sessão (as telas do jogo são recriadas a cada abertura). */
    private static int savedW = MIN_W;
    private static int savedH = MIN_H;

    private final ResizeHandle resizeHandle = new ResizeHandle(GRIP, EDGE);
    private int w = MIN_W;
    private int h = MIN_H;
    private final List<FlatButton> buttons = new ArrayList<>();
    private final LinkerScreenActions actions;
    private final RouterPanel routerPanel = new RouterPanel();
    private final AreaMap area = new AreaMap(this);

    private FlatButton linkButton;
    private FlatButton clearButton;

    public LinkerScreen(LinkerMenu menu, Inventory inventory, Component title) {
        this(menu, inventory, title, false);
    }

    /** @param preview sem servidor (captura de desenvolvimento): as ações valem só no estado local */
    public LinkerScreen(LinkerMenu menu, Inventory inventory, Component title, boolean preview) {
        super(menu, inventory, title);
        this.actions = new LinkerScreenActions(menu, preview, this::refresh);
        this.imageWidth = MIN_W;
        this.imageHeight = MIN_H;
    }

    // tamanho: a largura extra vai para a coluna da direita, a altura para o mapa

    public int themeX() {
        return leftPos + w - 4 - ThemeToggle.SIZE;
    }

    public int themeY() {
        return topPos + HEAD_Y + 1;
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

    /** O mapa: a altura do corpo. */
    int mapH() {
        return MAP_H + extraH();
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

    LinkerSnapshot snapshot() {
        return menu.snapshot();
    }

    /** Largura de cada botão do seletor Vincular / Desvincular: 56 px, ou o nome mais largo com folga. */
    private int choiceW() {
        return Math.max(56, Math.max(font.width(tr("action.link")), font.width(tr("action.unlink"))) + 10);
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
        int x = leftPos;
        int y = topPos;

        int choiceW = choiceW();
        int right = x + x1() - HEAD_SHIFT;
        add(new FlatButton(right - 2 * choiceW - 2 + 2, y + HEAD_Y - 1, choiceW, 15, tr("action.link"),
                (g, b, hovered) -> LinkerPaint.paintChoice(g, b, hovered, tr("action.link"), !snapshot().unlink()),
                () -> actions.setUnlink(false)).tooltip(() -> tr("action.link.tooltip")));
        add(new FlatButton(right - choiceW + 2, y + HEAD_Y - 1, choiceW, 15, tr("action.unlink"),
                (g, b, hovered) -> LinkerPaint.paintChoice(g, b, hovered, tr("action.unlink"), snapshot().unlink()),
                () -> actions.setUnlink(true)).tooltip(() -> tr("action.unlink.tooltip")));

        linkButton = add(new FlatButton(x + RX, y + actionY(), rw() - 48, 16, tr("link.label"),
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

    /** Estado dos botões que depende do snapshot. */
    void refresh() {
        LinkerSnapshot s = snapshot();
        linkButton.active = LinkerText.canLink(s);
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
        return super.mouseClicked(mouseX, mouseY, button);
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
            rebuildWidgets();
        }
    }

    @Override
    public void resize(net.minecraft.client.Minecraft minecraft, int width, int height) {
        resizeHandle.end();
        super.resize(minecraft, width, height);
    }

    // ------------------------------------------------------------------ desenho

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
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
        ThemeToggle.render(g, themeX(), themeY(), mouseX, mouseY);
        g.fill(x + X0, y + SEP_Y, x + x1(), y + SEP_Y + 1, GuiPaint.LINE);

        routerPanel.render(g, s, x, y);
        g.fill(x + RX - 5, y + BODY_Y, x + RX - 4, y + h - 9, GuiPaint.LINE);
        area.render(g, s, x + RX, y + BODY_Y);
        ResizeGrip.renderDotted(g, x, y, w, h, resizeHandle.hover(mouseX, mouseY, x, y, w, h), GuiPaint.ACCENT);
    }

    // ------------------------------------------------------------------ captura de desenvolvimento

    /** e2e: muda o tamanho como se a borda tivesse sido arrastada. */
    public void previewResize(int width, int height) {
        savedW = width;
        savedH = height;
        rebuildWidgets();
    }

    /** Tamanho atual do painel, {@code {largura, altura}} (gancho do e2e). */
    public int[] size() {
        return new int[] {w, h};
    }

    public int[] previewLinkCenter() {
        return new int[] {linkButton.getX() + linkButton.getWidth() / 2, linkButton.getY() + 8};
    }
}
