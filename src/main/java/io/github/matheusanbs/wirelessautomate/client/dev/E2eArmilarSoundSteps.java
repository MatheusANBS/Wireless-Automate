package io.github.matheusanbs.wirelessautomate.client.dev;

import static io.github.matheusanbs.wirelessautomate.client.dev.DevEndToEnd.*;
import static io.github.matheusanbs.wirelessautomate.client.dev.E2eResonatorSteps.core;
import static io.github.matheusanbs.wirelessautomate.client.dev.E2eWorld.*;

import io.github.matheusanbs.wirelessautomate.WirelessAutomate;
import io.github.matheusanbs.wirelessautomate.client.resonator.render.ArmilarSounds;
import io.github.matheusanbs.wirelessautomate.registry.ModSounds;
import io.github.matheusanbs.wirelessautomate.resonator.ResonanceCoreBlock;
import io.github.matheusanbs.wirelessautomate.resonator.ResonatorPartBlock;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;

/**
 * Os passos dos sons da Armilar dentro da suíte {@code ressonador}: os 14 eventos registrados, o loop do sol ativo no
 * {@code SoundManager} com a Armilar em sintonia e parado depois de ela desmontar (o Núcleo fica, só falta uma peça),
 * o loop do feixe ativo com a extração real, o da pressão com o buffer cheio e a montagem que toca ao formar. Os
 * avisos de som ausente no log são conferidos com {@code grep} no {@code latest.log} depois da rodada.
 */
final class E2eArmilarSoundSteps {
    private E2eArmilarSoundSteps() {
    }

    /** Os 14 eventos estão no registro de sons, com o id {@code wirelessautomate:armilar.<nome>}. */
    static Step registered() {
        return new Step("Armilar: os 14 sons registrados", STEP_TIMEOUT_MS, () -> {
        }, () -> {
            List<String> missing = new ArrayList<>();
            for (String id : ModSounds.ARMILAR_IDS) {
                if (!BuiltInRegistries.SOUND_EVENT.containsKey(
                        ResourceLocation.fromNamespaceAndPath(WirelessAutomate.MODID, "armilar." + id))) {
                    missing.add(id);
                }
            }
            if (!missing.isEmpty() || ModSounds.ARMILAR_IDS.size() != 14) {
                throw new StepFailure("sons sem registro: " + missing);
            }
            resolved();
            return true;
        }, () -> ModSounds.ARMILAR_IDS.size() + " eventos no registro e resolvidos pelo SoundManager");
    }

    /** As variações de cada evento no {@code sounds.json}: o que o sorteio tem de achar. */
    private static final java.util.Map<String, Integer> VARIANTS = java.util.Map.of("conj_burst", 2, "conj_impact", 3,
            "recharge_grain", 3, "crystal_crack", 2);

    /**
     * O que o log avisaria de som ausente, como asserção: o {@code SoundManager} resolve cada evento (não vazio), o
     * sorteio acha todas as variações e cada {@code Sound} tem o {@code .ogg} no {@code ResourceManager}.
     */
    private static void resolved() throws StepFailure {
        Minecraft minecraft = Minecraft.getInstance();
        var random = net.minecraft.util.RandomSource.create(7);
        for (String id : ModSounds.ARMILAR_IDS) {
            ResourceLocation event = ResourceLocation.fromNamespaceAndPath(WirelessAutomate.MODID, "armilar." + id);
            var weighed = minecraft.getSoundManager().getSoundEvent(event);
            if (weighed == null) {
                throw new StepFailure("o SoundManager não resolve " + event + " (sounds.json)");
            }
            java.util.Set<ResourceLocation> files = new java.util.HashSet<>();
            for (int i = 0; i < 300; i++) {
                var sound = weighed.getSound(random);
                if (sound == net.minecraft.client.sounds.SoundManager.EMPTY_SOUND || sound == null) {
                    throw new StepFailure(event + ": sorteio vazio");
                }
                files.add(sound.getPath());
            }
            int expected = VARIANTS.getOrDefault(id, 1);
            if (files.size() != expected) {
                throw new StepFailure(event + ": " + files.size() + " arquivos sorteados, esperava " + expected + " " + files);
            }
            for (ResourceLocation file : files) {
                if (minecraft.getResourceManager().getResource(file).isEmpty()) {
                    throw new StepFailure(event + ": falta o arquivo " + file);
                }
            }
        }
    }

