package io.github.matheusanbs.wirelessautomate.client.guide;

import com.mojang.blaze3d.platform.InputConstants;
import io.github.matheusanbs.wirelessautomate.WirelessAutomate;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;
import net.minecraft.ChatFormatting;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.event.ScreenEvent;
import net.neoforged.neoforge.client.settings.KeyConflictContext;
import net.neoforged.neoforge.event.entity.player.ItemTooltipEvent;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.glfw.GLFW;

/**
 * A tecla "Abrir no livro" (G): com uma tela aberta, abre o livro na página do item sob o mouse (o slot de uma tela
 * de contêiner ou o item que o JEI mostra, pela ponte {@link #jeiHovered}). Itens com página ganham na dica a linha
 * que diz a tecla. Só custa quando a tecla é apertada com uma tela aberta; a dica faz uma consulta num mapa.
 */
@EventBusSubscriber(modid = WirelessAutomate.MODID, value = Dist.CLIENT)
public final class GuideKeys {
    public static final KeyMapping OPEN_GUIDE = new KeyMapping("key.wirelessautomate.open_guide",
            KeyConflictContext.GUI, InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_G, "key.categories.wirelessautomate");

    /** O item que o JEI mostra sob o mouse; o plugin do JEI troca isto (sem o JEI, nunca há item). */
    public static volatile Supplier<ItemStack> jeiHovered = () -> ItemStack.EMPTY;

    /** Se a busca do JEI tem o foco do teclado (o plugin troca isto); a letra segue para o campo dele. */
    public static volatile BooleanSupplier jeiTyping = () -> false;

    /** A linha da dica e o nome da tecla de quando foi montada; refeita só quando o jogador troca a tecla. */
    private static @Nullable Component hint;
    private static String hintKey = "";

    private GuideKeys() {
    }

    /** Desliga as pontes do JEI (ao sair do mundo), para elas não segurarem o runtime dele até o próximo. */
    public static void clearJei() {
        jeiHovered = () -> ItemStack.EMPTY;
        jeiTyping = () -> false;
    }

    @SubscribeEvent
    static void onKey(ScreenEvent.KeyPressed.Pre event) {
        Screen screen = event.getScreen();
        if (screen instanceof GuideScreen || typing(screen) || jeiTyping.getAsBoolean()
                || !OPEN_GUIDE.isActiveAndMatches(InputConstants.getKey(event.getKeyCode(), event.getScanCode()))) {
            return;
        }
        ItemStack stack = ItemStack.EMPTY;
        if (screen instanceof AbstractContainerScreen<?> container) {
            Slot slot = container.getSlotUnderMouse();
            if (slot != null) {
                stack = slot.getItem();
            }
        }
        if (stack.isEmpty()) {
            stack = jeiHovered.get();
        }
        if (stack.isEmpty()) {
            return;
        }
        String page = GuidePages.pageForItem(BuiltInRegistries.ITEM.getKey(stack.getItem()).toString());
        if (page != null) {
            event.setCanceled(true);
            // o GLFW entrega o caractere da mesma tecla logo depois, na mesma rodada de eventos, para a tela aberta:
            // abrindo o livro só na fila de tarefas, a letra fica com a tela de antes e não cai na busca do livro
            Minecraft mc = Minecraft.getInstance();
            mc.tell(() -> {
                if (mc.screen == screen) {
                    GuideScreen.open(page, screen);
                }
            });
        }
    }

    /**
     * Algum campo de texto da tela tem o foco? Nem todo campo vira o filho com foco da tela: a busca do inventário
     * criativo só liga o foco dela ({@code searchBox.setFocused(true)}), por isso a busca olha todos os filhos.
     */
    private static boolean typing(Screen screen) {
        if (screen.getFocused() instanceof EditBox box && box.isFocused()) {
            return true;
        }
        for (GuiEventListener child : screen.children()) {
            if (child instanceof EditBox box && box.isFocused() && box.isVisible()) {
                return true;
            }
        }
        return false;
    }

    @SubscribeEvent
    static void onTooltip(ItemTooltipEvent event) {
        ItemStack stack = event.getItemStack();
        if (stack.isEmpty() || OPEN_GUIDE.isUnbound()) {
            return;
        }
        if (Minecraft.getInstance().isSameThread()) {
            GuidePages.ensure();
        }
        if (GuidePages.pageForItem(BuiltInRegistries.ITEM.getKey(stack.getItem()).toString()) == null) {
            return;
        }
        String key = OPEN_GUIDE.saveString();
        if (hint == null || !key.equals(hintKey)) {
            hintKey = key;
            hint = Component.translatable("tooltip.wirelessautomate.open_guide", OPEN_GUIDE.getTranslatedKeyMessage())
                    .withStyle(ChatFormatting.DARK_GRAY);
        }
        event.getToolTip().add(hint);
    }

    /** Registro da tecla, no barramento do mod. */
    // bus explícito: o FML já o deduz do evento e marcou o atributo para remoção, mas aqui ele deixa claro
    @SuppressWarnings("removal")
    @EventBusSubscriber(modid = WirelessAutomate.MODID, bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
    public static final class Registration {
        private Registration() {
        }

        @SubscribeEvent
        static void register(RegisterKeyMappingsEvent event) {
            event.register(OPEN_GUIDE);
        }
    }
}
