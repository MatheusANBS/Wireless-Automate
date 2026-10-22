package io.github.matheusanbs.wirelessautomate.menu;

import io.github.matheusanbs.wirelessautomate.block.RouterBlockEntity;
import io.github.matheusanbs.wirelessautomate.item.LinkerItem;
import io.github.matheusanbs.wirelessautomate.linker.LinkerActions;
import io.github.matheusanbs.wirelessautomate.linker.LinkerArea;
import io.github.matheusanbs.wirelessautomate.linker.LinkerBox;
import io.github.matheusanbs.wirelessautomate.linker.LinkerProblem;
import io.github.matheusanbs.wirelessautomate.range.RangeLinks;
import io.github.matheusanbs.wirelessautomate.range.RangeRules.LinkRefusal;
import io.github.matheusanbs.wirelessautomate.range.RemoteMachine;
import io.netty.handler.codec.DecoderException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.server.level.ServerChunkCache;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

/**
 * Tudo que a tela do Vinculador mostra, montado no servidor e enviado só com a tela aberta.
 *
 * @param unlink      os gestos tiram as máquinas do roteador em vez de pôr
 * @param router      o roteador escolhido
 * @param first       canto 1, se marcado na dimensão do jogador
 * @param second      canto 2, idem
 * @param otherDimension a área foi marcada noutra dimensão (os cantos não vêm)
 * @param problem     por que não dá para vincular a área agora ({@link LinkerProblem#NONE} se dá)
 * @param unloadedChunks chunks da área descarregados (as máquinas deles ficam de fora)
 * @param linkedInside máquinas do roteador escolhido que estão dentro da área (o que o Desvincular tira)
 * @param machines    pontos da prévia de cima: essas máquinas, no máximo {@link #MAX_DOTS}
 * @param maxVolume   volume máximo da config, para a mensagem de área grande
 * @param maxDistance distância máxima da config (0 = sem limite)
 * @param outcome     resultado do último Vincular desta tela, até a próxima ação
 */
