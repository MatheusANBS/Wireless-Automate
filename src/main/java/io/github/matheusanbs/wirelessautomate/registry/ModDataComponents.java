package io.github.matheusanbs.wirelessautomate.registry;

import io.github.matheusanbs.wirelessautomate.WirelessAutomate;
import io.github.matheusanbs.wirelessautomate.item.FilterCardItem;
import io.github.matheusanbs.wirelessautomate.item.LinkerItem;
import io.github.matheusanbs.wirelessautomate.network.ResourceType;
import io.github.matheusanbs.wirelessautomate.network.RouterPreset;
import java.util.function.Supplier;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.Registries;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModDataComponents {
    public static final DeferredRegister.DataComponents DATA_COMPONENTS =
            DeferredRegister.createDataComponents(Registries.DATA_COMPONENT_TYPE, WirelessAutomate.MODID);

    /** Configuração copiada pelo Configurador. */
    public static final Supplier<DataComponentType<RouterPreset>> PRESET = DATA_COMPONENTS.registerComponentType(
            "preset", builder -> builder.persistent(RouterPreset.CODEC).networkSynchronized(RouterPreset.STREAM_CODEC));

    /** Tipo e filtro do Cartão de Filtro. */
    public static final Supplier<DataComponentType<FilterCardItem.Contents>> CARD_FILTER =
            DATA_COMPONENTS.registerComponentType("card_filter", builder -> builder
                    .persistent(FilterCardItem.Contents.CODEC)
                    .networkSynchronized(FilterCardItem.Contents.STREAM_CODEC)
                    .cacheEncoding());

    /** Tipo que o Vinculador vincula; sem o componente, todos os tipos. */
    public static final Supplier<DataComponentType<ResourceType>> LINKER_TYPE =
            DATA_COMPONENTS.registerComponentType("linker_type", builder -> builder
                    .persistent(LinkerItem.TYPE_CODEC)
                    .networkSynchronized(LinkerItem.TYPE_STREAM_CODEC));

    private ModDataComponents() {
    }
}
