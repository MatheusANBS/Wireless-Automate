package io.github.matheusanbs.wirelessautomate.gametest;

import io.github.matheusanbs.wirelessautomate.Config;
import io.github.matheusanbs.wirelessautomate.WirelessAutomate;
import io.github.matheusanbs.wirelessautomate.block.RouterBlock;
import io.github.matheusanbs.wirelessautomate.block.RouterBlockEntity;
import io.github.matheusanbs.wirelessautomate.block.RouterTier;
import io.github.matheusanbs.wirelessautomate.menu.RouterSnapshot;
import io.github.matheusanbs.wirelessautomate.network.NetworkSavedData;
import io.github.matheusanbs.wirelessautomate.network.WaNetwork;
import io.github.matheusanbs.wirelessautomate.preset.PresetPaste;
import io.github.matheusanbs.wirelessautomate.registry.ModBlocks;
import java.util.UUID;
import java.util.function.Predicate;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.StringTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * O acesso às redes privadas: dono, operador, convidado e estranho nas três regras ({@code canManage},
 * {@code canEdit}, {@code canUse}), o NBT dos convidados e dos times, os limites da config e o {@code canLeave}.
 */
@GameTestHolder(WirelessAutomate.MODID)
@PrefixGameTestTemplate(false)
public final class NetworkAccessGameTests {
    private static final UUID OWNER = UUID.randomUUID();
    private static final UUID GUEST = UUID.randomUUID();
    private static final UUID STRANGER = UUID.randomUUID();

    private static WaNetwork privateWithGuest() {
        return new WaNetwork(UUID.randomUUID(), "acesso", 0x336699, OWNER).withMember(GUEST);
    }

