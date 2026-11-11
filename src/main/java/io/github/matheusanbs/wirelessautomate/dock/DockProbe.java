package io.github.matheusanbs.wirelessautomate.dock;

import org.jetbrains.annotations.Nullable;

/**
 * O sensor das medições do uso intenso da Doca com o AE2 de verdade ({@code gametest/DockQuantumMedida}), em que quem
 * chama a entrega é o provedor do AE2 e não o teste: conta as perguntas, as ofertas (com o motivo da recusa e o custo)
 * e as visitas da volta. Fora da medição, {@link #listener} é nulo e cada ponto custa uma leitura de campo estático.
 * Só a thread do servidor mexe aqui.
 */
public final class DockProbe {
    /** O ouvinte da medição em curso; nulo fora dela. */
    public static @Nullable Listener listener;

    private DockProbe() {
    }

    public interface Listener {
        /** O provedor perguntou se o lado aceita (o {@code acceptsPlans}) e ouviu {@code accepts}. */
        void asked(boolean accepts);

        /**
         * Uma oferta do provedor terminou: aceita, ou recusada pelo motivo {@code reason} ({@link DockStatus}), em
         * {@code nanos} (da conversão das entradas à entrega).
         */
        void offered(boolean accepted, int reason, long nanos);

        /** Uma visita da volta ({@link DockReturn#visit}) custou {@code nanos}. */
        void returned(long nanos);
    }
}
