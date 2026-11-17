package com.dulo.domain.shared;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

import java.util.List;

class CursorPageTest {

    @Test
    void limitsArePositiveAndBounded() {
        assertThat(CursorPage.checkLimit(1)).isEqualTo(1);
        assertThat(CursorPage.checkLimit(CursorPage.MAX_LIMIT)).isEqualTo(CursorPage.MAX_LIMIT);
        assertThatThrownBy(() -> CursorPage.checkLimit(0))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> CursorPage.checkLimit(CursorPage.MAX_LIMIT + 1))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void mapsItemsAndKeepsTheCursor() {
        final CursorPage<Integer> page = new CursorPage<>(List.of(1, 2), "next");

        assertThat(page.map(i -> i * 10)).isEqualTo(new CursorPage<>(List.of(10, 20), "next"));
    }
}
