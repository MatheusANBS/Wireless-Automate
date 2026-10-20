package io.github.matheusanbs.wirelessautomate.client.tablet;

import static io.github.matheusanbs.wirelessautomate.client.tablet.TabletText.plural;
import static io.github.matheusanbs.wirelessautomate.client.tablet.TabletText.tr;

import io.github.matheusanbs.wirelessautomate.client.GuiPaint;
import io.github.matheusanbs.wirelessautomate.client.GuiThemes;
import io.github.matheusanbs.wirelessautomate.client.RateFormat;
import io.github.matheusanbs.wirelessautomate.client.ResourceStyle;
import io.github.matheusanbs.wirelessautomate.menu.TabletMachineRows;
import io.github.matheusanbs.wirelessautomate.menu.TabletMachineRows.RouterRef;
import io.github.matheusanbs.wirelessautomate.menu.TabletRows.MachineLive;
import io.github.matheusanbs.wirelessautomate.menu.TabletRows.MachineStatus;
import io.github.matheusanbs.wirelessautomate.network.LoadedTypes;
import io.github.matheusanbs.wirelessautomate.network.ResourceType;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.Font;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

/**
 * Os textos das linhas da visão por máquinas, com as larguras, montados quando a página de máquinas muda (a
 * {@link TabletMachineRows} é outro objeto: linhas novas, ou o estado e a vazão de uma amostra) ou o tema muda; por
 * quadro, só o desenho. No cabeçalho do bloco: o ícone, "Fornalha · 23" e "3 roteadores". Na máquina: o nome (o
 * rótulo, "#N" ou, na presa, o nome do roteador), a segunda linha ("via Forja" ou "presa", com as coordenadas ou,
 * quando pede atenção, o estado em palavras) e a vazão, só quando move. Por posição nas linhas da página. As linhas
 * para a navegação vão no {@link MachineLines}.
 */
final class MachineRowTexts {
    static final Component SEPARATOR = Component.literal(" · ");

    private final ListTexts texts;
    private @Nullable TabletMachineRows built;
    private int builtTheme = -1;
    MachineLines lines = new MachineLines(List.of(), new boolean[0], new int[0], new boolean[0]);
    Component[] names = new Component[0];
    int[] nameW = new int[0];
    /** No cabeçalho, "N roteadores"; na máquina, a segunda linha inteira; no "mais N", vazio. */
    Component[] seconds = new Component[0];
    int[] secondW = new int[0];
    /** A vazão de cada máquina que move, já com a cor de cada tipo; vazio nas outras. */
    Component[] rates = new Component[0];
    int[] rateW = new int[0];
    /** O estado e a vazão de cada linha de máquina; {@code null} nas outras. */
    MachineLive[] lives = new MachineLive[0];
    /** O ícone do bloco de cada cabeçalho. */
    ItemStack[] icons = new ItemStack[0];
    /** O nome do bloco de cada linha (o do cabeçalho acima dela). */
    private String[] blocks = new String[0];
    Component badge = Component.empty();
    int badgeW;
    Component less = Component.empty();
    int lessW;

    MachineRowTexts(ListTexts texts) {
        this.texts = texts;
    }

    /** Monta os textos se a página de máquinas ou o tema mudaram. */
    void prepare(Font font, TabletMachineRows page) {
        if (page == built && builtTheme == GuiThemes.version()) {
            return;
        }
        built = page;
        builtTheme = GuiThemes.version();
        List<TabletMachineRows.Row> rows = page.rows();
        int n = rows.size();
        names = new Component[n];
        nameW = new int[n];
        seconds = new Component[n];
        secondW = new int[n];
        rates = new Component[n];
        rateW = new int[n];
        lives = new MachineLive[n];
        icons = new ItemStack[n];
        blocks = new String[n];
        badge = Component.translatable("gui.wirelessautomate.router.range.machine.exception");
        badgeW = font.width(badge);
        less = tr("tree.show_few");
        lessW = font.width(less);
        lines = lines(page);
        ResourceLocation[] presaBlocks = presaBlocks(page);
        int live = 0;
        String blockName = "";
        for (int r = 0; r < n; r++) {
            seconds[r] = Component.empty();
            rates[r] = Component.empty();
            icons[r] = ItemStack.EMPTY;
            switch (rows.get(r)) {
                case TabletMachineRows.Block block -> {
                    blockName = texts.machineName(block.block());
                    names[r] = Component.literal(blockName)
                            .append(Component.literal(" · " + block.machines()).withColor(GuiPaint.MUTED));
                    seconds[r] = plural("machines.routers", block.routers());
                    icons[r] = new ItemStack(BuiltInRegistries.BLOCK.get(block.block()).asItem());
                }
                case TabletMachineRows.Machine machine -> {
                    MachineLive state = live < page.lives().size() ? page.lives().get(live) : null;
                    live++;
                    lives[r] = state;
                    machine(r, page, machine, state, presaBlocks);
                }
                case TabletMachineRows.More more -> names[r] = tr("tree.more", more.hidden());
            }
            blocks[r] = blockName;
            nameW[r] = font.width(names[r]);
            secondW[r] = font.width(seconds[r]);
            rateW[r] = font.width(rates[r]);
        }
    }

