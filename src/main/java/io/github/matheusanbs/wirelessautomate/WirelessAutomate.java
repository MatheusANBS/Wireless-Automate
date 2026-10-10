package io.github.matheusanbs.wirelessautomate;

import com.mojang.logging.LogUtils;
import io.github.matheusanbs.wirelessautomate.chunk.ChunkLoaderEvents;
import io.github.matheusanbs.wirelessautomate.command.WaCommand;
import io.github.matheusanbs.wirelessautomate.network.NetworkManager;
import io.github.matheusanbs.wirelessautomate.packet.ModPayloads;
import io.github.matheusanbs.wirelessautomate.registry.ModBlockEntities;
import io.github.matheusanbs.wirelessautomate.registry.ModBlocks;
import io.github.matheusanbs.wirelessautomate.registry.ModCreativeTabs;
import io.github.matheusanbs.wirelessautomate.registry.ModDataComponents;
import io.github.matheusanbs.wirelessautomate.registry.ModItems;
import io.github.matheusanbs.wirelessautomate.registry.ModMenus;
import io.github.matheusanbs.wirelessautomate.registry.ModRecipes;
import io.github.matheusanbs.wirelessautomate.recipe.edit.RecipeEditor;
import io.github.matheusanbs.wirelessautomate.recipe.edit.RecipeOverridePack;
import io.github.matheusanbs.wirelessautomate.storage.SourceTankLevels;
import io.github.matheusanbs.wirelessautomate.storage.StorageCapabilities;
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
        ModDataComponents.DATA_COMPONENTS.register(modEventBus);
        ModBlockEntities.BLOCK_ENTITY_TYPES.register(modEventBus);
        ModMenus.MENU_TYPES.register(modEventBus);
        ModRecipes.RECIPE_SERIALIZERS.register(modEventBus);
        ModCreativeTabs.CREATIVE_MODE_TABS.register(modEventBus);
        modEventBus.addListener(ModPayloads::register);
        modEventBus.addListener(StorageCapabilities::register);
        modEventBus.addListener(RecipeOverridePack::onAddPackFinders);

        modContainer.registerConfig(ModConfig.Type.SERVER, Config.SPEC);

        NeoForge.EVENT_BUS.addListener(WirelessAutomate::onServerTick);
        NeoForge.EVENT_BUS.addListener(WirelessAutomate::onServerStopped);
        NeoForge.EVENT_BUS.addListener(WirelessAutomate::onRegisterCommands);
        // Fim de toda recarga (botão do editor ou /reload): zera as pendências do editor de receitas.
        NeoForge.EVENT_BUS.addListener(RecipeEditor::onDatapackSync);
        // Upgrade de chunk loading: controle de tickets, fila por tick e config.
        ChunkLoaderEvents.register(modEventBus);
    }

    public static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(MODID, path);
    }

    private static void onServerTick(ServerTickEvent.Post event) {
        NetworkManager.get().tick(event.getServer());
        SourceTankLevels.tick();
    }

    private static void onServerStopped(ServerStoppedEvent event) {
        NetworkManager.reset();
        SourceTankLevels.reset();
        RecipeEditor.reset();
    }

    private static void onRegisterCommands(RegisterCommandsEvent event) {
        WaCommand.register(event.getDispatcher());
    }
}
