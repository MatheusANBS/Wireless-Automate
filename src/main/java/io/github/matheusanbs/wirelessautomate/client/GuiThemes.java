package io.github.matheusanbs.wirelessautomate.client;

import io.github.matheusanbs.wirelessautomate.ClientConfig;
import io.github.matheusanbs.wirelessautomate.GuiTheme;
import io.github.matheusanbs.wirelessautomate.WirelessAutomate;
import net.minecraft.client.Minecraft;
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
    private static int version;

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

    /** Aplica o tema sem gravar na config (capturas de desenvolvimento). */
    public static void preview(GuiTheme theme) {
        apply(theme);
    }

    public static void toggle() {
        set(current.other());
    }

    /**
     * Sobe a cada troca de tema. Os campos de texto guardam a cor no próprio widget: cada tela
     * compara este número no quadro e reaplica a tinta só quando ele mudou.
     */
    public static int version() {
        return version;
    }

    private static void apply(GuiTheme theme) {
        current = theme;
        version++;
        GuiPaint.apply(GuiPalette.of(theme));
    }

    /** A recarga vem da thread que vigia o arquivo: a troca roda na thread do cliente. */
    private static void applyOnClient(GuiTheme theme) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft == null) {
            apply(theme);
        } else {
            minecraft.execute(() -> apply(theme));
        }
    }

    @SubscribeEvent
    static void onLoad(ModConfigEvent.Loading event) {
        if (event.getConfig().getSpec() == ClientConfig.SPEC) {
            applyOnClient(ClientConfig.THEME.get());
        }
    }

    @SubscribeEvent
    static void onReload(ModConfigEvent.Reloading event) {
        if (event.getConfig().getSpec() == ClientConfig.SPEC) {
            applyOnClient(ClientConfig.THEME.get());
        }
    }
}
