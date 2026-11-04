package io.github.matheusanbs.wirelessautomate.item.tuner;

import io.github.matheusanbs.wirelessautomate.WirelessAutomate;
import io.github.matheusanbs.wirelessautomate.block.RouterBlockEntity;
import io.github.matheusanbs.wirelessautomate.linker.LinkerOpen;
import io.github.matheusanbs.wirelessautomate.menu.TabletClick;
import io.github.matheusanbs.wirelessautomate.network.FaceConfig;
import io.github.matheusanbs.wirelessautomate.network.PortMode;
import io.github.matheusanbs.wirelessautomate.network.ResourceType;
import io.github.matheusanbs.wirelessautomate.packet.ConfiguratorProposalPayload;
import io.github.matheusanbs.wirelessautomate.range.EditTarget;
import io.github.matheusanbs.wirelessautomate.range.MachineGroup;
import io.github.matheusanbs.wirelessautomate.range.MachineNames;
import io.github.matheusanbs.wirelessautomate.range.RemoteIndex;
import io.github.matheusanbs.wirelessautomate.range.RemoteMachine;
import io.github.matheusanbs.wirelessautomate.range.RemoteMachines;
import io.github.matheusanbs.wirelessautomate.range.RemoteSide;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.level.block.Block;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.Nullable;

/**
 * O gesto do Afinador do Configurador (spec {@code 2026-11-02-configurador-afinador-design.md}): o primeiro clique numa
 * máquina a analisa ({@link TunerProbe}), guarda a proposta ({@link TunerPending}), manda a pintura ao cliente
 * ({@link ConfiguratorProposalPayload}) e a descreve na action bar; o segundo, na mesma máquina e no prazo, aplica os
 * modos na configuração dela (com Shift, no perfil do grupo), num aviso só ao motor.
 *
 * <p>O roteador é o primeiro que o modo Abrir do Vinculador acharia ({@link LinkerOpen#candidates}: a presa antes,
 * depois os de alcance pela distância, com o alcance do tier, o {@code mayInteract} e as proteções conferidos no
 * roteador). Só muda o modo: prioridade, redstone, filtro e rede de cada face ficam.
 */
public final class ConfiguratorTuner {
    private static final String KEY = "item.wirelessautomate.configurator.tuner.";
    private static final Direction[] DIRECTIONS = Direction.values();
    /** A ordem das faces no texto da proposta. */
    private static final RemoteSide[] TEXT_ORDER = {RemoteSide.TOP, RemoteSide.FRONT, RemoteSide.BACK,
            RemoteSide.LEFT, RemoteSide.RIGHT, RemoteSide.BOTTOM};

    private ConfiguratorTuner() {
    }

    /** Como o roteador chega na máquina: a presa ({@code index} −1) ou a remota do índice {@code index}. */
    record Reach(RouterBlockEntity router, int index) {
        boolean attached() {
            return index < 0;
        }

        /** A frente guardada da máquina ({@code -1}: sem frente). */
        int front() {
            if (attached()) {
                return router.attachedFront();
            }
            RemoteMachine machine = router.remoteMachines().machine(index);
            return machine == null ? -1 : machine.front();
        }
    }

    /**
     * O clique com o Configurador na mão principal num bloco que não é roteador. {@code wholeGroup}: Shift, que no
     * segundo clique aplica no perfil do grupo. Devolve o aviso mostrado na action bar ({@code null}: nenhum, o clique
     * repetido cedo demais).
     */
    public static @Nullable Component click(ServerPlayer player, BlockPos pos, boolean wholeGroup) {
        ServerLevel level = player.serverLevel();
        boolean fresh = TunerPending.attempt(player.getUUID(), level.getGameTime());
        // Segurar o botão repete o uso a cada 4 ticks: só um clique novo (com pausa desde a tentativa anterior) analisa
        // ou aplica, e as repetições não custam nada.
        if (!fresh || !level.isLoaded(pos)) {
            return null;
        }
        if (!player.mayBuild() || !level.mayInteract(player, pos)) {
            return refuse(player, pos, "protected");
        }
        Reach reach = reach(player, level, pos);
        if (reach == null) {
            boolean linked = !TabletClick.attachedRouters(level, pos).isEmpty()
                    || !RemoteIndex.get().routersAt(level, pos).isEmpty();
            return refuse(player, pos, linked ? "no_access" : "not_linked");
        }
        Block block = level.getBlockState(pos).getBlock();
        TunerPending.Proposal pending = TunerPending.get(player.getUUID());
        if (pending != null && pending.matches(level, pos, block)) {
            // o segundo clique só vale depois do intervalo mínimo
            if (!pending.confirmable(level)) {
                return null;
            }
            // O roteador e a frente não são conferidos de novo: os modos da proposta são por face absoluta, e o
            // roteador de agora (o primeiro que o jogador pode editar) é quem recebe.
            TunerPending.forget(player.getUUID());
            send(player, ConfiguratorProposalPayload.clear(pos));
            return show(player, apply(reach, pending.modes(), wholeGroup));
        }
        int front = reach.front();
        Map<ResourceType, PortMode[]> modes;
        try {
            modes = TunerProbe.analyze(level, pos, front, player);
        } catch (RuntimeException e) {
            // A análise chama as capabilities de outro mod com provas que ele não espera: uma exceção dele derruba
            // só esta análise, não o servidor.
            WirelessAutomate.LOGGER.warn("Afinador: a análise de {} em {} falhou", BuiltInRegistries.BLOCK.getKey(block),
                    pos.toShortString(), e);
            return refuse(player, pos, "failed");
        }
        if (modes.isEmpty()) {
            return refuse(player, pos, "nothing");
        }
        long now = level.getGameTime();
        TunerPending.put(player.getUUID(), new TunerPending.Proposal(level.dimension(), pos.immutable(), block, now,
                now + TunerPending.WINDOW, modes));
        send(player, new ConfiguratorProposalPayload(pos.immutable(), faces(modes), TunerPending.WINDOW));
        return show(player, Component.translatable(KEY + "proposal", describe(modes, front)));
    }

