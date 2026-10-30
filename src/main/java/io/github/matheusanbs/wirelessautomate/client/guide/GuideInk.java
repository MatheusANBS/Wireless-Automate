package io.github.matheusanbs.wirelessautomate.client.guide;

import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import io.github.matheusanbs.wirelessautomate.WirelessAutomate;
import io.github.matheusanbs.wirelessautomate.client.GuiPaint;
import io.github.matheusanbs.wirelessautomate.client.guide.layout.GuideMetrics;
import io.github.matheusanbs.wirelessautomate.client.guide.layout.Sheet;
import io.github.matheusanbs.wirelessautomate.client.guide.layout.SheetOp;
import io.github.matheusanbs.wirelessautomate.client.guide.layout.TextMeasure;
import io.github.matheusanbs.wirelessautomate.client.guide.layout.TextStyle;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.commands.arguments.item.ItemParser;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;
import org.joml.Vector3f;

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
    private static final Component OPEN_PAGE = Component.translatable("gui.wirelessautomate.guide.open_page")
            .withStyle(ChatFormatting.GRAY);

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

    /**
     * {@code page} é a página do item (nula se não tem, ou se é a própria página aberta); {@code tip} é a dica: o nome
     * e, se tem página, a linha que diz que o clique a abre.
     */
    record Item(int x, int y, int size, ItemStack stack, List<Component> tip, @Nullable String page) implements Ink {
    }

    record Img(int x, int y, int w, int h, ResourceLocation texture, int texW, int texH) implements Ink {
    }

    record Fill(int x, int y, int w, int h, int color) implements Ink {
    }

    record Ornament(int x, int y, int w, int h) implements Ink {
    }

    /**
     * Moldura de latão ({@code x, y, w, h}) com o ícone (se houver) e o rótulo do que vem dentro, já quebrado e
     * centrado a partir de {@code contentY}. {@code full} é o rótulo inteiro quando a última linha foi abreviada (a
     * dica mostra), senão nulo.
     */
    record Frame(int x, int y, int w, int h, int contentY, ItemStack stack, List<FormattedCharSequence> lines,
            int[] lineX, @Nullable Component full) implements Ink {
    }

    /**
     * A receita de bancada na moldura ({@code x, y, w, h}): os fundos dos slots ({@code slotX}, {@code slotY}, 18
     * unidades), as opções de cada casa (a que aparece alterna a cada segundo; cada opção já é um {@link Item} na
     * posição dela), o resultado no slot grande ({@code bigX}, {@code bigY}) e a seta.
     */
    record Craft(int x, int y, int w, int h, int[] slotX, int[] slotY, Item[][] cells, Item result, int bigX,
            int bigY, int arrowX, int arrowY) implements Ink {
    }

    /**
     * A cena parada na moldura ({@code x, y, w, h}): os estados de bloco e as posições ({@code pos}, x, y, z em
     * sequência), a vista já enquadrada (unidades de folha) e as duas luzes já giradas com ela.
     */
    record Scene(int x, int y, int w, int h, BlockState[] states, int[] pos, Matrix4f view, Vector3f light0,
            Vector3f light1) implements Ink {
    }

    /** A área de clique de um link para outra página, com o título dela para a dica. */
    record Link(int x, int y, int w, int h, String page, Component title) implements Ink {
    }

    private GuideInk() {
    }

    /**
     * Monta a tinta da folha; {@code current} é a página aberta (os itens dela não levam a ela mesma). Cada operação
     * que sai da folha e cada texto que, medido com a fonte de verdade, passa da folha vão para {@code problems}.
     */
    static List<Ink> prepare(Sheet sheet, @Nullable String current, Font font, TextMeasure measure,
            List<String> problems) {
        List<Ink> out = new ArrayList<>(sheet.ops().size() + 4);
        for (SheetOp op : sheet.ops()) {
            check(op, font, problems);
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
                case SheetOp.RecipeFrame r -> out.add(GuideRecipes.prepare(r, current, font));
                case SheetOp.SceneFrame s -> out.add(GuideScenes.prepare(s));
                case SheetOp.SubPagesMarker s -> out.add(frame(s, ItemStack.EMPTY,
                        Component.translatable("gui.wirelessautomate.guide.subpages"), font));
            }
        }
        return out;
    }

    /** A operação cabe na folha? O texto é medido de novo com a fonte, no estilo e na escala em que é desenhado. */
    private static void check(SheetOp op, Font font, List<String> problems) {
        int right = op.x() + op.w();
        int bottom = op.y() + op.h();
        if (op instanceof SheetOp.Text t) {
            FormattedText ft = FormattedText.of(t.text(), style(t.style()));
            right = Math.max(right, t.x() + (int) Math.ceil(font.width(ft) * t.scale()));
            bottom = Math.max(bottom, t.y() + (int) Math.ceil(font.lineHeight * t.scale()));
        }
        if (op.x() < 0 || op.y() < 0 || right > GuideMetrics.SHEET_W || bottom > GuideMetrics.SHEET_H) {
            problems.add(describe(op) + " sai da folha (" + op.x() + ", " + op.y() + " até " + right + ", " + bottom
                    + "; a folha tem " + GuideMetrics.SHEET_W + " × " + GuideMetrics.SHEET_H + ")");
        }
    }

    private static String describe(SheetOp op) {
        return op instanceof SheetOp.Text t ? "o texto \"" + t.text() + "\"" : op.getClass().getSimpleName();
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
        return item(x, y, size, stack(id, components), current);
    }

    /** O item já com a página dele (pelo id do item) e a dica. */
    static Item item(int x, int y, int size, ItemStack stack, @Nullable String current) {
        String page = stack.isEmpty() ? null
                : GuidePages.pageForItem(BuiltInRegistries.ITEM.getKey(stack.getItem()).toString());
        if (page != null && page.equals(current)) {
            page = null;
        }
        List<Component> tip = page == null ? List.of(stack.getHoverName())
                : List.of(stack.getHoverName(), OPEN_PAGE);
        return new Item(x, y, size, stack, tip, page);
    }

    /**
     * O rótulo dentro da moldura (4 unidades de borda): se não cabe com o ícone, o ícone sai; se nem assim cabe, a
     * última linha que cabe é abreviada e a dica da moldura mostra o rótulo inteiro.
     */
    static Frame frame(SheetOp op, ItemStack stack, Component label, Font font) {
        int width = Math.max(8, op.w() - 12);
        int inner = op.h() - 8;
        List<FormattedCharSequence> lines = new ArrayList<>(font.split(label, width));
        int lh = font.lineHeight;
        ItemStack icon = stack;
        if (!icon.isEmpty() && GuideMetrics.ICON + 4 + lines.size() * lh > inner) {
            icon = ItemStack.EMPTY;
        }
        Component full = null;
        int max = Math.max(1, inner / lh);
        if (lines.size() > max) {
            StringBuilder rest = new StringBuilder();
            for (int i = max - 1; i < lines.size(); i++) {
                if (rest.length() > 0) {
                    rest.append(' ');
                }
                lines.get(i).accept((index, style, cp) -> {
                    rest.appendCodePoint(cp);
                    return true;
                });
            }
            lines = new ArrayList<>(lines.subList(0, max - 1));
            lines.add(GuiPaint.ellipsize(font, Component.literal(rest.toString()), width));
            full = label;
        }
        int iconH = icon.isEmpty() ? 0 : GuideMetrics.ICON + 4;
        int[] lineX = new int[lines.size()];
        for (int i = 0; i < lines.size(); i++) {
            lineX[i] = op.x() + (op.w() - font.width(lines.get(i))) / 2;
        }
        int contentY = op.y() + (op.h() - iconH - lines.size() * lh) / 2;
        return new Frame(op.x(), op.y(), op.w(), op.h(), contentY, icon, List.copyOf(lines), lineX, full);
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
