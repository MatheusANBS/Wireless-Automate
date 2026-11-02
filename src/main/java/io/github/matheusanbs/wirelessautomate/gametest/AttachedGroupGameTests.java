package io.github.matheusanbs.wirelessautomate.gametest;

import static io.github.matheusanbs.wirelessautomate.gametest.RangeTestSupport.CHEST;
import static io.github.matheusanbs.wirelessautomate.gametest.RangeTestSupport.chestWithRouter;
import static io.github.matheusanbs.wirelessautomate.gametest.RangeTestSupport.container;
import static io.github.matheusanbs.wirelessautomate.gametest.RangeTestSupport.count;
import static io.github.matheusanbs.wirelessautomate.gametest.RangeTestSupport.groupOf;
import static io.github.matheusanbs.wirelessautomate.gametest.RangeTestSupport.itemCard;
import static io.github.matheusanbs.wirelessautomate.gametest.RangeTestSupport.link;
import static io.github.matheusanbs.wirelessautomate.gametest.RangeTestSupport.rangeUpgrade;
import static io.github.matheusanbs.wirelessautomate.gametest.RangeTestSupport.waitLoaded;

import io.github.matheusanbs.wirelessautomate.WirelessAutomate;
import io.github.matheusanbs.wirelessautomate.block.RouterBlock;
import io.github.matheusanbs.wirelessautomate.block.RouterBlockEntity;
import io.github.matheusanbs.wirelessautomate.block.RouterTier;
import io.github.matheusanbs.wirelessautomate.menu.RangeView;
import io.github.matheusanbs.wirelessautomate.menu.RouterMenu;
import io.github.matheusanbs.wirelessautomate.menu.RouterSnapshot;
import io.github.matheusanbs.wirelessautomate.network.FaceConfig;
import io.github.matheusanbs.wirelessautomate.network.IndexRemotes;
import io.github.matheusanbs.wirelessautomate.network.NetworkSavedData;
import io.github.matheusanbs.wirelessautomate.network.NodeIndex;
import io.github.matheusanbs.wirelessautomate.network.NodeIndex.NodeKey;
import io.github.matheusanbs.wirelessautomate.network.PortMode;
import io.github.matheusanbs.wirelessautomate.network.RedstoneMode;
import io.github.matheusanbs.wirelessautomate.network.RelativeSide;
import io.github.matheusanbs.wirelessautomate.network.ResourceType;
import io.github.matheusanbs.wirelessautomate.range.EditTarget;
import io.github.matheusanbs.wirelessautomate.range.MachineGroup;
import io.github.matheusanbs.wirelessautomate.range.RemoteMachines;
import io.github.matheusanbs.wirelessautomate.range.RemoteSide;
import io.github.matheusanbs.wirelessautomate.registry.ModBlocks;
import io.github.matheusanbs.wirelessautomate.registry.ModItems;
import io.netty.buffer.Unpooled;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ChestBlock;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * A máquina presa como membro do grupo do bloco dela (spec {@code 2026-11-02-presa-no-grupo-design.md}): entrar no
 * grupo, editar sozinha ou com remotas, a remota que segue o perfil, o bloco trocado, o ar, o giro, a conversão de
 * um mundo antigo e o NBT.
 */
@GameTestHolder(WirelessAutomate.MODID)
@PrefixGameTestTemplate(false)
public final class AttachedGroupGameTests {
    /** Um segundo baú, na diagonal do preso (não junta com ele). */
    private static final BlockPos OTHER_CHEST = new BlockPos(0, 1, 0);
    private static final BlockPos BARREL_A = new BlockPos(2, 1, 0);
    private static final BlockPos BARREL_B = new BlockPos(0, 1, 2);
    private static final ResourceLocation CHEST_ID = BuiltInRegistries.BLOCK.getKey(Blocks.CHEST);

    private static FaceConfig attachedFace(RouterBlockEntity router, Direction face) {
        return router.face(EditTarget.ATTACHED_TARGET, ResourceType.ITEM, face);
    }

    /** A presa nasce na primeira escrita, no grupo do bloco; a remota do mesmo bloco entra nele e segue o perfil. */
    @GameTest(template = "empty", timeoutTicks = 100)
    public static void attachedJoinsBlockGroupAndRemoteFollows(GameTestHelper helper) {
        RouterBlockEntity router = chestWithRouter(helper, RouterTier.ADVANCED);
        helper.setBlock(OTHER_CHEST, Blocks.CHEST);
        RemoteMachines remotes = router.remoteMachines();
        helper.assertTrue(remotes.attached() == null, "a presa nasceu sem escrita");
        router.setMode(ResourceType.ITEM, Direction.NORTH, PortMode.EXTRACT);
        helper.assertTrue(remotes.attached() != null, "a escrita não criou o membro presa");
        MachineGroup group = remotes.attachedGroup();
        helper.assertValueEqual(group.block(), CHEST_ID, "bloco do grupo da presa");
        helper.assertValueEqual(group.profile().face(ResourceType.ITEM, RemoteSide.FRONT).mode(), PortMode.EXTRACT,
                "sozinha, a edição não foi para o perfil");
        helper.assertFalse(remotes.attached().hasException(), "sozinha, ganhou exceção");
        helper.startSequence()
                .thenWaitUntil(() -> waitLoaded(helper, router))
                .thenExecute(() -> {
                    router.setRangeUpgrade(rangeUpgrade());
                    link(helper, router, OTHER_CHEST);
                    int index = remotes.index(helper.absolutePos(OTHER_CHEST));
                    helper.assertValueEqual(remotes.machine(index).group(), group.id(), "a remota foi para outro grupo");
                    helper.assertFalse(remotes.machine(index).hasException(), "a remota ganhou exceção");
                    helper.assertValueEqual(router.face(EditTarget.machine(index), ResourceType.ITEM, Direction.NORTH).mode(),
                            PortMode.EXTRACT, "a remota não segue o perfil da presa");
                    helper.assertValueEqual(remotes.memberCount(group.id()), 2, "o grupo não conta a presa");
                })
                .thenSucceed();
    }

