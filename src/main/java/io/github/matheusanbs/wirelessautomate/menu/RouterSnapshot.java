package io.github.matheusanbs.wirelessautomate.menu;

import io.github.matheusanbs.wirelessautomate.block.RouterBlockEntity;
import io.github.matheusanbs.wirelessautomate.block.RouterTier;
import io.github.matheusanbs.wirelessautomate.network.PortMode;
import io.github.matheusanbs.wirelessautomate.network.RedstoneMode;
import io.github.matheusanbs.wirelessautomate.network.ResourceType;
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

/**
 * Tudo que a tela do roteador mostra, montado no servidor e enviado ao cliente só com a tela
 * aberta. As faces são absolutas (as da máquina), como a tela as mostra.
 *
 * @param name     nome do nó dado pelo jogador; vazio = sem nome (a tela mostra o da máquina)
 * @param network  rede do roteador, vazia se não tiver ou se ela não existir mais
 * @param networks redes que o jogador pode escolher no seletor
 * @param machine  ícone da máquina conectada ({@link ItemStack#EMPTY} se não houver item)
 * @param faces    {@code ResourceType.values().length × 6}, no índice de {@link #index}
 */
public record RouterSnapshot(
        BlockPos pos,
        String name,
        RouterTier tier,
        Direction facing,
        Optional<UUID> network,
        List<NetworkEntry> networks,
        boolean powered,
        ItemStack machine,
        List<FaceView> faces) {

    /** Uma rede no seletor. {@code owned}: o jogador é o dono. */
    public record NetworkEntry(UUID id, String name, int color, boolean owned) {
    }

    /**
     * Configuração de uma face da máquina para um tipo.
     *
     * @param slots o que a face acessa: slots (itens), tanques (fluidos) ou 1 (energia);
     *              {@code -1} se a máquina não tem essa capability nessa face
     */
    public record FaceView(PortMode mode, int priority, RedstoneMode redstone, int slots) {
        public boolean available() {
            return slots >= 0;
        }
    }

    public static int index(ResourceType type, Direction face) {
        return type.ordinal() * 6 + face.get3DDataValue();
    }

    public FaceView face(ResourceType type, Direction face) {
        return faces.get(index(type, face));
    }

    /** Monta o snapshot do roteador para o jogador (as redes do seletor dependem dele). Só no servidor. */
    public static RouterSnapshot capture(RouterBlockEntity router, ServerPlayer player) {
        // TODO(contrato): implementar (agente do servidor).
        throw new UnsupportedOperationException("TODO");
    }

    public static final StreamCodec<RegistryFriendlyByteBuf, RouterSnapshot> STREAM_CODEC =
            StreamCodec.of(RouterSnapshot::encode, RouterSnapshot::decode);

    private static void encode(RegistryFriendlyByteBuf buf, RouterSnapshot s) {
        buf.writeBlockPos(s.pos);
        buf.writeUtf(s.name, 64);
        buf.writeEnum(s.tier);
        buf.writeEnum(s.facing);
        buf.writeOptional(s.network, (b, id) -> b.writeUUID(id));
        buf.writeVarInt(s.networks.size());
        for (NetworkEntry entry : s.networks) {
            buf.writeUUID(entry.id);
            buf.writeUtf(entry.name, 64);
            buf.writeInt(entry.color);
            buf.writeBoolean(entry.owned);
        }
        buf.writeBoolean(s.powered);
        ItemStack.OPTIONAL_STREAM_CODEC.encode(buf, s.machine);
        buf.writeVarInt(s.faces.size());
        for (FaceView face : s.faces) {
            buf.writeEnum(face.mode);
            buf.writeVarInt(face.priority);
            buf.writeEnum(face.redstone);
            buf.writeVarInt(face.slots);
        }
    }

    private static RouterSnapshot decode(RegistryFriendlyByteBuf buf) {
        BlockPos pos = buf.readBlockPos();
        String name = buf.readUtf(64);
        RouterTier tier = buf.readEnum(RouterTier.class);
        Direction facing = buf.readEnum(Direction.class);
        Optional<UUID> network = buf.readOptional(b -> b.readUUID());
        int networkCount = buf.readVarInt();
        List<NetworkEntry> networks = new ArrayList<>(networkCount);
        for (int i = 0; i < networkCount; i++) {
            networks.add(new NetworkEntry(buf.readUUID(), buf.readUtf(64), buf.readInt(), buf.readBoolean()));
        }
        boolean powered = buf.readBoolean();
        ItemStack machine = ItemStack.OPTIONAL_STREAM_CODEC.decode(buf);
        int faceCount = buf.readVarInt();
        List<FaceView> faces = new ArrayList<>(faceCount);
        for (int i = 0; i < faceCount; i++) {
            faces.add(new FaceView(buf.readEnum(PortMode.class), buf.readVarInt(),
                    buf.readEnum(RedstoneMode.class), buf.readVarInt()));
        }
        return new RouterSnapshot(pos, name, tier, facing, network, List.copyOf(networks), powered, machine,
                List.copyOf(faces));
    }
}
