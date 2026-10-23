package io.github.matheusanbs.wirelessautomate.client.dev;

import static io.github.matheusanbs.wirelessautomate.client.dev.DevEndToEnd.*;
import static io.github.matheusanbs.wirelessautomate.client.dev.E2eResonatorSteps.core;
import static io.github.matheusanbs.wirelessautomate.client.dev.E2eUi.*;
import static io.github.matheusanbs.wirelessautomate.client.dev.E2eWorld.*;

import io.github.matheusanbs.wirelessautomate.Config;
import io.github.matheusanbs.wirelessautomate.block.RouterTier;
import io.github.matheusanbs.wirelessautomate.registry.ModItems;
import io.github.matheusanbs.wirelessautomate.resonator.ChamberView;
import io.github.matheusanbs.wirelessautomate.resonator.ResonanceCoreBlockEntity;
import io.github.matheusanbs.wirelessautomate.resonator.SignalCrystalItem;
import java.util.List;
import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.energy.IEnergyStorage;

/**
 * As capturas da câmara que dependem do que o Núcleo faz: a energia saindo pela Porta (o conduto), o buffer cheio (o
 * plasma sob pressão, sem conduto), a vazão alta (o conduto grosso) e a recarga (a espiral, de longe e de perto, o
 * anel do fim e o Opaco de novo). O Núcleo e a câmera já estão na Vitrine montada, em sintonia.
 */
final class E2eResonatorFlowSteps {
    /** A faixa de vazão (de 7) a partir da qual o conduto conta como grosso. */
    private static final int HIGH_BAND = 6;
    /** Os ticks da câmara depois do último pedido de extração para o conduto acender (ou apagar) por inteiro. */
    private static final int SETTLE_TICKS = 40;
    /** Os ticks entre o servidor avisar o fim da recarga e a captura do anel (ele dura 14). */
    private static final int RING_DELAY_TICKS = 4;

    private E2eResonatorFlowSteps() {
    }

    /**
     * A energia saindo pela Porta de Energia: o teste tira da Porta a cada tick (a capability de energia do
     * mundo, como um consumidor) até a faixa chegar ao cliente e o conduto acender, e fotografa.
     */
    static void flowSteps(List<Step> list, UUID playerId, BlockPos[] origin) {
        long[] seen = new long[1];
        list.add(new Step("Ressonador: energia saindo pela Porta", STEP_TIMEOUT_MS, () -> {
            Minecraft.getInstance().options.hideGui = true;
            seen[0] = 0;
        }, () -> {
            drain(origin);
            if (seen[0] == 0 && band(origin) > 0) {
                seen[0] = stepTicks;
            }
            return seen[0] != 0 && stepTicks >= seen[0] + 30;
        }, () -> "faixa de vazão no cliente"));
        list.add(new Step("captura ressonador-camara-fluxo", STEP_TIMEOUT_MS, () -> {
        }, () -> {
            capture(Minecraft.getInstance(), "ressonador-camara-fluxo");
            return true;
        }, () -> "ressonador-camara-fluxo.png, faixa " + band(origin)));
        fullSteps(list, origin);
        highFlowSteps(list, origin);
        rechargeSteps(list, playerId, origin);
        list.add(new Step("Ressonador: a mão de volta", STEP_TIMEOUT_MS,
                () -> Minecraft.getInstance().options.hideGui = false, () -> true, () -> "mão"));
    }

    /**
     * O buffer cheio: ninguém tira energia, o buffer enche, a faixa cai a zero e o plasma fica sob pressão (raio
     * menor, a borda do tier pulsando, nenhum conduto).
     */
    private static void fullSteps(List<Step> list, BlockPos[] origin) {
        // a redstone gasta e a estação deriva: sem repor, o buffer pode não encher dentro do prazo
        list.add(new Step("Ressonador: buffer cheio", 4 * STEP_TIMEOUT_MS, () -> onServer(server -> {
            ResonanceCoreBlockEntity core = (ResonanceCoreBlockEntity) server.overworld()
                    .getBlockEntity(core(origin[0]));
            core.setRedstone(ResonanceCoreBlockEntity.MAX_REDSTONE);
            core.setStationForTest(98.0);
            core.setDial(98.0);
            return null;
        }), () -> view(origin).full() && band(origin) == 0 && stepTicks >= 20,
                () -> "cheio " + view(origin).full() + ", faixa " + band(origin)));
        list.add(DevEndToEnd.wait("a pressão assentar", SETTLE_TICKS));
        list.add(capture("ressonador-camara-cheio"));
    }

