package io.github.matheusanbs.wirelessautomate.dock;

import io.github.matheusanbs.wirelessautomate.range.MachineConfig;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.fluids.FluidStack;
import org.jetbrains.annotations.Nullable;

/**
 * Um lado da Doca de Padrões: o bloco das máquinas (todas do mesmo bloco), o perfil das faces (o {@link MachineConfig}
 * de sempre, que vale para todas pela frente de cada uma), as máquinas, o cursor da próxima candidata e o pendente (o
 * que sobrou de um pedido que não coube inteiro, com a posição da máquina que o recebeu).
 *
 * <p>Nasce no primeiro vínculo do lado. Mude as máquinas pelo {@link PatternDockBlockEntity}, que mantém o índice das
 * remotas. NBT: {@code side} (byte), {@code block} (id, só com bloco), {@code profile}, {@code machines} (lista de
 * {@link DockMachine}), {@code cursor}, {@code pending} (lista de {@code {item}} ou {@code {fluid}}) e
 * {@code pending_at} (long, só com pendente).
 */
public final class DockSide {
    /** O teto de {@code count} no codec do {@code ItemStack}: um pendente maior é salvo em partes. */
    private static final int SAVED_STACK_MAX = 99;

    private final int side;
    private @Nullable ResourceLocation block;
    private final MachineConfig profile;
    private final List<DockMachine> machines = new ArrayList<>();
    private final List<DockMachine> machinesView = Collections.unmodifiableList(machines);
    private int cursor;
    private final List<ItemStack> pendingItems = new ArrayList<>();
    private final List<FluidStack> pendingFluids = new ArrayList<>();
    private long pendingAt;

    public DockSide(int side) {
        this(side, new MachineConfig());
    }

    private DockSide(int side, MachineConfig profile) {
        this.side = side;
        this.profile = profile;
    }

    /** O lado ({@code Direction.ordinal()}). */
    public int side() {
        return side;
    }

    /** O bloco das máquinas deste lado, ou {@code null} antes do primeiro vínculo. */
    public @Nullable ResourceLocation block() {
        return block;
    }

    void assignBlock(@Nullable ResourceLocation block) {
        this.block = block;
    }

    /** O perfil das faces; mudar o devolvido muda o lado (quem muda salva a Doca). */
    public MachineConfig profile() {
        return profile;
    }

    /** As máquinas na ordem de vínculo, só para leitura. */
    public List<DockMachine> machines() {
        return machinesView;
    }

    void addMachine(DockMachine machine) {
        machines.add(machine);
    }

    @Nullable DockMachine removeMachine(BlockPos pos) {
        long key = pos.asLong();
        for (int i = 0; i < machines.size(); i++) {
            if (machines.get(i).pos().asLong() == key) {
                DockMachine removed = machines.remove(i);
                if (cursor > i) {
                    cursor--;
                }
                if (cursor >= machines.size()) {
                    cursor = 0;
                }
                return removed;
            }
        }
        return null;
    }

    /** Índice da próxima candidata na lista de máquinas. */
    public int cursor() {
        return cursor;
    }

    public void setCursor(int cursor) {
        this.cursor = cursor;
    }

    /** Os itens pendentes; vazio sem pendente. */
    public List<ItemStack> pendingItems() {
        return pendingItems;
    }

    /** Os fluidos pendentes; vazio sem pendente. */
    public List<FluidStack> pendingFluids() {
        return pendingFluids;
    }

    public boolean hasPending() {
        return !pendingItems.isEmpty() || !pendingFluids.isEmpty();
    }

    /** A posição da máquina que recebeu o pedido pendente; só vale com {@link #hasPending}. */
    public BlockPos pendingAt() {
        return BlockPos.of(pendingAt);
    }

    /** Guarda o pendente de um pedido entregue a {@code at}; listas vazias limpam. Os stacks passam a ser do lado. */
    public void setPending(List<ItemStack> items, List<FluidStack> fluids, BlockPos at) {
        pendingItems.clear();
        pendingFluids.clear();
        for (ItemStack stack : items) {
            if (!stack.isEmpty()) {
                pendingItems.add(stack);
            }
        }
        for (FluidStack stack : fluids) {
            if (!stack.isEmpty()) {
                pendingFluids.add(stack);
            }
        }
        pendingAt = at.asLong();
    }

    public void clearPending() {
        pendingItems.clear();
        pendingFluids.clear();
    }

    /** Sem bloco, sem máquinas e sem pendente: o lado pode deixar de existir. */
    public boolean isUnused() {
        return block == null && machines.isEmpty() && !hasPending();
    }

    CompoundTag save(HolderLookup.Provider registries) {
        CompoundTag tag = new CompoundTag();
        tag.putByte("side", (byte) side);
        if (block != null) {
            tag.putString("block", block.toString());
        }
        tag.put("profile", profile.save(registries));
        ListTag list = new ListTag();
        for (DockMachine machine : machines) {
            list.add(machine.save());
        }
        tag.put("machines", list);
        tag.putInt("cursor", cursor);
        if (hasPending()) {
            ListTag pending = new ListTag();
            for (ItemStack stack : pendingItems) {
                savePendingItem(pending, stack, registries);
            }
            for (FluidStack stack : pendingFluids) {
                CompoundTag entry = new CompoundTag();
                entry.put("fluid", stack.save(registries));
                pending.add(entry);
            }
            tag.put("pending", pending);
            tag.putLong("pending_at", pendingAt);
        }
        return tag;
    }

    /** Um item pendente, em partes de até {@link #SAVED_STACK_MAX} (o codec não aceita mais que isso num stack). */
    private static void savePendingItem(ListTag pending, ItemStack stack, HolderLookup.Provider registries) {
        int left = stack.getCount();
        while (left > 0) {
            int part = Math.min(left, SAVED_STACK_MAX);
            CompoundTag entry = new CompoundTag();
            entry.put("item", stack.copyWithCount(part).save(registries));
            pending.add(entry);
            left -= part;
        }
    }

    /** O lado salvo em {@code tag}, ou {@code null} com um lado fora de 0 a 5. */
    static @Nullable DockSide load(CompoundTag tag, HolderLookup.Provider registries) {
        int side = tag.getByte("side");
        if (side < 0 || side > 5) {
            return null;
        }
        DockSide loaded = new DockSide(side, MachineConfig.load(tag.getCompound("profile"), registries));
        loaded.block = tag.contains("block", Tag.TAG_STRING) ? ResourceLocation.tryParse(tag.getString("block")) : null;
        ListTag list = tag.getList("machines", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            loaded.machines.add(DockMachine.load(side, list.getCompound(i)));
        }
        int cursor = tag.getInt("cursor");
        loaded.cursor = cursor >= 0 && cursor < loaded.machines.size() ? cursor : 0;
        ListTag pending = tag.getList("pending", Tag.TAG_COMPOUND);
        for (int i = 0; i < pending.size(); i++) {
            CompoundTag entry = pending.getCompound(i);
            if (entry.contains("item", Tag.TAG_COMPOUND)) {
                ItemStack stack = ItemStack.parseOptional(registries, entry.getCompound("item"));
                if (!stack.isEmpty()) {
                    loaded.pendingItems.add(stack);
                }
            } else if (entry.contains("fluid", Tag.TAG_COMPOUND)) {
                FluidStack stack = FluidStack.parseOptional(registries, entry.getCompound("fluid"));
                if (!stack.isEmpty()) {
                    loaded.pendingFluids.add(stack);
                }
            }
        }
        loaded.pendingAt = tag.getLong("pending_at");
        return loaded;
    }
}
