package io.github.matheusanbs.wirelessautomate.client.dev;

import static io.github.matheusanbs.wirelessautomate.client.dev.DevEndToEnd.*;
import static io.github.matheusanbs.wirelessautomate.client.dev.E2eRouterSteps.*;
import static io.github.matheusanbs.wirelessautomate.client.dev.E2eUi.*;
import static io.github.matheusanbs.wirelessautomate.client.dev.E2eWorld.*;

import io.github.matheusanbs.wirelessautomate.client.filter.FilterScreen;
import io.github.matheusanbs.wirelessautomate.client.router.RouterScreen;
import io.github.matheusanbs.wirelessautomate.filter.Filter;
import io.github.matheusanbs.wirelessautomate.filter.FilterEntry;
import io.github.matheusanbs.wirelessautomate.filter.ItemRule;
import io.github.matheusanbs.wirelessautomate.network.ResourceType;
import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.lwjgl.glfw.GLFW;
import io.github.matheusanbs.wirelessautomate.client.FlatButton;

/**
 * A tela de filtro aberta pelo Editar do roteador B: tags com busca e inspetor, a aba Regra com o encantamento
 * por nome e o nível, a regra por tag e o redimensionar.
 */
final class E2eFilterSteps {
    private E2eFilterSteps() {
    }

    private static final String TAG_RULE = "#c:stones";

    /** A tela de filtro pelo Editar de B: tags, a aba Regra e o tamanho, de volta ao roteador. */
    static void filterSteps(List<Step> list) {
        filterTagSteps(list);
        filterRuleSteps(list);
        filterResizeSteps(list);
    }

    /** Filtro: a tag pela busca, marcada e adicionada, e o inspetor de uma picareta. */
    private static void filterTagSteps(List<Step> list) {
        list.add(new Step("abrir filtro (Editar)", STEP_TIMEOUT_MS,
                () -> clickPanel(panel().editCenter(), "Editar"),
                () -> Minecraft.getInstance().screen instanceof FilterScreen screen
                        && screen.getMenu().view().router().equals(Optional.of(routerB))
                        && screen.getMenu().view().face().equals(Optional.of(Direction.UP)),
                () -> "tela " + describe(Minecraft.getInstance().screen)));
        // os widgets de uma aba só aparecem no quadro seguinte, como para o jogador
        list.add(new Step("filtro: aba Tags", STEP_TIMEOUT_MS,
                () -> click(widget(byMessage(Component.translatable("gui.wirelessautomate.filter.tab.tags")), "aba Tags")),
                // visível também: a caixa só aparece no quadro seguinte e, invisível, ignora o que se digita
                () -> filterScreen().tab() == FilterScreen.Tab.TAGS && filterScreen().tagSearchBox().isFocused()
                        && filterScreen().tagSearchBox().isVisible(),
                () -> "aba " + filterScreen().tab()));
        list.add(new Step("filtro: buscar a tag", STEP_TIMEOUT_MS, () -> type(TAG_RULE.substring(3)),
                () -> filterScreen().candidateLabels().contains(TAG_RULE),
                () -> "linhas " + filterScreen().candidateLabels() + ", busca '" + filterScreen().tagSearchBox().getValue()
                        + "', foco " + filterScreen().getFocused() + ", tags do registro "
                        + net.minecraft.core.registries.BuiltInRegistries.ITEM.getTags().count()));
        list.add(new Step("filtro: marcar a tag", STEP_TIMEOUT_MS, () -> {
            int index = filterScreen().candidateLabels().indexOf(TAG_RULE);
            int[] row = filterScreen().candidateCenter(index);
            click(Minecraft.getInstance().screen, row[0], row[1]);
        }, () -> filterScreen().checkedLabels().equals(List.of(TAG_RULE))
                && find(byMessage(Component.translatable("gui.wirelessautomate.filter.tags.add.action"))) instanceof AbstractWidget add
                && add.active,
                () -> "marcadas " + filterScreen().checkedLabels()));
        list.add(new Step("filtro: adicionar a tag", STEP_TIMEOUT_MS,
                () -> click(widget(byMessage(Component.translatable("gui.wirelessautomate.filter.tags.add.action")), "Adicionar")),
                () -> onServer(server -> hasTag(router(server, routerB).face(ResourceType.ITEM, Direction.UP).filter().entries()))
                        && hasTag(filterScreen().getMenu().view().filter().entries()),
                () -> "filtro no servidor: " + onServer(server -> router(server, routerB)
                        .face(ResourceType.ITEM, Direction.UP).filter().entries().toString())));
        list.add(capture("4-filtro"));
        list.add(new Step("filtro: inspecionar uma picareta", STEP_TIMEOUT_MS,
                () -> filterScreen().inspect(new ItemStack(Items.DIAMOND_PICKAXE)),
                () -> filterScreen().candidateLabels().contains("#minecraft:pickaxes")
                        && filterScreen().candidateLabels().contains("@minecraft"),
                () -> "tags " + filterScreen().candidateLabels()));
        list.add(capture("4b-filtro-inspetor"));
    }

