package io.github.matheusanbs.wirelessautomate.menu;

import io.github.matheusanbs.wirelessautomate.block.RouterBlockEntity;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;

/**
 * A tela do roteador aberta à distância pelo Tablet. É um {@link RouterMenu} comum (o cliente nem
 * sabe a diferença) que só troca a regra de continuar aberta: em vez de 8 blocos, vale enquanto o
 * jogador tiver o Tablet e o roteador estiver carregado e ao alcance ({@link TabletMenu#inReach}).
 * Os pacotes da tela usam {@code stillValid}, então a mesma regra vale para eles.
 *
 * <p>Limite conhecido: o botão Editar do filtro abre a tela de filtro comum, que exige estar a até
 * 8 blocos do roteador; de longe ela fecha logo.
 */
public class RemoteRouterMenu extends RouterMenu {
    public RemoteRouterMenu(int containerId, Inventory inventory, RouterBlockEntity router, RouterSnapshot snapshot) {
        super(containerId, inventory, router, snapshot);
    }

    /** Abre a tela do roteador à distância. Quem chama já conferiu alcance e permissão. */
    public static void open(ServerPlayer player, RouterBlockEntity router) {
        RouterSnapshot snapshot = RouterSnapshot.capture(router, player);
        Component title = router.name().isEmpty()
                ? router.getBlockState().getBlock().getName()
                : Component.literal(router.name());
        player.openMenu(new SimpleMenuProvider(
                        (containerId, inventory, p) -> new RemoteRouterMenu(containerId, inventory, router, snapshot), title),
                buf -> RouterSnapshot.STREAM_CODEC.encode(buf, snapshot));
    }

    @Override
    public boolean stillValid(Player player) {
        RouterBlockEntity router = router();
        if (router == null) {
            return true;
        }
        if (router.isRemoved() || router.getLevel() == null
                || router.getLevel().getBlockEntity(router.getBlockPos()) != router) {
            return false;
        }
        return player instanceof ServerPlayer serverPlayer && TabletMenu.hasTablet(serverPlayer)
                && TabletMenu.inReach(serverPlayer, router);
    }
}
