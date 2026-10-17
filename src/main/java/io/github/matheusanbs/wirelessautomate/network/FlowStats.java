package io.github.matheusanbs.wirelessautomate.network;

import java.util.UUID;

/**
 * Os Fluxos de uma rede num tipo, como o {@link FlowSampler} os viu na última amostra: os totais da
 * janela ({@link FlowWindow}, até {@link FlowWindow#SECONDS} segundos) e os estados das portas naquela
 * amostra. Imutável; a unidade é a do tipo (itens, mB, FE, Source, unidades de químico) e o segundo é
 * de 20 ticks.
 *
 * @param network  a rede
 * @param type     o tipo
 * @param produced o que as faces Extrai entregaram na janela ("entra")
 * @param consumed o que as faces Insere receberam na janela ("sai")
 * @param stored   o que as faces Armazém receberam na janela
 * @param released o que as faces Armazém entregaram na janela
 * @param seconds  os segundos que a janela cobre, de 0 (a amostra que só zerou) a {@link FlowWindow#SECONDS}
 * @param states   os estados das portas na última amostra
 */
public record FlowStats(UUID network, ResourceType type, long produced, long consumed, long stored, long released,
        int seconds, States states) {

    /** O que entra por segundo; 0 enquanto a janela não tem nenhum segundo. */
    public double producedPerSecond() {
        return perSecond(produced);
    }

    /** O que sai por segundo. */
    public double consumedPerSecond() {
        return perSecond(consumed);
    }

    /** O que os Armazéns recebem por segundo. */
    public double storedPerSecond() {
        return perSecond(stored);
    }

    /** O que os Armazéns dão por segundo. */
    public double releasedPerSecond() {
        return perSecond(released);
    }

    /** Saldo dos Armazéns por segundo: positivo enchendo, negativo esvaziando. */
    public double balancePerSecond() {
        return perSecond(stored - released);
    }

    /** A janela cobre os {@link FlowWindow#SECONDS} segundos: acabou o "medindo…". */
    public boolean filled() {
        return seconds == FlowWindow.SECONDS;
    }

    private double perSecond(long total) {
        return seconds == 0 ? 0 : (double) total / seconds;
    }

    /**
     * Os estados das portas da rede no tipo, contados na última amostra. Uma face Armazém conta como
     * origem e como destino.
     *
     * @param sourcesMoving      origens que entregaram algo desde a amostra anterior
     * @param sourcesEmpty       origens dormindo sem o que dar
     * @param sourcesWaiting     origens dormindo com algo a dar (todos os destinos dormiam ou recusaram),
     *                           ou sem nenhum destino ao alcance
     * @param destinationsMoving destinos que receberam algo desde a amostra anterior
     * @param destinationsFull   destinos dormindo depois de recusas seguidas ({@link NodeProbe#isFull})
     * @param redstoneStopped    faces com modo e nesta rede que o redstone desliga (da configuração dos
     *                           nós carregados: presa, perfis e exceções)
     */
    public record States(int sourcesMoving, int sourcesEmpty, int sourcesWaiting, int destinationsMoving,
            int destinationsFull, int redstoneStopped) {
        public static final States NONE = new States(0, 0, 0, 0, 0, 0);
    }
}
