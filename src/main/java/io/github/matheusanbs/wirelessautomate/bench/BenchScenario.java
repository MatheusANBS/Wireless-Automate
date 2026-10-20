package io.github.matheusanbs.wirelessautomate.bench;

import io.github.matheusanbs.wirelessautomate.block.RouterTier;
import java.util.Locale;
import org.jetbrains.annotations.Nullable;

/**
 * Cenários do benchmark (docs/especificacao.md, "Plano de benchmark", e docs/benchmark.md).
 * {@code n} é o número de roteadores; nos cenários em pares, metade origem e metade destino. Nos do
 * Ressonador ({@link #resonator()}), {@code n} é o número de Ressonadores.
 *
 * <p>Os cenários de muitos nós usam o Elite (2.048 itens/s por face).
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
            "Pilha enorme: origem de 1 slot com 1.000.000 de itens para um ralo, tier Allthemodium (131.072 itens/s; máquinas de teste)"),
    REDSTONE("redstone", 100, RouterTier.ELITE, 60, 20,
            "Relógio de redstone: como Muitos nós, com um bloco de redstone que liga e desliga a cada tick em cima de cada roteador (nenhum usa redstone)"),
    REDSTONE_FACE("redstone-face", 100, RouterTier.ELITE, 60, 20,
            "Relógio nas faces: como o relógio de redstone, com as faces das origens em redstone alto (cada pulso liga ou desliga as portas)"),
    TABLET("tablet", 1000, RouterTier.ELITE, 60, 20,
            "Tablet aberto: como Muitos nós, com um jogador falso de Tablet aberto, sincronizado a cada tick"),
    PAIRS("pairs", 500, RouterTier.ELITE, 60, 20,
            "Pares: como Muitos nós, mas cada origem numa rede só com o destino vizinho (muitas redes pequenas)"),
    INFINITE("inf", 2, RouterTier.ULTIMATE, 60, 0,
            "Fonte e ralo infinitos: pares de máquinas de teste (pilha enorme para ralo), tier Ultimate, sem gargalo de inventário"),
    HUB("hub", 250, RouterTier.ELITE, 60, 20,
            "Baú central: um Baú do mod Elite de destino, com um roteador, e todas as outras máquinas origens cheias de pedregulho"),
    CHEST_TYPES("chesttypes", 20, RouterTier.ELITE, 60, 20,
            "Baú com milhares de tipos: pares de origem cheia de pedregulho e destino Baú do mod Elite com 2.000 tipos de papel e lista branca de pedregulho com estoque"),
    RESONATOR_GEN("resonator-gen", 100, RouterTier.ULTIMATE, 100, 0,
            "Ressonadores gerando: Ultimate montados e sintonizados, 8 Lapidados, cada um com um roteador na Porta de Energia para uma Bateria"),
    RESONATOR_IDLE("resonator-idle", 500, RouterTier.ULTIMATE, 300, 0,
            "Ressonadores ociosos: os mesmos, sem roteador; o buffer enche em 10 s e para (o custo de existir)"),
    RESONATOR_EVENT("resonator-event", 50, RouterTier.ULTIMATE, 100, 0,
            "Evento no Ressonador: no meio da medição, um canto de cada casca sai no mesmo tick (todos revalidam) e volta 40 ticks depois"),
    RANGE("range", 200, RouterTier.ULTIMATE, 100, 0,
            "Alcance: roteadores Ultimate com o Upgrade de Alcance, cada um com 64 barris vazios vinculados num grupo que extrai, nada para mover"),
    // Reenche a cada 30 ticks, e não 20: o Ultimate esvazia a presa num tick, e com o reenchimento no ritmo da
    // amostra do Tablet (20 ticks) a vazão de cada nó seria a mesma em toda amostra e nada mudaria na Lista.
    TABLET_RANGE("tablet-range", 100, RouterTier.ULTIMATE, 60, 30,
            "Tablet com alcance: roteadores Ultimate de alcance, cada um com 16 barris vinculados num grupo que recebe os itens da máquina presa, e um jogador falso com o Tablet aberto"),
    TABLET_MACHINES("tablet-machines", 100, RouterTier.ULTIMATE, 60, 30,
            "Tablet por máquinas: a cena do tablet-range com as máquinas vinculadas alternando barril, baú, fornalha e alto-forno (um grupo por bloco), e o jogador falso na visão por máquinas da Lista, os blocos recolhidos e o dos barris aberto");

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

    /** Mede o Ressonador de Sinal ({@link BenchResonators}); a cena não tem máquinas de itens. */
    public boolean resonator() {
        return this == RESONATOR_GEN || this == RESONATOR_IDLE || this == RESONATOR_EVENT;
    }

    /**
     * Tem equivalente direto no Logistics Network (docs/benchmark-logistics-network.md). Os outros dependem de
     * algo só nosso (filtros e estoque, remontagem por mudança de face, Tablet, tier Allthemodium).
     */
    public boolean comparable() {
        return switch (this) {
            case MANY, IDLE, FULL, RAW, BIG, BIG_FULL, MIXED, SPARSE, REDSTONE, INFINITE, PAIRS -> true;
            case TYPES, REBUILD, STOCK, BIG_STACK, TABLET, REDSTONE_FACE, HUB, CHEST_TYPES, RESONATOR_GEN, RESONATOR_IDLE,
                    RESONATOR_EVENT, RANGE, TABLET_RANGE, TABLET_MACHINES -> false;
        };
    }

    /** Um jogador falso tem o Tablet aberto, sincronizado a cada tick ({@link BenchScene#openTablet}). */
    public boolean tablet() {
        return this == TABLET || this == TABLET_RANGE || this == TABLET_MACHINES;
    }

    /**
     * Cada roteador tem o Upgrade de Alcance e {@link #rangeMachines()} barris vinculados num grupo: no {@link #RANGE}
     * o grupo extrai e nada se move; no {@link #TABLET_RANGE} e no {@link #TABLET_MACHINES} a máquina presa é a origem
     * e o grupo recebe ({@link #feedsRemotes()}).
     */
    public boolean range() {
        return this == RANGE || feedsRemotes();
    }

    /** A cena do {@link #TABLET_RANGE}: a máquina presa é a origem e o grupo dos barris vinculados recebe. */
    public boolean feedsRemotes() {
        return this == TABLET_RANGE || this == TABLET_MACHINES;
    }

    /** Máquinas remotas vinculadas a cada roteador nos cenários de alcance ({@link #range()}); 0 nos outros. */
    public int rangeMachines() {
        return switch (this) {
            case RANGE -> 64;
            case TABLET_RANGE, TABLET_MACHINES -> 16;
            default -> 0;
        };
    }

    /** O destino é sempre o Baú do mod ({@link BenchStorage#chest()}), qualquer que seja o armazenamento das origens. */
    public boolean modChestDestination() {
        return this == HUB || this == CHEST_TYPES;
    }

    /**
     * Mede o heap que a cena retém ({@link BenchRun}): um GC completo antes de montar e outro antes de medir, fora
     * do tempo medido. Só onde a memória é o que se quer ver, porque o GC atrapalha os outros cenários.
     */
    public boolean measuresMemory() {
        return this == RANGE;
    }

    /** Tem os relógios de redstone em cima dos roteadores. */
    public boolean clocked() {
        return this == REDSTONE || this == REDSTONE_FACE;
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
