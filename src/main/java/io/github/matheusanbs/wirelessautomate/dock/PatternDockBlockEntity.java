package io.github.matheusanbs.wirelessautomate.dock;

import io.github.matheusanbs.wirelessautomate.Config;
import io.github.matheusanbs.wirelessautomate.block.RouterBlock;
import io.github.matheusanbs.wirelessautomate.block.RouterTier;
import io.github.matheusanbs.wirelessautomate.network.NetworkManager;
import io.github.matheusanbs.wirelessautomate.network.ResourceType;
import io.github.matheusanbs.wirelessautomate.range.CacheOwner;
import io.github.matheusanbs.wirelessautomate.range.MachineCaches;
import io.github.matheusanbs.wirelessautomate.range.RangeRules;
import io.github.matheusanbs.wirelessautomate.range.RemoteIndex;
import io.github.matheusanbs.wirelessautomate.range.RemoteListener;
import io.github.matheusanbs.wirelessautomate.range.RemoteMachine;
import io.github.matheusanbs.wirelessautomate.range.RemoteMachines;
import io.github.matheusanbs.wirelessautomate.registry.ModBlockEntities;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.Containers;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

/**
 * O block entity da Doca de Padrões, sem ticker: os seis {@link DockSide} (nulo = lado nunca usado), as máquinas por
 * posição para achar a de um aviso sem varrer os lados, e o registro no {@link RemoteIndex} das máquinas que estão no
 * alcance do tier ({@link Config#dockRadius}, distância de Chebyshev) até o máximo ({@link Config#dockMaxMachines}),
 * contadas na ordem dos lados e, em cada lado, na ordem de vínculo.
 *
 * <p>O registro no índice muda só no carregamento ({@link #onLoad}), num vínculo ou desvínculo e numa troca de tier;
 * sai no {@link #setRemoved} e no {@link #onChunkUnloaded}. Os avisos das máquinas (o {@code setChanged} pelo mixin, a
 * carga do chunk e um cache invalidado) chegam todos ao {@link #machineChanged}.
 */
public class PatternDockBlockEntity extends BlockEntity implements RemoteListener, CacheOwner {
    public static final int SIDES = 6;

    private final DockSide[] sides = new DockSide[SIDES];
    /** Todas as máquinas da Doca, pela posição ({@code BlockPos.asLong}). */
    private final Long2ObjectOpenHashMap<DockMachine> machineAt = new Long2ObjectOpenHashMap<>();
    /** Dentro de um {@link #batchChanges}: os vínculos só marcam {@link #batchDirty}, e o registro é refeito no fim. */
    private boolean batching;
    private boolean batchDirty;

