package io.github.matheusanbs.wirelessautomate.resonator;

import io.github.matheusanbs.wirelessautomate.storage.BulkEnergy;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.neoforge.capabilities.BlockCapabilityCache;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.energy.IEnergyStorage;
import org.jetbrains.annotations.Nullable;

/**
 * O empurrão da Porta de Energia: a cada tick, cada Porta passa o que há no buffer (até o {@code maxOutput} por tick)
 * aos vizinhos que não são membros da Armilar e que aceitam energia. Primeiro a {@link BulkEnergy} do mod (em
 * {@code long}, o valor inteiro numa chamada), depois a {@link IEnergyStorage} do NeoForge (em {@code int}, até
 * {@link #MAX_FE_CALLS} chamadas de até {@link Integer#MAX_VALUE} por vizinho). O que o vizinho não aceita fica no
 * buffer, e o que sai soma na vazão da Porta.
 *
 * <p>Os {@link BlockCapabilityCache} nascem com a Porta no Núcleo ({@link #rebuild}, na montagem e na carga) e são
 * soltos na desmontagem e na saída do Núcleo ({@link #release}); um por capability e por face de vizinho que não é
 * membro. Eles avisam por um sinal ({@code dirty}) quando algo muda em volta; só então as capabilities são relidas
 * e o conjunto de receptores de cada Porta refeito. Sem receptor, o custo por tick é a leitura de um {@code boolean}.
 * Só a thread do servidor mexe. Nada daqui vai para o NBT.
 */
final class CorePush {
    /** Chamadas de {@link IEnergyStorage#receiveEnergy} por vizinho e por tick, cada uma de até {@link Integer#MAX_VALUE}. */
    static final int MAX_FE_CALLS = 4;
    private static final Direction[] FACES = Direction.values();
    private static final Port[] NO_PORTS = new Port[0];
    private static final BulkEnergy[] NO_BULK = new BulkEnergy[0];
    private static final IEnergyStorage[] NO_FE = new IEnergyStorage[0];

    /** Uma Porta de Energia: os caches das faces de fora e o que eles resolveram na última leitura. */
    private static final class Port {
        final List<BlockCapabilityCache<BulkEnergy, @Nullable Direction>> bulkCaches = new ArrayList<>(2);
        final List<BlockCapabilityCache<IEnergyStorage, @Nullable Direction>> feCaches = new ArrayList<>(2);
        BulkEnergy[] bulks = NO_BULK;
        IEnergyStorage[] fes = NO_FE;
    }

    /** As Portas de Energia que os caches cobrem (a lista do {@link PortOrder}, a mesma da {@link CoreFlow}). */
    private List<BlockPos> built = List.of();
    private Port[] ports = NO_PORTS;
    /** Algo mudou em volta (ou os caches são novos): as capabilities precisam ser relidas antes do próximo empurrão. */
    private boolean dirty;
    /** Alguma Porta tem receptor na última leitura. */
    private boolean active;
    /** Muda a cada soltura e a cada refeita: os caches antigos deixam de valer e saem da lista do nível sozinhos. */
    private int generation;

    /**
     * Cria os caches das Portas de Energia {@code energyPorts} (em ordem, o índice da vazão), uma face por vizinho que
     * não é membro de nenhuma Armilar. Igual ao que já está montado não faz nada, para a revalidação com a estrutura
     * intacta não refazer os caches.
     */
    void rebuild(ServerLevel level, ResonanceCoreBlockEntity core, List<BlockPos> energyPorts) {
        if (built.equals(energyPorts) && (ports.length > 0 || energyPorts.isEmpty())) {
            return;
        }
        release();
        if (energyPorts.isEmpty()) {
            return;
        }
        built = List.copyOf(energyPorts);
        int created = generation;
        ports = new Port[built.size()];
        for (int i = 0; i < ports.length; i++) {
            BlockPos at = built.get(i);
            Port port = new Port();
            for (Direction face : FACES) {
                BlockPos neighbor = at.relative(face);
                if (ResonatorMembers.owner(level.dimension(), neighbor) != null) {
                    continue;
                }
                // A capability do vizinho se pede pela face dele que olha para a Porta.
                Direction side = face.getOpposite();
                port.bulkCaches.add(BlockCapabilityCache.create(BulkEnergy.BLOCK, level, neighbor, side,
                        () -> generation == created && !core.isRemoved(), () -> dirty = true));
                port.feCaches.add(BlockCapabilityCache.create(Capabilities.EnergyStorage.BLOCK, level, neighbor, side,
                        () -> generation == created && !core.isRemoved(), () -> dirty = true));
            }
            ports[i] = port;
        }
        dirty = true;
    }

