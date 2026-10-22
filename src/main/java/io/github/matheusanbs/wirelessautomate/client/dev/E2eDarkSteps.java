package io.github.matheusanbs.wirelessautomate.client.dev;

import static io.github.matheusanbs.wirelessautomate.client.dev.DevEndToEnd.*;
import static io.github.matheusanbs.wirelessautomate.client.dev.E2eRouterSteps.*;
import static io.github.matheusanbs.wirelessautomate.client.dev.E2eShowcaseWorld.*;
import static io.github.matheusanbs.wirelessautomate.client.dev.E2eSteps.*;
import static io.github.matheusanbs.wirelessautomate.client.dev.E2eStorageSteps.*;
import static io.github.matheusanbs.wirelessautomate.client.dev.E2eUi.*;
import static io.github.matheusanbs.wirelessautomate.client.dev.E2eWorld.*;

import io.github.matheusanbs.wirelessautomate.ClientConfig;
import io.github.matheusanbs.wirelessautomate.GuiTheme;
import io.github.matheusanbs.wirelessautomate.client.filter.FilterScreen;
import io.github.matheusanbs.wirelessautomate.client.linker.LinkerScreen;
import io.github.matheusanbs.wirelessautomate.client.recipe.RecipeEditorScreen;
import io.github.matheusanbs.wirelessautomate.client.resonator.screen.ResonatorScreen;
import io.github.matheusanbs.wirelessautomate.client.router.RouterScreen;
import io.github.matheusanbs.wirelessautomate.client.storage.StorageListScreen;
import io.github.matheusanbs.wirelessautomate.client.storage.StorageScalarScreen;
import io.github.matheusanbs.wirelessautomate.client.tablet.TabletScreen;
import io.github.matheusanbs.wirelessautomate.registry.ModItems;
import java.lang.reflect.Field;
import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import io.github.matheusanbs.wirelessautomate.client.GuiPalette;
import io.github.matheusanbs.wirelessautomate.client.GuiThemes;
import io.github.matheusanbs.wirelessautomate.client.ThemeToggle;

/**
 * A volta no escuro: o botão do tema, as sete telas do mod fotografadas no escuro e a cor do texto de um campo
 * nos dois sentidos.
 */
final class E2eDarkSteps {
    private E2eDarkSteps() {
    }

