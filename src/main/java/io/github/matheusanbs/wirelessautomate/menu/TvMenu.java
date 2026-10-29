package io.github.matheusanbs.wirelessautomate.menu;

import io.github.matheusanbs.wirelessautomate.Config;
import io.github.matheusanbs.wirelessautomate.menu.TabletSnapshot.AccessView;
import io.github.matheusanbs.wirelessautomate.network.NetworkSavedData;
import io.github.matheusanbs.wirelessautomate.network.WaNetwork;
import io.github.matheusanbs.wirelessautomate.packet.NetworkSuggestionsPayload;
import io.github.matheusanbs.wirelessautomate.packet.TvAccessPayload;
import io.github.matheusanbs.wirelessautomate.registry.ModMenus;
import io.github.matheusanbs.wirelessautomate.storage.StorageBlockEntity;
import io.github.matheusanbs.wirelessautomate.tv.TvAccessActions;
import io.github.matheusanbs.wirelessautomate.tv.TvArea;
import io.github.matheusanbs.wirelessautomate.tv.TvArtSender;
import io.github.matheusanbs.wirelessautomate.tv.TvBinding;
import io.github.matheusanbs.wirelessautomate.tv.TvBlockEntity;
import io.github.matheusanbs.wirelessautomate.tv.TvImageConfig;
import io.github.matheusanbs.wirelessautomate.tv.TvImageLimits;
import io.github.matheusanbs.wirelessautomate.tv.TvImageStore;
import io.github.matheusanbs.wirelessautomate.tv.TvPictureSender;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

/**
 * Menu da tela da Tela de Sinal: sem slots. Os dados de abertura (posição, área, tetos, a rede e o tipo do primeiro
 * valor do layout, as redes que o jogador pode usar e, só para o dono e os operadores, a lista de acesso) vão no
 * buffer do {@code openMenu}; o resto são os pacotes de tamanho, do layout, da biblioteca e do acesso, validados no
 * servidor.
 */
public class TvMenu extends AbstractContainerMenu implements SuggestionsMenu {
    /** No máximo tantas redes na lista da abertura. */
    public static final int MAX_NETWORKS = 256;
    /** No máximo tantos armazenamentos ligados na lista da abertura. */
    public static final int MAX_LINKS = 64;
    private static final double REACH_SQ = 8.0 * 8.0;

    /** Uma rede que o jogador pode usar, como a lista da tela a mostra. */
    public record NetworkChoice(UUID id, String name, int color) {
        public static final StreamCodec<RegistryFriendlyByteBuf, NetworkChoice> STREAM_CODEC = StreamCodec.composite(
                UUIDUtil.STREAM_CODEC, NetworkChoice::id,
                ByteBufCodecs.stringUtf8(64), NetworkChoice::name,
                ByteBufCodecs.INT, NetworkChoice::color,
                NetworkChoice::new);
    }

    /** Um armazenamento ligado à Tela como a lista da aba Dado o mostra: onde está e o tipo ({@code ""} se não carregado). */
    public record LinkChoice(GlobalPos pos, String kind) {
        public static final StreamCodec<RegistryFriendlyByteBuf, LinkChoice> STREAM_CODEC = StreamCodec.composite(
                GlobalPos.STREAM_CODEC, LinkChoice::pos,
                ByteBufCodecs.stringUtf8(32), LinkChoice::kind,
                LinkChoice::new);
    }

    /**
     * As regras das imagens enviadas para o editor: se estão ligadas, se este jogador pode enviar (ligadas, operador
     * quando só operadores enviam, e fora da lista de banidos) e os tetos que o cliente confere antes de mandar.
     */
    public record ImageRules(boolean enabled, boolean canUpload, int maxFileBytes, int maxSize, int maxFrames,
            int maxPerScreen) {
        static final StreamCodec<RegistryFriendlyByteBuf, ImageRules> STREAM_CODEC = StreamCodec.of(
                (buf, r) -> {
                    buf.writeBoolean(r.enabled).writeBoolean(r.canUpload);
                    buf.writeVarInt(r.maxFileBytes).writeVarInt(r.maxSize).writeVarInt(r.maxFrames)
                            .writeVarInt(r.maxPerScreen);
                },
                buf -> new ImageRules(buf.readBoolean(), buf.readBoolean(), buf.readVarInt(), buf.readVarInt(),
                        buf.readVarInt(), buf.readVarInt()));

        static ImageRules of(ServerPlayer player) {
            TvImageLimits limits = TvImageConfig.limits(player.server);
            boolean canUpload = limits.enabled() && (!limits.opsOnly() || player.hasPermissions(2))
                    && !TvImageStore.get(player.server).banned(player.getUUID());
            return new ImageRules(limits.enabled(), canUpload, limits.maxFileBytes(), limits.maxSize(),
                    limits.maxFrames(), limits.maxPerScreen());
        }
    }

