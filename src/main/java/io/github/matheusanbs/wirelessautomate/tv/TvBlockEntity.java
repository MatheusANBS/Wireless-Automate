package io.github.matheusanbs.wirelessautomate.tv;

import com.mojang.serialization.DataResult;
import io.github.matheusanbs.wirelessautomate.WirelessAutomate;
import io.github.matheusanbs.wirelessautomate.registry.ModBlockEntities;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
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
 * Os dados de uma Tela de Sinal: dono, área e layout. Não faz tick. O update tag sai só quando a área ou o layout
 * mudam e não leva o dono.
 */
public class TvBlockEntity extends BlockEntity {
    private @Nullable UUID owner;
    private TvArea area = TvArea.SINGLE;
    private TvLayout layout = TvLayout.EMPTY;
    private int revision;

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

    /** O dono ou um operador; sem dono, só o operador. */
    public boolean canEdit(ServerPlayer player) {
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

    /** A caixa de todas as células da área, para o culling do renderer. */
    public AABB renderBox() {
        AABB first = new AABB(cellPos(0));
        return first.minmax(new AABB(cellPos(area.cells() - 1)));
    }

    private void writeShared(CompoundTag tag) {
        tag.putInt("width", area.width());
        tag.putInt("height", area.height());
        tag.putInt("anchor_col", area.anchorCol());
        tag.putInt("anchor_row", area.anchorRow());
        tag.putInt("revision", revision);
        TvLayout.CODEC.encodeStart(NbtOps.INSTANCE, layout).result().ifPresent(value -> tag.put("layout", value));
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
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        writeShared(tag);
        if (owner != null) {
            tag.putUUID("owner", owner);
        }
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        readShared(tag);
        owner = tag.hasUUID("owner") ? tag.getUUID("owner") : null;
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = new CompoundTag();
        writeShared(tag);
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
