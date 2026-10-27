package io.github.matheusanbs.wirelessautomate.client.tv.editor;

import io.github.matheusanbs.wirelessautomate.client.ResourceStyle;
import io.github.matheusanbs.wirelessautomate.menu.TvMenu;
import io.github.matheusanbs.wirelessautomate.network.LoadedTypes;
import io.github.matheusanbs.wirelessautomate.storage.StorageKind;
import io.github.matheusanbs.wirelessautomate.tv.TvLayouts;
import io.github.matheusanbs.wirelessautomate.tv.TvPiece;
import java.util.List;
import java.util.UUID;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

/**
 * Os layouts prontos, no topo da base da Tela: o seletor ‹ › com Energia, Armazenamento, Rede e Relógio e o botão
 * "Aplicar layout", que troca as peças pelas do layout (o tema e o fundo ficam) numa mudança só, que o desfazer
 * volta. As peças já saem ligadas à primeira rede que o jogador usa e ao primeiro armazenamento ligado à Tela, quando
 * há; sem eles, ficam sem dado para ligar na aba Dado.
 */
final class TvLayoutPicker {
    private TvLayoutPicker() {
    }

    static void add(TvForm form, TvPropsCtx ctx) {
        form.label("base.layout.title", TvPropsCtx.key("base.layout"));
        form.wideCycle("base.layout", () -> name(kind(ctx)), delta -> ctx.doc().layoutKind(
                Math.floorMod(ctx.doc().layoutKind() + delta, TvLayouts.Kind.values().length)));
        form.add(new TvControls.Button(form.font, "base.layout.apply", 0, form.y(), TvForm.W,
                TvPropsCtx.key("base.layout.apply"), () -> apply(ctx)));
        form.endRow(TvControls.ROW - 1);
        form.skip(2);
    }

    private static TvLayouts.Kind kind(TvPropsCtx ctx) {
        return TvLayouts.Kind.values()[ctx.doc().layoutKind()];
    }

    private static Component name(TvLayouts.Kind kind) {
        return TvPropsCtx.key("base.layout." + kind.key);
    }

    private static void apply(TvPropsCtx ctx) {
        List<TvPiece> pieces = TvLayouts.build(kind(ctx), ctx.doc().area(), TvLayoutPicker::text, network(ctx),
                storage(ctx), LoadedTypes.LIST);
        ctx.doc().changeLayout(l -> l.withPieces(pieces), "layout.preset");
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
}
