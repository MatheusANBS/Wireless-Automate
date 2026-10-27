package io.github.matheusanbs.wirelessautomate.gametest;

import io.github.matheusanbs.wirelessautomate.Config;
import io.github.matheusanbs.wirelessautomate.WirelessAutomate;
import io.github.matheusanbs.wirelessautomate.block.RouterBlock;
import io.github.matheusanbs.wirelessautomate.block.RouterBlockEntity;
import io.github.matheusanbs.wirelessautomate.item.LinkerItem;
import io.github.matheusanbs.wirelessautomate.network.ResourceType;
import io.github.matheusanbs.wirelessautomate.range.RangeLinks;
import io.github.matheusanbs.wirelessautomate.registry.ModBlocks;
import io.github.matheusanbs.wirelessautomate.registry.ModItems;
import io.github.matheusanbs.wirelessautomate.tv.TvBinding;
import io.github.matheusanbs.wirelessautomate.tv.TvBlock;
import io.github.matheusanbs.wirelessautomate.tv.TvBlockEntity;
import io.github.matheusanbs.wirelessautomate.tv.TvClockMetric;
import io.github.matheusanbs.wirelessautomate.tv.TvEditing;
import io.github.matheusanbs.wirelessautomate.tv.TvFormat;
import io.github.matheusanbs.wirelessautomate.tv.TvLayout;
import io.github.matheusanbs.wirelessautomate.tv.TvLinkRule;
import io.github.matheusanbs.wirelessautomate.tv.TvLinks;
import io.github.matheusanbs.wirelessautomate.tv.TvMetric;
import io.github.matheusanbs.wirelessautomate.tv.TvPiece;
import io.github.matheusanbs.wirelessautomate.tv.TvPieceType;
import io.github.matheusanbs.wirelessautomate.tv.TvStorageMetric;
import io.github.matheusanbs.wirelessautomate.tv.TvStyle;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * Ligar armazenamentos a uma Tela de Sinal pelo Vinculador (escolher a Tela, ligar, desligar, as recusas, a Tela em
 * outra dimensão e o NBT das ligações) e a ligação de uma peça por fonte (o NBT antigo lê como rede, o armazenamento só
 * vale se está ligado à Tela).
 */
@GameTestHolder(WirelessAutomate.MODID)
@PrefixGameTestTemplate(false)
public final class TvLinkGameTests {
    private static final BlockPos TV = new BlockPos(1, 1, 1);
    private static final BlockPos CHEST = new BlockPos(0, 1, 3);
    private static final BlockPos BATTERY = new BlockPos(2, 1, 3);
    private static final BlockPos STONE = new BlockPos(3, 1, 3);
    private static final BlockPos ROUTER = new BlockPos(3, 1, 1);

    private TvLinkGameTests() {
    }

    private static BlockState screen() {
        return ModBlocks.SIGNAL_SCREEN.get().defaultBlockState().setValue(TvBlock.FACING, Direction.NORTH);
    }

    private static TvBlockEntity screenAt(GameTestHelper helper, UUID owner) {
        helper.setBlock(TV.south(), Blocks.STONE);
        helper.setBlock(TV, screen());
        TvBlockEntity tv = helper.getBlockEntity(TV);
        tv.setOwner(owner);
        return tv;
    }

    private static void storages(GameTestHelper helper) {
        helper.setBlock(CHEST, ModBlocks.STORAGE_CHEST.get().defaultBlockState());
        helper.setBlock(BATTERY, ModBlocks.STORAGE.get(io.github.matheusanbs.wirelessautomate.storage.StorageKind.BATTERY)
                .get().defaultBlockState());
        helper.setBlock(STONE, Blocks.STONE);
    }

    private static ItemStack linker() {
        return new ItemStack(ModItems.LINKER.get());
    }

    private static String key(TestPlayers.MenuPlayer player) {
        Component bar = player.actionBar;
        return bar != null && bar.getContents() instanceof TranslatableContents contents ? contents.getKey() : "";
    }

