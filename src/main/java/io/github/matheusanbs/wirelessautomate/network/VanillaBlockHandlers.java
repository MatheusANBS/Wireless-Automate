package io.github.matheusanbs.wirelessautomate.network;

import com.google.common.math.IntMath;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.WorldlyContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ComposterBlock;
import net.minecraft.world.level.block.LayeredCauldronBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.FluidType;
import net.minecraftforge.fluids.capability.IFluidHandler;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.wrapper.SidedInvWrapper;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Porte 1.20.1: os handlers que o NeoForge 21.1 dá a blocos vanilla sem block entity e o Forge 1.20.1 não dá
 * (o {@code CapabilityHooks} do NeoForge): o caldeirão (fluido, {@link CauldronFluidHandler}, porte do
 * {@code CauldronWrapper}) e o compostor (itens, {@link ComposterItemHandler}). O baú, que o NeoForge também
 * registra, já tem block entity e capability no Forge. Caldeirões de outros mods não entram: no 1.20.1 não há
 * o registro de conteúdo de caldeirão do NeoForge.
 *
 * <p>O {@link CapCache} pergunta aqui no modo de busca por bloco ({@link CapCache#create(net.minecraftforge.common.capabilities.Capability,
 * ServerLevel, BlockPos, Direction, CapCache.BlockLookup, java.util.function.BooleanSupplier, Runnable)}): o estado
 * do bloco só é lido na busca suja, e o handler é guardado. Os handlers relêem o estado a cada chamada (como os do
 * NeoForge) e não leem nada com o chunk do bloco descarregado (o {@code getBlockState} carregaria o chunk): aí se
 * comportam como vazios.
 */
public final class VanillaBlockHandlers {

    /** Handler de fluido do caldeirão em {@code state}, ou {@code null} se o bloco não é um dos três caldeirões. */
    public static @Nullable IFluidHandler fluids(ServerLevel level, BlockPos pos, BlockState state, @Nullable Direction side) {
        return Cauldron.of(state.getBlock()) != null ? new CauldronFluidHandler(level, pos) : null;
    }

    /** Handler de itens do compostor em {@code state} pelo lado {@code side}, ou {@code null} se não é um compostor. */
    public static @Nullable IItemHandler items(ServerLevel level, BlockPos pos, BlockState state, @Nullable Direction side) {
        return state.is(Blocks.COMPOSTER) ? new ComposterItemHandler(level, pos, side) : null;
    }

    /**
     * Os dois blocos são caldeirões vanilla (vazio, água ou lava): trocar um pelo outro não muda o handler (o mesmo
     * {@link CauldronFluidHandler}, que relê o estado), então os caches da máquina continuam valendo.
     */
    public static boolean bothCauldrons(@Nullable Block from, @Nullable Block to) {
        return from != null && to != null && Cauldron.of(from) != null && Cauldron.of(to) != null;
    }

    /**
     * O conteúdo de cada caldeirão, como o {@code CauldronFluidContent} do NeoForge 21.1: bloco, fluido, total
     * ({@link FluidType#BUCKET_VOLUME}), nível máximo e a propriedade de nível (a neve em pó não entra).
     */
    enum Cauldron {
        EMPTY(Blocks.CAULDRON, Fluids.EMPTY, null, 1),
        WATER(Blocks.WATER_CAULDRON, Fluids.WATER, LayeredCauldronBlock.LEVEL, 3),
        LAVA(Blocks.LAVA_CAULDRON, Fluids.LAVA, null, 1);

        final Block block;
        final Fluid fluid;
        final int totalAmount = FluidType.BUCKET_VOLUME;
        final int maxLevel;
        final @Nullable IntegerProperty levelProperty;

        Cauldron(Block block, Fluid fluid, @Nullable IntegerProperty levelProperty, int maxLevel) {
            this.block = block;
            this.fluid = fluid;
            this.levelProperty = levelProperty;
            this.maxLevel = maxLevel;
        }

        int currentLevel(BlockState state) {
            if (fluid == Fluids.EMPTY) {
                return 0;
            } else if (levelProperty == null) {
                return 1;
            }
            return state.getValue(levelProperty);
        }

        static @Nullable Cauldron of(Block block) {
            for (Cauldron cauldron : values()) {
                if (cauldron.block == block) {
                    return cauldron;
                }
            }
            return null;
        }

