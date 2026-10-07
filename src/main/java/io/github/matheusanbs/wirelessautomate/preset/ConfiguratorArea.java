package io.github.matheusanbs.wirelessautomate.preset;

import io.github.matheusanbs.wirelessautomate.block.RouterBlockEntity;
import io.github.matheusanbs.wirelessautomate.item.ConfiguratorItem;
import io.github.matheusanbs.wirelessautomate.linker.LinkerActions;
import io.github.matheusanbs.wirelessautomate.linker.LinkerArea;
import io.github.matheusanbs.wirelessautomate.linker.LinkerBox;
import io.github.matheusanbs.wirelessautomate.linker.LinkerProblem;
import io.github.matheusanbs.wirelessautomate.linker.LinkerScan;
import io.github.matheusanbs.wirelessautomate.network.ResourceType;
import io.github.matheusanbs.wirelessautomate.network.RouterPreset;
import io.github.matheusanbs.wirelessautomate.registry.ModDataComponents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

/**
 * Colar em área do Configurador, no servidor: marcar os cantos com a varinha no modo Área e colar a
 * cópia em todos os roteadores da área presos ao mesmo tipo de máquina do roteador copiado. A área
 * segue as regras e os limites do Vinculador ({@link LinkerActions#checkArea}, config
 * {@code linker.maxAreaVolume} e {@code linker.maxDistance}) e a busca é a mesma ({@link LinkerScan}:
 * só chunks carregados, sem visitar bloco a bloco).
 */
public final class ConfiguratorArea {
    private static final String KEY = "item.wirelessautomate.configurator.area.";

    private ConfiguratorArea() {
    }

    /**
     * Resultado de colar numa área.
     *
     * @param applied         roteadores configurados
     * @param otherMachine    roteadores presos a outra máquina, que ficaram como estavam
     * @param protectedCount  roteadores em área protegida para o jogador
     * @param unloadedChunks  chunks da área descarregados (os roteadores deles ficaram de fora)
     * @param droppedNetworks alguma aba ficou com a rede de antes (de outro dono ou removida)
     * @param type            a aba colada (o seletor da varinha); {@code null} = todas
     */
    public record Outcome(LinkerProblem problem, int applied, int otherMachine, int protectedCount,
            int unloadedChunks, boolean droppedNetworks, @Nullable ResourceType type) {
        static Outcome refused(LinkerProblem problem) {
            return new Outcome(problem, 0, 0, 0, 0, false, null);
        }

        public boolean ok() {
            return problem == LinkerProblem.NONE;
        }
    }

    /** Id do bloco da máquina do roteador ({@code minecraft:air} sem máquina). */
    public static ResourceLocation machine(ServerLevel level, RouterBlockEntity router) {
        return BuiltInRegistries.BLOCK.getKey(level.getBlockState(router.machinePos()).getBlock());
    }

    /** Canto 1 se não há área (ou ela está completa, ou é de outra dimensão); senão, o canto 2. */
    public static Component markCorner(ServerPlayer player, ItemStack stack, BlockPos pos) {
        LinkerArea area = ConfiguratorItem.area(stack);
        if (area == null || area.complete() || !area.dimension().equals(player.level().dimension())) {
            ConfiguratorItem.setArea(stack, LinkerArea.firstCorner(player.level().dimension(), pos));
            click(player, pos, 1.1F);
            return Component.translatable(KEY + "corner1", LinkerActions.position(pos));
        }
        LinkerArea next = area.withSecond(pos);
        LinkerBox box = next.box();
        if (box.volume() > LinkerActions.maxVolume()) {
            return Component.translatable(KEY + "too_big", box.volume(), LinkerActions.maxVolume());
        }
        ConfiguratorItem.setArea(stack, next);
        click(player, pos, 1.4F);
        String size = box.sizeX() + "×" + box.sizeY() + "×" + box.sizeZ();
        ServerLevel level = player.serverLevel();
        LinkerScan scan = LinkerScan.of(level, box);
        ResourceLocation machine = ConfiguratorItem.machine(stack);
        if (!stack.has(ModDataComponents.PRESET.get())) {
            return Component.translatable(KEY + "corner2_no_copy", size, scan.routers().size());
        }
        int same = 0;
        for (RouterBlockEntity router : scan.routers()) {
            if (machine == null || machine.equals(machine(level, router))) {
                same++;
            }
        }
        return Component.translatable(KEY + "corner2", size, scan.routers().size(), same);
    }

