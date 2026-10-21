package io.github.matheusanbs.wirelessautomate.client.router;

import io.github.matheusanbs.wirelessautomate.client.FlatButton;
import io.github.matheusanbs.wirelessautomate.client.GuiPaint;
import io.github.matheusanbs.wirelessautomate.client.GuiText;
import io.github.matheusanbs.wirelessautomate.client.ResizeGrip;
import io.github.matheusanbs.wirelessautomate.client.ResizeHandle;
import io.github.matheusanbs.wirelessautomate.client.TabLayout;
import io.github.matheusanbs.wirelessautomate.client.ThemeToggle;
import io.github.matheusanbs.wirelessautomate.menu.RangeView;
import io.github.matheusanbs.wirelessautomate.menu.RouterMenu;
import io.github.matheusanbs.wirelessautomate.menu.RouterSnapshot;
import io.github.matheusanbs.wirelessautomate.network.PortMode;
import io.github.matheusanbs.wirelessautomate.network.ResourceType;
import io.github.matheusanbs.wirelessautomate.packet.PanelNotices;
import io.github.matheusanbs.wirelessautomate.packet.SelectFacePayload;
import io.github.matheusanbs.wirelessautomate.range.EditTarget;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.Nullable;

/**
 * Tela do roteador (especificação, "Painel do nó › Tela do roteador"): cabeçalho com nome, vazão da aba,
 * tier, os slots dos upgrades e o botão do tema; o visor 3D da máquina com o roteador
 * ({@link MachineView3D}); os slots de Cartão de Filtro da face escolhida embaixo do visor; o inventário
 * do jogador; e o painel do nó ({@link NodePanel}): abas por tipo, as faces desdobradas como um cubo e a
 * face escolhida com modo, rede, filtro, prioridade e redstone.
 *
 * <p>Duas disposições, pela largura ({@link RouterLayout}). Larga (a de abertura): à esquerda o visor, os
 * cartões e o inventário; à direita o painel inteiro, com as abas em cima. Estreita (até o mínimo de
 * 300 × 240, a menor escala automática): as abas na largura toda, o visor com o cubo e os cartões embaixo à
 * esquerda e, à direita, o resto do painel em cima do inventário.
 *
 * <p>O visor e o cubo andam juntos: a face clicada num escolhe a mesma no outro, e a face sob o mouse num
 * realça a do outro. Os slots de cartão mostram os da face e do tipo escolhidos: ao mudar de aba ou de
 * face a tela avisa o servidor ({@link SelectFacePayload}), que troca o que os slots mostram.
 *
 * <p>Com o Upgrade de Alcance valendo ({@link RangeView#present()}), um botão "Máquinas · 3/8" acima do
 * visor alterna o visor com a lista das máquinas ({@link RangeList}); sem a lista à vista, o painel ganha a
 * linha Editando.
 * Escolher um grupo ou uma máquina troca o alvo de edição: as faces passam ao quadro canônico (Norte =
 * Frente) e o visor mostra o bloco do alvo sem o roteador.
 *
 * <p>Tudo vem de {@link RouterMenu#snapshot()}; o painel lê o snapshot a cada quadro, então um snapshot
 * novo aparece sem recriar widgets e sem perder aba, face ou o que está aberto.
 *
 * <p>A tela é a anfitriã: tamanho e redimensionamento, os widgets, o repasse dos eventos e as dicas. O
 * cabeçalho fica no {@link RouterHeader}, a coluna do visor e da lista no {@link MachineColumn}, os slots no
 * {@link RouterSlots} e o que o painel manda no {@link RouterPanelActions}.
 */
public class RouterScreen extends AbstractContainerScreen<RouterMenu> {

