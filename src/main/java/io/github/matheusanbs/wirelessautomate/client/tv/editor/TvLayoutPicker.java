package io.github.matheusanbs.wirelessautomate.client.tv.editor;

import io.github.matheusanbs.wirelessautomate.client.ArmedClick;
import io.github.matheusanbs.wirelessautomate.client.GuiPaint;
import io.github.matheusanbs.wirelessautomate.client.GuiText;
import io.github.matheusanbs.wirelessautomate.client.ResourceStyle;
import io.github.matheusanbs.wirelessautomate.menu.TvMenu;
import io.github.matheusanbs.wirelessautomate.network.LoadedTypes;
import io.github.matheusanbs.wirelessautomate.storage.StorageKind;
import io.github.matheusanbs.wirelessautomate.tv.TvComponent;
import io.github.matheusanbs.wirelessautomate.tv.TvLayouts;
import io.github.matheusanbs.wirelessautomate.tv.TvPiece;
import io.github.matheusanbs.wirelessautomate.tv.TvSavedLayouts;
import java.util.List;
import java.util.UUID;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

/**
 * Os layouts, no topo da base da Tela: a lista com os quatro prontos (Energia, Armazenamento, Rede e Relógio)
 * e, depois deles, os do jogador (as Telas que ele salvou), o botão "Aplicar layout" (troca as peças; o tema fica,
 * numa mudança só que o desfazer volta), o campo do nome com "Salvar layout atual" (grava as peças, o fundo e as
 * linhas da Tela como um componente com layout, que vale para qualquer tamanho de Tela) e "Remover layout" em dois
 * cliques, só num do jogador; embaixo do Aplicar, Exportar, Importar e Copiar como texto ({@link TvLayoutCode}).
 * Os prontos saem ligados à primeira rede que o jogador usa e ao primeiro armazenamento ligado à Tela, quando há;
 * sem eles, ficam sem dado para ligar na aba Dado.
 */
final class TvLayoutPicker {
    private TvLayoutPicker() {
    }

    static void add(TvForm form, TvPropsCtx ctx) {
        form.label("base.layout.title", TvPropsCtx.key("base.layout"));
        form.add(new LayoutList(form.font, form.y(), ctx));
        form.endRow(LayoutList.HEIGHT);
        form.add(new TvControls.Button(form.font, "base.layout.apply", 0, form.y(), TvForm.W,
                TvPropsCtx.key("base.layout.apply"), () -> apply(ctx)));
        form.endRow(TvControls.ROW - 1);
        TvLayoutCode.add(form, ctx);
        form.text("base.layout.name", TvPropsCtx.key("base.layout.name"), TvComponent.MAX_NAME, null,
                TvPropsCtx.key("base.layout.name.hint"), () -> ctx.doc().layoutName(),
                text -> ctx.doc().layoutName(text));
        form.add(new TvControls.Button(form.font, "base.layout.save", 0, form.y(), TvForm.W,
                TvPropsCtx.key("base.layout.save"), () -> save(ctx)).enabledWhen(() -> canSave(ctx)));
        form.endRow(TvControls.ROW - 1);
        form.add(new Remove(form.font, form.y(), ctx));
        form.endRow(TvControls.ROW - 1);
        form.skip(2);
    }

    /** O centro da linha do layout salvo {@code name} na lista da base ({@code control}), para o e2e clicar. */
    static int @Nullable [] rowCenter(@Nullable TvControl control, String name) {
        return control instanceof LayoutList list ? list.rowCenter(name) : null;
    }

    private static int builtin() {
        return TvLayouts.Kind.values().length;
    }

    private static int count() {
        return builtin() + TvLibraryClient.layouts().size();
    }

    /** O índice escolhido, preso à lista (um layout removido deixa o índice para além do fim). */
    private static int index(TvPropsCtx ctx) {
        return Math.min(ctx.doc().layoutKind(), count() - 1);
    }

    /** O layout do jogador escolhido na lista, ou {@code null} com um pronto escolhido. */
    static @Nullable TvComponent mine(TvPropsCtx ctx) {
        int at = index(ctx) - builtin();
        return at >= 0 ? TvLibraryClient.layouts().get(at) : null;
    }

