package io.github.matheusanbs.wirelessautomate.client.dev;

import static io.github.matheusanbs.wirelessautomate.client.dev.DevEndToEnd.*;
import static io.github.matheusanbs.wirelessautomate.client.dev.E2eSteps.*;
import static io.github.matheusanbs.wirelessautomate.client.dev.E2eUi.*;
import static io.github.matheusanbs.wirelessautomate.client.dev.E2eWorld.*;

import io.github.matheusanbs.wirelessautomate.Config;
import io.github.matheusanbs.wirelessautomate.GuiTheme;
import io.github.matheusanbs.wirelessautomate.block.RouterBlock;
import io.github.matheusanbs.wirelessautomate.block.RouterTier;
import io.github.matheusanbs.wirelessautomate.client.GuiText;
import io.github.matheusanbs.wirelessautomate.client.dock.DockReachPaint;
import io.github.matheusanbs.wirelessautomate.client.dock.DockScreen;
import io.github.matheusanbs.wirelessautomate.dock.DockAe2;
import io.github.matheusanbs.wirelessautomate.dock.DockColors;
import io.github.matheusanbs.wirelessautomate.dock.DockDelivery;
import io.github.matheusanbs.wirelessautomate.dock.DockLinks;
import io.github.matheusanbs.wirelessautomate.dock.DockSide;
import io.github.matheusanbs.wirelessautomate.dock.DockStatus;
import io.github.matheusanbs.wirelessautomate.dock.PatternDockBlockEntity;
import io.github.matheusanbs.wirelessautomate.item.LinkerItem;
import io.github.matheusanbs.wirelessautomate.packet.DockStatePayload;
import io.github.matheusanbs.wirelessautomate.registry.ModBlocks;
import io.github.matheusanbs.wirelessautomate.registry.ModItems;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Predicate;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;

/**
 * A tela da Doca de Padrões nos três cenários do mockup, nos dois temas: a base em uso (Elite, 30/32, um lado
 * trabalhando, um com todas cheias, um sem provedor, um com duas máquinas em área descarregada, um com provedor e sem
 * máquina, um livre), a recém-colocada (Básica, só o provedor no Norte) e a config baixada (raio 12 e limite 24: o
 * provedor cheio e as máquinas fora do alcance, a trava do provedor, o padrão de bancada e o pedido misto). Cada cenário monta a Doca do
 * zero, ao lado do jogador, com máquinas de verdade (as do Mekanism e do AE2 que o cliente de dev traz) e o Pattern
 * Provider do AE2 encostado; as máquinas "descarregadas" ficam abaixo do mundo, onde o {@code isLoaded} é falso. Os
 * fatos que só um pedido do AE2 faria (a recusa, a última atividade, o provedor cheio) vão direto no lado. Em cada
 * foto, nenhum texto cortado. Depois, o cubo do alcance no mundo ({@code doca-cubo}): a Doca Básica escolhida no
 * Vinculador, com uma fileira de fornalhas no Norte e outra no Sul, vista de fora do cubo com o HUD; guardado o Vinculador,
 * o cubo some, e volta ao pegá-lo de novo (e pelo pedido ao servidor, sem o cubo no cliente). Por último, o modelo das
 * Docas de cada tier no mundo ({@code doca-blocos}, {@code doca-perto}, {@code doca-8} e {@code doca-16}). No fim, os
 * blocos saem e a config volta.
 */
final class E2eDockSteps {
    private static final int OPEN_RETRY_TICKS = 40;
    /** Onde o Vinculador fica guardado no passo que o tira da mão. */
    private static final int STASH_SLOT = 8;
    private static final int NORTH = Direction.NORTH.ordinal();
    private static final int SOUTH = Direction.SOUTH.ordinal();
    private static final int EAST = Direction.EAST.ordinal();
    private static final int WEST = Direction.WEST.ordinal();
    private static final int UP = Direction.UP.ordinal();
    /** O cliente de dev traz o AE2: os provedores existem e a trava do Leste vale no cenário da config baixada. */
    private static final boolean AE2 = DockAe2.LOADED;
    /** O raio e o limite da linha da Esmeralda, a que a Doca Elite lê, no cenário da config baixada. */
    private static final int LOW_RADIUS = 12;
    private static final int LOW_MAX = 24;

