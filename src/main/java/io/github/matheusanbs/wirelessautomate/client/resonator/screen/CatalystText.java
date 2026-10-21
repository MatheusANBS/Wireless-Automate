package io.github.matheusanbs.wirelessautomate.client.resonator.screen;

import static io.github.matheusanbs.wirelessautomate.client.resonator.screen.CoreScreenLayout.CRYSTALS_LABEL;
import static io.github.matheusanbs.wirelessautomate.client.resonator.screen.CoreScreenLayout.REDSTONE_SLOT;

import io.github.matheusanbs.wirelessautomate.block.RouterTier;
import io.github.matheusanbs.wirelessautomate.client.GuiPaint;
import io.github.matheusanbs.wirelessautomate.client.GuiText;
import io.github.matheusanbs.wirelessautomate.menu.ResonatorMenu;
import io.github.matheusanbs.wirelessautomate.packet.ResonatorStatePayload;
import io.github.matheusanbs.wirelessautomate.registry.ModItems;
import io.github.matheusanbs.wirelessautomate.resonator.CatalystRule;
import io.github.matheusanbs.wirelessautomate.resonator.CoreCatalyst;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.jetbrains.annotations.Nullable;

/**
 * O rótulo e a dica do slot do catalisador, pelo tier da tela: "Redstone" no Básico e no Avançado, "Pó de
 * Ressonância" no Elite e na Esmeralda e o Pó Carregado do tier do Allthemodium para cima; com um item no slot, o nome
 * dele. O catalisador que o tier não aceita mais (a redstone de um Núcleo Elite gravado antes da regra, ou o Pó de um
 * Núcleo que subiu) é "antigo": o Núcleo gasta até acabar e não aceita mais dele.
 */
final class CatalystText {
    private final ResonatorMenu menu;
    private int left;
    private int top;

    CatalystText(ResonatorMenu menu) {
        this.menu = menu;
    }

    void layout(int x, int y) {
        left = x;
        top = y;
    }

    void render(GuiGraphics g, Font font) {
        int textX = left + REDSTONE_SLOT.x() + REDSTONE_SLOT.w() + 4;
        int textW = CRYSTALS_LABEL.x() + CRYSTALS_LABEL.w() - (REDSTONE_SLOT.x() + REDSTONE_SLOT.w() + 4);
        int textY = top + REDSTONE_SLOT.y();
        ItemStack held = held();
        boolean old = old(held);
        Component label = old && held.is(Items.REDSTONE) ? ResonatorText.key("catalyst.old_redstone")
                : name(held.isEmpty() ? expected() : held.getItem());
        GuiText.draw(g, font, label, textX, textY, textW, GuiPaint.MUTED);
        GuiText.draw(g, font, ResonatorText.key(old ? "catalyst.old" : "redstone.hint"), textX, textY + 10, textW,
                old ? GuiPaint.WARN : GuiPaint.MUTED);
    }

    @Nullable List<Component> tooltip() {
        ResonatorStatePayload state = menu.state();
        if (state == null) {
            return null;
        }
        ItemStack held = held();
        List<Component> lines = new ArrayList<>();
        lines.add((held.isEmpty() ? expected() : held.getItem()).getDescription());
        lines.add(ResonatorText.muted(ResonatorText.key("redstone.tooltip.time", seconds(state.rechargeTicks()))));
        if (old(held)) {
            lines.add(ResonatorText.accent(ResonatorText.key("catalyst.old.tooltip", accepted())));
        } else {
            lines.add(ResonatorText.muted(ResonatorText.key("catalyst.accepts", accepted())));
        }
        return lines;
    }

    private ItemStack held() {
        return menu.getSlot(menu.redstoneSlot()).getItem();
    }

    /** Se o item no slot é de antes: o tier da tela não o aceita mais. */
    private boolean old(ItemStack held) {
        return !held.isEmpty() && !CoreCatalyst.accepts(menu.tier(), held);
    }

    /** O nome curto no rótulo: "Redstone" para o pó de redstone, o nome do item para os Pós. */
    private static Component name(Item item) {
        return item == Items.REDSTONE ? ResonatorText.key("redstone") : item.getDescription();
    }

    /** O catalisador que o rótulo mostra com o slot vazio. */
    private Item expected() {
        RouterTier tier = menu.tier();
        if (CatalystRule.needsCharged(tier.ordinal())) {
            return ModItems.CHARGED_DUSTS.get(tier).get();
        }
        return tier.ordinal() < CatalystRule.FIRST_DUST_ONLY ? Items.REDSTONE : ModItems.RESONANCE_DUST.get();
    }

    /** O que o slot aceita: no Básico e no Avançado, a redstone ou o Pó; acima, só o {@link #expected}. */
    private Component accepted() {
        if (menu.tier().ordinal() < CatalystRule.FIRST_DUST_ONLY) {
            return ResonatorText.key("catalyst.either", Items.REDSTONE.getDescription(),
                    ModItems.RESONANCE_DUST.get().getDescription());
        }
        return expected().getDescription();
    }

    static String seconds(int ticks) {
        return ticks % 20 == 0 ? Integer.toString(ticks / 20) : String.format(Locale.ROOT, "%.1f", ticks / 20.0);
    }
}
