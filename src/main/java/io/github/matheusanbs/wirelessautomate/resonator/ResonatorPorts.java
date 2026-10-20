package io.github.matheusanbs.wirelessautomate.resonator;

import io.github.matheusanbs.wirelessautomate.storage.BulkEnergy;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.energy.IEnergyStorage;
import net.neoforged.neoforge.items.IItemHandler;
import org.jetbrains.annotations.Nullable;

/**
 * O índice do servidor porta → Núcleo: as Portas não têm block entity, e a capability delas acha o Núcleo
 * por aqui, sem busca no mundo. Preenchido pela {@link ResonatorFormation} ao montar (e na revalidação do
 * {@code onLoad} do Núcleo); só a thread do servidor mexe. As visões das capabilities ({@link CoreEnergy},
 * {@link CoreEnergyHandler}, {@link CoreItems}) seguram o Núcleo: o cache de capability de quem as pegou só é
 * invalidado quando a Porta muda de Núcleo ou a estrutura desmonta, e o Núcleo descarregado ou quebrado não avisa,
 * então cada visão confere {@link #alive} a cada chamada e fica inerte sem ele.
 */
public final class ResonatorPorts {
    private static final Map<ResourceKey<Level>, Map<BlockPos, BlockPos>> PORTS = new HashMap<>();

    private ResonatorPorts() {
    }

    /** Liga a Porta ao Núcleo; devolve se mudou alguma coisa (a Porta era de outro Núcleo ou não estava). */
    public static boolean register(ResourceKey<Level> dimension, BlockPos port, BlockPos core) {
        BlockPos old = PORTS.computeIfAbsent(dimension, key -> new HashMap<>()).put(port.immutable(), core.immutable());
        return !core.equals(old);
    }

    /** Tira a Porta só se ela ainda for deste Núcleo: outro Núcleo pode tê-la registrado depois. */
    public static void unregister(ResourceKey<Level> dimension, BlockPos port, BlockPos core) {
        Map<BlockPos, BlockPos> ports = PORTS.get(dimension);
        if (ports != null) {
            ports.remove(port, core);
        }
    }

    /** O Núcleo registrado para a Porta, carregado ou não, ou {@code null}; só leitura do índice. */
    public static @Nullable BlockPos registered(ResourceKey<Level> dimension, BlockPos port) {
        Map<BlockPos, BlockPos> ports = PORTS.get(dimension);
        return ports == null ? null : ports.get(port);
    }

    /** O Núcleo montado e carregado desta Porta, ou {@code null}. */
    public static @Nullable ResonanceCoreBlockEntity core(Level level, BlockPos port) {
        if (level.isClientSide) {
            return null;
        }
        Map<BlockPos, BlockPos> ports = PORTS.get(level.dimension());
        BlockPos core = ports == null ? null : ports.get(port);
        if (core == null || !level.isLoaded(core)) {
            return null;
        }
        return level.getBlockEntity(core) instanceof ResonanceCoreBlockEntity entity && entity.formed() ? entity : null;
    }

    /** Parada do servidor: o próximo mundo começa sem Portas. */
    public static void reset() {
        PORTS.clear();
    }

    /** O Núcleo ainda vale para uma visão guardada: no mundo e montado. */
    static boolean alive(ResonanceCoreBlockEntity core) {
        return !core.isRemoved() && core.formed();
    }

    /**
     * A Porta de Energia para o roteador do mod, em {@code long}: só tira. {@code port} é o índice dela entre as
     * Portas de Energia do Núcleo ({@link PortOrder}), ou -1 sem registro; o que sai de verdade soma na vazão.
     */
    public record CoreEnergy(ResonanceCoreBlockEntity core, int port) implements BulkEnergy {
        @Override
        public long insert(long amount, boolean simulate) {
            return 0;
        }

        @Override
        public long extract(long amount, boolean simulate) {
            if (amount <= 0 || !alive(core)) {
                return 0;
            }
            long taken = core.extractEnergy(amount, simulate);
            if (!simulate && taken > 0) {
                core.flow().add(port, taken);
            }
            return taken;
        }
    }

