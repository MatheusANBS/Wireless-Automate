package io.github.matheusanbs.wirelessautomate.resonator;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.matheusanbs.wirelessautomate.resonator.ResonatorBlueprint.Layout;
import io.github.matheusanbs.wirelessautomate.resonator.ResonatorLayout.Member;
import io.github.matheusanbs.wirelessautomate.resonator.ResonatorLayout.Part;
import java.util.List;
import org.junit.jupiter.api.Test;

class ResonatorBillTest {
    @Test
    void vitrineContaAsPecas() {
        ResonatorBill bill = ResonatorBill.of(Layout.VITRINE);
        assertEquals(85, bill.count(Part.CASING));
        assertEquals(9, bill.count(Part.GLASS));
        assertOne(bill);
    }

    @Test
    void faixaContaAsPecas() {
        ResonatorBill bill = ResonatorBill.of(Layout.FAIXA);
        assertEquals(82, bill.count(Part.CASING));
        assertEquals(12, bill.count(Part.GLASS));
        assertOne(bill);
    }

    @Test
    void montaComNoventaEOitoMembros() {
        assertEquals(98, ResonatorBill.of(Layout.VITRINE).members().size());
        assertEquals(98, ResonatorBill.of(Layout.FAIXA).members().size());
    }

    @Test
    void camadasEOrdemEstavel() {
        ResonatorBill bill = ResonatorBill.of(Layout.VITRINE);
        List<Member> first = bill.upTo(1);
        assertTrue(!first.isEmpty() && first.stream().allMatch(member -> member.y() == 0));
        assertEquals(25, first.size());
        assertEquals(98, bill.upTo(5).size());
        assertEquals(bill.members(), bill.upTo(5));
        for (int i = 1; i < bill.members().size(); i++) {
            Member a = bill.members().get(i - 1);
            Member b = bill.members().get(i);
            assertTrue(a.x() < b.x() || a.x() == b.x() && (a.y() < b.y() || a.y() == b.y() && a.z() < b.z()));
        }
    }

    @Test
    void partePorPosicao() {
        ResonatorBill bill = ResonatorBill.of(Layout.VITRINE);
        assertEquals(Part.CORE, bill.at(2, 2, 4));
        assertEquals(Part.ENERGY_PORT, bill.at(2, 2, 0));
    }

    private static void assertOne(ResonatorBill bill) {
        assertEquals(1, bill.count(Part.CORE));
        assertEquals(1, bill.count(Part.ENERGY_PORT));
        assertEquals(1, bill.count(Part.CRYSTAL_PORT));
        assertEquals(1, bill.count(Part.TUNER));
    }
}
