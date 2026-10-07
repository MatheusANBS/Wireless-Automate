package io.github.matheusanbs.wirelessautomate.network;

import io.github.matheusanbs.wirelessautomate.WirelessAutomate;
import io.github.matheusanbs.wirelessautomate.compat.mekanism.MekanismChemicals;
import io.github.matheusanbs.wirelessautomate.filter.Filter;
import io.github.matheusanbs.wirelessautomate.filter.FilterSet;
import io.github.matheusanbs.wirelessautomate.filter.StockLimit;
import java.util.List;
import mekanism.api.Action;
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
 * <p>Usa a API do Mekanism: só é carregada com ele presente (o motor chama por {@link Chemicals}).
 */
final class ChemicalTransfer {
    /** Tanques examinados por visita. */
    static final int MAX_TANKS_PER_VISIT = 16;

    /** Uma visita. Devolve {@code true} se moveu algo. */
    static boolean move(Port source, long now) {
        IChemicalHandler handler = (IChemicalHandler) source.node.chemicals(source.face);
        RoundRobinOrder<Port> order = source.order;
        if (handler == null || order == null) {
            source.sourceBackoff.sleep(now);
            return false;
        }
        long tokens = source.limiter.available(now);
        if (tokens <= 0) {
            return false;
        }
        List<Port> pass = order.pass();
        if (!NetworkManager.hasAwakeDestination(pass, now)) {
            source.sourceBackoff.sleep(now);
            return false;
        }
        int tanks = Math.min(handler.getChemicalTanks(), MAX_TANKS_PER_VISIT);
        FilterSet filter = source.filter;
        long moved = 0;
        for (int tank = 0; tank < tanks && tokens > 0; tank++) {
            ChemicalStack inTank = handler.getChemicalInTank(tank);
            if (inTank.isEmpty()) {
                continue;
            }
            ResourceLocation id = MekanismChemicals.id(inTank);
            if (!filter.testChemical(id)) {
                continue;
            }
            long max = tokens;
            Filter rule = filter.chemicalStockFilter(id);
            long stock = rule == null ? 0 : rule.chemicalStock(id);
            if (stock > 0) {
                max = StockLimit.extractable(amountIn(handler, inTank), stock, max);
                if (max <= 0) {
                    continue;
                }
            }
            long amount = moveChemical(source, handler, tank, inTank, id, max, order, pass, now);
            tokens -= amount;
            moved += amount;
            if (!NetworkManager.hasAwakeDestination(pass, now)) {
                break;
            }
        }
        if (moved > 0) {
            source.node.addMoved(source.type, moved);
            source.limiter.consume(moved);
            source.sourceBackoff.wake();
            return true;
        }
        source.sourceBackoff.sleep(now);
        return false;
    }

    private static long moveChemical(Port source, IChemicalHandler handler, int tank, ChemicalStack inTank,
            ResourceLocation id, long max, RoundRobinOrder<Port> order, List<Port> pass, long now) {
        ChemicalStack offered = handler.extractChemical(tank, max, Action.SIMULATE);
        if (offered.isEmpty() || !ChemicalStack.isSameChemical(offered, inTank)) {
            return 0;
        }
        long remaining = offered.getAmount();
        long moved = 0;
        for (int i = 0, n = pass.size(); i < n && remaining > 0; i++) {
            Port destination = pass.get(i);
            if (destination.destinationBackoff.isSleeping(now)) {
                continue;
            }
            IChemicalHandler target = (IChemicalHandler) destination.node.chemicals(destination.face);
            if (target == null) {
                continue;
            }
            long want = remaining;
            FilterSet accept = destination.filter;
            if (!accept.isEmpty()) {
                // Recusa do filtro ou estoque já atingido: pula sem dormir.
                if (!accept.testChemical(id)) {
                    continue;
                }
                Filter rule = accept.chemicalStockFilter(id);
                long stock = rule == null ? 0 : rule.chemicalStock(id);
                if (stock > 0) {
                    want = StockLimit.acceptable(amountIn(target, offered), stock, want);
                    if (want <= 0) {
                        continue;
                    }
                }
            }
            // insertChemical devolve a sobra: aceito = oferecido − sobra.
            long accepts = want - target.insertChemical(offered.copyWithAmount(want), Action.SIMULATE).getAmount();
            if (accepts <= 0) {
                destination.destinationBackoff.sleep(now);
                continue;
            }
            ChemicalStack taken = handler.extractChemical(tank, Math.min(accepts, remaining), Action.EXECUTE);
            if (taken.isEmpty()) {
                break;
            }
            long takenAmount = taken.getAmount();
            ChemicalStack rest = target.insertChemical(taken, Action.EXECUTE);
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
            }
            if (takenAmount < accepts) {
                break;
            }
        }
        return moved;
    }

    /** Quanto do químico de {@code stack} há nos tanques. */
    private static long amountIn(IChemicalHandler handler, ChemicalStack stack) {
        long total = 0;
        for (int tank = 0, n = handler.getChemicalTanks(); tank < n; tank++) {
            ChemicalStack inTank = handler.getChemicalInTank(tank);
            if (ChemicalStack.isSameChemical(inTank, stack)) {
                total += inTank.getAmount();
            }
        }
        return total;
    }

    private static void giveBack(Port source, IChemicalHandler handler, ChemicalStack leftover) {
        ChemicalStack lost = handler.insertChemical(leftover, Action.EXECUTE);
        if (!lost.isEmpty()) {
            WirelessAutomate.LOGGER.warn("Destino recusou {} mB de {} depois de aceitar na simulação e a origem {} não aceitou de volta; perdido",
                    lost.getAmount(), MekanismChemicals.id(lost), source);
        }
    }

    private ChemicalTransfer() {
    }
}
