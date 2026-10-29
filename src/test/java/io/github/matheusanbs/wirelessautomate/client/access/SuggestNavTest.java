package io.github.matheusanbs.wirelessautomate.client.access;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class SuggestNavTest {
    @Test
    void startsWithNothingChosenAndArrowsWrapAround() {
        SuggestNav nav = new SuggestNav();
        nav.reset(3);
        assertEquals(-1, nav.selected());
        nav.next();
        assertEquals(0, nav.selected());
        nav.next();
        nav.next();
        nav.next();
        assertEquals(0, nav.selected());
        nav.previous();
        assertEquals(2, nav.selected());
    }

    @Test
    void previousFromNothingGoesToTheLastLine() {
        SuggestNav nav = new SuggestNav();
        nav.reset(4);
        nav.previous();
        assertEquals(3, nav.selected());
    }

    @Test
    void anEmptyListChoosesNothing() {
        SuggestNav nav = new SuggestNav();
        nav.reset(0);
        nav.next();
        nav.previous();
        nav.select(0);
        assertEquals(-1, nav.selected());
    }

    @Test
    void aNewListClearsTheChoiceAndHoverOnlyInsideTheList() {
        SuggestNav nav = new SuggestNav();
        nav.reset(3);
        nav.select(2);
        assertEquals(2, nav.selected());
        nav.select(5);
        nav.select(-1);
        assertEquals(2, nav.selected());
        nav.reset(2);
        assertEquals(-1, nav.selected());
        assertEquals(2, nav.size());
    }
}
