package io.github.matheusanbs.wirelessautomate.range;

import io.github.matheusanbs.wirelessautomate.network.ResourceType;
import java.util.UUID;
import net.minecraft.core.Direction;

/**
 * Uma face de um grupo ou de uma exceção que estava com modo Nenhum e numa rede, com a rede que ela tinha:
 * o que o "Tirar a rede das faces sem modo" tirou e o Desfazer devolve. O lado vem no quadro canônico da
 * tela ({@link RemoteSide#fromCanonical}).
 */
public record IdleNetworkFace(ResourceType type, Direction side, UUID network) {
}
