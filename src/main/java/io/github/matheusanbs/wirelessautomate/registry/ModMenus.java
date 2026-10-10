package io.github.matheusanbs.wirelessautomate.registry;

import io.github.matheusanbs.wirelessautomate.WirelessAutomate;
import io.github.matheusanbs.wirelessautomate.menu.FilterMenu;
import io.github.matheusanbs.wirelessautomate.menu.LinkerMenu;
import io.github.matheusanbs.wirelessautomate.menu.RouterMenu;
import io.github.matheusanbs.wirelessautomate.menu.StorageScalarMenu;
import io.github.matheusanbs.wirelessautomate.menu.StorageListMenu;
import io.github.matheusanbs.wirelessautomate.menu.TabletMenu;
import java.util.function.Supplier;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.inventory.MenuType;
import io.github.matheusanbs.wirelessautomate.net.RegistryFriendlyByteBuf;
import net.minecraftforge.common.extensions.IForgeMenuType;
import net.minecraftforge.registries.DeferredRegister;

public final class ModMenus {
    public static final DeferredRegister<MenuType<?>> MENU_TYPES =
            DeferredRegister.create(Registries.MENU, WirelessAutomate.MODID);

    public static final Supplier<MenuType<RouterMenu>> ROUTER =
            MENU_TYPES.register("router", () -> IForgeMenuType.create((id, inv, buf) -> new RouterMenu(id, inv, RegistryFriendlyByteBuf.wrap(buf))));

    public static final Supplier<MenuType<FilterMenu>> FILTER =
            MENU_TYPES.register("filter", () -> IForgeMenuType.create((id, inv, buf) -> new FilterMenu(id, inv, RegistryFriendlyByteBuf.wrap(buf))));

    public static final Supplier<MenuType<LinkerMenu>> LINKER =
            MENU_TYPES.register("linker", () -> IForgeMenuType.create((id, inv, buf) -> new LinkerMenu(id, inv, RegistryFriendlyByteBuf.wrap(buf))));
    public static final Supplier<MenuType<TabletMenu>> NETWORK_TABLET =
            MENU_TYPES.register("network_tablet", () -> IForgeMenuType.create((id, inv, buf) -> new TabletMenu(id, inv, RegistryFriendlyByteBuf.wrap(buf))));

    /** Tela em lista do Baú e dos Tanques (o tipo vem no buffer de abertura). */
    public static final Supplier<MenuType<StorageListMenu<?>>> STORAGE_LIST =
            MENU_TYPES.register("storage_list", () -> IForgeMenuType.create(
                    (id, inv, buf) -> StorageListMenu.fromNetwork(id, inv, RegistryFriendlyByteBuf.wrap(buf))));
    public static final Supplier<MenuType<StorageScalarMenu>> STORAGE_SCALAR =
            MENU_TYPES.register("storage_scalar", () -> IForgeMenuType.create((id, inv, buf) -> new StorageScalarMenu(id, inv, RegistryFriendlyByteBuf.wrap(buf))));

    private ModMenus() {
    }
}
