package io.github.matheusanbs.wirelessautomate.client;

import io.github.matheusanbs.wirelessautomate.WirelessAutomate;
import io.github.matheusanbs.wirelessautomate.client.filter.FilterScreen;
import io.github.matheusanbs.wirelessautomate.client.linker.LinkerScreen;
import io.github.matheusanbs.wirelessautomate.client.recipe.RecipeEditorScreen;
import io.github.matheusanbs.wirelessautomate.client.repairer.RepairerScreen;
import io.github.matheusanbs.wirelessautomate.client.repairer.render.RepairerRenderer;
import io.github.matheusanbs.wirelessautomate.client.resonator.ResonatorProjection;
import io.github.matheusanbs.wirelessautomate.client.resonator.render.ResonatorRenderer;
import io.github.matheusanbs.wirelessautomate.client.resonator.screen.ResonatorScreen;
import io.github.matheusanbs.wirelessautomate.client.router.RouterScreen;
import io.github.matheusanbs.wirelessautomate.client.storage.StorageListScreen;
import io.github.matheusanbs.wirelessautomate.client.storage.StorageScalarScreen;
import io.github.matheusanbs.wirelessautomate.client.tablet.TabletScreen;
import io.github.matheusanbs.wirelessautomate.client.tv.TvClientValues;
import io.github.matheusanbs.wirelessautomate.client.tv.TvScreen;
import io.github.matheusanbs.wirelessautomate.client.tv.editor.TvLibraryClient;
import io.github.matheusanbs.wirelessautomate.client.tv.render.TvCanvases;
import io.github.matheusanbs.wirelessautomate.client.tv.render.TvRenderer;
import io.github.matheusanbs.wirelessautomate.item.RouterBlockItem;
import io.github.matheusanbs.wirelessautomate.menu.FilterMenu;
import io.github.matheusanbs.wirelessautomate.menu.LinkerMenu;
import io.github.matheusanbs.wirelessautomate.menu.RecipeEditorMenu;
import io.github.matheusanbs.wirelessautomate.menu.RepairerMenu;
import io.github.matheusanbs.wirelessautomate.menu.ResonatorMenu;
import io.github.matheusanbs.wirelessautomate.menu.RouterMenu;
import io.github.matheusanbs.wirelessautomate.menu.StorageScalarMenu;
import io.github.matheusanbs.wirelessautomate.menu.StorageListMenu;
import io.github.matheusanbs.wirelessautomate.menu.TabletMenu;
import io.github.matheusanbs.wirelessautomate.menu.TvMenu;
import io.github.matheusanbs.wirelessautomate.registry.ModBlockEntities;
import io.github.matheusanbs.wirelessautomate.registry.ModItems;
import io.github.matheusanbs.wirelessautomate.registry.ModMenus;
import io.github.matheusanbs.wirelessautomate.resonator.ResonanceCoreBlock;
import io.github.matheusanbs.wirelessautomate.resonator.ResonanceCoreItem;
import io.github.matheusanbs.wirelessautomate.resonator.SignalCrystalItem;
import io.github.matheusanbs.wirelessautomate.storage.StorageBlockItem;
import io.github.matheusanbs.wirelessautomate.storage.StorageKind;
import io.github.matheusanbs.wirelessautomate.tv.TvClientHooks;
import io.github.matheusanbs.wirelessautomate.client.resonator.render.CrystalShader;
import io.github.matheusanbs.wirelessautomate.client.resonator.render.PlasmaShader;
import io.github.matheusanbs.wirelessautomate.client.resonator.render.RibbonShader;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.renderer.item.ItemProperties;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;
import net.neoforged.neoforge.client.event.RegisterShadersEvent;
import net.neoforged.neoforge.registries.DeferredItem;

