package com.dulo.domain.access;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class AccessTest {

    @Test
    void resolvesKeysIgnoringCase() {
        assertThat(Access.fromKey("view")).contains(Access.VIEW);
        assertThat(Access.fromKey("DELETE")).contains(Access.DELETE);
        assertThat(Access.fromKey("own")).isEmpty();
        assertThat(Access.EDIT.key()).isEqualTo("edit");
    }
}
