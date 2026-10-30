package io.github.matheusanbs.wirelessautomate.client.guide;

import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import io.github.matheusanbs.wirelessautomate.WirelessAutomate;
import io.github.matheusanbs.wirelessautomate.client.guide.layout.GuideMetrics;
import io.github.matheusanbs.wirelessautomate.client.guide.layout.Sheet;
import io.github.matheusanbs.wirelessautomate.client.guide.layout.SheetOp;
import io.github.matheusanbs.wirelessautomate.client.guide.layout.TextMeasure;
import io.github.matheusanbs.wirelessautomate.client.guide.layout.TextStyle;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.commands.arguments.item.ItemParser;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

/**
 * Uma folha pronta para desenhar: as {@link SheetOp} da paginação viram tinta (o texto já com estilo, a pilha do item,
 * a textura da imagem, o nome da dica, a página do link). Montada uma vez, quando a folha aparece; o quadro só percorre
 * a lista. As cores da tinta são fixas: o livro é papel e não segue o tema claro e escuro.
 */
final class GuideInk {
    /** Texto (grafite.2). */
    static final int TEXT = 0xFF2A2730;
    /** Títulos e links (coral.1). */
    static final int TITLE = 0xFFC2442A;
    /** Ornamentos e molduras (latão). */
    static final int BRASS = 0xFFB48E2E;
    /** Texto apagado: subtítulo, número da folha, dica. */
    static final int FAINT = 0xFF8A7F69;
    /** Linhas de tabela, fundo do código e seta desligada (porcelana.2). */
    static final int LEAF = 0xFFDDD3BF;
    /** O fundo claro dentro das molduras. */
    static final int FRAME_FILL = 0x8CFFFBF3;

    private static final Style BOLD = Style.EMPTY.withBold(true);
    private static final Style LINK = Style.EMPTY.withUnderlined(true);

    /** O estilo da fonte de cada estilo de texto; a medida e o desenho usam o mesmo. */
    static Style style(TextStyle s) {
        return switch (s) {
            case BOLD, TITLE2 -> BOLD;
            case LINK -> LINK;
            default -> Style.EMPTY;
        };
    }

    static int color(TextStyle s) {
        return switch (s) {
            case LINK, TITLE1, TITLE2, TITLE3 -> TITLE;
            default -> TEXT;
        };
    }

    sealed interface Ink {
    }

    /** {@code codeW} > 0 pinta o fundo do código atrás do texto. */
    record Txt(int x, int y, FormattedCharSequence text, int color, float scale, int codeW) implements Ink {
    }

    /** {@code page} é a página do item (nula se não tem, ou se é a própria página aberta). */
    record Item(int x, int y, int size, ItemStack stack, Component name, @Nullable String page) implements Ink {
    }

    record Img(int x, int y, int w, int h, ResourceLocation texture, int texW, int texH) implements Ink {
    }

    record Fill(int x, int y, int w, int h, int color) implements Ink {
    }

    record Ornament(int x, int y, int w, int h) implements Ink {
    }

    /**
     * Moldura de latão ({@code x, y, w, h}) com o ícone (se houver) e o rótulo do que vem dentro, já quebrado e
     * centrado a partir de {@code contentY}.
     */
    record Frame(int x, int y, int w, int h, int contentY, ItemStack stack, List<FormattedCharSequence> lines,
            int[] lineX) implements Ink {
    }

    /** A área de clique de um link para outra página, com o título dela para a dica. */
    record Link(int x, int y, int w, int h, String page, Component title) implements Ink {
    }

    private GuideInk() {
    }

