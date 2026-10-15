package io.github.matheusanbs.wirelessautomate.resonator;

import io.github.matheusanbs.wirelessautomate.Config;
import java.util.Arrays;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;

/**
 * O que a câmara do Núcleo desenha no cliente, mandado pelo update tag só quando muda: o que há em cada câmara, a
 * faixa da eficiência, a lâmpada, a recarga em curso, o tempo da montagem e o canto da caixa. A estação não vai.
 *
 * @param chambers uma posição por câmara do tier: {@code 0} vazia, {@code 1} Cristal de Sinal, {@code 2} Lapidado,
 *     {@code 3} Cristal Opaco, {@code 4} Lapidado Opaco
 * @param rechargeStart o tempo de jogo em que a recarga em curso começou, ou -1 sem recarga
 * @param formedAt o {@link ResonanceCoreBlockEntity#formedAt()}: -1 nunca montou, 0 já montado de antes
 * @param origin o canto mínimo da caixa (x, y, z)
 */
public record ChamberView(byte[] chambers, int band, boolean tuned, long rechargeStart, int rechargeTicks,
        long formedAt, int[] origin) {
    public static final ChamberView EMPTY = new ChamberView(new byte[0], 0, false, -1, 0, -1, new int[3]);

    private static final byte EMPTY_CHAMBER = 0;
    private static final byte SIGNAL = 1;
    private static final byte CUT = 2;
    private static final byte DULL_OFFSET = 2;

    public static ChamberView of(ResonanceCoreBlockEntity core, long gameTime) {
        int count = core.chambers();
        byte[] chambers = new byte[count];
        boolean anyDull = false;
        for (int i = 0; i < count; i++) {
            ItemStack stack = core.crystals().get(i);
            if (stack.getItem() instanceof SignalCrystalItem item) {
                boolean dull = SignalCrystalItem.isDull(stack);
                anyDull |= dull;
                chambers[i] = (byte) ((item.isCut() ? CUT : SIGNAL) + (dull ? DULL_OFFSET : 0));
            } else {
                chambers[i] = EMPTY_CHAMBER;
            }
        }
        long rechargeStart = anyDull && core.redstone() > 0 ? gameTime - core.rechargeProgress() : -1;
        return new ChamberView(chambers, TuneSignal.band(core.efficiency()),
                TuneSignal.tuned(core.station(), core.dial()), rechargeStart,
                (int) Math.min(Config.rechargeTicks(), Integer.MAX_VALUE), core.formedAt(),
                origin(core.members(), core.getBlockPos()));
    }

    private static int[] origin(List<BlockPos> members, BlockPos core) {
        if (members.isEmpty()) {
            return new int[] {core.getX(), core.getY(), core.getZ()};
        }
        int x = Integer.MAX_VALUE;
        int y = Integer.MAX_VALUE;
        int z = Integer.MAX_VALUE;
        for (BlockPos member : members) {
            x = Math.min(x, member.getX());
            y = Math.min(y, member.getY());
            z = Math.min(z, member.getZ());
        }
        return new int[] {x, y, z};
    }

    public void save(CompoundTag tag) {
        tag.putByteArray("chambers", chambers);
        tag.putByte("band", (byte) band);
        tag.putBoolean("tuned", tuned);
        tag.putLong("recharge_start", rechargeStart);
        tag.putInt("recharge_ticks", rechargeTicks);
        tag.putLong("formed_at", formedAt);
        tag.putIntArray("origin", origin);
    }

    /** Sem a chave {@code chambers} (o tag completo do disco, por exemplo), {@link #EMPTY}. */
    public static ChamberView load(CompoundTag tag) {
        if (!tag.contains("chambers")) {
            return EMPTY;
        }
        int[] origin = tag.getIntArray("origin");
        return new ChamberView(tag.getByteArray("chambers"), tag.getByte("band"), tag.getBoolean("tuned"),
                tag.getLong("recharge_start"), tag.getInt("recharge_ticks"), tag.getLong("formed_at"),
                origin.length == 3 ? origin : new int[3]);
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof ChamberView view && Arrays.equals(chambers, view.chambers) && band == view.band
                && tuned == view.tuned && rechargeStart == view.rechargeStart && rechargeTicks == view.rechargeTicks
                && formedAt == view.formedAt && Arrays.equals(origin, view.origin);
    }

    @Override
    public int hashCode() {
        int hash = Arrays.hashCode(chambers);
        hash = 31 * hash + band;
        hash = 31 * hash + Boolean.hashCode(tuned);
        hash = 31 * hash + Long.hashCode(rechargeStart);
        hash = 31 * hash + rechargeTicks;
        hash = 31 * hash + Long.hashCode(formedAt);
        return 31 * hash + Arrays.hashCode(origin);
    }

    @Override
    public String toString() {
        return "ChamberView[chambers=" + Arrays.toString(chambers) + ", band=" + band + ", tuned=" + tuned
                + ", rechargeStart=" + rechargeStart + ", rechargeTicks=" + rechargeTicks + ", formedAt=" + formedAt
                + ", origin=" + Arrays.toString(origin) + "]";
    }
}