    private static final List<BlockPos> PLACED = new ArrayList<>();
    private static Integer savedRadius;
    private static Integer savedMax;

    private E2eDockSteps() {
    }

    static void dockSteps(List<Step> list) {
        UUID playerId = Minecraft.getInstance().player.getUUID();
        BlockPos[] dock = new BlockPos[1];
        list.add(close("Doca: fechar a tela de antes"));
        round(list, playerId, dock, "");
        E2eVariants.pt(list, () -> {
            list.add(language("pt_br"));
            round(list, playerId, dock, "-pt");
            list.add(language("en_us"));
        });
        reach(list, playerId, dock);
        models(list, playerId, dock);
        E2eDockAnimSteps.animationSteps(list);
        list.add(new Step("Doca: tirar os blocos", STEP_TIMEOUT_MS, () -> onServer(server -> {
            restoreConfig();
            clear(server.overworld());
            return null;
        }), () -> Minecraft.getInstance().level.getBlockState(dock[0]).isAir(), () -> "Doca no cliente"));
    }

    /** Um idioma: os três cenários, cada um aberto, fotografado no claro e no escuro, e fechado. */
    private static void round(List<Step> list, UUID playerId, BlockPos[] dock, String suffix) {
        list.add(build("Doca: base em uso" + suffix, playerId, dock, E2eDockSteps::inUse));
        list.add(open("Doca: abrir a base em uso" + suffix, dock, state -> state.total() == 30
                && line(state, NORTH).working() == 9 && state(state, EAST) == DockStatus.State.ALL_FULL
                && state(state, SOUTH) == DockStatus.State.NO_PROVIDER && line(state, WEST).unloaded() == 2
                && state(state, UP) == DockStatus.State.NO_MACHINES));
        shot(list, "doca-uso" + suffix);
        if (suffix.isEmpty()) {
            hovered(list, "doca-uso-dica", 3);
        }
        list.add(close("Doca: fechar a base em uso" + suffix));

        list.add(build("Doca: recém-colocada" + suffix, playerId, dock, E2eDockSteps::fresh));
        list.add(open("Doca: abrir a recém-colocada" + suffix, dock, state -> state.total() == 0
                && state(state, NORTH) == DockStatus.State.NO_MACHINES && state(state, SOUTH) == DockStatus.State.FREE));
        shot(list, "doca-nova" + suffix);
        list.add(close("Doca: fechar a recém-colocada" + suffix));

        list.add(build("Doca: config baixada" + suffix, playerId, dock, E2eDockSteps::lowConfig));
        list.add(open("Doca: abrir a config baixada" + suffix, dock, state -> state.max() == LOW_MAX
                && state.radius() == LOW_RADIUS && line(state, NORTH).outOfReach() == 4
                && line(state, EAST).outOfReach() == 2 && state(state, NORTH) == DockStatus.State.PROVIDER_FULL
                && state(state, SOUTH) == DockStatus.State.CRAFTING_PATTERN
                && state(state, WEST) == DockStatus.State.MIXED
                && (!AE2 || state(state, EAST) == DockStatus.State.PROVIDER_LOCK)));
        shot(list, "doca-config" + suffix);
        list.add(close("Doca: fechar a config baixada" + suffix));
        list.add(new Step("Doca: config de volta" + suffix, STEP_TIMEOUT_MS, () -> onServer(server -> {
            restoreConfig();
            return null;
        }), () -> true, () -> ""));
    }

