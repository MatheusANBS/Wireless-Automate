package io.github.matheusanbs.wirelessautomate.client.dev;

import static io.github.matheusanbs.wirelessautomate.client.dev.DevEndToEnd.*;
import static io.github.matheusanbs.wirelessautomate.client.dev.E2eFilterSteps.*;
import static io.github.matheusanbs.wirelessautomate.client.dev.E2eLinkerSteps.*;
import static io.github.matheusanbs.wirelessautomate.client.dev.E2eRangeSteps.*;
import static io.github.matheusanbs.wirelessautomate.client.dev.E2eRouterSteps.*;
import static io.github.matheusanbs.wirelessautomate.client.dev.E2eShowcaseWorld.*;
import static io.github.matheusanbs.wirelessautomate.client.dev.E2eSteps.*;
import static io.github.matheusanbs.wirelessautomate.client.dev.E2eTabletSteps.*;
import static io.github.matheusanbs.wirelessautomate.client.dev.E2eUi.*;
import static io.github.matheusanbs.wirelessautomate.client.dev.E2eUpgradeCardSteps.*;
import static io.github.matheusanbs.wirelessautomate.client.dev.E2eWorld.*;

import io.github.matheusanbs.wirelessautomate.client.filter.FilterScreen;
import io.github.matheusanbs.wirelessautomate.client.linker.LinkerScreen;
import io.github.matheusanbs.wirelessautomate.client.storage.StorageListScreen;
import io.github.matheusanbs.wirelessautomate.client.storage.StorageScalarScreen;
import io.github.matheusanbs.wirelessautomate.client.tablet.TabletScreen;
import io.github.matheusanbs.wirelessautomate.filter.ItemRule;
import io.github.matheusanbs.wirelessautomate.item.ConfiguratorItem;
import io.github.matheusanbs.wirelessautomate.item.LinkerItem;
import io.github.matheusanbs.wirelessautomate.linker.LinkerArea;
import io.github.matheusanbs.wirelessautomate.range.EditTarget;
import io.github.matheusanbs.wirelessautomate.range.RemoteSide;
import io.github.matheusanbs.wirelessautomate.network.NetworkManager;
import io.github.matheusanbs.wirelessautomate.network.ResourceType;
import io.github.matheusanbs.wirelessautomate.registry.ModItems;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.client.ClientCommandHandler;
import org.lwjgl.glfw.GLFW;
import io.github.matheusanbs.wirelessautomate.storage.StorageKind;
import io.github.matheusanbs.wirelessautomate.client.ConfiguratorWheelScreen;

/**
 * A vitrine do CurseForge ({@code WA_SHOWCASE}): os passos que posicionam a câmera, abrem as telas e
 * fotografam a casa montada pelo {@link E2eShowcaseWorld}.
 */
final class E2eShowcase {
    /**
     * Com {@code WA_SHOWCASE_ONLY=ressonador}, a vitrine monta a casa e grava só o Ressonador (GIF e fotos); com
     * {@code cascata}, só a foto da cascata, com {@code reparador}, só as fotos do Reparador de Cristais, e com
     * {@code tela}, só a parede das Telas de Sinal (fotos e GIF).
     */
    private static final String ONLY = System.getenv("WA_SHOWCASE_ONLY");

    private E2eShowcase() {
    }

