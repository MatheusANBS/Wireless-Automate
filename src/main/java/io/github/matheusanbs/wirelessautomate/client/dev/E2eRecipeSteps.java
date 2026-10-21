package io.github.matheusanbs.wirelessautomate.client.dev;

import static io.github.matheusanbs.wirelessautomate.client.dev.DevEndToEnd.*;
import static io.github.matheusanbs.wirelessautomate.client.dev.E2eSteps.*;
import static io.github.matheusanbs.wirelessautomate.client.dev.E2eUi.*;
import static io.github.matheusanbs.wirelessautomate.client.dev.E2eWorld.*;

import io.github.matheusanbs.wirelessautomate.WirelessAutomate;
import io.github.matheusanbs.wirelessautomate.client.recipe.RecipeEditorScreen;
import io.github.matheusanbs.wirelessautomate.menu.RecipeEditorSnapshot;
import io.github.matheusanbs.wirelessautomate.recipe.edit.RecipeDraft;
import io.github.matheusanbs.wirelessautomate.recipe.edit.RecipeEditor;
import io.github.matheusanbs.wirelessautomate.recipe.edit.RecipeOverridePack;
import io.github.matheusanbs.wirelessautomate.recipe.edit.RecipeSlot;
import java.nio.file.Files;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import org.jetbrains.annotations.Nullable;
import io.github.matheusanbs.wirelessautomate.client.GuiText;

/**
 * O editor de receitas no e2e ({@code /wa recipes}): editar, salvar, recarregar e restaurar o Cartão de Filtro,
 * o slot de tag do Tanque e o anel do Elite.
 */
final class E2eRecipeSteps {
    private E2eRecipeSteps() {
    }

    private static final ResourceLocation FILTER_CARD_RECIPE = WirelessAutomate.id("filter_card");

