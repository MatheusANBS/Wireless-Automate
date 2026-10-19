package io.github.matheusanbs.wirelessautomate.client.router;

import io.github.matheusanbs.wirelessautomate.client.GuiPaint;
import io.github.matheusanbs.wirelessautomate.client.GuiThemes;
import io.github.matheusanbs.wirelessautomate.menu.RangeView;
import io.github.matheusanbs.wirelessautomate.menu.RouterSnapshot;
import io.github.matheusanbs.wirelessautomate.packet.RenameRouterPayload;
import io.github.matheusanbs.wirelessautomate.packet.RenameTargetPayload;
import io.github.matheusanbs.wirelessautomate.range.EditTarget;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.glfw.GLFW;

/**
 * A caixa de texto do nome de um grupo ou do rótulo de uma máquina do alcance, posta pela dona no lugar do
 * nome (a linha da lista das máquinas ou a pílula da linha Editando), como o renomear do roteador no
 * {@link RouterHeader}: começa com o nome atual, aceita até {@link RenameRouterPayload#MAX_LENGTH} caracteres,
 * Enter e o clique fora salvam e Esc desiste. Só o nome que mudou sai, pelo {@link Commit}.
 *
 * <p>O campo não é um widget da tela: a dona repassa teclas, letras, cliques e o desenho, e o põe no lugar a cada
 * quadro pelo {@link #place}. Fechada, não desenha nem responde. Só cliente.
 */
final class LabelEditor {
    /** O nome novo de {@code target}; {@code machinePos} é a máquina da edição, para o servidor conferir. */
    interface Commit {
        void rename(EditTarget target, @Nullable BlockPos machinePos, String name);
    }

    /** Altura da moldura: uma linha de texto com 1 px em cima e embaixo. */
    static final int H = 10;

    private final Commit commit;
    private @Nullable EditBox box;
    private @Nullable EditTarget target;
    private @Nullable BlockPos machinePos;
    private String original = "";
    /** O nome de quando o campo está vazio, na cor apagada; tingido de novo quando o tema muda. */
    private Component hint = Component.empty();
    private int inkVersion = -1;
    private int x;
    private int y;
    private int w;

    LabelEditor(Commit commit) {
        this.commit = commit;
    }

    private static Component tr(String key) {
        return Component.translatable("gui.wirelessautomate.router." + key);
    }

    boolean editing() {
        return target != null;
    }

    /** A máquina da edição, guardada ao abrir ({@code null} num grupo ou com a caixa fechada). */
    @Nullable BlockPos machinePos() {
        return target == null ? null : machinePos;
    }

    /** O alvo em edição, ou {@code null} com a caixa fechada. */
    @Nullable EditTarget target() {
        return target;
    }

    /**
     * Abre a caixa para {@code target} com {@code current} dentro (vazio = sem nome dado) e {@code hint}, o nome de
     * quando ele está vazio, na cor apagada.
     */
    void start(EditTarget target, @Nullable BlockPos machinePos, String current, Component hint) {
        if (box == null) {
            box = new EditBox(Minecraft.getInstance().font, 0, 0, 10, 9, tr("range.rename.narration"));
            box.setBordered(false);
            box.setMaxLength(RenameRouterPayload.MAX_LENGTH);
            box.setTextShadow(false); // tinta sobre porcelana, sem sombra
        }
        this.target = target;
        this.machinePos = machinePos;
        this.original = current;
        box.setValue(current);
        box.moveCursorToEnd(false);
        box.setFocused(true);
        this.hint = hint;
        inkVersion = -1;
    }

    /** A moldura no retângulo {@code x}, {@code y}, {@code w} × {@link #H} (a dona chama a cada quadro). */
    void place(int x, int y, int w) {
        this.x = x;
        this.y = y;
        this.w = Math.max(12, w);
        if (box != null) {
            box.setX(x + 3);
            box.setY(y + 1);
            box.setWidth(this.w - 5);
        }
    }

