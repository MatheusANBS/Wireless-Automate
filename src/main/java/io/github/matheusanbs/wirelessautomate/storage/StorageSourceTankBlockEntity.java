package io.github.matheusanbs.wirelessautomate.storage;

import io.github.matheusanbs.wirelessautomate.menu.StorageScalarMenu;
import io.github.matheusanbs.wirelessautomate.network.Sources;
import io.github.matheusanbs.wirelessautomate.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Tanque de Source: Source em {@code long} ({@link ScalarStore}). Para o roteador, a {@link BulkSource};
 * para o Ars, a {@code ISourceCap} e o provider do {@code SourceManager} ({@code compat/arsnouveau}).
 * O nível da coluna ({@link StorageSourceTankBlock#FILL}) acompanha o conteúdo, e o estado do bloco só
 * muda quando o nível muda.
 */
public class StorageSourceTankBlockEntity extends ScalarStorageBlockEntity {
    public StorageSourceTankBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.SOURCE_TANK.get(), StorageKind.SOURCE_TANK, pos, state);
    }

    /** No servidor, entra no {@code SourceManager} do Ars (com ele): as máquinas dele tiram Source daqui. */
    @Override
    public void onLoad() {
        super.onLoad();
        if (level != null && !level.isClientSide) {
            Sources.registerProvider(this);
        }
    }

    @Override
    protected void contentsChanged() {
        refreshFill();
    }

    /** Põe no bloco o nível do conteúdo atual (no servidor; sem nada se já for esse). */
    public void refreshFill() {
        if (level == null || level.isClientSide) {
            return;
        }
        BlockState state = getBlockState();
        if (!state.hasProperty(StorageSourceTankBlock.FILL)) {
            return;
        }
        int fill = StorageMath.fillLevel(store().stored(), capacity());
        if (state.getValue(StorageSourceTankBlock.FILL) != fill) {
            level.setBlock(worldPosition, state.setValue(StorageSourceTankBlock.FILL, fill), Block.UPDATE_CLIENTS);
        }
    }

    /** Colocado a partir de um item cheio: o conteúdo voltou, então o nível também. */
    @Override
    protected void applyImplicitComponents(DataComponentInput input) {
        super.applyImplicitComponents(input);
        refreshFill();
    }

    @Override
    protected String contentsKey() {
        return "source";
    }

    @Override
    public void open(ServerPlayer player) {
        StorageScalarMenu.open(player, this);
    }
}
