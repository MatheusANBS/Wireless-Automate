package io.github.matheusanbs.wirelessautomate.client;

import io.github.matheusanbs.wirelessautomate.block.RouterBlock;
import io.github.matheusanbs.wirelessautomate.client.TabletSearch.Candidate;
import io.github.matheusanbs.wirelessautomate.client.TabletSearch.Group;
import io.github.matheusanbs.wirelessautomate.client.TabletSearch.Suggestion;
import io.github.matheusanbs.wirelessautomate.menu.PanelAccess;
import io.github.matheusanbs.wirelessautomate.menu.RouterSnapshot;
import io.github.matheusanbs.wirelessautomate.menu.TabletMenu;
import io.github.matheusanbs.wirelessautomate.menu.TabletSnapshot;
import io.github.matheusanbs.wirelessautomate.menu.TabletSnapshot.Facet;
import io.github.matheusanbs.wirelessautomate.menu.TabletSnapshot.NodeView;
import io.github.matheusanbs.wirelessautomate.menu.TabletSnapshot.ProfileSummary;
import io.github.matheusanbs.wirelessautomate.menu.TabletSnapshot.Query;
import io.github.matheusanbs.wirelessautomate.menu.TabletSnapshot.QuickFilter;
import io.github.matheusanbs.wirelessautomate.network.LoadedTypes;
import io.github.matheusanbs.wirelessautomate.network.NodeIndex.NodeKey;
import io.github.matheusanbs.wirelessautomate.network.PortMode;
import io.github.matheusanbs.wirelessautomate.network.ResourceType;
import io.github.matheusanbs.wirelessautomate.packet.PanelNoticePayload;
import io.github.matheusanbs.wirelessautomate.packet.TabletPanelPayload;
import io.github.matheusanbs.wirelessautomate.packet.TabletProfilePayload;
import io.github.matheusanbs.wirelessautomate.packet.TabletProfilePayload.Action;
import io.github.matheusanbs.wirelessautomate.packet.TabletQueryPayload;
import io.github.matheusanbs.wirelessautomate.packet.TabletSelectNodePayload;
import io.github.matheusanbs.wirelessautomate.preset.ProfileDiff.ProfileMatch;
import io.github.matheusanbs.wirelessautomate.range.RemoteSide;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.glfw.GLFW;

/**
 * A aba Lista do Tablet (especificação, "Lista do Tablet"): à esquerda a busca com sugestões e etiquetas, os
 * filtros rápidos, a faixa do perfil copiado e os nós agrupados pela máquina ({@link ListGroups}); à direita o
 * painel do nó escolhido ({@link NodePanel}), com Tela completa, Copiar perfil e Colar perfil. Tudo desenhado e
 * respondido por geometria, como a {@link TabletScreen} que a hospeda; os botões fixos do modo Selecionar
 * continuam na tela.
 *
 * <p>A busca é feita no servidor (a consulta vai com um pequeno atraso a cada letra); as sugestões saem no
 * cliente, das facetas do cabeçalho e dos nomes dos nós recebidos ({@link TabletSearch}).
 */
final class TabletList {
    private static final int SEARCH_H = 14;
    private static final int QUICK_H = 11;
    private static final int BANNER_H = 12;
    private static final int HEADER_ROW = 12;
    private static final int NODE_ROW = 20;
    private static final int LIMIT_ROW = 12;
    private static final int BAR_H = 14;
    private static final int GAP = 3;
    /** Largura mínima da coluna da lista e máxima do painel. */
    private static final int LIST_MIN = 128;
    private static final int PANEL_MAX = 260;
    /** Abaixo desta largura da lista, os quadradinhos das faces somem. */
    private static final int CHIPS_MIN_LIST = 150;
    private static final int CHIP = 4;
    private static final int LABEL_H = 10;
    private static final int SUGGEST_ROW = 12;
    private static final int SUGGEST_TITLE = 10;
    private static final int SUGGEST_FOOT = 11;
    private static final int SUGGEST_MAX_W = 230;
    private static final long DOUBLE_CLICK_MS = 400;
    /** Tiques entre a última letra e a consulta. */
    private static final int DEBOUNCE = 6;
    /** Altura do cubo do painel: menor nas telas baixas. */
    private static final int NET_SMALL = 3 * 13 + 2 * 2;
    private static final int TALL = 300;

    private enum Kind { LABEL, QUICK, TYPE_CHIP, CLEAR, FORGET, HEADER, PASTE_GROUP, ROW, SUGGESTION, FULL_SCREEN, COPY,
        PASTE, UNDO }

    /** Uma área que responde ao mouse. */
    private record Hot(Kind kind, int x, int y, int w, int h, int arg) {
        boolean contains(double mx, double my) {
            return mx >= x && mx < x + w && my >= y && my < y + h;
        }

        int[] center() {
            return new int[] {x + w / 2, y + h / 2};
        }
    }

    /** Uma etiqueta da busca: uma máquina ou uma rede. */
    private record Label(@Nullable ResourceLocation machine, @Nullable UUID network) {
    }

    /** Um item da lista de sugestões aberta: o título de um grupo ou uma sugestão (índice em {@link #suggestions}). */
    private record SuggestLine(@Nullable Group title, int index) {
    }

    private final TabletScreen screen;
    private EditBox searchBox;

    // consulta
    private String text = "";
    private int textTicks = -1;
    private final List<Label> labels = new ArrayList<>();
    private final Set<QuickFilter> quick = EnumSet.noneOf(QuickFilter.class);
    private Optional<ResourceType> typeFilter = Optional.empty();

    // sugestões
    private List<Suggestion<?>> suggestions = List.of();
    private boolean suggestOpen;
    private int highlight = -1;
    private final Map<ResourceLocation, String> machineNames = new HashMap<>();

    // lista
    private final Set<ResourceLocation> collapsed = new HashSet<>();
    private int scroll;
    private @Nullable NodeKey chosen;
    /** Nó a pôr na vista no próximo arranjo (o restaurado ao abrir, o escolhido pelas setas). */
    private @Nullable NodeKey revealKey;
    private @Nullable NodeKey lastClicked;
    private long lastClickAt;
    private boolean multi;
    private final Set<NodeKey> selected = new LinkedHashSet<>();
    private ResourceType chipType = LoadedTypes.LIST.isEmpty() ? ResourceType.ITEM : LoadedTypes.LIST.getFirst();
    private @Nullable ListGroups<ResourceLocation> groups;
    private @Nullable List<NodeView> groupsFor;
    private Set<ResourceLocation> groupsCollapsed = Set.of();

    // painel
    private @Nullable NodePanel panel;
    private @Nullable NodeKey panelNode;
    private int panelVersion = -1;

    // geometria (absoluta), refeita a cada quadro
    private int x0;
    private int y0;
    private int bottom;
    private int listW;
    private int panelX;
    private int panelW;
    private int rowsTop;
    private int rowsBottom;
    /** A primeira linha que não coube (as de antes, a partir da rolagem, estão nos {@link #hots}). */
    private int drawnUntil;
    private int rowsEndY;
    private final List<Hot> hots = new ArrayList<>();
    /** O texto de cada filtro rápido do quadro, na ordem das áreas (inteiro, curto ou só o número). */
    private final List<Component> quickLabels = new ArrayList<>();

    TabletList(TabletScreen screen) {
        this.screen = screen;
        Query query = screen.getMenu().snapshot().query();
        this.text = query.text();
        query.machines().forEach(id -> labels.add(new Label(id, null)));
        query.networks().forEach(id -> labels.add(new Label(null, id)));
        this.quick.addAll(query.quick());
        this.typeFilter = query.type();
        typeFilter.ifPresent(t -> chipType = t);
        this.chosen = screen.getMenu().restoredNode();
        this.revealKey = chosen;
    }

    /** A caixa de busca da tela (um widget dela), refeita a cada {@code init}. */
    void attach(EditBox box) {
        this.searchBox = box;
        box.setValue(text);
        box.setResponder(value -> {
            if (!value.equals(text)) {
                text = value;
                textTicks = DEBOUNCE;
                refreshSuggestions();
                suggestOpen = !suggestions.isEmpty();
                highlight = -1;
            }
        });
    }

    // ------------------------------------------------------------------ estado

    private TabletSnapshot snapshot() {
        return screen.getMenu().snapshot();
    }

    private static Font font() {
        return Minecraft.getInstance().font;
    }

    private static Component tr(String key, Object... args) {
        return TabletScreen.tr(key, args);
    }

    /** Há busca, etiqueta, filtro rápido ou filtro de tipo. */
    boolean filtering() {
        return !text.isBlank() || !labels.isEmpty() || !quick.isEmpty() || typeFilter.isPresent();
    }

    Optional<ResourceType> typeFilter() {
        return typeFilter;
    }

    boolean multi() {
        return multi;
    }

    Set<NodeKey> selected() {
        return selected;
    }

    @Nullable NodeKey chosen() {
        return chosen;
    }

    /** O tipo dos quadradinhos das linhas: a aba do painel. */
    ResourceType chipType() {
        return chipType;
    }

    private String machineName(ResourceLocation id) {
        return machineNames.computeIfAbsent(id, key -> {
            Block block = BuiltInRegistries.BLOCK.get(key);
            return block == Blocks.AIR ? key.toString() : RouterBlock.machineName(block).getString();
        });
    }

    private Component nodeName(NodeView node) {
        return node.name().isEmpty() ? Component.literal(machineName(node.machine())) : Component.literal(node.name());
    }

