package io.github.matheusanbs.wirelessautomate.filter;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.Optional;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.fluids.FluidStack;

/**
 * Uma entrada de filtro: exata (item ou fluido, com componentes; químico do Mekanism pelo id), tag, mod
 * ou regra de item por propriedade ({@link ItemRule}). Dois limites, 0 = sem limite: {@code stock}
 * ("Aceitar até"): ao inserir, aceitar só até N no destino; {@code keep} ("Manter na origem"): ao
 * extrair, deixar sempre N na origem. Imutável. Duas entradas são duplicadas se {@link #sameTarget} for verdadeiro.
 */
public sealed interface FilterEntry {
    long stock();

    long keep();

    FilterEntry withStock(long stock);

    FilterEntry withKeep(long keep);

    /** Mesmo alvo, ignorando o estoque. */
    boolean sameTarget(FilterEntry other);

    String kind();

    /** Item exato; a pilha é guardada com quantidade 1. */
    record ItemEntry(ItemStack stack, long stock, long keep) implements FilterEntry {
        public ItemEntry(ItemStack stack, long stock) {
            this(stack, stock, 0);
        }

        public ItemEntry {
            stack = stack.copyWithCount(1);
            stock = Math.max(0, stock);
            keep = Math.max(0, keep);
        }

        @Override
        public FilterEntry withStock(long stock) {
            return new ItemEntry(stack, stock, keep);
        }

        @Override
        public FilterEntry withKeep(long keep) {
            return new ItemEntry(stack, stock, keep);
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
            return o instanceof ItemEntry other && stock == other.stock && keep == other.keep && sameTarget(other);
        }

        @Override
        public int hashCode() {
            return ItemStack.hashItemAndComponents(stack) * 31 + Long.hashCode(stock) * 31 + Long.hashCode(keep);
        }
    }

    /** Fluido exato; a pilha é guardada com 1 mB. */
    record FluidEntry(FluidStack stack, long stock, long keep) implements FilterEntry {
        public FluidEntry(FluidStack stack, long stock) {
            this(stack, stock, 0);
        }

        public FluidEntry {
            stack = stack.copyWithAmount(1);
            stock = Math.max(0, stock);
            keep = Math.max(0, keep);
        }

        @Override
        public FilterEntry withStock(long stock) {
            return new FluidEntry(stack, stock, keep);
        }

        @Override
        public FilterEntry withKeep(long keep) {
            return new FluidEntry(stack, stock, keep);
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
            return o instanceof FluidEntry other && stock == other.stock && keep == other.keep && sameTarget(other);
        }

        @Override
        public int hashCode() {
            return FluidStack.hashFluidAndComponents(stack) * 31 + Long.hashCode(stock) * 31 + Long.hashCode(keep);
        }
    }

    /**
     * Químico do Mekanism exato, pelo id ({@code mekanism:hydrogen}). Guarda só o id, sem classes do
     * Mekanism: o filtro é salvo e lido mesmo com ele ausente (e então não casa com nada).
     */
    record ChemicalEntry(ResourceLocation chemical, long stock, long keep) implements FilterEntry {
        public ChemicalEntry(ResourceLocation chemical, long stock) {
            this(chemical, stock, 0);
        }

        public ChemicalEntry {
            stock = Math.max(0, stock);
            keep = Math.max(0, keep);
        }

        @Override
        public FilterEntry withStock(long stock) {
            return new ChemicalEntry(chemical, stock, keep);
        }

        @Override
        public FilterEntry withKeep(long keep) {
            return new ChemicalEntry(chemical, stock, keep);
        }

        @Override
        public boolean sameTarget(FilterEntry other) {
            return other instanceof ChemicalEntry o && chemical.equals(o.chemical);
        }

        @Override
        public String kind() {
            return "chemical";
        }
    }

    /** Tag ({@code #c:ingots}); vale como tag de item ou de fluido conforme o tipo da face. */
    record TagEntry(ResourceLocation tag, long stock, long keep) implements FilterEntry {
        public TagEntry(ResourceLocation tag, long stock) {
            this(tag, stock, 0);
        }

        public TagEntry {
            stock = Math.max(0, stock);
            keep = Math.max(0, keep);
        }

        @Override
        public FilterEntry withStock(long stock) {
            return new TagEntry(tag, stock, keep);
        }

