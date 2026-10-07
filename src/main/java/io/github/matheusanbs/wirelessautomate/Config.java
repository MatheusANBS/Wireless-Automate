package io.github.matheusanbs.wirelessautomate;

import io.github.matheusanbs.wirelessautomate.block.RouterTier;
import java.util.EnumMap;
import java.util.Map;
import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * Config do servidor ({@code config/wirelessautomate-server.toml}), para o modpack ajustar.
 * Nos limites de vazão, 0 significa sem limite.
 */
public final class Config {
    public static final ModConfigSpec SPEC;

    public static final ModConfigSpec.DoubleValue TICK_BUDGET_MS;
    public static final ModConfigSpec.BooleanValue ADAPTIVE_BUDGET;
    public static final Map<RouterTier, TierValues> TIERS = new EnumMap<>(RouterTier.class);
    public static final ModConfigSpec.BooleanValue CHUNK_LOADING_ENABLED;
    public static final ModConfigSpec.IntValue CHUNK_LOADING_MAX_PER_PLAYER;
    public static final ModConfigSpec.IntValue LINKER_MAX_AREA_VOLUME;
    public static final ModConfigSpec.IntValue LINKER_MAX_DISTANCE;
    public static final ModConfigSpec.BooleanValue GIVE_GUIDE_ON_FIRST_JOIN;

    public record TierValues(
            ModConfigSpec.LongValue itemsPerSecond,
            ModConfigSpec.LongValue fluidPerSecond,
            ModConfigSpec.LongValue energyPerTick,
            ModConfigSpec.IntValue range,
            ModConfigSpec.BooleanValue crossDimension) {
    }

    static {
        ModConfigSpec.Builder builder = new ModConfigSpec.Builder();

        builder.push("performance");
        TICK_BUDGET_MS = builder
                .comment("Teto de tempo do mod por tick, em ms. O trabalho que não couber continua no tick seguinte.")
                .defineInRange("tickBudgetMs", 1.0, 0.05, 50.0);
        ADAPTIVE_BUDGET = builder
                .comment("Reduz o teto sozinho quando o MSPT do servidor passa de 40 ms.")
                .define("adaptiveBudget", true);
        builder.pop();

        builder.push("tiers");
        for (RouterTier tier : RouterTier.values()) {
            builder.push(tier.getSerializedName());
            TIERS.put(tier, new TierValues(
                    builder.comment("Itens por segundo, por face e por tipo (0 = sem limite).")
                            .defineInRange("itemsPerSecond", tier.defaultItemsPerSecond, 0L, Long.MAX_VALUE),
                    builder.comment("Fluido e químico em mB por segundo (0 = sem limite).")
                            .defineInRange("fluidPerSecond", tier.defaultFluidPerSecond, 0L, Long.MAX_VALUE),
                    builder.comment("Energia em FE por tick (0 = sem limite).")
                            .defineInRange("energyPerTick", tier.defaultEnergyPerTick, 0L, Long.MAX_VALUE),
                    builder.comment("Alcance em blocos (0 = a dimensão inteira).")
                            .defineInRange("range", tier.defaultRange, 0, Integer.MAX_VALUE),
                    builder.comment("Permite rotas entre dimensões.")
                            .define("crossDimension", tier.defaultCrossDimension)));
            builder.pop();
        }
        builder.pop();

        builder.push("chunkLoading");
        CHUNK_LOADING_ENABLED = builder
                .comment("Liga o Upgrade de chunk loading. Desligado, os upgrades ficam inativos e os chunks são liberados.")
                .define("enabled", true);
        CHUNK_LOADING_MAX_PER_PLAYER = builder
                .comment("Chunks forçados por jogador (o dono é quem pôs o upgrade; 0 = sem limite).",
                        "Cada chunk conta uma vez, mesmo com vários roteadores nele. Acima do limite o upgrade fica inativo.")
                .defineInRange("maxChunksPerPlayer", 16, 0, Integer.MAX_VALUE);
        builder.pop();

        builder.push("linker");
        LINKER_MAX_AREA_VOLUME = builder
                .comment("Volume máximo, em blocos, da área do Vinculador e do Configurador (modo Área).")
                .defineInRange("maxAreaVolume", 262_144, 1, 16_777_216);
        LINKER_MAX_DISTANCE = builder
                .comment("Distância máxima, em blocos, do jogador até a área para vincular ou colar por área (0 = sem limite).")
                .defineInRange("maxDistance", 64, 0, 4096);
        builder.pop();

        builder.push("guide");
        GIVE_GUIDE_ON_FIRST_JOIN = builder
                .comment("Entrega o livro-guia (precisa do GuideME) a cada jogador no primeiro login.")
                .define("giveOnFirstJoin", true);
        builder.pop();

        SPEC = builder.build();
    }

    private Config() {
    }
}
