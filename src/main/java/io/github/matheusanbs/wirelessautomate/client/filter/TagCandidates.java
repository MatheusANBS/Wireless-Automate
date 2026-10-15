package io.github.matheusanbs.wirelessautomate.client.filter;

import io.github.matheusanbs.wirelessautomate.filter.FilterEntry;
import io.github.matheusanbs.wirelessautomate.filter.FilterEntry.ChemicalEntry;
import io.github.matheusanbs.wirelessautomate.filter.FilterEntry.ModEntry;
import io.github.matheusanbs.wirelessautomate.filter.FilterEntry.TagEntry;
import io.github.matheusanbs.wirelessautomate.network.Chemicals;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import net.minecraft.core.HolderSet;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.material.Fluid;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.fluids.FluidStack;
import org.jetbrains.annotations.Nullable;

/**
 * As linhas da aba Tags: as tags (e o mod) do que está no inspetor, ou as tags, mods e químicos que a
 * busca acha; e o que o texto da busca adiciona sozinho ({@code #tag}, {@code @mod} ou um químico).
 */
final class TagCandidates {
    /** Uma linha da aba Tags: tag, mod ou químico para marcar; {@code count} = itens que ela pega (−1 = não se sabe). */
    record Candidate(FilterEntry entry, String label, int count) {
    }

    private final FilterScreen screen;
    private final FilterMembers members;
    private @Nullable List<Candidate> allTags;

    TagCandidates(FilterScreen screen, FilterMembers members) {
        this.screen = screen;
        this.members = members;
    }

    /** O que o texto da busca adiciona sozinho, ou {@code null}: {@code @mod}, {@code #tag} ou o id de um químico. */
    @Nullable FilterEntry typedEntry(String raw) {
        String text = raw.strip().toLowerCase(Locale.ROOT);
        if (text.startsWith("@")) {
            String mod = text.substring(1);
            return mod.matches("[a-z0-9_.-]{1,64}") ? new ModEntry(mod, 0) : null;
        }
        if (screen.isChemical()) {
            ResourceLocation id = ResourceLocation.tryParse(text);
            return id != null && text.contains(":") && Chemicals.exists(id) ? new ChemicalEntry(id, 0) : null;
        }
        String tag = text.startsWith("#") ? text.substring(1) : text;
        ResourceLocation id = tag.contains(":") ? ResourceLocation.tryParse(tag) : null;
        return id == null || id.getPath().isEmpty() ? null : new TagEntry(id, 0);
    }

    /** As linhas para a busca (já sem espaços e em minúsculas) e o que está no inspetor. */
    List<Candidate> build(String search, ItemStack inspected, FluidStack inspectedFluid) {
        boolean chemical = screen.isChemical();
        List<Candidate> list = new ArrayList<>();
        if (search.startsWith("@")) {
            String q = search.substring(1);
            Map<String, Integer> counts = members.modCounts();
            ModList.get().getMods().stream()
                    .map(info -> info.getModId())
                    .filter(id -> id.contains(q) && (chemical || counts.getOrDefault(id, 0) > 0))
                    .sorted(Comparator.comparingInt((String id) -> -counts.getOrDefault(id, 0)).thenComparing(id -> id))
                    .forEach(id -> list.add(new Candidate(new ModEntry(id, 0), "@" + id, chemical ? -1 : counts.getOrDefault(id, 0))));
        } else if (!search.isEmpty()) {
            if (chemical) {
                FilterEntry typed = typedEntry(search);
                if (typed != null) {
                    list.add(new Candidate(typed, Chemicals.name(((ChemicalEntry) typed).chemical()).getString(), -1));
                }
            } else {
                String q = search.startsWith("#") ? search.substring(1) : search;
                for (Candidate tag : allTags()) {
                    if (tag.label().contains(q)) {
                        list.add(tag);
                    }
                }
                FilterEntry typed = typedEntry(search);
                if (typed instanceof TagEntry t && list.stream().noneMatch(c -> c.entry().sameTarget(t))) {
                    list.addFirst(new Candidate(typed, "#" + t.tag(), 0));
                }
            }
        } else if (!inspected.isEmpty()) {
            Registry<Item> registry = BuiltInRegistries.ITEM;
            inspected.getTags().map(tag -> tagCandidate(tag.location(), registry.getTag(tag).map(HolderSet::size).orElse(0)))
                    .sorted(Comparator.comparing(Candidate::label)).forEach(list::add);
            String mod = BuiltInRegistries.ITEM.getKey(inspected.getItem()).getNamespace();
            list.add(new Candidate(new ModEntry(mod, 0), "@" + mod, members.modCounts().getOrDefault(mod, 0)));
        } else if (!inspectedFluid.isEmpty()) {
            Registry<Fluid> registry = BuiltInRegistries.FLUID;
            FilterMembers.fluidTags(inspectedFluid.getFluid())
                    .map(tag -> tagCandidate(tag.location(), registry.getTag(tag).map(HolderSet::size).orElse(0)))
                    .sorted(Comparator.comparing(Candidate::label)).forEach(list::add);
            String mod = BuiltInRegistries.FLUID.getKey(inspectedFluid.getFluid()).getNamespace();
            list.add(new Candidate(new ModEntry(mod, 0), "@" + mod, members.modCounts().getOrDefault(mod, 0)));
        }
        return list;
    }

    private static Candidate tagCandidate(ResourceLocation tag, int count) {
        return new Candidate(new TagEntry(tag, 0), "#" + tag, count);
    }

    /** Todas as tags do tipo (itens ou fluidos), com quantos membros têm; montada uma vez por tela. */
    private List<Candidate> allTags() {
        if (allTags == null) {
            List<Candidate> list = new ArrayList<>();
            if (screen.isFluid()) {
                BuiltInRegistries.FLUID.getTags().forEach(pair -> list.add(tagCandidate(pair.getFirst().location(), pair.getSecond().size())));
            } else {
                BuiltInRegistries.ITEM.getTags().forEach(pair -> list.add(tagCandidate(pair.getFirst().location(), pair.getSecond().size())));
            }
            list.sort(Comparator.comparing(Candidate::label));
            allTags = list;
        }
        return allTags;
    }
}
