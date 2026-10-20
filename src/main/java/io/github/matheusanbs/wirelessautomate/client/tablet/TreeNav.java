package io.github.matheusanbs.wirelessautomate.client.tablet;

import io.github.matheusanbs.wirelessautomate.client.tablet.ListTree.Kind;
import io.github.matheusanbs.wirelessautomate.client.tablet.ListTree.Line;
import io.github.matheusanbs.wirelessautomate.menu.TabletRows;
import io.github.matheusanbs.wirelessautomate.menu.TabletSnapshot.NodeView;
import io.github.matheusanbs.wirelessautomate.network.NodeIndex.NodeKey;
import io.github.matheusanbs.wirelessautomate.packet.SelectTargetPayload;
import io.github.matheusanbs.wirelessautomate.packet.TabletTreePayload.Action;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

/**
 * A escolha e o teclado das linhas da árvore na Lista do Tablet. Escolher uma linha da árvore escolhe o nó dela
 * ({@code TabletSelectNodePayload}) e, logo depois, o alvo do painel ({@link SelectTargetPayload}: a presa, o grupo
 * ou a máquina); o servidor trata os dois na ordem e valida o alvo. O cursor marca a linha da árvore em que as setas
 * estão (num nó, o cursor é o nó escolhido): ↑ ↓ andam pelas linhas, escolhendo os nós como sempre e só marcando as
 * da árvore; Enter escolhe a linha marcada; ← e → fazem o que o {@link ListTree} decide (fechar, recolher, subir,
 * abrir). O cursor sai quando o jogador volta à busca ({@link #clear}).
 * Abrir e fechar só mandam o {@code TabletTreePayload}: a árvore nova vem na próxima página do servidor.
 */
final class TreeNav {
    /** Uma linha da árvore que sobrevive às páginas novas: o nó, o tipo e o id (do grupo, ou o índice da máquina). */
    record Key(NodeKey node, Kind kind, int id) {
    }

    private final TabletList list;
    private @Nullable Key cursor;
    /** A linha a pôr na vista no próximo arranjo. */
    private @Nullable Key reveal;
    /** A linha do cursor na última árvore em que foi procurada. */
    private @Nullable ListTree<ResourceLocation> foundIn;
    private @Nullable Key foundKey;
    private int foundLine = -1;

    TreeNav(TabletList list) {
        this.list = list;
    }

    /** Larga o cursor (o jogador escolheu um nó). */
    void clear() {
        cursor = null;
    }

    /** Há uma linha da árvore marcada pelas setas ou pelo clique. */
    boolean hasCursor() {
        return cursor != null && find(cursor) >= 0;
    }

    boolean isCursor(int line) {
        return cursor != null && line == find(cursor);
    }

    /** A chave da linha da árvore {@code line}. */
    Key keyOf(Line<ResourceLocation> line) {
        NodeKey node = list.snapshot().nodes().get(line.node()).key();
        return switch (list.model.row(line)) {
            case TabletRows.Attached attached -> new Key(node, Kind.ATTACHED, 0);
            case TabletRows.Group group -> new Key(node, Kind.GROUP, group.id());
            case TabletRows.Machine machine -> new Key(node, Kind.MACHINE, machine.index());
            case TabletRows.More more -> new Key(node, Kind.MORE, more.group());
        };
    }

    /** A linha da chave na árvore de agora, ou -1; guardada até a árvore ou a chave mudarem. */
    private int find(Key key) {
        ListTree<ResourceLocation> tree = list.model.tree();
        if (tree == foundIn && key.equals(foundKey)) {
            return foundLine;
        }
        foundIn = tree;
        foundKey = key;
        foundLine = -1;
        List<Line<ResourceLocation>> lines = tree.lines();
        for (int i = 0; i < lines.size(); i++) {
            if (lines.get(i).kind() == key.kind() && keyOf(lines.get(i)).equals(key)) {
                foundLine = i;
                break;
            }
        }
        return foundLine;
    }

