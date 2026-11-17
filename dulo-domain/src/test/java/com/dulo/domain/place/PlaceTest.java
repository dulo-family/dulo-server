package com.dulo.domain.place;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class PlaceTest {

    @Test
    void stripsTheNameAndSortsByItInLowerCase() {
        final Place place = Place.create("  Ünterstadt ", PlaceType.TOWN, null, null);

        assertThat(place.name()).isEqualTo("Ünterstadt");
        assertThat(place.sortName()).isEqualTo("ünterstadt");
        assertThat(place.version()).isZero();
    }

    @Test
    void needsANameAndCantLieInItself() {
        assertThatThrownBy(() -> Place.create(" ", PlaceType.TOWN, null, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("required");
        assertThatThrownBy(() -> Place.create("x".repeat(201), PlaceType.TOWN, null, null))
                .isInstanceOf(IllegalArgumentException.class);
        final PlaceId id = PlaceId.newId();
        assertThatThrownBy(() -> new Place(id, "Loop", PlaceType.TOWN, null, id, 0))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new Place(id, "Old", PlaceType.TOWN, null, null, -1))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void canLeaveItsEnclosingPlace() {
        final Place village = Place.create("Dorf", PlaceType.VILLAGE, null, PlaceId.newId());

        assertThat(village.withoutEnclosure().enclosedBy()).isNull();
        assertThat(village.withoutEnclosure().id()).isEqualTo(village.id());
    }

    @ParameterizedTest
    @CsvSource({"-90.1, 0", "90.1, 0", "0, -180.1", "0, 180.1", "NaN, 0", "0, NaN"})
    void coordinatesAreOnEarth(final double latitude, final double longitude) {
        assertThatThrownBy(() -> new Coordinates(latitude, longitude))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void typesHaveStableKeys() {
        assertThat(PlaceType.CEMETERY.key()).isEqualTo("cemetery");
        assertThat(PlaceType.fromKey("Parish")).contains(PlaceType.PARISH);
        assertThat(PlaceType.fromKey("castle")).isEmpty();
        assertThat(PlaceId.parse(new PlaceId(PlaceId.newId().value()).toString())).isNotNull();
    }
}
