package io.github.matheusanbs.wirelessautomate.menu;

import io.github.matheusanbs.wirelessautomate.block.RouterBlock;
import io.github.matheusanbs.wirelessautomate.block.RouterBlockEntity;
import io.github.matheusanbs.wirelessautomate.menu.TabletSnapshot.ProfileSummary;
import io.github.matheusanbs.wirelessautomate.network.LoadedTypes;
import io.github.matheusanbs.wirelessautomate.network.NetworkSavedData;
import io.github.matheusanbs.wirelessautomate.network.NodeIndex;
import io.github.matheusanbs.wirelessautomate.network.NodeIndex.NodeKey;
import io.github.matheusanbs.wirelessautomate.network.PortMode;
import io.github.matheusanbs.wirelessautomate.network.RelativeSide;
import io.github.matheusanbs.wirelessautomate.network.ResourceType;
import io.github.matheusanbs.wirelessautomate.network.RouterPreset;
import io.github.matheusanbs.wirelessautomate.packet.ModPayloads;
import io.github.matheusanbs.wirelessautomate.packet.PanelNoticePayload;
import io.github.matheusanbs.wirelessautomate.packet.TabletProfilePayload;
import io.github.matheusanbs.wirelessautomate.preset.ConfiguratorArea;
import io.github.matheusanbs.wirelessautomate.preset.PresetApplier;
import io.github.matheusanbs.wirelessautomate.preset.PresetPaste;
import io.github.matheusanbs.wirelessautomate.preset.ProfileSides;
import io.github.matheusanbs.wirelessautomate.preset.ProfileSource;
import io.github.matheusanbs.wirelessautomate.registry.ModDataComponents;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

/**
 * O perfil do Tablet no servidor: copiar o perfil de um nó para o Tablet, esquecê-lo, colá-lo em um nó, num
 * grupo de máquina ou nos nós escolhidos, e desfazer o último colar. É dono do desfazer e do último aviso
 * mandado ao jogador.
 */
final class TabletProfile {
    private final ServerPlayer viewer;
    private final int containerId;
    private final TabletQuery query;
    private final TabletSnapshotBuilder builder;
    /** A visão por máquinas, que reavalia o bloco marcado. */
    private final TabletMachines machines;
    /** O último colar do Tablet: o que cada roteador mudou; some ao fechar o menu. */
    private final Map<NodeKey, PasteUndo> undo = new LinkedHashMap<>();
    private @Nullable Component lastNotice;

    TabletProfile(ServerPlayer viewer, int containerId, TabletQuery query, TabletSnapshotBuilder builder,
            TabletMachines machines) {
        this.viewer = viewer;
        this.containerId = containerId;
        this.query = query;
        this.builder = builder;
        this.machines = machines;
    }

    boolean canUndo() {
        return !undo.isEmpty();
    }

    @Nullable Component lastNotice() {
        return lastNotice;
    }

    /** A ação já validada quanto ao menu; ver {@link TabletMenu#profile}. Devolve quantos nós ela atingiu, ou -1. */
    int run(TabletProfilePayload.Action action, @Nullable NodeKey key, @Nullable ResourceLocation machine,
            List<NodeKey> keys, TabletMarks marks) {
        boolean bulk = switch (action) {
            case PASTE_GROUP -> machine != null;
            case PASTE_SELECTED -> !keys.isEmpty() || !marks.isEmpty();
            case UNDO -> !undo.isEmpty();
            default -> false;
        };
        if (bulk && !TabletPlayers.takeBulk(viewer.getUUID(), viewer.server.getTickCount())) {
            announce(PanelNoticePayload.plain(Component.translatable("gui.wirelessautomate.panel.notice.busy")));
            return -1;
        }
        ItemStack tablet = TabletMenu.tabletStack(viewer);
        return switch (action) {
            case COPY -> copyProfile(tablet, key);
            case FORGET -> {
                tablet.remove(ModDataComponents.PRESET.get());
                tablet.remove(ModDataComponents.PROFILE_SOURCE.get());
                yield 1;
            }
            case PASTE_ONE -> key == null ? -1 : pasteProfile(tablet, List.of(key), true);
            case PASTE_GROUP -> machine == null ? -1 : pasteProfile(tablet, groupKeys(machine), false);
            case PASTE_SELECTED -> !marks.isEmpty() ? pasteMarked(tablet, keys, marks)
                    : keys.isEmpty() ? -1 : pasteProfile(tablet, keys, false);
            case UNDO -> undoPaste();
        };
    }

