package io.github.matheusanbs.wirelessautomate.storage.upgrade;

import java.util.List;

/**
 * As cadeias que são variantes do mesmo material (a prata crua de cada mod), na ordem do id da forma base em texto: a
 * primeira é a preferida quando o jogador não escolheu ({@link CompactingRules#preferredOf}). Todas têm as mesmas formas
 * e fatores, então o nível {@code i} de uma troca 1 por 1 com o nível {@code i} de outra. O {@code id} só vale numa
 * geração de {@link CompactingChains}.
 */
public record VariantGroup(int id, List<CompactingChain> chains) {
}
