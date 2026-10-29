package io.github.matheusanbs.wirelessautomate.client.tv.editor;

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
    private final TvLibraryTab library;

    TvEditorProbe(TvEditorDoc doc, TvLeftTabs leftTabs, TvPalettePanel palette, TvComponentsPanel components,
            TvEditorCanvas canvas, TvEditorBar bar, TvLayersPanel layers, TvPropsPanel props, TvCanvasMenu menu,
            TvPopups popups, TvLibraryTab library) {
        this.library = library;
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

    /** O centro de uma parte da aba Artes ({@code ARTS} ou {@code PICTURES}), com a aba aberta. */
    public int[] librarySegmentCenter(String part) {
        leftTabs.select(TvLeftTabs.Tab.ARTS);
        return library.segmentCenter(TvLibraryTab.Part.valueOf(part));
    }

    /** A parte aberta da aba Artes. */
    public String libraryPart() {
        return library.part().name();
    }

    /** O centro da linha da imagem de nome {@code name} na parte Imagens, ou {@code null}. */
    public int @Nullable [] pictureRowCenter(String name) {
        return library.pictures().rowCenter(name);
    }

    /** O centro de um botão da parte Imagens ({@code SEND}, {@code RENAME}, {@code REMOVE}, {@code COPY}...). */
    public int[] picturesButtonCenter(String button) {
        return library.pictures().buttonCenter(TvPicturesPanel.Button.valueOf(button));
    }

    /** Se o campo do nome de uma imagem está aberto. */
    public boolean pictureNaming() {
        return library.pictures().textFocused();
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

    /**
     * O centro do controle de {@code id}, com a coluna rolada para ele ficar o mais embaixo que der (a lista que abre
     * perto da borda); {@code null} se a aba não o tem.
     */
    public int @Nullable [] propCenterLow(String id) {
        return props.centerLow(id);
    }

    /** O quanto a coluna de propriedades está rolada, em pixels. */
    public int propsScroll() {
        return props.scroll();
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

    /** Se uma janela solta (o seletor de cor, uma lista) está aberta. */
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

    private @Nullable TvDropdownPopup dropdown() {
        return popups.current() instanceof TvDropdownPopup d ? d : null;
    }

    /** O id do controle cuja lista está aberta, ou {@code null}. */
    public @Nullable String dropdownOwner() {
        TvDropdownPopup d = dropdown();
        return d == null ? null : d.owner().id;
    }

    /** O centro da linha {@code index} da lista aberta, rolando a lista até ela; {@code null} sem lista ou fora dela. */
    public int @Nullable [] dropdownRowCenter(int index) {
        TvDropdownPopup d = dropdown();
        return d == null || index < 0 || index >= d.size() ? null : d.rowCenter(index);
    }

    /** O centro da linha com o texto {@code text} na lista aberta; {@code null} sem lista ou sem a linha. */
    public int @Nullable [] dropdownRowCenter(String text) {
        TvDropdownPopup d = dropdown();
        return d == null ? null : dropdownRowCenter(d.indexOf(text));
    }

    /** A opção marcada (a que vale) na lista aberta, ou -1. */
    public int dropdownSelected() {
        TvDropdownPopup d = dropdown();
        return d == null ? -1 : d.selected();
    }

    /** A linha realçada (pelo mouse ou pelas setas) na lista aberta, ou -1. */
    public int dropdownActive() {
        TvDropdownPopup d = dropdown();
        return d == null ? -1 : d.active();
    }

    /** Quantas opções a lista aberta tem, ou 0. */
    public int dropdownSize() {
        TvDropdownPopup d = dropdown();
        return d == null ? 0 : d.size();
    }

    /**
     * Onde a lista aberta está: x, y, largura, altura, linhas à vista, 1 se abriu para cima, e o retângulo em que as
     * janelas soltas podem aparecer (x, y, largura, altura); {@code null} sem lista.
     */
    public int @Nullable [] dropdownGeometry() {
        TvDropdownPopup d = dropdown();
        if (d == null) {
            return null;
        }
        TvDropdownPlace p = d.place();
        TvRect b = popups.bounds();
        return new int[] {p.x(), p.y(), p.w(), p.h(), p.rows(), p.above() ? 1 : 0, b.x(), b.y(), b.w(), b.h()};
    }
}
