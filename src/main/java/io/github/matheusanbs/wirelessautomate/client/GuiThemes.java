package io.github.matheusanbs.wirelessautomate.client;

import io.github.matheusanbs.wirelessautomate.ClientConfig;
import io.github.matheusanbs.wirelessautomate.GuiTheme;
import io.github.matheusanbs.wirelessautomate.WirelessAutomate;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.config.ModConfigEvent;

/**
 * O tema das telas no cliente: lido da config na carga (e quando o arquivo muda com o jogo aberto)
 * e trocado pelo botão do cabeçalho ({@link ThemeToggle}), que grava o arquivo. Antes da config
 * carregar, vale o claro.
 */
@EventBusSubscriber(modid = WirelessAutomate.MODID, bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class GuiThemes {
    private static GuiTheme current = GuiTheme.LIGHT;

    private GuiThemes() {
    }

    public static GuiTheme current() {
        return current;
    }

    /** Aplica o tema em todas as telas e grava na config do cliente. */
    public static void set(GuiTheme theme) {
        apply(theme);
        ClientConfig.THEME.set(theme);
        ClientConfig.SPEC.save();
    }

    public static void toggle() {
        set(current.other());
    }

    private static void apply(GuiTheme theme) {
        current = theme;
        GuiPaint.apply(GuiPalette.of(theme));
    }

    @SubscribeEvent
    static void onLoad(ModConfigEvent.Loading event) {
        if (event.getConfig().getSpec() == ClientConfig.SPEC) {
            apply(ClientConfig.THEME.get());
        }
    }

    @SubscribeEvent
    static void onReload(ModConfigEvent.Reloading event) {
        if (event.getConfig().getSpec() == ClientConfig.SPEC) {
            apply(ClientConfig.THEME.get());
        }
    }
}
