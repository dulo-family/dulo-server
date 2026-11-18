package com.dulo.core.family.service;

import com.dulo.core.access.service.AccessPolicy;
import com.dulo.core.audit.port.out.ChangeEventRepository;
import com.dulo.core.audit.recording.ChangeRecorder;
import com.dulo.core.audit.recording.ChangeSet;
import com.dulo.core.audit.recording.Replay;
import com.dulo.core.event.port.out.EventRepository;
import com.dulo.core.event.service.EventFields;
import com.dulo.core.family.port.in.ManageFamiliesUseCase;
import com.dulo.core.family.port.out.FamilyRepository;
import com.dulo.core.person.port.out.PersonRepository;
import com.dulo.core.shared.port.in.HasLinksException;
import com.dulo.core.shared.port.in.RestoreConflictException;
import com.dulo.core.shared.port.in.VersionRequiredException;
import com.dulo.core.shared.port.out.UnitOfWork;
import com.dulo.core.shared.service.Cursors;
import com.dulo.core.shared.service.References;
import com.dulo.core.shared.service.Relink;
import com.dulo.domain.access.Access;
import com.dulo.domain.audit.ChangeEvent;
import com.dulo.domain.audit.ChangeEventId;
import com.dulo.domain.audit.EntityType;
import com.dulo.domain.audit.Link;
import com.dulo.domain.event.Event;
import com.dulo.domain.event.EventId;
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

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

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
    public Family restoreFamily(
            final UserId caller,
            final FamilyId id,
            final ChangeEventId eventId,
            final @Nullable Long expectedVersion,
            final boolean confirmed) {
        return this.unitOfWork.write(() -> {
            final List<ChangeEvent> history =
                    this.changes.history(EntityType.FAMILY, id.toString());
            if (history.isEmpty()) {
                throw new NotFoundException("Family", id);
            }
            final Optional<Family> current = this.families.find(id);
            this.accessPolicy.require(
                    caller, Feature.FAMILIES, current.isPresent() ? Access.EDIT : Access.CREATE);
            Family target =
                    FamilyFields.INSTANCE.fromFields(
                            id.toString(), Replay.stateAfter(history, eventId));
            if (current.isPresent()) {
                if (expectedVersion == null) {
                    throw new VersionRequiredException("family", id);
                }
                checkVersion(current.get(), expectedVersion);
            }
            final List<Link> conflicts = new ArrayList<>();
            for (final PersonId member : target.members()) {
                if (this.people.find(member).isEmpty()) {
                    conflicts.add(
                            new Link(
                                    EntityType.PERSON,
                                    member.toString(),
                                    target.partners().contains(member)
                                            ? FamilyFields.PARTNERS
                                            : FamilyFields.CHILDREN));
                    target = target.without(member);
                }
            }
            final ChangeEvent deletion = Replay.lastDeletion(history);
            final List<Relink<?>> relinks = new ArrayList<>();
            if (deletion != null) {
                for (final Link link : deletion.removedLinks()) {
                    final Optional<Event> event =
                            this.eventRecords.find(EventId.parse(link.entityId()));
                    if (event.isEmpty()
                            || (event.get().family() != null && !id.equals(event.get().family()))) {
                        conflicts.add(link);
                    } else if (event.get().family() == null) {
                        relinks.add(
                                new Relink<>(
                                        EventFields.INSTANCE,
                                        event.get(),
                                        event.get().ofFamily(id),
                                        this.eventRecords::save));
                    }
                }
            }
            if (!conflicts.isEmpty() && !confirmed) {
                throw new RestoreConflictException("family", id, conflicts);
            }
            // The record is already as it was: nothing to change.
            if (current.isPresent()
                    && FamilyFields.INSTANCE
                            .fields(current.get())
                            .equals(FamilyFields.INSTANCE.fields(target))) {
                return current.get();
            }
            final Family stored =
                    this.families.save(
                            new Family(
                                    id,
                                    target.partners(),
                                    target.relationshipType(),
                                    target.children(),
                                    current.map(Family::version).orElse(0L)));
            final ChangeSet changeSet = this.recorder.changeSet(caller);
            changeSet.restored(FamilyFields.INSTANCE, current.orElse(null), stored);
            relinks.forEach(relink -> relink.apply(changeSet));
            return stored;
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
