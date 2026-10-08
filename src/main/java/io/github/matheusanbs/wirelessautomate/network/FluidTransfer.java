package io.github.matheusanbs.wirelessautomate.network;

import io.github.matheusanbs.wirelessautomate.WirelessAutomate;
import io.github.matheusanbs.wirelessautomate.filter.Filter;
import io.github.matheusanbs.wirelessautomate.filter.FilterSet;
import io.github.matheusanbs.wirelessautomate.filter.StockLimit;
import io.github.matheusanbs.wirelessautomate.storage.BulkFluids;
import java.util.List;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.fluids.capability.IFluidHandler.FluidAction;
import org.jetbrains.annotations.Nullable;

/**
 * Move fluido de uma origem para os destinos dela, tanque a tanque. Mesmo protocolo dos itens
 * ({@link ItemTransfer}): simula o dreno, simula o enchimento, drena de verdade só o aceito e enche
 * com exatamente o que saiu. A sobra de um destino que mentiu volta para a origem; o que nem a
 * origem aceitar se perde (fluido não tem como cair no mundo) e vai para o log.
 *
 * <p>Filtros e estoque como nos itens (o conjunto da face, {@link FilterSet}): o filtro da origem decide que tanques podem sair, o do
 * destino o que pode entrar, e a recusa do filtro (ou o estoque atingido) pula o destino sem fazê-lo
 * dormir. O estoque soma os tanques do mesmo fluido (O(tanques), só para entradas com estoque):
 * a origem mantém N mB, o destino aceita até N mB.
 *
 * <p>Tanque do mod ({@link BulkFluids}): o lado dele (origem ou destino) é em {@code long} e por
 * tipo, sem o teto de {@link Integer#MAX_VALUE} mB por chamada do {@link IFluidHandler}; como
 * origem, os "tanques" são os tipos guardados. Entre dois Tanques, bilhões de mB passam numa
 * chamada; com tanques de outros mods, o lado deles continua em {@code int}. Um destino que é o
 * mesmo Tanque da origem (outra face) é pulado.
 */
final class FluidTransfer {
    /** Tanques examinados por visita: máquina com mais tanques continua do cursor na visita seguinte. */
    static final int MAX_TANKS_PER_VISIT = 16;
    /** O último {@link #moveFluid} pôs algum destino para dormir. */
    private static boolean destinationSlept;

