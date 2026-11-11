package io.github.matheusanbs.wirelessautomate.dock;

import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.ItemHandlerHelper;
import org.jetbrains.annotations.Nullable;

/**
 * O {@code ItemHandlerHelper.insertItemStacked} da entrega e do pendente (de verdade, nunca simulado), com a dica do
 * slot que recebeu a pilha anterior: um pedido de 4.096 minérios são 64 pilhas iguais, e o helper varre todos os slots
 * da máquina para cada uma (17 leituras na Infinite Factory). Com a dica, a pilha seguinte do mesmo item vai direto ao
 * slot da anterior, se ele ainda guarda esse item; o que não couber, e o item diferente, seguem pela varredura do helper.
 *
 * <p>O resultado é o mesmo da varredura: o slot da dica é o primeiro que aceitou o item nesta chamada da entrega, e
 * os de antes dele que guardam o item já recusaram (estão cheios), sem nada entre uma pilha e a seguinte além desta
 * inserção. Por isso a dica só vale dentro de uma entrega ou de uma tentativa do pendente: quem chama a esquece no
 * começo ({@link #reset}). Uma por lado ({@link DockSide#stacker}); só a thread do servidor mexe aqui.
 *
 * <p>A pilha maior que o tamanho do item (a entrega junta as pilhas iguais de um pedido, {@link #takesOversized}) entra
 * inteira num inventário do Mekanism, que limita cada slot sozinho; em qualquer outro, é repartida no tamanho do item,
 * até a primeira parte que não couber inteira (um inventário comum pode guardar a pilha como veio e quebrar ao salvar).
 */
final class DockStacker {
    /**
     * Os inventários que aceitam uma pilha maior que o tamanho do item e guardam no slot só o que cabe: os do Mekanism
     * (o {@code BasicInventorySlot} conta o limite do slot e devolve o resto), que as fábricas do Mekanism Extras também
     * usam. Pela classe, uma vez por classe, sem tipos do Mekanism.
     */
    private static final ClassValue<Boolean> OVERSIZED = new ClassValue<>() {
        @Override
        protected Boolean computeValue(Class<?> type) {
            return type.getName().startsWith("mekanism.");
        }
    };

    private @Nullable IItemHandler handler;
    private int slot;

    /** O inventário guarda no slot só o que cabe de uma pilha maior que o tamanho do item (ver {@link #OVERSIZED}). */
    static boolean takesOversized(IItemHandler handler) {
        return OVERSIZED.get(handler.getClass());
    }

    /** Esquece a dica: a máquina pode ter mudado desde a última inserção. */
    void reset() {
        handler = null;
    }

    /**
     * Insere {@code stack} de verdade em {@code target} (ver a classe); devolve a sobra, como o helper ({@code stack}
     * mesmo, se nada entrou).
     */
    ItemStack insert(IItemHandler target, ItemStack stack) {
        if (stack.isEmpty()) {
            return stack;
        }
        int max = stack.getMaxStackSize();
        if (stack.getCount() > max && !takesOversized(target)) {
            int left = stack.getCount();
            while (left > 0) {
                int part = Math.min(left, max);
                ItemStack rest = insertStacked(target, stack.copyWithCount(part));
                left -= part - rest.getCount();
                if (!rest.isEmpty()) {
                    break;
                }
            }
            return left == stack.getCount() ? stack : stack.copyWithCount(left);
        }
        return insertStacked(target, stack);
    }

    private ItemStack insertStacked(IItemHandler target, ItemStack stack) {
        if (!stack.isStackable()) {
            handler = null;
            return ItemHandlerHelper.insertItemStacked(target, stack, false);
        }
        if (target == handler && slot < target.getSlots()
                && ItemStack.isSameItemSameComponents(target.getStackInSlot(slot), stack)) {
            stack = target.insertItem(slot, stack, false);
            if (stack.isEmpty()) {
                return stack;
            }
        }
        int slots = target.getSlots();
        for (int i = 0; i < slots; i++) {
            if (ItemStack.isSameItemSameComponents(target.getStackInSlot(i), stack)) {
                stack = target.insertItem(i, stack, false);
                if (stack.isEmpty()) {
                    remember(target, i);
                    return stack;
                }
            }
        }
        for (int i = 0; i < slots; i++) {
            if (target.getStackInSlot(i).isEmpty()) {
                stack = target.insertItem(i, stack, false);
                if (stack.isEmpty()) {
                    remember(target, i);
                    return stack;
                }
            }
        }
        handler = null;
        return stack;
    }

    private void remember(IItemHandler target, int at) {
        handler = target;
        slot = at;
    }
}
