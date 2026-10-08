package io.github.matheusanbs.wirelessautomate.registry;

import com.mojang.serialization.Codec;
import io.github.matheusanbs.wirelessautomate.WirelessAutomate;
import io.github.matheusanbs.wirelessautomate.filter.Filter;
import io.github.matheusanbs.wirelessautomate.filter.FilterCodecs;
import io.github.matheusanbs.wirelessautomate.item.ConfiguratorItem;
import io.github.matheusanbs.wirelessautomate.item.FilterCardItem;
import io.github.matheusanbs.wirelessautomate.item.LinkerItem;
import io.github.matheusanbs.wirelessautomate.linker.LinkerArea;
import io.github.matheusanbs.wirelessautomate.linker.LinkerMode;
import io.github.matheusanbs.wirelessautomate.linker.LinkerTabs;
import io.github.matheusanbs.wirelessautomate.network.ResourceType;
import io.github.matheusanbs.wirelessautomate.network.RouterPreset;
import io.github.matheusanbs.wirelessautomate.storage.StorageContents;
import java.util.function.Supplier;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModDataComponents {
    public static final DeferredRegister.DataComponents DATA_COMPONENTS =
            DeferredRegister.createDataComponents(Registries.DATA_COMPONENT_TYPE, WirelessAutomate.MODID);

    /** Configuração copiada pelo Configurador. */
    public static final Supplier<DataComponentType<RouterPreset>> PRESET = DATA_COMPONENTS.registerComponentType(
            "preset", builder -> builder.persistent(RouterPreset.CODEC).networkSynchronized(RouterPreset.STREAM_CODEC));

    /** Referência ao conteúdo de um Baú quebrado cheio, mais o resumo do tooltip. */
    public static final Supplier<DataComponentType<StorageContents>> STORAGE_CONTENTS =
            DATA_COMPONENTS.registerComponentType("storage_contents", builder -> builder
                    .persistent(StorageContents.CODEC).networkSynchronized(StorageContents.STREAM_CODEC));

    /** Filtro de entrada de um Baú quebrado (volta ao bloco quando ele é colocado). */
    public static final Supplier<DataComponentType<Filter>> STORAGE_FILTER =
            DATA_COMPONENTS.registerComponentType("storage_filter", builder -> builder
                    .persistent(FilterCodecs.LENIENT).networkSynchronized(Filter.STREAM_CODEC));

    /** Tipo e filtro do Cartão de Filtro. */
    public static final Supplier<DataComponentType<FilterCardItem.Contents>> CARD_FILTER =
            DATA_COMPONENTS.registerComponentType("card_filter", builder -> builder
                    .persistent(FilterCardItem.Contents.CODEC)
                    .networkSynchronized(FilterCardItem.Contents.STREAM_CODEC)
                    .cacheEncoding());

    /**
     * Formato antigo (até a 0.1.0): um tipo só que o Vinculador vinculava. Continua registrado para
     * os itens antigos carregarem; {@link LinkerItem#tabs} o lê como aquela aba sozinha, e qualquer
     * troca de abas grava {@link #LINKER_TABS} e apaga este.
     */
    public static final Supplier<DataComponentType<ResourceType>> LINKER_TYPE =
            DATA_COMPONENTS.registerComponentType("linker_type", builder -> builder
                    .persistent(LinkerItem.TYPE_CODEC)
                    .networkSynchronized(LinkerItem.TYPE_STREAM_CODEC));

    /** Abas que o Vinculador vincula ou desvincula; sem o componente (nem o antigo), Todos. */
    public static final Supplier<DataComponentType<LinkerTabs>> LINKER_TABS =
            DATA_COMPONENTS.registerComponentType("linker_tabs", builder -> builder
                    .persistent(LinkerItem.TABS_CODEC)
                    .networkSynchronized(LinkerItem.TABS_STREAM_CODEC));

    /** Vinculador no modo desvincular ("Nenhuma" na escolha da rede); sem o componente, vincula. */
    public static final Supplier<DataComponentType<Boolean>> LINKER_UNLINK =
            DATA_COMPONENTS.registerComponentType("linker_unlink", builder -> builder
                    .persistent(Codec.BOOL)
                    .networkSynchronized(ByteBufCodecs.BOOL));

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

    /** Tipo (aba) que o Configurador cola; sem o componente, todos. */
    public static final Supplier<DataComponentType<ResourceType>> CONFIGURATOR_TYPE =
            DATA_COMPONENTS.registerComponentType("configurator_type", builder -> builder
                    .persistent(ConfiguratorItem.TYPE_CODEC)
                    .networkSynchronized(ConfiguratorItem.TYPE_STREAM_CODEC));

    private ModDataComponents() {
    }
}
