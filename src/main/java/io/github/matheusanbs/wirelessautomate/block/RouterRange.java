package io.github.matheusanbs.wirelessautomate.block;

import io.github.matheusanbs.wirelessautomate.network.NetworkManager;
import io.github.matheusanbs.wirelessautomate.network.ResourceType;
import io.github.matheusanbs.wirelessautomate.packet.RenameRouterPayload;
import io.github.matheusanbs.wirelessautomate.range.AttachedMember;
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
import net.minecraft.world.entity.player.Player;
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
    boolean link(BlockPos pos, @Nullable UUID network) {
        Level level = router.getLevel();
        if (level == null || level.isClientSide || !level.isLoaded(pos) || pos.equals(router.getBlockPos())) {
            return false;
        }
        BlockState state = level.getBlockState(pos);
        if (state.isAir()
                || !remotes.link(pos, BuiltInRegistries.BLOCK.getKey(state.getBlock()), RemoteMachines.frontOf(state),
                        network)) {
            return false;
        }
        remotesChanged();
        router.changed(NetworkManager.ALL_TYPES);
        return true;
    }

    /** Ver {@link RouterBlockEntity#unlinkMachine(int, Player)}. */
    int unlink(int index, @Nullable Player recipient) {
        Level level = router.getLevel();
        if (level == null || level.isClientSide || remotes.machine(index) == null) {
            return -1;
        }
        List<ItemStack> cards = remotes.unlink(index);
        deliver(recipient, cards);
        pruneCaches();
        remotesChanged();
        router.changed(NetworkManager.ALL_TYPES);
        return cards.size();
    }

    /** Ver {@link RouterBlockEntity#unlinkGroup}. */
    RemoteMachines.@Nullable GroupUnlink unlinkGroup(int groupId, @Nullable Player recipient) {
        RemoteMachines.GroupUnlink result = remotes.unlinkGroup(groupId);
        if (result == null || !result.changed()) {
            return result;
        }
        deliver(recipient, result.cards());
        if (result.machines() > 0) {
            pruneCaches();
            remotesChanged();
        }
        router.changed(NetworkManager.ALL_TYPES);
        return result;
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
        AttachedMember member = remotes.attached();
        if (remotes.groupHasAttached(groupId) && member.hasException()) {
            member.setException(null);
            reverted++;
        }
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

    /** Ver {@link RouterBlockEntity#updateReplaced(List, Player)}. */
    RemoteMachines.Regroup updateReplaced(List<Integer> indices, @Nullable Player recipient) {
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
        deliver(recipient, result.cards());
        pruneCaches();
        remotesChanged();
        router.changed(NetworkManager.ALL_TYPES);
        return result;
    }

    /**
     * Entrega cópias das pilhas a {@code recipient}: no inventário e, o que não couber, aos pés dele (o
     * {@code placeItemBackInInventory} solta o resto; o {@code Inventory.add} sumiria com ele no modo criativo). As
     * pilhas da lista ficam como estão, para quem chamou contar. Sem jogador (testes e comandos), caem no roteador.
     */
    private void deliver(@Nullable Player recipient, List<ItemStack> stacks) {
        if (recipient == null) {
            dropAtRouter(stacks);
            return;
        }
        for (ItemStack stack : stacks) {
            recipient.getInventory().placeItemBackInInventory(stack.copy());
        }
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
     * Descarta os caches (e os listeners deles) das remotas que não estão mais vinculadas ou ativas (fora do raio ou
     * do limite, que o motor não lê); sem o upgrade valendo, de todas. Chamado ao desvincular, ao carregar o NBT,
     * quando o upgrade sai e na recarga da config ({@link RouterBlockEntity#pruneRemoteCaches}).
     */
    void pruneCaches() {
        if (caches.isEmpty()) {
            return;
        }
        caches.prune(router.rangeActive() ? router.machineReach() : null, remotes);
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
     * lados relativos mudaram) e devolve {@code true}. O aviso da máquina ({@link #machineChanged}).
     */
    private boolean refreshFront(RemoteMachine machine, BlockState state) {
        if (!storeFront(machine, state)) {
            return false;
        }
        router.changed(NetworkManager.ALL_TYPES);
        return true;
    }

    /** Ver {@link RouterBlockEntity#rereadRemote}. */
    boolean rereadRemote(RemoteMachine machine) {
        Level level = router.getLevel();
        if (level == null || level.isClientSide || !level.isLoaded(machine.pos())) {
            return false;
        }
        BlockState state = level.getBlockState(machine.pos());
        // Sem curto-circuito: as duas marcas são gravadas, e qualquer uma faz os outros tipos remontarem.
        return storeReplaced(machine, state) | storeFront(machine, state);
    }

    /**
     * Grava se o bloco em {@code state} deixou de ser o do grupo da máquina ({@link RemoteMachine#replaced}); devolve
     * se isso mudou. Sem avisar o motor.
     */
    private boolean storeReplaced(RemoteMachine machine, BlockState state) {
        boolean replaced = !BuiltInRegistries.BLOCK.getKey(state.getBlock()).equals(remotes.groupOf(machine).block());
        if (replaced == machine.replaced()) {
            return false;
        }
        machine.setReplaced(replaced);
        return true;
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
     * Um cache de uma remota foi invalidado, no servidor: o bloco e a frente são relidos (só com o chunk carregado).
     * Se a frente mudou, ou se a troca é nova (a máquina virou outro bloco, ou o bloco do grupo voltou), as rotas do
     * nó são refeitas. Se ela continua trocada, nada muda nas rotas (a montagem já a pula): só o cache que a vigia é
     * consultado de novo, depois do aviso ({@link MachineCaches#watchSoon}), sem o qual o próximo aviso não viria.
     * Senão acorda a porta da face. A troca de um bloco com block entity sempre chega aqui: o NeoForge invalida as
     * capabilities da posição quando a block entity sai e quando a nova entra.
     */
    void invalidated(Level level, BlockPos pos, ResourceType type, Direction machineFace) {
        if (level.isLoaded(pos)) {
            RemoteMachine machine = remotes.machine(remotes.index(pos));
            if (machine != null) {
                BlockState state = level.getBlockState(pos);
                boolean swapped = storeReplaced(machine, state);
                if (storeFront(machine, state) || swapped) {
                    router.changed(NetworkManager.ALL_TYPES);
                    return;
                }
                if (machine.replaced()) {
                    caches(pos).watchSoon();
                    return;
                }
            }
        }
        NetworkManager.get().capabilityChanged(router, pos, type, machineFace);
    }
}