    @GameTest(template = "empty")
    public static void guestUsesAndEditsButDoesNotManage(GameTestHelper helper) {
        WaNetwork network = privateWithGuest();
        helper.assertTrue(network.canUse(GUEST, false), "o convidado não usa");
        helper.assertTrue(network.canEdit(GUEST, false), "o convidado não edita");
        helper.assertFalse(network.canManage(GUEST, false), "o convidado gerencia");
        helper.assertFalse(network.canUse(STRANGER, false), "o estranho usa a rede privada");
        helper.assertFalse(network.canEdit(STRANGER, false), "o estranho edita");
        helper.assertFalse(network.canManage(STRANGER, false), "o estranho gerencia");
        for (boolean operator : new boolean[] {false, true}) {
            helper.assertTrue(network.canManage(operator ? STRANGER : OWNER, operator), "gerenciar: dono ou operador");
            helper.assertTrue(network.canEdit(operator ? STRANGER : OWNER, operator), "editar: dono ou operador");
            helper.assertTrue(network.canUse(operator ? STRANGER : OWNER, operator), "usar: dono ou operador");
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void publicNetworkIsUsedButNotEditedByAStranger(GameTestHelper helper) {
        WaNetwork network = new WaNetwork(UUID.randomUUID(), "aberta", 0x336699, OWNER).withPublic(true);
        helper.assertTrue(network.canUse(STRANGER, false), "o estranho não usa a rede pública");
        helper.assertFalse(network.canEdit(STRANGER, false), "o estranho edita a rede pública");
        helper.assertFalse(network.canManage(STRANGER, false), "o estranho gerencia a rede pública");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void withMethodsAreIdempotentAndKeepTheRest(GameTestHelper helper) {
        WaNetwork network = privateWithGuest().withPublic(true).withName("novo").withColor(0x123456);
        UUID team = UUID.randomUUID();
        helper.assertTrue(network.withMember(GUEST) == network, "convidar de novo trocou a rede");
        helper.assertTrue(network.withoutMember(STRANGER) == network, "tirar um desconhecido trocou a rede");
        WaNetwork withTeam = network.withTeam(team);
        helper.assertTrue(withTeam.withTeam(team) == withTeam, "adicionar o time de novo trocou a rede");
        helper.assertTrue(withTeam.members().equals(network.members()) && withTeam.isPublic()
                && withTeam.name().equals("novo") && withTeam.color() == 0x123456, "o with perdeu um campo");
        WaNetwork cleaned = withTeam.withoutMember(GUEST).withoutTeam(team);
        helper.assertTrue(cleaned.members().isEmpty() && cleaned.teams().isEmpty(), "não tirou da lista");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void accessSurvivesTheNbtRoundTrip(GameTestHelper helper) {
        UUID team = UUID.randomUUID();
        WaNetwork network = privateWithGuest().withMember(STRANGER).withTeam(team);
        WaNetwork loaded = WaNetwork.load(network.save());
        helper.assertTrue(loaded.equals(network), "a rede mudou ao salvar e carregar");
        helper.assertTrue(loaded.members().equals(java.util.List.of(GUEST, STRANGER)), "ordem dos convidados");
        helper.assertTrue(loaded.teams().equals(java.util.List.of(team)), "times");

        CompoundTag empty = new WaNetwork(UUID.randomUUID(), "vazia", 1, OWNER).save();
        helper.assertFalse(empty.contains("members"), "gravou convidados vazios");
        helper.assertFalse(empty.contains("teams"), "gravou times vazios");
        helper.assertTrue(WaNetwork.load(empty).members().isEmpty(), "mundo antigo carregou convidados");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void loadIgnoresBrokenAndRepeatedEntries(GameTestHelper helper) {
        CompoundTag tag = new WaNetwork(UUID.randomUUID(), "editada", 1, OWNER).save();
        ListTag members = new ListTag();
        members.add(NbtUtils.createUUID(GUEST));
        members.add(NbtUtils.createUUID(GUEST));
        tag.put("members", members);
        WaNetwork loaded = WaNetwork.load(tag);
        helper.assertTrue(loaded.members().equals(java.util.List.of(GUEST)), "repetido entrou duas vezes");

        ListTag wrong = new ListTag();
        wrong.add(StringTag.valueOf("lixo"));
        tag.put("members", wrong);
        helper.assertTrue(WaNetwork.load(tag).members().isEmpty(), "entrada que não é UUID entrou");
        tag.putString("teams", "não é uma lista");
        helper.assertTrue(WaNetwork.load(tag).teams().isEmpty(), "times corrompidos derrubaram a carga");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void canLeaveNeedsEditNotJustUse(GameTestHelper helper) {
        NetworkSavedData data = NetworkSavedData.get(helper.getLevel().getServer());
        ServerPlayer player = TestPlayers.inLevel(helper);
        WaNetwork guest = data.create(UUID.randomUUID(), "teste-acesso-convidado").withMember(player.getUUID());
        data.update(guest);
        WaNetwork foreign = data.create(UUID.randomUUID(), "teste-acesso-alheia");
        WaNetwork foreignPublic = data.create(UUID.randomUUID(), "teste-acesso-publica").withPublic(true);
        data.update(foreignPublic);
        try {
            Predicate<UUID> canLeave = PresetPaste.canLeave(player);
            helper.assertTrue(canLeave.test(guest.id()), "o convidado não pode tirar a face da rede dele");
            helper.assertFalse(canLeave.test(foreign.id()), "tirou a face de uma rede privada alheia");
            helper.assertFalse(canLeave.test(foreignPublic.id()), "tirou a face de uma rede pública alheia");
            helper.assertTrue(canLeave.test(UUID.randomUUID()), "uma rede apagada prendeu a face");
            helper.succeed();
        } finally {
            data.remove(guest.id());
            data.remove(foreign.id());
            data.remove(foreignPublic.id());
        }
    }

    @GameTest(template = "empty", timeoutTicks = 300)
    public static void routerScreenListsTheNetworksTheGuestCanUse(GameTestHelper helper) {
        BlockPos machine = new BlockPos(1, 1, 1);
        helper.setBlock(machine, Blocks.CHEST);
        helper.setBlock(machine.above(), ModBlocks.ROUTER.get().defaultBlockState()
                .setValue(RouterBlock.FACING, Direction.UP).setValue(RouterBlock.TIER, RouterTier.BASIC));
        RouterBlockEntity router = helper.getBlockEntity(machine.above());
        NetworkSavedData data = NetworkSavedData.get(helper.getLevel().getServer());
        ServerPlayer player = TestPlayers.inLevel(helper);
        WaNetwork guest = data.create(UUID.randomUUID(), "teste-acesso-lista-a").withMember(player.getUUID());
        data.update(guest);
        WaNetwork foreign = data.create(UUID.randomUUID(), "teste-acesso-lista-b");
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(router.getLevel() != null, "roteador sem mundo"))
                .thenExecute(() -> {
                    try {
                        RouterSnapshot snapshot = RouterSnapshot.capture(router, player);
                        helper.assertTrue(snapshot.networks().stream().anyMatch(n -> n.id().equals(guest.id())),
                                "a rede em que o jogador foi convidado não aparece");
                        helper.assertFalse(snapshot.networks().stream().anyMatch(n -> n.id().equals(foreign.id())),
                                "a rede privada alheia apareceu");
                    } finally {
                        data.remove(guest.id());
                        data.remove(foreign.id());
                    }
                })
                .thenSucceed();
    }

    @GameTest(template = "empty", batch = "wirelessautomate_config_access")
    public static void memberAndTeamLimitsFollowTheConfig(GameTestHelper helper) {
        NetworkSavedData data = NetworkSavedData.get(helper.getLevel().getServer());
        ServerPlayer player = TestPlayers.inLevel(helper);
        int membersBefore = Config.MAX_MEMBERS_PER_NETWORK.get();
        int teamsBefore = Config.MAX_TEAMS_PER_NETWORK.get();
        try {
            Config.MAX_MEMBERS_PER_NETWORK.set(2);
            Config.MAX_TEAMS_PER_NETWORK.set(1);
            WaNetwork network = new WaNetwork(UUID.randomUUID(), "limite", 1, player.getUUID());
            helper.assertFalse(data.memberLimitReached(player, network), "limite com zero convidados");
            network = network.withMember(UUID.randomUUID()).withMember(UUID.randomUUID());
            helper.assertTrue(data.memberLimitReached(player, network), "passou do limite de convidados");
            helper.assertFalse(data.teamLimitReached(player, network), "limite de times com zero times");
            network = network.withTeam(UUID.randomUUID());
            helper.assertTrue(data.teamLimitReached(player, network), "passou do limite de times");
            Config.MAX_MEMBERS_PER_NETWORK.set(0);
            Config.MAX_TEAMS_PER_NETWORK.set(0);
            helper.assertFalse(data.memberLimitReached(player, network), "0 deveria ser sem limite (convidados)");
            helper.assertFalse(data.teamLimitReached(player, network), "0 deveria ser sem limite (times)");
            helper.succeed();
        } finally {
            Config.MAX_MEMBERS_PER_NETWORK.set(membersBefore);
            Config.MAX_TEAMS_PER_NETWORK.set(teamsBefore);
        }
    }
}
