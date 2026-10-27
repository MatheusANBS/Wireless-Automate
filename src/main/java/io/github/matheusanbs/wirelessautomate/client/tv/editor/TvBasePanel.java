package io.github.matheusanbs.wirelessautomate.client.tv.editor;

import io.github.matheusanbs.wirelessautomate.client.ArmedClick;
import io.github.matheusanbs.wirelessautomate.client.GuiPaint;
import io.github.matheusanbs.wirelessautomate.tv.TvBackground;
import io.github.matheusanbs.wirelessautomate.tv.TvColor;
import io.github.matheusanbs.wirelessautomate.tv.TvTheme;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

/**
 * A base da Tela, na coluna da direita enquanto nada está escolhido (como no mockup): o Tema (os quatro de partida e os
 * do jogador, trocados por ‹ ›), o Fundo (do tema, cor fixa ou degradê vertical), as linhas de varredura, a grade da
 * miniatura, as seis cores do tema (campos {@code #RRGGBB}), "Salvar tema" (grava a paleta atual na biblioteca com um
 * nome) e o remover do tema do jogador, em dois cliques. A Tela guarda a paleta inteira, não uma referência: trocar
 * ou apagar um tema da biblioteca nunca muda outra Tela. Tema, fundo e linhas vão pelo {@link TvEditorDoc#changeLayout}
 * (entram no desfazer); o que sai ao servidor passa pelo {@link TvEditorActions}.
 */
final class TvBasePanel {
    private static final int[] ROLES = {TvColor.BG, TvColor.CARD, TvColor.TEXT, TvColor.MUTED, TvColor.HIGH,
        TvColor.LOW};
    private static final String[] ROLE_KEYS = {"bg", "card", "text", "muted", "high", "low"};
    private static final int DOT = 10;

    private TvBasePanel() {
    }

    /** Uma chave que muda quando o formulário muda de forma: o tipo do fundo. */
    static int structure(TvEditorDoc doc) {
        return 1 + doc.draft().background().kind().ordinal();
    }

    static void build(TvForm form, TvPropsCtx ctx) {
        form.hint("base.hint", TvPropsCtx.key("base.hint"));
        TvLayoutPicker.add(form, ctx);
        form.label("base.theme.title", TvPropsCtx.key("base.theme"));
        form.wideCycle("base.theme", () -> themeName(ctx.theme()), delta -> stepTheme(ctx, delta));
        form.label("base.bg.title", TvPropsCtx.key("base.background"));
        form.wideCycle("base.bg.kind", () -> TvPropsCtx.key("base.bg." + ctx.doc().draft().background().kind()
                .getSerializedName()), delta -> stepBackground(ctx, delta));
        TvBackground background = ctx.doc().draft().background();
        switch (background.kind()) {
            case SOLID -> TvColorPicker.add(form, ctx, "base.bg.top", TvPropsCtx.key("base.bg.fill"),
                    () -> ctx.doc().draft().background().top(), v -> setTop(ctx, v), false, true);
            case GRADIENT -> {
                TvColorPicker.add(form, ctx, "base.bg.top", TvPropsCtx.key("base.bg.top"),
                        () -> ctx.doc().draft().background().top(), v -> setTop(ctx, v), false, true);
                TvColorPicker.add(form, ctx, "base.bg.bottom", TvPropsCtx.key("base.bg.bottom"),
                        () -> ctx.doc().draft().background().bottom(), v -> setBottom(ctx, v), false, true);
            }
            default -> {
            }
        }
        form.toggle("base.scanlines", TvPropsCtx.key("base.scanlines"), () -> ctx.doc().draft().scanlines(),
                on -> ctx.doc().changeLayout(l -> l.withScanlines(on), "scanlines"));
        form.toggle("base.grid", TvPropsCtx.key("base.grid"), () -> ctx.doc().gridVisible(),
                on -> ctx.doc().setGrid(on));
        form.skip(2);
        form.label("base.palette.title", TvPropsCtx.key("base.palette"));
        for (int i = 0; i < ROLES.length; i++) {
            int role = ROLES[i];
            int y = form.inline("base.color" + role, TvPropsCtx.key("base.role." + ROLE_KEYS[i]));
            form.add(new TvTextField(form.font, "base.color" + role, TvForm.LABEL_W, y,
                    TvForm.W - TvForm.LABEL_W - DOT - 4, 9, text -> text.matches("#?[0-9a-fA-F]*"),
                    Component.literal("#RRGGBB"), () -> TvColor.hex(ctx.theme().color(role)), text -> {
                        Integer parsed = TvColor.fromHex(text);
                        if (parsed != null && parsed != 0) {
                            ctx.doc().changeLayout(l -> l.withTheme(l.theme().withColor(role, parsed)),
                                    "theme.color." + role);
                        }
                    }));
            form.add(new Dot(y + 1, role, ctx));
            form.endRow(TvTextField.H);
        }
        form.text("base.theme_name", TvPropsCtx.key("base.theme_name"), TvTheme.MAX_NAME, null,
                TvPropsCtx.key("base.theme_name.hint"), () -> ctx.doc().themeName(), name -> ctx.doc().setThemeName(name));
        form.add(new TvControls.Button(form.font, "base.theme.save", 0, form.y(), TvForm.W,
                TvPropsCtx.key("base.save_theme"), () -> saveTheme(ctx)));
        form.endRow(TvControls.ROW - 1);
        form.add(new RemoveTheme(form.font, form.y(), ctx));
        form.endRow(TvControls.ROW - 1);
    }

