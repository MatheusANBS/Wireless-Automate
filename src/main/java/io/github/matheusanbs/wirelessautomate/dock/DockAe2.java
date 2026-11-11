package io.github.matheusanbs.wirelessautomate.dock;

import io.github.matheusanbs.wirelessautomate.compat.ae2.Ae2Dock;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import org.jetbrains.annotations.Nullable;

/**
 * Ponte da Doca para o AE2 sem tipos dele: só esta classe, fora do {@code compat/ae2}, conhece o {@link Ae2Dock}, e só
 * o chama com {@link #LOADED}. Assim a JVM nunca carrega uma classe com tipos do AE2 quando ele não está instalado.
 */
public final class DockAe2 {
    /** O AE2 está instalado: a Doca expõe a {@code ae2:crafting_machine} nas seis faces. */
    public static final boolean LOADED = ModList.get() != null && ModList.get().isLoaded("ae2");

    private DockAe2() {
    }

    /** Registra a capability de máquina de crafting do AE2 na Doca; sem o AE2, nada. */
    public static void register(RegisterCapabilitiesEvent event) {
        if (LOADED) {
            Ae2Dock.register(event);
        }
    }

    /**
     * O lado {@code side} tem um provedor do AE2: o vizinho expõe o inventário interno genérico sem expor o
     * armazenamento ME (o Pattern Provider sim, a Interface não), ou um provedor ofereceu um pedido a ele nos últimos
     * 60 s (os provedores de addon). Consulta o vizinho a cada chamada: só para a tela, nunca por tick.
     */
    public static boolean hasProvider(PatternDockBlockEntity dock, int side) {
        if (!LOADED || !(dock.getLevel() instanceof ServerLevel level)) {
            return false;
        }
        DockSide dockSide = dock.side(side);
        if (dockSide != null && dockSide.offeredWithin(level.getServer().getTickCount())) {
            return true;
        }
        return Ae2Dock.providerNextTo(dock, side);
    }

    /** Um provedor do AE2 falou com o lado {@code side} (perguntou se aceita ou ofereceu um pedido). */
    public static void offered(PatternDockBlockEntity dock, int side) {
        DockSide dockSide = dock.side(side);
        if (dockSide != null && dock.getLevel() instanceof ServerLevel level) {
            dockSide.setProviderOfferTick(level.getServer().getTickCount());
        }
    }

    /**
     * O destino da volta para o vizinho em {@code pos}, encostado na Doca pela face {@code face} dele: a rede ME se ele
     * é um nó do AE2 (o Pattern Provider, a Interface, um cabo), senão {@code null}. O resultado entra direto no
     * armazenamento da rede, e a CPU que espera por ele o conta ali. Só na troca do vizinho (a volta guarda o destino
     * por lado).
     */
    public static @Nullable DockReturnNetwork target(ServerLevel level, BlockPos pos, Direction face) {
        return LOADED ? Ae2Dock.target(level, pos, face) : null;
    }

    /**
     * O provedor do lado {@code side} está em "travar até o resultado", que com a Doca nunca destrava (o resultado não
     * passa pelo retorno dele). Só para a tela, nunca por tick.
     */
    public static boolean lockedUntilResult(PatternDockBlockEntity dock, int side) {
        return LOADED && Ae2Dock.lockedUntilResult(dock, side);
    }
}
