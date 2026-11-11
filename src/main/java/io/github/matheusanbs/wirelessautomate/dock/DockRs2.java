package io.github.matheusanbs.wirelessautomate.dock;

import io.github.matheusanbs.wirelessautomate.compat.refinedstorage.Rs2Dock;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.fml.ModList;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;

/**
 * Ponte da Doca para o Refined Storage 2 sem tipos dele: só esta classe, fora do {@code compat/refinedstorage},
 * conhece o {@link Rs2Dock}, e só o chama com {@link #LOADED}. Assim a JVM nunca carrega uma classe com tipos do RS2
 * quando ele não está instalado.
 */
public final class DockRs2 {
    /** O RS2 está instalado: o Autocrafter encostado na Doca entrega pedidos a ela. */
    public static final boolean LOADED = ModList.get() != null && ModList.get().isLoaded("refinedstorage");
    private static final String NAMESPACE = "refinedstorage";
    private static final Direction[] DIRECTIONS = Direction.values();

    private DockRs2() {
    }

    /**
     * Registra a fábrica de destinos da Doca no RS2; sem o RS2, nada. No common setup, e não na construção do mod: a
     * API do RS2 só existe depois do construtor dele, e a ordem dos construtores não é garantida. Na fila do setup,
     * porque o RS2 guarda as fábricas num conjunto sem trava.
     */
    public static void register(FMLCommonSetupEvent event) {
        if (LOADED) {
            event.enqueueWork(Rs2Dock::register);
        }
    }

    /**
     * O lado {@code side} tem um provedor do RS2: o vizinho é um Autocrafter (de qualquer cor: o id no namespace do
     * RS2 termina em {@code autocrafter}) ou o RS2 ofereceu um pedido ao lado nos últimos 60 s. Consulta o vizinho a
     * cada chamada: só para a tela, nunca por tick.
     */
    public static boolean hasProvider(PatternDockBlockEntity dock, int side) {
        if (!LOADED || !(dock.getLevel() instanceof ServerLevel level)) {
            return false;
        }
        DockSide dockSide = dock.side(side);
        if (dockSide != null && dockSide.offeredWithin(level.getServer().getTickCount())) {
            return true;
        }
        BlockPos neighbor = dock.getBlockPos().relative(DIRECTIONS[side]);
        if (!level.isLoaded(neighbor)) {
            return false;
        }
        ResourceLocation id = BuiltInRegistries.BLOCK.getKey(level.getBlockState(neighbor).getBlock());
        return id.getNamespace().equals(NAMESPACE) && id.getPath().endsWith("autocrafter");
    }

    /** O RS2 ofereceu um pedido ao lado {@code side}. */
    public static void offered(PatternDockBlockEntity dock, int side) {
        DockSide dockSide = dock.side(side);
        if (dockSide != null && dock.getLevel() instanceof ServerLevel level) {
            dockSide.setProviderOfferTick(level.getServer().getTickCount());
        }
    }

    /**
     * O lado {@code side} está vazio para a trava "até a máquina esvaziar" do Autocrafter: sem pendente e sem máquina
     * trabalhando (uma entrega ou volta nos últimos {@link DockStatus#WORKING_TICKS}). O Autocrafter pergunta a cada
     * tick com essa trava: percorre as máquinas do lado, sem consultar nenhuma.
     */
    public static boolean idle(PatternDockBlockEntity dock, int side) {
        DockSide dockSide = dock.side(side);
        if (dockSide == null) {
            return true;
        }
        if (dockSide.hasPending()) {
            return false;
        }
        if (!(dock.getLevel() instanceof ServerLevel level)) {
            return true;
        }
        long now = level.getServer().getTickCount();
        for (DockMachine machine : dockSide.machines()) {
            if (machine.lastActive() > 0 && now - machine.lastActive() < DockStatus.WORKING_TICKS) {
                return false;
            }
        }
        return true;
    }
}