    /** A linha do nó ({@link Kind#NODE}) ou da linha da árvore dele, ou -1 (gancho do e2e). */
    int lineOf(NodeKey node, Kind kind, int id) {
        return kind == Kind.NODE ? list.model.tree().lineOfNode(list.model.indexOf(node)) : find(new Key(node, kind, id));
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

    /**
     * Escolhe a linha: um nó como sempre; na árvore, o nó e o alvo do painel (o "mais N" mostra todas). A lista não
     * rola até o nó, que pode estar bem acima.
     */
    void choose(int index) {
        Line<ResourceLocation> line = list.model.tree().lines().get(index);
        NodeView node = list.snapshot().nodes().get(line.node());
        if (line.kind() == Kind.NODE) {
            cursor = null;
            list.choose(node.key());
            return;
        }
        TabletRows.Row row = list.model.row(line);
        if (row instanceof TabletRows.More more) {
            send(Action.SHOW_ALL, node.key(), more.group());
            return;
        }
        int id = list.actions.containerId();
        SelectTargetPayload target = switch (row) {
            case TabletRows.Attached attached -> SelectTargetPayload.attached(id);
            case TabletRows.Group group -> SelectTargetPayload.group(id, group.id(), group.block());
            case TabletRows.Machine machine -> SelectTargetPayload.machine(id, machine.index(), BlockPos.of(machine.pos()));
            case TabletRows.More more -> throw new IllegalStateException();
        };
        cursor = keyOf(line);
        list.choose(node.key());
        list.revealKey = null;
        list.actions.send(target);
    }

    void send(Action action, NodeKey node, int group) {
        list.actions.tree(action, node, group);
    }

    // ------------------------------------------------------------------ teclado

    /** A linha onde as setas estão: o cursor, ou a do nó escolhido (o cabeçalho, se ele está recolhido). */
    private int current() {
        int line = cursor == null ? -1 : find(cursor);
        return line >= 0 ? line : list.model.tree().anchorOfNode(list.model.indexOf(list.chosen));
    }

    /** ↑ ↓: os nós são escolhidos, as linhas da árvore só marcadas. */
    void upDown(int direction) {
        ListTree<ResourceLocation> tree = list.model.tree();
        int next = tree.next(current(), direction);
        if (next >= 0) {
            moveTo(next);
        }
    }

    private void moveTo(int index) {
        Line<ResourceLocation> line = list.model.tree().lines().get(index);
        if (line.kind() == Kind.NODE) {
            cursor = null;
            list.choose(list.snapshot().nodes().get(line.node()).key());
            return;
        }
        if (line.kind().tree()) {
            cursor = keyOf(line);
            reveal = cursor;
        }
    }

    /** Enter numa linha da árvore marcada: escolhe. {@code false} sem cursor (o Enter do nó é outro). */
    boolean enter() {
        int line = cursor == null ? -1 : find(cursor);
        if (line < 0) {
            return false;
        }
        choose(line);
        return true;
    }

    /** ← e → ({@link ListTree#left}, {@link ListTree#right}): aqui só se aplica o que o modelo decidiu. */
    void arrow(boolean left) {
        ListTree<ResourceLocation> tree = list.model.tree();
        int index = current();
        ListTree.Arrow arrow = left ? tree.left(index) : tree.right(index);
        if (arrow.step() == ListTree.Step.NONE) {
            return;
        }
        Line<ResourceLocation> line = tree.lines().get(arrow.line());
        switch (arrow.step()) {
            case COLLAPSE_HEADER -> list.model.collapse(line.group());
            case EXPAND_HEADER -> list.model.expand(line.group());
            case CLOSE_NODE -> send(Action.CLOSE, list.snapshot().nodes().get(line.node()).key(), 0);
            case OPEN_NODE -> send(Action.OPEN, list.snapshot().nodes().get(line.node()).key(), 0);
            case COLLAPSE_GROUP -> send(Action.COLLAPSE_GROUP, keyOf(line).node(), keyOf(line).id());
            case EXPAND_GROUP -> send(Action.EXPAND_GROUP, keyOf(line).node(), keyOf(line).id());
            case SHOW_ALL -> send(Action.SHOW_ALL, keyOf(line).node(), keyOf(line).id());
            case MOVE_TO -> moveTo(arrow.line());
            case NONE -> {
            }
        }
    }
}
