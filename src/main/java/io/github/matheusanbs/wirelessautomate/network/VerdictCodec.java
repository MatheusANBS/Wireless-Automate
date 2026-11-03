package io.github.matheusanbs.wirelessautomate.network;

import io.github.matheusanbs.wirelessautomate.network.Verdict.Action;
import io.github.matheusanbs.wirelessautomate.network.Verdict.Problem;
import io.github.matheusanbs.wirelessautomate.network.Verdict.ProblemKind;
import io.github.matheusanbs.wirelessautomate.network.Verdict.Reason;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.neoforged.neoforge.network.codec.NeoForgeStreamCodecs;

/**
 * O codec de rede do {@link Verdict}. Fica fora dele para o veredito continuar lógica pura (JUnit sem o Minecraft);
 * escrito campo a campo porque o {@code StreamCodec.composite} do jogo aceita no máximo 6 campos e o veredito tem 8.
 */
public final class VerdictCodec {
    private static final StreamCodec<RegistryFriendlyByteBuf, FaceSide> SIDE_CODEC = NeoForgeStreamCodecs.enumCodec(FaceSide.class);
    private static final StreamCodec<RegistryFriendlyByteBuf, ResourceType> TYPE_CODEC = NeoForgeStreamCodecs.enumCodec(ResourceType.class);
    private static final StreamCodec<RegistryFriendlyByteBuf, Action> ACTION_CODEC = NeoForgeStreamCodecs.enumCodec(Action.class);
    private static final StreamCodec<RegistryFriendlyByteBuf, Reason> REASON_CODEC = NeoForgeStreamCodecs.enumCodec(Reason.class);

    private static final StreamCodec<RegistryFriendlyByteBuf, Problem> PROBLEM_CODEC = StreamCodec.composite(
            NeoForgeStreamCodecs.enumCodec(ProblemKind.class), Problem::kind,
            TYPE_CODEC, Problem::type,
            SIDE_CODEC, Problem::face,
            ACTION_CODEC, Problem::action,
            Problem::new);

    private static final StreamCodec<RegistryFriendlyByteBuf, List<Problem>> PROBLEMS_CODEC =
            PROBLEM_CODEC.apply(ByteBufCodecs.list(Verdict.MAX_PROBLEMS));

    public static final StreamCodec<RegistryFriendlyByteBuf, Verdict> STREAM_CODEC = new StreamCodec<>() {
        @Override
        public Verdict decode(RegistryFriendlyByteBuf buf) {
            Reason reason = REASON_CODEC.decode(buf);
            ResourceType type = TYPE_CODEC.decode(buf);
            FaceSide face = SIDE_CODEC.decode(buf);
            UUID network = buf.readBoolean() ? UUIDUtil.STREAM_CODEC.decode(buf) : null;
            int count = buf.readVarInt();
            Action action = ACTION_CODEC.decode(buf);
            List<Problem> problems = PROBLEMS_CODEC.decode(buf);
            int n = Math.min(buf.readVarInt(), Verdict.MAX_POSITIONS);
            List<Long> positions = new ArrayList<>(n);
            for (int i = 0; i < n; i++) {
                positions.add(buf.readVarLong());
            }
            return new Verdict(reason, type, face, network, count, action, problems, positions);
        }

        @Override
        public void encode(RegistryFriendlyByteBuf buf, Verdict v) {
            REASON_CODEC.encode(buf, v.reason());
            TYPE_CODEC.encode(buf, v.type());
            SIDE_CODEC.encode(buf, v.face());
            buf.writeBoolean(v.network() != null);
            if (v.network() != null) {
                UUIDUtil.STREAM_CODEC.encode(buf, v.network());
            }
            buf.writeVarInt(v.count());
            ACTION_CODEC.encode(buf, v.action());
            PROBLEMS_CODEC.encode(buf, v.problems());
            buf.writeVarInt(v.positions().size());
            for (long pos : v.positions()) {
                buf.writeVarLong(pos);
            }
        }
    };

    private VerdictCodec() {
    }
}
