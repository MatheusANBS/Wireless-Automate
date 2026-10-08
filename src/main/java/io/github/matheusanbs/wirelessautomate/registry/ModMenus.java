package io.github.matheusanbs.wirelessautomate.registry;

import io.github.matheusanbs.wirelessautomate.WirelessAutomate;
import io.github.matheusanbs.wirelessautomate.menu.FilterMenu;
import io.github.matheusanbs.wirelessautomate.menu.LinkerMenu;
import io.github.matheusanbs.wirelessautomate.menu.RouterMenu;
import io.github.matheusanbs.wirelessautomate.menu.StorageBatteryMenu;
import io.github.matheusanbs.wirelessautomate.menu.StorageListMenu;
import io.github.matheusanbs.wirelessautomate.menu.TabletMenu;
import java.util.function.Supplier;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.inventory.MenuType;
import net.neoforged.neoforge.common.extensions.IMenuTypeExtension;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModMenus {
    public static final DeferredRegister<MenuType<?>> MENU_TYPES =
            DeferredRegister.create(Registries.MENU, WirelessAutomate.MODID);

    public static final Supplier<MenuType<RouterMenu>> ROUTER =
            MENU_TYPES.register("router", () -> IMenuTypeExtension.create(RouterMenu::new));

    public static final Supplier<MenuType<FilterMenu>> FILTER =
            MENU_TYPES.register("filter", () -> IMenuTypeExtension.create(FilterMenu::new));

    public static final Supplier<MenuType<LinkerMenu>> LINKER =
            MENU_TYPES.register("linker", () -> IMenuTypeExtension.create(LinkerMenu::new));
    public static final Supplier<MenuType<TabletMenu>> NETWORK_TABLET =
            MENU_TYPES.register("network_tablet", () -> IMenuTypeExtension.create(TabletMenu::new));

    /** Tela em lista do Baú e dos Tanques (o tipo vem no buffer de abertura). */
    public static final Supplier<MenuType<StorageListMenu<?>>> STORAGE_LIST =
            MENU_TYPES.register("storage_list", () -> IMenuTypeExtension.create(StorageListMenu::fromNetwork));
    public static final Supplier<MenuType<StorageBatteryMenu>> STORAGE_BATTERY =
            MENU_TYPES.register("storage_battery", () -> IMenuTypeExtension.create(StorageBatteryMenu::new));

    private ModMenus() {
    }
}
