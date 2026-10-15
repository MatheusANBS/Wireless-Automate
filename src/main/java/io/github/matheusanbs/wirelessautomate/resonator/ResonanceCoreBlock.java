package io.github.matheusanbs.wirelessautomate.resonator;

import com.mojang.serialization.MapCodec;
import io.github.matheusanbs.wirelessautomate.block.RouterBlock;
import io.github.matheusanbs.wirelessautomate.block.RouterTier;
import io.github.matheusanbs.wirelessautomate.item.TierCoreItem;
import java.util.function.Consumer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Containers;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
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

    /**
     * Cristal de Sinal ou Lapidado: vai para a primeira câmara vazia. Pó de redstone: entra o que cabe. O Cartão de
     * Upgrade pula o clique do bloco e age pelo {@code useOn} dele, como no roteador.
     */
    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos,
                                              Player player, InteractionHand hand, BlockHitResult hit) {
        if (stack.getItem() instanceof TierCoreItem) {
            return ItemInteractionResult.SKIP_DEFAULT_BLOCK_INTERACTION;
        }
        boolean crystal = stack.getItem() instanceof SignalCrystalItem;
        if (!crystal && !stack.is(Items.REDSTONE)) {
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }
        if (!level.isClientSide && level.getBlockEntity(pos) instanceof ResonanceCoreBlockEntity core) {
            int used = crystal ? stack.getCount() - core.insertCrystal(stack).getCount()
                    : core.addRedstone(stack.getCount());
            if (!player.hasInfiniteMaterials()) {
                stack.shrink(used);
            }
        }
        return ItemInteractionResult.sidedSuccess(level.isClientSide);
    }

    /**
     * Mão vazia. Shift tira o último cristal para a mão. Sem Shift, o servidor manda a linha do status (montado) ou
     * do problema (solto) na action bar; solto, o cliente também liga ou desliga a projeção, que escreve a contagem
     * dela por cima da linha do problema.
     */
    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player,
                                               BlockHitResult hit) {
        if (!player.getMainHandItem().isEmpty()) {
            return InteractionResult.PASS;
        }
        if (level.isClientSide) {
            Consumer<BlockPos> hook = projectionHook;
            if (!player.isShiftKeyDown() && !state.getValue(ResonatorPartBlock.FORMED) && hook != null) {
                hook.accept(pos.immutable());
            }
        } else if (level.getBlockEntity(pos) instanceof ResonanceCoreBlockEntity core) {
            if (player.isShiftKeyDown()) {
                ItemStack taken = core.takeLastCrystal();
                if (!taken.isEmpty()) {
                    player.setItemInHand(InteractionHand.MAIN_HAND, taken);
                }
            } else {
                player.displayClientMessage(ResonatorStatus.line(core), true);
            }
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    /**
     * Sobe o Núcleo em {@code pos} para {@code target} no lugar, se o tier dele aceita o cartão
     * {@code origin → target} ({@link RouterTier#acceptsCard}). Só o {@code TIER} do estado muda: o block entity
     * fica, com cristais, redstone e energia, e a revalidação na hora põe a capacidade e as câmaras novas.
     */
    public static boolean tryUpgrade(Level level, BlockPos pos, RouterTier target, @Nullable RouterTier origin) {
        BlockState state = level.getBlockState(pos);
        if (!(state.getBlock() instanceof ResonanceCoreBlock)
                || !state.getValue(RouterBlock.TIER).acceptsCard(origin, target)) {
            return false;
        }
        if (level instanceof ServerLevel server) {
            server.setBlockAndUpdate(pos, state.setValue(RouterBlock.TIER, target));
            if (server.getBlockEntity(pos) instanceof ResonanceCoreBlockEntity core) {
                ResonatorFormation.revalidate(server, core);
            }
        }
        return true;
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

    /**
     * Quebrar o Núcleo desmonta na hora (o block entity, que sabe quais são os membros, vai embora junto) e derruba
     * os cristais e a redstone; a energia se perde.
     */
    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        if (!state.is(newState.getBlock()) && level instanceof ServerLevel server
                && server.getBlockEntity(pos) instanceof ResonanceCoreBlockEntity core) {
            if (core.formed()) {
                ResonatorFormation.disassemble(server, core);
            }
            ResonatorManager.remove(core);
            Containers.dropContents(server, pos, core.crystals());
            if (core.redstone() > 0) {
                Block.popResource(server, pos, new ItemStack(Items.REDSTONE, core.redstone()));
            }
        }
        ResonatorManager.blockChanged(level, pos, state, newState);
        super.onRemove(state, level, pos, newState, movedByPiston);
    }
}
