package io.github.matheusanbs.wirelessautomate.linker;

import io.github.matheusanbs.wirelessautomate.network.ResourceType;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * Abas que o Vinculador vincula ou desvincula (lógica pura): um conjunto de {@link ResourceType}
 * guardado como máscara de bits pelo {@code ordinal}. Um tipo pode estar no conjunto mesmo sem o mod
 * dele (o item sobrevive à troca de instância); {@link #effective(List)} ignora os que não estão
 * entre os tipos disponíveis.
 *
 * <p>"Todos" é {@link #ALL}, todas as abas. Um conjunto com todos os tipos disponíveis também conta
 * como Todos ({@link #isAll(List)}).
 *
 * <p>Atalhos da roda do mouse ({@link #next}): Todos e depois cada tipo disponível sozinho, na ordem
 * do registro, e de volta a Todos. Uma combinação que não é atalho (por exemplo Itens + Fluidos) vai
 * para Todos, nos dois sentidos.
 */
public record LinkerTabs(int mask) {
    private static final ResourceType[] TYPES = ResourceType.values();
    private static final int FULL = (1 << TYPES.length) - 1;

    /** Todas as abas. */
    public static final LinkerTabs ALL = new LinkerTabs(FULL);
    /** Nenhuma aba (a tela não deixa chegar aqui, mas um item vindo de fora pode ter só um tipo ausente). */
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
     * Do formato salvo (as chaves dos tipos, como {@code "item"}). Nomes desconhecidos são
     * ignorados, para um item de outra versão não quebrar.
     */
    public static LinkerTabs fromNames(Collection<String> names) {
        int mask = 0;
        for (String name : names) {
            ResourceType type = ResourceType.byKey(name);
            if (type != null) {
                mask |= bit(type);
            }
        }
        return new LinkerTabs(mask);
    }

    /** Chaves salvas, na ordem das abas. */
    public List<String> names() {
        List<String> names = new ArrayList<>();
        for (ResourceType type : types()) {
            names.add(type.key());
        }
        return names;
    }

    public boolean contains(ResourceType type) {
        return (mask & bit(type)) != 0;
    }

    /** Marca ou desmarca a aba. */
    public LinkerTabs toggle(ResourceType type) {
        return new LinkerTabs(mask ^ bit(type));
    }

    /** Todas as abas guardadas, na ordem das abas (inclusive de tipos que esta instância não tem). */
    public List<ResourceType> types() {
        List<ResourceType> list = new ArrayList<>();
        for (ResourceType type : TYPES) {
            if (contains(type)) {
                list.add(type);
            }
        }
        return list;
    }

    /** As abas que valem agora: as guardadas que existem nesta instância. */
    public List<ResourceType> effective(List<ResourceType> available) {
        List<ResourceType> list = types();
        list.retainAll(available);
        return list;
    }

    /** Todas as abas disponíveis estão marcadas (é "Todos"). */
    public boolean isAll(List<ResourceType> available) {
        return effective(available).size() == available.size();
    }

    /** Nenhuma aba que valha agora. */
    public boolean isEmpty(List<ResourceType> available) {
        return effective(available).isEmpty();
    }

    /** Todas as disponíveis marcadas. */
    public static LinkerTabs available(List<ResourceType> available) {
        return of(available);
    }

    /** Atalhos da roda, em ordem: Todos e depois cada aba disponível sozinha. Cópia: pode mexer. */
    public static LinkerTabs[] shortcuts(List<ResourceType> available) {
        LinkerTabs[] shortcuts = new LinkerTabs[available.size() + 1];
        shortcuts[0] = ALL;
        for (int i = 0; i < available.size(); i++) {
            shortcuts[i + 1] = of(available.get(i));
        }
        return shortcuts;
    }

    /**
     * Avança ({@code direction > 0}) ou volta ({@code direction < 0}) um atalho, dando a volta. O
     * atalho atual é achado pelas abas que valem agora; uma combinação que não é atalho vai para
     * Todos. Direção 0 fica.
     */
    public LinkerTabs next(int direction, List<ResourceType> available) {
        if (direction == 0) {
            return this;
        }
        LinkerTabs[] shortcuts = shortcuts(available);
        int index = -1;
        if (isAll(available)) {
            index = 0;
        } else {
            List<ResourceType> effective = effective(available);
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
