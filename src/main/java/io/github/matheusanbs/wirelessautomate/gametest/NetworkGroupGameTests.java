package io.github.matheusanbs.wirelessautomate.gametest;

import io.github.matheusanbs.wirelessautomate.WirelessAutomate;
import io.github.matheusanbs.wirelessautomate.menu.TabletMenu;
import io.github.matheusanbs.wirelessautomate.menu.TabletMenu.Action;
import io.github.matheusanbs.wirelessautomate.network.NetworkSavedData;
import io.github.matheusanbs.wirelessautomate.network.WaGroup;
import io.github.matheusanbs.wirelessautomate.network.WaNetwork;
import io.github.matheusanbs.wirelessautomate.packet.TabletActionPayload;
import io.github.matheusanbs.wirelessautomate.packet.TabletPayloads;
import io.github.matheusanbs.wirelessautomate.registry.ModItems;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import org.jetbrains.annotations.Nullable;

/**
 * Os grupos de redes do Tablet novo: o ícone (NBT de ida e volta, grupo antigo sem a chave, a ação {@code GROUP_ICON}),
 * "uma rede, um grupo" ({@code GROUP_ADD_NETWORK} move) e a cor de uma rede nova (a próxima da paleta). Cada teste
 * cria as próprias redes e grupos e confere pertinência.
 */
@GameTestHolder(WirelessAutomate.MODID)
@PrefixGameTestTemplate(false)
public final class NetworkGroupGameTests {
    private static final ResourceLocation DIAMOND = ResourceLocation.withDefaultNamespace("diamond");

    private static void eq(GameTestHelper helper, @Nullable Object actual, @Nullable Object expected, String what) {
        helper.assertTrue(Objects.equals(actual, expected), what + ": esperado " + expected + ", veio " + actual);
    }

    private static NetworkSavedData data(GameTestHelper helper) {
        return NetworkSavedData.get(helper.getLevel().getServer());
    }

    private static ServerPlayer tabletPlayer(GameTestHelper helper, int containerId) {
        ServerPlayer player = TestPlayers.inLevel(helper);
        player.getInventory().setItem(0, new ItemStack(ModItems.NETWORK_TABLET.get()));
        player.containerMenu = new TabletMenu(containerId, player.getInventory(), player);
        return player;
    }

    private static boolean act(ServerPlayer player, int containerId, Action action, @Nullable UUID target,
            @Nullable UUID other, String text) {
        return TabletPayloads.handleAction(player, new TabletActionPayload(containerId, action,
                Optional.ofNullable(target), Optional.ofNullable(other), text, 0));
    }

