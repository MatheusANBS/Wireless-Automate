package io.github.matheusanbs.wirelessautomate.registry;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.Dynamic;
import io.github.matheusanbs.wirelessautomate.WirelessAutomate;
import io.github.matheusanbs.wirelessautomate.block.RouterTier;
import io.github.matheusanbs.wirelessautomate.dock.DockChoice;
import io.github.matheusanbs.wirelessautomate.dock.DockPendingContents;
import io.github.matheusanbs.wirelessautomate.filter.Filter;
import io.github.matheusanbs.wirelessautomate.filter.FilterCodecs;
import io.github.matheusanbs.wirelessautomate.item.FilterCardItem;
import io.github.matheusanbs.wirelessautomate.linker.LinkerMode;
import io.github.matheusanbs.wirelessautomate.linker.LinkerModeCodecs;
import io.github.matheusanbs.wirelessautomate.network.ResourceType;
import io.github.matheusanbs.wirelessautomate.network.RouterPreset;
import io.github.matheusanbs.wirelessautomate.preset.ProfileSource;
import io.github.matheusanbs.wirelessautomate.resonator.CrystalCharge;
import io.github.matheusanbs.wirelessautomate.storage.StorageContents;
import io.github.matheusanbs.wirelessautomate.storage.upgrade.CompactingRules;
import java.util.function.Supplier;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.ExtraCodecs;
import net.minecraft.world.item.component.ItemContainerContents;
import net.neoforged.neoforge.network.codec.NeoForgeStreamCodecs;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModDataComponents {
    public static final DeferredRegister.DataComponents DATA_COMPONENTS =
            DeferredRegister.createDataComponents(Registries.DATA_COMPONENT_TYPE, WirelessAutomate.MODID);

    /** Configuração copiada pelo Configurador. */
    public static final Supplier<DataComponentType<RouterPreset>> PRESET = DATA_COMPONENTS.registerComponentType(
            "preset", builder -> builder.persistent(RouterPreset.CODEC).networkSynchronized(RouterPreset.STREAM_CODEC));

    /** De onde veio o perfil guardado no Tablet (nome do nó, máquina e posição), junto do {@link #PRESET}. */
    public static final Supplier<DataComponentType<ProfileSource>> PROFILE_SOURCE =
            DATA_COMPONENTS.registerComponentType("profile_source", builder -> builder
                    .persistent(ProfileSource.CODEC).networkSynchronized(ProfileSource.STREAM_CODEC));

    /** Referência ao conteúdo de um Baú quebrado cheio, mais o resumo do tooltip. */
    public static final Supplier<DataComponentType<StorageContents>> STORAGE_CONTENTS =
            DATA_COMPONENTS.registerComponentType("storage_contents", builder -> builder
                    .persistent(StorageContents.CODEC).networkSynchronized(StorageContents.STREAM_CODEC));

    /** Filtro de entrada de um Baú quebrado (volta ao bloco quando ele é colocado). */
    public static final Supplier<DataComponentType<Filter>> STORAGE_FILTER =
            DATA_COMPONENTS.registerComponentType("storage_filter", builder -> builder
                    .persistent(FilterCodecs.LENIENT).networkSynchronized(Filter.STREAM_CODEC));

    /** Os dois upgrades de um Baú quebrado (voltam ao bloco quando ele é colocado); só existe com algum upgrade. */
    public static final Supplier<DataComponentType<ItemContainerContents>> STORAGE_UPGRADES =
            DATA_COMPONENTS.registerComponentType("storage_upgrades", builder -> builder
                    .persistent(ItemContainerContents.CODEC).networkSynchronized(ItemContainerContents.STREAM_CODEC));

    /** A grade 3×3 da Mesa de Trabalho de um Baú quebrado; só existe com algo na grade. */
    public static final Supplier<DataComponentType<ItemContainerContents>> STORAGE_CRAFT_GRID =
            DATA_COMPONENTS.registerComponentType("storage_craft_grid", builder -> builder
                    .persistent(ItemContainerContents.CODEC).networkSynchronized(ItemContainerContents.STREAM_CODEC));

    /** As regras de um Upgrade de Compactação (modo global e alvo por cadeia); sem o componente, as padrão. */
    public static final Supplier<DataComponentType<CompactingRules>> COMPACTING_RULES =
            DATA_COMPONENTS.registerComponentType("compacting_rules", builder -> builder
                    .persistent(CompactingRules.CODEC).networkSynchronized(CompactingRules.STREAM_CODEC));

    /** Tipo e filtro do Cartão de Filtro. */
    public static final Supplier<DataComponentType<FilterCardItem.Contents>> CARD_FILTER =
            DATA_COMPONENTS.registerComponentType("card_filter", builder -> builder
                    .persistent(FilterCardItem.Contents.CODEC)
                    .networkSynchronized(FilterCardItem.Contents.STREAM_CODEC)
                    .cacheEncoding());

    /** Modo do Vinculador; sem o componente, Vincular. */
    public static final Supplier<DataComponentType<LinkerMode>> LINKER_MODE =
            DATA_COMPONENTS.registerComponentType("linker_mode", builder -> builder
                    .persistent(LinkerModeCodecs.CODEC)
                    .networkSynchronized(LinkerModeCodecs.STREAM_CODEC));

    /**
     * Roteador escolhido no Vinculador (posição e dimensão): o alvo dos vínculos de máquinas. Não é limpo quando o roteador some; o servidor confere a cada clique.
     */
    public static final Supplier<DataComponentType<GlobalPos>> LINKER_ROUTER =
            DATA_COMPONENTS.registerComponentType("linker_router", builder -> builder
                    .persistent(GlobalPos.CODEC)
                    .networkSynchronized(GlobalPos.STREAM_CODEC));

    /**
     * Tela de Sinal escolhida no Vinculador (posição e dimensão): o alvo dos armazenamentos ligados. Escolher uma Tela
     * esquece o roteador e escolher um roteador esquece a Tela. Não é limpo quando a Tela some; o servidor confere a
     * cada clique.
     */
    public static final Supplier<DataComponentType<GlobalPos>> LINKER_TV =
            DATA_COMPONENTS.registerComponentType("linker_tv", builder -> builder
                    .persistent(GlobalPos.CODEC)
                    .networkSynchronized(GlobalPos.STREAM_CODEC));

    /**
     * Doca de Padrões escolhida no Vinculador (posição, dimensão e lado): o alvo dos vínculos de máquinas a um lado.
     * Escolher uma Doca limpa {@link #LINKER_ROUTER} e {@link #LINKER_TV}, e escolher um roteador ou uma Tela limpa
     * esta; o servidor confere a cada clique se a Doca ainda está lá.
     */
    public static final Supplier<DataComponentType<DockChoice>> LINKER_DOCK =
            DATA_COMPONENTS.registerComponentType("linker_dock", builder -> builder
                    .persistent(DockChoice.CODEC)
                    .networkSynchronized(DockChoice.STREAM_CODEC));

    /**
     * O pendente de uma Doca de Padrões quebrada (por lado: o bloco, os itens e os fluidos), que volta aos lados ao
     * recolocar; só existe com pendente.
     */
    public static final Supplier<DataComponentType<DockPendingContents>> PATTERN_DOCK_PENDING =
            DATA_COMPONENTS.registerComponentType("pattern_dock_pending", builder -> builder
                    .persistent(DockPendingContents.CODEC)
                    .networkSynchronized(DockPendingContents.STREAM_CODEC));

    /** Bloco da máquina copiado pelo Configurador antigo; nada mais o lê nem grava, fica registrado para os itens que o levam. */
    public static final Supplier<DataComponentType<ResourceLocation>> CONFIGURATOR_MACHINE =
            DATA_COMPONENTS.registerComponentType("configurator_machine", builder -> builder
                    .persistent(ResourceLocation.CODEC)
                    .networkSynchronized(ResourceLocation.STREAM_CODEC));

    /**
     * Componentes de versões antigas que nada mais lê. Ficam registrados só para os itens que ainda os levam
     * carregarem: sem o registro, o item com o componente falharia ao ler e sumiria do inventário.
     */
    static {
        legacy("linker_type");
        legacy("linker_unlink");
        legacy("linker_area");
        legacy("linker_tabs");
        legacy("linker_faces");
        legacy("configurator_mode");
        legacy("configurator_any_machine");
        legacy("configurator_area");
    }

    private static void legacy(String name) {
        DATA_COMPONENTS.<Dynamic<?>>registerComponentType(name, builder -> builder.persistent(Codec.PASSTHROUGH));
    }

    /**
     * Carga de um Cristal de Sinal ou Lapidado ({@link CrystalCharge}). Sem o componente, o cristal está
     * cheio; com 0, está Opaco. Um cristal com o componente valendo o cheio (por comando ou datapack) é cheio
     * para a receita, mas só empilha com o novo depois de passar pelo {@code setCharge}, que tira o componente.
     */
    public static final Supplier<DataComponentType<Integer>> CRYSTAL_CHARGE =
            DATA_COMPONENTS.registerComponentType("crystal_charge", builder -> builder
                    .persistent(ExtraCodecs.intRange(0, CrystalCharge.FULL))
                    .networkSynchronized(ByteBufCodecs.VAR_INT));

    /**
     * Integridade de um cristal do Ressonador (de 0 até {@code resonator.crystalIntegrity}); sem o componente, cheia.
     * Cada recarga gasta 1, e com 0 o cristal está rachado.
     */
    public static final Supplier<DataComponentType<Integer>> CRYSTAL_INTEGRITY =
            DATA_COMPONENTS.registerComponentType("crystal_integrity", builder -> builder
                    .persistent(ExtraCodecs.intRange(0, 64))
                    .networkSynchronized(ByteBufCodecs.VAR_INT));

    /**
     * Tipo (aba) do seletor do Configurador antigo; nada mais o lê nem grava. Fica registrado para os itens que o
     * levam carregarem; o codec aceita todos os tipos, inclusive Químicos sem o Mekanism.
     */
    public static final Supplier<DataComponentType<ResourceType>> CONFIGURATOR_TYPE =
            DATA_COMPONENTS.registerComponentType("configurator_type", builder -> builder
                    .persistent(Codec.STRING.comapFlatMap(name -> {
                        for (ResourceType type : ResourceType.values()) {
                            if (type.key().equals(name)) {
                                return DataResult.success(type);
                            }
                        }
                        return DataResult.error(() -> "tipo de Configurador desconhecido: " + name);
                    }, ResourceType::key))
                    .networkSynchronized(NeoForgeStreamCodecs.enumCodec(ResourceType.class)));

    /**
     * Tier de origem do Cartão de Upgrade (pelo nome do tier): o cartão só vale num bloco desse tier. Ausente =
     * cartão antigo (de antes desta regra), que sobe de qualquer tier abaixo do destino.
     */
    public static final Supplier<DataComponentType<RouterTier>> UPGRADE_FROM =
            DATA_COMPONENTS.registerComponentType("upgrade_from", builder -> builder
                    .persistent(RouterTier.CODEC)
                    .networkSynchronized(RouterTier.STREAM_CODEC));

    /** O Eco Ressonante do Núcleo Ultimate quebrado, que volta ao colocar; sem o componente, 0. */
    public static final Supplier<DataComponentType<Long>> RESONATOR_ECO =
            DATA_COMPONENTS.registerComponentType("resonator_eco", builder -> builder
                    .persistent(Codec.LONG.validate(value -> value >= 0
                            ? DataResult.success(value) : DataResult.error(() -> "Eco negativo: " + value)))
                    .networkSynchronized(ByteBufCodecs.VAR_LONG));

    private ModDataComponents() {
    }
}
