package io.github.matheusanbs.wirelessautomate.dock;

import io.github.matheusanbs.wirelessautomate.network.Chemicals;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.material.Fluid;
import net.neoforged.neoforge.fluids.FluidStack;

/**
 * O pendente de uma máquina (ou o solto de um lado, {@link DockSide#loose}): uma linha por item (item e componentes
 * iguais), por fluido e por químico, com a quantidade total em {@code long}. O resto de um padrão de 524.288 numa
 * fábrica cheia é uma linha, não 7.680 pilhas.
 *
 * <p>A chave de cada linha é uma cópia de tamanho 1 (a pilha, o fluido ou o {@code ChemicalStack} do Mekanism, este
 * como {@code Object}, pela ponte {@link Chemicals}); a quantidade fica na linha. Juntar uma pilha procura a linha
 * igual entre as poucas do lote, sem alocar se ela já existe.
 *
 * <p>NBT: {@code items} (lista de {@code {item, count}}, o item de tamanho 1 e o {@code count} em long), {@code fluids}
 * ({@code {fluid, amount}}, o fluido de 1 mB) e {@code chemicals} (cada químico salvo pelo Mekanism, com a quantidade
 * inteira, que já é long). Uma entrada de químico que não dá para ler (sem o Mekanism) não entra no lote: vai para quem
 * carrega, que a guarda crua.
 */
public final class DockPendingLot {
    private final List<Line<ItemStack>> items = new ArrayList<>(1);
    private final List<Line<FluidStack>> fluids = new ArrayList<>(0);
    private final List<Line<Object>> chemicals = new ArrayList<>(0);

    /** Uma linha do lote: a chave (de tamanho 1, não muda) e a quantidade. */
    public static final class Line<K> {
        private final K key;
        private long amount;

        Line(K key, long amount) {
            this.key = key;
            this.amount = amount;
        }

        /** A chave: a pilha de 1 item, o fluido de 1 mB ou o químico de 1 mB. Não mude. */
        public K key() {
            return key;
        }

        public long amount() {
            return amount;
        }

        void setAmount(long amount) {
            this.amount = amount;
        }

        @Override
        public String toString() {
            return amount + " × " + key;
        }
    }

    /** As linhas de item, só para leitura. */
    public List<Line<ItemStack>> items() {
        return Collections.unmodifiableList(items);
    }

    /** As linhas de fluido, só para leitura. */
    public List<Line<FluidStack>> fluids() {
        return Collections.unmodifiableList(fluids);
    }

    /** As linhas de químico ({@code ChemicalStack} como chave), só para leitura. */
    public List<Line<Object>> chemicals() {
        return Collections.unmodifiableList(chemicals);
    }

    List<Line<ItemStack>> itemLines() {
        return items;
    }

    List<Line<FluidStack>> fluidLines() {
        return fluids;
    }

    List<Line<Object>> chemicalLines() {
        return chemicals;
    }

    public boolean isEmpty() {
        return items.isEmpty() && fluids.isEmpty() && chemicals.isEmpty();
    }

    /** Quantos itens o lote guarda, somadas as linhas. */
    public long itemCount() {
        long total = 0;
        for (Line<ItemStack> line : items) {
            total += line.amount;
        }
        return total;
    }

    /** Quantos {@code item} o lote guarda (de qualquer componente). */
    public long count(Item item) {
        long total = 0;
        for (Line<ItemStack> line : items) {
            if (line.key.is(item)) {
                total += line.amount;
            }
        }
        return total;
    }

    /** Quantos mB de {@code fluid} o lote guarda (de qualquer componente). */
    public long amount(Fluid fluid) {
        long total = 0;
        for (Line<FluidStack> line : fluids) {
            if (line.key.is(fluid)) {
                total += line.amount;
            }
        }
        return total;
    }

    /** Junta {@code count} itens iguais a {@code stack} (item e componentes); {@code stack} não é guardada. */
    public void addItem(ItemStack stack, long count) {
        if (stack.isEmpty() || count <= 0) {
            return;
        }
        for (int i = 0; i < items.size(); i++) {
            Line<ItemStack> line = items.get(i);
            if (ItemStack.isSameItemSameComponents(line.key, stack)) {
                line.amount += count;
                return;
            }
        }
        items.add(new Line<>(stack.copyWithCount(1), count));
    }

    /** Junta {@code amount} mB do fluido de {@code stack} (fluido e componentes). */
    public void addFluid(FluidStack stack, long amount) {
        if (stack.isEmpty() || amount <= 0) {
            return;
        }
        for (int i = 0; i < fluids.size(); i++) {
            Line<FluidStack> line = fluids.get(i);
            if (FluidStack.isSameFluidSameComponents(line.key, stack)) {
                line.amount += amount;
                return;
            }
        }
        fluids.add(new Line<>(stack.copyWithAmount(1), amount));
    }

