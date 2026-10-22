package io.github.matheusanbs.wirelessautomate.client.tablet;

import static io.github.matheusanbs.wirelessautomate.client.tablet.TabletText.plural;
import static io.github.matheusanbs.wirelessautomate.client.tablet.TabletText.tr;

import io.github.matheusanbs.wirelessautomate.client.GuiPaint;
import io.github.matheusanbs.wirelessautomate.client.GuiText;
import io.github.matheusanbs.wirelessautomate.menu.TabletMenu;
import io.github.matheusanbs.wirelessautomate.packet.NetworkSuggestPayload;
import io.github.matheusanbs.wirelessautomate.packet.NetworkSuggestionsPayload;
import io.github.matheusanbs.wirelessautomate.packet.NetworkSuggestionsPayload.Suggestion;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.glfw.GLFW;

/**
 * Um campo de texto com lista de sugestões (convidar jogador, adicionar time). O servidor responde à consulta: ela
 * sai 150 ms depois da última tecla (nunca por tecla) e ao clicar no campo, e a resposta de um texto que o campo já
 * trocou é descartada. Setas e Enter escolhem, Tab completa, Esc fecha; sem linha escolhida o Enter usa o texto
 * digitado e o servidor resolve o nome. A lista é desenhada por cima das outras partes, pelo {@link #renderList}, e
 * só com o cursor no campo. As linhas (nome e detalhe) são montadas quando a resposta chega, não por quadro.
 */
final class SuggestField {
    static final int ROW_H = 12;
    private static final long DELAY_MS = 150;

    /** Uma linha da lista: o id para o servidor, o nome, o detalhe à direita e se já está na rede. */
    private record Line(UUID id, String text, Component name, @Nullable Component detail, int detailW,
            boolean taken) {
    }

    private final NetworkSuggestPayload.Kind kind;
    private final TabletMenu menu;
    private final TabletActions actions;
    private final Supplier<@Nullable UUID> network;
    private final Component hint;
    private final SuggestNav nav = new SuggestNav();
    private EditBox box;
    private List<Line> lines = List.of();
    /** O texto da última consulta mandada; a resposta só vale se trouxer este. */
    private @Nullable String asked;
    /** Quando o texto mudou e a consulta ainda não saiu (0: nada pendente). */
    private long dirtyAt;
    private int seenVersion;
    private boolean open;
    private boolean quiet;
    /** Onde a lista foi desenhada no último quadro: x, y, largura e altura (para o clique). */
    private final int[] rect = new int[4];

    SuggestField(NetworkSuggestPayload.Kind kind, TabletMenu menu, TabletActions actions,
            Supplier<@Nullable UUID> network, Component hint) {
        this.kind = kind;
        this.menu = menu;
        this.actions = actions;
        this.network = network;
        this.hint = hint;
        this.seenVersion = menu.suggestionsVersion();
    }

    /** O campo, refeito a cada {@code init} da tela, que o põe entre os widgets dela. */
    EditBox createBox(Font font, int x, int y, int width) {
        box = new EditBox(font, x, y, width, 9, hint);
        box.setBordered(false);
        box.setMaxLength(NetworkSuggestPayload.MAX_TEXT);
        box.setTextShadow(false); // tinta sobre porcelana, sem sombra
        box.setResponder(text -> {
            if (!quiet) {
                dirtyAt = System.currentTimeMillis();
                open = true;
            }
        });
        GuiPaint.ink(box, hint);
        return box;
    }

    EditBox box() {
        return box;
    }

    Component hint() {
        return hint;
    }

    /** Quantas linhas a lista tem agora. */
    int count() {
        return lines.size();
    }

    /** A lista está aberta e tem o que mostrar. */
    boolean shown() {
        return open && box != null && box.visible && box.isFocused() && !lines.isEmpty();
    }

    /** O mouse clicou no campo: abre a lista e pede as sugestões para o texto que está nele. */
    void clicked() {
        open = true;
        dirtyAt = System.currentTimeMillis() - DELAY_MS;
    }

    /** Larga o texto, a lista e a consulta pendente (outra rede escolhida, tela refeita, ação mandada). */
    void clear() {
        quiet = true;
        try {
            if (box != null) {
                box.setValue("");
            }
        } finally {
            quiet = false;
        }
        lines = List.of();
        asked = null;
        dirtyAt = 0;
        open = false;
        nav.reset(0);
    }

    /** Manda a consulta pendente (se o prazo passou) e pega a resposta nova; chamado a cada quadro com o campo à vista. */
    void update() {
        UUID net = network.get();
        if (box == null || !box.visible || net == null) {
            return;
        }
        if (dirtyAt != 0 && box.isFocused() && System.currentTimeMillis() - dirtyAt >= DELAY_MS) {
            asked = box.getValue();
            dirtyAt = 0;
            actions.suggest(kind, asked, net);
        }
        if (menu.suggestionsVersion() != seenVersion) {
            seenVersion = menu.suggestionsVersion();
            NetworkSuggestionsPayload reply = menu.suggestions();
            if (reply != null && reply.kind() == kind && reply.text().equals(asked)) {
                build(reply.suggestions());
            }
        }
    }

