package io.github.matheusanbs.wirelessautomate.resonator;

import java.util.HashMap;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

/**
 * O índice do servidor porta → Núcleo: as Portas não têm block entity, e a capability delas acha o Núcleo
 * por aqui, sem busca no mundo. Preenchido pela {@link ResonatorFormation} ao montar (e na revalidação do
 * {@code onLoad} do Núcleo); só a thread do servidor mexe.
 */
public final class ResonatorPorts {
    private static final Map<ResourceKey<Level>, Map<BlockPos, BlockPos>> PORTS = new HashMap<>();

    private ResonatorPorts() {
    }

    /** Liga a Porta ao Núcleo; devolve se mudou alguma coisa (a Porta era de outro Núcleo ou não estava). */
    public static boolean register(ResourceKey<Level> dimension, BlockPos port, BlockPos core) {
        BlockPos old = PORTS.computeIfAbsent(dimension, key -> new HashMap<>()).put(port.immutable(), core.immutable());
        return !core.equals(old);
    }

    /** Tira a Porta só se ela ainda for deste Núcleo: com uma parede dividida, outro Núcleo pode tê-la registrado depois. */
    public static void unregister(ResourceKey<Level> dimension, BlockPos port, BlockPos core) {
        Map<BlockPos, BlockPos> ports = PORTS.get(dimension);
        if (ports != null) {
            ports.remove(port, core);
        }
    }

    /** O Núcleo registrado para a Porta, carregado ou não, ou {@code null}; só leitura do índice. */
    public static @Nullable BlockPos registered(ResourceKey<Level> dimension, BlockPos port) {
        Map<BlockPos, BlockPos> ports = PORTS.get(dimension);
        return ports == null ? null : ports.get(port);
    }

    /** O Núcleo montado e carregado desta Porta, ou {@code null}. */
    public static @Nullable ResonanceCoreBlockEntity core(Level level, BlockPos port) {
        if (level.isClientSide) {
            return null;
        }
        Map<BlockPos, BlockPos> ports = PORTS.get(level.dimension());
        BlockPos core = ports == null ? null : ports.get(port);
        if (core == null || !level.isLoaded(core)) {
            return null;
        }
        return level.getBlockEntity(core) instanceof ResonanceCoreBlockEntity entity && entity.formed() ? entity : null;
    }

    /** Parada do servidor: o próximo mundo começa sem Portas. */
    public static void reset() {
        PORTS.clear();
    }
}
