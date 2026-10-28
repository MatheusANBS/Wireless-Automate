package io.github.matheusanbs.wirelessautomate.client.tv.render;

import io.github.matheusanbs.wirelessautomate.client.tv.TvIcons;
import io.github.matheusanbs.wirelessautomate.compat.sodium.SodiumSprites;
import io.github.matheusanbs.wirelessautomate.tv.TvLayout;
import io.github.matheusanbs.wirelessautomate.tv.TvPartStyle;
import io.github.matheusanbs.wirelessautomate.tv.TvPiece;
import io.github.matheusanbs.wirelessautomate.tv.TvPieceType;
import java.util.ArrayList;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.world.item.ItemStack;

/**
 * O Ícone: o item como no inventário (bloco em 3D, item chapado, brilho se ele tem, e os componentes que ele tenha),
 * o fluido ou o químico do Mekanism com a textura e a cor deles, sem nome nem contagem, na textura da Tela, escalado
 * para o lado menor da caixa e centrado. Sem nada ou com um id que não existe (de um mod que saiu), nada.
 */
final class TvPaintIcon {
    private static final float ITEM_SIZE = 16f;
    static final TextureAtlasSprite[] NO_SPRITES = new TextureAtlasSprite[0];
    private static final ArrayList<TextureAtlasSprite> COLLECTED = new ArrayList<>();

    private TvPaintIcon() {
    }

    /** Desenha; devolve se a imagem se mexe (a Tela redesenha a ~10 Hz). */
    static boolean paint(GuiGraphics g, TvPiece piece, TvPartStyle part) {
        if (part.item().isEmpty() || !TvIcons.present(part)) {
            return false;
        }
        float side = Math.min(piece.width(), piece.height());
        float k = side / ITEM_SIZE;
        g.pose().pushPose();
        g.pose().translate(piece.x() + (piece.width() - side) / 2f, piece.y() + (piece.height() - side) / 2f, 0);
        g.pose().scale(k, k, 1);
        ItemStack stack = TvIcons.stack(part);
        if (stack.isEmpty()) {
            TvIcons.drawFlat(g, Minecraft.getInstance().font, part, 0, 0);
        } else {
            g.renderItem(stack, 0, 0);
        }
        g.pose().popPose();
        boolean animated = TvIcons.animated(part);
        if (animated && SodiumSprites.AVAILABLE) {
            TextureAtlasSprite sprite = TvIcons.sprite(part);
            if (sprite != null) {
                COLLECTED.add(sprite);
            }
        }
        return animated;
    }

    /** Começa a juntar as texturas animadas do próximo desenho (só com o Sodium, que as quer marcadas a cada quadro). */
    static void beginCollect() {
        COLLECTED.clear();
    }

    /** As texturas animadas do último desenho, ou {@link #NO_SPRITES}. */
    static TextureAtlasSprite[] collected() {
        return COLLECTED.isEmpty() ? NO_SPRITES : COLLECTED.toArray(NO_SPRITES);
    }

    /** Se o layout tem um Ícone com item: a textura da Tela precisa de buffer de profundidade para o desenhar certo. */
    static boolean has(TvLayout layout) {
        for (TvPiece piece : layout.pieces()) {
            if (piece.type() == TvPieceType.ICON && piece.style().part().item().isPresent()
                    && piece.style().part().iconKind() == TvPartStyle.IconKind.ITEM) {
                return true;
            }
        }
        return false;
    }
}
