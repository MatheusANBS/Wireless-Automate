package io.github.matheusanbs.wirelessautomate.filter;

import io.github.matheusanbs.wirelessautomate.WirelessAutomate;
import java.util.concurrent.atomic.AtomicInteger;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.event.TagsUpdatedEvent;

/**
 * Geração global das tags: sobe a cada recarga (servidor ou cliente). Os matchers compilados
 * guardam a geração em que expandiram as tags e refazem o mapa na primeira consulta depois que ela
 * muda, então a recarga custa uma comparação de inteiro por consulta, e nada de varrer filtros.
 */
@Mod.EventBusSubscriber(modid = WirelessAutomate.MODID)
public final class FilterTags {
    private static final AtomicInteger GENERATION = new AtomicInteger();

    public static int generation() {
        return GENERATION.get();
    }

    @SubscribeEvent
    public static void onTagsUpdated(TagsUpdatedEvent event) {
        GENERATION.incrementAndGet();
    }

    private FilterTags() {
    }
}
