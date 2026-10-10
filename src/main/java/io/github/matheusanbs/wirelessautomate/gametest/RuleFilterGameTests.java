package io.github.matheusanbs.wirelessautomate.gametest;

import io.github.matheusanbs.wirelessautomate.WirelessAutomate;
import io.github.matheusanbs.wirelessautomate.block.RouterBlock;
import io.github.matheusanbs.wirelessautomate.block.RouterBlockEntity;
import io.github.matheusanbs.wirelessautomate.filter.Filter;
import io.github.matheusanbs.wirelessautomate.filter.FilterEntry;
import io.github.matheusanbs.wirelessautomate.filter.ItemRule;
import io.github.matheusanbs.wirelessautomate.filter.ItemRule.Durability;
import io.github.matheusanbs.wirelessautomate.filter.ItemRule.Enchant;
import io.github.matheusanbs.wirelessautomate.filter.ItemRule.Property;
import io.github.matheusanbs.wirelessautomate.menu.FilterMenu;
import io.github.matheusanbs.wirelessautomate.menu.RouterFaceFilterTarget;
import io.github.matheusanbs.wirelessautomate.network.NetworkManager;
import io.github.matheusanbs.wirelessautomate.network.NetworkSavedData;
import io.github.matheusanbs.wirelessautomate.network.PortMode;
import io.github.matheusanbs.wirelessautomate.network.ResourceType;
import io.github.matheusanbs.wirelessautomate.packet.FilterEntriesPayload;
import io.github.matheusanbs.wirelessautomate.packet.ModPayloads;
import io.github.matheusanbs.wirelessautomate.registry.ModItems;
import io.netty.buffer.Unpooled;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Predicate;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import io.github.matheusanbs.wirelessautomate.net.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import com.mojang.serialization.DynamicOps;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.EnchantedBookItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.Potion;
import net.minecraft.world.item.alchemy.PotionUtils;
import net.minecraft.world.item.alchemy.Potions;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.ContainerHelper;
import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentInstance;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/**
 * Regras por propriedade no filtro ({@link ItemRule}, {@link FilterEntry.RuleEntry}): cada condição
 * nos dois sentidos, encantamento com nível (no item e no livro), durabilidade, o "só em" por tag e
 * por mod, a ordem com as outras entradas e o estoque, os codecs, o pacote da tela
 * ({@link FilterEntriesPayload}) e o motor movendo só os encantados.
 */
@GameTestHolder(WirelessAutomate.MODID)
@PrefixGameTestTemplate(false)
public final class RuleFilterGameTests {
    private static final BlockPos A = new BlockPos(0, 1, 0);
    private static final BlockPos B = new BlockPos(2, 1, 2);
    private static final int CONTAINER_ID = 53;

    /**
     * Porte 1.20.1 (D6): os encantamentos são objetos do registro do Forge (não {@code ResourceKey} de um registro
     * de dados); o id vem do {@link ForgeRegistries#ENCHANTMENTS}.
     */
    private static Enchantment enchantment(GameTestHelper helper, Enchantment enchantment) {
        return enchantment;
    }

    /** O id do encantamento (o {@code Enchantments.X.location()} do 1.21). */
    private static ResourceLocation id(Enchantment enchantment) {
        return ForgeRegistries.ENCHANTMENTS.getKey(enchantment);
    }

    private static ItemStack enchanted(GameTestHelper helper, net.minecraft.world.item.Item item, Enchantment key,
            int level) {
        ItemStack stack = new ItemStack(item);
        stack.enchant(enchantment(helper, key), level);
        return stack;
    }

    private static ItemStack book(GameTestHelper helper, Enchantment key, int level) {
        return EnchantedBookItem.createForEnchantment(new EnchantmentInstance(enchantment(helper, key), level));
    }

    /** O {@code PotionContents.createItemStack} do 1.21. */
    private static ItemStack potion(net.minecraft.world.item.Item item, Potion potion) {
        return PotionUtils.setPotion(new ItemStack(item), potion);
    }