    /** A vazão alta: tira da Porta a cada tick até a faixa chegar a 6 ou 7 e fotografa o conduto grosso. */
    private static void highFlowSteps(List<Step> list, BlockPos[] origin) {
        long[] seen = new long[1];
        list.add(new Step("Ressonador: vazão alta pela Porta", 3 * STEP_TIMEOUT_MS, () -> seen[0] = 0, () -> {
            drain(origin);
            if (seen[0] == 0 && band(origin) >= HIGH_BAND) {
                seen[0] = stepTicks;
            }
            return seen[0] != 0 && stepTicks >= seen[0] + 30;
        }, () -> "faixa " + band(origin)));
        list.add(new Step("captura ressonador-camara-fluxo-alto", STEP_TIMEOUT_MS, () -> {
        }, () -> {
            capture(Minecraft.getInstance(), "ressonador-camara-fluxo-alto");
            return true;
        }, () -> "ressonador-camara-fluxo-alto.png, faixa " + band(origin)));
        // a medida com o conduto aceso: a extração continua a cada tick de espera
        for (int i = 0; i < 2; i++) {
            list.add(E2eResonatorSteps.frameTime(true, " com conduto", () -> drain(origin)));
            list.add(E2eResonatorSteps.frameTime(false, " com conduto", () -> drain(origin)));
        }
    }

    /**
     * A recarga: o teste põe um Opaco na última câmara e redstone, espera a metade do tempo da config e fotografa a
     * espiral e o cristal enchendo, de longe e da câmera de perto; espera o fim e fotografa o anel; depois põe o
     * Opaco de novo, sem redstone, e fotografa de perto os cristais vivos e ele caído.
     */
    private static void rechargeSteps(List<Step> list, UUID playerId, BlockPos[] origin) {
        long[] seen = new long[1];
        long half = Config.rechargeTicks() / 2;
        long waitMs = Config.rechargeTicks() * 50 + 30_000;
        list.add(new Step("Ressonador: Opaco em recarga", waitMs, () -> {
            seen[0] = 0;
            onServer(server -> {
                ResonanceCoreBlockEntity core = (ResonanceCoreBlockEntity) server.overworld()
                        .getBlockEntity(core(origin[0]));
                core.setCrystalAt(core.chambers() - 1, dull());
                core.setRedstone(14);
                return null;
            });
        }, () -> {
            if (seen[0] == 0 && view(origin).rechargeStart() >= 0) {
                seen[0] = stepTicks;
            }
            return seen[0] != 0 && stepTicks >= seen[0] + half;
        }, () -> "recarga no cliente"));
        list.add(capture("ressonador-camara-recarga"));
        // a medida com a espiral e a aura em curso
        for (int i = 0; i < 2; i++) {
            list.add(E2eResonatorSteps.frameTime(true, " com espiral", () -> recharging(origin)));
            list.add(E2eResonatorSteps.frameTime(false, " com espiral", () -> recharging(origin)));
        }
        list.add(E2eResonatorSteps.camera("Ressonador: câmera perto da recarga", playerId, () -> origin[0],
                E2eResonatorSteps.CLOSE_VIEW));
        list.add(DevEndToEnd.wait("a espiral de perto", 10));
        list.add(capture("ressonador-camara-recarga-perto"));
        list.add(new Step("Ressonador: o fim da recarga", waitMs, () -> seen[0] = 0, () -> {
            if (seen[0] == 0 && view(origin).rechargeStart() < 0) {
                seen[0] = stepTicks;
            }
            return seen[0] != 0 && stepTicks >= seen[0] + RING_DELAY_TICKS;
        }, () -> "fim da recarga no cliente"));
        list.add(capture("ressonador-camara-recarga-anel"));
        list.add(new Step("Ressonador: Opaco de novo, sem redstone", STEP_TIMEOUT_MS, () -> onServer(server -> {
            ResonanceCoreBlockEntity core = (ResonanceCoreBlockEntity) server.overworld()
                    .getBlockEntity(core(origin[0]));
            core.setRedstone(0);
            core.setCrystalAt(core.chambers() - 1, dull());
            return null;
        }), () -> view(origin).rechargeStart() < 0, () -> "recarga parada no cliente"));
        list.add(DevEndToEnd.wait("o Opaco cair", 40));
        list.add(capture("ressonador-camara-cristais"));
        list.add(new Step("Ressonador: um rachado ao lado do Opaco", STEP_TIMEOUT_MS, () -> onServer(server -> {
            ResonanceCoreBlockEntity core = (ResonanceCoreBlockEntity) server.overworld()
                    .getBlockEntity(core(origin[0]));
            core.setCrystalAt(0, E2eResonatorScreenSteps.cracked(new ItemStack(ModItems.SIGNAL_CRYSTAL.get())));
            return null;
        }), () -> view(origin).chambers()[0] == 5 && view(origin).rechargeStart() < 0,
                () -> "rachado no cliente"));
        list.add(DevEndToEnd.wait("o rachado assentar", 20));
        list.add(capture("ressonador-cristais-estados"));
        list.add(new Step("Ressonador: Afinados de dois tiers", STEP_TIMEOUT_MS, () -> onServer(server -> {
            ResonanceCoreBlockEntity core = (ResonanceCoreBlockEntity) server.overworld()
                    .getBlockEntity(core(origin[0]));
            core.setCrystalAt(1, new ItemStack(ModItems.TUNED_CRYSTALS.get(RouterTier.ELITE).get()));
            core.setCrystalAt(2, new ItemStack(ModItems.TUNED_CRYSTALS.get(RouterTier.ULTIMATE).get()));
            return null;
        }), () -> view(origin).tierAt(1) > 0 && view(origin).tierAt(2) > 0, () -> "Afinados no cliente"));
        list.add(DevEndToEnd.wait("os Afinados assentarem", 20));
        list.add(capture("ressonador-cristais-afinados"));
        E2eResonatorScreenSteps.crackedChamberSteps(list, origin);
    }

