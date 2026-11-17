package io.github.matheusanbs.wirelessautomate.client.dev.vitrine;

import io.github.matheusanbs.wirelessautomate.block.RouterBlock;
import io.github.matheusanbs.wirelessautomate.block.RouterTier;
import io.github.matheusanbs.wirelessautomate.client.dev.DevEndToEnd.Step;
import io.github.matheusanbs.wirelessautomate.client.storage.StorageListScreen;
import io.github.matheusanbs.wirelessautomate.item.ChestUpgradeItem;
import io.github.matheusanbs.wirelessautomate.menu.ChestUpgradeSlots;
import io.github.matheusanbs.wirelessautomate.registry.ModBlocks;
import io.github.matheusanbs.wirelessautomate.registry.ModDataComponents;
import io.github.matheusanbs.wirelessautomate.registry.ModItems;
import io.github.matheusanbs.wirelessautomate.storage.StorageChestBlockEntity;
import io.github.matheusanbs.wirelessautomate.storage.upgrade.CompactingChains;
import io.github.matheusanbs.wirelessautomate.storage.upgrade.CompactingMode;
import io.github.matheusanbs.wirelessautomate.storage.upgrade.CompactingRules;
import java.util.List;
import java.util.function.Supplier;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * O Baú Wireless e os dois upgrades: a câmera chega, o Baú abre já com a Mesa (o pistão montado na grade sai com um
 * clique e a grade se repõe sozinha pelo estoque do Baú) e o cursor leva o upgrade da Compactação do inventário ao
 * segundo espaço, que passa a Compactar, e as pepitas de ferro que chegam viram lingotes na lista.
 */
final class CenaBau {
    static final String NOME = "bau";
    private static final long IR_MS = 330;
    private static final long DEPOIS_MS = 220;
    private static int tique;

    private CenaBau() {
    }

    static void passos(List<Step> lista, int indice) {
        Supplier<BlockPos> bau = () -> Palco.base(indice);
        lista.add(Palco.montar(NOME, nivel -> montar(nivel, bau.get()), bau));
        Palco.chao(lista, NOME, () -> bau.get().offset(0, -1, 2), 22);
        Supplier<double[]> olho = () -> new double[] {bau.get().getX() + 0.5, bau.get().getY() + 2.0, bau.get().getZ() + 3.2};
        Supplier<double[]> alvo = () -> new double[] {bau.get().getX() + 0.5, bau.get().getY() + 0.5, bau.get().getZ() + 0.5};
        Supplier<double[]> longe = () -> new double[] {bau.get().getX() + 4.2, bau.get().getY() + 2.6, bau.get().getZ() + 5.6};
        lista.add(Passos.ate("câmera no Baú", () -> Camera.saltar(longe.get(), alvo.get()), () -> Camera.em(longe.get())));
        lista.add(Passos.esperar(1200));
        lista.add(Gravador.iniciar(NOME));
        lista.add(Camera.mover(1700, t -> Camera.entre(new double[][] {longe.get(), alvo.get()},
                new double[][] {olho.get(), alvo.get()}, t)));
        lista.add(Passos.esperar(1500));
        lista.add(Palco.usar("abrir o Baú", bau, () -> Minecraft.getInstance().screen instanceof StorageListScreen));
        lista.add(Cursor.centralizar());
        lista.add(Passos.ate("a Mesa com o pistão", () -> tela().upgradePanels().openKind() == ChestUpgradeItem.Kind.CRAFTING
                && tela().getMenu().slots.get(ChestUpgradeSlots.RESULT).getItem().is(Items.PISTON)));
        lista.add(Passos.esperar(500));
        // dois cliques no resultado: o pistão sai e a grade se repõe do Baú
        for (int i = 0; i < 2; i++) {
            lista.add(Cursor.ir(() -> centro(ChestUpgradeSlots.RESULT), IR_MS));
            lista.add(Passos.esperar(100));
            lista.add(Passos.agir("pegar o pistão", () -> clique(ChestUpgradeSlots.RESULT, ClickType.PICKUP)));
            lista.add(Passos.esperar(DEPOIS_MS));
        }
        lista.add(Cursor.ir(() -> centro(inventario(5)), IR_MS));
        lista.add(Passos.esperar(100));
        lista.add(Passos.agir("guardar os pistões", () -> clique(inventario(5), ClickType.PICKUP)));
        lista.add(Passos.esperar(DEPOIS_MS));
        // o upgrade da Compactação, do inventário para o segundo espaço
        lista.add(Cursor.ir(() -> centro(inventario(0)), IR_MS));
        lista.add(Passos.agir("pegar o upgrade da Compactação", () -> clique(inventario(0), ClickType.PICKUP)));
        lista.add(Cursor.ir(() -> centro(ChestUpgradeSlots.UPGRADE_FIRST + 1), IR_MS));
        lista.add(Passos.agir("pôr o upgrade da Compactação", () -> clique(ChestUpgradeSlots.UPGRADE_FIRST + 1, ClickType.PICKUP)));
        lista.add(Passos.esperar(DEPOIS_MS));
        Cursor.tocar(lista, () -> tela().upgradePanels().tabCenter(1), IR_MS, DEPOIS_MS);
        lista.add(Passos.ate("aba da Compactação", () -> tela().upgradePanels().openKind() == ChestUpgradeItem.Kind.COMPACTING));
        Cursor.tocar(lista, () -> tela().upgradePanels().modeCenter(CompactingMode.COMPRESS), IR_MS, 100);
        lista.add(Passos.durante("pepitas chegando", 1800, t -> pepitas(bau.get())));
        lista.add(Gravador.parar());
    }

