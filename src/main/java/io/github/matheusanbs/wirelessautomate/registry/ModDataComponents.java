package io.github.matheusanbs.wirelessautomate.registry;

import io.github.matheusanbs.wirelessautomate.WirelessAutomate;
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

    private ModDataComponents() {
    }
}