    /**
     * Fotos da página do CurseForge, numa casa de quartzo sem teto, de dia: a linha de fornalhas
     * (oeste), a galeria dos cinco armazenamentos com fluxo de verdade para um cofre sob o piso
     * (centro-norte), o salão dos oito tiers (fundo), a vitrine dos itens do mod em pedestais (centro)
     * e o laboratório do Ars Nouveau com quatro tiers do Tanque de Source (leste); no jardim, a cascata do Ressonador
     * (oeste), a Vitrine do Ressonador (leste) e, no fim do caminho, a parede das Telas de Sinal. Fotografa a casa inteira, cada ala e as telas. As
     * imagens finais saem de scripts/curseforge/gerar_imagens.py.
     */
    static List<Step> showcase() {
        List<Step> list = new ArrayList<>();
        if ("ressonador".equals(ONLY)) {
            list.add(mountWorld());
            E2eShowcaseResonator.steps(list);
            return list;
        }
        if ("reparador".equals(ONLY)) {
            list.add(mountWorld());
            E2eShowcaseRepairer.steps(list);
            return list;
        }
        if ("tela".equals(ONLY)) {
            list.add(mountWorld());
            E2eShowcaseTv.steps(list);
            return list;
        }
        if ("cascata".equals(ONLY)) {
            list.add(mountWorld());
            showcaseCascadeShots(list);
            return list;
        }
        showcaseHouseShots(list);
        showcaseToolShots(list);
        showcaseRouterShots(list);
        showcaseTierAndRangeShots(list);
        showcaseStorageShots(list);
        showcaseTabletShots(list);
        E2eShowcaseTv.steps(list);
        E2eShowcaseRepairer.steps(list);
        // por último: o GIF congela o tempo enquanto grava
        E2eShowcaseResonator.steps(list);
        return list;
    }

    private static Step mountWorld() {
        return new Step("montar a vitrine", STEP_TIMEOUT_MS, E2eShowcaseWorld::showcaseWorld,
                () -> onServer(server -> showRouters.stream().allMatch(p -> NetworkManager.get().contains(router(server, p))))
                        && showRouters.stream().allMatch(E2eRouterSteps::clientSees),
                () -> showRouters.size() + " roteadores");
    }

    /** Vitrine: monta o mundo, vincula o alcance e fotografa a casa e as fornalhas. */
    private static void showcaseHouseShots(List<Step> list) {
        list.add(mountWorld());
        list.add(new Step("vincular as máquinas do alcance", STEP_TIMEOUT_MS, () -> onServer(server -> {
            showcaseRangeLink(server);
            return null;
        }), () -> onServer(server -> router(server, showRange).remoteMachines().machines().size() == 10),
                () -> "máquinas " + onServer(server -> router(server, showRange).remoteMachines().machines().size())));
        // A casa inteira, de cima, vista do jardim.
        list.add(showLook("câmera na casa", 0, 17, 4.5, 0, 0, -14.5, false));
        list.add(DevEndToEnd.wait("chunks e itens", 100));
        list.add(capture("s0-casa"));
        // A linha de fornalhas, da ala oeste.
        list.add(showLook("câmera nas fornalhas", -5, 2.6, -7, -10.5, 0.6, -11.5, false));
        list.add(DevEndToEnd.wait("fornalhas", 20));
        list.add(capture("s1-fabrica"));
    }

