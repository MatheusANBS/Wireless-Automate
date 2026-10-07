package io.github.matheusanbs.wirelessautomate.menu;

import io.github.matheusanbs.wirelessautomate.block.RouterBlockEntity;
import io.github.matheusanbs.wirelessautomate.block.RouterTier;
import io.github.matheusanbs.wirelessautomate.filter.Filter;
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
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.energy.IEnergyStorage;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.items.IItemHandler;

/**
 * Tudo que a tela do roteador mostra, montado no servidor e enviado ao cliente só com a tela
 * aberta. As faces são absolutas (as da máquina), como a tela as mostra.
 *
 * @param name     nome do nó dado pelo jogador; vazio = sem nome (a tela mostra o da máquina)
 * @param typeNetworks rede de cada aba, por {@link ResourceType#ordinal()}; vazia se não tiver ou se
 *                 ela não existir mais
 * @param networks redes que o jogador pode escolher no seletor
 * @param machine  ícone da máquina conectada ({@link ItemStack#EMPTY} se não houver item)
 * @param machineState estado do bloco da máquina, para o visor 3D (ar se não houver)
 * @param faces    {@code ResourceType.values().length × 6}, no índice de {@link #index}
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
        List<FaceView> faces) {

    /** Uma rede no seletor. {@code owned}: o jogador é o dono. */
    public record NetworkEntry(UUID id, String name, int color, boolean owned) {
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
     * <p>O seletor traz as redes do jogador e, se for de outro dono, a rede atual do roteador
     * ({@code owned = false}). Os slots de cada face vêm dos {@code BlockCapabilityCache} do
     * roteador, sem consulta direta de capability. O ícone da máquina é só o item do bloco, sem os
     * dados do block entity, para o pacote continuar pequeno.
     */
    public static RouterSnapshot capture(RouterBlockEntity router, ServerPlayer player) {
        NetworkSavedData data = NetworkSavedData.get(player.server);
        List<NetworkEntry> networks = new ArrayList<>();
        for (WaNetwork network : data.networksOf(player.getUUID())) {
            networks.add(new NetworkEntry(network.id(), network.name(), network.color(), true));
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
                machineState, List.of(faces));
    }

    /** Slots (itens), tanques (fluidos) ou 1 (energia) da face; {@code -1} sem a capability. */
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
            // Químicos só com o Mekanism, que ainda não está integrado.
            case CHEMICAL -> -1;
        };
    }

    public static final StreamCodec<RegistryFriendlyByteBuf, RouterSnapshot> STREAM_CODEC =
            StreamCodec.of(RouterSnapshot::encode, RouterSnapshot::decode);

    private static void encode(RegistryFriendlyByteBuf buf, RouterSnapshot s) {
        buf.writeBlockPos(s.pos);
        buf.writeUtf(s.name, 64);
        buf.writeEnum(s.tier);
        buf.writeEnum(s.facing);
        buf.writeVarInt(s.typeNetworks.size());
        for (Optional<UUID> network : s.typeNetworks) {
            buf.writeOptional(network, (b, id) -> b.writeUUID(id));
        }
        buf.writeVarInt(s.networks.size());
        for (NetworkEntry entry : s.networks) {
            buf.writeUUID(entry.id);
            buf.writeUtf(entry.name, 64);
            buf.writeInt(entry.color);
            buf.writeBoolean(entry.owned);
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
    }

    private static RouterSnapshot decode(RegistryFriendlyByteBuf buf) {
        BlockPos pos = buf.readBlockPos();
        String name = buf.readUtf(64);
        RouterTier tier = buf.readEnum(RouterTier.class);
        Direction facing = buf.readEnum(Direction.class);
        int typeCount = buf.readVarInt();
        List<Optional<UUID>> typeNetworks = new ArrayList<>(typeCount);
        for (int i = 0; i < typeCount; i++) {
            typeNetworks.add(buf.readOptional(b -> b.readUUID()));
        }
        int networkCount = buf.readVarInt();
        List<NetworkEntry> networks = new ArrayList<>(networkCount);
        for (int i = 0; i < networkCount; i++) {
            networks.add(new NetworkEntry(buf.readUUID(), buf.readUtf(64), buf.readInt(), buf.readBoolean()));
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
        return new RouterSnapshot(pos, name, tier, facing, List.copyOf(typeNetworks), List.copyOf(networks), powered, machine,
                machineState, List.copyOf(faces));
    }
}
