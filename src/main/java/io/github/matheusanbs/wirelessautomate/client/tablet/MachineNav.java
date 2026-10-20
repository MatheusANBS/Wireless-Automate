package io.github.matheusanbs.wirelessautomate.client.tablet;

import static io.github.matheusanbs.wirelessautomate.client.tablet.TabletText.plural;
import static io.github.matheusanbs.wirelessautomate.client.tablet.TabletText.tr;

import io.github.matheusanbs.wirelessautomate.client.GuiPaint;
import io.github.matheusanbs.wirelessautomate.client.highlight.HighlightRenderer;
import io.github.matheusanbs.wirelessautomate.client.tablet.ListLayout.Hot;
import io.github.matheusanbs.wirelessautomate.client.tablet.ListLayout.Kind;
import io.github.matheusanbs.wirelessautomate.menu.TabletMachineRows;
import io.github.matheusanbs.wirelessautomate.menu.TabletRows.MachineLive;
import io.github.matheusanbs.wirelessautomate.menu.TreeOrder;
import io.github.matheusanbs.wirelessautomate.network.NodeIndex.NodeKey;
import io.github.matheusanbs.wirelessautomate.packet.SelectTargetPayload;
import io.github.matheusanbs.wirelessautomate.packet.TabletMachinesPayload.Action;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

/**
 * O mouse, o teclado e as dicas da visão por máquinas na Lista do Tablet. Clicar numa máquina escolhe o roteador
 * dela ({@code TabletSelectNodePayload}) e a põe como alvo do painel ({@link SelectTargetPayload}: a presa ou a
 * máquina), pelo mesmo caminho da árvore ({@link TreeNav}); o cabeçalho abre e recolhe o bloco, o "mais N" mostra
 * todas e o "Mostrar menos" volta a poucas (só o {@code TabletMachinesPayload}: a página nova vem do servidor); o Olho
 * acende a máquina no mundo. ↑ ↓ marcam as linhas, Enter faz o que o clique faria, ← → o que o {@link MachineLines}
 * decide. Os ganchos públicos são do e2e.
 */
public final class MachineNav {
    /** Uma linha que sobrevive às páginas novas: o bloco, o roteador e o índice (−1 na presa, −2 no cabeçalho, −3 no "mais N"). */
    private record Key(ResourceLocation block, @Nullable NodeKey router, int index) {
    }

    private static final int HEADER = -2;
    private static final int MORE = -3;

    private final TabletList list;
    private @Nullable Key cursor;
    private @Nullable Key reveal;
    private @Nullable TabletMachineRows foundIn;
    private @Nullable Key foundKey;
    private int foundLine = -1;

    MachineNav(TabletList list) {
        this.list = list;
    }

    private TabletMachineRows page() {
        return list.snapshot().machineRows();
    }

    private MachineLines lines() {
        list.machineRows.rows();
        return list.machineRows.texts.lines;
    }

    /** Larga o cursor (o jogador voltou à busca). */
    void clear() {
        cursor = null;
    }

    boolean hasCursor() {
        return cursor != null && find(cursor) >= 0;
    }

    boolean isCursor(int line) {
        return cursor != null && line == find(cursor);
    }

    private Key keyOf(int line) {
        List<TabletMachineRows.Row> rows = page().rows();
        ResourceLocation block = blockOf(rows, line);
        return switch (rows.get(line)) {
            case TabletMachineRows.Block header -> new Key(block, null, HEADER);
            case TabletMachineRows.Machine machine -> new Key(block, page().routers().get(machine.router()).key(),
                    machine.attached() ? -1 : machine.index());
            case TabletMachineRows.More more -> new Key(block, null, MORE);
        };
    }

    private static ResourceLocation blockOf(List<TabletMachineRows.Row> rows, int line) {
        for (int at = line; at >= 0; at--) {
            switch (rows.get(at)) {
                case TabletMachineRows.Block block -> {
                    return block.block();
                }
                case TabletMachineRows.More more -> {
                    return more.block();
                }
                default -> {
                }
            }
        }
        throw new IllegalStateException("máquina sem bloco");
    }

    /** A linha da chave na página de agora, ou -1; guardada até a página ou a chave mudarem. */
    private int find(Key key) {
        TabletMachineRows page = page();
        if (page == foundIn && key.equals(foundKey)) {
            return foundLine;
        }
        foundIn = page;
        foundKey = key;
        foundLine = -1;
        for (int i = 0; i < page.rows().size(); i++) {
            if (keyOf(i).equals(key)) {
                foundLine = i;
                break;
            }
        }
        return foundLine;
    }

