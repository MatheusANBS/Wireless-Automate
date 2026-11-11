package io.github.matheusanbs.wirelessautomate.dock;

import com.mojang.serialization.MapCodec;
import io.github.matheusanbs.wirelessautomate.block.RouterBlock;
import io.github.matheusanbs.wirelessautomate.block.RouterTier;
import io.github.matheusanbs.wirelessautomate.item.TierCoreItem;
import io.github.matheusanbs.wirelessautomate.storage.StorageBlockItem;
import io.github.matheusanbs.wirelessautomate.menu.DockMenu;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
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

/**
 * A Doca de Padrões: um bloco cheio, sem frente e sem giro (os seis lados são iguais e nomeados pela direção do
 * mundo), com o tier do roteador ({@link RouterBlock#TIER}), que define o alcance ({@link
 * io.github.matheusanbs.wirelessautomate.Config#dockRadius}). Sobe de tier com o Cartão de Upgrade, sem perder os lados.
 * Clique direito abre a tela ({@link DockMenu}). Quebrada, larga no chão o pendente de itens; o item leva só o tier.
 */
public class PatternDockBlock extends BaseEntityBlock {
    public static final MapCodec<PatternDockBlock> CODEC = simpleCodec(PatternDockBlock::new);

    public PatternDockBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(RouterBlock.TIER, RouterTier.BASIC));
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(RouterBlock.TIER);
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Override
    public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new PatternDockBlockEntity(pos, state);
    }

    /** O Cartão de Upgrade age pelo próprio {@code useOn}. */
    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos,
            Player player, InteractionHand hand, BlockHitResult hitResult) {
        if (stack.getItem() instanceof TierCoreItem) {
            return ItemInteractionResult.SKIP_DEFAULT_BLOCK_INTERACTION;
        }
        return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
    }

    /** Clique direito: a tela dos seis lados. O Vinculador e o Configurador agem antes, pelo {@code onItemUseFirst}. */
    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player,
            BlockHitResult hit) {
        if (!level.isClientSide && player instanceof ServerPlayer serverPlayer
                && level.getBlockEntity(pos) instanceof PatternDockBlockEntity dock) {
            DockMenu.open(serverPlayer, dock);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    /** Colocada por um jogador: o cubo do alcance aparece por 5 s só para ele ({@link DockReachSync}). */
    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity placer,
            ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        if (!level.isClientSide && placer instanceof ServerPlayer player
                && level.getBlockEntity(pos) instanceof PatternDockBlockEntity dock) {
            DockReachSync.send(player, dock, DockReachSync.PLACED_TICKS);
        }
    }

    /** Bloco saindo do mundo (não uma troca de tier, que só muda o estado): o pendente de itens cai no chão. */
    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        if (!state.is(newState.getBlock()) && level.getBlockEntity(pos) instanceof PatternDockBlockEntity dock) {
            dock.dropPending();
        }
        super.onRemove(state, level, pos, newState, movedByPiston);
    }

    /** Clique do meio (criativo): a Doca no tier dela. */
    @Override
    public ItemStack getCloneItemStack(LevelReader level, BlockPos pos, BlockState state) {
        return StorageBlockItem.withTier(asItem(), state.getValue(RouterBlock.TIER));
    }

    /**
     * Sobe a Doca em {@code pos} para {@code target} no lugar (os lados ficam), se o tier dela aceita o cartão
     * {@code origin → target} ({@link RouterTier#acceptsCard}): com origem, só daquele tier; sem origem (cartão
     * antigo), de qualquer tier abaixo. O registro das máquinas no índice é refeito com o alcance novo.
     */
    public static boolean tryUpgrade(Level level, BlockPos pos, RouterTier target, @Nullable RouterTier origin) {
        BlockState state = level.getBlockState(pos);
        if (!(state.getBlock() instanceof PatternDockBlock)
                || !state.getValue(RouterBlock.TIER).acceptsCard(origin, target)) {
            return false;
        }
        if (!level.isClientSide) {
            level.setBlockAndUpdate(pos, state.setValue(RouterBlock.TIER, target));
            if (level.getBlockEntity(pos) instanceof PatternDockBlockEntity dock) {
                dock.reachChanged();
            }
        }
        return true;
    }
}
