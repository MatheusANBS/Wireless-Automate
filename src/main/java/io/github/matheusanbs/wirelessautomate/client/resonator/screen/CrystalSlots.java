package io.github.matheusanbs.wirelessautomate.client.resonator.screen;

import static io.github.matheusanbs.wirelessautomate.client.resonator.screen.CoreScreenLayout.CRYSTALS_LABEL;
import static io.github.matheusanbs.wirelessautomate.client.resonator.screen.CoreScreenLayout.REDSTONE_SLOT;

import io.github.matheusanbs.wirelessautomate.client.GuiPaint;
import io.github.matheusanbs.wirelessautomate.client.GuiText;
import io.github.matheusanbs.wirelessautomate.menu.ResonatorMenu;
import io.github.matheusanbs.wirelessautomate.packet.ResonatorStatePayload;
import io.github.matheusanbs.wirelessautomate.resonator.CrystalCharge;
import io.github.matheusanbs.wirelessautomate.resonator.SignalCrystalItem;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

/**
 * Os slots do Núcleo: o rótulo das câmaras, o fundo de cada câmara e da redstone (o tracejado na vazia), a barra da
 * recarga em latão sobre o Opaco que recarrega (o primeiro, como no servidor) e o rótulo da redstone. Os itens e a barra de carga dos cristais são do vanilla.
 */
final class CrystalSlots {
    private static final int BAR_W = 13;

    private final ResonatorMenu menu;
    private int left;
    private int top;

    CrystalSlots(ResonatorMenu menu) {
        this.menu = menu;
    }

    void layout(int x, int y) {
        left = x;
        top = y;
    }

    void render(GuiGraphics g, Font font, int mouseX, int mouseY, float partialTick) {
        int chambers = menu.chambers();
        Component label = chambers == 1 ? ResonatorText.key("crystals.one")
                : ResonatorText.key("crystals.many", chambers);
        GuiText.draw(g, font, label, left + CRYSTALS_LABEL.x(), top + CRYSTALS_LABEL.y(), CRYSTALS_LABEL.w(),
                GuiPaint.MUTED);
        for (int i = 0; i <= chambers; i++) {
            Slot slot = menu.getSlot(i);
            int x = left + slot.x - 1;
            int y = top + slot.y - 1;
            GuiPaint.slot(g, x, y);
            if (!slot.hasItem()) {
                dashed(g, x + 4, y + 4, 10, GuiPaint.alpha(GuiPaint.LINE, 160));
            }
        }
        int textX = left + REDSTONE_SLOT.x() + REDSTONE_SLOT.w() + 4;
        int textW = CRYSTALS_LABEL.x() + CRYSTALS_LABEL.w() - (REDSTONE_SLOT.x() + REDSTONE_SLOT.w() + 4);
        int textY = top + REDSTONE_SLOT.y();
        GuiText.draw(g, font, ResonatorText.key("redstone"), textX, textY, textW, GuiPaint.MUTED);
        GuiText.draw(g, font, ResonatorText.key("redstone.hint"), textX, textY + 10, textW, GuiPaint.MUTED);
    }

    /**
     * A barra da recarga sobre o Opaco, chamada pela anfitriã logo depois de o vanilla desenhar o item do slot (em
     * coordenadas do slot), para ficar por cima dele como a barra de um item.
     */
    void renderBar(GuiGraphics g, Slot slot) {
        ResonatorStatePayload state = menu.state();
        if (state == null || slot.container != menu.getSlot(0).container || slot.index != recharging()) {
            return;
        }
        int x = slot.x + 2;
        int y = slot.y + 13;
        int filled = state.rechargeTicks() <= 0 ? 0
                : Math.min(BAR_W, Math.round((float) BAR_W * state.recharge() / state.rechargeTicks()));
        g.pose().pushPose();
        g.pose().translate(0, 0, 300);
        g.fill(x, y, x + BAR_W, y + 2, GuiPaint.RING);
        g.fill(x, y, x + filled, y + 1, GuiPaint.BRASS);
        g.pose().popPose();
    }

    /** A dica de uma câmara ou da redstone, ou {@code null} fora delas (ou com um item no cursor). */
    @Nullable List<Component> tooltip(int mouseX, int mouseY) {
        if (!menu.getCarried().isEmpty()) {
            return null;
        }
        for (int i = 0; i <= menu.chambers(); i++) {
            Slot slot = menu.getSlot(i);
            int x = left + slot.x - 1;
            int y = top + slot.y - 1;
            if (mouseX >= x && mouseX < x + 18 && mouseY >= y && mouseY < y + 18) {
                return i == menu.redstoneSlot() ? redstoneTip() : chamberTip(i, slot.getItem());
            }
        }
        return null;
    }

