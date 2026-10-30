package io.github.matheusanbs.wirelessautomate.menu;

import io.github.matheusanbs.wirelessautomate.item.ChestUpgradeItem;
import io.github.matheusanbs.wirelessautomate.storage.ItemStorage;
import io.github.matheusanbs.wirelessautomate.storage.StorageChestBlockEntity;
import io.github.matheusanbs.wirelessautomate.storage.upgrade.ChestUpgrades;
import java.util.Optional;
import net.minecraft.core.NonNullList;
import net.minecraft.network.protocol.game.ClientboundContainerSetSlotPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.ResultContainer;
import net.minecraft.world.inventory.ResultSlot;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

/**
 * Os slots dos upgrades no menu do Baú: os dois slots de upgrade, a grade 3×3 e o resultado da Mesa de Trabalho,
 * depois dos 36 do inventário. A grade é do Baú ({@link ChestCraftGrid}) e o resultado é de cada menu; toda mudança
 * dos upgrades ou da grade, venha de que menu vier, recalcula o resultado de todos os menus abertos no Baú (cada um
 * se registra no {@link ChestUpgrades} na abertura e sai no {@link #removed()}).
 *
 * <p>No cliente os slots usam contêineres locais, que a sincronização normal do menu preenche; o servidor decide
 * tudo (o resultado, a reposição, o Shift + clique).
 */
public final class ChestUpgradeSlots {
    public static final int UPGRADE_FIRST = StorageListMenu.INVENTORY_SLOTS;
    public static final int GRID_FIRST = UPGRADE_FIRST + ChestUpgrades.SLOTS;
    public static final int RESULT = GRID_FIRST + ChestUpgrades.GRID_SIZE;
    /** Posição dos slots que a tela não mostra: longe de qualquer tela, como o inventário antes do {@code placeInventory}. */
    public static final int HIDDEN = -10_000;

    private final StorageListMenu<?> menu;
    private final Player player;
    private final @Nullable StorageChestBlockEntity chest;
    private final Container upgrades;
    private final ChestCraftGrid grid;
    private final ResultContainer result = new ResultContainer();
    private final Runnable refresher = this::refreshAndSend;

    ChestUpgradeSlots(StorageListMenu<?> menu, Player player, @Nullable StorageChestBlockEntity chest) {
        this.menu = menu;
        this.player = player;
        this.chest = chest;
        if (chest != null) {
            ChestUpgrades chestUpgrades = chest.upgrades();
            this.upgrades = new UpgradeContainer(chestUpgrades);
            this.grid = new ChestCraftGrid(chestUpgrades.grid(), chestUpgrades::changed);
        } else {
            this.upgrades = new SimpleContainer(ChestUpgrades.SLOTS) {
                @Override
                public int getMaxStackSize() {
                    return 1;
                }
            };
            this.grid = new ChestCraftGrid(NonNullList.withSize(ChestUpgrades.GRID_SIZE, ItemStack.EMPTY), () -> {
            });
        }
        for (int i = 0; i < ChestUpgrades.SLOTS; i++) {
            menu.add(new UpgradeSlot(i, HIDDEN, HIDDEN));
        }
        for (int i = 0; i < ChestUpgrades.GRID_SIZE; i++) {
            menu.add(new GridSlot(i, HIDDEN, HIDDEN));
        }
        menu.add(new ChestResultSlot(HIDDEN, HIDDEN));
        if (chest != null) {
            chest.upgrades().listen(refresher);
            // A sincronização inicial da abertura leva o resultado: nada a mandar agora.
            refresh(false);
        }
    }

