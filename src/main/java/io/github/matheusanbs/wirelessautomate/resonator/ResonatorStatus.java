package io.github.matheusanbs.wirelessautomate.resonator;

import io.github.matheusanbs.wirelessautomate.item.TierCoreItem;
import java.util.Locale;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import org.jetbrains.annotations.Nullable;

/**
 * A linha da action bar do clique no Núcleo: montado, o tier, os cristais nas câmaras, a eficiência e a geração;
 * solto, o problema que impede montar, com a posição do bloco quando ela importa.
 */
public final class ResonatorStatus {
    private static final String KEY = "block.wirelessautomate.resonance_core.";

    private ResonatorStatus() {
    }

    public static Component line(ResonanceCoreBlockEntity core) {
        if (core.formed()) {
            return Component.translatable(KEY + "status", Component.translatable(core.tier().translationKey()),
                    crystals(core), core.chambers(), Math.round(core.efficiency() * 100),
                    TierCoreItem.grouped(core.generationPerTick())).withStyle(ChatFormatting.AQUA);
        }
        ResonatorLayout.Broken problem = problem(core);
        if (problem == null) {
            return Component.translatable(KEY + "problem.pending").withStyle(ChatFormatting.RED);
        }
        return Component.translatable(KEY + "problem." + problem.problem().name().toLowerCase(Locale.ROOT),
                problem.x() + " " + problem.y() + " " + problem.z()).withStyle(ChatFormatting.RED);
    }

    /** Quantas câmaras do tier têm cristal. */
    public static int crystals(ResonanceCoreBlockEntity core) {
        int count = 0;
        for (int i = 0; i < core.chambers(); i++) {
            if (!core.crystals().get(i).isEmpty()) {
                count++;
            }
        }
        return count;
    }

    /**
     * O último problema guardado pela revalidação; sem ele (ainda não revalidou), confere a estrutura agora. Nulo
     * se ela já está certa e só falta a revalidação montar.
     */
    private static @Nullable ResonatorLayout.Broken problem(ResonanceCoreBlockEntity core) {
        ResonatorLayout.Broken last = core.lastProblem();
        if (last != null || !(core.getLevel() instanceof ServerLevel level)) {
            return last;
        }
        return ResonatorFormation.evaluate(level, core.getBlockPos()) instanceof ResonatorLayout.Broken broken
                ? broken : null;
    }
}
