package com.dulo.core.person.service;

import com.dulo.core.access.service.AccessPolicy;
import com.dulo.core.audit.port.out.ChangeEventRepository;
import com.dulo.core.audit.recording.ChangeRecorder;
import com.dulo.core.audit.recording.ChangeSet;
import com.dulo.core.audit.recording.Replay;
import com.dulo.core.event.port.out.EventRepository;
import com.dulo.core.event.service.EventFields;
import com.dulo.core.family.port.out.FamilyRepository;
import com.dulo.core.family.service.FamilyFields;
import com.dulo.core.person.port.in.ManagePeopleUseCase;
import com.dulo.core.person.port.out.PersonRepository;
import com.dulo.core.shared.port.in.HasLinksException;
import com.dulo.core.shared.port.in.RestoreConflictException;
import com.dulo.core.shared.port.in.VersionRequiredException;
import com.dulo.core.shared.port.out.UnitOfWork;
import com.dulo.core.shared.service.Cursors;
import com.dulo.core.shared.service.Relink;
import com.dulo.core.shared.service.Searches;
import com.dulo.domain.access.Access;
import com.dulo.domain.audit.ChangeEvent;
import com.dulo.domain.audit.ChangeEventId;
import com.dulo.domain.audit.EntityType;
import com.dulo.domain.audit.Link;
import com.dulo.domain.event.Event;
import com.dulo.domain.event.EventId;
import com.dulo.domain.event.Participant;
import com.dulo.domain.event.ParticipantRole;
import com.dulo.domain.family.Child;
import com.dulo.domain.family.ChildRelation;
import com.dulo.domain.family.Family;
import com.dulo.domain.family.FamilyId;
import com.dulo.domain.feature.Feature;
import com.dulo.domain.person.Person;
import com.dulo.domain.person.PersonId;
import com.dulo.domain.person.PersonSort;
import com.dulo.domain.shared.ConcurrentModificationException;
import com.dulo.domain.shared.CursorPage;
import com.dulo.domain.shared.NotFoundException;
import com.dulo.domain.shared.PagePosition;
import com.dulo.domain.user.UserId;

