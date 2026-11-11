package io.github.matheusanbs.wirelessautomate.dock;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.fluids.FluidStack;

/**
 * O pendente de uma Doca quebrada (componente {@code wirelessautomate:pattern_dock_pending}): por lado com pendente, o
 * bloco do lado e o pendente do lado inteiro (o de todas as máquinas e o solto, juntos), uma linha por item e por
 * fluido, com a quantidade em {@code long}, e os químicos. Os químicos vão em NBT, um por químico com a quantidade
 * inteira (o {@code ChemicalStack} do Mekanism já salva em long), pela ponte do Mekanism, ou a entrada crua que não deu
 * para ler, para o componente não depender do Mekanism. Volta aos mesmos lados ao recolocar a Doca, solto; sem
 * máquinas, o lado guarda o bloco até o pendente sair numa máquina dele. Só existe com pendente, para Docas vazias
 * empilharem.
 *
 * <p>Campos de cada lado: {@code side}, {@code block}, {@code item_lines} ({@code {item, count}}, o item de tamanho 1),
 * {@code fluid_lines} ({@code {fluid, amount}}, o fluido de 1 mB) e {@code chemicals}. O formato antigo (até o 7470507:
 * {@code items} e {@code fluids}, listas de pilhas, o item partido em pilhas de até 99) é lido e convertido em linhas;
 * o item salvo de novo sai só no formato novo.
 */
public record DockPendingContents(List<Side> sides) {
    public static final Codec<DockPendingContents> CODEC = Side.CODEC.listOf()
            .xmap(DockPendingContents::new, DockPendingContents::sides);

    public static final StreamCodec<RegistryFriendlyByteBuf, DockPendingContents> STREAM_CODEC =
            Side.STREAM_CODEC.apply(ByteBufCodecs.list()).map(DockPendingContents::new, DockPendingContents::sides);

    public DockPendingContents {
        sides = List.copyOf(sides);
    }

    /** Uma linha de item: o item (de tamanho 1, com os componentes) e quantos. */
    public record ItemLine(ItemStack item, long count) {
        static final Codec<ItemLine> CODEC = RecordCodecBuilder.create(i -> i.group(
                ItemStack.CODEC.fieldOf("item").forGetter(ItemLine::item),
                Codec.LONG.fieldOf("count").forGetter(ItemLine::count))
                .apply(i, ItemLine::new));

        static final StreamCodec<RegistryFriendlyByteBuf, ItemLine> STREAM_CODEC = StreamCodec.composite(
                ItemStack.STREAM_CODEC, ItemLine::item,
                ByteBufCodecs.VAR_LONG, ItemLine::count,
                ItemLine::new);

        public ItemLine {
            item = item.copyWithCount(1);
        }

        // O ItemStack não tem equals: sem estes, dois itens iguais não empilhariam e o servidor reenviaria o slot.
        @Override
        public boolean equals(Object other) {
            return other instanceof ItemLine that && count == that.count
                    && ItemStack.isSameItemSameComponents(item, that.item);
        }

        @Override
        public int hashCode() {
            return 31 * ItemStack.hashItemAndComponents(item) + Long.hashCode(count);
        }
    }

    /** Uma linha de fluido: o fluido (de 1 mB, com os componentes) e quantos mB. */
    public record FluidLine(FluidStack fluid, long amount) {
        static final Codec<FluidLine> CODEC = RecordCodecBuilder.create(i -> i.group(
                FluidStack.CODEC.fieldOf("fluid").forGetter(FluidLine::fluid),
                Codec.LONG.fieldOf("amount").forGetter(FluidLine::amount))
                .apply(i, FluidLine::new));

        static final StreamCodec<RegistryFriendlyByteBuf, FluidLine> STREAM_CODEC = StreamCodec.composite(
                FluidStack.STREAM_CODEC, FluidLine::fluid,
                ByteBufCodecs.VAR_LONG, FluidLine::amount,
                FluidLine::new);

        public FluidLine {
            fluid = fluid.copyWithAmount(1);
        }

        @Override
        public boolean equals(Object other) {
            return other instanceof FluidLine that && amount == that.amount
                    && FluidStack.isSameFluidSameComponents(fluid, that.fluid);
        }

        @Override
        public int hashCode() {
            return 31 * FluidStack.hashFluidAndComponents(fluid) + Long.hashCode(amount);
        }
    }

