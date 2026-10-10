package io.github.matheusanbs.wirelessautomate.registry;

import com.mojang.serialization.Codec;
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
import net.minecraft.resources.ResourceLocation;

/**
 * Os dados do mod guardados nos itens. No {@code main} (1.21) são componentes de item registrados; no porte
 * 1.20.1 cada um é um {@link ItemData} (o valor pelo codec no NBT do item, sob o nome do componente), com os
 * mesmos nomes de campo. Nada é registrado. Os codecs são os persistentes do {@code main}; o NBT do item já
 * vai ao cliente, então os componentes que só sincronizavam também ficam no NBT.
 */
public final class ModDataComponents {
    /** Configuração copiada pelo Configurador. */
    public static final ItemData<RouterPreset> PRESET = new ItemData<>("preset", RouterPreset.CODEC);

    /** Referência ao conteúdo de um Baú quebrado cheio, mais o resumo do tooltip. */
    public static final ItemData<StorageContents> STORAGE_CONTENTS = new ItemData<>("storage_contents", StorageContents.CODEC);

    /** Filtro de entrada de um Baú quebrado (volta ao bloco quando ele é colocado). */
    public static final ItemData<Filter> STORAGE_FILTER = new ItemData<>("storage_filter", FilterCodecs.LENIENT);

    /** Tipo e filtro do Cartão de Filtro. */
    public static final ItemData<FilterCardItem.Contents> CARD_FILTER = new ItemData<>("card_filter", FilterCardItem.Contents.CODEC);

    /**
     * Formato antigo (até a 0.1.0): um tipo só que o Vinculador vinculava. Continua lido para
     * os itens antigos carregarem; {@link LinkerItem#tabs} o lê como aquela aba sozinha, e qualquer
     * troca de abas grava {@link #LINKER_TABS} e apaga este.
     */
    public static final ItemData<ResourceType> LINKER_TYPE = new ItemData<>("linker_type", LinkerItem.TYPE_CODEC);

    /** Abas que o Vinculador vincula ou desvincula; sem o componente (nem o antigo), Todos. */
    public static final ItemData<LinkerTabs> LINKER_TABS = new ItemData<>("linker_tabs", LinkerItem.TABS_CODEC);

    /** Vinculador no modo desvincular ("Nenhuma" na escolha da rede); sem o componente, vincula. */
    public static final ItemData<Boolean> LINKER_UNLINK = new ItemData<>("linker_unlink", Codec.BOOL);

    /** Modo do Vinculador; sem o componente, Único. */
    public static final ItemData<LinkerMode> LINKER_MODE = new ItemData<>("linker_mode", LinkerMode.CODEC);

    /** Cantos da área marcados com o Vinculador em modo Área. */
    public static final ItemData<LinkerArea> LINKER_AREA = new ItemData<>("linker_area", LinkerArea.CODEC);

    /** Bloco da máquina do roteador copiado pelo Configurador: colar em área só vale para essa máquina. */
    public static final ItemData<ResourceLocation> CONFIGURATOR_MACHINE = new ItemData<>("configurator_machine", ResourceLocation.CODEC);

    /** Modo do Configurador ({@link LinkerMode#SINGLE} é o pincel); sem o componente, pincel. */
    public static final ItemData<LinkerMode> CONFIGURATOR_MODE = new ItemData<>("configurator_mode", LinkerMode.CODEC);

    /**
     * Área do Configurador em "qualquer máquina": colar vale para todos os roteadores da área. Só
     * gravado quando {@code true} (sem o componente, mesma máquina) e removido no pincel.
     */
    public static final ItemData<Boolean> CONFIGURATOR_ANY_MACHINE = new ItemData<>("configurator_any_machine", Codec.BOOL);

    /** Cantos da área marcados com o Configurador em modo Área (o mesmo formato do Vinculador). */
    public static final ItemData<LinkerArea> CONFIGURATOR_AREA = new ItemData<>("configurator_area", LinkerArea.CODEC);

    /** Tipo (aba) que o Configurador cola; sem o componente, todos. */
    public static final ItemData<ResourceType> CONFIGURATOR_TYPE = new ItemData<>("configurator_type", ConfiguratorItem.TYPE_CODEC);

    private ModDataComponents() {
    }
}
