package io.github.matheusanbs.wirelessautomate.client.router;

import static io.github.matheusanbs.wirelessautomate.client.router.MachineTexts.exceptions;
import static io.github.matheusanbs.wirelessautomate.client.router.MachineTexts.groupName;
import static io.github.matheusanbs.wirelessautomate.client.router.MachineTexts.tr;

import io.github.matheusanbs.wirelessautomate.client.GuiPaint;
import io.github.matheusanbs.wirelessautomate.client.GuiText;
import io.github.matheusanbs.wirelessautomate.menu.RangeView;
import io.github.matheusanbs.wirelessautomate.menu.RangeView.GroupView;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

/** O desenho da linha de um grupo na lista das máquinas ({@link RangeList}). */
final class GroupRow {
    private GroupRow() {
    }

    /**
     * Um grupo, numa linha: a seta, o ícone do bloco a 3/4, "Nome ×N" e, à direita, o que merece aviso: "1 trocada" ou
     * as exceções (o perfil comum não diz nada). {@code id} é o grupo, {@code open} se ele está aberto, {@code x} a
     * borda esquerda da lista, {@code nameRight} até onde vai o texto (antes dos botões da linha escolhida) e
     * {@code right} a borda direita da linha.
     */
    static void render(GuiGraphics g, Font font, RangeView view, int id, boolean open, int x, int ry, int nameRight,
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
        Component name = tr("range.group.count", groupName(group), group.count());
        int replaced = RangeRowButtons.replaced(view, group.id());
        Component detail = replaced > 0 ? replaced == 1 ? tr("range.group.replaced.one") : tr("range.group.replaced", replaced)
                : group.exceptions() > 0 ? exceptions(group) : null;
        int color = replaced > 0 ? GuiPaint.BRASS : GuiPaint.ACCENT;
        int detailW = detail == null ? 0 : font.width(detail) + 4;
        // com os botões da linha escolhida, o aviso sai (ele vai na dica); sem eles, vai à direita
        boolean buttonsOnRow = nameRight < right;
        // o aviso só entra se o nome continua inteiro (senão ele vai só na dica)
        if (detail != null && !buttonsOnRow && textX + font.width(name) + detailW + 2 <= right) {
            GuiText.draw(g, font, detail, right - detailW + 2, ry + 2, detailW - 4, color);
        } else {
            detailW = 0;
        }
        GuiText.draw(g, font, name, textX, ry + 2, nameRight - detailW - textX - 2, GuiPaint.FG);
    }
}