    /** O pendente de um lado ({@code Direction.ordinal()}); as linhas são cópias e não mudam. */
    public record Side(int side, ResourceLocation block, List<ItemLine> items, List<FluidLine> fluids,
            List<CompoundTag> chemicals) {
        static final Codec<Side> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.intRange(0, 5).fieldOf("side").forGetter(Side::side),
                ResourceLocation.CODEC.fieldOf("block").forGetter(Side::block),
                ItemLine.CODEC.listOf().optionalFieldOf("item_lines", List.of()).forGetter(Side::items),
                FluidLine.CODEC.listOf().optionalFieldOf("fluid_lines", List.of()).forGetter(Side::fluids),
                CompoundTag.CODEC.listOf().optionalFieldOf("chemicals", List.of()).forGetter(Side::chemicals),
                // o formato antigo, só lido: o getter vazio não é escrito
                ItemStack.CODEC.listOf().optionalFieldOf("items", List.of()).forGetter(side -> List.of()),
                FluidStack.CODEC.listOf().optionalFieldOf("fluids", List.of()).forGetter(side -> List.of()))
                .apply(i, Side::decode));

        static final StreamCodec<RegistryFriendlyByteBuf, Side> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_INT, Side::side,
                ResourceLocation.STREAM_CODEC, Side::block,
                ItemLine.STREAM_CODEC.apply(ByteBufCodecs.list()), Side::items,
                FluidLine.STREAM_CODEC.apply(ByteBufCodecs.list()), Side::fluids,
                ByteBufCodecs.COMPOUND_TAG.apply(ByteBufCodecs.list()), Side::chemicals,
                Side::new);

        /** Junta as linhas iguais e tira as vazias: o mesmo pendente dá sempre o mesmo componente. */
        public Side {
            DockPendingLot lot = new DockPendingLot();
            for (ItemLine line : items) {
                lot.addItem(line.item(), line.count());
            }
            for (FluidLine line : fluids) {
                lot.addFluid(line.fluid(), line.amount());
            }
            items = itemLines(lot);
            fluids = fluidLines(lot);
            List<CompoundTag> chemicalCopies = new ArrayList<>(chemicals.size());
            for (CompoundTag tag : chemicals) {
                if (!tag.isEmpty()) {
                    chemicalCopies.add(tag.copy());
                }
            }
            chemicals = List.copyOf(chemicalCopies);
        }

        /** O lado lido do NBT: as linhas do formato novo e as pilhas do antigo, juntas em linhas. */
        private static Side decode(int side, ResourceLocation block, List<ItemLine> items, List<FluidLine> fluids,
                List<CompoundTag> chemicals, List<ItemStack> legacyItems, List<FluidStack> legacyFluids) {
            if (legacyItems.isEmpty() && legacyFluids.isEmpty()) {
                return new Side(side, block, items, fluids, chemicals);
            }
            List<ItemLine> allItems = new ArrayList<>(items);
            for (ItemStack stack : legacyItems) {
                if (!stack.isEmpty()) {
                    allItems.add(new ItemLine(stack, stack.getCount()));
                }
            }
            List<FluidLine> allFluids = new ArrayList<>(fluids);
            for (FluidStack stack : legacyFluids) {
                if (!stack.isEmpty()) {
                    allFluids.add(new FluidLine(stack, stack.getAmount()));
                }
            }
            return new Side(side, block, allItems, allFluids, chemicals);
        }

        /**
         * O lado {@code side} com o bloco {@code block}, o pendente {@code lot} (os químicos dele gravados um por
         * linha) e as entradas cruas {@code raw}.
         */
        static Side of(int side, ResourceLocation block, DockPendingLot lot, List<CompoundTag> raw,
                HolderLookup.Provider registries) {
            List<CompoundTag> chemicals = new ArrayList<>(lot.chemicalLines().size() + raw.size());
            chemicals.addAll(lot.chemicalTags(registries));
            chemicals.addAll(raw);
            return new Side(side, block, itemLines(lot), fluidLines(lot), chemicals);
        }

        /** Os itens e os fluidos deste lado num lote novo (os químicos ficam no {@link #chemicals}, em NBT). */
        DockPendingLot lot() {
            DockPendingLot lot = new DockPendingLot();
            for (ItemLine line : items) {
                lot.addItem(line.item(), line.count());
            }
            for (FluidLine line : fluids) {
                lot.addFluid(line.fluid(), line.amount());
            }
            return lot;
        }

        /** Quantos itens iguais a {@code stack} (item e componentes) o lado guarda. */
        public long count(ItemStack stack) {
            for (ItemLine line : items) {
                if (ItemStack.isSameItemSameComponents(line.item(), stack)) {
                    return line.count();
                }
            }
            return 0;
        }

        private static List<ItemLine> itemLines(DockPendingLot lot) {
            List<ItemLine> lines = new ArrayList<>(lot.itemLines().size());
            for (DockPendingLot.Line<ItemStack> line : lot.itemLines()) {
                lines.add(new ItemLine(line.key(), line.amount()));
            }
            return List.copyOf(lines);
        }

        private static List<FluidLine> fluidLines(DockPendingLot lot) {
            List<FluidLine> lines = new ArrayList<>(lot.fluidLines().size());
            for (DockPendingLot.Line<FluidStack> line : lot.fluidLines()) {
                lines.add(new FluidLine(line.key(), line.amount()));
            }
            return List.copyOf(lines);
        }
    }
}
