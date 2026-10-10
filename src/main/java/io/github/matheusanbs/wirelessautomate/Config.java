package io.github.matheusanbs.wirelessautomate;

import io.github.matheusanbs.wirelessautomate.block.RouterTier;
import io.github.matheusanbs.wirelessautomate.network.ResourceType;
import io.github.matheusanbs.wirelessautomate.storage.StorageKind;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import net.minecraftforge.common.ForgeConfigSpec;

/**
 * Config do servidor ({@code config/wirelessautomate-server.toml}), para o modpack ajustar.
 * Nos limites de vazão, 0 significa sem limite.
 */
public final class Config {
    public static final ForgeConfigSpec SPEC;

    public static final ForgeConfigSpec.DoubleValue TICK_BUDGET_MS;
    public static final ForgeConfigSpec.BooleanValue ADAPTIVE_BUDGET;
    public static final Map<RouterTier, TierValues> TIERS = new EnumMap<>(RouterTier.class);
    public static final ForgeConfigSpec.BooleanValue CHUNK_LOADING_ENABLED;
    public static final ForgeConfigSpec.IntValue CHUNK_LOADING_MAX_PER_PLAYER;
    public static final ForgeConfigSpec.IntValue LINKER_MAX_AREA_VOLUME;
    public static final ForgeConfigSpec.IntValue LINKER_MAX_DISTANCE;
    public static final ForgeConfigSpec.BooleanValue GIVE_GUIDE_ON_FIRST_JOIN;
    /** Versão do balanceamento já aplicada a este arquivo (ver {@link #migrateBalance()}). */
    public static final ForgeConfigSpec.IntValue BALANCE_VERSION;
    /** 1 = a escada ×8 da Esmeralda e do Allthemodium (8/10/2026). */
    public static final int CURRENT_BALANCE = 1;
    /** Capacidade dos armazenamentos do mod por tipo e tier (todos os tipos somados; 0 = sem limite). */
    public static final Map<StorageKind, Map<RouterTier, ForgeConfigSpec.LongValue>> STORAGE_CAPACITY =
            new EnumMap<>(StorageKind.class);

    /** Comentário de cada chave de vazão (a ordem no arquivo vem do enum {@link ResourceType}, não deste mapa). */
    private static final Map<String, String> RATE_COMMENTS = Map.of(
            "itemsPerSecond", "Itens por segundo, por face e por tipo (0 = sem limite).",
            "fluidPerSecond", "Fluido e químico em mB por segundo (0 = sem limite).",
            "energyPerTick", "Energia em FE por tick (0 = sem limite).",
            "sourcePerSecond", "Source por segundo, por face (Ars Nouveau; 0 = sem limite).");

    public record TierValues(
            Map<String, ForgeConfigSpec.LongValue> rates,
            ForgeConfigSpec.IntValue range,
            ForgeConfigSpec.BooleanValue crossDimension) {
        /**
         * Vazão do tipo na unidade da config ({@link ResourceType#ratePerTick()}); 0 = sem limite. O
         * padrão do tier enquanto a config do servidor não carregou (tela fora de um mundo).
         */
        public long rate(ResourceType type) {
            return read(rates.get(type.rateKey()));
        }
    }

    /**
     * O valor da config do servidor, ou o padrão enquanto ela não carregou: no cliente fora de um
     * mundo (tela de título, modo de capturas) o {@code get()} lança {@link IllegalStateException}.
     */
    public static <T> T read(ForgeConfigSpec.ConfigValue<T> value) {
        return SPEC.isLoaded() ? value.get() : value.getDefault();
    }

