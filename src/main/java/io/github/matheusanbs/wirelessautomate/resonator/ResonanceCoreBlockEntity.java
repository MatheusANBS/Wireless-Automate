package io.github.matheusanbs.wirelessautomate.resonator;

import io.github.matheusanbs.wirelessautomate.block.RouterBlock;
import io.github.matheusanbs.wirelessautomate.block.RouterTier;
import io.github.matheusanbs.wirelessautomate.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/** O estado do Núcleo de Ressonância (sem ticker: o gerenciador trabalha em lote). */
public class ResonanceCoreBlockEntity extends BlockEntity {
    public ResonanceCoreBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.RESONANCE_CORE.get(), pos, state);
    }

    public RouterTier tier() {
        return getBlockState().getValue(RouterBlock.TIER);
    }
}