    private static Component show(ServerPlayer player, Component message) {
        player.displayClientMessage(message, true);
        return message;
    }

    /**
     * O uso do Configurador no ar ou num bloco em que ele não age: com um clique novo (não o botão segurado desde a
     * máquina, que repete as tentativas a cada 4 ticks), a proposta some.
     */
    public static void useInAir(ServerPlayer player) {
        long now = player.serverLevel().getGameTime();
        boolean fresh = TunerPending.attempt(player.getUUID(), now);
        TunerPending.Proposal pending = TunerPending.get(player.getUUID());
        if (fresh && pending != null) {
            cancel(player);
        }
    }

    /** A proposta some, no servidor e na pintura do cliente. */
    public static void cancel(ServerPlayer player) {
        TunerPending.Proposal pending = TunerPending.get(player.getUUID());
        if (pending != null) {
            TunerPending.forget(player.getUUID());
            send(player, ConfiguratorProposalPayload.clear(pending.machine()));
        }
    }

    /**
     * A pintura sumiu no cliente (o jogador mirou outro bloco): a proposta da máquina {@code machine} some aqui
     * também, sem pacote de volta, e o próximo clique nela refaz a análise. Uma proposta de outra máquina, que chegou
     * depois, fica.
     */
    public static void discard(@Nullable ServerPlayer player, BlockPos machine) {
        if (player == null) {
            return;
        }
        TunerPending.Proposal pending = TunerPending.get(player.getUUID());
        if (pending != null && pending.machine().equals(machine)) {
            TunerPending.forget(player.getUUID());
        }
    }

    /** O primeiro roteador que o jogador pode editar e que chega na máquina, com o caminho; {@code null} sem nenhum. */
    static @Nullable Reach reach(ServerPlayer player, ServerLevel level, BlockPos pos) {
        for (RouterBlockEntity router : LinkerOpen.candidates(player, pos, InteractionHand.MAIN_HAND)) {
            if (pos.equals(router.machinePos())) {
                return new Reach(router, -1);
            }
            int index = router.remoteMachines().index(pos);
            if (index >= 0) {
                return new Reach(router, index);
            }
        }
        return null;
    }

    private static Component refuse(ServerPlayer player, BlockPos pos, String reason) {
        if (TunerPending.forget(player.getUUID())) {
            send(player, ConfiguratorProposalPayload.clear(pos));
        }
        return show(player, Component.translatable(KEY + reason));
    }

    // ------------------------------------------------------------------ aplicar