    private static void apply(TvPropsCtx ctx) {
        TvComponent saved = mine(ctx);
        // Cada Aplicar é uma mudança própria no desfazer: dois seguidos voltam um de cada vez.
        if (saved != null) {
            ctx.doc().applyLayout(TvSavedLayouts.apply(saved, ctx.doc().draft(), ctx.doc().area()));
            return;
        }
        List<TvPiece> pieces = TvLayouts.build(TvLayouts.Kind.values()[index(ctx)], ctx.doc().area(),
                TvLayoutPicker::text, network(ctx), storage(ctx), LoadedTypes.LIST);
        ctx.doc().changeLayout(l -> l.withPieces(pieces), null);
    }

    private static boolean canSave(TvPropsCtx ctx) {
        return !ctx.doc().layoutName().isBlank() && !ctx.doc().pieces().isEmpty();
    }

    /** Grava a Tela atual como layout; o nome já usado substitui o antigo, como nos componentes. */
    private static void save(TvPropsCtx ctx) {
        if (canSave(ctx)) {
            ctx.actions().saveComponent(TvSavedLayouts.of(ctx.doc().layoutName().strip(), ctx.doc().draft(),
                    ctx.doc().area()));
        }
    }

    private static String text(String key) {
        if (key.startsWith("type.")) {
            for (var type : LoadedTypes.LIST) {
                if (key.equals("type." + type.key())) {
                    return ResourceStyle.name(type).getString();
                }
            }
        }
        return TvPropsCtx.key("base.layout.text." + key).getString();
    }

    private static @Nullable UUID network(TvPropsCtx ctx) {
        return ctx.networks().isEmpty() ? null : ctx.networks().get(0).id();
    }

    private static TvLayouts.@Nullable Storage storage(TvPropsCtx ctx) {
        for (TvMenu.LinkChoice link : ctx.data().links()) {
            StorageKind kind = ctx.data().kindOf(link.pos());
            if (kind != null) {
                return new TvLayouts.Storage(link.pos(), kind.resource);
            }
        }
        return null;
    }

    /**
     * A lista dos layouts (os prontos e os do jogador) com o escolhido marcado: um clique escolhe, dois seguidos
     * aplicam. Tem {@link #ROWS} linhas à vista; a roda rola a lista quando ela tem mais do que isso e o mouse
     * está em cima (mesmo no fim dela, para não rolar o painel junto), senão passa para o painel (antes a roda trocava o layout sem querer).
     */
    private static final class LayoutList extends TvControl {
        static final int ROWS = 5;
        static final int ROW_H = 11;
        static final int HEIGHT = ROWS * ROW_H + 2;
        private static final long DOUBLE_MS = 400;

        private final Font font;
        private final TvPropsCtx ctx;
        private int top;
        private int lastRow = -1;
        private long lastClick;
        private int fittedVersion = -1;
        private int fittedCount = -1;
        private GuiText.Fitted[] fitted = new GuiText.Fitted[0];

        LayoutList(Font font, int y, TvPropsCtx ctx) {
            super("base.layout.list", 0, y, TvForm.W, HEIGHT);
            this.font = font;
            this.ctx = ctx;
        }

        private Component nameAt(int i) {
            return i < builtin() ? TvPropsCtx.key("base.layout." + TvLayouts.Kind.values()[i].key)
                    : Component.literal(TvLibraryClient.layouts().get(i - builtin()).name());
        }

        /** Refaz os textos ajustados só quando a biblioteca muda. */
        private void fit() {
            int count = count();
            if (fittedVersion == TvLibraryClient.version() && fittedCount == count) {
                return;
            }
            fittedVersion = TvLibraryClient.version();
            fittedCount = count;
            fitted = new GuiText.Fitted[count];
            for (int i = 0; i < count; i++) {
                fitted[i] = GuiText.fit(font, nameAt(i), w - 8);
            }
            top = Math.max(0, Math.min(top, count - ROWS));
        }

