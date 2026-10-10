package io.github.matheusanbs.wirelessautomate.chunk;

import io.github.matheusanbs.wirelessautomate.Config;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.event.config.ModConfigEvent;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;

/** Eventos do {@link RouterChunkLoader}: registro do controle de tickets, fila por tick, config e parada. */
public final class ChunkLoaderEvents {
    public static void register(IEventBus modEventBus) {
        modEventBus.addListener(ChunkLoaderEvents::onCommonSetup);
        modEventBus.addListener(ChunkLoaderEvents::onConfigReload);
        MinecraftForge.EVENT_BUS.addListener(ChunkLoaderEvents::onServerTick);
        MinecraftForge.EVENT_BUS.addListener(ChunkLoaderEvents::onServerStopped);
    }

    /** No Forge, a validação dos tickets salvos se registra no setup comum (não há {@code TicketController}). */
    private static void onCommonSetup(FMLCommonSetupEvent event) {
        event.enqueueWork(RouterChunkLoader::registerValidation);
    }

    private static void onConfigReload(ModConfigEvent.Reloading event) {
        if (event.getConfig().getSpec() == Config.SPEC) {
            RouterChunkLoader.configChanged();
        }
    }

    private static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        RouterChunkLoader.get().tick();
    }

    private static void onServerStopped(ServerStoppedEvent event) {
        RouterChunkLoader.reset();
    }

    private ChunkLoaderEvents() {
    }
}