    /**
     * O cubo do alcance: a Doca Básica com quatro fornalhas no Norte e quatro no Sul, o Vinculador na mão com o lado
     * Norte escolhido pelo {@link DockLinks#choose} (que manda o cubo), a foto de fora do cubo, o Vinculador guardado e
     * pego de novo e o cubo esquecido no cliente que volta pelo pedido.
     */
    private static void reach(List<Step> list, UUID playerId, BlockPos[] dock) {
        Vec3[] back = new Vec3[1];
        list.add(build("Doca: cubo do alcance", playerId, dock, (server, level, at) -> {
            PatternDockBlockEntity built = placeDock(level, at, RouterTier.BASIC);
            built.batchChanges(() -> {
                link(level, built, NORTH, Blocks.FURNACE, row(at, -3, 2, 4));
                link(level, built, SOUTH, Blocks.FURNACE, row(at, -3, 4, 4));
            });
        }));
        list.add(new Step("Doca: Vinculador com a Doca escolhida", STEP_TIMEOUT_MS, () -> {
            back[0] = Minecraft.getInstance().player.position();
            onServer(server -> {
                ServerPlayer player = server.getPlayerList().getPlayer(playerId);
                ItemStack linker = new ItemStack(ModItems.LINKER.get());
                player.setItemInHand(InteractionHand.MAIN_HAND, linker);
                DockLinks.choose(player, linker, (PatternDockBlockEntity) player.serverLevel()
                        .getBlockEntity(dock[0]), NORTH);
                return null;
            });
        }, () -> DockReachPaint.shown() && DockReachPaint.machineCount() == 8
                && LinkerItem.dock(Minecraft.getInstance().player.getMainHandItem()) != null && stepTicks >= 4,
                () -> "cubo " + DockReachPaint.shown() + ", máquinas " + DockReachPaint.machineCount() + ", mão "
                        + Minecraft.getInstance().player.getMainHandItem()));
        list.add(E2eRepairerSteps.cameraShot("doca-cubo", playerId, () -> dock[0], new double[] {9, 8, -13}, 0.5));
        list.add(new Step("Doca: guardar o Vinculador esconde o cubo", STEP_TIMEOUT_MS, () -> onServer(server -> {
            ServerPlayer player = server.getPlayerList().getPlayer(playerId);
            ItemStack linker = player.getMainHandItem().copy();
            player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
            player.getInventory().setItem(STASH_SLOT, linker);
            return null;
        }), () -> !DockReachPaint.shown() && DockReachPaint.stored() && stepTicks >= 4,
                () -> "à mostra " + DockReachPaint.shown() + ", guardado " + DockReachPaint.stored()));
        list.add(new Step("Doca: pegar o Vinculador de novo mostra o cubo", STEP_TIMEOUT_MS, () -> onServer(server -> {
            ServerPlayer player = server.getPlayerList().getPlayer(playerId);
            player.setItemInHand(InteractionHand.MAIN_HAND, player.getInventory().getItem(STASH_SLOT).copy());
            player.getInventory().setItem(STASH_SLOT, ItemStack.EMPTY);
            return null;
        }), () -> DockReachPaint.shown() && DockReachPaint.machineCount() == 8,
                () -> "à mostra " + DockReachPaint.shown() + ", máquinas " + DockReachPaint.machineCount()));
        // sem o cubo no cliente (como ao entrar no mundo de novo), o Vinculador na mão o pede ao servidor
        list.add(new Step("Doca: o cubo esquecido volta pelo pedido", STEP_TIMEOUT_MS, DockReachPaint::clear,
                () -> DockReachPaint.shown() && DockReachPaint.machineCount() == 8,
                () -> "à mostra " + DockReachPaint.shown() + ", máquinas " + DockReachPaint.machineCount()));
        list.add(new Step("Doca: devolver o jogador", STEP_TIMEOUT_MS, () -> onServer(server -> {
            ServerPlayer player = server.getPlayerList().getPlayer(playerId);
            player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
            player.getAbilities().flying = false;
            player.onUpdateAbilities();
            player.teleportTo(player.serverLevel(), back[0].x, back[0].y, back[0].z, player.getYRot(), 0);
            return null;
        }), () -> !DockReachPaint.shown(), () -> "o cubo continua à mostra"));
    }

