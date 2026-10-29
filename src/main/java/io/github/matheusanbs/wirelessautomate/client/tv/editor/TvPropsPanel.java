package io.github.matheusanbs.wirelessautomate.client.tv.editor;

import io.github.matheusanbs.wirelessautomate.client.GuiPaint;
import io.github.matheusanbs.wirelessautomate.client.GuiText;
import io.github.matheusanbs.wirelessautomate.client.tv.TvIcons;
import io.github.matheusanbs.wirelessautomate.menu.TvMenu;
import io.github.matheusanbs.wirelessautomate.tv.TvPiece;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

/**
 * A coluna da direita do editor: o resumo da escolha (com nada escolhido, a base da Tela: {@link TvBasePanel}), as abas Dado, Formato, Estilo e Regras (em duas fileiras, porque
 * os quatro nomes não cabem em 132 px) e, abaixo, os controles da aba da peça escolhida, que rolam pela roda do mouse.
 * Com várias peças só vale o que é de todas (a "Cor de tudo" e a opacidade). Os controles ({@link TvControl}) são
 * montados pelo {@link TvForm} da aba só quando a escolha, a aba ou a estrutura da peça mudam, e relidos
 * ({@code refresh}) quando o rascunho muda; por quadro, só o desenho, sob um recorte da área de rolagem. Repassa o
 * mouse e as letras ao controle sob o mouse ou com o foco do teclado.
 */
final class TvPropsPanel {
    /** As abas da coluna. */
    enum Tab { DATA, FORMAT, STYLE, RULES }

    private static final String[] TAB_KEYS = {"props.data", "props.format", "props.style", "props.rules"};
    private static final int INFO_H = 26;
    private static final int TAB_H = 12;
    private static final int SCROLL_STEP = 12;

    private final TvEditorDoc doc;
    private final TvSelectionInfo info;
    private final TvPropsCtx ctx;
    private final TvRect[] tabRects = new TvRect[Tab.values().length];
    private final GuiText.Fitted[] tabNames = new GuiText.Fitted[Tab.values().length];
    private final int[] tabWidths = new int[Tab.values().length];
    private TvRect rect = TvRect.NONE;
    private TvRect infoRect = TvRect.NONE;
    private TvRect content = TvRect.NONE;
    private Font font;
    private Tab tab = Tab.DATA;
    private List<TvControl> controls = List.of();
    private GuiText.Fitted placeholder;
    private int contentHeight;
    private int scroll;
    private @Nullable TvControl focus;
    private @Nullable TvControl pressed;
    private boolean stale = true;
    private int builtVersion = -1;
    private int builtLibrary = -1;
    private int builtStorage = -1;
    private int builtLayoutKind = -1;
    private int builtStructure;
    private @Nullable Tab builtTab;
    private TvSelection builtSelection = TvSelection.EMPTY;

    TvPropsPanel(TvEditorDoc doc, List<TvMenu.NetworkChoice> networks, List<TvMenu.LinkChoice> links,
            TvEditorActions actions, TvPopups popups) {
        this.doc = doc;
        this.info = new TvSelectionInfo(doc);
        this.ctx = new TvPropsCtx(doc, networks, actions, popups, new TvDataState(links));
    }

    void layout(Font font, TvRect rect) {
        this.font = font;
        this.rect = rect;
        infoRect = new TvRect(rect.x(), rect.y(), rect.w(), INFO_H);
        info.layout(font, infoRect);
        int tabsY = rect.y() + INFO_H + 3;
        int half = (rect.w() - 1) / 2;
        for (int i = 0; i < tabRects.length; i++) {
            int col = i % 2;
            int row = i / 2;
            tabRects[i] = new TvRect(rect.x() + col * (half + 1), tabsY + row * (TAB_H + 1),
                    col == 0 ? half : rect.w() - half - 1, TAB_H);
            tabNames[i] = GuiText.fit(font, TvEditorText.key(TAB_KEYS[i]), tabRects[i].w() - 4);
            tabWidths[i] = font.width(tabNames[i].shown());
        }
        int contentY = tabsY + 2 * (TAB_H + 1) + 2;
        content = new TvRect(rect.x(), contentY, rect.w(), Math.max(0, rect.bottom() - contentY));
        placeholder = GuiText.fit(font, TvEditorText.key("props.pick"), content.w() - 12);
        stale = true;
    }