    /**
     * Cliente: põe os slots na tela. A coluna dos upgrades desce a partir de ({@code upgradeX}, {@code upgradeY});
     * a grade e o resultado que a tela não mostra recebem {@link #HIDDEN}. Como o {@code placeInventory}, troca cada
     * slot por um igual na posição nova, com o mesmo índice.
     */
    public void place(int upgradeX, int upgradeY, int gridX, int gridY, int resultX, int resultY) {
        for (int i = 0; i < ChestUpgrades.SLOTS; i++) {
            move(UPGRADE_FIRST + i, upgradeX, upgradeY == HIDDEN ? HIDDEN : upgradeY + i * 18);
        }
        for (int i = 0; i < ChestUpgrades.GRID_SIZE; i++) {
            move(GRID_FIRST + i, gridX == HIDDEN ? HIDDEN : gridX + (i % ChestCraftGrid.SIZE) * 18,
                    gridY == HIDDEN ? HIDDEN : gridY + (i / ChestCraftGrid.SIZE) * 18);
        }
        move(RESULT, resultX, resultY);
    }

    private void move(int index, int x, int y) {
        Slot old = menu.slots.get(index);
        if (old.x == x && old.y == y) {
            return;
        }
        Slot moved;
        if (index < GRID_FIRST) {
            moved = new UpgradeSlot(index - UPGRADE_FIRST, x, y);
        } else if (index < RESULT) {
            moved = new GridSlot(index - GRID_FIRST, x, y);
        } else {
            moved = new ChestResultSlot(x, y);
        }
        moved.index = old.index;
        menu.slots.set(index, moved);
    }

    /** Há um Upgrade de Mesa de Trabalho num dos slots. */
    public boolean craftingActive() {
        return hasKind(ChestUpgradeItem.Kind.CRAFTING);
    }

    /** Há um Upgrade de Compactação num dos slots. */
    public boolean compactingActive() {
        return hasKind(ChestUpgradeItem.Kind.COMPACTING);
    }

    private boolean hasKind(ChestUpgradeItem.Kind kind) {
        for (int i = 0; i < ChestUpgrades.SLOTS; i++) {
            if (upgrades.getItem(i).getItem() instanceof ChestUpgradeItem item && item.kind() == kind) {
                return true;
            }
        }
        return false;
    }

    // ------------------------------------------------------------------ resultado

    private void refreshAndSend() {
        refresh(true);
    }

    /**
     * Procura a receita da grade e põe o resultado no slot (servidor). Copiado do
     * {@code CraftingMenu.slotChangedCraftingGrid}, que é {@code protected static} lá, com a receita anterior como
     * dica; {@code send} manda o slot ao jogador, como lá.
     */
    @SuppressWarnings("unchecked")
    private void refresh(boolean send) {
        Level level = player.level();
        if (chest == null || !(player instanceof ServerPlayer serverPlayer) || level.getServer() == null) {
            return;
        }
        ItemStack item = ItemStack.EMPTY;
        RecipeHolder<?> used = null;
        if (craftingActive()) {
            CraftingInput input = grid.asCraftInput();
            RecipeHolder<?> last = result.getRecipeUsed();
            RecipeHolder<CraftingRecipe> hint = last != null && last.value() instanceof CraftingRecipe
                    ? (RecipeHolder<CraftingRecipe>) last : null;
            Optional<RecipeHolder<CraftingRecipe>> found = level.getServer().getRecipeManager()
                    .getRecipeFor(RecipeType.CRAFTING, input, level, hint);
            if (found.isPresent() && result.setRecipeUsed(level, serverPlayer, found.get())) {
                ItemStack made = found.get().value().assemble(input, level.registryAccess());
                if (made.isItemEnabled(level.enabledFeatures())) {
                    item = made;
                    used = found.get();
                }
            }
        }
        result.setRecipeUsed(used);
        result.setItem(0, item);
        if (send) {
            menu.setRemoteSlot(RESULT, item);
            if (serverPlayer.connection != null) {
                serverPlayer.connection.send(new ClientboundContainerSetSlotPacket(menu.containerId,
                        menu.incrementStateId(), RESULT, item));
            }
        }
    }

