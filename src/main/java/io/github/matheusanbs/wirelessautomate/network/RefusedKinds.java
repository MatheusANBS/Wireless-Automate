package io.github.matheusanbs.wirelessautomate.network;

import java.util.Arrays;

/**
 * Os tipos que um destino por tanque (fluido ou químico) recusou desde a última mudança dele: o par do
 * {@link ItemHints} para os recursos por tanque. Um tanque com lava recusa a água sem estar cheio, e dormir
 * como cheio o deixaria de fora também para a lava que ele aceita; com isto a visita só pula o destino para
 * os tipos recusados, sem simular e sem dormir. Só existe na porta que recusou algum tipo tendo lugar para
 * outros ({@link Port#refusedKinds}): um tanque que aceita tudo nunca cria.
 *
 * <p>A classe não conhece os tipos (o químico é do Mekanism, opcional, e fica como o id): quem usa dá a
 * comparação ({@link Same}) e guarda a chave já copiada. Anel de tamanho fixo. Como no {@link ItemHints},
 * qualquer mudança na máquina apaga a lista ({@link #clear}, pelo aviso que acorda a porta), e a máquina que
 * muda sem avisar não a deixa para sempre: ela vence no teto do sono do destino, contado da primeira recusa.
 */
final class RefusedKinds {
    static final int SIZE = 4;

    /** Como comparar uma chave guardada com uma oferecida. */
    interface Same {
        boolean test(Object stored, Object offered);
    }

    /** Chaves comparadas por {@code equals} (o id de um químico). */
    static final Same EQUALS = Object::equals;

    private final Object[] keys = new Object[SIZE];
    private int size;
    private int next;
    /** O tick da primeira recusa da lista de agora (vale só com {@code size > 0}). */
    private long since;

    /**
     * O destino recusou {@code key} desde a última mudança dele. Passados {@code maxTicks} desde a primeira recusa
     * da lista (o teto do sono do destino), a lista vence e é apagada: o tipo volta a ser oferecido.
     */
    boolean contains(Object key, Same same, long now, int maxTicks) {
        if (size > 0 && now - since > maxTicks) {
            clear();
            return false;
        }
        return has(key, same);
    }

    private boolean has(Object key, Same same) {
        for (int i = 0; i < size; i++) {
            if (same.test(keys[i], key)) {
                return true;
            }
        }
        return false;
    }

    /** Anota a recusa de {@code key} (já uma cópia de quem chama) no tick {@code now}; com a lista cheia, sai a mais antiga. */
    void add(Object key, Same same, long now) {
        if (has(key, same)) {
            return;
        }
        if (size == 0) {
            since = now;
        }
        keys[next] = key;
        next = (next + 1) % SIZE;
        if (size < SIZE) {
            size++;
        }
    }

    /** A máquina mudou: as recusas não valem mais. */
    void clear() {
        if (size > 0) {
            Arrays.fill(keys, null);
            size = 0;
            next = 0;
        }
    }
}
