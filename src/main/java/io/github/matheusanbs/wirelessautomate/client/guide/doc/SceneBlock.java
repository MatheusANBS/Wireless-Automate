package io.github.matheusanbs.wirelessautomate.client.guide.doc;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/** Um bloco de uma cena, com a posição e as propriedades (os atributos {@code p:}, sem o prefixo). */
public record SceneBlock(String id, int x, int y, int z, Map<String, String> props) {
    public SceneBlock {
        props = Collections.unmodifiableMap(new LinkedHashMap<>(props));
    }
}
