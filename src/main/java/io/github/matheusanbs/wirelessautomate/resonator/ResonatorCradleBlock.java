package io.github.matheusanbs.wirelessautomate.resonator;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/** O Berço do Sol: o suporte do cristal no meio da Armilar, em duas casas (embaixo e em cima). */
public class ResonatorCradleBlock extends Block {
    public static final MapCodec<ResonatorCradleBlock> CODEC = simpleCodec(ResonatorCradleBlock::new);
    /** Solta enquanto nada a montou; embaixo ou em cima na estrutura montada. */
    public static final EnumProperty<StackPart> PART = EnumProperty.create("part", StackPart.class);

    private static final VoxelShape LOOSE_SHAPE = Shapes.or(
            Block.box(1, 0, 1, 15, 3, 15),
            Block.box(5, 3, 5, 11, 10, 11),
            Block.box(2, 10, 2, 14, 14, 14));
    private static final VoxelShape LOWER_SHAPE = Shapes.or(
            Block.box(2, 0, 2, 14, 2, 14),
            Block.box(5, 2, 5, 11, 16, 11));
    private static final VoxelShape UPPER_SHAPE = Shapes.or(
            Block.box(5, 0, 5, 11, 2, 11),
            Block.box(3, 2, 3, 13, 6, 13));

    public ResonatorCradleBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(PART, StackPart.LOOSE));
    }

    @Override
    protected MapCodec<? extends Block> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(PART);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return switch (state.getValue(PART)) {
            case LOOSE -> LOOSE_SHAPE;
            case LOWER -> LOWER_SHAPE;
            case UPPER -> UPPER_SHAPE;
        };
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
