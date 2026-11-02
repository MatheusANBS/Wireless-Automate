package io.github.matheusanbs.wirelessautomate.block;

import io.github.matheusanbs.wirelessautomate.network.FaceConfig;
import io.github.matheusanbs.wirelessautomate.network.NetworkManager;
import io.github.matheusanbs.wirelessautomate.network.PortMode;
import io.github.matheusanbs.wirelessautomate.network.RedstoneMode;
import io.github.matheusanbs.wirelessautomate.network.ResourceType;
import io.github.matheusanbs.wirelessautomate.range.AttachedMember;
import io.github.matheusanbs.wirelessautomate.range.MachineConfig;
import io.github.matheusanbs.wirelessautomate.range.MachineGroup;
import io.github.matheusanbs.wirelessautomate.range.RemoteMachine;
import io.github.matheusanbs.wirelessautomate.range.RemoteMachines;
import io.github.matheusanbs.wirelessautomate.range.RemoteSide;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;
import java.util.function.Predicate;
import org.jetbrains.annotations.Nullable;

/**
 * As regras das redes e do redstone que valem para as faces das máquinas de um {@link RouterBlockEntity}: os perfis
 * dos grupos e as exceções, das remotas e da presa ({@link RemoteMachines#attached()}), e o rascunho da presa de
 * um mundo antigo ({@link AttachedDraft}, {@code draft}, nulo sem rascunho). Não guarda nada nem avisa ninguém:
 * quem muda é o roteador, que salva e avisa o motor.
 */
final class RouterFaces {
    private static final ResourceType[] TYPES = ResourceType.values();

    private RouterFaces() {
    }

    // ------------------------------------------------------------------ redes

    /**
     * Põe as faces do tipo dos perfis e das exceções (das remotas e da presa) na rede {@code network}, menos as que
     * o {@code canLeave} segura (contadas em {@code kept}). Devolve se alguma mudou.
     */
    static boolean setNetworkAll(RemoteMachines remotes, @Nullable AttachedDraft draft, ResourceType type,
            @Nullable UUID network, Predicate<UUID> canLeave, int[] kept) {
        boolean changed = draft != null && draft.setNetwork(type, network, canLeave, kept);
        MachineConfig attached = attachedException(remotes);
        if (attached != null) {
            changed |= setNetwork(attached, type, network, canLeave, kept);
        }
        for (MachineGroup group : remotes.groups()) {
            changed |= setNetwork(group.profile(), type, network, canLeave, kept);
        }
        for (RemoteMachine machine : remotes.machines()) {
            MachineConfig exception = machine.exception();
            if (exception != null) {
                changed |= setNetwork(exception, type, network, canLeave, kept);
            }
        }
        return changed;
    }

    /** Como na edição de um grupo ({@code RouterTargets.editRemote}): a exceção que ficou igual ao perfil do grupo some. */
    static void dropExceptionsLikeProfile(RemoteMachines remotes) {
        AttachedMember attached = remotes.attached();
        MachineGroup attachedGroup = remotes.attachedGroup();
        if (attached != null && attachedGroup != null && attached.exception() != null
                && attached.exception().sameAs(attachedGroup.profile())) {
            attached.setException(null);
        }
        for (RemoteMachine machine : remotes.machines()) {
            MachineConfig exception = machine.exception();
            if (exception != null && exception.sameAs(remotes.groupOf(machine).profile())) {
                machine.setException(null);
            }
        }
    }

    private static boolean setNetwork(MachineConfig config, ResourceType type, @Nullable UUID network,
            Predicate<UUID> canLeave, int[] kept) {
        boolean changed = false;
        for (RemoteSide side : RemoteSide.values()) {
            changed |= moveFace(config.face(type, side), network, canLeave, kept);
        }
        return changed;
    }

    /** A exceção da presa, ou {@code null} sem membro ou sem exceção. */
    private static @Nullable MachineConfig attachedException(RemoteMachines remotes) {
        AttachedMember attached = remotes.attached();
        return attached == null ? null : attached.exception();
    }

    /** Muda a rede de uma face, menos se o {@code canLeave} segura a rede atual (contada em {@code kept}). */
    static boolean moveFace(FaceConfig config, @Nullable UUID network, Predicate<UUID> canLeave, int[] kept) {
        UUID from = config.network();
        if (from != null && !from.equals(network) && !canLeave.test(from)) {
            kept[0]++;
            return false;
        }
        return config.setNetwork(network);
    }