    /** Junta {@code amount} mB do químico de {@code stack} ({@code ChemicalStack}). Só com o Mekanism. */
    public void addChemical(Object stack, long amount) {
        if (amount <= 0 || Chemicals.isEmptyStack(stack)) {
            return;
        }
        for (int i = 0; i < chemicals.size(); i++) {
            Line<Object> line = chemicals.get(i);
            if (Chemicals.sameChemical(line.key, stack)) {
                line.amount += amount;
                return;
            }
        }
        chemicals.add(new Line<>(Chemicals.copyWithAmount(stack, 1), amount));
    }

    /** Junta todas as linhas de {@code other} a este lote ({@code other} fica como estava). */
    void merge(DockPendingLot other) {
        for (Line<ItemStack> line : other.items) {
            addItem(line.key, line.amount);
        }
        for (Line<FluidStack> line : other.fluids) {
            addFluid(line.key, line.amount);
        }
        for (Line<Object> line : other.chemicals) {
            addChemical(line.key, line.amount);
        }
    }

    /** Tira as linhas que chegaram a 0. */
    void prune() {
        items.removeIf(line -> line.amount <= 0);
        fluids.removeIf(line -> line.amount <= 0);
        chemicals.removeIf(line -> line.amount <= 0);
    }

    /** Os químicos do lote em NBT, um por linha, com a quantidade inteira. */
    List<CompoundTag> chemicalTags(HolderLookup.Provider registries) {
        List<CompoundTag> tags = new ArrayList<>(chemicals.size());
        for (Line<Object> line : chemicals) {
            tags.add(Chemicals.saveStack(Chemicals.copyWithAmount(line.key, line.amount), registries));
        }
        return tags;
    }

    /**
     * O lote em NBT; {@code raw} são as entradas de químico cruas que vão junto (as que não deu para ler, guardadas
     * como vieram).
     */
    CompoundTag save(HolderLookup.Provider registries, List<CompoundTag> raw) {
        CompoundTag tag = new CompoundTag();
        if (!items.isEmpty()) {
            ListTag list = new ListTag();
            for (Line<ItemStack> line : items) {
                CompoundTag entry = new CompoundTag();
                entry.put("item", line.key.save(registries));
                entry.putLong("count", line.amount);
                list.add(entry);
            }
            tag.put("items", list);
        }
        if (!fluids.isEmpty()) {
            ListTag list = new ListTag();
            for (Line<FluidStack> line : fluids) {
                CompoundTag entry = new CompoundTag();
                entry.put("fluid", line.key.save(registries));
                entry.putLong("amount", line.amount);
                list.add(entry);
            }
            tag.put("fluids", list);
        }
        if (!chemicals.isEmpty() || !raw.isEmpty()) {
            ListTag list = new ListTag();
            list.addAll(chemicalTags(registries));
            for (CompoundTag entry : raw) {
                list.add(entry.copy());
            }
            tag.put("chemicals", list);
        }
        return tag;
    }

    /**
     * O lote salvo em {@code tag} (o {@link #save}). Uma entrada de químico que não dá para ler vai a {@code raw},
     * crua; uma de item ou fluido que não existe mais (o mod saiu) se perde, como numa pilha de baú.
     */
    static DockPendingLot load(CompoundTag tag, HolderLookup.Provider registries, Consumer<CompoundTag> raw) {
        DockPendingLot lot = new DockPendingLot();
        ListTag items = tag.getList("items", Tag.TAG_COMPOUND);
        for (int i = 0; i < items.size(); i++) {
            CompoundTag entry = items.getCompound(i);
            lot.addItem(ItemStack.parseOptional(registries, entry.getCompound("item")), entry.getLong("count"));
        }
        ListTag fluids = tag.getList("fluids", Tag.TAG_COMPOUND);
        for (int i = 0; i < fluids.size(); i++) {
            CompoundTag entry = fluids.getCompound(i);
            lot.addFluid(FluidStack.parseOptional(registries, entry.getCompound("fluid")), entry.getLong("amount"));
        }
        ListTag chemicals = tag.getList("chemicals", Tag.TAG_COMPOUND);
        for (int i = 0; i < chemicals.size(); i++) {
            lot.addChemicalTag(chemicals.getCompound(i), registries, raw);
        }
        return lot;
    }

    /** Lê um químico salvo e o junta; o que não dá para ler vai a {@code raw}, cru (uma entrada vazia é descartada). */
    void addChemicalTag(CompoundTag tag, HolderLookup.Provider registries, Consumer<CompoundTag> raw) {
        Object stack = Chemicals.parseStack(tag, registries);
        if (stack != null) {
            addChemical(stack, Chemicals.amount(stack));
        } else if (!tag.isEmpty()) {
            raw.accept(tag.copy());
        }
    }
}
