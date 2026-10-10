package io.github.matheusanbs.wirelessautomate.network;

import io.github.matheusanbs.wirelessautomate.WirelessAutomate;
import io.github.matheusanbs.wirelessautomate.compat.mekanism.MekanismChemicals;
import io.github.matheusanbs.wirelessautomate.compat.mekanism.MekanismChemicals.Subtype;
import io.github.matheusanbs.wirelessautomate.filter.Filter;
import io.github.matheusanbs.wirelessautomate.filter.FilterSet;
import io.github.matheusanbs.wirelessautomate.filter.StockLimit;
import java.util.List;
import mekanism.api.Action;
import mekanism.api.chemical.Chemical;
import mekanism.api.chemical.ChemicalStack;
import mekanism.api.chemical.IChemicalHandler;
import net.minecraft.resources.ResourceLocation;

/**
 * Move químicos do Mekanism (gases, líquidos de infusão, pigmentos, slurries) de uma origem para os
 * destinos dela, tanque a tanque. Mesmo protocolo dos fluidos ({@link FluidTransfer}): simula a
 * retirada, simula a entrega, retira de verdade só o aceito e entrega exatamente o que saiu; a
 * sobra de um destino que mentiu volta para a origem, e o que nem ela aceitar se perde (vai para o
 * log). Filtro e estoque pelo id do químico ({@link FilterSet#testChemical}); a vazão usa o limite
 * de fluido do tier (mB por segundo).
 *
 * <p>Porte 1.20.1 (Mekanism 10.4, D4): cada tipo de químico tem o seu handler ({@link Subtype}), e a face
 * pode oferecer vários. Os tanques dos tipos que a face oferece formam uma fila só, na ordem gás, infusão,
 * pigmento, slurry: o cursor, o limite de tanques por visita e a contagem de tanques ociosos valem para a
 * fila inteira, como os tanques de um handler só no {@code main}. A vazão da porta (a da aba Químicos) também
 * é uma só: o limite da face vale para a soma dos quatro tipos. Num destino, só conta o handler do mesmo tipo;
 * um destino com químicos mas sem esse tipo é pulado sem dormir.
 *
 * <p>Usa a API do Mekanism: só é carregada com ele presente (o motor chama por {@link Chemicals}).
 */
final class ChemicalTransfer {
    /** Tanques examinados por visita: máquina com mais tanques continua do cursor na visita seguinte. */
    static final int MAX_TANKS_PER_VISIT = 16;
    private static final int SUBTYPES = MekanismChemicals.SUBTYPES.size();
    /** O último {@link #moveChemical} pôs algum destino para dormir. */
    private static boolean destinationSlept;
    /** Handlers da origem por tipo, na visita em curso (só a thread do servidor; sem alocar por visita). */
    private static final Object[] HANDLERS = new Object[SUBTYPES];
    /** Tanques de cada handler de {@link #HANDLERS} (0 sem handler). */
    private static final int[] TANKS = new int[SUBTYPES];

    /**
     * Uma visita. Devolve {@code true} se moveu algo. Tanques a partir do cursor da porta, como os
     * fluidos ({@link FluidTransfer#move}).
     */
    static boolean move(Port source, long now) {
        try {
            return visit(source, now);
        } finally {
            // Mesmo com exceção de um handler do Mekanism: os handlers não prendem block entities removidos.
            clear();
        }
    }

