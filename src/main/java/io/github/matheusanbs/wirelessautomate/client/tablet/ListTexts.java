package io.github.matheusanbs.wirelessautomate.client.tablet;

import static io.github.matheusanbs.wirelessautomate.client.tablet.TabletText.plural;
import static io.github.matheusanbs.wirelessautomate.client.tablet.TabletText.tr;

import io.github.matheusanbs.wirelessautomate.block.RouterBlock;
import io.github.matheusanbs.wirelessautomate.client.GuiPaint;
import io.github.matheusanbs.wirelessautomate.client.GuiText;
import io.github.matheusanbs.wirelessautomate.client.GuiThemes;
import io.github.matheusanbs.wirelessautomate.client.ResourceStyle;
import io.github.matheusanbs.wirelessautomate.client.tablet.TabletSearch.Group;
import io.github.matheusanbs.wirelessautomate.client.tablet.TabletSearch.Suggestion;
import io.github.matheusanbs.wirelessautomate.menu.TabletSnapshot;
import io.github.matheusanbs.wirelessautomate.menu.TabletSnapshot.NodeStatus;
import io.github.matheusanbs.wirelessautomate.menu.TabletSnapshot.NodeView;
import io.github.matheusanbs.wirelessautomate.menu.TabletSnapshot.ProfileSummary;
import io.github.matheusanbs.wirelessautomate.network.LoadedTypes;
import io.github.matheusanbs.wirelessautomate.network.ResourceType;
import io.github.matheusanbs.wirelessautomate.preset.ProfileDiff.ProfileMatch;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import net.minecraft.client.gui.Font;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import org.jetbrains.annotations.Nullable;

/**
 * Os textos da Lista do Tablet: nomes de máquina (guardados), de nó e de rede, as etiquetas da busca, a segunda
 * linha de cada nó, o resumo do perfil copiado e a contagem embaixo da lista.
 */
final class ListTexts {
    /** O nome e a segunda linha de um nó, medidos na mesma largura. */
    record NodeLine(GuiText.Fitted name, GuiText.Fitted state) {
    }

    private final TabletList list;
    private final Map<ResourceLocation, String> machineNames = new HashMap<>();
    /** As linhas dos nós, por índice do nó na página. */
    private final LineCache<NodeLine> nodeLines = new LineCache<>();
    /** Os cabeçalhos de grupo, por índice da linha na árvore. */
    private final LineCache<GuiText.Fitted> headers = new LineCache<>();

    ListTexts(TabletList list) {
        this.list = list;
    }

    private TabletSnapshot snapshot() {
        return list.snapshot();
    }

    String machineName(ResourceLocation id) {
        return machineNames.computeIfAbsent(id, key -> {
            Block block = BuiltInRegistries.BLOCK.get(key);
            return block == Blocks.AIR ? key.toString() : RouterBlock.machineName(block).getString();
        });
    }

    Component nodeName(NodeView node) {
        return node.name().isEmpty() ? Component.literal(machineName(node.machine())) : Component.literal(node.name());
    }

    /** O nome e a segunda linha do nó {@code index}, montados só quando a página, o tipo, o tema ou a largura mudam. */
    NodeLine nodeLine(Font font, int index, NodeView node, @Nullable ProfileSummary profile, int width) {
        TabletSnapshot s = snapshot();
        nodeLines.key(s, null, list.chipType, GuiThemes.version(), s.nodes().size());
        NodeLine line = nodeLines.get(index, width);
        if (line == null) {
            line = new NodeLine(GuiText.fit(font, nodeName(node), width),
                    GuiText.fit(font, stateLine(node, profile), width));
            nodeLines.put(index, width, line);
        }
        return line;
    }

    /**
     * O cabeçalho do grupo na linha {@code index} da árvore (máquina, contagem e quantos diferem do perfil),
     * montado só quando a página, a árvore, o tema ou a largura mudam.
     */
    GuiText.Fitted header(Font font, ListTree<ResourceLocation> tree, int lines, int index,
            ListTree.Line<ResourceLocation> row, @Nullable ProfileSummary profile, int width) {
        headers.key(snapshot(), tree, null, GuiThemes.version(), lines);
        GuiText.Fitted text = headers.get(index, width);
        if (text == null) {
            text = GuiText.fit(font, headerText(row, profile), width);
            headers.put(index, width, text);
        }
        return text;
    }

    private Component headerText(ListTree.Line<ResourceLocation> row, @Nullable ProfileSummary profile) {
        int count = Math.max(row.count(), snapshot().groupCount(row.group()));
        MutableComponent text = Component.literal(machineName(row.group())).copy()
                .append(Component.literal(" · " + count).withColor(GuiPaint.MUTED));
        int differ = profile == null ? 0 : differing(row.group());
        if (differ > 0) {
            text.append(Component.literal(" · ").withColor(GuiPaint.MUTED)).append(
                    plural("list.group.differ", differ).copy().withColor(GuiPaint.WARN));
        }
        return text;
    }

    /** Quantos nós recebidos do grupo diferem do perfil (os carregados que compararam). */
    private int differing(ResourceLocation machine) {
        int count = 0;
        for (NodeView node : snapshot().nodes()) {
            if (node.machine().equals(machine) && node.profile().map(m -> !m.equal()).orElse(false)) {
                count++;
            }
        }
        return count;
    }

