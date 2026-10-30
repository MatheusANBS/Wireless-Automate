package io.github.matheusanbs.wirelessautomate.client.dev;

import static io.github.matheusanbs.wirelessautomate.client.dev.DevEndToEnd.*;
import static io.github.matheusanbs.wirelessautomate.client.dev.E2eSteps.*;
import static io.github.matheusanbs.wirelessautomate.client.dev.E2eUi.*;
import static io.github.matheusanbs.wirelessautomate.client.dev.E2eWorld.*;

import io.github.matheusanbs.wirelessautomate.GuiTheme;
import io.github.matheusanbs.wirelessautomate.block.RouterBlock;
import io.github.matheusanbs.wirelessautomate.block.RouterTier;
import io.github.matheusanbs.wirelessautomate.client.GuiText;
import io.github.matheusanbs.wirelessautomate.client.GuiThemes;
import io.github.matheusanbs.wirelessautomate.client.storage.StorageListScreen;
import io.github.matheusanbs.wirelessautomate.client.storage.upgrade.UpgradePanels;
import io.github.matheusanbs.wirelessautomate.item.ChestUpgradeItem;
import io.github.matheusanbs.wirelessautomate.menu.ChestUpgradeSlots;
import io.github.matheusanbs.wirelessautomate.menu.StorageListMenu;
import io.github.matheusanbs.wirelessautomate.registry.ModBlocks;
import io.github.matheusanbs.wirelessautomate.registry.ModDataComponents;
import io.github.matheusanbs.wirelessautomate.registry.ModItems;
import io.github.matheusanbs.wirelessautomate.storage.ItemStorage;
import io.github.matheusanbs.wirelessautomate.storage.StorageBlock;
import io.github.matheusanbs.wirelessautomate.storage.StorageChestBlockEntity;
import io.github.matheusanbs.wirelessautomate.storage.upgrade.ChestUpgrades;
import io.github.matheusanbs.wirelessautomate.storage.upgrade.CompactingChain;
import io.github.matheusanbs.wirelessautomate.storage.upgrade.CompactingChains;
import io.github.matheusanbs.wirelessautomate.storage.upgrade.CompactingRules;
import java.util.List;
import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;

/**
 * Os upgrades do Baú no e2e: um Baú com a Mesa de Trabalho e a Compactação, a coluna dos slots, o painel da Mesa com
 * uma picareta montada, o painel da Compactação com as cadeias vanilla (ferro, ouro e diamante; com o AllTheCompressed
 * no cliente de dev, também as do pedregulho e da terra) e o seletor de formas, que manda a regra ao servidor. Em
 * cada painel, nenhum texto cortado. Com as variantes, de novo no tema escuro e em português.
 */
final class E2eChestUpgradeSteps {
    private E2eChestUpgradeSteps() {
    }

    private static BlockPos chestPos = BlockPos.ZERO;
    private static GuiTheme themeBefore = GuiTheme.LIGHT;

    static void upgradeSteps(List<Step> list) {
        UUID playerId = Minecraft.getInstance().player.getUUID();
        prepare(list, playerId);
        panels(list, "");
        E2eVariants.escuro(list, () -> {
            list.add(new Step("Upgrades: tema escuro", STEP_TIMEOUT_MS, () -> {
                themeBefore = GuiThemes.current();
                GuiThemes.preview(GuiTheme.DARK);
            }, () -> GuiThemes.current() == GuiTheme.DARK, () -> "tema " + GuiThemes.current()));
            panels(list, "-escuro");
            list.add(new Step("Upgrades: tema de volta", STEP_TIMEOUT_MS, () -> GuiThemes.preview(themeBefore),
                    () -> GuiThemes.current() == themeBefore, () -> "tema " + GuiThemes.current()));
        });
        E2eVariants.pt(list, () -> {
            list.add(language("pt_br"));
            panels(list, "-pt");
            list.add(language("en_us"));
        });
        list.add(new Step("Upgrades: tirar o Baú", STEP_TIMEOUT_MS, () -> onServer(server -> {
            server.overworld().setBlockAndUpdate(chestPos, Blocks.AIR.defaultBlockState());
            return null;
        }), () -> true, () -> ""));
    }

