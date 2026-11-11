package io.github.matheusanbs.wirelessautomate.client.dock;

import static io.github.matheusanbs.wirelessautomate.client.dock.DockLayout.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.matheusanbs.wirelessautomate.dock.DockStatus;
import org.junit.jupiter.api.Test;

class DockLayoutTest {
    @Test
    void sixRowsStackedInsideTheScreen() {
        assertEquals(ROWS_Y, rowTop(0));
        for (int i = 1; i < ROWS; i++) {
            assertEquals(rowTop(i - 1) + ROW_H, rowTop(i), "linha " + i);
        }
        assertTrue(rowTop(ROWS - 1) + ROW_H <= H - X0, "a última linha passa da borda de baixo");
        assertTrue(ROWS_Y > SUMMARY_Y + 9, "as linhas encostam no resumo");
        assertTrue(SUMMARY_Y > PILL_Y + PILL_H, "o resumo encosta no cabeçalho");
    }

    @Test
    void rowAtFindsTheRowUnderThePoint() {
        for (int i = 0; i < ROWS; i++) {
            assertEquals(i, rowAt(ROWS_X, rowTop(i)), "topo da linha " + i);
            assertEquals(i, rowAt(ROWS_X + ROWS_W - 1, rowTop(i) + ROW_H - 1), "fim da linha " + i);
        }
        assertEquals(-1, rowAt(ROWS_X - 1, rowTop(0)));
        assertEquals(-1, rowAt(ROWS_X + ROWS_W, rowTop(0)));
        assertEquals(-1, rowAt(ROWS_X, ROWS_Y - 1));
        assertEquals(-1, rowAt(ROWS_X, ROWS_Y + ROWS_H));
    }

    @Test
    void columnsInOrderAndInsideTheWidth() {
        assertTrue(ROWS_X < TAG_X && TAG_X + TAG_W < ICON_X, "etiqueta");
        assertTrue(ICON_X + 16 < NAME_X, "ícone");
        assertTrue(NAME_X < RIGHT && RIGHT <= ROWS_X + ROWS_W, "texto");
        assertTrue(ROWS_X + ROWS_W <= W - X0, "a área das linhas passa da borda");
        assertTrue(THEME_X + 11 <= W - 4, "o botão do tema passa da borda");
        assertTrue(TAG_DY + TAG_H <= ROW_H && ICON_DY + 16 <= ROW_H, "etiqueta ou ícone passa da linha");
        assertTrue(LINE1_DY + 9 <= LINE2_DY && LINE2_DY + 9 <= ROW_H, "as duas linhas de texto");
    }

    @Test
    void nameShrinksForTheCount() {
        assertEquals(TEXT_W, nameWidth(0));
        assertEquals(TEXT_W - 80 - GAP, nameWidth(80));
        assertEquals(0, nameWidth(TEXT_W * 2));
        assertTrue(NAME_X + nameWidth(80) + GAP + 80 <= RIGHT, "o nome encosta na contagem");
    }

    @Test
    void summaryBarFitsBetweenTheTexts() {
        int left = 70;
        int radius = 40;
        int bar = barWidth(left, radius);
        assertEquals(ROWS_W, left + SUMMARY_GAP + bar + SUMMARY_GAP + radius);
        assertEquals(0, barWidth(ROWS_W, ROWS_W));
        assertEquals(50, barFill(100, 16, 32));
        assertEquals(100, barFill(100, 30, 24));
        assertEquals(0, barFill(100, 0, 0));
    }

    @Test
    void signalByState() {
        assertEquals(SIGNAL_ERROR, signal(DockStatus.State.NO_PROVIDER));
        assertEquals(SIGNAL_ERROR, signal(DockStatus.State.NO_MACHINES));
        assertEquals(SIGNAL_ERROR, signal(DockStatus.State.CRAFTING_PATTERN));
        assertEquals(SIGNAL_ERROR, signal(DockStatus.State.MIXED));
        assertEquals(SIGNAL_WARN, signal(DockStatus.State.PROVIDER_FULL));
        assertEquals(SIGNAL_WARN, signal(DockStatus.State.ALL_FULL));
        assertEquals(SIGNAL_OK, signal(DockStatus.State.WORKING));
        assertEquals(SIGNAL_IDLE, signal(DockStatus.State.READY));
        assertEquals(SIGNAL_IDLE, signal(DockStatus.State.FREE));
    }
}