    /** Refaz os controles (a escolha, a aba ou a estrutura mudou) ou os relê (só o rascunho mudou). */
    private void sync() {
        TvSelection selection = doc.selection();
        if (!selection.equals(builtSelection)) {
            ctx.data().reset();
        }
        boolean valid = !selection.isEmpty() && selection.last() < doc.pieces().size();
        int structure = (valid ? structure(selection) : TvBasePanel.structure(doc)) * 31 + TvSections.version();
        boolean rebuild = stale || tab != builtTab || !selection.equals(builtSelection) || structure != builtStructure;
        // o layout escolhido na lista da base não muda o rascunho, mas muda o Remover layout
        if (!rebuild && builtVersion == doc.model().version() && builtLibrary == TvLibraryClient.version()
                && builtStorage == TvStorageClient.version() && builtLayoutKind == doc.layoutKind()) {
            return;
        }
        builtVersion = doc.model().version();
        builtLibrary = TvLibraryClient.version();
        builtStorage = TvStorageClient.version();
        builtLayoutKind = doc.layoutKind();
        if (rebuild) {
            build(selection, valid, structure);
        } else {
            for (TvControl control : controls) {
                control.refresh();
            }
        }
    }

    private int structure(TvSelection selection) {
        TvPiece piece = doc.pieces().get(selection.first());
        int key = piece.type().ordinal() * 2 + (selection.size() > 1 ? 1 : 0);
        if (tab == Tab.DATA) {
            key = key * 4 + ctx.data().source(piece.binding().orElse(null)).ordinal() + 1;
        }
        return tab == Tab.RULES ? key * 1009 + TvRulesTab.structure(piece.rules()) : key;
    }

    private void build(TvSelection selection, boolean valid, int structure) {
        blurFocus();
        stale = false;
        builtTab = tab;
        builtSelection = selection;
        builtStructure = structure;
        TvForm form = new TvForm(font, ctx.popups());
        if (!valid) {
            TvBasePanel.build(form, ctx);
            install(form);
            return;
        }
        boolean many = selection.size() > 1;
        if (many && tab != Tab.STYLE) {
            form.hint("props.many", TvEditorText.key("props.many"));
        } else {
            switch (tab) {
                case DATA -> TvDataTab.build(form, ctx);
                case FORMAT -> TvFormatTab.build(form, ctx);
                case STYLE -> TvStyleTab.build(form, ctx);
                case RULES -> TvRulesTab.build(form, ctx);
            }
        }
        install(form);
    }

    private void install(TvForm form) {
        controls = form.controls();
        contentHeight = form.height();
        scroll = Math.max(0, Math.min(scroll, maxScroll()));
        placeControls();
    }

    private int viewHeight() {
        return content.h() - 4;
    }

    private int maxScroll() {
        return Math.max(0, contentHeight - viewHeight());
    }

    /** O canto do conteúdo na tela, já rolado, repassado aos controles. */
    private void placeControls() {
        int originX = content.x() + 2;
        int originY = content.y() + 2 - scroll;
        for (TvControl control : controls) {
            control.place(originX, originY);
        }
    }

    void render(GuiGraphics g, int mouseX, int mouseY) {
        sync();
        info.render(g);
        boolean base = builtSelection.isEmpty() || builtSelection.last() >= doc.pieces().size();
        for (int i = 0; i < tabRects.length; i++) {
            TvRect r = tabRects[i];
            boolean hovered = r.contains(mouseX, mouseY);
            boolean active = !base && i == tab.ordinal();
            GuiPaint.tab(g, r.x(), r.y(), r.w(), r.h(), active, hovered);
            GuiText.draw(g, font, tabNames[i], r.x() + (r.w() - tabWidths[i]) / 2, r.y() + 2,
                    GuiPaint.tabText(active, hovered));
        }
        GuiPaint.inset(g, content.x(), content.y(), content.w(), content.h());
        if (controls.isEmpty()) {
            GuiText.draw(g, font, placeholder, content.x() + 6, content.y() + 6, GuiPaint.MUTED);
            return;
        }
        g.enableScissor(content.x() + 1, content.y() + 1, content.right() - 1, content.bottom() - 1);
        for (TvControl control : controls) {
            if (control.ay + control.h > content.y() && control.ay < content.bottom()) {
                control.render(g, mouseX, mouseY);
            }
        }
        g.disableScissor();
        if (maxScroll() > 0) {
            int track = viewHeight();
            int thumb = Math.max(8, track * track / contentHeight);
            int thumbY = content.y() + 2 + (track - thumb) * scroll / maxScroll();
            g.fill(content.right() - 4, thumbY, content.right() - 2, thumbY + thumb, GuiPaint.BUTTON_BORDER);
        }
    }

