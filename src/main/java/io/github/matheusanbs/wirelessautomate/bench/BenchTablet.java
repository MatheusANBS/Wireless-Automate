package io.github.matheusanbs.wirelessautomate.bench;

import com.mojang.authlib.GameProfile;
import io.github.matheusanbs.wirelessautomate.block.RouterBlockEntity;
import io.github.matheusanbs.wirelessautomate.menu.TabletMenu;
import io.github.matheusanbs.wirelessautomate.menu.TabletSnapshot;
import io.github.matheusanbs.wirelessautomate.network.NodeIndex;
import io.github.matheusanbs.wirelessautomate.packet.TabletHeaderPayload;
import io.github.matheusanbs.wirelessautomate.packet.TabletLivePayload;
import io.github.matheusanbs.wirelessautomate.packet.TabletMachinesPayload;
import io.github.matheusanbs.wirelessautomate.packet.TabletPagePayload;
import io.github.matheusanbs.wirelessautomate.packet.TabletTreePayload;
import io.github.matheusanbs.wirelessautomate.range.RemoteMachines;
import io.netty.buffer.Unpooled;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import org.jetbrains.annotations.Nullable;

/**
 * O Tablet aberto por um jogador falso numa {@link BenchScene}: sincroniza o menu a cada tick, mede o custo e soma
 * os bytes e a contagem dos pacotes que sairiam, para o relatório do benchmark.
 */
final class BenchTablet {
    private final BenchScene scene;
    private @Nullable TabletMenu tablet;
    private long tabletBytes;
    /** Pacotes do Tablet aberto desde a abertura, por tipo (a primeira página vai na abertura, fora daqui). */
    private int tabletHeaders;
    private int tabletPages;
    private int tabletLives;

    BenchTablet(BenchScene scene) {
        this.scene = scene;
    }

    /** Solta o menu aberto, no fim da cena. */
    void close() {
        tablet = null;
    }

    /**
     * Abre o Tablet para um jogador falso dono da rede da cena ({@link BenchScenario#tablet()}): ele vê
     * todos os nós dela. O menu é criado direto (sem pacote de abertura) e sincronizado por
     * {@link #sync}, como o {@code ServerPlayer} faz a cada tick com a tela aberta.
     */
    void open() {
        FakePlayer player = FakePlayerFactory.get(scene.level, new GameProfile(BenchScene.OWNER, "[wa-bench]"));
        player.setPos(BenchScene.ORIGIN.getX() + 0.5, BenchScene.ORIGIN.getY() + 2, BenchScene.ORIGIN.getZ() + 0.5);
        TabletMenu menu = new TabletMenu(1, player.getInventory(), player);
        tablet = menu;
        if (scene.scenario == BenchScenario.TABLET_RANGE) {
            openRangeRows(menu);
        } else if (scene.scenario == BenchScenario.TABLET_MACHINES) {
            openMachines(menu);
        }
    }

    /**
     * No {@link BenchScenario#TABLET_MACHINES}, a Lista na visão por máquinas, pelo mesmo caminho do
     * {@code TabletMachinesPayload}: com a base inteira acima do teto da página, os blocos começam recolhidos, e o
     * jogador falso abre o dos barris vinculados.
     */
    private static void openMachines(TabletMenu menu) {
        menu.machines(TabletMachinesPayload.Action.MACHINES, Optional.empty());
        menu.machines(TabletMachinesPayload.Action.OPEN_BLOCK, Optional.of(BuiltInRegistries.BLOCK.getKey(Blocks.BARREL)));
    }

    /**
     * No {@link BenchScenario#TABLET_RANGE}, o que o jogador falso abre na Lista do Tablet logo depois de abri-lo: 4
     * roteadores e, no primeiro, o grupo dos barris com "Mostrar todas", pelo mesmo caminho do
     * {@code TabletTreePayload}.
     */
    private void openRangeRows(TabletMenu menu) {
        int opened = 0;
        for (BenchScene.Node node : scene.nodes) {
            RouterBlockEntity router = scene.router(node);
            if (router == null || opened >= RANGE_ROWS_OPENED) {
                continue;
            }
            NodeIndex.NodeKey key = NodeIndex.NodeKey.of(scene.level, router.getBlockPos());
            menu.tree(TabletTreePayload.Action.OPEN, key, 0);
            RemoteMachines remotes = router.remoteMachines();
            if (opened == 0 && remotes.groupCount() > 0) {
                menu.tree(TabletTreePayload.Action.SHOW_ALL, key, remotes.groupAt(0).id());
            }
            opened++;
        }
    }

    /** Roteadores abertos na árvore pelo jogador falso do {@link BenchScenario#TABLET_RANGE}. */
    private static final int RANGE_ROWS_OPENED = 4;

    /**
     * Sincroniza o Tablet aberto, se houver; devolve quanto levou, em ns. Os pacotes que sairiam são codificados
     * depois, fora do tempo medido, para somar os bytes ({@link #bytes}).
     */
    long sync() {
        TabletMenu menu = tablet;
        if (menu == null) {
            return 0;
        }
        // O que o broadcastChanges do menu faz no servidor, menos o envio: o jogador falso não tem
        // canal de rede (o hasChannel dá NullPointerException) e o menu não tem slots.
        long start = System.nanoTime();
        List<CustomPacketPayload> payloads = menu.pollPayloads();
        long nanos = System.nanoTime() - start;
        for (CustomPacketPayload payload : payloads) {
            tabletBytes += encodedSize(payload);
            if (payload instanceof TabletHeaderPayload) {
                tabletHeaders++;
            } else if (payload instanceof TabletPagePayload) {
                tabletPages++;
            } else if (payload instanceof TabletLivePayload) {
                tabletLives++;
            }
        }
        return nanos;
    }

    /**
     * O que o Tablet aberto mandou desde a abertura e a página que o jogador falso tem agora (para a linha da
     * repetição); vazio sem Tablet.
     */
    String payloadLine() {
        TabletMenu menu = tablet;
        if (menu == null) {
            return "";
        }
        TabletSnapshot page = menu.snapshot();
        return String.format(Locale.ROOT,
                "  Tablet: %d cabeçalho(s), %d página(s), %d pacote(s) de estado e vazão depois da abertura;"
                        + " página com %d nós, %d linhas da árvore e %d da visão por máquinas (%d casam, %d no índice"
                        + " visível)",
                tabletHeaders, tabletPages, tabletLives, page.nodes().size(), page.rows().rows().size(),
                page.machineRows().rows().size(), page.matchingNodes(), page.totalNodes());
    }

    /** Os bytes de um pacote do Tablet pelo codec dele (cabeçalho, página ou o estado e a vazão). */
    private int encodedSize(CustomPacketPayload payload) {
        RegistryFriendlyByteBuf buf = new RegistryFriendlyByteBuf(Unpooled.buffer(), scene.level.registryAccess());
        if (payload instanceof TabletHeaderPayload header) {
            TabletHeaderPayload.STREAM_CODEC.encode(buf, header);
        } else if (payload instanceof TabletPagePayload page) {
            TabletPagePayload.STREAM_CODEC.encode(buf, page);
        } else if (payload instanceof TabletLivePayload live) {
            TabletLivePayload.STREAM_CODEC.encode(buf, live);
        }
        int size = buf.writerIndex();
        buf.release();
        return size;
    }

    /** Bytes que o Tablet aberto mandaria, somados desde que a cena começou. */
    long bytes() {
        return tabletBytes;
    }
}
