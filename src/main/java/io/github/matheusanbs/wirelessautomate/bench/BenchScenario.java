package io.github.matheusanbs.wirelessautomate.bench;

import io.github.matheusanbs.wirelessautomate.block.RouterTier;
import java.util.Locale;
import org.jetbrains.annotations.Nullable;

/**
 * Cenários do benchmark (docs/especificacao.md, "Plano de benchmark", e docs/benchmark.md).
 * {@code n} é o número de roteadores; nos cenários em pares, metade origem e metade destino.
 *
 * <p>Os cenários de muitos nós usam o Elite (2.048 itens/s por face desde o balanceamento ×8; antes era o
 * Básico, a 512): números de antes de 8/10/2026 não se comparam um a um com os de depois.
 *
 * <p>{@code recyclePeriod}: a cada tantos ticks o benchmark reenche as origens e esvazia os
 * destinos, fora do tempo medido, para a rede seguir ocupada durante a medição (0 = nunca).
 */
public enum BenchScenario {
    MANY("many", 500, RouterTier.ELITE, 60, 20,
            "Muitos nós: metade origens cheias de pedregulho, metade destinos vazios, todos ativos"),
    IDLE("idle", 500, RouterTier.ELITE, 300, 0,
            "Rede ociosa: origens e destinos vazios, nada para mover"),
    FULL("full", 100, RouterTier.ELITE, 300, 0,
            "Destino cheio: origens cheias enviando para destinos já cheios"),
    RAW("raw", 2, RouterTier.ULTIMATE, 40, 1,
            "Vazão bruta: inventário grande cheio para inventário grande vazio, tier Ultimate"),
    BIG("big", 20, RouterTier.ELITE, 60, 20,
            "Inventário grande: um item diferente por slot na origem, destino grande vazio"),
    BIG_FULL("bigfull", 20, RouterTier.ELITE, 60, 20,
            "Inventário grande quase cheio: o destino só tem espaço nos 4 últimos slots"),
    TYPES("types", 20, RouterTier.ELITE, 60, 20,
            "Muitos tipos: centenas de itens diferentes e filtros com milhares de entradas"),
    MIXED("mixed", 498, RouterTier.ELITE, 60, 20,
            "Misto: um terço itens, um terço fluidos e um terço energia, ao mesmo tempo"),
    REBUILD("rebuild", 500, RouterTier.ELITE, 60, 20,
            "Remontagem: como Muitos nós, e um nó muda de configuração a cada segundo"),
    SPARSE("sparse", 500, RouterTier.ELITE, 60, 20,
            "Esparsa: só uma origem tem itens, as outras estão vazias; destinos vazios"),
    STOCK("stock", 20, RouterTier.ELITE, 60, 0,
            "Estoque atingido: origem grande cheia de pedregulho, destino grande com lista branca e o estoque já completo"),
    BIG_STACK("bigstack", 2, RouterTier.ALLTHEMODIUM, 60, 0,
            "Pilha enorme: origem de 1 slot com 1.000.000 de itens para um ralo, tier Allthemodium (131.072 itens/s, o antigo Elite; máquinas de teste)"),
    REDSTONE("redstone", 100, RouterTier.ELITE, 60, 20,
            "Relógio de redstone: como Muitos nós, com um bloco de redstone que liga e desliga a cada tick em cima de cada roteador (nenhum usa redstone)"),
    TABLET("tablet", 1000, RouterTier.ELITE, 60, 20,
            "Tablet aberto: como Muitos nós, com um jogador falso de Tablet aberto, sincronizado a cada tick"),
    PAIRS("pairs", 500, RouterTier.ELITE, 60, 20,
            "Pares: como Muitos nós, mas cada origem numa rede só com o destino vizinho (muitas redes pequenas)"),
    INFINITE("inf", 2, RouterTier.ULTIMATE, 60, 0,
            "Fonte e ralo infinitos: pares de máquinas de teste (pilha enorme para ralo), tier Ultimate, sem gargalo de inventário");

    public final String id;
    public final int defaultNodes;
    public final RouterTier tier;
    public final int warmupTicks;
    public final int recyclePeriod;
    public final String description;

    BenchScenario(String id, int defaultNodes, RouterTier tier, int warmupTicks, int recyclePeriod, String description) {
        this.id = id;
        this.defaultNodes = defaultNodes;
        this.tier = tier;
        this.warmupTicks = warmupTicks;
        this.recyclePeriod = recyclePeriod;
        this.description = description;
    }

    /** Usa inventário grande (baú duplo ou Sophisticated Storage de netherita). */
    public boolean bigInventories() {
        return this == RAW || this == BIG || this == BIG_FULL || this == TYPES || this == STOCK;
    }

    /**
     * Tem equivalente direto no Logistics Network (docs/benchmark-logistics-network.md). Os outros dependem de
     * algo só nosso (filtros e estoque, remontagem por mudança de face, Tablet, tier Allthemodium).
     */
    public boolean comparable() {
        return switch (this) {
            case MANY, IDLE, FULL, RAW, BIG, BIG_FULL, MIXED, SPARSE, REDSTONE, INFINITE, PAIRS -> true;
            case TYPES, REBUILD, STOCK, BIG_STACK, TABLET -> false;
        };
    }

    public static @Nullable BenchScenario byId(String id) {
        String key = id.toLowerCase(Locale.ROOT);
        for (BenchScenario scenario : values()) {
            if (scenario.id.equals(key)) {
                return scenario;
            }
        }
        return null;
    }
}