    /** A linha a pôr na vista, ou -1; só uma vez. */
    int takeReveal() {
        if (reveal == null) {
            return -1;
        }
        int line = find(reveal);
        reveal = null;
        return line;
    }

    // ------------------------------------------------------------------ escolher

    /** O que o clique ou o Enter fazem na linha: abrir ou recolher o bloco, escolher a máquina, mostrar todas. */
    void activate(int line) {
        switch (page().rows().get(line)) {
            case TabletMachineRows.Block block -> {
                cursor = keyOf(line);
                send(block.collapsed() ? Action.OPEN_BLOCK : Action.CLOSE_BLOCK, block.block());
            }
            case TabletMachineRows.Machine machine -> choose(line, machine);
            case TabletMachineRows.More more -> send(Action.SHOW_ALL, more.block());
        }
    }

    private void choose(int line, TabletMachineRows.Machine machine) {
        NodeKey router = page().routers().get(machine.router()).key();
        int id = list.actions.containerId();
        cursor = keyOf(line);
        list.choose(router);
        list.revealKey = null;
        list.actions.send(machine.attached() ? SelectTargetPayload.attached(id)
                : SelectTargetPayload.machine(id, machine.index(), BlockPos.of(machine.pos())));
    }

    private void send(Action action, ResourceLocation block) {
        list.actions.machines(action, Optional.of(block));
    }

    /**
     * As áreas da visão por máquinas. No modo Selecionar, o clique no cabeçalho ou na máquina marca ou desmarca
     * ({@link MarkRows}); o ▸/▾ do cabeçalho continua abrindo e recolhendo.
     */
    void click(Hot hot) {
        TabletMachineRows.Row row = page().rows().get(hot.arg());
        switch (hot.kind()) {
            case MACHINE_EYE -> {
                if (row instanceof TabletMachineRows.Machine machine) {
                    NodeKey router = page().routers().get(machine.router()).key();
                    HighlightRenderer.toggle(router.dimension(), BlockPos.of(machine.pos()), router.pos());
                }
            }
            case MACHINE_FEW -> {
                if (row instanceof TabletMachineRows.Block block) {
                    send(Action.SHOW_FEW, block.block());
                }
            }
            case MACHINE -> {
                if (list.multi && !(row instanceof TabletMachineRows.More)) {
                    list.markRows.clickMachine(hot.arg());
                } else {
                    activate(hot.arg());
                }
            }
            case MACHINE_OPEN -> activate(hot.arg());
            default -> {
            }
        }
    }

    // ------------------------------------------------------------------ teclado

    /** A linha onde as setas estão: o cursor, ou a máquina que é o alvo do painel. */
    private int current() {
        int line = cursor == null ? -1 : find(cursor);
        return line >= 0 ? line : list.machineRows.targetLine();
    }

    void upDown(int direction) {
        int next = lines().next(current(), direction);
        if (next >= 0) {
            cursor = keyOf(next);
            reveal = cursor;
        }
    }

    /** Enter na linha marcada; {@code false} sem cursor (o Enter do nó é outro). */
    boolean enter() {
        int line = cursor == null ? -1 : find(cursor);
        if (line < 0) {
            return false;
        }
        activate(line);
        return true;
    }

    void arrow(boolean left) {
        MachineLines lines = lines();
        int index = current();
        MachineLines.Arrow arrow = left ? lines.left(index) : lines.right(index);
        if (arrow.step() == MachineLines.Step.NONE) {
            return;
        }
        ResourceLocation block = blockOf(page().rows(), arrow.line());
        switch (arrow.step()) {
            case OPEN_BLOCK -> send(Action.OPEN_BLOCK, block);
            case CLOSE_BLOCK -> send(Action.CLOSE_BLOCK, block);
            case SHOW_ALL -> send(Action.SHOW_ALL, block);
            case MOVE_TO -> {
                cursor = keyOf(arrow.line());
                reveal = cursor;
            }
            case NONE -> {
            }
        }
    }

    // ------------------------------------------------------------------ dicas

    @Nullable Component tooltip(Hot hot) {
        TabletMachineRows.Row row = page().rows().get(hot.arg());
        return switch (hot.kind()) {
            case MACHINE_EYE -> row instanceof TabletMachineRows.Machine machine
                    && HighlightRenderer.isLit(page().routers().get(machine.router()).key().dimension(), machine.pos())
                    ? Component.translatable("gui.wirelessautomate.highlight.eye.off.tooltip")
                    : Component.translatable("gui.wirelessautomate.highlight.eye.on.tooltip");
            case MACHINE_FEW -> tr("machines.show_few.tooltip", TreeOrder.FEW);
            case MACHINE_OPEN -> row instanceof TabletMachineRows.Block block
                    ? tr(block.collapsed() ? "machines.block.open" : "machines.block.close") : null;
            case MACHINE -> switch (row) {
                case TabletMachineRows.Block block -> blockTooltip(block, hot.arg());
                case TabletMachineRows.Machine machine -> machineTooltip(machine, hot.arg());
                case TabletMachineRows.More more -> tr("machines.more.tooltip");
            };
            default -> null;
        };
    }

