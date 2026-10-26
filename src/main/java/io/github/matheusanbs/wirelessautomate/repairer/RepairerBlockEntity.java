package io.github.matheusanbs.wirelessautomate.repairer;

import io.github.matheusanbs.wirelessautomate.Config;
import io.github.matheusanbs.wirelessautomate.block.RouterTier;
import io.github.matheusanbs.wirelessautomate.registry.ModBlockEntities;
import io.github.matheusanbs.wirelessautomate.registry.ModDataComponents;
import io.github.matheusanbs.wirelessautomate.storage.ScalarStore;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * O Reparador de Cristais: os 17 slots ({@link RepairerSlots}), o NBT e o motor de 8 linhas em paralelo. Não faz tick
 * próprio: o {@link RepairerManager} o anda só enquanto uma linha trabalha, e os eventos o acordam por {@link #wake()}.
 */
public class RepairerBlockEntity extends BlockEntity {
    private final NonNullList<ItemStack> items = NonNullList.withSize(RepairerSlots.TOTAL, ItemStack.EMPTY);
    private final RepairerContainer container = new RepairerContainer(this);
    private final ScalarStore energy = new ScalarStore(this::markUnsaved, RepairerBlockEntity::bufferCapacity);
    private final RepairerItems itemView = new RepairerItems(this);
    private final RepairerEnergy energyView = new RepairerEnergy(this, energy);
    private final RepairerBulkEnergy bulkEnergyView = new RepairerBulkEnergy(this, energy);
    /** O tick de jogo em que cada linha termina; 0 é parada. */
    private final long[] end = new long[RepairMath.LINES];
    /** A linha terminou e não achou saída livre: espera uma abrir. */
    private final boolean[] blocked = new boolean[RepairMath.LINES];
    /** Os tempos que faltavam, lidos do NBT e convertidos em tempo de jogo no {@code onLoad}. */
    private int[] loadedLines;

    public RepairerBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.CRYSTAL_REPAIRER.get(), pos, state);
    }

    public NonNullList<ItemStack> items() {
        return items;
    }

    public RepairerContainer container() {
        return container;
    }

    public ScalarStore energy() {
        return energy;
    }

    public RepairerItems itemView() {
        return itemView;
    }

    public RepairerEnergy energyView() {
        return energyView;
    }

    public RepairerBulkEnergy bulkEnergyView() {
        return bulkEnergyView;
    }

    /** O reparo mais caro (o Afinado Ultimate) vezes as oito linhas, pela config. */
    private static long bufferCapacity() {
        long highest = RepairMath.cost(Config.resonatorFePerCrystal(RouterTier.ULTIMATE), Config.repairCostFactor());
        return RepairMath.bufferCapacity(highest, RepairMath.LINES);
    }

    /** A energia mudou: marca o chunk para gravar, sem avisar os vizinhos. */
    private void markUnsaved() {
        if (level != null) {
            level.blockEntityChanged(worldPosition);
        }
    }

    public long lineEnd(int line) {
        return end[line];
    }

    public boolean lineBlocked(int line) {
        return blocked[line];
    }

    /** A linha está andando: tem fim marcado e não está travada. */
    public boolean running(int line) {
        return end[line] > 0 && !blocked[line];
    }

    public boolean anyRunning() {
        for (int i = 0; i < RepairMath.LINES; i++) {
            if (running(i)) {
                return true;
            }
        }
        return false;
    }

    /** Chamado depois de uma entrada de item ou de energia, ou de uma saída que esvazia: roda o motor agora. */
    public void wake() {
        if (level != null && !level.isClientSide) {
            tick(level.getGameTime());
        }
    }

    /** O conteúdo mudou: marca o chunk, acorda o motor e avisa os vizinhos (os roteadores dormem por vazio e por cheio). */
    public void itemsChanged() {
        setChanged();
        wake();
        notifyNeighbors();
    }

    private void notifyNeighbors() {
        if (level != null && !level.isClientSide) {
            level.updateNeighborsAt(worldPosition, getBlockState().getBlock());
        }
    }

    /**
     * Termina as linhas que venceram (ou tenta de novo as travadas), começa as que já podem e entra ou sai do gerenciador.
     * Chamado pelo {@link RepairerManager} a cada tick enquanto há linha trabalhando e pelos eventos pelo {@link #wake()}.
     */
    public void tick(long now) {
        boolean changed = false;
        for (int i = 0; i < RepairMath.LINES; i++) {
            if (end[i] > 0 && (blocked[i] || now >= end[i])) {
                changed |= finish(i);
            }
        }
        changed |= startLines(now);
        if (changed) {
            setChanged();
            notifyNeighbors();
        }
        if (anyRunning()) {
            RepairerManager.add(this);
        } else {
            RepairerManager.remove(this);
        }
    }

    /** Move o cristal reparado (cheio, sem rachadura) para a primeira saída livre; sem saída, trava a linha. */
    private boolean finish(int line) {
        if (!RepairerSlots.acceptsCracked(items.get(line))) {
            end[line] = 0;
            blocked[line] = false;
            return true;
        }
        int out = -1;
        for (int i = RepairerSlots.OUTPUT_FIRST; i < RepairerSlots.TOTAL; i++) {
            if (items.get(i).isEmpty()) {
                out = i;
                break;
            }
        }
        if (out < 0) {
            blocked[line] = true;
            return false;
        }
        ItemStack fixed = items.get(line).copyWithCount(1);
        fixed.remove(ModDataComponents.CRYSTAL_CHARGE.get());
        fixed.remove(ModDataComponents.CRYSTAL_INTEGRITY.get());
        items.set(out, fixed);
        ItemStack input = items.get(line);
        input.shrink(1);
        if (input.isEmpty()) {
            items.set(line, ItemStack.EMPTY);
        }
        end[line] = 0;
        blocked[line] = false;
        return true;
    }

    /** Começa cada linha parada com um rachado que tem cristais e energia; relê o estoque e o buffer a cada linha. */
    private boolean startLines(long now) {
        boolean started = false;
        int recast = Config.recastCrystals();
        for (int i = 0; i < RepairMath.LINES; i++) {
            if (end[i] != 0) {
                continue;
            }
            ItemStack input = items.get(i);
            ItemStack feed = items.get(RepairerSlots.FEED);
            if (!RepairerSlots.acceptsCracked(input) || feed.getCount() < recast) {
                continue;
            }
            long cost = RepairerCosts.of(input);
            if (energy.stored() < cost) {
                continue;
            }
            feed.shrink(recast);
            if (feed.isEmpty()) {
                items.set(RepairerSlots.FEED, ItemStack.EMPTY);
            }
            energy.extract(cost, false);
            end[i] = now + Config.repairTicks();
            started = true;
        }
        return started;
    }

    @Override
    public void onLoad() {
        super.onLoad();
        if (level != null && !level.isClientSide) {
            long now = level.getGameTime();
            if (loadedLines != null) {
                for (int i = 0; i < RepairMath.LINES; i++) {
                    int left = loadedLines[i];
                    end[i] = left == 0 ? 0 : left < 0 ? now : now + left;
                    blocked[i] = left < 0;
                }
                loadedLines = null;
            }
            wake();
        }
    }

    @Override
    public void setRemoved() {
        RepairerManager.remove(this);
        super.setRemoved();
    }

    @Override
    public void onChunkUnloaded() {
        RepairerManager.remove(this);
        super.onChunkUnloaded();
    }

    /** Grava só os slots ocupados, cada um com o índice em {@code slot}. */
    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        ListTag list = new ListTag();
        for (int i = 0; i < items.size(); i++) {
            ItemStack stack = items.get(i);
            if (!stack.isEmpty()) {
                CompoundTag entry = new CompoundTag();
                entry.putByte("slot", (byte) i);
                list.add(stack.save(registries, entry));
            }
        }
        tag.put("items", list);
        tag.putLong("energy", energy.stored());
        long now = level == null ? 0 : level.getGameTime();
        int[] left = new int[RepairMath.LINES];
        for (int i = 0; i < left.length; i++) {
            left[i] = end[i] <= 0 ? 0 : blocked[i] ? -1 : (int) Math.max(1, Math.min(Integer.MAX_VALUE, end[i] - now));
        }
        tag.putIntArray("lines", left);
    }

    /** Esvazia os slots e põe os gravados; um índice fora da lista é ignorado. */
    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        items.clear();
        int[] lines = tag.getIntArray("lines");
        loadedLines = lines.length == RepairMath.LINES ? lines : null;
        energy.set(Math.max(0, tag.getLong("energy")));
        ListTag list = tag.getList("items", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            CompoundTag entry = list.getCompound(i);
            int slot = entry.getByte("slot") & 0xFF;
            if (slot < items.size()) {
                items.set(slot, ItemStack.parse(registries, entry).orElse(ItemStack.EMPTY));
            }
        }
    }
}