    private static boolean visit(Port source, long now) {
        Object[] handlers = HANDLERS;
        int[] tanksOf = TANKS;
        boolean any = false;
        for (int s = 0; s < SUBTYPES; s++) {
            Object handler = source.node.chemicals(source.face, s);
            handlers[s] = handler;
            any |= handler != null;
        }
        RoundRobinOrder<Port> order = source.order;
        if (!any || order == null) {
            SourceSleep.nothingToMove(source, now);
            return false;
        }
        long tokens = source.limiter.available(now);
        if (tokens <= 0) {
            return false;
        }
        List<Port> pass = order.pass();
        if (!NetworkManager.hasAwakeDestination(pass, now)) {
            SourceSleep.untilDestinations(source, pass, now);
            return false;
        }
        int tanks = 0;
        for (int s = 0; s < SUBTYPES; s++) {
            int n = handlers[s] == null ? 0 : MekanismChemicals.tanks(handlers[s]);
            tanksOf[s] = n;
            tanks += n;
        }
        if (tanks <= 0) {
            SourceSleep.nothingToMove(source, now);
            return false;
        }
        int limit = Math.min(tanks, MAX_TANKS_PER_VISIT);
        int tank = source.slotCursor < tanks ? source.slotCursor : 0;
        int scanned = 0;
        long moved = 0;
        boolean destinationsAsleep = false;
        while (scanned < limit) {
            int current = tank;
            scanned++;
            tank = tank + 1 == tanks ? 0 : tank + 1;
            // O tanque da fila: o tipo e o tanque dentro do handler dele.
            int s = 0;
            int inHandler = current;
            while (inHandler >= tanksOf[s]) {
                inHandler -= tanksOf[s];
                s++;
            }
            long amount = visitTank(source, MekanismChemicals.SUBTYPES.get(s), handlers[s], inHandler, tokens,
                    order, pass, now);
            if (amount < 0) {
                continue;
            }
            tokens -= amount;
            moved += amount;
            if (tokens <= 0) {
                // Sem saldo: o tanque pode ter mais, continua dele na próxima visita.
                tank = current;
                break;
            }
            if (destinationSlept && !NetworkManager.hasAwakeDestination(pass, now)) {
                destinationsAsleep = true;
                break;
            }
        }
        source.slotCursor = tank;
        if (moved > 0) {
            source.node.addMoved(source.type, moved);
            source.idleSlots = 0;
            source.limiter.consume(moved);
            source.sourceBackoff.wake();
            return true;
        }
        source.idleSlots += scanned;
        if (destinationsAsleep) {
            source.idleSlots = 0;
            SourceSleep.untilDestinations(source, pass, now);
        } else if (source.idleSlots >= tanks) {
            source.idleSlots = 0;
            SourceSleep.nothingToMove(source, now);
        }
        return false;
    }

    /** Solta os handlers guardados da visita (não prendem block entities removidos). */
    private static void clear() {
        for (int s = 0; s < SUBTYPES; s++) {
            HANDLERS[s] = null;
        }
    }

    /**
     * Um tanque da origem: filtro, estoque e entrega. Devolve quanto moveu, ou -1 se o tanque foi pulado (vazio,
     * recusado pelo filtro ou no estoque) sem tentar entregar.
     */
    private static <C extends Chemical<C>, S extends ChemicalStack<C>> long visitTank(Port source,
            Subtype<C, S> type, Object rawHandler, int tank, long tokens, RoundRobinOrder<Port> order, List<Port> pass,
            long now) {
        IChemicalHandler<C, S> handler = type.handler(rawHandler);
        S inTank = handler.getChemicalInTank(tank);
        if (inTank.isEmpty()) {
            return -1;
        }
        ResourceLocation id = MekanismChemicals.id(inTank);
        FilterSet filter = source.filter;
        if (!filter.testChemical(id)) {
            return -1;
        }
        long max = tokens;
        Filter rule = filter.chemicalStockFilter(id);
        long stock = rule == null ? 0 : rule.chemicalStock(id);
        if (stock > 0) {
            max = StockLimit.extractable(amountIn(handler, inTank), stock, max);
            if (max <= 0) {
                return -1;
            }
        }
        return moveChemical(source, type, handler, tank, inTank, id, max, order, pass, now);
    }

