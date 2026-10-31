package io.github.matheusanbs.wirelessautomate.gametest;

import io.github.matheusanbs.wirelessautomate.WirelessAutomate;
import io.github.matheusanbs.wirelessautomate.item.GuideBook;
import io.github.matheusanbs.wirelessautomate.registry.ModItems;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** O livro-guia próprio: a receita e a entrega no primeiro login. */
@GameTestHolder(WirelessAutomate.MODID)
@PrefixGameTestTemplate(false)
public final class GuideGameTests {
    private GuideGameTests() {
    }

    /** Quantos livros o jogador tem no inventário. */
    private static int books(ServerPlayer player) {
        return player.getInventory().countItem(ModItems.GUIDE.get());
    }

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void recipeGivesTheBook(GameTestHelper helper) {
        var holder = helper.getLevel().getRecipeManager().byKey(WirelessAutomate.id("guide"))
                .orElseThrow(() -> new GameTestAssertException("a receita do guia deve existir"));
        helper.assertTrue(holder.value().getResultItem(helper.getLevel().registryAccess()).is(ModItems.GUIDE.get()),
                "a receita dá o livro do mod");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void newPlayerGetsOneBook(GameTestHelper helper) {
        ServerPlayer player = TestPlayers.inLevel(helper);
        helper.assertTrue(!GuideBook.given(player), "jogador novo sem a marca");
        helper.assertTrue(GuideBook.deliver(player), "a primeira entrega acontece");
        helper.assertValueEqual(books(player), 1, "um livro depois do login");
        helper.assertTrue(GuideBook.given(player), "a marca nova fica gravada");
        helper.assertTrue(!GuideBook.deliver(player), "logar de novo não entrega");
        helper.assertValueEqual(books(player), 1, "continua um livro só");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void oldMarkStillGetsTheNewBook(GameTestHelper helper) {
        ServerPlayer player = TestPlayers.inLevel(helper);
        CompoundTag persisted = new CompoundTag();
        persisted.putBoolean(WirelessAutomate.MODID + ":guide_given", true);
        player.getPersistentData().put(Player.PERSISTED_NBT_TAG, persisted);
        helper.assertTrue(!GuideBook.given(player), "a marca antiga não vale mais");
        helper.assertTrue(GuideBook.deliver(player), "quem tinha a marca antiga recebe o livro novo");
        helper.assertValueEqual(books(player), 1, "um livro");
        helper.assertTrue(!GuideBook.deliver(player), "e só uma vez");
        helper.succeed();
    }
}
