package io.github.matheusanbs.wirelessautomate.network;

import java.util.List;
import net.neoforged.fml.ModList;

/** Os tipos de recurso que existem nesta instância (o mod de cada um está carregado), na ordem do registro. */
public final class LoadedTypes {
    public static final List<ResourceType> LIST = ResourceType.available(LoadedTypes::modLoaded);

    public static boolean contains(ResourceType type) {
        return LIST.contains(type);
    }

    private static boolean modLoaded(String mod) {
        return ModList.get() != null && ModList.get().isLoaded(mod);
    }

    private LoadedTypes() {
    }
}