    /**
     * Mantém a espiral durante a medida: com o cliente lento (quadros de ~50 ms) as quatro medidas passam da metade
     * da recarga que sobra, então a recarga que acaba recomeça na hora com outro Opaco na última câmara. Os poucos
     * quadros até o servidor avisar ficam na média.
     */
    private static void recharging(BlockPos[] origin) throws Exception {
        if (view(origin).rechargeStart() >= 0) {
            return;
        }
        onServer(server -> {
            ResonanceCoreBlockEntity core = (ResonanceCoreBlockEntity) server.overworld()
                    .getBlockEntity(core(origin[0]));
            if (!SignalCrystalItem.isRechargeable(core.crystals().get(core.chambers() - 1))) {
                core.setCrystalAt(core.chambers() - 1, dull());
            }
            return null;
        });
    }

    private static ItemStack dull() {
        ItemStack dull = new ItemStack(ModItems.SIGNAL_CRYSTAL.get());
        SignalCrystalItem.setCharge(dull, 0);
        return dull;
    }

    /** Tira da Porta de Energia tudo o que ela der, pela capability de energia do mundo, como um consumidor. */
    private static void drain(BlockPos[] origin) throws Exception {
        onServer(server -> {
            IEnergyStorage energy = server.overworld().getCapability(Capabilities.EnergyStorage.BLOCK,
                    E2eResonatorSteps.energyPort(origin[0]), null);
            if (energy != null) {
                energy.extractEnergy(Integer.MAX_VALUE, false);
            }
            return null;
        });
    }

    private static ChamberView view(BlockPos[] origin) {
        return ((ResonanceCoreBlockEntity) Minecraft.getInstance().level.getBlockEntity(core(origin[0]))).clientView();
    }

    /** A maior faixa de vazão entre as Portas, no cliente. */
    private static int band(BlockPos[] origin) {
        int best = 0;
        for (byte band : view(origin).flows()) {
            best = Math.max(best, band);
        }
        return best;
    }
}
