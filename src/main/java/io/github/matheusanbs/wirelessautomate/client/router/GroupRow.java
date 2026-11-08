package io.github.matheusanbs.wirelessautomate.client.router;

import static io.github.matheusanbs.wirelessautomate.client.router.MachineTexts.exceptions;
import static io.github.matheusanbs.wirelessautomate.client.router.MachineTexts.groupName;
import static io.github.matheusanbs.wirelessautomate.client.router.MachineTexts.tr;

import io.github.matheusanbs.wirelessautomate.client.GuiPaint;
import io.github.matheusanbs.wirelessautomate.client.GuiText;
import io.github.matheusanbs.wirelessautomate.menu.RangeView;
import io.github.matheusanbs.wirelessautomate.menu.RangeView.GroupView;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

/**
 * O desenho da linha de um grupo na lista das máquinas ({@link RangeList}). Os textos e as larguras de cada grupo
 * ("Nome ×N", "1 trocada" ou as exceções) são montados uma vez por snapshot (a visão é outro objeto), como no
 * {@link MachineRow}: o quadro só compara larguras e desenha.
 */
final class GroupRow {
    /** Os textos de um grupo; {@code detail} é {@code null} quando não há aviso. */
    private record Texts(Component name, int nameW, @Nullable Component detail, int detailW, int color) {
    }

    private @Nullable RangeView cached;
    private final Int2ObjectMap<Texts> texts = new Int2ObjectOpenHashMap<>();

    private Texts texts(Font font, RangeView view, GroupView group) {
        if (view != cached) {
            cached = view;
            texts.clear();
        }
        Texts known = texts.get(group.id());
        if (known != null) {
            return known;
        }
        Component name = tr("range.group.count", groupName(group), group.count());
        int replaced = RangeRowButtons.replaced(view, group.id());
        Component detail = replaced > 0 ? replaced == 1 ? tr("range.group.replaced.one") : tr("range.group.replaced", replaced)
                : group.exceptions() > 0 ? exceptions(group) : null;
        Texts made = new Texts(name, font.width(name), detail, detail == null ? 0 : font.width(detail) + 4,
                replaced > 0 ? GuiPaint.BRASS : GuiPaint.ACCENT);
        texts.put(group.id(), made);
        return made;
    }

    /**
     * Um grupo, numa linha: a seta, o ícone do bloco a 3/4, "Nome ×N" e, à direita, o que merece aviso: "1 trocada" ou
     * as exceções (o perfil comum não diz nada). {@code id} é o grupo, {@code open} se ele está aberto, {@code x} a
     * borda esquerda da lista, {@code nameRight} até onde vai o texto (antes dos botões da linha escolhida) e
     * {@code right} a borda direita da linha.
     */
    void render(GuiGraphics g, Font font, RangeView view, int id, boolean open, int x, int ry, int nameRight,
            int right) {
        GroupView group = view.group(id);
        if (group == null) {
            return;
        }
        if (open) {
            GuiPaint.arrowDown(g, x + 3, ry + 5, GuiPaint.MUTED);
        } else {
            GuiPaint.arrowRight(g, x + 4, ry + 4, GuiPaint.MUTED);
        }
        ItemStack icon = GroupIcons.of(view, group);
        if (!icon.isEmpty()) {
            g.pose().pushPose();
            g.pose().translate(x + RangeList.ARROW_ZONE + 1, ry + 0.5f, 0);
            g.pose().scale(0.75f, 0.75f, 1f);
            g.renderItem(icon, 0, 0);
            g.pose().popPose();
        }
        int textX = x + RangeList.GROUP_TEXT_X;
        Texts row = texts(font, view, group);
        int detailW = row.detailW();
        // com os botões da linha escolhida, o aviso sai (ele vai na dica); sem eles, vai à direita
        boolean buttonsOnRow = nameRight < right;
        // o aviso só entra se o nome continua inteiro (senão ele vai só na dica)
        if (row.detail() != null && !buttonsOnRow && textX + row.nameW() + detailW + 2 <= right) {
            GuiText.draw(g, font, row.detail(), right - detailW + 2, ry + 2, detailW - 4, row.color());
        } else {
            detailW = 0;
        }
        GuiText.draw(g, font, row.name(), textX, ry + 2, nameRight - detailW - textX - 2, GuiPaint.FG);
    }
}
