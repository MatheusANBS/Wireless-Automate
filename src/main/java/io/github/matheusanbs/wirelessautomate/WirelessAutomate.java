package io.github.matheusanbs.wirelessautomate;

import com.mojang.logging.LogUtils;
import io.github.matheusanbs.wirelessautomate.chunk.ChunkLoaderEvents;
import io.github.matheusanbs.wirelessautomate.command.WaCommand;
import io.github.matheusanbs.wirelessautomate.network.NetworkManager;
import io.github.matheusanbs.wirelessautomate.packet.ModPayloads;
import io.github.matheusanbs.wirelessautomate.registry.ModBlockEntities;
import io.github.matheusanbs.wirelessautomate.registry.ModBlocks;
import io.github.matheusanbs.wirelessautomate.registry.ModCreativeTabs;
import io.github.matheusanbs.wirelessautomate.registry.ModItems;
import io.github.matheusanbs.wirelessautomate.registry.ModMenus;
import io.github.matheusanbs.wirelessautomate.registry.ModRecipes;
import io.github.matheusanbs.wirelessautomate.storage.SourceTankLevels;
import io.github.matheusanbs.wirelessautomate.storage.StorageCapabilities;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import org.slf4j.Logger;

@Mod(WirelessAutomate.MODID)
public final class WirelessAutomate {
    public static final String MODID = "wirelessautomate";
    public static final Logger LOGGER = LogUtils.getLogger();

    public WirelessAutomate() {
        IEventBus modEventBus = FMLJavaModLoadingContext.get().getModEventBus();
        ModBlocks.BLOCKS.register(modEventBus);
        ModItems.ITEMS.register(modEventBus);
        // Componentes de item: no 1.20.1 são uma fachada sobre o NBT (ItemData), sem registro.
        ModBlockEntities.BLOCK_ENTITY_TYPES.register(modEventBus);
        ModMenus.MENU_TYPES.register(modEventBus);
        ModRecipes.RECIPE_SERIALIZERS.register(modEventBus);
        ModCreativeTabs.CREATIVE_MODE_TABS.register(modEventBus);
        modEventBus.addListener(ModPayloads::register);
        modEventBus.addListener(StorageCapabilities::register);

        ModLoadingContext.get().registerConfig(ModConfig.Type.SERVER, Config.SPEC);

        MinecraftForge.EVENT_BUS.addListener(WirelessAutomate::onServerTick);
        MinecraftForge.EVENT_BUS.addListener(WirelessAutomate::onServerStopped);
        MinecraftForge.EVENT_BUS.addListener(WirelessAutomate::onRegisterCommands);
        // Upgrade de chunk loading: controle de tickets, fila por tick e config.
        ChunkLoaderEvents.register(modEventBus);
    }

    public static ResourceLocation id(String path) {
        return new ResourceLocation(MODID, path);
    }

    private static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        NetworkManager.get().tick(event.getServer());
        SourceTankLevels.tick();
    }

    private static void onServerStopped(ServerStoppedEvent event) {
        NetworkManager.reset();
        SourceTankLevels.reset();
    }

    private static void onRegisterCommands(RegisterCommandsEvent event) {
        WaCommand.register(event.getDispatcher());
    }
}
