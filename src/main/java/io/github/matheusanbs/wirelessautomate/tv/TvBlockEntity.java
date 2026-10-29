package io.github.matheusanbs.wirelessautomate.tv;

import com.mojang.serialization.DataResult;
import io.github.matheusanbs.wirelessautomate.Config;
import io.github.matheusanbs.wirelessautomate.WirelessAutomate;
import io.github.matheusanbs.wirelessautomate.registry.ModBlockEntities;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import org.jetbrains.annotations.Nullable;

/**
 * Os dados de uma Tela de Sinal: dono, lista de acesso, área e layout. Não faz tick. O update tag sai só quando a área
 * ou o layout mudam e não leva o dono, a lista de acesso nem os pixels das artes: no lugar delas vão as referências
 * ({@link TvArtRef}), e os pixels seguem à parte pelo {@link TvArtSender} só a quem está perto. O NBT do disco guarda
 * o layout inteiro e a lista de acesso (a chave {@code access}, opcional).
 */
public class TvBlockEntity extends BlockEntity {
    private @Nullable UUID owner;
    /** Quem edita além do dono; só no servidor (vai ao disco, não ao update tag). */
    private TvAccess access = TvAccess.EMPTY;
    private TvArea area = TvArea.SINGLE;
    private TvLayout layout = TvLayout.EMPTY;
    private int revision;
    /**
     * As referências das artes do layout: no servidor, calculadas na primeira vez que alguém as pede depois de o
     * layout mudar ({@code null} até lá); no cliente, as que o update tag trouxe.
     */
    private @Nullable List<TvArtRef> artRefs;
    private long artKey;
    /** Só no cliente: sobe a cada vez que as artes chegam e entram no layout ({@link #resolveArts}). */
    private int artsVersion;
    /** Só no servidor, criado quando a Tela tem arte e alguém perto: o que cada jogador já recebeu. */
    private TvArtSender.@Nullable Feed artFeed;
    /** Os armazenamentos do mod ligados pelo Vinculador (qualquer dimensão), na ordem em que foram ligados. */
    private static final int HARD_LINKS = 256;
    private final List<GlobalPos> links = new ArrayList<>();
    private final List<GlobalPos> linksView = Collections.unmodifiableList(links);
    /** Só no servidor, criado quando alguém perto precisa dos números; não vai ao NBT. */
    private @Nullable TvFeed feed;
    /** A caixa do {@link #renderBox()} e a área e o facing com que foi feita; o renderer a pede a cada quadro. */
    private @Nullable AABB renderBox;
    private @Nullable TvArea renderBoxArea;
    private @Nullable Direction renderBoxFacing;

