package io.github.matheusanbs.wirelessautomate.client.tv.editor;

import io.github.matheusanbs.wirelessautomate.client.ArmedClick;
import io.github.matheusanbs.wirelessautomate.client.ResourceStyle;
import io.github.matheusanbs.wirelessautomate.menu.TvMenu;
import io.github.matheusanbs.wirelessautomate.network.LoadedTypes;
import io.github.matheusanbs.wirelessautomate.storage.StorageKind;
import io.github.matheusanbs.wirelessautomate.tv.TvComponent;
import io.github.matheusanbs.wirelessautomate.tv.TvLayouts;
import io.github.matheusanbs.wirelessautomate.tv.TvPiece;
import io.github.matheusanbs.wirelessautomate.tv.TvSavedLayouts;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

/**
 * Os layouts, no topo da base da Tela: a lista ({@link TvDropdown}) com os quatro prontos (Energia, Armazenamento,
 * Rede e Relógio) e, depois deles, os do jogador (as Telas que ele salvou), o botão "Aplicar layout" (troca as peças; o tema fica,
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
        form.wideDropdown("base.layout.list", TvLayoutPicker::choices, Choice::name,
                () -> choices().get(index(ctx)), choice -> ctx.doc().layoutKind(choice.index()));
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

    /** Uma linha da lista: a posição (os prontos e depois os do jogador) e o nome. */
    private record Choice(int index, Component name) {
    }

    /** As linhas da lista, refeitas só no refresh; o nome entra na igualdade, então renomear um salvo refaz a lista. */
    private static List<Choice> choices() {
        List<TvComponent> mine = TvLibraryClient.layouts();
        List<Choice> out = new ArrayList<>(builtin() + mine.size());
        for (TvLayouts.Kind kind : TvLayouts.Kind.values()) {
            out.add(new Choice(out.size(), TvPropsCtx.key("base.layout." + kind.key)));
        }
        for (TvComponent saved : mine) {
            out.add(new Choice(out.size(), Component.literal(saved.name())));
        }
        return out;
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
