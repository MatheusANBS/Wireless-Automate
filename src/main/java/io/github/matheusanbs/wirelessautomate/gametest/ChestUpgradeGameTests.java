package io.github.matheusanbs.wirelessautomate.gametest;

import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import io.github.matheusanbs.wirelessautomate.WirelessAutomate;
import io.github.matheusanbs.wirelessautomate.block.RouterBlock;
import io.github.matheusanbs.wirelessautomate.block.RouterTier;
import io.github.matheusanbs.wirelessautomate.item.ChestUpgradeItem;
import io.github.matheusanbs.wirelessautomate.registry.ModBlocks;
import io.github.matheusanbs.wirelessautomate.registry.ModDataComponents;
import io.github.matheusanbs.wirelessautomate.registry.ModItems;
import io.github.matheusanbs.wirelessautomate.storage.StorageChestBlockEntity;
import io.github.matheusanbs.wirelessautomate.storage.upgrade.ChestUpgrades;
import io.github.matheusanbs.wirelessautomate.storage.upgrade.CompactingChain;
import io.github.matheusanbs.wirelessautomate.storage.upgrade.CompactingChains;
import io.github.matheusanbs.wirelessautomate.storage.upgrade.CompactingMode;
import io.github.matheusanbs.wirelessautomate.storage.upgrade.CompactingRules;
import io.netty.buffer.Unpooled;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * Os upgrades do Baú (Mesa de Trabalho e Compactação): os dois slots, a grade 3×3, o NBT ({@code upgrades} e
 * {@code craft_grid}), o drop e as regras da Compactação ({@link CompactingRules}). Só guardar: o efeito de cada
 * upgrade tem os testes dele.
 */
@GameTestHolder(WirelessAutomate.MODID)
@PrefixGameTestTemplate(false)
public final class ChestUpgradeGameTests {
    private static final BlockPos A = new BlockPos(1, 2, 1);
    private static final BlockPos B = new BlockPos(4, 2, 1);

    @GameTest(template = "empty", timeoutTicks = 300)
    public static void acceptsOneOfEachKind(GameTestHelper helper) {
        ChestUpgrades upgrades = chest(helper, A).upgrades();
        ItemStack crafting = new ItemStack(ModItems.CRAFTING_UPGRADE.get());
        ItemStack compacting = new ItemStack(ModItems.COMPACTING_UPGRADE.get());
        helper.assertTrue(upgrades.accepts(0, crafting), "o slot 0 recusou a Mesa");
        upgrades.setUpgrade(0, crafting);
        helper.assertFalse(upgrades.accepts(1, crafting), "o slot 1 aceitou uma segunda Mesa");
        helper.assertTrue(upgrades.accepts(1, compacting), "o slot 1 recusou a Compactação");
        helper.assertFalse(upgrades.accepts(0, new ItemStack(Items.STONE)), "o slot aceitou pedra");
        helper.assertTrue(upgrades.has(ChestUpgradeItem.Kind.CRAFTING), "a Mesa não foi achada");
        helper.assertFalse(upgrades.has(ChestUpgradeItem.Kind.COMPACTING), "a Compactação apareceu sem estar no Baú");
        helper.succeed();
    }

