package io.github.matheusanbs.wirelessautomate.client;

import io.github.matheusanbs.wirelessautomate.WirelessAutomate;
import io.github.matheusanbs.wirelessautomate.item.RouterBlockItem;
import io.github.matheusanbs.wirelessautomate.menu.FilterMenu;
import io.github.matheusanbs.wirelessautomate.menu.LinkerMenu;
import io.github.matheusanbs.wirelessautomate.menu.RouterMenu;
import io.github.matheusanbs.wirelessautomate.menu.StorageScalarMenu;
import io.github.matheusanbs.wirelessautomate.menu.StorageListMenu;
import io.github.matheusanbs.wirelessautomate.menu.TabletMenu;
import io.github.matheusanbs.wirelessautomate.registry.ModItems;
import io.github.matheusanbs.wirelessautomate.registry.ModMenus;
import io.github.matheusanbs.wirelessautomate.storage.StorageBlockItem;
import io.github.matheusanbs.wirelessautomate.storage.StorageKind;
import io.github.matheusanbs.wirelessautomate.net.ForgePayloadContext;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.MenuScreens;
import net.minecraft.client.renderer.item.ItemProperties;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import net.minecraftforge.fml.common.Mod;

/** Registros só do cliente. As classes de tela nunca são carregadas no servidor dedicado. */
@Mod.EventBusSubscriber(modid = WirelessAutomate.MODID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class ClientSetup {
    private ClientSetup() {
    }

    /**
     * Propriedade {@code wirelessautomate:tier} dos itens do roteador e dos armazenamentos (a posição no
     * {@code RouterTier}: 0 = Básico ... 7 = Ultimate): os overrides dos modelos de item trocam o ícone pelo modelo do tier.
     */
    @SubscribeEvent
    public static void clientSetup(FMLClientSetupEvent event) {
        // pacotes do servidor para o cliente: o IPayloadContext.player() do lado do cliente
        ForgePayloadContext.setClientPlayer(() -> Minecraft.getInstance().player);
        event.enqueueWork(() -> {
            ItemProperties.register(ModItems.ROUTER.get(), WirelessAutomate.id("tier"),
                    (stack, level, entity, seed) -> RouterBlockItem.tierOf(stack).ordinal());
            for (StorageKind kind : StorageKind.values()) {
                ItemProperties.register(ModItems.STORAGE.get(kind).get(), WirelessAutomate.id("tier"),
                        (stack, level, entity, seed) -> StorageBlockItem.tierOf(stack).ordinal());
            }
            registerScreens();
        });
    }

    /** Telas dos menus (o 1.20.1 não tem o RegisterMenuScreensEvent: o registro é no enqueueWork do setup). */
    private static void registerScreens() {
        MenuScreens.register(ModMenus.ROUTER.get(),
                (RouterMenu menu, Inventory inventory, Component title) -> new RouterScreen(menu, inventory, title));
        MenuScreens.register(ModMenus.STORAGE_LIST.get(), (StorageListMenu<?> menu, Inventory inventory, Component title)
                -> new StorageListScreen(menu, inventory, title));
        MenuScreens.register(ModMenus.STORAGE_SCALAR.get(), (StorageScalarMenu menu, Inventory inventory, Component title)
                -> new StorageScalarScreen(menu, inventory, title));
        MenuScreens.register(ModMenus.FILTER.get(),
                (FilterMenu menu, Inventory inventory, Component title) -> new FilterScreen(menu, inventory, title));
        MenuScreens.register(ModMenus.LINKER.get(),
                (LinkerMenu menu, Inventory inventory, Component title) -> new LinkerScreen(menu, inventory, title));
        MenuScreens.register(ModMenus.NETWORK_TABLET.get(),
                (TabletMenu menu, Inventory inventory, Component title) -> new TabletScreen(menu, inventory, title));
    }
}
