package io.github.matheusanbs.wirelessautomate.gametest;

import io.github.matheusanbs.wirelessautomate.WirelessAutomate;
import io.github.matheusanbs.wirelessautomate.block.RouterBlock;
import io.github.matheusanbs.wirelessautomate.block.RouterBlockEntity;
import io.github.matheusanbs.wirelessautomate.linker.LinkerTabs;
import io.github.matheusanbs.wirelessautomate.network.Chemicals;
import io.github.matheusanbs.wirelessautomate.network.LoadedTypes;
import io.github.matheusanbs.wirelessautomate.network.NetworkSavedData;
import io.github.matheusanbs.wirelessautomate.network.ResourceType;
import io.github.matheusanbs.wirelessautomate.network.Sources;
import io.github.matheusanbs.wirelessautomate.network.WaNetwork;
import io.github.matheusanbs.wirelessautomate.registry.ModBlocks;
import io.github.matheusanbs.wirelessautomate.registry.ModItems;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/**
 * Redes, rede ativa e Vinculador. O {@link NetworkSavedData} é global do servidor e os testes
 * rodam em paralelo, então cada teste usa UUIDs e nomes próprios e só confere pertinência.
 */
@GameTestHolder(WirelessAutomate.MODID)
@PrefixGameTestTemplate(false)
public final class NetworkGameTests {
    private static final BlockPos MACHINE = new BlockPos(1, 1, 1);
    private static final BlockPos ROUTER = MACHINE.above();

