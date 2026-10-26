package io.github.matheusanbs.wirelessautomate.client.dev;

import static io.github.matheusanbs.wirelessautomate.client.dev.DevEndToEnd.*;
import static io.github.matheusanbs.wirelessautomate.client.dev.E2eShowcaseWorld.*;
import static io.github.matheusanbs.wirelessautomate.client.dev.E2eSteps.*;
import static io.github.matheusanbs.wirelessautomate.client.dev.E2eUi.*;
import static io.github.matheusanbs.wirelessautomate.client.dev.E2eWorld.*;

import io.github.matheusanbs.wirelessautomate.block.RouterBlock;
import io.github.matheusanbs.wirelessautomate.block.RouterTier;
import io.github.matheusanbs.wirelessautomate.client.storage.StorageScalarScreen;
import io.github.matheusanbs.wirelessautomate.registry.ModBlocks;
import io.github.matheusanbs.wirelessautomate.registry.ModItems;
import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.BlockHitResult;
import io.github.matheusanbs.wirelessautomate.storage.StorageKind;
import net.minecraft.world.level.block.state.BlockState;
import io.github.matheusanbs.wirelessautomate.storage.StorageBlockItem;
import io.github.matheusanbs.wirelessautomate.storage.StorageSourceTankBlockEntity;
import io.github.matheusanbs.wirelessautomate.storage.StorageSourceTankBlock;
import io.github.matheusanbs.wirelessautomate.client.GuiText;

/**
 * O Tanque de Source no e2e (só com o Ars Nouveau): a tela de um valor só, em inglês e em português, e os quatro
 * tiers no mundo com a câmera.
 */
final class E2eSourceTankSteps {
    private E2eSourceTankSteps() {
    }

    /** Os Tanques de Source das fotos: Básico vazio, Avançado a 30%, Elite a 60% e Ultimate (níveis 0, 3, 6 e 10). */
    private static final RouterTier[] SOURCE_TANK_TIERS = {RouterTier.BASIC, RouterTier.ADVANCED, RouterTier.ELITE,
            RouterTier.ULTIMATE};
    private static final long[] SOURCE_TANK_CONTENTS = {0L, 24_000L, 384_000L, 3_000_000_000L};

    /**
     * A tela de um valor só do Tanque de Source (Avançado, 1.640.000 de Source): em inglês e, depois de
     * trocar o idioma, em português, as duas com a conferência dos textos cortados. A variação fica em
     * "Parado" (o tanque não se mexe).
     */
    static void scalarSourceSteps(List<Step> list, java.util.function.Supplier<BlockPos> where) {
        UUID playerId = Minecraft.getInstance().player.getUUID();
        BlockPos[] at = new BlockPos[1];
        list.add(new Step("Tanque de Source (tela): preparar", STEP_TIMEOUT_MS, () -> onServer(server -> {
            BlockPos pos = at[0] = where.get();
            ServerLevel level = server.getPlayerList().getPlayer(playerId).serverLevel();
            level.setBlockAndUpdate(pos, ModBlocks.STORAGE.get(StorageKind.SOURCE_TANK).get().defaultBlockState()
                    .setValue(RouterBlock.TIER, RouterTier.EMERALD));
            ((StorageSourceTankBlockEntity) level.getBlockEntity(pos)).store().insert(1_640_000L, false);
            return null;
        }), () -> Minecraft.getInstance().level != null
                && Minecraft.getInstance().level.getBlockState(at[0]).getBlock() instanceof StorageSourceTankBlock,
                () -> "bloco no cliente"));
        for (String code : E2eVariants.ptOn() ? new String[] {"en_us", "pt_br"} : new String[] {"en_us"}) {
            String suffix = code.equals("pt_br") ? "-pt" : "";
            String label = code.equals("pt_br") ? "pt: " : "";
            if (code.equals("pt_br")) {
                list.add(language("pt_br"));
            }
            list.add(new Step(label + "Tanque de Source: abrir a tela", STEP_TIMEOUT_MS, () -> useOn(at[0]),
                    () -> Minecraft.getInstance().screen instanceof StorageScalarScreen screen && screen.getMenu().received()
                            && screen.getMenu().kind() == StorageKind.SOURCE_TANK && screen.getMenu().stored() == 1_640_000L,
                    () -> "tela " + describe(Minecraft.getInstance().screen)));
            list.add(new Step(label + "Tanque de Source: nenhum texto cortado", STEP_TIMEOUT_MS, () -> {
            }, () -> GuiText.clipCount() == 0, () -> GuiText.clipCount() + " texto(s) cortado(s) na tela do tanque"));
            list.add(capture("tanque-source-1-tela" + suffix));
            list.add(close(label + "Tanque de Source: fechar"));
            if (code.equals("pt_br")) {
                list.add(language("en_us"));
            }
        }
        list.add(new Step("Tanque de Source (tela): limpar", STEP_TIMEOUT_MS, () -> onServer(server -> {
            server.overworld().setBlockAndUpdate(at[0], Blocks.AIR.defaultBlockState());
            return null;
        }), () -> true, () -> ""));
    }