    /**
     * Aplica os modos pela regra de máquina: a presa pela fachada dela (sozinha no grupo muda o perfil), a remota na
     * exceção (que some se igual ao perfil); {@code wholeGroup}, no perfil do grupo. Só as faces cujo modo muda são
     * tocadas, num aviso só ao motor. Devolve o aviso da action bar.
     */
    static Component apply(Reach reach, Map<ResourceType, PortMode[]> modes, boolean wholeGroup) {
        RouterBlockEntity router = reach.router();
        RemoteMachines remotes = router.remoteMachines();
        int front = reach.front();
        int[] changed = new int[1];
        // [0]: alguma face mudada com modo tem rede; [1]: alguma face mudada tem modo (o Nenhum não pede rede).
        boolean[] network = new boolean[2];
        Component[] where = new Component[1];
        router.batchChanges(() -> {
            EditTarget target;
            if (wholeGroup) {
                if (reach.attached()) {
                    router.ensureAttachedMember();
                }
                MachineGroup group = reach.attached() ? remotes.attachedGroup()
                        : remotes.groupOf(remotes.machine(reach.index()));
                if (group == null) {
                    return;
                }
                target = EditTarget.group(group.id());
                where[0] = MachineNames.group(group);
            } else if (reach.attached()) {
                target = EditTarget.ATTACHED_TARGET;
                where[0] = router.attachedLabel().isEmpty()
                        ? Component.translatable("gui.wirelessautomate.router.range.attached.row")
                        : Component.literal(router.attachedLabel());
            } else {
                target = EditTarget.machine(reach.index());
                where[0] = MachineNames.machine(remotes, remotes.machine(reach.index()).pos());
            }
            for (Map.Entry<ResourceType, PortMode[]> entry : modes.entrySet()) {
                ResourceType type = entry.getKey();
                PortMode[] faces = entry.getValue();
                for (int face = 0; face < faces.length; face++) {
                    // A presa edita pela face absoluta; o grupo e a remota, pelo quadro canônico dos lados da máquina.
                    Direction side = reach.attached() && !wholeGroup ? DIRECTIONS[face]
                            : DIRECTIONS[RemoteSide.fromAbsolute(front, face).canonical()];
                    FaceConfig current = router.face(target, type, side);
                    if (faces[face] == null || current.mode() == faces[face]) {
                        continue;
                    }
                    router.configureFace(target, type, side, faces[face], current.priority(), current.redstone());
                    changed[0] |= 1 << face;
                    if (faces[face] != PortMode.NONE) {
                        network[1] = true;
                        network[0] |= router.face(target, type, side).network() != null;
                    }
                }
            }
        });
        if (where[0] == null) {
            return Component.translatable(KEY + (reach.attached() ? "no_group" : "no_group_remote"));
        }
        String group = wholeGroup ? "_group" : "";
        int count = Integer.bitCount(changed[0]);
        if (count == 0) {
            return Component.translatable(KEY + "unchanged" + group, where[0]);
        }
        MutableComponent message = Component.translatable(KEY + "applied" + group, where[0],
                Component.translatable(KEY + (count == 1 ? "faces.one" : "faces"), count));
        if (network[1] && !network[0]) {
            message.append(Component.translatable(KEY + "choose_network"));
        }
        return message;
    }

    // ------------------------------------------------------------------ proposta

    /** As faces com modo para o payload (o Nenhum e as que ficam como estão não vão). */
    private static List<ConfiguratorProposalPayload.Face> faces(Map<ResourceType, PortMode[]> modes) {
        List<ConfiguratorProposalPayload.Face> faces = new ArrayList<>();
        for (Map.Entry<ResourceType, PortMode[]> entry : modes.entrySet()) {
            PortMode[] byFace = entry.getValue();
            for (int face = 0; face < byFace.length; face++) {
                if (byFace[face] != null && byFace[face] != PortMode.NONE) {
                    faces.add(new ConfiguratorProposalPayload.Face(face, entry.getKey(), byFace[face]));
                }
            }
        }
        return faces;
    }

    /**
     * "Cima Insere · Trás Insere · Baixo Extrai" pelos lados da máquina; com mais de um tipo, cada um com o nome
     * ("Itens: …; Energia: …").
     */
    private static Component describe(Map<ResourceType, PortMode[]> modes, int front) {
        MutableComponent text = Component.empty();
        boolean named = modes.size() > 1;
        boolean first = true;
        for (Map.Entry<ResourceType, PortMode[]> entry : modes.entrySet()) {
            MutableComponent faces = Component.empty();
            boolean firstFace = true;
            for (RemoteSide side : TEXT_ORDER) {
                PortMode mode = entry.getValue()[side.toAbsolute(front)];
                if (mode == null || mode == PortMode.NONE) {
                    continue;
                }
                if (!firstFace) {
                    faces.append(" · ");
                }
                firstFace = false;
                faces.append(Component.translatable(KEY + "face",
                        Component.translatable("gui.wirelessautomate.router.side." + side.key()),
                        Component.translatable("gui.wirelessautomate.router.mode." + modeKey(mode))));
            }
            if (!first) {
                text.append("; ");
            }
            first = false;
            text.append(named ? Component.translatable(KEY + "type",
                    Component.translatable("gui.wirelessautomate.router.type." + entry.getKey().key()), faces) : faces);
        }
        return text;
    }

    private static String modeKey(PortMode mode) {
        return switch (mode) {
            case EXTRACT -> "extract";
            case INSERT -> "insert";
            case BOTH -> "both";
            case NONE -> "none";
        };
    }

    /** Jogadores falsos (GameTests, o {@code FakePlayer} de outro mod) não negociam os canais do mod. */
    private static void send(ServerPlayer player, ConfiguratorProposalPayload payload) {
        if (!(player instanceof FakePlayer) && player.connection != null && player.connection.hasChannel(payload)) {
            PacketDistributor.sendToPlayer(player, payload);
        }
    }
}