    /** O que o buffer de abertura leva. */
    private record Data(BlockPos pos, TvArea area, int maxWidth, int maxHeight, int maxLayoutBytes, int maxArts,
            int maxImagePixels, ImageRules images, @Nullable UUID currentNetwork,
            String currentType, List<NetworkChoice> networks, List<LinkChoice> links, boolean manage,
            AccessView access) {
        static Data read(RegistryFriendlyByteBuf buf) {
            BlockPos pos = BlockPos.STREAM_CODEC.decode(buf);
            TvArea area = new TvArea(buf.readVarInt(), buf.readVarInt(), buf.readVarInt(), buf.readVarInt());
            int maxWidth = buf.readVarInt();
            int maxHeight = buf.readVarInt();
            int maxLayoutBytes = buf.readVarInt();
            int maxArts = buf.readVarInt();
            int maxImagePixels = buf.readVarInt();
            ImageRules images = ImageRules.STREAM_CODEC.decode(buf);
            UUID network = buf.readBoolean() ? UUIDUtil.STREAM_CODEC.decode(buf) : null;
            String type = buf.readUtf(32);
            int size = Math.min(buf.readVarInt(), MAX_NETWORKS);
            List<NetworkChoice> list = new ArrayList<>(size);
            for (int i = 0; i < size; i++) {
                list.add(NetworkChoice.STREAM_CODEC.decode(buf));
            }
            int linkCount = Math.min(buf.readVarInt(), MAX_LINKS);
            List<LinkChoice> links = new ArrayList<>(linkCount);
            for (int i = 0; i < linkCount; i++) {
                links.add(LinkChoice.STREAM_CODEC.decode(buf));
            }
            boolean manage = buf.readBoolean();
            AccessView access = manage ? AccessView.read(buf) : AccessView.EMPTY;
            return new Data(pos, area, maxWidth, maxHeight, maxLayoutBytes, maxArts, maxImagePixels, images, network,
                    type, list, links, manage, access);
        }

        void write(RegistryFriendlyByteBuf buf) {
            BlockPos.STREAM_CODEC.encode(buf, pos);
            buf.writeVarInt(area.width()).writeVarInt(area.height()).writeVarInt(area.anchorCol())
                    .writeVarInt(area.anchorRow());
            buf.writeVarInt(maxWidth).writeVarInt(maxHeight).writeVarInt(maxLayoutBytes).writeVarInt(maxArts)
                    .writeVarInt(maxImagePixels);
            ImageRules.STREAM_CODEC.encode(buf, images);
            buf.writeBoolean(currentNetwork != null);
            if (currentNetwork != null) {
                UUIDUtil.STREAM_CODEC.encode(buf, currentNetwork);
            }
            buf.writeUtf(currentType, 32);
            buf.writeVarInt(networks.size());
            for (NetworkChoice choice : networks) {
                NetworkChoice.STREAM_CODEC.encode(buf, choice);
            }
            buf.writeVarInt(links.size());
            for (LinkChoice link : links) {
                LinkChoice.STREAM_CODEC.encode(buf, link);
            }
            buf.writeBoolean(manage);
            if (manage) {
                access.write(buf);
            }
        }
    }

    private final Data data;
    private final @Nullable TvBlockEntity tv;
    // cliente: a lista de acesso que a aba Acesso mostra, trocada a cada resposta do servidor
    private AccessView access;
    private int accessVersion;
    private String accessNotice = "";
    private String accessArg = "";
    private @Nullable NetworkSuggestionsPayload suggestions;
    private int suggestionsVersion;

    /** Cliente. */
    public TvMenu(int containerId, Inventory inventory, RegistryFriendlyByteBuf buf) {
        this(containerId, Data.read(buf), null);
    }

    private TvMenu(int containerId, Data data, @Nullable TvBlockEntity tv) {
        super(ModMenus.SIGNAL_SCREEN.get(), containerId);
        this.data = data;
        this.tv = tv;
        this.access = data.access;
    }