    public TvBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.SIGNAL_SCREEN.get(), pos, state);
    }

    public @Nullable UUID owner() {
        return owner;
    }

    public void setOwner(UUID owner) {
        this.owner = owner;
        setChanged();
    }

    public TvAccess access() {
        return access;
    }

    /** Troca a lista de acesso: só marca o chunk, porque nada do que a Tela desenha muda. */
    public void setAccess(TvAccess access) {
        if (access.equals(this.access)) {
            return;
        }
        this.access = access;
        if (level != null) {
            level.blockEntityChanged(worldPosition);
        }
    }

    public TvArea area() {
        return area;
    }

    public TvLayout layout() {
        return layout;
    }

    /** Sobe a cada mudança de área ou de layout; o cliente a usa para saber se a imagem está velha. */
    public int revision() {
        return revision;
    }

    /**
     * As referências das artes do layout (vazia sem artes): no servidor, calculadas do layout na primeira vez depois
     * de ele mudar (o resumo de cada arte); no cliente, as do update tag, que continuam valendo enquanto as artes
     * não chegam.
     */
    public List<TvArtRef> artRefs() {
        if (artRefs == null) {
            setArtRefs(TvArtRef.of(layout));
        }
        return artRefs;
    }

    /** A chave do conteúdo das artes ({@link TvArtRef#key}): 0 sem artes. */
    public long artKey() {
        artRefs();
        return artKey;
    }

    private void setArtRefs(List<TvArtRef> refs) {
        artRefs = refs;
        artKey = TvArtRef.key(refs);
    }

    /**
     * Só no cliente: as artes das referências que já chegaram entram no layout (as que faltam, a Imagem e o mosaico
     * não desenham até chegarem). Não muda a revisão: quem desenha marca a Tela suja.
     */
    public void resolveArts(List<TvPixelArt> arts) {
        layout = layout.withImages(arts);
        artsVersion++;
    }

    /** Só no cliente: quantas vezes as artes entraram no layout; o editor relê a Tela quando muda. */
    public int artsVersion() {
        return artsVersion;
    }

    TvArtSender.@Nullable Feed artFeed() {
        return artFeed;
    }

    void setArtFeed(TvArtSender.@Nullable Feed feed) {
        artFeed = feed;
    }

    /** Os armazenamentos ligados; só leitura. */
    public List<GlobalPos> links() {
        return linksView;
    }

    public boolean hasLink(GlobalPos pos) {
        return links.contains(pos);
    }

    /** Liga o armazenamento; falso se já está ligado ou se passou de {@code tv.maxLinks}. */
    public boolean link(GlobalPos pos) {
        if (links.contains(pos) || links.size() >= Config.tvMaxLinks()) {
            return false;
        }
        links.add(pos);
        linksChanged();
        return true;
    }

    /** Desliga o armazenamento; falso se não estava ligado. */
    public boolean unlink(GlobalPos pos) {
        if (!links.remove(pos)) {
            return false;
        }
        linksChanged();
        return true;
    }

    /** As ligações não mudam o que a Tela desenha: avisa o chunk e os clientes, sem subir a revisão do layout. */
    private void linksChanged() {
        setChanged();
        if (level != null) {
            BlockState state = getBlockState();
            level.sendBlockUpdated(worldPosition, state, state, Block.UPDATE_CLIENTS);
        }
    }

    /** O canal de dados desta Tela no servidor, ou {@code null} se nunca houve quem lesse. */
    public @Nullable TvFeed feed() {
        return feed;
    }

    TvFeed feedOrCreate() {
        if (feed == null) {
            feed = new TvFeed();
        }
        return feed;
    }

    /**
     * Edita a Tela (tamanho, layout e ligações): quem gerencia, um convidado ou um membro de um time liberado.
     * Sem dono, só o operador e a lista.
     */
    public boolean canEdit(ServerPlayer player) {
        return canManage(player) || access.includes(player.getUUID());
    }

    /** Mexe na lista de acesso: o dono ou um operador; sem dono, só o operador. */
    public boolean canManage(ServerPlayer player) {
        return player.hasPermissions(2) || (owner != null && owner.equals(player.getUUID()));
    }

    /** Muda a área, traz as peças para dentro dela e liga ou desliga o {@code EXPANDED} do bloco. */
    public void setArea(TvArea area) {
        TvLayout clamped = layout.clampedTo(area);
        if (area.equals(this.area) && clamped.equals(layout)) {
            return;
        }
        this.area = area;
        this.layout = clamped;
        if (level != null) {
            BlockState state = getBlockState();
            boolean expanded = area.cells() > 1;
            if (state.hasProperty(TvBlock.EXPANDED) && state.getValue(TvBlock.EXPANDED) != expanded) {
                level.setBlock(worldPosition, state.setValue(TvBlock.EXPANDED, expanded),
                        Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
            }
        }
        changed();
    }

    public void setLayout(TvLayout layout) {
        TvLayout clamped = layout.clampedTo(area);
        if (clamped.equals(this.layout)) {
            return;
        }
        this.layout = clamped;
        changed();
    }

    private void changed() {
        revision++;
        artRefs = null;
        setChanged();
        if (level != null) {
            BlockState state = getBlockState();
            level.sendBlockUpdated(worldPosition, state, state, Block.UPDATE_CLIENTS);
        }
    }

    /** A posição no mundo da célula {@code cell} da área (colunas para a direita de quem olha, linhas para cima). */
    public BlockPos cellPos(int cell) {
        Direction facing = getBlockState().getValue(TvBlock.FACING);
        return worldPosition.relative(TvBlock.rightOf(facing), area.rightOf(cell)).above(area.upOf(cell));
    }

    /** A caixa de todas as células da área, para o culling do renderer; guardada até a área ou o facing mudarem. */
    public AABB renderBox() {
        Direction facing = getBlockState().getValue(TvBlock.FACING);
        if (renderBox == null || renderBoxArea != area || renderBoxFacing != facing) {
            renderBox = box(worldPosition, facing, area);
            renderBoxArea = area;
            renderBoxFacing = facing;
        }
        return renderBox;
    }

    /** A caixa das células de uma área com o bloco colocado em {@code pos}, sem precisar de um block entity. */
    public static AABB box(BlockPos pos, Direction facing, TvArea area) {
        Direction right = TvBlock.rightOf(facing);
        int last = area.cells() - 1;
        BlockPos first = pos.relative(right, area.rightOf(0)).above(area.upOf(0));
        BlockPos end = pos.relative(right, area.rightOf(last)).above(area.upOf(last));
        return new AABB(first).minmax(new AABB(end));
    }

    @Override
    public void onLoad() {
        super.onLoad();
        TvLoaded.add(this);
    }

    @Override
    public void setRemoved() {
        TvLoaded.remove(this);
        super.setRemoved();
    }

    @Override
    public void onChunkUnloaded() {
        TvLoaded.remove(this);
        super.onChunkUnloaded();
    }

    /** A parte que vai ao disco e ao cliente; ao cliente ({@code client}), o layout sem os pixels das artes. */
    private void writeShared(CompoundTag tag, boolean client) {
        tag.putInt("width", area.width());
        tag.putInt("height", area.height());
        tag.putInt("anchor_col", area.anchorCol());
        tag.putInt("anchor_row", area.anchorRow());
        tag.putInt("revision", revision);
        if (client) {
            tag.put("layout", TvArtRef.clientTag(layout, artRefs()));
        } else {
            TvLayout.CODEC.encodeStart(NbtOps.INSTANCE, layout).result().ifPresent(value -> tag.put("layout", value));
        }
        if (!links.isEmpty()) {
            ListTag list = new ListTag();
            for (GlobalPos link : links) {
                GlobalPos.CODEC.encodeStart(NbtOps.INSTANCE, link).result().ifPresent(list::add);
            }
            tag.put("links", list);
        }
    }

    private void readShared(CompoundTag tag) {
        TvArea read = TvArea.SINGLE;
        int width = tag.getInt("width");
        int height = tag.getInt("height");
        int col = tag.getInt("anchor_col");
        int row = tag.getInt("anchor_row");
        if (width >= 1 && height >= 1 && col >= 0 && col < width && row >= 0 && row < height) {
            read = new TvArea(width, height, col, row);
        }
        area = read;
        revision = tag.getInt("revision");
        TvLayout loaded = TvLayout.EMPTY;
        Tag stored = tag.get("layout");
        if (stored != null) {
            DataResult<TvLayout> parsed = TvLayout.CODEC.parse(NbtOps.INSTANCE, stored);
            if (parsed.result().isPresent()) {
                loaded = parsed.result().get();
            } else {
                WirelessAutomate.LOGGER.warn("Tela de Sinal em {} com layout ilegível; começa vazia", worldPosition);
            }
        }
        layout = loaded;
        List<TvArtRef> refs = TvArtRef.read(stored);
        if (refs != null) {
            setArtRefs(refs);
        } else {
            artRefs = null;
        }
        links.clear();
        if (tag.get("links") instanceof ListTag list) {
            for (int i = 0; i < list.size() && links.size() < HARD_LINKS; i++) {
                GlobalPos.CODEC.parse(NbtOps.INSTANCE, list.get(i)).result()
                        .filter(link -> !links.contains(link)).ifPresent(links::add);
            }
        }
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        writeShared(tag, false);
        if (owner != null) {
            tag.putUUID("owner", owner);
        }
        CompoundTag saved = access.save();
        if (saved != null) {
            tag.put("access", saved);
        }
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        readShared(tag);
        owner = tag.hasUUID("owner") ? tag.getUUID("owner") : null;
        access = TvAccess.load(tag.get("access"));
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = new CompoundTag();
        writeShared(tag, true);
        return tag;
    }

    @Override
    public ClientboundBlockEntityDataPacket getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    public void handleUpdateTag(CompoundTag tag, HolderLookup.Provider registries) {
        readShared(tag);
        TvClientHooks.changed.accept(this);
    }

    /** O pacote leva só a parte compartilhada: não passa pelo {@code loadWithComponents}, que zeraria o dono. */
    @Override
    public void onDataPacket(Connection net, ClientboundBlockEntityDataPacket packet, HolderLookup.Provider registries) {
        handleUpdateTag(packet.getTag(), registries);
    }
}
