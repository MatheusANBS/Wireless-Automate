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
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.TickTask;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.neoforge.capabilities.BlockCapability;
import net.neoforged.neoforge.capabilities.BlockCapabilityCache;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.energy.IEnergyStorage;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.items.IItemHandler;
import org.jetbrains.annotations.Nullable;

/**
 * Os {@link BlockCapabilityCache} de uma máquina de um roteador (ou de outro {@link CacheOwner}), um por capability e
 * por face absoluta
 * da máquina, criados na primeira consulta. O arranjo das seis faces de cada capability também só nasce na
 * primeira consulta dela: uma máquina com faces só de itens paga os dois arranjos de itens, e não os nove.
 * A máquina presa tem uma instância só, que é apontada de novo ({@link #retarget}) quando o roteador gira;
 * cada remota tem a sua, guardada pelo roteador e descartada ({@link #discard}) ao desvincular.
 *
 * <p>Numa remota, toda consulta confere antes {@code level.isLoaded(pos)}: o cache de capability lê o
 * bloco quando está inválido, e ler um chunk descarregado o carrega à força. A presa está colada no
 * roteador e não precisa disso. A resposta vale pelo resto do tick do servidor ({@link #loadedTick}): uma
 * visita consulta a mesma remota várias vezes (o Baú do mod e o inventário, slot a slot), e o
 * {@code isLoaded} é uma busca no mapa de chunks por consulta. Um chunk só descarrega no tick do
 * {@code ChunkMap}, e a contagem de descargas ({@link RemoteIndex#chunkEpoch}) invalida a resposta guardada
 * se alguma acontecer no meio do tick.
 *
 * <p>Cada cache avisa a própria invalidação ao dono ({@link CacheOwner#machineInvalidated})
 * com esta instância, o tipo e a face. Um cache de antes de um {@link #retarget} ou {@link #discard}
 * deixa de valer e não avisa mais.
 */
public final class MachineCaches {
    private static final int FACES = Direction.values().length;

    private final CacheOwner owner;
    /** O roteador da máquina presa, que segue o facing dele; nulo numa remota. */
    private final @Nullable RouterBlockEntity attachedTo;
    private final boolean remote;
    private BlockPos pos;
    /** Muda a cada {@link #retarget}/{@link #discard}: os caches criados antes deixam de valer. */
    private int generation;
    private boolean discarded;
    /** Um {@link #watchSoon} na fila do servidor, para não enfileirar outro no mesmo aviso. */
    private boolean watchQueued;
    /** O tick do servidor e a contagem de descargas em que {@link #loaded} foi lido; só numa remota. */
    private long loadedTick = Long.MIN_VALUE;
    private long loadedEpoch;
    /** O chunk da remota estava carregado na leitura de {@link #loadedTick}. */
    private boolean loaded;

    // Um arranjo por capability, [face absoluta], nulo até a primeira consulta daquela capability.
    private BlockCapabilityCache<IItemHandler, @Nullable Direction> @Nullable [] itemCaches;
    private BlockCapabilityCache<BulkItems, @Nullable Direction> @Nullable [] bulkItemCaches;
    private BlockCapabilityCache<BulkEnergy, @Nullable Direction> @Nullable [] bulkEnergyCaches;
    private BlockCapabilityCache<BulkFluids, @Nullable Direction> @Nullable [] bulkFluidCaches;
    private BlockCapabilityCache<BulkSource, @Nullable Direction> @Nullable [] bulkSourceCaches;
    private BlockCapabilityCache<IFluidHandler, @Nullable Direction> @Nullable [] fluidCaches;
    private BlockCapabilityCache<IEnergyStorage, @Nullable Direction> @Nullable [] energyCaches;
    /** Químicos do Mekanism; o handler fica como {@code Object} para esta classe não depender dele. */
    private BlockCapabilityCache<Object, @Nullable Direction> @Nullable [] chemicalCaches;
    /** Source do Ars Nouveau; o handler fica como {@code Object} para esta classe não depender dele. */
    private BlockCapabilityCache<Object, @Nullable Direction> @Nullable [] sourceCaches;

    private MachineCaches(CacheOwner owner, @Nullable RouterBlockEntity attachedTo, BlockPos pos, boolean remote) {
        this.owner = owner;
        this.attachedTo = attachedTo;
        this.pos = pos.immutable();
        this.remote = remote;
    }

    /** A máquina presa: sem {@code isLoaded}, apontada de novo quando o roteador gira. */
    public static MachineCaches attached(RouterBlockEntity router, BlockPos pos) {
        return new MachineCaches(router, router, pos, false);
    }

    /** Uma máquina remota vinculada a um roteador: toda consulta confere o chunk antes. */
    public static MachineCaches remote(RouterBlockEntity router, BlockPos pos) {
        return new MachineCaches(router, null, pos, true);
    }

