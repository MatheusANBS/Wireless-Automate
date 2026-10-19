package io.github.matheusanbs.wirelessautomate.client.filter;

import io.github.matheusanbs.wirelessautomate.filter.FilterEntry;
import io.github.matheusanbs.wirelessautomate.filter.FilterEntry.ModEntry;
import io.github.matheusanbs.wirelessautomate.filter.FilterEntry.TagEntry;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.fluids.FluidStack;
import org.jetbrains.annotations.Nullable;

/**
 * O que uma tag ou um mod pega, para os ícones da lista e as prévias (até 64), e quantos itens (ou
 * fluidos) cada mod tem, para a busca {@code @mod}. Montado sob demanda e guardado enquanto a tela vive; o
 * agrupamento por mod sai de uma passada só pelo registro ({@link ByMod}), guardada entre as aberturas.
 */
final class FilterMembers {
    /** Os primeiros itens (ou fluidos de fonte) de cada mod, até {@link #ICONS}, e quantos o mod tem. */
    private record ByMod<T>(Map<String, List<T>> first, Map<String, Integer> counts) {
        static <T> ByMod<T> of(Iterable<T> all, java.util.function.Function<T, String> modOf) {
            Map<String, List<T>> first = new HashMap<>();
            Map<String, Integer> counts = new HashMap<>();
            for (T value : all) {
                String mod = modOf.apply(value);
                counts.merge(mod, 1, Integer::sum);
                List<T> list = first.computeIfAbsent(mod, k -> new ArrayList<>());
                if (list.size() < ICONS) {
                    list.add(value);
                }
            }
            return new ByMod<>(first, counts);
        }

        List<T> firstOf(String mod) {
            return first.getOrDefault(mod, List.of());
        }
    }

    private static final int ICONS = 64;
    /**
     * Os registros de itens e fluidos do jogo ficam congelados depois da carga (as recargas de dados mexem nas
     * tags, não nos registros), então a passada vale até o jogo fechar.
     */
    private static @Nullable ByMod<Item> itemsByMod;
    private static @Nullable ByMod<Fluid> fluidsByMod;

    private final FilterScreen screen;
    private final Map<String, List<ItemStack>> itemIcons = new HashMap<>();
    private final Map<String, List<FluidStack>> fluidIcons = new HashMap<>();

    FilterMembers(FilterScreen screen) {
        this.screen = screen;
    }

    List<ItemStack> itemMembers(FilterEntry entry) {
        return switch (entry) {
            case TagEntry e -> itemIcons.computeIfAbsent("#" + e.tag(), k -> {
                List<ItemStack> list = new ArrayList<>();
                BuiltInRegistries.ITEM.getTag(TagKey.create(Registries.ITEM, e.tag()))
                        .ifPresent(set -> set.stream().limit(64).forEach(h -> list.add(new ItemStack(h))));
                return list;
            });
            case ModEntry e -> itemIcons.computeIfAbsent("@" + e.modId(), k -> {
                List<ItemStack> list = new ArrayList<>();
                for (Item item : itemsByMod().firstOf(e.modId())) {
                    list.add(new ItemStack(item));
                }
                return list;
            });
            default -> List.of();
        };
    }

    List<FluidStack> fluidMembers(FilterEntry entry) {
        return switch (entry) {
            case TagEntry e -> fluidIcons.computeIfAbsent("#" + e.tag(), k -> {
                List<FluidStack> list = new ArrayList<>();
                BuiltInRegistries.FLUID.getTag(TagKey.create(Registries.FLUID, e.tag())).map(HolderSet::stream)
                        .ifPresent(stream -> stream.map(Holder::value).filter(FilterMembers::isSource).limit(64)
                                .forEach(f -> list.add(new FluidStack(f, 1000))));
                return list;
            });
            case ModEntry e -> fluidIcons.computeIfAbsent("@" + e.modId(), k -> {
                List<FluidStack> list = new ArrayList<>();
                for (Fluid fluid : fluidsByMod().firstOf(e.modId())) {
                    list.add(new FluidStack(fluid, 1000));
                }
                return list;
            });
            default -> List.of();
        };
    }

    static boolean isSource(Fluid fluid) {
        return fluid != Fluids.EMPTY && fluid.isSource(fluid.defaultFluidState());
    }

    @SuppressWarnings("deprecation")
    static Stream<TagKey<Fluid>> fluidTags(Fluid fluid) {
        return fluid.builtInRegistryHolder().tags();
    }

    /** Itens (ou fluidos de fonte) de cada mod, para a busca {@code @mod}. */
    Map<String, Integer> modCounts() {
        return screen.isFluid() ? fluidsByMod().counts() : itemsByMod().counts();
    }

    private static ByMod<Item> itemsByMod() {
        if (itemsByMod == null) {
            itemsByMod = ByMod.of(BuiltInRegistries.ITEM, item -> BuiltInRegistries.ITEM.getKey(item).getNamespace());
        }
        return itemsByMod;
    }

    private static ByMod<Fluid> fluidsByMod() {
        if (fluidsByMod == null) {
            fluidsByMod = ByMod.of(BuiltInRegistries.FLUID.stream().filter(FilterMembers::isSource).toList(),
                    fluid -> BuiltInRegistries.FLUID.getKey(fluid).getNamespace());
        }
        return fluidsByMod;
    }

    int memberCount(TagEntry e) {
        return screen.isFluid()
                ? BuiltInRegistries.FLUID.getTag(TagKey.create(Registries.FLUID, e.tag())).map(HolderSet::size).orElse(0)
                : BuiltInRegistries.ITEM.getTag(TagKey.create(Registries.ITEM, e.tag())).map(HolderSet::size).orElse(0);
    }
}
