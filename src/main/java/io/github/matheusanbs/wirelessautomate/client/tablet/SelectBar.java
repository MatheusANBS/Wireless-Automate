package io.github.matheusanbs.wirelessautomate.client.tablet;

import static io.github.matheusanbs.wirelessautomate.client.tablet.TabletLayout.ROW_H;
import static io.github.matheusanbs.wirelessautomate.client.tablet.TabletLayout.X0;
import static io.github.matheusanbs.wirelessautomate.client.tablet.TabletText.tr;
import static io.github.matheusanbs.wirelessautomate.client.tablet.TabletText.typeName;

import io.github.matheusanbs.wirelessautomate.client.FlatButton;
import io.github.matheusanbs.wirelessautomate.client.highlight.HighlightRenderer;
import io.github.matheusanbs.wirelessautomate.client.highlight.MachineHighlights;
import io.github.matheusanbs.wirelessautomate.menu.TabletMenu;
import io.github.matheusanbs.wirelessautomate.menu.TabletSnapshot;
import io.github.matheusanbs.wirelessautomate.menu.TabletSnapshot.NetworkView;
import io.github.matheusanbs.wirelessautomate.network.LoadedTypes;
import io.github.matheusanbs.wirelessautomate.network.ResourceType;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

/**
 * Os botões fixos da aba Lista: Selecionar e, selecionando, o tipo, a rede e o Mover, o Todos e o Colar nos
 * marcados; fora da seleção e com alguma máquina acesa no mundo, o "Apagar todas (N)" ao lado do Selecionar
 * ({@link HighlightRenderer}). Guarda a escolha do Mover (tipo e rede).
 */
final class SelectBar {
    /** Tipos que o Mover oferece, depois de "Todas as abas". */
    private static final List<ResourceType> MOVE_TYPES = LoadedTypes.LIST;

    private final TabletLayout layout;
    private final Supplier<TabletSnapshot> snapshot;
    private final NodeText nodes;
    private final TabletActions actions;
    private final TabletList list;
    /** Índice em {@link #MOVE_TYPES}; -1 = todas as abas. */
    private int moveType = -1;
    private Optional<UUID> moveNetwork = Optional.empty();
    private boolean moveNetworkChosen;
    // [Selecionar, tipo, rede, Mover, Todos, Colar nos marcados, Apagar todas]
    private final List<FlatButton> buttons = new ArrayList<>();
    /** A versão das acesas que o texto do Apagar todas mostra. */
    private int litVersion = Integer.MIN_VALUE;
    /** O texto do Colar nos marcados, a versão dos marcados e a largura do botão em que ele foi escolhido. */
    private Component pasteText = Component.empty();
    private int pasteVersion = -1;
    private int pasteWidth = -1;
    /** A borda direita do Selecionar e as larguras dele fora e dentro da seleção. */
    private int selectRight;
    /** A borda esquerda da lista, de onde a Grade recalcula a posição dos botões da direita. */
    private int base;
    private int clearLitWidth;
    private int selectWidth;
    private int doneWidth;

    SelectBar(TabletLayout layout, Supplier<TabletSnapshot> snapshot, NodeText nodes, TabletActions actions,
            TabletList list) {
        this.layout = layout;
        this.snapshot = snapshot;
        this.nodes = nodes;
        this.actions = actions;
        this.list = list;
    }

    private TabletSnapshot snapshot() {
        return snapshot.get();
    }

