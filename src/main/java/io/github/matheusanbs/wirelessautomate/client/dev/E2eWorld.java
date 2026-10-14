package io.github.matheusanbs.wirelessautomate.client.dev;

import static io.github.matheusanbs.wirelessautomate.client.dev.DevEndToEnd.*;

import io.github.matheusanbs.wirelessautomate.block.RouterBlock;
import io.github.matheusanbs.wirelessautomate.block.RouterBlockEntity;
import io.github.matheusanbs.wirelessautomate.network.NetworkSavedData;
import io.github.matheusanbs.wirelessautomate.network.ResourceType;
import io.github.matheusanbs.wirelessautomate.network.WaNetwork;
import io.github.matheusanbs.wirelessautomate.registry.ModBlocks;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ChestBlock;
import org.jetbrains.annotations.Nullable;

/**
 * O mundo do roteiro do e2e: as redes, os baús A e B com um roteador em cima de cada, montados no servidor, e o
 * acesso ao servidor integrado ({@code onServer}, o roteador e a contagem de um baú).
 */
final class E2eWorld {
    private E2eWorld() {
    }

    static final String MAIN_NETWORK = "E2E Principal";
    static final String OTHER_NETWORK = "E2E Outra";
    static final String ENERGY_NETWORK = "E2E Energia";
    static final String NODE_NAME = "Baú B E2E";

    // Estado do mundo de teste, montado no servidor.
    static BlockPos chestA;
    static BlockPos chestB;
    static BlockPos routerA;
    static BlockPos routerB;
    static UUID mainNetwork;
    static UUID otherNetwork;
    static UUID energyNetwork;

    /** Rede de cada aba no servidor, para o log. */
    static String serverNetworks(BlockPos pos) throws Exception {
        return onServer(server -> serverNetworksText(server, pos));
    }

    /** Na thread do servidor. */
    static String serverNetworksText(MinecraftServer server, BlockPos pos) {
        RouterBlockEntity router = router(server, pos);
        return "itens " + networkName(router.networkId(ResourceType.ITEM)) + ", fluidos "
                + networkName(router.networkId(ResourceType.FLUID)) + ", energia "
                + networkName(router.networkId(ResourceType.ENERGY));
    }

    static String networkNames(List<UUID> ids) {
        if (ids.isEmpty()) {
            return "nenhuma";
        }
        List<String> names = new ArrayList<>();
        for (UUID id : ids) {
            names.add(networkName(id));
        }
        return String.join("+", names);
    }

    private static String networkName(@Nullable UUID id) {
        if (id == null) {
            return "nenhuma";
        }
        return id.equals(mainNetwork) ? MAIN_NETWORK : id.equals(otherNetwork) ? OTHER_NETWORK
                : id.equals(energyNetwork) ? ENERGY_NETWORK : id.toString();
    }

    /** Servidor: redes, baús e roteadores na frente do jogador e 64 pedras no baú A. */
    static void setUpWorld() throws Exception {
        UUID playerId = Minecraft.getInstance().player.getUUID();
        onServer(server -> {
            ServerPlayer player = server.getPlayerList().getPlayer(playerId);
            ServerLevel level = player.serverLevel();
            BlockPos base = player.blockPosition();
            level.setDayTime(6000);
            player.teleportTo(level, base.getX() + 0.5, base.getY(), base.getZ() + 0.5, 180f, 30f);
            NetworkSavedData data = NetworkSavedData.get(server);
            WaNetwork main = data.create(player.getUUID(), MAIN_NETWORK);
            WaNetwork other = data.create(player.getUUID(), OTHER_NETWORK);
            WaNetwork energy = data.create(player.getUUID(), ENERGY_NETWORK);
            data.setActiveNetwork(player.getUUID(), main.id());
            mainNetwork = main.id();
            otherNetwork = other.id();
            energyNetwork = energy.id();
            chestA = base.offset(-1, 0, -3);
            chestB = base.offset(1, 0, -3);
            routerA = chestA.above();
            routerB = chestB.above();
            for (BlockPos chest : List.of(chestA, chestB)) {
                level.setBlockAndUpdate(chest, Blocks.CHEST.defaultBlockState().setValue(ChestBlock.FACING, Direction.SOUTH));
                level.setBlockAndUpdate(chest.above(),
                        ModBlocks.ROUTER.get().defaultBlockState().setValue(RouterBlock.FACING, Direction.UP));
                router(server, chest.above()).setNetworkId(main.id());
            }
            ((Container) level.getBlockEntity(chestA)).setItem(0, new ItemStack(Items.STONE, 64));
            return null;
        });
    }

    /** Roda na thread do servidor integrado e espera o resultado (o servidor nunca espera o cliente). */
    static <T> T onServer(Function<MinecraftServer, T> task) throws Exception {
        MinecraftServer server = Minecraft.getInstance().getSingleplayerServer();
        if (server == null) {
            throw new StepFailure("sem servidor integrado");
        }
        return server.submit(() -> task.apply(server)).get(5, TimeUnit.SECONDS);
    }

    static RouterBlockEntity router(MinecraftServer server, BlockPos pos) {
        if (server.overworld().getBlockEntity(pos) instanceof RouterBlockEntity router) {
            return router;
        }
        throw new IllegalStateException("sem roteador em " + pos.toShortString());
    }

    static int count(MinecraftServer server, BlockPos pos, Item item) {
        if (!(server.overworld().getBlockEntity(pos) instanceof Container container)) {
            throw new IllegalStateException("sem baú em " + pos.toShortString());
        }
        int total = 0;
        for (int i = 0; i < container.getContainerSize(); i++) {
            ItemStack stack = container.getItem(i);
            if (stack.is(item)) {
                total += stack.getCount();
            }
        }
        return total;
    }
}
