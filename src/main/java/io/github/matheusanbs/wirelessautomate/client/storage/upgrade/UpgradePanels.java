package io.github.matheusanbs.wirelessautomate.client.storage.upgrade;

import io.github.matheusanbs.wirelessautomate.client.GuiText;
import io.github.matheusanbs.wirelessautomate.item.ChestUpgradeItem;
import io.github.matheusanbs.wirelessautomate.menu.ChestUpgradeSlots;
import io.github.matheusanbs.wirelessautomate.menu.StorageListMenu;
import io.github.matheusanbs.wirelessautomate.packet.CompactingRulePayload;
import io.github.matheusanbs.wirelessautomate.packet.StorageActionPayload;
import io.github.matheusanbs.wirelessautomate.storage.StorageKind;
import io.github.matheusanbs.wirelessautomate.storage.upgrade.CompactingMode;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.world.item.Item;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.glfw.GLFW;

/**
 * Os upgrades na tela do Baú: a coluna dos slots à esquerda ({@link UpgradeColumn}) e o painel do upgrade escolhido
 * à direita ({@link CraftingPanelView} ou {@link CompactingPanelView}). É o anfitrião dos dois painéis: põe os slots do
 * menu no lugar, repassa os eventos e manda os pedidos ao servidor (Devolver, e as regras e o {@code WATCH}/
 * {@code UNWATCH} da Compactação, que o servidor só acompanha com o painel aberto). Fora do Baú (Tanques) não ocupa
 * espaço e não faz nada.
 */
public final class UpgradePanels {
    private final StorageListMenu<?> menu;
    private final @Nullable ChestUpgradeSlots slots;
    private final boolean preview;
    private final Runnable relayout;
    private final UpgradeColumn column;
    private final CraftingPanelView crafting;
    private final CompactingPanelView compacting;
    private final List<Rect2i> areas = new ArrayList<>();
    private int leftPos;
    private int topPos;

    /**
     * @param preview sem servidor (captura de desenvolvimento): nada é mandado
     * @param relayout refaz a posição da tela inteira, quando o painel aberto muda a largura
     */
    public UpgradePanels(StorageListMenu<?> menu, boolean preview, Runnable relayout) {
        this.menu = menu;
        this.slots = menu.upgradeSlots();
        this.preview = preview;
        this.relayout = relayout;
        var font = Minecraft.getInstance().font;
        this.column = new UpgradeColumn(menu);
        this.crafting = new CraftingPanelView(font, this::returnGrid);
        this.compacting = new CompactingPanelView(font, new CompactingPanelView.Actions() {
            @Override
            public void mode(CompactingMode mode) {
                send(CompactingRulePayload.mode(menu.containerId, mode.ordinal()));
            }

            @Override
            public void target(Item form) {
                send(CompactingRulePayload.target(menu.containerId, CompactingRulePayload.Kind.TARGET, form));
            }

            @Override
            public void clear(Item anyForm) {
                send(CompactingRulePayload.target(menu.containerId, CompactingRulePayload.Kind.CLEAR, anyForm));
            }

            @Override
            public void prefer(Item base) {
                send(CompactingRulePayload.target(menu.containerId, CompactingRulePayload.Kind.PREFER, base));
            }
        });
    }

    static MutableComponent tr(String key) {
        return Component.translatable("gui.wirelessautomate.upgrade." + key);
    }

    private ChestUpgradeItem.@Nullable Kind open() {
        return slots == null ? null : column.open();
    }

    /** Largura da coluna à esquerda do painel do Baú (zero fora do Baú). */
    public int extraLeft() {
        return slots == null ? 0 : UpgradeColumn.WIDTH;
    }

    /** Largura do painel aberto à direita, com a sombra (zero sem painel). */
    public int extraRight() {
        ChestUpgradeItem.Kind open = open();
        if (open == null) {
            return 0;
        }
        return (open == ChestUpgradeItem.Kind.CRAFTING ? CraftingPanelView.WIDTH : CompactingPanelView.WIDTH) + 2;
    }