    void addButtons(TabletScreen.Buttons adder, Font font) {
        buttons.clear();
        int x = layout.left;
        int lw = list.splitListWidth();
        base = x + X0;
        int selectW = Math.max(56, font.width(tr("select")) + 10);
        // selecionando, o botão encolhe ao "Concluir" e a sobra vai para o Colar nos marcados
        int doneW = Math.min(selectW, font.width(tr("select.done")) + 8);
        selectRight = x + X0 + lw;
        selectWidth = selectW;
        doneWidth = doneW;
        add(adder, new FlatButton(x + X0 + lw - selectW, list.barY(), selectW, ROW_H, tr("select"),
                (g, b, hovered) -> TabletButtons.paintToggle(g, b, hovered, list.multi() ? tr("select.done") : tr("select"),
                        list.multi()),
                list::toggleMulti).tooltip(() -> tr("select.tooltip")));
        // barra de mover (só selecionando): tipo, rede e Mover
        int cycleW = (lw - 4) * 36 / 100;
        add(adder, new FlatButton(x + X0, list.multiBarY(), cycleW, ROW_H, Component.empty(),
                (g, b, hovered) -> TabletButtons.paintCycle(g, b, hovered, moveTypeText()),
                () -> moveType = Math.floorMod(moveType + 1 + (Screen.hasShiftDown() ? -1 : 1), MOVE_TYPES.size() + 1) - 1)
                .tooltip(() -> tr("move.type.tooltip")));
        add(adder, new FlatButton(x + X0 + cycleW + 2, list.multiBarY(), cycleW, ROW_H, Component.empty(),
                (g, b, hovered) -> TabletButtons.paintCycle(g, b, hovered, nodes.networkName(effectiveMoveNetwork())),
                () -> cycleMoveNetwork(Screen.hasShiftDown() ? -1 : 1)).tooltip(() -> tr("move.network.tooltip")));
        add(adder, new FlatButton(x + X0 + 2 * cycleW + 4, list.multiBarY(), lw - 2 * cycleW - 4, ROW_H, tr("move"),
                (g, b, hovered) -> TabletButtons.paintPrimary(g, b, hovered, tr("move")), this::moveSelected)
                .tooltip(() -> tr("move.marked.tooltip", list.markRows.what())));
        int allW = font.width(tr("select.all")) + 8;
        add(adder, new FlatButton(x + X0, list.barY(), allW, ROW_H, tr("select.all"),
                (g, b, hovered) -> TabletButtons.paintText(g, b, hovered, tr("select.all")), list::selectAll)
                .tooltip(() -> tr(list.machineView() ? "select.all.machines.tooltip" : "select.all.tooltip")));
        // colar o perfil nos marcados (só com perfil copiado)
        add(adder, new FlatButton(x + X0 + allW + 2, list.barY(), lw - allW - doneW - 4, ROW_H, Component.empty(),
                (g, b, hovered) -> TabletButtons.paintClipped(g, b, hovered, pasteText(b.getWidth() - 8)),
                list.profileActions::pasteSelected).tooltip(() -> snapshot().profile()
                        .map(p -> tr("list.paste_marked.tooltip", p.sourceName(), list.markRows.what())).orElse(null)));
        int clearW = clearLitWidth(font);
        clearLitWidth = clearW;
        add(adder, new FlatButton(x + X0 + lw - selectW - 2 - clearW, list.barY(), clearW, ROW_H, clearLitText(),
                (g, b, hovered) -> TabletButtons.paintText(g, b, hovered, b.getMessage()), HighlightRenderer::clear)
                .tooltip(() -> Component.translatable("gui.wirelessautomate.highlight.clear_all.tooltip")));
        litVersion = HighlightRenderer.version();
    }

    /** A largura do "Apagar todas (N)", pelo maior N: o botão não muda de tamanho quando as acesas mudam. */
    static int clearLitWidth(Font font) {
        return font.width(Component.translatable("gui.wirelessautomate.highlight.clear_all.count",
                MachineHighlights.LIMIT)) + 10;
    }

    private static Component clearLitText() {
        return Component.translatable("gui.wirelessautomate.highlight.clear_all.count", HighlightRenderer.count());
    }

    private void add(TabletScreen.Buttons adder, FlatButton button) {
        buttons.add(adder.add(TabletScreen.Tab.LIST, button));
    }

