package io.github.matheusanbs.wirelessautomate.resonator;

import com.mojang.serialization.MapCodec;
import io.github.matheusanbs.wirelessautomate.block.RouterBlock;
import io.github.matheusanbs.wirelessautomate.block.RouterTier;
import java.util.function.Consumer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.phys.BlockHitResult;
import org.jetbrains.annotations.Nullable;

/** O Núcleo de Ressonância: o tier (o mesmo do roteador) dá as câmaras de cristal; sem ticker, o lote é do gerenciador. */
public class ResonanceCoreBlock extends BaseEntityBlock {
    public static final MapCodec<ResonanceCoreBlock> CODEC = simpleCodec(ResonanceCoreBlock::new);
    /**
     * Liga e desliga a projeção da estrutura em volta do Núcleo clicado. Preenchido pelo cliente na carga; nulo no
     * servidor dedicado, e o código comum não conhece a classe que o preenche.
     */
    public static volatile Consumer<BlockPos> projectionHook;

    public ResonanceCoreBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(RouterBlock.TIER, RouterTier.BASIC)
                .setValue(ResonatorPartBlock.FACING, Direction.NORTH).setValue(ResonatorPartBlock.FORMED, false));
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(RouterBlock.TIER, ResonatorPartBlock.FACING, ResonatorPartBlock.FORMED);
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    /** Nasce solto, de frente para o jogador; o tier vem do item pelo componente do estado. */
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(ResonatorPartBlock.FACING, ResonatorPartBlock.towardPlayer(context));
    }

    @Override
    public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new ResonanceCoreBlockEntity(pos, state);
    }

    /** Clique do meio (criativo): o Núcleo no tier dele. */
    @Override
    public ItemStack getCloneItemStack(LevelReader level, BlockPos pos, BlockState state) {
        return ResonanceCoreItem.withTier(asItem(), state.getValue(RouterBlock.TIER));
    }

    /** Mão vazia, sem Shift, num Núcleo solto: liga ou desliga a projeção (só no cliente; o servidor só confirma o clique). */
    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player,
                                               BlockHitResult hit) {
        if (player.isShiftKeyDown() || state.getValue(ResonatorPartBlock.FORMED) || !player.getMainHandItem().isEmpty()) {
            return InteractionResult.PASS;
        }
        if (level.isClientSide) {
            Consumer<BlockPos> hook = projectionHook;
            if (hook != null) {
                hook.accept(pos.immutable());
            }
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    /** Revalida a estrutura: agendado pelo {@code onLoad} do Núcleo e pelas mudanças nos blocos do Ressonador. */
    @Override
    protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        if (level.getBlockEntity(pos) instanceof ResonanceCoreBlockEntity core) {
            ResonatorFormation.revalidate(level, core);
        }
    }

    @Override
    protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean movedByPiston) {
        super.onPlace(state, level, pos, oldState, movedByPiston);
        ResonatorManager.blockChanged(level, pos, oldState, state);
    }

    /** Quebrar o Núcleo desmonta na hora: o block entity, que sabe quais são os membros, vai embora junto. */
    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        if (!state.is(newState.getBlock()) && level instanceof ServerLevel server
                && server.getBlockEntity(pos) instanceof ResonanceCoreBlockEntity core) {
            if (core.formed()) {
                ResonatorFormation.disassemble(server, core);
            }
            ResonatorManager.remove(core);
        }
        ResonatorManager.blockChanged(level, pos, state, newState);
        super.onRemove(state, level, pos, newState, movedByPiston);
    }
}
