package io.github.matheusanbs.wirelessautomate.client.dev.vitrine;

import java.util.Arrays;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.Item;

/**
 * As telas do AE2 vistas só pelo que o Minecraft expõe (sem tipos do AE2): pelo nome simples da classe, pelos espaços do
 * menu e pelos botões da tela. O terminal, a tela de quantidade, a de confirmação e a de status do crafting.
 */
final class Ae2Telas {
    private Ae2Telas() {
    }

    /** A tela aberta é a do AE2 de nome simples {@code nome} (ou herda de uma classe com esse nome). */
    static boolean aberta(String nome) {
        Screen tela = Minecraft.getInstance().screen;
        for (Class<?> c = tela == null ? null : tela.getClass(); c != null; c = c.getSuperclass()) {
            if (c.getSimpleName().equals(nome)) {
                return true;
            }
        }
        return false;
    }

    /** O que o espaço mostra: o item dele ou, nos espaços de lista do AE2, o item de exibição. */
    private static boolean mostra(Slot slot, Item item) {
        if (slot.getItem().is(item)) {
            return true;
        }
        try {
            return ((net.minecraft.world.item.ItemStack) slot.getClass().getMethod("getDisplayStack").invoke(slot)).is(item);
        } catch (ReflectiveOperationException | ClassCastException e) {
            return false;
        }
    }

    /** O centro (coordenadas da GUI) do espaço da tela aberta que mostra {@code item}. */
    static int[] espaco(Item item) {
        var tela = (AbstractContainerScreen<?>) Minecraft.getInstance().screen;
        for (Slot slot : tela.getMenu().slots) {
            if (mostra(slot, item)) {
                return new int[] {tela.getGuiLeft() + slot.x + 8, tela.getGuiTop() + slot.y + 8};
            }
        }
        throw new IllegalStateException("nenhum espaço com " + item + " em " + tela.getClass().getSimpleName());
    }

    /** A tela aberta tem um espaço que mostra {@code item}. */
    static boolean temEspaco(Item item) {
        return Minecraft.getInstance().screen instanceof AbstractContainerScreen<?> tela
                && tela.getMenu().slots.stream().anyMatch(slot -> mostra(slot, item));
    }

    /** O centro do botão da tela aberta cujo texto é um dos {@code textos} (sem diferenciar maiúsculas). */
    static int[] botao(String... textos) {
        AbstractWidget widget = achar(textos);
        if (widget == null) {
            throw new IllegalStateException("sem botão " + Arrays.toString(textos));
        }
        return new int[] {widget.getX() + widget.getWidth() / 2, widget.getY() + widget.getHeight() / 2};
    }

    static boolean temBotao(String... textos) {
        return achar(textos) != null;
    }

    private static AbstractWidget achar(String... textos) {
        for (var filho : Minecraft.getInstance().screen.children()) {
            if (filho instanceof AbstractWidget widget && widget.visible && widget.active
                    && Arrays.stream(textos).anyMatch(t -> widget.getMessage().getString().equalsIgnoreCase(t))) {
                return widget;
            }
        }
        return null;
    }
}
