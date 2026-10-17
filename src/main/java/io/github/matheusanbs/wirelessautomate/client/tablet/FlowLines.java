package io.github.matheusanbs.wirelessautomate.client.tablet;

import io.github.matheusanbs.wirelessautomate.client.RateFormat;
import io.github.matheusanbs.wirelessautomate.network.FlowStats;
import io.github.matheusanbs.wirelessautomate.network.FlowStats.States;
import io.github.matheusanbs.wirelessautomate.network.FlowWindow;
import io.github.matheusanbs.wirelessautomate.network.ResourceType;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import org.jetbrains.annotations.Nullable;

/**
 * As linhas da aba Fluxos do Tablet, sem classes do Minecraft: o total de cada tipo somando as redes não
 * pausadas (Por tipo), as redes na ordem da tela (Por rede), o texto curto dos estados e os números.
 * Os números são por segundo na unidade do tipo; a energia vai para a tela por tick ({@link #screenUnit}).
 */
final class FlowLines {
    /** Abaixo disso, um número por segundo conta como zero na tela. */
    static final double EPSILON = 0.05;

    private FlowLines() {
    }

    /** Uma rede dos Fluxos, com o nome e a cor que o cabeçalho do Tablet dá a ela. */
    record Net(UUID id, String name, int color, boolean paused, long nanos, List<FlowStats> types) {
        /** Os Fluxos do tipo nesta rede, ou {@code null} se ele não está em uso nela. */
        @Nullable FlowStats type(ResourceType type) {
            for (FlowStats stats : types) {
                if (stats.type() == type) {
                    return stats;
                }
            }
            return null;
        }
    }

    /**
     * Os números de uma linha, por segundo.
     *
     * @param seconds os segundos medidos: 0 ainda sem nenhum, {@link FlowWindow#SECONDS} com a janela cheia
     */
    record Totals(double produced, double consumed, double stored, double released, int seconds, States states) {
        static final Totals NONE = new Totals(0, 0, 0, 0, FlowWindow.SECONDS, States.NONE);

        static Totals of(FlowStats stats) {
            return new Totals(stats.producedPerSecond(), stats.consumedPerSecond(), stats.storedPerSecond(),
                    stats.releasedPerSecond(), stats.seconds(), stats.states());
        }

        /** A soma: o por segundo de cada um (as janelas podem cobrir tempos diferentes) e o menor tempo medido. */
        static Totals sum(List<FlowStats> parts) {
            Totals total = NONE;
            for (FlowStats stats : parts) {
                total = total.plus(of(stats));
            }
            return total;
        }

        Totals plus(Totals other) {
            States a = states;
            States b = other.states;
            return new Totals(produced + other.produced, consumed + other.consumed, stored + other.stored,
                    released + other.released, Math.min(seconds, other.seconds),
                    new States(a.sourcesMoving() + b.sourcesMoving(), a.sourcesEmpty() + b.sourcesEmpty(),
                            a.sourcesWaiting() + b.sourcesWaiting(), a.destinationsMoving() + b.destinationsMoving(),
                            a.destinationsFull() + b.destinationsFull(), a.redstoneStopped() + b.redstoneStopped()));
        }

        /** O saldo dos Armazéns: positivo enchendo, negativo esvaziando. */
        double balance() {
            return stored - released;
        }

        /** Ainda sem nenhum segundo medido: nada a mostrar. */
        boolean measuring() {
            return seconds == 0;
        }

        /** A janela cobre os {@link FlowWindow#SECONDS} segundos. */
        boolean filled() {
            return seconds >= FlowWindow.SECONDS;
        }
    }

    /** Uma rede dentro de um tipo, ou um tipo dentro de uma rede. */
    record Part(Net net, ResourceType type, Totals totals) {
    }

    /**
     * Uma linha Por tipo: o total das redes não pausadas e as redes (da maior vazão para a menor).
     *
     * @param withoutNetwork faces do tipo com modo e sem rede
     */
    record TypeLine(ResourceType type, Totals totals, int withoutNetwork, List<Part> networks) {
    }

    /** Uma rede Por rede: o total dos tipos dela (os estados do cabeçalho) e os tipos, na ordem do registro. */
    record NetLine(Net net, Totals totals, List<Part> types) {
    }

