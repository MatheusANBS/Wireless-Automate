package io.github.matheusanbs.wirelessautomate.client.tv.editor;

import io.github.matheusanbs.wirelessautomate.client.GuiPaint;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.IntConsumer;
import java.util.function.IntSupplier;
import java.util.function.Predicate;
import java.util.function.Supplier;
import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.Component;

/**
 * O construtor de uma aba de propriedades: guarda os controles na ordem e o {@code y} onde vai o próximo (no
 * conteúdo, que o painel rola). As abas pedem rótulos, ajudas, linhas com rótulo e espaços; o formulário só empilha.
 * Refeito pelo painel quando a escolha, a aba ou a estrutura da peça mudam, nunca por quadro.
 */
final class TvForm {
    /** A largura do conteúdo: a coluna da direita menos as margens e a barra de rolagem. */
    static final int W = TvEditorLayout.RIGHT_W - 12;
    /** A largura do rótulo de uma linha "rótulo + controle". */
    static final int LABEL_W = 56;
    static final int GAP = 3;

    final Font font;
    private final List<TvControl> controls = new ArrayList<>();
    private int y;

    TvForm(Font font) {
        this.font = font;
    }

    List<TvControl> controls() {
        return controls;
    }

    int y() {
        return y;
    }

    /** A altura total do conteúdo. */
    int height() {
        return y;
    }

    <C extends TvControl> C add(C control) {
        controls.add(control);
        return control;
    }

    void skip(int pixels) {
        y += pixels;
    }

    /** Um rótulo apagado numa linha de 10 px. */
    void label(String id, Supplier<Component> text) {
        controls.add(new TvControls.Label(font, id, 0, y, W, GuiPaint.MUTED, text));
        y += 10;
    }

    void label(String id, Component text) {
        label(id, () -> text);
    }

    /** Um texto de ajuda quebrado, com um espaço depois. */
    void hint(String id, Component text) {
        TvControls.Hint hint = new TvControls.Hint(font, id, 0, y, W, text);
        controls.add(hint);
        y += hint.h + GAP;
    }

    /** O rótulo de uma linha "rótulo + controle": põe o rótulo e devolve o {@code y} do controle. */
    int inline(String id, Component label) {
        controls.add(new TvControls.Label(font, id + ".label", 0, y + 2, LABEL_W - 2, GuiPaint.MUTED, () -> label));
        return y;
    }

    /** Fecha uma linha de controles de {@code height} px. */
    void endRow(int height) {
        y += height + GAP;
    }

    /** Uma linha "rótulo + campo numérico". */
    TvNumberField number(String id, Component label, int min, int max, int step, IntSupplier get, IntConsumer set) {
        int y = inline(id, label);
        TvNumberField field = add(new TvNumberField(font, id, LABEL_W, y, W - LABEL_W, min, max, step, get, set));
        endRow(TvTextField.H);
        return field;
    }

    /** Uma linha "rótulo + campo de texto". */
    TvTextField text(String id, Component label, int maxLength, Predicate<String> filter, Component hint,
            Supplier<String> get, Consumer<String> set) {
        int y = inline(id, label);
        TvTextField field = add(new TvTextField(font, id, LABEL_W, y, W - LABEL_W, maxLength, filter, hint, get, set));
        endRow(TvTextField.H);
        return field;
    }

    /** Uma linha "rótulo + seletor ‹ ›". */
    TvControls.Cycle cycle(String id, Component label, Supplier<Component> shown, IntConsumer step) {
        int y = inline(id, label);
        TvControls.Cycle cycle = add(new TvControls.Cycle(font, id, LABEL_W, y, W - LABEL_W, shown, step));
        endRow(TvControls.ROW - 1);
        return cycle;
    }

    /** Uma caixa de marcar na largura toda. */
    TvControls.Toggle toggle(String id, Component label, BooleanSupplier get, Consumer<Boolean> set) {
        TvControls.Toggle toggle = add(new TvControls.Toggle(font, id, 0, y, W, label, get, set));
        endRow(11);
        return toggle;
    }

    /** Um seletor ‹ › na largura toda, sem rótulo ao lado (nomes compridos). */
    TvControls.Cycle wideCycle(String id, Supplier<Component> shown, IntConsumer step) {
        TvControls.Cycle cycle = add(new TvControls.Cycle(font, id, 0, y, W, shown, step));
        endRow(TvControls.ROW - 1);
        return cycle;
    }
}
