package io.github.matheusanbs.wirelessautomate.range;

import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.Reference2ObjectOpenHashMap;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;

/**
 * Os donos carregados (Docas, roteadores) por posição de máquina vinculada, ativa ou não (fora do alcance e além do
 * limite também): a unicidade de dono, o Ctrl + clique e o Configurador consultam aqui. O {@link RemoteIndex}, que o
 * mixin consulta a cada {@code setChanged}, fica só com as ativas. Um por servidor, descartado na parada pelo dono da
 * instância. Mantido pelo dono a cada registro refeito e ao sair; um dono descarregado não aparece (limite aceito).
 * Só no clique, nunca no motor; só a thread do servidor mexe nele.
 *
 * @param <T> o tipo do dono
 */
public final class OwnerIndex<T> {
    private final Reference2ObjectOpenHashMap<Level, Long2ObjectOpenHashMap<List<T>>> levels =
            new Reference2ObjectOpenHashMap<>();
    /** O que cada dono registrou, para tirar sem varrer os níveis. */
    private final Reference2ObjectOpenHashMap<T, Registration> registered = new Reference2ObjectOpenHashMap<>();

    /** Refaz o registro de {@code owner} em {@code level} com {@code positions} ({@code BlockPos.asLong}). */
    public void update(T owner, Level level, long[] positions) {
        remove(owner);
        if (positions.length == 0) {
            return;
        }
        Long2ObjectOpenHashMap<List<T>> byPos = levels.get(level);
        if (byPos == null) {
            byPos = new Long2ObjectOpenHashMap<>();
            levels.put(level, byPos);
        }
        for (long key : positions) {
            List<T> owners = byPos.get(key);
            if (owners == null) {
                owners = new ArrayList<>(1);
                byPos.put(key, owners);
            }
            owners.add(owner);
        }
        registered.put(owner, new Registration(level, positions));
    }

    /** Tira tudo o que {@code owner} registrou. */
    public void remove(T owner) {
        Registration registration = registered.remove(owner);
        if (registration == null) {
            return;
        }
        Long2ObjectOpenHashMap<List<T>> byPos = levels.get(registration.level);
        if (byPos == null) {
            return;
        }
        for (long key : registration.positions) {
            List<T> owners = byPos.get(key);
            if (owners != null && owners.remove(owner) && owners.isEmpty()) {
                byPos.remove(key);
            }
        }
        if (byPos.isEmpty()) {
            levels.remove(registration.level);
        }
    }

    /** Os donos carregados que têm a máquina em {@code pos} vinculada, na lista do índice (não mudar; só ler). */
    public List<T> at(Level level, BlockPos pos) {
        Long2ObjectOpenHashMap<List<T>> byPos = levels.get(level);
        List<T> owners = byPos == null ? null : byPos.get(pos.asLong());
        return owners == null ? List.of() : owners;
    }

    private record Registration(Level level, long[] positions) {
    }
}
