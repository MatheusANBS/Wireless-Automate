package io.github.matheusanbs.wirelessautomate.client.dev;

import static io.github.matheusanbs.wirelessautomate.client.dev.DevEndToEnd.*;
import static io.github.matheusanbs.wirelessautomate.client.dev.E2eShowcaseWorld.*;
import static io.github.matheusanbs.wirelessautomate.client.dev.E2eSteps.close;
import static io.github.matheusanbs.wirelessautomate.client.dev.E2eUi.*;
import static io.github.matheusanbs.wirelessautomate.client.dev.E2eWorld.*;

import io.github.matheusanbs.wirelessautomate.WirelessAutomate;
import io.github.matheusanbs.wirelessautomate.block.RouterBlock;
import io.github.matheusanbs.wirelessautomate.block.RouterTier;
import io.github.matheusanbs.wirelessautomate.client.storage.StorageListScreen;
import io.github.matheusanbs.wirelessautomate.client.storage.upgrade.UpgradePanels;
import io.github.matheusanbs.wirelessautomate.item.ChestUpgradeItem;
import io.github.matheusanbs.wirelessautomate.menu.ChestUpgradeSlots;
import io.github.matheusanbs.wirelessautomate.registry.ModBlocks;
import io.github.matheusanbs.wirelessautomate.registry.ModDataComponents;
import io.github.matheusanbs.wirelessautomate.registry.ModItems;
import io.github.matheusanbs.wirelessautomate.storage.ItemStorage;
import io.github.matheusanbs.wirelessautomate.storage.StorageChestBlockEntity;
import io.github.matheusanbs.wirelessautomate.storage.upgrade.ChestUpgrades;
import io.github.matheusanbs.wirelessautomate.storage.upgrade.CompactingChains;
import io.github.matheusanbs.wirelessautomate.storage.upgrade.CompactingMode;
import io.github.matheusanbs.wirelessautomate.storage.upgrade.CompactingRules;
import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;

/**
 * Os upgrades do Baú na vitrine do CurseForge ({@code WA_SHOWCASE_ONLY=bau}): um Baú a oeste da galeria dos
 * armazenamentos com a Mesa de Trabalho e a Compactação, a foto da Mesa com um pistão montado na grade
 * ({@code s25-bau-mesa}), a da Compactação no modo Compactar, com o ferro numa regra própria e a prata crua de dois mods
 * numa linha só ({@code s25b-bau-compactacao}), e o GIF ({@code gif/bau-NNN.png}): pepitas de ferro entrando no Baú
 * enquanto o modo passa de Desligada a Compactar e depois a Descompactar.
 *
 * <p>O GIF grava com o tempo do mundo congelado ({@code tickRateManager}), para o fundo não mexer entre quadros: o
 * servidor continua rodando o fim do tick (onde a Compactação converte) e o tick do jogador (que manda a lista à
 * tela). Cada quadro põe {@value #NUGGETS} pepitas no Baú pelo mesmo caminho de uma inserção de fora e só captura
 * quando a lista da tela bate com o Baú no servidor.
 */
final class E2eShowcaseChest {
    /** Quadros com o modo Desligada, depois com Compactar e por fim com Descompactar. */
    private static final int OFF_FRAMES = 24;
    private static final int COMPRESS_FRAMES = 80;
    private static final int DECOMPRESS_FRAMES = 48;
    private static final int FRAMES = OFF_FRAMES + COMPRESS_FRAMES + DECOMPRESS_FRAMES;
    private static final int NUGGETS = 4;
    /** O canto do Baú, relativo à base da vitrine: a oeste da galeria, livre no piso. */
    private static final int[] CHEST = {-6, 0, -15};
    /** As formas que a tela tem de mostrar iguais ao servidor antes de cada quadro. */
    private static final Item[] WATCHED = {Items.IRON_NUGGET, Items.IRON_INGOT, Items.IRON_BLOCK, Items.GOLD_NUGGET,
            Items.GOLD_INGOT, Items.GOLD_BLOCK, Items.COBBLESTONE};
    private static final Item MI_SILVER = item("modern_industrialization:raw_silver");
    private static final Item OCCULTISM_SILVER = item("occultism:raw_silver");

    private E2eShowcaseChest() {
    }

    private static Item item(String id) {
        return BuiltInRegistries.ITEM.get(ResourceLocation.parse(id));
    }

