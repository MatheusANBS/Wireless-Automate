package io.github.matheusanbs.wirelessautomate.range;

import io.github.matheusanbs.wirelessautomate.network.FaceConfig;
import io.github.matheusanbs.wirelessautomate.network.ResourceType;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;

/**
 * Configuração das faces de uma máquina remota: um {@link FaceConfig} por tipo de recurso e por
 * {@link RemoteSide}, sempre preenchido. É o perfil de um grupo ({@link MachineGroup}) ou a exceção de
 * uma máquina ({@link RemoteMachine}). Sem cartões: eles ficam só no grupo (decisão 9 da spec).
 *
 * <p>Mutável: no roteador, mude pelos métodos por alvo do {@code RouterBlockEntity}, que salvam e
 * avisam o motor.
 */
public final class MachineConfig {
    private static final ResourceType[] TYPES = ResourceType.values();
    private static final RemoteSide[] SIDES = RemoteSide.values();

    /** [tipo][lado remoto], nunca nulo. */
    private final FaceConfig[][] faces = new FaceConfig[TYPES.length][SIDES.length];

    public MachineConfig() {
        for (FaceConfig[] row : faces) {
            for (int i = 0; i < row.length; i++) {
                row[i] = new FaceConfig();
            }
        }
    }

    /** A face de {@code type} no lado {@code side}. Nunca nula; mudar a devolvida muda esta configuração. */
    public FaceConfig face(ResourceType type, RemoteSide side) {
        return faces[type.ordinal()][side.ordinal()];
    }

    /** Uma cópia independente (os {@code FaceConfig} são novos; o {@code Filter} é imutável). */
    public MachineConfig copy() {
        MachineConfig copy = new MachineConfig();
        copy.copyFrom(this);
        return copy;
    }

    /** Copia todas as faces de {@code other}. Devolve {@code true} se algo mudou. */
    public boolean copyFrom(MachineConfig other) {
        boolean changed = false;
        for (int t = 0; t < faces.length; t++) {
            for (int s = 0; s < faces[t].length; s++) {
                changed |= faces[t][s].copyFrom(other.faces[t][s]);
            }
        }
        return changed;
    }

    /** Modo, prioridade, redstone e filtro iguais em todas as faces de todos os tipos. */
    public boolean sameAs(MachineConfig other) {
        for (int t = 0; t < faces.length; t++) {
            for (int s = 0; s < faces[t].length; s++) {
                if (!faces[t][s].equals(other.faces[t][s])) {
                    return false;
                }
            }
        }
        return true;
    }

    /** Todas as faces no padrão (um perfil recém-criado). */
    public boolean isDefault() {
        for (FaceConfig[] row : faces) {
            for (FaceConfig config : row) {
                if (!config.isDefault()) {
                    return false;
                }
            }
        }
        return true;
    }

    /** NBT no formato das {@code faces} do roteador: tipo → lado → {@link FaceConfig#save}, sem os padrões. */
    public CompoundTag save(HolderLookup.Provider registries) {
        CompoundTag tag = new CompoundTag();
        for (ResourceType type : TYPES) {
            CompoundTag typeTag = new CompoundTag();
            for (RemoteSide side : SIDES) {
                FaceConfig config = face(type, side);
                if (!config.isDefault()) {
                    typeTag.put(side.key(), config.save(registries));
                }
            }
            if (!typeTag.isEmpty()) {
                tag.put(type.key(), typeTag);
            }
        }
        return tag;
    }

    /** Lê o formato de {@link #save}; tipo ou lado ausente fica no padrão. */
    public static MachineConfig load(CompoundTag tag, HolderLookup.Provider registries) {
        MachineConfig config = new MachineConfig();
        for (ResourceType type : TYPES) {
            CompoundTag typeTag = tag.getCompound(type.key());
            for (RemoteSide side : SIDES) {
                if (typeTag.contains(side.key(), CompoundTag.TAG_COMPOUND)) {
                    config.face(type, side).copyFrom(FaceConfig.load(typeTag.getCompound(side.key()), registries));
                }
            }
        }
        return config;
    }
}
