package io.github.matheusanbs.wirelessautomate.linker;

import io.github.matheusanbs.wirelessautomate.block.RouterBlockEntity;
import io.github.matheusanbs.wirelessautomate.dock.DockChoice;
import io.github.matheusanbs.wirelessautomate.dock.DockLinks;
import io.github.matheusanbs.wirelessautomate.item.LinkerItem;
import io.github.matheusanbs.wirelessautomate.range.RangeLinks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

/**
 * Os gestos do Vinculador no ar, do lado do servidor, e o texto das posições. O vínculo em si é do
 * {@link RangeLinks}; aqui ficam o que o clique no ar mostra e o Shift + clique no ar que solta o que está escolhido.
 */
public final class LinkerActions {
    private static final String KEY = "item.wirelessautomate.linker.";

    private LinkerActions() {
    }

    /** O que o Shift + clique no ar fez. */
    public enum Release {
        /** O roteador escolhido saiu. */
        ROUTER,
        /** A Tela de Sinal escolhida saiu. */
        TV,
        /** A Doca de Padrões escolhida saiu. */
        DOCK,
        /** Nada estava escolhido. */
        NOTHING
    }

    /**
     * Shift + clique no ar no modo Vincular: solta o roteador, a Tela de Sinal e a Doca escolhidos (só um dos três
     * existe de cada vez) e avisa na action bar; sem nada escolhido, diz que não havia nada. Devolve o que soltou.
     */
    public static Release release(ServerPlayer player, ItemStack stack) {
        GlobalPos router = LinkerItem.router(stack);
        GlobalPos tv = LinkerItem.tv(stack);
        DockChoice dock = LinkerItem.dock(stack);
        if (router == null && tv == null && dock == null) {
            player.displayClientMessage(Component.translatable(KEY + "released.nothing"), true);
            return Release.NOTHING;
        }
        LinkerItem.setRouter(stack, null);
        LinkerItem.setTv(stack, null);
        LinkerItem.setDock(stack, null);
        if (router == null && tv == null) {
            player.displayClientMessage(Component.translatable(KEY + "released.dock", position(dock.pos().pos())),
                    true);
            return Release.DOCK;
        }
        boolean wasRouter = router != null;
        player.displayClientMessage(Component.translatable(KEY + (wasRouter ? "released.router" : "released.tv"),
                position((wasRouter ? router : tv).pos())), true);
        return wasRouter ? Release.ROUTER : Release.TV;
    }

    /**
     * Clique no ar sem Shift no modo Vincular: mostra na action bar o roteador escolhido (nome e vagas, como na hora
     * de escolher), a Tela escolhida, a Doca e o lado escolhidos ou, sem nenhum, a dica do gesto. Não abre tela.
     */
    public static void showChosen(ServerPlayer player, ItemStack stack) {
        GlobalPos tv = LinkerItem.tv(stack);
        if (LinkerItem.router(stack) != null) {
            RouterBlockEntity router = RangeLinks.chosen(player, stack);
            player.displayClientMessage(router != null ? RangeLinks.chosenText(router)
                    : Component.translatable("item.wirelessautomate.linker.range.router_missing"), true);
        } else if (tv != null) {
            player.displayClientMessage(Component.translatable(KEY + "hint.tv", position(tv.pos())), true);
        } else if (LinkerItem.dock(stack) != null) {
            DockLinks.showChosen(player, stack);
        } else {
            player.displayClientMessage(Component.translatable(KEY + "hint.choose"), true);
        }
    }

    public static Component position(BlockPos pos) {
        return Component.literal(pos.getX() + ", " + pos.getY() + ", " + pos.getZ());
    }
}
