package io.github.matheusanbs.wirelessautomate.mixin;

import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraftforge.fml.loading.LoadingModList;
import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

/**
 * Plugin dos configs de mixin do mod ({@code wirelessautomate*.mixins.json}, ver o {@code build.gradle}). Um
 * mixin num pacote {@code compat.<mod>.mixin} só é aplicado com aquele mod opcional carregado (pelo
 * {@link LoadingModList}, que já existe quando as classes alvo carregam); os outros (como os de
 * {@code client.mixin}, sobre classes do Minecraft) sempre. Assim o mod carrega sem o mod alvo, e os
 * injetores com {@code require = 0} deixam passar uma versão dele em que o alvo mudou.
 *
 * <p>Fica fora de qualquer pacote de mixins: o Mixin não deixa carregar como classe comum o que está num.
 */
public final class WaMixinPlugin implements IMixinConfigPlugin {
    private static final String BASE = "io.github.matheusanbs.wirelessautomate.";

    /** Pacote dos mixins de cada mod opcional → o id do mod no Forge. */
    private static final Map<String, String> OPTIONAL_MODS = Map.of(
            BASE + "compat.arsnouveau.mixin.", "ars_nouveau");

    /** O mod exigido pelo mixin, ou {@code null} se ele vale sempre. */
    static String requiredMod(String mixinClassName) {
        for (Map.Entry<String, String> entry : OPTIONAL_MODS.entrySet()) {
            if (mixinClassName.startsWith(entry.getKey())) {
                return entry.getValue();
            }
        }
        return null;
    }

    private static boolean loaded(String modId) {
        LoadingModList mods = LoadingModList.get();
        return mods != null && mods.getModFileById(modId) != null;
    }

    @Override
    public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
        String mod = requiredMod(mixinClassName);
        return mod == null || loaded(mod);
    }

    @Override
    public void onLoad(String mixinPackage) {
    }

    @Override
    public String getRefMapperConfig() {
        return null; // o do config (wirelessautomate.refmap.json)
    }

    @Override
    public void acceptTargets(Set<String> myTargets, Set<String> otherTargets) {
    }

    @Override
    public List<String> getMixins() {
        return null;
    }

    @Override
    public void preApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {
    }

    @Override
    public void postApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {
    }
}
