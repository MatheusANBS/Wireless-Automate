package io.github.matheusanbs.wirelessautomate.client.router;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import org.jetbrains.annotations.Nullable;

/**
 * A última face escolhida de cada alvo de edição de cada roteador (lógica pura, só os mais recentes): reabrir
 * a tela ou voltar do filtro devolve a face, na presa e também numa máquina ou grupo do alcance.
 *
 * @param <S> o roteador (dimensão e posição)
 * @param <F> a face
 */
final class FaceMemory<S, F> {
    private record Key<S>(S spot, Object target) {}

    private final Map<Key<S>, F> faces;

    FaceMemory(int limit) {
        this.faces = new LinkedHashMap<>(16, 0.75f, true) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<Key<S>, F> eldest) {
                return size() > limit;
            }
        };
    }

    void put(S spot, Object target, F face) {
        faces.put(new Key<>(spot, Objects.requireNonNull(target)), face);
    }

    @Nullable F get(S spot, Object target) {
        return faces.get(new Key<>(spot, target));
    }
}
