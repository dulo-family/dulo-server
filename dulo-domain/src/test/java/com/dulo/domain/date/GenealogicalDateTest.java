package com.dulo.domain.date;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class GenealogicalDateTest {

    private static final PartialDate MAY_1890 = new PartialDate(1890, 5, null);

    @Test
    void partialDatesSortBeforeFullOnes() {
        assertThat(PartialDate.ofYear(1890).sortKey()).isEqualTo(18_900_000);
        assertThat(MAY_1890.sortKey()).isEqualTo(18_900_500);
        assertThat(new PartialDate(1890, 5, 17).sortKey()).isEqualTo(18_900_517);
    }

    @Test
    void partialDatesAreRealDates() {
        assertThat(new PartialDate(2024, 2, 29).day()).isEqualTo(29);
        assertThatThrownBy(() -> new PartialDate(1900, 2, 29))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("no day 29");
        assertThatThrownBy(() -> new PartialDate(0, null, null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new PartialDate(1890, 13, null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new PartialDate(1890, null, 1))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rangesNeedAnEndThatIsntBeforeTheStart() {
        final GenealogicalDate between =
                new GenealogicalDate(
                        DateModifier.BETWEEN, PartialDate.ofYear(1890), MAY_1890, "1890");

        assertThat(between.sortKey()).isEqualTo(18_900_000);
        assertThatThrownBy(() ->
                        new GenealogicalDate(
                                DateModifier.BETWEEN, MAY_1890, PartialDate.ofYear(1889), null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new GenealogicalDate(DateModifier.BETWEEN, MAY_1890, null, null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new GenealogicalDate(DateModifier.ABOUT, MAY_1890, MAY_1890, null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void aDateMayBeOnlyText() {
        final GenealogicalDate text =
                new GenealogicalDate(DateModifier.EXACT, null, null, " after the flood ");

        assertThat(text.text()).isEqualTo("after the flood");
        assertThat(text.sortKey()).isNull();
        assertThatThrownBy(() -> new GenealogicalDate(DateModifier.EXACT, null, null, " "))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new GenealogicalDate(DateModifier.ABOUT, null, null, "x"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(GenealogicalDate.exact(MAY_1890).modifier()).isEqualTo(DateModifier.EXACT);
        assertThat(DateModifier.fromKey("Between")).contains(DateModifier.BETWEEN);
    }
}
