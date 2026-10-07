package io.github.matheusanbs.wirelessautomate.network;

import io.github.matheusanbs.wirelessautomate.WirelessAutomate;
import java.util.List;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.fluids.capability.IFluidHandler.FluidAction;

/**
 * Move fluido de uma origem para os destinos dela, tanque a tanque. Mesmo protocolo dos itens
 * ({@link ItemTransfer}): simula o dreno, simula o enchimento, drena de verdade só o aceito e enche
 * com exatamente o que saiu. A sobra de um destino que mentiu volta para a origem; o que nem a
 * origem aceitar se perde (fluido não tem como cair no mundo) e vai para o log.
 */
final class FluidTransfer {
    /** Tanques examinados por visita. */
    static final int MAX_TANKS_PER_VISIT = 16;

    static void move(Port source, long now) {
        IFluidHandler handler = source.node.fluids(source.face);
        RoundRobinOrder<Port> order = source.order;
        if (handler == null || order == null) {
            source.sourceBackoff.sleep(now);
            return;
        }
        long tokens = source.limiter.available(now);
        if (tokens <= 0) {
            return;
        }
        List<Port> pass = order.pass();
        if (!NetworkManager.hasAwakeDestination(pass, now)) {
            source.sourceBackoff.sleep(now);
            return;
        }
        int tanks = Math.min(handler.getTanks(), MAX_TANKS_PER_VISIT);
        long moved = 0;
        for (int tank = 0; tank < tanks && tokens > 0; tank++) {
            FluidStack inTank = handler.getFluidInTank(tank);
            if (inTank.isEmpty()) {
                continue;
            }
            int amount = moveFluid(source, handler, inTank, (int) Math.min(tokens, Integer.MAX_VALUE), order, pass, now);
            tokens -= amount;
            moved += amount;
            if (!NetworkManager.hasAwakeDestination(pass, now)) {
                break;
            }
        }
        if (moved > 0) {
            source.limiter.consume(moved);
            source.sourceBackoff.wake();
        } else {
            source.sourceBackoff.sleep(now);
        }
    }

    private static int moveFluid(Port source, IFluidHandler handler, FluidStack inTank, int max,
            RoundRobinOrder<Port> order, List<Port> pass, long now) {
        FluidStack offered = handler.drain(inTank.copyWithAmount(max), FluidAction.SIMULATE);
        if (offered.isEmpty()) {
            return 0;
        }
        int remaining = offered.getAmount();
        int moved = 0;
        for (int i = 0, n = pass.size(); i < n && remaining > 0; i++) {
            Port destination = pass.get(i);
            if (destination.destinationBackoff.isSleeping(now)) {
                continue;
            }
            IFluidHandler target = destination.node.fluids(destination.face);
            if (target == null) {
                continue;
            }
            int accepts = target.fill(offered.copyWithAmount(remaining), FluidAction.SIMULATE);
            if (accepts <= 0) {
                destination.destinationBackoff.sleep(now);
                continue;
            }
            FluidStack taken = handler.drain(offered.copyWithAmount(Math.min(accepts, remaining)), FluidAction.EXECUTE);
            if (taken.isEmpty()) {
                break;
            }
            int takenAmount = taken.getAmount();
            int filled = target.fill(taken, FluidAction.EXECUTE);
            if (filled < takenAmount) {
                giveBack(source, handler, taken.copyWithAmount(takenAmount - filled));
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

    private static void giveBack(Port source, IFluidHandler handler, FluidStack leftover) {
        int back = handler.fill(leftover, FluidAction.EXECUTE);
        if (back < leftover.getAmount()) {
            WirelessAutomate.LOGGER.warn("Destino recusou {} mB de {} depois de aceitar na simulação e a origem {} não aceitou de volta; perdido",
                    leftover.getAmount() - back, leftover.getHoverName().getString(), source);
        }
    }

    private FluidTransfer() {
    }
}
