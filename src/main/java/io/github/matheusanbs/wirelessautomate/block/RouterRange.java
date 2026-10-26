package io.github.matheusanbs.wirelessautomate.block;

import io.github.matheusanbs.wirelessautomate.network.NetworkManager;
import io.github.matheusanbs.wirelessautomate.network.ResourceType;
import io.github.matheusanbs.wirelessautomate.packet.RenameRouterPayload;
import io.github.matheusanbs.wirelessautomate.range.EditTarget;
import io.github.matheusanbs.wirelessautomate.range.MachineCaches;
import io.github.matheusanbs.wirelessautomate.range.MachineConfig;
import io.github.matheusanbs.wirelessautomate.range.MachineGroup;
import io.github.matheusanbs.wirelessautomate.range.RangeRules;
import io.github.matheusanbs.wirelessautomate.range.RemoteCapabilities;
import io.github.matheusanbs.wirelessautomate.range.RemoteIndex;
import io.github.matheusanbs.wirelessautomate.range.RemoteMachine;
import io.github.matheusanbs.wirelessautomate.range.RemoteMachines;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.Containers;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

/**
 * O alcance de um {@link RouterBlockEntity} com o Upgrade de Alcance: vincular e desvincular máquinas remotas,
 * a exceção de uma máquina, a frente de cada remota (relida quando ela avisa ou quando um cache é
 * invalidado), os caches de capability delas ({@link RouterRemoteCaches}) e o registro no {@link RemoteIndex}.
 * Nenhuma remota é lida sem o chunk carregado.
 */
final class RouterRange {
    private final RouterBlockEntity router;
    private final RemoteMachines remotes;
    private final RouterRemoteCaches caches;

    RouterRange(RouterBlockEntity router, RemoteMachines remotes) {
        this.router = router;
        this.remotes = remotes;
        this.caches = new RouterRemoteCaches(router);
    }

    MachineCaches caches(BlockPos pos) {
        return caches.get(pos);
    }

    /** Ver {@link RouterBlockEntity#linkMachine(BlockPos, UUID)}. */
    boolean link(BlockPos pos, @Nullable UUID network, boolean isolated) {
        Level level = router.getLevel();
        if (level == null || level.isClientSide || !level.isLoaded(pos) || pos.equals(router.getBlockPos())) {
            return false;
        }
        BlockState state = level.getBlockState(pos);
        if (state.isAir()
                || !remotes.link(pos, BuiltInRegistries.BLOCK.getKey(state.getBlock()), RemoteMachines.frontOf(state),
                        network, isolated)) {
            return false;
        }
        remotesChanged();
        router.changed(NetworkManager.ALL_TYPES);
        return true;
    }

    /** Ver {@link RouterBlockEntity#unlinkMachine(int)}. */
    boolean unlink(int index) {
        if (remotes.machine(index) == null) {
            return false;
        }
        dropAtRouter(remotes.unlink(index));
        pruneCaches();
        remotesChanged();
        router.changed(NetworkManager.ALL_TYPES);
        return true;
    }

    /**
     * Ver {@link RouterBlockEntity#renameTarget}. Só o que a tela mostra muda: sobe o {@code changeVersion} e
     * avisa o índice, sem {@code changed()}, porque o nome não mexe nas rotas.
     */
    boolean rename(EditTarget target, String name) {
        String clean = RenameRouterPayload.clean(name);
        switch (target.kind()) {
            case GROUP -> {
                MachineGroup group = remotes.group(target.id());
                if (group == null || group.name().equals(clean)) {
                    return false;
                }
                group.setName(clean);
            }
            case MACHINE -> {
                RemoteMachine machine = remotes.machine(target.id());
                if (machine == null || machine.label().equals(clean)) {
                    return false;
                }
                machine.setLabel(clean);
            }
            default -> {
                return false;
            }
        }
        router.nameChanged();
        return true;
    }

    /** Ver {@link RouterBlockEntity#revertToProfile(int)}. */
    boolean revertToProfile(int index) {
        RemoteMachine machine = remotes.machine(index);
        if (machine == null || !machine.hasException()) {
            return false;
        }
        machine.setException(null);
        router.changed(NetworkManager.ALL_TYPES);
        return true;
    }

    /** Ver {@link RouterBlockEntity#revertGroupToProfile(int)}. */
    int revertGroupToProfile(int groupId) {
        int reverted = 0;
        for (RemoteMachine machine : remotes.machines()) {
            if (machine.group() == groupId && machine.hasException()) {
                machine.setException(null);
                reverted++;
            }
        }
        if (reverted > 0) {
            router.changed(NetworkManager.ALL_TYPES);
        }
        return reverted;
    }

    /** Ver {@link RouterBlockEntity#setGroupProfile}. */
    boolean setGroupProfile(int groupId, MachineConfig config) {
        MachineGroup group = remotes.group(groupId);
        if (group == null || !group.profile().copyFrom(config)) {
            return false;
        }
        RouterFaces.dropExceptionsLikeProfile(remotes);
        router.changed(NetworkManager.ALL_TYPES);
        return true;
    }

    /** Ver {@link RouterBlockEntity#setMachineException}. */
    boolean setMachineException(int index, @Nullable MachineConfig config) {
        RemoteMachine machine = remotes.machine(index);
        if (machine == null) {
            return false;
        }
        MachineConfig next = config == null || config.sameAs(remotes.groupOf(machine).profile()) ? null : config.copy();
        MachineConfig now = machine.exception();
        if (now == null ? next == null : next != null && now.sameAs(next)) {
            return false;
        }
        machine.setException(next);
        router.changed(NetworkManager.ALL_TYPES);
        return true;
    }

