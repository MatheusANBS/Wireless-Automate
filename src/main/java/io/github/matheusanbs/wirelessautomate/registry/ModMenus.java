package io.github.matheusanbs.wirelessautomate.registry;

import io.github.matheusanbs.wirelessautomate.WirelessAutomate;
import io.github.matheusanbs.wirelessautomate.menu.FilterMenu;
import io.github.matheusanbs.wirelessautomate.menu.RouterMenu;
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

    public static final Supplier<MenuType<TabletMenu>> NETWORK_TABLET =
            MENU_TYPES.register("network_tablet", () -> IMenuTypeExtension.create(TabletMenu::new));

    private ModMenus() {
    }
}
