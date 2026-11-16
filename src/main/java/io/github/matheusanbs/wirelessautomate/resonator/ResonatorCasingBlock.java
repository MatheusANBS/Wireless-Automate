package io.github.matheusanbs.wirelessautomate.resonator;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/** A Laje do Ressonador: as 21 ou 22 casas do chão da Armilar em volta do Núcleo, das Portas e do Sintonizador. */
public class ResonatorCasingBlock extends Block {
    public static final MapCodec<ResonatorCasingBlock> CODEC = simpleCodec(ResonatorCasingBlock::new);
    /** A casa da Laje no chão da Armilar (referencial local); solta enquanto nada a montou. */
    public static final EnumProperty<BasePosition> POSITION = EnumProperty.create("position", BasePosition.class);
    /** A frente da estrutura montada; só vale montada. */
    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;

    public ResonatorCasingBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(POSITION, BasePosition.LOOSE).setValue(FACING, Direction.NORTH));
    }

    @Override
    protected MapCodec<? extends Block> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(POSITION, FACING);
    }

    /** Montada, a Laje não é cubo inteiro (o modelo é um pedaço da escultura): não oculta as faces vizinhas. */
    @Override
    protected VoxelShape getOcclusionShape(BlockState state, BlockGetter level, BlockPos pos) {
        return state.getValue(POSITION) == BasePosition.LOOSE ? super.getOcclusionShape(state, level, pos) : Shapes.empty();
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
