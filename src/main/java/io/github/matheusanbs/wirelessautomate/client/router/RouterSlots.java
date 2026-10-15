package io.github.matheusanbs.wirelessautomate.client.router;

import io.github.matheusanbs.wirelessautomate.Config;
import io.github.matheusanbs.wirelessautomate.block.RouterTier;
import io.github.matheusanbs.wirelessautomate.chunk.ChunkLoadState;
import io.github.matheusanbs.wirelessautomate.client.GuiPaint;
import io.github.matheusanbs.wirelessautomate.client.GuiText;
import io.github.matheusanbs.wirelessautomate.client.ResourceStyle;
import io.github.matheusanbs.wirelessautomate.menu.RouterFaceFilterTarget;
import io.github.matheusanbs.wirelessautomate.menu.RouterMenu;
import io.github.matheusanbs.wirelessautomate.network.ResourceType;
import io.github.matheusanbs.wirelessautomate.registry.ModItems;
import java.util.Locale;
import java.util.function.Supplier;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

/**
 * Os slots da tela do roteador, desenhados e explicados: os de Cartão de Filtro da face escolhida (ou, num
 * tipo sem filtro, a vazão do tier no lugar deles), os de chunk loading (com a luz do estado) e de alcance
 * no canto do cabeçalho, e o fundo do inventário do jogador. Os slots em si são do {@link RouterMenu}.
 */
final class RouterSlots {
    /** Cartão desenhado apagado num slot de cartão vazio. */
    private static final ItemStack GHOST_CARD = new ItemStack(ModItems.FILTER_CARD.get());
    /** Upgrade desenhado apagado no slot de upgrade vazio. */
    private static final ItemStack GHOST_UPGRADE = new ItemStack(ModItems.CHUNK_LOADER_UPGRADE.get());
    /** Upgrade de Alcance desenhado apagado no slot de alcance vazio. */
    private static final ItemStack GHOST_RANGE = new ItemStack(ModItems.RANGE_UPGRADE.get());

    private final RouterMenu menu;
    private final RouterLayout layout;
    private final Supplier<ResourceType> type;

    RouterSlots(RouterMenu menu, RouterLayout layout, Supplier<ResourceType> type) {
        this.menu = menu;
        this.layout = layout;
        this.type = type;
    }

    private static Component tr(String key, Object... args) {
        return Component.translatable("gui.wirelessautomate.router." + key, args);
    }

    // ------------------------------------------------------------------ dicas

    private static boolean isCardSlot(@Nullable Slot slot) {
        return slot != null && slot.index < RouterMenu.CARD_SLOT_COUNT;
    }

    private static boolean isUpgradeSlot(@Nullable Slot slot) {
        return slot != null && slot.index == RouterMenu.UPGRADE_SLOT;
    }

    private static boolean isRangeSlot(@Nullable Slot slot) {
        return slot != null && slot.index == RouterMenu.RANGE_SLOT;
    }

    /** A dica do slot sob o mouse sem nada na mão (cartão vazio, upgrade ou alcance), ou {@code null}. */
    @Nullable Component tooltip(@Nullable Slot hovered) {
        if (isCardSlot(hovered) && !hovered.hasItem() && menu.getCarried().isEmpty()) {
            return cardSlotTooltip();
        }
        if (isUpgradeSlot(hovered) && menu.getCarried().isEmpty()) {
            return upgradeSlotTooltip();
        }
        if (isRangeSlot(hovered) && menu.getCarried().isEmpty()) {
            return rangeSlotTooltip();
        }
        return null;
    }

    /**
     * Dica do slot de alcance: vazio, o que ele faz neste tier (ou que o tier não aceita) e como pôr;
     * com o upgrade, o nome, se vale e como tirar. Raio e limite pela config sincronizada do servidor.
     */
    private Component rangeSlotTooltip() {
        RouterTier tier = menu.snapshot().tier();
        int radius = Config.machineRadius(tier);
        int max = Config.maxMachines(tier);
        if (!menu.getSlot(RouterMenu.RANGE_SLOT).hasItem()) {
            Component tip = tr("range.tooltip");
            if (radius <= 0) {
                return tip.copy().append("\n").append(tr("range.tooltip.unavailable").copy().withColor(GuiPaint.WARN));
            }
            return tip.copy()
                    .append("\n").append(tr("range.tooltip.effect", radius, max).copy().withColor(GuiPaint.TOOLTIP_MUTED))
                    .append("\n").append(tr("upgrade.tooltip.add").copy().withColor(GuiPaint.TOOLTIP_MUTED));
        }
        Component state = radius > 0
                ? tr("range.state.active", radius, max).copy().withColor(GuiPaint.OK)
                : tr("range.state.inactive").copy().withColor(GuiPaint.WARN);
        return GHOST_RANGE.getHoverName().copy()
                .append("\n").append(state)
                .append("\n").append(tr("upgrade.tooltip.remove").copy().withColor(GuiPaint.TOOLTIP_MUTED));
    }

    /** Estado do upgrade: o do snapshot, ou "sem upgrade" se o slot está vazio (a sincronização do slot chega antes). */
    private ChunkLoadState chunkLoadState() {
        return menu.getSlot(RouterMenu.UPGRADE_SLOT).hasItem() ? menu.snapshot().chunkLoad() : ChunkLoadState.NONE;
    }

    private static int chunkLoadColor(ChunkLoadState state) {
        return switch (state) {
            case ACTIVE -> GuiPaint.OK;
            case LIMIT -> GuiPaint.WARN;
            case DISABLED -> GuiPaint.DANGER;
            case NONE -> GuiPaint.MUTED;
        };
    }