/** Registros só do cliente. As classes de tela nunca são carregadas no servidor dedicado. */
// bus explícito: o FML já o deduz do evento e marcou o atributo para remoção, mas aqui ele deixa claro
@SuppressWarnings("removal")
@EventBusSubscriber(modid = WirelessAutomate.MODID, bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class ClientSetup {
    private ClientSetup() {
    }

    /**
     * Propriedade {@code wirelessautomate:tier} dos itens do roteador, dos armazenamentos e do Núcleo de Ressonância (a
     * posição no {@code RouterTier}: 0 = Básico ... 7 = Ultimate): os overrides dos modelos de item trocam o ícone pelo modelo do tier.
     * Os cristais (o de Sinal, o Lapidado e os Afinados) ganham {@code wirelessautomate:dull} (1 quando Opacos) e
     * {@code wirelessautomate:cracked} (1 quando rachados), que trocam o modelo. Liga também o clique no
     * Núcleo de Ressonância à projeção da estrutura e as Telas de Sinal à imagem delas (layout novo e números novos
     * marcam a imagem suja).
     */
    @SubscribeEvent
    static void clientSetup(FMLClientSetupEvent event) {
        event.enqueueWork(() -> {
            ItemProperties.register(ModItems.ROUTER.get(), WirelessAutomate.id("tier"),
                    (stack, level, entity, seed) -> RouterBlockItem.tierOf(stack).ordinal());
            for (StorageKind kind : StorageKind.values()) {
                ItemProperties.register(ModItems.STORAGE.get(kind).get(), WirelessAutomate.id("tier"),
                        (stack, level, entity, seed) -> StorageBlockItem.tierOf(stack).ordinal());
            }
            ItemProperties.register(ModItems.RESONANCE_CORE.get(), WirelessAutomate.id("tier"),
                    (stack, level, entity, seed) -> ResonanceCoreItem.tierOf(stack).ordinal());
            List<DeferredItem<SignalCrystalItem>> crystals = new ArrayList<>(ModItems.TUNED_CRYSTALS.values());
            crystals.add(ModItems.SIGNAL_CRYSTAL);
            crystals.add(ModItems.CUT_SIGNAL_CRYSTAL);
            for (var crystal : crystals) {
                ItemProperties.register(crystal.get(), WirelessAutomate.id("dull"),
                        (stack, level, entity, seed) -> SignalCrystalItem.isDull(stack) ? 1F : 0F);
                ItemProperties.register(crystal.get(), WirelessAutomate.id("cracked"),
                        (stack, level, entity, seed) -> SignalCrystalItem.isCracked(stack) ? 1F : 0F);
            }
            ResonanceCoreBlock.projectionHook = ResonatorProjection::toggle;
            TvClientHooks.changed = tv -> TvCanvases.markDirty(tv.getBlockPos().asLong());
            TvClientHooks.values = TvClientValues::receive;
            TvClientHooks.library = TvLibraryClient::receive;
            TvClientHooks.preview = io.github.matheusanbs.wirelessautomate.client.tv.TvPreviewValues::receive;
        });
    }

    @SubscribeEvent
    static void registerScreens(RegisterMenuScreensEvent event) {
        event.register(ModMenus.ROUTER.get(),
                (RouterMenu menu, Inventory inventory, Component title) -> new RouterScreen(menu, inventory, title));
        event.register(ModMenus.STORAGE_LIST.get(), (StorageListMenu<?> menu, Inventory inventory, Component title)
                -> new StorageListScreen(menu, inventory, title));
        event.register(ModMenus.STORAGE_SCALAR.get(), (StorageScalarMenu menu, Inventory inventory, Component title)
                -> new StorageScalarScreen(menu, inventory, title));
        event.register(ModMenus.FILTER.get(),
                (FilterMenu menu, Inventory inventory, Component title) -> new FilterScreen(menu, inventory, title));
        event.register(ModMenus.LINKER.get(),
                (LinkerMenu menu, Inventory inventory, Component title) -> new LinkerScreen(menu, inventory, title));
        event.register(ModMenus.NETWORK_TABLET.get(),
                (TabletMenu menu, Inventory inventory, Component title) -> new TabletScreen(menu, inventory, title));
        event.register(ModMenus.RECIPE_EDITOR.get(), (RecipeEditorMenu menu, Inventory inventory, Component title)
                -> new RecipeEditorScreen(menu, inventory, title));
        event.register(ModMenus.RESONATOR.get(),
                (ResonatorMenu menu, Inventory inventory, Component title) -> new ResonatorScreen(menu, inventory, title));
        event.register(ModMenus.CRYSTAL_REPAIRER.get(),
                (RepairerMenu menu, Inventory inventory, Component title) -> new RepairerScreen(menu, inventory, title));
        event.register(ModMenus.SIGNAL_SCREEN.get(),
                (TvMenu menu, Inventory inventory, Component title) -> new TvScreen(menu, inventory, title));
    }

    /** A câmara animada do Núcleo montado, a animação do Reparador de Cristais e a Tela de Sinal. */
    @SubscribeEvent
    static void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerBlockEntityRenderer(ModBlockEntities.RESONANCE_CORE.get(), ResonatorRenderer::new);
        event.registerBlockEntityRenderer(ModBlockEntities.CRYSTAL_REPAIRER.get(), RepairerRenderer::new);
        event.registerBlockEntityRenderer(ModBlockEntities.SIGNAL_SCREEN.get(), TvRenderer::new);
    }

    /** Os core shaders da câmara: o Núcleo de plasma, as fitas e o cristal. */
    @SubscribeEvent
    static void registerShaders(RegisterShadersEvent event) {
        PlasmaShader.register(event);
        RibbonShader.register(event);
        CrystalShader.register(event);
    }
}