    /** Um Baú Esmeralda com os dois upgrades, metais, diamantes, pedregulho e terra, e a picareta na grade. */
    private static void prepare(List<Step> list, UUID playerId) {
        list.add(new Step("Upgrades: preparar o Baú", STEP_TIMEOUT_MS, () -> onServer(server -> {
            ServerPlayer player = server.getPlayerList().getPlayer(playerId);
            ServerLevel level = player.serverLevel();
            player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
            chestPos = player.blockPosition().offset(0, 0, -3);
            level.setBlockAndUpdate(chestPos, ModBlocks.STORAGE_CHEST.get().defaultBlockState()
                    .setValue(RouterBlock.TIER, RouterTier.EMERALD));
            StorageChestBlockEntity chest = chest(server);
            ItemStorage storage = chest.storage();
            storage.insert(new ItemStack(Items.IRON_INGOT), 200, false);
            storage.insert(new ItemStack(Items.IRON_NUGGET), 5, false);
            storage.insert(new ItemStack(Items.GOLD_INGOT), 30, false);
            storage.insert(new ItemStack(Items.DIAMOND), 20, false);
            storage.insert(new ItemStack(Items.COBBLESTONE), 1_000, false);
            storage.insert(new ItemStack(Items.DIRT), 500, false);
            storage.insert(new ItemStack(Items.STICK), 64, false);
            ChestUpgrades upgrades = chest.upgrades();
            upgrades.setUpgrade(0, new ItemStack(ModItems.CRAFTING_UPGRADE.get()));
            upgrades.setUpgrade(1, new ItemStack(ModItems.COMPACTING_UPGRADE.get()));
            for (int i = 0; i < 3; i++) {
                upgrades.grid().set(i, new ItemStack(Items.IRON_INGOT));
            }
            upgrades.grid().set(4, new ItemStack(Items.STICK));
            upgrades.grid().set(7, new ItemStack(Items.STICK));
            upgrades.changed();
            return null;
        }), () -> Minecraft.getInstance().level != null
                && Minecraft.getInstance().level.getBlockState(chestPos).getBlock() instanceof StorageBlock,
                () -> "bloco no cliente"));
    }