    @GameTest(template = "empty")
    public static void createFindAndRemoveNetwork(GameTestHelper helper) {
        NetworkSavedData data = NetworkSavedData.get(helper.getLevel().getServer());
        UUID owner = UUID.randomUUID();
        String name = "Teste " + owner;

        WaNetwork network = data.create(owner, name);
        helper.assertTrue(network.equals(data.network(network.id())), "rede não encontrada pelo id");
        helper.assertTrue(data.networks().contains(network), "rede fora da lista");
        helper.assertTrue(network.equals(data.byName(owner, name.toUpperCase())), "busca por nome diferencia maiúsculas");
        helper.assertTrue(data.byName(UUID.randomUUID(), name) == null, "busca por nome ignorou o dono");

        data.setActiveNetwork(owner, network.id());
        helper.assertTrue(network.id().equals(data.activeNetwork(owner)), "rede ativa não gravada");

        helper.assertTrue(data.remove(network.id()), "remove devolveu false");
        helper.assertTrue(data.network(network.id()) == null, "rede continua existindo");
        helper.assertTrue(!data.networks().contains(network), "rede continua na lista");
        helper.assertTrue(data.activeNetwork(owner) == null, "rede ativa não foi limpa");
        helper.assertTrue(!data.remove(network.id()), "remover duas vezes devolveu true");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void activeOrCreateCreatesAndReuses(GameTestHelper helper) {
        NetworkSavedData data = NetworkSavedData.get(helper.getLevel().getServer());
        @SuppressWarnings("removal")
        ServerPlayer player = GameTestCompat.makeMockServerPlayerInLevel(helper);
        UUID playerId = player.getUUID();
        try {
            helper.assertTrue(data.activeNetwork(playerId) == null, "jogador novo já tinha rede ativa");

            WaNetwork first = data.activeOrCreate(player);
            helper.assertTrue(first.owner().equals(playerId), "dono errado");
            helper.assertTrue(first.name().equals(player.getGameProfile().getName()), "nome padrão errado");
            helper.assertTrue(first.id().equals(data.activeNetwork(playerId)), "rede criada não ficou ativa");
            helper.assertTrue(first.equals(data.activeOrCreate(player)), "não reutilizou a rede ativa");

            data.remove(first.id());
            WaNetwork second = data.activeOrCreate(player);
            helper.assertTrue(!second.id().equals(first.id()), "não criou outra rede após remover a ativa");
            data.remove(second.id());
        } finally {
            helper.getLevel().getServer().getPlayerList().remove(player);
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void savedDataRoundTripsThroughNbt(GameTestHelper helper) {
        NetworkSavedData original = new NetworkSavedData();
        UUID alice = UUID.randomUUID();
        UUID bob = UUID.randomUUID();
        WaNetwork ore = original.create(alice, "Minério");
        WaNetwork slurry = original.create(bob, "Slurries");
        WaNetwork power = original.create(alice, "Energia");
        original.setActiveNetwork(alice, power.id());
        original.setActiveNetwork(bob, slurry.id());

        CompoundTag tag = original.save(new CompoundTag());
        NetworkSavedData loaded = NetworkSavedData.load(tag);

        helper.assertTrue(List.copyOf(loaded.networks()).equals(List.of(ore, slurry, power)),
                "redes ou ordem de criação diferentes após carregar");
        helper.assertTrue(power.id().equals(loaded.activeNetwork(alice)), "rede ativa de alice perdida");
        helper.assertTrue(slurry.id().equals(loaded.activeNetwork(bob)), "rede ativa de bob perdida");
        helper.assertTrue(loaded.activeNetwork(UUID.randomUUID()) == null, "jogador desconhecido com rede ativa");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void linkerPutsRouterInActiveNetwork(GameTestHelper helper) {
        helper.setBlock(MACHINE, Blocks.CHEST);
        helper.setBlock(ROUTER, ModBlocks.ROUTER.get().defaultBlockState().setValue(RouterBlock.FACING, Direction.UP));
        RouterBlockEntity router = GameTestCompat.getBlockEntity(helper, ROUTER);
        NetworkSavedData data = NetworkSavedData.get(helper.getLevel().getServer());
        @SuppressWarnings("removal")
        ServerPlayer player = GameTestCompat.makeMockServerPlayerInLevel(helper);
        try {
            player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ModItems.LINKER.get()));
            BlockPos pos = helper.absolutePos(ROUTER);
            UseOnContext context = new UseOnContext(player, InteractionHand.MAIN_HAND,
                    new BlockHitResult(Vec3.atCenterOf(pos), Direction.UP, pos, false));

            helper.assertTrue(ModItems.LINKER.get().useOn(context).consumesAction(), "vinculador não agiu");
            UUID active = data.activeNetwork(player.getUUID());
            helper.assertTrue(active != null, "vinculador não criou a rede ativa");
            // Todos: as abas que existem (Químicos só com o Mekanism; sem ele, a aba fica como estava)
            for (ResourceType type : LoadedTypes.LIST) {
                helper.assertTrue(Objects.equals(router.networkId(type), active), type + " fora da rede ativa");
            }
            if (!Chemicals.LOADED) {
                helper.assertTrue(router.networkId(ResourceType.CHEMICAL) == null, "químicos entraram sem o Mekanism");
            }
            if (!Sources.LOADED) {
                helper.assertTrue(router.networkId(ResourceType.SOURCE) == null, "Source entrou sem o Ars Nouveau");
            }
            data.remove(active);
        } finally {
            helper.getLevel().getServer().getPlayerList().remove(player);
        }
        helper.succeed();
    }

    /**
     * Colocar um roteador pelo caminho do jogo (item na mão, clique no baú) não põe nenhuma aba em
     * rede, nem com rede ativa: o jogador configura o primeiro e replica com o Configurador.
     */
    @GameTest(template = "empty")
    public static void placedRouterJoinsNoNetwork(GameTestHelper helper) {
        helper.setBlock(MACHINE, Blocks.CHEST);
        NetworkSavedData data = NetworkSavedData.get(helper.getLevel().getServer());
        @SuppressWarnings("removal")
        ServerPlayer player = GameTestCompat.makeMockServerPlayerInLevel(helper);
        WaNetwork active = data.create(player.getUUID(), "Ativa " + player.getUUID());
        data.setActiveNetwork(player.getUUID(), active.id());
        try {
            player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ModItems.ROUTER.get()));
            BlockPos machine = helper.absolutePos(MACHINE);
            UseOnContext context = new UseOnContext(player, InteractionHand.MAIN_HAND,
                    new BlockHitResult(Vec3.atCenterOf(machine).add(0, 0.5, 0), Direction.UP, machine, false));
            helper.assertTrue(player.getMainHandItem().useOn(context).consumesAction(), "não colocou o roteador");
            helper.assertBlockPresent(ModBlocks.ROUTER.get(), ROUTER);
            RouterBlockEntity router = GameTestCompat.getBlockEntity(helper, ROUTER);
            for (ResourceType type : ResourceType.values()) {
                helper.assertTrue(router.networkId(type) == null, type + " entrou numa rede ao colocar");
            }
            helper.assertTrue(!router.hasNetwork(), "hasNetwork depois de colocar");
            GameTestCompat.assertValueEqual(helper, data.activeNetwork(player.getUUID()), active.id(), "rede ativa mudou");
        } finally {
            data.remove(active.id());
            helper.getLevel().getServer().getPlayerList().remove(player);
        }
        helper.succeed();
    }
}
