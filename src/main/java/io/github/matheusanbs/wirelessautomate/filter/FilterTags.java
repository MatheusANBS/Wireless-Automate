package io.github.matheusanbs.wirelessautomate.filter;

import io.github.matheusanbs.wirelessautomate.WirelessAutomate;
import java.util.concurrent.atomic.AtomicInteger;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.TagsUpdatedEvent;

/**
 * Geração global das tags: sobe a cada recarga (servidor ou cliente). Os matchers compilados
 * guardam a geração em que montaram o cache e o descartam quando ela muda, então a recarga custa
 * uma comparação de inteiro por consulta, e nada de varrer filtros.
 */
@EventBusSubscriber(modid = WirelessAutomate.MODID)
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
