package io.github.matheusanbs.wirelessautomate.block;

import io.github.matheusanbs.wirelessautomate.network.FaceConfig;
import io.github.matheusanbs.wirelessautomate.network.NetworkManager;
import io.github.matheusanbs.wirelessautomate.network.PortMode;
import io.github.matheusanbs.wirelessautomate.network.RedstoneMode;
import io.github.matheusanbs.wirelessautomate.network.RelativeSide;
import io.github.matheusanbs.wirelessautomate.network.ResourceType;
import io.github.matheusanbs.wirelessautomate.range.MachineConfig;
import io.github.matheusanbs.wirelessautomate.range.MachineGroup;
import io.github.matheusanbs.wirelessautomate.range.RemoteMachine;
import io.github.matheusanbs.wirelessautomate.range.RemoteMachines;
import io.github.matheusanbs.wirelessautomate.range.RemoteSide;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Predicate;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import org.jetbrains.annotations.Nullable;

/**
 * A configuração das faces da máquina presa de um {@link RouterBlockEntity}, por tipo e {@link RelativeSide},
 * e as regras das redes e do redstone que valem para elas e para as faces das máquinas remotas (perfis dos
 * grupos e exceções). Não avisa ninguém: quem muda é o roteador, que salva e avisa o motor.
 */
final class RouterFaces {
    private static final ResourceType[] TYPES = ResourceType.values();
    private static final RelativeSide[] SIDES = RelativeSide.values();

    /** [tipo][lado relativo], tudo alocado de início: consultas não alocam. */
    private final FaceConfig[][] faces = new FaceConfig[TYPES.length][SIDES.length];

    RouterFaces() {
        for (FaceConfig[] row : faces) {
            for (int i = 0; i < row.length; i++) {
                row[i] = new FaceConfig();
            }
        }
    }

    FaceConfig get(ResourceType type, RelativeSide side) {
        return faces[type.ordinal()][side.ordinal()];
    }

    /** A rede comum às seis faces do tipo, ou {@code null} se nenhuma tem rede ou se estão em redes diferentes. */
    @Nullable UUID commonNetwork(ResourceType type) {
        FaceConfig[] row = faces[type.ordinal()];
        UUID network = row[0].network();
        for (int i = 1; i < row.length; i++) {
            if (!Objects.equals(network, row[i].network())) {
                return null;
            }
        }
        return network;
    }

    /** Para onde vai cada lado relativo ao girar de {@code oldSpin} para {@code newSpin}, sem mudar as faces absolutas. */
    static int[] spinTargets(Direction facing, int oldSpin, int newSpin) {
        int[] target = new int[SIDES.length];
        for (RelativeSide side : SIDES) {
            target[side.ordinal()] = RelativeSide.fromAbsolute(facing, newSpin, side.toAbsolute(facing, oldSpin)).ordinal();
        }
        return target;
    }

    /** O giro: a configuração do lado relativo {@code i} vai para {@code target[i]}, em todos os tipos. */
    void rotate(int[] target) {
        for (FaceConfig[] row : faces) {
            FaceConfig[] old = row.clone();
            for (int i = 0; i < old.length; i++) {
                row[target[i]] = old[i];
            }
        }
    }

    // ------------------------------------------------------------------ redes

    /**
     * Põe as faces absolutas da presa marcadas em {@code absoluteFaceMask} dos tipos na rede, contando as
     * que mudaram em {@code count}. Devolve a máscara ({@link NetworkManager#typeBit}) dos tipos que mudaram.
     */
    int setAttachedNetwork(Collection<ResourceType> types, Direction facing, int spin, int absoluteFaceMask,
            @Nullable UUID network, int[] count) {
        int changedTypes = 0;
        for (ResourceType type : types) {
            for (Direction face : Direction.values()) {
                if ((absoluteFaceMask & (1 << face.ordinal())) != 0
                        && get(type, RelativeSide.fromAbsolute(facing, spin, face)).setNetwork(network)) {
                    count[0]++;
                    changedTypes |= NetworkManager.typeBit(type);
                }
            }
        }
        return changedTypes;
    }

