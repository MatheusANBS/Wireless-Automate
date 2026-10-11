package io.github.matheusanbs.wirelessautomate.menu;

import io.github.matheusanbs.wirelessautomate.Config;
import io.github.matheusanbs.wirelessautomate.block.RouterBlockEntity;
import io.github.matheusanbs.wirelessautomate.block.RouterTier;
import io.github.matheusanbs.wirelessautomate.chunk.ChunkLoadState;
import io.github.matheusanbs.wirelessautomate.filter.Filter;
import io.github.matheusanbs.wirelessautomate.network.Chemicals;
import io.github.matheusanbs.wirelessautomate.network.LoadedTypes;
import io.github.matheusanbs.wirelessautomate.network.FaceConfig;
import io.github.matheusanbs.wirelessautomate.network.NetworkSavedData;
import io.github.matheusanbs.wirelessautomate.network.PortMode;
import io.github.matheusanbs.wirelessautomate.network.RedstoneMode;
import io.github.matheusanbs.wirelessautomate.network.ResourceType;
import io.github.matheusanbs.wirelessautomate.network.WaNetwork;
import io.github.matheusanbs.wirelessautomate.range.EditTarget;
import io.github.matheusanbs.wirelessautomate.range.MachineCaches;
import io.github.matheusanbs.wirelessautomate.range.MachineGroup;
import io.github.matheusanbs.wirelessautomate.range.RangeRules;
import io.github.matheusanbs.wirelessautomate.range.RemoteMachine;
import io.github.matheusanbs.wirelessautomate.range.RemoteMachines;
import io.github.matheusanbs.wirelessautomate.range.RemoteSide;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.neoforged.neoforge.energy.IEnergyStorage;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.items.IItemHandler;
import org.jetbrains.annotations.Nullable;

/**
 * Tudo que a tela do roteador mostra, montado no servidor e enviado ao cliente só com a tela
 * aberta. Na máquina presa, as faces são absolutas (as da máquina), como a tela as mostra; num alvo
 * remoto ({@link RangeView}), canônicas.
 *
 * <p>Depois da abertura (que leva tudo, {@link #STREAM_CODEC}), o corpo ({@link #BODY_CODEC}) e as
 * redes do seletor ({@link #NETWORKS_CODEC}) vão em pacotes separados, cada um só quando muda.
 *
 * @param name     nome do nó dado pelo jogador; vazio = sem nome (a tela mostra o da máquina)
 * @param typeNetworks rede de cada aba, por {@link ResourceType#ordinal()}; vazia se não tiver ou se
 *                 ela não existir mais
 * @param networks redes que o jogador pode escolher no seletor
 * @param machine  ícone da máquina conectada ({@link ItemStack#EMPTY} se não houver item)
 * @param machineState estado do bloco da máquina, para o visor 3D (ar se não houver)
 * @param faces    {@code ResourceType.values().length × 6}, no índice de {@link #index}
 * @param chunkLoad estado do Upgrade de chunk loading ({@link ChunkLoadState#NONE} sem upgrade)
 * @param range    as máquinas remotas e o alvo de edição ({@link RangeView#EMPTY} sem o Upgrade de
 *                 Alcance valendo). Com um alvo remoto ({@code range.target()} de grupo ou máquina),
 *                 {@code faces} são as do alvo no quadro canônico (frente = Norte, ver
 *                 {@link RemoteSide#fromCanonical}) e {@code machine}/{@code machineState} são do bloco
 *                 do alvo, virado para Norte
 */