    /** Quebrado, o Baú leva os upgrades e a grade no próprio item; colocado de novo, eles voltam. */
    @GameTest(template = "empty", timeoutTicks = 300)
    public static void upgradesAndGridTravelInTheDrop(GameTestHelper helper) {
        ChestUpgrades upgrades = chest(helper, A).upgrades();
        upgrades.setUpgrade(0, new ItemStack(ModItems.CRAFTING_UPGRADE.get()));
        upgrades.setUpgrade(1, new ItemStack(ModItems.COMPACTING_UPGRADE.get()));
        upgrades.grid().set(4, new ItemStack(Items.IRON_INGOT));
        BlockPos absolute = helper.absolutePos(A);
        helper.getLevel().destroyBlock(absolute, true);

        List<ItemEntity> drops = helper.getLevel().getEntitiesOfClass(ItemEntity.class, new AABB(absolute).inflate(2),
                entity -> entity.getItem().is(ModBlocks.STORAGE_CHEST.get().asItem()));
        helper.assertValueEqual(drops.size(), 1, "um Baú no chão");
        ItemStack dropped = drops.get(0).getItem().copy();
        drops.get(0).discard();
        helper.assertTrue(dropped.has(ModDataComponents.STORAGE_UPGRADES.get()), "o item não leva os upgrades");
        helper.assertTrue(dropped.has(ModDataComponents.STORAGE_CRAFT_GRID.get()), "o item não leva a grade");

        place(helper, dropped, B);
        ChestUpgrades placed = ((StorageChestBlockEntity) helper.getBlockEntity(B)).upgrades();
        helper.assertTrue(placed.upgrade(0).is(ModItems.CRAFTING_UPGRADE.get()), "a Mesa não voltou");
        helper.assertTrue(placed.upgrade(1).is(ModItems.COMPACTING_UPGRADE.get()), "a Compactação não voltou");
        helper.assertTrue(placed.grid().get(4).is(Items.IRON_INGOT), "o lingote da grade não voltou");
        helper.succeed();
    }

    /** O Baú vazio, sem filtro e sem upgrades continua sem componentes (e empilha). */
    @GameTest(template = "empty", timeoutTicks = 300)
    public static void emptyChestDropsWithoutComponents(GameTestHelper helper) {
        chest(helper, A);
        BlockPos absolute = helper.absolutePos(A);
        helper.getLevel().destroyBlock(absolute, true);
        List<ItemEntity> drops = helper.getLevel().getEntitiesOfClass(ItemEntity.class, new AABB(absolute).inflate(2),
                entity -> entity.getItem().is(ModBlocks.STORAGE_CHEST.get().asItem()));
        helper.assertValueEqual(drops.size(), 1, "um Baú no chão");
        ItemStack dropped = drops.get(0).getItem();
        helper.assertFalse(dropped.has(ModDataComponents.STORAGE_UPGRADES.get()), "componente de upgrades à toa");
        helper.assertFalse(dropped.has(ModDataComponents.STORAGE_CRAFT_GRID.get()), "componente de grade à toa");
        drops.get(0).discard();
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 300)
    public static void upgradesSaveAndLoad(GameTestHelper helper) {
        StorageChestBlockEntity chest = chest(helper, A);
        helper.assertFalse(chest.saveWithoutMetadata(helper.getLevel().registryAccess()).contains("upgrades"),
                "upgrades gravado vazio");
        chest.upgrades().setUpgrade(1, new ItemStack(ModItems.COMPACTING_UPGRADE.get()));
        chest.upgrades().grid().set(0, new ItemStack(Items.GOLD_INGOT, 3));
        CompoundTag tag = chest.saveWithoutMetadata(helper.getLevel().registryAccess());
        helper.assertTrue(tag.contains("upgrades"), "upgrades não foi salvo");
        helper.assertTrue(tag.contains("craft_grid"), "craft_grid não foi salvo");

        StorageChestBlockEntity copy = new StorageChestBlockEntity(chest.getBlockPos(), chest.getBlockState());
        copy.loadWithComponents(tag, helper.getLevel().registryAccess());
        helper.assertTrue(copy.upgrades().upgrade(0).isEmpty(), "o slot 0 ganhou algo");
        helper.assertTrue(copy.upgrades().upgrade(1).is(ModItems.COMPACTING_UPGRADE.get()), "a Compactação não foi lida");
        helper.assertTrue(copy.upgrades().grid().get(0).is(Items.GOLD_INGOT) && copy.upgrades().grid().get(0).getCount() == 3,
                "a grade não foi lida");
        helper.succeed();
    }