    private String networkName(UUID id) {
        return snapshot().network(id).map(TabletSnapshot.NetworkView::name).orElse("?");
    }

    private Optional<ProfileSummary> profile() {
        return snapshot().profile();
    }

    private ListGroups<ResourceLocation> groups() {
        List<NodeView> nodes = snapshot().nodes();
        if (groups == null || groupsFor != nodes || !groupsCollapsed.equals(collapsed)) {
            groups = new ListGroups<>(nodes.stream().map(NodeView::machine).toList(), Set.copyOf(collapsed));
            groupsFor = nodes;
            groupsCollapsed = Set.copyOf(collapsed);
        }
        return groups;
    }

    private int indexOf(@Nullable NodeKey key) {
        List<NodeView> nodes = snapshot().nodes();
        for (int i = 0; i < nodes.size(); i++) {
            if (nodes.get(i).key().equals(key)) {
                return i;
            }
        }
        return -1;
    }

    private @Nullable NodeView node(@Nullable NodeKey key) {
        int index = indexOf(key);
        return index < 0 ? null : snapshot().nodes().get(index);
    }

    /** O painel do servidor para o nó escolhido; um painel atrasado de outro nó não vale. */
    @Nullable TabletPanelPayload panelPayload() {
        TabletPanelPayload payload = screen.getMenu().panel();
        return payload != null && chosen != null && payload.node().equals(Optional.of(chosen)) ? payload : null;
    }

    /** O componente do painel do nó escolhido, ou {@code null} sem nó ou antes de o servidor responder. */
    @Nullable NodePanel panel() {
        syncPanel();
        return panel;
    }

    /** Põe o painel em dia com o último {@link TabletPanelPayload}: um nó novo ganha um painel novo na mesma aba. */
    private void syncPanel() {
        TabletPanelPayload payload = panelPayload();
        Optional<RouterSnapshot> snap = payload == null ? Optional.empty() : payload.snapshot();
        if (snap.isEmpty()) {
            panel = null;
            panelNode = null;
            return;
        }
        RouterSnapshot s = snap.get();
        if (panel == null || !chosen.equals(panelNode)) {
            ResourceType type = panel != null ? panel.type() : chipType;
            panel = new NodePanel(new PanelActions(), s, s.range().present() ? s.range().face() : s.facing());
            panel.select(type);
            panelNode = chosen;
        } else if (panelVersion != screen.getMenu().panelVersion()) {
            panel.setSnapshot(s);
        }
        panelVersion = screen.getMenu().panelVersion();
        panel.readOnly(readOnlyReason(payload));
        chipType = panel.type();
    }

    /** O motivo do só leitura, ou {@code null} quando o painel edita. */
    private static @Nullable Component readOnlyReason(TabletPanelPayload payload) {
        if (payload.access() == PanelAccess.EDIT) {
            return null;
        }
        if (payload.access() == PanelAccess.READ_UNLOADED) {
            return Component.translatable("gui.wirelessautomate.panel.readonly.unloaded");
        }
        Component tier = payload.snapshot().map(s -> Component.translatable(s.tier().translationKey()))
                .orElse(Component.empty());
        return payload.distance() < 0 ? Component.translatable("gui.wirelessautomate.panel.readonly.dimension", tier)
                : Component.translatable("gui.wirelessautomate.panel.readonly.range", tier, payload.range());
    }

    /** O que o painel do Tablet manda: pelo menu do Tablet (o nó escolhido é o alvo no servidor). */
    private final class PanelActions implements NodePanel.Actions {
        @Override
        public int containerId() {
            return screen.getMenu().containerId;
        }

        @Override
        public void send(CustomPacketPayload payload) {
            screen.send(payload);
        }

        @Override
        public void undoPaste() {
            profileAction(Action.UNDO, null, null, List.of());
        }
    }

    // ------------------------------------------------------------------ consulta e ações

    private Query query() {
        List<ResourceLocation> machines = new ArrayList<>();
        List<UUID> networks = new ArrayList<>();
        for (Label label : labels) {
            if (label.machine() != null) {
                machines.add(label.machine());
            } else {
                networks.add(label.network());
            }
        }
        return Query.of(text.strip()).withTextMachines(textMachines()).withMachines(machines).withNetworks(networks)
                .withQuick(quick).withType(typeFilter);
    }

    private List<Candidate<ResourceLocation>> machineCandidates() {
        List<Candidate<ResourceLocation>> list = new ArrayList<>();
        for (Facet<ResourceLocation> facet : snapshot().machineFacets()) {
            list.add(new Candidate<>(facet.id(), machineName(facet.id()), facet.count()));
        }
        return list;
    }

    /** As máquinas cujo nome, no idioma do jogador, contém o texto: o servidor só conhece os ids. */
    private List<ResourceLocation> textMachines() {
        List<ResourceLocation> ids = TabletSearch.matchingIds(text, machineCandidates());
        return ids.size() > TabletSnapshot.MAX_TEXT_MACHINES ? ids.subList(0, TabletSnapshot.MAX_TEXT_MACHINES) : ids;
    }

    private void sendQuery() {
        textTicks = -1;
        scroll = 0;
        screen.send(new TabletQueryPayload(screen.getMenu().containerId, query()));
    }

    /** A cada tique da tela: a consulta sai um pouco depois da última letra. */
    void tick() {
        if (textTicks > 0 && --textTicks == 0) {
            sendQuery();
        }
    }

    private void refreshSuggestions() {
        List<Candidate<UUID>> networks = new ArrayList<>();
        for (Facet<UUID> facet : snapshot().networkFacets()) {
            snapshot().network(facet.id()).ifPresent(n -> networks.add(new Candidate<>(n.id(), n.name(), facet.count())));
        }
        Map<String, Integer> names = new LinkedHashMap<>();
        for (NodeView node : snapshot().nodes()) {
            if (!node.name().isEmpty()) {
                names.merge(node.name(), 1, Integer::sum);
            }
        }
        List<Candidate<String>> nameList = new ArrayList<>();
        names.forEach((name, count) -> nameList.add(new Candidate<>(name, name, count)));
        suggestions = TabletSearch.suggest(text, machineCandidates(), networks, nameList);
        if (highlight >= suggestions.size()) {
            highlight = -1;
        }
    }

    /** Escolhe a sugestão: máquina e rede viram etiqueta; um nome vira o texto da busca (e escolhe o nó, se é um só). */
    private void pick(Suggestion<?> suggestion) {
        suggestOpen = false;
        highlight = -1;
        switch (suggestion.group()) {
            case MACHINES -> addLabel(new Label((ResourceLocation) suggestion.id(), null));
            case NETWORKS -> addLabel(new Label(null, (UUID) suggestion.id()));
            case NAMES -> {
                setText(suggestion.label());
                NodeView only = null;
                for (NodeView node : snapshot().nodes()) {
                    if (node.name().equals(suggestion.label())) {
                        only = only == null ? node : null;
                        if (only == null) {
                            break;
                        }
                    }
                }
                if (only != null) {
                    choose(only.key());
                }
                sendQuery();
            }
        }
    }

    private void addLabel(Label label) {
        long same = labels.stream().filter(l -> (l.machine() != null) == (label.machine() != null)).count();
        if (!labels.contains(label) && same < TabletSnapshot.MAX_LABELS) {
            labels.add(label);
        }
        setText("");
        sendQuery();
    }

    private void removeLabel(int index) {
        if (index >= 0 && index < labels.size()) {
            labels.remove(index);
            sendQuery();
        }
    }

    private void setText(String value) {
        text = value;
        if (searchBox != null) {
            searchBox.setValue(value);
        }
        suggestOpen = false;
    }

    /** Tira a busca, as etiquetas, os filtros rápidos e o de tipo. */
    void clearAll() {
        setText("");
        labels.clear();
        quick.clear();
        typeFilter = Optional.empty();
        sendQuery();
    }

    private void toggleQuick(QuickFilter filter) {
        if (!quick.remove(filter)) {
            quick.add(filter);
        }
        sendQuery();
    }

    /** Só os nós com o tipo (o clique num cartão das Estatísticas), e o painel nessa aba. */
    void filterType(ResourceType type) {
        typeFilter = Optional.of(type);
        chipType = type;
        if (panel != null) {
            panel.select(type);
        }
        sendQuery();
    }

    private void clearType() {
        typeFilter = Optional.empty();
        sendQuery();
    }

    /**
     * Escolhe o nó do painel (no servidor também). O aviso da regra D1 sai: o Desfazer dele age no nó escolhido,
     * e ele passaria a ser outro.
     */
    void choose(@Nullable NodeKey key) {
        if (key != null && key.equals(chosen)) {
            return;
        }
        chosen = key;
        PanelNoticePayload notice = PanelNotices.current();
        if (notice != null && notice.undo() == PanelNoticePayload.Undo.CLEAR_FACE_NETWORK) {
            PanelNotices.clear();
        }
        screen.send(new TabletSelectNodePayload(screen.getMenu().containerId, Optional.ofNullable(key)));
        reveal(key);
    }

    private void open(NodeKey key) {
        screen.openNode(key);
    }

    private void profileAction(Action action, @Nullable NodeKey key, @Nullable ResourceLocation machine,
            List<NodeKey> keys) {
        screen.send(new TabletProfilePayload(screen.getMenu().containerId, action, Optional.ofNullable(key),
                Optional.ofNullable(machine), keys));
    }

