package io.github.matheusanbs.wirelessautomate.resonator;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.EnumProperty;

/** A Carcaça de Porcelana: as arestas e os cantos do Ressonador, e o enchimento das faces. */
public class ResonatorCasingBlock extends Block {
    public static final MapCodec<ResonatorCasingBlock> CODEC = simpleCodec(ResonatorCasingBlock::new);
    /** Onde a carcaça está na estrutura montada; solta enquanto nada a montou. */
    public static final EnumProperty<CasingPosition> POSITION = EnumProperty.create("position", CasingPosition.class);

    public ResonatorCasingBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(POSITION, CasingPosition.LOOSE));
    }

    @Override
    protected MapCodec<? extends Block> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(POSITION);
    }

    @Override
    protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean movedByPiston) {
        super.onPlace(state, level, pos, oldState, movedByPiston);
        ResonatorManager.blockChanged(level, pos, oldState, state);
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        ResonatorManager.blockChanged(level, pos, state, newState);
        super.onRemove(state, level, pos, newState, movedByPiston);
    }
}
