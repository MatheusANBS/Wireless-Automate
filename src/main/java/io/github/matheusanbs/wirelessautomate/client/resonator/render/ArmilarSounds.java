package io.github.matheusanbs.wirelessautomate.client.resonator.render;

import io.github.matheusanbs.wirelessautomate.WirelessAutomate;
import io.github.matheusanbs.wirelessautomate.resonator.CoreClientHooks;
import io.github.matheusanbs.wirelessautomate.resonator.ResonanceCoreBlockEntity;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;

/**
 * Os sons da Armilar no cliente. Cada Núcleo que recebe uma câmara ganha uma {@link ArmilarSoundBox}, tocada uma vez
 * por tick do cliente, com ou sem a Armilar na tela. A caixa some com o block entity, na troca de dimensão e ao sair do
 * mundo, e leva os loops junto; o {@code formedAt} da última montagem que tocou fica aqui, por posição, para uma cena
 * recriada pelo chunk não tocar a montagem de novo.
 */
@EventBusSubscriber(modid = WirelessAutomate.MODID, value = Dist.CLIENT)
public final class ArmilarSounds {
    private static final List<ArmilarSoundBox> BOXES = new ArrayList<>();
    private static final Map<Key, Long> ASSEMBLED = new HashMap<>();
    /** Quantas vezes cada evento pontual tocou nesta sessão (só o e2e lê). */
    private static final int[] PLAYED = new int[ArmilarSoundTriggers.Event.values().length];

    private record Key(ResourceKey<Level> dimension, BlockPos pos) {
    }

    private ArmilarSounds() {
    }

    /** Liga o gancho do código comum; chamado na preparação do cliente. */
    public static void install() {
        CoreClientHooks.changed = ArmilarSounds::track;
    }

    /** Um Núcleo recebeu uma câmara: ganha a caixa de sons se ainda não tem. */
    private static void track(ResonanceCoreBlockEntity core) {
        for (ArmilarSoundBox box : BOXES) {
            if (box.core() == core) {
                return;
            }
        }
        long assembled = core.getLevel() == null ? ArmilarSoundTriggers.NEVER
                : ASSEMBLED.getOrDefault(new Key(core.getLevel().dimension(), core.getBlockPos()),
                        ArmilarSoundTriggers.NEVER);
        BOXES.add(new ArmilarSoundBox(core, assembled));
    }

    @SubscribeEvent
    static void onTick(ClientTickEvent.Post event) {
        Minecraft minecraft = Minecraft.getInstance();
        for (int i = BOXES.size() - 1; i >= 0; i--) {
            ArmilarSoundBox box = BOXES.get(i);
            if (box.tick(minecraft)) {
                if (box.assembledChanged()) {
                    remember(box);
                }
            } else {
                box.dispose();
                BOXES.remove(i);
            }
        }
    }

    private static void remember(ArmilarSoundBox box) {
        long assembled = box.assembledFor();
        if (assembled != ArmilarSoundTriggers.NEVER && box.core().getLevel() != null) {
            ASSEMBLED.put(new Key(box.core().getLevel().dimension(), box.core().getBlockPos()), assembled);
        }
    }

    /** Ao sair do mundo nenhum loop pode ficar tocando, e a memória das montagens não vale mais. */
    @SubscribeEvent
    static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        for (ArmilarSoundBox box : BOXES) {
            box.dispose();
        }
        BOXES.clear();
        ASSEMBLED.clear();
    }

    static void played(ArmilarSoundTriggers.Event event) {
        PLAYED[event.ordinal()]++;
    }

    /** Só do e2e: quantas vezes o evento pontual {@code ASSEMBLE}, {@code CRACK}... tocou nesta sessão. */
    public static int playedForTests(String event) {
        return PLAYED[ArmilarSoundTriggers.Event.valueOf(event).ordinal()];
    }

    /** Só do e2e: se o loop {@code SUN}, {@code FLOW} ou {@code FULL} da Armilar do Núcleo em {@code core} está tocando no {@code SoundManager}. */
    public static boolean loopActiveForTests(BlockPos core, String loop) {
        for (ArmilarSoundBox box : BOXES) {
            if (box.core().getBlockPos().equals(core)) {
                return box.loopActive(ArmilarSoundBox.Loop.valueOf(loop));
            }
        }
        return false;
    }
}
