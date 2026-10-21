package io.github.matheusanbs.wirelessautomate.client.dev;

import static io.github.matheusanbs.wirelessautomate.client.dev.DevEndToEnd.*;
import static io.github.matheusanbs.wirelessautomate.client.dev.E2eResonatorSteps.core;
import static io.github.matheusanbs.wirelessautomate.client.dev.E2eSteps.*;
import static io.github.matheusanbs.wirelessautomate.client.dev.E2eUi.*;
import static io.github.matheusanbs.wirelessautomate.client.dev.E2eWorld.*;

import io.github.matheusanbs.wirelessautomate.GuiTheme;
import io.github.matheusanbs.wirelessautomate.block.RouterTier;
import io.github.matheusanbs.wirelessautomate.client.resonator.screen.ResonatorScreen;
import io.github.matheusanbs.wirelessautomate.packet.ResonatorStatePayload;
import io.github.matheusanbs.wirelessautomate.registry.ModItems;
import io.github.matheusanbs.wirelessautomate.resonator.ChamberView;
import io.github.matheusanbs.wirelessautomate.resonator.ResonanceCoreBlockEntity;
import io.github.matheusanbs.wirelessautomate.resonator.ResonatorPartBlock;
import io.github.matheusanbs.wirelessautomate.resonator.SignalCrystalItem;
import java.util.List;
import java.util.UUID;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;

/**
 * As capturas da tela do Núcleo com o que a etapa 2 mostra: a interferência (um segundo Ressonador, Ultimate, a 24
 * blocos do Elite), a integridade com um cristal gasto e um rachado (a redstone do Elite já é "antiga") e a caixa do
 * Eco no Ultimate, nos dois temas e em pt_br; e a câmara com dois rachados, que não recarregam com catalisador. O
 * Elite volta como estava antes das capturas da câmara, e o Ultimate sai.
 */
final class E2eResonatorScreenSteps {
    /** O segundo Ressonador fica a oeste do primeiro: os dois Núcleos a 24 blocos (Chebyshev). */
    private static final int APART = 24;
    private static final int ECO = 10;
    /** A câmara gasta do Elite e a integridade dela, e a rachada. */
    private static final int WORN = 0;
    private static final int WORN_INTEGRITY = 5;
    private static final int CRACKED = 2;

    private E2eResonatorScreenSteps() {
    }