    /**
     * A Porta de Energia como {@link IEnergyStorage} comum: só tira. Como o {@code EnergyStoreHandler}, cada
     * chamada passa até {@link Integer#MAX_VALUE} FE, e o guardado e a capacidade aparecem cortados nesse teto.
     */
    public record CoreEnergyHandler(ResonanceCoreBlockEntity core, int port) implements IEnergyStorage {
        @Override
        public int receiveEnergy(int toReceive, boolean simulate) {
            return 0;
        }

        @Override
        public int extractEnergy(int toExtract, boolean simulate) {
            if (toExtract <= 0 || !alive(core)) {
                return 0;
            }
            int taken = (int) core.extractEnergy(toExtract, simulate);
            if (!simulate && taken > 0) {
                core.flow().add(port, taken);
            }
            return taken;
        }

        @Override
        public int getEnergyStored() {
            return alive(core) ? (int) Math.min(core.energy(), Integer.MAX_VALUE) : 0;
        }

        @Override
        public int getMaxEnergyStored() {
            return alive(core) ? (int) Math.min(core.capacity(), Integer.MAX_VALUE) : 0;
        }

        @Override
        public boolean canExtract() {
            return alive(core);
        }

        @Override
        public boolean canReceive() {
            return false;
        }
    }

    /**
     * A Porta de Cristais: um slot por câmara do tier e o último para o catalisador. Cristal não rachado (cheio,
     * gasto ou Opaco) entra uma unidade por câmara vazia; o catalisador, pela regra do {@link CoreCatalyst#canAdd},
     * até {@link ResonanceCoreBlockEntity#MAX_REDSTONE}. Sai o rachado, sempre (o Núcleo nunca o recarrega), e o
     * Opaco só com o Núcleo sem catalisador (com ele, o Opaco ainda vai recarregar); o catalisador nunca sai. Tudo
     * passa pelos métodos do Núcleo, que marcam a mudança.
     */
    public record CoreItems(ResonanceCoreBlockEntity core) implements IItemHandler {
        @Override
        public int getSlots() {
            return alive(core) ? core.chambers() + 1 : 0;
        }

        @Override
        public ItemStack getStackInSlot(int slot) {
            if (!alive(core) || slot < 0 || slot > core.chambers()) {
                return ItemStack.EMPTY;
            }
            if (slot == core.chambers()) {
                return core.catalyst().stack();
            }
            return core.crystals().get(slot).copy();
        }

        @Override
        public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
            if (stack.isEmpty() || !alive(core) || slot < 0 || slot > core.chambers() || !isItemValid(slot, stack)) {
                return stack;
            }
            if (slot == core.chambers()) {
                int room = ResonanceCoreBlockEntity.MAX_REDSTONE - core.redstone();
                int moved = simulate ? Math.max(0, Math.min(stack.getCount(), room)) : core.addCatalyst(stack);
                if (moved == 0) {
                    return stack;
                }
                return stack.copyWithCount(stack.getCount() - moved);
            }
            if (!core.crystals().get(slot).isEmpty()) {
                return stack;
            }
            if (!simulate) {
                core.insertCrystalAt(slot, stack);
            }
            return stack.copyWithCount(stack.getCount() - 1);
        }

        @Override
        public ItemStack extractItem(int slot, int amount, boolean simulate) {
            if (amount <= 0 || !alive(core) || slot < 0 || slot >= core.chambers()) {
                return ItemStack.EMPTY;
            }
            ItemStack stack = core.crystals().get(slot);
            if (!SignalCrystalItem.isCracked(stack) && (!SignalCrystalItem.isDull(stack) || core.redstone() > 0)) {
                return ItemStack.EMPTY;
            }
            return simulate ? stack.copy() : core.takeCrystalAt(slot);
        }

        @Override
        public int getSlotLimit(int slot) {
            return alive(core) && slot == core.chambers() ? ResonanceCoreBlockEntity.MAX_REDSTONE : 1;
        }

        @Override
        public boolean isItemValid(int slot, ItemStack stack) {
            if (!alive(core) || slot < 0 || slot > core.chambers()) {
                return false;
            }
            if (slot == core.chambers()) {
                return core.catalyst().canAdd(core.tier(), stack);
            }
            return stack.getItem() instanceof SignalCrystalItem && !SignalCrystalItem.isCracked(stack);
        }
    }
}
