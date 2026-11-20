package com.dulo.service.config;

import org.jspecify.annotations.Nullable;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.boot.context.properties.source.ConfigurationProperty;
import org.springframework.boot.context.properties.source.ConfigurationPropertyName;
import org.springframework.boot.context.properties.source.ConfigurationPropertySource;
import org.springframework.boot.context.properties.source.ConfigurationPropertySources;
import org.springframework.boot.context.properties.source.IterableConfigurationPropertySource;
import org.springframework.boot.origin.PropertySourceOrigin;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.PropertySource;
import org.springframework.core.env.StandardEnvironment;

import java.lang.reflect.Parameter;
import java.lang.reflect.RecordComponent;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;

/**
 * Renders the merged Dulo configuration as YAML. Every value is annotated with every source that
 * sets it, lowest priority first ({@code default → file /config/dulo.yaml → environment variable
 * DULO_PUBLIC_URL}): the last one wins. Secrets are masked.
 */
public final class ConfigurationReport {

    static final String MASK = "****";
    static final String DEFAULT_SOURCE = "default";
    static final String SEPARATOR = " → ";
    private static final String ROOT = "dulo";

    private final ConfigurableEnvironment environment;

    public ConfigurationReport(final ConfigurableEnvironment environment) {
        this.environment = environment;
    }

    /** How a source that sets a property is named in the report, e.g. "file /config/dulo.yaml". */
    private static String describe(
            final PropertySource<?> source, final List<ConfigurationProperty> properties) {
        final String name = source.getName();
        if (StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME.equals(name)) {
            return (properties.size() == 1 ? "environment variable " : "environment variables ")
                    + originalNames(properties);
        }
        if (StandardEnvironment.SYSTEM_PROPERTIES_PROPERTY_SOURCE_NAME.equals(name)) {
            return (properties.size() == 1 ? "system property " : "system properties ")
                    + originalNames(properties);
        }
        if ("commandLineArgs".equals(name)) {
            return "command line";
        }
        if (name.contains("classpath")) {
            return DEFAULT_SOURCE;
        }
        if (name.contains("file [")) {
            return "file " + name.substring(name.indexOf("file [") + 6, name.indexOf(']'));
        }
        return name;
    }

    /** The names as the source spells them, e.g. DULO_PUBLIC_URL rather than dulo.public-url. */
    private static String originalNames(final List<ConfigurationProperty> properties) {
        return String.join(
                ", ",
                properties.stream()
                        .map(property ->
                                property.getOrigin() instanceof final PropertySourceOrigin origin
                                        ? origin.getPropertyName()
                                        : property.getName().toString())
                        .sorted()
                        .toList());
    }

    /** The property itself, or the elements of a list, that a source sets. */
    private static List<ConfigurationProperty> propertiesIn(
            final ConfigurationPropertySource source, final ConfigurationPropertyName name) {
        final ConfigurationProperty property = source.getConfigurationProperty(name);
        if (property != null) {
            return List.of(property);
        }
        if (source instanceof final IterableConfigurationPropertySource iterable) {
            return iterable.stream()
                    .filter(name::isAncestorOf)
                    .map(source::getConfigurationProperty)
                    .filter(Objects::nonNull)
                    .toList();
        }
        return List.of();
    }

    /** The record components that Dulo has a default for ({@code @DefaultValue}). */
    private static boolean[] defaulted(final Class<? extends Record> type) {
        final RecordComponent[] components = type.getRecordComponents();
        final boolean[] defaulted = new boolean[components.length];
        try {
            final Parameter[] parameters =
                    type.getDeclaredConstructor(
                                    Arrays.stream(components)
                                            .map(RecordComponent::getType)
                                            .toArray(Class<?>[]::new))
                            .getParameters();
            for (int i = 0; i < parameters.length; i++) {
                defaulted[i] = parameters[i].isAnnotationPresent(DefaultValue.class);
            }
        } catch (final NoSuchMethodException e) {
            throw new IllegalStateException(e);
        }
        return defaulted;
    }

