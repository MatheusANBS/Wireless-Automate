package io.github.matheusanbs.wirelessautomate.compat.sodium;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.neoforged.fml.ModList;
import org.jetbrains.annotations.Nullable;

/**
 * Ponte para o {@code SpriteUtil} público do Sodium (0.6.9 ou mais novo). O Sodium só anima a textura de um item ou
 * fluido que foi marcada como ativa no quadro; a imagem da Tela é desenhada de vez em quando, num framebuffer próprio,
 * então o Ícone animado parava. Quem desenha por conta própria deve marcar o sprite a cada quadro em que ele é usado.
 * Por reflexão (sem {@code compileOnly}): sem o Sodium, ou com uma versão sem a API, {@link #AVAILABLE} fica falso e
 * nada mais roda. Só na thread de render.
 */
public final class SodiumSprites {
    private static final @Nullable MethodHandle MARK = find();
    public static final boolean AVAILABLE = MARK != null;

    private SodiumSprites() {
    }

    private static @Nullable MethodHandle find() {
        if (ModList.get() == null || !ModList.get().isLoaded("sodium")) {
            return null;
        }
        try {
            Class<?> util = Class.forName("net.caffeinemc.mods.sodium.api.texture.SpriteUtil");
            Object instance = util.getField("INSTANCE").get(null);
            return MethodHandles.publicLookup()
                    .findVirtual(util, "markSpriteActive", MethodType.methodType(void.class, TextureAtlasSprite.class))
                    .bindTo(instance);
        } catch (ReflectiveOperationException | LinkageError e) {
            return null;
        }
    }

    /** Marca o sprite como visível neste quadro. */
    public static void mark(TextureAtlasSprite sprite) {
        if (MARK == null) {
            return;
        }
        try {
            MARK.invoke(sprite);
        } catch (Throwable e) {
            // a API do Sodium mudou: a animação volta ao comportamento de antes, sem derrubar o quadro
        }
    }
}