    /** O perfil guardado no Tablet do jogador, ou {@code null}: vale só com o preset e a origem juntos. */
    static @Nullable RouterPreset storedProfile(Player player) {
        ItemStack tablet = TabletMenu.tabletStack(player);
        return tablet.has(ModDataComponents.PROFILE_SOURCE.get()) ? tablet.get(ModDataComponents.PRESET.get()) : null;
    }

    /** O resumo do perfil para o cabeçalho: os tipos carregados e quantas faces têm modo. */
    static Optional<ProfileSummary> profileSummary(Player player, @Nullable RouterPreset profile) {
        ProfileSource source = profile == null ? null
                : TabletMenu.tabletStack(player).get(ModDataComponents.PROFILE_SOURCE.get());
        if (source == null) {
            return Optional.empty();
        }
        List<ResourceType> types = new ArrayList<>();
        int faces = 0;
        for (ResourceType type : LoadedTypes.LIST) {
            int withMode = 0;
            for (RelativeSide side : RelativeSide.values()) {
                if (profile.face(type, side).mode() != PortMode.NONE) {
                    withMode++;
                }
            }
            if (withMode > 0) {
                types.add(type);
                faces += withMode;
            }
        }
        return Optional.of(new ProfileSummary(ProfileSource.clip(source.nodeName()), source.machine(), source.origin(),
                List.copyOf(types), faces));
    }

    /** Guarda no Tablet o perfil do nó: o preset das faces, a origem e o quadro da presa. */
    private int copyProfile(ItemStack tablet, @Nullable NodeKey key) {
        RouterBlockEntity router = key == null ? null : TabletNodes.reachableRouter(viewer, key);
        if (router == null) {
            return -1;
        }
        tablet.set(ModDataComponents.PRESET.get(), PresetPaste.copy(viewer, router));
        tablet.set(ModDataComponents.PROFILE_SOURCE.get(),
                new ProfileSource(router.name(), machineOf(router, key), key, frameOf(router)));
        return 1;
    }

    /**
     * A frente da presa e o cima do mundo nos lados do roteador ({@link ProfileSides#frame}); sem frente, a presa vale
     * Norte.
     */
    private static int frameOf(RouterBlockEntity router) {
        int front = router.attachedFront();
        Direction absolute = front < 0 ? Direction.NORTH : Direction.values()[front];
        return ProfileSides.frame(RelativeSide.fromAbsolute(router.facing(), router.spin(), absolute).ordinal(),
                RelativeSide.fromAbsolute(router.facing(), router.spin(), Direction.UP).ordinal());
    }

    /**
     * O bloco da máquina do nó, lido no mundo do roteador (não no do jogador, que pode estar em outra dimensão)
     * e só com o chunk carregado; senão, o do índice.
     */
    private ResourceLocation machineOf(RouterBlockEntity router, NodeKey key) {
        if (router.getLevel() instanceof ServerLevel level && level.isLoaded(router.machinePos())) {
            return ConfiguratorArea.machine(level, router);
        }
        NodeIndex.Entry entry = NodeIndex.get(viewer.server).entry(key);
        return entry == null ? BuiltInRegistries.BLOCK.getKey(net.minecraft.world.level.block.Blocks.AIR)
                : entry.machine();
    }