    private static GlobalPos pos(GameTestHelper helper, BlockPos relative) {
        return GlobalPos.of(helper.getLevel().dimension(), helper.absolutePos(relative));
    }

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void escolherATelaEsqueceORoteadorEViceVersa(GameTestHelper helper) {
        TestPlayers.MenuPlayer player = TestPlayers.menuOpener(helper);
        TvBlockEntity tv = screenAt(helper, player.getUUID());
        helper.setBlock(ROUTER, ModBlocks.ROUTER.get().defaultBlockState().setValue(RouterBlock.FACING, Direction.UP));
        RouterBlockEntity router = helper.getBlockEntity(ROUTER);
        ItemStack stack = linker();
        RangeLinks.choose(player, stack, router);
        helper.assertTrue(LinkerItem.router(stack) != null && LinkerItem.tv(stack) == null, "só o roteador escolhido");
        TvLinks.choose(player, stack, tv);
        helper.assertTrue(LinkerItem.tv(stack) != null, "a Tela não ficou escolhida");
        helper.assertValueEqual(LinkerItem.tv(stack), pos(helper, TV), "a Tela escolhida");
        helper.assertTrue(LinkerItem.router(stack) == null, "escolher a Tela tinha de esquecer o roteador");
        RangeLinks.choose(player, stack, router);
        helper.assertTrue(LinkerItem.tv(stack) == null, "escolher o roteador tinha de esquecer a Tela");
        helper.assertTrue(LinkerItem.router(stack) != null, "o roteador não ficou escolhido");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void ligaEDesligaOsArmazenamentosDoMod(GameTestHelper helper) {
        TestPlayers.MenuPlayer player = TestPlayers.menuOpener(helper);
        TvBlockEntity tv = screenAt(helper, player.getUUID());
        storages(helper);
        ItemStack stack = linker();
        TvLinks.choose(player, stack, tv);
        int revision = tv.revision();
        helper.assertValueEqual(TvLinks.toggle(player, stack, helper.absolutePos(CHEST)), TvLinkRule.Action.LINK, "Baú");
        helper.assertValueEqual(TvLinks.toggle(player, stack, helper.absolutePos(BATTERY)), TvLinkRule.Action.LINK,
                "Bateria");
        helper.assertTrue(tv.hasLink(pos(helper, CHEST)) && tv.hasLink(pos(helper, BATTERY)), "as ligações");
        helper.assertValueEqual(tv.revision(), revision, "ligar não muda o que a Tela desenha");
        helper.assertValueEqual(TvLinks.toggle(player, stack, helper.absolutePos(CHEST)), TvLinkRule.Action.UNLINK,
                "o segundo clique desliga");
        helper.assertFalse(tv.hasLink(pos(helper, CHEST)), "continua ligado");
        helper.assertValueEqual(tv.links().size(), 1, "sobrou a Bateria");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void recusasNaActionBar(GameTestHelper helper) {
        TestPlayers.MenuPlayer owner = TestPlayers.menuOpener(helper);
        TestPlayers.MenuPlayer other = TestPlayers.menuOpener(helper);
        TvBlockEntity tv = screenAt(helper, owner.getUUID());
        storages(helper);
        ItemStack stack = linker();
        TvLinks.choose(owner, stack, tv);
        helper.assertValueEqual(TvLinks.toggle(owner, stack, helper.absolutePos(STONE)),
                TvLinkRule.Action.NOT_A_STORAGE, "pedra");
        helper.assertTrue(key(owner).endsWith("tv.not_a_storage"), "aviso da pedra: " + key(owner));

        ItemStack otherStack = linker();
        LinkerItem.setTv(otherStack, pos(helper, TV));
        helper.assertValueEqual(TvLinks.toggle(other, otherStack, helper.absolutePos(CHEST)),
                TvLinkRule.Action.NO_PERMISSION, "quem não edita a Tela");
        helper.assertTrue(key(other).endsWith("tv.no_permission"), "aviso da permissão: " + key(other));
        TvLinks.choose(other, linker(), tv);
        helper.assertTrue(key(other).endsWith("tv.no_permission"), "escolher sem permissão: " + key(other));
        helper.assertTrue(tv.links().isEmpty(), "uma recusa ligou");

        int max = Config.tvMaxLinks();
        for (int i = 0; i < max; i++) {
            tv.link(GlobalPos.of(Level.OVERWORLD, new BlockPos(i, -60, 0)));
        }
        helper.assertValueEqual(TvLinks.toggle(owner, stack, helper.absolutePos(CHEST)), TvLinkRule.Action.FULL, "teto");
        helper.assertTrue(key(owner).endsWith("tv.full"), "aviso do teto: " + key(owner));
        helper.assertValueEqual(tv.links().size(), max, "passou do teto");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void telaQuebradaAvisaQueSumiu(GameTestHelper helper) {
        TestPlayers.MenuPlayer player = TestPlayers.menuOpener(helper);
        storages(helper);
        ItemStack stack = linker();
        LinkerItem.setTv(stack, pos(helper, TV));
        TvLinks.toggle(player, stack, helper.absolutePos(CHEST));
        helper.assertTrue(key(player).endsWith("tv.missing"), "aviso: " + key(player));
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void telaEmOutraDimensaoLiga(GameTestHelper helper) {
        ServerLevel nether = helper.getLevel().getServer().getLevel(Level.NETHER);
        helper.assertTrue(nether != null, "o servidor de teste não tem o Nether");
        TestPlayers.MenuPlayer player = TestPlayers.menuOpener(helper);
        BlockPos far = new BlockPos(-5_000 + (helper.absolutePos(TV).getX() & 0xFF) * 16, 70, 5_000);
        nether.setBlock(far.south(), Blocks.STONE.defaultBlockState(), 3);
        nether.setBlock(far, screen(), 3);
        TvBlockEntity tv = (TvBlockEntity) nether.getBlockEntity(far);
        tv.setOwner(player.getUUID());
        storages(helper);
        ItemStack stack = linker();
        LinkerItem.setTv(stack, GlobalPos.of(Level.NETHER, far));
        helper.assertValueEqual(TvLinks.toggle(player, stack, helper.absolutePos(CHEST)), TvLinkRule.Action.LINK,
                "o Baú do Overworld na Tela do Nether");
        helper.assertTrue(tv.hasLink(pos(helper, CHEST)), "a Tela do Nether não guardou a ligação");
        helper.assertTrue(pos(helper, CHEST).dimension().equals(Level.OVERWORLD), "a ligação leva a dimensão");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void aLigacaoVoltaDoNbtEDoUpdateTag(GameTestHelper helper) {
        TestPlayers.MenuPlayer player = TestPlayers.menuOpener(helper);
        TvBlockEntity tv = screenAt(helper, player.getUUID());
        GlobalPos a = GlobalPos.of(Level.OVERWORLD, new BlockPos(1, 2, 3));
        GlobalPos b = GlobalPos.of(Level.NETHER, new BlockPos(-4, 70, 5));
        tv.link(a);
        tv.link(b);
        helper.assertFalse(tv.link(a), "ligar de novo não pode duplicar");
        var registries = helper.getLevel().registryAccess();
        TvBlockEntity saved = new TvBlockEntity(tv.getBlockPos(), screen());
        saved.loadWithComponents(tv.saveWithoutMetadata(registries), registries);
        helper.assertValueEqual(saved.links(), List.of(a, b), "as ligações salvas");
        TvBlockEntity synced = new TvBlockEntity(tv.getBlockPos(), screen());
        synced.handleUpdateTag(tv.getUpdateTag(registries), registries);
        helper.assertValueEqual(synced.links(), List.of(a, b), "as ligações no update tag");
        helper.assertTrue(synced.owner() == null, "o update tag não leva o dono");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void bindingAntigoLeComoRedeESemChavesNovas(GameTestHelper helper) {
        UUID id = UUID.randomUUID();
        CompoundTag old = new CompoundTag();
        old.put("network", NbtUtils.createUUID(id));
        old.putString("type", "energy");
        old.putString("metric", "balance");
        CompoundTag piece = new CompoundTag();
        piece.putString("type", "value");
        piece.put("bind", old);
        ListTag list = new ListTag();
        list.add(piece);
        CompoundTag root = new CompoundTag();
        root.put("pieces", list);
        TvLayout read = TvLayout.CODEC.parse(NbtOps.INSTANCE, root).getOrThrow();
        TvBinding binding = read.pieces().get(0).binding().orElseThrow();
        helper.assertValueEqual(binding, new TvBinding(id, ResourceType.ENERGY, TvMetric.BALANCE), "ligação lida");
        helper.assertValueEqual(binding.source(), TvBinding.Source.NETWORK, "a fonte antiga é a rede");
        CompoundTag back = (CompoundTag) TvBinding.CODEC.encodeStart(NbtOps.INSTANCE, binding).getOrThrow();
        helper.assertValueEqual(back.getAllKeys(), old.getAllKeys(), "a rede grava as mesmas chaves de antes");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void bindingsNovosVoltamIguaisNoLayout(GameTestHelper helper) {
        GlobalPos pos = GlobalPos.of(Level.NETHER, new BlockPos(-4, 70, 12));
        TvBinding storage = TvBinding.storage(ResourceType.ITEM, pos, TvStorageMetric.IN,
                Optional.of(net.minecraft.resources.ResourceLocation.withDefaultNamespace("diamond")));
        TvBinding fill = TvBinding.storage(ResourceType.ENERGY, pos, TvStorageMetric.FILL, Optional.empty());
        TvBinding clock = TvBinding.clock(TvClockMetric.TIME);
        TvLayout layout = TvLayout.of(List.of(piece(storage), piece(fill), piece(clock),
                piece(new TvBinding(UUID.randomUUID(), ResourceType.FLUID, TvMetric.OUT))));
        net.minecraft.nbt.Tag encoded = TvLayout.CODEC.encodeStart(NbtOps.INSTANCE, layout).getOrThrow();
        TvLayout back = TvLayout.CODEC.parse(NbtOps.INSTANCE, encoded).getOrThrow();
        helper.assertValueEqual(back, layout, "layout depois da ida e volta");
        helper.assertValueEqual(back.bindings().size(), 4, "ligações distintas");
        helper.assertTrue(back.bindings().get(2).clock() == TvClockMetric.TIME, "o relógio");
        helper.succeed();
    }

    private static TvPiece piece(TvBinding binding) {
        return new TvPiece(TvPieceType.VALUE, 0, 0, 20, 10, 0, "", Optional.of(binding), TvFormat.DEFAULT,
                TvStyle.DEFAULT);
    }

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void salvarSoMantemOArmazenamentoLigadoEORelogio(GameTestHelper helper) {
        TestPlayers.MenuPlayer player = TestPlayers.menuOpener(helper);
        TvBlockEntity tv = screenAt(helper, player.getUUID());
        storages(helper);
        GlobalPos linked = pos(helper, CHEST);
        GlobalPos notLinked = pos(helper, BATTERY);
        tv.link(linked);
        TvBinding ok = TvBinding.storage(ResourceType.ITEM, linked, TvStorageMetric.STORED, Optional.empty());
        TvBinding bad = TvBinding.storage(ResourceType.ENERGY, notLinked, TvStorageMetric.STORED, Optional.empty());
        TvLayout layout = TvLayout.of(List.of(piece(ok), piece(bad), piece(TvBinding.clock(TvClockMetric.DAY))));
        TvEditing.LayoutResult result = TvEditing.applyLayout(player, tv, layout);
        helper.assertTrue(result.ok(), "recusou");
        helper.assertValueEqual(result.dropped(), 1, "ligações que saíram");
        helper.assertTrue(tv.layout().pieces().get(0).binding().isPresent(), "o armazenamento ligado saiu");
        helper.assertTrue(tv.layout().pieces().get(1).binding().isEmpty(), "o não ligado ficou");
        helper.assertTrue(tv.layout().pieces().get(2).binding().isPresent(), "o relógio saiu");
        helper.succeed();
    }
}
