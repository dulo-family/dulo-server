package com.dulo.core.testing;

import com.dulo.core.family.port.out.FamilyRepository;
import com.dulo.domain.family.Family;
import com.dulo.domain.family.FamilyId;
import com.dulo.domain.person.PersonId;
import com.dulo.domain.shared.ConcurrentModificationException;
import com.dulo.domain.shared.PagePosition;

import org.jspecify.annotations.Nullable;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Keeps families in a map, sorted, filtered and versioned like the real adapter. */
public final class InMemoryFamilies implements FamilyRepository {

    private static final Comparator<Family> ORDER = Comparator.comparing(f -> f.id().toString());

    private final Map<FamilyId, Family> families = new LinkedHashMap<>();

    @Override
    public Optional<Family> find(final FamilyId id) {
        return Optional.ofNullable(this.families.get(id));
    }

    @Override
    public List<Family> find(
            final @Nullable PersonId member, final @Nullable PagePosition after, final int limit) {
        return this.families.values().stream()
                .filter(f -> member == null || f.members().contains(member))
                .filter(f ->
                        after == null || Pages.isAfter(f.id().toString(), f.id().value(), after))
                .sorted(ORDER)
                .limit(limit)
                .toList();
    }

    @Override
    public List<Family> findWithMember(final PersonId member) {
        return this.find(member, null, Integer.MAX_VALUE);
    }

    @Override
    public Family save(final Family family) {
        final long actual =
                Optional.ofNullable(this.families.get(family.id())).map(Family::version).orElse(0L);
        if (family.version() != actual) {
            throw new ConcurrentModificationException(
                    "Family", family.id(), family.version(), actual);
        }
        final boolean isNew = !this.families.containsKey(family.id());
        final Family stored =
                new Family(
                        family.id(),
                        family.partners(),
                        family.relationshipType(),
                        family.children(),
                        isNew ? 0 : family.version() + 1);
        this.families.put(stored.id(), stored);
        return stored;
    }

    @Override
    public void delete(final Family family) {
        final Family stored = this.families.get(family.id());
        if (stored == null || stored.version() != family.version()) {
            throw new ConcurrentModificationException(
                    "Family",
                    family.id(),
                    family.version(),
                    stored == null ? -1 : stored.version());
        }
        this.families.remove(family.id());
    }
}
