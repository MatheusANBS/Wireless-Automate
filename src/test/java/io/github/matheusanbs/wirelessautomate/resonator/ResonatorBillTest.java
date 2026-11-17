package io.github.matheusanbs.wirelessautomate.resonator;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.matheusanbs.wirelessautomate.resonator.ResonatorLayout.Member;
import io.github.matheusanbs.wirelessautomate.resonator.ResonatorLayout.Part;
import java.util.List;
import org.junit.jupiter.api.Test;

class ResonatorBillTest {
    @Test
    void contaAsPecasDaArmilar() {
        ResonatorBill bill = ResonatorBill.of();
        assertEquals(21, bill.count(Part.CASING));
        assertEquals(8, bill.count(Part.COLUMN));
        assertEquals(2, bill.count(Part.CRADLE));
        assertEquals(1, bill.count(Part.TUNER));
        assertEquals(1, bill.count(Part.CORE));
        assertEquals(1, bill.count(Part.ENERGY_PORT));
        assertEquals(1, bill.count(Part.CRYSTAL_PORT));
        assertEquals(35, bill.members().size());
    }

    @Test
    void camadasEOrdemEstavel() {
        ResonatorBill bill = ResonatorBill.of();
        List<Member> first = bill.upTo(1);
        assertTrue(!first.isEmpty() && first.stream().allMatch(member -> member.y() == 0));
        assertEquals(25, first.size());
        assertEquals(35, bill.upTo(5).size());
        assertEquals(bill.members(), bill.upTo(5));
        for (int i = 1; i < bill.members().size(); i++) {
            Member a = bill.members().get(i - 1);
            Member b = bill.members().get(i);
            assertTrue(a.x() < b.x() || a.x() == b.x() && (a.y() < b.y() || a.y() == b.y() && a.z() < b.z()));
        }
    }

    @Test
    void partePorPosicao() {
        ResonatorBill bill = ResonatorBill.of();
        assertEquals(Part.CORE, bill.at(2, 0, 4));
        assertEquals(Part.ENERGY_PORT, bill.at(4, 0, 2));
        assertEquals(Part.CRADLE, bill.at(2, 2, 2));
    }
}