    static void steps(List<Step> list) {
        list.add(new Step("Baú (upgrades): fechar a tela", STEP_TIMEOUT_MS, () -> {
            Minecraft minecraft = Minecraft.getInstance();
            if (minecraft.screen != null) {
                minecraft.screen.onClose();
            }
        }, () -> Minecraft.getInstance().screen == null, () -> "tela " + describe(Minecraft.getInstance().screen)));
        list.add(placeChest("Baú (upgrades): o Baú da Mesa", E2eShowcaseChest::fillVaried));
        list.add(E2eShowcase.showLook("Baú (upgrades): câmera no Baú", CHEST[0], 0.5, CHEST[2] + 2.5, CHEST[0], 0.5,
                CHEST[2], true));
        list.add(new Step("Baú (upgrades): abrir com a Mesa", STEP_TIMEOUT_MS, () -> useOn(chest()), () -> {
            if (!(Minecraft.getInstance().screen instanceof StorageListScreen screen)) {
                return false;
            }
            if (screen.upgradePanels().openKind() != ChestUpgradeItem.Kind.CRAFTING && stepTicks % 10 == 5) {
                int[] tab = screen.upgradePanels().tabCenter(0);
                click(screen, tab[0], tab[1]);
            }
            return screen.upgradePanels().openKind() == ChestUpgradeItem.Kind.CRAFTING
                    && screen.getMenu().slots.get(ChestUpgradeSlots.RESULT).getItem().is(Items.PISTON);
        }, () -> "tela " + describe(Minecraft.getInstance().screen)));
        // a lista do JEI ocupa a sobra ao lado do painel da Mesa: uma busca sem resultado a esvazia na foto
        list.add(new Step("Baú (upgrades): JEI vazio", STEP_TIMEOUT_MS, () -> jeiFilter("@@vitrine"),
                () -> stepTicks >= 4, () -> "JEI"));
        list.add(capture("s25-bau-mesa"));
        list.add(new Step("Baú (upgrades): Compactar, o ferro no lingote", STEP_TIMEOUT_MS, () -> {
            onServer(server -> {
                CompactingRules rules = CompactingRules.DEFAULT.withMode(CompactingMode.COMPRESS)
                        .withTarget(CompactingChains.get().chainOf(Items.IRON_INGOT), Items.IRON_INGOT);
                ItemStack upgrade = new ItemStack(ModItems.COMPACTING_UPGRADE.get());
                upgrade.set(ModDataComponents.COMPACTING_RULES.get(), rules);
                chest(server).upgrades().setUpgrade(1, upgrade);
                return null;
            });
            int[] tab = panels().tabCenter(1);
            click(screen(), tab[0], tab[1]);
            moveMouse(0, 0);
        }, () -> panels().openKind() == ChestUpgradeItem.Kind.COMPACTING && stepTicks >= 10
                && panels().compactingRowOf(Items.IRON_INGOT) >= 0
                && panels().compactingTarget(panels().compactingRowOf(Items.IRON_INGOT)) == 1,
                () -> "painel " + panels().openKind() + ", " + panels().compactingRows() + " linhas"));
        list.add(capture("s25b-bau-compactacao"));
        list.add(new Step("Baú (upgrades): JEI de volta", STEP_TIMEOUT_MS, () -> jeiFilter(""), () -> true, () -> "JEI"));
        list.add(close("Baú (upgrades): fechar"));
        gif(list);
    }

    /** O GIF: um Baú novo, com a Compactação desligada e o painel dela aberto, e os quadros do roteiro. */
    private static void gif(List<Step> list) {
        list.add(placeChest("Baú (GIF): o Baú das pepitas", E2eShowcaseChest::fillGif));
        list.add(new Step("Baú (GIF): abrir com a Compactação", STEP_TIMEOUT_MS, () -> useOn(chest()), () -> {
            if (!(Minecraft.getInstance().screen instanceof StorageListScreen screen)) {
                return false;
            }
            if (screen.upgradePanels().openKind() != ChestUpgradeItem.Kind.COMPACTING && stepTicks % 10 == 5) {
                int[] tab = screen.upgradePanels().tabCenter(1);
                click(screen, tab[0], tab[1]);
                moveMouse(0, 0);
            }
            return screen.upgradePanels().openKind() == ChestUpgradeItem.Kind.COMPACTING
                    && screen.upgradePanels().compactingRowOf(Items.IRON_INGOT) >= 0 && stepTicks >= 20;
        }, () -> "tela " + describe(Minecraft.getInstance().screen)));
        list.add(new Step("Baú (GIF): congelar o tempo", STEP_TIMEOUT_MS, () -> {
            clearFrames();
            Minecraft.getInstance().getToasts().clear();
            Minecraft.getInstance().gui.getChat().clearMessages(false);
            onServer(server -> {
                server.tickRateManager().setFrozen(true);
                return null;
            });
            WirelessAutomate.LOGGER.info("vitrine: GIF do Baú com o tempo congelado, {} quadros", FRAMES);
        }, () -> !Minecraft.getInstance().level.tickRateManager().runsNormally(), () -> "cliente congelado"));
        for (int i = 0; i < FRAMES; i++) {
            list.add(frame(i));
        }
        list.add(new Step("Baú (GIF): descongelar", STEP_TIMEOUT_MS, () -> onServer(server -> {
            server.tickRateManager().setFrozen(false);
            return null;
        }), () -> Minecraft.getInstance().level.tickRateManager().runsNormally(), () -> "cliente descongelado"));
        list.add(close("Baú (GIF): fechar"));
    }