    void refresh(boolean shown) {
        FlatButton select = buttons.get(0);
        boolean grid = list.gridView();
        int selectW = list.multi() ? doneWidth : selectWidth;
        if (select.getWidth() != selectW) {
            select.setWidth(selectW);
            select.setX(selectRight - selectW);
        }
        // a Grade não marca nem move: sem o Selecionar, e o Apagar todas passa para a ponta direita dela
        select.visible = shown && !grid;
        buttons.get(6).setX(grid ? base + list.listWidth() - clearLitWidth
                : selectRight - selectWidth - 2 - clearLitWidth);
        for (int i = 1; i <= 4; i++) {
            buttons.get(i).visible = shown && list.multi();
        }
        buttons.get(3).active = !list.marks.isEmpty();
        FlatButton pasteSelected = buttons.get(5);
        pasteSelected.visible = shown && list.multi() && snapshot().profile().isPresent();
        pasteSelected.active = !list.marks.isEmpty();
        FlatButton clearLit = buttons.get(6);
        clearLit.visible = shown && !list.multi() && HighlightRenderer.count() > 0;
        if (litVersion != HighlightRenderer.version()) {
            litVersion = HighlightRenderer.version();
            clearLit.setMessage(clearLitText());
        }
    }

    /** Centro do "Colar nos marcados", ou {@code null} se ele não aparece. */
    int @Nullable [] pasteCenter() {
        FlatButton paste = buttons.size() > 5 ? buttons.get(5) : null;
        return paste == null || !paste.visible ? null
                : new int[] {paste.getX() + paste.getWidth() / 2, paste.getY() + paste.getHeight() / 2};
    }

    /** Snapshot novo: sem rede escolhida no Mover, vale a ativa. */
    void onSnapshot(TabletSnapshot s) {
        if (!moveNetworkChosen) {
            moveNetwork = s.activeNetwork();
        }
    }

    /**
     * O texto do Colar nos marcados que cabe em {@code room}: "Colar nos marcados (2 roteadores, 3 máquinas)",
     * "Colar (5)" ou "Colar" (a dica diz tudo); escolhido de novo só quando os marcados ou a largura mudam.
     */
    private Component pasteText(int room) {
        int version = list.marks.version();
        if (version != pasteVersion || room != pasteWidth) {
            pasteVersion = version;
            pasteWidth = room;
            Font font = Minecraft.getInstance().font;
            Component full = tr("list.paste_marked", list.markRows.what());
            Component count = tr("list.paste_marked.short", list.marks.routers() + list.marks.machineCount());
            pasteText = font.width(full) <= room ? full : font.width(count) <= room ? count : tr("list.paste.short");
        }
        return pasteText;
    }

    private Component moveTypeText() {
        return moveType < 0 ? tr("move.type.all") : typeName(MOVE_TYPES.get(moveType));
    }

    private Optional<UUID> effectiveMoveNetwork() {
        return moveNetwork.filter(id -> snapshot().network(id).isPresent());
    }

    private void cycleMoveNetwork(int direction) {
        // as redes em que o jogador pode pôr nós: as dele, as públicas e, para operador, todas
        List<NetworkView> networks = snapshot().networks();
        // posições: 0..n-1 as redes, n = sem rede
        int current = networks.size();
        Optional<UUID> chosen = effectiveMoveNetwork();
        for (int i = 0; i < networks.size(); i++) {
            if (chosen.isPresent() && networks.get(i).id().equals(chosen.get())) {
                current = i;
            }
        }
        int next = Math.floorMod(current + direction, networks.size() + 1);
        moveNetwork = next == networks.size() ? Optional.empty() : Optional.of(networks.get(next).id());
        moveNetworkChosen = true;
    }

    private void moveSelected() {
        if (list.marks.isEmpty()) {
            return;
        }
        actions.moveNodes(list.marks.nodeList(TabletMenu.MAX_MOVE), list.marks.payload(),
                moveType < 0 ? Optional.empty() : Optional.of(MOVE_TYPES.get(moveType)), effectiveMoveNetwork());
        list.toggleMulti();
    }
}
