package io.github.matheusanbs.wirelessautomate.client.tv;

import io.github.matheusanbs.wirelessautomate.WirelessAutomate;
import io.github.matheusanbs.wirelessautomate.client.tv.render.TvCanvases;
import io.github.matheusanbs.wirelessautomate.tv.TvLoaded;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderFrameEvent;

/**
 * Os eventos do jogo que a imagem das Telas usa: o começo do quadro (redesenhar as sujas), o tick do cliente (soltar
 * as que saíram da vista) e a saída do mundo (limpar tudo). Os ganchos do {@code TvClientHooks} e o BER ficam no
 * {@code ClientSetup}.
 */
@EventBusSubscriber(modid = WirelessAutomate.MODID, value = Dist.CLIENT)
public final class TvClientEvents {
    private TvClientEvents() {
    }

    @SubscribeEvent
    static void onFrame(RenderFrameEvent.Pre event) {
        TvCanvases.onFrame();
    }

    @SubscribeEvent
    static void onClientTick(ClientTickEvent.Post event) {
        TvCanvases.onClientTick();
    }

    @SubscribeEvent
    static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        TvLoaded.clearClient();
        TvClientValues.clear();
        TvCanvases.clear();
    }
}