    /** Filtro: a aba Regra, o encantamento por nome e a regra "Encantado só em picaretas". */
    private static void filterRuleSteps(List<Step> list) {
        list.add(new Step("filtro: aba Regra", STEP_TIMEOUT_MS,
                () -> click(widget(byMessage(Component.translatable("gui.wirelessautomate.filter.tab.rule")), "aba Regra")),
                () -> filterScreen().tab() == FilterScreen.Tab.RULE
                        && find(byMessage(Component.translatable("gui.wirelessautomate.filter.rule.yes"))) != null,
                () -> "aba " + filterScreen().tab()));
        // encantamento por nome: sugestões pelo nome traduzido do registro, Enter escolhe, nível digitado
        Predicate<AbstractWidget> enchantToggle = w -> w instanceof FlatButton
                && w.getMessage().equals(Component.translatable("gui.wirelessautomate.filter.rule.enchantment"));
        enchantmentSteps(list, enchantToggle);
        scopeRuleSteps(list, enchantToggle);
    }

    /** Filtro: as sugestões do encantamento, Enter, o nível, os valores inválidos e o Esc. */
    private static void enchantmentSteps(List<Step> list, Predicate<AbstractWidget> enchantToggle) {
        list.add(new Step("filtro: marcar Encantamento", STEP_TIMEOUT_MS,
                () -> click(widget(enchantToggle, "Encantamento")),
                () -> filterScreen().ruleDraft().enchantment().isPresent() && filterScreen().enchantBox().visible,
                () -> "rascunho " + filterScreen().ruleDraft()));
        list.add(new Step("filtro: focar o encantamento lista todos", STEP_TIMEOUT_MS, () -> {
            EditBox box = filterScreen().enchantBox();
            click(Minecraft.getInstance().screen, box.getX() + 4, box.getY() + 3);
        }, () -> filterScreen().suggestionLabels().size() > 5,
                () -> "sugestões " + filterScreen().suggestionLabels()));
        list.add(capture("filtro-regra-lista"));
        list.add(new Step("filtro: digitar prot no encantamento", STEP_TIMEOUT_MS, () -> type("prot"), () -> filterScreen().enchantBox().getValue().equals("prot")
                && filterScreen().suggestionLabels().contains(enchantmentName(
                        net.minecraft.world.item.enchantment.Enchantments.PROTECTION)),
                () -> "campo '" + filterScreen().enchantBox().getValue() + "', sugestões " + filterScreen().suggestionLabels()));
        list.add(capture("filtro-regra-sugestoes"));
        list.add(clipCheck("filtro: sugestões"));
        list.add(new Step("filtro: escolher com Enter", STEP_TIMEOUT_MS,
                () -> filterScreen().keyPressed(GLFW.GLFW_KEY_ENTER, 0, 0),
                () -> filterScreen().ruleDraft().enchantment()
                        .map(e -> e.id().equals(net.minecraft.world.item.enchantment.Enchantments.PROTECTION.location()))
                        .orElse(false)
                        && filterScreen().suggestionLabels().isEmpty()
                        && filterScreen().enchantBox().getValue().equals(enchantmentName(
                                net.minecraft.world.item.enchantment.Enchantments.PROTECTION)),
                () -> "rascunho " + filterScreen().ruleDraft() + ", campo '" + filterScreen().enchantBox().getValue() + "'"));
        list.add(new Step("filtro: nível 37", STEP_TIMEOUT_MS, () -> {
            EditBox level = filterScreen().levelBox();
            click(Minecraft.getInstance().screen, level.getX() + 4, level.getY() + 3);
            type("37");
        }, () -> filterScreen().ruleDraft().enchantment()
                .map(e -> e.minLevel() == 37
                        && e.id().equals(net.minecraft.world.item.enchantment.Enchantments.PROTECTION.location()))
                .orElse(false) && filterScreen().levelBox().getValue().equals("37"),
                () -> "rascunho " + filterScreen().ruleDraft() + ", nível '" + filterScreen().levelBox().getValue() + "'"));
        list.add(capture("filtro-regra-nivel"));
        list.add(clipCheck("filtro: regra com nível"));
        list.add(new Step("filtro: encantamento e nível inválidos", STEP_TIMEOUT_MS, () -> {
            EditBox box = filterScreen().enchantBox();
            click(Minecraft.getInstance().screen, box.getX() + 4, box.getY() + 3);
            type("zzz");
            EditBox level = filterScreen().levelBox();
            click(Minecraft.getInstance().screen, level.getX() + 4, level.getY() + 3);
            type("0");
            click(Minecraft.getInstance().screen, box.getX() + 4, box.getY() + 3);
            type("zzz");
        }, () -> filterScreen().suggestionLabels().isEmpty() && filterScreen().enchantBox().getValue().equals("zzz")
                && filterScreen().levelBox().getValue().equals("0")
                && find(byMessage(Component.translatable("gui.wirelessautomate.filter.rule.add"))) instanceof AbstractWidget add
                && !add.active,
                () -> "campo '" + filterScreen().enchantBox().getValue() + "', nível '" + filterScreen().levelBox().getValue()
                        + "', sugestões " + filterScreen().suggestionLabels()));
        list.add(capture("filtro-regra-invalido"));
        list.add(new Step("filtro: Esc volta o encantamento", STEP_TIMEOUT_MS,
                () -> filterScreen().keyPressed(GLFW.GLFW_KEY_ESCAPE, 0, 0),
                () -> Minecraft.getInstance().screen instanceof FilterScreen
                        && filterScreen().enchantBox().getValue().equals(enchantmentName(
                                net.minecraft.world.item.enchantment.Enchantments.PROTECTION))
                        && filterScreen().ruleDraft().enchantment().map(e -> e.minLevel() == 37).orElse(false),
                () -> "campo '" + filterScreen().enchantBox().getValue() + "', rascunho " + filterScreen().ruleDraft()));
    }

