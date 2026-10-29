package io.github.matheusanbs.wirelessautomate.client.tv;

import static io.github.matheusanbs.wirelessautomate.client.tv.TvScreenLayout.ACCESS_HINT;
import static io.github.matheusanbs.wirelessautomate.client.tv.TvScreenLayout.ACCESS_LABEL_Y;
import static io.github.matheusanbs.wirelessautomate.client.tv.TvScreenLayout.ACCESS_NOTICE;
import static io.github.matheusanbs.wirelessautomate.client.tv.TvScreenLayout.H;

import io.github.matheusanbs.wirelessautomate.client.GuiPaint;
import io.github.matheusanbs.wirelessautomate.client.GuiText;
import io.github.matheusanbs.wirelessautomate.client.GuiThemes;
import io.github.matheusanbs.wirelessautomate.client.access.AccessHost;
import io.github.matheusanbs.wirelessautomate.client.access.AccessPanel;
import io.github.matheusanbs.wirelessautomate.menu.SuggestionsMenu;
import io.github.matheusanbs.wirelessautomate.menu.TabletSnapshot.AccessView;
import io.github.matheusanbs.wirelessautomate.menu.TvMenu;
import io.github.matheusanbs.wirelessautomate.packet.NetworkSuggestPayload;
import io.github.matheusanbs.wirelessautomate.packet.TvAccessActionPayload.Action;
import java.util.List;
import java.util.UUID;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.glfw.GLFW;

/**
 * A aba Acesso da tela da Tela de Sinal, só para o dono e os operadores: quem mais edita a Tela. Em cima, o que a
 * lista dá e o aviso da última ação; embaixo, o {@link AccessPanel} da central da rede (convidados com o campo de
 * convidar e as sugestões dos jogadores online, e os times do FTB Teams, só com ele instalado). A lista vem do
 * servidor na abertura do menu e depois de cada ação ({@code TvAccessPayload}); nada chega com a aba fechada.
 */
final class TvAccessTab {
    private static final int TEAM_GUEST_ROWS = 5;
    private static final int TEAM_ROWS = 2;
    private static final int ONLY_GUEST_ROWS = 9;

    private final TvMenu menu;
    private final TvScreenActions actions;
    private final AccessPanel panel;
    private int left;
    private int top;
    private TvScreenText.@Nullable Wrapped hint;
    // o aviso montado quando chega uma resposta nova, não por quadro
    private int noticeVersion = -1;
    private GuiText.@Nullable Fitted notice;
    private boolean noticeRefused;
    private int inkVersion = -1;

    TvAccessTab(TvMenu menu, TvScreenActions actions) {
        this.menu = menu;
        this.actions = actions;
        this.panel = new AccessPanel(new Frame(), new Host());
    }

    /** O canto da tela (refeito a cada {@code init}) e os textos medidos uma vez. */
    void layout(Font font, int left, int top) {
        this.left = left;
        this.top = top;
        hint = TvScreenText.wrap(font, TvScreenText.key("access.hint"), ACCESS_HINT.w(), 2);
        noticeVersion = -1;
        inkVersion = -1;
    }

    /** Os dois campos de texto, que a tela põe entre os widgets dela. */
    List<EditBox> createBoxes(Font font) {
        return panel.createBoxes(font);
    }

    /** {@code shown}: a aba Acesso está aberta. */
    void refresh(boolean shown) {
        panel.refresh(shown);
    }

    AccessPanel panel() {
        return panel;
    }

    void render(GuiGraphics g, Font font, int mouseX, int mouseY) {
        if (inkVersion != GuiThemes.version()) {
            inkVersion = GuiThemes.version();
            panel.ink();
        }
        if (hint != null) {
            hint.draw(g, font, left + ACCESS_HINT.x(), top + ACCESS_HINT.y(), GuiPaint.MUTED);
        }
        if (noticeVersion != menu.accessVersion()) {
            noticeVersion = menu.accessVersion();
            String key = menu.accessNotice();
            notice = key.isEmpty() ? null
                    : GuiText.fit(font, TvScreenText.key("access.notice." + key, menu.accessArg()), ACCESS_NOTICE.w());
            noticeRefused = key.startsWith("refused.");
        }
        if (notice != null) {
            GuiText.draw(g, font, notice, left + ACCESS_NOTICE.x(), top + ACCESS_NOTICE.y(),
                    noticeRefused ? GuiPaint.WARN : GuiPaint.OK);
        }
        panel.render(g, font, mouseX, mouseY);
    }

    /** As listas de sugestões, por cima dos campos (a tela chama depois de desenhar os widgets). */
    void renderOverlay(GuiGraphics g, Font font) {
        panel.renderOverlay(g, font);
    }

    boolean mouseClicked(double mouseX, double mouseY) {
        return panel.mouseClicked(mouseX, mouseY);
    }

    void mouseScrolled(double mouseY, double scrollY) {
        panel.mouseScrolled(mouseY, scrollY > 0 ? -1 : 1);
    }

    /**
     * Teclas com o cursor num dos campos: as da lista de sugestões, o Esc solta o campo e o resto vai ao campo. Nenhuma
     * fecha a tela enquanto se digita (nem a do inventário).
     */
    boolean keyPressed(@Nullable EditBox focused, int keyCode, int scanCode, int modifiers, Runnable unfocus) {
        if (focused == null || !focused.isFocused()) {
            return false;
        }
        if (!panel.keyPressed(focused, keyCode)) {
            if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
                unfocus.run();
            } else {
                focused.keyPressed(keyCode, scanCode, modifiers);
            }
        }
        return true;
    }

    @Nullable Component tooltip(int mouseX, int mouseY) {
        return panel.tooltip(mouseX, mouseY);
    }

    /** As medidas do painel na aba, lidas a cada uso: com times, menos linhas de convidados. */
    private final class Frame implements AccessPanel.Frame {
        @Override
        public int x() {
            return left + ACCESS_HINT.x();
        }

        @Override
        public int labelY() {
            return top + ACCESS_LABEL_Y;
        }

        @Override
        public int width() {
            return ACCESS_HINT.w();
        }

        @Override
        public int guestRows() {
            return AccessPanel.withTeams() ? TEAM_GUEST_ROWS : ONLY_GUEST_ROWS;
        }

        @Override
        public int teamRows() {
            return TEAM_ROWS;
        }

        @Override
        public int bottom() {
            return top + H - 8;
        }
    }

    /** A lista é a do menu; as ações saem pelo {@link TvScreenActions}. */
    private final class Host implements AccessHost {
        @Override
        public AccessView access() {
            return menu.access();
        }

        @Override
        public SuggestionsMenu menu() {
            return menu;
        }

        @Override
        public void member(@Nullable UUID id, String text, boolean add) {
            actions.access(add ? Action.ADD_PLAYER : Action.REMOVE_PLAYER, id, text);
        }

        @Override
        public void team(@Nullable UUID id, String text, boolean add) {
            actions.access(add ? Action.ADD_TEAM : Action.REMOVE_TEAM, id, text);
        }

        @Override
        public void suggest(NetworkSuggestPayload.Kind kind, String text) {
            actions.access(kind == NetworkSuggestPayload.Kind.PLAYER ? Action.SUGGEST_PLAYER : Action.SUGGEST_TEAM,
                    null, text);
        }
    }
}