    /** Uma máquina remota de um dono que não é roteador: toda consulta confere o chunk antes. */
    public static MachineCaches remote(CacheOwner owner, BlockPos pos) {
        return new MachineCaches(owner, null, pos, true);
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
        generation++;
        // Esvazia os arranjos sem trocá-los: o retarget pode vir de dentro do capability, que já tem o arranjo na mão.
        empty(itemCaches);
        empty(bulkItemCaches);
        empty(bulkEnergyCaches);
        empty(bulkFluidCaches);
        empty(bulkSourceCaches);
        empty(fluidCaches);
        empty(energyCaches);
        empty(chemicalCaches);
        empty(sourceCaches);
        pos = newPos.immutable();
    }

    /** A máquina saiu (desvinculada): esquece os caches e não consulta mais nada. */
    public void discard() {
        generation++;
        itemCaches = null;
        bulkItemCaches = null;
        bulkEnergyCaches = null;
        bulkFluidCaches = null;
        bulkSourceCaches = null;
        fluidCaches = null;
        energyCaches = null;
        chemicalCaches = null;
        sourceCaches = null;
        discarded = true;
    }

    /**
     * Garante que a troca do bloco desta remota avise o dono antes da primeira consulta do motor e mesmo sem porta
     * (a trocada, que a montagem pula): consulta o cache de itens da face de cima, criado na primeira vez, cujo listener
     * chega no {@link CacheOwner#machineInvalidated} quando a block entity sai ou entra. Depois de cada aviso o
     * listener só volta a valer com uma consulta, e é esta, na montagem seguinte. Só na montagem.
     */
    public void watch() {
        items(Direction.UP);
    }

    /**
     * O {@link #watch} de dentro de um listener de invalidação: lá o {@code BlockCapabilityCache} não aceita consulta
     * ({@code IllegalStateException}, que derruba o servidor), então ela vai para a fila do servidor e roda depois da
     * troca do bloco, se os caches ainda valem.
     */
    public void watchSoon() {
        if (watchQueued || discarded || !(owner.ownerLevel() instanceof ServerLevel level)) {
            return;
        }
        watchQueued = true;
        int queued = generation;
        MinecraftServer server = level.getServer();
        server.tell(new TickTask(server.getTickCount(), () -> {
            watchQueued = false;
            if (generation == queued) {
                watch();
            }
        }));
    }

    /** Para GameTests: quantas capabilities já têm o arranjo das faces criado. */
    public int capabilityArraysForTests() {
        Object[][] all = {itemCaches, bulkItemCaches, bulkEnergyCaches, bulkFluidCaches, bulkSourceCaches, fluidCaches,
                energyCaches, chemicalCaches, sourceCaches};
        int count = 0;
        for (Object[] caches : all) {
            if (caches != null) {
                count++;
            }
        }
        return count;
    }

    private static void empty(Object @Nullable [] caches) {
        if (caches != null) {
            Arrays.fill(caches, null);
        }
    }

    /**
     * O nível para consultar agora, ou {@code null}: só no servidor, com o dono e a máquina valendo e,
     * numa remota, com o chunk dela carregado.
     */
    private @Nullable ServerLevel queryLevel() {
        if (!(owner.ownerLevel() instanceof ServerLevel serverLevel) || owner.ownerRemoved() || discarded) {
            return null;
        }
        // Antes de tocar no cache: um cache inválido lê o bloco, e isso carregaria o chunk.
        if (remote && !isLoaded(serverLevel)) {
            return null;
        }
        return serverLevel;
    }

    /** {@code level.isLoaded(pos)} lido uma vez por tick do servidor (ver a classe). */
    private boolean isLoaded(ServerLevel serverLevel) {
        long tick = serverLevel.getServer().getTickCount();
        long epoch = RemoteIndex.chunkEpoch();
        if (tick != loadedTick || epoch != loadedEpoch) {
            loaded = serverLevel.isLoaded(pos);
            loadedTick = tick;
            loadedEpoch = epoch;
        }
        return loaded;
    }

    /**
     * Pode consultar agora: o dono e a máquina valem e, numa remota, o chunk dela está carregado (a mesma resposta do
     * tick, sem uma busca a mais). Para quem escolhe entre várias remotas pular a descarregada antes de tentar.
     */
    public boolean available() {
        return queryLevel() != null;
    }

    /** Inventário da máquina pela face {@code machineFace}. */
    public @Nullable IItemHandler items(Direction machineFace) {
        ServerLevel level = queryLevel();
        if (level == null) {
            return null;
        }
        if (itemCaches == null) {
            itemCaches = newCaches();
        }
        return capability(level, itemCaches, Capabilities.ItemHandler.BLOCK, ResourceType.ITEM, machineFace);
    }

    /** Itens por tipo e quantidade (o Baú do mod) pela face, ou {@code null}. */
    public @Nullable BulkItems bulkItems(Direction machineFace) {
        ServerLevel level = queryLevel();
        if (level == null) {
            return null;
        }
        if (bulkItemCaches == null) {
            bulkItemCaches = newCaches();
        }
        return capability(level, bulkItemCaches, BulkItems.BLOCK, ResourceType.ITEM, machineFace);
    }