    /** Põe a coluna, o painel e os slots do menu ao lado do painel do Baú em ({@code leftPos}, {@code topPos}). */
    public void layout(int leftPos, int topPos, int imageWidth, int imageHeight) {
        if (slots == null) {
            return;
        }
        this.leftPos = leftPos;
        this.topPos = topPos;
        column.layout(leftPos, topPos);
        int panelX = leftPos + imageWidth;
        int panelY = topPos + UpgradeColumn.TOP;
        int panelH = imageHeight - UpgradeColumn.TOP;
        crafting.layout(panelX, panelY);
        compacting.layout(panelX, panelY, panelH);
        boolean craftingOpen = open() == ChestUpgradeItem.Kind.CRAFTING;
        int hidden = ChestUpgradeSlots.HIDDEN;
        slots.place(UpgradeColumn.slotItemX(), UpgradeColumn.slotItemY(),
                craftingOpen ? crafting.gridItemX() - leftPos : hidden, craftingOpen ? crafting.gridItemY() - topPos : hidden,
                craftingOpen ? crafting.resultItemX() - leftPos : hidden, craftingOpen ? crafting.resultItemY() - topPos : hidden);
        areas.clear();
        areas.add(new Rect2i(leftPos - UpgradeColumn.WIDTH, panelY, UpgradeColumn.WIDTH, UpgradeColumn.HEIGHT + 2));
        if (open() != null) {
            int panelH2 = craftingOpen ? CraftingPanelView.HEIGHT : panelH;
            areas.add(new Rect2i(panelX - 1, panelY, extraRight() + 1, panelH2 + 2));
        }
    }

    /**
     * A cada tick da tela: acerta o painel aberto pelos upgrades nos slots e passa ao painel da Compactação o último
     * estado recebido (ele só remonta as linhas quando o estado é outro).
     */
    public void tick() {
        if (slots == null) {
            return;
        }
        ChestUpgradeItem.Kind before = column.open();
        if (column.sync()) {
            opened(before);
        }
        if (open() == ChestUpgradeItem.Kind.COMPACTING) {
            compacting.sync(slots.compactingPanel().state());
        }
    }

    /** O painel aberto mudou: avisa o servidor (a Compactação só é acompanhada aberta) e refaz a posição da tela. */
    private void opened(ChestUpgradeItem.@Nullable Kind before) {
        if (before == ChestUpgradeItem.Kind.COMPACTING) {
            send(CompactingRulePayload.of(menu.containerId, CompactingRulePayload.Kind.UNWATCH));
        }
        compacting.closePicker();
        if (open() == ChestUpgradeItem.Kind.COMPACTING) {
            send(CompactingRulePayload.of(menu.containerId, CompactingRulePayload.Kind.WATCH));
            compacting.sync(slots.compactingPanel().state());
        }
        relayout.run();
    }

    private void returnGrid() {
        send(new StorageActionPayload(menu.containerId, StorageKind.CHEST, StorageActionPayload.Action.RETURN_GRID));
    }

    private void send(CustomPacketPayload payload) {
        if (!preview) {
            PacketDistributor.sendToServer(payload);
        }
    }

