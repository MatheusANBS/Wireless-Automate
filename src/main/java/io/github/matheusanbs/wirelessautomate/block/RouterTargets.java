package io.github.matheusanbs.wirelessautomate.block;

import io.github.matheusanbs.wirelessautomate.filter.Filter;
import io.github.matheusanbs.wirelessautomate.network.FaceConfig;
import io.github.matheusanbs.wirelessautomate.network.LoadedTypes;
import io.github.matheusanbs.wirelessautomate.network.NetworkManager;
import io.github.matheusanbs.wirelessautomate.network.PortMode;
import io.github.matheusanbs.wirelessautomate.network.RedstoneMode;
import io.github.matheusanbs.wirelessautomate.network.RelativeSide;
import io.github.matheusanbs.wirelessautomate.network.ResourceType;
import io.github.matheusanbs.wirelessautomate.range.EditTarget;
import io.github.matheusanbs.wirelessautomate.range.IdleNetworkFace;
import io.github.matheusanbs.wirelessautomate.range.MachineConfig;
import io.github.matheusanbs.wirelessautomate.range.MachineGroup;
import io.github.matheusanbs.wirelessautomate.range.RemoteMachine;
import io.github.matheusanbs.wirelessautomate.range.RemoteMachines;
import io.github.matheusanbs.wirelessautomate.range.RemoteSide;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import java.util.function.Predicate;
import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

/**
 * A edição de um {@link RouterBlockEntity} por alvo ({@link EditTarget}: a presa, um grupo ou uma máquina
 * remota), como as telas a fazem: o lado vem no quadro da tela, a presa vai para os métodos de sempre do
 * roteador e um grupo ou uma máquina mudam o perfil ou a exceção, com um aviso só ao motor.
 */
final class RouterTargets {
    private final RouterBlockEntity router;
    private final RouterFaces faces;
    private final RemoteMachines remotes;

    RouterTargets(RouterBlockEntity router, RouterFaces faces, RemoteMachines remotes) {
        this.router = router;
        this.faces = faces;
        this.remotes = remotes;
    }

    /** Ver {@link RouterBlockEntity#setAttachedFacesNetwork}. */
    int setAttachedNetwork(Collection<ResourceType> types, int absoluteFaceMask, @Nullable UUID network) {
        int[] count = new int[1];
        router.batchChanges(() -> router.changed(
                faces.setAttachedNetwork(types, router.facing(), router.spin(), absoluteFaceMask, network, count)));
        return count[0];
    }

    /** Ver {@link RouterBlockEntity#setNetworkAllFaces(Collection, UUID, Predicate)}. */
    int setNetworkAll(Collection<ResourceType> types, @Nullable UUID network, Predicate<UUID> canLeave) {
        int[] kept = new int[1];
        router.batchChanges(() -> {
            for (ResourceType type : types) {
                if (faces.setNetworkAll(remotes, type, network, canLeave, kept)) {
                    router.changed(NetworkManager.typeBit(type));
                }
            }
            RouterFaces.dropExceptionsLikeProfile(remotes);
        });
        return kept[0];
    }

    /** O grupo de um alvo remoto (o próprio grupo, ou o da máquina); {@code null} na presa ou num alvo que não existe. */
    private @Nullable MachineGroup groupFor(EditTarget target) {
        return switch (target.kind()) {
            case ATTACHED -> null;
            case GROUP -> remotes.group(target.id());
            case MACHINE -> {
                RemoteMachine machine = remotes.machine(target.id());
                yield machine == null ? null : remotes.groupOf(machine);
            }
        };
    }

    /** Ver {@link RouterBlockEntity#face(EditTarget, ResourceType, Direction)}. */
    FaceConfig face(EditTarget target, ResourceType type, Direction side) {
        return switch (target.kind()) {
            case ATTACHED -> router.face(type, side);
            case GROUP -> {
                MachineGroup group = remotes.group(target.id());
                yield group == null ? new FaceConfig() : group.profile().face(type, remoteSide(side));
            }
            case MACHINE -> {
                RemoteMachine machine = remotes.machine(target.id());
                yield machine == null ? new FaceConfig() : remotes.effective(machine).face(type, remoteSide(side));
            }
        };
    }

