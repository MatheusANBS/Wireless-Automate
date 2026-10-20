package io.github.matheusanbs.wirelessautomate.client.tablet;

import static io.github.matheusanbs.wirelessautomate.client.tablet.TabletText.tr;

import io.github.matheusanbs.wirelessautomate.client.GuiPaint;
import io.github.matheusanbs.wirelessautomate.client.RateFormat;
import io.github.matheusanbs.wirelessautomate.client.ResourceStyle;
import io.github.matheusanbs.wirelessautomate.menu.TabletRows;
import io.github.matheusanbs.wirelessautomate.menu.TabletRows.MachineLive;
import io.github.matheusanbs.wirelessautomate.menu.TabletRows.MachineStatus;
import io.github.matheusanbs.wirelessautomate.menu.TabletSnapshot;
import io.github.matheusanbs.wirelessautomate.menu.TabletSnapshot.NodeView;
import io.github.matheusanbs.wirelessautomate.network.LoadedTypes;
import io.github.matheusanbs.wirelessautomate.network.ResourceType;
import java.util.List;
import java.util.Locale;
import net.minecraft.client.gui.Font;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import org.jetbrains.annotations.Nullable;

/**
 * Os textos das linhas da árvore da Lista do Tablet, com as larguras, montados quando as linhas ou o estado e a
 * vazão das máquinas mudam (a {@link TabletRows} é outro objeto), nunca por quadro (as cores do tema vêm no desenho): o nome de cada linha, a
 * segunda linha (o bloco e o lado da presa, as exceções de um grupo, as coordenadas de uma máquina), o estado em
 * palavras e a vazão de cada máquina, na cor do tipo. Por posição na lista de linhas do servidor.
 */
final class TreeTexts {
    static final Component SEPARATOR = Component.literal(" · ");

    private final ListTexts texts;
    private @Nullable TabletRows built;
    Component[] names = new Component[0];
    int[] nameW = new int[0];
    /** A segunda linha: o bloco e o lado da presa, as exceções do grupo ou as coordenadas da máquina. */
    Component[] seconds = new Component[0];
    int[] secondW = new int[0];
    /** O estado de cada máquina em palavras (vazio nas outras linhas). */
    Component[] states = new Component[0];
    int[] stateW = new int[0];
    /** A vazão de cada máquina, já com a cor de cada tipo; vazio sem vazão a mostrar. */
    Component[] rates = new Component[0];
    int[] rateW = new int[0];
    /** O estado e a vazão de cada linha de máquina; {@code null} nas outras. */
    MachineLive[] lives = new MachineLive[0];
    /** O "#N" de cada máquina sem rótulo: a posição dela no grupo, como na tela do roteador. */
    int[] numbers = new int[0];
    Component less = Component.empty();
    int lessW;
    Component badge = Component.empty();
    int badgeW;
    int separatorW;

    TreeTexts(ListTexts texts) {
        this.texts = texts;
    }

    /** Monta os textos se as linhas da página mudaram. */
    void prepare(Font font, TabletSnapshot snapshot) {
        TabletRows rows = snapshot.rows();
        if (rows == built) {
            return;
        }
        built = rows;
        int n = rows.rows().size();
        names = new Component[n];
        nameW = new int[n];
        seconds = new Component[n];
        secondW = new int[n];
        states = new Component[n];
        stateW = new int[n];
        rates = new Component[n];
        rateW = new int[n];
        lives = new MachineLive[n];
        numbers = new int[n];
        separatorW = font.width(SEPARATOR);
        less = tr("tree.show_few");
        lessW = font.width(less);
        badge = Component.translatable("gui.wirelessautomate.router.range.machine.exception");
        badgeW = font.width(badge);
        List<NodeView> nodes = snapshot.nodes();
        int live = 0;
        int number = 0;
        for (int r = 0; r < n; r++) {
            TabletRows.Row row = rows.rows().get(r);
            seconds[r] = Component.empty();
            states[r] = Component.empty();
            rates[r] = Component.empty();
            switch (row) {
                case TabletRows.Attached attached -> {
                    names[r] = Component.translatable("gui.wirelessautomate.router.range.attached");
                    NodeView node = nodes.get(attached.parent());
                    Component block = Component.literal(texts.machineName(node.machine()));
                    seconds[r] = attached.side().<Component>map(side -> tr("tree.attached.detail", block,
                            Component.translatable("gui.wirelessautomate.router.face." + side.getSerializedName())))
                            .orElse(block);
                }
                case TabletRows.Group group -> {
                    number = 0;
                    String name = group.name().isEmpty() ? texts.machineName(group.block()) : group.name();
                    names[r] = Component.translatable("gui.wirelessautomate.router.range.group.count", name, group.total());
                    seconds[r] = group.exceptions() == 0 ? Component.empty()
                            : Component.translatable("gui.wirelessautomate.router.range.group.exceptions"
                                    + (group.exceptions() == 1 ? ".one" : ""), group.exceptions());
                }
                case TabletRows.Machine machine -> {
                    number++;
                    numbers[r] = number;
                    names[r] = Component.literal(machine.label().isEmpty() ? "#" + number : machine.label());
                    BlockPos pos = BlockPos.of(machine.pos());
                    seconds[r] = Component.literal(pos.getX() + " " + pos.getY() + " " + pos.getZ());
                    MachineLive state = live < rows.lives().size() ? rows.lives().get(live) : null;
                    live++;
                    lives[r] = state;
                    if (state != null) {
                        states[r] = stateText(state.status());
                        rates[r] = rateText(state);
                    }
                }
                case TabletRows.More more -> names[r] = tr("tree.more", more.hidden());
            }
            nameW[r] = font.width(names[r]);
            secondW[r] = font.width(seconds[r]);
            stateW[r] = font.width(states[r]);
            rateW[r] = font.width(rates[r]);
        }
    }

