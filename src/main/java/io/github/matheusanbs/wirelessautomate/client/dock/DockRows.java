package io.github.matheusanbs.wirelessautomate.client.dock;

import static io.github.matheusanbs.wirelessautomate.client.dock.DockLayout.*;

import io.github.matheusanbs.wirelessautomate.client.GuiPaint;
import io.github.matheusanbs.wirelessautomate.client.GuiText;
import io.github.matheusanbs.wirelessautomate.client.GuiText.Fitted;
import io.github.matheusanbs.wirelessautomate.dock.DockColors;
import io.github.matheusanbs.wirelessautomate.dock.DockStatus;
import io.github.matheusanbs.wirelessautomate.packet.DockStatePayload;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

/**
 * As seis linhas da tela da Doca, uma por lado na ordem de {@link DockColors#ORDER}: a etiqueta na cor do lado, o
 * ícone e o nome da máquina com "12 · 9 trabalhando", o estado com o sinal de forma (ponto, anel, triângulo) e as
 * notas de máquinas descarregadas e fora do alcance; o lado sem nada fica apagado, com "livre". Os textos são medidos
 * e a dica de cada linha montada só quando chega um estado novo; por quadro, só desenha.
 */
final class DockRows {
    private static final String KEY = "gui.wirelessautomate.pattern_dock.";
    private static final DockStatus.State[] STATES = DockStatus.State.values();
    private static final Direction[] DIRECTIONS = Direction.values();
    /** A sombra de dentro da etiqueta e a do texto branco sobre ela (o {@code rgba(0,0,0,.22)} e {@code .35} do mockup). */
    private static final int TAG_SHADE = 0x38000000;
    private static final int TAG_TEXT_SHADOW = 0x59000000;
    private static final int TAG_TEXT = 0xFFFFFFFF;

    /** Uma nota medida e a largura que ela ocupa. */
    private record Note(Fitted text, int width) {
    }

    private final Row[] rows = new Row[ROWS];
    private @Nullable DockStatePayload shown;

    /** O que uma linha desenha, medido na chegada do estado. */
    private record Row(int side, DockStatus.State state, ItemStack icon, Fitted tag, int tagWidth, Fitted name,
            @Nullable Fitted count, int countWidth, Fitted status, int statusWidth, List<Note> notes,
            List<FormattedCharSequence> tip) {
        boolean free() {
            return state == DockStatus.State.FREE;
        }
    }

    static MutableComponent key(String key, Object... args) {
        return Component.translatable(KEY + key, args);
    }

    static Component sideName(int side) {
        return key("side." + DIRECTIONS[side].getSerializedName());
    }

    /** Mede de novo as linhas se o estado mudou (ou na primeira vez). */
    void update(@Nullable DockStatePayload state, Font font) {
        if (state == null || state == shown) {
            return;
        }
        shown = state;
        for (int i = 0; i < ROWS; i++) {
            int side = DockColors.ORDER[i];
            rows[i] = measure(side, state.lines().get(side), font);
        }
    }

    /** Remede tudo na próxima atualização (troca de idioma ou de tamanho da janela). */
    void invalidate() {
        shown = null;
    }