    /** Ver {@link RouterBlockEntity#setFaceNetwork(EditTarget, ResourceType, Direction, UUID)}. */
    void setFaceNetwork(EditTarget target, ResourceType type, Direction side, @Nullable UUID network) {
        if (target.attached()) {
            router.setFaceNetwork(type, side, network);
            return;
        }
        editRemote(target, type, side, config -> config.setNetwork(network));
    }

    /** Ver {@link RouterBlockEntity#configureFace(EditTarget, ResourceType, Direction, PortMode, int, RedstoneMode, UUID)}. */
    void configureFace(EditTarget target, ResourceType type, Direction side, PortMode mode, int priority,
            RedstoneMode redstone, @Nullable UUID joinNetwork) {
        if (target.attached()) {
            router.batchChanges(() -> {
                router.configureFace(type, side, mode, priority, redstone);
                if (joinNetwork != null && router.faceNetwork(type, side) == null) {
                    router.setFaceNetwork(type, side, joinNetwork);
                }
            });
            return;
        }
        editRemote(target, type, side, config -> {
            boolean changed = config.setMode(mode) | config.setPriority(priority) | config.setRedstone(redstone);
            if (joinNetwork != null && config.network() == null) {
                changed |= config.setNetwork(joinNetwork);
            }
            return changed;
        });
    }

    /**
     * Muda uma face do alvo: na presa, a configuração dela; num grupo ou numa máquina remota, o perfil ou a
     * exceção ({@link #editRemote}). Um aviso ao motor, com o tipo, se algo mudou.
     */
    private boolean editFace(EditTarget target, ResourceType type, Direction side, Predicate<FaceConfig> edit) {
        if (!target.attached()) {
            return editRemote(target, type, side, edit);
        }
        if (!edit.test(router.face(type, side))) {
            return false;
        }
        router.changed(NetworkManager.typeBit(type));
        return true;
    }

    /** Ver {@link RouterBlockEntity#clearFaces(EditTarget, Collection)}. */
    int clearFaces(EditTarget target, Collection<ResourceType> types) {
        int[] count = new int[1];
        router.batchChanges(() -> {
            for (ResourceType type : types) {
                for (Direction side : Direction.values()) {
                    if (editFace(target, type, side, config -> config.setMode(PortMode.NONE))) {
                        count[0]++;
                    }
                }
            }
        });
        return count[0];
    }

    /** Ver {@link RouterBlockEntity#pasteFace(EditTarget, ResourceType, Direction, Direction)}. */
    boolean pasteFace(EditTarget target, ResourceType type, Direction from, Direction to) {
        if (from == to) {
            return false;
        }
        FaceConfig source = new FaceConfig();
        source.copyFrom(face(target, type, from));
        boolean[] changed = new boolean[1];
        router.batchChanges(() -> changed[0] = editFace(target, type, to, config -> config.copyFrom(source)));
        return changed[0];
    }

    /** Ver {@link RouterBlockEntity#clearIdleNetworks(EditTarget)}. */
    List<IdleNetworkFace> clearIdleNetworks(EditTarget target) {
        if (!hasOwnFaces(target)) {
            return List.of();
        }
        List<IdleNetworkFace> cleared = new ArrayList<>();
        router.batchChanges(() -> {
            for (ResourceType type : LoadedTypes.LIST) {
                for (Direction side : Direction.values()) {
                    FaceConfig face = face(target, type, side);
                    UUID network = face.network();
                    if (face.mode() == PortMode.NONE && network != null
                            && editRemote(target, type, side, config -> config.setNetwork(null))) {
                        cleared.add(new IdleNetworkFace(type, side, network));
                    }
                }
            }
        });
        return cleared;
    }

    /** Ver {@link RouterBlockEntity#restoreIdleNetworks(EditTarget, List)}. */
    int restoreIdleNetworks(EditTarget target, List<IdleNetworkFace> faces) {
        if (target.attached()) {
            return 0;
        }
        int[] restored = new int[1];
        router.batchChanges(() -> {
            for (IdleNetworkFace idle : faces) {
                FaceConfig face = face(target, idle.type(), idle.side());
                if (face.mode() == PortMode.NONE && face.network() == null
                        && editRemote(target, idle.type(), idle.side(), config -> config.setNetwork(idle.network()))) {
                    restored[0]++;
                }
            }
        });
        return restored[0];
    }

