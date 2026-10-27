package io.github.matheusanbs.wirelessautomate.menu;

import io.github.matheusanbs.wirelessautomate.Config;
import io.github.matheusanbs.wirelessautomate.network.NetworkSavedData;
import io.github.matheusanbs.wirelessautomate.network.WaNetwork;
import io.github.matheusanbs.wirelessautomate.registry.ModMenus;
import io.github.matheusanbs.wirelessautomate.tv.TvArea;
import io.github.matheusanbs.wirelessautomate.tv.TvBinding;
import io.github.matheusanbs.wirelessautomate.tv.TvBlockEntity;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

/**
 * Menu da tela da Tela de Sinal: sem slots. Os dados de abertura (posição, área, tetos, a rede e o tipo do primeiro
 * valor do layout e as redes que o jogador pode usar) vão no buffer do {@code openMenu}; o resto são os pacotes de
 * tamanho, do layout e da biblioteca, validados no servidor.
 */
public class TvMenu extends AbstractContainerMenu {
    /** No máximo tantas redes na lista da abertura. */
    public static final int MAX_NETWORKS = 256;
    private static final double REACH_SQ = 8.0 * 8.0;

    /** Uma rede que o jogador pode usar, como a lista da tela a mostra. */
    public record NetworkChoice(UUID id, String name, int color) {
        public static final StreamCodec<RegistryFriendlyByteBuf, NetworkChoice> STREAM_CODEC = StreamCodec.composite(
                UUIDUtil.STREAM_CODEC, NetworkChoice::id,
                ByteBufCodecs.stringUtf8(64), NetworkChoice::name,
                ByteBufCodecs.INT, NetworkChoice::color,
                NetworkChoice::new);
    }

    /** O que o buffer de abertura leva. */
    private record Data(BlockPos pos, TvArea area, int maxWidth, int maxHeight, @Nullable UUID currentNetwork,
            String currentType, List<NetworkChoice> networks) {
        static Data read(RegistryFriendlyByteBuf buf) {
            BlockPos pos = BlockPos.STREAM_CODEC.decode(buf);
            TvArea area = new TvArea(buf.readVarInt(), buf.readVarInt(), buf.readVarInt(), buf.readVarInt());
            int maxWidth = buf.readVarInt();
            int maxHeight = buf.readVarInt();
            UUID network = buf.readBoolean() ? UUIDUtil.STREAM_CODEC.decode(buf) : null;
            String type = buf.readUtf(32);
            int size = Math.min(buf.readVarInt(), MAX_NETWORKS);
            List<NetworkChoice> list = new ArrayList<>(size);
            for (int i = 0; i < size; i++) {
                list.add(NetworkChoice.STREAM_CODEC.decode(buf));
            }
            return new Data(pos, area, maxWidth, maxHeight, network, type, list);
        }

        void write(RegistryFriendlyByteBuf buf) {
            BlockPos.STREAM_CODEC.encode(buf, pos);
            buf.writeVarInt(area.width()).writeVarInt(area.height()).writeVarInt(area.anchorCol())
                    .writeVarInt(area.anchorRow());
            buf.writeVarInt(maxWidth).writeVarInt(maxHeight);
            buf.writeBoolean(currentNetwork != null);
            if (currentNetwork != null) {
                UUIDUtil.STREAM_CODEC.encode(buf, currentNetwork);
            }
            buf.writeUtf(currentType, 32);
            buf.writeVarInt(networks.size());
            for (NetworkChoice choice : networks) {
                NetworkChoice.STREAM_CODEC.encode(buf, choice);
            }
        }
    }

    private final Data data;
    private final @Nullable TvBlockEntity tv;

    /** Cliente. */
    public TvMenu(int containerId, Inventory inventory, RegistryFriendlyByteBuf buf) {
        this(containerId, Data.read(buf), null);
    }

    private TvMenu(int containerId, Data data, @Nullable TvBlockEntity tv) {
        super(ModMenus.SIGNAL_SCREEN.get(), containerId);
        this.data = data;
        this.tv = tv;
    }

    /** Servidor: abre a tela da Tela de Sinal; a lista de redes é montada uma vez, na abertura. */
    public static void open(ServerPlayer player, TvBlockEntity tv) {
        List<NetworkChoice> networks = new ArrayList<>();
        for (WaNetwork network : NetworkSavedData.get(player.server).networks()) {
            if (network.canUse(player)) {
                networks.add(new NetworkChoice(network.id(), network.name(), network.color()));
            }
        }
        networks.sort(Comparator.comparing(NetworkChoice::name, String.CASE_INSENSITIVE_ORDER)
                .thenComparing(NetworkChoice::id));
        if (networks.size() > MAX_NETWORKS) {
            networks = new ArrayList<>(networks.subList(0, MAX_NETWORKS));
        }
        TvBinding first = tv.layout().bindings().isEmpty() ? null : tv.layout().bindings().get(0);
        Data data = new Data(tv.getBlockPos(), tv.area(), Config.tvMaxWidth(), Config.tvMaxHeight(), first == null ? null : first.network(),
                first == null ? "" : first.type().key(), networks);
        player.openMenu(new SimpleMenuProvider((containerId, inventory, p) -> new TvMenu(containerId, data, tv),
                Component.translatable("container.wirelessautomate.signal_screen")), data::write);
    }

    public BlockPos pos() {
        return data.pos;
    }

    public TvArea area() {
        return data.area;
    }

    public int maxWidth() {
        return data.maxWidth;
    }

    public int maxHeight() {
        return data.maxHeight;
    }

    /** As redes que o jogador pode usar, ordenadas pelo nome. */
    public List<NetworkChoice> networks() {
        return data.networks;
    }

    /** A rede do primeiro valor do layout na abertura, ou {@code null}. */
    public @Nullable UUID currentNetwork() {
        return data.currentNetwork;
    }

    /** A chave do tipo do primeiro valor do layout na abertura; vazia sem valor. */
    public String currentType() {
        return data.currentType;
    }

    /** Servidor: a Tela da tela; nulo no cliente. */
    public @Nullable TvBlockEntity tv() {
        return tv;
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        return ItemStack.EMPTY;
    }

    @Override
    public boolean stillValid(Player player) {
        if (tv == null) {
            return true;
        }
        return !tv.isRemoved() && tv.getLevel() != null && tv.getLevel().getBlockEntity(tv.getBlockPos()) == tv
                && player instanceof ServerPlayer server && tv.canEdit(server)
                && player.distanceToSqr(tv.getBlockPos().getX() + 0.5, tv.getBlockPos().getY() + 0.5,
                        tv.getBlockPos().getZ() + 0.5) <= REACH_SQ;
    }
}
