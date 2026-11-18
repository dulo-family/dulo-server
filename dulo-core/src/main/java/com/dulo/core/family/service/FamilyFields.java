package com.dulo.core.family.service;

import com.dulo.core.audit.recording.FieldReader;
import com.dulo.core.audit.recording.RestorableFields;
import com.dulo.domain.audit.EntityType;
import com.dulo.domain.family.Child;
import com.dulo.domain.family.ChildRelation;
import com.dulo.domain.family.Family;
import com.dulo.domain.family.FamilyId;
import com.dulo.domain.family.RelationshipType;
import com.dulo.domain.person.PersonId;

import org.jspecify.annotations.Nullable;

import java.util.LinkedHashMap;
import java.util.Map;

/** Families in the audit history. */
public final class FamilyFields implements RestorableFields<Family> {

    public static final FamilyFields INSTANCE = new FamilyFields();

    /** The field that holds the partners, as links name it. */
    public static final String PARTNERS = "partners";

    /** The field that holds the children, as links name it. */
    public static final String CHILDREN = "children";

    private FamilyFields() {}

    @Override
    public EntityType entityType() {
        return EntityType.FAMILY;
    }

    @Override
    public String id(final Family family) {
        return family.id().toString();
    }

    @Override
    public long version(final Family family) {
        return family.version();
    }

    /** Partners are a list of person ids, children a list of {@code {person, relation}}. */
    @Override
    public Map<String, @Nullable Object> fields(final Family family) {
        final Map<String, @Nullable Object> fields = new LinkedHashMap<>();
        fields.put(PARTNERS, family.partners().stream().map(PersonId::toString).toList());
        fields.put("relationshipType", family.relationshipType().key());
        fields.put(
                CHILDREN,
                family.children().stream()
                        .map(child ->
                                Map.of(
                                        "person", child.person().toString(),
                                        "relation", child.relation().key()))
                        .toList());
        return fields;
    }

    @Override
    public Family fromFields(final String id, final Map<String, Object> fields) {
        return new Family(
                FamilyId.parse(id),
                FieldReader.list(fields, PARTNERS, partner -> PersonId.parse((String) partner)),
                FieldReader.constant(
                        FieldReader.text(fields, "relationshipType"),
                        "relationship type",
                        RelationshipType::fromKey),
                FieldReader.list(
                        fields,
                        CHILDREN,
                        element -> {
                            final Map<?, ?> child = FieldReader.asMap(element);
                            return new Child(
                                    PersonId.parse(FieldReader.text(child, "person")),
                                    FieldReader.constant(
                                            FieldReader.text(child, "relation"),
                                            "child relation",
                                            ChildRelation::fromKey));
                        }),
                0);
    }
}
