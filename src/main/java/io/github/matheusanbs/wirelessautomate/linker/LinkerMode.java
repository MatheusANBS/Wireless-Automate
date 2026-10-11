package io.github.matheusanbs.wirelessautomate.linker;

import com.mojang.serialization.Codec;
import java.util.Locale;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.util.StringRepresentable;
import net.neoforged.neoforge.network.codec.NeoForgeStreamCodecs;

/**
 * Modo do Vinculador: um roteador por clique, todos de uma área, ou máquinas para o roteador
 * escolhido (Upgrade de Alcance). O Configurador usa o mesmo enum ({@code configurator_mode}), mas só
 * Único e Área: para ele, {@link #MACHINES} vale como Único.
 */
public enum LinkerMode implements StringRepresentable {
    SINGLE,
    AREA,
    /** Vinculador: clique numa máquina a vincula ao roteador escolhido; Shift + clique marca a área. */
    MACHINES;

    public static final Codec<LinkerMode> CODEC = StringRepresentable.fromEnum(LinkerMode::values);
    public static final StreamCodec<RegistryFriendlyByteBuf, LinkerMode> STREAM_CODEC =
            NeoForgeStreamCodecs.enumCodec(LinkerMode.class);

    @Override
    public String getSerializedName() {
        return name().toLowerCase(Locale.ROOT);
    }

    /** Alternância do Configurador: Único ↔ Área ({@link #MACHINES} conta como Único). */
    public LinkerMode toggled() {
        return this == AREA ? SINGLE : AREA;
    }

    /** O ciclo do Vinculador: Único → Área → Máquinas → Único. */
    public LinkerMode next() {
        return switch (this) {
            case SINGLE -> AREA;
            case AREA -> MACHINES;
            case MACHINES -> SINGLE;
        };
    }

    /** Shift + clique num bloco marca um canto da área (Área e Máquinas). */
    public boolean marksArea() {
        return this != SINGLE;
    }
}
