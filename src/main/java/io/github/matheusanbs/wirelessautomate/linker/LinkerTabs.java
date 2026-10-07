package io.github.matheusanbs.wirelessautomate.linker;

import io.github.matheusanbs.wirelessautomate.network.ResourceType;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;

/**
 * Abas que o Vinculador vincula ou desvincula (lógica pura): um conjunto de {@link ResourceType}
 * guardado como máscara de bits pelo {@code ordinal}. Químicos podem estar no conjunto mesmo sem o
 * Mekanism (o item sobrevive à troca de instância); sem ele, {@link #effective(boolean)} os ignora.
 *
 * <p>"Todos" é {@link #ALL}, as quatro abas. Com o Mekanism ausente, um conjunto com Itens, Fluidos
 * e Energia também conta como Todos ({@link #isAll(boolean)}).
 *
 * <p>Atalhos da roda do mouse ({@link #next}): Todos → Itens → Fluidos → Energia → Químicos (só com
 * o Mekanism) → Todos. Uma combinação que não é atalho (por exemplo Itens + Fluidos) vai para Todos,
 * nos dois sentidos.
 */
public record LinkerTabs(int mask) {
    private static final ResourceType[] TYPES = ResourceType.values();
    private static final int FULL = (1 << TYPES.length) - 1;

    /** Todas as abas. */
    public static final LinkerTabs ALL = new LinkerTabs(FULL);
    /** Nenhuma aba (a tela não deixa chegar aqui, mas um item vindo de fora pode ter só Químicos). */
    public static final LinkerTabs NONE = new LinkerTabs(0);

    public LinkerTabs {
        mask &= FULL;
    }

    public static LinkerTabs of(ResourceType... types) {
        return of(List.of(types));
    }

    public static LinkerTabs of(Collection<ResourceType> types) {
        int mask = 0;
        for (ResourceType type : types) {
            mask |= bit(type);
        }
        return new LinkerTabs(mask);
    }

    /**
     * Do formato salvo (nomes em minúsculas, como {@code "item"}). Nomes desconhecidos são ignorados,
     * para um item de outra versão não quebrar.
     */
    public static LinkerTabs fromNames(Collection<String> names) {
        int mask = 0;
        for (String name : names) {
            for (ResourceType type : TYPES) {
                if (key(type).equals(name)) {
                    mask |= bit(type);
                }
            }
        }
        return new LinkerTabs(mask);
    }

    /** Nomes em minúsculas, na ordem das abas. */
    public List<String> names() {
        List<String> names = new ArrayList<>();
        for (ResourceType type : types()) {
            names.add(key(type));
        }
        return names;
    }

    public static String key(ResourceType type) {
        return type.name().toLowerCase(Locale.ROOT);
    }

    public boolean contains(ResourceType type) {
        return (mask & bit(type)) != 0;
    }

    /** Marca ou desmarca a aba. */
    public LinkerTabs toggle(ResourceType type) {
        return new LinkerTabs(mask ^ bit(type));
    }

    /** Todas as abas guardadas, na ordem das abas (inclusive Químicos sem o Mekanism). */
    public List<ResourceType> types() {
        List<ResourceType> list = new ArrayList<>();
        for (ResourceType type : TYPES) {
            if (contains(type)) {
                list.add(type);
            }
        }
        return list;
    }

    /** As abas que valem agora: sem o Mekanism, Químicos ficam de fora. */
    public List<ResourceType> effective(boolean chemicals) {
        List<ResourceType> list = types();
        if (!chemicals) {
            list.remove(ResourceType.CHEMICAL);
        }
        return list;
    }

    /** Todas as abas que existem agora estão marcadas (é "Todos"). */
    public boolean isAll(boolean chemicals) {
        return effective(chemicals).size() == available(chemicals).length;
    }

    /** Nenhuma aba que valha agora. */
    public boolean isEmpty(boolean chemicals) {
        return effective(chemicals).isEmpty();
    }

    /** As abas que existem: Químicos só com o Mekanism. */
    public static ResourceType[] available(boolean chemicals) {
        return chemicals ? TYPES.clone()
                : new ResourceType[] {ResourceType.ITEM, ResourceType.FLUID, ResourceType.ENERGY};
    }

    /** Atalhos da roda, em ordem: Todos e depois cada aba sozinha. Cópia: pode mexer. */
    public static LinkerTabs[] shortcuts(boolean chemicals) {
        ResourceType[] available = available(chemicals);
        LinkerTabs[] shortcuts = new LinkerTabs[available.length + 1];
        shortcuts[0] = ALL;
        for (int i = 0; i < available.length; i++) {
            shortcuts[i + 1] = of(available[i]);
        }
        return shortcuts;
    }

    /**
     * Avança ({@code direction > 0}) ou volta ({@code direction < 0}) um atalho, dando a volta. O
     * atalho atual é achado pelas abas que valem agora; uma combinação que não é atalho vai para
     * Todos. Direção 0 fica.
     */
    public LinkerTabs next(int direction, boolean chemicals) {
        if (direction == 0) {
            return this;
        }
        LinkerTabs[] shortcuts = shortcuts(chemicals);
        int index = -1;
        if (isAll(chemicals)) {
            index = 0;
        } else {
            List<ResourceType> effective = effective(chemicals);
            for (int i = 1; i < shortcuts.length; i++) {
                if (effective.equals(shortcuts[i].types())) {
                    index = i;
                    break;
                }
            }
        }
        if (index < 0) {
            return ALL;
        }
        return shortcuts[Math.floorMod(index + Integer.signum(direction), shortcuts.length)];
    }

    private static int bit(ResourceType type) {
        return 1 << type.ordinal();
    }
}
