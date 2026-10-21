package io.github.matheusanbs.wirelessautomate.client.tablet;

import static io.github.matheusanbs.wirelessautomate.client.tablet.TabletText.plural;
import static io.github.matheusanbs.wirelessautomate.client.tablet.TabletText.tr;

import io.github.matheusanbs.wirelessautomate.block.RouterBlock;
import io.github.matheusanbs.wirelessautomate.block.RouterTier;
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
import java.util.Arrays;
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
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import org.jetbrains.annotations.Nullable;

/**
 * Os textos da Lista do Tablet: nomes de máquina (guardados), de nó e de rede, as etiquetas da busca, a segunda
 * linha de cada nó, o resumo do perfil copiado e a contagem embaixo da lista.
 */
final class ListTexts {
    /** O nome e a segunda linha de um nó, medidos na mesma largura. */
    record NodeLine(GuiText.Fitted name, GuiText.Fitted state, boolean pill) {
    }

    /** A pílula do tier: o nome, a largura com a borda e a cor. */
    record TierPill(Component name, int width, int color) {
    }

    static final int PILL_PAD = 3;

    private final TabletList list;
    private final Map<ResourceLocation, String> machineNames = new HashMap<>();
    private final Map<ResourceLocation, ItemStack> blockIcons = new HashMap<>();
    private final TierPill[] pills = new TierPill[RouterIcons.TIERS.length];
    private int pillTheme = -1;
    /** As linhas dos nós, por índice do nó na página. */
    private final LineCache<NodeLine> nodeLines = new LineCache<>();

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
        return list.nodes.nodeName(node);
    }

    /** O ícone do bloco {@code id}, criado uma vez. */
    ItemStack blockIcon(ResourceLocation id) {
        return blockIcons.computeIfAbsent(id, key -> {
            Block block = BuiltInRegistries.BLOCK.get(key);
            return block == Blocks.AIR ? ItemStack.EMPTY : new ItemStack(block.asItem());
        });
    }

    /** A pílula do tier (nome na cor do tier) e a largura dela, refeitas quando o tema muda. */
    TierPill pill(Font font, RouterTier tier) {
        if (pillTheme != GuiThemes.version()) {
            pillTheme = GuiThemes.version();
            Arrays.fill(pills, null);
        }
        TierPill pill = pills[tier.ordinal()];
        if (pill == null) {
            Component name = Component.translatable(tier.translationKey());
            pill = new TierPill(name, font.width(name) + 2 * PILL_PAD + 2, GuiPaint.tierColor(tier));
            pills[tier.ordinal()] = pill;
        }
        return pill;
    }

    /**
     * O nome e a segunda linha do nó {@code index} para uma linha com {@code room} de largura, montados só quando a
     * página, o tipo, o tema ou a largura mudam. A pílula do tier ({@code pillWidth}) só entra se o nome cabe
     * inteiro ao lado dela. A segunda linha começa sempre por "presa: Fornalha"; o que não cabe é cortado com "…"
     * no fim (o estado já está na bolinha e a dica da linha traz tudo).
     */
    NodeLine nodeLine(Font font, int index, NodeView node, @Nullable ProfileSummary profile, int room,
            int pillWidth) {
        TabletSnapshot s = snapshot();
        nodeLines.key(s, null, list.chipType, GuiThemes.version(), s.nodes().size());
        NodeLine line = nodeLines.get(index, room);
        if (line == null) {
            Component name = nodeName(node);
            boolean pill = font.width(name) + 4 + pillWidth <= room;
            line = new NodeLine(GuiText.fit(font, name, pill ? room - pillWidth - 4 : room),
                    GuiText.fit(font, secondLine(node, profile), room), pill);
            nodeLines.put(index, room, line);
        }
        return line;
    }

    private Component secondLine(NodeView node, @Nullable ProfileSummary profile) {
        return tr("list.router.attached", list.nodes.attachedName(node)).copy().withColor(GuiPaint.MUTED)
                .append(Component.literal(" · ").withColor(GuiPaint.MUTED)).append(stateLine(node, profile));
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

    /**
     * O estado do nó na Grade, numa palavra só: sem rede (coral), destino cheio, pausada ou descarregada; senão a
     * vazão do primeiro tipo que move algo, ou "parado".
     */
    Component gridState(NodeView node) {
        return switch (node.status()) {
            case UNLOADED -> tr("list.state.unloaded").copy().withColor(GuiPaint.MUTED);
            case NO_NETWORK -> tr("list.state.no_network").copy().withColor(GuiPaint.ACCENT);
            case FULL -> tr("list.state.full").copy().withColor(GuiPaint.WARN);
            case PAUSED -> tr("list.state.paused").copy().withColor(GuiPaint.PAUSED);
            case ACTIVE, IDLE -> {
                for (ResourceType type : LoadedTypes.LIST) {
                    if (node.rate(type) > 0) {
                        yield ResourceStyle.rate(type, node.rate(type)).copy().withColor(GuiPaint.MUTED);
                    }
                }
                yield TabletText.statusName(node.status()).copy().withColor(GuiPaint.MUTED);
            }
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
