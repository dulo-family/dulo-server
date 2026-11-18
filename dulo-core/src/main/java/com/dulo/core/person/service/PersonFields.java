package com.dulo.core.person.service;

import com.dulo.core.audit.recording.FieldReader;
import com.dulo.core.audit.recording.RestorableFields;
import com.dulo.domain.audit.EntityType;
import com.dulo.domain.person.NameType;
import com.dulo.domain.person.Person;
import com.dulo.domain.person.PersonId;
import com.dulo.domain.person.PersonName;
import com.dulo.domain.person.Sex;

import org.jspecify.annotations.Nullable;

import java.util.LinkedHashMap;
import java.util.Map;

/** People in the audit history. */
public final class PersonFields implements RestorableFields<Person> {

    public static final PersonFields INSTANCE = new PersonFields();

    private PersonFields() {}

    /** A name as a map of its parts; parts without a value are left out. */
    private static Map<String, Object> name(final PersonName name) {
        final Map<String, @Nullable Object> parts = new LinkedHashMap<>();
        parts.put("type", name.type().key());
        parts.put("given", name.given());
        parts.put("surname", name.surname());
        parts.put("prefix", name.prefix());
        parts.put("suffix", name.suffix());
        parts.put("nickname", name.nickname());
        final Map<String, Object> present = new LinkedHashMap<>();
        parts.forEach((part, value) -> {
            if (value != null) {
                present.put(part, value);
            }
        });
        return present;
    }

    @Override
    public EntityType entityType() {
        return EntityType.PERSON;
    }

    @Override
    public String id(final Person person) {
        return person.id().toString();
    }

    @Override
    public long version(final Person person) {
        return person.version();
    }

    /** Names are a list of maps, the primary name first. */
    @Override
    public Map<String, @Nullable Object> fields(final Person person) {
        final Map<String, @Nullable Object> fields = new LinkedHashMap<>();
        fields.put("names", person.names().stream().map(PersonFields::name).toList());
        fields.put("sex", person.sex().key());
        fields.put("notes", person.notes());
        return fields;
    }

    @Override
    public Person fromFields(final String id, final Map<String, Object> fields) {
        return new Person(
                PersonId.parse(id),
                FieldReader.list(
                        fields,
                        "names",
                        element -> {
                            final Map<?, ?> name = FieldReader.asMap(element);
                            return new PersonName(
                                    FieldReader.constant(
                                            FieldReader.text(name, "type"),
                                            "name type",
                                            NameType::fromKey),
                                    FieldReader.optionalText(name, "given"),
                                    FieldReader.optionalText(name, "surname"),
                                    FieldReader.optionalText(name, "prefix"),
                                    FieldReader.optionalText(name, "suffix"),
                                    FieldReader.optionalText(name, "nickname"));
                        }),
                FieldReader.constant(FieldReader.text(fields, "sex"), "sex", Sex::fromKey),
                FieldReader.optionalText(fields, "notes"),
                0);
    }
}
