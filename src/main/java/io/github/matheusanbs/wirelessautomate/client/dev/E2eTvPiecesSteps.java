package io.github.matheusanbs.wirelessautomate.client.dev;

import static io.github.matheusanbs.wirelessautomate.client.dev.DevEndToEnd.*;
import static io.github.matheusanbs.wirelessautomate.client.dev.E2eSteps.*;
import static io.github.matheusanbs.wirelessautomate.client.dev.E2eUi.*;
import static io.github.matheusanbs.wirelessautomate.client.dev.E2eWorld.*;

import io.github.matheusanbs.wirelessautomate.block.RouterBlock;
import io.github.matheusanbs.wirelessautomate.client.tv.TvClientValues;
import io.github.matheusanbs.wirelessautomate.client.tv.TvScreen;
import io.github.matheusanbs.wirelessautomate.client.tv.render.TvCanvases;
import io.github.matheusanbs.wirelessautomate.item.LinkerItem;
import io.github.matheusanbs.wirelessautomate.network.ResourceType;
import io.github.matheusanbs.wirelessautomate.registry.ModBlocks;
import io.github.matheusanbs.wirelessautomate.registry.ModItems;
import io.github.matheusanbs.wirelessautomate.storage.StorageBatteryBlockEntity;
import io.github.matheusanbs.wirelessautomate.storage.StorageChestBlockEntity;
import io.github.matheusanbs.wirelessautomate.storage.StorageKind;
import io.github.matheusanbs.wirelessautomate.tv.TvAlign;
import io.github.matheusanbs.wirelessautomate.tv.TvArea;
import io.github.matheusanbs.wirelessautomate.tv.TvBinding;
import io.github.matheusanbs.wirelessautomate.tv.TvBlockEntity;
import io.github.matheusanbs.wirelessautomate.tv.TvClockMetric;
import io.github.matheusanbs.wirelessautomate.tv.TvColor;
import io.github.matheusanbs.wirelessautomate.tv.TvFormat;
import io.github.matheusanbs.wirelessautomate.tv.TvLayout;
import io.github.matheusanbs.wirelessautomate.tv.TvPartStyle;
import io.github.matheusanbs.wirelessautomate.tv.TvPiece;
import io.github.matheusanbs.wirelessautomate.tv.TvPieceType;
import io.github.matheusanbs.wirelessautomate.tv.TvStorageMetric;
import io.github.matheusanbs.wirelessautomate.tv.TvStyle;
import io.github.matheusanbs.wirelessautomate.tv.TvUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;

/**
 * As peças da etapa 3 no e2e: um Baú e uma Bateria do mod ligados à Tela pelo Vinculador de verdade (o clique na Tela
 * a escolhe, o clique em cada armazenamento o liga), o painel do mockup montado com Ícone, Barra, Medidor, Gráfico,
 * Seta e Valores sobre eles (entra e sai exatos: o e2e insere e extrai de verdade a cada meio segundo), o relógio, e a
 * aba Dado do editor com a lista dos armazenamentos ligados e a grade de itens do Baú.
 */
final class E2eTvPiecesSteps {
    private static final ResourceLocation DIAMOND = ResourceLocation.withDefaultNamespace("diamond");
    private static final long DRIVE_TICKS = 700;
    private static BlockPos chest = BlockPos.ZERO;
    private static BlockPos battery = BlockPos.ZERO;
    private static int stockPiece;
    private static long paintsAtClock;

    private E2eTvPiecesSteps() {
    }

