package io.github.matheusanbs.wirelessautomate.dock;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.fluids.FluidStack;

/**
 * O pendente de uma Doca quebrada (componente {@code wirelessautomate:pattern_dock_pending}): por lado com pendente, o
 * bloco do lado, os itens, os fluidos e os químicos. Os químicos vão em NBT, como o {@link DockSide} os salva (pela
 * ponte do Mekanism, ou a entrada crua que não deu para ler), para o componente não depender do Mekanism. Volta aos
 * mesmos lados ao recolocar a Doca; sem máquinas, o lado guarda o bloco até o pendente sair numa máquina dele. Só
 * existe com pendente, para Docas vazias empilharem. O pendente é o resto de um pedido (poucas pilhas), então vai
 * inteiro no item, sem referência a dados do servidor.
 */
public record DockPendingContents(List<Side> sides) {
    /** O teto de {@code count} no codec do {@code ItemStack}: um pendente maior vai em partes. */
    private static final int STACK_MAX = 99;

    public static final Codec<DockPendingContents> CODEC = Side.CODEC.listOf()
            .xmap(DockPendingContents::new, DockPendingContents::sides);

    public static final StreamCodec<RegistryFriendlyByteBuf, DockPendingContents> STREAM_CODEC =
            Side.STREAM_CODEC.apply(ByteBufCodecs.list()).map(DockPendingContents::new, DockPendingContents::sides);

    public DockPendingContents {
        sides = List.copyOf(sides);
    }

    /** O pendente de um lado ({@code Direction.ordinal()}); as pilhas são cópias e não mudam. */
    public record Side(int side, ResourceLocation block, List<ItemStack> items, List<FluidStack> fluids,
            List<CompoundTag> chemicals) {
        static final Codec<Side> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.intRange(0, 5).fieldOf("side").forGetter(Side::side),
                ResourceLocation.CODEC.fieldOf("block").forGetter(Side::block),
                ItemStack.CODEC.listOf().optionalFieldOf("items", List.of()).forGetter(Side::items),
                FluidStack.CODEC.listOf().optionalFieldOf("fluids", List.of()).forGetter(Side::fluids),
                CompoundTag.CODEC.listOf().optionalFieldOf("chemicals", List.of()).forGetter(Side::chemicals))
                .apply(i, Side::new));

        static final StreamCodec<RegistryFriendlyByteBuf, Side> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_INT, Side::side,
                ResourceLocation.STREAM_CODEC, Side::block,
                ItemStack.STREAM_CODEC.apply(ByteBufCodecs.list()), Side::items,
                FluidStack.STREAM_CODEC.apply(ByteBufCodecs.list()), Side::fluids,
                ByteBufCodecs.COMPOUND_TAG.apply(ByteBufCodecs.list()), Side::chemicals,
                Side::new);

        public Side {
            List<ItemStack> parts = new ArrayList<>(items.size());
            for (ItemStack stack : items) {
                int left = stack.getCount();
                while (left > 0) {
                    int part = Math.min(left, STACK_MAX);
                    parts.add(stack.copyWithCount(part));
                    left -= part;
                }
            }
            items = List.copyOf(parts);
            List<FluidStack> fluidCopies = new ArrayList<>(fluids.size());
            for (FluidStack stack : fluids) {
                if (!stack.isEmpty()) {
                    fluidCopies.add(stack.copy());
                }
            }
            fluids = List.copyOf(fluidCopies);
            List<CompoundTag> chemicalCopies = new ArrayList<>(chemicals.size());
            for (CompoundTag tag : chemicals) {
                chemicalCopies.add(tag.copy());
            }
            chemicals = List.copyOf(chemicalCopies);
        }

        // O ItemStack e o FluidStack não têm equals: sem estes, dois itens iguais não empilhariam e o servidor
        // reenviaria o slot a cada conferência.
        @Override
        public boolean equals(Object other) {
            if (this == other) {
                return true;
            }
            if (!(other instanceof Side that) || side != that.side || !block.equals(that.block)
                    || !ItemStack.listMatches(items, that.items) || fluids.size() != that.fluids.size()
                    || !chemicals.equals(that.chemicals)) {
                return false;
            }
            for (int i = 0; i < fluids.size(); i++) {
                if (!FluidStack.matches(fluids.get(i), that.fluids.get(i))) {
                    return false;
                }
            }
            return true;
        }

        @Override
        public int hashCode() {
            int hash = 31 * side + block.hashCode();
            hash = 31 * hash + ItemStack.hashStackList(items);
            for (FluidStack stack : fluids) {
                hash = 31 * hash + FluidStack.hashFluidAndComponents(stack) * 31 + stack.getAmount();
            }
            return 31 * hash + chemicals.hashCode();
        }
    }
}
