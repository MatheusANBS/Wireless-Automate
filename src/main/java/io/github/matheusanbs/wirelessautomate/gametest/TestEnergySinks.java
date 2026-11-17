package io.github.matheusanbs.wirelessautomate.gametest;

import io.github.matheusanbs.wirelessautomate.WirelessAutomate;
import io.github.matheusanbs.wirelessautomate.registry.ModBlocks;
import io.github.matheusanbs.wirelessautomate.storage.BulkEnergy;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.energy.IEnergyStorage;

/**
 * Os receptores de energia dos GameTests da Porta de Energia ativa do Ressonador: um {@link IEnergyStorage} que aceita
 * até {@link FeSink#perCall} por chamada, sem limite de capacidade, e um {@link BulkEnergy} que aceita tudo, os dois
 * contando o que receberam. Presos a blocos vanilla sem capability, com o estado por posição. Mais a sonda dos
 * membros: nas posições que o teste marca ({@link #probe}), os blocos da própria Armilar também aceitam energia, e o
 * teste confere que o empurrão da Porta nunca chega neles. Só existem com {@code -Dwirelessautomate.gameTests=true}.
 */
@EventBusSubscriber(modid = WirelessAutomate.MODID)
public final class TestEnergySinks {
    /** Receptor {@link IEnergyStorage} com teto por chamada ({@link FeSink}). */
    public static final Block FE_SINK = Blocks.GILDED_BLACKSTONE;
    /** Receptor {@link BulkEnergy} em {@code long} que aceita tudo ({@link BulkSink}). */
    public static final Block BULK_SINK = Blocks.NETHERITE_BLOCK;

    private static final boolean ENABLED = Boolean.getBoolean("wirelessautomate.gameTests");
    private static final Map<BlockPos, FeSink> FE = new ConcurrentHashMap<>();
    private static final Map<BlockPos, BulkSink> BULK = new ConcurrentHashMap<>();
    private static final Set<BlockPos> PROBED = ConcurrentHashMap.newKeySet();

    private TestEnergySinks() {
    }

    @SubscribeEvent
    static void register(RegisterCapabilitiesEvent event) {
        if (!ENABLED) {
            return;
        }
        event.registerBlock(Capabilities.EnergyStorage.BLOCK, (level, pos, state, be, side) -> fe(pos), FE_SINK);
        event.registerBlock(BulkEnergy.BLOCK, (level, pos, state, be, side) -> bulk(pos), BULK_SINK);
        // A sonda: os blocos da Armilar nas posições marcadas aceitam energia, pelos dois caminhos.
        for (Block member : new Block[] {ModBlocks.RESONATOR_CASING.get(), ModBlocks.RESONATOR_COLUMN.get(),
                ModBlocks.RESONATOR_CRADLE.get(), ModBlocks.RESONATOR_CRYSTAL_PORT.get(), ModBlocks.RESONATOR_TUNER.get(),
                ModBlocks.RESONANCE_CORE.get()}) {
            event.registerBlock(Capabilities.EnergyStorage.BLOCK,
                    (level, pos, state, be, side) -> PROBED.contains(pos) ? fe(pos) : null, member);
            event.registerBlock(BulkEnergy.BLOCK,
                    (level, pos, state, be, side) -> PROBED.contains(pos) ? bulk(pos) : null, member);
        }
    }

    /** O receptor de {@link IEnergyStorage} na posição absoluta {@code pos} (criado na primeira consulta). */
    static FeSink fe(BlockPos pos) {
        return FE.computeIfAbsent(pos.immutable(), key -> new FeSink());
    }

    /** O receptor de {@link BulkEnergy} na posição absoluta {@code pos} (criado na primeira consulta). */
    static BulkSink bulk(BlockPos pos) {
        return BULK.computeIfAbsent(pos.immutable(), key -> new BulkSink());
    }

    /** Marca {@code pos} para a sonda dos membros: se ali houver um bloco da Armilar, ele aceita energia. */
    static void probe(BlockPos pos) {
        PROBED.add(pos.immutable());
    }

    /** Esquece o receptor e a sonda da posição, ao fim do teste. */
    static void forget(BlockPos pos) {
        FE.remove(pos);
        BULK.remove(pos);
        PROBED.remove(pos);
    }

    /** Aceita até {@link #perCall} por chamada e guarda tudo: capacidade sem fim. */
    static final class FeSink implements IEnergyStorage {
        int perCall = Integer.MAX_VALUE;
        long received;
        int calls;

        @Override
        public int receiveEnergy(int toReceive, boolean simulate) {
            int taken = Math.max(0, Math.min(toReceive, perCall));
            if (!simulate) {
                calls++;
                received += taken;
            }
            return taken;
        }

        @Override
        public int extractEnergy(int toExtract, boolean simulate) {
            return 0;
        }

        @Override
        public int getEnergyStored() {
            return 0;
        }

        @Override
        public int getMaxEnergyStored() {
            return Integer.MAX_VALUE;
        }

        @Override
        public boolean canExtract() {
            return false;
        }

        @Override
        public boolean canReceive() {
            return true;
        }
    }

    /** Aceita tudo o que vier, numa chamada só, e conta em {@code long}. */
    static final class BulkSink implements BulkEnergy {
        long received;
        int calls;

        @Override
        public long stored() {
            return 0;
        }

        @Override
        public long capacity() {
            return 0;
        }

        @Override
        public long insert(long amount, boolean simulate) {
            long taken = Math.max(0, amount);
            if (!simulate) {
                calls++;
                received += taken;
            }
            return taken;
        }

        @Override
        public long extract(long amount, boolean simulate) {
            return 0;
        }
    }
}