public record LinkerSnapshot(
        boolean unlink,
        RouterInfo router,
        Optional<BlockPos> first,
        Optional<BlockPos> second,
        boolean otherDimension,
        LinkerProblem problem,
        int unloadedChunks,
        int linkedInside,
        List<Dot> machines,
        long maxVolume,
        int maxDistance,
        Optional<Outcome> outcome) {

    /** Pontos na prévia: acima disso só a contagem vale. */
    public static final int MAX_DOTS = 512;

    /** O estado do roteador escolhido. */
    public enum Status {
        /** Nenhum roteador escolhido. */
        NONE,
        /** Escolhido, mas descarregado, quebrado ou noutra dimensão. */
        MISSING,
        READY
    }

    /**
     * O roteador escolhido.
     *
     * @param name    o nome (ou o do bloco); vazio sem roteador carregado
     * @param used    vagas usadas: as máquinas vinculadas dentro do raio
     * @param max     o limite do tier
     * @param refusal por que ele ainda não vincula (sem upgrade, raio 0) ou {@link LinkRefusal#NONE}
     */
    public record RouterInfo(Status status, String name, BlockPos pos, int used, int max, LinkRefusal refusal) {
        static final RouterInfo NO_ROUTER = new RouterInfo(Status.NONE, "", BlockPos.ZERO, 0, 0, LinkRefusal.NONE);

        public boolean ready() {
            return status == Status.READY;
        }

        /** O roteador vincula máquinas agora. */
        public boolean links() {
            return ready() && refusal == LinkRefusal.NONE;
        }
    }

    /** Uma máquina na prévia de cima. */
    public record Dot(int x, int z) {
    }

    /** Resultado do Vincular (ou Desvincular), com o que ficou de fora. */
    public record Outcome(boolean unlink, int linked, int already, int outOfRange, int overLimit, int protectedCount,
            int unloadedChunks) {
    }

    public @Nullable LinkerBox box() {
        if (first.isEmpty() || second.isEmpty()) {
            return null;
        }
        BlockPos a = first.get();
        BlockPos b = second.get();
        return LinkerBox.of(a.getX(), a.getY(), a.getZ(), b.getX(), b.getY(), b.getZ());
    }

    /** Monta o estado da tela para o jogador e o Vinculador na mão dele. Só no servidor. */
    public static LinkerSnapshot capture(ServerPlayer player, ItemStack stack, @Nullable Outcome outcome) {
        LinkerArea area = LinkerItem.area(stack);
        boolean otherDimension = area != null && !area.dimension().equals(player.level().dimension());
        Optional<BlockPos> first = area == null || otherDimension ? Optional.empty() : Optional.of(area.first());
        Optional<BlockPos> second = area == null || otherDimension ? Optional.empty() : area.second();
        LinkerProblem problem = LinkerActions.checkArea(player, area);
        RouterBlockEntity chosen = RangeLinks.chosen(player, stack);
        RouterInfo router = routerInfo(stack, chosen);

        int unloaded = 0;
        int linkedInside = 0;
        List<Dot> dots = new ArrayList<>();
        LinkerBox box = area == null || otherDimension ? null : area.box();
        // área grande demais nem é varrida: a tela só mostra o aviso
        if (box != null && problem == LinkerProblem.NONE) {
            ServerChunkCache chunks = player.serverLevel().getChunkSource();
            for (int cx = box.minChunkX(); cx <= box.maxChunkX(); cx++) {
                for (int cz = box.minChunkZ(); cz <= box.maxChunkZ(); cz++) {
                    if (chunks.getChunkNow(cx, cz) == null) {
                        unloaded++;
                    }
                }
            }
            if (chosen != null) {
                for (RemoteMachine machine : chosen.remoteMachines().machines()) {
                    BlockPos pos = machine.pos();
                    if (box.contains(pos.getX(), pos.getY(), pos.getZ())) {
                        linkedInside++;
                        if (dots.size() < MAX_DOTS) {
                            dots.add(new Dot(pos.getX(), pos.getZ()));
                        }
                    }
                }
            }
        }
        return new LinkerSnapshot(LinkerItem.unlink(stack), router, first, second, otherDimension, problem, unloaded,
                linkedInside, List.copyOf(dots), LinkerActions.maxVolume(), LinkerActions.maxDistance(),
                Optional.ofNullable(outcome));
    }

    private static RouterInfo routerInfo(ItemStack stack, @Nullable RouterBlockEntity chosen) {
        GlobalPos pos = LinkerItem.router(stack);
        if (pos == null) {
            return RouterInfo.NO_ROUTER;
        }
        if (chosen == null) {
            return new RouterInfo(Status.MISSING, "", pos.pos(), 0, 0, LinkRefusal.NONE);
        }
        return new RouterInfo(Status.READY, RangeLinks.routerName(chosen).getString(), pos.pos(),
                RangeLinks.used(chosen), RangeLinks.max(chosen), RangeLinks.upgradeRefusal(chosen));
    }

    public static final StreamCodec<RegistryFriendlyByteBuf, LinkerSnapshot> STREAM_CODEC =
            StreamCodec.of(LinkerSnapshot::encode, LinkerSnapshot::decode);

    private static void encode(RegistryFriendlyByteBuf buf, LinkerSnapshot s) {
        buf.writeBoolean(s.unlink);
        buf.writeEnum(s.router.status());
        buf.writeUtf(s.router.name(), 64);
        buf.writeBlockPos(s.router.pos());
        buf.writeVarInt(s.router.used());
        buf.writeVarInt(s.router.max());
        buf.writeEnum(s.router.refusal());
        buf.writeOptional(s.first, (b, p) -> b.writeBlockPos(p));
        buf.writeOptional(s.second, (b, p) -> b.writeBlockPos(p));
        buf.writeBoolean(s.otherDimension);
        buf.writeEnum(s.problem);
        buf.writeVarInt(s.unloadedChunks);
        buf.writeVarInt(s.linkedInside);
        buf.writeVarInt(s.machines.size());
        for (Dot dot : s.machines) {
            buf.writeVarInt(dot.x());
            buf.writeVarInt(dot.z());
        }
        buf.writeVarLong(s.maxVolume);
        buf.writeVarInt(s.maxDistance);
        buf.writeOptional(s.outcome, (b, o) -> {
            b.writeBoolean(o.unlink());
            b.writeVarInt(o.linked());
            b.writeVarInt(o.already());
            b.writeVarInt(o.outOfRange());
            b.writeVarInt(o.overLimit());
            b.writeVarInt(o.protectedCount());
            b.writeVarInt(o.unloadedChunks());
        });
    }

    private static LinkerSnapshot decode(RegistryFriendlyByteBuf buf) {
        boolean unlink = buf.readBoolean();
        RouterInfo router = new RouterInfo(buf.readEnum(Status.class), buf.readUtf(64), buf.readBlockPos(),
                buf.readVarInt(), buf.readVarInt(), buf.readEnum(LinkRefusal.class));
        Optional<BlockPos> first = buf.readOptional(b -> b.readBlockPos());
        Optional<BlockPos> second = buf.readOptional(b -> b.readBlockPos());
        boolean otherDimension = buf.readBoolean();
        LinkerProblem problem = buf.readEnum(LinkerProblem.class);
        int unloaded = buf.readVarInt();
        int linkedInside = buf.readVarInt();
        int dotCount = buf.readVarInt();
        if (dotCount < 0 || dotCount > MAX_DOTS) {
            throw new DecoderException("pontos demais na prévia do Vinculador: " + dotCount);
        }
        List<Dot> dots = new ArrayList<>(dotCount);
        for (int i = 0; i < dotCount; i++) {
            dots.add(new Dot(buf.readVarInt(), buf.readVarInt()));
        }
        long maxVolume = buf.readVarLong();
        int maxDistance = buf.readVarInt();
        Optional<Outcome> outcome = buf.readOptional(b -> new Outcome(b.readBoolean(), b.readVarInt(), b.readVarInt(),
                b.readVarInt(), b.readVarInt(), b.readVarInt(), b.readVarInt()));
        return new LinkerSnapshot(unlink, router, first, second, otherDimension, problem, unloaded, linkedInside,
                List.copyOf(dots), maxVolume, maxDistance, outcome);
    }
}
