package io.github.matheusanbs.wirelessautomate.menu;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import org.jetbrains.annotations.Nullable;

/**
 * As partes do aviso do colar e do mover com máquinas, numa linha só: "3 grupos e 1 máquina", "2 roteadores, 1 grupo
 * e 4 máquinas" (só o que não é zero), e o aviso inteiro do colar.
 */
final class MarkedNotice {
    private static final String BASE = "gui.wirelessautomate.panel.notice.";

    private MarkedNotice() {
    }

    /** "2 roteadores, 1 grupo e 4 máquinas", sem as partes zeradas; {@code null} se todas são zero. */
    static @Nullable MutableComponent parts(int routers, int groups, int machines) {
        List<Component> parts = new ArrayList<>(3);
        add(parts, "routers", routers);
        add(parts, "groups", groups);
        add(parts, "machines", machines);
        if (parts.isEmpty()) {
            return null;
        }
        MutableComponent head = parts.getFirst().copy();
        for (int i = 1; i < parts.size() - 1; i++) {
            head.append(", ").append(parts.get(i));
        }
        return parts.size() == 1 ? head : Component.translatable(BASE + "and", head, parts.getLast());
    }

    private static void add(List<Component> parts, String key, int count) {
        if (count > 0) {
            parts.add(Component.translatable(BASE + "part." + key + (count == 1 ? ".one" : ""), count));
        }
    }

    /**
     * O aviso do colar nos marcados: o que recebeu, as exceções mantidas, o que ficou de fora, as faces que ficaram
     * com a rede de antes e, com o perfil antigo, o pedido de copiar de novo.
     */
    static MutableComponent paste(MarkedPaste.Result result, int keptFaces) {
        MutableComponent parts = parts(result.routers(), result.groups(), result.machines());
        MutableComponent message;
        if (parts == null) {
            message = result.skipped() > 0 ? Component.translatable(BASE + "nothing_pasted_skipped", result.skipped())
                    : Component.translatable(BASE + "nothing_pasted");
        } else {
            message = Component.translatable(BASE + "pasted_on", parts);
            if (result.exceptionsKept() > 0) {
                message.append(" · ").append(Component.translatable(BASE + "exceptions_kept"
                        + (result.exceptionsKept() == 1 ? ".one" : ""), result.exceptionsKept()));
            }
            if (result.skipped() > 0) {
                message.append(" · ").append(Component.translatable(BASE + "skipped", result.skipped()));
            }
        }
        if (keptFaces > 0) {
            message.append(" · ").append(Component.translatable(BASE + "kept_networks", keptFaces));
        }
        if (result.refusedOld()) {
            message.append(" · ").append(Component.translatable(BASE + "copy_again"));
        }
        return message;
    }
}