    /** Monta a tinta da folha; {@code current} é a página aberta (os itens dela não levam a ela mesma). */
    static List<Ink> prepare(Sheet sheet, @Nullable String current, Font font, TextMeasure measure) {
        List<Ink> out = new ArrayList<>(sheet.ops().size() + 4);
        for (SheetOp op : sheet.ops()) {
            switch (op) {
                case SheetOp.Text t -> text(t, out);
                case SheetOp.LinkArea l -> {
                    String page = GuidePages.resolve(l.page());
                    if (page != null) {
                        out.add(new Link(l.x(), l.y(), l.w(), l.h(), page, Component.literal(GuidePages.title(page))));
                    }
                }
                case SheetOp.Icon i -> out.add(item(i.x(), i.y(), i.w(), i.id(), i.components(), current));
                case SheetOp.BlockIcon b -> out.add(item(b.x(), b.y(), b.w(), b.id(), blockState(b.props()), current));
                case SheetOp.Rule r -> out.add(new Fill(r.x(), r.y(), r.w(), r.h(), LEAF));
                case SheetOp.Ornament o -> out.add(new Ornament(o.x(), o.y(), o.w(), o.h()));
                case SheetOp.Image i -> {
                    TextMeasure.Size s = measure.imageSize(i.path());
                    out.add(new Img(i.x(), i.y(), i.w(), i.h(), FontMeasure.image(i.path()), s.w(), s.h()));
                }
                case SheetOp.RecipeFrame r -> {
                    String id = r.itemId() != null ? r.itemId() : r.recipeId();
                    ItemStack stack = stack(id, "");
                    Component what = stack.isEmpty() ? Component.literal(id) : stack.getHoverName();
                    out.add(frame(r, stack, Component.translatable("gui.wirelessautomate.guide.recipe", what), font));
                }
                case SheetOp.SceneFrame s -> out.add(frame(s, ItemStack.EMPTY,
                        Component.translatable("gui.wirelessautomate.guide.scene"), font));
                case SheetOp.SubPagesMarker s -> out.add(frame(s, ItemStack.EMPTY,
                        Component.translatable("gui.wirelessautomate.guide.subpages"), font));
            }
        }
        return out;
    }

    private static void text(SheetOp.Text t, List<Ink> out) {
        int codeW = t.style() == TextStyle.CODE ? t.w() : 0;
        FormattedCharSequence seq = FormattedCharSequence.forward(t.text(), style(t.style()));
        out.add(new Txt(t.x(), t.y(), seq, color(t.style()), (float) t.scale(), codeW));
        // o título de seção leva uma linha fina até a borda da folha, como no mockup
        int lineX = t.x() + t.w() + 4;
        if (t.style() == TextStyle.TITLE2 && lineX < GuideMetrics.SHEET_W - 8) {
            out.add(new Fill(lineX, t.y() + t.h() / 2 - 1, GuideMetrics.SHEET_W - lineX, 1, LEAF));
        }
    }

    private static Item item(int x, int y, int size, String id, String components, @Nullable String current) {
        ItemStack stack = stack(id, components);
        String page = GuidePages.pageForItem(id);
        if (page != null && page.equals(current)) {
            page = null;
        }
        return new Item(x, y, size, stack, stack.getHoverName(), page);
    }

    private static Frame frame(SheetOp op, ItemStack stack, Component label, Font font) {
        int iconH = stack.isEmpty() ? 0 : GuideMetrics.ICON + 4;
        List<FormattedCharSequence> lines = font.split(label, Math.max(8, op.w() - 12));
        int[] lineX = new int[lines.size()];
        for (int i = 0; i < lines.size(); i++) {
            lineX[i] = op.x() + (op.w() - font.width(lines.get(i))) / 2;
        }
        int contentY = op.y() + (op.h() - iconH - lines.size() * font.lineHeight) / 2;
        return new Frame(op.x(), op.y(), op.w(), op.h(), contentY, stack, List.copyOf(lines), lineX);
    }

    /** As propriedades de um {@code BlockImage} como o componente {@code minecraft:block_state} do item. */
    private static String blockState(Map<String, String> props) {
        if (props.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder("minecraft:block_state={");
        boolean first = true;
        for (Map.Entry<String, String> e : props.entrySet()) {
            if (!first) {
                sb.append(',');
            }
            first = false;
            sb.append(e.getKey()).append(":'").append(e.getValue()).append('\'');
        }
        return sb.append('}').toString();
    }

    /** A pilha do item pelo id, com os componentes do texto ({@code minecraft:block_state={tier:'elite'}}). */
    static ItemStack stack(String id, @Nullable String components) {
        ResourceLocation rl = ResourceLocation.tryParse(id);
        if (rl == null || !BuiltInRegistries.ITEM.containsKey(rl)) {
            return ItemStack.EMPTY;
        }
        Minecraft mc = Minecraft.getInstance();
        if (components != null && !components.isEmpty() && mc.level != null) {
            try {
                ItemParser.ItemResult r = new ItemParser(mc.level.registryAccess())
                        .parse(new StringReader(id + "[" + components + "]"));
                return new ItemStack(r.item(), 1, r.components());
            } catch (CommandSyntaxException e) {
                WirelessAutomate.LOGGER.warn("Livro: componentes inválidos em {}: {}", id, e.getMessage());
            }
        }
        return new ItemStack(BuiltInRegistries.ITEM.get(rl));
    }
}
