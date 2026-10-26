package io.github.matheusanbs.wirelessautomate.repairer;

import io.github.matheusanbs.wirelessautomate.Config;
import io.github.matheusanbs.wirelessautomate.resonator.SignalCrystalItem;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;

/**
 * O que o cliente anima no Reparador: por linha, o tipo do rachado, o tick em que o reparo termina e se a linha está
 * travada, mais a duração do reparo. Vai no update tag do block entity (chaves {@code kinds}, {@code ends},
 * {@code blocked} e {@code duration}, fora do NBT salvo), só quando muda. Imutável.
 */
public final class RepairerView {
    public static final byte NONE = 0;
    public static final byte SIGNAL = 1;
    public static final byte CUT = 2;
    /** O Afinado do tier de posição {@code t} é {@code TUNED + t}. */
    public static final byte TUNED = 3;
    public static final RepairerView EMPTY = new RepairerView(new byte[RepairMath.LINES], new long[RepairMath.LINES], 0, 0);

    private final byte[] kinds;
    private final long[] ends;
    private final int blocked;
    private final int duration;

    private RepairerView(byte[] kinds, long[] ends, int blocked, int duration) {
        this.kinds = kinds;
        this.ends = ends;
        this.blocked = blocked;
        this.duration = duration;
    }

    /** O tipo do cristal rachado; {@link #NONE} para o que não é rachado. */
    public static byte kindOf(ItemStack stack) {
        if (!RepairerSlots.acceptsCracked(stack)) {
            return NONE;
        }
        SignalCrystalItem item = (SignalCrystalItem) stack.getItem();
        if (item.tunedTier() != null) {
            return (byte) (TUNED + item.tunedTier().ordinal());
        }
        return item.isCut() ? CUT : SIGNAL;
    }

    private static int durationNow() {
        return (int) Math.min(Integer.MAX_VALUE, Config.repairTicks());
    }

    public static RepairerView of(RepairerBlockEntity repairer) {
        byte[] kinds = new byte[RepairMath.LINES];
        long[] ends = new long[RepairMath.LINES];
        int blocked = 0;
        for (int i = 0; i < RepairMath.LINES; i++) {
            kinds[i] = kindOf(repairer.items().get(i));
            ends[i] = repairer.lineEnd(i);
            if (repairer.lineBlocked(i)) {
                blocked |= 1 << i;
            }
        }
        return new RepairerView(kinds, ends, blocked, durationNow());
    }

    /** Compara com o estado de agora, campo a campo, sem montar outra view. */
    public boolean matches(RepairerBlockEntity repairer) {
        if (duration != durationNow()) {
            return false;
        }
        for (int i = 0; i < RepairMath.LINES; i++) {
            if (kinds[i] != kindOf(repairer.items().get(i)) || ends[i] != repairer.lineEnd(i)
                    || blocked(i) != repairer.lineBlocked(i)) {
                return false;
            }
        }
        return true;
    }

    public void save(CompoundTag tag) {
        tag.putByteArray("kinds", kinds);
        tag.putLongArray("ends", ends);
        tag.putByte("blocked", (byte) blocked);
        tag.putInt("duration", duration);
    }

    /** A view do tag, ou {@code null} sem {@code kinds} ou com tamanho errado. */
    @Nullable
    public static RepairerView load(CompoundTag tag) {
        byte[] kinds = tag.getByteArray("kinds");
        long[] ends = tag.getLongArray("ends");
        if (kinds.length != RepairMath.LINES || ends.length != RepairMath.LINES) {
            return null;
        }
        return new RepairerView(kinds, ends, tag.getByte("blocked") & 0xFF, tag.getInt("duration"));
    }

    public byte kind(int line) {
        return kinds[line];
    }

    public long end(int line) {
        return ends[line];
    }

    public boolean blocked(int line) {
        return (blocked >> line & 1) != 0;
    }

    public int duration() {
        return duration;
    }

    /** A linha está andando: tem fim marcado e não está travada. */
    public boolean running(int line) {
        return ends[line] > 0 && !blocked(line);
    }

    public boolean empty() {
        for (int i = 0; i < RepairMath.LINES; i++) {
            if (kinds[i] != NONE) {
                return false;
            }
        }
        return true;
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof RepairerView view && blocked == view.blocked && duration == view.duration
                && Arrays.equals(kinds, view.kinds) && Arrays.equals(ends, view.ends);
    }

    @Override
    public int hashCode() {
        return 31 * (31 * Arrays.hashCode(kinds) + Arrays.hashCode(ends)) + blocked * 7 + duration;
    }
}
