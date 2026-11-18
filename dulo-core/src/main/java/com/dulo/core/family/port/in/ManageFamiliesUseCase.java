package com.dulo.core.family.port.in;

import com.dulo.domain.audit.ChangeEvent;
import com.dulo.domain.family.Child;
import com.dulo.domain.family.Family;
import com.dulo.domain.family.FamilyId;
import com.dulo.domain.family.RelationshipType;
import com.dulo.domain.person.PersonId;
import com.dulo.domain.shared.CursorPage;
import com.dulo.domain.user.UserId;

import org.jspecify.annotations.Nullable;

import java.util.List;

/**
 * Lists, reads and changes families. Needs the {@code families} feature, with the access each
 * method names.
 */
public interface ManageFamiliesUseCase {

    /**
     * One page of families, in the order they were created, to the millisecond. Needs view access.
     *
     * @param member only families with this person as a partner or child; null for all
     * @param after the cursor of the previous page, or null for the first page
     * @param limit the page size, see {@link CursorPage#checkLimit(int)}
     * @throws IllegalArgumentException for an invalid cursor or limit
     * @throws com.dulo.domain.access.AccessDeniedException if the caller may not view families
     */
    CursorPage<Family> families(
            UserId caller, @Nullable PersonId member, @Nullable String after, int limit);

    /**
     * Needs view access.
     *
     * @throws com.dulo.domain.shared.NotFoundException if the family doesn't exist
     */
    Family family(UserId caller, FamilyId id);

    /**
     * Needs create access.
     *
     * @throws IllegalArgumentException if a partner or child doesn't exist
     */
    Family createFamily(UserId caller, FamilyContent content);

    /**
     * Replaces everything about the family. Needs edit access.
     *
     * @param expectedVersion the version the caller saw
     * @throws com.dulo.domain.shared.NotFoundException if the family doesn't exist
     * @throws com.dulo.domain.shared.ConcurrentModificationException if the version is stale
     * @throws IllegalArgumentException if a partner or child doesn't exist
     */
    Family replaceFamily(UserId caller, FamilyId id, long expectedVersion, FamilyContent content);

    /**
     * Deletes the family; its people stay. Needs delete access.
     *
     * @param confirmed whether to delete it even if events belong to it; they then belong to no
     *     family
     * @throws com.dulo.domain.shared.NotFoundException if the family doesn't exist
     * @throws com.dulo.domain.shared.ConcurrentModificationException if the version is stale
     * @throws com.dulo.core.shared.port.in.HasLinksException if events belong to it and the
     *     deletion is not confirmed
     */
    void deleteFamily(UserId caller, FamilyId id, long expectedVersion, boolean confirmed);

    /**
     * Every change to the family, oldest first, also after it was deleted. Needs view access.
     *
     * @throws com.dulo.domain.shared.NotFoundException if the family never existed
     */
    List<ChangeEvent> familyHistory(UserId caller, FamilyId id);

    /** Everything about a family that users set. */
    record FamilyContent(
            List<PersonId> partners, RelationshipType relationshipType, List<Child> children) {

        public FamilyContent {
            partners = List.copyOf(partners);
            children = List.copyOf(children);
        }
    }
}
