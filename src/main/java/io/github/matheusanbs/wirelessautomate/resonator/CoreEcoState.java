package io.github.matheusanbs.wirelessautomate.resonator;

import io.github.matheusanbs.wirelessautomate.Config;
import io.github.matheusanbs.wirelessautomate.block.RouterTier;
import io.github.matheusanbs.wirelessautomate.registry.ModDataComponents;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

/**
 * O Eco Ressonante de um Núcleo (chave {@code eco} no NBT, componente {@code resonator_eco} no item): só o Ultimate o
 * usa, e só um Afinado Ultimate rachado o alimenta, consumido na hora. As contas são do {@link CoreEco}; o Núcleo de
 * outro tier recusa o cristal e não aplica o fator. Nada aqui roda por tick: o fator é lido na geração e no buffer.
 */
public final class CoreEcoState {
    static final String KEY = "eco";

    private final ResonanceCoreBlockEntity core;
    private long eco;

    CoreEcoState(ResonanceCoreBlockEntity core) {
        this.core = core;
    }

    /** O Eco guardado (pode passar do {@code ecoMax} se a config baixou o teto; o fator usa o que vale). */
    public long value() {
        return eco;
    }

    /** Afinado Ultimate rachado: o único cristal que vira Eco. */
    public static boolean isFuel(ItemStack stack) {
        return stack.getItem() instanceof SignalCrystalItem crystal && crystal.tunedTier() == RouterTier.ULTIMATE
                && SignalCrystalItem.isCracked(stack);
    }

    private boolean ultimate() {
        return core.tier() == RouterTier.ULTIMATE;
    }

    /** Quantos cristais da pilha o Núcleo queimaria agora: 0 fora do Ultimate, fora do combustível ou no teto. */
    public int room(ItemStack stack) {
        if (!ultimate() || stack.isEmpty() || !isFuel(stack)) {
            return 0;
        }
        return CoreEco.room(eco, Config.ecoMax(), stack.getCount());
    }

    /**
     * Queima no Eco os cristais da pilha que cabem ({@link #room}) e devolve quantos, sem mexer na pilha; o slot do
     * menu e a Porta de Cristais entram por aqui.
     */
    public int burn(ItemStack stack, boolean simulate) {
        int burned = room(stack);
        if (!simulate && burned > 0) {
            eco = CoreEco.add(eco, burned);
            core.setChanged();
        }
        return burned;
    }

    /** {@code base} no fator do Eco, só no Ultimate. */
    long apply(long base) {
        return ultimate() ? CoreEco.apply(base, eco, Config.ecoBonus(), Config.ecoMax()) : base;
    }

    /** O fator agora (1 fora do Ultimate), para a tela e o comando. */
    public double factor() {
        return ultimate() ? CoreEco.factor(eco, Config.ecoBonus(), Config.ecoMax()) : 1;
    }

    void save(CompoundTag tag) {
        if (eco > 0) {
            tag.putLong(KEY, eco);
        }
    }

    void load(CompoundTag tag) {
        eco = Math.max(0, tag.getLong(KEY));
    }

    void collectComponents(DataComponentMap.Builder components) {
        if (eco > 0) {
            components.set(ModDataComponents.RESONATOR_ECO.get(), eco);
        }
    }

    /** O Eco do item colocado ({@code resonator_eco}); sem o componente, 0. */
    void restore(@Nullable Long value) {
        eco = value == null ? 0 : Math.max(0, value);
    }

    /** O NBT copiado para o item já leva o Eco no componente: a chave sai, para não ir duas vezes. */
    static void removeFrom(CompoundTag tag) {
        tag.remove(KEY);
    }
}