    /** O alvo tem faces próprias: um grupo (o perfil) ou uma máquina com exceção. */
    private boolean hasOwnFaces(EditTarget target) {
        return switch (target.kind()) {
            case ATTACHED -> false;
            case GROUP -> remotes.group(target.id()) != null;
            case MACHINE -> {
                RemoteMachine machine = remotes.machine(target.id());
                yield machine != null && machine.hasException();
            }
        };
    }

    /** Ver {@link RouterBlockEntity#setFilter(EditTarget, ResourceType, Direction, Filter)}. */
    void setFilter(EditTarget target, ResourceType type, Direction side, Filter filter) {
        if (target.attached()) {
            router.setFilter(type, side, filter);
            return;
        }
        editRemote(target, type, side, config -> config.setFilter(filter));
    }

    /**
     * Muda uma face de um grupo ou de uma máquina remota. No grupo, muda o perfil (vale para as
     * máquinas sem exceção) e as exceções que ficaram iguais ao perfil somem. Numa máquina sem
     * exceção, copia o perfil para a exceção e aplica; se a exceção fica igual ao perfil, ela some. Um
     * aviso só ao motor, com o tipo.
     */
    private boolean editRemote(EditTarget target, ResourceType type, Direction side, Predicate<FaceConfig> edit) {
        RemoteSide remote = remoteSide(side);
        if (target.kind() == EditTarget.Kind.GROUP) {
            MachineGroup group = remotes.group(target.id());
            if (group == null || !edit.test(group.profile().face(type, remote))) {
                return false;
            }
            for (RemoteMachine machine : remotes.machines()) {
                MachineConfig exception = machine.exception();
                if (machine.group() == group.id() && exception != null && exception.sameAs(group.profile())) {
                    machine.setException(null);
                }
            }
            router.changed(NetworkManager.typeBit(type));
            return true;
        }
        RemoteMachine machine = remotes.machine(target.id());
        if (machine == null) {
            return false;
        }
        MachineConfig profile = remotes.groupOf(machine).profile();
        MachineConfig exception = machine.exception();
        MachineConfig config = exception != null ? exception : profile.copy();
        if (!edit.test(config.face(type, remote))) {
            return false;
        }
        machine.setException(config.sameAs(profile) ? null : config);
        router.changed(NetworkManager.typeBit(type));
        return true;
    }

    private static RemoteSide remoteSide(Direction canonical) {
        return RemoteSide.fromCanonical(canonical.ordinal());
    }

    /** Ver {@link RouterBlockEntity#card(EditTarget, ResourceType, Direction, int)}. */
    ItemStack card(EditTarget target, ResourceType type, Direction side, int slot) {
        if (target.attached()) {
            return router.card(type, RelativeSide.fromAbsolute(router.facing(), router.spin(), side), slot);
        }
        MachineGroup group = groupFor(target);
        return group == null ? ItemStack.EMPTY : group.card(type, remoteSide(side), slot);
    }

    /** Ver {@link RouterBlockEntity#setCard(EditTarget, ResourceType, Direction, int, ItemStack)}. */
    void setCard(EditTarget target, ResourceType type, Direction side, int slot, ItemStack stack) {
        if (target.attached()) {
            router.setCard(type, RelativeSide.fromAbsolute(router.facing(), router.spin(), side), slot, stack);
            return;
        }
        MachineGroup group = groupFor(target);
        if (group != null && group.setCard(type, remoteSide(side), slot, stack)) {
            router.changed(NetworkManager.typeBit(type));
        }
    }

    /** Ver {@link RouterBlockEntity#cardCount(EditTarget, ResourceType, Direction)}. */
    int cardCount(EditTarget target, ResourceType type, Direction side) {
        if (target.attached()) {
            return router.cardCount(type, side);
        }
        int count = 0;
        for (int slot = 0; slot < RouterBlockEntity.CARD_SLOTS; slot++) {
            if (!card(target, type, side, slot).isEmpty()) {
                count++;
            }
        }
        return count;
    }
}