    /** Filtro: desmarca o encantamento e adiciona a regra "Encantado só em #minecraft:pickaxes". */
    private static void scopeRuleSteps(List<Step> list, Predicate<AbstractWidget> enchantToggle) {
        list.add(new Step("filtro: desmarcar Encantamento", STEP_TIMEOUT_MS,
                () -> click(widget(enchantToggle, "Encantamento")),
                () -> filterScreen().ruleDraft().enchantment().isEmpty() && !filterScreen().enchantBox().visible,
                () -> "rascunho " + filterScreen().ruleDraft()));
        list.add(new Step("filtro: regra Encantado em picaretas", STEP_TIMEOUT_MS, () -> {
            // o primeiro "Sim" é o de Encantado (as condições vêm na ordem da tela)
            click(widget(byMessage(Component.translatable("gui.wirelessautomate.filter.rule.yes")), "Sim de Encantado"));
            EditBox scope = filterScreen().scopeBox();
            click(Minecraft.getInstance().screen, scope.getX() + 4, scope.getY() + 3);
            type("#minecraft:pickaxes");
        }, () -> {
            AbstractWidget add = find(byMessage(Component.translatable("gui.wirelessautomate.filter.rule.add")));
            return add != null && add.active;
        }, () -> "Adicionar regra ativo"));
        list.add(capture("4c-filtro-regra"));
        list.add(new Step("filtro: adicionar a regra", STEP_TIMEOUT_MS,
                () -> click(widget(byMessage(Component.translatable("gui.wirelessautomate.filter.rule.add")), "Adicionar regra")),
                () -> onServer(server -> hasPickaxeRule(router(server, routerB).face(ResourceType.ITEM, Direction.UP).filter()))
                        && filterScreen().tab() == FilterScreen.Tab.ENTRY,
                () -> "filtro no servidor: " + onServer(server -> router(server, routerB)
                        .face(ResourceType.ITEM, Direction.UP).filter().entries().toString())));
    }

