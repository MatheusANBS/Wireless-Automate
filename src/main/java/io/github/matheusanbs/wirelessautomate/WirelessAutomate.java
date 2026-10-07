package io.github.matheusanbs.wirelessautomate;

import com.mojang.logging.LogUtils;
import io.github.matheusanbs.wirelessautomate.command.WaCommand;
import io.github.matheusanbs.wirelessautomate.network.NetworkManager;
import io.github.matheusanbs.wirelessautomate.registry.ModBlockEntities;
import io.github.matheusanbs.wirelessautomate.registry.ModBlocks;
import io.github.matheusanbs.wirelessautomate.registry.ModCreativeTabs;
import io.github.matheusanbs.wirelessautomate.registry.ModItems;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.slf4j.Logger;

@Mod(WirelessAutomate.MODID)
public final class WirelessAutomate {
    public static final String MODID = "wirelessautomate";
    public static final Logger LOGGER = LogUtils.getLogger();

    public WirelessAutomate(IEventBus modEventBus, ModContainer modContainer) {
        ModBlocks.BLOCKS.register(modEventBus);
        ModItems.ITEMS.register(modEventBus);
        ModBlockEntities.BLOCK_ENTITY_TYPES.register(modEventBus);
        ModCreativeTabs.CREATIVE_MODE_TABS.register(modEventBus);

        modContainer.registerConfig(ModConfig.Type.SERVER, Config.SPEC);

        NeoForge.EVENT_BUS.addListener(WirelessAutomate::onServerTick);
        NeoForge.EVENT_BUS.addListener(WirelessAutomate::onServerStopped);
        NeoForge.EVENT_BUS.addListener(WirelessAutomate::onRegisterCommands);
    }

    public static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(MODID, path);
    }

    private static void onServerTick(ServerTickEvent.Post event) {
        NetworkManager.get().tick(event.getServer());
    }

    private static void onServerStopped(ServerStoppedEvent event) {
        NetworkManager.reset();
    }

    private static void onRegisterCommands(RegisterCommandsEvent event) {
        WaCommand.register(event.getDispatcher());
    }
}