    private static <C extends Chemical<C>, S extends ChemicalStack<C>> long moveChemical(Port source,
            Subtype<C, S> type, IChemicalHandler<C, S> handler, int tank, S inTank, ResourceLocation id, long max,
            RoundRobinOrder<Port> order, List<Port> pass, long now) {
        destinationSlept = false;
        S offered = handler.extractChemical(tank, max, Action.SIMULATE);
        if (offered.isEmpty() || !offered.isTypeEqual(inTank)) {
            return 0;
        }
        long remaining = offered.getAmount();
        long moved = 0;
        for (int i = 0, n = pass.size(); i < n && remaining > 0; i++) {
            Port destination = pass.get(i);
            if (destination.destinationBackoff.isSleeping(now)) {
                continue;
            }
            long want = remaining;
            FilterSet accept = destination.filter;
            Filter rule = null;
            if (!accept.isEmpty()) {
                // Recusa do filtro: pula sem dormir, e sem buscar a máquina.
                if (!accept.testChemical(id)) {
                    continue;
                }
                rule = accept.chemicalStockFilter(id);
            }
            Object rawTarget = destination.node.chemicals(destination.face, type.index);
            // Passou no filtro do destino: a origem tem o que oferecer (motivo do sono). Vale também para a
            // máquina sem este tipo: a origem dorme esperando destino e acorda quando a capability dele muda.
            source.offered = true;
            if (rawTarget == null && destination.node.chemicals(destination.face) != null) {
                // A máquina tem químicos, mas não deste tipo: pula sem dormir.
                continue;
            }
            if (rawTarget == null) {
                // Destino sem máquina (ou com o chunk dela descarregado): dorme até a capability
                // voltar (o listener dela o acorda) ou o teto do sono, sem contar como cheio.
                destination.sleepWithoutMachine(now);
                destinationSlept = true;
                continue;
            }
            IChemicalHandler<C, S> target = type.handler(rawTarget);
            long stock = rule == null ? 0 : rule.chemicalStock(id);
            if (stock > 0) {
                // Estoque já atingido: pula sem dormir.
                want = StockLimit.acceptable(amountIn(target, offered), stock, want);
                if (want <= 0) {
                    continue;
                }
            }
            // insertChemical devolve a sobra: aceito = oferecido − sobra.
            long accepts = want - target.insertChemical(type.withAmount(offered, want), Action.SIMULATE).getAmount();
            if (accepts <= 0) {
                destination.destinationBackoff.sleep(now);
                destinationSlept = true;
                continue;
            }
            S taken = handler.extractChemical(tank, Math.min(accepts, remaining), Action.EXECUTE);
            if (taken.isEmpty()) {
                break;
            }
            long takenAmount = taken.getAmount();
            S rest = target.insertChemical(taken, Action.EXECUTE);
            long filled = takenAmount - rest.getAmount();
            if (!rest.isEmpty()) {
                giveBack(source, handler, rest);
            }
            if (filled > 0) {
                destination.destinationBackoff.wake();
                order.delivered(destination);
                if (source.network != null) {
                    source.network.ops++;
                }
                moved += filled;
                remaining -= filled;
            } else {
                destination.destinationBackoff.sleep(now);
                destinationSlept = true;
            }
            if (takenAmount < accepts) {
                break;
            }
        }
        return moved;
    }

    /** Quanto do químico de {@code stack} há nos tanques. */
    private static <C extends Chemical<C>, S extends ChemicalStack<C>> long amountIn(IChemicalHandler<C, S> handler,
            S stack) {
        long total = 0;
        for (int tank = 0, n = handler.getTanks(); tank < n; tank++) {
            S inTank = handler.getChemicalInTank(tank);
            if (!inTank.isEmpty() && inTank.isTypeEqual(stack)) {
                total += inTank.getAmount();
            }
        }
        return total;
    }

    private static <C extends Chemical<C>, S extends ChemicalStack<C>> void giveBack(Port source,
            IChemicalHandler<C, S> handler, S leftover) {
        S lost = handler.insertChemical(leftover, Action.EXECUTE);
        if (!lost.isEmpty()) {
            WirelessAutomate.LOGGER.warn("Destino recusou {} mB de {} depois de aceitar na simulação e a origem {} não aceitou de volta; perdido",
                    lost.getAmount(), MekanismChemicals.id(lost), source);
        }
    }

    private ChemicalTransfer() {
    }
}
