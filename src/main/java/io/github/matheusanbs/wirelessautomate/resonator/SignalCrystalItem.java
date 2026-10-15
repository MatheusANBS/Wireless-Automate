package io.github.matheusanbs.wirelessautomate.resonator;

import io.github.matheusanbs.wirelessautomate.registry.ModDataComponents;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

/**
 * Cristal de Sinal e Cristal Lapidado. A carga fica no componente {@code crystal_charge} (sem ele, cheio);
 * com carga 0 o cristal fica Opaco: o mesmo item, com nome e textura de Opaco, que o Núcleo recarrega.
 */
public class SignalCrystalItem extends Item {
    /** A largura cheia da barra de durabilidade do vanilla. */
    private static final float BAR_WIDTH = 13f;
    /** O coral da carga. */
    private static final int BAR_COLOR = 0xF0603E;

    private final boolean cut;

    public SignalCrystalItem(boolean cut, Properties properties) {
        super(properties);
        this.cut = cut;
    }

    public boolean isCut() {
        return cut;
    }

    /** Quanto o cristal rende no Núcleo: o Lapidado, o dobro. */
    public int yield() {
        return cut ? 2 : 1;
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

    public static boolean isFull(ItemStack stack) {
        return stack.getItem() instanceof SignalCrystalItem && charge(stack) == CrystalCharge.FULL;
    }

    /** A barra só no cristal gasto e com carga: o cheio não tem, e o Opaco só mostra a recarga dentro do Núcleo. */
    @Override
    public boolean isBarVisible(ItemStack stack) {
        int carga = charge(stack);
        return carga > 0 && carga < CrystalCharge.FULL;
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
        return isDull(stack) ? Component.translatable(getDescriptionId() + ".dull") : super.getName(stack);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> lines, TooltipFlag flag) {
        int carga = charge(stack);
        if (CrystalCharge.isDull(carga)) {
            lines.add(Component.translatable("item.wirelessautomate.signal_crystal.tooltip.dull")
                    .withStyle(ChatFormatting.GRAY));
        } else {
            lines.add(Component.translatable("item.wirelessautomate.signal_crystal.tooltip.charge",
                    CrystalCharge.percent(carga)).withStyle(ChatFormatting.GRAY));
        }
        if (cut) {
            lines.add(Component.translatable("item.wirelessautomate.cut_signal_crystal.tooltip.yield")
                    .withStyle(ChatFormatting.GRAY));
        }
    }
}