    // a tela cresce pela borda direita, pela de baixo e pelo canto, e o tamanho fica lembrado na sessão
    /** Largura de abertura: a disposição larga, com o painel inteiro ao lado do visor. */
    private static final int DEFAULT_W = 420;
    /** Alça do canto (lado, em px) e espessura das bordas que redimensionam. */
    private static final int GRIP = 7;
    private static final int EDGE = 3;
    private static int savedW = DEFAULT_W;
    /** Altura de abertura, com e sem o Upgrade de Alcance (a lista das máquinas pede mais), até o jogador mudar. */
    private static final int DEFAULT_H = 264;
    private static final int RANGE_DEFAULT_H = 300;
    private static int savedH = -1;

    private final boolean preview;
    private final List<FlatButton> buttons = new ArrayList<>();
    private final NodePanel panel;
    private final RouterLayout layout;
    private final RouterHeader header;
    private final MachineColumn column;
    private final RouterSlots slots;

    private int w = RouterLayout.MIN_W;
    private int h = RouterLayout.MIN_H;
    private final ResizeHandle resizeHandle = new ResizeHandle(GRIP, EDGE);

    public RouterScreen(RouterMenu menu, Inventory inventory, Component title) {
        this(menu, inventory, title, false);
    }

    /**
     * @param preview sem servidor (captura de desenvolvimento): as mudanças valem só no snapshot local
     */
    public RouterScreen(RouterMenu menu, Inventory inventory, Component title, boolean preview) {
        super(menu, inventory, title);
        this.preview = preview;
        this.imageWidth = RouterLayout.MIN_W;
        this.imageHeight = RouterLayout.MIN_H;
        RouterPanelActions actions = new RouterPanelActions(menu, preview);
        this.panel = new NodePanel(actions, menu.snapshot(), menu.selectedFace());
        this.layout = new RouterLayout(() -> menu.snapshot().range().present());
        this.header = new RouterHeader(menu, layout, preview, panel::type, this::setFocused, this::refresh);
        this.column = new MachineColumn(menu::snapshot, panel, layout, actions::send, () -> menu.containerId);
        this.slots = new RouterSlots(menu, layout, panel::type);
        // o aviso de outra tela agiria no alvo errado
        PanelNotices.clear();
    }

    // ------------------------------------------------------------------ estado

    private RouterSnapshot snapshot() {
        return menu.snapshot();
    }

    private ResourceType type() {
        return panel.type();
    }

    private Direction face() {
        return panel.face();
    }

    private int tierColor() {
        return GuiPaint.tierColor(snapshot().tier());
    }

    // ------------------------------------------------------------------ montagem

    @Override
    protected void init() {
        w = Math.max(RouterLayout.MIN_W, Math.min(savedW, maxW()));
        int wantedH = savedH > 0 ? savedH : menu.snapshot().range().present() ? RANGE_DEFAULT_H : DEFAULT_H;
        h = Math.max(RouterLayout.MIN_H, Math.min(wantedH, maxH()));
        imageWidth = w;
        imageHeight = h;
        layout.setSize(w, h);
        super.init();
        buttons.clear();
        int x = leftPos;
        int y = topPos;

        add(header.createTitle(x, y));
        EditBox renameBox = header.createRenameBox(font, x, y);
        addRenderableWidget(renameBox);
        if (header.renaming()) {
            setFocused(renameBox);
        }

        add(column.createMachinesButton(x, y));

        layoutPanel();
        placedCards = null;
        refresh();
    }

    /** O painel no lugar da disposição atual. */
    private void layoutPanel() {
        int x = leftPos;
        int y = topPos;
        if (layout.wide()) {
            panel.layout(x + layout.rx(), y + RouterLayout.TOP_Y, layout.rw(), h - 8 - RouterLayout.TOP_Y);
        } else {
            int netY = layout.narrowNetY();
            int bodyBottom = layout.inventoryY() - 1 - 4;
            panel.layoutSplit(x + RouterLayout.X0, y + RouterLayout.TOP_Y, layout.x1() - RouterLayout.X0,
                    x + RouterLayout.X0, y + netY, layout.lw(), RouterLayout.NARROW_NET_H, x + layout.rx(),
                    y + RouterLayout.NARROW_BODY_Y, layout.rw(), bodyBottom - RouterLayout.NARROW_BODY_Y);
        }
    }