        @Override
        public FilterEntry withKeep(long keep) {
            return new TagEntry(tag, stock, keep);
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
    record ModEntry(String modId, long stock, long keep) implements FilterEntry {
        public ModEntry(String modId, long stock) {
            this(modId, stock, 0);
        }

        public ModEntry {
            stock = Math.max(0, stock);
            keep = Math.max(0, keep);
        }

        @Override
        public FilterEntry withStock(long stock) {
            return new ModEntry(modId, stock, keep);
        }

        @Override
        public FilterEntry withKeep(long keep) {
            return new ModEntry(modId, stock, keep);
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

    /** Regra de item por propriedade ("qualquer item encantado"); só vale em filtros de itens. */
    record RuleEntry(ItemRule rule, long stock, long keep) implements FilterEntry {
        public RuleEntry(ItemRule rule, long stock) {
            this(rule, stock, 0);
        }

        public RuleEntry {
            stock = Math.max(0, stock);
            keep = Math.max(0, keep);
        }

        @Override
        public FilterEntry withStock(long stock) {
            return new RuleEntry(rule, stock, keep);
        }

        @Override
        public FilterEntry withKeep(long keep) {
            return new RuleEntry(rule, stock, keep);
        }

        @Override
        public boolean sameTarget(FilterEntry other) {
            return other instanceof RuleEntry o && rule.equals(o.rule);
        }

        @Override
        public String kind() {
            return "rule";
        }
    }

    Codec<Long> STOCK = Codec.LONG;
    // "keep" ausente (dado salvo antes dele existir): vale o estoque, o que mantém o comportamento de quando era um número só.

    MapCodec<ItemEntry> ITEM_CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            ItemStack.SINGLE_ITEM_CODEC.fieldOf("item").forGetter(ItemEntry::stack),
            STOCK.optionalFieldOf("stock", 0L).forGetter(ItemEntry::stock),
            Codec.LONG.optionalFieldOf("keep").forGetter(e -> Optional.of(e.keep())))
            .apply(i, (a, stock, keep) -> new ItemEntry(a, stock, keep.orElse(stock))));
    MapCodec<FluidEntry> FLUID_CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            FluidStack.CODEC.fieldOf("fluid").forGetter(FluidEntry::stack),
            STOCK.optionalFieldOf("stock", 0L).forGetter(FluidEntry::stock),
            Codec.LONG.optionalFieldOf("keep").forGetter(e -> Optional.of(e.keep())))
            .apply(i, (a, stock, keep) -> new FluidEntry(a, stock, keep.orElse(stock))));
    MapCodec<ChemicalEntry> CHEMICAL_CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            ResourceLocation.CODEC.fieldOf("chemical").forGetter(ChemicalEntry::chemical),
            STOCK.optionalFieldOf("stock", 0L).forGetter(ChemicalEntry::stock),
            Codec.LONG.optionalFieldOf("keep").forGetter(e -> Optional.of(e.keep())))
            .apply(i, (a, stock, keep) -> new ChemicalEntry(a, stock, keep.orElse(stock))));
    MapCodec<TagEntry> TAG_CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            ResourceLocation.CODEC.fieldOf("tag").forGetter(TagEntry::tag),
            STOCK.optionalFieldOf("stock", 0L).forGetter(TagEntry::stock),
            Codec.LONG.optionalFieldOf("keep").forGetter(e -> Optional.of(e.keep())))
            .apply(i, (a, stock, keep) -> new TagEntry(a, stock, keep.orElse(stock))));
    MapCodec<ModEntry> MOD_CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            Codec.STRING.fieldOf("mod").forGetter(ModEntry::modId),
            STOCK.optionalFieldOf("stock", 0L).forGetter(ModEntry::stock),
            Codec.LONG.optionalFieldOf("keep").forGetter(e -> Optional.of(e.keep())))
            .apply(i, (a, stock, keep) -> new ModEntry(a, stock, keep.orElse(stock))));

    MapCodec<RuleEntry> RULE_CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            ItemRule.CODEC.fieldOf("rule").forGetter(RuleEntry::rule),
            STOCK.optionalFieldOf("stock", 0L).forGetter(RuleEntry::stock),
            Codec.LONG.optionalFieldOf("keep").forGetter(e -> Optional.of(e.keep())))
            .apply(i, (a, stock, keep) -> new RuleEntry(a, stock, keep.orElse(stock))));

    Codec<FilterEntry> CODEC = Codec.STRING.dispatch("kind", FilterEntry::kind, kind -> switch (kind) {
        case "item" -> ITEM_CODEC;
        case "fluid" -> FLUID_CODEC;
        case "tag" -> TAG_CODEC;
        case "mod" -> MOD_CODEC;
        case "chemical" -> CHEMICAL_CODEC;
        case "rule" -> RULE_CODEC;
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
            case ChemicalEntry e -> {
                buf.writeByte(4);
                buf.writeResourceLocation(e.chemical());
            }
            case RuleEntry e -> {
                buf.writeByte(5);
                ItemRule.STREAM_CODEC.encode(buf, e.rule());
            }
        }
        buf.writeVarLong(entry.stock());
        buf.writeVarLong(entry.keep());
    }

    private static FilterEntry decode(RegistryFriendlyByteBuf buf) {
        byte kind = buf.readByte();
        return switch (kind) {
            case 0 -> readLimits(new ItemEntry(ItemStack.STREAM_CODEC.decode(buf), 0), buf);
            case 1 -> readLimits(new FluidEntry(FluidStack.STREAM_CODEC.decode(buf), 0), buf);
            case 2 -> readLimits(new TagEntry(buf.readResourceLocation(), 0), buf);
            case 3 -> readLimits(new ModEntry(buf.readUtf(64), 0), buf);
            case 4 -> readLimits(new ChemicalEntry(buf.readResourceLocation(), 0), buf);
            case 5 -> readLimits(new RuleEntry(ItemRule.STREAM_CODEC.decode(buf), 0), buf);
            default -> throw new IllegalArgumentException("Tipo de entrada de filtro desconhecido: " + kind);
        };
    }

    private static FilterEntry readLimits(FilterEntry entry, RegistryFriendlyByteBuf buf) {
        long stock = buf.readVarLong();
        return entry.withStock(stock).withKeep(buf.readVarLong());
    }
}
