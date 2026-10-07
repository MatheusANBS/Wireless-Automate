package io.github.matheusanbs.wirelessautomate.compat.jei;

import io.github.matheusanbs.wirelessautomate.WirelessAutomate;
import io.github.matheusanbs.wirelessautomate.client.FilterScreen;
import java.util.List;
import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.gui.handlers.IGuiContainerHandler;
import mezz.jei.api.registration.IGuiHandlerRegistration;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.resources.ResourceLocation;

/**
 * Integração opcional com o JEI. Só o próprio JEI acha e carrega esta classe (pela anotação
 * {@link JeiPlugin}, no cliente); nenhum código do mod a referencia, então sem o JEI nada daqui é
 * carregado. Na tela de filtro: arrastar um ingrediente para a grade e Shift + clique nele na lista
 * acrescentam a entrada ({@link FilterGhostHandler}); as áreas extras do painel afastam o JEI.
 */
@JeiPlugin
public final class WirelessAutomateJeiPlugin implements IModPlugin {
    private static final ResourceLocation UID = WirelessAutomate.id("jei");

    @Override
    public ResourceLocation getPluginUid() {
        return UID;
    }

    @Override
    public void registerGuiHandlers(IGuiHandlerRegistration registration) {
        registration.addGhostIngredientHandler(FilterScreen.class, new FilterGhostHandler());
        registration.addGuiContainerHandler(FilterScreen.class, new IGuiContainerHandler<FilterScreen>() {
            @Override
            public List<Rect2i> getGuiExtraAreas(FilterScreen screen) {
                return screen.extraAreas();
            }
        });
    }
}
