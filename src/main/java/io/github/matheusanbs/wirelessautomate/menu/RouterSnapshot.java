package io.github.matheusanbs.wirelessautomate.menu;

import io.github.matheusanbs.wirelessautomate.Config;
import io.github.matheusanbs.wirelessautomate.block.RouterBlockEntity;
import io.github.matheusanbs.wirelessautomate.block.RouterTier;
import io.github.matheusanbs.wirelessautomate.chunk.ChunkLoadState;
import io.github.matheusanbs.wirelessautomate.filter.Filter;
import io.github.matheusanbs.wirelessautomate.network.Chemicals;
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
import io.github.matheusanbs.wirelessautomate.range.RemoteCapabilities;
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
import net.minecraft.resources.ResourceLocation;
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
 * @param attachedFront frente horizontal da máquina presa, no índice de {@link Direction#ordinal()}
 *                 ({@link RouterBlockEntity#attachedFront()}, lida só no {@link #capture}); −1 sem frente
 *                 ou com a frente vertical. A tela mostra os lados da presa relativos a ela
 * @param networks redes que o jogador pode escolher no seletor e as de outro dono que aparecem em
 *                 alguma face do alvo editado (para a pílula e o resumo mostrarem o nome)
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
        int attachedFront,
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
            int attachedFront, List<NetworkEntry> networks, boolean powered, ItemStack machine,
            BlockState machineState, List<FaceView> faces, ChunkLoadState chunkLoad) {
        this(pos, name, tier, facing, attachedFront, networks, powered, machine, machineState, faces, chunkLoad,
                RangeView.EMPTY);
    }

    /** Sem o estado do upgrade de chunk loading (capturas e prévias que não o mostram). */
    public RouterSnapshot(BlockPos pos, String name, RouterTier tier, Direction facing,
            int attachedFront, List<NetworkEntry> networks, boolean powered, ItemStack machine,
            BlockState machineState, List<FaceView> faces) {
        this(pos, name, tier, facing, attachedFront, networks, powered, machine, machineState, faces,
                ChunkLoadState.NONE);
    }

    /**
     * Uma rede no seletor. {@code owned}: o jogador é o dono. {@code usable}: o jogador pode pôr uma face
     * nela (dono, rede pública ou operador, a regra do servidor, {@link WaNetwork#canUse}); uma rede
     * privada de outro dono que está numa face vem só para mostrar o nome, apagada na lista.
     */
    public record NetworkEntry(UUID id, String name, int color, boolean owned, boolean usable) {
        /**
         * Uma rede marcada como usável (o Vinculador lista as do jogador e a rede ativa dele, mesmo
         * de outro dono; a tela do Vinculador só olha o {@code owned}).
         */
        public NetworkEntry(UUID id, String name, int color, boolean owned) {
            this(id, name, color, owned, true);
        }
    }

    /** O mesmo snapshot com outras redes no seletor. */
    public RouterSnapshot withNetworks(List<NetworkEntry> networks) {
        return new RouterSnapshot(pos, name, tier, facing, attachedFront, networks, powered, machine, machineState,
                faces, chunkLoad, range);
    }

    /** O mesmo snapshot com outras faces (prévias da tela). */
    public RouterSnapshot withFaces(List<FaceView> faces) {
        return new RouterSnapshot(pos, name, tier, facing, attachedFront, networks, powered, machine, machineState,
                faces, chunkLoad, range);
    }

    /** O mesmo snapshot com outra frente da máquina presa (prévias da tela). */
    public RouterSnapshot withAttachedFront(int attachedFront) {
        return new RouterSnapshot(pos, name, tier, facing, attachedFront, networks, powered, machine, machineState,
                faces, chunkLoad, range);
    }

    /** O alvo de edição atual ({@link EditTarget#ATTACHED_TARGET} sem o Upgrade de Alcance). */
    public EditTarget target() {
        return range.target();
    }

    /** Tudo igual menos, talvez, as redes do seletor. */
    public boolean sameBody(RouterSnapshot other) {
        return pos.equals(other.pos) && name.equals(other.name) && tier == other.tier && facing == other.facing
                && attachedFront == other.attachedFront && powered == other.powered
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
        return java.util.Objects.hash(pos, name, tier, facing, attachedFront, networks, powered,
                ItemStack.hashItemAndComponents(machine), machine.getCount(), machineState, faces, chunkLoad, range);
    }

    /**
     * Configuração de uma face da máquina para um tipo.
     *
     * @param slots      o que a face acessa: slots (itens), tanques (fluidos e químicos) ou 1 (energia e Source);
     *                   {@code -1} se a máquina não tem essa capability nessa face
     * @param filterSize entradas do filtro (0 = passa tudo)
     * @param blacklist  o filtro é lista negra
     * @param network    a rede da face (no alvo editado); vazia sem rede ou se a rede não existe mais
     */
    public record FaceView(PortMode mode, int priority, RedstoneMode redstone, int slots, int filterSize,
            boolean blacklist, Optional<UUID> network) {
        /** Sem rede (prévias da tela). */
        public FaceView(PortMode mode, int priority, RedstoneMode redstone, int slots, int filterSize,
                boolean blacklist) {
            this(mode, priority, redstone, slots, filterSize, blacklist, Optional.empty());
        }

        public boolean available() {
            return slots >= 0;
        }

        /** A mesma face em outra rede. */
        public FaceView withNetwork(Optional<UUID> network) {
            return new FaceView(mode, priority, redstone, slots, filterSize, blacklist, network);
        }
    }

    /** Rede da face {@code face} (no quadro do alvo) no tipo {@code type}; vazia sem rede. */
    public Optional<UUID> network(ResourceType type, Direction face) {
        return face(type, face).network();
    }

    /** As redes distintas das seis faces do tipo {@code type}, na ordem das faces ({@link Direction#values()}). */
    public List<UUID> networks(ResourceType type) {
        List<UUID> ids = new ArrayList<>(2);
        for (Direction face : Direction.values()) {
            face(type, face).network().filter(id -> !ids.contains(id)).ifPresent(ids::add);
        }
        return ids;
    }

    /** A entrada do seletor com a rede {@code id}, ou {@code null} se o snapshot não a traz. */
    public @Nullable NetworkEntry entry(UUID id) {
        for (NetworkEntry entry : networks) {
            if (entry.id.equals(id)) {
                return entry;
            }
        }
        return null;
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
     * <p>O seletor traz as redes do jogador, as públicas de outros donos e as de outro dono que estão em
     * alguma face, de qualquer tipo, do alvo editado ({@code owned = false} nas de outro dono). Os slots de cada face vêm dos {@code BlockCapabilityCache} do
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
        return capture(router, player, target, target.attached() ? router.facing() : Direction.NORTH);
    }

    /**
     * Como {@link #capture(RouterBlockEntity, ServerPlayer, EditTarget)}, com a face selecionada no menu
     * ({@code selectedFace}: absoluta na presa, canônica num alvo remoto), que vai na {@link RangeView#face()}.
     */
    public static RouterSnapshot capture(RouterBlockEntity router, ServerPlayer player, EditTarget target,
            Direction selectedFace) {
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
        RangeView range = RangeView.EMPTY;
        EditTarget editing = EditTarget.ATTACHED_TARGET;
        RemoteMachine viewed = null;
        MachineGroup viewedGroup = null;
        if (router.rangeActive()) {
            Captured captured = captureRange(router, target, selectedFace);
            range = captured.view();
            editing = range.target();
            viewed = captured.viewed();
            viewedGroup = captured.group();
        }

        Level level = router.getLevel();
        BlockState machineState;
        if (editing.attached() || viewedGroup == null || level == null) {
            editing = editing.attached() ? editing : EditTarget.ATTACHED_TARGET;
            machineState = level == null ? Blocks.AIR.defaultBlockState() : level.getBlockState(router.machinePos());
        } else if (viewed != null) {
            machineState = facingNorth(level.getBlockState(viewed.pos()));
        } else {
            // Nenhuma máquina do alvo carregada: o bloco do grupo, e as faces sem capability conhecida.
            machineState = BuiltInRegistries.BLOCK.get(viewedGroup.block()).defaultBlockState();
        }
        ItemStack machine = new ItemStack(machineState.getBlock().asItem());

        ResourceType[] types = ResourceType.values();
        FaceView[] faces = new FaceView[types.length * 6];
        for (ResourceType type : types) {
            for (Direction face : Direction.values()) {
                FaceConfig config = router.face(editing, type, face);
                int slots;
                if (editing.attached()) {
                    slots = slots(router.attachedCaches(), type, face);
                } else if (viewed != null) {
                    // A face da tela é canônica; a da máquina vem da frente dela.
                    Direction absolute = Direction.values()[RemoteSide.fromCanonical(face.ordinal())
                            .toAbsolute(viewed.front())];
                    slots = RemoteCapabilities.slots(level, viewed.pos(), type, absolute);
                } else {
                    slots = -1;
                }
                // Uma rede que não existe mais vai vazia: a tela mostra "Sem rede" (a face guarda o UUID).
                WaNetwork network = config.network() == null ? null : data.network(config.network());
                faces[index(type, face)] = new FaceView(config.mode(), config.priority(), config.redstone(),
                        slots, config.filter().entries().size(),
                        config.filter().listMode() == Filter.ListMode.BLACKLIST,
                        Optional.ofNullable(network).map(WaNetwork::id));
                // Rede de outro dono numa face aparece no seletor, para o jogador ver onde a face está.
                if (network != null && !network.owner().equals(player.getUUID())
                        && networks.stream().noneMatch(e -> e.id().equals(network.id()))) {
                    networks.add(new NetworkEntry(network.id(), network.name(), network.color(), false,
                            network.canUse(player)));
                }
            }
        }

        return new RouterSnapshot(router.getBlockPos(), router.name(), router.tier(), router.facing(),
                router.attachedFront(), List.copyOf(networks), router.powered(), machine,
                machineState, List.of(faces), router.chunkLoadState(), range);
    }

    /** A visão das remotas, mais a máquina que o visor e os slots mostram (nula se nenhuma carregada) e o grupo do alvo. */
    private record Captured(RangeView view, @Nullable RemoteMachine viewed, @Nullable MachineGroup group) {
    }

    /**
     * Monta a {@link RangeView}. O estado de cada máquina: fora do raio ou além do limite pela regra do
     * motor ({@link RouterBlockEntity#machineReach()}); das ativas, {@code isLoaded} primeiro (nada aqui
     * carrega chunk), depois a checagem barata de {@link #state}. Um alvo que não existe vira a presa.
     */
    private static Captured captureRange(RouterBlockEntity router, EditTarget target, Direction face) {
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
            RangeView.MachineState state = state(level, remotes, remote, reach[i]);
            // Só quando o bloco já não bate: a consulta de capability da trocada não roda nas outras.
            ResourceLocation replacedBy = state == RangeView.MachineState.MISSING ? router.replacedBy(remote, reach[i]) : null;
            if (replacedBy != null) {
                state = RangeView.MachineState.REPLACED;
            }
            machines.add(new RangeView.MachineView(pos, remote.group(), distance, remote.hasException(), state,
                    replacedBy));
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
                List.copyOf(machines), editing, face);
        return new Captured(view, viewed, group);
    }

    private static RangeView.MachineState state(@Nullable Level level, RemoteMachines remotes,
            RemoteMachine remote, RangeRules.Reach reach) {
        return switch (reach) {
            case OUT_OF_RANGE -> RangeView.MachineState.OUT_OF_RANGE;
            case OVER_LIMIT -> RangeView.MachineState.OVER_LIMIT;
            case ACTIVE -> {
                // Antes de qualquer consulta: ler um chunk descarregado o carregaria.
                if (level == null || !level.isLoaded(remote.pos())) {
                    yield RangeView.MachineState.UNLOADED;
                }
                // Checagem barata, sem criar caches nem listeners: o bloco é o do grupo e, se o bloco tem
                // block entity, ela existe. Um bloco sem block entity (a composteira) não tem como perder
                // a capability sem trocar de bloco.
                BlockState state = level.getBlockState(remote.pos());
                if (!BuiltInRegistries.BLOCK.getKey(state.getBlock()).equals(remotes.groupOf(remote).block())
                        || (state.hasBlockEntity() && level.getBlockEntity(remote.pos()) == null)) {
                    yield RangeView.MachineState.MISSING;
                }
                yield RangeView.MachineState.LOADED;
            }
        };
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
            buf.writeBoolean(entry.usable);
        }
    }

    private static List<NetworkEntry> decodeNetworks(RegistryFriendlyByteBuf buf) {
        int networkCount = buf.readVarInt();
        List<NetworkEntry> networks = new ArrayList<>(networkCount);
        for (int i = 0; i < networkCount; i++) {
            networks.add(new NetworkEntry(buf.readUUID(), buf.readUtf(64), buf.readInt(), buf.readBoolean(),
                    buf.readBoolean()));
        }
        return List.copyOf(networks);
    }

    private static void encodeBody(RegistryFriendlyByteBuf buf, RouterSnapshot s) {
        buf.writeBlockPos(s.pos);
        buf.writeUtf(s.name, NAME_LENGTH);
        buf.writeEnum(s.tier);
        buf.writeEnum(s.facing);
        buf.writeVarInt(s.attachedFront + 1);
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
            buf.writeOptional(face.network, (b, id) -> b.writeUUID(id));
        }
        buf.writeEnum(s.chunkLoad);
        RangeView.encode(buf, s.range);
    }

    private static RouterSnapshot decodeBody(RegistryFriendlyByteBuf buf) {
        BlockPos pos = buf.readBlockPos();
        String name = buf.readUtf(NAME_LENGTH);
        RouterTier tier = buf.readEnum(RouterTier.class);
        Direction facing = buf.readEnum(Direction.class);
        int attachedFront = buf.readVarInt() - 1;
        boolean powered = buf.readBoolean();
        ItemStack machine = ItemStack.OPTIONAL_STREAM_CODEC.decode(buf);
        BlockState machineState = Block.stateById(buf.readVarInt());
        int faceCount = buf.readVarInt();
        List<FaceView> faces = new ArrayList<>(faceCount);
        for (int i = 0; i < faceCount; i++) {
            faces.add(new FaceView(buf.readEnum(PortMode.class), buf.readVarInt(),
                    buf.readEnum(RedstoneMode.class), buf.readVarInt(), buf.readVarInt(), buf.readBoolean(),
                    buf.readOptional(b -> b.readUUID())));
        }
        ChunkLoadState chunkLoad = buf.readEnum(ChunkLoadState.class);
        RangeView range = RangeView.decode(buf);
        return new RouterSnapshot(pos, name, tier, facing, attachedFront, List.of(), powered, machine,
                machineState, List.copyOf(faces), chunkLoad, range);
    }
}
