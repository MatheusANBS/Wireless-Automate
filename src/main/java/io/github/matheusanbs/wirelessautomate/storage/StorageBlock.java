package io.github.matheusanbs.wirelessautomate.storage;

import io.github.matheusanbs.wirelessautomate.block.RouterBlock;
import io.github.matheusanbs.wirelessautomate.block.RouterTier;
import io.github.matheusanbs.wirelessautomate.item.TierCoreItem;
import io.github.matheusanbs.wirelessautomate.network.Sources;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraftforge.fluids.FluidUtil;
import org.jetbrains.annotations.Nullable;

/**
 * Bloco de armazenamento do mod: Baú, Tanque, Bateria, Tanque Químico ou Tanque de Source ({@link StorageKind}), com
 * a capacidade do tier ({@link RouterBlock#TIER}, a mesma propriedade do roteador). Sobe de tier
 * com os mesmos Cartões de Upgrade, sem perder o conteúdo. Clique direito abre a tela; no Tanque, um
 * balde (ou outro recipiente de fluido) na mão enche ou esvazia direto.
 *
 * <p>Com conteúdo, o bloco nunca some sem virar item, como a caixa de shulker (ver
 * {@link #playerWillDestroy}).
 */
public class StorageBlock extends BaseEntityBlock {
    private final StorageKind kind;
    /** A forma do tipo ({@link StorageShapes}), a mesma em todos os tiers (e níveis). */
    private final VoxelShape shape;

    public StorageBlock(StorageKind kind, Properties properties) {
        super(properties);
        this.kind = kind;
        this.shape = shape(StorageShapes.boxes(kind.id));
        registerDefaultState(stateDefinition.any().setValue(RouterBlock.TIER, RouterTier.BASIC));
    }

    public StorageKind kind() {
        return kind;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(RouterBlock.TIER);
    }

