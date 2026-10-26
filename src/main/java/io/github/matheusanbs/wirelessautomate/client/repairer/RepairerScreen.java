package io.github.matheusanbs.wirelessautomate.client.repairer;

import static io.github.matheusanbs.wirelessautomate.client.repairer.RepairerLayout.H;
import static io.github.matheusanbs.wirelessautomate.client.repairer.RepairerLayout.INVENTORY_LABEL;
import static io.github.matheusanbs.wirelessautomate.client.repairer.RepairerLayout.STATUS;
import static io.github.matheusanbs.wirelessautomate.client.repairer.RepairerLayout.THEME_X;
import static io.github.matheusanbs.wirelessautomate.client.repairer.RepairerLayout.THEME_Y;
import static io.github.matheusanbs.wirelessautomate.client.repairer.RepairerLayout.W;

import io.github.matheusanbs.wirelessautomate.client.GuiPaint;
import io.github.matheusanbs.wirelessautomate.client.GuiText;
import io.github.matheusanbs.wirelessautomate.client.GuiText.Fitted;
import io.github.matheusanbs.wirelessautomate.client.ThemeToggle;
import io.github.matheusanbs.wirelessautomate.menu.RepairerMenu;
import io.github.matheusanbs.wirelessautomate.repairer.RepairerSlots;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

/**
 * A tela do Reparador de Cristais, 352 × 256 de tamanho fixo: o cabeçalho com o medidor de energia, a câmara com os 8
 * pedestais à esquerda, os slots (rachados, estoque e reparados) à direita, a linha de estado e o inventário embaixo.
 * Esta classe só junta as partes: cada uma desenha e dá a dica da sua área. O progresso vem do relógio do mundo e do
 * fim de cada linha, sem pacote por tick.
 */
public class RepairerScreen extends AbstractContainerScreen<RepairerMenu> {
    private final RepairerHeader header;
    private final RepairerChamberPanel chamber;
    private final RepairerSlotsPaint slotsPaint;
    private final RepairerText text;
    private @Nullable Fitted inventoryLabel;

    public RepairerScreen(RepairerMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        this.imageWidth = W;
        this.imageHeight = H;
        this.header = new RepairerHeader(menu, title);
        this.chamber = new RepairerChamberPanel(menu);
        this.slotsPaint = new RepairerSlotsPaint(menu);
        this.text = new RepairerText(menu);
    }

    @Override
    protected void init() {
        super.init();
        header.layout(leftPos, topPos, font);
        chamber.layout(leftPos, topPos);
        slotsPaint.layout(leftPos, topPos, font);
        text.layout(leftPos, topPos);
        inventoryLabel = GuiText.fit(font, RepairerText.key("inventory"), INVENTORY_LABEL.w());
    }

    public int themeX() {
        return leftPos + THEME_X;
    }

    public int themeY() {
        return topPos + THEME_Y;
    }

    private static long gameTime() {
        Minecraft minecraft = Minecraft.getInstance();
        return minecraft.level == null ? 0 : minecraft.level.getGameTime();
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        GuiText.beginFrame();
        super.render(g, mouseX, mouseY, partialTick);
        if (ThemeToggle.hovered(themeX(), themeY(), mouseX, mouseY)) {
            g.renderComponentTooltip(font, ThemeToggle.tooltip(), mouseX, mouseY);
            return;
        }
        if (!menu.getCarried().isEmpty()) {
            return;
        }
        List<Component> tip = partTooltip(mouseX, mouseY);
        if (tip != null) {
            g.renderComponentTooltip(font, tip, mouseX, mouseY);
            return;
        }
        if (hoveredSlot != null && hoveredSlot.hasItem()) {
            renderTooltip(g, mouseX, mouseY);
            return;
        }
        Component clipped = GuiText.clipAt(mouseX, mouseY);
        if (clipped != null) {
            g.renderComponentTooltip(font, List.of(clipped), mouseX, mouseY);
        }
    }

    /** A dica do medidor, de um slot vazio do Reparador ou de uma entrada com cristal (a do item mais a da linha). */
    private @Nullable List<Component> partTooltip(int mouseX, int mouseY) {
        if (header.energyHovered(mouseX, mouseY)) {
            return text.energyTooltip();
        }
        int index = slotsPaint.slotAt(mouseX, mouseY);
        if (index < 0) {
            return null;
        }
        ItemStack stack = menu.getSlot(index).getItem();
        if (stack.isEmpty()) {
            return text.emptySlotTooltip(index);
        }
        if (!RepairerSlots.isInput(index)) {
            return null;
        }
        List<Component> lines = new ArrayList<>(getTooltipFromContainerItem(stack));
        lines.addAll(text.inputLines(index, gameTime()));
        return lines;
    }

    @Override
    protected void renderLabels(GuiGraphics g, int mouseX, int mouseY) {
        // os textos são desenhados em renderBg, em coordenadas da tela
    }

    @Override
    protected void renderBg(GuiGraphics g, float partialTick, int mouseX, int mouseY) {
        int x = leftPos;
        int y = topPos;
        long now = gameTime();
        GuiPaint.panel(g, x, y, W, H, GuiPaint.ACCENT);
        header.render(g, font);
        ThemeToggle.render(g, themeX(), themeY(), mouseX, mouseY);
        chamber.render(g, font, now);
        slotsPaint.render(g, font);
        text.render(g, font);
        GuiText.draw(g, font, inventoryLabel, x + INVENTORY_LABEL.x(), y + INVENTORY_LABEL.y(), GuiPaint.MUTED);
        for (int i = RepairerMenu.INVENTORY_START; i < menu.slots.size(); i++) {
            Slot slot = menu.getSlot(i);
            GuiPaint.slot(g, x + slot.x - 1, y + slot.y - 1);
        }
    }

    @Override
    protected void renderSlot(GuiGraphics g, Slot slot) {
        super.renderSlot(g, slot);
        slotsPaint.renderBar(g, slot, gameTime());
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (ThemeToggle.mouseClicked(themeX(), themeY(), mouseX, mouseY, button)) {
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    public int[] themeCenter() {
        return new int[] {themeX() + ThemeToggle.SIZE / 2, themeY() + ThemeToggle.SIZE / 2};
    }

    /** O centro de um slot do Reparador (0 a 16); gancho do e2e e das capturas. */
    public int[] repairerSlotCenter(int index) {
        return slotsPaint.slotCenter(index);
    }

    /** O centro da linha de estado; gancho do e2e. */
    public int[] statusCenter() {
        return new int[] {leftPos + STATUS.x() + STATUS.w() / 2, topPos + STATUS.y() + STATUS.h() / 2};
    }
}