    /**
     * A receita do resultado ainda casa com a grade. Outro menu (ou o JEI, ou a devolução) pode ter mudado a grade
     * depois do cálculo: sem esta conferência, o resultado velho sairia sem consumir os ingredientes.
     */
    private boolean recipeStillMatches() {
        RecipeHolder<?> used = result.getRecipeUsed();
        return craftingActive() && used != null && used.value() instanceof CraftingRecipe recipe
                && recipe.matches(grid.asCraftInput(), player.level());
    }

    /**
     * Repõe pelo estoque os slots da grade que o craft diminuiu: só o slot que ficou vazio ou com menos do mesmo item
     * (item e componentes); o resto da receita que ficou num slot (o balde vazio) não é tocado. Faltando o item e
     * com a Compactação no Baú, desce antes a forma de cima.
     */
    private void refill(ItemStack[] before) {
        ItemStorage storage = chest.storage();
        boolean changed = false;
        for (int i = 0; i < before.length; i++) {
            ItemStack was = before[i];
            if (was.isEmpty()) {
                continue;
            }
            ItemStack now = grid.getItem(i);
            if (!now.isEmpty() && !ItemStack.isSameItemSameComponents(now, was)) {
                continue;
            }
            int missing = was.getCount() - now.getCount();
            if (missing <= 0) {
                continue;
            }
            ItemStack key = was.copyWithCount(1);
            if (storage.count(key) < missing) {
                chest.compacting().unpackFor(key, missing);
            }
            int got = (int) storage.extract(key, missing, false);
            if (got <= 0) {
                continue;
            }
            if (now.isEmpty()) {
                grid.setItem(i, key.copyWithCount(got));
            } else {
                now.grow(got);
            }
            changed = true;
        }
        if (changed) {
            grid.setChanged();
        }
    }

    // ------------------------------------------------------------------ cliques

    /** O clique que o menu não deve tratar do jeito comum: o Shift + clique no resultado. Devolve se tratou. */
    boolean clicked(int slotId, ClickType type) {
        if (slotId == RESULT && type == ClickType.QUICK_MOVE) {
            quickCraft();
            return true;
        }
        return false;
    }

    /**
     * Shift + clique no resultado (servidor): repete o craft, repondo a grade a cada vez, enquanto o resultado for o
     * mesmo item e a soma não passar de uma pilha dele; para no primeiro que não couber inteiro no inventário. O
     * {@code onTake} vem antes de mover: se ele recusar (a receita deixou de casar), a pilha zera e nada entra. Os
     * restos da receita que o {@code onTake} põe no inventário podem tomar o lugar conferido: o que não couber cai
     * no chão, como no {@code CraftingMenu}, e o laço para.
     */
    private void quickCraft() {
        Slot slot = menu.slots.get(RESULT);
        ItemStack first = slot.getItem();
        if (chest == null || first.isEmpty()) {
            return;
        }
        ItemStack kind = first.copy();
        int max = first.getMaxStackSize();
        int moved = 0;
        while (moved < max) {
            ItemStack item = slot.getItem();
            if (item.isEmpty() || !ItemStack.isSameItemSameComponents(item, kind) || !slot.mayPickup(player)
                    || !fitsInInventory(item)) {
                return;
            }
            ItemStack taken = slot.remove(item.getCount());
            slot.onTake(player, taken);
            if (taken.isEmpty()) {
                return;
            }
            moved += taken.getCount();
            menu.moveToInventory(taken);
            if (!taken.isEmpty()) {
                player.drop(taken, false);
                return;
            }
        }
    }

    /** A pilha inteira cabe nos slots do inventário do menu. */
    private boolean fitsInInventory(ItemStack stack) {
        int room = 0;
        for (int i = 0; i < StorageListMenu.INVENTORY_SLOTS && room < stack.getCount(); i++) {
            Slot slot = menu.slots.get(i);
            ItemStack there = slot.getItem();
            int max = Math.min(stack.getMaxStackSize(), slot.getMaxStackSize(stack));
            if (there.isEmpty()) {
                room += max;
            } else if (ItemStack.isSameItemSameComponents(there, stack)) {
                room += Math.max(0, max - there.getCount());
            }
        }
        return room >= stack.getCount();
    }