    /** Com a Armilar formada e em sintonia, o loop do sol está tocando. */
    static Step sunLoopOn(BlockPos[] origin) {
        return new Step("Armilar: o loop do sol ativo no SoundManager", STEP_TIMEOUT_MS, () -> {
        }, () -> ArmilarSounds.loopActiveForTests(core(origin[0]), "SUN"),
                () -> "sun_loop ativo " + ArmilarSounds.loopActiveForTests(core(origin[0]), "SUN"));
    }

    /** Com a extração real a cada tick, o loop do feixe está tocando. */
    static Step flowLoopOn(BlockPos[] origin) {
        return new Step("Armilar: o loop do feixe ativo com a extração real", STEP_TIMEOUT_MS, () -> {
        }, () -> {
            E2eArmilarRenderSteps.drain(origin);
            return ArmilarSounds.loopActiveForTests(core(origin[0]), "FLOW");
        }, () -> "flow_loop ativo " + ArmilarSounds.loopActiveForTests(core(origin[0]), "FLOW"));
    }

    /** O buffer cheio, sem ninguém extrair: o loop da pressão está tocando. */
    static Step fullLoopOn(BlockPos[] origin) {
        return new Step("Armilar: o loop da pressão ativo com o buffer cheio", STEP_TIMEOUT_MS, () -> {
        }, () -> ArmilarSounds.loopActiveForTests(core(origin[0]), "FULL"),
                () -> "full_loop ativo " + ArmilarSounds.loopActiveForTests(core(origin[0]), "FULL"));
    }

    /**
     * Tira uma peça da estrutura (o Núcleo fica): a Armilar desmonta e os loops descem por rampa e param. Quem chama
     * monta de novo em seguida.
     */
    static void dismantle(List<Step> list, BlockPos[] origin) {
        list.add(new Step("Armilar: tirar uma peça da estrutura", STEP_TIMEOUT_MS, () -> onServer(server -> {
            ServerLevel level = server.overworld();
            for (BlockPos pos : BlockPos.betweenClosed(origin[0], origin[0].offset(4, 4, 4))) {
                var state = level.getBlockState(pos);
                if (state.getBlock() instanceof ResonatorPartBlock && !(state.getBlock() instanceof ResonanceCoreBlock)) {
                    level.setBlockAndUpdate(pos, Blocks.AIR.defaultBlockState());
                    return null;
                }
            }
            throw new IllegalStateException("nenhuma peça para tirar");
        }), () -> !formed(origin) && !ArmilarSounds.loopActiveForTests(core(origin[0]), "SUN")
                && !ArmilarSounds.loopActiveForTests(core(origin[0]), "FLOW")
                && !ArmilarSounds.loopActiveForTests(core(origin[0]), "FULL"),
                () -> "desmontada, sem loop ativo"));
    }

    /** A montagem de novo toca o som de montar e liga o loop do sol outra vez. */
    static void reassembled(List<Step> list, BlockPos[] origin, int[] assembledBefore) {
        list.add(new Step("Armilar: o som da montagem e o loop do sol de volta", STEP_TIMEOUT_MS, () -> {
        }, () -> {
            if (ArmilarSounds.playedForTests("ASSEMBLE") <= assembledBefore[0]) {
                return false;
            }
            return ArmilarSounds.loopActiveForTests(core(origin[0]), "SUN");
        }, () -> "montagens tocadas " + ArmilarSounds.playedForTests("ASSEMBLE") + " (antes " + assembledBefore[0]
                + "), sun_loop " + ArmilarSounds.loopActiveForTests(core(origin[0]), "SUN")));
    }

    private static boolean formed(BlockPos[] origin) {
        var state = Minecraft.getInstance().level.getBlockState(core(origin[0]));
        return state.getBlock() instanceof ResonanceCoreBlock && state.hasProperty(ResonatorPartBlock.FORMED)
                && state.getValue(ResonatorPartBlock.FORMED);
    }
}
