package io.github.matheusanbs.wirelessautomate.gametest;

import io.github.matheusanbs.wirelessautomate.WirelessAutomate;
import io.github.matheusanbs.wirelessautomate.network.RelativeSide;
import io.github.matheusanbs.wirelessautomate.preset.ProfileSides;
import io.github.matheusanbs.wirelessautomate.preset.ProfileSource;
import io.github.matheusanbs.wirelessautomate.range.RemoteSide;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * A conversão dos lados do perfil para as máquinas do alcance ({@link ProfileSides#toRemote}) composta com a do
 * roteador ({@link RelativeSide}) e a da máquina ({@link RemoteSide}), em todos os casos: 6 facings × 4 giros × a
 * frente da presa (Norte, Sul, Oeste, Leste e sem frente, que vale Norte) × 6 lados. A verdade é o lado da máquina de
 * origem em que fica a face absoluta do lado do roteador. Só lógica: não usa o mundo.
 */
@GameTestHolder(WirelessAutomate.MODID)
@PrefixGameTestTemplate(false)
public final class ProfileSidesGameTests {
    private static final int[] FRONTS = {-1, Direction.NORTH.ordinal(), Direction.SOUTH.ordinal(),
            Direction.WEST.ordinal(), Direction.EAST.ordinal()};

    private ProfileSidesGameTests() {
    }

    @GameTest(template = "empty", timeoutTicks = 20)
    public static void everyRouterOrientationMapsToTheSourceMachineSides(GameTestHelper helper) {
        int cases = 0;
        for (Direction facing : Direction.values()) {
            for (int spin = 0; spin < RelativeSide.SPINS; spin++) {
                for (int front : FRONTS) {
                    int frame = ProfileSource.frameOf(facing, spin, front);
                    helper.assertTrue(ProfileSides.valid(frame),
                            "sem quadro: facing " + facing + ", giro " + spin + ", frente " + front);
                    int machineFront = front < 0 ? Direction.NORTH.ordinal() : front;
                    for (RelativeSide side : RelativeSide.values()) {
                        RemoteSide expected = RemoteSide.fromAbsolute(machineFront,
                                side.toAbsolute(facing, spin).ordinal());
                        RemoteSide actual = RemoteSide.values()[ProfileSides.toRemote(side.ordinal(), frame)];
                        helper.assertTrue(expected == actual, "facing " + facing + ", giro " + spin + ", frente "
                                + front + ", lado " + side + ": esperado " + expected + ", veio " + actual);
                        cases++;
                    }
                }
            }
        }
        helper.assertTrue(cases == 720, "casos: " + cases);
        helper.succeed();
    }
}
