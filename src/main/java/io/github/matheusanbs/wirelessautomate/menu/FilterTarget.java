package io.github.matheusanbs.wirelessautomate.menu;

import io.github.matheusanbs.wirelessautomate.filter.Filter;
import io.github.matheusanbs.wirelessautomate.network.ResourceType;
import net.minecraft.world.entity.player.Player;

/** De onde vem e para onde vai o filtro editado no {@link FilterMenu}. Só no servidor. */
public interface FilterTarget {
    ResourceType type();

    Filter filter();

    void setFilter(Filter filter);

    boolean stillValid(Player player);

    /** Muda quando o filtro muda por fora (outro jogador, Configurador): o menu reenvia a visão. */
    int version();

    FilterView view(Player player);
}