    private boolean canEditChosen() {
        TabletPanelPayload payload = panelPayload();
        return payload != null && payload.access() == PanelAccess.EDIT;
    }

    void copyProfile() {
        if (chosen != null && canEditChosen()) {
            profileAction(Action.COPY, chosen, null, List.of());
        }
    }

    void pasteProfile() {
        if (chosen != null && profile().isPresent() && canEditChosen()) {
            profileAction(Action.PASTE_ONE, chosen, null, List.of());
        }
    }

    void undo() {
        profileAction(Action.UNDO, null, null, List.of());
    }

    private void forgetProfile() {
        profileAction(Action.FORGET, null, null, List.of());
    }

    void pasteSelected() {
        if (profile().isPresent() && !selected.isEmpty()) {
            List<NodeKey> keys = new ArrayList<>(selected);
            profileAction(Action.PASTE_SELECTED, null, null,
                    List.copyOf(keys.size() > TabletProfilePayload.MAX_KEYS ? keys.subList(0, TabletProfilePayload.MAX_KEYS)
                            : keys));
        }
    }

    /** Quantos nós do grupo o "Colar nas N" atinge: os da consulta, menos a origem do perfil. */
    int pasteCount(ResourceLocation machine) {
        ProfileSummary summary = profile().orElse(null);
        if (summary == null) {
            return 0;
        }
        TabletSnapshot s = snapshot();
        int count = s.groupCount(machine);
        if (s.originMatches() && summary.machine().equals(machine)) {
            count--;
        }
        return Math.min(count, TabletMenu.MAX_MOVE);
    }

    void toggleMulti() {
        multi = !multi;
        if (!multi) {
            selected.clear();
        }
    }

    /** Todos: marca todos os nós da lista; se já estavam, desmarca. */
    void selectAll() {
        List<NodeKey> keys = snapshot().nodes().stream().map(NodeView::key).toList();
        if (selected.containsAll(keys)) {
            keys.forEach(selected::remove);
        } else {
            selected.addAll(keys);
        }
    }

    void previewMulti(boolean on, int count) {
        multi = on;
        selected.clear();
        if (on) {
            snapshot().nodes().stream().limit(count).forEach(node -> selected.add(node.key()));
        }
    }

    void previewChoose(@Nullable NodeKey key) {
        chosen = key;
        revealKey = key;
    }

    void previewText(String value) {
        setText(value);
        refreshSuggestions();
        suggestOpen = !suggestions.isEmpty();
    }

    void previewCollapse(ResourceLocation machine) {
        collapsed.add(machine);
    }

    /** Rola a lista até o cabeçalho do grupo da máquina. */
    void previewScrollTo(ResourceLocation machine) {
        arrange();
        scroll = Math.max(0, groups().headerRow(machine));
    }

    /** Snapshot novo: as sugestões e a seleção que sumiu. */
    void onSnapshot() {
        if (suggestOpen) {
            refreshSuggestions();
        }
        Set<NodeKey> present = new HashSet<>();
        snapshot().nodes().forEach(n -> present.add(n.key()));
        selected.retainAll(present);
    }

    // ------------------------------------------------------------------ geometria

    /** O retângulo da aba (de cima da busca até o fim da tela). */
    void layout(int x, int y, int w, int bottomY) {
        x0 = x;
        y0 = y;
        bottom = bottomY;
        // o painel fica com pouco mais da metade (até PANEL_MAX); a lista, com o resto, nunca menos que LIST_MIN
        panelW = Math.min(PANEL_MAX, w * 55 / 100);
        listW = Math.max(LIST_MIN, w - panelW - 9);
        panelW = w - listW - 9;
        panelX = x0 + listW + 9;
    }

    int listWidth() {
        return listW;
    }

    int barY() {
        return bottom - BAR_H;
    }

    /** Onde as linhas de ação do modo Selecionar começam (duas linhas acima do fim). */
    int multiBarY() {
        return bottom - 2 * BAR_H - 2;
    }

    private int panelBodyTop() {
        return y0 + 24;
    }

    private int copyRowY() {
        return bottom - BAR_H;
    }

    /** Posições e áreas de clique do quadro atual. */
    private void arrange() {
        Font font = font();
        hots.clear();
        int y = y0;
        arrangeSearch(font);
        y += SEARCH_H + GAP;
        int quickBottom = arrangeQuick(font, y);
        if (quickBottom > y) {
            y = quickBottom + GAP;
        }
        if (profile().isPresent()) {
            int fw = font.width(forgetLabel(font)) + 6;
            hots.add(new Hot(Kind.FORGET, x0 + listW - fw - 2, y, fw, BANNER_H, 0));
            y += BANNER_H + GAP;
        }
        rowsTop = y;
        rowsBottom = (multi ? multiBarY() : barY()) - GAP;
        arrangeRows(font);
        if (filtering()) {
            Component clear = tr("list.clear");
            int cw = font.width(clear);
            Component count = countText();
            int countW = Math.min(font.width(count), Math.max(0, listW - 62 - cw - 8));
            if (!multi) {
                hots.add(new Hot(Kind.CLEAR, x0 + countW + 4, barY(), cw + 2, BAR_H, 0));
            }
        }
        arrangePanel(font);
        if (revealKey != null && revealNow()) {
            arrange();
        }
    }

    private void arrangeSearch(Font font) {
        int x = x0 + 3;
        int room = listW - 6;
        int maxChip = labels.isEmpty() ? 0 : Math.max(24, (room - 40) / labels.size() - 2);
        for (int i = 0; i < labels.size(); i++) {
            int w = Math.min(font.width(labelText(labels.get(i))) + 14, maxChip);
            hots.add(new Hot(Kind.LABEL, x, y0 + 2, w, LABEL_H, i));
            x += w + 2;
        }
        if (searchBox != null) {
            int boxX = x + 1;
            int boxW = Math.max(20, x0 + listW - 4 - boxX);
            if (searchBox.getX() != boxX || searchBox.getWidth() != boxW) {
                searchBox.setX(boxX);
                searchBox.setWidth(boxW);
            }
            searchBox.setY(y0 + 3);
        }
        if (suggestOpen && !suggestions.isEmpty()) {
            List<SuggestLine> lines = suggestLines();
            int sy = suggestTop() + 1;
            for (SuggestLine line : lines) {
                if (line.title() != null) {
                    sy += SUGGEST_TITLE;
                } else {
                    hots.add(new Hot(Kind.SUGGESTION, x0, sy, suggestWidth(), SUGGEST_ROW, line.index()));
                    sy += SUGGEST_ROW;
                }
            }
        }
    }

    private int arrangeQuick(Font font, int y) {
        List<Integer> kinds = new ArrayList<>();
        List<Component> texts = new ArrayList<>();
        List<Component> shorts = new ArrayList<>();
        if (typeFilter.isPresent()) {
            kinds.add(-1);
            texts.add(tr("list.type", ResourceStyle.name(typeFilter.get())));
            shorts.add(tr("list.type", ResourceStyle.name(typeFilter.get())));
        }
        for (QuickFilter filter : QuickFilter.values()) {
            int count = snapshot().quickCounts().count(filter);
            if (count > 0 || quick.contains(filter)) {
                String key = filter.name().toLowerCase(Locale.ROOT);
                kinds.add(filter.ordinal());
                texts.add(tr("list.quick." + key, count));
                shorts.add(tr("list.quick." + key + ".short", count));
            }
        }
        if (kinds.isEmpty()) {
            return y;
        }
        int total = 0;
        for (Component t : texts) {
            total += font.width(t) + 12;
        }
        int shortTotal = 0;
        for (Component t : shorts) {
            shortTotal += font.width(t) + 12;
        }
        List<Component> shown = total + 3 * (texts.size() - 1) <= listW ? texts : shorts;
        if (shortTotal + 3 * (texts.size() - 1) > listW) {
            // nem os nomes curtos cabem: o tipo continua, os filtros ficam só com o número (o nome vai na dica)
            List<Component> counts = new ArrayList<>();
            for (int i = 0; i < kinds.size(); i++) {
                counts.add(kinds.get(i) < 0 ? shorts.get(i) : Component.literal(
                        Integer.toString(snapshot().quickCounts().count(QuickFilter.values()[kinds.get(i)]))));
            }
            shown = counts;
        }
        quickLabels.clear();
        int x = x0;
        for (int i = 0; i < kinds.size(); i++) {
            int extra = kinds.get(i) < 0 ? ResourceStyle.ICON + 3 : 8;
            int w = Math.min(font.width(shown.get(i)) + 4 + extra, x0 + listW - x);
            if (w <= extra + 6) {
                break;
            }
            quickLabels.add(shown.get(i));
            hots.add(new Hot(kinds.get(i) < 0 ? Kind.TYPE_CHIP : Kind.QUICK, x, y, w, QUICK_H,
                    kinds.get(i) < 0 ? 0 : kinds.get(i)));
            x += w + 3;
        }
        return y + QUICK_H;
    }

    /** Altura de uma linha da lista. */
    private static int rowHeight(ListGroups.Row<ResourceLocation> row) {
        return row.header() ? HEADER_ROW : NODE_ROW;
    }

    /** Linhas desenháveis: as dos grupos e, se o servidor cortou o resultado, a do limite. */
    private int rowCount() {
        TabletSnapshot s = snapshot();
        return groups().rows().size() + (s.matchingNodes() > s.nodes().size() ? 1 : 0);
    }

