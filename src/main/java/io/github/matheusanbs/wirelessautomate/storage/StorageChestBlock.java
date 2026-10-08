package io.github.matheusanbs.wirelessautomate.storage;

import com.mojang.serialization.MapCodec;
import io.github.matheusanbs.wirelessautomate.block.RouterBlock;
import io.github.matheusanbs.wirelessautomate.block.RouterTier;
import io.github.matheusanbs.wirelessautomate.item.TierCoreItem;
import io.github.matheusanbs.wirelessautomate.menu.StorageChestMenu;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.item.ItemEntity;
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
 * Baú do mod: guarda itens por tipo e quantidade {@code long}, com a capacidade do tier
 * ({@link RouterBlock#TIER}, a mesma propriedade do roteador). Sobe de tier com os mesmos Cartões
 * de Upgrade, sem perder o conteúdo. O roteador preso nele usa o {@link BulkItems}.
 */
public class StorageChestBlock extends BaseEntityBlock {
    public static final MapCodec<StorageChestBlock> CODEC = simpleCodec(StorageChestBlock::new);

    public StorageChestBlock(Properties properties) {
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
        return new StorageChestBlockEntity(pos, state);
    }

    /** O Cartão de Upgrade age pelo próprio {@code useOn}; o resto segue para o clique sem item. */
    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos,
            Player player, InteractionHand hand, BlockHitResult hitResult) {
        if (stack.getItem() instanceof TierCoreItem) {
            return ItemInteractionResult.SKIP_DEFAULT_BLOCK_INTERACTION;
        }
        return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
    }

    /** Clique direito abre a tela do Baú (a lista com busca). */
    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player,
            BlockHitResult hitResult) {
        if (!level.isClientSide && player instanceof ServerPlayer serverPlayer
                && level.getBlockEntity(pos) instanceof StorageChestBlockEntity chest) {
            StorageChestMenu.open(serverPlayer, chest);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    /** "12.600.000 itens em 3 tipos · capacidade 16.777.216" (ou "sem limite"). */
    public static Component summary(long total, int types, long capacity) {
        Component limit = capacity <= 0
                ? Component.translatable("block.wirelessautomate.storage_chest.unlimited")
                : Component.literal(TierCoreItem.grouped(capacity));
        return Component.translatable("block.wirelessautomate.storage_chest.summary",
                TierCoreItem.grouped(total), TierCoreItem.grouped(types), limit);
    }

    @Override
    protected boolean hasAnalogOutputSignal(BlockState state) {
        return true;
    }

    @Override
    protected int getAnalogOutputSignal(BlockState state, Level level, BlockPos pos) {
        return level.getBlockEntity(pos) instanceof StorageChestBlockEntity chest
                ? StorageMath.signal(chest.storage().total(), chest.capacity())
                : 0;
    }

    /**
     * Um Baú com conteúdo nunca some sem virar item, como a caixa de shulker: se a quebra não vai
     * dropar nada (criativo, ou sem a ferramenta certa), o próprio bloco solta o item com o conteúdo.
     * Com a picareta, no sobrevivência, quem dropa é a loot table, e aqui não se faz nada.
     */
    @Override
    public BlockState playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
        if (!level.isClientSide && level.getBlockEntity(pos) instanceof StorageChestBlockEntity chest
                && !chest.storage().isEmpty()
                && (player.isCreative() || !state.canHarvestBlock(level, pos, player))) {
            ItemStack stack = StorageChestBlockItem.withTier((StorageChestBlockItem) asItem(), state.getValue(RouterBlock.TIER));
            stack.applyComponents(chest.collectComponents());
            ItemEntity drop = new ItemEntity(level, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, stack);
            drop.setDefaultPickUpDelay();
            level.addFreshEntity(drop);
        }
        return super.playerWillDestroy(level, pos, state, player);
    }

    /**
     * Bloco saindo do mundo: o conteúdo vai para o {@link StorageSavedData} com o id que o drop já
     * leva (a loot table roda antes desta chamada). O upgrade de tier só troca o estado e não passa
     * por aqui.
     */
    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        if (!state.is(newState.getBlock()) && level.getBlockEntity(pos) instanceof StorageChestBlockEntity chest) {
            chest.stash();
        }
        super.onRemove(state, level, pos, newState, movedByPiston);
    }

    /** Clique do meio (criativo): o Baú no tier do bloco, vazio. */
    @Override
    public ItemStack getCloneItemStack(LevelReader level, BlockPos pos, BlockState state) {
        return StorageChestBlockItem.withTier((StorageChestBlockItem) asItem(), state.getValue(RouterBlock.TIER));
    }

    /** Sobe o Baú em {@code pos} para {@code target}, se for exatamente o tier seguinte. */
    public static boolean tryUpgrade(Level level, BlockPos pos, RouterTier target) {
        BlockState state = level.getBlockState(pos);
        if (!(state.getBlock() instanceof StorageChestBlock) || state.getValue(RouterBlock.TIER).next() != target) {
            return false;
        }
        if (!level.isClientSide) {
            level.setBlockAndUpdate(pos, state.setValue(RouterBlock.TIER, target));
        }
        return true;
    }
}