    private String networkName(UUID id) {
        return snapshot().network(id).map(TabletSnapshot.NetworkView::name).orElse("?");
    }

    Component labelText(ListSearch.Label label) {
        return label.machine() != null ? tr("list.label.machine", machineName(label.machine()))
                : tr("list.label.network", networkName(label.network()));
    }

    String sourceName(ProfileSummary s) {
        return s.sourceName().isEmpty() ? machineName(s.machine()) : s.sourceName();
    }

    static Component profileSummary(ProfileSummary s) {
        if (s.faces() == 0) {
            return tr("list.profile.empty");
        }
        MutableComponent types = Component.empty();
        for (int i = 0; i < s.types().size(); i++) {
            if (i > 0) {
                types.append(", ");
            }
            types.append(ResourceStyle.name(s.types().get(i)));
        }
        return tr(s.faces() == 1 ? "list.profile.summary.one" : "list.profile.summary", types, s.faces());
    }

    /**
     * A segunda linha do nó: o {@link #stateText} e, com máquinas vinculadas, quantas. Num nó sem nada a dizer (a
     * rede e a vazão), "10 máquinas" vem antes, como no mockup; num problema ou numa diferença do perfil, que pedem
     * atenção, vem depois.
     */
    private Component stateLine(NodeView node, @Nullable ProfileSummary profile) {
        Component state = stateText(node, profile);
        if (node.linked() <= 0) {
            return state;
        }
        Component machines = plural("list.machines", node.linked()).copy().withColor(GuiPaint.MUTED);
        Component dot = Component.literal(" · ").withColor(GuiPaint.MUTED);
        boolean calm = profile == null && (node.status() == NodeStatus.ACTIVE || node.status() == NodeStatus.IDLE);
        return calm ? machines.copy().append(dot).append(state) : state.copy().append(dot).append(machines);
    }

    /**
     * O estado do nó: com perfil copiado, num nó carregado, "igual ao perfil" ou a primeira diferença (a origem diz
     * que é a origem); senão o estado em palavras ("destino cheio", "sem rede", "descarregada", "pausada") ou,
     * tudo bem, a rede e a vazão na aba do painel.
     */
    private Component stateText(NodeView node, @Nullable ProfileSummary profile) {
        if (profile != null && node.key().equals(profile.origin())) {
            return tr("list.profile.origin").copy().withColor(GuiPaint.ACCENT);
        }
        if (profile != null && node.profile().isPresent()) {
            ProfileMatch match = node.profile().get();
            if (match.equal()) {
                return tr("list.profile.equal").copy().withColor(GuiPaint.OK);
            }
            Component side = Component.translatable("gui.wirelessautomate.router.side." + match.side().key());
            if (match.type() != list.chipType) {
                side = side.copy().append(" (").append(ResourceStyle.name(match.type())).append(")");
            }
            return tr("list.diff." + match.what().name().toLowerCase(Locale.ROOT), side).copy().withColor(GuiPaint.WARN);
        }
        return switch (node.status()) {
            case UNLOADED -> tr("list.state.unloaded").copy().withColor(GuiPaint.MUTED);
            case NO_NETWORK -> tr("list.state.no_network").copy().withColor(GuiPaint.DANGER);
            case FULL -> tr("list.state.full").copy().withColor(GuiPaint.WARN);
            case PAUSED -> tr("list.state.paused").copy().withColor(GuiPaint.PAUSED);
            case ACTIVE, IDLE -> okLine(node);
        };
    }

    private Component okLine(NodeView node) {
        ResourceType type = list.chipType;
        List<UUID> networks = node.networks(type);
        if (networks.isEmpty()) {
            for (ResourceType t : LoadedTypes.LIST) {
                if (!node.networks(t).isEmpty()) {
                    type = t;
                    networks = node.networks(t);
                    break;
                }
            }
        }
        Component network = networks.isEmpty() ? tr("network.none") : Component.literal(networkName(networks.getFirst()));
        return tr("list.state.ok", network, ResourceStyle.rate(type, node.rate(type))).copy().withColor(GuiPaint.MUTED);
    }

    /**
     * A contagem embaixo da lista. Por máquinas: "13 de 46 linhas" com linhas recolhidas ou cortadas, senão "5 blocos ·
     * 1.204 máquinas".
     */
    Component countText() {
        TabletSnapshot s = snapshot();
        if (list.machineView()) {
            var page = s.machineRows();
            return page.totalRows() > page.rows().size() ? tr("machines.count.rows", page.rows().size(), page.totalRows())
                    : plural("machines.count.blocks", page.blocks()).copy().append(" · ")
                            .append(plural("list.machines", page.machines()));
        }
        return s.matchingNodes() > s.nodes().size() ? tr("list.limit", s.nodes().size(), s.matchingNodes())
                : tr("list.count", s.matchingNodes(), s.totalNodes());
    }

    /** À direita da sugestão: quantos nós pega; num nome de um nó só, a máquina dele. */
    Component suggestRight(Suggestion<?> s) {
        if (s.group() == Group.NAMES && s.count() == 1) {
            for (NodeView node : snapshot().nodes()) {
                if (node.name().equals(s.label())) {
                    return Component.literal(machineName(node.machine()));
                }
            }
        }
        return plural("meta.nodes", s.count());
    }
}