    /** Abre o Baú, os dois painéis e o seletor, com as capturas e a regra conferida no servidor. */
    private static void panels(List<Step> list, String suffix) {
        list.add(new Step("Upgrades" + suffix + ": abrir o Baú com a Mesa aberta", STEP_TIMEOUT_MS, () -> useOn(chestPos),
                () -> Minecraft.getInstance().screen instanceof StorageListScreen screen
                        && screen.upgradePanels().openKind() == ChestUpgradeItem.Kind.CRAFTING
                        && screen.getMenu().slots.get(ChestUpgradeSlots.RESULT).getItem().is(Items.IRON_PICKAXE),
                () -> Minecraft.getInstance().screen instanceof StorageListScreen screen
                        ? "painel " + screen.upgradePanels().openKind() + ", resultado "
                                + screen.getMenu().slots.get(ChestUpgradeSlots.RESULT).getItem()
                        : "tela " + describe(Minecraft.getInstance().screen)));
        list.add(noClip("Upgrades" + suffix + ": Mesa"));
        list.add(capture("armazenamento-upgrade-mesa" + suffix));
        list.add(new Step("Upgrades" + suffix + ": a aba abre a Compactação", STEP_TIMEOUT_MS, () -> {
            int[] tab = panels().tabCenter(1);
            click(screen(), tab[0], tab[1]);
        }, () -> panels().openKind() == ChestUpgradeItem.Kind.COMPACTING && panels().compactingRowOf(Items.IRON_INGOT) >= 0
                && panels().compactingRowOf(Items.GOLD_INGOT) >= 0 && panels().compactingRowOf(Items.DIAMOND) >= 0
                && onServer(server -> openMenu(server).upgradeSlots().compactingPanel().watching()),
                () -> "painel " + panels().openKind() + ", " + panels().compactingRows() + " linhas"));
        list.add(noClip("Upgrades" + suffix + ": Compactação"));
        list.add(capture("armazenamento-upgrade-compactacao" + suffix));
        list.add(new Step("Upgrades" + suffix + ": clicar na forma do ferro abre o seletor", STEP_TIMEOUT_MS, () -> {
            int[] target = panels().compactingTargetCenter(panels().compactingRowOf(Items.IRON_INGOT));
            click(screen(), target[0], target[1]);
        }, () -> panels().pickerOpen(), () -> "seletor fechado"));
        list.add(noClip("Upgrades" + suffix + ": seletor"));
        list.add(capture("armazenamento-upgrade-seletor" + suffix));
        pickerSteps(list, suffix);
        list.add(new Step("Upgrades" + suffix + ": escolher o lingote", STEP_TIMEOUT_MS, () -> {
            int[] cell = panels().pickerCellCenter(1);
            click(screen(), cell[0], cell[1]);
        }, () -> !panels().pickerOpen() && panels().compactingTarget(panels().compactingRowOf(Items.IRON_INGOT)) == 1
                && onServer(server -> {
                    CompactingChain chain = CompactingChains.get().chainOf(Items.IRON_INGOT);
                    CompactingRules rules = rules(server);
                    return rules.hasOwn(chain) && rules.targetLevel(chain) == 1;
                }), () -> "no servidor " + onServer(server -> rules(server).toString())));
        list.add(new Step("Upgrades" + suffix + ": a rodinha sobre a forma anda uma", STEP_TIMEOUT_MS, () -> {
            int[] target = panels().compactingTargetCenter(panels().compactingRowOf(Items.IRON_INGOT));
            moveMouse(target[0], target[1]);
            screen().mouseScrolled(target[0], target[1], 0, 1);
        }, () -> panels().compactingTarget(panels().compactingRowOf(Items.IRON_INGOT)) == 2 && onServer(server -> {
            CompactingChain chain = CompactingChains.get().chainOf(Items.IRON_INGOT);
            return rules(server).targetLevel(chain) == 2;
        }), () -> "no servidor " + onServer(server -> rules(server).toString())));
        list.add(new Step("Upgrades" + suffix + ": a regra volta ao padrão", STEP_TIMEOUT_MS, () -> onServer(server -> {
            chest(server).upgrades().setUpgrade(1, new ItemStack(ModItems.COMPACTING_UPGRADE.get()));
            return null;
        }), () -> panels().compactingTarget(panels().compactingRowOf(Items.IRON_INGOT)) < 0,
                () -> "alvo do ferro na tela " + panels().compactingTarget(panels().compactingRowOf(Items.IRON_INGOT))));
        list.add(close("Upgrades" + suffix + ": fechar"));
    }