    /**
     * O modelo no mundo: as Docas dos tiers carregados lado a lado, da Básica à Ultimate, com um bloco de folga, vistas
     * do Sul e de cima, longe dos roteadores do preparo ({@code doca-blocos}), e a Elite (a cor do {@code .bbmodel}) de perto, pela quina Norte-Leste
     * ({@code doca-perto}) e à noite, com o circuito aceso ({@code doca-noite}), e de dia de 8 e de 16 blocos pelo Sul,
     * para as etiquetas dos lados ({@code doca-8} e {@code doca-16}).
     */
    private static void models(List<Step> list, UUID playerId, BlockPos[] dock) {
        Vec3[] back = new Vec3[1];
        BlockPos[] elite = new BlockPos[1];
        list.add(build("Doca: os blocos dos tiers", playerId, dock, (server, level, at) -> {
            List<RouterTier> tiers = new ArrayList<>();
            for (RouterTier tier : RouterTier.values()) {
                if (tier.loaded()) {
                    tiers.add(tier);
                }
            }
            for (int i = 0; i < tiers.size(); i++) {
                BlockPos pos = at.offset(2 * (i - tiers.size() / 2), 0, 0);
                placeDock(level, pos, tiers.get(i));
                if (tiers.get(i) == RouterTier.ELITE) {
                    elite[0] = pos;
                }
            }
        }));
        list.add(new Step("Doca: guardar a posição do jogador", STEP_TIMEOUT_MS,
                () -> {
                    back[0] = Minecraft.getInstance().player.position();
                    Minecraft.getInstance().gui.setOverlayMessage(Component.empty(), false);
                }, () -> true, () -> ""));
        list.add(E2eRepairerSteps.cameraShot("doca-blocos", playerId, () -> dock[0].offset(-1, 0, 0),
                new double[] {1, 3.5, 7}, 0.5));
        list.add(E2eRepairerSteps.cameraShot("doca-perto", playerId, () -> elite[0], new double[] {1.4, 1.1, -1.7},
                0.5));
        long[] day = new long[1];
        list.add(new Step("Doca: noite, para o circuito aceso", STEP_TIMEOUT_MS, () -> onServer(server -> {
            day[0] = server.overworld().getDayTime();
            server.overworld().setDayTime(18000);
            return null;
        }), () -> Minecraft.getInstance().level.getDayTime() % 24000 == 18000 && stepTicks >= 20,
                () -> "hora no cliente " + Minecraft.getInstance().level.getDayTime()));
        list.add(new Step("captura doca-noite", STEP_TIMEOUT_MS, () -> {
        }, () -> {
            capture(Minecraft.getInstance(), "doca-noite");
            return true;
        }, () -> "doca-noite.png"));
        list.add(new Step("Doca: dia de volta", STEP_TIMEOUT_MS, () -> onServer(server -> {
            server.overworld().setDayTime(day[0]);
            return null;
        }), () -> true, () -> ""));
        list.add(E2eRepairerSteps.cameraShot("doca-8", playerId, () -> elite[0], new double[] {2, 2, 7.5}, 0.5));
        list.add(E2eRepairerSteps.cameraShot("doca-16", playerId, () -> elite[0], new double[] {4, 4, 15}, 0.5));
        list.add(new Step("Doca: devolver o jogador depois dos blocos", STEP_TIMEOUT_MS, () -> onServer(server -> {
            ServerPlayer player = server.getPlayerList().getPlayer(playerId);
            player.getAbilities().flying = false;
            player.onUpdateAbilities();
            player.teleportTo(player.serverLevel(), back[0].x, back[0].y, back[0].z, player.getYRot(), 0);
            return null;
        }), () -> Minecraft.getInstance().player.position().distanceToSqr(back[0]) < 0.01,
                () -> "jogador em " + Minecraft.getInstance().player.position()));
    }