    /** As redes distintas do tipo (sem nulo, na ordem em que aparecem) nas faces dos perfis e das exceções. */
    static UUID[] networks(RemoteMachines remotes, @Nullable AttachedDraft draft, ResourceType type) {
        Set<UUID> found = new LinkedHashSet<>();
        if (draft != null) {
            draft.addNetworks(found, type);
        }
        MachineConfig attached = attachedException(remotes);
        if (attached != null) {
            addNetworks(found, attached, type);
        }
        for (MachineGroup group : remotes.groups()) {
            addNetworks(found, group.profile(), type);
        }
        for (RemoteMachine machine : remotes.machines()) {
            MachineConfig exception = machine.exception();
            if (exception != null) {
                addNetworks(found, exception, type);
            }
        }
        return found.toArray(UUID[]::new);
    }

    /** Alguma face (perfil ou exceção) de algum tipo está numa rede. */
    static boolean hasNetwork(RemoteMachines remotes, @Nullable AttachedDraft draft) {
        for (ResourceType type : TYPES) {
            if (networks(remotes, draft, type).length > 0) {
                return true;
            }
        }
        return false;
    }

    private static void addNetworks(Set<UUID> found, MachineConfig config, ResourceType type) {
        for (RemoteSide side : RemoteSide.values()) {
            addNetwork(found, config.face(type, side));
        }
    }

    private static void addNetwork(Set<UUID> found, FaceConfig config) {
        if (config.network() != null) {
            found.add(config.network());
        }
    }

    /**
     * Migração da rede da aba: põe {@code network} nas faces do tipo ainda sem rede dos perfis e das exceções.
     * Faces que já têm rede ficam. Sem avisar ninguém: o fim da carga avisa.
     */
    static void migrateLegacyNetwork(RemoteMachines remotes, ResourceType type, UUID network) {
        MachineConfig attached = attachedException(remotes);
        if (attached != null) {
            migrateLegacyNetwork(attached, type, network);
        }
        for (MachineGroup group : remotes.groups()) {
            migrateLegacyNetwork(group.profile(), type, network);
        }
        for (RemoteMachine machine : remotes.machines()) {
            MachineConfig exception = machine.exception();
            if (exception != null) {
                migrateLegacyNetwork(exception, type, network);
            }
        }
    }

    private static void migrateLegacyNetwork(MachineConfig config, ResourceType type, UUID network) {
        for (RemoteSide side : RemoteSide.values()) {
            FaceConfig face = config.face(type, side);
            if (face.network() == null) {
                face.setNetwork(network);
            }
        }
    }

    // ------------------------------------------------------------------ redstone

    /** Máscara ({@link NetworkManager#typeBit}) dos tipos com alguma face com modo e controle por redstone. */
    static int redstoneTypes(RemoteMachines remotes, @Nullable AttachedDraft draft) {
        int types = 0;
        for (ResourceType type : TYPES) {
            if ((draft != null && draft.usesRedstone(type)) || usesRedstone(remotes, type)) {
                types |= NetworkManager.typeBit(type);
            }
        }
        return types;
    }

    /** Alguma face do tipo, nos perfis ou nas exceções (das remotas e da presa), tem modo e controle por redstone. */
    private static boolean usesRedstone(RemoteMachines remotes, ResourceType type) {
        MachineConfig attached = attachedException(remotes);
        if (attached != null && usesRedstone(attached, type)) {
            return true;
        }
        for (MachineGroup group : remotes.groups()) {
            if (usesRedstone(group.profile(), type)) {
                return true;
            }
        }
        for (RemoteMachine machine : remotes.machines()) {
            MachineConfig exception = machine.exception();
            if (exception != null && usesRedstone(exception, type)) {
                return true;
            }
        }
        return false;
    }

    private static boolean usesRedstone(MachineConfig config, ResourceType type) {
        for (RemoteSide side : RemoteSide.values()) {
            if (usesRedstone(config.face(type, side))) {
                return true;
            }
        }
        return false;
    }

    private static boolean usesRedstone(FaceConfig config) {
        return config.redstone() != RedstoneMode.IGNORE && config.mode() != PortMode.NONE;
    }
}
