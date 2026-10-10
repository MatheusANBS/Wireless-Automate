package io.github.matheusanbs.wirelessautomate.menu;

import io.github.matheusanbs.wirelessautomate.filter.Filter;
import io.github.matheusanbs.wirelessautomate.item.FilterCardItem;
import io.github.matheusanbs.wirelessautomate.net.ServerMenus;
import io.github.matheusanbs.wirelessautomate.network.ResourceType;
import io.github.matheusanbs.wirelessautomate.registry.ModDataComponents;
import java.util.Optional;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

/**
 * Filtro de um Cartão de Filtro na mão do jogador. Vale enquanto aquela mesma pilha estiver
 * naquela mão; o tipo é fixado na abertura (trocar o tipo exige o cartão vazio, fora da tela).
 */
public final class CardFilterTarget implements FilterTarget {
    private final Player player;
    private final InteractionHand hand;
    private final ItemStack stack;
    private final ResourceType type;
    // Versão: o componente é imutável, então basta comparar a referência com a última vista.
    private @Nullable Object seen;
    private int version;

    public CardFilterTarget(Player player, InteractionHand hand) {
        this.player = player;
        this.hand = hand;
        this.stack = player.getItemInHand(hand);
        this.type = FilterCardItem.contents(stack).type();
        this.seen = ModDataComponents.CARD_FILTER.get(stack);
    }

    /** Abre a tela de filtro do cartão na mão {@code hand}, se houver um. */
    public static void open(ServerPlayer player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!FilterCardItem.isCard(stack)) {
            return;
        }
        CardFilterTarget target = new CardFilterTarget(player, hand);
        FilterView view = target.view(player);
        ServerMenus.openMenu(player, new SimpleMenuProvider(
                        (containerId, inventory, p) -> new FilterMenu(containerId, inventory, target, view),
                        stack.getHoverName()),
                buf -> FilterView.STREAM_CODEC.encode(buf, view));
    }

    public InteractionHand hand() {
        return hand;
    }

    public ItemStack stack() {
        return stack;
    }

    @Override
    public ResourceType type() {
        return type;
    }

    @Override
    public Filter filter() {
        FilterCardItem.Contents contents = FilterCardItem.contents(stack);
        return contents.type() == type ? contents.filter() : Filter.EMPTY;
    }

    @Override
    public void setFilter(Filter filter) {
        if (stillValid(player)) {
            FilterCardItem.setContents(stack, new FilterCardItem.Contents(type, filter));
        }
    }

    @Override
    public boolean stillValid(Player player) {
        return player == this.player && !stack.isEmpty() && player.getItemInHand(hand) == stack
                && FilterCardItem.isCard(stack);
    }

    @Override
    public int version() {
        Object current = ModDataComponents.CARD_FILTER.get(stack);
        if (current != seen) {
            seen = current;
            version++;
        }
        return version;
    }

    @Override
    public FilterView view(Player player) {
        return new FilterView(type, Optional.empty(), Optional.empty(), filter(),
                FilterCardItem.isCard(player.getMainHandItem()));
    }
}
