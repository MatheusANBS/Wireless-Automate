package io.github.matheusanbs.wirelessautomate.client.tablet.networks;

import java.util.Collection;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

/** O nome de uma rede ou grupo criado pelo "+ Rede" ou "+ Grupo": o primeiro livre de "Rede", "Rede 2", ... Pura. */
final class DefaultNames {
    private DefaultNames() {
    }

    static String next(String base, Collection<String> taken) {
        Set<String> lower = new HashSet<>();
        for (String name : taken) {
            lower.add(name.toLowerCase(Locale.ROOT));
        }
        if (!lower.contains(base.toLowerCase(Locale.ROOT))) {
            return base;
        }
        for (int n = 2; ; n++) {
            String name = base + " " + n;
            if (!lower.contains(name.toLowerCase(Locale.ROOT))) {
                return name;
            }
        }
    }
}