    /**
     * Servidor: abre a tela da Tela de Sinal; a lista de redes é montada uma vez, na abertura, e as artes da Tela vão
     * antes ({@link TvArtSender#sendOnOpen}) se o jogador ainda não as tem. As imagens enviadas da Tela e da biblioteca
     * do jogador entram na fila da entrega ({@link TvPictureSender#sendOnOpen}).
     */
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
        List<LinkChoice> links = new ArrayList<>();
        for (GlobalPos link : tv.links()) {
            if (links.size() >= MAX_LINKS) {
                break;
            }
            links.add(new LinkChoice(link, linkKind(player.server, link)));
        }
        TvBinding first = tv.layout().bindings().isEmpty() ? null : tv.layout().bindings().get(0);
        boolean manage = tv.canManage(player);
        Data data = new Data(tv.getBlockPos(), tv.area(), Config.tvMaxWidth(), Config.tvMaxHeight(),
                Config.tvMaxLayoutBytes(), Config.tvMaxArts(), Config.tvMaxImagePixels(), ImageRules.of(player),
                first == null ? null : first.network(),
                first == null ? "" : first.type().key(), networks, links, manage,
                manage ? TvAccessActions.view(player.server, tv) : AccessView.EMPTY);
        // As artes vão antes do menu, pela mesma conexão: o editor abre com o layout já inteiro.
        TvArtSender.sendOnOpen(player, tv);
        TvPictureSender.sendOnOpen(player, tv);
        player.openMenu(new SimpleMenuProvider((containerId, inventory, p) -> new TvMenu(containerId, data, tv),
                Component.translatable("container.wirelessautomate.signal_screen")), data::write);
    }

    /** O tipo do armazenamento numa posição ligada, se o chunk está carregado (nunca o carrega); senão vazio. */
    private static String linkKind(MinecraftServer server, GlobalPos link) {
        ServerLevel level = server.getLevel(link.dimension());
        return level != null && level.isLoaded(link.pos())
                && level.getBlockEntity(link.pos()) instanceof StorageBlockEntity storage
                ? storage.kind().getSerializedName() : "";
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

    /** O teto, em bytes, do layout salvo: o editor confere antes de mandar. */
    public int maxLayoutBytes() {
        return data.maxLayoutBytes;
    }

    /** O teto de pixel arts na biblioteca do jogador ({@code tv.maxArts}), para a aba Pixel art mostrar. */
    public int maxArts() {
        return data.maxArts;
    }

    /** O teto de pixels somados nas imagens de uma Tela ({@code tv.maxImagePixels}). */
    public int maxImagePixels() {
        return data.maxImagePixels;
    }

    /** As regras das imagens enviadas na abertura ({@code tv.images}). */
    public ImageRules imageRules() {
        return data.images;
    }

    /** As redes que o jogador pode usar, ordenadas pelo nome. */
    public List<NetworkChoice> networks() {
        return data.networks;
    }

    /** Os armazenamentos ligados à Tela na abertura, com o tipo de cada um. */
    public List<LinkChoice> links() {
        return data.links;
    }

    /** A rede do primeiro valor do layout na abertura, ou {@code null}. */
    public @Nullable UUID currentNetwork() {
        return data.currentNetwork;
    }

    /** A chave do tipo do primeiro valor do layout na abertura; vazia sem valor. */
    public String currentType() {
        return data.currentType;
    }

    /** Quem abriu é o dono ou um operador: a aba Acesso aparece. */
    public boolean manage() {
        return data.manage;
    }

    /** Cliente: a lista de acesso como o servidor a mandou por último (vazia para quem não gerencia). */
    public AccessView access() {
        return access;
    }

    /** Cliente: muda a cada {@link TvAccessPayload} recebido. */
    public int accessVersion() {
        return accessVersion;
    }

    /** Cliente: o fim da chave do último aviso da lista de acesso; vazio sem aviso. */
    public String accessNotice() {
        return accessNotice;
    }

    /** Cliente: o nome que o último aviso mostra. */
    public String accessArg() {
        return accessArg;
    }

    /** Cliente: a lista de acesso nova, depois de uma ação do dono. */
    public void applyAccess(TvAccessPayload payload) {
        access = payload.access();
        accessNotice = payload.notice();
        accessArg = payload.arg();
        accessVersion++;
    }

    @Override
    public void applySuggestions(NetworkSuggestionsPayload payload) {
        suggestions = payload;
        suggestionsVersion++;
    }

    @Override
    public @Nullable NetworkSuggestionsPayload suggestions() {
        return suggestions;
    }

    @Override
    public int suggestionsVersion() {
        return suggestionsVersion;
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