    private static ItemStack damaged(net.minecraft.world.item.Item item, int damage) {
        ItemStack stack = new ItemStack(item);
        stack.setDamageValue(damage);
        return stack;
    }

    private static ItemRule rule(Property property, boolean value) {
        return ItemRule.EMPTY.withFlag(property, value);
    }

    private static void expect(GameTestHelper helper, ItemRule rule, ItemStack stack, boolean expected, String what) {
        helper.assertTrue(rule.compile().test(stack) == expected,
                (expected ? "não pegou " : "pegou ") + what + " com " + rule);
    }

    @GameTest(template = "empty")
    public static void eachPropertyBothWays(GameTestHelper helper) {
        ItemStack pick = new ItemStack(Items.DIAMOND_PICKAXE);
        ItemStack fortunePick = enchanted(helper, Items.DIAMOND_PICKAXE, Enchantments.BLOCK_FORTUNE, 3);
        ItemStack fortuneBook = book(helper, Enchantments.BLOCK_FORTUNE, 3);
        ItemStack diamond = new ItemStack(Items.DIAMOND, 12);

        ItemRule enchantedYes = rule(Property.ENCHANTED, true);
        expect(helper, enchantedYes, fortunePick, true, "picareta encantada");
        expect(helper, enchantedYes, fortuneBook, true, "livro encantado");
        expect(helper, enchantedYes, pick, false, "picareta comum");
        expect(helper, rule(Property.ENCHANTED, false), pick, true, "picareta comum (sem encantamento)");
        expect(helper, rule(Property.ENCHANTED, false), fortunePick, false, "picareta encantada (sem encantamento)");

        expect(helper, rule(Property.DAMAGED, true), damaged(Items.DIAMOND_PICKAXE, 10), true, "picareta gasta");
        expect(helper, rule(Property.DAMAGED, true), pick, false, "picareta nova");
        expect(helper, rule(Property.DAMAGED, false), diamond, true, "diamante (não gasta = intacto)");

        ItemStack named = new ItemStack(Items.DIAMOND);
        named.setHoverName(Component.literal("Joia"));
        expect(helper, rule(Property.NAMED, true), named, true, "diamante renomeado");
        expect(helper, rule(Property.NAMED, true), diamond, false, "diamante comum");

        expect(helper, rule(Property.POTION, true), potion(Items.POTION, Potions.HEALING), true,
                "poção de cura");
        expect(helper, rule(Property.POTION, true), potion(Items.POTION, Potions.WATER), false,
                "garrafa de água");
        expect(helper, rule(Property.POTION, true), potion(Items.TIPPED_ARROW, Potions.POISON), true,
                "flecha com efeito");

        expect(helper, rule(Property.STACKABLE, true), diamond, true, "diamante");
        expect(helper, rule(Property.STACKABLE, true), pick, false, "picareta");
        expect(helper, rule(Property.STACKABLE, false), pick, true, "picareta (não empilhável)");

        ItemStack fullBox = new ItemStack(Items.SHULKER_BOX);
        // Porte 1.20.1: o conteúdo fica no NBT (o BlockEntityTag da caixa e a lista Items do bundle).
        BlockItem.setBlockEntityData(fullBox, BlockEntityType.SHULKER_BOX,
                ContainerHelper.saveAllItems(new CompoundTag(), NonNullList.of(ItemStack.EMPTY, new ItemStack(Items.DIRT, 3))));
        ItemStack bundle = new ItemStack(Items.BUNDLE);
        ListTag bundleItems = new ListTag();
        bundleItems.add(new ItemStack(Items.DIRT).save(new CompoundTag()));
        bundle.getOrCreateTag().put("Items", bundleItems);
        expect(helper, rule(Property.CONTENTS, true), fullBox, true, "caixa de shulker com terra");
        expect(helper, rule(Property.CONTENTS, true), bundle, true, "bundle com terra");
        expect(helper, rule(Property.CONTENTS, true), new ItemStack(Items.SHULKER_BOX), false, "caixa de shulker vazia");
        expect(helper, rule(Property.CONTENTS, false), new ItemStack(Items.SHULKER_BOX), true, "caixa vazia (vazio)");

        // várias condições: todas precisam bater
        ItemRule both = enchantedYes.withFlag(Property.DAMAGED, true);
        ItemStack worn = enchanted(helper, Items.DIAMOND_PICKAXE, Enchantments.BLOCK_FORTUNE, 1);
        worn.setDamageValue(50);
        expect(helper, both, worn, true, "encantada e gasta");
        expect(helper, both, fortunePick, false, "encantada e nova");
        expect(helper, both, damaged(Items.DIAMOND_PICKAXE, 50), false, "gasta e sem encantamento");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void enchantmentDurabilityAndScope(GameTestHelper helper) {
        ItemRule fortune2 = ItemRule.EMPTY.withEnchantment(Optional.of(new Enchant(id(Enchantments.BLOCK_FORTUNE), 2)));
        expect(helper, fortune2, enchanted(helper, Items.DIAMOND_PICKAXE, Enchantments.BLOCK_FORTUNE, 3), true, "Fortuna III");
        expect(helper, fortune2, enchanted(helper, Items.DIAMOND_PICKAXE, Enchantments.BLOCK_FORTUNE, 1), false, "Fortuna I");
        expect(helper, fortune2, book(helper, Enchantments.BLOCK_FORTUNE, 2), true, "livro de Fortuna II");
        expect(helper, fortune2, enchanted(helper, Items.DIAMOND_PICKAXE, Enchantments.SILK_TOUCH, 1), false,
                "Toque de seda");

        // diamante: 1561 de durabilidade
        ItemRule below50 = ItemRule.EMPTY.withDurability(Optional.of(new Durability(false, 50)));
        expect(helper, below50, damaged(Items.DIAMOND_PICKAXE, 1000), true, "36% restante");
        expect(helper, below50, damaged(Items.DIAMOND_PICKAXE, 100), false, "94% restante");
        expect(helper, below50, new ItemStack(Items.DIAMOND), false, "item que não gasta");
        ItemRule atLeast90 = ItemRule.EMPTY.withDurability(Optional.of(new Durability(true, 90)));
        expect(helper, atLeast90, new ItemStack(Items.DIAMOND_PICKAXE), true, "picareta nova");
        expect(helper, atLeast90, damaged(Items.DIAMOND_PICKAXE, 1000), false, "picareta gasta");

        ItemRule pickaxes = rule(Property.ENCHANTED, true).withScope("#minecraft:pickaxes");
        expect(helper, pickaxes, enchanted(helper, Items.DIAMOND_PICKAXE, Enchantments.BLOCK_FORTUNE, 1), true, "picareta encantada");
        expect(helper, pickaxes, enchanted(helper, Items.DIAMOND_SWORD, Enchantments.SHARPNESS, 1), false, "espada encantada");
        expect(helper, pickaxes, book(helper, Enchantments.BLOCK_FORTUNE, 3), false, "livro encantado");

        ItemStack linker = new ItemStack(ModItems.LINKER.get());
        linker.setHoverName(Component.literal("Meu"));
        ItemStack namedDiamond = new ItemStack(Items.DIAMOND);
        namedDiamond.setHoverName(Component.literal("Meu"));
        ItemRule ours = rule(Property.NAMED, true).withScope("@" + WirelessAutomate.MODID);
        expect(helper, ours, linker, true, "Vinculador renomeado");
        expect(helper, ours, namedDiamond, false, "diamante renomeado");

        // "só em" mal escrito: a regra não vale e nunca pega nada
        ItemRule bad = rule(Property.STACKABLE, true).withScope("#");
        helper.assertFalse(bad.isValid(), "aceitou \"#\" como só em");
        expect(helper, bad, new ItemStack(Items.DIAMOND), false, "diamante com só em inválido");
        helper.assertFalse(ItemRule.EMPTY.isValid(), "regra vazia valeu");
        helper.assertTrue(ItemRule.EMPTY.withScope("@minecraft").isValid(), "só em sozinho deveria valer");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void rulesInFilterOrderAndStock(GameTestHelper helper) {
        ItemStack fortunePick = enchanted(helper, Items.DIAMOND_PICKAXE, Enchantments.BLOCK_FORTUNE, 3);
        FilterEntry diamond = new FilterEntry.ItemEntry(new ItemStack(Items.DIAMOND), 5);
        FilterEntry stackable = new FilterEntry.RuleEntry(rule(Property.STACKABLE, true), 64);
        Filter white = new Filter(Filter.ListMode.WHITELIST, false, List.of(diamond, stackable));
        helper.assertTrue(white.testItem(new ItemStack(Items.DIRT)) && !white.testItem(fortunePick), "lista branca");
        // a primeira entrada que casa decide o estoque
        GameTestCompat.assertValueEqual(helper, white.itemStock(new ItemStack(Items.DIAMOND)), 5L, "estoque do diamante");
        GameTestCompat.assertValueEqual(helper, white.itemStock(new ItemStack(Items.DIRT)), 64L, "estoque pela regra");
        helper.assertTrue(white.usesItemStock(), "estoque da regra não contou");

        Filter black = new Filter(Filter.ListMode.BLACKLIST, false,
                List.of(new FilterEntry.RuleEntry(rule(Property.ENCHANTED, true), 0)));
        helper.assertTrue(!black.testItem(fortunePick) && black.testItem(new ItemStack(Items.DIAMOND_PICKAXE)),
                "lista negra de encantados");
        // regra só vale para itens: num filtro de fluidos não casa com nada
        helper.assertFalse(white.testFluid(new FluidStack(Fluids.WATER, 1000)), "regra casou com fluido");

        // duplicada (mesma regra) é ignorada, outra regra entra
        Filter more = white.withEntry(new FilterEntry.RuleEntry(rule(Property.STACKABLE, true), 0))
                .withEntry(new FilterEntry.RuleEntry(rule(Property.STACKABLE, false), 0));
        GameTestCompat.assertValueEqual(helper, more.entries().size(), 3, "entradas depois de duplicada e nova");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void ruleCodecs(GameTestHelper helper) {
        ItemRule rule = ItemRule.EMPTY.withFlag(Property.ENCHANTED, true).withFlag(Property.STACKABLE, false)
                .withEnchantment(Optional.of(new Enchant(id(Enchantments.MENDING), 1)))
                .withDurability(Optional.of(new Durability(false, 25))).withScope("#c:tools");
        Filter filter = new Filter(Filter.ListMode.WHITELIST, true, List.of(
                new FilterEntry.RuleEntry(rule, 7), new FilterEntry.TagEntry(new ResourceLocation("c:ingots"), 0)));
        DynamicOps<Tag> ops = NbtOps.INSTANCE;
        Tag saved = Filter.CODEC.encodeStart(ops, filter).getOrThrow(false, error -> {});
        GameTestCompat.assertValueEqual(helper, Filter.CODEC.parse(ops, saved).getOrThrow(false, error -> {}), filter, "filtro salvo e lido");

        RegistryFriendlyByteBuf buf = new RegistryFriendlyByteBuf(Unpooled.buffer());
        try {
            FilterEntriesPayload payload = FilterEntriesPayload.add(CONTAINER_ID, filter.entries());
            FilterEntriesPayload.STREAM_CODEC.encode(buf, payload);
            GameTestCompat.assertValueEqual(helper, FilterEntriesPayload.STREAM_CODEC.decode(buf), payload, "pacote decodificado");
        } finally {
            buf.release();
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void entriesPayloadValidates(GameTestHelper helper) {
        helper.setBlock(A, Blocks.CHEST);
        helper.setBlock(A.above(), io.github.matheusanbs.wirelessautomate.registry.ModBlocks.ROUTER.get()
                .defaultBlockState().setValue(RouterBlock.FACING, Direction.UP));
        RouterBlockEntity router = GameTestCompat.getBlockEntity(helper, A.above());
        @SuppressWarnings("removal")
        ServerPlayer player = GameTestCompat.makeMockServerPlayerInLevel(helper);
        player.moveTo(Vec3.atCenterOf(router.getBlockPos().above()));
        try {
            FilterMenu items = open(player, router, ResourceType.ITEM);
            FilterEntry ingots = new FilterEntry.TagEntry(new ResourceLocation("c:ingots"), 99);
            FilterEntry logs = new FilterEntry.TagEntry(new ResourceLocation("logs"), 0);
            FilterEntry enchantedRule = new FilterEntry.RuleEntry(rule(Property.ENCHANTED, true), 0);
            helper.assertTrue(send(player, -1, List.of(ingots, logs, enchantedRule, new FilterEntry.ModEntry("minecraft", 0))),
                    "recusou tags, regra e mod");
            Filter filter = router.face(ResourceType.ITEM, Direction.UP).filter();
            GameTestCompat.assertValueEqual(helper, filter.entries().size(), 4, "entradas");
            GameTestCompat.assertValueEqual(helper, filter.entries().get(0).stock(), 0L, "estoque vindo do cliente não zerou");
            helper.assertTrue(items.pollView() != null, "a visão não acompanhou");

            // duplicadas não entram de novo
            helper.assertTrue(send(player, -1, List.of(logs)), "recusou duplicada");
            GameTestCompat.assertValueEqual(helper, router.face(ResourceType.ITEM, Direction.UP).filter().entries().size(), 4, "duplicada entrou");

            // editar a regra: troca no lugar e mantém o estoque
            router.setFilter(ResourceType.ITEM, Direction.UP, router.face(ResourceType.ITEM, Direction.UP).filter().withStock(2, 32));
            FilterEntry damagedRule = new FilterEntry.RuleEntry(rule(Property.DAMAGED, true), 0);
            helper.assertTrue(send(player, 2, List.of(damagedRule)), "recusou a edição");
            FilterEntry edited = router.face(ResourceType.ITEM, Direction.UP).filter().entries().get(2);
            helper.assertTrue(edited instanceof FilterEntry.RuleEntry r && Boolean.TRUE.equals(r.rule().flag(Property.DAMAGED))
                    && edited.stock() == 32, "edição: " + edited);

            // recusas: regra vazia ou com só em inválido, índice fora, lista vazia, item exato por aqui
            helper.assertFalse(send(player, -1, List.of(new FilterEntry.RuleEntry(ItemRule.EMPTY, 0))), "aceitou regra vazia");
            helper.assertFalse(send(player, -1, List.of(new FilterEntry.RuleEntry(rule(Property.NAMED, true).withScope("@@"), 0))),
                    "aceitou só em inválido");
            helper.assertFalse(send(player, 9, List.of(damagedRule)), "aceitou índice fora");
            helper.assertFalse(send(player, -1, List.of()), "aceitou lista vazia");
            helper.assertFalse(send(player, -1, List.of(new FilterEntry.ItemEntry(new ItemStack(Items.STONE), 0))),
                    "aceitou item exato pelo pacote de regras");
            helper.assertFalse(send(player, -1, List.of(logs, new FilterEntry.RuleEntry(ItemRule.EMPTY, 0))),
                    "aceitou pacote com uma entrada inválida");

            // fluidos: tag sim, regra não
            open(player, router, ResourceType.FLUID);
            helper.assertTrue(send(player, -1, List.of(new FilterEntry.TagEntry(new ResourceLocation("water"), 0))),
                    "recusou tag de fluido");
            helper.assertFalse(send(player, -1, List.of(enchantedRule)), "aceitou regra num filtro de fluidos");
            GameTestCompat.assertValueEqual(helper, router.face(ResourceType.FLUID, Direction.UP).filter().entries().size(), 1, "fluidos");
        } finally {
            player.containerMenu = player.inventoryMenu;
        }
        helper.succeed();
    }

    private static FilterMenu open(ServerPlayer player, RouterBlockEntity router, ResourceType type) {
        RouterFaceFilterTarget target = new RouterFaceFilterTarget(router, type, Direction.UP);
        FilterMenu menu = new FilterMenu(CONTAINER_ID, player.getInventory(), target, target.view(player));
        player.containerMenu = menu;
        return menu;
    }

    private static boolean send(ServerPlayer player, int replace, List<FilterEntry> entries) {
        return ModPayloads.handleFilterEntries(player, new FilterEntriesPayload(CONTAINER_ID, replace, entries));
    }

    /** O motor leva só os encantados: regra "Encantado" na origem. */
    @GameTest(template = "empty")
    public static void engineMovesOnlyEnchanted(GameTestHelper helper) {
        UUID network = NetworkSavedData.get(helper.getLevel().getServer()).create(UUID.randomUUID(), "regra-encantados").id();
        RouterBlockEntity source = chest(helper, A, network, PortMode.EXTRACT,
                new Filter(Filter.ListMode.WHITELIST, false, List.of(new FilterEntry.RuleEntry(rule(Property.ENCHANTED, true), 0))));
        RouterBlockEntity target = chest(helper, B, network, PortMode.INSERT, Filter.EMPTY);
        ChestBlockEntity from = GameTestCompat.getBlockEntity(helper, A);
        from.setItem(0, new ItemStack(Items.DIAMOND_PICKAXE));
        from.setItem(1, enchanted(helper, Items.DIAMOND_PICKAXE, Enchantments.BLOCK_EFFICIENCY, 5));
        from.setItem(2, new ItemStack(Items.COBBLESTONE, 32));
        from.setItem(3, book(helper, Enchantments.MENDING, 1));
        Predicate<ItemStack> isEnchanted = stack -> ItemRule.has(stack, Property.ENCHANTED);

        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(NetworkManager.get().contains(source) && NetworkManager.get().contains(target),
                        "roteadores não registrados"))
                .thenWaitUntil(() -> GameTestCompat.assertValueEqual(helper, count(helper, B, isEnchanted), 2, "encantados no destino"))
                .thenIdle(10)
                .thenExecute(() -> {
                    GameTestCompat.assertValueEqual(helper, count(helper, B, isEnchanted.negate()), 0, "comuns no destino");
                    GameTestCompat.assertValueEqual(helper, count(helper, A, isEnchanted.negate()), 33, "comuns na origem");
                })
                .thenSucceed();
    }

    private static RouterBlockEntity chest(GameTestHelper helper, BlockPos pos, UUID network, PortMode mode, Filter filter) {
        helper.setBlock(pos, Blocks.CHEST);
        helper.setBlock(pos.above(), io.github.matheusanbs.wirelessautomate.registry.ModBlocks.ROUTER.get()
                .defaultBlockState().setValue(RouterBlock.FACING, Direction.UP));
        RouterBlockEntity router = GameTestCompat.getBlockEntity(helper, pos.above());
        router.setNetworkId(network);
        router.setMode(ResourceType.ITEM, Direction.UP, mode);
        router.setFilter(ResourceType.ITEM, Direction.UP, filter);
        return router;
    }

    private static int count(GameTestHelper helper, BlockPos pos, Predicate<ItemStack> which) {
        ChestBlockEntity chest = GameTestCompat.getBlockEntity(helper, pos);
        int total = 0;
        for (int i = 0; i < chest.getContainerSize(); i++) {
            ItemStack stack = chest.getItem(i);
            if (!stack.isEmpty() && which.test(stack)) {
                total += stack.getCount();
            }
        }
        return total;
    }
}
