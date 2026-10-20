package io.github.matheusanbs.wirelessautomate.client.tablet;

import static io.github.matheusanbs.wirelessautomate.client.tablet.TabletText.tr;

import io.github.matheusanbs.wirelessautomate.client.GuiPaint;
import io.github.matheusanbs.wirelessautomate.client.tablet.ListMarks.Box;
import io.github.matheusanbs.wirelessautomate.client.tablet.ListTree.Kind;
import io.github.matheusanbs.wirelessautomate.client.tablet.ListTree.Line;
import io.github.matheusanbs.wirelessautomate.menu.TabletMachineRows;
import io.github.matheusanbs.wirelessautomate.menu.TabletMarks.MachineKey;
import io.github.matheusanbs.wirelessautomate.menu.TabletRows;
import io.github.matheusanbs.wirelessautomate.menu.TabletSnapshot.NodeView;
import io.github.matheusanbs.wirelessautomate.menu.TabletTree.GroupKey;
import io.github.matheusanbs.wirelessautomate.network.NodeIndex.NodeKey;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

/**
 * As caixas do Selecionar nas linhas da Lista do Tablet (etapa 3b): o que cada linha marca e como a caixa dela aparece,
 * na árvore (a presa, o grupo e a máquina) e na visão por máquinas (o cabeçalho do bloco e a máquina), sobre o
 * {@link ListMarks}; e o texto do que o Colar atinge. O grupo de cada linha da árvore e o bloco de cada linha da visão
 * por máquinas são guardados quando as linhas mudam, para o desenho só ler.
 */
final class MarkRows {
    private final TabletList list;
    private @Nullable ListTree treeFor;
    /** Por linha da árvore: a linha do grupo de cima (numa máquina) ou -1. */
    private int[] groupLine = new int[0];
    private @Nullable TabletMachineRows pageFor;
    /** Por linha da visão por máquinas: o bloco dela. */
    private ResourceLocation[] blockOf = new ResourceLocation[0];

    MarkRows(TabletList list) {
        this.list = list;
    }

    private ListMarks marks() {
        return list.marks;
    }

    // ------------------------------------------------------------------ árvore

    private ListTree tree() {
        ListTree tree = list.model.tree();
        if (tree != treeFor) {
            treeFor = tree;
            List<Line> lines = tree.lines();
            groupLine = new int[lines.size()];
            int group = -1;
            for (int i = 0; i < lines.size(); i++) {
                Kind kind = lines.get(i).kind();
                if (kind == Kind.GROUP) {
                    group = i;
                } else if (kind != Kind.MACHINE && kind != Kind.MORE) {
                    group = -1;
                }
                groupLine[i] = kind == Kind.MACHINE ? group : -1;
            }
        }
        return tree;
    }

    private NodeKey nodeOf(Line line) {
        return list.snapshot().nodes().get(line.node()).key();
    }

    private @Nullable GroupKey groupKey(Line groupLineOf) {
        return list.model.row(groupLineOf) instanceof TabletRows.Group group
                ? new GroupKey(nodeOf(groupLineOf), group.id()) : null;
    }

    /** As máquinas à vista abaixo da linha do grupo {@code group}. */
    private List<MachineKey> machinesUnder(int group) {
        List<Line> lines = tree().lines();
        List<MachineKey> keys = new ArrayList<>();
        for (int i = group + 1; i < lines.size() && groupLine[i] == group; i++) {
            if (list.model.row(lines.get(i)) instanceof TabletRows.Machine machine) {
                keys.add(new MachineKey(nodeOf(lines.get(i)), machine.pos()));
            }
        }
        return keys;
    }

    /** A caixa da linha {@code line} da árvore (a do nó vem do {@link #node}). */
    Box treeBox(int line) {
        ListTree tree = tree();
        Line at = tree.lines().get(line);
        return switch (list.model.row(at)) {
            case TabletRows.Attached attached -> marks().machine(MachineKey.attached(nodeOf(at)), null, null)
                    ? Box.ALL : Box.NONE;
            case TabletRows.Group group -> marks().group(new GroupKey(nodeOf(at), group.id()));
            case TabletRows.Machine machine -> {
                int g = groupLine[line];
                GroupKey key = g < 0 ? null : groupKey(tree.lines().get(g));
                yield marks().machine(new MachineKey(nodeOf(at), machine.pos()), key, null) ? Box.ALL : Box.NONE;
            }
            case TabletRows.More more -> Box.NONE;
        };
    }

    Box node(NodeKey key) {
        return marks().node(key);
    }

