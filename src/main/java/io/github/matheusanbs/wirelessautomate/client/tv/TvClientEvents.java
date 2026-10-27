package io.github.matheusanbs.wirelessautomate.client.tv;

import io.github.matheusanbs.wirelessautomate.WirelessAutomate;
import io.github.matheusanbs.wirelessautomate.client.tv.editor.TvLibraryClient;
import io.github.matheusanbs.wirelessautomate.client.tv.render.TvCanvases;
import io.github.matheusanbs.wirelessautomate.client.tv.render.TvEditorImage;
import io.github.matheusanbs.wirelessautomate.tv.TvLoaded;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderFrameEvent;
import org.jetbrains.annotations.Nullable;

/**
 * Os eventos do jogo que a imagem das Telas usa: o começo do quadro (redesenhar as sujas e a miniatura do editor), o
 * tick do cliente (soltar as que saíram da vista), a troca de dimensão e a saída do mundo (limpar tudo). Os números e
 * as imagens são guardados pela posição: valem só para a dimensão atual, e trocar de dimensão os limpa. Os ganchos do
 * {@code TvClientHooks} e o BER ficam no {@code ClientSetup}.
 */
@EventBusSubscriber(modid = WirelessAutomate.MODID, value = Dist.CLIENT)
public final class TvClientEvents {
    private static @Nullable ResourceKey<Level> dimension;
    private TvClientEvents() {
    }

    @SubscribeEvent
    static void onFrame(RenderFrameEvent.Pre event) {
        TvCanvases.onFrame();
        TvEditorImage.onFrame();
    }

    @SubscribeEvent
    static void onClientTick(ClientTickEvent.Post event) {
        TvCanvases.onClientTick();
    }

    @SubscribeEvent
    static void onLoggingIn(ClientPlayerNetworkEvent.LoggingIn event) {
        dimension = event.getPlayer().level().dimension();
    }

    /** O jogador novo do respawn: numa dimensão diferente, o que era da anterior sai. */
    @SubscribeEvent
    static void onClone(ClientPlayerNetworkEvent.Clone event) {
        ResourceKey<Level> now = event.getNewPlayer().level().dimension();
        if (!now.equals(dimension)) {
            dimension = now;
            clear();
        }
    }

    @SubscribeEvent
    static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        dimension = null;
        clear();
    }

    private static void clear() {
        TvLoaded.clearClient();
        TvClientValues.clear();
        TvCanvases.clear();
        TvLibraryClient.clear();
    }
}
