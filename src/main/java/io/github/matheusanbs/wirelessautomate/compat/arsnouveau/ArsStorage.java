package io.github.matheusanbs.wirelessautomate.compat.arsnouveau;

import com.hollingsworth.arsnouveau.api.source.ISourceCap;
import com.hollingsworth.arsnouveau.api.source.ISourceTile;
import com.hollingsworth.arsnouveau.api.source.ISpecialSourceProvider;
import com.hollingsworth.arsnouveau.api.source.SourceManager;
import io.github.matheusanbs.wirelessautomate.registry.ModBlockEntities;
import io.github.matheusanbs.wirelessautomate.storage.ScalarStore;
import io.github.matheusanbs.wirelessautomate.storage.StorageSourceTankBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;

/**
 * O Tanque de Source visto pelo Ars Nouveau. Só é carregada com o Ars presente (pela ponte
 * {@code network.Sources}); as assinaturas públicas não têm tipos do Ars.
 *
 * <p>Duas portas de entrada: a capability {@code ars_nouveau:source} ({@link ISourceCap}), que os
 * Relays e outros mods usam, e um provider no {@link SourceManager}, que as máquinas do Ars (e os
 * Sourcelinks) consultam no raio delas. O Ars é todo em {@code int}: quantidade e capacidade aparecem
 * no máximo {@link Integer#MAX_VALUE}, mas o conteúdo continua em {@code long} no {@link ScalarStore}.
 */
public final class ArsStorage {
    /** Registra a {@link ISourceCap} do tanque. */
    public static void register(RegisterCapabilitiesEvent event) {
        event.registerBlockEntity(ArsSources.BLOCK, ModBlockEntities.SOURCE_TANK.get(), (tank, side) -> new TankCap(tank));
    }

    /**
     * Põe o tanque no {@link SourceManager} (só no servidor). O provider deixa de valer quando o block
     * entity sai do mundo, e o Ars o descarta sozinho a cada 60 ticks.
     */
    public static void registerProvider(StorageSourceTankBlockEntity tank) {
        Level level = tank.getLevel();
        if (level == null || level.isClientSide) {
            return;
        }
        SourceManager.INSTANCE.addInterface(level, new TankProvider(tank));
    }

    /**
     * O {@code setSource} do Ars, tratado como delta sobre a visão em {@code int}: o Ars só enxerga
     * {@link Integer#MAX_VALUE}, então um {@code setSource(getSource() - n)} de terceiros num tanque com
     * mais que isso tira só {@code n} e não derruba o conteúdo para o teto do {@code int}. Abaixo do teto
     * é o mesmo que trocar o valor; {@code setSource(getSource())} não muda nada.
     */
    private static void setFromArs(ScalarStore store, int source) {
        long stored = store.stored();
        store.replace(stored + ((long) Math.max(0, source) - clamp(stored)));
    }

    private static int clamp(long value) {
        return (int) Math.min(Math.max(0, value), Integer.MAX_VALUE);
    }

    /** A capacidade em {@code int}: sem limite (0) ou acima do {@code int} vira {@link Integer#MAX_VALUE}. */
    private static int maxSource(StorageSourceTankBlockEntity tank) {
        long capacity = tank.capacity();
        return capacity <= 0 || capacity > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) capacity;
    }

    /** A capability do Ars sobre o conteúdo do tanque. */
    private record TankCap(StorageSourceTankBlockEntity tank) implements ISourceCap {
        private ScalarStore store() {
            return tank.store();
        }

        @Override
        public boolean canAcceptSource(int source) {
            return receiveSource(source, true) > 0;
        }

        @Override
        public boolean canProvideSource(int source) {
            return extractSource(source, true) > 0;
        }

        @Override
        public int getMaxExtract() {
            return Integer.MAX_VALUE;
        }

        @Override
        public int getMaxReceive() {
            return Integer.MAX_VALUE;
        }

        @Override
        public int getSource() {
            return clamp(store().stored());
        }

        @Override
        public int getSourceCapacity() {
            return maxSource(tank);
        }

        @Override
        public int getMaxSource() {
            return maxSource(tank);
        }

        @Override
        public void setSource(int source) {
            setFromArs(store(), source);
        }

        /** A capacidade é a do tier: não muda por aqui. */
        @Override
        public void setMaxSource(int max) {
        }

        @Override
        public int receiveSource(int source, boolean simulate) {
            return (int) store().insert(Math.max(0, source), simulate);
        }

        @Override
        public int extractSource(int source, boolean simulate) {
            return (int) store().extract(Math.max(0, source), simulate);
        }
    }

    /** O tanque no {@link SourceManager}: vale enquanto este block entity é o que está na posição. */
    private static final class TankProvider implements ISpecialSourceProvider {
        private final StorageSourceTankBlockEntity tank;
        private final TankTile tile;

        TankProvider(StorageSourceTankBlockEntity tank) {
            this.tank = tank;
            this.tile = new TankTile(tank);
        }

        @Override
        public ISourceTile getSource() {
            return tile;
        }

        @Override
        public boolean isValid() {
            // trocar o block entity, quebrar e descarregar o chunk passam todos por setRemoved
            return !tank.isRemoved() && tank.getLevel() != null;
        }

        @Override
        public BlockPos getCurrentPos() {
            return tank.getBlockPos();
        }
    }

    /**
     * O tanque como {@link ISourceTile}. {@code addSource(n)} e {@code removeSource(n)} sem simulação
     * devolvem o "total novo" relativo ao {@link #getSource()} de antes, para o
     * {@code SourceUtil.takeSourceMultiple} (que calcula antes − depois) acertar mesmo com mais de
     * {@link Integer#MAX_VALUE} guardado; as versões com {@code simulate} devolvem quanto passou.
     */
    private record TankTile(StorageSourceTankBlockEntity tank) implements ISourceTile {
        private ScalarStore store() {
            return tank.store();
        }

        @Override public int getTransferRate() { return Integer.MAX_VALUE; }
        /**
         * Só aceita se a visão em {@code int} tem espaço: o Sourcelink calcula a vazão como
         * {@code getMaxSource() − getSource()}, e com o {@code int} cheio passaria 0 para sempre.
         */
        @Override public boolean canAcceptSource() { return getSource() < getMaxSource() && store().insert(1, true) > 0; }
        @Override public int getSource() { return clamp(store().stored()); }
        @Override public int getMaxSource() { return maxSource(tank); }
        @Override public int setSource(int source) { setFromArs(store(), source); return getSource(); }
        /** Existe para o Ars 5.2 (abstrato lá, sem @Override para compilar com o 5.13); a capacidade é a do tier. */
        public void setMaxSource(int max) { }
        /** Total novo relativo ao getSource() de antes (o Ars calcula o que passou como antes − depois). */
        @Override public int addSource(int source) {
            int before = getSource();
            return clamp((long) before + store().insert(Math.max(0, source), false));
        }
        @Override public int removeSource(int source) {
            int before = getSource();
            return (int) Math.max(0, before - store().extract(Math.max(0, source), false));
        }
        @Override public int addSource(int source, boolean simulate) { return (int) store().insert(Math.max(0, source), simulate); }
        @Override public int removeSource(int source, boolean simulate) { return (int) store().extract(Math.max(0, source), simulate); }
    }

    private ArsStorage() {
    }
}
