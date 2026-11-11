package io.github.matheusanbs.wirelessautomate.compat.appmek;

import appeng.api.stacks.AEKey;
import me.ramidzkh.mekae2.ae2.MekanismKey;
import mekanism.api.chemical.ChemicalStack;
import org.jetbrains.annotations.Nullable;

/**
 * Os químicos da Doca na rede ME: a chave {@link MekanismKey} do Applied Mekanistics, um {@link AEKey} como o
 * {@code AEItemKey}. Só carrega com o AE2, o Mekanism e o Applied Mekanistics, pela ponte {@code compat/ae2/Ae2Chemicals}
 * (que confere o {@code LOADED} dela); o químico entra e sai como {@code Object} ({@link ChemicalStack}).
 */
public final class AppMekDock {
    private AppMekDock() {
    }

    /** O químico de {@code amount} mB da chave {@code key}, ou {@code null} se ela não é de químico. */
    public static @Nullable Object chemical(AEKey key, long amount) {
        return key instanceof MekanismKey chemical ? chemical.withAmount(amount) : null;
    }

    /**
     * A chave do químico {@code stack}: {@code last} se ela já é a dele (a volta insere a mesma saída várias vezes, e a
     * chave guarda uma cópia do químico), senão uma nova; {@code null} com o químico vazio.
     */
    public static @Nullable AEKey key(Object stack, @Nullable AEKey last) {
        ChemicalStack chemical = (ChemicalStack) stack;
        if (last instanceof MekanismKey known && known.getStack().getChemical() == chemical.getChemical()) {
            return last;
        }
        return MekanismKey.of(chemical);
    }
}