    boolean mouseClicked(double mouseX, double mouseY, int button) {
        // o soltar de um clique que abriu uma janela solta vai a ela: o controle desse clique não fica preso
        pressed = null;
        if (!rect.contains(mouseX, mouseY)) {
            blurFocus();
            return false;
        }
        for (int i = 0; i < tabRects.length; i++) {
            if (tabRects[i].contains(mouseX, mouseY)) {
                blurFocus();
                tab = Tab.values()[i];
                scroll = 0;
                return true;
            }
        }
        TvControl hit = null;
        if (content.contains(mouseX, mouseY)) {
            for (TvControl control : controls) {
                if (control.mouseClicked(mouseX, mouseY, button)) {
                    hit = control;
                    break;
                }
            }
        }
        pressed = hit;
        if (focus != null && focus != hit) {
            blurFocus();
        }
        if (hit != null && hit.wantsKeys()) {
            focus = hit;
        }
        return true;
    }

    /** O mouse andou com o botão apertado: só o controle clicado recebe (o número arrastado, o controle deslizante). */
    boolean mouseDragged(double mouseX, double mouseY) {
        if (pressed == null) {
            return false;
        }
        pressed.mouseDragged(mouseX, mouseY);
        return true;
    }

    boolean mouseReleased(double mouseX, double mouseY) {
        TvControl control = pressed;
        if (control == null) {
            return false;
        }
        pressed = null;
        if (control.mouseReleased(mouseX, mouseY) && control.wantsKeys()) {
            focus = control;
        }
        doc.model().history().breakKey();
        return true;
    }

    /** A dica do controle sob o mouse, ou nulo. */
    @Nullable Component tooltip(double mouseX, double mouseY) {
        if (!content.contains(mouseX, mouseY)) {
            return null;
        }
        for (TvControl control : controls) {
            Component tip = control.tooltip(mouseX, mouseY);
            if (tip != null) {
                return tip;
            }
        }
        return null;
    }

    boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        if (!content.contains(mouseX, mouseY)) {
            return false;
        }
        for (TvControl control : controls) {
            if (control.mouseScrolled(mouseX, mouseY, delta)) {
                return true;
            }
        }
        int next = Math.max(0, Math.min(maxScroll(), scroll - (int) Math.signum(delta) * SCROLL_STEP));
        if (next != scroll) {
            scroll = next;
            placeControls();
        }
        return true;
    }

    /** Um campo de texto tem o foco: o teclado é dele (atalhos do editor ficam quietos). */
    boolean textFocused() {
        return focus != null && focus.wantsKeys();
    }

    boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (focus == null || !focus.wantsKeys()) {
            return false;
        }
        TvControl field = focus;
        field.keyPressed(keyCode, scanCode, modifiers);
        if (!field.wantsKeys()) {
            blurFocus();
        }
        return true;
    }

    boolean charTyped(char c, int modifiers) {
        return focus != null && focus.wantsKeys() && focus.charTyped(c, modifiers);
    }

    /** Solta o foco do teclado; o texto do campo volta ao valor e a próxima mudança do campo é outra no desfazer. */
    void blurFocus() {
        if (focus != null) {
            focus.blur();
            focus = null;
            doc.model().history().breakKey();
        }
    }

    // ------------------------------------------------------------------ ganchos do e2e

    void selectTab(Tab wanted) {
        blurFocus();
        tab = wanted;
        scroll = 0;
    }

    Tab tab() {
        return tab;
    }

    int[] tabCenter(Tab wanted) {
        return tabRects[wanted.ordinal()].center();
    }

    /** O controle de {@code id}, rolado até aparecer; {@code null} se a aba aberta não o tem. */
    @Nullable TvControl find(String id) {
        sync();
        for (TvControl control : controls) {
            if (control.id.equals(id)) {
                if (control.y < scroll || control.y + control.h > scroll + viewHeight()) {
                    scroll = Math.max(0, Math.min(maxScroll(), control.y - 4));
                    placeControls();
                }
                return control;
            }
        }
        return null;
    }

    /**
     * Onde o JEI pode largar um item: a grade de itens do Baú (do dado ou do Ícone) ou, no Ícone sem Baú ligado, o
     * campo do id. {@code null} quando a aba aberta não tem nenhum dos dois. Só olha a lista de controles montada.
     */
    int @Nullable [] ghostBounds() {
        TvControl target = ghostTarget();
        return target == null ? null : new int[] {target.ax, target.ay, target.w, target.h};
    }

    /** Escolhe o item largado pelo JEI no alvo de {@link #ghostBounds()}. */
    void acceptGhost(ResourceLocation id) {
        TvControl target = ghostTarget();
        if (target instanceof TvItemGrid grid) {
            grid.accept(id);
        } else if (target != null) {
            TvDataTab.setIconItem(ctx, Optional.of(id));
        }
    }

    /** O alvo do arrasto aceita o ingrediente: a grade só itens; o espaço do Ícone item, fluido ou químico. */
    boolean ghostAccepts(Object ingredient) {
        TvControl target = ghostTarget();
        if (target instanceof TvItemGrid) {
            return ingredient instanceof net.minecraft.world.item.ItemStack stack && !stack.isEmpty();
        }
        return target != null && TvIcons.accepts(ingredient);
    }

    /** Escolhe o ingrediente largado pelo JEI no alvo de {@link #ghostBounds()}. */
    void acceptGhostIngredient(Object ingredient) {
        TvControl target = ghostTarget();
        if (target instanceof TvItemGrid grid) {
            if (ingredient instanceof net.minecraft.world.item.ItemStack stack && !stack.isEmpty()) {
                grid.accept(net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(stack.getItem()));
            }
        } else if (target != null) {
            TvDataTab.setIconIngredient(ctx, ingredient);
        }
    }

    private @Nullable TvControl ghostTarget() {
        TvControl field = null;
        for (TvControl control : controls) {
            if (control instanceof TvItemGrid) {
                return control;
            }
            if (control.id.equals("data.icon.slot")) {
                field = control;
            }
        }
        return field;
    }

    int @Nullable [] networkCenter(String id, UUID network) {
        return find(id) instanceof TvNetworkList list ? list.centerOf(network) : null;
    }

    int @Nullable [] arrowCenter(String id, boolean right) {
        return find(id) instanceof TvNumberField field ? field.arrowCenter(right) : null;
    }

    int @Nullable [] chipCenter(String id, int index) {
        TvControl control = find(id);
        return control == null ? null : control.chipCenter(index);
    }

    /** O centro do controle de {@code id}, com a coluna rolada para ele ficar o mais embaixo que der. */
    int @Nullable [] centerLow(String id) {
        TvControl control = find(id);
        if (control == null) {
            return null;
        }
        scroll = Math.max(0, Math.min(maxScroll(), control.y + control.h + 2 - viewHeight()));
        placeControls();
        return new int[] {control.centerX(), control.centerY()};
    }

    int scroll() {
        return scroll;
    }

    int @Nullable [] center(String id) {
        TvControl control = find(id);
        return control == null ? null : new int[] {control.centerX(), control.centerY()};
    }

    /** Dá o foco a um campo de texto ou numérico (como um clique nele). */
    boolean focusField(String id) {
        TvControl control = find(id);
        blurFocus();
        if (control == null || !control.mouseClicked(control.centerX(), control.centerY(), 0)) {
            return false;
        }
        if (control.mouseReleased(control.centerX(), control.centerY()) || control.wantsKeys()) {
            focus = control;
        }
        return focus != null;
    }
}