        @Override
        void render(GuiGraphics g, int mouseX, int mouseY) {
            fit();
            GuiPaint.box(g, ax, ay, w, h, GuiPaint.BUTTON, GuiPaint.BUTTON_BORDER);
            int chosen = index(ctx);
            for (int r = 0; r < ROWS && top + r < fitted.length; r++) {
                int i = top + r;
                int ry = ay + 1 + r * ROW_H;
                boolean hovered = contains(mouseX, mouseY) && mouseY >= ry && mouseY < ry + ROW_H;
                if (i == chosen) {
                    g.fill(ax + 1, ry, ax + w - 1, ry + ROW_H, GuiPaint.BUTTON_HOVER_BORDER);
                } else if (hovered) {
                    g.fill(ax + 1, ry, ax + w - 1, ry + ROW_H, GuiPaint.BUTTON_BORDER);
                }
                GuiText.draw(g, font, fitted[i], ax + 4, ry + 2, i == chosen ? GuiPaint.FG : GuiPaint.MUTED);
            }
            if (fitted.length > ROWS) {
                int barH = Math.max(6, (h - 2) * ROWS / fitted.length);
                int barY = ay + 1 + (h - 2 - barH) * top / (fitted.length - ROWS);
                g.fill(ax + w - 2, barY, ax + w - 1, barY + barH, GuiPaint.MUTED);
            }
        }

        @Override
        boolean mouseClicked(double mouseX, double mouseY, int button) {
            if (!contains(mouseX, mouseY)) {
                return false;
            }
            fit();
            int row = top + (int) ((mouseY - ay - 1) / ROW_H);
            if (row < 0 || row >= fitted.length) {
                return true;
            }
            long now = System.currentTimeMillis();
            ctx.doc().layoutKind(row);
            if (row == lastRow && now - lastClick < DOUBLE_MS) {
                apply(ctx);
            }
            lastRow = row;
            lastClick = now;
            return true;
        }

        /** O centro da linha do layout do jogador com o nome, rolando a lista até ela; {@code null} se não há (o e2e). */
        int @Nullable [] rowCenter(String name) {
            fit();
            List<TvComponent> mine = TvLibraryClient.layouts();
            for (int k = 0; k < mine.size(); k++) {
                if (mine.get(k).name().equals(name)) {
                    int i = builtin() + k;
                    top = Math.max(0, Math.min(Math.max(0, fitted.length - ROWS), i - ROWS + 1));
                    return new int[] {ax + w / 2, ay + 1 + (i - top) * ROW_H + ROW_H / 2};
                }
            }
            return null;
        }

        @Override
        boolean mouseScrolled(double mouseX, double mouseY, double delta) {
            fit();
            if (!contains(mouseX, mouseY) || fitted.length <= ROWS) {
                return false;
            }
            top = Math.max(0, Math.min(fitted.length - ROWS, top - (int) Math.signum(delta)));
            return true;
        }
    }

    /** "Remover layout": vale só com um layout do jogador escolhido; o primeiro clique arma, o segundo remove. */
    private static final class Remove extends TvControl {
        private final Font font;
        private final TvPropsCtx ctx;
        private final TvEditorPaint.Label normal;
        private final TvEditorPaint.Label confirm;
        private final ArmedClick armed = new ArmedClick();
        private boolean on;

        Remove(Font font, int y, TvPropsCtx ctx) {
            super("base.layout.remove", 0, y, TvForm.W, TvControls.ROW - 1);
            this.font = font;
            this.ctx = ctx;
            normal = TvEditorPaint.label(font, TvPropsCtx.key("base.layout.remove"), w - 4);
            confirm = TvEditorPaint.label(font, TvPropsCtx.key("base.layout.remove.armed"), w - 4);
            refresh();
        }

        @Override
        void refresh() {
            on = mine(ctx) != null;
            if (!on) {
                armed.disarm();
            }
        }

        @Override
        void render(GuiGraphics g, int mouseX, int mouseY) {
            boolean confirming = on && armed.armed(System.currentTimeMillis());
            TvEditorPaint.button(g, font, ax, ay, w, h, confirming ? confirm : normal, contains(mouseX, mouseY), on);
        }

        @Override
        boolean mouseClicked(double mouseX, double mouseY, int button) {
            if (!contains(mouseX, mouseY)) {
                return false;
            }
            TvComponent saved = mine(ctx);
            if (on && saved != null && armed.click(System.currentTimeMillis())) {
                ctx.actions().removeComponent(saved.name());
            }
            return true;
        }
    }
}
