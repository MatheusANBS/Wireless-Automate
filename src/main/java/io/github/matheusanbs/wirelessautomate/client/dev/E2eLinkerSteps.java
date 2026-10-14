package io.github.matheusanbs.wirelessautomate.client.dev;

import static io.github.matheusanbs.wirelessautomate.client.dev.DevEndToEnd.*;
import static io.github.matheusanbs.wirelessautomate.client.dev.E2eRangeSteps.*;
import static io.github.matheusanbs.wirelessautomate.client.dev.E2eSteps.*;
import static io.github.matheusanbs.wirelessautomate.client.dev.E2eUi.*;
import static io.github.matheusanbs.wirelessautomate.client.dev.E2eWorld.*;

import io.github.matheusanbs.wirelessautomate.block.RouterBlockEntity;
import io.github.matheusanbs.wirelessautomate.client.linker.LinkerScreen;
import io.github.matheusanbs.wirelessautomate.item.LinkerItem;
import io.github.matheusanbs.wirelessautomate.linker.LinkerArea;
import io.github.matheusanbs.wirelessautomate.linker.LinkerFaces;
import io.github.matheusanbs.wirelessautomate.linker.LinkerMode;
import io.github.matheusanbs.wirelessautomate.linker.LinkerProblem;
import io.github.matheusanbs.wirelessautomate.range.RemoteSide;
import io.github.matheusanbs.wirelessautomate.network.NetworkSavedData;
import io.github.matheusanbs.wirelessautomate.network.LoadedTypes;
import io.github.matheusanbs.wirelessautomate.network.ResourceType;
import io.github.matheusanbs.wirelessautomate.registry.ModItems;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import org.lwjgl.glfw.GLFW;

/**
 * O Vinculador por área no e2e: cantos marcados com Shift + clique, a tela com a rede e as abas, Vincular, a
 * linha Faces (desvincular e vincular por face) e o rascunho do nome da rede nova.
 */
final class E2eLinkerSteps {
    private E2eLinkerSteps() {
    }

    private static final String AREA_NETWORK = "E2E Área";
    private static UUID areaNetwork;
    static BlockPos areaCorner1;
    static BlockPos areaCorner2;
    /** Rede de itens de A e de B antes do Vincular (só a de fluidos pode mudar). */
    private static UUID itemsA;
    private static UUID itemsB;