    @GameTest(template = "empty")
    public static void groupIconRoundTripsAndOldGroupsLoadWithoutIt(GameTestHelper helper) {
        WaGroup group = new WaGroup(UUID.randomUUID(), "teste-icone", UUID.randomUUID(), List.of(UUID.randomUUID()),
                true, DIAMOND);
        CompoundTag tag = group.save();
        eq(helper, tag.getString("icon"), "minecraft:diamond", "chave icon");
        eq(helper, WaGroup.load(tag), group, "ida e volta");

        CompoundTag old = group.withIcon(null).save();
        helper.assertFalse(old.contains("icon"), "grupo sem ícone gravou a chave");
        WaGroup loaded = WaGroup.load(old);
        helper.assertTrue(loaded.icon() == null && loaded.paused() && loaded.networks().size() == 1,
                "grupo antigo: " + loaded);
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void groupIconActionChecksTheItem(GameTestHelper helper) {
        ServerPlayer player = tabletPlayer(helper, 81);
        try {
            NetworkSavedData data = data(helper);
            WaGroup group = data.createGroup(player.getUUID(), "teste-acao-icone");
            helper.assertTrue(act(player, 81, Action.GROUP_ICON, group.id(), null, "minecraft:diamond"), "pôr o ícone");
            eq(helper, data.group(group.id()).icon(), DIAMOND, "ícone");
            helper.assertFalse(act(player, 81, Action.GROUP_ICON, group.id(), null, "minecraft:nao_existe"),
                    "item que não existe");
            helper.assertFalse(act(player, 81, Action.GROUP_ICON, group.id(), null, "minecraft:air"), "ar");
            helper.assertFalse(act(player, 81, Action.GROUP_ICON, group.id(), null, "Não é id"), "texto torto");
            eq(helper, data.group(group.id()).icon(), DIAMOND, "ícone depois das recusas");
            helper.assertTrue(act(player, 81, Action.GROUP_ICON, group.id(), null, ""), "tirar o ícone");
            helper.assertTrue(data.group(group.id()).icon() == null, "ícone ficou");

            WaGroup foreign = data.createGroup(UUID.randomUUID(), "teste-acao-icone-alheio");
            helper.assertFalse(act(player, 81, Action.GROUP_ICON, foreign.id(), null, "minecraft:diamond"),
                    "mudou o ícone de grupo alheio");
            data.removeGroup(foreign.id());
            data.removeGroup(group.id());
            helper.succeed();
        } finally {
            player.containerMenu = player.inventoryMenu;
        }
    }

    @GameTest(template = "empty")
    public static void addingANetworkToAGroupMovesIt(GameTestHelper helper) {
        ServerPlayer player = tabletPlayer(helper, 82);
        try {
            NetworkSavedData data = data(helper);
            UUID me = player.getUUID();
            WaNetwork network = data.create(me, "teste-mover-rede");
            WaGroup first = data.createGroup(me, "teste-mover-1");
            WaGroup second = data.createGroup(me, "teste-mover-2");
            data.updateGroup(data.group(second.id()).withPaused(true));

            helper.assertTrue(act(player, 82, Action.GROUP_ADD_NETWORK, first.id(), network.id(), ""), "pôr no 1");
            helper.assertTrue(data.group(first.id()).networks().contains(network.id()), "não entrou no 1");
            helper.assertFalse(data.isPaused(network.id()), "pausada no grupo ativo");

            helper.assertTrue(act(player, 82, Action.GROUP_ADD_NETWORK, second.id(), network.id(), ""), "mover p/ 2");
            helper.assertFalse(data.group(first.id()).networks().contains(network.id()), "ficou no 1");
            helper.assertTrue(data.group(second.id()).networks().contains(network.id()), "não entrou no 2");
            helper.assertTrue(data.isPaused(network.id()), "o grupo 2 está pausado");

            helper.assertTrue(act(player, 82, Action.GROUP_REMOVE_NETWORK, second.id(), network.id(), ""), "sem grupo");
            helper.assertFalse(data.isPaused(network.id()), "sem grupo e pausada");

            // mundo antigo: rede em dois grupos fica como está até alguém mexer
            data.updateGroup(data.group(first.id()).withNetwork(network.id()));
            data.updateGroup(data.group(second.id()).withNetwork(network.id()));
            NetworkSavedData reloaded = NetworkSavedData.load(data.save(new CompoundTag(),
                    helper.getLevel().registryAccess()), helper.getLevel().registryAccess());
            helper.assertTrue(reloaded.group(first.id()).networks().contains(network.id())
                    && reloaded.group(second.id()).networks().contains(network.id()), "o load normalizou");

            data.remove(network.id());
            data.removeGroup(first.id());
            data.removeGroup(second.id());
            helper.succeed();
        } finally {
            player.containerMenu = player.inventoryMenu;
        }
    }

    @GameTest(template = "empty")
    public static void newNetworksTakeThePaletteInOrder(GameTestHelper helper) {
        NetworkSavedData data = data(helper);
        UUID owner = UUID.randomUUID();
        int expected = data.nextColor(owner);
        WaNetwork a = data.create(owner, "teste-cor-1");
        eq(helper, a.color(), expected, "primeira cor");
        int second = data.nextColor(owner);
        helper.assertTrue(second != expected, "a próxima repetiu a cor");
        WaNetwork b = data.create(owner, "teste-cor-2");
        eq(helper, b.color(), second, "segunda cor");
        data.remove(a.id());
        data.remove(b.id());
        helper.succeed();
    }
}