    /**
     * Os nós da máquina que casam com a consulta atual do jogador (a mesma da Lista, reavaliada agora),
     * visíveis, no máximo {@link TabletMenu#MAX_MOVE}. A origem do perfil sai no colar.
     */
    private List<NodeKey> groupKeys(ResourceLocation machine) {
        MinecraftServer server = viewer.server;
        NetworkSavedData data = NetworkSavedData.get(server);
        boolean operator = viewer.hasPermissions(2);
        Map<UUID, String> networkNames = query.networkNames(data);
        List<NodeKey> result = new ArrayList<>();
        for (NodeIndex.Entry entry : NodeIndex.get(server).entries()) {
            if (result.size() >= TabletMenu.MAX_MOVE) {
                break;
            }
            if (entry.machine().equals(machine) && TabletNodes.visible(entry, data, operator, viewer.getUUID())
                    && builder.matchesQuery(entry, data, networkNames)) {
                result.add(entry.key());
            }
        }
        return result;
    }

    /**
     * Cola o perfil guardado nos nós, menos a origem. O que o jogador não vê é ignorado (com um nó só, recusa);
     * o descarregado e o fora do alcance ficam de fora e são contados no aviso. Guarda o que cada nó era
     * ({@link RouterPreset#copyOf}) para o desfazer, só se algo foi colado. Devolve quantos nós receberam.
     */
    private int pasteProfile(ItemStack tablet, Collection<NodeKey> keys, boolean single) {
        RouterPreset profile = storedProfile(viewer);
        ProfileSource source = tablet.get(ModDataComponents.PROFILE_SOURCE.get());
        if (profile == null || source == null) {
            return -1;
        }
        MinecraftServer server = viewer.server;
        NodeIndex index = NodeIndex.get(server);
        NetworkSavedData data = NetworkSavedData.get(server);
        boolean operator = viewer.hasPermissions(2);
        Map<NodeKey, PasteUndo> before = new LinkedHashMap<>();
        PresetApplier.Checked rule = null;
        Component lastName = Component.empty();
        int skipped = 0;
        for (NodeKey key : new LinkedHashSet<>(keys)) {
            NodeIndex.Entry entry = index.entry(key);
            if (entry == null || !TabletNodes.visible(entry, data, operator, viewer.getUUID())) {
                if (single) {
                    return -1;
                }
                continue;
            }
            if (key.equals(source.origin())) {
                continue;
            }
            RouterBlockEntity router = TabletNodes.loadedRouter(server, index, entry);
            if (router == null || !TabletMenu.inReach(viewer, router)) {
                skipped++;
                continue;
            }
            PasteUndo.State was = PasteUndo.State.of(router, false);
            rule = PresetPaste.paste(viewer, router, profile, null);
            before.put(key, PasteUndo.between(was, PasteUndo.State.of(router, false), true));
            lastName = nodeName(entry);
        }
        if (!before.isEmpty()) {
            undo.clear();
            undo.putAll(before);
        }
        int pasted = before.size();
        sendPasteNotice(pasted, skipped, lastName, rule == null ? 0 : keptFaces(profile, rule));
        return pasted;
    }

    /**
     * Cola o perfil guardado nos roteadores {@code keys} (na presa) e nos marcados {@code marks} (máquinas, grupos e
     * blocos; {@link MarkedPaste}), menos a presa da origem. Devolve quantos alvos receberam.
     */
    private int pasteMarked(ItemStack tablet, List<NodeKey> keys, TabletMarks marks) {
        RouterPreset profile = storedProfile(viewer);
        ProfileSource source = tablet.get(ModDataComponents.PROFILE_SOURCE.get());
        if (profile == null || source == null) {
            return -1;
        }
        PresetApplier.Checked rule = PresetApplier.check(viewer, profile, null);
        Map<NodeKey, PasteUndo> changes = new LinkedHashMap<>();
        MarkedPaste.Result result = MarkedPaste.paste(viewer, MarkedTargets.collect(viewer, keys, marks, machines), rule,
                source, changes);
        if (!changes.isEmpty()) {
            undo.clear();
            undo.putAll(changes);
        }
        announce(new PanelNoticePayload(MarkedNotice.paste(result, result.pastedAny() ? keptFaces(profile, rule) : 0),
                result.pastedAny() ? PanelNoticePayload.Undo.UNDO_PASTE : PanelNoticePayload.Undo.NONE, containerId,
                ResourceType.ITEM, Direction.UP));
        return result.routers() + result.groups() + result.machines();
    }

