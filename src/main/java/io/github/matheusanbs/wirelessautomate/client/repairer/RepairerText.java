package io.github.matheusanbs.wirelessautomate.client.repairer;

import static io.github.matheusanbs.wirelessautomate.client.repairer.RepairerLayout.STATUS;

import io.github.matheusanbs.wirelessautomate.client.GuiPaint;
import io.github.matheusanbs.wirelessautomate.client.GuiText;
import io.github.matheusanbs.wirelessautomate.client.GuiText.Fitted;
import io.github.matheusanbs.wirelessautomate.item.TierCoreItem;
import io.github.matheusanbs.wirelessautomate.menu.RepairerMenu;
import io.github.matheusanbs.wirelessautomate.packet.RepairerStatePayload;
import io.github.matheusanbs.wirelessautomate.repairer.RepairMath;
import io.github.matheusanbs.wirelessautomate.repairer.RepairerSlots;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import org.jetbrains.annotations.Nullable;

/**
 * Os textos da tela do Reparador: as chaves, a linha de estado (o que o Reparador faz ou o que falta, com a bolinha na
 * cor do caso) e as dicas dos slots vazios e das entradas. A linha de estado só é medida de novo quando o caso muda.
 */
final class RepairerText {
    static final int IDLE = 0;
    static final int RUNNING = 1;
    static final int NO_ENERGY = 2;
    static final int NO_CRYSTALS = 3;
    static final int OUTPUT_FULL = 4;
    private static final String PREFIX = "gui.wirelessautomate.repairer.";

    private final RepairerMenu menu;
    private int left;
    private int top;
    private int shownStatus = -1;
    private int shownRunning = -1;
    private int shownNeeded = -1;
    private long shownCost = -1;
    private @Nullable Fitted statusText;

    RepairerText(RepairerMenu menu) {
        this.menu = menu;
    }

    static MutableComponent key(String key, Object... args) {
        return Component.translatable(PREFIX + key, args);
    }

    static Component muted(Component line) {
        return line.copy().withColor(GuiPaint.TOOLTIP_MUTED);
    }

    static Component accent(Component line) {
        return line.copy().withColor(GuiPaint.TOOLTIP_ACCENT);
    }

    void layout(int x, int y) {
        left = x;
        top = y;
    }

    /** As linhas com fim marcado que ainda andam (as travadas não contam). */
    static int running(@Nullable RepairerStatePayload state) {
        int running = 0;
        if (state != null) {
            for (int i = 0; i < RepairMath.LINES; i++) {
                if (state.ends()[i] > 0 && !state.blocked()[i]) {
                    running++;
                }
            }
        }
        return running;
    }

    /**
     * O caso da linha de estado: a saída cheia primeiro (uma linha travada), depois o que falta a um rachado que espera
     * (cristais cheios, energia), depois reparando ou parado.
     */
    static int status(RepairerMenu menu) {
        RepairerStatePayload state = menu.state();
        if (state == null) {
            return IDLE;
        }
        boolean waiting = false;
        for (int i = 0; i < RepairMath.LINES; i++) {
            if (state.blocked()[i]) {
                return OUTPUT_FULL;
            }
            waiting |= state.ends()[i] == 0 && menu.getSlot(i).hasItem();
        }
        if (waiting && menu.getSlot(RepairerSlots.FEED).getItem().getCount() < state.needed()) {
            return NO_CRYSTALS;
        }
        if (waiting && state.energy() < state.cost0()) {
            return NO_ENERGY;
        }
        return running(state) > 0 ? RUNNING : IDLE;
    }

    /** Sem energia para o próximo reparo, ou o buffer vazio com nada andando: a câmara escurece. */
    static boolean powerless(RepairerMenu menu) {
        RepairerStatePayload state = menu.state();
        return state != null && (status(menu) == NO_ENERGY || state.energy() <= 0 && running(state) == 0);
    }

    /** A linha de estado afundada: a bolinha na cor do caso e o texto, cortado com dica se não couber. */
    void render(GuiGraphics g, Font font) {
        int status = status(menu);
        RepairerStatePayload state = menu.state();
        int running = running(state);
        int needed = state == null ? 0 : state.needed();
        long cost = state == null ? 0 : state.cost0();
        if (statusText == null || status != shownStatus || running != shownRunning || needed != shownNeeded
                || cost != shownCost) {
            shownStatus = status;
            shownRunning = running;
            shownNeeded = needed;
            shownCost = cost;
            statusText = GuiText.fit(font, statusLine(status, running, needed, cost), STATUS.w() - 18);
        }
        int x = left + STATUS.x();
        int y = top + STATUS.y();
        GuiPaint.inset(g, x, y, STATUS.w(), STATUS.h());
        GuiPaint.dot(g, x + 4, y + 4, statusColor(status));
        GuiText.draw(g, font, statusText, x + 14, y + 3, GuiPaint.FG);
    }

    private static Component statusLine(int status, int running, int needed, long cost) {
        return switch (status) {
            case RUNNING -> key("status.running", running, RepairMath.LINES);
            case NO_ENERGY -> key("status.no_energy", TierCoreItem.grouped(cost));
            case NO_CRYSTALS -> key("status.no_crystals", needed);
            case OUTPUT_FULL -> key("status.output_full");
            default -> key("status.idle");
        };
    }

    private static int statusColor(int status) {
        return switch (status) {
            case RUNNING -> GuiPaint.OK;
            case NO_ENERGY -> GuiPaint.DANGER;
            case NO_CRYSTALS, OUTPUT_FULL -> GuiPaint.WARN;
            default -> GuiPaint.MUTED;
        };
    }

    /**
     * A dica de um slot do Reparador vazio, ou {@code null} (o slot com item fica com a dica do item, mais a linha da
     * entrada pelo {@link #inputLines}).
     */
    @Nullable List<Component> emptySlotTooltip(int index) {
        RepairerStatePayload state = menu.state();
        int needed = state == null ? 0 : state.needed();
        if (RepairerSlots.isInput(index)) {
            return List.of(key("slot.input"), muted(key("slot.input.accepts")));
        }
        if (index == RepairerSlots.FEED) {
            return List.of(key("slot.feed"), muted(key("slot.feed.accepts", needed)));
        }
        if (RepairerSlots.isOutput(index)) {
            return List.of(key("slot.output"));
        }
        return null;
    }

    /** As linhas a somar à dica do cristal numa entrada: andando, travada ou na fila. */
    List<Component> inputLines(int line, long gameTime) {
        List<Component> lines = new ArrayList<>(2);
        RepairerStatePayload state = menu.state();
        if (state == null) {
            return lines;
        }
        if (state.blocked()[line]) {
            lines.add(accent(key("slot.input.blocked")));
        } else if (state.ends()[line] > 0) {
            lines.add(muted(key("slot.input.running", menu.progress(line, gameTime) / 10)));
            lines.add(muted(key("slot.input.locked")));
        } else {
            lines.add(muted(key("slot.input.waiting", state.needed())));
        }
        return lines;
    }

    /** A dica do medidor de energia, com os números inteiros. */
    @Nullable List<Component> energyTooltip() {
        RepairerStatePayload state = menu.state();
        if (state == null) {
            return null;
        }
        return List.of(key("energy.tooltip", TierCoreItem.grouped(state.energy()), TierCoreItem.grouped(state.capacity())),
                muted(key("energy.tooltip.hint")));
    }
}
