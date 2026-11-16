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

/** A Coluna do Ressonador: a perna de uma casa da Armilar; solta, é um pilar pequeno sobre uma base. */
public class ResonatorColumnBlock extends Block {
    public static final MapCodec<ResonatorColumnBlock> CODEC = simpleCodec(ResonatorColumnBlock::new);
    /** Solta enquanto nada a montou; embaixo ou em cima na estrutura montada. */
    public static final EnumProperty<StackPart> PART = EnumProperty.create("part", StackPart.class);
    /** O canto da casa em que a perna fica (só vale montada). */
    public static final EnumProperty<Corner> CORNER = EnumProperty.create("corner", Corner.class);

    private static final VoxelShape LOOSE_SHAPE = Shapes.or(
            Block.box(1, 0, 1, 15, 3, 15),
            Block.box(4, 3, 4, 12, 13, 12),
            Block.box(2, 13, 2, 14, 16, 14));
    /** A perna montada, uma forma por canto, na ordem do {@link Corner}. */
    private static final VoxelShape[] LEG_SHAPES = {
            Block.box(12, 0, 12, 16, 16, 16),
            Block.box(0, 0, 12, 4, 16, 16),
            Block.box(0, 0, 0, 4, 16, 4),
            Block.box(12, 0, 0, 16, 16, 4)};

    public ResonatorColumnBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(PART, StackPart.LOOSE).setValue(CORNER, Corner.SE));
    }

    @Override
    protected MapCodec<? extends Block> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(PART, CORNER);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return state.getValue(PART) == StackPart.LOOSE ? LOOSE_SHAPE : LEG_SHAPES[state.getValue(CORNER).ordinal()];
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