    static Component stateText(MachineStatus status) {
        return tr("tree.state." + status.name().toLowerCase(Locale.ROOT));
    }

    /** A cor do estado em palavras. */
    static int stateColor(MachineStatus status) {
        return switch (status) {
            case MOVING -> GuiPaint.OK;
            case FULL, UNLOADED, NO_UPGRADE -> GuiPaint.WARN;
            case SLEEPING, OUT_OF_RANGE, OVER_LIMIT -> GuiPaint.MUTED;
            case REPLACED -> GuiPaint.BRASS;
            case MISSING, NO_NETWORK -> GuiPaint.DANGER;
            case PAUSED -> GuiPaint.PAUSED;
        };
    }

    /** A cor da bolinha: verde movendo, âmbar cheia, cinza no resto que não é problema. */
    static int dotColor(MachineStatus status) {
        return switch (status) {
            case MOVING -> GuiPaint.OK;
            case FULL, NO_UPGRADE -> GuiPaint.WARN;
            case SLEEPING, UNLOADED, OUT_OF_RANGE, OVER_LIMIT -> GuiPaint.MUTED;
            case REPLACED -> GuiPaint.BRASS;
            case MISSING, NO_NETWORK -> GuiPaint.DANGER;
            case PAUSED -> GuiPaint.PAUSED;
        };
    }

    /**
     * A vazão curta, "↓8/s ↑8/s", cada parte na cor do tipo: o que recebe (↓) e o que dá (↑) de cada tipo
     * carregado. Sem movimento, vazia: a linha fica só com o estado em palavras.
     */
    static Component rateText(MachineLive live) {
        MutableComponent text = Component.empty();
        for (ResourceType type : LoadedTypes.LIST) {
            appendRate(text, "↓", type, live.received(type));
            appendRate(text, "↑", type, live.given(type));
        }
        return text;
    }

    private static void appendRate(MutableComponent text, String arrow, ResourceType type, long value) {
        if (value == 0) {
            return;
        }
        if (!text.getSiblings().isEmpty()) {
            text.append(" ");
        }
        Component amount = type == ResourceType.ITEM ? Component.literal(RateFormat.abbreviate(value) + "/s")
                : ResourceStyle.rate(type, value);
        text.append(Component.literal(arrow).append(amount).withColor(ResourceStyle.color(type)));
    }

    /** A vazão por extenso, para a dica: uma linha por parte, "↓ recebe 8 itens/s". */
    static Component rateTooltip(MachineLive live) {
        MutableComponent text = Component.empty();
        for (ResourceType type : LoadedTypes.LIST) {
            long received = live.received(type);
            long given = live.given(type);
            if (received != 0) {
                text.append("\n").append(tr("tree.received", ResourceStyle.rate(type, received)).copy()
                        .withColor(ResourceStyle.color(type)));
            }
            if (given != 0) {
                text.append("\n").append(tr("tree.given", ResourceStyle.rate(type, given)).copy()
                        .withColor(ResourceStyle.color(type)));
            }
        }
        return text;
    }
}