    /**
     * Editor de receitas: abre a tela por {@code /wa recipes} (o jogador do e2e é operador, o mundo tem
     * cheats), escolhe o Cartão de Filtro, sobe o resultado de 2 para 4 pelo {@code +}, salva, recarrega e
     * confere a receita carregada no servidor integrado; depois restaura, recarrega e confere que volta a 2.
     * O override fica em {@code run/config/wirelessautomate/recipes} e o Restaurar o apaga.
     */
    static void recipeEditorSteps(List<Step> list) {
        // um override de uma rodada que falhou no meio mudaria o ponto de partida: apaga e recarrega
        list.add(new Step("Receitas: sem override do Cartão de Filtro", 30_000, () -> onServer(server -> {
            if (Files.exists(RecipeOverridePack.recipeFile(FILTER_CARD_RECIPE))) {
                RecipeEditor.restore(server, FILTER_CARD_RECIPE);
                RecipeEditor.reload(server);
            }
            return null;
        }), () -> onServer(server -> loadedCount(server, FILTER_CARD_RECIPE) == 2),
                () -> onServer(server -> "carregada dá " + loadedCount(server, FILTER_CARD_RECIPE))));
        list.add(new Step("Receitas: abrir por /wa recipes", STEP_TIMEOUT_MS, () -> {
            Minecraft minecraft = Minecraft.getInstance();
            if (minecraft.screen != null) {
                throw new StepFailure("ainda há uma tela aberta: " + describe(minecraft.screen));
            }
            minecraft.player.connection.sendCommand("wa recipes");
        }, () -> Minecraft.getInstance().screen instanceof RecipeEditorScreen screen
                && recipeRow(screen, FILTER_CARD_RECIPE) != null,
                () -> "tela " + describe(Minecraft.getInstance().screen)));
        // a primeira receita da lista (alloy_frame) tem uma tag longa no slot 1, abreviada com tooltip
        list.add(clipCheck("Receitas: lista"));
        list.add(capture("recipes-1-lista"));
        list.add(new Step("Receitas: escolher o Cartão de Filtro", STEP_TIMEOUT_MS, () -> {
            RecipeEditorScreen screen = recipeScreen();
            int[] center = screen.rowCenter(FILTER_CARD_RECIPE);
            if (center == null) {
                throw new StepFailure("o Cartão de Filtro não está na lista");
            }
            click(screen, center[0], center[1]);
        }, () -> FILTER_CARD_RECIPE.equals(recipeScreen().selectedId()) && recipeScreen().draft().count() == 2,
                () -> "escolhida " + recipeScreen().selectedId() + ", rascunho " + recipeScreen().draft()));
        list.add(new Step("Receitas: + duas vezes", STEP_TIMEOUT_MS, () -> {
            click(recipeScreen().plusButton());
            click(recipeScreen().plusButton());
        }, () -> recipeScreen().draft().count() == 4, () -> "quantidade " + recipeScreen().draft().count()));
        list.add(new Step("Receitas: salvar", STEP_TIMEOUT_MS, () -> click(recipeScreen().saveButton()), () -> {
            RecipeEditorScreen screen = recipeScreen();
            RecipeEditorSnapshot.Row row = recipeRow(screen, FILTER_CARD_RECIPE);
            return row != null && row.state() == RecipeEditor.State.EDITED && row.current().count() == 4
                    && screen.draft().equals(row.current()) && screen.getMenu().snapshot().pending() > 0
                    && screen.reloadButton().visible
                    && Files.exists(RecipeOverridePack.recipeFile(FILTER_CARD_RECIPE)) && GuiText.clipCount() == 0;
        }, () -> "linha " + recipeRow(recipeScreen(), FILTER_CARD_RECIPE) + ", pendentes "
                + recipeScreen().getMenu().snapshot().pending() + ", " + GuiText.clipCount() + " texto(s) cortado(s)"));
        list.add(capture("recipes-2-editada"));
        list.add(new Step("Receitas: recarregar, o Cartão de Filtro dá 4", 30_000,
                () -> click(recipeScreen().reloadButton()),
                () -> recipeScreen().getMenu().snapshot().pending() == 0
                        && onServer(server -> loadedCount(server, FILTER_CARD_RECIPE) == 4)
                        && !recipeRow(recipeScreen(), FILTER_CARD_RECIPE).divergent(),
                () -> "pendentes " + recipeScreen().getMenu().snapshot().pending() + ", carregada dá "
                        + onServer(server -> loadedCount(server, FILTER_CARD_RECIPE))));
        list.add(frames());
        list.add(capture("recipes-3-aplicada"));
        list.add(new Step("Receitas: restaurar", STEP_TIMEOUT_MS, () -> click(recipeScreen().restoreButton()), () -> {
            RecipeEditorSnapshot.Row row = recipeRow(recipeScreen(), FILTER_CARD_RECIPE);
            // o botão Recarregar só aparece no quadro seguinte ao snapshot com a pendência
            return row != null && row.state() == RecipeEditor.State.DEFAULT && recipeScreen().draft().count() == 2
                    && recipeScreen().getMenu().snapshot().pending() > 0 && recipeScreen().reloadButton().visible
                    && !Files.exists(RecipeOverridePack.recipeFile(FILTER_CARD_RECIPE));
        }, () -> "linha " + recipeRow(recipeScreen(), FILTER_CARD_RECIPE)));
        list.add(new Step("Receitas: recarregar, o Cartão de Filtro volta a 2", 30_000,
                () -> click(recipeScreen().reloadButton()),
                () -> recipeScreen().getMenu().snapshot().pending() == 0
                        && onServer(server -> loadedCount(server, FILTER_CARD_RECIPE) == 2),
                () -> "pendentes " + recipeScreen().getMenu().snapshot().pending() + ", carregada dá "
                        + onServer(server -> loadedCount(server, FILTER_CARD_RECIPE))));
        // o Tanque Wireless tem uma tag na grade (#c:glass_blocks): o # coral no canto do slot
        ResourceLocation tank = WirelessAutomate.id("storage_tank");
        list.add(new Step("Receitas: escolher o Tanque (slot de tag)", STEP_TIMEOUT_MS, () -> {
            RecipeEditorScreen screen = recipeScreen();
            int[] center = screen.rowCenter(tank);
            if (center == null) {
                throw new StepFailure("o Tanque não está na lista");
            }
            click(screen, center[0], center[1]);
        }, () -> tank.equals(recipeScreen().selectedId()) && recipeScreen().draft().slots().stream()
                .anyMatch(slot -> slot.kind() == RecipeSlot.Kind.TAG) && GuiText.clipCount() == 0,
                () -> "escolhida " + recipeScreen().selectedId() + ", " + GuiText.clipCount() + " texto(s) cortado(s)"));
        list.add(frames());
        list.add(capture("recipes-4-tag"));
        // o anel de subir tier (wirelessautomate:tier_upgrade): centro travado, sem quantidade, o aviso do centro
        ResourceLocation ring = WirelessAutomate.id("tier_core_elite");
        list.add(new Step("Receitas: escolher o anel do Elite", STEP_TIMEOUT_MS, () -> {
            RecipeEditorScreen screen = recipeScreen();
            int[] center = screen.rowCenter(ring);
            if (center == null) {
                throw new StepFailure("o anel do Elite não está na lista");
            }
            click(screen, center[0], center[1]);
        }, () -> ring.equals(recipeScreen().selectedId())
                && recipeScreen().draft().shape() == RecipeDraft.Shape.RING
                && recipeScreen().draft().slots().get(RecipeDraft.CENTER).isEmpty()
                && !recipeScreen().plusButton().visible && GuiText.clipCount() == 0,
                () -> "escolhida " + recipeScreen().selectedId() + ", rascunho " + recipeScreen().draft() + ", "
                        + GuiText.clipCount() + " texto(s) cortado(s)"));
        list.add(frames());
        list.add(capture("recipes-5-anel"));
        list.add(close("Receitas: fechar"));
    }

    /**
     * Meio segundo antes da captura: depois da recarga o cliente roda vários ticks de uma vez, sem
     * desenhar, e a captura sairia com o quadro de antes.
     */
    private static Step frames() {
        return new Step("Receitas: esperar quadros novos", STEP_TIMEOUT_MS, () -> { },
                () -> System.currentTimeMillis() - stepStart > 500, () -> "");
    }

    private static RecipeEditorScreen recipeScreen() throws StepFailure {
        if (Minecraft.getInstance().screen instanceof RecipeEditorScreen screen) {
            return screen;
        }
        throw new StepFailure("o editor de receitas não está aberto (tela " + describe(Minecraft.getInstance().screen) + ")");
    }

    private static RecipeEditorSnapshot.@Nullable Row recipeRow(RecipeEditorScreen screen, ResourceLocation id) {
        return screen.getMenu().snapshot().rows().stream().filter(r -> r.id().equals(id)).findFirst().orElse(null);
    }

    /** Quantos itens a receita carregada no servidor dá (0 se ela não está carregada). */
    private static int loadedCount(MinecraftServer server, ResourceLocation id) {
        return server.getRecipeManager().byKey(id)
                .map(holder -> holder.value().getResultItem(server.registryAccess()).getCount()).orElse(0);
    }
}
