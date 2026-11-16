package io.github.matheusanbs.wirelessautomate.resonator;

import com.mojang.serialization.MapCodec;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/** O Berço do Sol: o suporte do cristal no meio da Armilar, em duas casas (embaixo e em cima). */
public class ResonatorCradleBlock extends Block {
    public static final MapCodec<ResonatorCradleBlock> CODEC = simpleCodec(ResonatorCradleBlock::new);
    /** Solta enquanto nada a montou; embaixo ou em cima na estrutura montada. */
    public static final EnumProperty<StackPart> PART = EnumProperty.create("part", StackPart.class);

    /** A forma de cada estado, montada uma vez no construtor. */
    private final Map<BlockState, VoxelShape> shapes;

    public ResonatorCradleBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(PART, StackPart.LOOSE));
        shapes = ArmilarShapes.tabela(this, ArmilarShapes.CRADLE);
    }

    @Override
    protected MapCodec<? extends Block> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(PART);
    }

    /** A forma do desenho (colisão e seleção): tirada do modelo pelo gerador, ver {@link ArmilarShapes}. */
    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return shapes.get(state);
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
