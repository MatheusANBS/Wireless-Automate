package io.github.matheusanbs.wirelessautomate.range;

import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.Reference2ObjectOpenHashMap;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;

/**
 * Os donos carregados (Docas, roteadores) por posição de máquina vinculada, ativa ou não (fora do alcance e além do
 * limite também). O dos roteadores ({@link RouterOwners}) serve à recusa de outro roteador e ao Ctrl + clique do
 * Vinculador; o das Docas ({@code dock/DockIndex}), à recusa de outra Doca, ao Ctrl + clique e ao Configurador. O
 * {@link RemoteIndex}, que o mixin consulta a cada {@code setChanged}, fica só com as ativas. Um por servidor,
 * descartado na parada pelo dono da instância. Mantido pelo dono a cada registro refeito e ao sair; um dono
 * descarregado não aparece (limite aceito). Só no clique, nunca no motor; só a thread do servidor mexe nele.
 *
 * <p>Memória: a posição com um dono só (o caso normal) guarda o dono direto no mapa; uma lista ({@link Many}) só
 * quando há dois ou mais.
 *
 * @param <T> o tipo do dono
 */
public final class OwnerIndex<T> {
    /** Por nível, o valor de cada posição: o dono, ou um {@link Many} com dois ou mais. */
    private final Reference2ObjectOpenHashMap<Level, Long2ObjectOpenHashMap<Object>> levels =
            new Reference2ObjectOpenHashMap<>();
    /** O que cada dono registrou, para tirar sem varrer os níveis. */
    private final Reference2ObjectOpenHashMap<T, Registration> registered = new Reference2ObjectOpenHashMap<>();

    /** Refaz o registro de {@code owner} em {@code level} com {@code positions} ({@code BlockPos.asLong}). */
    @SuppressWarnings("unchecked")
    public void update(T owner, Level level, long[] positions) {
        remove(owner);
        if (positions.length == 0) {
            return;
        }
        Long2ObjectOpenHashMap<Object> byPos = levels.get(level);
        if (byPos == null) {
            byPos = new Long2ObjectOpenHashMap<>();
            levels.put(level, byPos);
        }
        for (long key : positions) {
            Object current = byPos.putIfAbsent(key, owner);
            if (current == null) {
                continue;
            }
            if (current instanceof Many<?> many) {
                ((Many<T>) many).add(owner);
            } else {
                Many<T> many = new Many<>();
                many.add((T) current);
                many.add(owner);
                byPos.put(key, many);
            }
        }
        registered.put(owner, new Registration(level, positions));
    }

    /** Tira tudo o que {@code owner} registrou. */
    public void remove(T owner) {
        Registration registration = registered.remove(owner);
        if (registration == null) {
            return;
        }
        Long2ObjectOpenHashMap<Object> byPos = levels.get(registration.level);
        if (byPos == null) {
            return;
        }
        for (long key : registration.positions) {
            Object current = byPos.get(key);
            if (current == owner) {
                byPos.remove(key);
            } else if (current instanceof Many<?> many && many.remove(owner) && many.size() == 1) {
                // volta a guardar o dono que sobrou direto
                byPos.put(key, many.get(0));
            }
        }
        if (byPos.isEmpty()) {
            levels.remove(registration.level);
        }
    }

    /**
     * Os donos carregados que têm a máquina em {@code pos} vinculada: com um só, uma lista nova dele (só no clique);
     * com dois ou mais, a lista do índice (não mudar; só ler).
     */
    @SuppressWarnings("unchecked")
    public List<T> at(Level level, BlockPos pos) {
        Long2ObjectOpenHashMap<Object> byPos = levels.get(level);
        Object owners = byPos == null ? null : byPos.get(pos.asLong());
        if (owners == null) {
            return List.of();
        }
        return owners instanceof Many<?> many ? (List<T>) many : List.of((T) owners);
    }

    private record Registration(Level level, long[] positions) {
    }

    /** Os donos de uma posição com dois ou mais (uma classe própria: o dono nunca é confundido com a lista). */
    private static final class Many<T> extends ArrayList<T> {
        Many() {
            super(2);
        }
    }
}