        static @Nullable Cauldron forFluid(Fluid fluid) {
            for (Cauldron cauldron : values()) {
                if (cauldron.fluid == fluid) {
                    return cauldron;
                }
            }
            return null;
        }
    }

    /**
     * Porte linha a linha do {@code CauldronWrapper} do NeoForge 21.1: um tanque de 1000 mB; água e lava só em
     * passos de {@code totalAmount / gcd(maxLevel, totalAmount)} (1000 mB: meio caldeirão aparece como 333 ou 666
     * mB, mas não aceita nem entrega); encher ou esvaziar troca o bloco ({@code setBlockAndUpdate}). Diferenças: o
     * {@code drain(FluidStack)} exige {@code !hasTag()} (o {@code getComponents().isEmpty()} do 1.21); e onde o
     * NeoForge lançaria exceção (o bloco já não é caldeirão) ou carregaria o chunk, este se comporta como vazio.
     */
    static final class CauldronFluidHandler implements IFluidHandler {
        private final ServerLevel level;
        private final BlockPos pos;

        CauldronFluidHandler(ServerLevel level, BlockPos pos) {
            this.level = level;
            this.pos = pos.immutable();
        }

        /** O estado do caldeirão, ou {@code null} com o chunk descarregado. */
        private @Nullable BlockState state() {
            return level.isLoaded(pos) ? level.getBlockState(pos) : null;
        }

        @Override
        public int getTanks() {
            return 1;
        }

        @Override
        public @NotNull FluidStack getFluidInTank(int tank) {
            BlockState state = state();
            Cauldron contents = state == null ? null : Cauldron.of(state.getBlock());
            if (contents == null) {
                return FluidStack.EMPTY;
            }
            int amount = contents.totalAmount * contents.currentLevel(state) / contents.maxLevel;
            return amount <= 0 ? FluidStack.EMPTY : new FluidStack(contents.fluid, amount);
        }

        @Override
        public int getTankCapacity(int tank) {
            BlockState state = state();
            Cauldron contents = state == null ? null : Cauldron.of(state.getBlock());
            return contents == null ? 0 : contents.totalAmount;
        }

        @Override
        public boolean isFluidValid(int tank, @NotNull FluidStack stack) {
            return Cauldron.forFluid(stack.getFluid()) != null;
        }

        /** Chamado pelo {@code fill} e pelo {@code drain} para trocar o estado. */
        private void updateLevel(Cauldron newContent, int newLevel, FluidAction action) {
            if (action.execute()) {
                BlockState newState = newContent.block.defaultBlockState();
                if (newContent.levelProperty != null) {
                    newState = newState.setValue(newContent.levelProperty, newLevel);
                }
                level.setBlockAndUpdate(pos, newState);
            }
        }

        @Override
        public int fill(FluidStack resource, FluidAction action) {
            if (resource.isEmpty()) {
                return 0;
            }
            Cauldron insertContent = Cauldron.forFluid(resource.getFluid());
            if (insertContent == null) {
                return 0;
            }
            BlockState state = state();
            Cauldron currentContent = state == null ? null : Cauldron.of(state.getBlock());
            if (currentContent == null) {
                return 0;
            }
            if (currentContent.fluid != Fluids.EMPTY && resource.getFluid() != currentContent.fluid) {
                // Fluido diferente.
                return 0;
            }
            // Só em passos do mdc entre o número de níveis e o total.
            int d = IntMath.gcd(insertContent.maxLevel, insertContent.totalAmount);
            int amountIncrements = insertContent.totalAmount / d;
            int levelIncrements = insertContent.maxLevel / d;

            int currentLevel = currentContent.currentLevel(state);
            int insertedIncrements = Math.min(resource.getAmount() / amountIncrements,
                    (insertContent.maxLevel - currentLevel) / levelIncrements);
            if (insertedIncrements > 0) {
                updateLevel(insertContent, currentLevel + insertedIncrements * levelIncrements, action);
            }
            return insertedIncrements * amountIncrements;
        }

        @Override
        public @NotNull FluidStack drain(FluidStack resource, FluidAction action) {
            if (resource.isEmpty()) {
                return FluidStack.EMPTY;
            }
            BlockState state = state();
            Cauldron contents = state == null ? null : Cauldron.of(state.getBlock());
            if (contents != null && resource.getFluid() == contents.fluid && !resource.hasTag()) {
                return drain(state, contents, resource.getAmount(), action);
            }
            return FluidStack.EMPTY;
        }

