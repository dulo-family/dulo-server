package com.dulo.adapter.rest.event;

import com.dulo.domain.event.EventType;

/** The event type keys, for the OpenAPI document. */
final class EventTypes {

    static final String DESCRIPTION =
            "baptism, birth, burial, cremation, custom, death, divorce, education, emigration,"
                    + " engagement, immigration, marriage, military, occupation or residence.";

    private EventTypes() {}

    /**
     * @throws IllegalArgumentException for an unknown type
     */
    static EventType parse(final String key) {
        return EventType.fromKey(key)
                .orElseThrow(() ->
                        new IllegalArgumentException("Unknown event type '%s'".formatted(key)));
    }
}
