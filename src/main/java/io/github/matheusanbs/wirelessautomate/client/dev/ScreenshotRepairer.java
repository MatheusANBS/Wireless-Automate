package io.github.matheusanbs.wirelessautomate.client.dev;

import static io.github.matheusanbs.wirelessautomate.client.dev.DevScreenshot.*;

import com.mojang.blaze3d.platform.Window;
import io.github.matheusanbs.wirelessautomate.GuiTheme;
import io.github.matheusanbs.wirelessautomate.block.RouterTier;
import io.github.matheusanbs.wirelessautomate.client.GuiThemes;
import io.github.matheusanbs.wirelessautomate.client.repairer.RepairerScreen;
import io.github.matheusanbs.wirelessautomate.menu.RepairerMenu;
import io.github.matheusanbs.wirelessautomate.packet.RepairerStatePayload;
import io.github.matheusanbs.wirelessautomate.registry.ModItems;
import io.github.matheusanbs.wirelessautomate.repairer.RepairMath;
import io.github.matheusanbs.wirelessautomate.repairer.RepairerSlots;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/**
 * As capturas da tela do Reparador sem mundo: parado, reparando (as linhas a meio caminho, pelo relógio 0 da tela de
 * título), sem energia e a saída cheia, alternando os temas. {@code WA_SCREENSHOT_ONLY=reparador} tira só estas.
 */
final class ScreenshotRepairer {
    private static final long CAPACITY = 4_294_967_296L;
    private static final int DURATION = 140;

    private ScreenshotRepairer() {
    }

    /** Desenhada no lugar das outras a partir do primeiro passo do Reparador. */
    static RepairerScreen repairerScreen;

    /**
     * Um estado: os rachados das entradas ("s" Sinal, "l" Lapidado, "a" Afinado Elite, "-" vazia), o fim de cada
     * linha em ticks a partir do relógio 0 (0 parada, -1 travada), o estoque, as saídas cheias e a energia.
     */
    private record Sample(String inputs, int[] ends, int feed, int outputs, long energy, long cost0) {
    }

    static List<Step> repairerSteps() {
        Sample parado = new Sample("--------", new int[8], 16, 2, 20_000, 0);
        Sample reparando = new Sample("slas----", new int[] {70, 100, 20, 120, 0, 0, 0, 0}, 4, 1, 1_500_000, 0);
        Sample semEnergia = new Sample("sla-----", new int[8], 16, 0, 0, 864);
        Sample cheia = new Sample("sl------", new int[] {-1, 40, 0, 0, 0, 0, 0, 0}, 8, 8, 320_000, 0);
        return List.of(
                new Step(() -> {
                    themeBefore = GuiThemes.current();
                    show(parado, GuiTheme.LIGHT);
                }, "reparador-1-parado"),
                new Step(() -> show(reparando, GuiTheme.DARK), "reparador-2-reparando"),
                new Step(() -> show(semEnergia, GuiTheme.LIGHT), "reparador-3-sem-energia"),
                new Step(() -> {
                    show(cheia, GuiTheme.DARK);
                    int[] center = repairerScreen.repairerSlotCenter(0);
                    mouseX = center[0];
                    mouseY = center[1];
                }, "reparador-4-saida-cheia"),
                new Step(() -> {
                    GuiThemes.preview(themeBefore);
                    show(parado, themeBefore);
                }, "reparador-5-tema-de-antes"));
    }

    private static void show(Sample sample, GuiTheme theme) {
        mouseX = mouseY = -1;
        GuiThemes.preview(theme);
        Inventory inventory = new Inventory(null);
        inventory.setItem(0, new ItemStack(ModItems.SIGNAL_CRYSTAL.get(), 12));
        RepairerMenu menu = new RepairerMenu(0, inventory, BlockPos.ZERO);
        long[] ends = new long[RepairMath.LINES];
        boolean[] blocked = new boolean[RepairMath.LINES];
        for (int i = 0; i < RepairMath.LINES; i++) {
            char kind = sample.inputs().charAt(i);
            if (kind != '-') {
                menu.getSlot(i).set(E2eResonatorScreenSteps.cracked(new ItemStack(item(kind))));
            }
            ends[i] = sample.ends()[i] < 0 ? 1 : sample.ends()[i];
            blocked[i] = sample.ends()[i] < 0;
        }
        if (sample.feed() > 0) {
            menu.getSlot(RepairerSlots.FEED).set(new ItemStack(ModItems.SIGNAL_CRYSTAL.get(), sample.feed()));
        }
        for (int i = 0; i < sample.outputs(); i++) {
            menu.getSlot(RepairerSlots.OUTPUT_FIRST + i).set(new ItemStack(i % 2 == 0
                    ? ModItems.SIGNAL_CRYSTAL.get() : ModItems.CUT_SIGNAL_CRYSTAL.get()));
        }
        menu.apply(new RepairerStatePayload(0, sample.energy(), CAPACITY, ends, blocked, DURATION, 4, sample.cost0()));
        repairerScreen = new RepairerScreen(menu, inventory, Component.translatable("container.wirelessautomate.repairer"));
        // já no tamanho da janela: os ganchos dos centros dependem da posição da tela
        Window window = Minecraft.getInstance().getWindow();
        repairerScreen.init(Minecraft.getInstance(), window.getGuiScaledWidth(), window.getGuiScaledHeight());
    }

    private static Item item(char kind) {
        return switch (kind) {
            case 'l' -> ModItems.CUT_SIGNAL_CRYSTAL.get();
            case 'a' -> ModItems.TUNED_CRYSTALS.get(RouterTier.ELITE).get();
            default -> ModItems.SIGNAL_CRYSTAL.get();
        };
    }
}