import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public class PersonService implements ManagePeopleUseCase {

    private final PersonRepository people;
    private final FamilyRepository families;
    private final EventRepository eventRecords;
    private final ChangeEventRepository events;
    private final AccessPolicy accessPolicy;
    private final ChangeRecorder recorder;
    private final UnitOfWork unitOfWork;

    public PersonService(
            final PersonRepository people,
            final FamilyRepository families,
            final EventRepository eventRecords,
            final ChangeEventRepository events,
            final AccessPolicy accessPolicy,
            final ChangeRecorder recorder,
            final UnitOfWork unitOfWork) {
        this.people = people;
        this.families = families;
        this.eventRecords = eventRecords;
        this.events = events;
        this.accessPolicy = accessPolicy;
        this.recorder = recorder;
        this.unitOfWork = unitOfWork;
    }

    private static void checkVersion(final Person person, final long expectedVersion) {
        if (person.version() != expectedVersion) {
            throw new ConcurrentModificationException(
                    "Person", person.id(), expectedVersion, person.version());
        }
    }

    private static PagePosition position(final Person person, final PersonSort sort) {
        return new PagePosition(person.sortName(sort), person.id().value());
    }

    @Override
    public CursorPage<Person> people(
            final UserId caller,
            final @Nullable String search,
            final PersonSort sort,
            final @Nullable String after,
            final int limit) {
        CursorPage.checkLimit(limit);
        final PagePosition position = Cursors.decodePosition(after);
        final String normalized = Searches.normalize(search);
        return this.unitOfWork.read(() -> {
            this.accessPolicy.require(caller, Feature.PEOPLE, Access.VIEW);
            return Cursors.page(
                    this.people.find(normalized, sort, position, limit + 1),
                    limit,
                    person -> position(person, sort));
        });
    }

    @Override
    public Person person(final UserId caller, final PersonId id) {
        return this.unitOfWork.read(() -> {
            this.accessPolicy.require(caller, Feature.PEOPLE, Access.VIEW);
            return this.find(id);
        });
    }

    @Override
    public Person createPerson(final UserId caller, final PersonContent content) {
        return this.unitOfWork.write(() -> {
            this.accessPolicy.require(caller, Feature.PEOPLE, Access.CREATE);
            final Person stored =
                    this.people.save(
                            Person.create(content.names(), content.sex(), content.notes()));
            this.recorder.changeSet(caller).created(PersonFields.INSTANCE, stored);
            return stored;
        });
    }

    @Override
    public Person replacePerson(
            final UserId caller,
            final PersonId id,
            final long expectedVersion,
            final PersonContent content) {
        return this.unitOfWork.write(() -> {
            this.accessPolicy.require(caller, Feature.PEOPLE, Access.EDIT);
            final Person existing = this.find(id);
            checkVersion(existing, expectedVersion);
            final Person stored =
                    this.people.save(
                            new Person(
                                    id,
                                    content.names(),
                                    content.sex(),
                                    content.notes(),
                                    existing.version()));
            this.recorder.changeSet(caller).changed(PersonFields.INSTANCE, existing, stored);
            return stored;
        });
    }

    /** Families and events refer to people: as partners, children and participants. */
    @Override
    public void deletePerson(
            final UserId caller,
            final PersonId id,
            final long expectedVersion,
            final boolean confirmed) {
        this.unitOfWork.write(() -> {
            this.accessPolicy.require(caller, Feature.PEOPLE, Access.DELETE);
            final Person person = this.find(id);
            checkVersion(person, expectedVersion);
            final List<Family> families = this.families.findWithMember(id);
            final List<Event> events = this.eventRecords.findWithParticipant(id);
            final List<Link> links = new ArrayList<>();
            for (final Family family : families) {
                links.add(
                        new Link(
                                EntityType.FAMILY,
                                family.id().toString(),
                                family.partners().contains(id)
                                        ? FamilyFields.PARTNERS
                                        : FamilyFields.CHILDREN));
            }
            for (final Event event : events) {
                links.add(
                        new Link(
                                EntityType.EVENT, event.id().toString(), EventFields.PARTICIPANTS));
            }
            if (!links.isEmpty() && !confirmed) {
                throw new HasLinksException("person", id, links);
            }
            final ChangeSet changes = this.recorder.changeSet(caller);
            for (final Family family : families) {
                changes.changed(
                        FamilyFields.INSTANCE, family, this.families.save(family.without(id)));
            }
            for (final Event event : events) {
                changes.changed(
                        EventFields.INSTANCE, event, this.eventRecords.save(event.without(id)));
            }
            this.people.delete(person);
            changes.deleted(PersonFields.INSTANCE, person, links);
            return person;
        });
    }

    @Override
    public Person restorePerson(
            final UserId caller,
            final PersonId id,
            final ChangeEventId eventId,
            final @Nullable Long expectedVersion,
            final boolean confirmed) {
        return this.unitOfWork.write(() -> {
            final List<ChangeEvent> history = this.events.history(EntityType.PERSON, id.toString());
            if (history.isEmpty()) {
                throw new NotFoundException("Person", id);
            }
            final Optional<Person> current = this.people.find(id);
            this.accessPolicy.require(
                    caller, Feature.PEOPLE, current.isPresent() ? Access.EDIT : Access.CREATE);
            final Person target =
                    PersonFields.INSTANCE.fromFields(
                            id.toString(), Replay.stateAfter(history, eventId));
            if (current.isPresent()) {
                if (expectedVersion == null) {
                    throw new VersionRequiredException("person", id);
                }
                checkVersion(current.get(), expectedVersion);
            }
            final List<Link> conflicts = new ArrayList<>();
            final ChangeEvent deletion = Replay.lastDeletion(history);
            final List<Relink<?>> relinks =
                    deletion == null ? List.of() : this.relinks(id, deletion, conflicts);
            if (!conflicts.isEmpty() && !confirmed) {
                throw new RestoreConflictException("person", id, conflicts);
            }
            // The record is already as it was: nothing to change.
            if (current.isPresent()
                    && PersonFields.INSTANCE
                            .fields(current.get())
                            .equals(PersonFields.INSTANCE.fields(target))) {
                return current.get();
            }
            final Person stored =
                    this.people.save(
                            new Person(
                                    id,
                                    target.names(),
                                    target.sex(),
                                    target.notes(),
                                    current.map(Person::version).orElse(0L)));
            final ChangeSet changes = this.recorder.changeSet(caller);
            changes.restored(PersonFields.INSTANCE, current.orElse(null), stored);
            relinks.forEach(relink -> relink.apply(changes));
            return stored;
        });
    }

    @Override
    public List<ChangeEvent> personHistory(final UserId caller, final PersonId id) {
        return this.unitOfWork.read(() -> {
            this.accessPolicy.require(caller, Feature.PEOPLE, Access.VIEW);
            final List<ChangeEvent> history = this.events.history(EntityType.PERSON, id.toString());
            if (history.isEmpty()) {
                throw new NotFoundException("Person", id);
            }
            return history;
        });
    }

    /**
     * The links the deletion removed that can come back: into families that still exist and have
     * room, and events that still exist. The others are added to the conflicts.
     */
    private List<Relink<?>> relinks(
            final PersonId id, final ChangeEvent deletion, final List<Link> conflicts) {
        final List<Relink<?>> relinks = new ArrayList<>();
        for (final Link link : deletion.removedLinks()) {
            final Relink<?> relink =
                    switch (link.entityType()) {
                        case FAMILY -> this.familyRelink(id, link, deletion);
                        case EVENT -> this.eventRelink(id, link, deletion);
                        default -> null;
                    };
            if (relink != null) {
                relinks.add(relink);
            } else if (!this.isLinked(id, link)) {
                conflicts.add(link);
            }
        }
        return relinks;
    }

    /** Whether the other record refers to the person already, so there is nothing to bring back. */
    private boolean isLinked(final PersonId id, final Link link) {
        return switch (link.entityType()) {
            case FAMILY ->
                    this.families
                            .find(FamilyId.parse(link.entityId()))
                            .filter(family -> family.members().contains(id))
                            .isPresent();
            case EVENT ->
                    this.eventRecords
                            .find(EventId.parse(link.entityId()))
                            .filter(event ->
                                    event.participants().stream()
                                            .anyMatch(p -> p.person().equals(id)))
                            .isPresent();
            default -> false;
        };
    }

    /** The person back in the family, or null if the family is gone, has room or has them. */
    private @Nullable Relink<Family> familyRelink(
            final PersonId id, final Link link, final ChangeEvent deletion) {
        final Family family = this.families.find(FamilyId.parse(link.entityId())).orElse(null);
        if (family == null || family.members().contains(id)) {
            return null;
        }
        final Family relinked = this.relinked(family, id, link.field(), deletion);
        return relinked == null
                ? null
                : new Relink<>(FamilyFields.INSTANCE, family, relinked, this.families::save);
    }

    /** The person back in the event, or null if the event is gone, full or has them. */
    private @Nullable Relink<Event> eventRelink(
            final PersonId id, final Link link, final ChangeEvent deletion) {
        final Event event = this.eventRecords.find(EventId.parse(link.entityId())).orElse(null);
        if (event == null
                || event.participants().size() >= Event.MAX_PARTICIPANTS
                || event.participants().stream().anyMatch(p -> p.person().equals(id))) {
            return null;
        }
        final Map<?, ?> removed =
                this.removedEntry(EntityType.EVENT, event.id().toString(), deletion, id);
        final ParticipantRole role =
                removed == null
                        ? ParticipantRole.OTHER
                        : ParticipantRole.fromKey(String.valueOf(removed.get("role")))
                                .orElse(ParticipantRole.OTHER);
        return new Relink<>(
                EventFields.INSTANCE,
                event,
                event.withParticipant(new Participant(id, role)),
                this.eventRecords::save);
    }

    /** The family with the person back as partner or child, or null if it has no room. */
    private @Nullable Family relinked(
            final Family family,
            final PersonId id,
            final String field,
            final ChangeEvent deletion) {
        if (FamilyFields.PARTNERS.equals(field)) {
            return family.partners().size() < Family.MAX_PARTNERS ? family.withPartner(id) : null;
        }
        if (family.children().size() >= Family.MAX_CHILDREN) {
            return null;
        }
        final Map<?, ?> removed =
                this.removedEntry(EntityType.FAMILY, family.id().toString(), deletion, id);
        final ChildRelation relation =
                removed == null
                        ? ChildRelation.BIRTH
                        : ChildRelation.fromKey(String.valueOf(removed.get("relation")))
                                .orElse(ChildRelation.BIRTH);
        return family.withChild(new Child(id, relation));
    }

    /** What the other record's list field held for the person before the deletion. */
    private @Nullable Map<?, ?> removedEntry(
            final EntityType type,
            final String otherId,
            final ChangeEvent deletion,
            final PersonId id) {
        final String field =
                type == EntityType.FAMILY ? FamilyFields.CHILDREN : EventFields.PARTICIPANTS;
        return Replay.removedEntry(
                this.events.history(type, otherId), deletion.correlationId(), field, id.toString());
    }

    private Person find(final PersonId id) {
        return this.people.find(id).orElseThrow(() -> new NotFoundException("Person", id));
    }
}
