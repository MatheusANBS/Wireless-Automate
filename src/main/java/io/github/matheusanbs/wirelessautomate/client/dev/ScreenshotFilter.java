package io.github.matheusanbs.wirelessautomate.client.dev;

import static io.github.matheusanbs.wirelessautomate.client.dev.DevScreenshot.*;
import static io.github.matheusanbs.wirelessautomate.client.dev.ScreenshotRouter.*;
import static io.github.matheusanbs.wirelessautomate.client.dev.ScreenshotTablet.*;

import io.github.matheusanbs.wirelessautomate.block.RouterTier;
import io.github.matheusanbs.wirelessautomate.client.filter.FilterScreen;
import io.github.matheusanbs.wirelessautomate.filter.Filter;
import io.github.matheusanbs.wirelessautomate.filter.FilterEntry;
import io.github.matheusanbs.wirelessautomate.filter.ItemRule;
import io.github.matheusanbs.wirelessautomate.filter.FilterEntry.FluidEntry;
import io.github.matheusanbs.wirelessautomate.filter.FilterEntry.ItemEntry;
import io.github.matheusanbs.wirelessautomate.filter.FilterEntry.ModEntry;
import io.github.matheusanbs.wirelessautomate.filter.FilterEntry.TagEntry;
import io.github.matheusanbs.wirelessautomate.menu.FilterMenu;
import io.github.matheusanbs.wirelessautomate.menu.FilterView;
import io.github.matheusanbs.wirelessautomate.menu.RouterSnapshot;
import io.github.matheusanbs.wirelessautomate.menu.RouterSnapshot.FaceView;
import io.github.matheusanbs.wirelessautomate.network.ResourceType;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.fluids.FluidStack;
import org.jetbrains.annotations.Nullable;
import io.github.matheusanbs.wirelessautomate.client.GuiPaint;

/**
 * As capturas da tela de filtro: o resumo na tela do roteador e a tela de filtro com os filtros de exemplo
 * (itens, tags, regra, "Mais", fluidos, lista negra e cartão vazio).
 */
final class ScreenshotFilter {
    private ScreenshotFilter() {
    }

    /** Desenhada no lugar da {@link ScreenshotRouter#screen} a partir do primeiro passo de filtro. */
    static FilterScreen filterScreen;

    /**
     * Tela de filtro: o resumo na tela do roteador e a FilterScreen com um filtro de exemplo (grade
     * com seleção, "Mais" aberto, fluidos, lista negra com Limpar armado e cartão vazio).
     */
    static List<Step> filterSteps() {
        return List.of(
                new Step(() -> {
                    // resumo do filtro na tela do roteador, de volta à fornalha (o visor terminou sem
                    // máquina): lista branca embaixo, negra no norte
                    RouterSnapshot s = sample();
                    List<FaceView> faces = new ArrayList<>(s.faces());
                    int down = RouterSnapshot.index(ResourceType.ITEM, Direction.DOWN);
                    int north = RouterSnapshot.index(ResourceType.ITEM, Direction.NORTH);
                    FaceView d = faces.get(down);
                    FaceView n = faces.get(north);
                    faces.set(down, new FaceView(d.mode(), d.priority(), d.redstone(), d.slots(), 12, false, d.network()));
                    faces.set(north, new FaceView(n.mode(), n.priority(), n.redstone(), n.slots(), 3, true, n.network()));
                    screen.getMenu().applySnapshot(new RouterSnapshot(s.pos(), "Fornalha Norte", s.tier(), s.facing(),
                            s.attachedFront(), s.networks(), s.powered(), s.machine(), s.machineState(), List.copyOf(faces)));
                    screen.previewType(ResourceType.ITEM);
                    screen.previewFace(Direction.DOWN);
                    mouseX = mouseY = -1;
                }, "f1-roteador-filtro"),
                new Step(() -> {
                    mouseX = mouseY = -1;
                    filterScreen = filterScreen(itemFilter(Filter.ListMode.WHITELIST), Direction.DOWN);
                    filterScreen.previewSelect(2);
                    int[] center = filterScreen.previewEntryCenter(18);
                    mouseX = center[0];
                    mouseY = center[1];
                }, "f2-filtro-itens"),
                new Step(() -> {
                    mouseX = mouseY = -1;
                    filterScreen.previewInspect(new ItemStack(Items.IRON_PICKAXE));
                    filterScreen.previewCheck(0);
                    filterScreen.previewCheck(2);
                    int[] center = filterScreen.candidateCenter(2);
                    mouseX = center[0];
                    mouseY = center[1];
                }, "f3-filtro-tags"),
                new Step(() -> {
                    mouseX = mouseY = -1;
                    filterScreen.previewTagSearch("ingots");
                }, "f3b-filtro-busca"),
                new Step(() -> {
                    mouseX = mouseY = -1;
                    filterScreen.previewRule(ItemRule.EMPTY.withFlag(ItemRule.Property.DAMAGED, true)
                            .withDurability(Optional.of(new ItemRule.Durability(false, 50))).withScope("#minecraft:pickaxes"));
                }, "f3c-filtro-regra"),
                new Step(() -> {
                    mouseX = mouseY = -1;
                    filterScreen.previewTab(FilterScreen.Tab.MORE);
                }, "f3d-filtro-mais"),
                new Step(() -> {
                    mouseX = mouseY = -1;
                    filterScreen = filterScreen(fluidFilter(), Direction.NORTH);
                    filterScreen.previewSelect(0);
                }, "f4-filtro-fluidos"),
                new Step(() -> {
                    filterScreen = filterScreen(itemFilter(Filter.ListMode.BLACKLIST).withMatchComponents(true),
                            Direction.UP);
                    filterScreen.previewArmClear();
                }, "f5-filtro-negra"),
                new Step(() -> {
                    mouseX = mouseY = -1;
                    filterScreen = filterScreen(Filter.EMPTY, null);
                }, "f6-filtro-cartao-vazio"));
    }