    /** As regras: o padrão não mexe, o modo global escolhe o topo ou a base, o alvo da cadeia vence o global. */
    @GameTest(template = "empty", timeoutTicks = 300)
    public static void compactingRulesPickTheTarget(GameTestHelper helper) {
        CompactingChain chain = CompactingChains.get().chainOf(Items.IRON_INGOT);
        helper.assertTrue(chain != null, "a cadeia do ferro não existe");
        int top = chain.forms().length - 1;
        helper.assertValueEqual(CompactingRules.DEFAULT.targetLevel(chain), -1, "o padrão mexeu");
        helper.assertValueEqual(CompactingRules.DEFAULT.withMode(CompactingMode.COMPRESS).targetLevel(chain), top, "Compactar");
        helper.assertValueEqual(CompactingRules.DEFAULT.withMode(CompactingMode.DECOMPRESS).targetLevel(chain), 0, "Descompactar");

        CompactingRules own = CompactingRules.DEFAULT.withMode(CompactingMode.COMPRESS).withTarget(chain, Items.IRON_INGOT);
        helper.assertTrue(own.hasOwn(chain), "a cadeia ficou sem alvo próprio");
        helper.assertValueEqual(own.targetLevel(chain), chain.levelOf(Items.IRON_INGOT), "o alvo não venceu o global");
        CompactingRules back = own.withTarget(chain, null);
        helper.assertFalse(back.hasOwn(chain), "o alvo não saiu");
        helper.assertValueEqual(back.targetLevel(chain), top, "não voltou ao global");

        CompactingRules decoded = CompactingRules.CODEC.parse(NbtOps.INSTANCE,
                CompactingRules.CODEC.encodeStart(NbtOps.INSTANCE, own).getOrThrow()).getOrThrow();
        helper.assertValueEqual(decoded, own, "o codec não voltou igual");
        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
        CompactingRules.STREAM_CODEC.encode(buf, own);
        helper.assertValueEqual(CompactingRules.STREAM_CODEC.decode(buf), own, "o stream codec não voltou igual");
        helper.succeed();
    }

    /** Um id de mod removido é lido sem erro e ignorado: o item do upgrade não some. */
    @GameTest(template = "empty", timeoutTicks = 300)
    public static void unknownTargetIdIsIgnored(GameTestHelper helper) {
        CompactingChain chain = CompactingChains.get().chainOf(Items.IRON_INGOT);
        var json = JsonParser.parseString("{\"mode\":\"compress\",\"targets\":[\"modsumido:bloco\"]}");
        CompactingRules rules = CompactingRules.CODEC.parse(JsonOps.INSTANCE, json).getOrThrow();
        helper.assertValueEqual(rules.mode(), CompactingMode.COMPRESS, "o modo");
        helper.assertFalse(rules.hasOwn(chain), "o id sumido virou alvo");
        helper.assertValueEqual(rules.targetLevel(chain), chain.forms().length - 1, "o id sumido mudou o alvo");

        ItemStack upgrade = new ItemStack(ModItems.COMPACTING_UPGRADE.get());
        upgrade.set(ModDataComponents.COMPACTING_RULES.get(), rules);
        helper.assertValueEqual(upgrade.get(ModDataComponents.COMPACTING_RULES.get()), rules, "o componente não guardou");
        helper.succeed();
    }

    private static StorageChestBlockEntity chest(GameTestHelper helper, BlockPos pos) {
        helper.setBlock(pos, ModBlocks.STORAGE_CHEST.get().defaultBlockState().setValue(RouterBlock.TIER, RouterTier.BASIC));
        return helper.getBlockEntity(pos);
    }

    private static void place(GameTestHelper helper, ItemStack stack, BlockPos pos) {
        helper.setBlock(pos.below(), Blocks.STONE);
        BlockPos floor = helper.absolutePos(pos).below();
        BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(floor).add(0, 0.5, 0), Direction.UP, floor, false);
        ((BlockItem) stack.getItem()).place(new BlockPlaceContext(helper.getLevel(), null, InteractionHand.MAIN_HAND, stack, hit));
        helper.assertBlockPresent(ModBlocks.STORAGE_CHEST.get(), pos);
    }

    private ChestUpgradeGameTests() {
    }
}