    /** Com uma remota no grupo, editar a presa vira exceção; o perfil e a remota não mudam; o Voltar tira a exceção. */
    @GameTest(template = "empty", timeoutTicks = 100)
    public static void editingAttachedWithRemoteMakesException(GameTestHelper helper) {
        RouterBlockEntity router = chestWithRouter(helper, RouterTier.ADVANCED);
        helper.setBlock(OTHER_CHEST, Blocks.CHEST);
        RemoteMachines remotes = router.remoteMachines();
        helper.startSequence()
                .thenWaitUntil(() -> waitLoaded(helper, router))
                .thenExecute(() -> {
                    router.setRangeUpgrade(rangeUpgrade());
                    router.setMode(ResourceType.ITEM, Direction.NORTH, PortMode.EXTRACT);
                    link(helper, router, OTHER_CHEST);
                    router.configureFace(EditTarget.ATTACHED_TARGET, ResourceType.ITEM, Direction.SOUTH, PortMode.INSERT,
                            0, RedstoneMode.IGNORE);
                    helper.assertTrue(remotes.attached().hasException(), "a presa não ganhou exceção");
                    MachineGroup group = remotes.attachedGroup();
                    helper.assertValueEqual(group.profile().face(ResourceType.ITEM, RemoteSide.BACK).mode(), PortMode.NONE,
                            "a edição da presa mudou o perfil");
                    int index = remotes.index(helper.absolutePos(OTHER_CHEST));
                    helper.assertValueEqual(router.face(EditTarget.machine(index), ResourceType.ITEM, Direction.SOUTH).mode(),
                            PortMode.NONE, "a edição da presa chegou à remota");
                    helper.assertValueEqual(attachedFace(router, Direction.SOUTH).mode(), PortMode.INSERT, "a presa");
                    helper.assertValueEqual(attachedFace(router, Direction.NORTH).mode(), PortMode.EXTRACT,
                            "a exceção não partiu do perfil");

                    // Editar o grupo vale para a presa sem exceção; com exceção, não.
                    router.configureFace(EditTarget.group(group.id()), ResourceType.ITEM, Direction.EAST, PortMode.INSERT, 0,
                            RedstoneMode.IGNORE);
                    // Baú com a frente em Norte: Leste no quadro canônico é a Esquerda, Leste também no mundo.
                    helper.assertValueEqual(attachedFace(router, Direction.EAST).mode(), PortMode.NONE,
                            "o grupo passou por cima da exceção");
                    helper.assertTrue(router.revertAttachedToProfile(), "o Voltar ao perfil não tirou a exceção");
                    helper.assertValueEqual(attachedFace(router, Direction.SOUTH).mode(), PortMode.NONE, "voltou com a exceção");
                    helper.assertValueEqual(attachedFace(router, Direction.EAST).mode(), PortMode.INSERT,
                            "de volta ao perfil, a presa não segue o grupo");

                    // Desvincular a última remota não leva o grupo da presa.
                    helper.assertTrue(router.unlinkMachine(index), "não desvinculou");
                    helper.assertTrue(remotes.group(group.id()) != null, "o grupo da presa saiu com a última remota");
                })
                .thenSucceed();
    }

