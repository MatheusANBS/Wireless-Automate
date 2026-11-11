package io.github.matheusanbs.wirelessautomate.dock;

import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.Reference2ObjectOpenHashMap;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

/**
 * As Docas carregadas por posição de máquina vinculada, ativa ou não (fora do alcance e além do limite também): a
 * unicidade entre Docas ({@link DockLinks#otherDock}), o Ctrl + clique e o Configurador ({@link DockTuner}) consultam
 * aqui. O {@code RemoteIndex}, que o mixin consulta a cada {@code setChanged}, fica só com as ativas. Um por servidor,
 * descartado na parada. Mantido pela Doca a cada registro refeito ({@code onLoad}, vínculo, desvínculo, tier, config,
 * carga de NBT) e ao sair ({@code setRemoved}, {@code onChunkUnloaded}); uma Doca descarregada não aparece (limite
 * aceito pelo dono). Só no clique, nunca no motor; só a thread do servidor mexe nele.
 */
public final class DockIndex {
    private static @Nullable DockIndex instance;

    private final Reference2ObjectOpenHashMap<Level, Long2ObjectOpenHashMap<List<PatternDockBlockEntity>>> levels =
            new Reference2ObjectOpenHashMap<>();
    /** O que cada Doca registrou, para tirar sem varrer os níveis. */
    private final Reference2ObjectOpenHashMap<PatternDockBlockEntity, Registration> registered =
            new Reference2ObjectOpenHashMap<>();

    public static DockIndex get() {
        if (instance == null) {
            instance = new DockIndex();
        }
        return instance;
    }

    /** Descarta o estado ao parar o servidor. */
    public static void reset() {
        instance = null;
    }

    /** Refaz o registro de {@code dock} em {@code level} com {@code positions} ({@code BlockPos.asLong}). */
    void update(PatternDockBlockEntity dock, Level level, long[] positions) {
        remove(dock);
        if (positions.length == 0) {
            return;
        }
        Long2ObjectOpenHashMap<List<PatternDockBlockEntity>> byPos = levels.get(level);
        if (byPos == null) {
            byPos = new Long2ObjectOpenHashMap<>();
            levels.put(level, byPos);
        }
        for (long key : positions) {
            List<PatternDockBlockEntity> docks = byPos.get(key);
            if (docks == null) {
                docks = new ArrayList<>(1);
                byPos.put(key, docks);
            }
            docks.add(dock);
        }
        registered.put(dock, new Registration(level, positions));
    }

    /** Tira tudo o que {@code dock} registrou. */
    void remove(PatternDockBlockEntity dock) {
        Registration registration = registered.remove(dock);
        if (registration == null) {
            return;
        }
        Long2ObjectOpenHashMap<List<PatternDockBlockEntity>> byPos = levels.get(registration.level);
        if (byPos == null) {
            return;
        }
        for (long key : registration.positions) {
            List<PatternDockBlockEntity> docks = byPos.get(key);
            if (docks != null && docks.remove(dock) && docks.isEmpty()) {
                byPos.remove(key);
            }
        }
        if (byPos.isEmpty()) {
            levels.remove(registration.level);
        }
    }

    /** As Docas carregadas que têm a máquina em {@code pos} vinculada, na lista do índice (não mudar; só ler). */
    List<PatternDockBlockEntity> docksAt(Level level, BlockPos pos) {
        Long2ObjectOpenHashMap<List<PatternDockBlockEntity>> byPos = levels.get(level);
        List<PatternDockBlockEntity> docks = byPos == null ? null : byPos.get(pos.asLong());
        return docks == null ? List.of() : docks;
    }

    private record Registration(Level level, long[] positions) {
    }
}