    static {
        ForgeConfigSpec.Builder builder = new ForgeConfigSpec.Builder();

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
            Map<String, ForgeConfigSpec.LongValue> rates = new LinkedHashMap<>();
            for (ResourceType type : ResourceType.values()) {
                if (!rates.containsKey(type.rateKey())) {
                    rates.put(type.rateKey(), builder.comment(Objects.requireNonNull(RATE_COMMENTS.get(type.rateKey()), "sem comentário para a vazão " + type.rateKey()))
                            .defineInRange(type.rateKey(), type.defaultRate(tier.ordinal()), 0L, Long.MAX_VALUE));
                }
            }
            TIERS.put(tier, new TierValues(Map.copyOf(rates),
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

        builder.push("storage");
        for (StorageKind kind : StorageKind.values()) {
            builder.comment(switch (kind) {
                case CHEST -> "Capacidade do Baú por tier, em itens (todos os tipos somados; 0 = sem limite).";
                case TANK -> "Capacidade do Tanque por tier, em mB (todos os fluidos somados; 0 = sem limite).";
                case BATTERY -> "Capacidade da Bateria por tier, em FE (0 = sem limite).";
                case CHEMICAL_TANK -> "Capacidade do Tanque Químico por tier, em mB (todos os químicos somados; 0 = sem limite).";
                case SOURCE_TANK -> "Capacidade do Tanque de Source por tier, em Source (0 = sem limite).";
            });
            builder.push(kind.configKey);
            Map<RouterTier, ForgeConfigSpec.LongValue> byTier = new EnumMap<>(RouterTier.class);
            for (RouterTier tier : RouterTier.values()) {
                byTier.put(tier, builder.defineInRange(tier.getSerializedName(), kind.defaultCapacity(tier), 0L, Long.MAX_VALUE));
            }
            STORAGE_CAPACITY.put(kind, byTier);
            builder.pop();
        }
        builder.pop();

        builder.push("guide");
        GIVE_GUIDE_ON_FIRST_JOIN = builder
                .comment("Entrega o livro-guia (precisa do GuideME) a cada jogador no primeiro login.")
                .define("giveOnFirstJoin", true);
        builder.pop();

        builder.push("migration");
        BALANCE_VERSION = builder
                .comment("Versão do balanceamento já aplicada (não mexa). Abaixo da atual, os valores de vazão, alcance e",
                        "capacidade que ainda estão no padrão antigo passam para o novo na próxima carga; os que você mudou ficam.")
                .defineInRange("balanceVersion", 0, 0, CURRENT_BALANCE);
        builder.pop();

        SPEC = builder.build();
    }

    /** Padrões da escada de quatro tiers (×16), por tier antigo: Básico, Avançado e Elite (o Ultimate não mudou). */
    private static final RouterTier[] OLD_TIERS = {RouterTier.BASIC, RouterTier.ADVANCED, RouterTier.ELITE};
    private static final Map<String, long[]> OLD_RATES = Map.of(
            "itemsPerSecond", new long[] {512L, 8_192L, 131_072L},
            "fluidPerSecond", new long[] {32_000L, 512_000L, 8_000_000L},
            "energyPerTick", new long[] {16_000L, 256_000L, 4_000_000L},
            "sourcePerSecond", new long[] {1_000L, 16_000L, 256_000L});
    private static final int[] OLD_RANGES = {128, 1_024, 0};
    private static final Map<StorageKind, long[]> OLD_CAPACITY = Map.of(
            StorageKind.CHEST, new long[] {262_144L, 16_777_216L, 1_073_741_824L},
            StorageKind.TANK, new long[] {1_000_000L, 64_000_000L, 4_000_000_000L},
            StorageKind.BATTERY, new long[] {16_000_000L, 1_000_000_000L, 64_000_000_000L},
            StorageKind.CHEMICAL_TANK, new long[] {1_000_000L, 64_000_000L, 4_000_000_000L},
            StorageKind.SOURCE_TANK, new long[] {160_000L, 2_560_000L, 40_960_000L});

    /**
     * Na primeira carga depois da 1.2, troca os valores que ainda estão no padrão antigo pelo novo (senão,
     * num mundo que já existia, o Elite ficaria acima da Esmeralda); o que o dono do servidor mudou fica.
     * Roda uma vez por arquivo, marcada em {@code migration.balanceVersion}. Devolve quantos valores mudaram.
     */
    public static int migrateBalance() {
        if (BALANCE_VERSION.get() >= CURRENT_BALANCE) {
            return 0;
        }
        int changed = 0;
        for (int i = 0; i < OLD_TIERS.length; i++) {
            RouterTier tier = OLD_TIERS[i];
            TierValues values = TIERS.get(tier);
            for (ResourceType type : ResourceType.values()) {
                ForgeConfigSpec.LongValue rate = values.rates().get(type.rateKey());
                long target = type.defaultRate(tier.ordinal());
                if (rate.get() == OLD_RATES.get(type.rateKey())[i] && rate.get() != target) {
                    rate.set(target);
                    changed++;
                }
            }
            if (values.range().get() == OLD_RANGES[i] && values.range().get() != tier.defaultRange) {
                values.range().set(tier.defaultRange);
                changed++;
            }
            for (StorageKind kind : StorageKind.values()) {
                ForgeConfigSpec.LongValue capacity = STORAGE_CAPACITY.get(kind).get(tier);
                long target = kind.defaultCapacity(tier);
                if (capacity.get() == OLD_CAPACITY.get(kind)[i] && capacity.get() != target) {
                    capacity.set(target);
                    changed++;
                }
            }
        }
        BALANCE_VERSION.set(CURRENT_BALANCE);
        SPEC.save();
        return changed;
    }

    /** Capacidade do armazenamento no tier, pela config (o padrão se ela ainda não carregou); 0 = sem limite. */
    public static long storageCapacity(StorageKind kind, RouterTier tier) {
        return SPEC.isLoaded() ? STORAGE_CAPACITY.get(kind).get(tier).get() : kind.defaultCapacity(tier);
    }

    /** Capacidade do Baú no tier; 0 = sem limite. */
    public static long chestCapacity(RouterTier tier) {
        return storageCapacity(StorageKind.CHEST, tier);
    }

    private Config() {
    }
}
