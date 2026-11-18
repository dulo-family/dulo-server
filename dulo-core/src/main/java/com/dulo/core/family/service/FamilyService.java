package com.dulo.core.family.service;

import com.dulo.core.access.service.AccessPolicy;
import com.dulo.core.audit.port.out.ChangeEventRepository;
import com.dulo.core.audit.recording.ChangeRecorder;
import com.dulo.core.audit.recording.ChangeSet;
import com.dulo.core.event.port.out.EventRepository;
import com.dulo.core.event.service.EventFields;
import com.dulo.core.family.port.in.ManageFamiliesUseCase;
import com.dulo.core.family.port.out.FamilyRepository;
import com.dulo.core.person.port.out.PersonRepository;
import com.dulo.core.shared.port.in.HasLinksException;
import com.dulo.core.shared.port.out.UnitOfWork;
import com.dulo.core.shared.service.Cursors;
import com.dulo.core.shared.service.References;
import com.dulo.domain.access.Access;
import com.dulo.domain.audit.ChangeEvent;
import com.dulo.domain.audit.EntityType;
import com.dulo.domain.audit.Link;
import com.dulo.domain.event.Event;
import com.dulo.domain.family.Family;
import com.dulo.domain.family.FamilyId;
import com.dulo.domain.feature.Feature;
import com.dulo.domain.person.PersonId;
import com.dulo.domain.shared.ConcurrentModificationException;
import com.dulo.domain.shared.CursorPage;
import com.dulo.domain.shared.NotFoundException;
import com.dulo.domain.shared.PagePosition;
import com.dulo.domain.user.UserId;

import org.jspecify.annotations.Nullable;

import java.util.List;

public class FamilyService implements ManageFamiliesUseCase {

    private final FamilyRepository families;
    private final PersonRepository people;
    private final EventRepository eventRecords;
    private final ChangeEventRepository changes;
    private final AccessPolicy accessPolicy;
    private final ChangeRecorder recorder;
    private final UnitOfWork unitOfWork;

    public FamilyService(
            final FamilyRepository families,
            final PersonRepository people,
            final EventRepository eventRecords,
            final ChangeEventRepository changes,
            final AccessPolicy accessPolicy,
            final ChangeRecorder recorder,
            final UnitOfWork unitOfWork) {
        this.families = families;
        this.people = people;
        this.eventRecords = eventRecords;
        this.changes = changes;
        this.accessPolicy = accessPolicy;
        this.recorder = recorder;
        this.unitOfWork = unitOfWork;
    }

    private static void checkVersion(final Family family, final long expectedVersion) {
        if (family.version() != expectedVersion) {
            throw new ConcurrentModificationException(
                    "Family", family.id(), expectedVersion, family.version());
        }
    }

    /** Families are sorted by id, so the id is also the sort key. */
    private static PagePosition position(final Family family) {
        return new PagePosition(family.id().toString(), family.id().value());
    }

    @Override
    public CursorPage<Family> families(
            final UserId caller,
            final @Nullable PersonId member,
            final @Nullable String after,
            final int limit) {
        CursorPage.checkLimit(limit);
        final PagePosition position = Cursors.decodePosition(after);
        return this.unitOfWork.read(() -> {
            this.accessPolicy.require(caller, Feature.FAMILIES, Access.VIEW);
            return Cursors.page(
                    this.families.find(member, position, limit + 1),
                    limit,
                    FamilyService::position);
        });
    }

    @Override
    public Family family(final UserId caller, final FamilyId id) {
        return this.unitOfWork.read(() -> {
            this.accessPolicy.require(caller, Feature.FAMILIES, Access.VIEW);
            return this.find(id);
        });
    }

    @Override
    public Family createFamily(final UserId caller, final FamilyContent content) {
        return this.unitOfWork.write(() -> {
            this.accessPolicy.require(caller, Feature.FAMILIES, Access.CREATE);
            final Family family =
                    Family.create(
                            content.partners(), content.relationshipType(), content.children());
            this.checkMembers(family);
            final Family stored = this.families.save(family);
            this.recorder.changeSet(caller).created(FamilyFields.INSTANCE, stored);
            return stored;
        });
    }

    @Override
    public Family replaceFamily(
            final UserId caller,
            final FamilyId id,
            final long expectedVersion,
            final FamilyContent content) {
        return this.unitOfWork.write(() -> {
            this.accessPolicy.require(caller, Feature.FAMILIES, Access.EDIT);
            final Family existing = this.find(id);
            checkVersion(existing, expectedVersion);
            final Family changed =
                    new Family(
                            id,
                            content.partners(),
                            content.relationshipType(),
                            content.children(),
                            existing.version());
            this.checkMembers(changed);
            final Family stored = this.families.save(changed);
            this.recorder.changeSet(caller).changed(FamilyFields.INSTANCE, existing, stored);
            return stored;
        });
    }

    @Override
    public void deleteFamily(
            final UserId caller,
            final FamilyId id,
            final long expectedVersion,
            final boolean confirmed) {
        this.unitOfWork.write(() -> {
            this.accessPolicy.require(caller, Feature.FAMILIES, Access.DELETE);
            final Family family = this.find(id);
            checkVersion(family, expectedVersion);
            final List<Event> events = this.eventRecords.findOfFamily(id);
            final List<Link> links =
                    events.stream()
                            .map(event ->
                                    new Link(
                                            EntityType.EVENT,
                                            event.id().toString(),
                                            EventFields.FAMILY))
                            .toList();
            if (!links.isEmpty() && !confirmed) {
                throw new HasLinksException("family", id, links);
            }
            final ChangeSet changeSet = this.recorder.changeSet(caller);
            for (final Event event : events) {
                changeSet.changed(
                        EventFields.INSTANCE, event, this.eventRecords.save(event.withoutFamily()));
            }
            this.families.delete(family);
            changeSet.deleted(FamilyFields.INSTANCE, family, links);
            return family;
        });
    }

    @Override
    public List<ChangeEvent> familyHistory(final UserId caller, final FamilyId id) {
        return this.unitOfWork.read(() -> {
            this.accessPolicy.require(caller, Feature.FAMILIES, Access.VIEW);
            final List<ChangeEvent> history =
                    this.changes.history(EntityType.FAMILY, id.toString());
            if (history.isEmpty()) {
                throw new NotFoundException("Family", id);
            }
            return history;
        });
    }

    private void checkMembers(final Family family) {
        References.requireExisting(
                "person", family.members(), person -> this.people.find(person).isPresent());
    }

    private Family find(final FamilyId id) {
        return this.families.find(id).orElseThrow(() -> new NotFoundException("Family", id));
    }
}