    /** Um cenário: a Doca de antes sai, a nova entra no lugar e {@code setUp} a monta; espera o cliente ver a Doca. */
    private static Step build(String name, UUID playerId, BlockPos[] dock, Scenario setUp) {
        return new Step(name, STEP_TIMEOUT_MS, () -> onServer(server -> {
            ServerPlayer player = server.getPlayerList().getPlayer(playerId);
            ServerLevel level = player.serverLevel();
            if (dock[0] == null) {
                dock[0] = player.blockPosition().offset(-2, 0, 3);
            }
            player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
            restoreConfig();
            clear(level);
            setUp.build(server, level, dock[0]);
            return null;
        }), () -> Minecraft.getInstance().level.getBlockEntity(dock[0]) instanceof PatternDockBlockEntity,
                () -> "Doca no cliente");
    }

    @FunctionalInterface
    private interface Scenario {
        void build(MinecraftServer server, ServerLevel level, BlockPos dock);
    }

    /**
     * Elite, 30/32: Norte com 12 fornalhas (9 trabalhando), Leste com 8 trituradores todos cheios, Sul com 6 câmaras
     * sem provedor, Oeste com 4 prensas (1 trabalhando, 2 descarregadas), Cima só com o provedor, Baixo livre.
     */
    private static void inUse(MinecraftServer server, ServerLevel level, BlockPos at) {
        PatternDockBlockEntity dock = placeDock(level, at, RouterTier.ELITE);
        providers(level, at, Direction.NORTH, Direction.EAST, Direction.WEST, Direction.UP);
        long now = server.getTickCount();
        List<BlockPos> north = row(at, -9, 3, 8);
        north.addAll(column(at, -14, -3, 4));
        List<BlockPos> east = row(at, -7, 5, 6);
        east.addAll(column(at, -14, 2, 2));
        List<BlockPos> south = row(at, -7, 7, 6);
        List<BlockPos> west = row(at, -3, 9, 2);
        int below = level.getMinBuildHeight();
        west.add(new BlockPos(at.getX() - 5, below - 1, at.getZ() + 9));
        west.add(new BlockPos(at.getX() - 4, below - 2, at.getZ() + 9));
        dock.batchChanges(() -> {
            link(level, dock, NORTH, Blocks.FURNACE, north);
            link(level, dock, EAST, machine("mekanism:crusher", Blocks.BLAST_FURNACE), east);
            link(level, dock, SOUTH, machine("mekanism:enrichment_chamber", Blocks.SMOKER), south);
            link(level, dock, WEST, machine("ae2:inscriber", Blocks.CRAFTER), west);
        });
        working(dock, NORTH, 9, now);
        working(dock, EAST, 8, now);
        working(dock, WEST, 1, now);
        DockDelivery.refuse(dock, EAST, DockStatus.ALL_FULL);
    }

    /** Básica, 0/8: só o provedor encostado no Norte. */
    private static void fresh(MinecraftServer server, ServerLevel level, BlockPos at) {
        placeDock(level, at, RouterTier.BASIC);
        providers(level, at, Direction.NORTH);
    }

    /**
     * Elite com o raio 12 e o limite 24: Norte com 12 fornalhas trabalhando, 4 fora do alcance e o provedor cheio,
     * Leste com 8 trituradores parados (2 fora do alcance) e o provedor em "travar até o resultado", Sul com o padrão
     * de bancada recusado, Oeste com o pedido misto recusado; Cima e Baixo livres.
     */
    private static void lowConfig(MinecraftServer server, ServerLevel level, BlockPos at) {
        savedRadius = Config.TIERS.get(RouterTier.EMERALD).machineRadius().get();
        savedMax = Config.TIERS.get(RouterTier.EMERALD).maxMachines().get();
        Config.TIERS.get(RouterTier.EMERALD).machineRadius().set(LOW_RADIUS);
        Config.TIERS.get(RouterTier.EMERALD).maxMachines().set(LOW_MAX);
        PatternDockBlockEntity dock = placeDock(level, at, RouterTier.ELITE);
        providers(level, at, Direction.NORTH, Direction.EAST, Direction.SOUTH, Direction.WEST);
        long now = server.getTickCount();
        List<BlockPos> north = row(at, -9, 3, 8);
        north.addAll(column(at, -14, -3, 4));
        List<BlockPos> east = row(at, -7, 5, 6);
        east.addAll(column(at, -14, 2, 2));
        dock.batchChanges(() -> {
            link(level, dock, NORTH, Blocks.FURNACE, north);
            link(level, dock, EAST, machine("mekanism:crusher", Blocks.BLAST_FURNACE), east);
            link(level, dock, SOUTH, machine("mekanism:enrichment_chamber", Blocks.SMOKER), row(at, -7, 7, 6));
            link(level, dock, WEST, machine("ae2:inscriber", Blocks.CRAFTER), row(at, -5, 9, 4));
        });
        dock.reachChanged();
        working(dock, NORTH, 12, now);
        dock.side(NORTH).setProviderFull(true);
        DockDelivery.refuse(dock, SOUTH, DockStatus.CRAFTING_PATTERN);
        DockDelivery.refuse(dock, WEST, DockStatus.MIXED);
        lockUntilResult(level, at.relative(Direction.EAST));
    }

