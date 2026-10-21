package io.github.matheusanbs.wirelessautomate;

import io.github.matheusanbs.wirelessautomate.block.RouterTier;
import io.github.matheusanbs.wirelessautomate.network.ResourceType;
import io.github.matheusanbs.wirelessautomate.range.RangeTiers;
import io.github.matheusanbs.wirelessautomate.resonator.CoreMath;
import io.github.matheusanbs.wirelessautomate.storage.StorageKind;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
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
    /** Versão do balanceamento já aplicada a este arquivo (ver {@link #migrateBalance()}). */
    public static final ModConfigSpec.IntValue BALANCE_VERSION;
    /**
     * 1 = a escada ×8 da Esmeralda e do Allthemodium (8/10/2026); 2 = a energia do roteador Unobtainium ×8 e o FE
     * por cristal do Ressonador (15/10/2026); 3 = a escada nova do Ressonador e a energia do roteador por tier
     * (21/10/2026).
     */
    public static final int CURRENT_BALANCE = 3;
    /** Capacidade dos armazenamentos do mod por tipo e tier (todos os tipos somados; 0 = sem limite). */
    public static final Map<StorageKind, Map<RouterTier, ModConfigSpec.LongValue>> STORAGE_CAPACITY =
            new EnumMap<>(StorageKind.class);
    /** Prazo, em dias de servidor ligado, do conteúdo de um armazenamento quebrado ({@code StorageSavedData}). */
    public static final ModConfigSpec.IntValue STORAGE_STASH_EXPIRY_DAYS;

    public static final ModConfigSpec.LongValue CRYSTAL_LIFE_TICKS;
    public static final ModConfigSpec.LongValue CUT_CRYSTAL_LIFE_TICKS;
    /** Minutos que a carga dura a 100% de geração, um por tier do Núcleo (ver {@link #crystalLifeTicks(int)}). */
    public static final ModConfigSpec.ConfigValue<List<? extends Integer>> CRYSTAL_LIFE_MINUTES;
    private static final int MAX_LIFE_MINUTES = 240;
    public static final ModConfigSpec.LongValue RECHARGE_TICKS;
    public static final ModConfigSpec.IntValue CRYSTAL_INTEGRITY;
    public static final ModConfigSpec.IntValue RECAST_CRYSTALS;
    public static final ModConfigSpec.IntValue INTERFERENCE_RADIUS;
    public static final ModConfigSpec.DoubleValue INTERFERENCE_PENALTY;
    public static final ModConfigSpec.DoubleValue INTERFERENCE_MAX;
    public static final ModConfigSpec.DoubleValue ECO_BONUS;
    public static final ModConfigSpec.LongValue ECO_MAX;
    /** FE por tick que um cristal de rendimento 1 dá ao Núcleo de Ressonância, por tier. */
    public static final Map<RouterTier, ModConfigSpec.LongValue> RESONATOR_FE_PER_CRYSTAL = new EnumMap<>(RouterTier.class);

    /** Comentário de cada chave de vazão (a ordem no arquivo vem do enum {@link ResourceType}, não deste mapa). */
    private static final Map<String, String> RATE_COMMENTS = Map.of(
            "itemsPerSecond", "Itens por segundo, por face e por tipo (0 = sem limite).",
            "fluidPerSecond", "Fluido e químico em mB por segundo (0 = sem limite).",
            "energyPerTick", "Energia em FE por tick (0 = sem limite).",
            "sourcePerSecond", "Source por segundo, por face (Ars Nouveau; 0 = sem limite).");

    public record TierValues(
            Map<String, ModConfigSpec.LongValue> rates,
            ModConfigSpec.IntValue range,
            ModConfigSpec.BooleanValue crossDimension,
            ModConfigSpec.IntValue machineRadius,
            ModConfigSpec.IntValue maxMachines) {
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
    public static <T> T read(ModConfigSpec.ConfigValue<T> value) {
        return SPEC.isLoaded() ? value.get() : value.getDefault();
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
            Map<String, ModConfigSpec.LongValue> rates = new LinkedHashMap<>();
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
                            .define("crossDimension", tier.defaultCrossDimension),
                    builder.comment("Raio, em blocos, em que o Upgrade de Alcance vincula máquinas (0 = este tier não aceita o upgrade).")
                            .defineInRange("machineRadius", RangeTiers.defaultRadius(tier.ordinal()), 0, RangeTiers.MAX_RADIUS),
                    builder.comment("Máximo de máquinas vinculadas pelo Upgrade de Alcance.")
                            .defineInRange("maxMachines", RangeTiers.defaultMaxMachines(tier.ordinal()), 0, RangeTiers.MAX_MACHINES)));
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
            Map<RouterTier, ModConfigSpec.LongValue> byTier = new EnumMap<>(RouterTier.class);
            for (RouterTier tier : RouterTier.values()) {
                byTier.put(tier, builder.defineInRange(tier.getSerializedName(), kind.defaultCapacity(tier), 0L, Long.MAX_VALUE));
            }
            STORAGE_CAPACITY.put(kind, byTier);
            builder.pop();
        }
        STORAGE_STASH_EXPIRY_DAYS = builder
                .comment("Dias de servidor ligado que o conteúdo de um armazenamento quebrado fica guardado no servidor "
                        + "esperando o item ser colocado (0 = para sempre, o padrão). Com prazo, vencido, o conteúdo some: o item que ainda "
                        + "exista nasce vazio ao ser colocado. Carregar o item no inventário renova o prazo uma vez por dia; "
                        + "num baú ou num disco, não. É também o prazo do /wa storage recover.")
                .defineInRange("stashExpiryDays", 0, 0, 36_500);
        builder.pop();

        builder.push("resonator");
        CRYSTAL_LIFE_TICKS = builder
                .comment("Ticks de geração de um Cristal de Sinal cheio, no Núcleo de Ressonância, até ficar Opaco.")
                .defineInRange("crystalLifeTicks", 12_000L, 1L, Long.MAX_VALUE);
        CUT_CRYSTAL_LIFE_TICKS = builder
                .comment("Ticks de geração de um Cristal Lapidado cheio, até ficar Opaco.")
                .defineInRange("cutCrystalLifeTicks", 24_000L, 1L, Long.MAX_VALUE);
        List<Integer> lifeMinutes = new ArrayList<>();
        for (RouterTier tier : RouterTier.values()) {
            lifeMinutes.add(CoreMath.defaultLifeMinutes(tier.ordinal()));
        }
        CRYSTAL_LIFE_MINUTES = builder
                .comment("Minutos que a carga de um cristal dura no Núcleo de cada tier a 100% de geração, na ordem",
                        "Básico, Avançado, Elite, Esmeralda, Allthemodium, Vibranium, Unobtainium e Ultimate (1 a 240).",
                        "Em 20, o cristal dura o crystalLifeTicks (ou o cutCrystalLifeTicks) como está; em 10, metade.")
                .defineList("crystalLifeMinutes", List.copyOf(lifeMinutes),
                        () -> CoreMath.defaultLifeMinutes(0),
                        value -> value instanceof Integer minutes && minutes >= 1 && minutes <= MAX_LIFE_MINUTES);
        RECHARGE_TICKS = builder
                .comment("Ticks que o Núcleo leva para recarregar um cristal Opaco (gasta redstone).")
                .defineInRange("rechargeTicks", 600L, 1L, Long.MAX_VALUE);
        CRYSTAL_INTEGRITY = builder
                .comment("Cargas de um cristal até rachar: cada recarga gasta 1; com 0 ele racha e precisa ser refeito.")
                .defineInRange("crystalIntegrity", 8, 1, 64);
        RECAST_CRYSTALS = builder
                .comment("Cristais de Sinal cheios que refazem um cristal rachado na bancada.")
                .defineInRange("recastCrystals", 4, 1, 9);
        INTERFERENCE_RADIUS = builder
                .comment("Raio, em blocos (distância de Chebyshev entre os Núcleos), em que outro Núcleo montado e carregado",
                        "da mesma dimensão tira eficiência deste (0 desliga a interferência).")
                .defineInRange("interferenceRadius", 32, 0, 256);
        INTERFERENCE_PENALTY = builder
                .comment("Eficiência que cada Núcleo vizinho no raio tira (0,15 = 15%).")
                .defineInRange("interferencePenalty", 0.15, 0.0, 1.0);
        INTERFERENCE_MAX = builder
                .comment("Perda máxima da eficiência pela interferência, somados todos os vizinhos (0,60 = 60%).")
                .defineInRange("interferenceMax", 0.60, 0.0, 1.0);
        ECO_BONUS = builder
                .comment("Geração base que cada Eco Ressonante soma ao Núcleo Ultimate (0,05 = +5%).")
                .defineInRange("ecoBonus", 0.05, 0.0, 1.0);
        ECO_MAX = builder
                .comment("Eco Ressonante máximo de um Núcleo Ultimate (0 = sem teto).")
                .defineInRange("ecoMax", 0L, 0L, Long.MAX_VALUE);
        for (RouterTier tier : RouterTier.values()) {
            builder.push(tier.getSerializedName());
            RESONATOR_FE_PER_CRYSTAL.put(tier, builder
                    .comment("FE por tick de um cristal de rendimento 1 a 100% de sintonia (Lapidado e Afinado rendem 2; do Elite para cima, o cristal comum rende 1/4).")
                    .defineInRange("fePerCrystal", CoreMath.defaultFePerCrystal(tier.ordinal()), 0L, Long.MAX_VALUE));
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
                .comment("Versão do balanceamento já aplicada (não mexa). Abaixo da atual, os valores de vazão, alcance,",
                        "capacidade e geração que ainda estão no padrão antigo passam para o novo na próxima carga; os que você mudou ficam.")
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

    /** A energia do roteador Unobtainium no balanceamento 1 (FE/t). */
    private static final long OLD_UNOBTAINIUM_ENERGY = 262_144_000L;
    /** O FE por cristal do Ressonador antes do balanceamento 2, pela posição do tier. */
    private static final long[] OLD_FE_PER_CRYSTAL = {
        64, 512, 4_096, 32_768, 262_144, 2_097_152, 16_777_216L, 134_217_728L
    };
    /** O FE por cristal do Ressonador no balanceamento 2 (o destino do passo 2 e a origem do 3), pela posição do tier. */
    private static final long[] BALANCE2_FE_PER_CRYSTAL = {
        128, 1_024, 8_192, 32_768, 262_144, 2_097_152, 134_217_728L, 1_073_741_824L
    };
    /** A energia do roteador Unobtainium no balanceamento 2 (FE/t). */
    private static final long BALANCE2_UNOBTAINIUM_ENERGY = 2_097_152_000L;
    /** A energia do roteador por tier no balanceamento 2 (FE/t), pela posição do tier. */
    private static final long[] BALANCE2_ENERGY = {
        1_000, 8_000, 64_000, 512_000, 4_096_000, 32_768_000, 2_097_152_000L, 0
    };

    /**
     * Na primeira carga depois de um balanceamento novo, troca os valores que ainda estão no padrão antigo pelo
     * novo (senão, num mundo que já existia, o Elite ficaria acima da Esmeralda, e o roteador Unobtainium não daria
     * conta do Ressonador); o que o dono do servidor mudou fica. Cada passo roda uma vez por arquivo, marcado em
     * {@code migration.balanceVersion}. Devolve quantos valores mudaram.
     */
    public static int migrateBalance() {
        int version = BALANCE_VERSION.get();
        if (version >= CURRENT_BALANCE) {
            return 0;
        }
        int changed = version < 1 ? migrateBalance1() : 0;
        changed += version < 2 ? migrateBalance2() : 0;
        changed += migrateBalance3();
        BALANCE_VERSION.set(CURRENT_BALANCE);
        SPEC.save();
        return changed;
    }

    private static int migrateBalance2() {
        int changed = 0;
        ModConfigSpec.LongValue energy = TIERS.get(RouterTier.UNOBTAINIUM).rates().get(ResourceType.ENERGY.rateKey());
        if (energy.get() == OLD_UNOBTAINIUM_ENERGY) {
            energy.set(BALANCE2_UNOBTAINIUM_ENERGY);
            changed++;
        }
        for (RouterTier tier : RouterTier.values()) {
            ModConfigSpec.LongValue fe = RESONATOR_FE_PER_CRYSTAL.get(tier);
            long target = BALANCE2_FE_PER_CRYSTAL[tier.ordinal()];
            if (fe.get() == OLD_FE_PER_CRYSTAL[tier.ordinal()] && fe.get() != target) {
                fe.set(target);
                changed++;
            }
        }
        return changed;
    }

    private static int migrateBalance3() {
        int changed = 0;
        for (RouterTier tier : RouterTier.values()) {
            int i = tier.ordinal();
            ModConfigSpec.LongValue fe = RESONATOR_FE_PER_CRYSTAL.get(tier);
            long feTarget = CoreMath.defaultFePerCrystal(i);
            if (fe.get() == BALANCE2_FE_PER_CRYSTAL[i] && fe.get() != feTarget) {
                fe.set(feTarget);
                changed++;
            }
            ModConfigSpec.LongValue energy = TIERS.get(tier).rates().get(ResourceType.ENERGY.rateKey());
            long energyTarget = ResourceType.ENERGY.defaultRate(i);
            if (energy.get() == BALANCE2_ENERGY[i] && energy.get() != energyTarget) {
                energy.set(energyTarget);
                changed++;
            }
        }
        return changed;
    }

    private static int migrateBalance1() {
        int changed = 0;
        for (int i = 0; i < OLD_TIERS.length; i++) {
            RouterTier tier = OLD_TIERS[i];
            TierValues values = TIERS.get(tier);
            for (ResourceType type : ResourceType.values()) {
                ModConfigSpec.LongValue rate = values.rates().get(type.rateKey());
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
                ModConfigSpec.LongValue capacity = STORAGE_CAPACITY.get(kind).get(tier);
                long target = kind.defaultCapacity(tier);
                if (capacity.get() == OLD_CAPACITY.get(kind)[i] && capacity.get() != target) {
                    capacity.set(target);
                    changed++;
                }
            }
        }
        return changed;
    }

    /** FE por tick de um cristal de rendimento 1 no tier do Núcleo de Ressonância. */
    public static long resonatorFePerCrystal(RouterTier tier) {
        return read(RESONATOR_FE_PER_CRYSTAL.get(tier));
    }

    /** Ticks de geração de um cristal cheio, até ficar Opaco (o Lapidado, {@code cut}, dura o dobro por padrão). */
    public static long crystalLifeTicks(boolean cut) {
        return read(cut ? CUT_CRYSTAL_LIFE_TICKS : CRYSTAL_LIFE_TICKS);
    }

    /**
     * Ticks que a carga dura a 100% de geração no Núcleo do tier de posição {@code tierOrdinal}, pela lista
     * {@code crystalLifeMinutes}; a posição que falta na lista usa o padrão.
     */
    public static long crystalLifeTicks(int tierOrdinal) {
        List<? extends Integer> minutes = read(CRYSTAL_LIFE_MINUTES);
        int value = tierOrdinal >= 0 && tierOrdinal < minutes.size()
                ? minutes.get(tierOrdinal) : CoreMath.defaultLifeMinutes(tierOrdinal);
        return (long) Math.max(1, Math.min(value, MAX_LIFE_MINUTES)) * CoreMath.TICKS_PER_MINUTE;
    }

    /** Cargas de um cristal até rachar (cada recarga gasta uma). */
    public static int crystalIntegrity() {
        return read(CRYSTAL_INTEGRITY);
    }

    /** Cristais de Sinal cheios que refazem um cristal rachado na bancada. */
    public static int recastCrystals() {
        return read(RECAST_CRYSTALS);
    }

    /** Raio da interferência entre Núcleos (0 = desligada). */
    public static int interferenceRadius() {
        return read(INTERFERENCE_RADIUS);
    }

    /** Eficiência que cada vizinho no raio tira. */
    public static double interferencePenalty() {
        return read(INTERFERENCE_PENALTY);
    }

    /** Perda máxima da eficiência pela interferência. */
    public static double interferenceMax() {
        return read(INTERFERENCE_MAX);
    }

    /** Geração base que cada Eco soma (0,05 = +5%). */
    public static double ecoBonus() {
        return read(ECO_BONUS);
    }

    /** Eco máximo de um Núcleo (0 = sem teto). */
    public static long ecoMax() {
        return read(ECO_MAX);
    }

    /** Ticks que o Núcleo leva para recarregar um cristal Opaco. */
    public static long rechargeTicks() {
        return read(RECHARGE_TICKS);
    }

    /** Capacidade do armazenamento no tier, pela config (o padrão se ela ainda não carregou); 0 = sem limite. */
    public static long storageCapacity(StorageKind kind, RouterTier tier) {
        return SPEC.isLoaded() ? STORAGE_CAPACITY.get(kind).get(tier).get() : kind.defaultCapacity(tier);
    }

    /** Raio, em blocos, em que o Upgrade de Alcance vincula máquinas no tier (0 = o tier não aceita o upgrade). */
    /**
     * Um resumo dos valores dos tiers que as rotas leem (vazões, alcance, dimensão, raio e limite de máquinas): na
     * recarga da config, o gerenciador só remonta as redes se ele mudou.
     */
    public static long tiersFingerprint() {
        long hash = 1;
        for (TierValues values : TIERS.values()) {
            for (ModConfigSpec.LongValue rate : values.rates().values()) {
                hash = 31 * hash + read(rate);
            }
            hash = 31 * hash + read(values.range());
            hash = 31 * hash + (read(values.crossDimension()) ? 1 : 0);
            hash = 31 * hash + read(values.machineRadius());
            hash = 31 * hash + read(values.maxMachines());
        }
        return hash;
    }

    public static int machineRadius(RouterTier tier) {
        return read(TIERS.get(tier).machineRadius());
    }

    /** Máximo de máquinas vinculadas pelo Upgrade de Alcance no tier. */
    public static int maxMachines(RouterTier tier) {
        return read(TIERS.get(tier).maxMachines());
    }

    /** Capacidade do Baú no tier; 0 = sem limite. */
    public static long chestCapacity(RouterTier tier) {
        return storageCapacity(StorageKind.CHEST, tier);
    }

    private Config() {
    }
}