    /**
     * Dica do slot de upgrade: vazio, o que ele faz e como pôr; com o upgrade, o nome, o estado e
     * como tirar.
     */
    private Component upgradeSlotTooltip() {
        ChunkLoadState state = chunkLoadState();
        if (state == ChunkLoadState.NONE) {
            return tr("upgrade.tooltip").copy()
                    .append("\n").append(tr("upgrade.tooltip.effect").copy().withColor(GuiPaint.TOOLTIP_MUTED))
                    .append("\n").append(tr("upgrade.tooltip.add").copy().withColor(GuiPaint.TOOLTIP_MUTED));
        }
        String key = "upgrade.state." + state.name().toLowerCase(Locale.ROOT);
        return GHOST_UPGRADE.getHoverName().copy()
                .append("\n").append(tr(key).copy().withColor(chunkLoadColor(state)))
                .append("\n").append(tr(key + ".detail").copy().withColor(GuiPaint.TOOLTIP_MUTED))
                .append("\n").append(tr("upgrade.tooltip.remove").copy().withColor(GuiPaint.TOOLTIP_MUTED));
    }

    /** Dica de um slot de cartão vazio: o tipo, a regra do conjunto e como pôr um cartão. */
    private Component cardSlotTooltip() {
        return tr("cards.tooltip", ResourceStyle.name(type.get())).copy()
                .append("\n").append(tr("cards.tooltip.rule").copy().withColor(GuiPaint.TOOLTIP_MUTED))
                .append("\n").append(tr("cards.tooltip.add").copy().withColor(GuiPaint.TOOLTIP_MUTED));
    }

    // ------------------------------------------------------------------ desenho

    /** Os slots de chunk loading (com a luz do estado) e de alcance, no canto do cabeçalho. */
    void renderUpgradeSlots(GuiGraphics g, int x, int y) {
        int ux = x + layout.upgradeBgX();
        int uy = y + RouterLayout.UPGRADE_BG_Y;
        GuiPaint.slot(g, ux, uy);
        ChunkLoadState chunkLoad = chunkLoadState();
        if (chunkLoad == ChunkLoadState.NONE) {
            ghostItem(g, GHOST_UPGRADE, ux + 1, uy + 1);
        } else {
            int color = chunkLoadColor(chunkLoad);
            g.pose().pushPose();
            g.pose().translate(0, 0, 300);
            g.fill(ux + 11, uy + 1, ux + 17, uy + 7, GuiPaint.RING);
            g.fill(ux + 12, uy + 2, ux + 16, uy + 6, color);
            g.fill(ux + 12, uy + 2, ux + 14, uy + 4, GuiPaint.mix(color, GuiPaint.BEVEL_LIGHT, 0.5f));
            g.pose().popPose();
        }
        int rangeX = x + layout.rangeBgX();
        GuiPaint.slot(g, rangeX, uy);
        if (!menu.getSlot(RouterMenu.RANGE_SLOT).hasItem()) {
            ghostItem(g, GHOST_RANGE, rangeX + 1, uy + 1);
        }
    }

    /**
     * Os slots de cartão da face escolhida e, ao lado, de qual face são ({@code faceSide}, a chave do lado
     * relativo); num tipo sem filtro, no lugar deles, a vazão do tier. O texto só aparece se couber inteiro
     * em duas linhas.
     */
    void renderCards(GuiGraphics g, Font font, int x, int y, String faceSide) {
        ResourceType type = this.type.get();
        int cy = y + layout.cardsBgY();
        int textX = x + RouterLayout.X0 + RouterMenu.CARD_SLOT_COUNT * 18 + 5;
        int room = x + RouterLayout.X0 + layout.lw() - textX;
        Component text;
        if (RouterFaceFilterTarget.hasFilter(type)) {
            for (int i = 0; i < RouterMenu.CARD_SLOT_COUNT; i++) {
                int sx = x + RouterLayout.X0 + i * 18;
                GuiPaint.slot(g, sx, cy);
                if (!menu.getSlot(i).hasItem()) {
                    ghostItem(g, GHOST_CARD, sx + 1, cy + 1);
                }
            }
            text = tr("cards.label", tr("side." + faceSide));
        } else {
            textX = x + RouterLayout.X0;
            room = layout.lw();
            long tierRate = Config.TIERS.get(menu.snapshot().tier()).rate(type);
            text = tr("rate.tier", tierRate == 0 ? tr("rate.unlimited") : ResourceStyle.rate(type, tierRate));
        }
        int lines = font.split(text, Math.max(1, room)).size();
        if (room > 0 && lines <= 2) {
            GuiText.wrap(g, font, text, textX, cy + (lines == 1 ? 5 : 0), room, 2, GuiPaint.MUTED);
        }
    }

    /** O fundo do inventário do jogador, colado na borda de baixo. */
    void renderInventory(GuiGraphics g, int x, int y) {
        int invX = x + layout.inventoryX();
        int invY = y + layout.inventoryY();
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                GuiPaint.slot(g, invX - 1 + col * 18, invY - 1 + row * 18);
            }
        }
        for (int col = 0; col < 9; col++) {
            GuiPaint.slot(g, invX - 1 + col * 18, invY + 57);
        }
    }

    /** Item apagado no fundo de um slot vazio (cartão ou upgrade). */
    private static void ghostItem(GuiGraphics g, ItemStack ghost, int x, int y) {
        g.renderFakeItem(ghost, x, y);
        g.pose().pushPose();
        g.pose().translate(0, 0, 250);
        g.fill(x, y, x + 16, y + 16, GuiPaint.alpha(GuiPaint.INSET, 0xD8));
        g.pose().popPose();
    }

    /** Centro do slot {@code index} do menu, na tela. */
    int[] slotCenter(int left, int top, int index) {
        Slot s = menu.getSlot(index);
        return new int[] {left + s.x + 8, top + s.y + 8};
    }
}