    /**
     * Uma visita. Devolve {@code true} se moveu algo. Os tanques são varridos a partir do cursor da
     * porta ({@link Port#slotCursor}, o mesmo dos slots de itens), como os slots: a origem só dorme
     * por falta do que mover depois de uma volta inteira sem mover ({@link Port#idleSlots}).
     */
    static boolean move(Port source, long now) {
        BulkFluids bulk = source.node.bulkFluids(source.face);
        IFluidHandler handler = bulk == null ? source.node.fluids(source.face) : null;
        RoundRobinOrder<Port> order = source.order;
        if (bulk == null && handler == null || order == null) {
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
        int tanks = bulk != null ? bulk.types() : handler.getTanks();
        if (tanks <= 0) {
            SourceSleep.nothingToMove(source, now);
            return false;
        }
        int limit = Math.min(tanks, MAX_TANKS_PER_VISIT);
        int tank = source.slotCursor < tanks ? source.slotCursor : 0;
        int scanned = 0;
        FilterSet filter = source.filter;
        long moved = 0;
        boolean destinationsAsleep = false;
        while (scanned < limit) {
            // No Tanque do mod a lista encolhe quando um tipo zera.
            if (bulk != null && (tanks = bulk.types()) == 0) {
                break;
            }
            int current = tank < tanks ? tank : 0;
            scanned++;
            tank = current + 1 == tanks ? 0 : current + 1;
            FluidStack inTank = bulk != null ? bulk.key(current) : handler.getFluidInTank(current);
            if (inTank.isEmpty() || !filter.testFluid(inTank)) {
                continue;
            }
            if (bulk == null) {
                // A chave é cópia: o Mekanism devolve a pilha interna do tanque e a encolhe no dreno;
                // ao esvaziar, a referência zera e o enchimento recebia FluidStack.EMPTY (fluido perdido).
                inTank = inTank.copy();
            }
            long max = bulk != null ? Math.min(tokens, bulk.count(current)) : Math.min(tokens, Integer.MAX_VALUE);
            Filter rule = filter.fluidStockFilter(inTank);
            long stock = rule == null ? 0 : rule.fluidStock(inTank);
            if (stock > 0) {
                max = StockLimit.extractable(amountIn(bulk, handler, inTank, rule.matchComponents()), stock, max);
                if (max <= 0) {
                    continue;
                }
            }
            long amount = moveFluid(source, bulk, handler, inTank, max, order, pass, now);
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

    private static long moveFluid(Port source, @Nullable BulkFluids bulk, @Nullable IFluidHandler handler,
            FluidStack inTank, long max, RoundRobinOrder<Port> order, List<Port> pass, long now) {
        destinationSlept = false;
        long remaining = drain(bulk, handler, inTank, max, true);
        if (remaining <= 0) {
            return 0;
        }
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
                if (!accept.testFluid(inTank)) {
                    continue;
                }
                rule = accept.fluidStockFilter(inTank);
            }
            // Passou no filtro do destino: a origem tem o que oferecer (motivo do sono).
            source.offered = true;
            BulkFluids bulkTarget = destination.node.bulkFluids(destination.face);
            IFluidHandler target = bulkTarget == null ? destination.node.fluids(destination.face) : null;
            if (bulkTarget == null && target == null) {
                // Destino sem máquina (ou com o chunk dela descarregado): dorme até a capability
                // voltar (o listener dela o acorda) ou o teto do sono, sem contar como cheio.
                destination.sleepWithoutMachine(now);
                destinationSlept = true;
                continue;
            }
            if (bulkTarget != null && bulkTarget == bulk) {
                // O mesmo Tanque por outra face: passar para ele mesmo não move nada.
                continue;
            }
            long stock = rule == null ? 0 : rule.fluidStock(inTank);
            if (stock > 0) {
                // Estoque já atingido: pula sem dormir.
                want = StockLimit.acceptable(amountIn(bulkTarget, target, inTank, rule.matchComponents()), stock, want);
                if (want <= 0) {
                    continue;
                }
            }
            long accepts = fill(bulkTarget, target, inTank, want, true);
            if (accepts <= 0) {
                destination.destinationBackoff.sleep(now);
                destinationSlept = true;
                continue;
            }
            long taken = drain(bulk, handler, inTank, Math.min(accepts, remaining), false);
            if (taken <= 0) {
                break;
            }
            long filled = fill(bulkTarget, target, inTank, taken, false);
            if (filled < taken) {
                giveBack(source, bulk, handler, inTank, taken - filled);
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
            if (taken < accepts) {
                break;
            }
        }
        return moved;
    }

    /** Drena até {@code amount} do fluido de {@code key} pelo lado que existir; devolve quanto saiu. */
    private static long drain(@Nullable BulkFluids bulk, @Nullable IFluidHandler handler, FluidStack key, long amount,
            boolean simulate) {
        if (bulk != null) {
            return bulk.extract(key, amount, simulate);
        }
        FluidStack out = handler.drain(key.copyWithAmount((int) Math.min(amount, Integer.MAX_VALUE)),
                simulate ? FluidAction.SIMULATE : FluidAction.EXECUTE);
        return out.isEmpty() || !FluidStack.isSameFluidSameComponents(out, key) ? 0 : out.getAmount();
    }

    /** Enche até {@code amount} do fluido de {@code key} pelo lado que existir; devolve quanto entrou. */
    private static long fill(@Nullable BulkFluids bulk, @Nullable IFluidHandler handler, FluidStack key, long amount,
            boolean simulate) {
        if (bulk != null) {
            return bulk.insert(key, amount, simulate);
        }
        return handler.fill(key.copyWithAmount((int) Math.min(amount, Integer.MAX_VALUE)),
                simulate ? FluidAction.SIMULATE : FluidAction.EXECUTE);
    }

    /** Quanto do fluido de {@code stack} há no lado que existir (mesmos componentes, se pedido). */
    private static long amountIn(@Nullable BulkFluids bulk, @Nullable IFluidHandler handler, FluidStack stack,
            boolean components) {
        if (bulk != null) {
            return components ? bulk.count(stack) : bulk.countFluid(stack);
        }
        long total = 0;
        for (int tank = 0, n = handler.getTanks(); tank < n; tank++) {
            FluidStack inTank = handler.getFluidInTank(tank);
            if (components ? FluidStack.isSameFluidSameComponents(inTank, stack) : FluidStack.isSameFluid(inTank, stack)) {
                total += inTank.getAmount();
            }
        }
        return total;
    }

    private static void giveBack(Port source, @Nullable BulkFluids bulk, @Nullable IFluidHandler handler, FluidStack key,
            long leftover) {
        long back = fill(bulk, handler, key, leftover, false);
        if (back < leftover) {
            WirelessAutomate.LOGGER.warn("Destino recusou {} mB de {} depois de aceitar na simulação e a origem {} não aceitou de volta; perdido",
                    leftover - back, key.getHoverName().getString(), source);
        }
    }

    private FluidTransfer() {
    }
}