public record RouterSnapshot(
        BlockPos pos,
        String name,
        RouterTier tier,
        Direction facing,
        List<Optional<UUID>> typeNetworks,
        List<NetworkEntry> networks,
        boolean powered,
        ItemStack machine,
        BlockState machineState,
        List<FaceView> faces,
        ChunkLoadState chunkLoad,
        RangeView range) {

    /** Tamanho máximo dos nomes (do nó e dos grupos) no pacote. */
    public static final int NAME_LENGTH = 64;

    /** Sem as máquinas remotas (o snapshot de sempre, sem o Upgrade de Alcance). */
    public RouterSnapshot(BlockPos pos, String name, RouterTier tier, Direction facing,
            List<Optional<UUID>> typeNetworks, List<NetworkEntry> networks, boolean powered, ItemStack machine,
            BlockState machineState, List<FaceView> faces, ChunkLoadState chunkLoad) {
        this(pos, name, tier, facing, typeNetworks, networks, powered, machine, machineState, faces, chunkLoad,
                RangeView.EMPTY);
    }

    /** Sem o estado do upgrade de chunk loading (capturas e prévias que não o mostram). */
    public RouterSnapshot(BlockPos pos, String name, RouterTier tier, Direction facing,
            List<Optional<UUID>> typeNetworks, List<NetworkEntry> networks, boolean powered, ItemStack machine,
            BlockState machineState, List<FaceView> faces) {
        this(pos, name, tier, facing, typeNetworks, networks, powered, machine, machineState, faces,
                ChunkLoadState.NONE);
    }

    /** Uma rede no seletor. {@code owned}: o jogador é o dono. */
    public record NetworkEntry(UUID id, String name, int color, boolean owned) {
    }

    /** O mesmo snapshot com outras redes no seletor. */
    public RouterSnapshot withNetworks(List<NetworkEntry> networks) {
        return new RouterSnapshot(pos, name, tier, facing, typeNetworks, networks, powered, machine, machineState,
                faces, chunkLoad, range);
    }

    /** O mesmo snapshot com outras faces (prévias da tela). */
    public RouterSnapshot withFaces(List<FaceView> faces) {
        return new RouterSnapshot(pos, name, tier, facing, typeNetworks, networks, powered, machine, machineState,
                faces, chunkLoad, range);
    }

    /** O mesmo snapshot com outras redes por aba (prévias da tela). */
    public RouterSnapshot withTypeNetworks(List<Optional<UUID>> typeNetworks) {
        return new RouterSnapshot(pos, name, tier, facing, typeNetworks, networks, powered, machine, machineState,
                faces, chunkLoad, range);
    }

    /** O alvo de edição atual ({@link EditTarget#ATTACHED_TARGET} sem o Upgrade de Alcance). */
    public EditTarget target() {
        return range.target();
    }

    /** Tudo igual menos, talvez, as redes do seletor. */
    public boolean sameBody(RouterSnapshot other) {
        return pos.equals(other.pos) && name.equals(other.name) && tier == other.tier && facing == other.facing
                && typeNetworks.equals(other.typeNetworks) && powered == other.powered
                && ItemStack.matches(machine, other.machine) && machineState == other.machineState
                && faces.equals(other.faces) && chunkLoad == other.chunkLoad && range.equals(other.range);
    }

    /** Igualdade de valor; o {@link ItemStack} da máquina é comparado por {@link ItemStack#matches}. */
    @Override
    public boolean equals(Object o) {
        return o instanceof RouterSnapshot other && sameBody(other) && networks.equals(other.networks);
    }

    @Override
    public int hashCode() {
        return java.util.Objects.hash(pos, name, tier, facing, typeNetworks, networks, powered,
                ItemStack.hashItemAndComponents(machine), machine.getCount(), machineState, faces, chunkLoad, range);
    }

    /**
     * Configuração de uma face da máquina para um tipo.
     *
     * @param slots      o que a face acessa: slots (itens), tanques (fluidos) ou 1 (energia);
     *                   {@code -1} se a máquina não tem essa capability nessa face
     * @param filterSize entradas do filtro (0 = passa tudo)
     * @param blacklist  o filtro é lista negra
     */
    public record FaceView(PortMode mode, int priority, RedstoneMode redstone, int slots, int filterSize,
            boolean blacklist) {
        public boolean available() {
            return slots >= 0;
        }
    }

    /** Rede da aba {@code type}. */
    public Optional<UUID> network(ResourceType type) {
        return typeNetworks.get(type.ordinal());
    }

    public static int index(ResourceType type, Direction face) {
        return type.ordinal() * 6 + face.get3DDataValue();
    }

    public FaceView face(ResourceType type, Direction face) {
        return faces.get(index(type, face));
    }

    /**
     * Monta o snapshot do roteador para o jogador (as redes do seletor dependem dele). Só no servidor.
     *
     * <p>O seletor traz as redes do jogador, as públicas de outros donos e, se for de outro dono, a
     * rede atual do roteador ({@code owned = false} nas de outro dono). Os slots de cada face vêm dos {@code BlockCapabilityCache} do
     * roteador, sem consulta direta de capability. O ícone da máquina é só o item do bloco, sem os
     * dados do block entity, para o pacote continuar pequeno.
     */
    public static RouterSnapshot capture(RouterBlockEntity router, ServerPlayer player) {
        return capture(router, player, EditTarget.ATTACHED_TARGET);
    }

    /**
     * Como {@link #capture(RouterBlockEntity, ServerPlayer)}, editando {@code target}: com o Upgrade de
     * Alcance valendo, o snapshot leva a {@link RangeView} e, com um alvo remoto, as faces do alvo no
     * quadro canônico e o bloco dele no visor. Quem chama confere o alvo ({@link EditTargetRef}); um que
     * não existe, ou sem o upgrade, vira a presa.
     */
    public static RouterSnapshot capture(RouterBlockEntity router, ServerPlayer player, EditTarget target) {
        NetworkSavedData data = NetworkSavedData.get(player.server);
        List<NetworkEntry> networks = new ArrayList<>();
        for (WaNetwork network : data.networksOf(player.getUUID())) {
            networks.add(new NetworkEntry(network.id(), network.name(), network.color(), true));
        }
        // Redes públicas de outros donos também podem ser escolhidas (privacidade no Tablet).
        for (WaNetwork network : data.networks()) {
            if (network.isPublic() && !network.owner().equals(player.getUUID())) {
                networks.add(new NetworkEntry(network.id(), network.name(), network.color(), false));
            }
        }
        List<Optional<UUID>> typeNetworks = new ArrayList<>();
        for (ResourceType type : ResourceType.values()) {
            UUID networkId = router.networkId(type);
            WaNetwork current = networkId == null ? null : data.network(networkId);
            typeNetworks.add(Optional.ofNullable(current).map(WaNetwork::id));
            // Rede de outro dono aparece no seletor para o jogador ver onde o roteador está.
            if (current != null && !current.owner().equals(player.getUUID())
                    && networks.stream().noneMatch(e -> e.id().equals(current.id()))) {
                networks.add(new NetworkEntry(current.id(), current.name(), current.color(), false));
            }
        }

        RangeView range = RangeView.EMPTY;
        EditTarget editing = EditTarget.ATTACHED_TARGET;
        RemoteMachine viewed = null;
        MachineGroup viewedGroup = null;
        if (router.rangeActive()) {
            Captured captured = captureRange(router, target);
            range = captured.view();
            editing = range.target();
            viewed = captured.viewed();
            viewedGroup = captured.group();
        }

        Level level = router.getLevel();
        BlockState machineState;
        MachineCaches caches;
        if (editing.attached() || viewedGroup == null || level == null) {
            editing = editing.attached() ? editing : EditTarget.ATTACHED_TARGET;
            machineState = level == null ? Blocks.AIR.defaultBlockState() : level.getBlockState(router.machinePos());
            caches = router.attachedCaches();
        } else if (viewed != null) {
            machineState = facingNorth(level.getBlockState(viewed.pos()));
            caches = router.remoteCaches(viewed.pos());
        } else {
            // Nenhuma máquina do alvo carregada: o bloco do grupo, e as faces sem capability conhecida.
            machineState = BuiltInRegistries.BLOCK.get(viewedGroup.block()).defaultBlockState();
            caches = null;
        }
        ItemStack machine = new ItemStack(machineState.getBlock().asItem());

        ResourceType[] types = ResourceType.values();
        FaceView[] faces = new FaceView[types.length * 6];
        for (ResourceType type : types) {
            for (Direction face : Direction.values()) {
                FaceConfig config = router.face(editing, type, face);
                int slots;
                if (editing.attached()) {
                    slots = slots(caches, type, face);
                } else if (caches != null) {
                    // A face da tela é canônica; a da máquina vem da frente dela.
                    Direction absolute = Direction.values()[RemoteSide.fromCanonical(face.ordinal())
                            .toAbsolute(viewed.front())];
                    slots = slots(caches, type, absolute);
                } else {
                    slots = -1;
                }
                faces[index(type, face)] = new FaceView(config.mode(), config.priority(), config.redstone(),
                        slots, config.filter().entries().size(),
                        config.filter().listMode() == Filter.ListMode.BLACKLIST);
            }
        }

        return new RouterSnapshot(router.getBlockPos(), router.name(), router.tier(), router.facing(),
                List.copyOf(typeNetworks), List.copyOf(networks), router.powered(), machine,
                machineState, List.of(faces), router.chunkLoadState(), range);
    }

    /** A visão das remotas, mais a máquina que o visor e os slots mostram (nula se nenhuma carregada) e o grupo do alvo. */
    private record Captured(RangeView view, @Nullable RemoteMachine viewed, @Nullable MachineGroup group) {
    }

    /**
     * Monta a {@link RangeView}. O estado de cada máquina: fora do raio ou além do limite pela regra do
     * motor ({@link RouterBlockEntity#machineReach()}); das ativas, {@code isLoaded} primeiro (nada aqui
     * carrega chunk), depois o bloco (tem de ser o do grupo) e as capabilities, pelos
     * {@link MachineCaches} dela. Um alvo que não existe vira a presa.
     */
    private static Captured captureRange(RouterBlockEntity router, EditTarget target) {
        Level level = router.getLevel();
        RemoteMachines remotes = router.remoteMachines();
        List<RemoteMachine> list = remotes.machines();
        RangeRules.Reach[] reach = router.machineReach();
        BlockPos center = router.getBlockPos();
        List<RangeView.MachineView> machines = new ArrayList<>(list.size());
        for (int i = 0; i < list.size(); i++) {
            RemoteMachine remote = list.get(i);
            BlockPos pos = remote.pos();
            int distance = RangeRules.chebyshev(center.getX(), center.getY(), center.getZ(), pos.getX(), pos.getY(),
                    pos.getZ());
            machines.add(new RangeView.MachineView(pos, remote.group(), distance, remote.hasException(),
                    state(router, level, remotes, remote, reach[i])));
        }
        List<RangeView.GroupView> groups = new ArrayList<>(remotes.groups().size());
        for (MachineGroup group : remotes.groups()) {
            int count = 0;
            int exceptions = 0;
            for (RemoteMachine remote : list) {
                if (remote.group() == group.id()) {
                    count++;
                    if (remote.hasException()) {
                        exceptions++;
                    }
                }
            }
            String name = group.name().length() > NAME_LENGTH ? group.name().substring(0, NAME_LENGTH) : group.name();
            groups.add(new RangeView.GroupView(group.id(), group.block(), name, count, exceptions));
        }

        EditTarget editing = target;
        RemoteMachine viewed = null;
        MachineGroup group = null;
        switch (target.kind()) {
            case ATTACHED -> {
            }
            case GROUP -> {
                group = remotes.group(target.id());
                if (group == null) {
                    editing = EditTarget.ATTACHED_TARGET;
                } else {
                    for (int i = 0; i < list.size(); i++) {
                        if (list.get(i).group() == group.id()
                                && machines.get(i).state() == RangeView.MachineState.LOADED) {
                            viewed = list.get(i);
                            break;
                        }
                    }
                }
            }
            case MACHINE -> {
                RemoteMachine remote = remotes.machine(target.id());
                if (remote == null) {
                    editing = EditTarget.ATTACHED_TARGET;
                } else {
                    group = remotes.groupOf(remote);
                    if (machines.get(target.id()).state() == RangeView.MachineState.LOADED) {
                        viewed = remote;
                    }
                }
            }
        }
        RouterTier tier = router.tier();
        RangeView view = new RangeView(Config.machineRadius(tier), Config.maxMachines(tier), List.copyOf(groups),
                List.copyOf(machines), editing);
        return new Captured(view, viewed, group);
    }

    private static RangeView.MachineState state(RouterBlockEntity router, @Nullable Level level, RemoteMachines remotes,
            RemoteMachine remote, RangeRules.Reach reach) {
        return switch (reach) {
            case OUT_OF_RANGE -> RangeView.MachineState.OUT_OF_RANGE;
            case OVER_LIMIT -> RangeView.MachineState.OVER_LIMIT;
            case ACTIVE -> {
                // Antes de qualquer consulta: ler um chunk descarregado o carregaria.
                if (level == null || !level.isLoaded(remote.pos())) {
                    yield RangeView.MachineState.UNLOADED;
                }
                BlockState state = level.getBlockState(remote.pos());
                if (!BuiltInRegistries.BLOCK.getKey(state.getBlock()).equals(remotes.groupOf(remote).block())
                        || !hasAnyCapability(router.remoteCaches(remote.pos()))) {
                    yield RangeView.MachineState.MISSING;
                }
                yield RangeView.MachineState.LOADED;
            }
        };
    }

    /** A máquina tem, em alguma face, a capability de algum tipo carregado. */
    private static boolean hasAnyCapability(MachineCaches caches) {
        for (ResourceType type : LoadedTypes.LIST) {
            for (Direction face : Direction.values()) {
                if (slots(caches, type, face) >= 0) {
                    return true;
                }
            }
        }
        return false;
    }

    /** O estado com a frente (horizontal) virada para Norte, para o visor mostrar a frente. */
    private static BlockState facingNorth(BlockState state) {
        if (state.hasProperty(BlockStateProperties.HORIZONTAL_FACING)) {
            return state.setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.NORTH);
        }
        if (state.hasProperty(BlockStateProperties.FACING)
                && state.getValue(BlockStateProperties.FACING).getAxis().isHorizontal()) {
            return state.setValue(BlockStateProperties.FACING, Direction.NORTH);
        }
        return state;
    }

    /** Slots (itens), tanques (fluidos e químicos) ou 1 (energia e Source) da face; {@code -1} sem a capability. */
    private static int slots(MachineCaches caches, ResourceType type, Direction face) {
        return switch (type) {
            case ITEM -> {
                IItemHandler items = caches.items(face);
                yield items == null ? -1 : items.getSlots();
            }
            case FLUID -> {
                IFluidHandler fluids = caches.fluids(face);
                yield fluids == null ? -1 : fluids.getTanks();
            }
            case ENERGY -> {
                IEnergyStorage energy = caches.energy(face);
                yield energy == null ? -1 : 1;
            }
            case CHEMICAL -> {
                Object chemicals = caches.chemicals(face);
                yield chemicals == null ? -1 : Chemicals.tanks(chemicals);
            }
            case SOURCE -> caches.arsSource(face) == null ? -1 : 1;
        };
    }

    /** Snapshot inteiro, para o buffer de abertura. */
    public static final StreamCodec<RegistryFriendlyByteBuf, RouterSnapshot> STREAM_CODEC = StreamCodec.of(
            (buf, s) -> {
                encodeBody(buf, s);
                encodeNetworks(buf, s.networks);
            },
            buf -> decodeBody(buf).withNetworks(decodeNetworks(buf)));
    /** Sem as redes do seletor: decodificado com a lista vazia (o cliente mantém a que tem). */
    public static final StreamCodec<RegistryFriendlyByteBuf, RouterSnapshot> BODY_CODEC =
            StreamCodec.of(RouterSnapshot::encodeBody, RouterSnapshot::decodeBody);
    /** Só as redes do seletor. */
    public static final StreamCodec<RegistryFriendlyByteBuf, List<NetworkEntry>> NETWORKS_CODEC =
            StreamCodec.of(RouterSnapshot::encodeNetworks, RouterSnapshot::decodeNetworks);

    private static void encodeNetworks(RegistryFriendlyByteBuf buf, List<NetworkEntry> networks) {
        buf.writeVarInt(networks.size());
        for (NetworkEntry entry : networks) {
            buf.writeUUID(entry.id);
            buf.writeUtf(entry.name, 64);
            buf.writeInt(entry.color);
            buf.writeBoolean(entry.owned);
        }
    }

    private static List<NetworkEntry> decodeNetworks(RegistryFriendlyByteBuf buf) {
        int networkCount = buf.readVarInt();
        List<NetworkEntry> networks = new ArrayList<>(networkCount);
        for (int i = 0; i < networkCount; i++) {
            networks.add(new NetworkEntry(buf.readUUID(), buf.readUtf(64), buf.readInt(), buf.readBoolean()));
        }
        return List.copyOf(networks);
    }

    private static void encodeBody(RegistryFriendlyByteBuf buf, RouterSnapshot s) {
        buf.writeBlockPos(s.pos);
        buf.writeUtf(s.name, NAME_LENGTH);
        buf.writeEnum(s.tier);
        buf.writeEnum(s.facing);
        buf.writeVarInt(s.typeNetworks.size());
        for (Optional<UUID> network : s.typeNetworks) {
            buf.writeOptional(network, (b, id) -> b.writeUUID(id));
        }
        buf.writeBoolean(s.powered);
        ItemStack.OPTIONAL_STREAM_CODEC.encode(buf, s.machine);
        buf.writeVarInt(Block.getId(s.machineState));
        buf.writeVarInt(s.faces.size());
        for (FaceView face : s.faces) {
            buf.writeEnum(face.mode);
            buf.writeVarInt(face.priority);
            buf.writeEnum(face.redstone);
            buf.writeVarInt(face.slots);
            buf.writeVarInt(face.filterSize);
            buf.writeBoolean(face.blacklist);
        }
        buf.writeEnum(s.chunkLoad);
        RangeView.encode(buf, s.range);
    }

    private static RouterSnapshot decodeBody(RegistryFriendlyByteBuf buf) {
        BlockPos pos = buf.readBlockPos();
        String name = buf.readUtf(NAME_LENGTH);
        RouterTier tier = buf.readEnum(RouterTier.class);
        Direction facing = buf.readEnum(Direction.class);
        int typeCount = buf.readVarInt();
        List<Optional<UUID>> typeNetworks = new ArrayList<>(typeCount);
        for (int i = 0; i < typeCount; i++) {
            typeNetworks.add(buf.readOptional(b -> b.readUUID()));
        }
        boolean powered = buf.readBoolean();
        ItemStack machine = ItemStack.OPTIONAL_STREAM_CODEC.decode(buf);
        BlockState machineState = Block.stateById(buf.readVarInt());
        int faceCount = buf.readVarInt();
        List<FaceView> faces = new ArrayList<>(faceCount);
        for (int i = 0; i < faceCount; i++) {
            faces.add(new FaceView(buf.readEnum(PortMode.class), buf.readVarInt(),
                    buf.readEnum(RedstoneMode.class), buf.readVarInt(), buf.readVarInt(), buf.readBoolean()));
        }
        ChunkLoadState chunkLoad = buf.readEnum(ChunkLoadState.class);
        RangeView range = RangeView.decode(buf);
        return new RouterSnapshot(pos, name, tier, facing, List.copyOf(typeNetworks), List.of(), powered, machine,
                machineState, List.copyOf(faces), chunkLoad, range);
    }
}
