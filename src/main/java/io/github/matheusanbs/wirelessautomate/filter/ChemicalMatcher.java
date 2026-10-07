package io.github.matheusanbs.wirelessautomate.filter;

import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import java.util.List;
import net.minecraft.resources.ResourceLocation;

/**
 * {@link Filter} compilado para químicos do Mekanism, pelo id do químico (sem classes do Mekanism:
 * o filtro existe e é salvo mesmo sem ele). Vale o químico exato ({@link FilterEntry.ChemicalEntry})
 * e o mod ({@link FilterEntry.ModEntry}, pelo namespace do id); tags não se aplicam a químicos.
 * Dois mapas de hash, então a consulta custa o mesmo com qualquer número de entradas.
 */
final class ChemicalMatcher {
    /** Alguma entrada que vale para químicos tem estoque. */
    final boolean usesStock;
    private final Object2IntOpenHashMap<ResourceLocation> exact = new Object2IntOpenHashMap<>();
    private final Object2IntOpenHashMap<String> mods = new Object2IntOpenHashMap<>();

    ChemicalMatcher(List<FilterEntry> entries) {
        exact.defaultReturnValue(CompiledMatcher.NONE);
        mods.defaultReturnValue(CompiledMatcher.NONE);
        boolean stock = false;
        for (int i = 0, n = entries.size(); i < n; i++) {
            switch (entries.get(i)) {
                case FilterEntry.ChemicalEntry e -> {
                    exact.putIfAbsent(e.chemical(), i);
                    stock |= e.stock() > 0;
                }
                case FilterEntry.ModEntry e -> {
                    mods.putIfAbsent(e.modId(), i);
                    stock |= e.stock() > 0;
                }
                default -> {
                }
            }
        }
        usesStock = stock;
    }

    /** Índice da primeira entrada que casa com o químico, ou {@link CompiledMatcher#NONE}. */
    int index(ResourceLocation chemical) {
        int index = exact.getInt(chemical);
        if (!mods.isEmpty()) {
            index = CompiledMatcher.first(index, mods.getInt(chemical.getNamespace()));
        }
        return index;
    }
}
