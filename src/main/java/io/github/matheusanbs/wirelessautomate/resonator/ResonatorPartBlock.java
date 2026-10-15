package io.github.matheusanbs.wirelessautomate.resonator;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.Locale;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.DirectionProperty;

/** As peças das faces do Ressonador: a Porta de Energia, a Porta de Cristais e o Sintonizador. */
public class ResonatorPartBlock extends Block {
    public enum Kind implements StringRepresentable {
        ENERGY_PORT, CRYSTAL_PORT, TUNER;

        @Override
        public String getSerializedName() {
            return name().toLowerCase(Locale.ROOT);
        }
    }

    public static final MapCodec<ResonatorPartBlock> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            StringRepresentable.fromEnum(Kind::values).fieldOf("kind").forGetter(ResonatorPartBlock::kind),
            propertiesCodec()).apply(instance, ResonatorPartBlock::new));
    public static final DirectionProperty FACING = BlockStateProperties.FACING;
    public static final BooleanProperty FORMED = BooleanProperty.create("formed");

    private final Kind kind;

    public ResonatorPartBlock(Kind kind, Properties properties) {
        super(properties);
        this.kind = kind;
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(FORMED, false));
    }

    public Kind kind() {
        return kind;
    }

    @Override
    protected MapCodec<? extends Block> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, FORMED);
    }

    /** Nasce solta, de frente para a face em que o jogador clicou. */
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(FACING, context.getClickedFace()).setValue(FORMED, false);
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