    /** Ver {@link RouterBlockEntity#replacedBy}. */
    @Nullable ResourceLocation replacedBy(RemoteMachine machine, RangeRules.Reach reach) {
        Level level = router.getLevel();
        if (level == null || reach != RangeRules.Reach.ACTIVE || !router.rangeActive()
                || !level.isLoaded(machine.pos())) {
            return null;
        }
        BlockState state = level.getBlockState(machine.pos());
        if (state.isAir()) {
            return null;
        }
        ResourceLocation block = BuiltInRegistries.BLOCK.getKey(state.getBlock());
        if (block.equals(remotes.groupOf(machine).block())) {
            return null;
        }
        return RemoteCapabilities.any(level, machine.pos()) ? block : null;
    }

    /** Ver {@link RouterBlockEntity#updateReplaced}. */
    RemoteMachines.Regroup updateReplaced(List<Integer> indices) {
        Level level = router.getLevel();
        if (level == null || level.isClientSide) {
            return RemoteMachines.Regroup.EMPTY;
        }
        RangeRules.Reach[] reach = router.machineReach();
        Map<Integer, ResourceLocation> moves = new LinkedHashMap<>();
        for (int index : indices) {
            RemoteMachine machine = remotes.machine(index);
            ResourceLocation block = machine == null ? null : replacedBy(machine, reach[index]);
            if (block != null) {
                moves.put(index, block);
            }
        }
        RemoteMachines.Regroup result = remotes.regroup(moves);
        if (result.isEmpty()) {
            return result;
        }
        for (int index : result.destinations().keySet()) {
            RemoteMachine machine = remotes.machine(index);
            // O replacedBy conferiu o chunk carregado.
            storeFront(machine, level.getBlockState(machine.pos()));
        }
        dropAtRouter(result.cards());
        pruneCaches();
        remotesChanged();
        router.changed(NetworkManager.ALL_TYPES);
        return result;
    }

    private void dropAtRouter(List<ItemStack> stacks) {
        Level level = router.getLevel();
        if (level == null) {
            return;
        }
        BlockPos worldPosition = router.getBlockPos();
        for (ItemStack stack : stacks) {
            Containers.dropItemStack(level, worldPosition.getX(), worldPosition.getY(), worldPosition.getZ(), stack);
        }
    }

    /**
     * Descarta os caches (e os listeners deles) das remotas que não estão mais vinculadas; sem o
     * upgrade valendo, de todas. Chamado ao desvincular, ao carregar o NBT e quando o upgrade sai.
     */
    void pruneCaches() {
        if (caches.isEmpty()) {
            return;
        }
        caches.prune(router.rangeActive(), remotes);
    }

    /**
     * As máquinas vinculadas ou o upgrade mudaram: refaz o registro no {@link RemoteIndex} (só se o nó
     * está no motor e o upgrade vale; senão, só tira).
     */
    void remotesChanged() {
        Level level = router.getLevel();
        if (level != null && !level.isClientSide) {
            RemoteIndex.get().update(router);
        }
    }

    /** Ver {@link RouterBlockEntity#remoteMachineChanged}. */
    void machineChanged(RemoteMachine machine, @Nullable BlockState state) {
        // Durante uma visita do motor o aviso é a nossa entrega ou extração, que nunca gira a máquina.
        if (state != null && !NetworkManager.visiting()) {
            refreshFront(machine, state);
        }
        NetworkManager.get().wakeMachine(router, machine.pos());
    }

    /**
     * Relê a frente da máquina remota em {@code state}. Se mudou, grava e refaz as rotas do nó (os
     * lados relativos mudaram) e devolve {@code true}. A regra num lugar só: o aviso da máquina
     * ({@link #machineChanged}) e a invalidação da capability ({@link #invalidated}).
     */
    private boolean refreshFront(RemoteMachine machine, BlockState state) {
        if (!storeFront(machine, state)) {
            return false;
        }
        router.changed(NetworkManager.ALL_TYPES);
        return true;
    }

    /** Ver {@link RouterBlockEntity#rereadFront}. */
    boolean rereadFront(RemoteMachine machine) {
        Level level = router.getLevel();
        if (level == null || level.isClientSide || !level.isLoaded(machine.pos())) {
            return false;
        }
        return storeFront(machine, level.getBlockState(machine.pos()));
    }

    /** Grava a frente de {@code state} na máquina, se mudou (salva e refaz o snapshot), sem avisar o motor. */
    private boolean storeFront(RemoteMachine machine, BlockState state) {
        int front = RemoteMachines.frontOf(state);
        if (front == machine.front()) {
            return false;
        }
        machine.setFront(front);
        router.bumpChangeVersion();
        router.setChanged();
        return true;
    }

    /**
     * Um cache de uma remota foi invalidado, no servidor: a frente é relida (só com o chunk carregado); se
     * mudou, as rotas do nó são refeitas, senão acorda a porta da face.
     */
    void invalidated(Level level, BlockPos pos, ResourceType type, Direction machineFace) {
        if (level.isLoaded(pos)) {
            RemoteMachine machine = remotes.machine(remotes.index(pos));
            if (machine != null && refreshFront(machine, level.getBlockState(pos))) {
                return;
            }
        }
        NetworkManager.get().capabilityChanged(router, pos, type, machineFace);
    }
}
