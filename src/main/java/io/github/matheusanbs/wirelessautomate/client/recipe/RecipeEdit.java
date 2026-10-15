package io.github.matheusanbs.wirelessautomate.client.recipe;

import static io.github.matheusanbs.wirelessautomate.client.recipe.RecipeItems.item;
import static io.github.matheusanbs.wirelessautomate.client.recipe.RecipeItems.resultStack;
import static io.github.matheusanbs.wirelessautomate.client.recipe.RecipeItems.tagReference;
import static io.github.matheusanbs.wirelessautomate.client.recipe.RecipeItems.tagsOf;

import io.github.matheusanbs.wirelessautomate.menu.RecipeEditorMenu;
import io.github.matheusanbs.wirelessautomate.menu.RecipeEditorSnapshot;
import io.github.matheusanbs.wirelessautomate.menu.RecipeEditorSnapshot.Row;
import io.github.matheusanbs.wirelessautomate.recipe.edit.RecipeDraft;
import io.github.matheusanbs.wirelessautomate.recipe.edit.RecipeSlot;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.jetbrains.annotations.Nullable;

/**
 * A receita escolhida e o rascunho dela: o slot escolhido, o item de onde saiu cada tag e as edições
 * (pôr e tirar item, Item/Tag, as setas das tags e a quantidade). O rascunho fica só aqui até Salvar.
 */
final class RecipeEdit {
    private final RecipeEditorMenu menu;
    private @Nullable ResourceLocation selectedId;
    private @Nullable RecipeDraft draft;
    private int selectedSlot;
    /** O item de onde saiu a tag de cada slot: as setas percorrem as tags dele e Item volta a ele. */
    private final Item[] origin = new Item[RecipeDraft.SLOTS];

    RecipeEdit(RecipeEditorMenu menu) {
        this.menu = menu;
    }

    @Nullable ResourceLocation selectedId() {
        return selectedId;
    }

    @Nullable RecipeDraft draft() {
        return draft;
    }

    int selectedSlot() {
        return selectedSlot;
    }

    void selectSlot(int i) {
        selectedSlot = i;
    }

    @Nullable Row selectedRow() {
        if (selectedId == null) {
            return null;
        }
        for (Row row : menu.snapshot().rows()) {
            if (row.id().equals(selectedId)) {
                return row;
            }
        }
        return null;
    }

    /**
     * Snapshot novo: a receita escolhida continua escolhida e o rascunho passa a ser o salvo; sem
     * receita escolhida, escolhe a primeira.
     */
    void onSnapshot(RecipeEditorSnapshot snapshot) {
        Row row = selectedRow();
        if (row == null) {
            row = snapshot.rows().isEmpty() ? null : snapshot.rows().get(0);
            select(row);
            return;
        }
        draft = row.current();
        java.util.Arrays.fill(origin, null);
    }

    void select(@Nullable Row row) {
        selectedId = row == null ? null : row.id();
        draft = row == null ? null : row.current();
        java.util.Arrays.fill(origin, null);
        selectedSlot = 0;
        if (draft != null) {
            for (int i = 0; i < RecipeDraft.SLOTS; i++) {
                if (!draft.slots().get(i).isEmpty()) {
                    selectedSlot = i;
                    break;
                }
            }
        }
    }

    boolean dirty() {
        Row row = selectedRow();
        return row != null && draft != null && !draft.equals(row.current());
    }

    boolean disabled() {
        return draft != null && draft.disabled();
    }

    /** A grade aceita itens: há receita escolhida e ela não está desativada. */
    boolean canEditGrid() {
        return draft != null && !draft.disabled();
    }

    /** O anel de subir tier: centro travado, resultado e quantidade fixos. */
    boolean ring() {
        return draft != null && draft.shape() == RecipeDraft.Shape.RING;
    }

    RecipeSlot.Kind slotKind() {
        return draft == null ? RecipeSlot.Kind.EMPTY : draft.slots().get(selectedSlot).kind();
    }

