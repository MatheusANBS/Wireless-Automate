package io.github.matheusanbs.wirelessautomate.client.fx;

import io.github.matheusanbs.wirelessautomate.WirelessAutomate;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;

/**
 * A pré-carga das texturas de efeito: ao entrar no mundo, enfileira as folhas de {@link FxSheets} e as peças sólidas
 * registradas por {@link #add}, e carrega duas por tick, para o primeiro uso não travar o quadro.
 */
@EventBusSubscriber(modid = WirelessAutomate.MODID, value = Dist.CLIENT)
public final class FxWarmup {
    private static final int PER_TICK = 2;
    private static final List<ResourceLocation> EXTRA = new ArrayList<>();
    private static WarmupQueue queue;

    private FxWarmup() {
    }

    /** Uma textura a mais na pré-carga (as das peças sólidas); vale a partir da próxima entrada no mundo. */
    public static void add(ResourceLocation texture) {
        if (!EXTRA.contains(texture)) {
            EXTRA.add(texture);
        }
    }

    /** Recomeça a fila com todas as folhas e as texturas acrescentadas. */
    public static void reset() {
        List<ResourceLocation> all = new ArrayList<>();
        for (FxSheet sheet : FxSheets.all()) {
            all.add(sheet.texture());
        }
        all.addAll(EXTRA);
        queue = new WarmupQueue(all);
    }

    @SubscribeEvent
    static void onLogin(ClientPlayerNetworkEvent.LoggingIn event) {
        reset();
    }

    @SubscribeEvent
    static void onTick(ClientTickEvent.Post event) {
        if (queue == null) {
            return;
        }
        for (ResourceLocation texture : queue.next(PER_TICK)) {
            // getTexture registra e carrega a textura se ainda não estiver no gerenciador
            Minecraft.getInstance().getTextureManager().getTexture(texture);
        }
        if (queue.done()) {
            queue = null;
        }
    }
}
