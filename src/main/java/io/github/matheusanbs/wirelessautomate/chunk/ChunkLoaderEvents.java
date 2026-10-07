package io.github.matheusanbs.wirelessautomate.chunk;

import io.github.matheusanbs.wirelessautomate.Config;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.event.config.ModConfigEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.world.chunk.RegisterTicketControllersEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/** Eventos do {@link RouterChunkLoader}: registro do controle de tickets, fila por tick, config e parada. */
public final class ChunkLoaderEvents {
    public static void register(IEventBus modEventBus) {
        modEventBus.addListener(ChunkLoaderEvents::onRegisterTicketControllers);
        modEventBus.addListener(ChunkLoaderEvents::onConfigReload);
        NeoForge.EVENT_BUS.addListener(ChunkLoaderEvents::onServerTick);
        NeoForge.EVENT_BUS.addListener(ChunkLoaderEvents::onServerStopped);
    }

    private static void onRegisterTicketControllers(RegisterTicketControllersEvent event) {
        event.register(RouterChunkLoader.CONTROLLER);
    }

    private static void onConfigReload(ModConfigEvent.Reloading event) {
        if (event.getConfig().getSpec() == Config.SPEC) {
            RouterChunkLoader.configChanged();
        }
    }

    private static void onServerTick(ServerTickEvent.Post event) {
        RouterChunkLoader.get().tick();
    }

    private static void onServerStopped(ServerStoppedEvent event) {
        RouterChunkLoader.reset();
    }

    private ChunkLoaderEvents() {
    }
}
