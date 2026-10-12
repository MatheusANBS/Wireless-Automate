package io.github.matheusanbs.wirelessautomate.range;

import io.github.matheusanbs.wirelessautomate.block.RouterBlockEntity;
import io.github.matheusanbs.wirelessautomate.network.Chemicals;
import io.github.matheusanbs.wirelessautomate.network.ResourceType;
import io.github.matheusanbs.wirelessautomate.network.Sources;
import io.github.matheusanbs.wirelessautomate.storage.BulkEnergy;
import io.github.matheusanbs.wirelessautomate.storage.BulkFluids;
import io.github.matheusanbs.wirelessautomate.storage.BulkItems;
import io.github.matheusanbs.wirelessautomate.storage.BulkSource;
import java.util.Arrays;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.neoforge.capabilities.BlockCapability;
import net.neoforged.neoforge.capabilities.BlockCapabilityCache;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.energy.IEnergyStorage;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.items.IItemHandler;
import org.jetbrains.annotations.Nullable;

/**
 * Os {@link BlockCapabilityCache} de uma máquina de um roteador, um por capability e por face absoluta
 * da máquina, criados na primeira consulta. A máquina presa tem uma instância só, que é apontada de
 * novo ({@link #retarget}) quando o roteador gira; cada remota tem a sua, guardada pelo roteador e
 * descartada ({@link #discard}) ao desvincular.
 *
 * <p>Numa remota, toda consulta confere antes {@code level.isLoaded(pos)}: o cache de capability lê o
 * bloco quando está inválido, e ler um chunk descarregado o carrega à força. A presa está colada no
 * roteador e não precisa disso.
 *
 * <p>Cada cache avisa a própria invalidação ao roteador ({@link RouterBlockEntity#machineInvalidated})
 * com esta instância, o tipo e a face. Um cache de antes de um {@link #retarget} ou {@link #discard}
 * deixa de valer e não avisa mais.
 */
public final class MachineCaches {
    private static final int FACES = Direction.values().length;

    private final RouterBlockEntity router;
    private final boolean remote;
    private BlockPos pos;
    /** Muda a cada {@link #retarget}/{@link #discard}: os caches criados antes deixam de valer. */
    private int generation;
    private boolean discarded;

    private final BlockCapabilityCache<IItemHandler, @Nullable Direction>[] itemCaches = newCaches();
    private final BlockCapabilityCache<BulkItems, @Nullable Direction>[] bulkItemCaches = newCaches();
    private final BlockCapabilityCache<BulkEnergy, @Nullable Direction>[] bulkEnergyCaches = newCaches();
    private final BlockCapabilityCache<BulkFluids, @Nullable Direction>[] bulkFluidCaches = newCaches();
    private final BlockCapabilityCache<BulkSource, @Nullable Direction>[] bulkSourceCaches = newCaches();
    private final BlockCapabilityCache<IFluidHandler, @Nullable Direction>[] fluidCaches = newCaches();
    private final BlockCapabilityCache<IEnergyStorage, @Nullable Direction>[] energyCaches = newCaches();
    /** Químicos do Mekanism; o handler fica como {@code Object} para esta classe não depender dele. */
    private final BlockCapabilityCache<Object, @Nullable Direction>[] chemicalCaches = newCaches();
    /** Source do Ars Nouveau; o handler fica como {@code Object} para esta classe não depender dele. */
    private final BlockCapabilityCache<Object, @Nullable Direction>[] sourceCaches = newCaches();

    private MachineCaches(RouterBlockEntity router, BlockPos pos, boolean remote) {
        this.router = router;
        this.pos = pos.immutable();
        this.remote = remote;
    }

    /** A máquina presa: sem {@code isLoaded}, apontada de novo quando o roteador gira. */
    public static MachineCaches attached(RouterBlockEntity router, BlockPos pos) {
        return new MachineCaches(router, pos, false);
    }

    /** Uma máquina remota vinculada: toda consulta confere o chunk antes. */
    public static MachineCaches remote(RouterBlockEntity router, BlockPos pos) {
        return new MachineCaches(router, pos, true);
    }

    public BlockPos pos() {
        return pos;
    }

    public boolean isRemote() {
        return remote;
    }

    /**
     * A máquina mudou de lugar (o roteador girou): esquece os caches, que deixam de valer e de avisar,
     * e passa a consultar {@code newPos}. Só para a presa.
     */
    public void retarget(BlockPos newPos) {
        clear();
        pos = newPos.immutable();
    }

    /** A máquina saiu (desvinculada): esquece os caches e não consulta mais nada. */
    public void discard() {
        clear();
        discarded = true;
    }

