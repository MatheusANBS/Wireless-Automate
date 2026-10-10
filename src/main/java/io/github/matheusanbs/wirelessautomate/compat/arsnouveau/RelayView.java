package io.github.matheusanbs.wirelessautomate.compat.arsnouveau;

import com.hollingsworth.arsnouveau.api.source.AbstractSourceMachine;
import net.minecraft.nbt.CompoundTag;

/**
 * Porte 1.20.1 (Ars 4.12): o Tanque de Source visto pelos Relays. O Relay só liga e só transfere com um
 * {@link AbstractSourceMachine} na posição; os mixins de {@code compat/arsnouveau/mixin} trocam o block entity
 * do tanque por esta visão nesses pontos. Não está no mundo: tem a posição, o tipo e o nível do tanque e
 * delega a Source ao {@link ArsStorage.SourceTank} (a visão em {@code int} limitada dele, e o
 * {@code setSource} por delta). Não guarda nada: o conteúdo e o NBT são do tanque.
 *
 * <p>Uma por tanque, criada na primeira vez que um Relay a pede ({@link ArsStorage.SourceTank#relayView()}).
 */
public final class RelayView extends AbstractSourceMachine {
    private final ArsStorage.SourceTank tank;

    RelayView(ArsStorage.SourceTank tank) {
        super(tank.getType(), tank.getBlockPos(), tank.getBlockState());
        this.tank = tank;
    }

    /** O tanque por trás desta visão. */
    public ArsStorage.SourceTank tank() {
        return tank;
    }

    @Override
    public int getSource() {
        return tank.getSource();
    }

    @Override
    public int getMaxSource() {
        return tank.getMaxSource();
    }

    @Override
    public void setMaxSource(int max) {
    }

    @Override
    public int setSource(int source) {
        return tank.setSource(source);
    }

    @Override
    public int addSource(int source) {
        return tank.addSource(source);
    }

    @Override
    public int removeSource(int source) {
        return tank.removeSource(source);
    }

    @Override
    public int getTransferRate() {
        return tank.getTransferRate();
    }

    @Override
    public boolean canAcceptSource() {
        return tank.canAcceptSource();
    }

    @Override
    public boolean canAcceptSource(int source) {
        return (long) getSource() + source <= getMaxSource() && tank.canAcceptSource();
    }

    /** O tanque avisa a mudança (salvar, nível da coluna); esta visão não está no mundo. */
    @Override
    public boolean updateBlock() {
        tank.setChanged();
        return tank.getLevel() != null;
    }

    @Override
    public void load(CompoundTag tag) {
    }

    @Override
    public void saveAdditional(CompoundTag tag) {
    }
}
