package io.github.matheusanbs.wirelessautomate.client.tv.render;

import io.github.matheusanbs.wirelessautomate.tv.TvPartStyle;
import io.github.matheusanbs.wirelessautomate.tv.TvPiece;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * O Ícone: o item como no inventário (bloco em 3D, item chapado, brilho se ele tem), sem nome nem contagem, pelo
 * {@code ItemRenderer} do jogo na textura da Tela, escalado para o lado menor da caixa e centrado. Sem item ou com um
 * id que não existe (de um mod que saiu), nada.
 */
final class TvPaintIcon {
    private static final float ITEM_SIZE = 16f;

    private TvPaintIcon() {
    }

    static void paint(GuiGraphics g, TvPiece piece, TvPartStyle part) {
        if (part.item().isEmpty()) {
            return;
        }
        Item item = BuiltInRegistries.ITEM.get(part.item().get());
        if (item == Items.AIR) {
            return;
        }
        float side = Math.min(piece.width(), piece.height());
        float k = side / ITEM_SIZE;
        g.pose().pushPose();
        g.pose().translate(piece.x() + (piece.width() - side) / 2f, piece.y() + (piece.height() - side) / 2f, 0);
        g.pose().scale(k, k, 1);
        g.renderItem(new ItemStack(item), 0, 0);
        g.pose().popPose();
    }
}
