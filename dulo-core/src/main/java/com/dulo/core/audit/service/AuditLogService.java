package com.dulo.core.audit.service;

import com.dulo.core.access.service.AccessPolicy;
import com.dulo.core.audit.port.in.QueryAuditLogUseCase;
import com.dulo.core.audit.port.out.ChangeEventRepository;
import com.dulo.core.shared.port.out.UnitOfWork;
import com.dulo.core.shared.service.Cursors;
import com.dulo.domain.access.Access;
import com.dulo.domain.access.AccessDeniedException;
import com.dulo.domain.audit.ChangeEvent;
import com.dulo.domain.audit.ChangeEventQuery;
import com.dulo.domain.audit.EntityType;
import com.dulo.domain.feature.Feature;
import com.dulo.domain.shared.CursorPage;
import com.dulo.domain.user.Actor;
import com.dulo.domain.user.UserId;

import org.jspecify.annotations.Nullable;

import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

public class AuditLogService implements QueryAuditLogUseCase {

    private final ChangeEventRepository events;
    private final AccessPolicy accessPolicy;
    private final UnitOfWork unitOfWork;

    public AuditLogService(
            final ChangeEventRepository events,
            final AccessPolicy accessPolicy,
            final UnitOfWork unitOfWork) {
        this.events = events;
        this.accessPolicy = accessPolicy;
        this.unitOfWork = unitOfWork;
    }

    /** The types the actor may see, narrowed to the filter's type. */
    private static Set<EntityType> visibleTypes(final Actor actor, final AuditFilter filter) {
        final boolean administrator =
                actor.root() || actor.permissions().allows(Feature.ADMINISTRATION, Access.VIEW);
        final EntityType type = filter.entityType();
        if (type != null) {
            if (type.administrative() && !administrator) {
                throw new AccessDeniedException(
                        AccessDeniedException.Reason.NOT_PERMITTED,
                        "Only administrators may see changes to %ss".formatted(type.key()));
            }
            return Set.of(type);
        }
        return Arrays.stream(EntityType.values())
                .filter(t -> administrator || !t.administrative())
                .collect(Collectors.toUnmodifiableSet());
    }

    @Override
    public CursorPage<ChangeEvent> changes(
            final UserId caller,
            final AuditFilter filter,
            final @Nullable String after,
            final int limit) {
        CursorPage.checkLimit(limit);
        final Long beforeSequence = Cursors.decodeLong(after);
        return this.unitOfWork.read(() -> {
            final Actor actor = this.accessPolicy.require(caller, Feature.AUDIT_LOG, Access.VIEW);
            final ChangeEventQuery query =
                    new ChangeEventQuery(
                            visibleTypes(actor, filter),
                            filter.entityId(),
                            filter.actor(),
                            filter.from(),
                            filter.to());
            // One more than asked tells whether there is a next page.
            final List<ChangeEvent> found = this.events.find(query, beforeSequence, limit + 1);
            if (found.size() <= limit) {
                return new CursorPage<>(found, null);
            }
            final List<ChangeEvent> page = found.subList(0, limit);
            return new CursorPage<>(page, Cursors.encode(Long.toString(page.getLast().sequence())));
        });
    }
}
