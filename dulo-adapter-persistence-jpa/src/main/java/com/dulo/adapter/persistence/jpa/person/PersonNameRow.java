package com.dulo.adapter.persistence.jpa.person;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

import org.jspecify.annotations.Nullable;

/** Row of the {@code person_name} table: one of a person's names. */
@Embeddable
record PersonNameRow(
        @Column(name = "type", length = 20, nullable = false) String type,
        @Column(name = "given", length = 100) @Nullable String given,
        @Column(name = "surname", length = 100) @Nullable String surname,
        @Column(name = "prefix", length = 100) @Nullable String prefix,
        @Column(name = "suffix", length = 100) @Nullable String suffix,
        @Column(name = "nickname", length = 100) @Nullable String nickname,
        @Column(name = "search_text", length = 520, nullable = false) String searchText) {}