    private @Nullable List<Component> redstoneTip() {
        ResonatorStatePayload state = menu.state();
        if (state == null) {
            return null;
        }
        return ResonatorText.tip(ResonatorText.key("redstone.tooltip"),
                ResonatorText.key("redstone.tooltip.time", seconds(state.rechargeTicks())));
    }

    private @Nullable List<Component> chamberTip(int index, ItemStack stack) {
        if (stack.isEmpty()) {
            return ResonatorText.tip(ResonatorText.key("chamber.empty"), ResonatorText.key("chamber.accepts"));
        }
        ResonatorStatePayload state = menu.state();
        if (state == null || !(stack.getItem() instanceof SignalCrystalItem crystal)) {
            return null;
        }
        List<Component> lines = new ArrayList<>();
        lines.add(stack.getHoverName());
        if (SignalCrystalItem.isDull(stack)) {
            if (index != recharging()) {
                lines.add(ResonatorText.muted(ResonatorText.key("chamber.waiting")));
                return lines;
            }
            int percent = state.rechargeTicks() <= 0 ? 0
                    : (int) Math.min(100, (long) state.recharge() * 100 / state.rechargeTicks());
            lines.add(ResonatorText.muted(ResonatorText.key("chamber.recharging", percent)));
            boolean redstone = menu.getSlot(menu.redstoneSlot()).hasItem();
            lines.add(redstone ? ResonatorText.muted(ResonatorText.key("chamber.with_redstone"))
                    : ResonatorText.accent(ResonatorText.key("chamber.no_redstone")));
            return lines;
        }
        lines.add(ResonatorText.muted(ResonatorText.key("chamber.charge",
                CrystalCharge.percent(SignalCrystalItem.charge(stack)))));
        int quarters = crystal.yieldQuarters(menu.tier());
        lines.add(ResonatorText.muted(ResonatorText.key("chamber.yield", multiplier(quarters),
                ResonatorText.fe(share(state.generation(), quarters)))));
        return lines;
    }

    /** A câmara do Opaco que recarrega: o primeiro, a mesma regra do servidor e da câmara; -1 sem Opaco. */
    private int recharging() {
        for (int i = 0; i < menu.chambers(); i++) {
            ItemStack stack = menu.getSlot(i).getItem();
            if (stack.getItem() instanceof SignalCrystalItem && SignalCrystalItem.isDull(stack)) {
                return i;
            }
        }
        return -1;
    }

    /**
     * A parte da geração de um cristal: {@code fePerCrystal} não chega ao cliente, então pela soma dos rendimentos em
     * quartos, pela mesma regra do servidor.
     */
    private long share(long generation, int quarters) {
        int sum = 0;
        for (int i = 0; i < menu.chambers(); i++) {
            ItemStack stack = menu.getSlot(i).getItem();
            if (stack.getItem() instanceof SignalCrystalItem crystal && !SignalCrystalItem.isDull(stack)
                    && !SignalCrystalItem.isCracked(stack)) {
                sum += crystal.yieldQuarters(menu.tier());
            }
        }
        return sum <= 0 ? 0 : Math.round((double) generation * quarters / sum);
    }

    /** O rendimento em quartos como multiplicador: 4 é "1", 2 é "0.5" e 1 é "0.25". */
    private static String multiplier(int quarters) {
        return quarters % 4 == 0 ? Integer.toString(quarters / 4) : Double.toString(quarters / 4.0);
    }

    private static String seconds(int ticks) {
        return ticks % 20 == 0 ? Integer.toString(ticks / 20) : String.format(Locale.ROOT, "%.1f", ticks / 20.0);
    }

    /** Um quadrado tracejado de 1 px (um pixel sim, um não) de {@code size} de lado. */
    private static void dashed(GuiGraphics g, int x, int y, int size, int color) {
        for (int i = 0; i < size; i += 2) {
            g.fill(x + i, y, x + i + 1, y + 1, color);
            g.fill(x + i + 1, y + size - 1, x + i + 2, y + size, color);
            g.fill(x, y + i + 1, x + 1, y + i + 2, color);
            g.fill(x + size - 1, y + i, x + size, y + i + 1, color);
        }
    }

    int[] slotCenter(int index) {
        Slot slot = menu.getSlot(index);
        return new int[] {left + slot.x + 8, top + slot.y + 8};
    }
}
