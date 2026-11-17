package io.github.matheusanbs.wirelessautomate.client.dev.vitrine;

import appeng.api.AECapabilities;
import appeng.api.config.Actionable;
import appeng.api.crafting.PatternDetailsHelper;
import appeng.api.inventories.InternalInventory;
import appeng.api.networking.IGridNode;
import appeng.api.networking.IInWorldGridNodeHost;
import appeng.api.networking.security.IActionSource;
import appeng.api.parts.IPart;
import appeng.api.parts.IPartHost;
import appeng.api.parts.IPartItem;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.GenericStack;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.items.IItemHandler;

/**
 * A rede do AE2 da cena da Doca, com a API dele (só é carregada com o AE2 presente): um cabo de vidro com o terminal
 * de crafting numa face e, nas outras, o Pattern Provider (que encosta na Doca), o drive com uma célula, a energia
 * criativa e o armazenamento de crafting que serve de CPU.
 */
final class Ae2Rede {
    private Ae2Rede() {
    }

    private static ResourceLocation ae2(String path) {
        return ResourceLocation.fromNamespaceAndPath("ae2", path);
    }

    private static Block bloco(String path) {
        return BuiltInRegistries.BLOCK.get(ae2(path));
    }

    /** O AE2 está carregado, com o que a cena precisa. */
    static boolean presente() {
        return bloco("pattern_provider") != net.minecraft.world.level.block.Blocks.AIR;
    }

    /** Põe o bloco do AE2 {@code path} em {@code pos}. */
    static void bloco(ServerLevel nivel, BlockPos pos, String path) {
        nivel.setBlockAndUpdate(pos, bloco(path).defaultBlockState());
    }

    /** O cabo de vidro em {@code pos}, com o terminal de crafting na face {@code lado}. */
    static void cabo(ServerLevel nivel, BlockPos pos, Direction lado) {
        bloco(nivel, pos, "cable_bus");
        IPartHost host = (IPartHost) nivel.getBlockEntity(pos);
        host.addPart(IPartItem.byId(ae2("fluix_glass_cable")), null, null);
        host.addPart(IPartItem.byId(ae2("crafting_terminal")), lado, null);
    }

    /** O padrão de processamento {@code entrada} → {@code saida} (um de cada), instalado no provedor. */
    static void padrao(ServerLevel nivel, BlockPos provedor, Item entrada, Item saida) {
        ItemStack padrao = PatternDetailsHelper.encodeProcessingPattern(
                List.of(new GenericStack(AEItemKey.of(entrada), 1)), List.of(new GenericStack(AEItemKey.of(saida), 1)));
        try {
            Object logica = nivel.getBlockEntity(provedor).getClass().getMethod("getLogic").invoke(nivel.getBlockEntity(provedor));
            InternalInventory padroes = (InternalInventory) logica.getClass().getMethod("getPatternInv").invoke(logica);
            padroes.addItems(padrao);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("sem o inventário de padrões do provedor", e);
        }
    }

    /** Põe a célula de 1k no drive. */
    static void celula(ServerLevel nivel, BlockPos drive) {
        IItemHandler itens = nivel.getCapability(Capabilities.ItemHandler.BLOCK, drive, null);
        itens.insertItem(0, new ItemStack(BuiltInRegistries.ITEM.get(ae2("item_storage_cell_1k"))), false);
    }

    /** O nó do bloco em {@code pos} está ativo (com energia e canal). */
    static boolean ativo(ServerLevel nivel, BlockPos pos) {
        IGridNode no = no(nivel, pos);
        return no != null && no.isActive();
    }

    /** Guarda {@code quantidade} de {@code item} na rede do bloco em {@code pos}. */
    static void estoque(ServerLevel nivel, BlockPos pos, Item item, long quantidade) {
        no(nivel, pos).getGrid().getStorageService().getInventory().insert(AEItemKey.of(item), quantidade,
                Actionable.MODULATE, IActionSource.empty());
    }

    /** A rede do bloco em {@code pos} conhece o padrão que fabrica {@code item} e tem uma CPU. */
    static boolean pronta(ServerLevel nivel, BlockPos pos, Item item) {
        IGridNode no = no(nivel, pos);
        var crafting = no.getGrid().getCraftingService();
        return crafting.isCraftable(AEItemKey.of(item)) && !crafting.getCpus().isEmpty();
    }

    /** Abre a tela do terminal (a parte da face {@code lado} do cabo em {@code cabo}) para o jogador. */
    static void abrir(ServerLevel nivel, BlockPos cabo, Direction lado, net.minecraft.world.entity.player.Player jogador) {
        IPart terminal = ((IPartHost) nivel.getBlockEntity(cabo)).getPart(lado);
        terminal.onUseWithoutItem(jogador, Vec3.atCenterOf(cabo));
    }

    private static IGridNode no(ServerLevel nivel, BlockPos pos) {
        IInWorldGridNodeHost host = nivel.getCapability(AECapabilities.IN_WORLD_GRID_NODE_HOST, pos, null);
        if (host == null) {
            return null;
        }
        for (Direction face : Direction.values()) {
            IGridNode no = host.getGridNode(face);
            if (no != null) {
                return no;
            }
        }
        return null;
    }
}