    private int height(int row) {
        List<ListGroups.Row<ResourceLocation>> rows = groups().rows();
        return row < rows.size() ? rowHeight(rows.get(row)) : LIMIT_ROW;
    }

    private int maxScroll() {
        int room = rowsBottom - rowsTop;
        int used = 0;
        int row = rowCount();
        while (row > 0 && used + height(row - 1) <= room) {
            used += height(row - 1);
            row--;
        }
        return row;
    }

    private void arrangeRows(Font font) {
        scroll = Math.max(0, Math.min(scroll, maxScroll()));
        List<ListGroups.Row<ResourceLocation>> rows = groups().rows();
        int y = rowsTop;
        drawnUntil = scroll;
        for (int r = scroll; r < rows.size(); r++) {
            ListGroups.Row<ResourceLocation> row = rows.get(r);
            int h = rowHeight(row);
            if (y + h > rowsBottom) {
                break;
            }
            drawnUntil = r + 1;
            if (row.header()) {
                hots.add(new Hot(Kind.HEADER, x0, y, listW, h, r));
                int n = pasteCount(row.group());
                if (n > 0) {
                    Component label = tr("list.paste_group", n);
                    int bw = font.width(label) + 8;
                    hots.add(new Hot(Kind.PASTE_GROUP, x0 + listW - bw - 2, y + 1, bw, h - 2, r));
                }
            } else {
                hots.add(new Hot(Kind.ROW, x0, y, listW, h, row.node()));
            }
            y += h;
        }
        rowsEndY = y;
    }

    private void arrangePanel(Font font) {
        if (panelW <= 0) {
            return;
        }
        if (chosen != null) {
            Component full = fullScreenLabel(font);
            int fw = font.width(full) + 8;
            hots.add(new Hot(Kind.FULL_SCREEN, panelX + panelW - fw, y0, fw, 12, 0));
        }
        NodePanel p = panel();
        if (p != null) {
            int top = panelBodyTop();
            int netH = screen.size()[1] >= TALL ? NodePanel.stackedNetHeight() : NET_SMALL;
            int netW = Math.min(panelW, 4 * 44 + 6);
            int netY = top + 20;
            int bodyY = netY + netH + 5;
            p.layoutSplit(panelX, top, panelW, panelX, netY, netW, netH, panelX, bodyY, panelW,
                    copyRowY() - 3 - bodyY);
            boolean paste = profile().isPresent();
            copyShort = paste && font.width(tr("list.copy")) + font.width(tr("list.paste")) + 24 > panelW;
            int cw = font.width(copyLabel()) + 10;
            hots.add(new Hot(Kind.COPY, panelX, copyRowY(), cw, BAR_H, 0));
            if (paste) {
                int pw = font.width(pasteLabel()) + 10;
                hots.add(new Hot(Kind.PASTE, panelX + panelW - pw, copyRowY(), pw, BAR_H, 0));
            }
        } else if (PanelNotices.current() != null && PanelNotices.current().undo() != PanelNoticePayload.Undo.NONE) {
            Component undo = Component.translatable("gui.wirelessautomate.panel.notice.undo");
            int uw = font.width(undo) + 8;
            hots.add(new Hot(Kind.UNDO, panelX + panelW - uw, ownNoticeY(), uw, 13, 0));
        }
    }

    /** Os botões do perfil com o nome curto ("Copiar", "Colar") quando os dois inteiros não cabem. */
    private boolean copyShort;

    private Component copyLabel() {
        return copyShort ? tr("list.copy.short") : tr("list.copy");
    }

    private Component pasteLabel() {
        return copyShort ? tr("list.paste.short") : tr("list.paste");
    }

    private Component fullScreenLabel(Font font) {
        Component full = tr("list.full_screen");
        return font.width(full) + 8 <= panelW / 2 ? full : tr("open");
    }

    private int ownNoticeY() {
        return bottom - 13;
    }

    private int suggestTop() {
        return y0 + SEARCH_H + 1;
    }

    private int suggestWidth() {
        return Math.max(listW, Math.min(SUGGEST_MAX_W, panelX + panelW - x0));
    }

    private List<SuggestLine> suggestLines() {
        List<SuggestLine> lines = new ArrayList<>();
        Group last = null;
        for (int i = 0; i < suggestions.size(); i++) {
            Group group = suggestions.get(i).group();
            if (group != last) {
                lines.add(new SuggestLine(group, -1));
                last = group;
            }
            lines.add(new SuggestLine(null, i));
        }
        return lines;
    }

    private int suggestHeight() {
        int h = 0;
        for (SuggestLine line : suggestLines()) {
            h += line.title() != null ? SUGGEST_TITLE : SUGGEST_ROW;
        }
        return h + SUGGEST_FOOT + 2;
    }

    private boolean inSuggestions(double mx, double my) {
        return suggestOpen && !suggestions.isEmpty() && mx >= x0 && mx < x0 + suggestWidth() && my >= suggestTop()
                && my < suggestTop() + suggestHeight();
    }

    private @Nullable Hot hot(Kind kind, int arg) {
        for (Hot hot : hots) {
            if (hot.kind() == kind && hot.arg() == arg) {
                return hot;
            }
        }
        return null;
    }

    private @Nullable Hot hotAt(double mx, double my) {
        if (inSuggestions(mx, my)) {
            for (Hot hot : hots) {
                if (hot.kind() == Kind.SUGGESTION && hot.contains(mx, my)) {
                    return hot;
                }
            }
            return null;
        }
        Hot found = null;
        for (Hot hot : hots) {
            if (hot.kind() != Kind.SUGGESTION && hot.contains(mx, my)) {
                // o botão do cabeçalho fica por cima do cabeçalho
                if (found == null || hot.kind() == Kind.PASTE_GROUP) {
                    found = hot;
                }
            }
        }
        return found;
    }

    /** Garante a linha do nó na vista (as setas e o escolher pela sugestão). */
    private void reveal(@Nullable NodeKey key) {
        revealKey = key;
    }

    /** Rola até a linha de {@link #revealKey}, se ela já está na lista; devolve se rolou. */
    private boolean revealNow() {
        int row = groups().rowOfNode(indexOf(revealKey));
        if (row < 0) {
            return false;
        }
        revealKey = null;
        int before = scroll;
        if (row < scroll) {
            scroll = row;
            return true;
        }
        int room = rowsBottom - rowsTop;
        while (scroll < row) {
            int used = 0;
            for (int r = scroll; r <= row; r++) {
                used += height(r);
            }
            if (used <= room) {
                break;
            }
            scroll++;
        }
        return scroll != before;
    }

    // ------------------------------------------------------------------ entrada

    boolean mouseClicked(double mx, double my, int button) {
        arrange();
        NodePanel p = panel;
        if (p != null && p.popupOpen()) {
            return p.mouseClicked(mx, my, button);
        }
        if (inSuggestions(mx, my)) {
            Hot hot = hotAt(mx, my);
            if (hot != null && button == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
                pick(suggestions.get(hot.arg()));
            }
            return true;
        }
        if (suggestOpen && !(searchBox != null && searchBox.isMouseOver(mx, my))) {
            suggestOpen = false;
        }
        if (p != null && mx >= panelX && p.mouseClicked(mx, my, button)) {
            return true;
        }
        if (button != GLFW.GLFW_MOUSE_BUTTON_LEFT) {
            return false;
        }
        Hot hot = hotAt(mx, my);
        if (hot == null) {
            return clickEmpty(mx, my);
        }
        List<NodeView> nodes = snapshot().nodes();
        switch (hot.kind()) {
            case SUGGESTION -> pick(suggestions.get(hot.arg()));
            case LABEL -> removeLabel(hot.arg());
            case QUICK -> toggleQuick(QuickFilter.values()[hot.arg()]);
            case TYPE_CHIP -> clearType();
            case CLEAR -> clearAll();
            case FORGET -> forgetProfile();
            case HEADER -> {
                ResourceLocation group = groups().rows().get(hot.arg()).group();
                if (!collapsed.remove(group)) {
                    collapsed.add(group);
                }
            }
            case PASTE_GROUP -> profileAction(Action.PASTE_GROUP, null, groups().rows().get(hot.arg()).group(), List.of());
            case ROW -> clickRow(nodes.get(hot.arg()).key());
            case FULL_SCREEN -> {
                if (chosen != null) {
                    open(chosen);
                }
            }
            case COPY -> copyProfile();
            case PASTE -> pasteProfile();
            case UNDO -> {
                PanelNotices.clear();
                undo();
            }
        }
        return true;
    }

    /** O "Limpar a busca" da lista vazia. */
    private boolean clickEmpty(double mx, double my) {
        int[] clear = emptyClearCenter();
        if (clear != null && Math.abs(mx - clear[0]) <= font().width(tr("list.clear_search")) / 2 + 2
                && Math.abs(my - clear[1]) <= 6) {
            clearAll();
            return true;
        }
        return mx >= x0 && mx < x0 + listW && my >= rowsTop && my < rowsBottom;
    }

    private void clickRow(NodeKey key) {
        long now = System.currentTimeMillis();
        boolean twice = key.equals(lastClicked) && now - lastClickAt <= DOUBLE_CLICK_MS;
        lastClicked = key;
        lastClickAt = now;
        if (multi) {
            if (!selected.remove(key)) {
                selected.add(key);
            }
            return;
        }
        if (twice) {
            lastClicked = null;
            open(key);
            return;
        }
        choose(key);
    }