        @Override
        public @NotNull FluidStack drain(int maxDrain, FluidAction action) {
            if (maxDrain <= 0) {
                return FluidStack.EMPTY;
            }
            BlockState state = state();
            Cauldron contents = state == null ? null : Cauldron.of(state.getBlock());
            return contents == null ? FluidStack.EMPTY : drain(state, contents, maxDrain, action);
        }

        private FluidStack drain(BlockState state, Cauldron content, int maxDrain, FluidAction action) {
            // Só em passos do mdc entre o número de níveis e o total.
            int d = IntMath.gcd(content.maxLevel, content.totalAmount);
            int amountIncrements = content.totalAmount / d;
            int levelIncrements = content.maxLevel / d;

            int currentLevel = content.currentLevel(state);
            int extractedIncrements = Math.min(maxDrain / amountIncrements, currentLevel / levelIncrements);
            if (extractedIncrements > 0) {
                int newLevel = currentLevel - extractedIncrements * levelIncrements;
                if (newLevel == 0) {
                    // Esvaziou: volta a ser o caldeirão vazio.
                    if (action.execute()) {
                        level.setBlockAndUpdate(pos, Blocks.CAULDRON.defaultBlockState());
                    }
                } else {
                    updateLevel(content, newLevel, action);
                }
            }
            int amount = extractedIncrements * amountIncrements;
            return amount <= 0 ? FluidStack.EMPTY : new FluidStack(content.fluid, amount);
        }
    }

    /**
     * O handler de itens do compostor do NeoForge 21.1 ({@code ForwardingItemHandler} sobre um
     * {@link SidedInvWrapper} do {@code ComposterBlock.getContainer}), refeito a cada chamada com o estado do
     * momento: com nível abaixo de 7 aceita itens compostáveis por cima (um por vez), com 7 não faz nada, com 8
     * entrega a farinha de osso por baixo. Com o chunk descarregado ou outro bloco no lugar, nada.
     */
    static final class ComposterItemHandler implements IItemHandler {
        private static final WorldlyContainer NOTHING = new Nothing();

        private final ServerLevel level;
        private final BlockPos pos;
        private final @Nullable Direction side;

        ComposterItemHandler(ServerLevel level, BlockPos pos, @Nullable Direction side) {
            this.level = level;
            this.pos = pos.immutable();
            this.side = side;
        }

        private IItemHandler current() {
            BlockState state = level.isLoaded(pos) ? level.getBlockState(pos) : null;
            WorldlyContainer container = state != null && state.getBlock() instanceof ComposterBlock composter
                    ? composter.getContainer(state, level, pos) : NOTHING;
            return new SidedInvWrapper(container, side);
        }

        @Override
        public int getSlots() {
            return current().getSlots();
        }

        @Override
        public @NotNull ItemStack getStackInSlot(int slot) {
            return current().getStackInSlot(slot);
        }

        @Override
        public @NotNull ItemStack insertItem(int slot, @NotNull ItemStack stack, boolean simulate) {
            return current().insertItem(slot, stack, simulate);
        }

        @Override
        public @NotNull ItemStack extractItem(int slot, int amount, boolean simulate) {
            return current().extractItem(slot, amount, simulate);
        }

        @Override
        public int getSlotLimit(int slot) {
            return current().getSlotLimit(slot);
        }

        @Override
        public boolean isItemValid(int slot, @NotNull ItemStack stack) {
            return current().isItemValid(slot, stack);
        }

        /** Sem slots (o {@code EmptyContainer} do compostor, que é privado). */
        private static final class Nothing extends SimpleContainer implements WorldlyContainer {
            Nothing() {
                super(0);
            }

            @Override
            public int[] getSlotsForFace(Direction face) {
                return new int[0];
            }

            @Override
            public boolean canPlaceItemThroughFace(int index, ItemStack stack, @Nullable Direction direction) {
                return false;
            }

            @Override
            public boolean canTakeItemThroughFace(int index, ItemStack stack, Direction direction) {
                return false;
            }
        }
    }

    private VanillaBlockHandlers() {
    }
}
