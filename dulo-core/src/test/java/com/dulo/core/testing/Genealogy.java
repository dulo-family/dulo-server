package com.dulo.core.testing;

import com.dulo.core.access.service.AccessPolicy;
import com.dulo.core.event.service.EventService;
import com.dulo.core.family.service.FamilyService;
import com.dulo.core.person.service.PersonService;
import com.dulo.core.place.service.PlaceService;
import com.dulo.domain.access.DefaultRoles;
import com.dulo.domain.access.RoleKey;
import com.dulo.domain.feature.Feature;
import com.dulo.domain.feature.FeatureOverrides;
import com.dulo.domain.tree.TreeSettings;
import com.dulo.domain.user.UserId;
import com.dulo.domain.user.UserStatus;

import java.util.Set;

/**
 * The family tree records in memory: people, places, families and events, with their services, the
 * audit history, and a tree with all their features on.
 */
public final class Genealogy {

    private final InMemoryUsers users = new InMemoryUsers();
    private final InMemoryTrees trees =
            new InMemoryTrees(
                    TreeSettings.create(
                            "The Smiths",
                            null,
                            Set.of(
                                    Feature.EVENTS,
                                    Feature.FAMILIES,
                                    Feature.PEOPLE,
                                    Feature.PLACES)));
    private final InMemoryPeople people = new InMemoryPeople();
    private final InMemoryPlaces places = new InMemoryPlaces();
    private final InMemoryFamilies families = new InMemoryFamilies();
    private final InMemoryEvents events = new InMemoryEvents();
    private final InMemoryChangeEvents changes = new InMemoryChangeEvents();
    private final AccessPolicy accessPolicy =
            new AccessPolicy(
                    this.users,
                    new InMemoryRoles(DefaultRoles.all()),
                    this.trees,
                    FeatureOverrides::none);

    /** An active user with the role, stored. */
    public UserId user(final RoleKey role) {
        return this.users.save(Users.user(UserStatus.ACTIVE, role)).id();
    }

    public InMemoryTrees trees() {
        return this.trees;
    }

    public InMemoryPeople people() {
        return this.people;
    }

    public InMemoryPlaces places() {
        return this.places;
    }

    public InMemoryFamilies families() {
        return this.families;
    }

    public InMemoryEvents events() {
        return this.events;
    }

    public InMemoryChangeEvents changes() {
        return this.changes;
    }

    public PersonService personService() {
        return new PersonService(
                this.people,
                this.families,
                this.events,
                this.changes,
                this.accessPolicy,
                this.changes.recorder(),
                new DirectUnitOfWork());
    }

    public PlaceService placeService() {
        return new PlaceService(
                this.places,
                this.events,
                this.changes,
                this.accessPolicy,
                this.changes.recorder(),
                new DirectUnitOfWork());
    }

    public FamilyService familyService() {
        return new FamilyService(
                this.families,
                this.people,
                this.events,
                this.changes,
                this.accessPolicy,
                this.changes.recorder(),
                new DirectUnitOfWork());
    }

    public EventService eventService() {
        return new EventService(
                this.events,
                this.people,
                this.places,
                this.families,
                this.changes,
                this.accessPolicy,
                this.changes.recorder(),
                new DirectUnitOfWork());
    }
}