    /** Quantas faces do perfil ficaram com a rede de antes, porque o jogador não pode usar a rede delas. */
    private static int keptFaces(RouterPreset profile, PresetApplier.Checked rule) {
        int faces = 0;
        for (ResourceType type : LoadedTypes.LIST) {
            for (RelativeSide side : RelativeSide.values()) {
                UUID network = profile.network(type, side);
                if (network != null && rule.kept().contains(network)) {
                    faces++;
                }
            }
        }
        return faces;
    }

    /** O nome que o aviso dá ao nó: o nome do roteador ou, sem ele, o da máquina. */
    private static Component nodeName(NodeIndex.Entry entry) {
        return entry.name().isEmpty() ? RouterBlock.machineName(BuiltInRegistries.BLOCK.get(entry.machine()))
                : Component.literal(entry.name());
    }

    private void sendPasteNotice(int pasted, int skipped, Component name, int keptFaces) {
        String base = "gui.wirelessautomate.panel.notice.";
        MutableComponent message;
        if (pasted == 0) {
            message = skipped > 0 ? Component.translatable(base + "nothing_pasted_skipped", skipped)
                    : Component.translatable(base + "nothing_pasted");
        } else if (skipped > 0) {
            message = Component.translatable(base + "pasted_skipped", pasted, skipped);
        } else if (pasted == 1) {
            message = Component.translatable(base + "pasted", name);
        } else {
            message = Component.translatable(base + "pasted_many", pasted);
        }
        if (keptFaces > 0) {
            message.append(" · ").append(Component.translatable(base + "kept_networks", keptFaces));
        }
        announce(new PanelNoticePayload(message, pasted > 0 ? PanelNoticePayload.Undo.UNDO_PASTE
                : PanelNoticePayload.Undo.NONE, containerId, ResourceType.ITEM, Direction.UP));
    }

    /**
     * Devolve cada roteador do último colar ao que era ({@link PasteUndo#restore}: a presa, os perfis e as exceções),
     * se ainda é visível, está carregado e ao alcance, e só no que ninguém mudou depois do colar (senão o desfazer
     * apagaria a mudança), e esquece o colar. Devolve quantos voltaram, ou -1 sem nada para desfazer.
     */
    private int undoPaste() {
        if (undo.isEmpty()) {
            return -1;
        }
        Map<NodeKey, PasteUndo> pending = new LinkedHashMap<>(undo);
        undo.clear();
        int restored = 0;
        int skipped = 0;
        for (Map.Entry<NodeKey, PasteUndo> entry : pending.entrySet()) {
            RouterBlockEntity router = TabletNodes.reachableRouter(viewer, entry.getKey());
            if (router == null || entry.getValue().restore(router) == 0) {
                skipped++;
                continue;
            }
            restored++;
        }
        String base = "gui.wirelessautomate.panel.notice.";
        announce(PanelNoticePayload.plain(skipped > 0 ? Component.translatable(base + "undone_skipped", restored, skipped)
                : Component.translatable(base + (restored == 1 ? "undone.one" : "undone"), restored)));
        return restored;
    }

    private void announce(PanelNoticePayload notice) {
        lastNotice = notice.message();
        ModPayloads.sendNotice(viewer, notice);
    }
}
