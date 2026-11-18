package com.dulo.core.person.port.in;

import com.dulo.domain.audit.ChangeEvent;
import com.dulo.domain.person.Person;
import com.dulo.domain.person.PersonId;
import com.dulo.domain.person.PersonName;
import com.dulo.domain.person.PersonSort;
import com.dulo.domain.person.Sex;
import com.dulo.domain.shared.CursorPage;
import com.dulo.domain.user.UserId;

import org.jspecify.annotations.Nullable;

import java.util.List;

/**
 * Lists, reads and changes people. Needs the {@code people} feature, with the access each method
 * names.
 */
public interface ManagePeopleUseCase {

    /**
     * One page of people, sorted by their primary name. Needs view access.
     *
     * @param search only people with a name that contains this text, ignoring case; null for all
     * @param sort the order; a cursor reads the next page only in the order it was made for
     * @param after the cursor of the previous page, or null for the first page
     * @param limit the page size, see {@link CursorPage#checkLimit(int)}
     * @throws IllegalArgumentException for an invalid cursor, limit or search
     * @throws com.dulo.domain.access.AccessDeniedException if the caller may not view people
     */
    CursorPage<Person> people(
            UserId caller,
            @Nullable String search,
            PersonSort sort,
            @Nullable String after,
            int limit);

    /**
     * Needs view access.
     *
     * @throws com.dulo.domain.shared.NotFoundException if the person doesn't exist
     */
    Person person(UserId caller, PersonId id);

    /** Needs create access. */
    Person createPerson(UserId caller, PersonContent content);

    /**
     * Replaces everything about the person. Needs edit access.
     *
     * @param expectedVersion the version the caller saw
     * @throws com.dulo.domain.shared.NotFoundException if the person doesn't exist
     * @throws com.dulo.domain.shared.ConcurrentModificationException if the version is stale
     */
    Person replacePerson(UserId caller, PersonId id, long expectedVersion, PersonContent content);

    /**
     * Deletes the person. Needs delete access.
     *
     * @param confirmed whether to delete them even if other records refer to them, removing those
     *     references
     * @throws com.dulo.domain.shared.NotFoundException if the person doesn't exist
     * @throws com.dulo.domain.shared.ConcurrentModificationException if the version is stale
     * @throws com.dulo.core.shared.port.in.HasLinksException if other records refer to them and the
     *     deletion is not confirmed
     */
    void deletePerson(UserId caller, PersonId id, long expectedVersion, boolean confirmed);

    /**
     * Every change to the person, oldest first, also after they were deleted. Needs view access.
     *
     * @throws com.dulo.domain.shared.NotFoundException if the person never existed
     */
    List<ChangeEvent> personHistory(UserId caller, PersonId id);

    /**
     * Everything about a person that users set.
     *
     * @param names the primary name first
     */
    record PersonContent(List<PersonName> names, Sex sex, @Nullable String notes) {

        public PersonContent {
            names = List.copyOf(names);
        }
    }
}
