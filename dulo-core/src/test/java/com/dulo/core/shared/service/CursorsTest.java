package com.dulo.core.shared.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

class CursorsTest {

    @Test
    void roundTripsValuesWithSeparatorsAndUnicode() {
        final String cursor = Cursors.encode("müller, anna", "", "01a1-x");

        assertThat(cursor).matches("[A-Za-z0-9_-]+");
        assertThat(Cursors.decode(cursor, 3)).containsExactly("müller, anna", "", "01a1-x");
    }

    @Test
    void noCursorMeansTheFirstPage() {
        assertThat(Cursors.decode(null, 1)).isNull();
        assertThat(Cursors.decode("", 1)).isNull();
        assertThat(Cursors.decodeLong(null)).isNull();
    }

    @Test
    void decodesNumbers() {
        assertThat(Cursors.decodeLong(Cursors.encode("42"))).isEqualTo(42L);
    }

    @Test
    void rejectsForeignCursors() {
        final String text =
                Base64.getUrlEncoder().encodeToString("abc".getBytes(StandardCharsets.UTF_8));

        assertThatThrownBy(() -> Cursors.decode("not base64!", 1))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Invalid cursor");
        assertThatThrownBy(() -> Cursors.decode(Cursors.encode("a", "b"), 1))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> Cursors.decodeLong(text))
                .isInstanceOf(IllegalArgumentException.class);
        // A broken escape inside a well-formed Base64 text.
        final String badEscape =
                Base64.getUrlEncoder().encodeToString("%zz".getBytes(StandardCharsets.UTF_8));
        assertThatThrownBy(() -> Cursors.decode(badEscape, 1))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