    /**
     * Uma linha por tipo em uso, na ordem de {@code types}: com alguma rede não pausada nele ou alguma face sem
     * rede. As redes pausadas ficam de fora dos totais.
     *
     * @param withoutNetwork faces com modo e sem rede, por {@link ResourceType#ordinal()}
     */
    static List<TypeLine> byType(List<Net> nets, List<ResourceType> types, List<Integer> withoutNetwork) {
        List<TypeLine> lines = new ArrayList<>();
        for (ResourceType type : types) {
            List<Part> parts = new ArrayList<>();
            Totals total = Totals.NONE;
            for (Net net : nets) {
                FlowStats stats = net.paused() ? null : net.type(type);
                if (stats != null) {
                    Totals totals = Totals.of(stats);
                    parts.add(new Part(net, type, totals));
                    total = total.plus(totals);
                }
            }
            int without = type.ordinal() < withoutNetwork.size() ? withoutNetwork.get(type.ordinal()) : 0;
            if (parts.isEmpty() && without == 0) {
                continue;
            }
            parts.sort(Comparator.<Part>comparingDouble(p -> -(p.totals.produced() + p.totals.consumed()))
                    .thenComparing(p -> p.net.name(), String.CASE_INSENSITIVE_ORDER));
            lines.add(new TypeLine(type, total, without, List.copyOf(parts)));
        }
        return lines;
    }

    /** As redes pausadas, pelo nome. */
    static List<Net> paused(List<Net> nets) {
        List<Net> paused = new ArrayList<>();
        for (Net net : nets) {
            if (net.paused()) {
                paused.add(net);
            }
        }
        paused.sort(Comparator.comparing(Net::name, String.CASE_INSENSITIVE_ORDER));
        return paused;
    }

    /** As redes Por rede: as que rodam pelo nome, depois as pausadas pelo nome; os tipos de {@code types} em uso. */
    static List<NetLine> byNetwork(List<Net> nets, List<ResourceType> types) {
        List<NetLine> lines = new ArrayList<>();
        for (Net net : nets) {
            List<Part> parts = new ArrayList<>();
            Totals total = Totals.NONE;
            for (ResourceType type : types) {
                FlowStats stats = net.type(type);
                if (stats != null) {
                    Totals totals = Totals.of(stats);
                    parts.add(new Part(net, type, totals));
                    total = total.plus(totals);
                }
            }
            lines.add(new NetLine(net, total, List.copyOf(parts)));
        }
        lines.sort(Comparator.<NetLine, Boolean>comparing(l -> l.net.paused())
                .thenComparing(l -> l.net.name(), String.CASE_INSENSITIVE_ORDER));
        return lines;
    }

    /** O que entra no texto curto dos estados, só quando não é zero. */
    enum StateKind { WAITING, FULL, REDSTONE, NO_NETWORK }

    record StatePart(StateKind kind, int count) {
    }

    /**
     * O texto curto dos estados: só os que pedem atenção e não são zero (origens esperando destino, destinos
     * cheios, faces paradas por redstone e sem rede). Movendo e vazia ficam só na dica.
     */
    static List<StatePart> shortStates(States states, int withoutNetwork) {
        List<StatePart> parts = new ArrayList<>(4);
        add(parts, StateKind.WAITING, states.sourcesWaiting());
        add(parts, StateKind.FULL, states.destinationsFull());
        add(parts, StateKind.REDSTONE, states.redstoneStopped());
        add(parts, StateKind.NO_NETWORK, withoutNetwork);
        return parts;
    }

    private static void add(List<StatePart> parts, StateKind kind, int count) {
        if (count > 0) {
            parts.add(new StatePart(kind, count));
        }
    }

    /** O por segundo na unidade da tela do tipo: por tick para a energia, por segundo para os outros. */
    static double screenUnit(ResourceType type, double perSecond) {
        return type.ratePerTick() ? perSecond / 20 : perSecond;
    }

    /** Zero na tela. */
    static boolean zero(double value) {
        return Math.abs(value) < EPSILON;
    }

    /**
     * O número curto: uma casa abaixo de 10 quando não é inteiro ({@code 2.5}), senão inteiro com o sufixo do
     * {@link RateFormat} ({@code 370}, {@code 16.5k}).
     */
    static String amount(double value) {
        double abs = Math.abs(value);
        String sign = value < 0 && !zero(value) ? "-" : "";
        if (abs < EPSILON) {
            return "0";
        }
        if (abs < 10 && Math.abs(abs - Math.rint(abs)) >= EPSILON) {
            return sign + String.format(Locale.ROOT, "%.1f", abs);
        }
        return sign + RateFormat.abbreviate(Math.round(abs));
    }

    /** O número com o sinal sempre à vista ({@code +16.5k}, {@code -4}). */
    static String signed(double value) {
        return (value > 0 && !zero(value) ? "+" : "") + amount(value);
    }
}