    /** Filtro: aumenta a janela pela alça e volta ao tamanho; Voltar retorna ao roteador. */
    private static void filterResizeSteps(List<Step> list) {
        int[] filterSize = new int[2];
        list.add(new Step("filtro: aumentar a janela", STEP_TIMEOUT_MS, () -> {
            filterSize[0] = filterScreen().panelWidth();
            filterSize[1] = filterScreen().panelHeight();
            dragGrip(filterScreen(), 40, 36);
        }, () -> filterScreen().panelWidth() > filterSize[0] && filterScreen().panelHeight() > filterSize[1]
                && filterScreen().inventoryFollows() && centered(filterScreen()),
                () -> "tamanho " + filterScreen().panelWidth() + "x" + filterScreen().panelHeight()));
        list.add(capture("4d-filtro-maior"));
        list.add(new Step("filtro: tamanho de volta", STEP_TIMEOUT_MS,
                // a janela cresce dos dois lados (fica no centro): metade da diferença em cada borda
                () -> dragGrip(filterScreen(), (filterSize[0] - filterScreen().panelWidth()) / 2,
                        (filterSize[1] - filterScreen().panelHeight()) / 2),
                () -> filterScreen().panelWidth() == filterSize[0] && filterScreen().panelHeight() == filterSize[1]
                        && filterScreen().inventoryFollows() && centered(filterScreen()),
                () -> "tamanho " + filterScreen().panelWidth() + "x" + filterScreen().panelHeight()));
        list.add(new Step("voltar ao roteador", STEP_TIMEOUT_MS,
                () -> click(widget(byTooltip(Component.translatable("gui.wirelessautomate.filter.back.tooltip")), "Voltar")),
                () -> Minecraft.getInstance().screen instanceof RouterScreen screen
                        && screen.getMenu().snapshot().pos().equals(routerB)
                        && screen.getMenu().snapshot().face(ResourceType.ITEM, Direction.UP).filterSize() == 2,
                () -> "tela " + describe(Minecraft.getInstance().screen)));
    }

    /** Nome traduzido de um encantamento, pelo registro do mundo do cliente. */
    private static String enchantmentName(net.minecraft.resources.ResourceKey<net.minecraft.world.item.enchantment.Enchantment> key) {
        return Minecraft.getInstance().level.registryAccess().registryOrThrow(Registries.ENCHANTMENT)
                .getHolderOrThrow(key).value().description().getString();
    }

    /**
     * O filtro tem a regra "Encantado só em #minecraft:pickaxes", e ela sozinha não pega a picareta
     * comum nem o livro encantado (fora da tag).
     */
    private static boolean hasPickaxeRule(Filter filter) {
        List<FilterEntry> rules = filter.entries().stream().filter(e -> e instanceof FilterEntry.RuleEntry r
                && Boolean.TRUE.equals(r.rule().flag(ItemRule.Property.ENCHANTED))
                && r.rule().scope().equals("#minecraft:pickaxes")).toList();
        Filter only = new Filter(Filter.ListMode.WHITELIST, false, rules);
        return !rules.isEmpty() && !only.testItem(new ItemStack(Items.DIAMOND_PICKAXE))
                && !only.testItem(new ItemStack(Items.ENCHANTED_BOOK));
    }

    static FilterScreen filterScreen() throws StepFailure {
        if (Minecraft.getInstance().screen instanceof FilterScreen screen) {
            return screen;
        }
        throw new StepFailure("tela " + describe(Minecraft.getInstance().screen));
    }

    private static boolean hasTag(List<FilterEntry> entries) {
        ResourceLocation tag = ResourceLocation.parse(TAG_RULE.substring(1));
        return entries.stream().anyMatch(e -> e instanceof FilterEntry.TagEntry t && t.tag().equals(tag));
    }
}
