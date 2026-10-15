package io.github.matheusanbs.wirelessautomate.client.filter;

import io.github.matheusanbs.wirelessautomate.client.RateFormat;
import io.github.matheusanbs.wirelessautomate.client.ResourceStyle;
import io.github.matheusanbs.wirelessautomate.filter.FilterEntry;
import io.github.matheusanbs.wirelessautomate.filter.FilterEntry.ChemicalEntry;
import io.github.matheusanbs.wirelessautomate.filter.FilterEntry.FluidEntry;
import io.github.matheusanbs.wirelessautomate.filter.FilterEntry.ItemEntry;
import io.github.matheusanbs.wirelessautomate.filter.FilterEntry.ModEntry;
import io.github.matheusanbs.wirelessautomate.filter.FilterEntry.RuleEntry;
import io.github.matheusanbs.wirelessautomate.filter.FilterEntry.TagEntry;
import io.github.matheusanbs.wirelessautomate.filter.ItemRule;
import io.github.matheusanbs.wirelessautomate.menu.FilterView;
import io.github.matheusanbs.wirelessautomate.network.Chemicals;
import io.github.matheusanbs.wirelessautomate.network.ResourceType;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.tags.TagKey;
import net.minecraft.util.FormattedCharSequence;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.common.Tags;

/**
 * Os textos da tela de filtro: as chaves de tradução, o contexto do cabeçalho, o nome, a segunda linha, o
 * id e a dica de cada entrada, o estoque abreviado e a linha acima do inventário.
 */
final class FilterText {
    /** JEI instalado: as dicas falam dele e o plugin em {@code compat/jei} oferece arrastar e clicar. */
    static final boolean JEI = ModList.get().isLoaded("jei");

    private final FilterScreen screen;
    private final FilterMembers members;

    FilterText(FilterScreen screen, FilterMembers members) {
        this.screen = screen;
        this.members = members;
    }

    static Component tr(String key, Object... args) {
        return Component.translatable("gui.wirelessautomate.filter." + key, args);
    }

    private static Component typeName(ResourceType type) {
        return ResourceStyle.name(type);
    }

    /** Nome do armazenamento do filtro de entrada, pelo bloco no mundo do cliente. */
    private static Component storageName(FilterView v) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level != null && v.router().isPresent()) {
            return minecraft.level.getBlockState(v.router().get()).getBlock().getName();
        }
        return Component.translatable("block.wirelessautomate.storage_chest");
    }

    /** "Baixo · Itens", "Tanque Wireless · Fluidos" ou "Cartão de Filtro · Itens". */
    Component context() {
        FilterView v = screen.view();
        Component where = v.face().isPresent()
                ? Component.translatable("gui.wirelessautomate.router.face." + v.face().get().getName())
                : v.isCard() ? Component.translatable("item.wirelessautomate.filter_card")
                : storageName(v);
        return where.copy().append(" · ").append(typeName(v.type()));
    }

    static Component ruleText(ItemRule rule) {
        return rule.describe();
    }

    Component entryName(FilterEntry entry) {
        return switch (entry) {
            case ItemEntry e -> e.stack().getHoverName();
            case FluidEntry e -> e.stack().getHoverName();
            case TagEntry e -> Component.literal("#" + e.tag());
            case ModEntry e -> Component.literal(ModList.get().getModContainerById(e.modId())
                    .map(c -> c.getModInfo().getDisplayName()).orElse("@" + e.modId()));
            case ChemicalEntry e -> Chemicals.name(e.chemical());
            case RuleEntry e -> ruleText(e.rule());
        };
    }

    /** Segunda linha da entrada na lista: o tipo e quanto ela pega. */
    Component entryKind(FilterEntry entry) {
        return switch (entry) {
            case ItemEntry e -> screen.filter().matchComponents() ? tr("kind.item.components") : tr("kind.item");
            case FluidEntry e -> tr("kind.fluid");
            case ChemicalEntry e -> tr("kind.chemical");
            case TagEntry e -> screen.isChemical() ? tr("kind.tag.plain") : tr("kind.tag", members.memberCount(e));
            case ModEntry e -> screen.isChemical() ? tr("kind.mod.plain")
                    : tr("kind.mod", members.modCounts().getOrDefault(e.modId(), 0));
            case RuleEntry e -> tr("kind.rule");
        };
    }

    static String entryId(FilterEntry entry) {
        return switch (entry) {
            case ItemEntry e -> BuiltInRegistries.ITEM.getKey(e.stack().getItem()).toString();
            case FluidEntry e -> BuiltInRegistries.FLUID.getKey(e.stack().getFluid()).toString();
            case TagEntry e -> "#" + e.tag();
            case ModEntry e -> "@" + e.modId();
            case ChemicalEntry e -> e.chemical().toString();
            case RuleEntry e -> e.rule().scope();
        };
    }

    List<FormattedCharSequence> entryTooltip(FilterEntry entry) {
        List<FormattedCharSequence> lines = new ArrayList<>(Minecraft.getInstance().font.split(entryName(entry), 220));
        if (!entryId(entry).isEmpty()) {
            lines.add(Component.literal(entryId(entry)).withStyle(ChatFormatting.DARK_GRAY).getVisualOrderText());
        }
        if (entry instanceof TagEntry e) {
            Component name = Component.translatableWithFallback(Tags.getTagTranslationKey(screen.isFluid()
                    ? TagKey.create(Registries.FLUID, e.tag()) : TagKey.create(Registries.ITEM, e.tag())), "");
            if (!name.getString().isEmpty()) {
                lines.add(name.copy().withStyle(ChatFormatting.GRAY).getVisualOrderText());
            }
            lines.add(tr("entry.tag").copy().withStyle(ChatFormatting.GRAY).getVisualOrderText());
        } else if (entry instanceof ModEntry) {
            lines.add(tr("entry.mod").copy().withStyle(ChatFormatting.GRAY).getVisualOrderText());
        } else if (entry instanceof RuleEntry) {
            lines.add(tr("entry.rule").copy().withStyle(ChatFormatting.GRAY).getVisualOrderText());
        }
        if (entry.stock() > 0) {
            Component amount = screen.inMb() ? Component.literal(entry.stock() + " ").append(tr("unit.mb"))
                    : Component.literal(Long.toString(entry.stock()));
            lines.add(tr("stock.line", amount).copy().withStyle(ChatFormatting.GRAY).getVisualOrderText());
        }
        return lines;
    }

    /** Estoque abreviado: itens como estão; fluidos e químicos em milhares de mB a partir de 1.000. */
    String stockShort(long stock) {
        if (!screen.inMb()) {
            return RateFormat.abbreviate(stock);
        }
        return stock < 1000 ? stock + " mB" : RateFormat.abbreviate(stock / 1000) + " B";
    }

    /** A linha acima do inventário: como adicionar; com o JEI, cita ele também. */
    Component inventoryHint() {
        String type = screen.isChemical() ? "chemical" : screen.isFluid() ? "fluid" : "item";
        return tr("hint." + type + (JEI ? ".jei" : ""));
    }
}
