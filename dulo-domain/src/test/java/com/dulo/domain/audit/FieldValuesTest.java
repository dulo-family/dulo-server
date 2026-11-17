package com.dulo.domain.audit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

class FieldValuesTest {

    @Test
    void keepsJsonValues() {
        assertThat(FieldValues.copy(null)).isNull();
        assertThat(FieldValues.copy("Ada")).isEqualTo("Ada");
        assertThat(FieldValues.copy(true)).isEqualTo(true);
        assertThat(FieldValues.copy(42L)).isEqualTo(42L);
        assertThat(FieldValues.copy(42)).isEqualTo(42L);
        assertThat(FieldValues.copy(48.2)).isEqualTo(48.2);
    }

    @Test
    void copiesListsAndSortsMapsByKey() {
        final List<Object> list = new ArrayList<>(List.of("a", 1));
        final Map<String, Object> map = new HashMap<>(Map.of("b", list, "a", Map.of("x", 1)));

        final Object copy = FieldValues.copy(map);
        list.add("changed");

        assertThat(copy).isEqualTo(Map.of("a", Map.of("x", 1L), "b", List.of("a", 1L)));
        assertThat(List.copyOf(((Map<?, ?>) copy).keySet())).isEqualTo(List.of("a", "b"));
        assertThatThrownBy(() -> ((Map<?, ?>) copy).clear())
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void rejectsWhatJsonCantHoldWithoutLoss() {
        assertThatThrownBy(() -> FieldValues.copy(Instant.EPOCH))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Instant");
        assertThatThrownBy(() -> FieldValues.copy(Double.NaN))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> FieldValues.copy(Arrays.asList("a", null)))
                .isInstanceOf(IllegalArgumentException.class);
        final Map<String, Object> withNull = new HashMap<>();
        withNull.put("a", null);
        assertThatThrownBy(() -> FieldValues.copy(withNull))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> FieldValues.copy(Map.of(1, "a")))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void fieldChangesNeedANameAndAllowedValues() {
        assertThat(new FieldChange("name", null, "Ada").after()).isEqualTo("Ada");
        assertThatThrownBy(() -> new FieldChange(" ", null, "Ada"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new FieldChange("at", null, Instant.EPOCH))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