    /** Filtro de uma face (ou de um cartão, sem face) com o inventário de exemplo e a cor Elite. */
    static FilterScreen filterScreen(Filter filter, @Nullable Direction face) {
        ResourceType type = filter.entries().stream().anyMatch(e -> e instanceof FluidEntry) ? ResourceType.FLUID
                : ResourceType.ITEM;
        FilterView view = new FilterView(type, face == null ? Optional.empty() : Optional.of(new BlockPos(0, 64, 0)),
                Optional.ofNullable(face), filter, face == null);
        Inventory inventory = new Inventory(null);
        ItemStack[] items = {new ItemStack(Items.IRON_INGOT, 64), new ItemStack(Items.COAL, 23),
                new ItemStack(Items.WATER_BUCKET), new ItemStack(Items.REDSTONE, 41), new ItemStack(Items.DIAMOND, 7),
                new ItemStack(Items.OAK_LOG, 32), new ItemStack(Items.LAVA_BUCKET), new ItemStack(Items.COBBLESTONE, 64),
                damaged(Items.IRON_PICKAXE, 200), new ItemStack(Items.TORCH, 50), damaged(Items.DIAMOND_PICKAXE, 1400),
                new ItemStack(Items.GOLDEN_PICKAXE)};
        for (int i = 0; i < items.length; i++) {
            inventory.setItem(i < 5 ? i : 9 + i, items[i]);
        }
        FilterScreen created = new FilterScreen(new FilterMenu(0, inventory, view), inventory,
                Component.translatable("gui.wirelessautomate.filter.title"), true);
        created.previewTrim(face == null ? GuiPaint.ACCENT : GuiPaint.tierColor(RouterTier.ELITE));
        // os passos mexem em widgets: a tela precisa estar montada antes do primeiro quadro
        Minecraft minecraft = Minecraft.getInstance();
        created.init(minecraft, minecraft.getWindow().getGuiScaledWidth(), minecraft.getWindow().getGuiScaledHeight());
        return created;
    }

    private static ItemStack damaged(net.minecraft.world.item.Item item, int damage) {
        ItemStack stack = new ItemStack(item);
        stack.setDamageValue(damage);
        return stack;
    }

    static Filter itemFilter(Filter.ListMode mode) {
        List<FilterEntry> entries = new ArrayList<>();
        entries.add(new ItemEntry(new ItemStack(Items.IRON_INGOT), 0));
        entries.add(new ItemEntry(new ItemStack(Items.GOLD_INGOT), 0));
        entries.add(new ItemEntry(new ItemStack(Items.COAL), 64));
        entries.add(new TagEntry(ResourceLocation.parse("c:ingots"), 0));
        entries.add(new ModEntry("mekanism", 0));
        entries.add(new FilterEntry.RuleEntry(ItemRule.EMPTY.withFlag(ItemRule.Property.ENCHANTED, true).withScope("#c:armors"), 0));
        ItemStack[] more = {new ItemStack(Items.COPPER_INGOT), new ItemStack(Items.REDSTONE), new ItemStack(Items.DIAMOND),
                new ItemStack(Items.EMERALD), new ItemStack(Items.LAPIS_LAZULI), new ItemStack(Items.QUARTZ),
                new ItemStack(Items.RAW_IRON), new ItemStack(Items.RAW_GOLD), new ItemStack(Items.RAW_COPPER),
                new ItemStack(Items.NETHERITE_SCRAP), new ItemStack(Items.ANCIENT_DEBRIS), new ItemStack(Items.OBSIDIAN),
                new ItemStack(Items.GLOWSTONE_DUST), new ItemStack(Items.AMETHYST_SHARD), new ItemStack(Items.IRON_PICKAXE),
                new ItemStack(Items.SAND), new ItemStack(Items.GRAVEL), new ItemStack(Items.CLAY_BALL),
                new ItemStack(Items.SLIME_BALL), new ItemStack(Items.BONE), new ItemStack(Items.STRING),
                new ItemStack(Items.GUNPOWDER), new ItemStack(Items.ENDER_PEARL), new ItemStack(Items.BLAZE_ROD),
                new ItemStack(Items.GHAST_TEAR), new ItemStack(Items.PRISMARINE_SHARD), new ItemStack(Items.SPONGE)};
        for (ItemStack stack : more) {
            entries.add(new ItemEntry(stack, 0));
        }
        entries.add(new TagEntry(ResourceLocation.parse("c:ores"), 0));
        return new Filter(mode, false, entries);
    }

    static Filter fluidFilter() {
        return new Filter(Filter.ListMode.WHITELIST, false, List.of(
                new FluidEntry(new FluidStack(Fluids.WATER, 1), 8_000),
                new FluidEntry(new FluidStack(Fluids.LAVA, 1), 0),
                new TagEntry(ResourceLocation.parse("c:oil"), 0),
                new ModEntry("mekanism", 500)));
    }
}
