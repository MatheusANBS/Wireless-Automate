package io.github.matheusanbs.wirelessautomate.resonator;

import io.github.matheusanbs.wirelessautomate.Config;
import io.github.matheusanbs.wirelessautomate.block.RouterTier;
import io.github.matheusanbs.wirelessautomate.registry.ModDataComponents;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import org.jetbrains.annotations.Nullable;

/**
 * Cristal de Sinal, Cristal Lapidado e os Cristais Afinados (Lapidados com o tier gravado no item, um por tier). A
 * carga fica no componente {@code crystal_charge} (sem ele, cheio); com carga 0 o cristal fica Opaco: o mesmo item,
 * com nome e textura de Opaco, que o Núcleo recarrega. A integridade fica no {@code crystal_integrity} (sem ele,
 * cheia): cada recarga gasta 1, e com 0 o cristal está rachado, não gera e só volta refeito na bancada.
 */
public class SignalCrystalItem extends Item {
    /** A largura cheia da barra de durabilidade do vanilla. */
    private static final float BAR_WIDTH = 13f;
    /** O coral da carga. */
    private static final int BAR_COLOR = 0xF0603E;

    private final boolean cut;
    @Nullable
    private final RouterTier tuned;

    public SignalCrystalItem(boolean cut, Properties properties) {
        this(cut, null, properties);
    }

    public SignalCrystalItem(boolean cut, @Nullable RouterTier tuned, Properties properties) {
        super(properties);
        this.cut = cut;
        this.tuned = tuned;
    }

    public boolean isCut() {
        return cut;
    }

    /** O tier para o qual o cristal foi afinado; {@code null} no Cristal de Sinal e no Lapidado. */
    @Nullable
    public RouterTier tunedTier() {
        return tuned;
    }

    /** O tipo para o {@link CoreMath#yieldQuarters}: {@code KIND_SIGNAL}, {@code KIND_CUT} ou {@code KIND_TUNED}. */
    public int kind() {
        if (tuned != null) {
            return CoreMath.KIND_TUNED;
        }
        return cut ? CoreMath.KIND_CUT : CoreMath.KIND_SIGNAL;
    }

    /** Quanto o cristal rende numa câmara do Núcleo daquele tier, em quartos (4 é o rendimento 1). */
    public int yieldQuarters(RouterTier coreTier) {
        return CoreMath.yieldQuarters(kind(), tuned == null ? -1 : tuned.ordinal(), coreTier.ordinal());
    }

    /** A integridade do cristal, limitada ao máximo da config; sem o componente, cheia. */
    public static int integrity(ItemStack stack) {
        int max = Config.crystalIntegrity();
        Integer valor = stack.get(ModDataComponents.CRYSTAL_INTEGRITY.get());
        return valor == null ? max : CrystalIntegrity.clamp(valor, max);
    }

    /** Cheia tira o componente, para o cristal empilhar com o novo, como a carga. */
    public static void setIntegrity(ItemStack stack, int integrity) {
        int max = Config.crystalIntegrity();
        int valor = CrystalIntegrity.clamp(integrity, max);
        if (valor == max) {
            stack.remove(ModDataComponents.CRYSTAL_INTEGRITY.get());
        } else {
            stack.set(ModDataComponents.CRYSTAL_INTEGRITY.get(), valor);
        }
    }

    /** Rachado: um cristal com integridade 0; sem o componente, nunca. */
    public static boolean isCracked(ItemStack stack) {
        return stack.getItem() instanceof SignalCrystalItem
                && stack.has(ModDataComponents.CRYSTAL_INTEGRITY.get())
                && CrystalIntegrity.isCracked(integrity(stack));
    }

    public static int charge(ItemStack stack) {
        return stack.getOrDefault(ModDataComponents.CRYSTAL_CHARGE.get(), CrystalCharge.FULL);
    }

    /** Cheio tira o componente, para o cristal recarregado empilhar com o novo. */
    public static void setCharge(ItemStack stack, int charge) {
        int valor = CrystalCharge.clamp(charge);
        if (valor == CrystalCharge.FULL) {
            stack.remove(ModDataComponents.CRYSTAL_CHARGE.get());
        } else {
            stack.set(ModDataComponents.CRYSTAL_CHARGE.get(), valor);
        }
    }

    public static boolean isDull(ItemStack stack) {
        return stack.getItem() instanceof SignalCrystalItem && CrystalCharge.isDull(charge(stack));
    }

    /** O Opaco que o Núcleo recarrega: carga 0 e sem rachadura. */
    public static boolean isRechargeable(ItemStack stack) {
        return isDull(stack) && !isCracked(stack);
    }

    public static boolean isFull(ItemStack stack) {
        return stack.getItem() instanceof SignalCrystalItem && charge(stack) == CrystalCharge.FULL;
    }

    /**
     * A barra só no cristal gasto e com carga: o cheio não tem, o Opaco só mostra a recarga dentro do Núcleo e o
     * rachado não tem.
     */
    @Override
    public boolean isBarVisible(ItemStack stack) {
        int carga = charge(stack);
        return carga > 0 && carga < CrystalCharge.FULL && !isCracked(stack);
    }

    @Override
    public int getBarWidth(ItemStack stack) {
        return Math.round(BAR_WIDTH * charge(stack) / CrystalCharge.FULL);
    }

    @Override
    public int getBarColor(ItemStack stack) {
        return BAR_COLOR;
    }

    @Override
    public Component getName(ItemStack stack) {
        if (isCracked(stack)) {
            return Component.translatable(getDescriptionId() + ".cracked");
        }
        return isDull(stack) ? Component.translatable(getDescriptionId() + ".dull") : super.getName(stack);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> lines, TooltipFlag flag) {
        if (tuned != null) {
            lines.add(Component.translatable("item.wirelessautomate.signal_crystal.tooltip.tuned",
                    Component.translatable(tuned.translationKey())).withStyle(ChatFormatting.GRAY));
        }
        if (isCracked(stack)) {
            lines.add(Component.translatable("item.wirelessautomate.signal_crystal.tooltip.cracked",
                    Config.recastCrystals()).withStyle(ChatFormatting.RED));
            return;
        }
        int max = Config.crystalIntegrity();
        int integridade = integrity(stack);
        if (integridade < max) {
            lines.add(Component.translatable("item.wirelessautomate.signal_crystal.tooltip.integrity",
                    integridade, max).withStyle(ChatFormatting.GRAY));
        }
        int carga = charge(stack);
        if (CrystalCharge.isDull(carga)) {
            lines.add(Component.translatable("item.wirelessautomate.signal_crystal.tooltip.dull")
                    .withStyle(ChatFormatting.GRAY));
        } else {
            lines.add(Component.translatable("item.wirelessautomate.signal_crystal.tooltip.charge",
                    CrystalCharge.percent(carga)).withStyle(ChatFormatting.GRAY));
        }
        if (cut && tuned == null) {
            lines.add(Component.translatable("item.wirelessautomate.cut_signal_crystal.tooltip.yield")
                    .withStyle(ChatFormatting.GRAY));
        }
    }
}
