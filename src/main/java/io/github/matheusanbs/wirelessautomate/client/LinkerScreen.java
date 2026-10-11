package io.github.matheusanbs.wirelessautomate.client;

import io.github.matheusanbs.wirelessautomate.linker.LinkerBox;
import io.github.matheusanbs.wirelessautomate.linker.LinkerMode;
import io.github.matheusanbs.wirelessautomate.linker.LinkerProblem;
import io.github.matheusanbs.wirelessautomate.linker.LinkerTabs;
import io.github.matheusanbs.wirelessautomate.menu.LinkerMenu;
import io.github.matheusanbs.wirelessautomate.menu.LinkerSnapshot;
import io.github.matheusanbs.wirelessautomate.menu.LinkerSnapshot.Outcome;
import io.github.matheusanbs.wirelessautomate.menu.LinkerSnapshot.RouterDot;
import io.github.matheusanbs.wirelessautomate.menu.RouterSnapshot.NetworkEntry;
import io.github.matheusanbs.wirelessautomate.network.ResourceType;
import io.github.matheusanbs.wirelessautomate.packet.LinkerActionPayload;
import io.github.matheusanbs.wirelessautomate.packet.LinkerActionPayload.Op;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.entity.player.Inventory;
import io.github.matheusanbs.wirelessautomate.net.PacketDistributor;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.glfw.GLFW;

/**
 * Tela do Vinculador (especificação, "Telas da interface › Vinculador"): à esquerda a rede ativa
 * (a linha "Nenhuma (desvincular)", a lista das redes do jogador e Nova rede) e as caixas das abas
 * (Itens, Fluidos, Energia e, com o Mekanism, Químicos); no cabeçalho o modo (Único ou Área). Em
 * Único, só a explicação do clique; em Área, a prévia de cima da caixa marcada com os roteadores
 * dentro dela, os cantos, a contagem e o botão Vincular (ou Desvincular).
 *
 * <p>Tudo vem de {@link LinkerMenu#snapshot()}; os botões leem o estado a cada quadro, então um
 * estado novo aparece sem recriar widgets. A tela não muda nada sozinha: manda a ação e espera o
 * servidor (na captura de desenvolvimento, {@code preview}, muda só o estado local).
 */
public class LinkerScreen extends AbstractContainerScreen<LinkerMenu> {
    /** Tamanho mínimo (o de antes de redimensionar) e margem da janela no máximo. */
    private static final int MIN_W = 300;
    private static final int MIN_H = 204;
    private static final int WINDOW_MARGIN = 8;
    private static final int GRIP = 7;
    private static final int EDGE = 3;
    /** Tamanho lembrado na sessão (as telas do jogo são recriadas a cada abertura). */
    private static int savedW = MIN_W;
    private static int savedH = MIN_H;
    private static final int X0 = 9;
    private static final int HEAD_Y = 8;
    private static final int SEP_Y = 26;
    private static final int BODY_Y = 31;
    /** Cor de destaque do Vinculador (a do item). */
    static final int ACCENT = GuiPaint.ACCENT;
    private static final int WARN = GuiPaint.WARN;
    private static final int GOOD = GuiPaint.OK;
    /** Cor do modo desvincular. */
    private static final int UNLINK = GuiPaint.DANGER;

    // coluna da esquerda: redes e tipo
    private static final int LW = 144;
    private static final int LIST_Y = 42;
    private static final int ROW = 12;
    /** Linhas da lista no tamanho mínimo, no máximo; cada ROW de altura extra soma uma linha. */
    private static final int BASE_ROWS = 6;
    private static final int TYPE_H = 16;
    // coluna da direita: o corpo do modo
    private static final int RX = X0 + LW + 8;
    private static final int MAP_H = 104;

    /** Caixas das abas, na ordem dos botões (Químicos só aparece com o Mekanism). */
    private static final ResourceType[] TABS = ResourceType.values();

    private final boolean preview;
    private final ResizeHandle resizeHandle = new ResizeHandle(GRIP, EDGE);
    private int w = MIN_W;
    private int h = MIN_H;
    private final List<FlatButton> buttons = new ArrayList<>();
    private final List<FlatButton> rowButtons = new ArrayList<>();
    private final List<FlatButton> tabButtons = new ArrayList<>();
    private int scroll;
    private boolean creating;
    private String draft = "";

    private FlatButton newButton;
    private FlatButton createButton;
    private FlatButton linkButton;
    private FlatButton clearButton;
    private FlatButton allButton;
    private EditBox nameBox;

    public LinkerScreen(LinkerMenu menu, Inventory inventory, Component title) {
        this(menu, inventory, title, false);
    }

    /** @param preview sem servidor (captura de desenvolvimento): as ações valem só no estado local */
    public LinkerScreen(LinkerMenu menu, Inventory inventory, Component title, boolean preview) {
        super(menu, inventory, title);
        this.preview = preview;
        this.imageWidth = MIN_W;
        this.imageHeight = MIN_H;
    }

    // tamanho: a largura extra vai para a coluna da direita, a altura para o mapa e a lista de redes

    private int x1() {
        return w - 9;
    }

    private int rw() {
        return x1() - RX;
    }

    private int extraH() {
        return h - MIN_H;
    }