    /**
     * Vinculador em modo Área pela tela: com o Vinculador na mão e o Shift pressionado (o mesmo
     * pacote do teclado), clique no ar passa para Área e dois cliques marcam os cantos (o chão
     * diante do baú A e o roteador B), pegando os dois roteadores; solta o Shift, clique no ar abre a
     * tela, que mostra os dois na área; escolhe a rede "E2E Área" na lista, o tipo Fluidos e clica em
     * Vincular. Confere no servidor que só a aba de fluidos dos dois mudou e que a tela mostra o
     * resultado.
     */
    static void linkerAreaSteps(List<Step> list) {
        list.add(new Step("Vinculador na mão", STEP_TIMEOUT_MS, () -> {
            UUID playerId = Minecraft.getInstance().player.getUUID();
            onServer(server -> {
                ServerPlayer player = server.getPlayerList().getPlayer(playerId);
                areaNetwork = NetworkSavedData.get(server).create(player.getUUID(), AREA_NETWORK).id();
                itemsA = router(server, routerA).networkId(ResourceType.ITEM);
                itemsB = router(server, routerB).networkId(ResourceType.ITEM);
                player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ModItems.LINKER.get()));
                return null;
            });
            areaCorner1 = chestA.offset(-1, -1, -1);
            areaCorner2 = routerB;
        }, () -> Minecraft.getInstance().player.getMainHandItem().is(ModItems.LINKER.get()),
                () -> "na mão " + Minecraft.getInstance().player.getMainHandItem()));
        list.add(shift("Shift pressionado", true));
        list.add(new Step("Shift + clique no ar: modo Área", STEP_TIMEOUT_MS, () -> {
            Minecraft minecraft = Minecraft.getInstance();
            minecraft.gameMode.useItem(minecraft.player, InteractionHand.MAIN_HAND);
        }, () -> serverLinker(stack -> LinkerItem.mode(stack) == LinkerMode.AREA)
                && LinkerItem.mode(Minecraft.getInstance().player.getMainHandItem()) == LinkerMode.AREA,
                () -> "modo no servidor " + serverLinker(stack -> LinkerItem.mode(stack).name())));
        list.add(new Step("Shift + clique nos dois cantos", STEP_TIMEOUT_MS, () -> {
            Minecraft minecraft = Minecraft.getInstance();
            for (BlockPos corner : List.of(areaCorner1, areaCorner2)) {
                minecraft.gameMode.useItemOn(minecraft.player, InteractionHand.MAIN_HAND,
                        new BlockHitResult(Vec3.atCenterOf(corner), Direction.UP, corner, false));
            }
        }, () -> serverLinker(stack -> {
            LinkerArea area = LinkerItem.area(stack);
            return area != null && area.first().equals(areaCorner1) && area.second().equals(Optional.of(areaCorner2));
        }) && LinkerItem.area(Minecraft.getInstance().player.getMainHandItem()) != null
                && LinkerItem.area(Minecraft.getInstance().player.getMainHandItem()).complete(),
                () -> "área no servidor " + serverLinker(stack -> String.valueOf(LinkerItem.area(stack)))));
        list.add(shift("Shift solto", false));
        list.add(capture("7b-vinculador-contorno"));
        list.add(new Step("abrir o Vinculador (clique no ar)", STEP_TIMEOUT_MS, () -> {
            Minecraft minecraft = Minecraft.getInstance();
            if (minecraft.screen != null) {
                throw new StepFailure("ainda há uma tela aberta: " + describe(minecraft.screen));
            }
            minecraft.gameMode.useItem(minecraft.player, InteractionHand.MAIN_HAND);
        }, () -> Minecraft.getInstance().screen instanceof LinkerScreen screen
                && screen.getMenu().snapshot().inside() == 2
                && screen.getMenu().snapshot().problem() == LinkerProblem.NONE,
                () -> Minecraft.getInstance().screen instanceof LinkerScreen screen
                        ? "na área " + screen.getMenu().snapshot().inside() + ", " + screen.getMenu().snapshot().problem()
                        : "tela " + describe(Minecraft.getInstance().screen)));
        list.add(capture("8-vinculador-area"));
        list.add(new Step("escolher a rede " + AREA_NETWORK, STEP_TIMEOUT_MS,
                () -> click(widget(byMessage(Component.literal(AREA_NETWORK)), "linha da rede " + AREA_NETWORK)),
                () -> {
                    UUID playerId = Minecraft.getInstance().player.getUUID();
                    return areaNetwork.equals(onServer(server -> NetworkSavedData.get(server).activeNetwork(playerId)))
                            && linkerScreen().getMenu().snapshot().active().equals(Optional.of(areaNetwork));
                }, () -> "ativa na tela " + linkerScreen().getMenu().snapshot().active()));
        // de Todos, o botão deixa só a primeira aba; apertado de novo, marca tudo outra vez
        list.add(new Step("Todos com tudo marcado deixa uma aba", STEP_TIMEOUT_MS,
                () -> click(widget(byMessageKey("gui.wirelessautomate.linker.type.all"), "Todos")),
                // espera a tela também: o próximo clique lê o estado dela, e um estado velho desmarcaria de novo
                () -> serverLinker(stack -> LinkerItem.effectiveTabs(stack).size() == 1)
                        && linkerScreen().getMenu().snapshot().tabs().effective(LoadedTypes.LIST).size() == 1,
                () -> "abas " + linkerScreen().getMenu().snapshot().tabs()));
        list.add(new Step("Todos marca tudo", STEP_TIMEOUT_MS,
                () -> click(widget(byMessageKey("gui.wirelessautomate.linker.type.all"), "Todos")),
                () -> serverLinker(stack -> LinkerItem.tabs(stack).isAll(LoadedTypes.LIST)),
                () -> "abas " + linkerScreen().getMenu().snapshot().tabs()));
        list.add(capture("8b-vinculador-chips"));
        list.add(new Step("Vinculador maior", STEP_TIMEOUT_MS,
                () -> linkerScreen().previewResize(460, 300),
                () -> linkerScreen().size()[0] > 300 && linkerScreen().size()[1] > 204,
                () -> "tamanho " + java.util.Arrays.toString(linkerScreen().size())));
        list.add(capture("8d-vinculador-grande"));
        list.add(new Step("Vinculador de volta ao mínimo", STEP_TIMEOUT_MS,
                () -> linkerScreen().previewResize(300, 204),
                () -> linkerScreen().size()[0] == 300 && linkerScreen().size()[1] == 204,
                () -> "tamanho " + java.util.Arrays.toString(linkerScreen().size())));
        list.add(new Step("só a aba Fluidos", STEP_TIMEOUT_MS,
                () -> {
                    // de Todos, desmarca as outras caixas (só aparecem os tipos disponíveis)
                    for (ResourceType type : new ResourceType[] {ResourceType.ITEM, ResourceType.ENERGY,
                            ResourceType.CHEMICAL, ResourceType.SOURCE}) {
                        if (linkerScreen().getMenu().snapshot().available().contains(type)) {
                            click(widget(byMessage(Component.translatable("gui.wirelessautomate.router.type."
                                    + type.key())), "aba " + type));
                        }
                    }
                },
                () -> serverLinker(stack -> LinkerItem.effectiveTabs(stack).equals(List.of(ResourceType.FLUID)))
                        && linkerScreen().getMenu().snapshot().effectiveTabs().equals(List.of(ResourceType.FLUID))
                        && linkerScreen().getMenu().snapshot().already() == 0,
                () -> "abas na tela " + linkerScreen().getMenu().snapshot().tabs()));
        list.add(new Step("Vincular", STEP_TIMEOUT_MS,
                () -> click(widget(byMessageKey("gui.wirelessautomate.linker.link.count"), "Vincular")),
                () -> onServer(server -> {
                    RouterBlockEntity a = router(server, routerA);
                    RouterBlockEntity b = router(server, routerB);
                    return areaNetwork.equals(a.networkId(ResourceType.FLUID))
                            && areaNetwork.equals(b.networkId(ResourceType.FLUID))
                            && java.util.Objects.equals(itemsA, a.networkId(ResourceType.ITEM))
                            && java.util.Objects.equals(itemsB, b.networkId(ResourceType.ITEM));
                }) && linkerScreen().getMenu().snapshot().outcome().map(o -> o.linked() == 2).orElse(false)
                        && linkerScreen().getMenu().snapshot().already() == 2,
                () -> "A: " + serverNetworks(routerA) + "; B: " + serverNetworks(routerB) + "; resultado na tela "
                        + linkerScreen().getMenu().snapshot().outcome()));
        list.add(capture("9-vinculador-vinculado"));
        linkerFacesSteps(list);
        list.add(close("fechar o Vinculador"));
    }

    /**
     * Linha Faces (rede por face): desmarca Frente, Esquerda, Direita e Cima (sobram Trás e Baixo),
     * escolhe "Nenhuma (desvincular)" e Desvincular tira da rede só as faces de fluidos Trás e Baixo de
     * cada roteador (pela frente da máquina de cada um); de volta à rede, Vincular põe essas faces de
     * novo, e Todas marca as seis.
     */
    private static void linkerFacesSteps(List<Step> list) {
        LinkerFaces backAndBottom = LinkerFaces.NONE.toggle(RemoteSide.BACK).toggle(RemoteSide.BOTTOM);
        LinkerFaces faces = LinkerFaces.ALL;
        for (RemoteSide side : new RemoteSide[] {RemoteSide.FRONT, RemoteSide.LEFT, RemoteSide.RIGHT, RemoteSide.TOP}) {
            faces = faces.toggle(side);
            LinkerFaces expected = faces;
            list.add(new Step("desmarcar a face " + side.key(), STEP_TIMEOUT_MS,
                    () -> click(widget(byMessageKey("gui.wirelessautomate.router.side.letter." + side.key()),
                            "face " + side.key())),
                    // espera a tela também: o próximo clique parte do estado dela
                    () -> serverLinker(stack -> LinkerItem.faces(stack).equals(expected))
                            && linkerScreen().getMenu().snapshot().faces().equals(expected),
                    () -> "faces " + linkerScreen().getMenu().snapshot().faces()));
        }
        list.add(new Step("Nenhuma (desvincular)", STEP_TIMEOUT_MS,
                () -> click(widget(byMessageKey("gui.wirelessautomate.linker.network.unlink"), "Nenhuma")),
                () -> serverLinker(LinkerItem::unlink) && linkerScreen().getMenu().snapshot().unlink(),
                () -> "desvincular na tela " + linkerScreen().getMenu().snapshot().unlink()));
        list.add(clipCheck("Vinculador com faces"));
        list.add(capture("9b-vinculador-faces-desvincular"));
        list.add(new Step("Desvincular Trás e Baixo", STEP_TIMEOUT_MS,
                () -> click(widget(byMessageKey("gui.wirelessautomate.linker.unlink.count.faces"), "Desvincular")),
                () -> onServer(server -> {
                    for (BlockPos pos : List.of(routerA, routerB)) {
                        RouterBlockEntity router = router(server, pos);
                        int marked = backAndBottom.absoluteMask(router.attachedFront());
                        if (!router.attachedFacesIn(List.of(ResourceType.FLUID), marked, null)
                                || !router.attachedFacesIn(List.of(ResourceType.FLUID), 63 & ~marked, areaNetwork)) {
                            return false;
                        }
                    }
                    return true;
                }) && linkerScreen().getMenu().snapshot().outcome().map(o -> o.unlink() && o.linked() == 2)
                        .orElse(false),
                () -> "A: " + serverNetworks(routerA) + "; B: " + serverNetworks(routerB) + "; resultado na tela "
                        + linkerScreen().getMenu().snapshot().outcome()));
        list.add(rangeClipCheck("Vinculador desvinculado por faces"));
        list.add(capture("9c-vinculador-faces-desvinculadas"));
        list.add(new Step("de volta à rede " + AREA_NETWORK, STEP_TIMEOUT_MS,
                () -> click(widget(byMessage(Component.literal(AREA_NETWORK)), "linha da rede " + AREA_NETWORK)),
                () -> serverLinker(stack -> !LinkerItem.unlink(stack)) && !linkerScreen().getMenu().snapshot().unlink(),
                () -> "desvincular na tela " + linkerScreen().getMenu().snapshot().unlink()));
        list.add(new Step("Vincular Trás e Baixo", STEP_TIMEOUT_MS,
                () -> click(widget(byMessageKey("gui.wirelessautomate.linker.link.count.faces"), "Vincular")),
                () -> onServer(server -> router(server, routerA).attachedFacesIn(List.of(ResourceType.FLUID), 63,
                        areaNetwork) && router(server, routerB).attachedFacesIn(List.of(ResourceType.FLUID), 63,
                        areaNetwork))
                        && linkerScreen().getMenu().snapshot().outcome().map(o -> !o.unlink() && o.linked() == 2)
                                .orElse(false),
                () -> "A: " + serverNetworks(routerA) + "; B: " + serverNetworks(routerB)));
        list.add(rangeClipCheck("Vinculador vinculado por faces"));
        list.add(capture("9d-vinculador-faces-vinculadas"));
        // modo Único com Trás e Baixo marcados: a linha Faces no pé da coluna e as dicas acima dela
        list.add(new Step("modo Único com faces", STEP_TIMEOUT_MS,
                () -> click(widget(byMessageKey("gui.wirelessautomate.linker.mode.single"), "Único")),
                () -> serverLinker(stack -> LinkerItem.mode(stack) == LinkerMode.SINGLE)
                        && linkerScreen().getMenu().snapshot().mode() == LinkerMode.SINGLE,
                () -> "modo " + linkerScreen().getMenu().snapshot().mode()));
        list.add(rangeClipCheck("Vinculador Único com faces"));
        list.add(capture("9e-vinculador-unico-faces"));
        list.add(new Step("de volta a Área", STEP_TIMEOUT_MS,
                () -> click(widget(byMessageKey("gui.wirelessautomate.linker.mode.area"), "Área")),
                () -> serverLinker(stack -> LinkerItem.mode(stack) == LinkerMode.AREA)
                        && linkerScreen().getMenu().snapshot().mode() == LinkerMode.AREA,
                () -> "modo " + linkerScreen().getMenu().snapshot().mode()));
        list.add(new Step("Todas as faces", STEP_TIMEOUT_MS,
                () -> click(widget(byMessageKey("gui.wirelessautomate.linker.faces.all"), "Todas")),
                () -> serverLinker(stack -> LinkerItem.faces(stack).isAll())
                        && linkerScreen().getMenu().snapshot().faces().isAll(),
                () -> "faces " + linkerScreen().getMenu().snapshot().faces()));
        draftSteps(list);
    }

    private static final String DRAFT_NAME = "Rascunho e2e";

    /**
     * O nome começado para uma rede nova volta ao reabrir o campo e a tela, e depois de criar a rede o
     * campo volta vazio. Termina com a rede de área ativa de novo.
     */
    private static void draftSteps(List<Step> list) {
        String newKey = "gui.wirelessautomate.linker.network.new";
        list.add(new Step("rascunho: abrir o campo da rede nova", STEP_TIMEOUT_MS,
                () -> click(widget(byMessageKey(newKey), "+ Nova rede")),
                () -> linkerScreen().creating() && linkerScreen().nameBox().getValue().isEmpty(),
                () -> "criando " + linkerScreen().creating() + ", texto '" + linkerScreen().nameBox().getValue() + "'"));
        list.add(new Step("rascunho: digitar o nome", STEP_TIMEOUT_MS, () -> type(DRAFT_NAME),
                () -> linkerScreen().nameBox().getValue().equals(DRAFT_NAME),
                () -> "texto '" + linkerScreen().nameBox().getValue() + "'"));
        list.add(new Step("rascunho: fechar o campo com Esc", STEP_TIMEOUT_MS,
                () -> linkerScreen().keyPressed(GLFW.GLFW_KEY_ESCAPE, 0, 0),
                () -> !linkerScreen().creating(), () -> "criando " + linkerScreen().creating()));
        list.add(new Step("rascunho: reabrir o campo, o texto volta", STEP_TIMEOUT_MS,
                () -> click(widget(byMessageKey(newKey), "+ Nova rede")),
                () -> linkerScreen().creating() && linkerScreen().nameBox().getValue().equals(DRAFT_NAME),
                () -> "criando " + linkerScreen().creating() + ", texto '" + linkerScreen().nameBox().getValue() + "'"));
        list.add(new Step("rascunho: fechar o campo de novo", STEP_TIMEOUT_MS,
                () -> linkerScreen().keyPressed(GLFW.GLFW_KEY_ESCAPE, 0, 0),
                () -> !linkerScreen().creating(), () -> "criando " + linkerScreen().creating()));
        list.add(close("rascunho: fechar a tela"));
        list.add(new Step("rascunho: reabrir a tela", STEP_TIMEOUT_MS, () -> {
            Minecraft minecraft = Minecraft.getInstance();
            if (minecraft.screen != null) {
                throw new StepFailure("ainda há uma tela aberta: " + describe(minecraft.screen));
            }
            minecraft.gameMode.useItem(minecraft.player, InteractionHand.MAIN_HAND);
        }, () -> Minecraft.getInstance().screen instanceof LinkerScreen screen
                && screen.getMenu().snapshot().active().isPresent(),
                () -> "tela " + describe(Minecraft.getInstance().screen)));
        list.add(new Step("rascunho: abrir o campo na tela nova, o texto volta", STEP_TIMEOUT_MS,
                () -> click(widget(byMessageKey(newKey), "+ Nova rede")),
                () -> linkerScreen().creating() && linkerScreen().nameBox().getValue().equals(DRAFT_NAME),
                () -> "criando " + linkerScreen().creating() + ", texto '" + linkerScreen().nameBox().getValue() + "'"));
        list.add(new Step("rascunho: Enter cria a rede", STEP_TIMEOUT_MS,
                () -> linkerScreen().keyPressed(GLFW.GLFW_KEY_ENTER, 0, 0),
                () -> {
                    UUID playerId = Minecraft.getInstance().player.getUUID();
                    return !linkerScreen().creating()
                            && onServer(server -> NetworkSavedData.get(server).byName(playerId, DRAFT_NAME) != null);
                },
                () -> "criando " + linkerScreen().creating() + ", ativa " + linkerScreen().getMenu().snapshot().active()));
        list.add(new Step("rascunho: depois de criar, o campo volta vazio", STEP_TIMEOUT_MS,
                () -> click(widget(byMessageKey(newKey), "+ Nova rede")),
                () -> linkerScreen().creating() && linkerScreen().nameBox().getValue().isEmpty(),
                () -> "criando " + linkerScreen().creating() + ", texto '" + linkerScreen().nameBox().getValue() + "'"));
        list.add(new Step("rascunho: fechar o campo vazio", STEP_TIMEOUT_MS,
                () -> linkerScreen().keyPressed(GLFW.GLFW_KEY_ESCAPE, 0, 0),
                () -> !linkerScreen().creating(), () -> "criando " + linkerScreen().creating()));
        list.add(new Step("rascunho: de volta à rede " + AREA_NETWORK, STEP_TIMEOUT_MS,
                () -> click(widget(byMessage(Component.literal(AREA_NETWORK)), "linha da rede " + AREA_NETWORK)),
                () -> linkerScreen().getMenu().snapshot().active().equals(Optional.of(areaNetwork)),
                () -> "ativa na tela " + linkerScreen().getMenu().snapshot().active()));
    }

    /** Lê o Vinculador da mão principal do jogador no servidor. */
    static <T> T serverLinker(Function<ItemStack, T> read) throws Exception {
        UUID playerId = Minecraft.getInstance().player.getUUID();
        return onServer(server -> read.apply(server.getPlayerList().getPlayer(playerId).getMainHandItem()));
    }

    static LinkerScreen linkerScreen() throws StepFailure {
        if (Minecraft.getInstance().screen instanceof LinkerScreen screen) {
            return screen;
        }
        throw new StepFailure("a tela do Vinculador não está aberta (tela: " + describe(Minecraft.getInstance().screen) + ")");
    }
}
