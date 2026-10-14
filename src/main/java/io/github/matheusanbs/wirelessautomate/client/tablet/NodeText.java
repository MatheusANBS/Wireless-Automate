package io.github.matheusanbs.wirelessautomate.client.tablet;

import static io.github.matheusanbs.wirelessautomate.client.tablet.TabletText.plural;
import static io.github.matheusanbs.wirelessautomate.client.tablet.TabletText.statusColor;
import static io.github.matheusanbs.wirelessautomate.client.tablet.TabletText.statusName;
import static io.github.matheusanbs.wirelessautomate.client.tablet.TabletText.tr;
import static io.github.matheusanbs.wirelessautomate.client.tablet.TabletText.typeName;
import static io.github.matheusanbs.wirelessautomate.client.tablet.TabletText.typeWord;

import io.github.matheusanbs.wirelessautomate.block.RouterBlock;
import io.github.matheusanbs.wirelessautomate.client.GuiPaint;
import io.github.matheusanbs.wirelessautomate.menu.TabletSnapshot;
import io.github.matheusanbs.wirelessautomate.menu.TabletSnapshot.NodeView;
import io.github.matheusanbs.wirelessautomate.network.LoadedTypes;
import io.github.matheusanbs.wirelessautomate.network.NodeIndex;
import io.github.matheusanbs.wirelessautomate.network.PortMode;
import io.github.matheusanbs.wirelessautomate.network.ResourceType;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;

/**
 * Os textos de um nó e das redes dele, lidos do snapshot do Tablet: nome, onde fica, papéis, redes das faces e a
 * dica completa do nó (a do Mapa e a das linhas da Lista).
 */
final class NodeText {
    /** Tipos das linhas de papel e de rede da dica. */
    private static final List<ResourceType> TYPES = LoadedTypes.LIST;

    private final Supplier<TabletSnapshot> snapshot;
    private final Map<ResourceLocation, ItemStack> icons = new HashMap<>();

    NodeText(Supplier<TabletSnapshot> snapshot) {
        this.snapshot = snapshot;
    }

    private TabletSnapshot snapshot() {
        return snapshot.get();
    }

    private Block machineBlock(NodeView node) {
        return BuiltInRegistries.BLOCK.get(node.machine());
    }

    private ItemStack icon(NodeView node) {
        return icons.computeIfAbsent(node.machine(), id -> new ItemStack(BuiltInRegistries.BLOCK.get(id).asItem()));
    }

    /** Nome do nó; sem nome, o da máquina; sem máquina conhecida, "Roteador". */
    Component nodeName(NodeView node) {
        if (!node.name().isEmpty()) {
            return Component.literal(node.name());
        }
        Block block = machineBlock(node);
        return block == Blocks.AIR ? Component.translatable("block.wirelessautomate.router") : RouterBlock.machineName(block);
    }

    boolean sameDimension(NodeView node) {
        return node.key().dimension().location().equals(snapshot().dimension());
    }

    Component coords(NodeView node) {
        BlockPos pos = node.key().pos();
        Component text = Component.literal(pos.getX() + ", " + pos.getY() + ", " + pos.getZ());
        if (!sameDimension(node)) {
            text = text.copy().append(" · " + node.key().dimension().location().getPath());
        }
        return text;
    }

    /** Etiquetas de papel, na cor do modo: "Extrai itens", "Insere energia", "Armazém de fluidos". */
    private List<Component> roleLabels(int roles) {
        List<Component> labels = new ArrayList<>();
        for (ResourceType type : TYPES) {
            if ((roles & NodeIndex.role(type, NodeIndex.EXTRACT)) != 0) {
                labels.add(tr("label.extract", typeWord(type)).copy().withColor(GuiPaint.modeColor(PortMode.EXTRACT)));
            }
            if ((roles & NodeIndex.role(type, NodeIndex.INSERT)) != 0) {
                labels.add(tr("label.insert", typeWord(type)).copy().withColor(GuiPaint.modeColor(PortMode.INSERT)));
            }
            if ((roles & NodeIndex.role(type, NodeIndex.STORAGE)) != 0) {
                labels.add(tr("label.storage", typeWord(type)).copy().withColor(GuiPaint.modeColor(PortMode.BOTH)));
            }
        }
        return labels;
    }

    Component networkName(Optional<UUID> id) {
        if (id.isEmpty()) {
            return tr("network.none");
        }
        return snapshot().network(id.get()).<Component>map(n -> Component.literal(n.name()).withColor(n.color()))
                .orElse(tr("network.hidden"));
    }

    /** As redes das faces de um tipo, separadas por vírgula ("Mina, Armazém"), ou "Sem rede". */
    private Component networkNames(List<UUID> ids) {
        if (ids.isEmpty()) {
            return tr("network.none");
        }
        MutableComponent text = Component.empty();
        for (int i = 0; i < ids.size(); i++) {
            if (i > 0) {
                text.append(Component.literal(", ").withColor(GuiPaint.TOOLTIP_MUTED));
            }
            text.append(networkName(Optional.of(ids.get(i))));
        }
        return text;
    }

    /** Os avisos de rede da linha do nó: faces em várias redes e faces numa rede removida. */
    private List<Component> networkLabels(NodeView node, int mutedColor) {
        List<Component> labels = new ArrayList<>(2);
        if (node.removedNetwork()) {
            labels.add(tr("node.removed_network").copy().withColor(GuiPaint.WARN));
        }
        int distinct = node.distinctNetworks();
        if (distinct > 1) {
            labels.add(plural("node.face_networks", distinct).copy().withColor(mutedColor));
        }
        return labels;
    }

    /** Nome, onde fica, tier, máquina, as redes das faces de cada tipo, avisos de rede, papéis e status. */
    Component nodeTooltip(NodeView node, boolean clickOpens) {
        MutableComponent text = nodeName(node).copy();
        text.append("\n").append(coords(node).copy().withColor(GuiPaint.TOOLTIP_MUTED));
        text.append("\n").append(Component.translatable(node.tier().translationKey()).copy().withColor(GuiPaint.TOOLTIP_MUTED))
                .append(Component.literal(" · ").withColor(GuiPaint.TOOLTIP_MUTED))
                .append(RouterBlock.machineName(machineBlock(node)).copy().withColor(GuiPaint.TOOLTIP_MUTED));
        for (ResourceType type : TYPES) {
            text.append("\n").append(typeName(type).copy().withColor(GuiPaint.TOOLTIP_MUTED))
                    .append(Component.literal(": ").withColor(GuiPaint.TOOLTIP_MUTED)).append(networkNames(node.networks(type)));
        }
        for (Component label : networkLabels(node, GuiPaint.TOOLTIP_MUTED)) {
            text.append("\n").append(label);
        }
        List<Component> roles = roleLabels(node.roles());
        if (!roles.isEmpty()) {
            MutableComponent line = Component.empty();
            for (int i = 0; i < roles.size(); i++) {
                if (i > 0) {
                    line.append(Component.literal(" · ").withColor(GuiPaint.TOOLTIP_MUTED));
                }
                line.append(roles.get(i));
            }
            text.append("\n").append(line);
        }
        text.append("\n").append(statusName(node.status()).copy().withColor(statusColor(node.status())));
        if (clickOpens) {
            text.append("\n").append(tr("open.click").copy().withColor(GuiPaint.TOOLTIP_MUTED));
        }
        return text;
    }
}