    /** Vitrine: o Vinculador e o Configurador na mão, com a área, a tela do Vinculador e a roda. */
    private static void showcaseToolShots(List<Step> list) {
        // Vinculador na mão, com a fileira de fornalhas marcada.
        list.add(new Step("área do Vinculador", STEP_TIMEOUT_MS, () -> {
            UUID playerId = Minecraft.getInstance().player.getUUID();
            onServer(server -> {
                ServerPlayer player = server.getPlayerList().getPlayer(playerId);
                ItemStack linker = new ItemStack(ModItems.LINKER.get());
                LinkerItem.setArea(linker, new LinkerArea(player.level().dimension(), showBase.offset(-11, 0, -15),
                        java.util.Optional.of(showBase.offset(-8, 1, -7))));
                LinkerItem.setRouter(linker, net.minecraft.core.GlobalPos.of(player.level().dimension(), showRange));
                player.setItemInHand(InteractionHand.MAIN_HAND, linker);
                return null;
            });
        }, () -> LinkerItem.area(Minecraft.getInstance().player.getMainHandItem()) != null, () -> "Vinculador na mão"));
        list.add(DevEndToEnd.wait("contorno", 10));
        list.add(capture("s2-vinculador-area"));
        // Com o Vinculador ainda na mão: a tela dele.
        list.add(new Step("abrir o Vinculador", STEP_TIMEOUT_MS, () -> {
            Minecraft minecraft = Minecraft.getInstance();
            minecraft.options.hideGui = false;
            minecraft.gameMode.useItem(minecraft.player, InteractionHand.MAIN_HAND);
        }, () -> Minecraft.getInstance().screen instanceof LinkerScreen,
                () -> "tela " + describe(Minecraft.getInstance().screen)));
        list.add(new Step("Vinculador maior", STEP_TIMEOUT_MS,
                () -> linkerScreen().previewResize(460, 300),
                () -> linkerScreen().size()[0] > 300,
                () -> "tamanho " + java.util.Arrays.toString(linkerScreen().size())));
        list.add(DevEndToEnd.wait("tela do Vinculador", 5));
        list.add(capture("s13-vinculador"));
        list.add(close("fechar o Vinculador"));
        // Configurador na mão e a roda aberta, com o mouse no tipo Itens.
        list.add(new Step("Configurador na mão", STEP_TIMEOUT_MS, () -> {
            UUID playerId = Minecraft.getInstance().player.getUUID();
            onServer(server -> {
                ServerPlayer player = server.getPlayerList().getPlayer(playerId);
                player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ModItems.CONFIGURATOR.get()));
                return null;
            });
        }, () -> Minecraft.getInstance().player.getMainHandItem().getItem() instanceof ConfiguratorItem,
                () -> "Configurador na mão"));
        list.add(showLook("câmera nas fornalhas (roda)", -5, 2.6, -7, -10.5, 0.6, -11.5, false));
        list.add(new Step("abrir a roda", STEP_TIMEOUT_MS, () -> {
            ConfiguratorWheelScreen wheel = new ConfiguratorWheelScreen();
            Minecraft.getInstance().setScreen(wheel);
            wheel.previewHover(wheel.sliceOf(ResourceType.ITEM));
        }, () -> Minecraft.getInstance().screen instanceof ConfiguratorWheelScreen, () -> "tela "
                + describe(Minecraft.getInstance().screen)));
        list.add(DevEndToEnd.wait("abertura da roda", 10));
        list.add(capture("s16-roda"));
        list.add(close("fechar a roda"));
        list.add(new Step("mão vazia e interface", STEP_TIMEOUT_MS, () -> {
            Minecraft.getInstance().options.hideGui = false;
            UUID playerId = Minecraft.getInstance().player.getUUID();
            onServer(server -> {
                server.getPlayerList().getPlayer(playerId).setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
                return null;
            });
        }, () -> Minecraft.getInstance().player.getMainHandItem().isEmpty(), () -> "mão"));
    }

    /** Vitrine: a tela do roteador da entrada, a da fornalha com a lista de redes e o filtro. */
    private static void showcaseRouterShots(List<Step> list) {
        // Tela do baú de entrada, larga, com as cinco abas e a vazão aparecendo.
        list.add(showLook("perto da entrada", -5.5, 0.5, -13, -8, 1, -13, true));
        list.add(new Step("repor os itens", STEP_TIMEOUT_MS, () -> onServer(server -> {
            refillShowcase(server.overworld());
            return null;
        }), () -> true, () -> "itens"));
        list.add(open("abrir a entrada", () -> showInput.above()));
        list.add(new Step("roteador largo", STEP_TIMEOUT_MS,
                () -> routerScreen().previewResize(Math.min(600, routerScreen().width - 8), 300),
                () -> routerScreen().size()[0] > 400, () -> "tamanho " + java.util.Arrays.toString(routerScreen().size())));
        list.add(new Step("vazão na tela", 15_000, () -> {
        }, () -> routerScreen().getMenu().throughput()[ResourceType.ITEM.ordinal()] > 0,
                () -> "vazão " + routerScreen().getMenu().throughput()[ResourceType.ITEM.ordinal()]));
        list.add(capture("s3-roteador"));
        list.add(close("fechar a entrada"));
        // Filtro da fornalha (face de cima, itens).
        list.add(showLook("perto da fornalha", -9.5, 0.5, -11, -11, 1, -11, true));
        list.add(open("abrir a fornalha", () -> showFurnace.above()));
        list.add(new Step("filtro da fornalha", STEP_TIMEOUT_MS, () -> {
            clickFace(Direction.UP);
        }, () -> panel().editCenter() != null, () -> "Editar"));
        list.add(capture("s4-roteador-fornalha"));
        // Rede por face: a fornalha larga, face de baixo (na rede dos armazenamentos) com a lista de redes aberta.
        list.add(new Step("fornalha larga, face de baixo", STEP_TIMEOUT_MS, () -> {
            routerScreen().previewResize(Math.min(600, routerScreen().width - 8), 300);
            clickFace(Direction.DOWN);
        }, () -> routerScreen().size()[0] > 400 && routerScreen().getMenu().selectedFace() == Direction.DOWN,
                () -> "face " + routerScreen().getMenu().selectedFace()));
        list.add(new Step("abrir a lista de redes", STEP_TIMEOUT_MS,
                () -> clickPanel(panel().networkCenter(), "pílula da Rede"),
                () -> routerScreen().networkListShown() && stepTicks >= 4, () -> "lista " + routerScreen().networkListShown()));
        list.add(capture("s3b-roteador-redes"));
        list.add(new Step("fechar a lista e voltar à face de cima", STEP_TIMEOUT_MS, () -> {
            routerScreen().keyPressed(GLFW.GLFW_KEY_ESCAPE, 0, 0);
            clickFace(Direction.UP);
        }, () -> !routerScreen().networkListShown() && routerScreen().getMenu().selectedFace() == Direction.UP,
                () -> "lista " + routerScreen().networkListShown()));
        list.add(new Step("abrir o filtro", STEP_TIMEOUT_MS, () -> {
            UUID playerId = Minecraft.getInstance().player.getUUID();
            onServer(server -> {
                showcaseInventory(server, server.getPlayerList().getPlayer(playerId));
                return null;
            });
            clickPanel(panel().editCenter(), "Editar");
        }, () -> Minecraft.getInstance().screen instanceof FilterScreen, () -> "tela " + describe(Minecraft.getInstance().screen)));
        list.add(capture("s5-filtro"));
        list.add(new Step("filtro: tags do minério de ferro", STEP_TIMEOUT_MS, () -> {
            FilterScreen screen = filterScreen();
            screen.previewInspect(new ItemStack(Items.RAW_IRON));
            List<String> labels = screen.candidateLabels();
            for (String label : List.of("#c:raw_materials", "#c:raw_materials/iron")) {
                if (labels.contains(label)) {
                    screen.previewCheck(labels.indexOf(label));
                }
            }
            int[] row = screen.candidateCenter(Math.max(0, labels.indexOf("#c:raw_materials")));
            moveMouse(row[0], row[1]);
        }, () -> filterScreen().tab() == FilterScreen.Tab.TAGS, () -> "aba " + filterScreen().tab()));
        list.add(DevEndToEnd.wait("prévia da tag", 5));
        list.add(capture("s5b-filtro-tags"));
        list.add(new Step("filtro: regra de reparo", STEP_TIMEOUT_MS, () -> {
            filterScreen().previewRule(ItemRule.EMPTY.withFlag(ItemRule.Property.ENCHANTED, true)
                    .withDurability(java.util.Optional.of(new ItemRule.Durability(false, 50))));
            moveMouse(0, 0);
        }, () -> filterScreen().tab() == FilterScreen.Tab.RULE, () -> "aba " + filterScreen().tab()));
        list.add(DevEndToEnd.wait("inventário aceso", 5));
        list.add(capture("s5c-filtro-regra"));
        list.add(close("fechar o filtro"));
        list.add(new Step("inventário vazio", STEP_TIMEOUT_MS, () -> {
            UUID playerId = Minecraft.getInstance().player.getUUID();
            onServer(server -> {
                server.getPlayerList().getPlayer(playerId).getInventory().clearContent();
                return null;
            });
        }, () -> Minecraft.getInstance().player.getInventory().isEmpty(), () -> "inventário"));
    }

    /** Vitrine: o salão dos tiers, o roteador de alcance e os Cartões de Upgrade. */
    private static void showcaseTierAndRangeShots(List<Step> list) {
        // O salão dos tiers, visto de trás da galeria dos armazenamentos.
        list.add(showLook("câmera no salão dos tiers", 0, 1.1, -16.9, 0, 2.3, -24, false));
        list.add(DevEndToEnd.wait("salão dos tiers", 20));
        list.add(capture("s15-tiers"));
        // O roteador de alcance: o canto noroeste e a tela larga, com a lista e o visor lado a lado.
        list.add(showLook("câmera no alcance", -4, 2.2, -20, -11, 0.6, -20, false));
        list.add(DevEndToEnd.wait("alcance", 20));
        list.add(capture("s17-alcance"));
        list.add(showLook("perto do roteador de alcance", -6, 0.5, -20, -8, 1, -20, true));
        list.add(open("abrir o roteador de alcance", () -> showRange));
        list.add(new Step("alcance: tela larga com a lista", STEP_TIMEOUT_MS, () -> {
            routerScreen().previewResize(Math.min(600, routerScreen().width - 8), 300);
            if (!routerScreen().rangeListShown()) {
                routerScreen().previewToggleList();
            }
        }, () -> routerScreen().rangeListShown() && routerScreen().rangeViewShown() && rangeView().groups().size() == 2
                && stepTicks >= 4, () -> "lista " + routerScreen().rangeListShown() + ", visor " + routerScreen().rangeViewShown()));
        list.add(new Step("alcance: grupo dos altos-fornos", STEP_TIMEOUT_MS,
                () -> clickRow(EditTarget.group(rangeGroup(Blocks.BLAST_FURNACE))),
                () -> routerScreen().getMenu().snapshot().target().equals(EditTarget.group(rangeGroup(Blocks.BLAST_FURNACE)))
                        && stepTicks >= 4,
                () -> "alvo " + routerScreen().getMenu().snapshot().target()));
        list.add(new Step("alcance: face de cima e vazão", 20_000,
                () -> clickSide(RemoteSide.TOP),
                () -> routerScreen().getMenu().selectedFace() == Direction.UP
                        && (routerScreen().getMenu().throughput()[ResourceType.ITEM.ordinal()] > 0 || stepTicks > 300),
                () -> "face " + routerScreen().getMenu().selectedFace() + ", vazão "
                        + routerScreen().getMenu().throughput()[ResourceType.ITEM.ordinal()]));
        list.add(new Step("alcance: anotar a vazão", STEP_TIMEOUT_MS, () -> record("alcance: vazão",
                String.valueOf(routerScreen().getMenu().throughput()[ResourceType.ITEM.ordinal()])), () -> true, () -> ""));
        list.add(capture("s18-alcance-tela"));
        list.add(close("fechar o roteador de alcance"));
        // Subir tier: o tooltip do cartão com origem e as receitas no JEI, com o salão ao fundo.
        list.add(showLook("câmera no salão (cartões)", 0, 1.1, -16.9, 0, 2.3, -24, true));
        upgradeCardSteps(list);
    }

    /** Vitrine: a vitrine dos itens, os armazenamentos, o Baú e o laboratório do Ars. */
    private static void showcaseStorageShots(List<Step> list) {
        // A vitrine dos itens, de perto.
        list.add(showLook("câmera na vitrine", 0, 4.2, -5, 0, 0.8, -10.5, false));
        list.add(DevEndToEnd.wait("vitrine", 20));
        list.add(capture("s14-vitrine"));
        showcaseCascadeShots(list);
        // A galeria dos armazenamentos.
        list.add(showLook("câmera nos armazenamentos", 0, 3.2, -11, 0, 0.5, -16.5, false));
        list.add(DevEndToEnd.wait("armazenamentos", 20));
        list.add(capture("s9-armazenamentos"));
        list.add(showLook("perto do Baú", -4, 0.5, -13.5, -4, 0.5, -16, true));
        list.add(new Step("abrir o Baú Wireless", STEP_TIMEOUT_MS, () -> useOn(showStorage.get(0)),
                () -> Minecraft.getInstance().screen instanceof StorageListScreen screen && screen.shownCount() > 20,
                () -> "tela " + describe(Minecraft.getInstance().screen)));
        list.add(DevEndToEnd.wait("lista do Baú", 10));
        list.add(capture("s10-bau"));
        list.add(close("fechar o Baú"));
        if (StorageKind.SOURCE_TANK.loaded()) {
            // O laboratório do Ars: a Source da jarra criativa vai sem fio para o tanque e a jarra.
            list.add(showLook("câmera no laboratório", 3.5, 3.4, -7, 9, 0.5, -13, false));
            list.add(DevEndToEnd.wait("laboratório", 20));
            list.add(capture("s11-ars"));
            list.add(showLook("perto da jarra", 9, 0.5, -14.5, 9, 1, -16, true));
            list.add(open("abrir a jarra", () -> showSourceJar.above()));
            list.add(new Step("roteador largo na jarra", STEP_TIMEOUT_MS,
                    () -> routerScreen().previewResize(Math.min(600, routerScreen().width - 8), 300),
                    () -> routerScreen().size()[0] > 400, () -> "tamanho " + java.util.Arrays.toString(routerScreen().size())));
            list.add(new Step("aba Source", STEP_TIMEOUT_MS,
                    () -> clickTab(ResourceType.SOURCE),
                    () -> routerScreen().getMenu().selectedType() == ResourceType.SOURCE
                            && routerScreen().getMenu().throughput()[ResourceType.SOURCE.ordinal()] > 0,
                    () -> "aba " + routerScreen().getMenu().selectedType()));
            list.add(DevEndToEnd.wait("aba Source", 5));
            list.add(capture("s11b-roteador-source"));
            list.add(close("fechar a jarra"));
            // Os quatro tiers do Tanque de Source, de dentro, com o jardim ao fundo.
            list.add(showLook("câmera nos tanques", 8, 0.4, -8.6, 8, 0.5, -5, false));
            list.add(DevEndToEnd.wait("tanques", 20));
            list.add(capture("s12-tanques"));
            list.add(showLook("perto dos tanques", 9, 0.5, -7.5, 9, 0.5, -5, true));
            list.add(new Step("abrir o Tanque de Source", STEP_TIMEOUT_MS, () -> useOn(showTanks.get(2)),
                    () -> Minecraft.getInstance().screen instanceof StorageScalarScreen screen && screen.getMenu().received(),
                    () -> "tela " + describe(Minecraft.getInstance().screen)));
            list.add(DevEndToEnd.wait("tela do tanque", 5));
            list.add(capture("s12b-tanque-tela"));
            list.add(close("fechar o tanque"));
        }
    }

    /** Vitrine: a cascata do Ressonador no estande do jardim, vista de dentro da casa por cima da mureta sul. */
    private static void showcaseCascadeShots(List<Step> list) {
        list.add(showLook("câmera na cascata", CASCADE_X, 3.6, CASCADE_Z - 6, CASCADE_X, 2.0, CASCADE_Z + 0.5, false));
        list.add(DevEndToEnd.wait("cascata", 20));
        list.add(capture("s21-cascata"));
    }

    /** Vitrine: a aba Redes com os convidados e o time do FTB Teams de uma rede privada. */
    private static void showcaseAccessShots(List<Step> list) {
        UUID playerId = Minecraft.getInstance().player.getUUID();
        String[] names = {"Ana", "Bruno", "Carla"};
        UUID[] guests = new UUID[names.length];
        for (int i = 0; i < names.length; i++) {
            guests[i] = UUID.nameUUIDFromBytes(names[i].getBytes());
        }
        UUID[] network = new UUID[1];
        list.add(new Step("aba Redes", STEP_TIMEOUT_MS, () -> {
            TabletScreen screen = tabletScreen();
            screen.previewTab(TabletScreen.Tab.NETWORKS);
            var networks = screen.getMenu().snapshot().networks();
            for (int i = 0; i < networks.size(); i++) {
                if (networks.get(i).manageable() && !networks.get(i).isPublic() && network[0] == null) {
                    network[0] = networks.get(i).id();
                    screen.previewNetwork(i);
                }
            }
            if (network[0] == null) {
                throw new StepFailure("nenhuma rede privada do jogador na vitrine");
            }
        }, () -> tabletScreen().access().shown(), () -> "sem a seção de acesso"));
        list.add(new Step("convidados e time", STEP_TIMEOUT_MS, () -> onServer(server -> {
            for (int i = 0; i < names.length; i++) {
                server.getProfileCache().add(new com.mojang.authlib.GameProfile(guests[i], names[i]));
            }
            var data = io.github.matheusanbs.wirelessautomate.network.NetworkSavedData.get(server);
            var net = data.network(network[0]);
            for (UUID guest : guests) {
                net = net.withMember(guest);
            }
            if (io.github.matheusanbs.wirelessautomate.network.TeamAccess.LOADED) {
                io.github.matheusanbs.wirelessautomate.compat.ftbteams.FtbTeamsDev.createParty(
                        server.getPlayerList().getPlayer(playerId), "Smelting Crew");
                for (var team : io.github.matheusanbs.wirelessautomate.network.TeamAccess.teams()) {
                    if (team.name().equals("Smelting Crew")) {
                        net = net.withTeam(team.id());
                    }
                }
            }
            data.update(net);
            return null;
        }), () -> tabletScreen().access().counts()[0] == names.length, () -> "convidados "
                + java.util.Arrays.toString(tabletScreen().access().counts())));
        list.add(DevEndToEnd.wait("acesso desenhado", 10));
        list.add(capture("s7c-tablet-acesso"));
    }

    /** Vitrine: o Tablet (lista, estatísticas e mapa) e o guia. */
    private static void showcaseTabletShots(List<Step> list) {
        // Tablet: lista, estatísticas (com o fluxo da galeria) e mapa.
        list.add(new Step("Tablet na mão (vitrine)", STEP_TIMEOUT_MS, () -> {
            UUID playerId = Minecraft.getInstance().player.getUUID();
            onServer(server -> {
                server.getPlayerList().getPlayer(playerId).setItemInHand(InteractionHand.MAIN_HAND,
                        new ItemStack(ModItems.NETWORK_TABLET.get()));
                return null;
            });
        }, () -> Minecraft.getInstance().player.getMainHandItem().is(ModItems.NETWORK_TABLET.get()), () -> "tablet"));
        list.add(new Step("abrir o Tablet", STEP_TIMEOUT_MS, () -> {
            Minecraft minecraft = Minecraft.getInstance();
            minecraft.gameMode.useItem(minecraft.player, InteractionHand.MAIN_HAND);
        }, () -> Minecraft.getInstance().screen instanceof TabletScreen screen
                && !screen.getMenu().snapshot().nodes().isEmpty(),
                () -> "tela " + describe(Minecraft.getInstance().screen)));
        list.add(new Step("Tablet maior", STEP_TIMEOUT_MS,
                () -> tabletScreen().previewResize(Math.min(560, tabletScreen().width - 8), 300),
                () -> tabletScreen().size()[0] > 400,
                () -> "tamanho " + java.util.Arrays.toString(tabletScreen().size())));
        list.add(capture("s6-tablet-lista"));
        list.add(new Step("aba Fluxos", STEP_TIMEOUT_MS,
                () -> click(widget(byMessageKey("gui.wirelessautomate.tablet.tab.flows"), "aba Fluxos")),
                () -> tabletScreen().currentTab() == TabletScreen.Tab.FLOWS,
                () -> "aba " + tabletScreen().currentTab()));
        list.add(DevEndToEnd.wait("os 5 s dos Fluxos", 140));
        list.add(capture("s6b-tablet-fluxos"));
        list.add(new Step("aba Mapa", STEP_TIMEOUT_MS,
                () -> click(widget(byMessage(Component.translatable("gui.wirelessautomate.tablet.tab.map")), "aba Mapa")),
                () -> true, () -> "mapa"));
        list.add(DevEndToEnd.wait("mapa desenhado", 10));
        list.add(capture("s7-tablet-mapa"));
        // o mapa arrastado: o botão Centralizar aparece
        list.add(new Step("arrastar o mapa", STEP_TIMEOUT_MS, () -> {
            TabletScreen screen = tabletScreen();
            int[] c = screen.mapEmptyPoint();
            screen.mouseClicked(c[0], c[1], GLFW.GLFW_MOUSE_BUTTON_LEFT);
            screen.mouseDragged(c[0] + 70, c[1] - 30, GLFW.GLFW_MOUSE_BUTTON_LEFT, 70, -30);
            screen.mouseReleased(c[0] + 70, c[1] - 30, GLFW.GLFW_MOUSE_BUTTON_LEFT);
        }, () -> tabletScreen().mapPan()[0] != 0, () -> "mapa não andou"));
        list.add(DevEndToEnd.wait("mapa arrastado", 10));
        list.add(capture("s7b-tablet-mapa-arrastado"));
        showcaseAccessShots(list);
        list.add(close("fechar o Tablet"));
        if (ModList.get().isLoaded("guideme")) {
            list.add(new Step("guia", STEP_TIMEOUT_MS,
                    () -> ClientCommandHandler.runCommand("guidemec wirelessautomate:guide open wirelessautomate:getting-started.md"),
                    () -> Minecraft.getInstance().screen != null
                            && Minecraft.getInstance().screen.getClass().getName().startsWith("guideme"),
                    () -> "tela " + describe(Minecraft.getInstance().screen)));
            // o GuideME monta a página e a cena 3D nos primeiros quadros: sem a espera, a captura pega a tela anterior
            list.add(DevEndToEnd.wait("guia desenhado", 40));
            list.add(capture("s8-guia"));
        }
    }

    /**
     * Teleporta o jogador para {@code base + (cx, cy, cz)} (o pé; x e z no centro do bloco) olhando
     * para {@code base + (tx, ty, tz)}; {@code gui} liga a interface.
     */
    static Step showLook(String name, double cx, double cy, double cz, double tx, double ty, double tz, boolean gui) {
        double[] cam = new double[3];
        float[] ang = new float[2];
        return new Step(name, STEP_TIMEOUT_MS, () -> {
            cam[0] = showBase.getX() + 0.5 + cx;
            cam[1] = showBase.getY() + cy;
            cam[2] = showBase.getZ() + 0.5 + cz;
            double ex = tx - cx, ey = ty - (cy + 1.62), ez = tz - cz;
            ang[0] = (float) Math.toDegrees(Math.atan2(-ex, ez));
            ang[1] = (float) -Math.toDegrees(Math.atan2(ey, Math.sqrt(ex * ex + ez * ez)));
            Minecraft minecraft = Minecraft.getInstance();
            minecraft.options.hideGui = !gui;
            minecraft.player.setYRot(ang[0]);
            minecraft.player.setXRot(ang[1]);
            minecraft.player.yRotO = ang[0];
            minecraft.player.xRotO = ang[1];
            UUID playerId = minecraft.player.getUUID();
            onServer(server -> {
                ServerPlayer player = server.getPlayerList().getPlayer(playerId);
                player.getAbilities().flying = true;
                player.onUpdateAbilities();
                player.teleportTo(player.serverLevel(), cam[0], cam[1], cam[2], ang[0], ang[1]);
                return null;
            });
        }, () -> Math.abs(Minecraft.getInstance().player.getXRot() - ang[1]) < 0.5f
                && Minecraft.getInstance().player.position().distanceToSqr(cam[0], cam[1], cam[2]) < 0.5,
                () -> "câmera em " + Minecraft.getInstance().player.position());
    }
}
