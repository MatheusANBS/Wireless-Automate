package io.github.matheusanbs.wirelessautomate.client.dev;

import static io.github.matheusanbs.wirelessautomate.client.dev.DevEndToEnd.*;
import static io.github.matheusanbs.wirelessautomate.client.dev.E2eSteps.*;
import static io.github.matheusanbs.wirelessautomate.client.dev.E2eUi.*;
import static io.github.matheusanbs.wirelessautomate.client.dev.E2eWorld.*;

import io.github.matheusanbs.wirelessautomate.client.ResourceStyle;
import io.github.matheusanbs.wirelessautomate.client.tv.TvScreen;
import io.github.matheusanbs.wirelessautomate.network.LoadedTypes;
import io.github.matheusanbs.wirelessautomate.network.ResourceType;
import io.github.matheusanbs.wirelessautomate.tv.TvArea;
import io.github.matheusanbs.wirelessautomate.tv.TvBackground;
import io.github.matheusanbs.wirelessautomate.tv.TvBlockEntity;
import io.github.matheusanbs.wirelessautomate.tv.TvLayout;
import io.github.matheusanbs.wirelessautomate.tv.TvLayouts;
import io.github.matheusanbs.wirelessautomate.tv.TvPiece;
import io.github.matheusanbs.wirelessautomate.tv.TvTheme;
import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * Os layouts prontos da Tela inteira no e2e, montados pelo {@link TvLayouts} com dado de verdade (a rede da energia e a
 * dos itens dos Fluxos, o Baú e a Bateria ligados) em Telas de 1 × 1 a 4 × 3, nos temas Grafite e Porcelana, e o
 * seletor de layouts da base do editor, com o Aplicar clicado de verdade.
 */
final class E2eTvLayoutSteps {
    private static final double FRONT = 13.0 / 16.0;

    private E2eTvLayoutSteps() {
    }

    private record Case(String name, TvLayouts.Kind kind, int width, int height, TvTheme theme, int store) {
    }

    static void layoutSteps(List<Step> list, UUID playerId, Supplier<BlockPos> tv, Supplier<BlockPos> chest,
            Supplier<BlockPos> battery) {
        List<Case> cases = List.of(
                new Case("energia", TvLayouts.Kind.ENERGY, 3, 2, TvTheme.GRAPHITE, 0),
                new Case("energia-porcelana", TvLayouts.Kind.ENERGY, 3, 2, TvTheme.PORCELAIN, 0),
                new Case("energia-2x2", TvLayouts.Kind.ENERGY, 2, 2, TvTheme.GRAPHITE, 0),
                new Case("energia-1x1", TvLayouts.Kind.ENERGY, 1, 1, TvTheme.GRAPHITE, 0),
                new Case("energia-4x3", TvLayouts.Kind.ENERGY, 4, 3, TvTheme.GRAPHITE, 0),
                new Case("armazenamento", TvLayouts.Kind.STORAGE, 4, 3, TvTheme.GRAPHITE, 1),
                new Case("armazenamento-porcelana", TvLayouts.Kind.STORAGE, 4, 3, TvTheme.PORCELAIN, 1),
                new Case("armazenamento-bateria", TvLayouts.Kind.STORAGE, 3, 2, TvTheme.GRAPHITE, 2),
                new Case("armazenamento-2x2", TvLayouts.Kind.STORAGE, 2, 2, TvTheme.GRAPHITE, 1),
                new Case("rede", TvLayouts.Kind.NETWORK, 4, 3, TvTheme.GRAPHITE, 0),
                new Case("rede-porcelana", TvLayouts.Kind.NETWORK, 4, 3, TvTheme.PORCELAIN, 0),
                new Case("rede-3x2", TvLayouts.Kind.NETWORK, 3, 2, TvTheme.GRAPHITE, 0),
                new Case("relogio", TvLayouts.Kind.CLOCK, 3, 2, TvTheme.GRAPHITE, 0),
                new Case("relogio-porcelana", TvLayouts.Kind.CLOCK, 3, 2, TvTheme.PORCELAIN, 0));
        list.add(E2eTvSteps.hideGui(playerId, true));
        for (Case c : cases) {
            TvArea area = TvArea.grown(c.width, c.height, TvArea.Grow.UP_RIGHT);
            TvLayout[] applied = new TvLayout[1];
            list.add(new Step("Layout " + c.name + ": " + c.width + " × " + c.height, STEP_TIMEOUT_MS,
                    () -> onServer(server -> {
                        TvBlockEntity entity = (TvBlockEntity) server.overworld().getBlockEntity(tv.get());
                        entity.setArea(area);
                        BlockPos at = c.store == 2 ? battery.get() : chest.get();
                        ResourceType type = c.store == 2 ? ResourceType.ENERGY : ResourceType.ITEM;
                        List<TvPiece> pieces = TvLayouts.build(c.kind, area, E2eTvLayoutSteps::text,
                                c.kind == TvLayouts.Kind.ENERGY ? energyNetwork : E2eFlowsSteps.flowsNetworkId(),
                                c.store == 0 ? null : new TvLayouts.Storage(GlobalPos.of(Level.OVERWORLD, at), type),
                                LoadedTypes.LIST);
                        applied[0] = TvLayout.of(pieces, c.theme, TvBackground.DEFAULT, false);
                        entity.setLayout(applied[0]);
                        return null;
                    }), () -> Minecraft.getInstance().level.getBlockEntity(tv.get()) instanceof TvBlockEntity entity
                            && entity.area().equals(area) && entity.layout().pieces().size() == applied[0].pieces().size(),
                    () -> "layout no cliente"));
            double depth = 0.45 * Math.max(c.width, c.height * 1.8) + 0.2;
            list.add(new Step("Layout " + c.name + ": os números chegam", STEP_TIMEOUT_MS, () -> {
            }, () -> stepTicks >= 70, () -> "esperando"));
            list.add(E2eTvSteps.view("tela-layout-" + c.name, playerId, () -> target(tv.get(), area).add(0, 0, -depth),
                    () -> target(tv.get(), area)));
        }
        list.add(E2eTvSteps.hideGui(playerId, false));
        editorSteps(list, tv);
    }

