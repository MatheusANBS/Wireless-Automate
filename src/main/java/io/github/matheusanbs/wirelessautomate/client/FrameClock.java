package io.github.matheusanbs.wirelessautomate.client;

import io.github.matheusanbs.wirelessautomate.WirelessAutomate;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderFrameEvent;

/**
 * O número do quadro desenhado agora, para a {@link FrameCache}. Sobe no começo de cada quadro, depois dos ticks
 * do cliente: o que um tick muda (snapshot, aviso, busca com atraso) já entra no quadro novo.
 */
@EventBusSubscriber(modid = WirelessAutomate.MODID, value = Dist.CLIENT)
public final class FrameClock {
    private static long frame;

    private FrameClock() {
    }

    public static long frame() {
        return frame;
    }

    @SubscribeEvent
    static void onFrame(RenderFrameEvent.Pre event) {
        frame++;
    }
}
