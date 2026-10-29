package io.github.matheusanbs.wirelessautomate.client.tv.editor;

import io.github.matheusanbs.wirelessautomate.tv.TvFont;
import io.github.matheusanbs.wirelessautomate.tv.TvPieceType;
import java.util.UUID;
import org.jetbrains.annotations.Nullable;

/**
 * Os ganchos do e2e e das capturas sobre o editor: onde clicar (o centro de cada parte, abrindo a aba certa quando
 * precisa) e o que está à vista, sem expor as partes ao resto do mod. Só lê; quem clica é o {@code E2eUi}, pelo mesmo
 * caminho do mouse. Vem de {@link TvEditorTab#probe()}.
 */
public final class TvEditorProbe {
    private final TvEditorDoc doc;
    private final TvLeftTabs leftTabs;
    private final TvPalettePanel palette;
    private final TvComponentsPanel components;
    private final TvEditorCanvas canvas;
    private final TvEditorBar bar;
    private final TvLayersPanel layers;
    private final TvPropsPanel props;
    private final TvCanvasMenu menu;
    private final TvPopups popups;

    TvEditorProbe(TvEditorDoc doc, TvLeftTabs leftTabs, TvPalettePanel palette, TvComponentsPanel components,
            TvEditorCanvas canvas, TvEditorBar bar, TvLayersPanel layers, TvPropsPanel props, TvCanvasMenu menu,
            TvPopups popups) {
        this.doc = doc;
        this.leftTabs = leftTabs;
        this.palette = palette;
        this.components = components;
        this.canvas = canvas;
        this.bar = bar;
        this.layers = layers;
        this.props = props;
        this.menu = menu;
        this.popups = popups;
    }

    public int[] paletteCenter(TvPieceType type) {
        leftTabs.select(TvLeftTabs.Tab.PIECES);
        return palette.center(type);
    }

    public int[] pieceCenter(int index) {
        return canvas.pieceCenter(index);
    }

    public int[] handleCenter() {
        return canvas.handleCenter();
    }

    public float canvasScale() {
        return canvas.scale();
    }

    public int[] barCenter(String action) {
        return bar.center(TvEditorBar.Action.valueOf(action));
    }

    public int @Nullable [] layerCenter(int index) {
        leftTabs.select(TvLeftTabs.Tab.LAYERS);
        return layers.rowCenter(index);
    }

    /** As abas da coluna de propriedades pelo nome: {@code DATA}, {@code FORMAT}, {@code STYLE}, {@code RULES}. */
    public int[] propsTabCenter(String tab) {
        return props.tabCenter(TvPropsPanel.Tab.valueOf(tab));
    }

    public String propsTab() {
        return props.tab().name();
    }

    /** O centro do controle de {@code id} (rolado até aparecer), ou {@code null}. */
    public int @Nullable [] propCenter(String id) {
        return props.center(id);
    }

    /** O centro do campo de busca da grade de itens da aba Dado, ou {@code null} sem a grade. */
    public int @Nullable [] itemSearchCenter() {
        return props.find("data.storage.items") instanceof TvItemGrid grid ? grid.searchCenter() : null;
    }

    /** O centro da célula da arte {@code art} na grade de artes {@code id} (rolada até aparecer), ou {@code null}. */
    public int @Nullable [] artCellCenter(String id, UUID art) {
        return props.find(id) instanceof TvArtGrid grid ? grid.centerOf(art) : null;
    }

    /** Se a grade de artes {@code id} mostra a arte {@code art} entre as "Da Tela". */
    public boolean artOnScreenOnly(String id, UUID art) {
        return props.find(id) instanceof TvArtGrid grid && grid.onScreenOnly(art);
    }

    public int @Nullable [] propNetworkCenter(String id, UUID network) {
        return props.networkCenter(id, network);
    }

    public int @Nullable [] propArrowCenter(String id, boolean right) {
        return props.arrowCenter(id, right);
    }

    public int @Nullable [] propChipCenter(String id, int index) {
        return props.chipCenter(id, index);
    }

    /** Dá o foco a um campo de texto (como um clique nele). */
    public boolean focusProp(String id) {
        return props.focusField(id);
    }

    /** O centro da linha do componente de nome guardado {@code name} (os prontos pela chave {@code tv.component.*}). */
    public int @Nullable [] componentCenter(String name) {
        leftTabs.select(TvLeftTabs.Tab.COMPONENTS);
        return components.rowCenter(name);
    }

    /** O centro do ✎ ({@code remove} falso) ou do ✕ de um componente do jogador. */
    public int @Nullable [] componentButton(String name, boolean remove) {
        leftTabs.select(TvLeftTabs.Tab.COMPONENTS);
        return components.buttonCenter(name, remove);
    }

    public boolean componentNaming() {
        return components.textFocused();
    }

    /** As abas da coluna da esquerda pelo nome: {@code PIECES}, {@code COMPONENTS}, {@code LAYERS}. */
    public int[] leftTabCenter(String tab) {
        return leftTabs.center(TvLeftTabs.Tab.valueOf(tab));
    }

    public String leftTab() {
        return leftTabs.tab().name();
    }

    /** O fator do zoom da miniatura, 1 em "caber". */
    public int zoomFactor() {
        return canvas.zoomFactor();
    }

    public int @Nullable [] fitCenter() {
        return canvas.fitCenter();
    }

    public int[] canvasCenter() {
        return canvas.center();
    }

    public boolean menuOpen() {
        return menu.isOpen();
    }

    /** O centro da linha do menu do clique direito pelo item ({@code FRONT}, {@code UP}...), ou {@code null}. */
    public int @Nullable [] menuCenter(String item) {
        return menu.center(TvCanvasMenu.Item.valueOf(item));
    }

    public int undoSize() {
        return doc.model().history().undoSize();
    }

    public boolean canRedo() {
        return doc.model().history().canRedo();
    }

    // ------------------------------------------------------------------ janelas soltas

    /** Se o seletor de cor ou a lista de fontes está aberta. */
    public boolean popupOpen() {
        return popups.isOpen();
    }

    private @Nullable TvColorPopup color() {
        return popups.current() instanceof TvColorPopup c ? c : null;
    }

    /** O centro da amostra do papel {@code role} (1 a 6) no seletor de cor aberto, ou {@code null}. */
    public int @Nullable [] colorThemeCenter(int role) {
        TvColorPopup c = color();
        return c == null ? null : c.themeCenter(role);
    }

    /** O centro do quadrado de saturação e brilho do seletor aberto. */
    public int @Nullable [] colorSquareCenter() {
        TvColorPopup c = color();
        return c == null ? null : c.svCenter();
    }

    public int @Nullable [] colorEyedropperCenter() {
        TvColorPopup c = color();
        return c == null ? null : c.eyeCenter();
    }

    /** Dá o foco ao campo hexadecimal do seletor aberto. */
    public boolean colorHexFocus() {
        TvColorPopup c = color();
        if (c == null) {
            return false;
        }
        c.hex().focus();
        return true;
    }

    /** O valor da cor no seletor aberto, ou -1. */
    public int colorValue() {
        TvColorPopup c = color();
        return c == null ? -1 : c.value();
    }

    /** O centro da linha de uma fonte na lista de fontes aberta, ou {@code null}. */
    public int @Nullable [] fontRowCenter(TvFont font) {
        return popups.current() instanceof TvFontMenu.Popup p ? p.center(font) : null;
    }
}