    private void machine(int r, TabletMachineRows page, TabletMachineRows.Machine machine, @Nullable MachineLive state,
            ResourceLocation[] presaBlocks) {
        RouterRef router = page.routers().get(machine.router());
        Component routerName = routerName(router, presaBlocks[machine.router()]);
        names[r] = !machine.label().isEmpty() ? Component.literal(machine.label())
                : machine.attached() ? routerName : Component.literal("#" + lines.number(r));
        MachineStatus status = state == null ? MachineStatus.UNLOADED : state.status();
        MutableComponent second = (machine.attached() ? tr("machines.attached") : tr("machines.via", routerName))
                .copy().withColor(GuiPaint.MUTED).append(SEPARATOR.copy().withColor(GuiPaint.MUTED));
        if (calm(status)) {
            BlockPos pos = BlockPos.of(machine.pos());
            second.append(Component.literal(pos.getX() + " " + pos.getY() + " " + pos.getZ()).withColor(GuiPaint.MUTED));
        } else {
            second.append(TreeTexts.stateText(status).copy().withColor(TreeTexts.stateColor(status)));
        }
        seconds[r] = second;
        if (state != null && status == MachineStatus.MOVING) {
            rates[r] = machine.attached() ? nodeRate(state) : TreeTexts.rateText(state);
        }
    }

    /** O nome do bloco da linha. */
    String blockName(int r) {
        return r >= 0 && r < blocks.length ? blocks[r] : "";
    }

    /** O estado não pede atenção: a segunda linha fica com as coordenadas. */
    static boolean calm(MachineStatus status) {
        return status == MachineStatus.MOVING || status == MachineStatus.SLEEPING;
    }

    /**
     * O nome do roteador: o dado pelo jogador; sem nome, o padrão da Lista (o bloco da presa), se a presa dele está na
     * página; senão, "roteador".
     */
    Component routerName(RouterRef router, @Nullable ResourceLocation presaBlock) {
        if (!router.name().isEmpty()) {
            return Component.literal(router.name());
        }
        return presaBlock != null ? Component.literal(texts.machineName(presaBlock)) : tr("machines.router");
    }

    /** O bloco da presa de cada roteador da paleta que tem a presa na página, ou {@code null}. */
    static ResourceLocation[] presaBlocks(TabletMachineRows page) {
        ResourceLocation[] blocks = new ResourceLocation[page.routers().size()];
        ResourceLocation block = null;
        for (TabletMachineRows.Row row : page.rows()) {
            if (row instanceof TabletMachineRows.Block header) {
                block = header.block();
            } else if (row instanceof TabletMachineRows.Machine machine && machine.attached()) {
                blocks[machine.router()] = block;
            }
        }
        return blocks;
    }

    private static MachineLines lines(TabletMachineRows page) {
        List<TabletMachineRows.Row> rows = page.rows();
        List<MachineLines.Kind> kinds = new ArrayList<>(rows.size());
        boolean[] collapsed = new boolean[rows.size()];
        int[] routers = new int[rows.size()];
        boolean[] attached = new boolean[rows.size()];
        for (int r = 0; r < rows.size(); r++) {
            switch (rows.get(r)) {
                case TabletMachineRows.Block block -> {
                    kinds.add(MachineLines.Kind.BLOCK);
                    collapsed[r] = block.collapsed();
                }
                case TabletMachineRows.Machine machine -> {
                    kinds.add(MachineLines.Kind.MACHINE);
                    routers[r] = machine.router();
                    attached[r] = machine.attached();
                }
                case TabletMachineRows.More more -> kinds.add(MachineLines.Kind.MORE);
            }
        }
        return new MachineLines(kinds, collapsed, routers, attached);
    }

    /**
     * A vazão da presa: a do nó, que o servidor manda na metade do que ela deu, sem a seta (o nó move nos dois
     * sentidos), na cor de cada tipo.
     */
    private static Component nodeRate(MachineLive live) {
        MutableComponent text = Component.empty();
        for (ResourceType type : LoadedTypes.LIST) {
            long value = live.given(type);
            if (value == 0) {
                continue;
            }
            if (!text.getSiblings().isEmpty()) {
                text.append(" ");
            }
            Component amount = type == ResourceType.ITEM ? Component.literal(RateFormat.abbreviate(value) + "/s")
                    : ResourceStyle.rate(type, value);
            text.append(amount.copy().withColor(ResourceStyle.color(type)));
        }
        return text;
    }

    /** A vazão por extenso da presa, para a dica: uma linha por tipo. */
    static Component nodeRateTooltip(MachineLive live) {
        MutableComponent text = Component.empty();
        for (ResourceType type : LoadedTypes.LIST) {
            if (live.given(type) != 0) {
                text.append("\n").append(tr("machines.node_rate", ResourceStyle.rate(type, live.given(type))).copy()
                        .withColor(ResourceStyle.color(type)));
            }
        }
        return text;
    }
}