    /**
     * Shift + clique num slot dos upgrades (servidor). O resultado repete o craft ({@link #quickCraft()}), nunca
     * guarda o item feito sem consumir a grade; a grade vai para o Baú e o que não couber para o inventário; o
     * upgrade vai para o inventário.
     */
    ItemStack quickMove(int index) {
        if (index == RESULT) {
            quickCraft();
            return ItemStack.EMPTY;
        }
        Slot slot = menu.slots.get(index);
        ItemStack stack = slot.getItem();
        if (stack.isEmpty() || chest == null) {
            return ItemStack.EMPTY;
        }
        if (index < GRID_FIRST) {
            // O upgrade sai pelo remove do slot, que devolve antes a grade; se ela tomou o lugar no inventário, ele cai.
            if (fitsInInventory(stack)) {
                ItemStack taken = slot.remove(stack.getCount());
                menu.moveToInventory(taken);
                if (!taken.isEmpty()) {
                    player.drop(taken, false);
                }
            }
            return ItemStack.EMPTY;
        }
        long accepted = chest.storage().insert(stack, stack.getCount(), false);
        if (accepted > 0) {
            slot.remove((int) accepted);
        }
        ItemStack left = slot.getItem();
        if (!left.isEmpty() && menu.moveToInventory(left)) {
            if (left.isEmpty()) {
                slot.set(ItemStack.EMPTY);
            } else {
                slot.setChanged();
            }
        }
        return ItemStack.EMPTY;
    }

    /** A grade mudou por fora dos slots, e quem mudou avisou o menu: recalcula o resultado deste menu. */
    void slotsChanged(Container container) {
        if (container == grid) {
            refresh(true);
        }
    }

    /** O botão Devolver: a grade inteira para o Baú (servidor). */
    boolean returnGrid(Player who) {
        return chest != null && chest.returnGrid(who);
    }

    /** O menu fechou: este menu deixa de ser avisado. A grade fica no Baú. */
    void removed() {
        if (chest != null) {
            chest.upgrades().unlisten(refresher);
        }
    }

    // ------------------------------------------------------------------ slots e contêineres

    /**
     * Um slot de upgrade: só um upgrade do Baú, um só, e de um tipo que o outro slot não tem. A Mesa que sai pelas
     * mãos do jogador devolve antes a grade com ele ({@link StorageChestBlockEntity#returnGrid}: o excesso vai para o
     * inventário dele); sem jogador, o Baú devolve sozinho e o excesso cai em cima do bloco.
     */
    private final class UpgradeSlot extends Slot {
        UpgradeSlot(int slot, int x, int y) {
            super(upgrades, slot, x, y);
        }

        @Override
        public boolean mayPlace(ItemStack stack) {
            return ChestUpgrades.fits(stack, upgrades.getItem(ChestUpgrades.SLOTS - 1 - getContainerSlot()));
        }

        @Override
        public int getMaxStackSize() {
            return 1;
        }

        @Override
        public int getMaxStackSize(ItemStack stack) {
            return 1;
        }

        @Override
        public ItemStack remove(int amount) {
            if (amount > 0) {
                beforeLeaving(ItemStack.EMPTY);
            }
            return super.remove(amount);
        }

        @Override
        public void setByPlayer(ItemStack next, ItemStack old) {
            beforeLeaving(next);
            super.setByPlayer(next, old);
        }

        private void beforeLeaving(ItemStack next) {
            if (chest != null && isCrafting(getItem()) && !isCrafting(next) && !chest.upgrades().gridEmpty()) {
                chest.returnGrid(player);
            }
        }
    }