    static void screenSteps(List<Step> list, UUID playerId, BlockPos[] origin) {
        BlockPos[] second = new BlockPos[1];
        list.add(new Step("Ressonador: segundo Núcleo, Ultimate, a 24 blocos", 3 * STEP_TIMEOUT_MS, () -> onServer(server -> {
            ServerLevel level = server.overworld();
            second[0] = origin[0].offset(-APART, 0, 0);
            E2eResonatorSteps.clear(level, second[0]);
            E2eResonatorSteps.placeVitrine(level, second[0], RouterTier.ULTIMATE);
            return null;
        }), () -> formed(core(second[0])) && onServer(server -> elite(server.overworld(), origin)
                .interference().lossPermille() > 0), () -> "interferência no Elite"));
        list.add(new Step("Ressonador: cristal gasto e rachado no Elite", STEP_TIMEOUT_MS, () -> onServer(server -> {
            ResonanceCoreBlockEntity elite = elite(server.overworld(), origin);
            ItemStack worn = new ItemStack(ModItems.SIGNAL_CRYSTAL.get());
            SignalCrystalItem.setIntegrity(worn, WORN_INTEGRITY);
            elite.setCrystalAt(WORN, worn);
            elite.setCrystalAt(CRACKED, cracked(new ItemStack(ModItems.SIGNAL_CRYSTAL.get())));
            return null;
        }), () -> view(origin[0]).chambers()[CRACKED] == 5, () -> "rachado no cliente"));
        list.add(new Step("Ressonador: Eco e cristais no Ultimate", STEP_TIMEOUT_MS, () -> onServer(server -> {
            ResonanceCoreBlockEntity ultimate = (ResonanceCoreBlockEntity) server.overworld()
                    .getBlockEntity(core(second[0]));
            ultimate.setStationForTest(98.0);
            ultimate.setDial(98.0);
            ultimate.eco().burn(cracked(new ItemStack(ModItems.TUNED_CRYSTALS.get(RouterTier.ULTIMATE).get(), ECO)),
                    false);
            for (int i = 0; i < 3; i++) {
                ultimate.insertCrystal(new ItemStack(ModItems.TUNED_CRYSTALS.get(RouterTier.ULTIMATE).get()));
            }
            ultimate.setCatalyst(ModItems.CHARGED_DUSTS.get(RouterTier.ULTIMATE).get(), 20);
            return null;
        }), () -> onServer(server -> ((ResonanceCoreBlockEntity) server.overworld().getBlockEntity(core(second[0])))
                .eco().value() == ECO), () -> "Eco no Ultimate"));
        shots(list, playerId, origin, second, "");
        list.add(language("pt_br"));
        shots(list, playerId, origin, second, "-pt");
        list.add(language("en_us"));
        list.add(new Step("Ressonador: o Elite como antes, sem o Ultimate", STEP_TIMEOUT_MS, () -> onServer(server -> {
            ServerLevel level = server.overworld();
            E2eResonatorSteps.clear(level, second[0]);
            ResonanceCoreBlockEntity elite = elite(level, origin);
            elite.setCrystalAt(WORN, new ItemStack(ModItems.SIGNAL_CRYSTAL.get()));
            ItemStack dull = new ItemStack(ModItems.SIGNAL_CRYSTAL.get());
            SignalCrystalItem.setCharge(dull, 0);
            elite.setCrystalAt(CRACKED, dull);
            return null;
        }), () -> view(origin[0]).chambers()[CRACKED] == 3 && onServer(server -> elite(server.overworld(), origin)
                .interference().lossPermille() == 0), () -> "Elite sem interferência"));
        list.add(E2eResonatorSteps.camera("Ressonador: câmera de volta", playerId, () -> origin[0],
                E2eResonatorSteps.SIDE_VIEW));
    }

    /**
     * As fotos de um idioma: o Elite com a dica da interferência e com a do rachado, e o Ultimate com a do Eco; em
     * inglês também no tema escuro (o {@code suffix} vazio).
     */
    private static void shots(List<Step> list, UUID playerId, BlockPos[] origin, BlockPos[] second, String suffix) {
        boolean withDark = suffix.isEmpty();
        list.add(E2eResonatorSteps.camera("Ressonador: câmera no Elite" + suffix, playerId, () -> origin[0],
                E2eResonatorSteps.SIDE_VIEW));
        list.add(open("Ressonador: abrir o Elite" + suffix, () -> core(origin[0]),
                state -> state.interferencePermille() > 0));
        hovered(list, "ressonador-tela-interferencia" + suffix, ResonatorScreen::interferenceCenter);
        hovered(list, "ressonador-tela-rachado" + suffix, screen -> screen.coreSlotCenter(CRACKED));
        if (withDark) {
            list.add(E2eDarkSteps.themeClick("Ressonador: tema escuro no Elite", GuiTheme.DARK));
            hovered(list, "ressonador-tela-interferencia-escuro", ResonatorScreen::interferenceCenter);
            hovered(list, "ressonador-tela-rachado-escuro", screen -> screen.coreSlotCenter(CRACKED));
            list.add(E2eDarkSteps.themeClick("Ressonador: tema claro no Elite", GuiTheme.LIGHT));
        }
        list.add(close("Ressonador: fechar o Elite" + suffix));
        list.add(E2eResonatorSteps.camera("Ressonador: câmera no Ultimate" + suffix, playerId, () -> second[0],
                E2eResonatorSteps.SIDE_VIEW));
        list.add(open("Ressonador: abrir o Ultimate" + suffix, () -> core(second[0]), state -> state.eco() == ECO));
        // sem dica: ela cobriria a caixa do Eco; a dica vai na foto do escuro
        hovered(list, "ressonador-tela-eco" + suffix, screen -> new int[] {0, 0});
        if (withDark) {
            list.add(E2eDarkSteps.themeClick("Ressonador: tema escuro no Ultimate", GuiTheme.DARK));
            hovered(list, "ressonador-tela-eco-escuro", ResonatorScreen::ecoSlotCenter);
            list.add(E2eDarkSteps.themeClick("Ressonador: tema claro no Ultimate", GuiTheme.LIGHT));
        }
        list.add(close("Ressonador: fechar o Ultimate" + suffix));
    }