    /** Fecha a caixa; com {@code save}, manda o nome se ele mudou (vazio tira o nome). */
    void finish(boolean save) {
        if (target == null || box == null) {
            return;
        }
        EditTarget edited = target;
        target = null;
        box.setFocused(false);
        String name = RenameRouterPayload.clean(box.getValue());
        if (save && !name.equals(original)) {
            commit.rename(edited, machinePos, name);
        }
    }

    private boolean inside(double mx, double my) {
        return mx >= x && mx < x + w && my >= y && my < y + H;
    }

    /** Uma tecla com a caixa aberta: Esc desiste, Enter salva, o resto vai para o campo. Aberta, engole todas. */
    boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (!editing() || box == null) {
            return false;
        }
        if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
            finish(false);
        } else if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) {
            finish(true);
        } else {
            box.keyPressed(keyCode, scanCode, modifiers);
        }
        // nenhuma tecla fecha a tela enquanto se digita (nem a do inventário)
        return true;
    }

    boolean charTyped(char codePoint, int modifiers) {
        if (!editing() || box == null) {
            return false;
        }
        box.charTyped(codePoint, modifiers);
        return true;
    }

    /**
     * Um clique com a caixa aberta: dentro dela, move o cursor e é engolido ({@code true}); fora, salva e
     * deixa o clique seguir ({@code false}).
     */
    boolean mouseClicked(double mx, double my, int button) {
        if (!editing() || box == null) {
            return false;
        }
        if (inside(mx, my)) {
            box.mouseClicked(mx, my, button);
            return true;
        }
        finish(true);
        return false;
    }

    void render(GuiGraphics g, int mouseX, int mouseY) {
        if (!editing() || box == null) {
            return;
        }
        GuiPaint.box(g, x, y, w, H, GuiPaint.INSET, GuiPaint.ACCENT);
        // a cor do EditBox é do widget: segue o tema quando ele muda
        if (inkVersion != GuiThemes.version()) {
            inkVersion = GuiThemes.version();
            GuiPaint.ink(box, hint);
        }
        box.render(g, mouseX, mouseY, 0f);
    }

    /** A dica da caixa aberta sob o mouse: como salvar e desistir. */
    @Nullable Component tooltip(double mx, double my) {
        return editing() && inside(mx, my) ? tr("range.rename.hint") : null;
    }

    // ------------------------------------------------------------------ o pedido

    /** O pedido ao servidor para dar {@code name} ao alvo {@code target} da tela {@code containerId}. */
    static RenameTargetPayload payload(int containerId, EditTarget target, @Nullable BlockPos machinePos, String name) {
        return new RenameTargetPayload(containerId, target.kind(), target.id(), Optional.ofNullable(machinePos), name);
    }

    /** Captura sem servidor: o snapshot com o nome do pedido no grupo ou na máquina dele. */
    static RouterSnapshot renamed(RouterSnapshot snapshot, RenameTargetPayload p) {
        RangeView range = snapshot.range();
        List<RangeView.GroupView> groups = new ArrayList<>(range.groups());
        List<RangeView.MachineView> machines = new ArrayList<>(range.machines());
        if (p.kind() == EditTarget.Kind.GROUP) {
            groups.replaceAll(g -> g.id() == p.id()
                    ? new RangeView.GroupView(g.id(), g.block(), p.name(), g.count(), g.exceptions()) : g);
        } else if (p.kind() == EditTarget.Kind.MACHINE && p.id() >= 0 && p.id() < machines.size()) {
            RangeView.MachineView m = machines.get(p.id());
            machines.set(p.id(), new RangeView.MachineView(m.pos(), m.group(), m.distance(), m.exception(), m.state(),
                    m.replacedBy(), p.name()));
        }
        return snapshot.withRange(new RangeView(range.radius(), range.max(), List.copyOf(groups), List.copyOf(machines),
                range.target(), range.face()));
    }
}
