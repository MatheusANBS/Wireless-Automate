package io.github.matheusanbs.wirelessautomate.resonator;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.github.matheusanbs.wirelessautomate.resonator.ResonatorLayout.Face;
import java.util.Locale;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

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
    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;
    public static final BooleanProperty FORMED = BooleanProperty.create("formed");

    /** Até onde o Núcleo de uma caixa que contém a posição pode estar: a casa mais longe fica a 4 blocos dele. */
    private static final int CORE_REACH = ResonatorLayout.SIZE - 1;

    private final Kind kind;
    /** A forma de cada estado, montada uma vez no construtor. */
    private final Map<BlockState, VoxelShape> shapes;

    public ResonatorPartBlock(Kind kind, Properties properties) {
        super(properties);
        this.kind = kind;
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(FORMED, false));
        shapes = ArmilarShapes.tabela(this, switch (kind) {
            case ENERGY_PORT -> ArmilarShapes.ENERGY_PORT;
            case CRYSTAL_PORT -> ArmilarShapes.CRYSTAL_PORT;
            case TUNER -> ArmilarShapes.TUNER;
        });
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

    /** A forma do desenho (colisão e seleção): tirada do modelo pelo gerador, ver {@link ArmilarShapes}. */
    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return shapes.get(state);
    }

    /**
     * Nasce solta. Na casa de uma Porta ou do Sintonizador da Armilar de um Núcleo a até 4 blocos, de frente para
     * fora dessa casa; nos outros casos, de frente para o jogador.
     */
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        Direction wall = wallFacing(context);
        return defaultBlockState().setValue(FACING, wall != null ? wall : towardPlayer(context)).setValue(FORMED, false);
    }

    /** A frente horizontal virada para o jogador, como a do forno. Sem jogador, a do bloco que o põe. */
    static Direction towardPlayer(BlockPlaceContext context) {
        return context.getHorizontalDirection().getOpposite();
    }

    /**
     * Para fora da casa da Armilar do Núcleo mais próximo que tem uma peça de frente ali, ou {@code null} se nenhum.
     * Só ao colocar: varre o cubo de raio 4, sem ler chunk descarregado. Mundo igual dá a mesma resposta no
     * cliente e no servidor.
     */
    private static Direction wallFacing(BlockPlaceContext context) {
        Level level = context.getLevel();
        BlockPos pos = context.getClickedPos();
        Face best = null;
        int bestDistance = Integer.MAX_VALUE;
        BlockPos.MutableBlockPos probe = new BlockPos.MutableBlockPos();
        for (int dx = -CORE_REACH; dx <= CORE_REACH; dx++) {
            for (int dy = -CORE_REACH; dy <= CORE_REACH; dy++) {
                for (int dz = -CORE_REACH; dz <= CORE_REACH; dz++) {
                    int distance = dx * dx + dy * dy + dz * dz;
                    if (distance >= bestDistance) {
                        continue;
                    }
                    probe.set(pos.getX() + dx, pos.getY() + dy, pos.getZ() + dz);
                    if (!level.isLoaded(probe)) {
                        continue;
                    }
                    BlockState state = level.getBlockState(probe);
                    if (!(state.getBlock() instanceof ResonanceCoreBlock)) {
                        continue;
                    }
                    Face front = Face.values()[state.getValue(FACING).get3DDataValue()];
                    int[] origin = ResonatorBlueprint.origin(probe.getX(), probe.getY(), probe.getZ(), front);
                    Face wall = PlacementFacing.wallFacing(origin, pos.getX(), pos.getY(), pos.getZ());
                    if (wall != null) {
                        best = wall;
                        bestDistance = distance;
                    }
                }
            }
        }
        return best == null ? null : Direction.from3DDataValue(best.ordinal());
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