    /** Os temas da lista, na ordem do ‹ ›: os quatro prontos e os do jogador. */
    private static List<TvTheme> themes() {
        List<TvTheme> all = new ArrayList<>(TvTheme.PRESETS);
        all.addAll(TvLibraryClient.themes());
        return all;
    }

    /** A posição na lista do tema com a paleta dada, ou -1 se a paleta é outra (editada à mão). */
    private static int indexOf(List<TvTheme> all, TvTheme theme) {
        for (int i = 0; i < all.size(); i++) {
            if (all.get(i).sameColors(theme)) {
                return i;
            }
        }
        return -1;
    }

    private static Component themeName(TvTheme theme) {
        List<TvTheme> all = themes();
        int at = indexOf(all, theme);
        if (at < 0) {
            return TvPropsCtx.key("base.custom");
        }
        return at < TvTheme.PRESETS.size() ? TvEditorText.readyName(all.get(at).name())
                : Component.literal(all.get(at).name());
    }

    private static void stepTheme(TvPropsCtx ctx, int delta) {
        List<TvTheme> all = themes();
        int at = indexOf(all, ctx.theme());
        int next = Math.floorMod((at < 0 ? (delta > 0 ? -1 : 0) : at) + delta, all.size());
        TvTheme chosen = all.get(next);
        ctx.doc().changeLayout(l -> l.withTheme(chosen), "theme");
    }

    private static void stepBackground(TvPropsCtx ctx, int delta) {
        TvBackground.Kind[] kinds = TvBackground.Kind.values();
        TvBackground.Kind kind = kinds[Math.floorMod(ctx.doc().draft().background().kind().ordinal() + delta,
                kinds.length)];
        TvBackground next = switch (kind) {
            case THEME -> TvBackground.DEFAULT;
            case SOLID -> new TvBackground(kind, TvColor.theme(TvColor.CARD), 0);
            case GRADIENT -> new TvBackground(kind, TvColor.theme(TvColor.BG), TvColor.theme(TvColor.CARD));
        };
        ctx.doc().changeLayout(l -> l.withBackground(next), "background");
    }

    private static void setTop(TvPropsCtx ctx, int value) {
        ctx.doc().changeLayout(l -> l.withBackground(new TvBackground(l.background().kind(), value,
                l.background().bottom())), "background.top");
    }

    private static void setBottom(TvPropsCtx ctx, int value) {
        ctx.doc().changeLayout(l -> l.withBackground(new TvBackground(l.background().kind(), l.background().top(),
                value)), "background.bottom");
    }

    /** Grava a paleta atual na biblioteca com o nome digitado; sem nome, nada. */
    private static void saveTheme(TvPropsCtx ctx) {
        String name = ctx.doc().themeName().strip();
        if (!name.isEmpty()) {
            TvTheme now = ctx.theme();
            ctx.actions().saveTheme(new TvTheme(name, now.bg(), now.card(), now.text(), now.muted(), now.high(),
                    now.low()));
        }
    }

    /** O nome do tema do jogador com a paleta atual, ou {@code null} (um pronto ou uma paleta solta). */
    private static @Nullable String mineNamed(TvPropsCtx ctx) {
        List<TvTheme> mine = TvLibraryClient.themes();
        for (TvTheme theme : mine) {
            if (theme.sameColors(ctx.theme())) {
                return theme.name();
            }
        }
        return null;
    }

    /** A amostra da cor do tema ao lado do campo, lida no refresh. */
    private static final class Dot extends TvControl {
        private final int role;
        private final TvPropsCtx ctx;
        private int shown;

        Dot(int y, int role, TvPropsCtx ctx) {
            super("base.dot" + role, TvForm.W - DOT, y, DOT, DOT);
            this.role = role;
            this.ctx = ctx;
            refresh();
        }

        @Override
        void refresh() {
            shown = ctx.theme().color(role) | 0xFF000000;
        }

        @Override
        void render(GuiGraphics g, int mouseX, int mouseY) {
            g.fill(ax + 1, ay + 1, ax + w - 1, ay + h - 1, shown);
            g.fill(ax + 1, ay, ax + w - 1, ay + 1, GuiPaint.BUTTON_BORDER);
            g.fill(ax + 1, ay + h - 1, ax + w - 1, ay + h, GuiPaint.BUTTON_BORDER);
            g.fill(ax, ay + 1, ax + 1, ay + h - 1, GuiPaint.BUTTON_BORDER);
            g.fill(ax + w - 1, ay + 1, ax + w, ay + h - 1, GuiPaint.BUTTON_BORDER);
        }
    }

    /** "Remover tema": vale só com a paleta de um tema do jogador; o primeiro clique arma, o segundo remove. */
    private static final class RemoveTheme extends TvControl {
        private final Font font;
        private final TvPropsCtx ctx;
        private final TvEditorPaint.Label normal;
        private final TvEditorPaint.Label confirm;
        private final ArmedClick armed = new ArmedClick();
        private boolean on;

        RemoveTheme(Font font, int y, TvPropsCtx ctx) {
            super("base.theme.remove", 0, y, TvForm.W, TvControls.ROW - 1);
            this.font = font;
            this.ctx = ctx;
            normal = TvEditorPaint.label(font, TvPropsCtx.key("base.remove_theme"), w - 4);
            confirm = TvEditorPaint.label(font, TvPropsCtx.key("base.remove_theme.armed"), w - 4);
            refresh();
        }

        @Override
        void refresh() {
            on = mineNamed(ctx) != null;
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
            String name = mineNamed(ctx);
            if (on && name != null && armed.click(System.currentTimeMillis())) {
                ctx.actions().removeTheme(name);
            }
            return true;
        }
    }
}