    private static Row measure(int side, DockStatePayload.Line line, Font font) {
        DockStatus.State state = line.state() >= 0 && line.state() < STATES.length ? STATES[line.state()]
                : DockStatus.State.READY;
        boolean hasBlock = line.block() != null;
        ItemStack icon = hasBlock ? new ItemStack(BuiltInRegistries.BLOCK.get(line.block())) : ItemStack.EMPTY;
        Fitted tag = GuiText.fit(font, sideName(side), TAG_W - 4);
        int tagWidth = shownWidth(font, tag);
        Fitted count = null;
        int countWidth = 0;
        if (hasBlock) {
            count = GuiText.fit(font, key("count", line.linked(), line.working()), TEXT_W / 2);
            countWidth = shownWidth(font, count);
        }
        Component nameText = hasBlock ? line.machine()
                : state == DockStatus.State.FREE ? Component.literal("—") : key("no_machine");
        Fitted name = GuiText.fit(font, nameText, nameWidth(countWidth));
        Component statusText = key("state." + stateKey(state));
        Fitted status = GuiText.fit(font, statusText, TEXT_W - SIGN_TEXT);
        int statusWidth = shownWidth(font, status);
        List<Component> noteTexts = new ArrayList<>(2);
        if (line.unloaded() > 0) {
            noteTexts.add(key("unloaded", line.unloaded()));
        }
        if (line.outOfReach() > 0) {
            noteTexts.add(key("out_of_reach", line.outOfReach()));
        }
        List<Note> notes = new ArrayList<>(noteTexts.size());
        int left = TEXT_W - SIGN_TEXT - statusWidth;
        for (Component note : noteTexts) {
            left -= GAP + SIGN_TEXT;
            Fitted fitted = GuiText.fit(font, note, Math.max(0, left));
            int width = shownWidth(font, fitted);
            notes.add(new Note(fitted, width));
            left -= width;
        }
        return new Row(side, state, icon, tag, tagWidth, name, count, countWidth, status, statusWidth,
                List.copyOf(notes), tip(font, side, line, state, noteTexts));
    }

    /** A largura que o texto medido ocupa na tela (a dele, ou a do espaço se foi abreviado). */
    private static int shownWidth(Font font, Fitted text) {
        return Math.min(text.width(), font.width(text.shown()));
    }

    /** A dica: o lado e o provedor, a frase longa do estado e, com notas, por que essas máquinas não recebem. */
    private static List<FormattedCharSequence> tip(Font font, int side, DockStatePayload.Line line,
            DockStatus.State state, List<Component> notes) {
        MutableComponent head = key("tip_side", sideName(side)).withStyle(ChatFormatting.BOLD)
                .append(Component.literal(" · ").withStyle(style -> style.withBold(false)))
                .append(line.provider() == null ? key("nothing_attached").withStyle(style -> style.withBold(false))
                        : line.provider().copy().withStyle(style -> style.withBold(false)));
        List<FormattedCharSequence> lines = new ArrayList<>(font.split(head, TIP_W));
        lines.addAll(font.split(key("state." + stateKey(state) + ".hint"), TIP_W));
        if (!notes.isEmpty()) {
            MutableComponent joined = Component.empty();
            for (int i = 0; i < notes.size(); i++) {
                if (i > 0) {
                    joined.append(" · ");
                }
                joined.append(notes.get(i));
            }
            lines.addAll(font.split(key("notes", joined), TIP_W));
        }
        return List.copyOf(lines);
    }

    static String stateKey(DockStatus.State state) {
        return state.name().toLowerCase(java.util.Locale.ROOT);
    }

    /** A dica da linha sob o mouse, ou {@code null}. */
    @Nullable List<FormattedCharSequence> tipAt(int left, int top, double mouseX, double mouseY) {
        int index = rowAt(mouseX - left, mouseY - top);
        return index < 0 || rows[index] == null ? null : rows[index].tip();
    }

    void render(GuiGraphics g, Font font, int left, int top, int mouseX, int mouseY) {
        GuiPaint.inset(g, left + ROWS_X, top + ROWS_Y, ROWS_W, ROWS_H);
        int hovered = rowAt(mouseX - left, mouseY - top);
        for (int i = 0; i < ROWS; i++) {
            int y = top + rowTop(i);
            if (i > 0) {
                g.fill(left + ROWS_X + 1, y, left + ROWS_X + ROWS_W, y + 1, GuiPaint.LINE);
            }
            Row row = rows[i];
            if (row == null) {
                continue;
            }
            if (i == hovered) {
                g.fill(left + ROWS_X + 1, y + 1, left + ROWS_X + ROWS_W, y + ROW_H, GuiPaint.ROW_HOVER);
            }
            renderRow(g, font, row, left, y);
        }
    }