    Component tagIndexText(List<String> tags) {
        String current = draft.slots().get(selectedSlot).id();
        return Component.literal((tags.indexOf(current) + 1) + "/" + tags.size());
    }

    /** Quantidade máxima do resultado: 64 ou o tamanho de pilha do item, o menor. */
    int maxCount() {
        Row row = selectedRow();
        return row == null ? RecipeDraft.MAX_COUNT
                : Math.max(RecipeDraft.MIN_COUNT, Math.min(RecipeDraft.MAX_COUNT, resultStack(row).getMaxStackSize()));
    }

    /** O item que o slot mostra: o próprio, ou o de onde a tag saiu, ou o de referência da tag. */
    Item shownItem(int i, RecipeSlot slot) {
        return switch (slot.kind()) {
            case EMPTY -> Items.AIR;
            case ITEM -> item(slot.id());
            case TAG -> origin[i] != null ? origin[i] : tagReference(slot.id());
        };
    }

    /** As tags oferecidas no slot escolhido (as do item dele). */
    List<String> slotTags() {
        if (draft == null) {
            return List.of();
        }
        RecipeSlot slot = draft.slots().get(selectedSlot);
        List<String> tags = tagsOf(shownItem(selectedSlot, slot));
        if (slot.kind() == RecipeSlot.Kind.TAG && !tags.contains(slot.id())) {
            List<String> withOwn = new ArrayList<>(tags);
            withOwn.add(0, slot.id());
            return withOwn;
        }
        return tags;
    }

    void setSlot(int i, RecipeSlot slot) {
        if (!canEditGrid()) {
            return;
        }
        draft = draft.withSlot(i, slot);
        if (slot.kind() != RecipeSlot.Kind.TAG) {
            origin[i] = null;
        }
    }

    /** Fantasma do JEI (ou do cursor) no slot {@code i} da grade. */
    void setGhost(int i, ItemStack stack) {
        if (stack.isEmpty() || !canEditGrid() || i < 0 || i >= RecipeDraft.SLOTS || draft.locked(i)) {
            return;
        }
        selectedSlot = i;
        setSlot(i, RecipeSlot.item(BuiltInRegistries.ITEM.getKey(stack.getItem()).toString()));
    }

    void stepCount(int delta) {
        if (canEditGrid()) {
            draft = draft.withCount(Math.max(RecipeDraft.MIN_COUNT, Math.min(maxCount(), draft.count() + delta)));
        }
    }

    void toItem() {
        RecipeSlot slot = draft.slots().get(selectedSlot);
        Item item = shownItem(selectedSlot, slot);
        if (slot.kind() == RecipeSlot.Kind.TAG && item != Items.AIR) {
            setSlot(selectedSlot, RecipeSlot.item(BuiltInRegistries.ITEM.getKey(item).toString()));
        }
    }

    void toTag() {
        RecipeSlot slot = draft.slots().get(selectedSlot);
        if (slot.kind() != RecipeSlot.Kind.ITEM) {
            return;
        }
        Item item = item(slot.id());
        List<String> tags = tagsOf(item);
        if (!tags.isEmpty()) {
            setSlot(selectedSlot, RecipeSlot.tag(tags.get(0)));
            origin[selectedSlot] = item;
        }
    }

    void cycleTag(int delta) {
        List<String> tags = slotTags();
        RecipeSlot slot = draft.slots().get(selectedSlot);
        if (slot.kind() != RecipeSlot.Kind.TAG || tags.size() < 2) {
            return;
        }
        Item from = shownItem(selectedSlot, slot);
        int k = tags.indexOf(slot.id());
        String next = tags.get(Math.floorMod(k + delta, tags.size()));
        setSlot(selectedSlot, RecipeSlot.tag(next));
        // a referência da tag nova pode ser outro item: as setas continuam nas tags do mesmo
        origin[selectedSlot] = from;
    }
}