    /** O modo que o quadro clica, ou {@code null} se não clica nenhum. */
    private static CompactingMode clickedMode(int frame) {
        if (frame == OFF_FRAMES) {
            return CompactingMode.COMPRESS;
        }
        return frame == OFF_FRAMES + COMPRESS_FRAMES ? CompactingMode.DECOMPRESS : null;
    }

    private static CompactingMode modeAt(int frame) {
        return frame < OFF_FRAMES ? CompactingMode.OFF
                : frame < OFF_FRAMES + COMPRESS_FRAMES ? CompactingMode.COMPRESS : CompactingMode.DECOMPRESS;
    }

    /**
     * Um quadro: as pepitas entram no Baú (e o clique no modo, nos quadros da troca); a captura sai quando o servidor
     * já está no modo do quadro e a lista da tela mostra as mesmas quantidades que o Baú.
     */
    private static Step frame(int index) {
        String file = String.format("gif/bau-%03d", index);
        CompactingMode mode = modeAt(index);
        return new Step("Baú (GIF): quadro " + index, STEP_TIMEOUT_MS, () -> {
            CompactingMode clicked = clickedMode(index);
            if (clicked != null) {
                int[] button = panels().modeCenter(clicked);
                click(screen(), button[0], button[1]);
            }
            moveMouse(0, 0);
            onServer(server -> chest(server).storage().insert(new ItemStack(Items.IRON_NUGGET), NUGGETS, false));
        }, () -> {
            if (stepTicks < 3) {
                return false;
            }
            String server = onServer(server1 -> modeOf(server1) == mode ? counts(chest(server1).storage()) : "modo");
            if (!server.equals(clientCounts())) {
                return false;
            }
            capture(Minecraft.getInstance(), file);
            return true;
        }, () -> file + ".png: tela " + clientCounts() + ", servidor "
                + onServer(server -> modeOf(server) + " " + counts(chest(server).storage())));
    }

    private static CompactingMode modeOf(MinecraftServer server) {
        return chest(server).upgrades().upgrade(1)
                .getOrDefault(ModDataComponents.COMPACTING_RULES.get(), CompactingRules.DEFAULT).mode();
    }

    /** As quantidades das formas vigiadas no Baú do servidor. */
    private static String counts(ItemStorage storage) {
        StringBuilder out = new StringBuilder();
        for (Item item : WATCHED) {
            out.append(storage.countItem(new ItemStack(item))).append(' ');
        }
        return out.toString();
    }

    /** As mesmas quantidades, na lista da tela. */
    private static String clientCounts() throws StepFailure {
        StorageListScreen screen = screen();
        long[] amounts = new long[WATCHED.length];
        for (int i = 0; i < screen.shownCount(); i++) {
            if (screen.shownKey(i) instanceof ItemStack stack) {
                for (int k = 0; k < WATCHED.length; k++) {
                    if (stack.is(WATCHED[k])) {
                        amounts[k] += screen.shownAmount(i);
                    }
                }
            }
        }
        StringBuilder out = new StringBuilder();
        for (long amount : amounts) {
            out.append(amount).append(' ');
        }
        return out.toString();
    }

    /** Põe um Baú novo (Allthemodium com o mod, senão Esmeralda) com os dois upgrades e o conteúdo de {@code fill}. */
    private static Step placeChest(String name, java.util.function.BiConsumer<ItemStorage, ChestUpgrades> fill) {
        return new Step(name, STEP_TIMEOUT_MS, () -> {
            UUID playerId = Minecraft.getInstance().player.getUUID();
            onServer(server -> {
                server.getPlayerList().getPlayer(playerId).getInventory().clearContent();
                var level = server.overworld();
                level.setBlockAndUpdate(chest(), Blocks.AIR.defaultBlockState());
                RouterTier tier = RouterTier.ALLTHEMODIUM.loaded() ? RouterTier.ALLTHEMODIUM : RouterTier.EMERALD;
                level.setBlockAndUpdate(chest(), ModBlocks.STORAGE_CHEST.get().defaultBlockState()
                        .setValue(RouterBlock.TIER, tier));
                StorageChestBlockEntity chest = chest(server);
                ChestUpgrades upgrades = chest.upgrades();
                upgrades.setUpgrade(0, new ItemStack(ModItems.CRAFTING_UPGRADE.get()));
                upgrades.setUpgrade(1, new ItemStack(ModItems.COMPACTING_UPGRADE.get()));
                fill.accept(chest.storage(), upgrades);
                upgrades.changed();
                // o Baú anterior derruba a grade no chão ao sair; o jogador não pode pegar nada para a barra
                server.getCommands().performPrefixedCommand(server.createCommandSourceStack().withSuppressedOutput(),
                        "kill @e[type=item]");
                return null;
            });
        }, () -> Minecraft.getInstance().level.getBlockEntity(chest()) instanceof StorageChestBlockEntity,
                () -> "Baú no cliente");
    }