    /**
     * A volta no escuro: clica no botão do tema da tela do roteador (pelo {@code mouseClicked}, como o
     * jogador), confere o tema e a config, fotografa as sete telas do mod no escuro ({@code escuro-*})
     * conferindo que nenhum texto fica cortado sem a dica do texto inteiro (o {@code clipCheck}), confere a
     * cor do texto de um campo nas duas voltas do botão e clica de novo no botão, na última tela, para voltar ao
     * claro. O {@code finish} devolve o claro se algum passo falhar no meio.
     */
    static void darkSteps(List<Step> list) {
        UUID playerId = Minecraft.getInstance().player.getUUID();
        list.add(handStep("Escuro: mão vazia", playerId, ItemStack.EMPTY));
        // o roteador B fica longe de onde os passos dos armazenamentos deixaram o jogador: vai até ele e volta
        Vec3[] back = new Vec3[1];
        list.add(teleport("Escuro: ir até o roteador B", playerId, back, () -> new Vec3(routerB.getX() + 0.5, routerB.getY() - 1, routerB.getZ() + 2.5)));
        list.add(open("Escuro: abrir B", () -> routerB));
        list.add(themeClick("Escuro: clicar no botão do tema", GuiTheme.DARK));
        darkShot(list, "escuro-roteador");
        list.add(new Step("Escuro: abrir o filtro", STEP_TIMEOUT_MS,
                () -> clickPanel(panel().editCenter(), "Editar"),
                () -> Minecraft.getInstance().screen instanceof FilterScreen,
                () -> "tela " + describe(Minecraft.getInstance().screen)));
        darkShot(list, "escuro-filtro");
        list.add(new Step("Escuro: voltar ao roteador", STEP_TIMEOUT_MS,
                () -> click(widget(byTooltip(Component.translatable("gui.wirelessautomate.filter.back.tooltip")), "Voltar")),
                () -> Minecraft.getInstance().screen instanceof RouterScreen,
                () -> "tela " + describe(Minecraft.getInstance().screen)));
        list.add(close("Escuro: fechar B"));
        list.add(teleport("Escuro: voltar", playerId, null, () -> back[0]));
        list.add(handStep("Escuro: Tablet na mão", playerId, new ItemStack(ModItems.NETWORK_TABLET.get())));
        list.add(useInAir("Escuro: abrir o Tablet", TabletScreen.class));
        darkShot(list, "escuro-tablet");
        list.add(close("Escuro: fechar o Tablet"));
        list.add(handStep("Escuro: Vinculador na mão", playerId, new ItemStack(ModItems.LINKER.get())));
        list.add(useInAir("Escuro: abrir o Vinculador", LinkerScreen.class));
        darkShot(list, "escuro-vinculador");
        list.add(close("Escuro: fechar o Vinculador"));
        list.add(handStep("Escuro: mão vazia de novo", playerId, ItemStack.EMPTY));
        list.add(new Step("Escuro: abrir o Baú", STEP_TIMEOUT_MS, () -> useOn(storageB),
                () -> Minecraft.getInstance().screen instanceof StorageListScreen,
                () -> "tela " + describe(Minecraft.getInstance().screen)));
        darkShot(list, "escuro-bau");
        // O texto de um campo não pode ficar com a cor do tema anterior: digita na busca, troca o tema
        // pelo botão e confere a cor do campo nos dois sentidos (a captura sai no escuro, com o texto).
        list.add(new Step("Escuro: digitar na busca do Baú", STEP_TIMEOUT_MS, () -> editBox().setValue("diamante"),
                () -> "diamante".equals(editBox().getValue()), () -> "campo \"" + editBox().getValue() + "\""));
        list.add(themeClick("Escuro: campo de texto, clicar no botão (claro)", GuiTheme.LIGHT));
        list.add(inkCheck("Escuro: texto do campo no claro", GuiTheme.LIGHT));
        list.add(themeClick("Escuro: campo de texto, clicar no botão (escuro)", GuiTheme.DARK));
        list.add(inkCheck("Escuro: texto do campo no escuro", GuiTheme.DARK));
        darkShot(list, "escuro-bau-texto");
        list.add(close("Escuro: fechar o Baú"));
        list.add(new Step("Escuro: abrir a Bateria", STEP_TIMEOUT_MS, () -> useOn(batteryPos),
                () -> Minecraft.getInstance().screen instanceof StorageScalarScreen screen && screen.getMenu().received(),
                () -> "tela " + describe(Minecraft.getInstance().screen)));
        darkShot(list, "escuro-bateria");
        list.add(close("Escuro: fechar a Bateria"));
        // o editor de receitas é a última tela: o segundo clique no botão volta ao claro
        list.add(new Step("Escuro: abrir o editor de receitas", STEP_TIMEOUT_MS,
                () -> Minecraft.getInstance().player.connection.sendCommand("wa recipes"),
                () -> Minecraft.getInstance().screen instanceof RecipeEditorScreen,
                () -> "tela " + describe(Minecraft.getInstance().screen)));
        darkShot(list, "escuro-receitas");
        list.add(themeClick("Escuro: clicar de novo, volta ao claro", GuiTheme.LIGHT));
        list.add(close("Escuro: fechar o editor de receitas"));
    }

    /** Leva o jogador a um ponto (guardando onde estava em {@code saved}, se não for nulo) e espera o cliente ver. */
    private static Step teleport(String name, UUID playerId, Vec3 @Nullable [] saved, Supplier<Vec3> to) {
        return new Step(name, STEP_TIMEOUT_MS, () -> {
            if (saved != null) {
                saved[0] = Minecraft.getInstance().player.position();
            }
            Vec3 target = to.get();
            onServer(server -> {
                ServerPlayer player = server.getPlayerList().getPlayer(playerId);
                player.teleportTo(player.serverLevel(), target.x, target.y, target.z, 180f, 30f);
                return null;
            });
        }, () -> Minecraft.getInstance().player.position().distanceTo(to.get()) < 0.5,
                () -> "distância " + Minecraft.getInstance().player.position().distanceTo(to.get()));
    }

    /** Põe o item na mão do jogador, no servidor, e espera o cliente ver. */
    static Step handStep(String name, UUID playerId, ItemStack stack) {
        return new Step(name, STEP_TIMEOUT_MS, () -> onServer(server -> {
            server.getPlayerList().getPlayer(playerId).setItemInHand(InteractionHand.MAIN_HAND, stack.copy());
            return null;
        }), () -> ItemStack.isSameItem(Minecraft.getInstance().player.getMainHandItem(), stack),
                () -> "na mão " + Minecraft.getInstance().player.getMainHandItem());
    }