    /** Modo atual das abas (gancho do e2e). */
    public TabLayout.Mode tabMode() {
        return panel.tabMode();
    }

    /** O painel do nó (gancho do e2e). */
    public NodePanel panel() {
        return panel;
    }

    public int themeX() {
        return leftPos + w - 4 - ThemeToggle.SIZE;
    }

    public int themeY() {
        return topPos + RouterLayout.HEAD_Y + 1;
    }

    private int maxW() {
        return Math.max(RouterLayout.MIN_W, width - 8);
    }

    private int maxH() {
        return Math.max(RouterLayout.MIN_H, height - 8);
    }

    private FlatButton add(FlatButton button) {
        buttons.add(button);
        return addRenderableWidget(button);
    }

    /** Posições, visibilidade e narração que dependem do snapshot ou do estado da tela. */
    private void refresh() {
        panel.setSnapshot(snapshot());
        panel.targetFromList(column.listShown());
        header.refresh(leftPos);
        column.refresh(leftPos, topPos);
        syncSelection();
        placeSlots();
    }

    /** Fora da tela, onde ficam os slots de cartão sem a linha do Filtro à vista. */
    private static final int[] PARKED = {-1000, -1000};
    /** Onde os slots do menu estão agora (canto do primeiro cartão); {@code null} = ainda não postos. */
    private int @Nullable [] placedCards;

    /**
     * Os slots do menu acompanham o tamanho da tela e a linha do Filtro do painel, onde ficam os de cartão: só
     * troca os slots quando o lugar muda.
     */
    private void placeSlots() {
        int[] at = panel.cardSlotsAt();
        int[] cards = at == null ? PARKED : new int[] {at[0] - leftPos + 1, at[1] - topPos + 1};
        if (placedCards != null && placedCards[0] == cards[0] && placedCards[1] == cards[1]) {
            return;
        }
        placedCards = cards;
        menu.placeSlots(cards[0], cards[1], layout.inventoryX(), layout.inventoryY(),
                w - 26 - RouterLayout.HEAD_SHIFT, w - 26 - 18 - RouterLayout.HEAD_SHIFT);
    }

    /**
     * Aba ou face mudou (pelo painel, pelo visor ou pelo alvo novo): os slots de cartão passam a mostrar
     * os da face e do tipo novos. O menu local muda já, para a previsão dos cliques; o servidor, pelo pacote.
     */
    private void syncSelection() {
        if (type() == menu.selectedType() && face() == menu.selectedFace()) {
            return;
        }
        menu.select(type(), face());
        if (!preview) {
            PacketDistributor.sendToServer(new SelectFacePayload(menu.containerId, type(), face()));
        }
    }