    /** A máquina vinculada num grupo já configurado move na hora, sem exceção vazia. */
    @GameTest(template = "empty", timeoutTicks = 300)
    public static void linkedMachineInConfiguredGroupMoves(GameTestHelper helper) {
        RouterBlockEntity router = chestWithRouter(helper, RouterTier.ADVANCED);
        UUID network = NetworkSavedData.get(helper.getLevel().getServer()).create(UUID.randomUUID(), "presa-grupo").id();
        helper.setBlock(BARREL_A, Blocks.BARREL);
        helper.setBlock(BARREL_B, Blocks.BARREL);
        // O primeiro barril cheio: só a segunda vinculada tem lugar.
        Container full = container(helper, BARREL_A);
        for (int slot = 0; slot < full.getContainerSize(); slot++) {
            full.setItem(slot, new ItemStack(Items.DIRT, 64));
        }
        helper.startSequence()
                .thenWaitUntil(() -> waitLoaded(helper, router))
                .thenExecute(() -> {
                    router.setRangeUpgrade(rangeUpgrade());
                    link(helper, router, BARREL_A);
                    router.setNetworkId(network);
                    int barrels = groupOf(router, helper.absolutePos(BARREL_A));
                    router.configureFace(EditTarget.group(barrels), ResourceType.ITEM, Direction.NORTH, PortMode.INSERT, 0,
                            RedstoneMode.IGNORE);
                    router.setMode(ResourceType.ITEM, Direction.UP, PortMode.EXTRACT);
                    link(helper, router, BARREL_B);
                    container(helper, CHEST).setItem(0, new ItemStack(Items.COBBLESTONE, 64));
                })
                .thenWaitUntil(() -> helper.assertTrue(count(helper, BARREL_B) > 0, "a segunda vinculada não recebeu"))
                .thenSucceed();
    }

    /** Outro bloco na frente leva a presa para o grupo dele; o grupo antigo, configurado, fica. */
    @GameTest(template = "empty", timeoutTicks = 100)
    public static void swappedBlockMovesAttachedToNewGroup(GameTestHelper helper) {
        RouterBlockEntity router = chestWithRouter(helper, RouterTier.ADVANCED);
        RemoteMachines remotes = router.remoteMachines();
        router.setMode(ResourceType.ITEM, Direction.NORTH, PortMode.EXTRACT);
        int chests = remotes.attached().group();
        helper.setBlock(CHEST, Blocks.BARREL);
        helper.assertValueEqual(remotes.attachedGroup().block(), BuiltInRegistries.BLOCK.getKey(Blocks.BARREL),
                "a presa não foi para o grupo do barril");
        helper.assertValueEqual(attachedFace(router, Direction.NORTH).mode(), PortMode.NONE,
                "o barril herdou a configuração do baú");
        MachineGroup old = remotes.group(chests);
        helper.assertTrue(old != null && old.profile().face(ResourceType.ITEM, RemoteSide.FRONT).mode() == PortMode.EXTRACT,
                "o grupo do baú, configurado, sumiu");

        // De volta ao baú: entra no primeiro grupo do baú, que guardou a configuração. O do barril, vazio, sai.
        int barrels = remotes.attached().group();
        helper.setBlock(CHEST, Blocks.CHEST);
        helper.assertValueEqual(remotes.attached().group(), chests, "não voltou ao grupo do baú");
        helper.assertValueEqual(attachedFace(router, Direction.NORTH).mode(), PortMode.EXTRACT, "perdeu a configuração");
        helper.assertTrue(remotes.group(barrels) == null, "o grupo vazio do barril ficou");
        helper.succeed();
    }