    private static void click(ServerPlayer player, BlockPos pos, float pitch) {
        player.level().playSound(null, pos, SoundEvents.UI_BUTTON_CLICK.value(), SoundSource.PLAYERS, 0.4F, pitch);
    }

    /**
     * Cola a cópia da varinha em todos os roteadores carregados da área presos à mesma máquina do
     * roteador copiado (sem máquina gravada, em todos). As redes seguem {@link PresetApplier}. Com um
     * tipo no seletor da varinha, só aquela aba é colada.
     * Quem chama já conferiu que a varinha tem uma cópia.
     */
    public static Outcome paste(ServerPlayer player, ItemStack stack, RouterPreset preset) {
        LinkerArea area = ConfiguratorItem.area(stack);
        LinkerProblem problem = LinkerActions.checkArea(player, area);
        if (problem != LinkerProblem.NONE) {
            return Outcome.refused(problem);
        }
        ResourceType type = ConfiguratorItem.type(stack);
        PresetApplier.Checked checked = PresetApplier.check(player, preset, type);
        ResourceLocation machine = ConfiguratorItem.machine(stack);
        ServerLevel level = player.serverLevel();
        LinkerScan scan = LinkerScan.of(level, area.box());
        int applied = 0;
        int otherMachine = 0;
        int protectedCount = 0;
        for (RouterBlockEntity router : scan.routers()) {
            if (machine != null && !machine.equals(machine(level, router))) {
                otherMachine++;
            } else if (!level.mayInteract(player, router.getBlockPos())) {
                protectedCount++;
            } else {
                checked.preset().applyTo(router, type);
                applied++;
            }
        }
        if (applied > 0) {
            level.playSound(null, player.blockPosition(), SoundEvents.EXPERIENCE_ORB_PICKUP, SoundSource.PLAYERS,
                    0.5F, 1.4F);
        }
        return new Outcome(LinkerProblem.NONE, applied, otherMachine, protectedCount, scan.unloadedChunks(),
                checked.droppedAny(), type);
    }

    /** "Colado em 12 roteadores · só a aba Fluidos · 3 em outra máquina ficaram como estavam · ...", ou o problema da área. */
    public static Component message(Outcome outcome) {
        if (!outcome.ok()) {
            return problem(outcome.problem());
        }
        MutableComponent text = Component.translatable(KEY + "pasted", outcome.applied());
        Component only = ConfiguratorItem.onlyTab(outcome.type());
        if (only != null) {
            text.append(" · ").append(only);
        }
        if (outcome.otherMachine() > 0) {
            text.append(" · ").append(Component.translatable(KEY + "other_machine", outcome.otherMachine()));
        }
        if (outcome.protectedCount() > 0) {
            text.append(" · ").append(Component.translatable(KEY + "protected", outcome.protectedCount()));
        }
        if (outcome.unloadedChunks() > 0) {
            text.append(" · ").append(Component.translatable(KEY + "unloaded", outcome.unloadedChunks()));
        }
        if (outcome.droppedNetworks()) {
            text.append(" · ").append(Component.translatable(KEY + "networks_dropped"));
        }
        return text;
    }

    public static Component problem(LinkerProblem problem) {
        return switch (problem) {
            case TOO_BIG -> Component.translatable(KEY + "problem.too_big", LinkerActions.maxVolume());
            case TOO_FAR -> Component.translatable(KEY + "problem.too_far", LinkerActions.maxDistance());
            default -> Component.translatable(KEY + "problem." + problem.key());
        };
    }

    /** A área da varinha, se houver, como texto curto ("5×3×9"); {@code null} sem os dois cantos. */
    public static @Nullable String size(ItemStack stack) {
        LinkerArea area = ConfiguratorItem.area(stack);
        LinkerBox box = area == null ? null : area.box();
        return box == null ? null : box.sizeX() + "×" + box.sizeY() + "×" + box.sizeZ();
    }
}
