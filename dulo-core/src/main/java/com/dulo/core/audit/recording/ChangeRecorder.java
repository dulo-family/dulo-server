package com.dulo.core.audit.recording;

import com.dulo.core.audit.port.out.ChangeEventRepository;
import com.dulo.domain.shared.Ids;
import com.dulo.domain.user.UserId;

import java.time.Clock;
import java.time.temporal.ChronoUnit;

/**
 * Starts the recording of an action's changes. Every write use case records what it changes, inside
 * its unit of work, so a change and its event are stored together or not at all.
 */
public class ChangeRecorder {

    private final ChangeEventRepository events;
    private final Clock clock;

    public ChangeRecorder(final ChangeEventRepository events, final Clock clock) {
        this.events = events;
        this.clock = clock;
    }

    /**
     * Records the changes of one action. Its events share the time and a correlation id.
     *
     * @param actor who acts
     */
    public ChangeSet changeSet(final UserId actor) {
        // Milliseconds: what every supported database stores without rounding.
        return new ChangeSet(
                this.events,
                actor,
                Ids.newId(),
                this.clock.instant().truncatedTo(ChronoUnit.MILLIS));
    }
}
