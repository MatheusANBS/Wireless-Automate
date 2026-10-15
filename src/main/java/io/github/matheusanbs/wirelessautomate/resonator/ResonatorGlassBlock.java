package io.github.matheusanbs.wirelessautomate.resonator;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.TransparentBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;

/** O Vidro de Ressonância: vidro do vanilla, sem oclusão, com a face da estrutura e os quatro bits de vizinhos do vidro. */
public class ResonatorGlassBlock extends TransparentBlock {
    public static final MapCodec<ResonatorGlassBlock> CODEC = simpleCodec(ResonatorGlassBlock::new);
    public static final EnumProperty<GlassFace> FACE = EnumProperty.create("face", GlassFace.class);
    /** Os quatro bits de borda aberta da textura conectada (ver {@code ResonatorLayout.Member#glassBits}). */
    public static final IntegerProperty CONNECTIONS = IntegerProperty.create("connections", 0, 15);

    public ResonatorGlassBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACE, GlassFace.LOOSE).setValue(CONNECTIONS, 0));
    }

    @Override
    public MapCodec<? extends TransparentBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACE, CONNECTIONS);
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