    /** Solta os caches (desmontagem, descarga ou remoção do Núcleo): ninguém mais os consulta. */
    void release() {
        generation++;
        built = List.of();
        ports = NO_PORTS;
        dirty = false;
        active = false;
    }

    /** Há o que empurrar: energia no buffer e receptor (ou leitura por fazer). Só leitura de campos. */
    boolean canPush(ResonanceCoreBlockEntity core) {
        return (active || dirty) && core.energy() > 0;
    }

    /** Relê as capabilities de todos os caches (o que rearma os avisos deles) e refaz os receptores de cada Porta. */
    private void resolve() {
        dirty = false;
        active = false;
        for (Port port : ports) {
            int bulkCount = 0;
            int feCount = 0;
            int faces = port.bulkCaches.size();
            BulkEnergy[] bulks = new BulkEnergy[faces];
            IEnergyStorage[] fes = new IEnergyStorage[faces];
            for (int i = 0; i < faces; i++) {
                BulkEnergy bulk = port.bulkCaches.get(i).getCapability();
                IEnergyStorage fe = port.feCaches.get(i).getCapability();
                if (bulk != null) {
                    // O mesmo bloco com as duas: a de long basta, a de int só repetiria a chamada.
                    bulks[bulkCount++] = bulk;
                } else if (fe != null) {
                    fes[feCount++] = fe;
                }
            }
            port.bulks = bulkCount == 0 ? NO_BULK : Arrays.copyOf(bulks, bulkCount);
            port.fes = feCount == 0 ? NO_FE : Arrays.copyOf(fes, feCount);
            active |= bulkCount + feCount > 0;
        }
    }

    /**
     * Empurra um tick: cada Porta passa até {@code min(buffer, maxOutput)} aos receptores, e o que saiu soma na vazão
     * dela. Cada FE aceito sai do buffer na hora, antes da chamada seguinte.
     */
    void push(ResonanceCoreBlockEntity core) {
        if (dirty) {
            resolve();
        }
        if (!active) {
            return;
        }
        long maxOutput = core.maxOutput();
        long moved = 0;
        for (int i = 0; i < ports.length; i++) {
            long left = core.energy();
            if (left <= 0) {
                break;
            }
            long sent = pushPort(core, ports[i], Math.min(left, maxOutput));
            if (sent > 0) {
                core.flow().add(i, sent);
                moved += sent;
            }
        }
        if (moved > 0) {
            core.markUnsaved();
        }
    }

    private static long pushPort(ResonanceCoreBlockEntity core, Port port, long budget) {
        long rest = budget;
        for (BulkEnergy bulk : port.bulks) {
            if (rest <= 0) {
                break;
            }
            long accepted = Math.min(rest, bulk.insert(rest, false));
            if (accepted > 0) {
                core.removePushed(accepted);
                rest -= accepted;
            }
        }
        for (IEnergyStorage storage : port.fes) {
            for (int call = 0; call < MAX_FE_CALLS && rest > 0; call++) {
                int ask = (int) Math.min(rest, Integer.MAX_VALUE);
                int accepted = Math.min(ask, storage.receiveEnergy(ask, false));
                if (accepted <= 0) {
                    break;
                }
                core.removePushed(accepted);
                rest -= accepted;
            }
        }
        return budget - rest;
    }
}