    /**
     * Põe o Pattern Provider em {@code pos} em "travar até o resultado" sem tipos do AE2: pelo NBT do block entity, onde
     * o AE2 guarda cada configuração com o nome dela ({@code lock_crafting_mode}). Sem o AE2, nada.
     */
    private static void lockUntilResult(ServerLevel level, BlockPos pos) {
        BlockEntity entity = level.getBlockEntity(pos);
        if (!AE2 || entity == null) {
            return;
        }
        CompoundTag tag = entity.saveWithoutMetadata(level.registryAccess());
        tag.putString("lock_crafting_mode", "LOCK_UNTIL_RESULT");
        entity.loadWithComponents(tag, level.registryAccess());
        entity.setChanged();
    }

    private static PatternDockBlockEntity placeDock(ServerLevel level, BlockPos at, RouterTier tier) {
        level.setBlockAndUpdate(at, ModBlocks.PATTERN_DOCK.get().defaultBlockState().setValue(RouterBlock.TIER, tier));
        PLACED.add(at);
        return (PatternDockBlockEntity) level.getBlockEntity(at);
    }

    /** O Pattern Provider do AE2 encostado em cada lado dado (sem o AE2, nada: os lados ficam sem provedor). */
    private static void providers(ServerLevel level, BlockPos at, Direction... sides) {
        Block provider = BuiltInRegistries.BLOCK.get(ResourceLocation.parse("ae2:pattern_provider"));
        if (provider == Blocks.AIR) {
            return;
        }
        for (Direction side : sides) {
            BlockPos pos = at.relative(side);
            level.setBlockAndUpdate(pos, provider.defaultBlockState());
            PLACED.add(pos);
        }
    }

