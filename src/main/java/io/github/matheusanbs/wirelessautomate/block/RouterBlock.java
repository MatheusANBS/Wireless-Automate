package io.github.matheusanbs.wirelessautomate.block;

import com.mojang.serialization.MapCodec;
import io.github.matheusanbs.wirelessautomate.item.ConfiguratorItem;
import io.github.matheusanbs.wirelessautomate.item.LinkerItem;
import io.github.matheusanbs.wirelessautomate.item.TierCoreItem;
import io.github.matheusanbs.wirelessautomate.menu.RouterMenu;
import io.github.matheusanbs.wirelessautomate.network.NetworkManager;
import io.github.matheusanbs.wirelessautomate.network.NetworkSavedData;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

/**
 * Roteador Wireless. Gruda na face da máquina onde foi colocado: {@link #FACING} é essa face,
 * então a máquina fica em {@code pos.relative(facing.getOpposite())}.
 */
public class RouterBlock extends BaseEntityBlock {
    public static final MapCodec<RouterBlock> CODEC = simpleCodec(RouterBlock::new);
    public static final DirectionProperty FACING = BlockStateProperties.FACING;
    public static final EnumProperty<RouterTier> TIER = EnumProperty.create("tier", RouterTier.class);

    public RouterBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.UP).setValue(TIER, RouterTier.BASIC));
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, TIER);
    }

    @Override
    public @Nullable BlockState getStateForPlacement(BlockPlaceContext context) {
        BlockState state = defaultBlockState().setValue(FACING, context.getClickedFace());
        return state.canSurvive(context.getLevel(), context.getClickedPos()) ? state : null;
    }

    /** Posição da máquina onde o roteador está preso. */
    public static BlockPos attachedPos(BlockState state, BlockPos pos) {
        return pos.relative(state.getValue(FACING).getOpposite());
    }

    @Override
    protected boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
        return !level.getBlockState(attachedPos(state, pos)).isAir();
    }

    @Override
    protected BlockState updateShape(BlockState state, Direction direction, BlockState neighborState,
            LevelAccessor level, BlockPos pos, BlockPos neighborPos) {
        if (direction == state.getValue(FACING).getOpposite() && !state.canSurvive(level, pos)) {
            return Blocks.AIR.defaultBlockState();
        }
        return super.updateShape(state, direction, neighborState, level, pos, neighborPos);
    }

    /**
     * Entra na rede ativa de quem colocou. Se o item trouxe dados do block entity com uma rede,
     * eles já foram aplicados antes desta chamada e a rede que veio é mantida.
     */
    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        if (placer instanceof ServerPlayer player && level.getBlockEntity(pos) instanceof RouterBlockEntity router
                && router.networkId() == null) {
            router.setNetworkId(NetworkSavedData.get(player.server).activeOrCreate(player).id());
        }
    }

    @Override
    protected void neighborChanged(BlockState state, Level level, BlockPos pos, Block neighborBlock,
            BlockPos neighborPos, boolean movedByPiston) {
        super.neighborChanged(state, level, pos, neighborBlock, neighborPos, movedByPiston);
        if (!level.isClientSide && level.getBlockEntity(pos) instanceof RouterBlockEntity router) {
            router.updatePowered();
            if (neighborPos.equals(attachedPos(state, pos))) {
                // Máquina trocada ou removida: só a tela aberta, se houver, refaz o ícone e os slots.
                router.machineChanged();
            }
        }
    }

    /**
     * Itens que agem no roteador pelo próprio {@code useOn} (Vinculador, Configurador e núcleos de
     * tier) pulam a interação do bloco; senão o clique abriria a tela e o item não rodaria. Com
     * qualquer outro item, segue para {@link #useWithoutItem}, que abre a tela.
     */
    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos,
            Player player, InteractionHand hand, BlockHitResult hitResult) {
        Item item = stack.getItem();
        if (item instanceof LinkerItem || item instanceof ConfiguratorItem || item instanceof TierCoreItem) {
            return ItemInteractionResult.SKIP_DEFAULT_BLOCK_INTERACTION;
        }
        return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
    }

    /** Clique direito abre a tela do roteador. */
    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player,
            BlockHitResult hitResult) {
        if (!level.isClientSide && player instanceof ServerPlayer serverPlayer
                && level.getBlockEntity(pos) instanceof RouterBlockEntity router) {
            RouterMenu.open(serverPlayer, router);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    /**
     * O conteúdo da máquina mudou: acorda os destinos do nó. Só chega quando a máquina chama
     * {@code setChanged()} no block entity (o NeoForge propaga para as 6 faces); máquinas que
     * mudam o inventário sem isso ficam com o reserva, as checagens com backoff do gerenciador.
     */
    @Override
    public void onNeighborChange(BlockState state, LevelReader level, BlockPos pos, BlockPos neighbor) {
        if (!level.isClientSide() && neighbor.equals(attachedPos(state, pos))
                && level.getBlockEntity(pos) instanceof RouterBlockEntity router) {
            NetworkManager.get().wake(router);
        }
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return RouterShapes.get(state.getValue(FACING));
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Override
    protected BlockState rotate(BlockState state, Rotation rotation) {
        return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
    }

    @Override
    protected BlockState mirror(BlockState state, Mirror mirror) {
        return state.rotate(mirror.getRotation(state.getValue(FACING)));
    }

    @Override
    public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new RouterBlockEntity(pos, state);
    }

    /**
     * Sobe o roteador em {@code pos} para {@code target}, se for exatamente o tier seguinte.
     * O block entity é mantido, então a configuração não se perde.
     */
    public static boolean tryUpgrade(Level level, BlockPos pos, RouterTier target) {
        BlockState state = level.getBlockState(pos);
        if (!(state.getBlock() instanceof RouterBlock) || state.getValue(TIER).next() != target) {
            return false;
        }
        if (!level.isClientSide) {
            level.setBlockAndUpdate(pos, state.setValue(TIER, target));
        }
        return true;
    }
}