    private int mapH() {
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

    /** Botões de linha criados: o máximo de linhas que a maior altura comporta. */
    private int maxRows() {
        return BASE_ROWS + (maxH() - MIN_H) / ROW;
    }

    private LinkerSnapshot snapshot() {
        return menu.snapshot();
    }

    static Component tr(String key, Object... args) {
        return Component.translatable("gui.wirelessautomate.linker." + key, args);
    }

    private static Component typeName(@Nullable ResourceType type) {
        return type == null ? tr("type.all")
                : ResourceStyle.name(type);
    }

    private static Component modeName(LinkerMode mode) {
        return tr("mode." + mode.getSerializedName());
    }

    private boolean unlink() {
        return snapshot().unlink();
    }

    /** Todas as abas que existem estão marcadas. */
    private boolean allTabs() {
        return snapshot().tabs().isAll(snapshot().available().types());
    }

    /** "Todos" ou "Itens + Fluidos", pelas abas que valem. */
    private Component tabsName() {
        return tabsName(snapshot().tabs(), snapshot().available().types());
    }

    private static Component tabsName(LinkerTabs tabs, List<ResourceType> available) {
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

    private Component activeName() {
        if (unlink()) {
            return tr("network.unlink");
        }
        NetworkEntry entry = snapshot().activeEntry();
        return entry == null ? tr("network.none") : Component.literal(entry.name());
    }

    private int activeColor() {
        if (unlink()) {
            return UNLINK;
        }
        NetworkEntry entry = snapshot().activeEntry();
        return entry == null ? GuiPaint.MUTED : 0xFF000000 | entry.color();
    }

    /** Linhas da lista: "Nenhuma (desvincular)" e as redes. */
    private int rowCount() {
        return snapshot().networks().size() + 1;
    }

    /** A rede da linha {@code index} da lista; {@code null} na linha 0, "Nenhuma (desvincular)". */
    private @Nullable NetworkEntry entryAt(int index) {
        return index == 0 ? null : snapshot().networks().get(index - 1);
    }

    // layout da coluna da esquerda: a lista encolhe quando os tipos precisam de mais linhas

    /** Linhas de tipo (dois chips por linha) dos tipos disponíveis. */
    private int typeRows() {
        return (snapshot().available().types().size() + 1) / 2;
    }

    /** Linhas da lista de redes: 6 com duas linhas de tipo, 5 com três, mais uma por ROW de altura extra. */
    private int listRows() {
        return Math.max(1, Math.min(BASE_ROWS, 8 - typeRows()) + extraH() / ROW);
    }

    private int listH() {
        return listRows() * ROW + 4;
    }

    private int newY() {
        return LIST_Y + listH() + 4;
    }

    private int typeLabelY() {
        return newY() + 20;
    }

    private int typeY() {
        return typeLabelY() + 11;
    }

    /** Roteadores que o Vincular mudaria. */
    private int toLink() {
        return snapshot().inside() - snapshot().already();
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
        rowButtons.clear();
        tabButtons.clear();
        int x = leftPos;
        int y = topPos;

        int modeW = 44;
        LinkerMode[] modes = LinkerMode.values();
        for (int i = 0; i < modes.length; i++) {
            LinkerMode mode = modes[i];
            add(new FlatButton(x + x1() - (modes.length - i) * (modeW + 2) + 2, y + HEAD_Y - 1, modeW, 15, modeName(mode),
                    (g, b, hovered) -> paintChoice(g, b, hovered, modeName(mode), snapshot().mode() == mode),
                    () -> setMode(mode)).tooltip(() -> tr("mode." + mode.getSerializedName() + ".tooltip")));
        }

        for (int i = 0; i < maxRows(); i++) {
            int row = i;
            FlatButton button = add(new FlatButton(x + X0 + 1, y + LIST_Y + 2 + i * ROW, LW - 2, ROW, Component.empty(),
                    (g, b, hovered) -> paintRow(g, b, hovered, row), () -> chooseRow(row))
                    .tooltip(() -> rowTooltip(row)));
            rowButtons.add(button);
        }
        newButton = add(new FlatButton(x + X0, y + newY(), LW, 14, tr("network.new"),
                (g, b, hovered) -> paintText(g, b, hovered, tr("network.new"), GuiPaint.FG), this::startCreate)
                .tooltip(() -> tr("network.new.tooltip")));
        createButton = add(new FlatButton(x + X0 + LW - 26, y + newY(), 26, 14, tr("network.create"),
                (g, b, hovered) -> paintText(g, b, hovered, tr("network.create"), GuiPaint.FG), () -> finishCreate(true)));
        nameBox = new EditBox(font, x + X0 + 4, y + newY() + 3, LW - 34, 9, tr("network.name"));
        nameBox.setBordered(false);
        nameBox.setMaxLength(LinkerActionPayload.MAX_NAME_LENGTH);
        nameBox.setTextColor(GuiPaint.FG);
        TextShadow.setTextShadow(nameBox, false); // tinta sobre porcelana, sem sombra
        nameBox.setValue(draft);
        nameBox.setResponder(value -> draft = value);
        nameBox.setHint(tr("network.name").copy().withStyle(style -> style.withColor(GuiPaint.DISABLED)));
        addRenderableWidget(nameBox);
        if (creating) {
            setFocused(nameBox);
        }

        // a posição dos chips e do Todos vem de refresh(), pela ordem entre os tipos disponíveis
        int typeW = (LW - 2) / 2;
        for (int i = 0; i < TABS.length; i++) {
            ResourceType t = TABS[i];
            tabButtons.add(add(new FlatButton(x + X0, y + typeY(), typeW, TYPE_H, typeName(t),
                    (g, b, hovered) -> paintCheck(g, b, hovered, t), () -> toggleTab(t))
                    .tooltip(() -> tabTooltip(t))));
        }
        allButton = add(new FlatButton(x + X0 + LW - 50, y + typeLabelY() - 3, 50, 12, tr("type.all"),
                (g, b, hovered) -> paintAll(g, b, hovered), this::setAllTabs).tooltip(this::allTooltip));

        linkButton = add(new FlatButton(x + RX, y + actionY(), rw() - 48, 16, tr("link.count", 0), this::paintLink,
                this::link).tooltip(this::linkTooltip));
        clearButton = add(new FlatButton(x + x1() - 46, y + actionY(), 46, 16, tr("clear"),
                (g, b, hovered) -> paintText(g, b, hovered, tr("clear"), b.active ? GuiPaint.FG : GuiPaint.DISABLED),
                this::clear).tooltip(() -> tr("clear.tooltip")));
        refresh();
    }

    private FlatButton add(FlatButton button) {
        buttons.add(button);
        return addRenderableWidget(button);
    }

    /** Visibilidade e mensagens que dependem do estado. */
    private void refresh() {
        LinkerSnapshot s = snapshot();
        int rows = rowCount();
        scroll = Math.max(0, Math.min(scroll, rows - listRows()));
        for (int i = 0; i < rowButtons.size(); i++) {
            FlatButton row = rowButtons.get(i);
            int index = i + scroll;
            row.visible = index < rows;
            NetworkEntry entry = row.visible ? entryAt(index) : null;
            row.setMessage(!row.visible ? Component.empty()
                    : entry == null ? tr("network.unlink") : Component.literal(entry.name()));
        }
        // a lista encolhe quando os tipos precisam de mais linhas; os botões que passam dela somem
        for (int i = listRows(); i < rowButtons.size(); i++) {
            rowButtons.get(i).visible = false;
        }
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
        newButton.setY(topPos + newY());
        createButton.setY(topPos + newY());
        nameBox.setY(topPos + newY() + 3);
        newButton.visible = !creating;
        createButton.visible = creating;
        createButton.active = !draft.strip().isEmpty();
        nameBox.visible = creating;
        boolean area = s.mode() == LinkerMode.AREA;
        linkButton.visible = clearButton.visible = area;
        linkButton.active = s.problem() == LinkerProblem.NONE && toLink() > 0;
        linkButton.setMessage(linkLabel());
        clearButton.active = s.first().isPresent() || s.otherDimension();
    }

    // ------------------------------------------------------------------ ações

    private void send(Op op, Optional<UUID> network, String text, int value) {
        PacketDistributor.sendToServer(new LinkerActionPayload(menu.containerId, op, network, text, value));
    }

    /** Cópia do estado com rede, desvincular, abas e modo trocados (só na captura de desenvolvimento). */
    private static LinkerSnapshot with(LinkerSnapshot s, Optional<UUID> active, boolean unlink, LinkerTabs tabs,
            LinkerMode mode) {
        return new LinkerSnapshot(s.networks(), active, unlink, tabs, s.available(), mode, s.first(), s.second(),
                s.otherDimension(), s.inside(), s.already(), s.unloadedChunks(), s.routers(), s.problem(),
                s.maxVolume(), s.maxDistance(), Optional.empty());
    }

    private void setMode(LinkerMode mode) {
        if (preview) {
            LinkerSnapshot s = snapshot();
            apply(with(s, s.active(), s.unlink(), s.tabs(), mode));
            return;
        }
        send(Op.SET_MODE, Optional.empty(), "", mode.ordinal());
    }

    /** Marca ou desmarca a aba; o servidor recusa desmarcar a última. */
    private void toggleTab(ResourceType t) {
        if (preview) {
            LinkerSnapshot s = snapshot();
            LinkerTabs next = s.tabs().toggle(t);
            if (!next.isEmpty(s.available().types())) {
                apply(with(s, s.active(), s.unlink(), next, s.mode()));
            }
            return;
        }
        send(Op.TOGGLE_TAB, Optional.empty(), "", t.ordinal());
    }

    /**
     * "Todos": com todas as abas marcadas deixa só a primeira disponível (não dá para ficar sem
     * nenhuma); senão marca todas as disponíveis.
     */
    private void setAllTabs() {
        LinkerSnapshot s = snapshot();
        List<ResourceType> available = s.available().types();
        if (available.isEmpty()) {
            return;
        }
        LinkerTabs next = allTabs() ? LinkerTabs.of(available.get(0)) : LinkerTabs.available(available);
        if (preview) {
            apply(with(s, s.active(), s.unlink(), next, s.mode()));
            return;
        }
        send(Op.SET_TABS, Optional.empty(), "", next.mask());
    }

    private void chooseRow(int row) {
        int index = row + scroll;
        if (index >= rowCount()) {
            return;
        }
        LinkerSnapshot s = snapshot();
        NetworkEntry entry = entryAt(index);
        if (entry == null) {
            // "Nenhuma (desvincular)"
            if (s.unlink()) {
                return;
            }
            if (preview) {
                apply(with(s, s.active(), true, s.tabs(), s.mode()));
                return;
            }
            send(Op.SET_UNLINK, Optional.empty(), "", 1);
            return;
        }
        if (!entry.owned() || (!s.unlink() && Optional.of(entry.id()).equals(s.active()))) {
            return;
        }
        if (preview) {
            apply(with(s, Optional.of(entry.id()), false, s.tabs(), s.mode()));
            return;
        }
        send(Op.SET_ACTIVE, Optional.of(entry.id()), "", 0);
    }

    private void link() {
        if (!preview) {
            send(Op.LINK, Optional.empty(), "", 0);
        }
    }

    private void clear() {
        if (preview) {
            LinkerSnapshot s = snapshot();
            apply(new LinkerSnapshot(s.networks(), s.active(), s.unlink(), s.tabs(), s.available(), s.mode(),
                    Optional.empty(), Optional.empty(), false, 0, 0, 0, List.of(), LinkerProblem.NO_AREA, s.maxVolume(),
                    s.maxDistance(), Optional.empty()));
            return;
        }
        send(Op.CLEAR_AREA, Optional.empty(), "", 0);
    }

    private void apply(LinkerSnapshot snapshot) {
        menu.applySnapshot(snapshot);
        refresh();
    }

    private void startCreate() {
        creating = true;
        draft = "";
        nameBox.setValue("");
        setFocused(nameBox);
        refresh();
    }

    private void finishCreate(boolean commit) {
        if (!creating) {
            return;
        }
        creating = false;
        setFocused(null);
        String name = nameBox.getValue().strip();
        if (commit && !name.isEmpty() && !preview) {
            send(Op.CREATE_NETWORK, Optional.empty(), name, 0);
        }
        refresh();
    }

    // ------------------------------------------------------------------ entrada

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT && resizeHandle.begin(mouseX, mouseY, leftPos, topPos, w, h)) {
            return true;
        }
        if (creating && !nameBox.isMouseOver(mouseX, mouseY) && !createButton.isMouseOver(mouseX, mouseY)) {
            finishCreate(false);
        }
        boolean wasCreating = creating;
        boolean handled = super.mouseClicked(mouseX, mouseY, button);
        if (creating && !wasCreating) {
            // a tela dá o foco ao botão clicado depois do onPress, tirando-o do campo do nome
            setFocused(nameBox);
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
     * rede nova é fechado (o texto digitado volta ao reabrir, como no Tablet).
     */
    private void rebuild() {
        creating = false;
        setFocused(null);
        rebuildWidgets();
    }

    @Override
    public void resize(net.minecraft.client.Minecraft minecraft, int width, int height) {
        resizeHandle.end();
        super.resize(minecraft, width, height);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollY) {
        if (mouseX >= leftPos + X0 && mouseX < leftPos + X0 + LW && mouseY >= topPos + LIST_Y
                && mouseY < topPos + LIST_Y + listH()) {
            scroll -= (int) Math.signum(scrollY);
            refresh();
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollY);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (creating) {
            if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
                finishCreate(false);
            } else if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) {
                finishCreate(true);
            } else {
                nameBox.keyPressed(keyCode, scanCode, modifiers);
                refresh();
            }
            // nenhuma tecla fecha a tela enquanto se digita (nem a do inventário)
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean charTyped(char codePoint, int modifiers) {
        if (creating) {
            boolean typed = nameBox.charTyped(codePoint, modifiers);
            refresh();
            return typed;
        }
        return super.charTyped(codePoint, modifiers);
    }

    // ------------------------------------------------------------------ desenho

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        refresh();
        GuiText.beginFrame();
        renderBackground(g); // no 1.20.1 o super.render não escurece o fundo
        super.render(g, mouseX, mouseY, partialTick);
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
        GuiPaint.panel(g, x, y, w, h, ACCENT);

        int titleW = font.width(title);
        GuiText.draw(g, font, title, x + X0, y + HEAD_Y + 3, titleW, GuiPaint.FG);
        Component typeText = unlink() ? tr("head.unlink", tabsName()) : tr("head.type", tabsName());
        int headX = X0 + titleW + 8;
        // até os botões de modo, à direita
        int headW = x1() - LinkerMode.values().length * 46 - 4 - headX;
        GuiText.draw(g, font, typeText, x + headX, y + HEAD_Y + 3, headW, unlink() ? UNLINK : GuiPaint.MUTED);
        g.fill(x + X0, y + SEP_Y, x + x1(), y + SEP_Y + 1, GuiPaint.LINE);

        // rede ativa
        GuiPaint.text(g, font, tr("network.label"), x + X0, y + BODY_Y, GuiPaint.MUTED);
        GuiPaint.box(g, x + X0, y + LIST_Y, LW, listH(), GuiPaint.INSET, GuiPaint.LINE);
        if (s.networks().isEmpty() && scroll == 0) {
            // embaixo da linha "Nenhuma (desvincular)"
            GuiText.draw(g, font, tr("network.empty"), x + X0 + 5, y + LIST_Y + 4 + ROW, LW - 10, GuiPaint.MUTED);
        }
        if (scroll > 0) {
            GuiPaint.text(g, font, Component.literal("▲"), x + X0 + LW - 9, y + LIST_Y + 2, GuiPaint.MUTED);
        }
        if (scroll + listRows() < rowCount()) {
            GuiPaint.text(g, font, Component.literal("▼"), x + X0 + LW - 9, y + LIST_Y + listH() - 10, GuiPaint.MUTED);
        }
        if (creating) {
            GuiPaint.box(g, x + X0, y + newY(), LW - 28, 14, GuiPaint.INSET, ACCENT);
        }

        GuiPaint.text(g, font, tr("type.label"), x + X0, y + typeLabelY(), GuiPaint.MUTED);

        // corpo do modo
        g.fill(x + RX - 5, y + BODY_Y, x + RX - 4, y + h - 9, GuiPaint.LINE);
        if (s.mode() == LinkerMode.SINGLE) {
            renderSingle(g, x + RX, y + BODY_Y);
        } else {
            renderArea(g, s, x + RX, y + BODY_Y, mouseX, mouseY);
        }
        ResizeGrip.renderDotted(g, x, y, w, h, resizeHandle.hover(mouseX, mouseY, x, y, w, h), ACCENT);
    }

    private void renderSingle(GuiGraphics g, int x, int y) {
        int lineY = y;
        Component main = unlink() ? tr("single.unlink.body")
                : tr("single.body", Component.literal(activeName().getString()).withStyle(style -> style.withColor(activeColor())));
        lineY += GuiText.wrap(g, font, main, x, lineY, rw(), 4, GuiPaint.FG);
        lineY += 6;
        Component typeHint = unlink()
                ? (allTabs() ? tr("single.unlink.all") : tr("single.unlink.tabs", tabsName()))
                : (allTabs() ? tr("single.all") : tr("single.tabs", tabsName()));
        for (Component hint : List.of(typeHint, tr("single.toggle"), tr("single.open"))) {
            lineY += GuiText.wrap(g, font, hint, x, lineY, rw(), 3, GuiPaint.MUTED);
            lineY += 4;
        }
    }

    // ------------------------------------------------------------------ prévia de cima

    /** Região do mundo (x, z) mostrada na prévia e a escala, montadas a cada quadro. */
    private record MapView(int left, int top, int width, int height, double centerX, double centerZ, double scale) {
        int sx(double worldX) {
            return left + width / 2 + (int) Math.floor((worldX - centerX) * scale);
        }

        int sz(double worldZ) {
            return top + height / 2 + (int) Math.floor((worldZ - centerZ) * scale);
        }
    }

    private MapView mapView(LinkerSnapshot s, int left, int top, int width, int height) {
        LinkerBox box = s.box();
        double minX;
        double maxX;
        double minZ;
        double maxZ;
        if (box != null) {
            minX = box.minX();
            maxX = box.maxX() + 1;
            minZ = box.minZ();
            maxZ = box.maxZ() + 1;
        } else if (s.first().isPresent()) {
            BlockPos c = s.first().get();
            minX = c.getX() - 15;
            maxX = c.getX() + 16;
            minZ = c.getZ() - 15;
            maxZ = c.getZ() + 16;
        } else {
            minX = minZ = -16;
            maxX = maxZ = 16;
        }
        double margin = Math.max(1.5, Math.max(maxX - minX, maxZ - minZ) * 0.12);
        double spanX = maxX - minX + 2 * margin;
        double spanZ = maxZ - minZ + 2 * margin;
        double scale = Math.min((width - 4) / spanX, (height - 4) / spanZ);
        return new MapView(left, top, width, height, (minX + maxX) / 2, (minZ + maxZ) / 2, scale);
    }

    private void renderArea(GuiGraphics g, LinkerSnapshot s, int x, int y, int mouseX, int mouseY) {
        int mapW = rw();
        int mh = mapH();
        g.fill(x + 1, y + 1, x + mapW - 1, y + mh - 1, GuiPaint.VIEW_PAPER);
        GuiPaint.outline(g, x, y, mapW, mh, GuiPaint.LINE);
        MapView view = mapView(s, x, y, mapW, mh);
        g.enableScissor(x + 1, y + 1, x + mapW - 1, y + mh - 1);
        if (s.first().isPresent()) {
            // grade dos chunks: um quadrado = 16 blocos (agrupa se ficar apertada)
            int step = 16;
            while (step * view.scale() < 6) {
                step *= 2;
            }
            double halfW = mapW / 2.0 / view.scale();
            double halfH = mh / 2.0 / view.scale();
            int fromX = Math.floorDiv((int) Math.floor(view.centerX() - halfW), step) * step;
            int fromZ = Math.floorDiv((int) Math.floor(view.centerZ() - halfH), step) * step;
            for (int wx = fromX; wx <= view.centerX() + halfW; wx += step) {
                int sx = view.sx(wx);
                g.fill(sx, y + 1, sx + 1, y + mh - 1, GuiPaint.VIEW_GRID);
            }
            for (int wz = fromZ; wz <= view.centerZ() + halfH; wz += step) {
                int sz = view.sz(wz);
                g.fill(x + 1, sz, x + mapW - 1, sz + 1, GuiPaint.VIEW_GRID);
            }
            LinkerBox box = s.box();
            int color = activeColor();
            if (box != null) {
                int bx0 = view.sx(box.minX());
                int bz0 = view.sz(box.minZ());
                int bx1 = Math.max(bx0 + 1, view.sx(box.maxX() + 1));
                int bz1 = Math.max(bz0 + 1, view.sz(box.maxZ() + 1));
                g.fill(bx0, bz0, bx1, bz1, (color & 0x00FFFFFF) | 0x26000000);
                dashedRect(g, bx0, bz0, bx1, bz1, color);
            }
            // os cantos embaixo dos pontos: um roteador no canto continua visível
            corner(g, view, s.first().get(), "1");
            s.second().ifPresent(second -> corner(g, view, second, "2"));
            int size = view.scale() >= 3 ? 5 : 3;
            for (RouterDot dot : s.routers()) {
                int dx = view.sx(dot.x() + 0.5) - size / 2;
                int dz = view.sz(dot.z() + 0.5) - size / 2;
                int fill = dot.color() < 0 ? GuiPaint.INSET : 0xFF000000 | dot.color();
                int ring = dot.linked() ? GuiPaint.RING : GuiPaint.BEVEL_LIGHT;
                g.fill(dx - 1, dz - 1, dx + size + 1, dz + size + 1, ring);
                g.fill(dx, dz, dx + size, dz + size, fill);
                if (dot.color() < 0) {
                    GuiPaint.outline(g, dx, dz, size, size, GuiPaint.MUTED);
                }
            }
        } else {
            Component hint = s.otherDimension() ? tr("problem.other_dimension") : tr("map.empty");
            List<FormattedCharSequence> lines = font.split(hint, mapW - 16);
            int ty = y + mh / 2 - lines.size() * 5;
            for (FormattedCharSequence line : lines) {
                g.drawString(font, line, x + (mapW - font.width(line)) / 2, ty, GuiPaint.MUTED, false);
                ty += 10;
            }
        }
        g.disableScissor();
        if (s.first().isPresent()) {
            int step = 16;
            while (step * view.scale() < 6) {
                step *= 2;
            }
            Component scaleText = tr("map.scale", step);
            GuiPaint.textRight(g, font, scaleText, x + mapW - 3, y + mh - 10, GuiPaint.DISABLED);
        }

        // cantos
        int cy = y + mh + 4;
        cornerLine(g, x, cy, tr("corner1"), s.first());
        cornerLine(g, x, cy + 10, tr("corner2"), s.second());

        // estado (contagem ou o que impede) e, embaixo, o detalhe ou o resultado do último Vincular
        int sy = y + mh + 25;
        if (s.problem() != LinkerProblem.NONE) {
            GuiText.wrap(g, font, status(s), x, sy, rw(), 2, statusColor(s));
            return;
        }
        // depois do Vincular, o resultado toma o lugar da contagem (que viraria "N já na rede")
        Component head = s.outcome().map(this::outcomeText).orElse(status(s));
        GuiText.draw(g, font, head, x, sy, rw(), s.outcome().isPresent() ? GOOD : GuiPaint.FG);
        Component detail = s.outcome().isPresent() ? outcomeDetail(s.outcome().get()) : detail(s);
        if (detail != null) {
            boolean unloaded = s.outcome().map(o -> o.unloadedChunks() > 0).orElse(s.unloadedChunks() > 0);
            GuiText.draw(g, font, detail, x, sy + 11, rw(), unloaded ? WARN : GuiPaint.MUTED);
        }
    }

    private void cornerLine(GuiGraphics g, int x, int y, Component label, Optional<BlockPos> pos) {
        GuiPaint.text(g, font, label, x, y, GuiPaint.MUTED);
        Component value = pos.<Component>map(p -> Component.literal(p.getX() + ", " + p.getY() + ", " + p.getZ()))
                .orElse(Component.literal("—"));
        GuiPaint.text(g, font, value, x + Math.max(font.width(tr("corner1")), font.width(tr("corner2"))) + 6, y,
                pos.isPresent() ? GuiPaint.FG : GuiPaint.DISABLED);
    }

    private void corner(GuiGraphics g, MapView view, BlockPos pos, String label) {
        int cx = view.sx(pos.getX() + 0.5);
        int cz = view.sz(pos.getZ() + 0.5);
        g.fill(cx - 3, cz - 3, cx + 4, cz + 4, GuiPaint.RING);
        g.fill(cx - 2, cz - 2, cx + 3, cz + 3, GuiPaint.ACCENT);
        g.drawString(font, label, cx + 5, cz - 9, GuiPaint.FG, false);
    }

    /** Retângulo tracejado (traços de 4 px, folgas de 2 px). */
    private static void dashedRect(GuiGraphics g, int x0, int y0, int x1, int y1, int color) {
        for (int i = x0; i < x1; i += 6) {
            g.fill(i, y0, Math.min(i + 4, x1), y0 + 1, color);
            g.fill(i, y1 - 1, Math.min(i + 4, x1), y1, color);
        }
        for (int i = y0; i < y1; i += 6) {
            g.fill(x0, i, x0 + 1, Math.min(i + 4, y1), color);
            g.fill(x1 - 1, i, x1, Math.min(i + 4, y1), color);
        }
    }

    /** "N roteadores na área" (ou "N roteadores · M já na rede"), ou o que impede. */
    private Component status(LinkerSnapshot s) {
        return switch (s.problem()) {
            case NONE -> s.already() > 0
                    ? tr(s.unlink() ? "count.with_unlinked" : "count.with_already", s.inside(), s.already())
                    : s.inside() == 1 ? tr("count.one") : tr("count", s.inside());
            case TOO_BIG -> tr("problem.too_big", s.maxVolume());
            case TOO_FAR -> tr("problem.too_far", s.maxDistance());
            default -> tr("problem." + s.problem().key());
        };
    }

    /** Os chunks descarregados da área, que ficariam de fora; {@code null} se não há. */
    private @Nullable Component detail(LinkerSnapshot s) {
        if (s.unloadedChunks() == 0) {
            return null;
        }
        return s.unloadedChunks() == 1 ? tr("outcome.unloaded.one") : tr("outcome.unloaded", s.unloadedChunks());
    }

    private static int statusColor(LinkerSnapshot s) {
        return switch (s.problem()) {
            case NONE -> GuiPaint.FG;
            case NO_AREA, INCOMPLETE -> GuiPaint.MUTED;
            default -> WARN;
        };
    }

    private Component outcomeText(Outcome o) {
        if (o.unlink()) {
            return tr("outcome.unlinked", o.linked(), tabsName(o.tabs(), snapshot().available().types()));
        }
        Component network = Component.literal(o.network()).withStyle(style -> style.withColor(0xFF000000 | o.color()));
        return tr("outcome", o.linked(), network);
    }

    /**
     * Embaixo do resultado: os chunks descarregados que ficaram de fora ou, sem eles, os protegidos
     * ou os que já estavam (não cabem todos numa linha).
     */
    private @Nullable Component outcomeDetail(Outcome o) {
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

    private Component linkTooltip() {
        LinkerSnapshot s = snapshot();
        if (s.problem() != LinkerProblem.NONE) {
            return status(s);
        }
        if (toLink() == 0) {
            return s.inside() == 0 ? tr("link.none") : tr(s.unlink() ? "unlink.all_out" : "link.all_in");
        }
        if (s.unlink()) {
            return allTabs() ? tr("unlink.tooltip", toLink()) : tr("unlink.tooltip.tabs", toLink(), tabsName());
        }
        return allTabs() ? tr("link.tooltip", toLink(), activeName())
                : tr("link.tooltip.tabs", toLink(), tabsName(), activeName());
    }

    /**
     * "Vincular 9" ou "Desvincular 9". Quais abas entram aparece nas caixas e no tooltip: pôr o
     * número delas aqui não cabe no botão (82 px) em português.
     */
    private Component linkLabel() {
        return tr(unlink() ? "unlink.count" : "link.count", toLink());
    }

    private Component tabTooltip(ResourceType t) {
        LinkerSnapshot s = snapshot();
        boolean checked = s.tabs().contains(t);
        if (checked && s.tabs().toggle(t).isEmpty(s.available().types())) {
            return tr("tab.last", typeName(t));
        }
        return tr(checked ? "tab.on" : "tab.off", typeName(t));
    }

    // ------------------------------------------------------------------ botões

    private void paintRow(GuiGraphics g, FlatButton b, boolean hovered, int row) {
        int index = row + scroll;
        if (index >= rowCount()) {
            return;
        }
        int scrollSpace = rowCount() > listRows() ? 10 : 0;
        NetworkEntry entry = entryAt(index);
        if (entry == null) {
            // "Nenhuma (desvincular)": anel vazio na cor do modo
            boolean selected = unlink();
            if (selected || hovered) {
                g.fill(b.getX(), b.getY(), b.getX() + b.getWidth(), b.getY() + b.getHeight(),
                        selected ? GuiPaint.ROW_SELECTED : GuiPaint.ROW_HOVER);
            }
            if (selected) {
                g.fill(b.getX(), b.getY(), b.getX() + 1, b.getY() + b.getHeight(), UNLINK);
            }
            GuiPaint.outline(g, b.getX() + 5, b.getY() + 3, 6, 6, UNLINK);
            GuiText.draw(g, font, tr("network.unlink"), b.getX() + 14, b.getY() + 2, b.getWidth() - 18 - scrollSpace,
                    selected ? GuiPaint.FG : GuiPaint.MUTED);
            return;
        }
        boolean selected = !unlink() && Optional.of(entry.id()).equals(snapshot().active());
        if (selected) {
            g.fill(b.getX(), b.getY(), b.getX() + b.getWidth(), b.getY() + b.getHeight(), GuiPaint.ROW_SELECTED);
            g.fill(b.getX(), b.getY(), b.getX() + 1, b.getY() + b.getHeight(), ACCENT);
        } else if (hovered && entry.owned()) {
            g.fill(b.getX(), b.getY(), b.getX() + b.getWidth(), b.getY() + b.getHeight(), GuiPaint.ROW_HOVER);
        }
        GuiPaint.eye(g, b.getX() + 5, b.getY() + 3, 0xFF000000 | entry.color());
        GuiText.draw(g, font, Component.literal(entry.name()), b.getX() + 14, b.getY() + 2,
                b.getWidth() - 18 - scrollSpace,
                entry.owned() ? (selected ? GuiPaint.FG : GuiPaint.MUTED) : GuiPaint.DISABLED);
    }

    private @Nullable Component rowTooltip(int row) {
        int index = row + scroll;
        if (index >= rowCount()) {
            return null;
        }
        NetworkEntry entry = entryAt(index);
        if (entry == null) {
            return tr("network.unlink").copy().append("\n")
                    .append(tr("network.unlink.tooltip").copy().withStyle(style -> style.withColor(GuiPaint.TOOLTIP_MUTED)));
        }
        Component name = Component.literal(entry.name());
        if (!entry.owned()) {
            return name.copy().append("\n").append(tr("network.foreign").copy().withStyle(style -> style.withColor(GuiPaint.TOOLTIP_MUTED)));
        }
        if (!unlink() && Optional.of(entry.id()).equals(snapshot().active())) {
            return name.copy().append("\n").append(tr("network.active").copy().withStyle(style -> style.withColor(GuiPaint.TOOLTIP_MUTED)));
        }
        return name.copy().append("\n").append(tr("network.choose").copy().withStyle(style -> style.withColor(GuiPaint.TOOLTIP_MUTED)));
    }

    private void paintChoice(GuiGraphics g, FlatButton b, boolean hovered, Component text, boolean selected) {
        if (selected) {
            GuiPaint.box(g, b.getX(), b.getY(), b.getWidth(), b.getHeight(), GuiPaint.SELECTED, GuiPaint.SELECTED);
        } else {
            GuiPaint.box(g, b.getX(), b.getY(), b.getWidth(), b.getHeight(), GuiPaint.BUTTON,
                    hovered ? GuiPaint.BUTTON_HOVER_BORDER : GuiPaint.BUTTON_BORDER);
        }
        GuiPaint.textCentered(g, font, text, b.getX() + b.getWidth() / 2 + 1, b.getY() + (b.getHeight() - 8) / 2,
                selected ? GuiPaint.SELECTED_TEXT : GuiPaint.FG);
    }

    /** Chip de um tipo: ícone e nome; marcado, borda e fundo na cor do tipo (ou do desvincular). */
    private void paintCheck(GuiGraphics g, FlatButton b, boolean hovered, ResourceType t) {
        boolean checked = snapshot().tabs().contains(t);
        int color = unlink() ? UNLINK : ResourceStyle.color(t);
        int border = checked ? color : hovered ? GuiPaint.BUTTON_HOVER_BORDER : GuiPaint.BUTTON_BORDER;
        int fill = checked ? GuiPaint.mix(GuiPaint.INSET, color, 0.18f) : hovered ? GuiPaint.BUTTON : GuiPaint.INSET;
        GuiPaint.box(g, b.getX(), b.getY(), b.getWidth(), b.getHeight(), fill, border);
        int ix = b.getX() + 5;
        ResourceStyle.drawIcon(g, t, ix, b.getY() + (b.getHeight() - ResourceStyle.ICON) / 2);
        int tx = ix + ResourceStyle.ICON + 4;
        GuiText.draw(g, font, typeName(t), tx, b.getY() + (b.getHeight() - 8) / 2,
                b.getX() + b.getWidth() - 3 - tx, checked ? GuiPaint.FG : GuiPaint.MUTED);
    }

    /** Todos: botão de texto, na cor do Vinculador quando todas as abas estão marcadas. */
    private void paintAll(GuiGraphics g, FlatButton b, boolean hovered) {
        boolean checked = allTabs();
        int color = unlink() ? UNLINK : ACCENT;
        GuiPaint.box(g, b.getX(), b.getY(), b.getWidth(), b.getHeight(),
                checked ? GuiPaint.mix(GuiPaint.INSET, color, 0.18f) : hovered ? GuiPaint.BUTTON : GuiPaint.INSET,
                checked ? color : hovered ? GuiPaint.BUTTON_HOVER_BORDER : GuiPaint.BUTTON_BORDER);
        GuiText.draw(g, font, tr("type.all"), b.getX() + 4, b.getY() + (b.getHeight() - 8) / 2, b.getWidth() - 8,
                checked || hovered ? GuiPaint.FG : GuiPaint.MUTED);
    }

    private Component allTooltip() {
        return allTabs() ? tr("type.all.tooltip.one") : tr("type.all.tooltip");
    }

    private void paintText(GuiGraphics g, FlatButton b, boolean hovered, Component text, int color) {
        int border = hovered ? GuiPaint.BUTTON_HOVER_BORDER : GuiPaint.BUTTON_BORDER;
        GuiPaint.box(g, b.getX(), b.getY(), b.getWidth(), b.getHeight(), b.active ? GuiPaint.BUTTON : GuiPaint.INSET,
                b.active ? border : GuiPaint.LINE);
        GuiPaint.textCentered(g, font, text, b.getX() + b.getWidth() / 2 + 1, b.getY() + (b.getHeight() - 8) / 2,
                b.active ? color : GuiPaint.DISABLED);
    }

    /** Vincular: botão principal na cor da rede ativa, com o ponto dela. */
    private void paintLink(GuiGraphics g, FlatButton b, boolean hovered) {
        int color = activeColor();
        if (b.active) {
            int fill = hovered ? GuiPaint.mix(color, GuiPaint.BEVEL_LIGHT, 0.15f) : color;
            GuiPaint.box(g, b.getX(), b.getY(), b.getWidth(), b.getHeight(), fill, GuiPaint.mix(color, GuiPaint.RING, 0.3f));
        } else {
            GuiPaint.box(g, b.getX(), b.getY(), b.getWidth(), b.getHeight(), GuiPaint.INSET, GuiPaint.LINE);
        }
        Component text = linkLabel();
        int textW = Math.min(font.width(text), b.getWidth() - 6);
        GuiText.draw(g, font, text, b.getX() + (b.getWidth() - textW) / 2, b.getY() + 4, textW,
                b.active ? GuiPaint.DARK_TEXT : GuiPaint.DISABLED);
    }

    // ------------------------------------------------------------------ captura de desenvolvimento

    void previewCreate(String name) {
        startCreate();
        nameBox.setValue(name);
        refresh();
    }

    void previewCancelCreate() {
        finishCreate(false);
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

    int[] previewLinkCenter() {
        return new int[] {linkButton.getX() + linkButton.getWidth() / 2, linkButton.getY() + 8};
    }
}
