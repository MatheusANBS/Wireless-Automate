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
 * <p>O filtro aberto daqui vale na mesma regra ({@link FilterOrigin#REMOTE_ROUTER}) e o Voltar dele reabre
 * esta tela.
 */
public class RemoteRouterMenu extends RouterMenu {
    public RemoteRouterMenu(int containerId, Inventory inventory, RouterBlockEntity router, RouterSnapshot snapshot) {
        super(containerId, inventory, router, snapshot);
    }

    /** Editando {@code target} (o {@code snapshot} já foi capturado com ele). */
    public RemoteRouterMenu(int containerId, Inventory inventory, RouterBlockEntity router, RouterSnapshot snapshot,
            EditTargetRef target) {
        super(containerId, inventory, router, snapshot, target);
    }

    @Override
    public FilterOrigin filterOrigin() {
        return FilterOrigin.REMOTE_ROUTER;
    }

    /** Abre a tela do roteador à distância. Quem chama já conferiu alcance e permissão. */
    public static void open(ServerPlayer player, RouterBlockEntity router) {
        open(player, router, EditTargetRef.ATTACHED);
    }

    /** Abre a tela já editando {@code target} (a volta do filtro); se o alvo não existe mais, na presa. */
    public static void open(ServerPlayer player, RouterBlockEntity router, EditTargetRef target) {
        EditTargetRef fresh = target.refresh(router);
        RouterSnapshot snapshot = RouterSnapshot.capture(router, player, fresh.target());
        Component title = router.name().isEmpty()
                ? router.getBlockState().getBlock().getName()
                : Component.literal(router.name());
        player.openMenu(new SimpleMenuProvider(
                        (containerId, inventory, p) -> new RemoteRouterMenu(containerId, inventory, router, snapshot, fresh), title),
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
