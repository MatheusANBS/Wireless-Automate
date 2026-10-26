package io.github.matheusanbs.wirelessautomate.resonator;

import io.github.matheusanbs.wirelessautomate.Config;
import java.util.Arrays;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;

/**
 * O que a câmara do Núcleo desenha no cliente, mandado pelo update tag só quando muda: o que há em cada câmara, a
 * faixa da eficiência, a lâmpada, a recarga em curso, o tempo da montagem, o canto da caixa, a faixa de vazão de
 * cada Porta de Energia e o buffer cheio. A estação não vai.
 *
 * @param chambers uma posição por câmara do tier: {@code 0} vazia, {@code 1} Cristal de Sinal, {@code 2} Lapidado,
 *     {@code 3} Cristal Opaco, {@code 4} Lapidado Opaco, {@code 5} Cristal rachado, {@code 6} Lapidado rachado (os
 *     Afinados contam como Lapidado)
 * @param tiers um byte por câmara: {@code 0} sem tier, ou o tier do Afinado mais um (o {@code RouterTier.ordinal()
 *     + 1}); vazio quando nenhuma câmara tem Afinado
 * @param rechargeStart o tempo de jogo em que a recarga em curso começou, ou -1 sem recarga
 * @param rechargeTicks a duração da recarga, pelo menos 1
 * @param rechargers quantos Opacos recarregam agora: um por catalisador no Núcleo, até o total de recarregáveis
 *     ({@link RechargeRank#size}); 0 sem recarga
 * @param formedAt o {@link ResonanceCoreBlockEntity#formedAt()}: -1 nunca montou, 0 já montado de antes
 * @param origin o canto mínimo da caixa (x, y, z)
 * @param flows a faixa de vazão (0 a 7) de cada Porta de Energia no último lote, na ordem do {@link PortOrder}
 * @param full o buffer terminou o último lote cheio
 */