    private static StorageListScreen tela() {
        return (StorageListScreen) Minecraft.getInstance().screen;
    }

    private static int[] centro(int indice) {
        Slot slot = tela().getMenu().slots.get(indice);
        return new int[] {tela().getGuiLeft() + slot.x + 8, tela().getGuiTop() + slot.y + 8};
    }

    private static void clique(int indice, ClickType tipo) {
        var jogo = Minecraft.getInstance();
        jogo.gameMode.handleInventoryMouseClick(tela().getMenu().containerId, indice, 0, tipo, jogo.player);
    }

    /** O índice, no menu, do slot {@code 9 + n} do inventário do jogador. */
    private static int inventario(int n) {
        var slots = tela().getMenu().slots;
        for (int i = 0; i < slots.size(); i++) {
            if (slots.get(i).container instanceof net.minecraft.world.entity.player.Inventory
                    && slots.get(i).getContainerSlot() == 9 + n) {
                return i;
            }
        }
        throw new IllegalStateException("sem o slot " + n + " do inventário");
    }

    /** Nove pepitas de ferro a cada 0,3 s, pelo caminho de uma inserção de fora. */
    private static void pepitas(BlockPos bau) {
        if (tique++ % 6 == 0) {
            MinecraftServer servidor = Minecraft.getInstance().getSingleplayerServer();
            servidor.execute(() -> chest(servidor, bau).storage().insert(new ItemStack(Items.IRON_NUGGET), 9, false));
        }
    }

    private static StorageChestBlockEntity chest(MinecraftServer servidor, BlockPos pos) {
        return (StorageChestBlockEntity) servidor.overworld().getBlockEntity(pos);
    }

    /** O Baú com a Mesa (um pistão montado na grade) e a Compactação desligada, com o ferro como alvo. */
    private static void montar(net.minecraft.server.level.ServerLevel nivel, BlockPos pos) {
        Palco.praca(nivel, pos.offset(0, -1, 2), 9, 8);
        RouterTier tier = RouterTier.ALLTHEMODIUM.loaded() ? RouterTier.ALLTHEMODIUM : RouterTier.EMERALD;
        nivel.setBlockAndUpdate(pos, ModBlocks.STORAGE_CHEST.get().defaultBlockState().setValue(RouterBlock.TIER, tier));
        var bau = (StorageChestBlockEntity) nivel.getBlockEntity(pos);
        var upgrades = bau.upgrades();
        ItemStack compactacao = new ItemStack(ModItems.COMPACTING_UPGRADE.get());
        compactacao.set(ModDataComponents.COMPACTING_RULES.get(), CompactingRules.DEFAULT.withMode(CompactingMode.OFF)
                .withTarget(CompactingChains.get().chainOf(Items.IRON_INGOT), Items.IRON_INGOT));
        var inventario = nivel.getServer().getPlayerList().getPlayers().get(0).getInventory();
        upgrades.setUpgrade(0, new ItemStack(ModItems.CRAFTING_UPGRADE.get()));
        inventario.setItem(9, compactacao);
        Object[][] itens = {{Items.OAK_PLANKS, 9}, {Items.COBBLESTONE, 2_000}, {Items.IRON_INGOT, 27}, {Items.REDSTONE, 40},
                {Items.GOLD_INGOT, 20}, {Items.STICK, 32}, {Items.TORCH, 48}};
        for (Object[] item : itens) {
            bau.storage().insert(new ItemStack((Item) item[0]), (Integer) item[1], false);
        }
        Item[] pistao = {Items.OAK_PLANKS, Items.OAK_PLANKS, Items.OAK_PLANKS, Items.COBBLESTONE, Items.IRON_INGOT,
                Items.COBBLESTONE, Items.COBBLESTONE, Items.REDSTONE, Items.COBBLESTONE};
        for (int i = 0; i < pistao.length; i++) {
            upgrades.grid().set(i, new ItemStack(pistao[i]));
        }
        upgrades.changed();
    }
}