    private void build(List<Suggestion> suggestions) {
        List<Line> built = new ArrayList<>(suggestions.size());
        Font font = TabletList.font();
        for (Suggestion s : suggestions) {
            Component detail;
            if (s.taken()) {
                detail = tr("access.taken");
            } else if (kind == NetworkSuggestPayload.Kind.PLAYER) {
                detail = s.detail() == 1 ? tr("access.online") : null;
            } else {
                detail = plural("access.members", s.detail());
            }
            built.add(new Line(s.id(), s.name(), Component.literal(s.name()), detail,
                    detail == null ? 0 : font.width(detail), s.taken()));
        }
        lines = built;
        nav.reset(built.size());
    }

    // ------------------------------------------------------------------ entrada

    /** Teclas do campo com o cursor: setas, Tab, Enter e Esc da lista. Devolve se tratou. */
    boolean keyPressed(int keyCode) {
        boolean enter = keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER;
        if (keyCode == GLFW.GLFW_KEY_DOWN && shown()) {
            nav.next();
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_UP && shown()) {
            nav.previous();
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_TAB && shown()) {
            int index = Math.max(0, nav.selected());
            quiet = true;
            try {
                box.setValue(lines.get(index).text());
            } finally {
                quiet = false;
            }
            return true;
        }
        if (enter) {
            submit();
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_ESCAPE && shown()) {
            open = false;
            return true;
        }
        return false;
    }

    /** Enter ou o +: a linha escolhida ou, sem ela, o texto digitado. */
    void submit() {
        if (shown() && nav.selected() >= 0) {
            choose(nav.selected());
            return;
        }
        String text = box.getValue().strip();
        UUID net = network.get();
        if (!text.isEmpty() && net != null) {
            send(net, null, text);
            clear();
        }
    }

    private void choose(int index) {
        UUID net = network.get();
        if (net != null && index >= 0 && index < lines.size() && !lines.get(index).taken()) {
            send(net, lines.get(index).id(), "");
            clear();
        }
    }

    private void send(UUID net, @Nullable UUID id, String text) {
        if (kind == NetworkSuggestPayload.Kind.PLAYER) {
            actions.member(net, id, text, true);
        } else {
            actions.team(net, id, text, true);
        }
    }

    /** O clique na lista. Devolve se tratou (a lista aberta engole o clique em cima dela). */
    boolean mouseClicked(double mouseX, double mouseY) {
        int row = rowAt(mouseX, mouseY);
        if (row < 0) {
            return false;
        }
        choose(row);
        return true;
    }

    /** Move a linha escolhida para a que está sob o mouse. */
    void hover(double mouseX, double mouseY) {
        nav.select(rowAt(mouseX, mouseY));
    }

    private int rowAt(double mouseX, double mouseY) {
        if (!shown() || mouseX < rect[0] || mouseX >= rect[0] + rect[2] || mouseY < rect[1] + 1
                || mouseY >= rect[1] + rect[3] - 1) {
            return -1;
        }
        int row = (int) ((mouseY - rect[1] - 1) / ROW_H);
        return row < lines.size() ? row : -1;
    }

    // ------------------------------------------------------------------ desenho

    /**
     * Desenha a lista debaixo do campo ({@code fieldX}, {@code fieldY}, {@code width}, {@code fieldH}) ou, sem
     * espaço até {@code bottom}, em cima dele.
     */
    void renderList(GuiGraphics g, Font font, int fieldX, int fieldY, int width, int fieldH, int bottom) {
        if (!shown()) {
            return;
        }
        int height = lines.size() * ROW_H + 2;
        int y = fieldY + fieldH + 1;
        if (y + height > bottom) {
            y = fieldY - height - 1;
        }
        rect[0] = fieldX;
        rect[1] = y;
        rect[2] = width;
        rect[3] = height;
        GuiPaint.box(g, fieldX, y, width, height, GuiPaint.INSET, GuiPaint.ACCENT);
        for (int i = 0; i < lines.size(); i++) {
            Line line = lines.get(i);
            int ry = y + 1 + i * ROW_H;
            if (i == nav.selected()) {
                g.fill(fieldX + 1, ry, fieldX + width - 1, ry + ROW_H, GuiPaint.ROW_SELECTED);
            }
            int right = fieldX + width - 4;
            if (line.detail() != null) {
                GuiPaint.textRight(g, font, line.detail(), right, ry + 2,
                        line.taken() ? GuiPaint.DISABLED : GuiPaint.MUTED);
                right -= line.detailW() + 4;
            }
            GuiText.draw(g, font, line.name(), fieldX + 4, ry + 2, right - fieldX - 4,
                    line.taken() ? GuiPaint.DISABLED : GuiPaint.FG);
        }
    }
}