    /** O centro da frente da área: as colunas vão do bloco colocado para o menor x, as linhas para cima. */
    private static Vec3 target(BlockPos tv, TvArea area) {
        return new Vec3(tv.getX() + 0.5 - (area.width() - 1) / 2.0, tv.getY() + area.height() / 2.0,
                tv.getZ() + FRONT);
    }

    private static String text(String key) {
        for (ResourceType type : LoadedTypes.LIST) {
            if (key.equals("type." + type.key())) {
                return ResourceStyle.name(type).getString();
            }
        }
        return Component.translatable("gui.wirelessautomate.tv.editor.base.layout.text." + key).getString();
    }

    /** A base do editor com o seletor de layouts e o Aplicar clicado: as peças do layout entram, desfazíveis. */
    private static void editorSteps(List<Step> list, Supplier<BlockPos> tv) {
        int[] undo = new int[1];
        list.add(new Step("Layout: a Tela volta a 4 × 2 e abre", STEP_TIMEOUT_MS, () -> {
            onServer(server -> {
                TvBlockEntity entity = (TvBlockEntity) server.overworld().getBlockEntity(tv.get());
                entity.setArea(TvArea.grown(4, 2, TvArea.Grow.UP_RIGHT));
                entity.setLayout(TvLayout.EMPTY);
                return null;
            });
            useOn(tv.get());
        }, () -> Minecraft.getInstance().screen instanceof TvScreen,
                () -> "tela " + describe(Minecraft.getInstance().screen)));
        list.add(E2eTvSteps.tab("Layout: aba Editar", TvScreen.Tab.EDIT));
        list.add(new Step("Layout: o seletor da base aparece", STEP_TIMEOUT_MS, () -> {
        }, () -> E2eTvSteps.screen().editor().probe().propCenter("base.layout.apply") != null && stepTicks >= 10,
                () -> "sem o botão Aplicar layout"));
        E2eTvSteps.shot(list, "tela-editor-layouts");
        list.add(new Step("Layout: Aplicar layout", STEP_TIMEOUT_MS, () -> {
            undo[0] = E2eTvSteps.screen().editor().probe().undoSize();
            int[] at = E2eTvSteps.screen().editor().probe().propCenter("base.layout.apply");
            click(E2eTvSteps.screen(), at[0], at[1]);
        }, () -> E2eTvSteps.screen().editor().probe().undoSize() > undo[0]
                && E2eTvSteps.screen().editor().draft().pieces().size() > 3,
                () -> "peças " + E2eTvSteps.screen().editor().draft().pieces().size()));
        E2eTvSteps.shot(list, "tela-editor-layout-aplicado");
        list.add(new Step("Layout: salvar para fechar sem confirmar", STEP_TIMEOUT_MS,
                () -> click(E2eTvSteps.screen(), E2eTvSteps.screen().editor().probe().barCenter("SAVE")[0],
                        E2eTvSteps.screen().editor().probe().barCenter("SAVE")[1]),
                () -> !E2eTvSteps.screen().editor().dirty(), () -> "não salvo"));
        list.add(close("Layout: fechar o editor"));
        list.add(new Step("Layout: Tela de volta ao vazio", STEP_TIMEOUT_MS, () -> onServer(server -> {
            ((TvBlockEntity) server.overworld().getBlockEntity(tv.get())).setLayout(TvLayout.EMPTY);
            return null;
        }), () -> Minecraft.getInstance().level.getBlockEntity(tv.get()) instanceof TvBlockEntity entity
                && entity.layout().pieces().isEmpty(), () -> "layout no cliente"));
    }
}
