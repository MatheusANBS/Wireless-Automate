package io.github.matheusanbs.wirelessautomate.block;

import com.mojang.serialization.MapCodec;
import io.github.matheusanbs.wirelessautomate.item.ConfiguratorItem;
import io.github.matheusanbs.wirelessautomate.item.LinkerItem;
import io.github.matheusanbs.wirelessautomate.item.RouterBlockItem;
import io.github.matheusanbs.wirelessautomate.item.TierCoreItem;
import io.github.matheusanbs.wirelessautomate.menu.RouterMenu;
import io.github.matheusanbs.wirelessautomate.network.NetworkManager;
import io.github.matheusanbs.wirelessautomate.network.NodeIndex;
import io.github.matheusanbs.wirelessautomate.network.RelativeSide;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Containers;
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
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

/**
 * Roteador Wireless. Gruda na face da máquina onde foi colocado: {@link #FACING} é essa face,
 * então a máquina fica em {@code pos.relative(facing.getOpposite())}. {@link #SPIN} é o giro de 90° em
 * torno do eixo dessa face (Shift + clique com as mãos vazias), só visual: a configuração de cada face
 * absoluta da máquina não muda (ver {@link RelativeSide}).
 */
public class RouterBlock extends BaseEntityBlock {
    public static final MapCodec<RouterBlock> CODEC = simpleCodec(RouterBlock::new);
    public static final DirectionProperty FACING = BlockStateProperties.FACING;
    public static final EnumProperty<RouterTier> TIER = EnumProperty.create("tier", RouterTier.class);
    /** Giro em torno do eixo do {@link #FACING}, 0 a 3; nasce em 0, a posição de antes do giro existir. */
    public static final IntegerProperty SPIN = IntegerProperty.create("spin", 0, RelativeSide.SPINS - 1);

