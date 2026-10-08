package io.github.matheusanbs.wirelessautomate.network;

import io.github.matheusanbs.wirelessautomate.block.RouterBlockEntity;
import net.minecraft.core.Direction;
import org.jetbrains.annotations.Nullable;

/**
 * Como o {@link ScalarTransfer} fala com um recurso de "um valor só" (sem tipos dentro: energia,
 * Source). O handler é o objeto da capability da face, devolvido como {@code Object} para o motor não
 * depender da API de outro mod; só a implementação sabe o tipo dele. As quantidades são {@code long};
 * a implementação corta no teto da API de fora ({@code int}) quando for o caso.
 *
 * <p>As implementações não guardam estado e não alocam: o laço chama estes métodos a cada visita.
 */
public interface ScalarAccess {
    /** O handler da máquina pela face {@code machineFace} do nó (pelo cache do roteador), ou {@code null}. */
    @Nullable Object handler(RouterBlockEntity node, Direction machineFace);

    /** O handler pode dar alguma coisa (sem olhar quanto tem). */
    boolean canExtract(Object handler);

    /** O handler pode receber alguma coisa (sem olhar quanto cabe). */
    boolean canReceive(Object handler);

    /** Tira até {@code amount}; devolve quanto saiu (ou sairia, simulando). */
    long extract(Object handler, long amount, boolean simulate);

    /** Põe até {@code amount}; devolve quanto entrou (ou entraria, simulando). */
    long insert(Object handler, long amount, boolean simulate);
}