    public PatternDockBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.PATTERN_DOCK.get(), pos, state);
    }

    public RouterTier tier() {
        return getBlockState().getValue(RouterBlock.TIER);
    }

    /** O lado {@code side} ({@code Direction.ordinal()}), ou {@code null} se nunca foi usado. */
    public @Nullable DockSide side(int side) {
        return sides[side];
    }

    /** A máquina em {@code pos}, de qualquer lado, ou {@code null}. */
    public @Nullable DockMachine machineAt(BlockPos pos) {
        return machineAt.get(pos.asLong());
    }

    /** O {@link #machineAt(BlockPos)} pela posição em {@code long}, sem criar a posição. */
    @Nullable DockMachine machineAt(long pos) {
        return machineAt.get(pos);
    }

    /** Quantas máquinas a Doca tem vinculadas, somando os seis lados (dentro do alcance ou não). */
    public int machineCount() {
        return machineAt.size();
    }

    /** Quantas máquinas estão ativas (no alcance e dentro do máximo): as vagas usadas, como na tela. */
    public int activeCount() {
        int count = 0;
        for (DockMachine machine : machineAt.values()) {
            if (machine.active()) {
                count++;
            }
        }
        return count;
    }

    /**
     * Roda {@code changes} (vários vínculos e desvínculos, como os do Ctrl + clique) refazendo o registro no índice e
     * salvando uma vez só, no fim, em vez de uma vez por máquina.
     */
    public void batchChanges(Runnable changes) {
        if (batching) {
            changes.run();
            return;
        }
        batching = true;
        try {
            changes.run();
        } finally {
            batching = false;
            if (batchDirty) {
                batchDirty = false;
                linksChanged();
            }
        }
    }

    /**
     * Vincula a máquina em {@code pos} (bloco {@code block}, frente {@code front} ou −1) ao lado {@code side}, criando o
     * lado no primeiro vínculo, e refaz o registro no índice. Recusa ({@code false}) uma máquina já vinculada a esta
     * Doca ou de outro bloco que o do lado; o alcance e o limite são conferidos por quem chama (o Vinculador).
     */
    public boolean link(int side, BlockPos pos, ResourceLocation block, int front) {
        if (machineAt.containsKey(pos.asLong())) {
            return false;
        }
        DockSide dockSide = sides[side];
        if (dockSide == null) {
            dockSide = new DockSide(side);
            sides[side] = dockSide;
        }
        if (dockSide.block() == null) {
            dockSide.assignBlock(block);
        } else if (!dockSide.block().equals(block)) {
            return false;
        }
        DockMachine machine = new DockMachine(side, pos, front);
        dockSide.addMachine(machine);
        machineAt.put(pos.asLong(), machine);
        linksChanged();
        return true;
    }

    /**
     * Desvincula a máquina em {@code pos} do lado {@code side} e refaz o registro no índice. Sem máquinas, o lado
     * esquece o bloco (o próximo vínculo o escolhe de novo) e, sem pendente, deixa de existir.
     */
    public boolean unlink(int side, BlockPos pos) {
        DockSide dockSide = sides[side];
        DockMachine machine = dockSide == null ? null : dockSide.removeMachine(pos);
        if (machine == null) {
            return false;
        }
        machine.discard();
        machineAt.remove(pos.asLong());
        if (dockSide.machines().isEmpty()) {
            dockSide.assignBlock(null);
            if (dockSide.isUnused()) {
                sides[side] = null;
            }
        }
        linksChanged();
        return true;
    }

    /** O tier mudou (Cartão de Upgrade): o alcance e o limite mudaram com ele. */
    public void reachChanged() {
        refreshIndex();
    }

    /** O que só muda o NBT: marca o chunk sem avisar os vizinhos (ninguém lê a Doca por comparador). */
    public void saveChanged() {
        if (level != null) {
            level.blockEntityChanged(worldPosition);
        }
    }

    private void linksChanged() {
        if (batching) {
            batchDirty = true;
            return;
        }
        saveChanged();
        refreshIndex();
    }

    /**
     * Refaz o registro no {@link RemoteIndex} e a marca {@link DockMachine#active} de cada máquina: no alcance do tier
     * e até o máximo, na ordem dos lados e de vínculo. Só no servidor e com a Doca no mundo.
     */
    private void refreshIndex() {
        if (level == null || level.isClientSide || isRemoved()) {
            return;
        }
        RouterTier tier = tier();
        int radius = Config.dockRadius(tier);
        int max = Config.dockMaxMachines(tier);
        List<RemoteMachine> active = new ArrayList<>(Math.min(machineAt.size(), max));
        for (DockSide side : sides) {
            if (side == null) {
                continue;
            }
            int sideActive = 0;
            for (DockMachine machine : side.machines()) {
                BlockPos pos = machine.pos();
                boolean inReach = active.size() < max && RangeRules.chebyshev(worldPosition.getX(),
                        worldPosition.getY(), worldPosition.getZ(), pos.getX(), pos.getY(), pos.getZ()) <= radius;
                machine.setActive(inReach);
                if (inReach) {
                    active.add(machine.remote());
                    sideActive++;
                }
            }
            side.setActiveMachines(sideActive);
        }
        RemoteIndex.get().update(this, active);
    }

    /** A Doca saiu do mundo ou o chunk dela descarregou: sai do índice, e os caches das máquinas deixam de valer. */
    private void leave() {
        if (level == null || level.isClientSide) {
            return;
        }
        RemoteIndex.get().remove(this);
        for (DockMachine machine : machineAt.values()) {
            machine.discard();
        }
    }

    /**
     * O ponto único dos avisos de uma máquina (o {@code setChanged} dela, a carga do chunk dela e um cache de
     * capability invalidado): marca a máquina como mudada ({@link DockMachine#changed}), a acorda para a entrega e,
     * se ela tem o pendente do lado, enfileira uma tentativa dele ({@link DockPending#flushSoon}). A tentativa não
     * roda aqui: o aviso chega de dentro do {@code setChanged} da máquina, de uma entrega do motor ou de um listener
     * de cache, onde inserir nela reentraria (ver o {@code flushSoon}).
     */
    void machineChanged(DockMachine machine) {
        machine.markChanged();
        machine.wake();
        DockSide side = sides[machine.side()];
        if (side != null && side.hasPending() && side.pendingAtKey() == machine.pos().asLong()) {
            DockPending.flushSoon(this, side);
        }
    }

    /** Larga no chão o pendente de itens de todos os lados (o bloco saiu); os fluidos pendentes se perdem. */
    public void dropPending() {
        if (level == null || level.isClientSide) {
            return;
        }
        for (DockSide side : sides) {
            if (side == null || !side.hasPending()) {
                continue;
            }
            for (ItemStack stack : side.pendingItems()) {
                Containers.dropItemStack(level, worldPosition.getX(), worldPosition.getY(), worldPosition.getZ(), stack);
            }
            side.clearPending();
        }
    }

    /**
     * Uma máquina avisou mudança pelo índice. Com o estado do bloco, relê a frente, fora de uma visita do motor (o
     * aviso de dentro de uma visita é uma entrega ou retirada, que nunca gira a máquina).
     */
    @Override
    public void remoteMachineChanged(RemoteMachine remote, @Nullable BlockState state) {
        DockMachine machine = machineAt.get(remote.pos().asLong());
        if (machine == null) {
            return;
        }
        if (state != null && !NetworkManager.visiting()) {
            int front = RemoteMachines.frontOf(state);
            if (front != machine.front()) {
                machine.setFront(front);
                saveChanged();
            }
        }
        machineChanged(machine);
    }

    /** Um cache de capability de uma máquina foi invalidado (a block entity saiu ou entrou). */
    @Override
    public void machineInvalidated(MachineCaches caches, ResourceType type, Direction face) {
        DockMachine machine = machineAt.get(caches.pos().asLong());
        if (machine != null && machine.cachesIfCreated() == caches) {
            machineChanged(machine);
        }
    }

    @Override
    public @Nullable Level listenerLevel() {
        return level;
    }

    @Override
    public BlockPos listenerPos() {
        return worldPosition;
    }

    @Override
    public @Nullable Level ownerLevel() {
        return level;
    }

    @Override
    public boolean ownerRemoved() {
        return isRemoved();
    }

    @Override
    public void onLoad() {
        super.onLoad();
        refreshIndex();
    }

    @Override
    public void onChunkUnloaded() {
        super.onChunkUnloaded();
        leave();
    }

    @Override
    public void setRemoved() {
        super.setRemoved();
        leave();
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        ListTag list = new ListTag();
        for (DockSide side : sides) {
            if (side != null) {
                list.add(side.save(registries));
            }
        }
        if (!list.isEmpty()) {
            tag.put("sides", list);
        }
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        for (DockMachine machine : machineAt.values()) {
            machine.discard();
        }
        machineAt.clear();
        Arrays.fill(sides, null);
        ListTag list = tag.getList("sides", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            DockSide side = DockSide.load(list.getCompound(i), registries);
            if (side == null || sides[side.side()] != null) {
                continue;
            }
            sides[side.side()] = side;
            for (DockMachine machine : side.machines()) {
                machineAt.putIfAbsent(machine.pos().asLong(), machine);
            }
        }
        // Um NBT carregado com a Doca já no mundo (um comando, um esquema colado) refaz o registro; na carga do
        // chunk o level ainda é nulo e quem refaz é o onLoad.
        refreshIndex();
    }
}