    /**
     * O seletor do ferro aberto: o Esc o fecha sem fechar a tela; reaberto, um estado novo que tira a cadeia do
     * diamante (antes do ferro na lista) não pode deixá-lo apontando outra cadeia, nem derrubar a dica sob o mouse.
     */
    private static void pickerSteps(List<Step> list, String suffix) {
        list.add(new Step("Upgrades" + suffix + ": Esc fecha o seletor e não a tela", STEP_TIMEOUT_MS,
                () -> screen().keyPressed(org.lwjgl.glfw.GLFW.GLFW_KEY_ESCAPE, 0, 0),
                () -> stepTicks >= 2 && Minecraft.getInstance().screen instanceof StorageListScreen && !panels().pickerOpen(),
                () -> "tela " + describe(Minecraft.getInstance().screen)));
        list.add(reopenIronPicker(suffix));
        list.add(new Step("Upgrades" + suffix + ": a cadeia do diamante sai com o seletor aberto", STEP_TIMEOUT_MS,
                () -> onServer(server -> chest(server).storage().extract(new ItemStack(Items.DIAMOND), 20, false)),
                () -> panels().compactingRowOf(Items.DIAMOND) < 0, () -> panels().compactingRows() + " linhas"));
        list.add(new Step("Upgrades" + suffix + ": o seletor segue o ferro, com o mouse em cima", STEP_TIMEOUT_MS, () -> {
            if (panels().pickerOpen()) {
                int[] cell = panels().pickerCellCenter(2);
                moveMouse(cell[0], cell[1]);
            }
        }, () -> stepTicks >= 5 && (!panels().pickerOpen()
                || panels().pickerRow() == panels().compactingRowOf(Items.IRON_INGOT)),
                () -> "seletor na linha " + panels().pickerRow() + ", ferro na " + panels().compactingRowOf(Items.IRON_INGOT)));
        list.add(new Step("Upgrades" + suffix + ": o diamante volta", STEP_TIMEOUT_MS,
                () -> onServer(server -> chest(server).storage().insert(new ItemStack(Items.DIAMOND), 20, false)),
                () -> panels().compactingRowOf(Items.DIAMOND) >= 0
                        && (!panels().pickerOpen() || panels().pickerRow() == panels().compactingRowOf(Items.IRON_INGOT)),
                () -> "seletor na linha " + panels().pickerRow() + ", ferro na " + panels().compactingRowOf(Items.IRON_INGOT)));
        list.add(reopenIronPicker(suffix));
    }

    /** Abre o seletor do ferro, se não estiver aberto nele. */
    private static Step reopenIronPicker(String suffix) {
        return new Step("Upgrades" + suffix + ": seletor do ferro aberto", STEP_TIMEOUT_MS, () -> {
            int iron = panels().compactingRowOf(Items.IRON_INGOT);
            if (panels().pickerOpen() && panels().pickerRow() != iron) {
                screen().keyPressed(org.lwjgl.glfw.GLFW.GLFW_KEY_ESCAPE, 0, 0);
            }
            if (!panels().pickerOpen()) {
                int[] target = panels().compactingTargetCenter(iron);
                click(screen(), target[0], target[1]);
            }
        }, () -> panels().pickerOpen() && panels().pickerRow() == panels().compactingRowOf(Items.IRON_INGOT),
                () -> "seletor na linha " + panels().pickerRow());
    }

    /** Alguns quadros depois, nenhum texto da tela está cortado. */
    private static Step noClip(String name) {
        return new Step(name + ": nenhum texto cortado", STEP_TIMEOUT_MS, () -> {
        }, () -> stepTicks >= 4 && GuiText.clipCount() == 0,
                () -> GuiText.clipCount() + " texto(s) cortado(s): " + E2eRangeSteps.clippedTexts());
    }

    private static StorageListScreen screen() throws StepFailure {
        if (Minecraft.getInstance().screen instanceof StorageListScreen screen) {
            return screen;
        }
        throw new StepFailure("a tela do Baú não está aberta: " + describe(Minecraft.getInstance().screen));
    }

    private static UpgradePanels panels() throws StepFailure {
        return screen().upgradePanels();
    }

    /** Na thread do servidor. */
    private static StorageChestBlockEntity chest(MinecraftServer server) {
        if (!(server.overworld().getBlockEntity(chestPos) instanceof StorageChestBlockEntity chest)) {
            throw new IllegalStateException("sem Baú Wireless em " + chestPos.toShortString());
        }
        return chest;
    }

    private static CompactingRules rules(MinecraftServer server) {
        return chest(server).upgrades().upgrade(1).getOrDefault(ModDataComponents.COMPACTING_RULES.get(),
                CompactingRules.DEFAULT);
    }

    private static StorageListMenu<?> openMenu(MinecraftServer server) {
        ServerPlayer player = server.getPlayerList().getPlayer(Minecraft.getInstance().player.getUUID());
        if (!(player.containerMenu instanceof StorageListMenu<?> menu) || menu.upgradeSlots() == null) {
            throw new IllegalStateException("o menu do Baú não está aberto no servidor");
        }
        return menu;
    }
}