    private static void renderRow(GuiGraphics g, Font font, Row row, int left, int y) {
        boolean free = row.free();
        int tagX = left + TAG_X;
        int tagY = y + TAG_DY;
        int fill = free ? GuiPaint.DISABLED : DockColors.side(row.side());
        g.fill(tagX, tagY, tagX + TAG_W, tagY + TAG_H, GuiPaint.BUTTON_BORDER);
        g.fill(tagX + 1, tagY + 1, tagX + TAG_W - 1, tagY + TAG_H - 1, fill);
        if (!free) {
            g.fill(tagX + 1, tagY + TAG_H - 3, tagX + TAG_W - 1, tagY + TAG_H - 1, TAG_SHADE);
            g.fill(tagX + TAG_W - 3, tagY + 1, tagX + TAG_W - 1, tagY + TAG_H - 3, TAG_SHADE);
        }
        int tagTextX = tagX + (TAG_W - row.tagWidth()) / 2;
        int tagTextY = tagY + (TAG_H - 8) / 2;
        if (!free) {
            GuiPaint.text(g, font, row.tag().shown(), tagTextX + 1, tagTextY + 1, TAG_TEXT_SHADOW);
        }
        GuiText.draw(g, font, row.tag(), tagTextX, tagTextY, free ? GuiPaint.PANEL : TAG_TEXT);

        if (!row.icon().isEmpty()) {
            g.renderItem(row.icon(), left + ICON_X, y + ICON_DY);
        }
        int textX = left + NAME_X;
        GuiText.draw(g, font, row.name(), textX, y + LINE1_DY, free ? GuiPaint.MUTED : GuiPaint.FG);
        if (row.count() != null) {
            GuiText.draw(g, font, row.count(), left + RIGHT - row.countWidth(), y + LINE1_DY, GuiPaint.MUTED);
        }

        int lineY = y + LINE2_DY;
        int signal = signal(row.state());
        sign(g, signal, textX, lineY, signalColor(signal));
        GuiText.draw(g, font, row.status(), textX + SIGN_TEXT, lineY,
                signal == SIGNAL_IDLE ? GuiPaint.MUTED : GuiPaint.FG);
        int x = textX + SIGN_TEXT + row.statusWidth();
        for (Note note : row.notes()) {
            x += GAP;
            sign(g, SIGNAL_WARN, x, lineY, GuiPaint.WARN);
            GuiText.draw(g, font, note.text(), x + SIGN_TEXT, lineY, GuiPaint.MUTED);
            x += SIGN_TEXT + note.width();
        }
    }

    private static int signalColor(int signal) {
        return switch (signal) {
            case SIGNAL_OK -> GuiPaint.OK;
            case SIGNAL_WARN -> GuiPaint.WARN;
            case SIGNAL_ERROR -> GuiPaint.DANGER;
            default -> GuiPaint.MUTED;
        };
    }

    /**
     * O sinal de forma, 7 × 7 px a partir de ({@code x}, {@code y}), na altura das maiúsculas do texto: ponto cheio,
     * anel de 2 px, triângulo, ou o ponto pequeno de 5 × 5 no meio.
     */
    static void sign(GuiGraphics g, int signal, int x, int y, int color) {
        switch (signal) {
            case SIGNAL_OK -> {
                g.fill(x + 2, y, x + 5, y + 7, color);
                g.fill(x + 1, y + 1, x + 6, y + 6, color);
                g.fill(x, y + 2, x + 7, y + 5, color);
            }
            case SIGNAL_WARN -> {
                g.fill(x + 2, y, x + 5, y + 1, color);
                g.fill(x + 1, y + 1, x + 6, y + 2, color);
                g.fill(x, y + 2, x + 2, y + 5, color);
                g.fill(x + 5, y + 2, x + 7, y + 5, color);
                g.fill(x + 1, y + 5, x + 6, y + 6, color);
                g.fill(x + 2, y + 6, x + 5, y + 7, color);
            }
            case SIGNAL_ERROR -> {
                g.fill(x + 3, y, x + 4, y + 1, color);
                g.fill(x + 2, y + 1, x + 5, y + 3, color);
                g.fill(x + 1, y + 3, x + 6, y + 5, color);
                g.fill(x, y + 5, x + 7, y + 7, color);
            }
            default -> GuiPaint.dot(g, x + 1, y + 1, color);
        }
    }
}