    /** Fluidos em {@code long} (o Tanque do mod) pela face, ou {@code null}. */
    public @Nullable BulkFluids bulkFluids(Direction machineFace) {
        ServerLevel level = queryLevel();
        if (level == null) {
            return null;
        }
        if (bulkFluidCaches == null) {
            bulkFluidCaches = newCaches();
        }
        return capability(level, bulkFluidCaches, BulkFluids.BLOCK, ResourceType.FLUID, machineFace);
    }

    public @Nullable IFluidHandler fluids(Direction machineFace) {
        ServerLevel level = queryLevel();
        if (level == null) {
            return null;
        }
        if (fluidCaches == null) {
            fluidCaches = newCaches();
        }
        return capability(level, fluidCaches, Capabilities.FluidHandler.BLOCK, ResourceType.FLUID, machineFace);
    }

    /** Energia em {@code long} (a Bateria do mod) pela face, ou {@code null}. */
    public @Nullable BulkEnergy bulkEnergy(Direction machineFace) {
        ServerLevel level = queryLevel();
        if (level == null) {
            return null;
        }
        if (bulkEnergyCaches == null) {
            bulkEnergyCaches = newCaches();
        }
        return capability(level, bulkEnergyCaches, BulkEnergy.BLOCK, ResourceType.ENERGY, machineFace);
    }

    public @Nullable IEnergyStorage energy(Direction machineFace) {
        ServerLevel level = queryLevel();
        if (level == null) {
            return null;
        }
        if (energyCaches == null) {
            energyCaches = newCaches();
        }
        return capability(level, energyCaches, Capabilities.EnergyStorage.BLOCK, ResourceType.ENERGY, machineFace);
    }

    /** Handler de químicos do Mekanism como {@code Object}, ou {@code null} sem ele ou sem o Mekanism. */
    @SuppressWarnings("unchecked")
    public @Nullable Object chemicals(Direction machineFace) {
        BlockCapability<?, @Nullable Direction> capability = Chemicals.capability();
        ServerLevel level = capability == null ? null : queryLevel();
        if (level == null) {
            return null;
        }
        if (chemicalCaches == null) {
            chemicalCaches = newCaches();
        }
        return capability(level, chemicalCaches, (BlockCapability<Object, @Nullable Direction>) capability,
                ResourceType.CHEMICAL, machineFace);
    }

    /** Source em {@code long} (o Tanque de Source do mod) pela face, ou {@code null}. */
    public @Nullable BulkSource bulkSource(Direction machineFace) {
        ServerLevel level = queryLevel();
        if (level == null) {
            return null;
        }
        if (bulkSourceCaches == null) {
            bulkSourceCaches = newCaches();
        }
        return capability(level, bulkSourceCaches, BulkSource.BLOCK, ResourceType.SOURCE, machineFace);
    }

    /** Source do Ars Nouveau ({@code ISourceCap}) como {@code Object}, ou {@code null} sem ela ou sem o Ars. */
    @SuppressWarnings("unchecked")
    public @Nullable Object arsSource(Direction machineFace) {
        BlockCapability<?, @Nullable Direction> capability = Sources.capability();
        ServerLevel level = capability == null ? null : queryLevel();
        if (level == null) {
            return null;
        }
        if (sourceCaches == null) {
            sourceCaches = newCaches();
        }
        return capability(level, sourceCaches, (BlockCapability<Object, @Nullable Direction>) capability,
                ResourceType.SOURCE, machineFace);
    }

    /** A capability pelo cache da face, criando o cache na primeira consulta. Só com o nível do {@link #queryLevel}. */
    private <T> @Nullable T capability(ServerLevel serverLevel, BlockCapabilityCache<T, @Nullable Direction>[] caches,
            BlockCapability<T, @Nullable Direction> capability, ResourceType type, Direction machineFace) {
        BlockCapabilityCache<T, @Nullable Direction> cache = caches[machineFace.ordinal()];
        if (cache == null) {
            if (attachedTo != null) {
                // A presa segue o facing: lido só ao criar um cache (girar já chama o retarget).
                BlockPos machine = attachedTo.machinePos();
                if (!machine.equals(pos)) {
                    retarget(machine);
                }
            }
            int created = generation;
            cache = BlockCapabilityCache.create(capability, serverLevel, pos, machineFace,
                    () -> !owner.ownerRemoved() && generation == created,
                    () -> owner.machineInvalidated(this, type, machineFace));
            caches[machineFace.ordinal()] = cache;
        }
        return cache.getCapability();
    }

    @SuppressWarnings("unchecked")
    private static <T> BlockCapabilityCache<T, @Nullable Direction>[] newCaches() {
        return (BlockCapabilityCache<T, @Nullable Direction>[]) new BlockCapabilityCache[FACES];
    }
}
