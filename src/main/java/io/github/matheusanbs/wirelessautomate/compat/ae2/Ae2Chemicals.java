package io.github.matheusanbs.wirelessautomate.compat.ae2;

import appeng.api.stacks.AEKey;
import io.github.matheusanbs.wirelessautomate.compat.appmek.AppMekDock;
import io.github.matheusanbs.wirelessautomate.network.Chemicals;
import net.neoforged.fml.ModList;
import org.jetbrains.annotations.Nullable;

/**
 * Ponte da Doca do AE2 para os químicos do Applied Mekanistics, sem tipos do Mekanism: só esta classe, fora do
 * {@code compat/appmek}, conhece o {@link AppMekDock}, e só o chama com {@link #LOADED}. O químico passa como
 * {@code Object} ({@code ChemicalStack}), como na ponte {@code network/Chemicals}.
 */
public final class Ae2Chemicals {
    /** O Mekanism e o Applied Mekanistics estão instalados: a rede ME guarda químicos e o provedor os empurra. */
    public static final boolean LOADED = Chemicals.LOADED && ModList.get() != null
            && ModList.get().isLoaded("appmek");

    private Ae2Chemicals() {
    }

    /** O químico ({@code ChemicalStack}) de {@code amount} mB da chave {@code key}; {@code null} se não é químico. */
    public static @Nullable Object chemical(AEKey key, long amount) {
        return LOADED ? AppMekDock.chemical(key, amount) : null;
    }

    /** A chave do químico {@code stack} ({@code last} se é a mesma); {@code null} sem o addon ou com ele vazio. */
    public static @Nullable AEKey key(Object stack, @Nullable AEKey last) {
        return LOADED ? AppMekDock.key(stack, last) : null;
    }
}
