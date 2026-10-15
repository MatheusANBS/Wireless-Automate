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
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.fluids.FluidStack;
import org.jetbrains.annotations.Nullable;

/**
 * O que uma tag ou um mod pega, para os ícones da lista e as prévias (até 64), e quantos itens (ou
 * fluidos) cada mod tem, para a busca {@code @mod}. Montado sob demanda e guardado enquanto a tela vive.
 */
final class FilterMembers {
    private final FilterScreen screen;
    private final Map<String, List<ItemStack>> itemIcons = new HashMap<>();
    private final Map<String, List<FluidStack>> fluidIcons = new HashMap<>();
    private @Nullable Map<String, Integer> modCounts;

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
                for (Item item : BuiltInRegistries.ITEM) {
                    if (list.size() < 64 && BuiltInRegistries.ITEM.getKey(item).getNamespace().equals(e.modId())) {
                        list.add(new ItemStack(item));
                    }
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
                for (Fluid fluid : BuiltInRegistries.FLUID) {
                    if (list.size() < 64 && isSource(fluid)
                            && BuiltInRegistries.FLUID.getKey(fluid).getNamespace().equals(e.modId())) {
                        list.add(new FluidStack(fluid, 1000));
                    }
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

    /** Itens (ou fluidos) de cada mod, para a busca {@code @mod}; montado uma vez por tela. */
    Map<String, Integer> modCounts() {
        if (modCounts == null) {
            Map<String, Integer> counts = new HashMap<>();
            if (screen.isFluid()) {
                for (Fluid fluid : BuiltInRegistries.FLUID) {
                    if (isSource(fluid)) {
                        counts.merge(BuiltInRegistries.FLUID.getKey(fluid).getNamespace(), 1, Integer::sum);
                    }
                }
            } else {
                for (ResourceLocation id : BuiltInRegistries.ITEM.keySet()) {
                    counts.merge(id.getNamespace(), 1, Integer::sum);
                }
            }
            modCounts = counts;
        }
        return modCounts;
    }

    int memberCount(TagEntry e) {
        return screen.isFluid()
                ? BuiltInRegistries.FLUID.getTag(TagKey.create(Registries.FLUID, e.tag())).map(HolderSet::size).orElse(0)
                : BuiltInRegistries.ITEM.getTag(TagKey.create(Registries.ITEM, e.tag())).map(HolderSet::size).orElse(0);
    }
}