    // ------------------------------------------------------------------ entrada

    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (slots == null) {
            return false;
        }
        ChestUpgradeItem.Kind before = column.open();
        if (column.mouseClicked(mouseX, mouseY, button)) {
            if (column.open() != before) {
                opened(before);
            }
            return true;
        }
        ChestUpgradeItem.Kind open = open();
        return open == ChestUpgradeItem.Kind.CRAFTING ? crafting.mouseClicked(mouseX, mouseY, button)
                : open == ChestUpgradeItem.Kind.COMPACTING && compacting.mouseClicked(mouseX, mouseY, button);
    }

    public boolean mouseDragged(double mouseY) {
        return open() == ChestUpgradeItem.Kind.COMPACTING && compacting.mouseDragged(mouseY);
    }

    public boolean mouseReleased() {
        return open() == ChestUpgradeItem.Kind.COMPACTING && compacting.mouseReleased();
    }

    public boolean mouseScrolled(double mouseX, double mouseY, double scrollY) {
        return open() == ChestUpgradeItem.Kind.COMPACTING && compacting.mouseScrolled(mouseX, mouseY, scrollY);
    }

    /** Esc fecha o seletor de formas, sem fechar a tela. */
    public boolean keyPressed(int keyCode) {
        return keyCode == GLFW.GLFW_KEY_ESCAPE && open() == ChestUpgradeItem.Kind.COMPACTING && compacting.escape();
    }

    /** O ponto está na coluna ou no painel aberto (clicar ali não joga o item do cursor no chão). */
    public boolean isInside(double mouseX, double mouseY) {
        if (slots == null) {
            return false;
        }
        ChestUpgradeItem.Kind open = open();
        return column.isInside(mouseX, mouseY)
                || open == ChestUpgradeItem.Kind.CRAFTING && crafting.isInside(mouseX, mouseY)
                || open == ChestUpgradeItem.Kind.COMPACTING && compacting.isInside(mouseX, mouseY);
    }

    // ------------------------------------------------------------------ desenho

    /** O fundo da coluna e do painel aberto, antes dos slots do menu. */
    public void renderBg(GuiGraphics g, int mouseX, int mouseY, int trim) {
        if (slots == null) {
            return;
        }
        column.render(g, mouseX, mouseY);
        ChestUpgradeItem.Kind open = open();
        if (open == ChestUpgradeItem.Kind.CRAFTING) {
            crafting.render(g, mouseX, mouseY, trim);
        } else if (open == ChestUpgradeItem.Kind.COMPACTING) {
            compacting.render(g, mouseX, mouseY, trim);
        }
    }

    /**
     * O que vai por cima dos slots (o seletor de formas), no {@code renderLabels} da tela, que vem com a origem no
     * canto do painel do Baú.
     */
    public void renderOver(GuiGraphics g, int mouseX, int mouseY) {
        if (open() != ChestUpgradeItem.Kind.COMPACTING) {
            return;
        }
        g.pose().pushPose();
        g.pose().translate(-leftPos, -topPos, 0);
        compacting.renderOver(g, mouseX, mouseY);
        g.pose().popPose();
    }

    /** A dica sob o mouse: texto cortado, aba, slot vazio, Devolver ou a lista da Compactação; senão {@code null}. */
    public @Nullable List<Component> tooltip(double mouseX, double mouseY) {
        if (slots == null) {
            return null;
        }
        Component clip = GuiText.clipAt(mouseX, mouseY);
        if (clip != null) {
            return List.of(clip);
        }
        Component simple = column.tooltip(mouseX, mouseY);
        ChestUpgradeItem.Kind open = open();
        if (simple == null && open == ChestUpgradeItem.Kind.CRAFTING) {
            simple = crafting.tooltip();
        }
        if (simple != null) {
            return List.of(simple);
        }
        return open == ChestUpgradeItem.Kind.COMPACTING ? compacting.tooltip(mouseX, mouseY) : null;
    }

    /** As áreas da coluna e do painel, para o JEI não pôr a lista dele por cima. */
    public List<Rect2i> areas() {
        return areas;
    }

    // ------------------------------------------------------------------ ganchos do e2e

    public ChestUpgradeItem.@Nullable Kind openKind() {
        return open();
    }

    public int[] tabCenter(int slot) {
        return column.tabCenter(slot);
    }

    public int[] returnCenter() {
        return crafting.returnCenter();
    }

    public int compactingRows() {
        return compacting.rowCount();
    }

    public int compactingRowOf(Item form) {
        return compacting.rowOf(form);
    }

    public int compactingTarget(int row) {
        return compacting.targetOf(row);
    }

    public int[] compactingTargetCenter(int row) {
        return compacting.targetCenter(row);
    }

    public int compactingVariants(int row) {
        return compacting.variantCount(row);
    }

    public int[] compactingVariantCenter(int row) {
        return compacting.variantCenter(row);
    }

    public boolean pickerOpen() {
        return compacting.pickerOpen();
    }

    public int pickerRow() {
        return compacting.pickerRow();
    }

    public int[] pickerCellCenter(int level) {
        return compacting.pickerCellCenter(level);
    }

    public int[] modeCenter(CompactingMode mode) {
        return compacting.modeCenter(mode);
    }
}