public record ChamberView(byte[] chambers, byte[] tiers, int band, boolean tuned, long rechargeStart, int rechargeTicks,
        int rechargers, long formedAt, int[] origin, byte[] flows, boolean full) {
    /** A duração padrão da recarga, para quem dividir por ela nunca ver 0. */
    private static final int DEFAULT_RECHARGE_TICKS = (int) Math.min(Config.RECHARGE_TICKS.getDefault(),
            Integer.MAX_VALUE);
    public static final ChamberView EMPTY = new ChamberView(new byte[0], new byte[0], 0, false, -1, DEFAULT_RECHARGE_TICKS, 0, -1,
            new int[3], new byte[0], false);

    private static final byte EMPTY_CHAMBER = 0;
    private static final byte SIGNAL = 1;
    private static final byte CUT = 2;
    private static final byte DULL_OFFSET = 2;
    private static final byte CRACKED_OFFSET = 4;

    /** Os arrays são copiados na entrada e na saída: uma view nunca muda depois de criada. */
    public ChamberView {
        chambers = chambers.clone();
        tiers = anyTier(tiers) ? tiers.clone() : new byte[0];
        origin = origin.clone();
        flows = flows.clone();
        rechargeTicks = Math.max(1, rechargeTicks);
        rechargers = Math.max(0, rechargers);
    }

    @Override
    public byte[] chambers() {
        return chambers.clone();
    }

    @Override
    public byte[] tiers() {
        return tiers.clone();
    }

    /** O tier do Afinado da câmara {@code i} mais um, ou 0 sem Afinado. */
    public int tierAt(int i) {
        return i < tiers.length ? tiers[i] : 0;
    }

    private static boolean anyTier(byte[] tiers) {
        for (byte tier : tiers) {
            if (tier != 0) {
                return true;
            }
        }
        return false;
    }

    @Override
    public int[] origin() {
        return origin.clone();
    }

    @Override
    public byte[] flows() {
        return flows.clone();
    }

    /**
     * A view do Núcleo agora, sem mudar nada nele: o início da recarga vem pronto, fixado pelo {@link CoreSync} (o
     * progresso só anda no lote, o tempo a cada tick).
     */
    public static ChamberView of(ResonanceCoreBlockEntity core, long rechargeStart) {
        int count = core.chambers();
        byte[] chambers = new byte[count];
        byte[] tiers = new byte[count];
        for (int i = 0; i < count; i++) {
            ItemStack stack = core.crystals().get(i);
            chambers[i] = chamber(stack);
            tiers[i] = tier(stack);
        }
        return new ChamberView(chambers, tiers, TuneSignal.band(core.efficiency()),
                TuneSignal.tuned(core.station(), core.dial()), rechargeStart,
                (int) Math.min(Config.rechargeTicks(), Integer.MAX_VALUE), rechargers(core), core.formedAt(), core.origin(),
                core.flow().bands(), core.flow().full());
    }

    /**
     * Se a view é a do Núcleo agora (o que o {@link #of} montaria), sem montar outra: o lote pergunta a cada 20
     * ticks, e quase sempre nada mudou.
     */
    boolean matches(ResonanceCoreBlockEntity core, long rechargeStart) {
        int count = core.chambers();
        if (chambers.length != count || this.rechargeStart != rechargeStart || formedAt != core.formedAt()
                || band != TuneSignal.band(core.efficiency()) || tuned != TuneSignal.tuned(core.station(), core.dial())
                || rechargeTicks != Math.max(1, (int) Math.min(Config.rechargeTicks(), Integer.MAX_VALUE))
                || rechargers != rechargers(core)
                || !Arrays.equals(origin, core.origin()) || full != core.flow().full()
                || !Arrays.equals(flows, core.flow().bands())) {
            return false;
        }
        for (int i = 0; i < count; i++) {
            ItemStack stack = core.crystals().get(i);
            if (chambers[i] != chamber(stack) || tierAt(i) != tier(stack)) {
                return false;
            }
        }
        return true;
    }

    private static byte chamber(ItemStack stack) {
        if (stack.getItem() instanceof SignalCrystalItem item) {
            int offset = SignalCrystalItem.isCracked(stack) ? CRACKED_OFFSET
                    : SignalCrystalItem.isDull(stack) ? DULL_OFFSET : 0;
            return (byte) ((item.isCut() ? CUT : SIGNAL) + offset);
        }
        return EMPTY_CHAMBER;
    }

    private static byte tier(ItemStack stack) {
        if (stack.getItem() instanceof SignalCrystalItem item && item.tunedTier() != null) {
            return (byte) (item.tunedTier().ordinal() + 1);
        }
        return 0;
    }

    /** Quantos Opacos recarregam agora: um por catalisador no Núcleo, até o total de recarregáveis (o rachado fica de fora). */
    static int rechargers(ResonanceCoreBlockEntity core) {
        int rechargeable = 0;
        for (int i = 0; i < core.chambers(); i++) {
            if (SignalCrystalItem.isRechargeable(core.crystals().get(i))) {
                rechargeable++;
            }
        }
        return RechargeRank.size(rechargeable, core.redstone());
    }

    /** Se há recarga em curso: um Opaco não rachado numa câmara do tier e catalisador no Núcleo. */
    static boolean recharging(ResonanceCoreBlockEntity core) {
        return rechargers(core) > 0;
    }

    /** O canto mínimo da caixa (x, y, z): o dos membros, ou o próprio Núcleo sem membros. */
    static int[] origin(List<BlockPos> members, BlockPos core) {
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
        if (tiers.length > 0) {
            tag.putByteArray("tiers", tiers);
        }
        tag.putByte("band", (byte) band);
        tag.putBoolean("tuned", tuned);
        tag.putLong("recharge_start", rechargeStart);
        tag.putInt("recharge_ticks", rechargeTicks);
        tag.putByte("rechargers", (byte) rechargers);
        tag.putLong("formed_at", formedAt);
        tag.putIntArray("origin", origin);
        tag.putByteArray("flows", flows);
        tag.putBoolean("full", full);
    }

    /** Sem a chave {@code chambers} (o tag completo do disco, por exemplo), {@link #EMPTY}. */
    public static ChamberView load(CompoundTag tag) {
        if (!tag.contains("chambers")) {
            return EMPTY;
        }
        int[] origin = tag.getIntArray("origin");
        return new ChamberView(tag.getByteArray("chambers"), tag.getByteArray("tiers"), tag.getByte("band"), tag.getBoolean("tuned"),
                tag.getLong("recharge_start"), tag.getInt("recharge_ticks"), tag.getByte("rechargers"), tag.getLong("formed_at"),
                origin.length == 3 ? origin : new int[3], tag.getByteArray("flows"), tag.getBoolean("full"));
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof ChamberView view && Arrays.equals(chambers, view.chambers) && Arrays.equals(tiers, view.tiers)
                && band == view.band
                && tuned == view.tuned && rechargeStart == view.rechargeStart && rechargeTicks == view.rechargeTicks && rechargers == view.rechargers
                && formedAt == view.formedAt && Arrays.equals(origin, view.origin) && full == view.full
                && Arrays.equals(flows, view.flows);
    }

    @Override
    public int hashCode() {
        int hash = Arrays.hashCode(chambers);
        hash = 31 * hash + Arrays.hashCode(tiers);
        hash = 31 * hash + band;
        hash = 31 * hash + Boolean.hashCode(tuned);
        hash = 31 * hash + Long.hashCode(rechargeStart);
        hash = 31 * hash + rechargeTicks;
        hash = 31 * hash + rechargers;
        hash = 31 * hash + Long.hashCode(formedAt);
        hash = 31 * hash + Arrays.hashCode(origin);
        hash = 31 * hash + Arrays.hashCode(flows);
        return 31 * hash + Boolean.hashCode(full);
    }

    @Override
    public String toString() {
        return "ChamberView[chambers=" + Arrays.toString(chambers) + ", tiers=" + Arrays.toString(tiers) + ", band=" + band + ", tuned=" + tuned
                + ", rechargeStart=" + rechargeStart + ", rechargeTicks=" + rechargeTicks + ", rechargers=" + rechargers + ", formedAt=" + formedAt
                + ", origin=" + Arrays.toString(origin) + ", flows=" + Arrays.toString(flows) + ", full=" + full + "]";
    }
}