    private Component blockTooltip(TabletMachineRows.Block block, int r) {
        MachineRowTexts texts = list.machineRows.texts;
        MutableComponent text = texts.names[r].copy().append("\n")
                .append(texts.seconds[r].copy().withColor(GuiPaint.TOOLTIP_MUTED));
        if (block.hidden() > 0) {
            text.append("\n").append(plural("machines.hidden", block.hidden()).copy().withColor(GuiPaint.WARN));
        }
        return text.append("\n").append(tr(block.collapsed() ? "machines.block.open" : "machines.block.close").copy()
                .withColor(GuiPaint.TOOLTIP_MUTED));
    }

    /** O nome, a segunda linha, se segue o perfil, o estado, a vazão por extenso e o que o clique faz. */
    private Component machineTooltip(TabletMachineRows.Machine machine, int r) {
        MachineRowTexts texts = list.machineRows.texts;
        MutableComponent text = texts.names[r].copy();
        BlockPos pos = BlockPos.of(machine.pos());
        Component where = Component.literal(texts.blockName(r) + " · " + pos.getX() + " " + pos.getY() + " " + pos.getZ());
        text.append("\n").append(where.copy().withColor(GuiPaint.TOOLTIP_MUTED));
        text.append("\n").append(texts.seconds[r].copy().withColor(GuiPaint.TOOLTIP_MUTED));
        if (!machine.attached()) {
            text.append("\n").append(Component.translatable("gui.wirelessautomate.router.range.machine."
                    + (machine.exception() ? "exception" : "follows")).copy().withColor(GuiPaint.TOOLTIP_MUTED));
        }
        MachineLive live = texts.lives[r];
        if (live != null) {
            text.append(machine.attached() ? MachineRowTexts.nodeRateTooltip(live) : TreeTexts.rateTooltip(live));
        }
        return text.append("\n").append(tr(machine.attached() ? "machines.attached.tooltip" : "machines.machine.tooltip")
                .copy().withColor(GuiPaint.TOOLTIP_MUTED));
    }

    // ------------------------------------------------------------------ ganchos do e2e

    /** Centro da linha da máquina {@code index} (−1: a presa) do roteador, ou {@code null} fora da vista. */
    public int @Nullable [] machineCenter(NodeKey router, int index) {
        return center(Kind.MACHINE, line(r -> r instanceof TabletMachineRows.Machine m
                && Objects.equals(page().routers().get(m.router()).key(), router) && (m.attached() ? -1 : m.index()) == index));
    }

    /** Centro do cabeçalho do bloco, ou {@code null}. */
    public int @Nullable [] blockCenter(ResourceLocation block) {
        return center(Kind.MACHINE, line(r -> r instanceof TabletMachineRows.Block b && b.block().equals(block)));
    }

    /** Centro do "mais N" do bloco, ou {@code null}. */
    public int @Nullable [] moreCenter(ResourceLocation block) {
        return center(Kind.MACHINE, line(r -> r instanceof TabletMachineRows.More m && m.block().equals(block)));
    }

    /** Centro do "Mostrar menos" no cabeçalho do bloco com todas à vista, ou {@code null}. */
    public int @Nullable [] fewCenter(ResourceLocation block) {
        return center(Kind.MACHINE_FEW, line(r -> r instanceof TabletMachineRows.Block b && b.block().equals(block)));
    }

    /** Centro do seletor: Máquinas ({@code true}) ou Roteadores. */
    public int @Nullable [] viewCenter(boolean machines) {
        return center(Kind.VIEW, machines ? 1 : 0);
    }

    private int line(java.util.function.Predicate<TabletMachineRows.Row> test) {
        List<TabletMachineRows.Row> rows = page().rows();
        for (int i = 0; i < rows.size(); i++) {
            if (test.test(rows.get(i))) {
                return i;
            }
        }
        return -1;
    }

    private int @Nullable [] center(Kind kind, int arg) {
        if (arg < 0) {
            return null;
        }
        list.arrangeNow();
        Hot hot = list.layoutHot(kind, arg);
        return hot == null ? null : hot.center();
    }
}