    private void clear() {
        generation++;
        Arrays.fill(itemCaches, null);
        Arrays.fill(bulkItemCaches, null);
        Arrays.fill(bulkEnergyCaches, null);
        Arrays.fill(bulkFluidCaches, null);
        Arrays.fill(bulkSourceCaches, null);
        Arrays.fill(fluidCaches, null);
        Arrays.fill(energyCaches, null);
        Arrays.fill(chemicalCaches, null);
        Arrays.fill(sourceCaches, null);
    }

    /** Inventário da máquina pela face {@code machineFace}. */
    public @Nullable IItemHandler items(Direction machineFace) {
        return capability(itemCaches, Capabilities.ItemHandler.BLOCK, ResourceType.ITEM, machineFace);
    }

    /** Itens por tipo e quantidade (o Baú do mod) pela face, ou {@code null}. */
    public @Nullable BulkItems bulkItems(Direction machineFace) {
        return capability(bulkItemCaches, BulkItems.BLOCK, ResourceType.ITEM, machineFace);
    }

    /** Fluidos em {@code long} (o Tanque do mod) pela face, ou {@code null}. */
    public @Nullable BulkFluids bulkFluids(Direction machineFace) {
        return capability(bulkFluidCaches, BulkFluids.BLOCK, ResourceType.FLUID, machineFace);
    }

    public @Nullable IFluidHandler fluids(Direction machineFace) {
        return capability(fluidCaches, Capabilities.FluidHandler.BLOCK, ResourceType.FLUID, machineFace);
    }

    /** Energia em {@code long} (a Bateria do mod) pela face, ou {@code null}. */
    public @Nullable BulkEnergy bulkEnergy(Direction machineFace) {
        return capability(bulkEnergyCaches, BulkEnergy.BLOCK, ResourceType.ENERGY, machineFace);
    }

    public @Nullable IEnergyStorage energy(Direction machineFace) {
        return capability(energyCaches, Capabilities.EnergyStorage.BLOCK, ResourceType.ENERGY, machineFace);
    }

    /** Handler de químicos do Mekanism como {@code Object}, ou {@code null} sem ele ou sem o Mekanism. */
    @SuppressWarnings("unchecked")
    public @Nullable Object chemicals(Direction machineFace) {
        BlockCapability<?, @Nullable Direction> capability = Chemicals.capability();
        return capability == null ? null
                : capability(chemicalCaches, (BlockCapability<Object, @Nullable Direction>) capability,
                        ResourceType.CHEMICAL, machineFace);
    }

    /** Source em {@code long} (o Tanque de Source do mod) pela face, ou {@code null}. */
    public @Nullable BulkSource bulkSource(Direction machineFace) {
        return capability(bulkSourceCaches, BulkSource.BLOCK, ResourceType.SOURCE, machineFace);
    }

    /** Source do Ars Nouveau ({@code ISourceCap}) como {@code Object}, ou {@code null} sem ela ou sem o Ars. */
    @SuppressWarnings("unchecked")
    public @Nullable Object arsSource(Direction machineFace) {
        BlockCapability<?, @Nullable Direction> capability = Sources.capability();
        return capability == null ? null
                : capability(sourceCaches, (BlockCapability<Object, @Nullable Direction>) capability,
                        ResourceType.SOURCE, machineFace);
    }

    /** A capability pelo cache da face, criando o cache na primeira consulta. Só no servidor. */
    private <T> @Nullable T capability(BlockCapabilityCache<T, @Nullable Direction>[] caches,
            BlockCapability<T, @Nullable Direction> capability, ResourceType type, Direction machineFace) {
        if (!(router.getLevel() instanceof ServerLevel serverLevel) || router.isRemoved() || discarded) {
            return null;
        }
        // Antes de tocar no cache: um cache inválido lê o bloco, e isso carregaria o chunk.
        if (remote && !serverLevel.isLoaded(pos)) {
            return null;
        }
        BlockCapabilityCache<T, @Nullable Direction> cache = caches[machineFace.ordinal()];
        if (cache == null) {
            if (!remote) {
                // A presa segue o facing: lido só ao criar um cache (girar já chama o retarget).
                BlockPos machine = router.machinePos();
                if (!machine.equals(pos)) {
                    retarget(machine);
                }
            }
            int created = generation;
            cache = BlockCapabilityCache.create(capability, serverLevel, pos, machineFace,
                    () -> !router.isRemoved() && generation == created,
                    () -> router.machineInvalidated(this, type, machineFace));
            caches[machineFace.ordinal()] = cache;
        }
        return cache.getCapability();
    }

    @SuppressWarnings("unchecked")
    private static <T> BlockCapabilityCache<T, @Nullable Direction>[] newCaches() {
        return (BlockCapabilityCache<T, @Nullable Direction>[]) new BlockCapabilityCache[FACES];
    }
}
