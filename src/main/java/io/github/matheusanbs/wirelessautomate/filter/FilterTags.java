package io.github.matheusanbs.wirelessautomate.filter;

import io.github.matheusanbs.wirelessautomate.WirelessAutomate;
import java.util.concurrent.atomic.AtomicInteger;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.TagsUpdatedEvent;

/**
 * Geração global das tags: sobe a cada recarga (servidor ou cliente), e a recarga esvazia as expansões
 * compartilhadas ({@link TagExpansions}). Os matchers compilados guardam a expansão que usam, com a geração
 * dela, e pegam a nova na primeira consulta depois que a geração muda, então a recarga custa uma comparação
 * de inteiro por consulta, e nada de varrer filtros.
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
        TagExpansions.clear();
    }

    private FilterTags() {
    }
}