    private static @Nullable Object read(final RecordComponent component, final Record record) {
        try {
            return component.getAccessor().invoke(record);
        } catch (final ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }

    private static boolean isSecret(final Class<?> type, final String field) {
        try {
            final ConfigDoc doc = type.getDeclaredField(field).getAnnotation(ConfigDoc.class);
            return doc != null && doc.secret();
        } catch (final NoSuchFieldException _) {
            return false;
        }
    }

    static String kebab(final String camel) {
        return camel.replaceAll("([a-z0-9])([A-Z])", "$1-$2").toLowerCase(Locale.ROOT);
    }

    public String render(final DuloProperties properties) {
        final StringBuilder out = new StringBuilder();
        out.append("# Dulo configuration (secrets masked)\n");
        out.append("# Database: ").append(properties.database().kind()).append('\n');
        out.append(ROOT).append(":\n");
        this.renderRecord(out, properties, ROOT, 1);
        return out.toString();
    }

    /**
     * Every source that sets a property, lowest priority first; the last one wins. {@code default}
     * comes first when Dulo has a default for it ({@code hasDefault}, or a built-in value in
     * application.yaml), and stands alone when nothing else sets it.
     */
    public List<String> sourcesOf(final String propertyPath, final boolean hasDefault) {
        final ConfigurationPropertyName name = ConfigurationPropertyName.adapt(propertyPath, '.');
        final List<String> sources = new ArrayList<>();
        for (final ConfigurationPropertySource source :
                ConfigurationPropertySources.get(this.environment)) {
            final List<ConfigurationProperty> properties = propertiesIn(source, name);
            if (!properties.isEmpty()
                    && source.getUnderlyingSource() instanceof final PropertySource<?> underlying) {
                final String described = describe(underlying, properties);
                if (!sources.contains(described)) {
                    sources.addFirst(described);
                }
            }
        }
        if (sources.isEmpty() || hasDefault && !sources.getFirst().equals(DEFAULT_SOURCE)) {
            sources.addFirst(DEFAULT_SOURCE);
        }
        return sources;
    }

    private void renderRecord(
            final StringBuilder out, final Record record, final String path, final int depth) {
        final RecordComponent[] components = record.getClass().getRecordComponents();
        final boolean[] defaulted = defaulted(record.getClass());
        for (int i = 0; i < components.length; i++) {
            final String key = kebab(components[i].getName());
            final Object value = read(components[i], record);
            final boolean secret = isSecret(record.getClass(), components[i].getName());
            this.renderEntry(out, key, value, path + "." + key, depth, secret, defaulted[i]);
        }
    }

    private void renderEntry(
            final StringBuilder out,
            final String key,
            final @Nullable Object value,
            final String path,
            final int depth,
            final boolean secret,
            final boolean hasDefault) {
        final String indent = "  ".repeat(depth);
        switch (value) {
            case null -> out.append(indent).append(key).append(": ~\n");
            case final Record nested -> {
                out.append(indent).append(key).append(":\n");
                this.renderRecord(out, nested, path, depth + 1);
            }
            case final Map<?, ?> map when map.isEmpty() ->
                    out.append(indent)
                            .append(key)
                            .append(": {}")
                            .append(this.comment(path, hasDefault))
                            .append('\n');
            case final Map<?, ?> map -> {
                out.append(indent).append(key).append(":\n");
                new TreeMap<>(map)
                        .forEach((k, v) ->
                                this.renderEntry(
                                        out,
                                        String.valueOf(k),
                                        v,
                                        path + "." + k,
                                        depth + 1,
                                        false,
                                        false));
            }
            case final Collection<?> list ->
                    out.append(indent)
                            .append(key)
                            .append(": ")
                            .append(list.stream().map(String::valueOf).toList())
                            .append(this.comment(path, hasDefault))
                            .append('\n');
            default ->
                    out.append(indent)
                            .append(key)
                            .append(": ")
                            // Quoted: a bare * starts a YAML alias.
                            .append(secret ? '"' + MASK + '"' : value)
                            .append(this.comment(path, hasDefault))
                            .append('\n');
        }
    }

    private String comment(final String path, final boolean hasDefault) {
        return "  # " + String.join(SEPARATOR, this.sourcesOf(path, hasDefault));
    }
}
