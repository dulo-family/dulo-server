package com.dulo.core.audit.port.in;

import com.dulo.domain.audit.ChangeEvent;
import com.dulo.domain.audit.EntityType;
import com.dulo.domain.shared.CursorPage;
import com.dulo.domain.user.UserId;

import org.jspecify.annotations.Nullable;

import java.time.Instant;

/**
 * Reads the audit history of the whole instance. Needs view access to the {@code audit-log}
 * feature. Changes to administrative records (the tree settings, roles and users) are visible only
 * to callers who may also view the {@code administration} feature.
 */
public interface QueryAuditLogUseCase {

    /**
     * One page of the matching changes, newest first.
     *
     * @param after the cursor of the previous page, or null for the first page
     * @param limit the page size, see {@link CursorPage#checkLimit(int)}
     * @throws IllegalArgumentException for an invalid cursor or limit, or {@code from} not before
     *     {@code to}
     * @throws com.dulo.domain.access.AccessDeniedException if the caller may not view the audit
     *     log, or filters by an administrative type without view access to {@code administration}
     */
    CursorPage<ChangeEvent> changes(
            UserId caller, AuditFilter filter, @Nullable String after, int limit);

    /**
     * Which changes to read. Null values don't filter.
     *
     * @param entityId the record's id; combine it with the type, since role keys are not unique
     *     across types
     * @param from only changes at or after this instant
     * @param to only changes before this instant
     */
    record AuditFilter(
            @Nullable EntityType entityType,
            @Nullable String entityId,
            @Nullable UserId actor,
            @Nullable Instant from,
            @Nullable Instant to) {

        public AuditFilter {
            if (from != null && to != null && !from.isBefore(to)) {
                throw new IllegalArgumentException("'from' must be before 'to'");
            }
        }

        /** No filter: all changes the caller may see. */
        public static AuditFilter all() {
            return new AuditFilter(null, null, null, null, null);
        }
    }
}