    /**
     * Itens variados, a prata crua dos dois mods e um pistão montado na grade. Poucas cadeias (com o AllTheCompressed,
     * carvão, cobre, lápis, vidro, tábuas, sílex e pérolas também viram linhas), para as da foto caberem no painel sem rolar.
     */
    private static void fillVaried(ItemStorage storage, ChestUpgrades upgrades) {
        Object[][] items = {{Items.COBBLESTONE, 3_000}, {Items.IRON_INGOT, 200}, {Items.IRON_NUGGET, 40},
                {Items.REDSTONE, 500}, {Items.GOLD_INGOT, 90}, {Items.GOLD_NUGGET, 25}, {Items.DIAMOND, 24},
                {Items.STICK, 128}, {Items.STRING, 32}, {Items.TORCH, 96}, {Items.FEATHER, 40}, {Items.LEATHER, 36},
                {Items.BOOK, 20}, {Items.BONE, 30}, {Items.PAPER, 64}, {Items.GUNPOWDER, 48}};
        for (Object[] entry : items) {
            storage.insert(new ItemStack((Item) entry[0]), (Integer) entry[1], false);
        }
        if (MI_SILVER != Items.AIR && OCCULTISM_SILVER != Items.AIR) {
            storage.insert(new ItemStack(MI_SILVER), 40, false);
            storage.insert(new ItemStack(OCCULTISM_SILVER), 40, false);
        }
        Item[] piston = {Items.OAK_PLANKS, Items.OAK_PLANKS, Items.OAK_PLANKS, Items.COBBLESTONE, Items.IRON_INGOT,
                Items.COBBLESTONE, Items.COBBLESTONE, Items.REDSTONE, Items.COBBLESTONE};
        for (int i = 0; i < piston.length; i++) {
            upgrades.grid().set(i, new ItemStack(piston[i]));
        }
    }

    /** Poucos tipos, para a lista caber inteira: o ferro, o ouro, pedregulho e duas coisas fora de cadeia. */
    private static void fillGif(ItemStorage storage, ChestUpgrades upgrades) {
        Object[][] items = {{Items.COBBLESTONE, 2_000}, {Items.IRON_INGOT, 27}, {Items.GOLD_INGOT, 20},
                {Items.GOLD_NUGGET, 5}, {Items.STICK, 32}, {Items.TORCH, 48}};
        for (Object[] entry : items) {
            storage.insert(new ItemStack((Item) entry[0]), (Integer) entry[1], false);
        }
    }

    /**
     * Põe {@code text} na busca do JEI, pela classe interna dele e por reflexão (o JEI é opcional e a API não expõe o
     * runtime fora do plugin); sem o JEI, ou se a classe mudar, só a foto fica com a lista dele.
     */
    private static void jeiFilter(String text) {
        try {
            Object runtime = Class.forName("mezz.jei.common.Internal").getMethod("getJeiRuntime").invoke(null);
            Object filter = runtime.getClass().getMethod("getIngredientFilter").invoke(runtime);
            filter.getClass().getMethod("setFilterText", String.class).invoke(filter, text);
        } catch (ReflectiveOperationException | RuntimeException e) {
            WirelessAutomate.LOGGER.warn("vitrine: não deu para mexer na busca do JEI", e);
        }
    }

    private static BlockPos chest() {
        return showBase.offset(CHEST[0], CHEST[1], CHEST[2]);
    }

    /** Na thread do servidor. */
    private static StorageChestBlockEntity chest(MinecraftServer server) {
        if (!(server.overworld().getBlockEntity(chest()) instanceof StorageChestBlockEntity chest)) {
            throw new IllegalStateException("sem Baú Wireless em " + chest().toShortString());
        }
        return chest;
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

    /** Apaga os quadros de uma rodada anterior, para o script não misturar. */
    private static void clearFrames() throws IOException {
        Path dir = Path.of(SHOWCASE, "gif");
        Files.createDirectories(dir);
        try (DirectoryStream<Path> old = Files.newDirectoryStream(dir, "bau-*.png")) {
            for (Path path : old) {
                Files.delete(path);
            }
        }
    }
}
