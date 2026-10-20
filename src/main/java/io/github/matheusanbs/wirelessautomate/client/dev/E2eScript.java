package io.github.matheusanbs.wirelessautomate.client.dev;

import static io.github.matheusanbs.wirelessautomate.client.dev.DevEndToEnd.*;
import static io.github.matheusanbs.wirelessautomate.client.dev.E2eConfiguratorSteps.*;
import static io.github.matheusanbs.wirelessautomate.client.dev.E2eDarkSteps.*;
import static io.github.matheusanbs.wirelessautomate.client.dev.E2eFilterSteps.*;
import static io.github.matheusanbs.wirelessautomate.client.dev.E2eGuideSteps.*;
import static io.github.matheusanbs.wirelessautomate.client.dev.E2eLinkerSteps.*;
import static io.github.matheusanbs.wirelessautomate.client.dev.E2ePanelSteps.*;
import static io.github.matheusanbs.wirelessautomate.client.dev.E2ePortugueseSteps.*;
import static io.github.matheusanbs.wirelessautomate.client.dev.E2eRangeSteps.*;
import static io.github.matheusanbs.wirelessautomate.client.dev.E2eRecipeSteps.*;
import static io.github.matheusanbs.wirelessautomate.client.dev.E2eResonatorSteps.*;
import static io.github.matheusanbs.wirelessautomate.client.dev.E2eJeiResonatorSteps.*;
import static io.github.matheusanbs.wirelessautomate.client.dev.E2eRouterSteps.*;
import static io.github.matheusanbs.wirelessautomate.client.dev.E2eSourceTankSteps.*;
import static io.github.matheusanbs.wirelessautomate.client.dev.E2eSteps.*;
import static io.github.matheusanbs.wirelessautomate.client.dev.E2eStorageSteps.*;
import static io.github.matheusanbs.wirelessautomate.client.dev.E2eTabletSteps.*;

import java.util.ArrayList;
import java.util.List;
import net.neoforged.fml.ModList;
import io.github.matheusanbs.wirelessautomate.storage.StorageKind;

/**
 * O roteiro do e2e ({@code WA_E2E}): a ordem das áreas de passos, cada uma na sua classe. A ordem e os nomes dos
 * passos e das capturas são o contrato do {@code result.txt}.
 */
final class E2eScript {
    /** Opcional ({@code WA_E2E_ONLY=tablet}): só a abertura, o nome e as redes de B, a transferência entre redes e os passos do Tablet. */
    private static final String ONLY = System.getenv("WA_E2E_ONLY");

    private E2eScript() {
    }

    static List<Step> script() {
        List<Step> list = new ArrayList<>();
        openingSteps(list);
        if ("tablet".equals(ONLY)) {
            networkSteps(list);
            list.add(close("fechar B"));
            typeNetworkTransferSteps(list);
            tabletSteps(list);
            return list;
        }
        filterSteps(list);
        networkSteps(list);
        layoutSteps(list);
        panelSteps(list);
        attachedFrameSteps(list);
        cardSteps(list);
        chunkUpgradeSteps(list);
        list.add(close("fechar B"));
        rangeSteps(list);
        typeNetworkTransferSteps(list);
        tabletSteps(list);
        configuratorSteps(list);
        linkerAreaSteps(list);
        portugueseSteps(list);
        storageChestSteps(list);
        if (StorageKind.SOURCE_TANK.loaded()) {
            sourceTankSteps(list);
        }
        recipeEditorSteps(list);
        darkSteps(list);
        resonatorSteps(list);
        jeiResonatorSteps(list);
        if (ModList.get().isLoaded("guideme")) {
            guideSteps(list);
        }
        return list;
    }
}