    @Override
    @SuppressWarnings("deprecation")
    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    /** Colisão e seleção abraçando o modelo: a união das caixas do tipo em {@link StorageShapes}. */
    @Override
    @SuppressWarnings("deprecation")
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return shape;
    }

    /** A união das caixas em pixels. */
    static VoxelShape shape(double[][] boxes) {
        VoxelShape shape = Shapes.empty();
        for (double[] b : boxes) {
            shape = Shapes.or(shape, Block.box(b[0], b[1], b[2], b[3], b[4], b[5]));
        }
        return shape;
    }

    @Override
    public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return switch (kind) {
            case CHEST -> new StorageChestBlockEntity(pos, state);
            case TANK -> new StorageTankBlockEntity(pos, state);
            case BATTERY -> new StorageBatteryBlockEntity(pos, state);
            case CHEMICAL_TANK -> new StorageChemicalTankBlockEntity(pos, state);
            case SOURCE_TANK -> Sources.newSourceTank(pos, state);
        };
    }

    /**
     * O Cartão de Upgrade age pelo próprio {@code useOn}. No Tanque, um recipiente de fluido enche ou
     * esvazia direto (o mesmo caminho dos tanques comuns). O resto abre a tela (a lista com busca, ou a
     * da Bateria). Porte 1.20.1: o {@code use} junta o {@code useItemOn} e o {@code useWithoutItem} do
     * 1.21; como lá, só a mão principal abre a tela.
     */
    @Override
    @SuppressWarnings("deprecation")
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand,
            BlockHitResult hitResult) {
        ItemStack stack = player.getItemInHand(hand);
        if (stack.getItem() instanceof TierCoreItem) {
            return InteractionResult.PASS;
        }
        if (kind == StorageKind.TANK && FluidUtil.interactWithFluidHandler(player, hand, level, pos, hitResult.getDirection())) {
            return InteractionResult.sidedSuccess(level.isClientSide);
        }
        if (hand != InteractionHand.MAIN_HAND) {
            return InteractionResult.PASS;
        }
        if (!level.isClientSide && player instanceof ServerPlayer serverPlayer
                && level.getBlockEntity(pos) instanceof StorageBlockEntity storage) {
            storage.open(serverPlayer);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    /** Quantidade com a unidade do tipo: "32.768 itens", "256.000 mB", "1.000.000 FE". */
    public static Component amount(StorageKind kind, long value) {
        return Component.translatable("gui.wirelessautomate.unit." + kind.unit, TierCoreItem.grouped(value));
    }

    /** Capacidade com a unidade, ou "sem limite". */
    public static Component capacity(StorageKind kind, long capacity) {
        return capacity <= 0 ? Component.translatable("block.wirelessautomate.storage.unlimited") : amount(kind, capacity);
    }

    /** "12.600.000 itens em 3 tipos · capacidade 16.777.216 itens" (na Bateria, sem os tipos). */
    public static Component summary(StorageKind kind, long total, int types, long capacity) {
        return kind.hasTypes()
                ? Component.translatable("block.wirelessautomate.storage.summary", amount(kind, total),
                        TierCoreItem.grouped(types), capacity(kind, capacity))
                : Component.translatable("block.wirelessautomate.storage.summary.energy", amount(kind, total),
                        capacity(kind, capacity));
    }

    @Override
    @SuppressWarnings("deprecation")
    public boolean hasAnalogOutputSignal(BlockState state) {
        return true;
    }

    @Override
    @SuppressWarnings("deprecation")
    public int getAnalogOutputSignal(BlockState state, Level level, BlockPos pos) {
        return level.getBlockEntity(pos) instanceof StorageBlockEntity storage ? storage.signal() : 0;
    }

    /**
     * Com conteúdo, o bloco nunca some sem virar item, como a caixa de shulker: se a quebra não vai
     * dropar nada (criativo, ou sem a ferramenta certa), o próprio bloco solta o item com o conteúdo.
     * Com a picareta, no sobrevivência, quem dropa é a loot table, e aqui não se faz nada.
     */
    @Override
    public void playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
        if (!level.isClientSide && level.getBlockEntity(pos) instanceof StorageBlockEntity storage
                && !storage.isEmptyContents() && !lootWillDrop(player, state, level, pos)) {
            ItemStack stack = StorageBlockItem.withTier(asItem(), state.getValue(RouterBlock.TIER));
            storage.writeToItem(stack);
            ItemEntity drop = new ItemEntity(level, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, stack);
            drop.setDefaultPickUpDelay();
            level.addFreshEntity(drop);
        }
        super.playerWillDestroy(level, pos, state, player);
    }

    /**
     * A quebra vai rodar a loot table? A mesma pergunta do {@code ServerPlayerGameMode.destroyBlock}:
     * fora do criativo (pelo modo de jogo do servidor) e com a ferramenta certa.
     */
    private static boolean lootWillDrop(Player player, BlockState state, Level level, BlockPos pos) {
        boolean creative = player instanceof ServerPlayer serverPlayer ? serverPlayer.gameMode.isCreative() : player.isCreative();
        return !creative && state.canHarvestBlock(level, pos, player);
    }

    /**
     * Bloco saindo do mundo: o conteúdo vai para o {@link StorageSavedData} com o id que o drop já
     * leva (a loot table roda antes desta chamada). O upgrade de tier só troca o estado e não passa
     * por aqui.
     */
    @Override
    @SuppressWarnings("deprecation")
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        if (!state.is(newState.getBlock()) && level.getBlockEntity(pos) instanceof StorageBlockEntity storage) {
            storage.stash();
        }
        super.onRemove(state, level, pos, newState, movedByPiston);
    }

    /** Clique do meio (criativo): o bloco no tier dele, vazio. */
    @Override
    public ItemStack getCloneItemStack(BlockGetter level, BlockPos pos, BlockState state) {
        return StorageBlockItem.withTier(asItem(), state.getValue(RouterBlock.TIER));
    }

    /** Sobe o armazenamento em {@code pos} para {@code target}, se {@code target} estiver acima e carregado. */
    public static boolean tryUpgrade(Level level, BlockPos pos, RouterTier target) {
        BlockState state = level.getBlockState(pos);
        if (!(state.getBlock() instanceof StorageBlock) || !state.getValue(RouterBlock.TIER).canUpgradeTo(target)) {
            return false;
        }
        if (!level.isClientSide) {
            level.setBlockAndUpdate(pos, state.setValue(RouterBlock.TIER, target));
            if (level.getBlockEntity(pos) instanceof StorageSourceTankBlockEntity tank) {
                tank.refreshFill();
            }
        }
        return true;
    }
}
