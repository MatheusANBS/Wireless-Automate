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
import net.minecraft.client.renderer.item.ItemProperties;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;

/** Registros só do cliente. As classes de tela nunca são carregadas no servidor dedicado. */
// bus explícito: o FML já o deduz do evento e marcou o atributo para remoção, mas aqui ele deixa claro
@SuppressWarnings("removal")
@EventBusSubscriber(modid = WirelessAutomate.MODID, bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class ClientSetup {
    private ClientSetup() {
    }

    /**
     * Propriedade {@code wirelessautomate:tier} dos itens do roteador e dos armazenamentos (0 = Básico ... 3 =
     * Ultimate): os overrides dos modelos de item trocam o ícone pelo modelo do tier.
     */
    @SubscribeEvent
    static void clientSetup(FMLClientSetupEvent event) {
        event.enqueueWork(() -> {
            ItemProperties.register(ModItems.ROUTER.get(), WirelessAutomate.id("tier"),
                    (stack, level, entity, seed) -> RouterBlockItem.tierOf(stack).ordinal());
            for (StorageKind kind : StorageKind.values()) {
                ItemProperties.register(ModItems.STORAGE.get(kind).get(), WirelessAutomate.id("tier"),
                        (stack, level, entity, seed) -> StorageBlockItem.tierOf(stack).ordinal());
            }
        });
    }

    @SubscribeEvent
    static void registerScreens(RegisterMenuScreensEvent event) {
        event.register(ModMenus.ROUTER.get(),
                (RouterMenu menu, Inventory inventory, Component title) -> new RouterScreen(menu, inventory, title));
        event.register(ModMenus.STORAGE_LIST.get(), (StorageListMenu<?> menu, Inventory inventory, Component title)
                -> new StorageListScreen(menu, inventory, title));
        event.register(ModMenus.STORAGE_SCALAR.get(), (StorageScalarMenu menu, Inventory inventory, Component title)
                -> new StorageScalarScreen(menu, inventory, title));
        event.register(ModMenus.FILTER.get(),
                (FilterMenu menu, Inventory inventory, Component title) -> new FilterScreen(menu, inventory, title));
        event.register(ModMenus.LINKER.get(),
                (LinkerMenu menu, Inventory inventory, Component title) -> new LinkerScreen(menu, inventory, title));
        event.register(ModMenus.NETWORK_TABLET.get(),
                (TabletMenu menu, Inventory inventory, Component title) -> new TabletScreen(menu, inventory, title));
    }
}
