package io.github.matheusanbs.wirelessautomate.menu;

import io.github.matheusanbs.wirelessautomate.block.RouterBlockEntity;
import io.github.matheusanbs.wirelessautomate.item.LinkerItem;
import io.github.matheusanbs.wirelessautomate.linker.LinkerActions;
import io.github.matheusanbs.wirelessautomate.linker.LinkerArea;
import io.github.matheusanbs.wirelessautomate.linker.LinkerBox;
import io.github.matheusanbs.wirelessautomate.linker.LinkerMode;
import io.github.matheusanbs.wirelessautomate.linker.LinkerProblem;
import io.github.matheusanbs.wirelessautomate.linker.LinkerScan;
import io.github.matheusanbs.wirelessautomate.linker.LinkerTabs;
import io.github.matheusanbs.wirelessautomate.menu.RouterSnapshot.NetworkEntry;
import io.github.matheusanbs.wirelessautomate.network.LoadedTypes;
import io.github.matheusanbs.wirelessautomate.network.NetworkSavedData;
import io.github.matheusanbs.wirelessautomate.network.ResourceType;
import io.github.matheusanbs.wirelessautomate.network.WaNetwork;
import io.netty.handler.codec.DecoderException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

/**
 * Tudo que a tela do Vinculador mostra, montado no servidor e enviado só com a tela aberta.
 *
 * @param networks   redes do jogador (e a ativa, se for de outro dono, com {@code owned = false})
 * @param active     rede ativa; vazia se ele não tem (o primeiro vínculo cria uma)
 * @param unlink     "Nenhuma (desvincular)" escolhida: os gestos tiram as abas da rede
 * @param tabs       abas marcadas no Vinculador
 * @param available  os tipos que existem no servidor; a tela mostra um chip para cada
 * @param first      canto 1, se marcado na dimensão do jogador
 * @param second     canto 2, idem
 * @param otherDimension a área foi marcada noutra dimensão (os cantos não vêm)
 * @param inside     roteadores carregados dentro da área
 * @param already    desses, quantos já estão na rede ativa nas abas marcadas (ao desvincular, já sem rede)
 * @param unloadedChunks chunks da área descarregados (os roteadores deles não entram na conta)
 * @param routers    pontos da prévia de cima, no máximo {@link #MAX_DOTS}
 * @param problem    por que não dá para vincular agora ({@link LinkerProblem#NONE} se dá)
 * @param maxVolume  volume máximo da config, para a mensagem de área grande
 * @param maxDistance distância máxima da config (0 = sem limite)
 * @param outcome    resultado do último Vincular desta tela, até a próxima ação
 */
