package io.github.matheusanbs.wirelessautomate.client.filter;

import io.github.matheusanbs.wirelessautomate.filter.Filter;
import io.github.matheusanbs.wirelessautomate.filter.FilterEntry;
import io.github.matheusanbs.wirelessautomate.filter.FilterEntry.ChemicalEntry;
import io.github.matheusanbs.wirelessautomate.filter.FilterEntry.ModEntry;
import io.github.matheusanbs.wirelessautomate.filter.FilterEntry.TagEntry;
import io.github.matheusanbs.wirelessautomate.menu.FilterMenu;
import io.github.matheusanbs.wirelessautomate.menu.FilterView;
import io.github.matheusanbs.wirelessautomate.packet.AddFilterEntryPayload;
import io.github.matheusanbs.wirelessautomate.packet.EditFilterPayload;
import io.github.matheusanbs.wirelessautomate.packet.EditFilterPayload.Op;
import io.github.matheusanbs.wirelessautomate.packet.FilterEntriesPayload;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Todo pacote que a tela de filtro manda ({@link EditFilterPayload}, {@link FilterEntriesPayload} e
 * {@link AddFilterEntryPayload}); numa captura sem servidor, a edição vale só na visão local.
 */
final class FilterActions {
    private final FilterMenu menu;
    private final boolean preview;

    /**
     * @param preview sem servidor (captura de desenvolvimento): as mudanças valem só na visão local
     */
    FilterActions(FilterMenu menu, boolean preview) {
        this.menu = menu;
        this.preview = preview;
    }

    boolean preview() {
        return preview;
    }

    private Filter filter() {
        return menu.view().filter();
    }

    void send(Op op, int index, long value, String text) {
        EditFilterPayload payload = new EditFilterPayload(menu.containerId, op, index, value, text);
        if (preview) {
            applyLocally(payload);
        } else {
            PacketDistributor.sendToServer(payload);
        }
    }

    /** Captura sem servidor: aplica a edição na visão local, como o servidor faria. */
    private void applyLocally(EditFilterPayload p) {
        Filter f = filter();
        Filter next = switch (p.op()) {
            case ADD_TAG -> {
                ResourceLocation tag = ResourceLocation.tryParse(p.text());
                yield tag == null ? f : f.withEntry(new TagEntry(tag, 0));
            }
            case ADD_MOD -> f.withEntry(new ModEntry(p.text(), 0));
            case REMOVE -> f.withoutEntry(p.index());
            case SET_STOCK -> f.withStock(p.index(), p.value());
            case SET_LIST_MODE -> f.withListMode(p.value() == 0 ? Filter.ListMode.WHITELIST : Filter.ListMode.BLACKLIST);
            case SET_COMPONENTS -> f.withMatchComponents(p.value() != 0);
            case CLEAR -> f.cleared();
            case IMPORT_CARD, EXPORT_CARD, BACK -> f;
        };
        applyLocally(next);
    }

    private void applyLocally(Filter next) {
        FilterView v = menu.view();
        menu.applyView(new FilterView(v.type(), v.router(), v.face(), next, v.hasCard()));
    }

    /** Manda tags, mods e regras ({@link FilterEntriesPayload}); químicos exatos vão pelo pacote do JEI. */
    void sendEntries(int replace, List<FilterEntry> entries) {
        if (entries.isEmpty()) {
            return;
        }
        if (preview) {
            applyLocally(replace < 0 ? filter().withEntries(entries) : filter().withReplaced(replace, entries.getFirst()));
            return;
        }
        List<FilterEntry> rules = new ArrayList<>();
        for (FilterEntry entry : entries) {
            if (entry instanceof ChemicalEntry) {
                PacketDistributor.sendToServer(new AddFilterEntryPayload(menu.containerId, entry));
            } else {
                rules.add(entry);
            }
        }
        for (int i = 0; i < rules.size(); i += FilterEntriesPayload.MAX) {
            PacketDistributor.sendToServer(new FilterEntriesPayload(menu.containerId, replace,
                    List.copyOf(rules.subList(i, Math.min(rules.size(), i + FilterEntriesPayload.MAX)))));
        }
    }

    /** Uma entrada vinda de fora (o JEI), que ainda não está no filtro. */
    void addEntry(FilterEntry entry) {
        if (preview) {
            applyLocally(filter().withEntry(entry));
        } else {
            PacketDistributor.sendToServer(new AddFilterEntryPayload(menu.containerId, entry));
        }
    }
}