    boolean mouseScrolled(double mx, double my, double scrollY) {
        NodePanel p = panel;
        if (p != null && p.mouseScrolled(mx, my, scrollY)) {
            return true;
        }
        if (mx < x0 + listW) {
            scroll = Math.max(0, Math.min(maxScroll(), scroll - (int) Math.signum(scrollY)));
        }
        return true;
    }

    /** O teclado da Lista (especificação, "Teclado"); {@code false} deixa a tecla para a busca ou a tela. */
    boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        NodePanel p = panel;
        if (p != null && p.keyPressed(keyCode)) {
            return true;
        }
        boolean ctrl = (modifiers & (GLFW.GLFW_MOD_CONTROL | GLFW.GLFW_MOD_SUPER)) != 0;
        if (ctrl && keyCode == GLFW.GLFW_KEY_C) {
            if (searchBox != null && !searchBox.getHighlighted().isEmpty()) {
                return false;
            }
            copyProfile();
            return true;
        }
        if (ctrl && keyCode == GLFW.GLFW_KEY_V && profile().isPresent() && chosen != null) {
            pasteProfile();
            return true;
        }
        if (ctrl && keyCode == GLFW.GLFW_KEY_Z) {
            PanelNotices.clear();
            undo();
            return true;
        }
        boolean suggesting = suggestOpen && !suggestions.isEmpty();
        switch (keyCode) {
            case GLFW.GLFW_KEY_ESCAPE -> {
                if (suggesting) {
                    suggestOpen = false;
                    return true;
                }
                if (filtering()) {
                    clearAll();
                    return true;
                }
                return false;
            }
            case GLFW.GLFW_KEY_UP, GLFW.GLFW_KEY_DOWN -> {
                int direction = keyCode == GLFW.GLFW_KEY_DOWN ? 1 : -1;
                if (suggesting) {
                    highlight = Math.max(-1, Math.min(suggestions.size() - 1, highlight + direction));
                } else {
                    int next = groups().next(indexOf(chosen), direction);
                    if (next >= 0) {
                        choose(snapshot().nodes().get(next).key());
                    }
                }
                return true;
            }
            case GLFW.GLFW_KEY_ENTER, GLFW.GLFW_KEY_KP_ENTER -> {
                if (suggesting && highlight >= 0) {
                    pick(suggestions.get(highlight));
                } else if (suggestOpen || textTicks > 0) {
                    suggestOpen = false;
                    sendQuery();
                } else if (chosen != null) {
                    open(chosen);
                }
                return true;
            }
            case GLFW.GLFW_KEY_LEFT, GLFW.GLFW_KEY_RIGHT -> {
                ResourceLocation group = groups().group(indexOf(chosen));
                if (!text.isEmpty() || group == null) {
                    return false;
                }
                if (keyCode == GLFW.GLFW_KEY_LEFT) {
                    collapsed.add(group);
                } else {
                    collapsed.remove(group);
                }
                return true;
            }
            case GLFW.GLFW_KEY_BACKSPACE -> {
                if (text.isEmpty() && !labels.isEmpty()) {
                    removeLabel(labels.size() - 1);
                    return true;
                }
                return false;
            }
            default -> {
                return false;
            }
        }
    }

    /** A dica do ponto, ou {@code null}. */
    @Nullable Component tooltip(double mx, double my) {
        arrange();
        NodePanel p = panel;
        if (p != null && p.popupOpen()) {
            return null;
        }
        if (p != null && mx >= panelX) {
            Component tip = p.tooltip(mx, my);
            if (tip != null) {
                return tip;
            }
        }
        Component banner = bannerTooltip(mx, my);
        if (banner != null) {
            return banner;
        }
        Hot hot = hotAt(mx, my);
        if (hot == null) {
            return null;
        }
        return switch (hot.kind()) {
            case LABEL -> tr("list.label.tooltip", labelText(labels.get(hot.arg())));
            case QUICK -> tr("list.quick." + QuickFilter.values()[hot.arg()].name().toLowerCase(Locale.ROOT) + ".tooltip");
            case TYPE_CHIP -> tr("list.type.tooltip");
            case FORGET -> tr("list.profile.forget.tooltip");
            case HEADER -> collapsed.contains(groups().rows().get(hot.arg()).group()) ? tr("list.group.open")
                    : tr("list.group.close");
            case PASTE_GROUP -> pasteGroupTooltip(groups().rows().get(hot.arg()).group());
            case ROW -> screen.nodeTooltip(snapshot().nodes().get(hot.arg()), !multi);
            case FULL_SCREEN -> tr("open.tooltip");
            case COPY -> canEditChosen() ? tr("list.copy.tooltip") : readOnlyTip();
            case PASTE -> !canEditChosen() ? readOnlyTip()
                    : profile().map(s -> tr("list.paste.tooltip", s.sourceName())).orElse(null);
            case CLEAR, SUGGESTION, UNDO -> null;
        };
    }

    private Component readOnlyTip() {
        TabletPanelPayload payload = panelPayload();
        Component reason = payload == null ? null : readOnlyReason(payload);
        return reason == null ? tr("list.copy.tooltip") : reason;
    }

    private Component pasteGroupTooltip(ResourceLocation machine) {
        ProfileSummary s = profile().orElseThrow();
        return tr("list.paste_group.tooltip", s.sourceName(), pasteCount(machine), machineName(machine));
    }

    // ------------------------------------------------------------------ desenho

    void render(GuiGraphics g, int mouseX, int mouseY) {
        arrange();
        Font font = font();
        NodePanel p = panel;
        boolean covered = (p != null && p.popupOpen()) || inSuggestions(mouseX, mouseY);
        int mx = covered ? -1 : mouseX;
        int my = covered ? -1 : mouseY;
        renderSearch(g, font, mx, my);
        renderQuick(g, font, mx, my);
        renderBanner(g, font, mx, my);
        renderRows(g, font, mx, my);
        renderBar(g, font, mx, my);
        g.fill(x0 + listW + 4, y0, x0 + listW + 5, bottom, GuiPaint.LINE);
        renderPanel(g, font, mx, my);
    }

    /** Por cima de tudo: as sugestões, as listas abertas do painel e os avisos. */
    void renderOverlay(GuiGraphics g, int mouseX, int mouseY) {
        arrange();
        Font font = font();
        NodePanel p = panel;
        if (p != null) {
            p.renderOverlay(g, mouseX, mouseY);
        } else {
            renderOwnNotice(g, font, mouseX, mouseY);
        }
        if (suggestOpen && !suggestions.isEmpty()) {
            renderSuggestions(g, font, mouseX, mouseY);
        }
    }

    private Component labelText(Label label) {
        return label.machine() != null ? tr("list.label.machine", machineName(label.machine()))
                : tr("list.label.network", networkName(label.network()));
    }

    private void renderSearch(GuiGraphics g, Font font, int mx, int my) {
        boolean focused = searchBox != null && searchBox.isFocused();
        GuiPaint.box(g, x0, y0, listW, SEARCH_H, GuiPaint.INSET, focused ? GuiPaint.ACCENT : GuiPaint.LINE);
        if (searchBox != null && text.isEmpty()) {
            // a dica fica com o cursor na caixa (a do EditBox some com o foco); a curta quando a inteira não cabe
            int room = searchBox.getWidth() - 4;
            Component hint = font.width(tr("search")) <= room ? tr("search") : tr("search.short");
            GuiText.draw(g, font, hint, searchBox.getX() + (focused ? 7 : 0), searchBox.getY(), room - 7,
                    GuiPaint.DISABLED);
        }
        for (int i = 0; i < labels.size(); i++) {
            Hot hot = hot(Kind.LABEL, i);
            if (hot == null) {
                continue;
            }
            Label label = labels.get(i);
            boolean hovered = hot.contains(mx, my);
            int color = label.network() != null
                    ? snapshot().network(label.network()).map(n -> 0xFF000000 | n.color()).orElse(GuiPaint.MUTED)
                    : GuiPaint.BUTTON_BORDER;
            GuiPaint.box(g, hot.x(), hot.y(), hot.w(), hot.h(), GuiPaint.BUTTON, hovered ? GuiPaint.ACCENT : color);
            GuiText.draw(g, font, labelText(label), hot.x() + 3, hot.y() + 1, hot.w() - 12, GuiPaint.FG);
            GuiPaint.text(g, font, Component.literal("✕"), hot.x() + hot.w() - 8, hot.y() + 1,
                    hovered ? GuiPaint.ACCENT : GuiPaint.MUTED);
        }
    }

    private void renderQuick(GuiGraphics g, Font font, int mx, int my) {
        for (Hot hot : hots) {
            boolean hovered = hot.contains(mx, my);
            if (hot.kind() == Kind.TYPE_CHIP && typeFilter.isPresent()) {
                ResourceType type = typeFilter.get();
                int color = ResourceStyle.color(type);
                GuiPaint.box(g, hot.x(), hot.y(), hot.w(), hot.h(), GuiPaint.mix(GuiPaint.BUTTON, color, 0.18f),
                        hovered ? color : GuiPaint.BUTTON_BORDER);
                g.pose().pushPose();
                g.pose().translate(hot.x() + 2, hot.y() + 1, 0);
                g.pose().scale(0.75f, 0.75f, 1f);
                ResourceStyle.drawIcon(g, type, 0, 0);
                g.pose().popPose();
                int textX = hot.x() + 3 + ResourceStyle.ICON;
                GuiText.draw(g, font, tr("list.type", ResourceStyle.name(type)), textX, hot.y() + 2,
                        hot.x() + hot.w() - 2 - textX, hovered ? GuiPaint.FG : color);
            } else if (hot.kind() == Kind.QUICK) {
                QuickFilter filter = QuickFilter.values()[hot.arg()];
                boolean on = quick.contains(filter);
                GuiPaint.box(g, hot.x(), hot.y(), hot.w(), hot.h(), on ? GuiPaint.BUTTON : GuiPaint.PANEL,
                        on ? GuiPaint.FG : hovered ? GuiPaint.BUTTON_HOVER_BORDER : GuiPaint.LINE);
                if (on) {
                    g.fill(hot.x() + 1, hot.y() + hot.h() - 2, hot.x() + hot.w() - 1, hot.y() + hot.h() - 1,
                            GuiPaint.ACCENT);
                }
                g.fill(hot.x() + 3, hot.y() + 3, hot.x() + 7, hot.y() + 7, quickColor(filter));
                int index = quickIndex(hot);
                Component text = index < quickLabels.size() ? quickLabels.get(index) : Component.empty();
                GuiText.draw(g, font, text, hot.x() + 9, hot.y() + 2, hot.w() - 11,
                        on || hovered ? GuiPaint.FG : GuiPaint.MUTED);
            }
        }
    }

    /** A posição da área entre as do tipo e dos filtros rápidos (a mesma do texto em {@link #quickLabels}). */
    private int quickIndex(Hot target) {
        int index = 0;
        for (Hot hot : hots) {
            if (hot == target) {
                return index;
            }
            if (hot.kind() == Kind.QUICK || hot.kind() == Kind.TYPE_CHIP) {
                index++;
            }
        }
        return index;
    }

    private static int quickColor(QuickFilter filter) {
        return switch (filter) {
            case PROBLEM -> GuiPaint.WARN;
            case NO_NETWORK -> GuiPaint.DANGER;
            case UNLOADED -> GuiPaint.MUTED;
        };
    }

    private void renderBanner(GuiGraphics g, Font font, int mx, int my) {
        ProfileSummary s = profile().orElse(null);
        Hot forget = hot(Kind.FORGET, 0);
        if (s == null || forget == null) {
            return;
        }
        int y = forget.y();
        g.fill(x0 + 1, y + 1, x0 + listW - 1, y + BANNER_H - 1, GuiPaint.BUTTON);
        dashed(g, x0, y, listW, BANNER_H, GuiPaint.ACCENT);
        GuiPaint.text(g, font, Component.literal("▣"), x0 + 3, y + 2, GuiPaint.ACCENT);
        GuiText.draw(g, font, tr("list.profile.short", sourceName(s)), x0 + 12, y + 2, forget.x() - x0 - 14, GuiPaint.FG);
        GuiPaint.text(g, font, forgetLabel(font), forget.x() + 3, y + 2,
                forget.contains(mx, my) ? GuiPaint.ACCENT : GuiPaint.MUTED);
    }

    private String sourceName(ProfileSummary s) {
        return s.sourceName().isEmpty() ? machineName(s.machine()) : s.sourceName();
    }

    /** "esquecer ✕", ou só o ✕ quando ele tiraria o lugar do nome do perfil. */
    private Component forgetLabel(Font font) {
        Component full = tr("list.profile.forget");
        return font.width(full) + 6 <= listW / 3 ? full : Component.literal("✕");
    }

    /** A dica da faixa: de onde veio o perfil, a máquina e o que ele tem. */
    private @Nullable Component bannerTooltip(double mx, double my) {
        ProfileSummary s = profile().orElse(null);
        Hot forget = hot(Kind.FORGET, 0);
        if (s == null || forget == null || mx < x0 || mx >= forget.x() || my < forget.y() || my >= forget.y() + BANNER_H) {
            return null;
        }
        return tr("list.profile.banner", sourceName(s), machineName(s.machine()), profileSummary(s));
    }

    private static Component profileSummary(ProfileSummary s) {
        if (s.faces() == 0) {
            return tr("list.profile.empty");
        }
        MutableComponent types = Component.empty();
        for (int i = 0; i < s.types().size(); i++) {
            if (i > 0) {
                types.append(", ");
            }
            types.append(ResourceStyle.name(s.types().get(i)));
        }
        return tr(s.faces() == 1 ? "list.profile.summary.one" : "list.profile.summary", types, s.faces());
    }

    private void renderRows(GuiGraphics g, Font font, int mx, int my) {
        TabletSnapshot s = snapshot();
        List<NodeView> nodes = s.nodes();
        if (nodes.isEmpty()) {
            renderEmpty(g, font);
            return;
        }
        boolean chips = listW >= CHIPS_MIN_LIST;
        ProfileSummary profile = s.profile().orElse(null);
        List<ListGroups.Row<ResourceLocation>> rows = groups().rows();
        for (Hot hot : hots) {
            if (hot.kind() == Kind.HEADER) {
                renderHeader(g, font, hot, rows.get(hot.arg()), profile, mx, my);
            } else if (hot.kind() == Kind.ROW) {
                renderNode(g, font, hot, nodes.get(hot.arg()), profile, chips, hot.contains(mx, my));
            }
        }
        // o resultado cortado pelo servidor: a última linha diz quantos casaram
        if (s.matchingNodes() > nodes.size() && drawnUntil >= rows.size() && rowsEndY + LIMIT_ROW <= rowsBottom) {
            GuiText.draw(g, font, tr("list.limit", nodes.size(), s.matchingNodes()), x0 + 4, rowsEndY + 2, listW - 8,
                    GuiPaint.MUTED);
        }
        renderScrollbar(g);
    }

    /** A barra de rolagem fina na borda direita das linhas, só quando elas não cabem. */
    private void renderScrollbar(GuiGraphics g) {
        int max = maxScroll();
        if (max <= 0) {
            return;
        }
        int track = rowsBottom - rowsTop;
        int rows = rowCount();
        int thumb = Math.max(8, track * (rows - max) / rows);
        int top = rowsTop + (track - thumb) * scroll / max;
        int x = x0 + listW - 2;
        g.fill(x, rowsTop, x + 2, rowsBottom, GuiPaint.LINE);
        g.fill(x, top, x + 2, top + thumb, GuiPaint.MUTED);
    }

    private void renderEmpty(GuiGraphics g, Font font) {
        TabletSnapshot s = snapshot();
        int cy = rowsTop + Math.max(10, (rowsBottom - rowsTop) / 2 - 12);
        if (s.totalNodes() == 0) {
            GuiText.wrapCentered(g, font, tr("list.empty"), x0 + listW / 2, cy, listW - 8, 2, GuiPaint.MUTED);
            return;
        }
        Component why = text.isBlank() ? tr("list.no_match") : tr("list.no_match.text", text.strip());
        GuiText.wrapCentered(g, font, why, x0 + listW / 2, cy, listW - 8, 2, GuiPaint.MUTED);
        int[] clear = emptyClearCenter();
        if (clear != null) {
            Component label = tr("list.clear_search");
            int w = font.width(label);
            GuiPaint.text(g, font, label, clear[0] - w / 2, clear[1] - 4, GuiPaint.ACCENT);
            g.fill(clear[0] - w / 2, clear[1] + 4, clear[0] - w / 2 + w - 1, clear[1] + 5, GuiPaint.ACCENT);
        }
    }

    private int @Nullable [] emptyClearCenter() {
        TabletSnapshot s = snapshot();
        if (!s.nodes().isEmpty() || s.totalNodes() == 0 || !filtering()) {
            return null;
        }
        int cy = rowsTop + Math.max(10, (rowsBottom - rowsTop) / 2 - 12) + 26;
        return new int[] {x0 + listW / 2, cy};
    }

    private void renderHeader(GuiGraphics g, Font font, Hot hot, ListGroups.Row<ResourceLocation> row,
            @Nullable ProfileSummary profile, int mx, int my) {
        boolean hovered = hot.contains(mx, my);
        g.fill(hot.x(), hot.y(), hot.x() + hot.w(), hot.y() + hot.h(), hovered ? GuiPaint.ROW_HOVER : GuiPaint.INSET);
        g.fill(hot.x(), hot.y() + hot.h() - 1, hot.x() + hot.w(), hot.y() + hot.h(), GuiPaint.LINE);
        int color = hovered ? GuiPaint.FG : GuiPaint.MUTED;
        if (row.collapsed()) {
            GuiPaint.arrowRight(g, hot.x() + 4, hot.y() + 3, color);
        } else {
            GuiPaint.arrowDown(g, hot.x() + 3, hot.y() + 4, color);
        }
        Hot paste = hot(Kind.PASTE_GROUP, hot.arg());
        int right = paste != null ? paste.x() - 3 : hot.x() + hot.w() - 3;
        int count = Math.max(row.count(), snapshot().groupCount(row.group()));
        MutableComponent text = Component.literal(machineName(row.group())).copy()
                .append(Component.literal(" · " + count).withColor(GuiPaint.MUTED));
        int differ = profile == null ? 0 : differing(row.group());
        if (differ > 0) {
            text.append(Component.literal(" · ").withColor(GuiPaint.MUTED)).append(
                    TabletScreen.plural("list.group.differ", differ).copy().withColor(GuiPaint.WARN));
        }
        GuiText.draw(g, font, text, hot.x() + 11, hot.y() + 2, right - hot.x() - 11, GuiPaint.FG);
        if (paste != null && profile != null) {
            boolean same = profile.machine().equals(row.group());
            boolean over = paste.contains(mx, my);
            int fill = same ? (over ? GuiPaint.mix(GuiPaint.OK, GuiPaint.FG, 0.2f) : GuiPaint.OK)
                    : over ? GuiPaint.BUTTON : GuiPaint.PANEL;
            GuiPaint.box(g, paste.x(), paste.y(), paste.w(), paste.h(), fill, same ? fill : GuiPaint.BUTTON_BORDER);
            GuiPaint.textCentered(g, font, tr("list.paste_group", pasteCount(row.group())),
                    paste.x() + paste.w() / 2 + 1, paste.y() + 1, same ? GuiPaint.SELECTED_TEXT : GuiPaint.FG);
        }
    }

    /** Quantos nós recebidos do grupo diferem do perfil (os carregados que compararam). */
    private int differing(ResourceLocation machine) {
        int count = 0;
        for (NodeView node : snapshot().nodes()) {
            if (node.machine().equals(machine) && node.profile().map(m -> !m.equal()).orElse(false)) {
                count++;
            }
        }
        return count;
    }

    private void renderNode(GuiGraphics g, Font font, Hot hot, NodeView node, @Nullable ProfileSummary profile,
            boolean chips, boolean hovered) {
        boolean isChosen = node.key().equals(chosen);
        boolean picked = selected.contains(node.key());
        int x = hot.x();
        int y = hot.y();
        if (isChosen || picked) {
            g.fill(x, y, x + hot.w(), y + hot.h() - 1, GuiPaint.ROW_SELECTED);
            g.fill(x, y, x + 2, y + hot.h() - 1, GuiPaint.ACCENT);
        } else if (hovered) {
            g.fill(x, y, x + hot.w(), y + hot.h() - 1, GuiPaint.ROW_HOVER);
        }
        g.fill(x + 2, y + hot.h() - 1, x + hot.w() - 2, y + hot.h(), GuiPaint.LINE);
        int cx = x + 5;
        if (multi) {
            GuiPaint.checkbox(g, cx, y + 5, picked, GuiPaint.ACCENT);
            cx += 12;
        }
        GuiPaint.dot(g, cx, y + 7, TabletScreen.statusColor(node.status()));
        int textX = cx + 8;
        int right = x + hot.w() - 3;
        if (chips) {
            right -= 3 * CHIP + 2 + 4;
            renderChips(g, right + 4, y + 5, node);
        }
        GuiText.draw(g, font, nodeName(node), textX, y + 2, right - textX, GuiPaint.FG);
        Component line = stateLine(node, profile);
        GuiText.draw(g, font, line, textX, y + 11, right - textX, GuiPaint.MUTED);
    }

    /** Os seis quadradinhos das faces na aba do painel: Cima, Frente, Esquerda / Baixo, Trás, Direita. */
    private void renderChips(GuiGraphics g, int x, int y, NodeView node) {
        for (int i = 0; i < FaceNet.CHIP_ORDER.size(); i++) {
            RemoteSide side = FaceNet.CHIP_ORDER.get(i);
            int cx = x + (i % 3) * (CHIP + 1);
            int cy = y + (i / 3) * (CHIP + 1);
            PortMode mode = node.faceMode(chipType, side);
            if (mode == PortMode.NONE) {
                GuiPaint.outline(g, cx, cy, CHIP, CHIP, GuiPaint.LINE);
            } else {
                g.fill(cx, cy, cx + CHIP, cy + CHIP, GuiPaint.modeColor(mode));
            }
        }
    }

    /**
     * A segunda linha do nó: com perfil copiado, num nó carregado, "igual ao perfil" ou a primeira diferença (a
     * origem diz que é a origem); senão o estado em palavras ("destino cheio", "sem rede", "descarregada",
     * "pausada") ou, tudo bem, a rede e a vazão na aba do painel.
     */
    private Component stateLine(NodeView node, @Nullable ProfileSummary profile) {
        if (profile != null && node.key().equals(profile.origin())) {
            return tr("list.profile.origin").copy().withColor(GuiPaint.ACCENT);
        }
        if (profile != null && node.profile().isPresent()) {
            ProfileMatch match = node.profile().get();
            if (match.equal()) {
                return tr("list.profile.equal").copy().withColor(GuiPaint.OK);
            }
            Component side = Component.translatable("gui.wirelessautomate.router.side." + match.side().key());
            if (match.type() != chipType) {
                side = side.copy().append(" (").append(ResourceStyle.name(match.type())).append(")");
            }
            return tr("list.diff." + match.what().name().toLowerCase(Locale.ROOT), side).copy().withColor(GuiPaint.WARN);
        }
        return switch (node.status()) {
            case UNLOADED -> tr("list.state.unloaded").copy().withColor(GuiPaint.MUTED);
            case NO_NETWORK -> tr("list.state.no_network").copy().withColor(GuiPaint.DANGER);
            case FULL -> tr("list.state.full").copy().withColor(GuiPaint.WARN);
            case PAUSED -> tr("list.state.paused").copy().withColor(GuiPaint.PAUSED);
            case ACTIVE, IDLE -> okLine(node);
        };
    }

    private Component okLine(NodeView node) {
        ResourceType type = chipType;
        List<UUID> networks = node.networks(type);
        if (networks.isEmpty()) {
            for (ResourceType t : LoadedTypes.LIST) {
                if (!node.networks(t).isEmpty()) {
                    type = t;
                    networks = node.networks(t);
                    break;
                }
            }
        }
        Component network = networks.isEmpty() ? tr("network.none") : Component.literal(networkName(networks.getFirst()));
        return tr("list.state.ok", network, ResourceStyle.rate(type, node.rate(type))).copy().withColor(GuiPaint.MUTED);
    }

    private Component countText() {
        TabletSnapshot s = snapshot();
        return s.matchingNodes() > s.nodes().size() ? tr("list.limit", s.nodes().size(), s.matchingNodes())
                : tr("list.count", s.matchingNodes(), s.totalNodes());
    }

    /** A linha de baixo da lista: a contagem com Limpar (com busca ou filtro). */
    private void renderBar(GuiGraphics g, Font font, int mx, int my) {
        if (multi || !filtering()) {
            return;
        }
        Hot clear = hot(Kind.CLEAR, 0);
        int countW = clear == null ? 0 : clear.x() - 4 - x0;
        GuiText.draw(g, font, countText(), x0, barY() + 3, countW, GuiPaint.MUTED);
        if (clear != null) {
            GuiPaint.text(g, font, Component.literal("·"), clear.x() - 4, barY() + 3, GuiPaint.MUTED);
            boolean over = clear.contains(mx, my);
            GuiPaint.text(g, font, tr("list.clear"), clear.x() + 1, barY() + 3, over ? GuiPaint.ACCENT : GuiPaint.FG);
            g.fill(clear.x() + 1, barY() + 12, clear.x() + clear.w() - 1, barY() + 13, GuiPaint.ACCENT);
        }
    }

    private void renderPanel(GuiGraphics g, Font font, int mx, int my) {
        if (panelW <= 0) {
            return;
        }
        if (chosen == null) {
            GuiText.wrapCentered(g, font, tr("list.panel.pick"), panelX + panelW / 2, y0 + 60, panelW - 8, 3,
                    GuiPaint.MUTED);
            return;
        }
        TabletPanelPayload payload = panelPayload();
        RouterSnapshot snap = payload == null ? null : payload.snapshot().orElse(null);
        NodeView node = node(chosen);
        Hot full = hot(Kind.FULL_SCREEN, 0);
        int nameRoom = (full == null ? panelX + panelW : full.x() - 4) - panelX;
        Component name = snap != null && !snap.name().isEmpty() ? Component.literal(snap.name())
                : node != null ? nodeName(node)
                : snap != null ? RouterBlock.machineName(snap.machineState().getBlock()) : Component.empty();
        GuiText.draw(g, font, name, panelX, y0 + 2, nameRoom, GuiPaint.FG);
        if (full != null) {
            textButton(g, font, full, fullScreenLabel(font), full.contains(mx, my), true);
        }
        if (snap == null) {
            Component wait = payload == null ? tr("list.panel.loading")
                    : Objects.requireNonNullElse(readOnlyReason(payload), tr("list.panel.loading"));
            GuiText.wrap(g, font, wait, panelX, y0 + 16, panelW, 3, payload == null ? GuiPaint.MUTED : GuiPaint.WARN);
            return;
        }
        Component machine = RouterBlock.machineName(snap.machineState().getBlock());
        Component distance = payload.distance() < 0 ? tr("list.panel.other_dimension")
                : TabletScreen.plural("list.panel.distance", payload.distance());
        Component meta = machine.copy().append(" · ").append(Component.translatable(snap.tier().translationKey()))
                .append(" · ").append(distance);
        GuiText.draw(g, font, meta, panelX, y0 + 13, panelW, GuiPaint.MUTED);
        NodePanel p = panel;
        if (p == null) {
            return;
        }
        p.render(g, inSuggestions(mx, my) ? -1 : mx, my, null);
        boolean edit = payload.access() == PanelAccess.EDIT;
        Hot copy = hot(Kind.COPY, 0);
        if (copy != null) {
            textButton(g, font, copy, copyLabel(), copy.contains(mx, my), edit);
            Hot paste = hot(Kind.PASTE, 0);
            if (paste != null) {
                textButton(g, font, paste, pasteLabel(), paste.contains(mx, my), edit);
            } else {
                int hintX = copy.x() + copy.w() + 4;
                GuiText.draw(g, font, tr("list.copy.hint"), hintX, copy.y() + 3, panelX + panelW - hintX, GuiPaint.DISABLED);
            }
        }
    }

    private static void textButton(GuiGraphics g, Font font, Hot b, Component text, boolean hovered, boolean active) {
        int border = hovered && active ? GuiPaint.BUTTON_HOVER_BORDER : GuiPaint.BUTTON_BORDER;
        GuiPaint.box(g, b.x(), b.y(), b.w(), b.h(), active ? GuiPaint.BUTTON : GuiPaint.INSET, active ? border : GuiPaint.LINE);
        GuiText.draw(g, font, text, b.x() + 4, b.y() + (b.h() - 8) / 2, b.w() - 7, active ? GuiPaint.FG : GuiPaint.DISABLED);
    }

    /** O aviso de colar sem painel aberto: embaixo da coluna do painel, com o Desfazer. */
    private void renderOwnNotice(GuiGraphics g, Font font, int mouseX, int mouseY) {
        PanelNoticePayload notice = PanelNotices.current();
        if (notice == null || panelW <= 0) {
            return;
        }
        int y = ownNoticeY();
        g.pose().pushPose();
        g.pose().translate(0, 0, 350);
        g.fill(panelX, y, panelX + panelW, y + 13, GuiPaint.FG);
        Hot undo = hot(Kind.UNDO, 0);
        int room = panelW - 8 - (undo != null ? undo.w() + 2 : 0);
        GuiText.draw(g, font, notice.message(), panelX + 4, y + 3, room, GuiPaint.PANEL);
        if (undo != null) {
            Component label = Component.translatable("gui.wirelessautomate.panel.notice.undo");
            boolean over = undo.contains(mouseX, mouseY);
            GuiPaint.text(g, font, label, undo.x() + 4, y + 3, over ? GuiPaint.ACCENT : GuiPaint.PANEL);
            g.fill(undo.x() + 4, y + 11, undo.x() + 3 + font.width(label), y + 12, GuiPaint.ACCENT);
        }
        g.pose().popPose();
    }

    private void renderSuggestions(GuiGraphics g, Font font, int mouseX, int mouseY) {
        int x = x0;
        int y = suggestTop();
        int w = suggestWidth();
        g.pose().pushPose();
        g.pose().translate(0, 0, 400);
        GuiPaint.box(g, x, y, w, suggestHeight(), GuiPaint.PANEL, GuiPaint.BUTTON_BORDER);
        int sy = y + 1;
        for (SuggestLine line : suggestLines()) {
            if (line.title() != null) {
                GuiText.draw(g, font, tr("list.suggest." + line.title().name().toLowerCase(Locale.ROOT)), x + 5, sy + 1,
                        w - 10, GuiPaint.MUTED);
                sy += SUGGEST_TITLE;
                continue;
            }
            Suggestion<?> s = suggestions.get(line.index());
            boolean hi = line.index() == highlight || (mouseX >= x && mouseX < x + w && mouseY >= sy
                    && mouseY < sy + SUGGEST_ROW);
            if (hi) {
                g.fill(x + 1, sy, x + w - 1, sy + SUGGEST_ROW, GuiPaint.ROW_SELECTED);
                g.fill(x + 1, sy, x + 3, sy + SUGGEST_ROW, GuiPaint.ACCENT);
            }
            Component right = suggestRight(s);
            int rightW = Math.min(font.width(right), w / 3);
            GuiText.draw(g, font, right, x + w - 5 - rightW, sy + 2, rightW, GuiPaint.MUTED);
            GuiText.draw(g, font, highlighted(s), x + 8, sy + 2, w - 18 - rightW, GuiPaint.FG);
            sy += SUGGEST_ROW;
        }
        g.fill(x + 3, sy, x + w - 3, sy + 1, GuiPaint.LINE);
        GuiText.draw(g, font, tr("list.suggest.footer", text.strip()), x + 5, sy + 2, w - 10, GuiPaint.DISABLED);
        g.pose().popPose();
    }

    /** O nome com o trecho casado em coral e sublinhado. */
    private static Component highlighted(Suggestion<?> s) {
        String label = s.label();
        return Component.literal(label.substring(0, s.start()))
                .append(Component.literal(label.substring(s.start(), s.end()))
                        .withStyle(Style.EMPTY.withColor(GuiPaint.ACCENT).withUnderlined(true)))
                .append(Component.literal(label.substring(s.end())));
    }

    /** À direita da sugestão: quantos nós pega; num nome de um nó só, a máquina dele. */
    private Component suggestRight(Suggestion<?> s) {
        if (s.group() == Group.NAMES && s.count() == 1) {
            for (NodeView node : snapshot().nodes()) {
                if (node.name().equals(s.label())) {
                    return Component.literal(machineName(node.machine()));
                }
            }
        }
        return TabletScreen.plural("meta.nodes", s.count());
    }

    /** Contorno tracejado de 1 px (a faixa do perfil). */
    private static void dashed(GuiGraphics g, int x, int y, int w, int h, int color) {
        for (int i = 0; i < w; i += 3) {
            int end = Math.min(i + 2, w);
            g.fill(x + i, y, x + end, y + 1, color);
            g.fill(x + i, y + h - 1, x + end, y + h, color);
        }
        for (int i = 0; i < h; i += 3) {
            int end = Math.min(i + 2, h);
            g.fill(x, y + i, x + 1, y + end, color);
            g.fill(x + w - 1, y + i, x + w, y + end, color);
        }
    }

    // ------------------------------------------------------------------ ganchos do e2e e das capturas

    /** Centro da linha do nó, ou {@code null} se ela não está na vista (grupo recolhido ou rolado). */
    int @Nullable [] nodeRowCenter(NodeKey key) {
        arrange();
        Hot hot = hot(Kind.ROW, indexOf(key));
        return hot == null ? null : new int[] {hot.x() + hot.w() / 2, hot.y() + hot.h() / 2};
    }

    /** Centro da sugestão do valor {@code id} (máquina, rede ou nome) na lista aberta, ou {@code null}. */
    int @Nullable [] suggestionCenter(Object id) {
        arrange();
        for (int i = 0; i < suggestions.size(); i++) {
            if (suggestions.get(i).id().equals(id)) {
                Hot hot = hot(Kind.SUGGESTION, i);
                return hot == null ? null : hot.center();
            }
        }
        return null;
    }

    /** Centro do ✕ da etiqueta {@code index}. */
    int @Nullable [] labelCenter(int index) {
        arrange();
        Hot hot = hot(Kind.LABEL, index);
        return hot == null ? null : new int[] {hot.x() + hot.w() - 5, hot.y() + hot.h() / 2};
    }

    int @Nullable [] quickCenter(QuickFilter filter) {
        arrange();
        Hot hot = hot(Kind.QUICK, filter.ordinal());
        return hot == null ? null : hot.center();
    }

    int @Nullable [] typeChipCenter() {
        arrange();
        Hot hot = hot(Kind.TYPE_CHIP, 0);
        return hot == null ? null : hot.center();
    }

    /** O "Colar nas N" do grupo da máquina. */
    int @Nullable [] pasteGroupCenter(ResourceLocation machine) {
        arrange();
        int row = groups().headerRow(machine);
        Hot hot = row < 0 ? null : hot(Kind.PASTE_GROUP, row);
        return hot == null ? null : hot.center();
    }

    int @Nullable [] groupHeaderCenter(ResourceLocation machine) {
        arrange();
        int row = groups().headerRow(machine);
        Hot hot = row < 0 ? null : hot(Kind.HEADER, row);
        return hot == null ? null : new int[] {hot.x() + 20, hot.y() + hot.h() / 2};
    }

    int @Nullable [] copyCenter() {
        arrange();
        Hot hot = hot(Kind.COPY, 0);
        return hot == null ? null : hot.center();
    }

    int @Nullable [] pasteCenter() {
        arrange();
        Hot hot = hot(Kind.PASTE, 0);
        return hot == null ? null : hot.center();
    }

    int @Nullable [] fullScreenCenter() {
        arrange();
        Hot hot = hot(Kind.FULL_SCREEN, 0);
        return hot == null ? null : hot.center();
    }

    int @Nullable [] forgetCenter() {
        arrange();
        Hot hot = hot(Kind.FORGET, 0);
        return hot == null ? null : hot.center();
    }

    int @Nullable [] clearCenter() {
        arrange();
        Hot hot = hot(Kind.CLEAR, 0);
        return hot == null ? null : hot.center();
    }

    /** O Desfazer do aviso: o do painel, ou o da faixa de aviso sem painel. */
    int @Nullable [] undoCenter() {
        arrange();
        NodePanel p = panel;
        if (p != null) {
            return p.undoCenter();
        }
        Hot hot = hot(Kind.UNDO, 0);
        return hot == null ? null : hot.center();
    }

    boolean suggestionsOpen() {
        return suggestOpen && !suggestions.isEmpty();
    }

    List<Object> labelIds() {
        List<Object> ids = new ArrayList<>();
        labels.forEach(l -> ids.add(l.machine() != null ? l.machine() : l.network()));
        return ids;
    }
}