    /** Ar na frente (máquina quebrada sem derrubar o roteador) não muda nada; a máquina de volta segue igual. */
    @GameTest(template = "empty", timeoutTicks = 100)
    public static void airKeepsAttachedGroup(GameTestHelper helper) {
        RouterBlockEntity router = chestWithRouter(helper, RouterTier.ADVANCED);
        RemoteMachines remotes = router.remoteMachines();
        router.setMode(ResourceType.ITEM, Direction.NORTH, PortMode.EXTRACT);
        int group = remotes.attached().group();
        helper.getLevel().setBlock(helper.absolutePos(CHEST), Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
        router.machineNeighborChanged();
        helper.assertTrue(helper.getBlockEntity(CHEST.above()) == router, "o roteador caiu com o ar");
        helper.assertValueEqual(remotes.attached().group(), group, "o ar tirou a presa do grupo");
        router.setMode(ResourceType.ITEM, Direction.SOUTH, PortMode.INSERT);
        helper.assertValueEqual(attachedFace(router, Direction.SOUTH).mode(), PortMode.INSERT, "a escrita no ar se perdeu");
        helper.getLevel().setBlock(helper.absolutePos(CHEST), Blocks.CHEST.defaultBlockState(), Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
        router.machineNeighborChanged();
        helper.assertValueEqual(remotes.attached().group(), group, "a máquina de volta mudou de grupo");
        helper.assertValueEqual(attachedFace(router, Direction.NORTH).mode(), PortMode.EXTRACT, "perdeu a configuração");
        helper.succeed();
    }

    /** Sem membro e com ar na frente, a escrita é ignorada e não cria grupo. */
    @GameTest(template = "empty", timeoutTicks = 100)
    public static void writeWithoutMachineIsIgnored(GameTestHelper helper) {
        RouterBlockEntity router = chestWithRouter(helper, RouterTier.ADVANCED);
        helper.getLevel().setBlock(helper.absolutePos(CHEST), Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
        router.setMode(ResourceType.ITEM, Direction.NORTH, PortMode.EXTRACT);
        helper.assertTrue(router.remoteMachines().attached() == null, "a escrita no ar criou o membro");
        helper.assertTrue(router.remoteMachines().groups().isEmpty(), "a escrita no ar criou grupo");
        helper.assertValueEqual(attachedFace(router, Direction.NORTH).mode(), PortMode.NONE, "a escrita no ar valeu");
        helper.getLevel().setBlock(helper.absolutePos(CHEST), Blocks.CHEST.defaultBlockState(), Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
        helper.succeed();
    }

    /** Girar o roteador (spin) não muda a configuração das faces absolutas nem os cartões. */
    @GameTest(template = "empty", timeoutTicks = 100)
    public static void spinKeepsAbsoluteConfig(GameTestHelper helper) {
        RouterBlockEntity router = chestWithRouter(helper, RouterTier.ADVANCED);
        router.setMode(ResourceType.ITEM, Direction.NORTH, PortMode.EXTRACT);
        router.setCard(ResourceType.ITEM, RelativeSide.fromAbsolute(router.facing(), router.spin(), Direction.EAST), 0,
                itemCard());
        for (int spin = 1; spin < RelativeSide.SPINS; spin++) {
            router.spinTo(spin);
            helper.assertValueEqual(router.spin(), spin, "não girou");
            helper.assertValueEqual(attachedFace(router, Direction.NORTH).mode(), PortMode.EXTRACT, "a face norte, giro " + spin);
            helper.assertValueEqual(router.cardCount(ResourceType.ITEM, Direction.EAST), 1, "o cartão leste, giro " + spin);
        }
        helper.succeed();
    }

    /** Salvar e carregar mantém o membro, o grupo e a exceção, sem gravar as chaves antigas. */
    @GameTest(template = "empty", timeoutTicks = 100)
    public static void saveAndLoadKeepsMember(GameTestHelper helper) {
        HolderLookup.Provider registries = helper.getLevel().registryAccess();
        RouterBlockEntity router = chestWithRouter(helper, RouterTier.ADVANCED);
        router.setMode(ResourceType.ITEM, Direction.NORTH, PortMode.EXTRACT);
        router.remoteMachines().attached().setException(router.remoteMachines().attachedGroup().profile().copy());
        CompoundTag tag = router.saveWithoutMetadata(registries);
        helper.assertFalse(tag.contains("faces") || tag.contains("cards"), "gravou as chaves antigas");
        RouterBlockEntity copy = new RouterBlockEntity(router.getBlockPos(), router.getBlockState());
        copy.loadWithComponents(tag, registries);
        RemoteMachines remotes = copy.remoteMachines();
        helper.assertTrue(remotes.attached() != null && remotes.attached().hasException(), "perdeu o membro ou a exceção");
        helper.assertValueEqual(remotes.attached().group(), router.remoteMachines().attached().group(), "outro grupo");
        helper.assertValueEqual(attachedFace(copy, Direction.NORTH).mode(), PortMode.EXTRACT, "perdeu a configuração");
        helper.succeed();
    }

    // ------------------------------------------------------------------ mundos antigos

    /** O NBT de um roteador antigo: a presa nas chaves {@code faces} e {@code cards} por lado do roteador. */
    private static CompoundTag legacy(GameTestHelper helper, RouterBlockEntity router, boolean withCard) {
        return legacy(helper, router, RelativeSide.BOTTOM, withCard);
    }

    /** Como o outro, com a face configurada (Extrai, prioridade 4) e o cartão no lado relativo {@code at}. */
    private static CompoundTag legacy(GameTestHelper helper, RouterBlockEntity router, RelativeSide at,
            boolean withCard) {
        HolderLookup.Provider registries = helper.getLevel().registryAccess();
        CompoundTag tag = router.saveWithoutMetadata(registries);
        tag.remove("attached");
        FaceConfig face = new FaceConfig();
        face.setMode(PortMode.EXTRACT);
        face.setPriority(4);
        CompoundTag item = new CompoundTag();
        item.put(at.key(), face.save(registries));
        CompoundTag faces = new CompoundTag();
        faces.put(ResourceType.ITEM.key(), item);
        tag.put("faces", faces);
        if (withCard) {
            CompoundTag slot = new CompoundTag();
            slot.putByte("slot", (byte) 0);
            ListTag list = new ListTag();
            list.add(itemCard().save(registries, slot));
            CompoundTag side = new CompoundTag();
            side.put(at.key(), list);
            CompoundTag cards = new CompoundTag();
            cards.put(ResourceType.ITEM.key(), side);
            tag.put("cards", cards);
        }
        return tag;
    }

    /** A face absoluta do lado {@code BOTTOM} do roteador antigo. */
    private static Direction bottom(RouterBlockEntity router) {
        return RelativeSide.BOTTOM.toAbsolute(router.facing(), router.spin());
    }

    private static void assertConverted(GameTestHelper helper, RouterBlockEntity router, String what) {
        helper.assertTrue(router.remoteMachines().attached() != null, what + ": não converteu");
        FaceConfig face = attachedFace(router, bottom(router));
        helper.assertTrue(face.mode() == PortMode.EXTRACT && face.priority() == 4, what + ": a face mudou de lugar");
    }

    /** Caso 4: sem grupo do bloco, um grupo novo com o rascunho como perfil e os cartões dele. */
    @GameTest(template = "empty", timeoutTicks = 100)
    public static void legacyWithoutGroupMakesGroup(GameTestHelper helper) {
        RouterBlockEntity router = chestWithRouter(helper, RouterTier.ADVANCED);
        router.loadWithComponents(legacy(helper, router, true), helper.getLevel().registryAccess());
        assertConverted(helper, router, "sem grupo");
        RemoteMachines remotes = router.remoteMachines();
        helper.assertFalse(remotes.attached().hasException(), "o rascunho virou exceção");
        helper.assertValueEqual(router.cardCount(ResourceType.ITEM, bottom(router)), 1, "o cartão não foi para o grupo");
        CompoundTag saved = router.saveWithoutMetadata(helper.getLevel().registryAccess());
        helper.assertFalse(saved.contains("faces") || saved.contains("cards"), "gravou o rascunho depois de converter");
        helper.succeed();
    }

    /** Caso 5: com grupo do bloco e o rascunho sem cartões, entra nele como exceção. */
    @GameTest(template = "empty", timeoutTicks = 100)
    public static void legacyJoinsGroupAsException(GameTestHelper helper) {
        RouterBlockEntity router = chestWithRouter(helper, RouterTier.ADVANCED);
        helper.setBlock(OTHER_CHEST, Blocks.CHEST);
        helper.startSequence()
                .thenWaitUntil(() -> waitLoaded(helper, router))
                .thenExecute(() -> {
                    link(helper, router, OTHER_CHEST);
                    int chests = groupOf(router, helper.absolutePos(OTHER_CHEST));
                    router.loadWithComponents(legacy(helper, router, false), helper.getLevel().registryAccess());
                    assertConverted(helper, router, "com grupo");
                    RemoteMachines remotes = router.remoteMachines();
                    helper.assertValueEqual(remotes.attached().group(), chests, "não entrou no grupo do baú");
                    helper.assertTrue(remotes.attached().hasException(), "a configuração diferente não virou exceção");
                })
                .thenSucceed();
    }

    /** Caso 6: com grupo do bloco e cartões diferentes, um grupo novo do mesmo bloco. */
    @GameTest(template = "empty", timeoutTicks = 100)
    public static void legacyWithOtherCardsMakesSecondGroup(GameTestHelper helper) {
        RouterBlockEntity router = chestWithRouter(helper, RouterTier.ADVANCED);
        helper.setBlock(OTHER_CHEST, Blocks.CHEST);
        helper.startSequence()
                .thenWaitUntil(() -> waitLoaded(helper, router))
                .thenExecute(() -> {
                    link(helper, router, OTHER_CHEST);
                    int chests = groupOf(router, helper.absolutePos(OTHER_CHEST));
                    router.loadWithComponents(legacy(helper, router, true), helper.getLevel().registryAccess());
                    assertConverted(helper, router, "cartões diferentes");
                    RemoteMachines remotes = router.remoteMachines();
                    helper.assertTrue(remotes.attached().group() != chests, "entrou no grupo de cartões diferentes");
                    helper.assertValueEqual(remotes.attachedGroup().block(), CHEST_ID, "o grupo novo não é do baú");
                    helper.assertFalse(remotes.attached().hasException(), "o rascunho virou exceção");
                    helper.assertValueEqual(router.cardCount(ResourceType.ITEM, bottom(router)), 1, "perdeu o cartão");
                })
                .thenSucceed();
    }

    /** Caso 5 com cartões iguais aos do grupo: entra no grupo e o cartão físico do rascunho cai no roteador. */
    @GameTest(template = "empty", timeoutTicks = 100)
    public static void legacySameCardsDropsDraftCard(GameTestHelper helper) {
        RouterBlockEntity router = chestWithRouter(helper, RouterTier.ADVANCED);
        helper.setBlock(OTHER_CHEST, Blocks.CHEST);
        helper.startSequence()
                .thenWaitUntil(() -> waitLoaded(helper, router))
                .thenExecute(() -> {
                    link(helper, router, OTHER_CHEST);
                    int chests = groupOf(router, helper.absolutePos(OTHER_CHEST));
                    // BOTTOM do roteador em cima do baú é o Norte, a frente do baú: Norte no quadro canônico.
                    router.setCard(EditTarget.group(chests), ResourceType.ITEM, Direction.NORTH, 0, itemCard());
                    router.loadWithComponents(legacy(helper, router, true), helper.getLevel().registryAccess());
                    assertConverted(helper, router, "cartões iguais");
                    helper.assertValueEqual(router.remoteMachines().attached().group(), chests, "não entrou no grupo");
                    helper.assertItemEntityPresent(ModItems.FILTER_CARD.get(), CHEST.above(), 2.0);
                })
                .thenSucceed();
    }

    /** Lado lateral, spin 1 e baú com a frente em Leste: o lado relativo vai para o lado certo da máquina. */
    @GameTest(template = "empty", timeoutTicks = 100)
    public static void legacyLateralSpunRouterConverts(GameTestHelper helper) {
        helper.setBlock(CHEST, Blocks.CHEST.defaultBlockState().setValue(ChestBlock.FACING, Direction.EAST));
        BlockPos at = CHEST.north();
        helper.setBlock(at, ModBlocks.ROUTER.get().defaultBlockState().setValue(RouterBlock.FACING, Direction.NORTH)
                .setValue(RouterBlock.SPIN, 1).setValue(RouterBlock.TIER, RouterTier.ADVANCED));
        RouterBlockEntity router = helper.getBlockEntity(at);
        router.loadWithComponents(legacy(helper, router, RelativeSide.TOP, false), helper.getLevel().registryAccess());
        // facing=north, spin 1: TOP (sul no caso facing=up) gira para oeste e a rotação x=90 o deixa em oeste.
        // Com a frente do baú em Leste, oeste é Trás.
        MachineGroup group = router.remoteMachines().attachedGroup();
        helper.assertTrue(group != null, "não converteu");
        for (RemoteSide side : RemoteSide.values()) {
            PortMode expected = side == RemoteSide.BACK ? PortMode.EXTRACT : PortMode.NONE;
            helper.assertValueEqual(group.profile().face(ResourceType.ITEM, side).mode(), expected, "lado " + side);
        }
        helper.succeed();
    }

    /** Caso 1: o rascunho vazio some, sem criar membro nem grupo. */
    @GameTest(template = "empty", timeoutTicks = 100)
    public static void legacyEmptyDraftVanishes(GameTestHelper helper) {
        RouterBlockEntity router = chestWithRouter(helper, RouterTier.ADVANCED);
        CompoundTag tag = router.saveWithoutMetadata(helper.getLevel().registryAccess());
        CompoundTag item = new CompoundTag();
        item.put(RelativeSide.BOTTOM.key(), new FaceConfig().save(helper.getLevel().registryAccess()));
        CompoundTag faces = new CompoundTag();
        faces.put(ResourceType.ITEM.key(), item);
        tag.put("faces", faces);
        router.loadWithComponents(tag, helper.getLevel().registryAccess());
        helper.assertTrue(router.remoteMachines().attached() == null, "o rascunho vazio criou o membro");
        helper.assertTrue(router.remoteMachines().groups().isEmpty(), "o rascunho vazio criou grupo");
        CompoundTag saved = router.saveWithoutMetadata(helper.getLevel().registryAccess());
        helper.assertFalse(saved.contains("faces"), "o rascunho vazio continuou salvo");
        helper.succeed();
    }

    /** Caso 3: com ar na frente o rascunho fica (lido e salvo nas chaves antigas) e converte quando a máquina volta. */
    @GameTest(template = "empty", timeoutTicks = 100)
    public static void legacyWaitsForMachine(GameTestHelper helper) {
        RouterBlockEntity router = chestWithRouter(helper, RouterTier.ADVANCED);
        helper.getLevel().setBlock(helper.absolutePos(CHEST), Blocks.AIR.defaultBlockState(),
                Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
        router.loadWithComponents(legacy(helper, router, false), helper.getLevel().registryAccess());
        helper.assertTrue(router.remoteMachines().attached() == null, "converteu com ar");
        helper.assertValueEqual(attachedFace(router, bottom(router)).mode(), PortMode.EXTRACT, "o rascunho não vale");
        CompoundTag saved = router.saveWithoutMetadata(helper.getLevel().registryAccess());
        helper.assertTrue(saved.contains("faces"), "o rascunho não ficou salvo");
        // Girar com o rascunho pendente gira o rascunho junto: a face absoluta não muda.
        Direction where = bottom(router);
        router.spinTo(1);
        helper.assertValueEqual(attachedFace(router, where).mode(), PortMode.EXTRACT, "o giro mexeu no rascunho");
        helper.getLevel().setBlock(helper.absolutePos(CHEST), Blocks.CHEST.defaultBlockState(),
                Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
        router.machineNeighborChanged();
        helper.assertTrue(router.remoteMachines().attached() != null, "não converteu com a máquina de volta");
        helper.assertValueEqual(attachedFace(router, where).mode(), PortMode.EXTRACT, "a face mudou de lugar");
        helper.succeed();
    }

    /** Caso 7: a rede antiga da aba vai para o rascunho e, convertido, para o perfil. */
    @GameTest(template = "empty", timeoutTicks = 100)
    public static void legacyTabNetworkGoesToDraft(GameTestHelper helper) {
        RouterBlockEntity router = chestWithRouter(helper, RouterTier.ADVANCED);
        UUID network = UUID.randomUUID();
        CompoundTag tag = legacy(helper, router, false);
        tag.putUUID("network", network);
        router.loadWithComponents(tag, helper.getLevel().registryAccess());
        assertConverted(helper, router, "rede antiga");
        for (Direction face : Direction.values()) {
            helper.assertTrue(network.equals(router.faceNetwork(ResourceType.ITEM, face)), "a face " + face + " sem a rede");
        }
        helper.succeed();
    }

    /** Uma escrita que não muda nada no grupo em que a presa entrou ainda cria o membro, salvo e com a porta. */
    @GameTest(template = "empty", timeoutTicks = 300)
    public static void createdMemberWithoutChangeCounts(GameTestHelper helper) {
        RouterBlockEntity router = chestWithRouter(helper, RouterTier.ADVANCED);
        UUID network = NetworkSavedData.get(helper.getLevel().getServer()).create(UUID.randomUUID(), "presa-sem-mudar").id();
        helper.setBlock(OTHER_CHEST, Blocks.CHEST);
        helper.setBlock(BARREL_A, Blocks.BARREL);
        container(helper, CHEST).setItem(0, new ItemStack(Items.COBBLESTONE, 16));
        helper.startSequence()
                .thenWaitUntil(() -> waitLoaded(helper, router))
                .thenExecute(() -> {
                    router.setRangeUpgrade(rangeUpgrade());
                    link(helper, router, OTHER_CHEST);
                    link(helper, router, BARREL_A);
                    int chests = groupOf(router, helper.absolutePos(OTHER_CHEST));
                    int barrels = groupOf(router, helper.absolutePos(BARREL_A));
                    router.configureFace(EditTarget.group(chests), ResourceType.ITEM, Direction.NORTH, PortMode.EXTRACT, 0,
                            RedstoneMode.IGNORE, network);
                    router.configureFace(EditTarget.group(barrels), ResourceType.ITEM, Direction.NORTH, PortMode.INSERT, 0,
                            RedstoneMode.IGNORE, network);
                    helper.assertTrue(router.remoteMachines().attached() == null, "a presa já tinha membro");
                    router.setMode(ResourceType.ITEM, Direction.NORTH, PortMode.EXTRACT);
                    helper.assertTrue(router.remoteMachines().attached() != null, "a escrita sem mudança não criou o membro");
                    helper.assertValueEqual(router.remoteMachines().attached().group(), chests, "outro grupo");
                    helper.assertFalse(router.remoteMachines().attached().hasException(), "exceção igual ao perfil");
                    HolderLookup.Provider registries = helper.getLevel().registryAccess();
                    RouterBlockEntity copy = new RouterBlockEntity(router.getBlockPos(), router.getBlockState());
                    copy.loadWithComponents(router.saveWithoutMetadata(registries), registries);
                    helper.assertTrue(copy.remoteMachines().attached() != null, "o membro não foi salvo");
                })
                .thenWaitUntil(() -> helper.assertValueEqual(count(helper, BARREL_A), 16, "a presa não extraiu"))
                .thenSucceed();
    }

    /** Sozinha no grupo e com exceção, editar a presa passa a exceção para o perfil e muda o perfil. */
    @GameTest(template = "empty", timeoutTicks = 100)
    public static void soleAttachedFoldsExceptionIntoProfile(GameTestHelper helper) {
        RouterBlockEntity router = chestWithRouter(helper, RouterTier.ADVANCED);
        helper.setBlock(OTHER_CHEST, Blocks.CHEST);
        RemoteMachines remotes = router.remoteMachines();
        helper.startSequence()
                .thenWaitUntil(() -> waitLoaded(helper, router))
                .thenExecute(() -> {
                    router.setRangeUpgrade(rangeUpgrade());
                    link(helper, router, OTHER_CHEST);
                    router.setMode(ResourceType.ITEM, Direction.NORTH, PortMode.EXTRACT);
                    helper.assertTrue(remotes.attached().hasException(), "com a remota, não virou exceção");
                    helper.assertTrue(router.unlinkMachine(helper.absolutePos(OTHER_CHEST)), "não desvinculou");
                    router.setMode(ResourceType.ITEM, Direction.SOUTH, PortMode.INSERT);
                    helper.assertFalse(remotes.attached().hasException(), "a exceção ficou");
                    MachineGroup group = remotes.attachedGroup();
                    helper.assertValueEqual(group.profile().face(ResourceType.ITEM, RemoteSide.FRONT).mode(), PortMode.EXTRACT,
                            "a exceção não foi para o perfil");
                    helper.assertValueEqual(group.profile().face(ResourceType.ITEM, RemoteSide.BACK).mode(), PortMode.INSERT,
                            "a edição não foi para o perfil");
                })
                .thenSucceed();
    }

    /** O slot de cartão da presa recusa o cartão sem máquina na frente (ele sumiria) e aceita com ela. */
    @GameTest(template = "empty", timeoutTicks = 100)
    public static void cardSlotRefusesWithoutMachine(GameTestHelper helper) {
        RouterBlockEntity router = chestWithRouter(helper, RouterTier.ADVANCED);
        helper.startSequence()
                .thenWaitUntil(() -> waitLoaded(helper, router))
                .thenExecute(() -> {
                    ServerPlayer[] holder = new ServerPlayer[1];
                    RouterMenu menu = RangeTestSupport.openMenu(helper, router, holder);
                    try {
                        helper.getLevel().setBlock(helper.absolutePos(CHEST), Blocks.AIR.defaultBlockState(),
                                Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
                        helper.assertFalse(router.attachedCanHold(), "a presa sem máquina guarda");
                        helper.assertFalse(menu.getSlot(0).mayPlace(itemCard()), "o slot aceitou o cartão sem máquina");
                        helper.getLevel().setBlock(helper.absolutePos(CHEST), Blocks.CHEST.defaultBlockState(),
                                Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
                        helper.assertTrue(menu.getSlot(0).mayPlace(itemCard()), "o slot recusou o cartão com a máquina");
                    } finally {
                        holder[0].containerMenu = holder[0].inventoryMenu;
                    }
                })
                .thenSucceed();
    }

    /**
     * O snapshot da tela: o grupo da presa diz que tem a presa e conta com ela, a exceção dela aparece como a de uma
     * remota (contagem, flag e as faces que diferem), e o grupo que a presa deixa fica com zero máquinas.
     */
    @GameTest(template = "empty", timeoutTicks = 100)
    public static void snapshotGroupCountsTheAttached(GameTestHelper helper) {
        RouterBlockEntity router = chestWithRouter(helper, RouterTier.ADVANCED);
        helper.setBlock(OTHER_CHEST, Blocks.CHEST);
        helper.setBlock(BARREL_B, Blocks.BARREL);
        RemoteMachines remotes = router.remoteMachines();
        helper.startSequence()
                .thenWaitUntil(() -> waitLoaded(helper, router))
                .thenExecute(() -> {
                    ServerPlayer player = TestPlayers.inLevel(helper);
                    router.setRangeUpgrade(rangeUpgrade());
                    router.setMode(ResourceType.ITEM, Direction.NORTH, PortMode.EXTRACT);
                    link(helper, router, OTHER_CHEST);
                    link(helper, router, BARREL_B);
                    int chests = remotes.attached().group();
                    int barrels = groupOf(router, helper.absolutePos(BARREL_B));

                    RangeView range = RouterSnapshot.capture(router, player).range();
                    RangeView.GroupView chestGroup = range.group(chests);
                    helper.assertTrue(chestGroup != null && chestGroup.attached() && chestGroup.count() == 2
                            && chestGroup.exceptions() == 0, "o grupo da presa: " + chestGroup);
                    RangeView.GroupView barrelGroup = range.group(barrels);
                    helper.assertTrue(barrelGroup != null && !barrelGroup.attached() && barrelGroup.count() == 1,
                            "o grupo do barril: " + barrelGroup);
                    helper.assertValueEqual(range.attachedGroup(), chests, "o grupo da presa no snapshot");
                    helper.assertFalse(range.attachedException(), "exceção sem exceção");
                    helper.assertValueEqual(range.machines().size(), 2, "a presa entrou nas remotas");

                    // Com uma remota no grupo, editar a presa vira exceção: Sul de um baú virado para o norte é Atrás.
                    router.configureFace(EditTarget.ATTACHED_TARGET, ResourceType.ITEM, Direction.SOUTH, PortMode.INSERT,
                            0, RedstoneMode.IGNORE);
                    RouterSnapshot snapshot = RouterSnapshot.capture(router, player, EditTarget.ATTACHED_TARGET);
                    range = snapshot.range();
                    helper.assertTrue(range.attachedException(), "a exceção da presa não chegou ao snapshot");
                    helper.assertValueEqual(range.group(chests).exceptions(), 1, "a exceção da presa no grupo");
                    helper.assertTrue(range.differs(ResourceType.ITEM, RemoteSide.BACK), "a face que difere");
                    helper.assertFalse(range.differs(ResourceType.ITEM, RemoteSide.FRONT), "a Frente igual ao perfil");
                    RegistryFriendlyByteBuf buf = new RegistryFriendlyByteBuf(Unpooled.buffer(),
                            helper.getLevel().registryAccess());
                    RouterSnapshot.STREAM_CODEC.encode(buf, snapshot);
                    helper.assertTrue(RouterSnapshot.STREAM_CODEC.decode(buf).equals(snapshot), "o snapshot pelo codec");

                    // Bloco trocado: a presa vai para o grupo do barril e o dos baús, configurado, fica com zero.
                    helper.assertTrue(router.unlinkMachine(helper.absolutePos(OTHER_CHEST)), "não desvinculou");
                    helper.setBlock(CHEST, Blocks.BARREL);
                    range = RouterSnapshot.capture(router, player).range();
                    chestGroup = range.group(chests);
                    helper.assertTrue(chestGroup != null && !chestGroup.attached() && chestGroup.count() == 0
                            && chestGroup.exceptions() == 0, "o grupo deixado pela presa: " + chestGroup);
                    barrelGroup = range.group(barrels);
                    helper.assertTrue(barrelGroup.attached() && barrelGroup.count() == 2 && !range.attachedException(),
                            "a presa no grupo do barril: " + barrelGroup);
                    IndexRemotes index = NodeIndex.get(helper.getLevel().getServer())
                            .entry(NodeKey.of(router.getLevel(), router.getBlockPos())).remotes();
                    int g = index.groupIndex(chests);
                    helper.assertTrue(g >= 0 && index.groupMachines(g) == 0, "o grupo deixado fora do índice");
                    helper.assertValueEqual(index.attachedGroup(), barrels, "o grupo da presa no índice");
                })
                .thenSucceed();
    }
}
