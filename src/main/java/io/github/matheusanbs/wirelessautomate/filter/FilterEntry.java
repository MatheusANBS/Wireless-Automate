package io.github.matheusanbs.wirelessautomate.filter;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.fluids.FluidStack;

/**
 * Uma entrada de filtro: exata (item ou fluido, com componentes), tag ou mod. {@code stock} é o
 * estoque da especificação (0 = sem estoque): ao inserir, aceitar só até N no destino; ao extrair,
 * manter sempre N na origem. Imutável. Duas entradas são duplicadas se {@link #sameTarget} for verdadeiro.
 */
public sealed interface FilterEntry {
    long stock();

    FilterEntry withStock(long stock);

    /** Mesmo alvo, ignorando o estoque. */
    boolean sameTarget(FilterEntry other);

    String kind();

    /** Item exato; a pilha é guardada com quantidade 1. */
    record ItemEntry(ItemStack stack, long stock) implements FilterEntry {
        public ItemEntry {
            stack = stack.copyWithCount(1);
            stock = Math.max(0, stock);
        }

        @Override
        public FilterEntry withStock(long stock) {
            return new ItemEntry(stack, stock);
        }

        @Override
        public boolean sameTarget(FilterEntry other) {
            return other instanceof ItemEntry o && ItemStack.isSameItemSameComponents(stack, o.stack);
        }

        @Override
        public String kind() {
            return "item";
        }

        @Override
        public boolean equals(Object o) {
            return o instanceof ItemEntry other && stock == other.stock && sameTarget(other);
        }

        @Override
        public int hashCode() {
            return ItemStack.hashItemAndComponents(stack) * 31 + Long.hashCode(stock);
        }
    }

    /** Fluido exato; a pilha é guardada com 1 mB. */
    record FluidEntry(FluidStack stack, long stock) implements FilterEntry {
        public FluidEntry {
            stack = stack.copyWithAmount(1);
            stock = Math.max(0, stock);
        }

        @Override
        public FilterEntry withStock(long stock) {
            return new FluidEntry(stack, stock);
        }

        @Override
        public boolean sameTarget(FilterEntry other) {
            return other instanceof FluidEntry o && FluidStack.isSameFluidSameComponents(stack, o.stack);
        }

        @Override
        public String kind() {
            return "fluid";
        }

        @Override
        public boolean equals(Object o) {
            return o instanceof FluidEntry other && stock == other.stock && sameTarget(other);
        }

        @Override
        public int hashCode() {
            return FluidStack.hashFluidAndComponents(stack) * 31 + Long.hashCode(stock);
        }
    }

    /** Tag ({@code #c:ingots}); vale como tag de item ou de fluido conforme o tipo da face. */
    record TagEntry(ResourceLocation tag, long stock) implements FilterEntry {
        public TagEntry {
            stock = Math.max(0, stock);
        }

        @Override
        public FilterEntry withStock(long stock) {
            return new TagEntry(tag, stock);
        }

        @Override
        public boolean sameTarget(FilterEntry other) {
            return other instanceof TagEntry o && tag.equals(o.tag);
        }

        @Override
        public String kind() {
            return "tag";
        }
    }

    /** Tudo de um mod ({@code @mekanism}), pelo namespace do id. */
    record ModEntry(String modId, long stock) implements FilterEntry {
        public ModEntry {
            stock = Math.max(0, stock);
        }

        @Override
        public FilterEntry withStock(long stock) {
            return new ModEntry(modId, stock);
        }

        @Override
        public boolean sameTarget(FilterEntry other) {
            return other instanceof ModEntry o && modId.equals(o.modId);
        }

        @Override
        public String kind() {
            return "mod";
        }
    }

    Codec<Long> STOCK = Codec.LONG;

    MapCodec<ItemEntry> ITEM_CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            ItemStack.SINGLE_ITEM_CODEC.fieldOf("item").forGetter(ItemEntry::stack),
            STOCK.optionalFieldOf("stock", 0L).forGetter(ItemEntry::stock)).apply(i, ItemEntry::new));
    MapCodec<FluidEntry> FLUID_CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            FluidStack.CODEC.fieldOf("fluid").forGetter(FluidEntry::stack),
            STOCK.optionalFieldOf("stock", 0L).forGetter(FluidEntry::stock)).apply(i, FluidEntry::new));
    MapCodec<TagEntry> TAG_CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            ResourceLocation.CODEC.fieldOf("tag").forGetter(TagEntry::tag),
            STOCK.optionalFieldOf("stock", 0L).forGetter(TagEntry::stock)).apply(i, TagEntry::new));
    MapCodec<ModEntry> MOD_CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            Codec.STRING.fieldOf("mod").forGetter(ModEntry::modId),
            STOCK.optionalFieldOf("stock", 0L).forGetter(ModEntry::stock)).apply(i, ModEntry::new));

    Codec<FilterEntry> CODEC = Codec.STRING.dispatch("kind", FilterEntry::kind, kind -> switch (kind) {
        case "item" -> ITEM_CODEC;
        case "fluid" -> FLUID_CODEC;
        case "tag" -> TAG_CODEC;
        case "mod" -> MOD_CODEC;
        default -> throw new IllegalArgumentException("Tipo de entrada de filtro desconhecido: " + kind);
    });

    StreamCodec<RegistryFriendlyByteBuf, FilterEntry> STREAM_CODEC = StreamCodec.of(FilterEntry::encode, FilterEntry::decode);

    private static void encode(RegistryFriendlyByteBuf buf, FilterEntry entry) {
        switch (entry) {
            case ItemEntry e -> {
                buf.writeByte(0);
                ItemStack.STREAM_CODEC.encode(buf, e.stack());
            }
            case FluidEntry e -> {
                buf.writeByte(1);
                FluidStack.STREAM_CODEC.encode(buf, e.stack());
            }
            case TagEntry e -> {
                buf.writeByte(2);
                buf.writeResourceLocation(e.tag());
            }
            case ModEntry e -> {
                buf.writeByte(3);
                buf.writeUtf(e.modId(), 64);
            }
        }
        buf.writeVarLong(entry.stock());
    }

    private static FilterEntry decode(RegistryFriendlyByteBuf buf) {
        byte kind = buf.readByte();
        return switch (kind) {
            case 0 -> new ItemEntry(ItemStack.STREAM_CODEC.decode(buf), 0).withStock(buf.readVarLong());
            case 1 -> new FluidEntry(FluidStack.STREAM_CODEC.decode(buf), 0).withStock(buf.readVarLong());
            case 2 -> new TagEntry(buf.readResourceLocation(), buf.readVarLong());
            case 3 -> new ModEntry(buf.readUtf(64), buf.readVarLong());
            default -> throw new IllegalArgumentException("Tipo de entrada de filtro desconhecido: " + kind);
        };
    }
}