    /**
     * Tanque de Source no mundo: os quatro tiers lado a lado com níveis diferentes (vazio, 25%, 60%, cheio),
     * a vista de perto, o contorno de seleção com a mira num deles e os itens na mão e na barra.
     */
    static void sourceTankSteps(List<Step> list) {
        UUID playerId = Minecraft.getInstance().player.getUUID();
        BlockPos[] tanks = new BlockPos[4];
        BlockPos[] origin = new BlockPos[1];
        list.add(new Step("Tanque de Source: preparar", STEP_TIMEOUT_MS, () -> onServer(server -> {
            ServerPlayer player = server.getPlayerList().getPlayer(playerId);
            ServerLevel level = player.serverLevel();
            origin[0] = player.blockPosition();
            RouterTier[] tiers = SOURCE_TANK_TIERS;
            long[] contents = SOURCE_TANK_CONTENTS;
            for (int i = 0; i < 4; i++) {
                tanks[i] = origin[0].offset(-3 + 2 * i, 0, 4);
                level.setBlockAndUpdate(tanks[i], ModBlocks.STORAGE.get(StorageKind.SOURCE_TANK).get().defaultBlockState()
                        .setValue(RouterBlock.TIER, tiers[i]));
                if (contents[i] > 0) {
                    ((StorageSourceTankBlockEntity) level.getBlockEntity(tanks[i])).store().insert(contents[i], false);
                }
            }
            for (int i = 0; i < 4; i++) {
                player.getInventory().setItem(i, StorageBlockItem.withTier(
                        ModItems.STORAGE.get(StorageKind.SOURCE_TANK).get(), tiers[i]));
            }
            return null;
        }), () -> {
            Minecraft minecraft = Minecraft.getInstance();
            if (minecraft.level == null) {
                return false;
            }
            int[] expected = {0, 3, 6, 10};
            for (int i = 0; i < 4; i++) {
                BlockState state = minecraft.level.getBlockState(tanks[i]);
                if (!(state.getBlock() instanceof StorageSourceTankBlock)
                        || state.getValue(StorageSourceTankBlock.FILL) != expected[i]) {
                    return false;
                }
            }
            return true;
        }, () -> "tanques no cliente com os níveis 0, 3, 6 e 10"));
        list.add(sourceCamera("Tanque de Source: câmera geral", () -> origin[0], -2.0, 0, 0.3, 1.5, false, 0));
        list.add(DevEndToEnd.wait("chunk do tanque", 20));
        list.add(capture("tanque-source-mundo"));
        list.add(sourceCamera("Tanque de Source: câmera de perto", () -> origin[0], 0.5, 0.6, 2.0, 1.5, false, 0));
        list.add(DevEndToEnd.wait("tanque de perto", 10));
        list.add(capture("tanque-source-perto"));
        // Mira no tanque Elite, com a interface (mira e barra) e o item do Avançado na mão.
        list.add(sourceCamera("Tanque de Source: mira", () -> origin[0], 1.5, 0.4, 2.4, 2, true, 1));
        list.add(new Step("Tanque de Source: a mira está no tanque Elite", STEP_TIMEOUT_MS, () -> {
        }, () -> Minecraft.getInstance().hitResult instanceof BlockHitResult hit && hit.getBlockPos().equals(tanks[2]),
                () -> "mira em " + Minecraft.getInstance().hitResult));
        list.add(DevEndToEnd.wait("contorno", 10));
        list.add(capture("tanque-source-contorno"));
        list.add(new Step("Tanque de Source: limpar", STEP_TIMEOUT_MS, () -> {
            Minecraft.getInstance().options.hideGui = false;
            onServer(server -> {
                for (int i = 0; i < 4; i++) {
                    server.getPlayerList().getPlayer(playerId).getInventory().setItem(i, ItemStack.EMPTY);
                    server.overworld().setBlockAndUpdate(tanks[i], Blocks.AIR.defaultBlockState());
                }
                return null;
            });
        }, () -> true, () -> ""));
    }

    /**
     * Câmera do jogador em {@code origem + (dx, dy, dz)}, olhando o tanque de índice {@code tanque}
     * (o olho fica 1,62 acima do pé); {@code gui} liga a interface e {@code slot} escolhe a barra.
     */
    private static Step sourceCamera(String name, java.util.function.Supplier<BlockPos> origin, double dx, double dy,
                                     double dz, double tanque, boolean gui, int slot) {
        double[] cam = new double[3];
        float[] ang = new float[2];
        return new Step(name, STEP_TIMEOUT_MS, () -> {
            BlockPos base = origin.get();
            // alvo: o centro do tanque de índice `tanque` (x = -3 + 2i, z = 4) e, de perto, só a altura do meio
            double tx = base.getX() + 0.5 + (-3 + 2 * tanque), ty = base.getY() + 0.6, tz = base.getZ() + 4.5;
            cam[0] = base.getX() + 0.5 + dx;
            cam[1] = base.getY() + dy;
            cam[2] = base.getZ() + 0.5 + dz;
            double ex = tx - cam[0], ey = ty - (cam[1] + 1.62), ez = tz - cam[2];
            ang[0] = (float) (Math.toDegrees(Math.atan2(-ex, ez)));
            ang[1] = (float) (-Math.toDegrees(Math.atan2(ey, Math.sqrt(ex * ex + ez * ez))));
            Minecraft minecraft = Minecraft.getInstance();
            minecraft.options.hideGui = !gui;
            minecraft.player.getInventory().selected = slot;
            minecraft.player.setYRot(ang[0]);
            minecraft.player.setXRot(ang[1]);
            minecraft.player.yRotO = ang[0];
            minecraft.player.xRotO = ang[1];
            UUID playerId = minecraft.player.getUUID();
            onServer(server -> {
                ServerPlayer player = server.getPlayerList().getPlayer(playerId);
                player.getAbilities().flying = true;
                player.onUpdateAbilities();
                player.getInventory().selected = slot;
                player.teleportTo(player.serverLevel(), cam[0], cam[1], cam[2], ang[0], ang[1]);
                return null;
            });
        }, () -> Math.abs(Minecraft.getInstance().player.getXRot() - ang[1]) < 0.5f
                && Minecraft.getInstance().player.position().distanceToSqr(cam[0], cam[1], cam[2]) < 0.5,
                () -> "câmera em " + Minecraft.getInstance().player.position());
    }
}
