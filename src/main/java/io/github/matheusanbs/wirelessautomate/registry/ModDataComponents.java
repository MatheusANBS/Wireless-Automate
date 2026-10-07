package io.github.matheusanbs.wirelessautomate.registry;

import io.github.matheusanbs.wirelessautomate.WirelessAutomate;
import io.github.matheusanbs.wirelessautomate.item.FilterCardItem;
import io.github.matheusanbs.wirelessautomate.item.LinkerItem;
import io.github.matheusanbs.wirelessautomate.linker.LinkerArea;
import io.github.matheusanbs.wirelessautomate.linker.LinkerMode;
import io.github.matheusanbs.wirelessautomate.network.ResourceType;
import io.github.matheusanbs.wirelessautomate.network.RouterPreset;
import java.util.function.Supplier;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
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

    /** Modo do Vinculador; sem o componente, Único. */
    public static final Supplier<DataComponentType<LinkerMode>> LINKER_MODE =
            DATA_COMPONENTS.registerComponentType("linker_mode", builder -> builder
                    .persistent(LinkerMode.CODEC)
                    .networkSynchronized(LinkerMode.STREAM_CODEC));

    /** Cantos da área marcados com o Vinculador em modo Área. */
    public static final Supplier<DataComponentType<LinkerArea>> LINKER_AREA =
            DATA_COMPONENTS.registerComponentType("linker_area", builder -> builder
                    .persistent(LinkerArea.CODEC)
                    .networkSynchronized(LinkerArea.STREAM_CODEC));

    /** Bloco da máquina do roteador copiado pelo Configurador: colar em área só vale para essa máquina. */
    public static final Supplier<DataComponentType<ResourceLocation>> CONFIGURATOR_MACHINE =
            DATA_COMPONENTS.registerComponentType("configurator_machine", builder -> builder
                    .persistent(ResourceLocation.CODEC)
                    .networkSynchronized(ResourceLocation.STREAM_CODEC));

    /** Modo do Configurador ({@link LinkerMode#SINGLE} é o pincel); sem o componente, pincel. */
    public static final Supplier<DataComponentType<LinkerMode>> CONFIGURATOR_MODE =
            DATA_COMPONENTS.registerComponentType("configurator_mode", builder -> builder
                    .persistent(LinkerMode.CODEC)
                    .networkSynchronized(LinkerMode.STREAM_CODEC));

    /** Cantos da área marcados com o Configurador em modo Área (o mesmo formato do Vinculador). */
    public static final Supplier<DataComponentType<LinkerArea>> CONFIGURATOR_AREA =
            DATA_COMPONENTS.registerComponentType("configurator_area", builder -> builder
                    .persistent(LinkerArea.CODEC)
                    .networkSynchronized(LinkerArea.STREAM_CODEC));

    private ModDataComponents() {
    }
}
