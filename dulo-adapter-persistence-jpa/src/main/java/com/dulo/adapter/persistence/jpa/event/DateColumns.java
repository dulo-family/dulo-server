package com.dulo.adapter.persistence.jpa.event;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

import org.jspecify.annotations.Nullable;

/** The date columns of the {@code event} table; all null for an event without a date. */
@Embeddable
record DateColumns(
        @Column(name = "date_modifier", length = 20) @Nullable String modifier,
        @Column(name = "date_start_year") @Nullable Integer startYear,
        @Column(name = "date_start_month") @Nullable Integer startMonth,
        @Column(name = "date_start_day") @Nullable Integer startDay,
        @Column(name = "date_end_year") @Nullable Integer endYear,
        @Column(name = "date_end_month") @Nullable Integer endMonth,
        @Column(name = "date_end_day") @Nullable Integer endDay,
        @Column(name = "date_text", length = 100) @Nullable String text) {}