    /**
     * A câmara do Elite com dois rachados e catalisador: nenhum recarrega ({@code rechargeStart} fica -1) e os dois
     * ficam escuros e caídos. A câmera já está dentro da câmara, de perto.
     */
    static void crackedChamberSteps(List<Step> list, BlockPos[] origin) {
        list.add(new Step("Ressonador: rachados na câmara, com catalisador", STEP_TIMEOUT_MS, () -> onServer(server -> {
            ResonanceCoreBlockEntity elite = elite(server.overworld(), origin);
            elite.setCrystalAt(0, cracked(new ItemStack(ModItems.SIGNAL_CRYSTAL.get())));
            elite.setCrystalAt(elite.chambers() - 1, cracked(new ItemStack(ModItems.CUT_SIGNAL_CRYSTAL.get())));
            elite.setRedstone(14);
            return null;
        }), () -> {
            ChamberView view = view(origin[0]);
            byte[] chambers = view.chambers();
            return chambers[0] == 5 && chambers[chambers.length - 1] == 6 && view.rechargeStart() < 0
                    && stepTicks >= 40;
        }, () -> "rachados no cliente, recarga " + view(origin[0]).rechargeStart()));
        list.add(capture("ressonador-camara-rachado"));
    }

    /** Abre a tela do Núcleo e espera o estado que a foto precisa. */
    private static Step open(String name, Supplier<BlockPos> pos,
            Predicate<ResonatorStatePayload> ready) {
        return new Step(name, STEP_TIMEOUT_MS, () -> useOn(pos.get()),
                () -> Minecraft.getInstance().screen instanceof ResonatorScreen screen && screen.getMenu().state() != null
                        && screen.getMenu().pos().equals(pos.get()) && ready.test(screen.getMenu().state()),
                () -> "tela " + describe(Minecraft.getInstance().screen));
    }

    /** A foto com o mouse em {@code at} (a dica aberta) e a conferência dos textos cortados. */
    private static void hovered(List<Step> list, String name, Function<ResonatorScreen, int[]> at) {
        list.add(new Step("captura " + name, STEP_TIMEOUT_MS, () -> {
            if (!(Minecraft.getInstance().screen instanceof ResonatorScreen screen)) {
                throw new StepFailure("sem a tela do Núcleo: " + describe(Minecraft.getInstance().screen));
            }
            int[] point = at.apply(screen);
            moveMouse(point[0], point[1]);
        }, () -> {
            if (stepTicks < 8) {
                return false;
            }
            capture(Minecraft.getInstance(), name);
            return true;
        }, () -> name + ".png"));
        list.add(clipCheck(name));
    }

    private static ItemStack cracked(ItemStack stack) {
        SignalCrystalItem.setCharge(stack, 0);
        SignalCrystalItem.setIntegrity(stack, 0);
        return stack;
    }

    private static boolean formed(BlockPos pos) {
        BlockState state = Minecraft.getInstance().level.getBlockState(pos);
        return state.hasProperty(ResonatorPartBlock.FORMED) && state.getValue(ResonatorPartBlock.FORMED);
    }

    private static ResonanceCoreBlockEntity elite(ServerLevel level, BlockPos[] origin) {
        return (ResonanceCoreBlockEntity) level.getBlockEntity(core(origin[0]));
    }

    private static ChamberView view(BlockPos origin) {
        return ((ResonanceCoreBlockEntity) Minecraft.getInstance().level.getBlockEntity(core(origin))).clientView();
    }
}