    /** O clique numa linha da árvore no Selecionar: marca ou desmarca a presa, o grupo ou a máquina. */
    void clickTree(int line) {
        ListTree tree = tree();
        Line at = tree.lines().get(line);
        switch (list.model.row(at)) {
            case TabletRows.Attached attached -> marks().toggleMachine(MachineKey.attached(nodeOf(at)), null, null,
                    List.of());
            case TabletRows.Group group -> marks().toggleGroup(new GroupKey(nodeOf(at), group.id()), group.total(),
                    machinesUnder(line));
            case TabletRows.Machine machine -> {
                int g = groupLine[line];
                marks().toggleMachine(new MachineKey(nodeOf(at), machine.pos()),
                        g < 0 ? null : groupKey(tree.lines().get(g)), null, g < 0 ? List.of() : machinesUnder(g));
            }
            case TabletRows.More more -> {
            }
        }
    }

    // ------------------------------------------------------------------ visão por máquinas

    private TabletMachineRows page() {
        TabletMachineRows page = list.snapshot().machineRows();
        if (page != pageFor) {
            pageFor = page;
            List<TabletMachineRows.Row> rows = page.rows();
            blockOf = new ResourceLocation[rows.size()];
            ResourceLocation block = null;
            for (int i = 0; i < rows.size(); i++) {
                switch (rows.get(i)) {
                    case TabletMachineRows.Block header -> block = header.block();
                    case TabletMachineRows.More more -> block = more.block();
                    case TabletMachineRows.Machine machine -> {
                    }
                }
                blockOf[i] = block;
            }
        }
        return page;
    }

    private MachineKey machineKey(TabletMachineRows page, TabletMachineRows.Machine machine) {
        NodeKey router = page.routers().get(machine.router()).key();
        return machine.attached() ? MachineKey.attached(router) : new MachineKey(router, machine.pos());
    }

    /** As máquinas à vista do bloco da linha {@code row}. */
    private List<MachineKey> machinesOfBlock(TabletMachineRows page, ResourceLocation block) {
        List<MachineKey> keys = new ArrayList<>();
        for (int i = 0; i < page.rows().size(); i++) {
            if (block.equals(blockOf[i]) && page.rows().get(i) instanceof TabletMachineRows.Machine machine) {
                keys.add(machineKey(page, machine));
            }
        }
        return keys;
    }

    Box machineBox(int row) {
        TabletMachineRows page = page();
        return switch (page.rows().get(row)) {
            case TabletMachineRows.Block block -> marks().block(block.block());
            case TabletMachineRows.Machine machine -> marks().machine(machineKey(page, machine), null, blockOf[row])
                    ? Box.ALL : Box.NONE;
            case TabletMachineRows.More more -> Box.NONE;
        };
    }

    /** O clique numa linha da visão por máquinas no Selecionar: marca ou desmarca o bloco ou a máquina. */
    void clickMachine(int row) {
        TabletMachineRows page = page();
        switch (page.rows().get(row)) {
            case TabletMachineRows.Block block -> marks().toggleBlock(block.block(), block.machines(),
                    machinesOfBlock(page, block.block()));
            case TabletMachineRows.Machine machine -> marks().toggleMachine(machineKey(page, machine), null,
                    blockOf[row], machinesOfBlock(page, blockOf[row]));
            case TabletMachineRows.More more -> {
            }
        }
    }

    /** Todos: na visão por máquinas, os blocos da página; na por roteadores, os nós da lista. */
    void selectAll() {
        if (!list.machineView()) {
            List<NodeKey> all = list.snapshot().nodes().stream().map(NodeView::key).toList();
            if (marks().nodes().containsAll(all)) {
                marks().removeNodes(all);
            } else {
                marks().addNodes(all);
            }
            return;
        }
        Map<ResourceLocation, Integer> blocks = new LinkedHashMap<>();
        for (TabletMachineRows.Row row : page().rows()) {
            if (row instanceof TabletMachineRows.Block block) {
                blocks.put(block.block(), block.machines());
            }
        }
        marks().toggleBlocks(blocks);
    }

    /** A caixa de 9 × 9 px: vazia, cheia ou pela metade (uma faixa no meio). */
    static void paint(GuiGraphics g, int x, int y, Box box) {
        GuiPaint.checkbox(g, x, y, box == Box.ALL, GuiPaint.ACCENT);
        if (box == Box.PART) {
            GuiPaint.outline(g, x, y, 9, 9, GuiPaint.ACCENT);
            g.fill(x + 2, y + 4, x + 7, y + 5, GuiPaint.ACCENT);
        }
    }

    // ------------------------------------------------------------------ textos

    /** "2 roteadores, 3 máquinas", sem as partes zeradas. */
    Component what() {
        int routers = marks().routers();
        int machines = marks().machineCount();
        Component r = TabletText.plural("marks.routers", routers);
        Component m = TabletText.plural("marks.machines", machines);
        if (routers > 0 && machines > 0) {
            return tr("marks.join", r, m);
        }
        return machines > 0 ? m : r;
    }
}
