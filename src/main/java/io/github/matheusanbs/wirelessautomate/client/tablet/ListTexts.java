package io.github.matheusanbs.wirelessautomate.client.tablet;

import static io.github.matheusanbs.wirelessautomate.client.tablet.TabletText.plural;
import static io.github.matheusanbs.wirelessautomate.client.tablet.TabletText.tr;

import io.github.matheusanbs.wirelessautomate.block.RouterBlock;
import io.github.matheusanbs.wirelessautomate.client.GuiPaint;
import io.github.matheusanbs.wirelessautomate.client.ResourceStyle;
import io.github.matheusanbs.wirelessautomate.client.tablet.TabletSearch.Group;
import io.github.matheusanbs.wirelessautomate.client.tablet.TabletSearch.Suggestion;
import io.github.matheusanbs.wirelessautomate.menu.TabletSnapshot;
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
    private final TabletList list;
    private final Map<ResourceLocation, String> machineNames = new HashMap<>();

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
     * A segunda linha do nó: com perfil copiado, num nó carregado, "igual ao perfil" ou a primeira diferença (a
     * origem diz que é a origem); senão o estado em palavras ("destino cheio", "sem rede", "descarregada",
     * "pausada") ou, tudo bem, a rede e a vazão na aba do painel.
     */
    Component stateLine(NodeView node, @Nullable ProfileSummary profile) {
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

    Component countText() {
        TabletSnapshot s = snapshot();
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
