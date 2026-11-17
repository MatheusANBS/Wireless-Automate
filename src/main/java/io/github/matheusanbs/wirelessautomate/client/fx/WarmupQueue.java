package io.github.matheusanbs.wirelessautomate.client.fx;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.resources.ResourceLocation;

/** A fila da pré-carga: entrega as texturas aos poucos, {@code n} por vez, na ordem em que foram dadas. */
final class WarmupQueue {
    private final List<ResourceLocation> pending;
    private int next;

    WarmupQueue(List<ResourceLocation> all) {
        this.pending = new ArrayList<>(all);
    }

    /** Até {@code n} texturas que ainda não saíram; vazio quando acabou. */
    List<ResourceLocation> next(int n) {
        List<ResourceLocation> out = new ArrayList<>(n);
        while (out.size() < n && next < pending.size()) {
            out.add(pending.get(next++));
        }
        return out;
    }

    boolean done() {
        return next >= pending.size();
    }
}