    // ------------------------------------------------------------------ entrada

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        // com um nome em edição, o clique fora (até no tema ou na alça) salva; na caixa, fica nela
        if (column.labelClick(mouseX, mouseY, button) || panel.labelClick(mouseX, mouseY, button)) {
            return true;
        }
        boolean popup = panel.popupOpen();
        if (!popup && ThemeToggle.mouseClicked(themeX(), themeY(), mouseX, mouseY, button)) {
            return true;
        }
        if (button == 0 && !popup && resizeHandle.begin(mouseX, mouseY, leftPos, topPos, w, h)) {
            return true;
        }
        if (popup) {
            return panel.mouseClicked(mouseX, mouseY, button);
        }
        header.clickOutside(mouseX, mouseY);
        if (panel.mouseClicked(mouseX, mouseY, button)) {
            return true;
        }
        if (column.mouseClicked(mouseX, mouseY, button)) {
            return true;
        }
        boolean wasRenaming = header.renaming();
        boolean handled = super.mouseClicked(mouseX, mouseY, button);
        if (header.renaming() && !wasRenaming) {
            // a tela dá o foco ao botão clicado depois do onPress, tirando-o do campo do nome
            setFocused(header.renameBox());
        }
        return handled;
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (resizeHandle.dragging()) {
            dragResize(mouseX, mouseY);
            return true;
        }
        // a tela de contêiner não repassa o arrasto aos filhos; o visor recebe direto
        if (column.mouseDragged(mouseX, mouseY, button)) {
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
        if (column.mouseReleased(mouseX, mouseY, button)) {
            return true;
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }

    /**
     * Redimensiona em torno do centro, como o filtro: a borda arrastada segue o mouse e a oposta se
     * move igual, então o painel continua centralizado.
     */
    private void dragResize(double mouseX, double mouseY) {
        int newW = w;
        int newH = h;
        if (resizeHandle.changesWidth()) {
            double wanted = resizeHandle.wantedWidth(mouseX);
            newW = Math.max(RouterLayout.MIN_W, Math.min(maxW(), (int) Math.round(wanted / 2) * 2));
        }
        if (resizeHandle.changesHeight()) {
            double wanted = resizeHandle.wantedHeight(mouseY);
            newH = Math.max(RouterLayout.MIN_H, Math.min(maxH(), (int) Math.round(wanted / 2) * 2));
        }
        if (newW != w || newH != h) {
            savedW = newW;
            savedH = newH;
            rebuild();
        }
    }

    /**
     * Refaz os widgets no tamanho lembrado: aba, face e o que está aberto ficam no painel e sobrevivem; o
     * nome em edição é fechado sem gravar.
     */
    private void rebuild() {
        header.cancelRename();
        rebuildWidgets();
    }

    @Override
    public void resize(Minecraft minecraft, int width, int height) {
        resizeHandle.end();
        super.resize(minecraft, width, height);
    }

    @Override
    public void removed() {
        PanelNotices.clear();
        super.removed();
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (panel.mouseScrolled(mouseX, mouseY, scrollY)) {
            return true;
        }
        if (column.mouseScrolled(mouseX, mouseY, scrollY)) {
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (header.renaming()) {
            header.keyPressed(keyCode, scanCode, modifiers);
            // nenhuma tecla fecha a tela enquanto se digita (nem a do inventário)
            return true;
        }
        if (column.keyPressed(keyCode, scanCode, modifiers) || panel.keyPressed(keyCode, scanCode, modifiers)) {
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean charTyped(char codePoint, int modifiers) {
        if (header.renaming()) {
            return header.renameBox().charTyped(codePoint, modifiers);
        }
        if (column.charTyped(codePoint, modifiers) || panel.charTyped(codePoint, modifiers)) {
            return true;
        }
        return super.charTyped(codePoint, modifiers);
    }

    // ------------------------------------------------------------------ desenho

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        // barato (meia dúzia de larguras de texto): cobre snapshot novo (menu.version() mudou) e mudanças
        // de estado feitas pelo teclado, sem recriar widgets
        refresh();
        GuiText.beginFrame();
        boolean popup = panel.popupOpen();
        // com uma lista do painel aberta, o que está embaixo dela não fica realçado
        super.render(g, popup ? -1 : mouseX, popup ? -1 : mouseY, partialTick);
        panel.renderOverlay(g, mouseX, mouseY);
        if (popup) {
            Component clipped = GuiText.clipAt(mouseX, mouseY);
            if (clipped != null) {
                setTooltipForNextRenderPass(clipped);
            }
            return;
        }
        renderTooltips(g, mouseX, mouseY);
    }

    /** A dica do ponto, na ordem: tema, alça, botões, painel, slots, itens, visor e lista, texto abreviado. */
    private void renderTooltips(GuiGraphics g, int mouseX, int mouseY) {
        if (ThemeToggle.hovered(themeX(), themeY(), mouseX, mouseY)) {
            g.renderComponentTooltip(font, ThemeToggle.tooltip(), mouseX, mouseY);
            return;
        }
        if (resizeHandle.hover(mouseX, mouseY, leftPos, topPos, w, h) != null) {
            setTooltipForNextRenderPass(Component.translatable("gui.wirelessautomate.resize.tooltip"));
            return;
        }
        for (FlatButton button : buttons) {
            if (button.visible && button.isHovered()) {
                Component tooltip = button.currentTooltip();
                if (tooltip != null) {
                    setTooltipForNextRenderPass(tooltip);
                }
                return;
            }
        }
        Component panelTip = panel.tooltip(mouseX, mouseY);
        if (panelTip != null) {
            setTooltipForNextRenderPass(panelTip);
            return;
        }
        Component slotTip = slots.tooltip(hoveredSlot);
        if (slotTip != null) {
            setTooltipForNextRenderPass(slotTip);
            return;
        }
        renderTooltip(g, mouseX, mouseY);
        boolean machineTip = column.tooltips(mouseX, mouseY, this::setTooltipForNextRenderPass);
        // texto abreviado: a dica traz o texto inteiro, se nenhuma outra dica já está no ar
        boolean hasTooltip = machineTip || hoveredSlot != null && hoveredSlot.hasItem();
        if (!hasTooltip) {
            Component clipped = GuiText.clipAt(mouseX, mouseY);
            if (clipped != null) {
                setTooltipForNextRenderPass(clipped);
            }
        }
    }

    @Override
    protected void renderLabels(GuiGraphics g, int mouseX, int mouseY) {
        // os textos são desenhados em renderBg, em coordenadas da tela
    }

    @Override
    protected void renderBg(GuiGraphics g, float partialTick, int mouseX, int mouseY) {
        int x = leftPos;
        int y = topPos;
        int trim = tierColor();
        GuiPaint.panel(g, x, y, w, h, trim);

        header.render(g, x, y, trim, themeX(), themeY(), mouseX, mouseY);
        slots.renderUpgradeSlots(g, x, y);

        // na larga, uma linha entre a coluna do visor e a do painel
        if (layout.wide()) {
            int lineX = x + layout.rx() - RouterLayout.COLUMN_GAP / 2;
            g.fill(lineX, y + RouterLayout.TOP_Y, lineX + 1, y + h - 8, GuiPaint.LINE);
        }

        // visor 3D: máquina e roteador; com uma lista do painel aberta, nada de realce sob o mouse
        boolean popup = panel.popupOpen();
        int pointerX = popup ? -1 : mouseX;
        int pointerY = popup ? -1 : mouseY;
        column.render(g, font, x, y, pointerX, pointerY);

        panel.render(g, pointerX, pointerY, column.viewShown() && !popup ? column.hoveredFace() : null);
        slots.renderCards(g, panel.cardSlotsAt());
        slots.renderInventory(g, x, y);

        // alça de redimensionar: três riscos na diagonal, acesos com o mouse em cima ou arrastando
        ResizeHandle.Edge hover = resizeHandle.dragging() || !popup
                ? resizeHandle.hover(mouseX, mouseY, x, y, w, h) : null;
        ResizeGrip.renderDotted(g, x, y, w, h, hover, trim);
    }

    // ------------------------------------------------------------------ captura de desenvolvimento

    public void previewType(ResourceType type) {
        panel.select(type);
        refresh();
    }

    public void previewFace(Direction face) {
        panel.selectFace(face);
        refresh();
    }

    public void previewRename(String draft) {
        header.previewRename(draft);
    }

    /** e2e: muda o tamanho como se a borda tivesse sido arrastada. */
    public void previewResize(int width, int height) {
        savedW = width;
        savedH = height;
        rebuild();
    }

    /**
     * e2e: põe o painel em dia com o snapshot do menu agora; um snapshot com o alvo novo devolve a face escolhida à do
     * alvo, e o gancho que escolhe uma face não pode ser desfeito por ele no quadro seguinte.
     */
    public void syncNow() {
        refresh();
    }

    /** e2e: esconde ou mostra a lista das máquinas (o jogador não esconde). */
    public void previewToggleList() {
        column.toggleListForTests();
        refresh();
    }

    /** Tamanho atual do painel, {@code {largura, altura}} (gancho do e2e). */
    public int[] size() {
        return new int[] {w, h};
    }

    /** A disposição larga (o painel inteiro ao lado do visor) está valendo (gancho do e2e). */
    public boolean wideLayout() {
        return layout.wide();
    }

    /** A face da máquina sob o mouse no visor (gancho do e2e). */
    public @Nullable Direction viewHoveredFace() {
        return column.hoveredFace();
    }

    /** Largura da coluna do visor (gancho do e2e). */
    public int viewColumnWidth() {
        return layout.lw();
    }

    public void previewNetworkList(boolean open) {
        panel.previewNetworkList(open);
    }

    /** A lista de redes da linha Rede está aberta (gancho do e2e). */
    public boolean networkListShown() {
        return panel.networkListShown();
    }

    /** Centro da linha do alvo {@code target} na lista (aberta), ou {@code null} se fora da vista (gancho do e2e). */
    public int @Nullable [] rangeRowCenter(EditTarget target) {
        return column.rangeRowCenter(target);
    }

    /** Centro do botão {@code action} na linha do alvo na lista, ou {@code null} se ele não está lá (gancho do e2e). */
    public int @Nullable [] rangeButtonCenter(RangeRowButtons.Action action) {
        return column.rangeButtonCenter(action);
    }

    /** Centro do Olho da linha da máquina {@code target} na lista, ou {@code null} (gancho do e2e). */
    public int @Nullable [] rangeEyeCenter(EditTarget target) {
        return column.rangeEyeCenter(target);
    }

    /** Centro do Apagar todas embaixo da lista, ou {@code null} se ele não aparece (gancho do e2e). */
    public int @Nullable [] litClearCenter() {
        return column.litClearCenter();
    }

    /** O ✕ da linha do alvo na lista está armado, esperando o segundo clique (gancho do e2e). */
    public boolean rangeUnlinkArmed() {
        return column.rangeUnlinkArmed();
    }

    /** Ponto da seta de um grupo na lista, que abre e fecha sem escolher (gancho do e2e). */
    public int @Nullable [] rangeArrowPoint(int group) {
        return column.rangeArrowPoint(group);
    }

    /** O nome de uma linha da lista das máquinas está em edição (gancho do e2e). */
    public boolean rangeLabelEditing() {
        return column.labelEditing();
    }

    /** A lista das máquinas está na tela (gancho do e2e). */
    public boolean rangeListShown() {
        return column.listShown();
    }

    /** O visor está na tela (gancho do e2e); lado a lado, os dois estão. */
    public boolean rangeViewShown() {
        return column.rangeShown() && column.viewShown();
    }

    /** Centro da face no cubo do painel, para simular o mouse em cima na captura. */
    public int[] previewFaceCenter(Direction direction) {
        return orNowhere(panel.faceCenter(direction));
    }

    /** Visor no ângulo inicial do snapshot atual. */
    public void previewViewDefault() {
        column.previewViewDefault();
    }

    /** Visor num ângulo fixo (graus) e zoom. */
    public void previewView(float yaw, float pitch, float zoom) {
        column.previewView(yaw, pitch, zoom);
    }

    /** Ponto da tela no centro da face da máquina no visor, para simular o mouse em cima. */
    public int[] previewViewFaceCenter(Direction direction) {
        return column.previewViewFaceCenter(direction);
    }

    public int[] previewCardSlotCenter(int slot) {
        return slots.slotCenter(leftPos, topPos, slot);
    }

    public int[] previewUpgradeSlotCenter() {
        return slots.slotCenter(leftPos, topPos, RouterMenu.UPGRADE_SLOT);
    }

    public int[] previewRangeSlotCenter() {
        return slots.slotCenter(leftPos, topPos, RouterMenu.RANGE_SLOT);
    }

    public int[] previewEditFilterCenter() {
        return orNowhere(panel.editCenter());
    }

    public int[] previewTabCenter(ResourceType t) {
        return orNowhere(panel.tabCenter(t));
    }

    public int[] previewNetworkCenter() {
        return orNowhere(panel.networkCenter());
    }

    private static int[] orNowhere(int @Nullable [] point) {
        return point == null ? new int[] {-1, -1} : point;
    }
}