    /** Todas as faces absolutas da presa marcadas em {@code absoluteFaceMask}, nos tipos, já estão na rede. */
    boolean attachedIn(Collection<ResourceType> types, Direction facing, int spin, int absoluteFaceMask,
            @Nullable UUID network) {
        for (ResourceType type : types) {
            for (Direction face : Direction.values()) {
                if ((absoluteFaceMask & (1 << face.ordinal())) != 0
                        && !Objects.equals(network, get(type, RelativeSide.fromAbsolute(facing, spin, face)).network())) {
                    return false;
                }
            }
        }
        return true;
    }

    /**
     * Põe as faces do tipo da presa, dos perfis e das exceções na rede {@code network}, menos as que o
     * {@code canLeave} segura (contadas em {@code kept}). Devolve se alguma mudou.
     */
    boolean setNetworkAll(RemoteMachines remotes, ResourceType type, @Nullable UUID network, Predicate<UUID> canLeave,
            int[] kept) {
        boolean changed = false;
        for (FaceConfig config : faces[type.ordinal()]) {
            changed |= moveFace(config, network, canLeave, kept);
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

    private static boolean moveFace(FaceConfig config, @Nullable UUID network, Predicate<UUID> canLeave, int[] kept) {
        UUID from = config.network();
        if (from != null && !from.equals(network) && !canLeave.test(from)) {
            kept[0]++;
            return false;
        }
        return config.setNetwork(network);
    }

    /** As redes distintas do tipo (sem nulo, na ordem em que aparecem) nas faces da presa, dos perfis e das exceções. */
    UUID[] networks(RemoteMachines remotes, ResourceType type) {
        Set<UUID> found = new LinkedHashSet<>();
        for (FaceConfig config : faces[type.ordinal()]) {
            addNetwork(found, config);
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

    /** Alguma face (presa, perfil ou exceção) de algum tipo está numa rede. */
    boolean hasNetwork(RemoteMachines remotes) {
        for (ResourceType type : TYPES) {
            if (networks(remotes, type).length > 0) {
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
     * Migração da rede da aba: põe {@code network} nas faces do tipo ainda sem rede da presa, dos
     * perfis e das exceções. Faces que já têm rede ficam. Sem avisar ninguém: o fim da carga avisa.
     */
    void migrateLegacyNetwork(RemoteMachines remotes, ResourceType type, UUID network) {
        for (FaceConfig config : faces[type.ordinal()]) {
            if (config.network() == null) {
                config.setNetwork(network);
            }
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
    int redstoneTypes(RemoteMachines remotes) {
        int types = 0;
        for (ResourceType type : TYPES) {
            if (usesRedstone(remotes, type)) {
                types |= NetworkManager.typeBit(type);
            }
        }
        return types;
    }

    /** Alguma face do tipo, na presa, nos perfis ou nas exceções, tem modo e controle por redstone. */
    private boolean usesRedstone(RemoteMachines remotes, ResourceType type) {
        for (FaceConfig config : faces[type.ordinal()]) {
            if (usesRedstone(config)) {
                return true;
            }
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

    // ------------------------------------------------------------------ NBT

    /** As faces fora do padrão, por tipo e lado (chave {@code faces}); vazio se todas estão no padrão. */
    CompoundTag save(HolderLookup.Provider registries) {
        CompoundTag facesTag = new CompoundTag();
        for (ResourceType type : TYPES) {
            CompoundTag typeTag = new CompoundTag();
            for (RelativeSide side : SIDES) {
                FaceConfig config = get(type, side);
                if (!config.isDefault()) {
                    typeTag.put(side.key(), config.save(registries));
                }
            }
            if (!typeTag.isEmpty()) {
                facesTag.put(type.key(), typeTag);
            }
        }
        return facesTag;
    }

    /** Lê as faces de {@code facesTag}; uma face que não está lá volta ao padrão. */
    void load(CompoundTag facesTag, HolderLookup.Provider registries) {
        for (ResourceType type : TYPES) {
            CompoundTag typeTag = facesTag.getCompound(type.key());
            for (RelativeSide side : SIDES) {
                FaceConfig config = get(type, side);
                if (typeTag.contains(side.key(), CompoundTag.TAG_COMPOUND)) {
                    config.copyFrom(FaceConfig.load(typeTag.getCompound(side.key()), registries));
                } else {
                    config.reset();
                }
            }
        }
    }
}