    /** {@code count} posições em x a partir de {@code dx}, na linha {@code dz} da Doca. */
    private static List<BlockPos> row(BlockPos at, int dx, int dz, int count) {
        List<BlockPos> list = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            list.add(at.offset(dx + i, 0, dz));
        }
        return list;
    }

    /** {@code count} posições em z a partir de {@code dz}, na coluna {@code dx} da Doca. */
    private static List<BlockPos> column(BlockPos at, int dx, int dz, int count) {
        List<BlockPos> list = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            list.add(at.offset(dx, 0, dz + i));
        }
        return list;
    }

    /** Põe {@code block} em cada posição dentro do mundo e vincula todas ao lado. */
    private static void link(ServerLevel level, PatternDockBlockEntity dock, int side, Block block,
            List<BlockPos> positions) {
        ResourceLocation id = BuiltInRegistries.BLOCK.getKey(block);
        for (BlockPos pos : positions) {
            if (!level.isOutsideBuildHeight(pos)) {
                level.setBlockAndUpdate(pos, block.defaultBlockState());
                PLACED.add(pos);
            }
            dock.link(side, pos, id, -1);
        }
    }

    /** A máquina de outro mod, ou {@code fallback} sem ele. */
    private static Block machine(String id, Block fallback) {
        Block block = BuiltInRegistries.BLOCK.get(ResourceLocation.parse(id));
        return block == Blocks.AIR ? fallback : block;
    }

    /** As {@code count} primeiras máquinas do lado trabalharam agora. */
    private static void working(PatternDockBlockEntity dock, int side, int count, long now) {
        DockSide dockSide = dock.side(side);
        for (int i = 0; i < count; i++) {
            dockSide.machines().get(i).setLastActive(now);
        }
    }

    private static void clear(ServerLevel level) {
        for (int i = PLACED.size() - 1; i >= 0; i--) {
            level.setBlockAndUpdate(PLACED.get(i), Blocks.AIR.defaultBlockState());
        }
        PLACED.clear();
    }

    private static void restoreConfig() {
        if (savedRadius != null) {
            Config.TIERS.get(RouterTier.EMERALD).machineRadius().set(savedRadius);
            Config.TIERS.get(RouterTier.EMERALD).maxMachines().set(savedMax);
            savedRadius = null;
            savedMax = null;
        }
    }

    /** A foto no claro e no escuro, cada uma sem nenhum texto cortado. */
    private static void shot(List<Step> list, String name) {
        list.add(capture(name));
        list.add(noClip(name));
        list.add(E2eDarkSteps.themeClick(name + ": tema escuro", GuiTheme.DARK));
        list.add(capture(name + "-escuro"));
        list.add(noClip(name + "-escuro"));
        list.add(E2eDarkSteps.themeClick(name + ": tema claro", GuiTheme.LIGHT));
    }

    /** A foto com o mouse numa linha (a dica aberta). */
    private static void hovered(List<Step> list, String name, int row) {
        list.add(new Step("captura " + name, STEP_TIMEOUT_MS, () -> {
            int[] point = screen().rowCenter(row);
            moveMouse(point[0], point[1]);
        }, () -> {
            if (stepTicks < 8) {
                return false;
            }
            capture(Minecraft.getInstance(), name);
            return true;
        }, () -> name + ".png"));
        list.add(noClip(name));
    }

    /** Nenhum texto cortado no último quadro. */
    private static Step noClip(String name) {
        return new Step(name + ": nenhum texto cortado", STEP_TIMEOUT_MS, () -> {
        }, () -> stepTicks >= 2 && GuiText.clipCount() == 0, () -> GuiText.clipCount() + " texto(s) cortado(s)");
    }

    private static Step open(String name, BlockPos[] pos, Predicate<DockStatePayload> ready) {
        return new Step(name, STEP_TIMEOUT_MS, () -> useOn(pos[0]), () -> {
            if (Minecraft.getInstance().screen == null && stepTicks > 0 && stepTicks % OPEN_RETRY_TICKS == 0) {
                useOn(pos[0]);
            }
            return Minecraft.getInstance().screen instanceof DockScreen screen
                    && screen.getMenu().pos().equals(pos[0]) && screen.getMenu().state() != null
                    && ready.test(screen.getMenu().state());
        }, () -> "tela " + describe(Minecraft.getInstance().screen) + ", estado " + describeState());
    }

    private static DockScreen screen() throws StepFailure {
        if (Minecraft.getInstance().screen instanceof DockScreen screen) {
            return screen;
        }
        throw new StepFailure("sem a tela da Doca: " + describe(Minecraft.getInstance().screen));
    }

    private static DockStatePayload.Line line(DockStatePayload state, int side) {
        return state.lines().get(side);
    }

    private static DockStatus.State state(DockStatePayload state, int side) {
        return DockStatus.State.values()[line(state, side).state()];
    }

    private static String describeState() {
        if (!(Minecraft.getInstance().screen instanceof DockScreen screen) || screen.getMenu().state() == null) {
            return "nenhum";
        }
        DockStatePayload state = screen.getMenu().state();
        StringBuilder text = new StringBuilder(state.total() + "/" + state.max() + " raio " + state.radius());
        for (int side : DockColors.ORDER) {
            DockStatePayload.Line line = line(state, side);
            text.append("; ").append(Direction.values()[side]).append(' ').append(state(state, side))
                    .append(' ').append(line.linked()).append('/').append(line.working()).append(" desc ")
                    .append(line.unloaded()).append(" fora ").append(line.outOfReach());
        }
        return text.toString();
    }
}