    public RouterBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.UP).setValue(SPIN, 0)
                .setValue(TIER, RouterTier.BASIC));
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, SPIN, TIER);
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
     * O roteador nasce sem rede em todas as abas: o jogador configura o primeiro (tela do roteador ou
     * Vinculador) e replica com o Configurador. Se o item trouxe dados do block entity com alguma
     * rede, eles já foram aplicados antes desta chamada e as redes que vieram são mantidas.
     */
    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        if (placer instanceof ServerPlayer player && level.getBlockEntity(pos) instanceof RouterBlockEntity router) {
            NodeIndex.placedBy(router, player.getUUID());
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
                // Só o estado mudou (fornalha acesa)? O roteador confere o bloco e não faz nada.
                router.machineNeighborChanged();
            }
        }
    }

    /**
     * Itens que agem no roteador pelo próprio {@code useOn} (Vinculador, Configurador e núcleos de
     * tier) pulam a interação do bloco; senão o clique abriria a tela e o item não rodaria. Com
     * qualquer outro item, segue para {@link #useWithoutItem}, que abre a tela. Com Shift e um item
     * na mão o vanilla nem chama a interação do bloco: o giro é só com as mãos vazias.
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

    /**
     * Clique direito abre a tela do roteador. Com Shift (o vanilla só chega aqui com Shift quando as
     * duas mãos estão vazias) gira o roteador 90° em torno da face onde está preso, se o jogador pode
     * mexer no bloco; senão não faz nada.
     */
    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player,
            BlockHitResult hitResult) {
        if (player.isSecondaryUseActive()) {
            if (!player.mayBuild() || !level.mayInteract(player, pos)) {
                return InteractionResult.PASS;
            }
            if (!level.isClientSide && level.getBlockEntity(pos) instanceof RouterBlockEntity router) {
                router.spinTo((state.getValue(SPIN) + 1) % RelativeSide.SPINS);
                level.playSound(null, pos, SoundEvents.ITEM_FRAME_ROTATE_ITEM, SoundSource.BLOCKS, 1.0F, 1.0F);
            }
            return InteractionResult.sidedSuccess(level.isClientSide);
        }
        if (!level.isClientSide && player instanceof ServerPlayer serverPlayer
                && level.getBlockEntity(pos) instanceof RouterBlockEntity router) {
            RouterMenu.open(serverPlayer, router);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    /**
     * O conteúdo da máquina mudou: acorda as portas do nó e as origens que esperam por elas (ver
     * {@link NetworkManager#wake}; uma entrega nossa não acorda as alimentadoras). Só chega quando a máquina chama
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

    /**
     * Roteador removido (quebrado, solto por falta de apoio, trocado): solta os Cartões de Filtro e o
     * Upgrade de chunk loading. O upgrade de tier troca só o estado e não passa por aqui.
     */
    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        if (!state.is(newState.getBlock()) && level.getBlockEntity(pos) instanceof RouterBlockEntity router) {
            Containers.dropContents(level, pos, router.removeAllCards());
            ItemStack upgrade = router.removeUpgrade();
            if (!upgrade.isEmpty()) {
                Containers.dropItemStack(level, pos.getX(), pos.getY(), pos.getZ(), upgrade);
            }
            // Por último: tirar os cartões avisa o índice, que senão guardaria o nó de novo.
            NodeIndex.forget(level, pos);
        }
        super.onRemove(state, level, pos, newState, movedByPiston);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return RouterShapes.get(state.getValue(FACING));
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    /**
     * Estruturas: gira o {@code facing} e escolhe o {@code spin} que leva o lado {@link RelativeSide#TOP}
     * para a direção girada, para o roteador inteiro girar junto.
     */
    @Override
    protected BlockState rotate(BlockState state, Rotation rotation) {
        Direction top = RelativeSide.TOP.toAbsolute(state.getValue(FACING), state.getValue(SPIN));
        return withTop(state, rotation.rotate(state.getValue(FACING)), rotation.rotate(top));
    }

    /** Mesma regra do {@link #rotate}: o espelho não preserva a lateralidade, manter o {@code TOP} basta. */
    @Override
    protected BlockState mirror(BlockState state, Mirror mirror) {
        Direction top = RelativeSide.TOP.toAbsolute(state.getValue(FACING), state.getValue(SPIN));
        return withTop(state, mirror.mirror(state.getValue(FACING)), mirror.mirror(top));
    }

    /** O estado com {@code facing} e o giro em que {@code TOP} fica em {@code top}; sem nenhum, mantém o giro. */
    private static BlockState withTop(BlockState state, Direction facing, Direction top) {
        int spin = state.getValue(SPIN);
        for (int candidate = 0; candidate < RelativeSide.SPINS; candidate++) {
            if (RelativeSide.TOP.toAbsolute(facing, candidate) == top) {
                spin = candidate;
                break;
            }
        }
        return state.setValue(FACING, facing).setValue(SPIN, spin);
    }

    @Override
    public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new RouterBlockEntity(pos, state);
    }

    /**
     * Sobe o roteador em {@code pos} para {@code target}, se for exatamente o tier seguinte.
     * O block entity é mantido, então a configuração não se perde.
     */
    /**
     * Nome de uma máquina para mostrar ao jogador: o do item dela, como no inventário. O nome do
     * bloco pode ser um texto com argumentos que só o item preenche (os barris do Sophisticated
     * Storage mostram "%s%sNetherite Barrel" pelo bloco). Sem item, o nome do bloco.
     */
    public static Component machineName(Block block) {
        ItemStack stack = new ItemStack(block);
        return stack.isEmpty() ? block.getName() : stack.getHoverName();
    }

    /** Clique do meio (criativo): o roteador no tier do bloco, não o Básico padrão do item. */
    @Override
    public ItemStack getCloneItemStack(LevelReader level, BlockPos pos, BlockState state) {
        return RouterBlockItem.withTier((RouterBlockItem) asItem(), state.getValue(TIER));
    }

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
