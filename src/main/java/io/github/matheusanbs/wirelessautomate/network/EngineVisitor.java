package io.github.matheusanbs.wirelessautomate.network;

/**
 * Um trabalho visitado pelo laço de transferência do {@link NetworkManager} fora das portas dos roteadores, dentro do
 * mesmo orçamento do tick ({@link NetworkManager#addVisitor}). Dorme até {@link #wakeAt()} ou até o
 * {@link NetworkManager#wakeVisitor}; dormindo, custa ao laço só a leitura do {@code wakeAt}.
 */
public interface EngineVisitor {
    /** Resultado da visita: moveu algo. */
    int MOVED = NetworkManager.MOVED;
    /** Resultado da visita: parou num teto da visita e pode mover mais neste tick (ganha voltas extras se sobrar orçamento). */
    int MORE = NetworkManager.MORE;

    /** Uma visita no tick {@code now} do servidor: {@link #MOVED} e {@link #MORE} combinados, ou 0. */
    int visit(long now);

    /** O tick do servidor em que quer ser visitado de novo; {@link Long#MAX_VALUE} acorda só pelo {@code wakeVisitor}. */
    long wakeAt();
}
