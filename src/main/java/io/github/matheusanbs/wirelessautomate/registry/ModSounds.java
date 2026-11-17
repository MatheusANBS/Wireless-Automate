package io.github.matheusanbs.wirelessautomate.registry;

import io.github.matheusanbs.wirelessautomate.WirelessAutomate;
import java.util.List;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/** Os 14 sons da Armilar (o design em {@code docs/superpowers/specs/2026-11-16-armilar-fluxo-continuo-design.md}, seção 3). */
public final class ModSounds {
    public static final DeferredRegister<SoundEvent> SOUND_EVENTS =
            DeferredRegister.create(Registries.SOUND_EVENT, WirelessAutomate.MODID);

    public static final DeferredHolder<SoundEvent, SoundEvent> ARMILAR_ASSEMBLE = armilar("assemble");
    public static final DeferredHolder<SoundEvent, SoundEvent> ARMILAR_SUN_LOOP = armilar("sun_loop");
    public static final DeferredHolder<SoundEvent, SoundEvent> ARMILAR_TUNE_LOCK = armilar("tune_lock");
    public static final DeferredHolder<SoundEvent, SoundEvent> ARMILAR_TUNE_LOST = armilar("tune_lost");
    public static final DeferredHolder<SoundEvent, SoundEvent> ARMILAR_CONJ_CHARGE = armilar("conj_charge");
    public static final DeferredHolder<SoundEvent, SoundEvent> ARMILAR_CONJ_BURST = armilar("conj_burst");
    public static final DeferredHolder<SoundEvent, SoundEvent> ARMILAR_CONJ_ARC = armilar("conj_arc");
    public static final DeferredHolder<SoundEvent, SoundEvent> ARMILAR_CONJ_IMPACT = armilar("conj_impact");
    public static final DeferredHolder<SoundEvent, SoundEvent> ARMILAR_FLOW_LOOP = armilar("flow_loop");
    public static final DeferredHolder<SoundEvent, SoundEvent> ARMILAR_FULL_LOOP = armilar("full_loop");
    public static final DeferredHolder<SoundEvent, SoundEvent> ARMILAR_RECHARGE_GRAIN = armilar("recharge_grain");
    public static final DeferredHolder<SoundEvent, SoundEvent> ARMILAR_RECHARGE_DONE = armilar("recharge_done");
    public static final DeferredHolder<SoundEvent, SoundEvent> ARMILAR_CRYSTAL_CRACK = armilar("crystal_crack");
    public static final DeferredHolder<SoundEvent, SoundEvent> ARMILAR_CRYSTAL_DULL = armilar("crystal_dull");

    /** Os ids dos 14 eventos, sem o namespace, na ordem do registro (o e2e confere que todos existem). */
    public static final List<String> ARMILAR_IDS = List.of("assemble", "sun_loop", "tune_lock", "tune_lost",
            "conj_charge", "conj_burst", "conj_arc", "conj_impact", "flow_loop", "full_loop", "recharge_grain",
            "recharge_done", "crystal_crack", "crystal_dull");

    private ModSounds() {
    }

    private static DeferredHolder<SoundEvent, SoundEvent> armilar(String name) {
        String id = "armilar." + name;
        return SOUND_EVENTS.register(id, () -> SoundEvent
                .createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(WirelessAutomate.MODID, id)));
    }
}
