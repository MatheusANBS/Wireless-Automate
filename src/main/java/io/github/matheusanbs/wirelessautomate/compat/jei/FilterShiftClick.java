package io.github.matheusanbs.wirelessautomate.compat.jei;

import io.github.matheusanbs.wirelessautomate.client.FilterScreen;
import io.github.matheusanbs.wirelessautomate.filter.FilterEntry;
import java.util.Optional;
import org.jetbrains.annotations.Nullable;
import mezz.jei.api.ingredients.ITypedIngredient;
import mezz.jei.api.runtime.IJeiRuntime;
import net.minecraft.client.gui.screens.Screen;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.EventPriority;

/**
 * Shift + clique esquerdo num ingrediente da lista do JEI (ou dos favoritos) com a tela de filtro aberta
 * acrescenta a entrada, como o {@code quickMove} do JEI 19.28+ no main. Porte 1.20.1: o JEI 15.20 não tem o
 * {@code quickMove}; ele escuta o {@code MouseButtonPressed.Pre} com prioridade normal, sem ver eventos
 * cancelados, e com o modo trapaça daria a pilha no Shift + clique. Aqui o aperto é visto antes, com
 * prioridade alta: com a tela de filtro, o botão esquerdo, o Shift, o cursor vazio e um ingrediente que
 * serve ao filtro sob o mouse, o aperto é guardado e cancelado; a soltura do mesmo botão acrescenta a
 * entrada e também é cancelada, então o JEI não vê nenhum dos dois. Ingrediente que não serve fica com o
 * JEI. Diferença do main: o atalho é fixo em Shift + esquerdo (lá era a tecla configurável do JEI).
 */
final class FilterShiftClick {
    private static @Nullable IJeiRuntime runtime;
    private static boolean registered;
    /** A entrada guardada no aperto, até a soltura do mesmo botão. */
    private static @Nullable FilterEntry pending;
    private static @Nullable Screen pendingScreen;

    private FilterShiftClick() {
    }

    /** Runtime do JEI pronto: guarda e registra os ouvintes (uma vez só). */
    static void runtimeAvailable(IJeiRuntime jeiRuntime) {
        runtime = jeiRuntime;
        if (!registered) {
            registered = true;
            MinecraftForge.EVENT_BUS.addListener(EventPriority.HIGH, false, ScreenEvent.MouseButtonPressed.Pre.class,
                    event -> {
                        if (press(event.getScreen(), event.getButton(), Screen.hasShiftDown())) {
                            event.setCanceled(true);
                        }
                    });
            MinecraftForge.EVENT_BUS.addListener(EventPriority.HIGH, false, ScreenEvent.MouseButtonReleased.Pre.class,
                    event -> {
                        if (release(event.getScreen(), event.getButton())) {
                            event.setCanceled(true);
                        }
                    });
        }
    }

    static void runtimeUnavailable() {
        runtime = null;
        pending = null;
        pendingScreen = null;
    }

    /**
     * O aperto: verdadeiro (e a entrada guardada) se é Shift + esquerdo na tela de filtro, com o cursor vazio
     * e um ingrediente do JEI que serve ao filtro sob o mouse. O {@code shift} vem do teclado
     * ({@link Screen#hasShiftDown()}); o e2e chama este método direto, por reflexão, com o Shift.
     */
    static boolean press(@Nullable Screen screen, int button, boolean shift) {
        pending = null;
        pendingScreen = null;
        if (button != 0 || !shift || !(screen instanceof FilterScreen filter)
                || !filter.getMenu().getCarried().isEmpty()) {
            return false;
        }
        Optional<FilterEntry> entry = ingredientUnderMouse().flatMap(filter::ghostEntry);
        if (entry.isEmpty()) {
            return false;
        }
        pending = entry.get();
        pendingScreen = screen;
        return true;
    }

    /** A soltura do mesmo botão depois de um aperto guardado: acrescenta a entrada (se a tela é a mesma). */
    static boolean release(@Nullable Screen screen, int button) {
        if (button != 0 || pending == null) {
            return false;
        }
        FilterEntry entry = pending;
        Screen pressed = pendingScreen;
        pending = null;
        pendingScreen = null;
        if (screen == pressed && screen instanceof FilterScreen filter) {
            filter.addGhost(entry);
        }
        return true;
    }

    /** O ingrediente sob o mouse na lista do JEI ou nos favoritos. */
    private static Optional<Object> ingredientUnderMouse() {
        IJeiRuntime jei = runtime;
        if (jei == null) {
            return Optional.empty();
        }
        Optional<ITypedIngredient<?>> under = jei.getIngredientListOverlay().getIngredientUnderMouse();
        if (under.isEmpty()) {
            under = jei.getBookmarkOverlay().getIngredientUnderMouse();
        }
        return under.map(ITypedIngredient::getIngredient);
    }
}