    static void piecesSteps(List<Step> list, UUID playerId, Supplier<BlockPos> tv, Supplier<BlockPos> origin) {
        list.add(new Step("Peças: Baú e Bateria do mod, com o Vinculador na mão", STEP_TIMEOUT_MS, () -> onServer(server -> {
            ServerLevel level = server.overworld();
            chest = origin.get().offset(5, 0, 4);
            battery = origin.get().offset(7, 0, 4);
            level.setBlockAndUpdate(chest, ModBlocks.STORAGE.get(StorageKind.CHEST).get().defaultBlockState()
                    .setValue(RouterBlock.TIER, io.github.matheusanbs.wirelessautomate.block.RouterTier.ADVANCED));
            level.setBlockAndUpdate(battery, ModBlocks.STORAGE.get(StorageKind.BATTERY).get().defaultBlockState()
                    .setValue(RouterBlock.TIER, io.github.matheusanbs.wirelessautomate.block.RouterTier.ADVANCED));
            StorageChestBlockEntity box = (StorageChestBlockEntity) level.getBlockEntity(chest);
            box.storage().insert(new ItemStack(Items.DIAMOND), 1280, false);
            box.storage().insert(new ItemStack(Items.IRON_INGOT), 3000, false);
            box.storage().insert(new ItemStack(Items.REDSTONE), 500, false);
            StorageBatteryBlockEntity cell = (StorageBatteryBlockEntity) level.getBlockEntity(battery);
            cell.store().insert(cell.store().capacity() * 3 / 5, false);
            ServerPlayer player = server.getPlayerList().getPlayer(playerId);
            player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ModItems.LINKER.get()));
            return null;
        }), () -> Minecraft.getInstance().level.getBlockEntity(battery) != null
                && Minecraft.getInstance().player.getMainHandItem().is(ModItems.LINKER.get()), () -> "blocos no cliente"));
        list.add(new Step("Peças: o clique na Tela a escolhe", STEP_TIMEOUT_MS, () -> useOn(tv.get()),
                () -> E2eLinkerSteps.serverLinker(stack -> LinkerItem.tv(stack) != null
                        && LinkerItem.tv(stack).pos().equals(tv.get())),
                () -> "Tela no Vinculador " + E2eLinkerSteps.serverLinker(stack -> String.valueOf(LinkerItem.tv(stack)))));
        list.add(new Step("Peças: o clique no Baú o liga", STEP_TIMEOUT_MS, () -> useOn(chest),
                () -> linked(tv.get(), chest), () -> "ligações " + links(tv.get())));
        list.add(new Step("Peças: o clique na Bateria a liga", STEP_TIMEOUT_MS, () -> useOn(battery),
                () -> linked(tv.get(), battery), () -> "ligações " + links(tv.get())));
        list.add(new Step("Peças: o painel do mockup", STEP_TIMEOUT_MS, () -> onServer(server -> {
            TvBlockEntity entity = (TvBlockEntity) server.overworld().getBlockEntity(tv.get());
            entity.setLayout(panel());
            ServerPlayer player = server.getPlayerList().getPlayer(playerId);
            player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
            return null;
        }), () -> Minecraft.getInstance().level.getBlockEntity(tv.get()) instanceof TvBlockEntity entity
                && entity.layout().pieces().size() == panel().pieces().size()
                && TvClientValues.maxForTests(tv.get().asLong()) >= 0, () -> "layout no cliente"));
        long[] started = new long[1];
        list.add(new Step("Peças: entra e sai de verdade por " + DRIVE_TICKS + " ticks, para o Gráfico encher", 120_000,
                () -> started[0] = 0, () -> {
                    started[0]++;
                    if (started[0] % 10 == 0) {
                        drive();
                    }
                    return started[0] >= DRIVE_TICKS;
                }, () -> "ticks " + started[0]));
        list.add(E2eTvSteps.hideGui(playerId, true));
        list.add(E2eTvSteps.view("tela-pecas", playerId, E2eTvSteps::worldEye, E2eTvSteps::worldTarget));
        list.add(E2eTvSteps.view("tela-grafico", playerId, () -> E2eTvSteps.worldTarget().add(1.0, 0.0, -1.6),
                () -> E2eTvSteps.worldTarget().add(1.0, -0.2, 0)));
        list.add(new Step("Peças: o relógio como Valor", STEP_TIMEOUT_MS, () -> onServer(server -> {
            ((TvBlockEntity) server.overworld().getBlockEntity(tv.get())).setLayout(clock());
            return null;
        }), () -> Minecraft.getInstance().level.getBlockEntity(tv.get()) instanceof TvBlockEntity entity
                && entity.layout().pieces().size() == 3, () -> "layout no cliente"));
        list.add(E2eTvSteps.view("tela-relogio", playerId, E2eTvSteps::worldEye, E2eTvSteps::worldTarget));
        list.add(E2eTvSteps.hideGui(playerId, false));
        list.add(new Step("Peças: o minuto do jogo muda e só a Tela do relógio redesenha", 30_000, () -> {
            paintsAtClock = TvCanvases.paintsForTests;
            onServer(server -> {
                server.overworld().setDayTime(server.overworld().getDayTime() + 1000);
                return null;
            });
        }, () -> TvCanvases.paintsForTests > paintsAtClock && TvCanvases.paintsForTests - paintsAtClock <= 4,
                () -> "redesenhos " + (TvCanvases.paintsForTests - paintsAtClock)));
        list.add(new Step("Peças: de volta ao painel e abrir o editor", STEP_TIMEOUT_MS, () -> {
            onServer(server -> {
                ((TvBlockEntity) server.overworld().getBlockEntity(tv.get())).setLayout(panel());
                return null;
            });
            useOn(tv.get());
        }, () -> Minecraft.getInstance().screen instanceof TvScreen, () -> "tela " + describe(Minecraft.getInstance().screen)));
        list.add(E2eTvSteps.tab("Peças: aba Editar", TvScreen.Tab.EDIT));
        list.add(new Step("Peças: escolher o estoque do Baú e a aba Dado", STEP_TIMEOUT_MS, () -> {
            var probe = E2eTvSteps.screen().editor().probe();
            click(E2eTvSteps.screen(), probe.layerCenter(stockPiece)[0], probe.layerCenter(stockPiece)[1]);
            click(E2eTvSteps.screen(), probe.propsTabCenter("DATA")[0], probe.propsTabCenter("DATA")[1]);
        }, () -> E2eTvSteps.screen().editor().selection().size() == 1
                && "DATA".equals(E2eTvSteps.screen().editor().probe().propsTab())
                && E2eTvSteps.screen().editor().probe().propCenter("data.storage.items") != null
                && stepTicks >= 20, () -> "escolha " + E2eTvSteps.screen().editor().selection()));
        E2eTvSteps.shot(list, "tela-editor-armazenamento-itens");
        list.add(new Step("Peças: digitar na busca de itens e o servidor filtra", STEP_TIMEOUT_MS, () -> {
            int[] at = E2eTvSteps.screen().editor().probe().itemSearchCenter();
            click(E2eTvSteps.screen(), at[0], at[1]);
            type("dia");
        }, () -> stepTicks >= 30, () -> "esperando a resposta"));
        E2eTvSteps.shot(list, "tela-editor-armazenamento");
        list.add(new Step("Peças: Esc solta o foco da busca", STEP_TIMEOUT_MS, () -> E2eTvSteps.screen().editor().escape(),
                () -> true, () -> ""));
        ghostSteps(list);
        list.add(close("Peças: fechar o editor"));
        E2eTvLayoutSteps.layoutSteps(list, playerId, tv, () -> chest, () -> battery);
        list.add(new Step("Peças: de volta ao painel", STEP_TIMEOUT_MS, () -> onServer(server -> {
            ((TvBlockEntity) server.overworld().getBlockEntity(tv.get())).setLayout(panel());
            return null;
        }), () -> Minecraft.getInstance().level.getBlockEntity(tv.get()) instanceof TvBlockEntity entity
                && entity.layout().pieces().size() == panel().pieces().size(), () -> "layout no cliente"));
        list.add(new Step("Peças: soltar o Baú e a Bateria", STEP_TIMEOUT_MS, () -> onServer(server -> {
            ServerLevel level = server.overworld();
            level.setBlockAndUpdate(chest, Blocks.AIR.defaultBlockState());
            level.setBlockAndUpdate(battery, Blocks.AIR.defaultBlockState());
            return null;
        }), () -> Minecraft.getInstance().level.getBlockState(chest).isAir(), () -> "blocos no cliente"));
    }

    /**
     * O arrastar do JEI, sem o JEI: o retângulo que o {@code TvGhostHandler} oferece existe com a aba Editar e a grade
     * de itens, e o item largado vira o filtro do Baú no Valor e o item do Ícone.
     */
    private static void ghostSteps(List<Step> list) {
        ResourceLocation redstone = ResourceLocation.withDefaultNamespace("redstone");
        list.add(new Step("Peças: escolher o Valor do Baú de novo", STEP_TIMEOUT_MS, () -> {
            var probe = E2eTvSteps.screen().editor().probe();
            click(E2eTvSteps.screen(), probe.layerCenter(stockPiece)[0], probe.layerCenter(stockPiece)[1]);
        }, () -> E2eTvSteps.screen().editor().selection().size() == 1
                && E2eTvSteps.screen().editor().probe().propCenter("data.storage.items") != null && stepTicks >= 10,
                () -> "escolha " + E2eTvSteps.screen().editor().selection()));
        list.add(new Step("Peças: soltar um item do JEI no filtro do Baú", STEP_TIMEOUT_MS, () -> {
            if (E2eTvSteps.screen().ghostBounds() == null) {
                throw new StepFailure("sem alvo do JEI na grade do Baú");
            }
            E2eTvSteps.screen().editor().acceptGhost(redstone);
        }, () -> E2eTvSteps.screen().editor().draft().pieces().get(stockPiece).binding().map(
                b -> b.storage() != null && b.storage().item().equals(Optional.of(redstone))).orElse(false),
                () -> "ligação " + E2eTvSteps.screen().editor().draft().pieces().get(stockPiece).binding()));
        list.add(new Step("Peças: escolher o Ícone", STEP_TIMEOUT_MS, () -> {
            var probe = E2eTvSteps.screen().editor().probe();
            click(E2eTvSteps.screen(), probe.layerCenter(stockPiece - 1)[0], probe.layerCenter(stockPiece - 1)[1]);
        }, () -> E2eTvSteps.screen().editor().selection().size() == 1
                && E2eTvSteps.screen().editor().probe().propCenter("data.icon.id") != null && stepTicks >= 10,
                () -> "escolha " + E2eTvSteps.screen().editor().selection()));
        list.add(new Step("Peças: soltar um item do JEI no Ícone", STEP_TIMEOUT_MS, () -> {
            if (E2eTvSteps.screen().ghostBounds() == null) {
                throw new StepFailure("sem alvo do JEI no Ícone");
            }
            E2eTvSteps.screen().editor().acceptGhost(redstone);
        }, () -> E2eTvSteps.screen().editor().draft().pieces().get(stockPiece - 1).style().part().item()
                .equals(Optional.of(redstone)), () -> "Ícone " + E2eTvSteps.screen().editor().draft().pieces()
                .get(stockPiece - 1).style().part().item()));
        list.add(new Step("Peças: salvar para poder fechar sem confirmar", STEP_TIMEOUT_MS,
                () -> click(E2eTvSteps.screen(), E2eTvSteps.screen().editor().probe().barCenter("SAVE")[0],
                        E2eTvSteps.screen().editor().probe().barCenter("SAVE")[1]),
                () -> !E2eTvSteps.screen().editor().dirty(), () -> "não salvo"));
        list.add(new Step("Peças: Esc solta a escolha do Ícone", STEP_TIMEOUT_MS,
                () -> E2eTvSteps.screen().editor().escape(), () -> E2eTvSteps.screen().editor().selection().isEmpty(),
                () -> "escolha " + E2eTvSteps.screen().editor().selection()));
    }

    private static boolean linked(BlockPos tv, BlockPos storage) throws Exception {
        return onServer(server -> ((TvBlockEntity) server.overworld().getBlockEntity(tv))
                .hasLink(GlobalPos.of(server.overworld().dimension(), storage)));
    }

    private static String links(BlockPos tv) throws Exception {
        return onServer(server -> String.valueOf(((TvBlockEntity) server.overworld().getBlockEntity(tv)).links()));
    }

    /** Insere e extrai de verdade no Baú e na Bateria, para as taxas e o Gráfico terem o que mostrar. */
    private static void drive() throws Exception {
        onServer(server -> {
            ServerLevel level = server.overworld();
            StorageChestBlockEntity box = (StorageChestBlockEntity) level.getBlockEntity(chest);
            StorageBatteryBlockEntity cell = (StorageBatteryBlockEntity) level.getBlockEntity(battery);
            long wave = Math.round(60 + 40 * Math.sin(server.getTickCount() / 60.0));
            box.storage().insert(new ItemStack(Items.DIAMOND), wave, false);
            box.storage().extract(new ItemStack(Items.DIAMOND), 25, false);
            cell.store().insert(wave * 80, false);
            cell.store().extract(2200, false);
            return null;
        });
    }

    private static GlobalPos gpos(BlockPos pos) {
        return GlobalPos.of(Level.OVERWORLD, pos);
    }

    private static TvStyle style(int color, int size, TvAlign align) {
        return new TvStyle(color, 0, 0, 0, size, align, true, "");
    }

    private static TvPiece piece(TvPieceType type, int x, int y, int w, int h, Optional<TvBinding> bind, TvFormat format,
            TvStyle style) {
        return new TvPiece(type, x, y, w, h, 0, "", bind, format, style);
    }

    private static Optional<TvBinding> battery(TvStorageMetric metric) {
        return Optional.of(TvBinding.storage(ResourceType.ENERGY, gpos(battery), metric, Optional.empty()));
    }

    private static Optional<TvBinding> diamonds(TvStorageMetric metric) {
        return Optional.of(TvBinding.storage(ResourceType.ITEM, gpos(chest), metric, Optional.of(DIAMOND)));
    }

    /** O painel do mockup em 128 × 64: o cartão de energia (Medidor, setas, Gráfico) e o do item (Ícone, Barra). */
    private static TvLayout panel() {
        int muted = TvColor.theme(TvColor.MUTED);
        int high = TvColor.theme(TvColor.HIGH);
        int low = TvColor.theme(TvColor.LOW);
        int text = TvColor.theme(TvColor.TEXT);
        TvStyle card = new TvStyle(text, TvColor.theme(TvColor.CARD), 0, 0, 8, TvAlign.LEFT, true, "", 2, 100,
                TvStyle.DEFAULT.font());
        List<TvPiece> pieces = new ArrayList<>();
        pieces.add(piece(TvPieceType.SHAPE, 0, 0, 76, 64, Optional.empty(), TvFormat.DEFAULT, card));
        pieces.add(piece(TvPieceType.SHAPE, 80, 0, 48, 64, Optional.empty(), TvFormat.DEFAULT, card));
        pieces.add(piece(TvPieceType.TEXT, 4, 3, 50, 7, Optional.empty(), TvFormat.DEFAULT,
                style(muted, 6, TvAlign.LEFT).withText("ENERGIA")));
        pieces.add(piece(TvPieceType.GAUGE, 4, 12, 34, 34, battery(TvStorageMetric.FILL), TvFormat.DEFAULT,
                style(high, 8, TvAlign.LEFT).withPart(TvPartStyle.DEFAULT.withThickness(5))));
        pieces.add(piece(TvPieceType.VALUE, 9, 23, 24, 12, battery(TvStorageMetric.FILL),
                new TvFormat(0, false, false, "", "", TvUnit.NONE), style(text, 9, TvAlign.CENTER)));
        pieces.add(piece(TvPieceType.ARROW, 42, 13, 7, 9, battery(TvStorageMetric.IN), TvFormat.DEFAULT,
                style(high, 8, TvAlign.LEFT)));
        pieces.add(piece(TvPieceType.VALUE, 51, 13, 23, 9, battery(TvStorageMetric.IN), TvFormat.DEFAULT,
                style(high, 7, TvAlign.LEFT)));
        pieces.add(piece(TvPieceType.ARROW, 42, 25, 7, 9, battery(TvStorageMetric.OUT), TvFormat.DEFAULT,
                style(low, 8, TvAlign.LEFT).withPart(TvPartStyle.DEFAULT.withReverse(true))));
        pieces.add(piece(TvPieceType.VALUE, 51, 25, 23, 9, battery(TvStorageMetric.OUT), TvFormat.DEFAULT,
                style(low, 7, TvAlign.LEFT)));
        pieces.add(piece(TvPieceType.GRAPH, 42, 37, 32, 17, battery(TvStorageMetric.IN), TvFormat.DEFAULT,
                style(high, 8, TvAlign.LEFT)));
        pieces.add(piece(TvPieceType.VALUE, 4, 52, 36, 8, battery(TvStorageMetric.STORED), TvFormat.DEFAULT,
                style(muted, 6, TvAlign.LEFT)));
        pieces.add(piece(TvPieceType.ICON, 84, 4, 18, 18, Optional.empty(), TvFormat.DEFAULT,
                style(text, 8, TvAlign.LEFT).withPart(TvPartStyle.DEFAULT.withItem(Optional.of(DIAMOND)))));
        stockPiece = pieces.size();
        pieces.add(piece(TvPieceType.VALUE, 104, 7, 22, 12, diamonds(TvStorageMetric.STORED),
                new TvFormat(1, true, false, "", "", TvUnit.NONE), style(text, 9, TvAlign.LEFT)));
        pieces.add(piece(TvPieceType.VALUE, 84, 27, 40, 7, diamonds(TvStorageMetric.IN),
                new TvFormat(0, false, false, "", "/s", TvUnit.NONE), style(high, 6, TvAlign.LEFT)));
        pieces.add(piece(TvPieceType.VALUE, 84, 36, 40, 7, diamonds(TvStorageMetric.OUT),
                new TvFormat(0, false, false, "−", "/s", TvUnit.NONE), style(low, 6, TvAlign.LEFT)));
        pieces.add(piece(TvPieceType.BAR, 84, 48, 40, 5, Optional.of(TvBinding.storage(ResourceType.ITEM, gpos(chest),
                TvStorageMetric.FILL, Optional.empty())), TvFormat.DEFAULT, style(high, 8, TvAlign.LEFT)));
        pieces.add(piece(TvPieceType.VALUE, 84, 55, 40, 7, Optional.of(TvBinding.clock(TvClockMetric.TIME)),
                TvFormat.DEFAULT, style(muted, 6, TvAlign.LEFT)));
        return TvLayout.of(pieces);
    }

    /** A hora e o dia do mundo em dois Valores grandes. */
    private static TvLayout clock() {
        int text = TvColor.theme(TvColor.TEXT);
        return TvLayout.of(List.of(
                piece(TvPieceType.VALUE, 8, 6, 112, 32, Optional.of(TvBinding.clock(TvClockMetric.TIME)),
                        TvFormat.DEFAULT, style(text, 32, TvAlign.CENTER)),
                piece(TvPieceType.VALUE, 8, 42, 112, 14, Optional.of(TvBinding.clock(TvClockMetric.DAY)),
                        new TvFormat(0, false, false, "Dia ", "", TvUnit.NONE),
                        style(TvColor.theme(TvColor.MUTED), 12, TvAlign.CENTER)),
                piece(TvPieceType.SHAPE, 8, 38, 112, 1, Optional.empty(), TvFormat.DEFAULT,
                        new TvStyle(text, TvColor.theme(TvColor.MUTED), 0, 0, 8, TvAlign.LEFT, true, ""))));
    }
}