    /** Usa o item da mão no ar (abre a tela do Tablet ou do Vinculador). */
    private static Step useInAir(String name, Class<? extends Screen> screenClass) {
        return new Step(name, STEP_TIMEOUT_MS, () -> {
            Minecraft minecraft = Minecraft.getInstance();
            minecraft.gameMode.useItem(minecraft.player, InteractionHand.MAIN_HAND);
        }, () -> screenClass.isInstance(Minecraft.getInstance().screen),
                () -> "tela " + describe(Minecraft.getInstance().screen));
    }

    /** Clique de mouse no botão do tema da tela aberta; termina com o tema e a config no valor esperado. */
    /** O tema e o ponto do último clique no botão do tema, para a mensagem de falha. */
    private static String themeBefore = "";

    static Step themeClick(String name, GuiTheme expected) {
        return new Step(name, STEP_TIMEOUT_MS, () -> {
            Screen screen = Minecraft.getInstance().screen;
            int[] at = themeButton(screen);
            themeBefore = GuiThemes.current() + "/" + ClientConfig.THEME.get() + " em " + at[0] + "," + at[1];
            click(screen, at[0] + ThemeToggle.SIZE / 2, at[1] + ThemeToggle.SIZE / 2);
        }, () -> GuiThemes.current() == expected && ClientConfig.THEME.get() == expected,
                () -> "tema " + GuiThemes.current() + ", config " + ClientConfig.THEME.get() + ", antes do clique "
                        + themeBefore + ", tela " + describe(Minecraft.getInstance().screen));
    }

    /** O primeiro campo de texto da tela aberta. */
    private static EditBox editBox() throws StepFailure {
        Screen screen = Minecraft.getInstance().screen;
        if (screen != null) {
            for (GuiEventListener child : screen.children()) {
                if (child instanceof EditBox box) {
                    return box;
                }
            }
        }
        throw new StepFailure("sem campo de texto na tela " + describe(screen));
    }

    /** Espera o campo de texto da tela aberta pintar o texto com a tinta do tema (o {@code textColor} do EditBox). */
    private static Step inkCheck(String name, GuiTheme theme) {
        int expected = GuiPalette.of(theme).fg();
        return new Step(name, STEP_TIMEOUT_MS, () -> {
        }, () -> {
            Field field = EditBox.class.getDeclaredField("textColor");
            field.setAccessible(true);
            return field.getInt(editBox()) == expected;
        }, () -> {
            Field field = EditBox.class.getDeclaredField("textColor");
            field.setAccessible(true);
            return "cor do campo " + Integer.toHexString(field.getInt(editBox())) + ", esperada " + Integer.toHexString(expected);
        });
    }

    /** A posição do botão do tema na tela aberta (cada uma das oito a calcula). */
    private static int[] themeButton(@Nullable Screen screen) throws StepFailure {
        if (screen instanceof RouterScreen s) {
            return new int[] {s.themeX(), s.themeY()};
        } else if (screen instanceof TabletScreen s) {
            return new int[] {s.themeX(), s.themeY()};
        } else if (screen instanceof FilterScreen s) {
            return new int[] {s.themeX(), s.themeY()};
        } else if (screen instanceof LinkerScreen s) {
            return new int[] {s.themeX(), s.themeY()};
        } else if (screen instanceof StorageListScreen s) {
            return new int[] {s.themeX(), s.themeY()};
        } else if (screen instanceof StorageScalarScreen s) {
            return new int[] {s.themeX(), s.themeY()};
        } else if (screen instanceof RecipeEditorScreen s) {
            return new int[] {s.themeX(), s.themeY()};
        } else if (screen instanceof ResonatorScreen s) {
            return new int[] {s.themeX(), s.themeY()};
        }
        throw new StepFailure("sem botão do tema na tela " + describe(screen));
    }

    /**
     * Captura a tela no escuro e confere que nenhum texto ficou cortado sem tooltip com o texto inteiro (o
     * nome de rede comprido que o passo em português deixa numa face corta no seletor de rede da face, com a dica).
     */
    private static void darkShot(List<Step> list, String file) {
        list.add(new Step("captura " + file, STEP_TIMEOUT_MS, () -> moveMouse(0, 0), () -> {
            if (stepTicks < 6) {
                return false;
            }
            capture(Minecraft.getInstance(), file);
            return GuiThemes.current() == GuiTheme.DARK;
        }, () -> "tema " + GuiThemes.current()));
        list.add(clipCheck(file));
    }
}