    private static boolean isCrafting(ItemStack stack) {
        return stack.getItem() instanceof ChestUpgradeItem item && item.kind() == ChestUpgradeItem.Kind.CRAFTING;
    }

    /** Um slot da grade: aceita item só com a Mesa no slot; tirar, sempre. */
    private final class GridSlot extends Slot {
        GridSlot(int slot, int x, int y) {
            super(grid, slot, x, y);
        }

        @Override
        public boolean mayPlace(ItemStack stack) {
            return craftingActive();
        }
    }

    /**
     * O resultado: confere a receita de novo antes de entregar ({@link #recipeStillMatches()}), consome a grade como
     * a mesa vanilla e repõe pelo estoque ({@link #refill}). Os avisos da grade durante o craft se juntam num só.
     */
    private final class ChestResultSlot extends ResultSlot {
        ChestResultSlot(int x, int y) {
            super(player, grid, result, 0, x, y);
        }

        @Override
        public boolean mayPickup(Player who) {
            if (chest == null || !hasItem() || recipeStillMatches()) {
                return true;
            }
            refresh(true);
            return false;
        }

        @Override
        public void onTake(Player who, ItemStack stack) {
            if (chest != null && !recipeStillMatches()) {
                // A pilha já saiu do slot: zerada, não entrega nada.
                stack.setCount(0);
                refresh(true);
                return;
            }
            grid.beginBatch();
            try {
                ItemStack[] before = new ItemStack[grid.getContainerSize()];
                for (int i = 0; i < before.length; i++) {
                    before[i] = grid.getItem(i).copy();
                }
                super.onTake(who, stack);
                if (chest != null) {
                    refill(before);
                }
            } finally {
                grid.endBatch();
            }
        }
    }

    /** Os dois slots de upgrade, lidos e gravados direto no Baú (servidor). */
    private static final class UpgradeContainer implements Container {
        private final ChestUpgrades upgrades;

        UpgradeContainer(ChestUpgrades upgrades) {
            this.upgrades = upgrades;
        }

        @Override
        public int getContainerSize() {
            return ChestUpgrades.SLOTS;
        }

        @Override
        public boolean isEmpty() {
            return upgrades.upgrade(0).isEmpty() && upgrades.upgrade(1).isEmpty();
        }

        @Override
        public ItemStack getItem(int slot) {
            return slot >= 0 && slot < ChestUpgrades.SLOTS ? upgrades.upgrade(slot) : ItemStack.EMPTY;
        }

        @Override
        public ItemStack removeItem(int slot, int amount) {
            ItemStack stack = getItem(slot);
            if (stack.isEmpty() || amount <= 0) {
                return ItemStack.EMPTY;
            }
            ItemStack taken = stack.split(amount);
            upgrades.setUpgrade(slot, stack.isEmpty() ? ItemStack.EMPTY : stack);
            return taken;
        }

        @Override
        public ItemStack removeItemNoUpdate(int slot) {
            ItemStack stack = getItem(slot);
            if (!stack.isEmpty()) {
                upgrades.setUpgrade(slot, ItemStack.EMPTY);
            }
            return stack;
        }

        @Override
        public void setItem(int slot, ItemStack stack) {
            if (slot >= 0 && slot < ChestUpgrades.SLOTS) {
                upgrades.setUpgrade(slot, stack);
            }
        }

        @Override
        public int getMaxStackSize() {
            return 1;
        }

        @Override
        public boolean canPlaceItem(int slot, ItemStack stack) {
            return upgrades.accepts(slot, stack);
        }

        @Override
        public void setChanged() {
            upgrades.changed();
        }

        @Override
        public boolean stillValid(Player player) {
            return true;
        }

        @Override
        public void clearContent() {
            for (int i = 0; i < ChestUpgrades.SLOTS; i++) {
                upgrades.setUpgrade(i, ItemStack.EMPTY);
            }
        }
    }
}
