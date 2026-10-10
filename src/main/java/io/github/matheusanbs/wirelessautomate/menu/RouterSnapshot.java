package io.github.matheusanbs.wirelessautomate.menu;

import io.github.matheusanbs.wirelessautomate.block.RouterBlockEntity;
import io.github.matheusanbs.wirelessautomate.block.RouterTier;
import io.github.matheusanbs.wirelessautomate.chunk.ChunkLoadState;
import io.github.matheusanbs.wirelessautomate.filter.Filter;
import io.github.matheusanbs.wirelessautomate.net.GameCodecs;
import io.github.matheusanbs.wirelessautomate.net.RegistryFriendlyByteBuf;
import io.github.matheusanbs.wirelessautomate.net.StreamCodec;
import io.github.matheusanbs.wirelessautomate.network.Chemicals;
import io.github.matheusanbs.wirelessautomate.network.FaceConfig;
import io.github.matheusanbs.wirelessautomate.network.NetworkSavedData;
import io.github.matheusanbs.wirelessautomate.network.PortMode;
import io.github.matheusanbs.wirelessautomate.network.RedstoneMode;
import io.github.matheusanbs.wirelessautomate.network.ResourceType;
import io.github.matheusanbs.wirelessautomate.network.WaNetwork;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemStackLinkedSet;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.energy.IEnergyStorage;
import net.minecraftforge.fluids.capability.IFluidHandler;
import net.minecraftforge.items.IItemHandler;

/**
 * Tudo que a tela do roteador mostra, montado no servidor e enviado ao cliente só com a tela
 * aberta. As faces são absolutas (as da máquina), como a tela as mostra.
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
        ChunkLoadState chunkLoad) {

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
                faces, chunkLoad);
    }

    /** Tudo igual menos, talvez, as redes do seletor. */
    public boolean sameBody(RouterSnapshot other) {
        return pos.equals(other.pos) && name.equals(other.name) && tier == other.tier && facing == other.facing
                && typeNetworks.equals(other.typeNetworks) && powered == other.powered
                && ItemStack.matches(machine, other.machine) && machineState == other.machineState
                && faces.equals(other.faces) && chunkLoad == other.chunkLoad;
    }

    /** Igualdade de valor; o {@link ItemStack} da máquina é comparado por {@link ItemStack#matches}. */
    @Override
    public boolean equals(Object o) {
        return o instanceof RouterSnapshot other && sameBody(other) && networks.equals(other.networks);
    }

    @Override
    public int hashCode() {
        return java.util.Objects.hash(pos, name, tier, facing, typeNetworks, networks, powered,
                ItemStackLinkedSet.TYPE_AND_TAG.hashCode(machine), machine.getCount(), machineState, faces, chunkLoad);
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

        Level level = router.getLevel();
        BlockState machineState = level == null
                ? Blocks.AIR.defaultBlockState()
                : level.getBlockState(router.machinePos());
        ItemStack machine = new ItemStack(machineState.getBlock().asItem());

        ResourceType[] types = ResourceType.values();
        FaceView[] faces = new FaceView[types.length * 6];
        for (ResourceType type : types) {
            for (Direction face : Direction.values()) {
                FaceConfig config = router.face(type, face);
                faces[index(type, face)] = new FaceView(config.mode(), config.priority(), config.redstone(),
                        slots(router, type, face), config.filter().entries().size(),
                        config.filter().listMode() == Filter.ListMode.BLACKLIST);
            }
        }

        return new RouterSnapshot(router.getBlockPos(), router.name(), router.tier(), router.facing(),
                List.copyOf(typeNetworks), List.copyOf(networks), router.powered(), machine,
                machineState, List.of(faces), router.chunkLoadState());
    }

    /** Slots (itens), tanques (fluidos e químicos) ou 1 (energia e Source) da face; {@code -1} sem a capability. */
    private static int slots(RouterBlockEntity router, ResourceType type, Direction face) {
        return switch (type) {
            case ITEM -> {
                IItemHandler items = router.items(face);
                yield items == null ? -1 : items.getSlots();
            }
            case FLUID -> {
                IFluidHandler fluids = router.fluids(face);
                yield fluids == null ? -1 : fluids.getTanks();
            }
            case ENERGY -> {
                IEnergyStorage energy = router.energy(face);
                yield energy == null ? -1 : 1;
            }
            case CHEMICAL -> {
                Object chemicals = router.chemicals(face);
                yield chemicals == null ? -1 : Chemicals.tanks(chemicals);
            }
            case SOURCE -> router.arsSource(face) == null ? -1 : 1;
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
        buf.writeUtf(s.name, 64);
        buf.writeEnum(s.tier);
        buf.writeEnum(s.facing);
        buf.writeVarInt(s.typeNetworks.size());
        for (Optional<UUID> network : s.typeNetworks) {
            buf.writeOptional(network, (b, id) -> b.writeUUID(id));
        }
        buf.writeBoolean(s.powered);
        GameCodecs.OPTIONAL_ITEM_STACK.encode(buf, s.machine);
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
    }

    private static RouterSnapshot decodeBody(RegistryFriendlyByteBuf buf) {
        BlockPos pos = buf.readBlockPos();
        String name = buf.readUtf(64);
        RouterTier tier = buf.readEnum(RouterTier.class);
        Direction facing = buf.readEnum(Direction.class);
        int typeCount = buf.readVarInt();
        List<Optional<UUID>> typeNetworks = new ArrayList<>(typeCount);
        for (int i = 0; i < typeCount; i++) {
            typeNetworks.add(buf.readOptional(b -> b.readUUID()));
        }
        boolean powered = buf.readBoolean();
        ItemStack machine = GameCodecs.OPTIONAL_ITEM_STACK.decode(buf);
        BlockState machineState = Block.stateById(buf.readVarInt());
        int faceCount = buf.readVarInt();
        List<FaceView> faces = new ArrayList<>(faceCount);
        for (int i = 0; i < faceCount; i++) {
            faces.add(new FaceView(buf.readEnum(PortMode.class), buf.readVarInt(),
                    buf.readEnum(RedstoneMode.class), buf.readVarInt(), buf.readVarInt(), buf.readBoolean()));
        }
        ChunkLoadState chunkLoad = buf.readEnum(ChunkLoadState.class);
        return new RouterSnapshot(pos, name, tier, facing, List.copyOf(typeNetworks), List.of(), powered, machine,
                machineState, List.copyOf(faces), chunkLoad);
    }
}