public record LinkerSnapshot(
        List<NetworkEntry> networks,
        Optional<UUID> active,
        boolean unlink,
        LinkerTabs tabs,
        LinkerTabs available,
        LinkerMode mode,
        Optional<BlockPos> first,
        Optional<BlockPos> second,
        boolean otherDimension,
        int inside,
        int already,
        int unloadedChunks,
        List<RouterDot> routers,
        LinkerProblem problem,
        long maxVolume,
        int maxDistance,
        Optional<Outcome> outcome) {

    /** Pontos na prévia: acima disso só a contagem vale. */
    public static final int MAX_DOTS = 512;

    /**
     * Um roteador na prévia de cima.
     *
     * @param color  cor da rede dele na primeira aba marcada, ou −1 sem rede
     * @param linked já está na rede ativa nas abas marcadas (ao desvincular, já sem rede nelas)
     */
    public record RouterDot(int x, int z, int color, boolean linked) {
    }

    /**
     * Resultado do Vincular (ou Desvincular): roteadores que mudaram, os que já estavam, os protegidos
     * e os chunks descarregados. Ao desvincular, {@code network} vem vazio.
     */
    public record Outcome(int linked, int already, int protectedCount, int unloadedChunks, LinkerTabs tabs,
            boolean unlink, String network, int color) {
    }

    /** As abas que valem (só as dos tipos disponíveis), na ordem das abas. */
    public List<ResourceType> effectiveTabs() {
        return tabs.effective(available.types());
    }

    public @Nullable LinkerBox box() {
        if (first.isEmpty() || second.isEmpty()) {
            return null;
        }
        BlockPos a = first.get();
        BlockPos b = second.get();
        return LinkerBox.of(a.getX(), a.getY(), a.getZ(), b.getX(), b.getY(), b.getZ());
    }

    public @Nullable NetworkEntry activeEntry() {
        if (active.isEmpty()) {
            return null;
        }
        for (NetworkEntry entry : networks) {
            if (entry.id().equals(active.get())) {
                return entry;
            }
        }
        return null;
    }

    /** Monta o estado da tela para o jogador e o Vinculador na mão dele. Só no servidor. */
    public static LinkerSnapshot capture(ServerPlayer player, ItemStack stack, @Nullable Outcome outcome) {
        NetworkSavedData data = NetworkSavedData.get(player.server);
        List<NetworkEntry> networks = new ArrayList<>();
        for (WaNetwork network : data.networksOf(player.getUUID())) {
            networks.add(new NetworkEntry(network.id(), network.name(), network.color(), true));
        }
        WaNetwork active = LinkerActions.activeNetwork(player);
        if (active != null && !active.owner().equals(player.getUUID())) {
            networks.add(new NetworkEntry(active.id(), active.name(), active.color(), false));
        }
        boolean unlink = LinkerItem.unlink(stack);
        LinkerTabs tabs = LinkerItem.tabs(stack);
        List<ResourceType> types = LinkerItem.effectiveTabs(stack);
        LinkerArea area = LinkerItem.area(stack);
        boolean otherDimension = area != null && !area.dimension().equals(player.level().dimension());
        Optional<BlockPos> first = area == null || otherDimension ? Optional.empty() : Optional.of(area.first());
        Optional<BlockPos> second = area == null || otherDimension ? Optional.empty() : area.second();
        LinkerProblem problem = LinkerActions.check(player, stack);

        int inside = 0;
        int already = 0;
        int unloaded = 0;
        List<RouterDot> dots = new ArrayList<>();
        LinkerBox box = area == null || otherDimension ? null : area.box();
        // área grande demais nem é varrida: a tela só mostra o aviso
        if (box != null && problem != LinkerProblem.TOO_BIG) {
            LinkerScan scan = LinkerScan.of(player.serverLevel(), box);
            unloaded = scan.unloadedChunks();
            ResourceType colorType = types.isEmpty() ? ResourceType.ITEM : types.get(0);
            for (RouterBlockEntity router : scan.routers()) {
                inside++;
                boolean linked = unlink ? LinkerActions.inTarget(router, types, null)
                        : active != null && LinkerActions.inTarget(router, types, active.id());
                if (linked) {
                    already++;
                }
                if (dots.size() < MAX_DOTS) {
                    UUID id = router.networkId(colorType);
                    WaNetwork network = id == null ? null : data.network(id);
                    BlockPos pos = router.getBlockPos();
                    dots.add(new RouterDot(pos.getX(), pos.getZ(), network == null ? -1 : network.color(), linked));
                }
            }
        }
        return new LinkerSnapshot(List.copyOf(networks), Optional.ofNullable(active).map(WaNetwork::id),
                unlink, tabs, LinkerTabs.available(LoadedTypes.LIST), LinkerItem.mode(stack), first, second, otherDimension, inside, already,
                unloaded, List.copyOf(dots), problem, LinkerActions.maxVolume(), LinkerActions.maxDistance(),
                Optional.ofNullable(outcome));
    }

    public static final StreamCodec<RegistryFriendlyByteBuf, LinkerSnapshot> STREAM_CODEC =
            StreamCodec.of(LinkerSnapshot::encode, LinkerSnapshot::decode);

    private static void encode(RegistryFriendlyByteBuf buf, LinkerSnapshot s) {
        buf.writeVarInt(s.networks.size());
        for (NetworkEntry entry : s.networks) {
            buf.writeUUID(entry.id());
            buf.writeUtf(entry.name(), 64);
            buf.writeInt(entry.color());
            buf.writeBoolean(entry.owned());
        }
        buf.writeOptional(s.active, (b, id) -> b.writeUUID(id));
        buf.writeBoolean(s.unlink);
        buf.writeVarInt(s.tabs.mask());
        buf.writeVarInt(s.available.mask());
        buf.writeEnum(s.mode);
        buf.writeOptional(s.first, (b, p) -> b.writeBlockPos(p));
        buf.writeOptional(s.second, (b, p) -> b.writeBlockPos(p));
        buf.writeBoolean(s.otherDimension);
        buf.writeVarInt(s.inside);
        buf.writeVarInt(s.already);
        buf.writeVarInt(s.unloadedChunks);
        buf.writeVarInt(s.routers.size());
        for (RouterDot dot : s.routers) {
            buf.writeVarInt(dot.x());
            buf.writeVarInt(dot.z());
            buf.writeInt(dot.color());
            buf.writeBoolean(dot.linked());
        }
        buf.writeEnum(s.problem);
        buf.writeVarLong(s.maxVolume);
        buf.writeVarInt(s.maxDistance);
        buf.writeOptional(s.outcome, (b, o) -> {
            b.writeVarInt(o.linked());
            b.writeVarInt(o.already());
            b.writeVarInt(o.protectedCount());
            b.writeVarInt(o.unloadedChunks());
            b.writeVarInt(o.tabs().mask());
            b.writeBoolean(o.unlink());
            b.writeUtf(o.network(), 64);
            b.writeInt(o.color());
        });
    }

    private static LinkerSnapshot decode(RegistryFriendlyByteBuf buf) {
        int networkCount = buf.readVarInt();
        List<NetworkEntry> networks = new ArrayList<>(networkCount);
        for (int i = 0; i < networkCount; i++) {
            networks.add(new NetworkEntry(buf.readUUID(), buf.readUtf(64), buf.readInt(), buf.readBoolean()));
        }
        Optional<UUID> active = buf.readOptional(b -> b.readUUID());
        boolean unlink = buf.readBoolean();
        LinkerTabs tabs = new LinkerTabs(buf.readVarInt());
        LinkerTabs available = new LinkerTabs(buf.readVarInt());
        LinkerMode mode = buf.readEnum(LinkerMode.class);
        Optional<BlockPos> first = buf.readOptional(b -> b.readBlockPos());
        Optional<BlockPos> second = buf.readOptional(b -> b.readBlockPos());
        boolean otherDimension = buf.readBoolean();
        int inside = buf.readVarInt();
        int already = buf.readVarInt();
        int unloaded = buf.readVarInt();
        int dotCount = buf.readVarInt();
        if (dotCount < 0 || dotCount > MAX_DOTS) {
            throw new DecoderException("pontos demais na prévia do Vinculador: " + dotCount);
        }
        List<RouterDot> dots = new ArrayList<>(dotCount);
        for (int i = 0; i < dotCount; i++) {
            dots.add(new RouterDot(buf.readVarInt(), buf.readVarInt(), buf.readInt(), buf.readBoolean()));
        }
        LinkerProblem problem = buf.readEnum(LinkerProblem.class);
        long maxVolume = buf.readVarLong();
        int maxDistance = buf.readVarInt();
        Optional<Outcome> outcome = buf.readOptional(b -> new Outcome(b.readVarInt(), b.readVarInt(), b.readVarInt(),
                b.readVarInt(), new LinkerTabs(b.readVarInt()), b.readBoolean(), b.readUtf(64), b.readInt()));
        return new LinkerSnapshot(List.copyOf(networks), active, unlink, tabs, available, mode, first, second, otherDimension, inside,
                already, unloaded, List.copyOf(dots), problem, maxVolume, maxDistance, outcome);
    }
}
