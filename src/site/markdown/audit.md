# Audit history

Dulo records every change to every record: who changed what, when, and the values before and
after. This page explains how, and why it is built this way. Read it before adding a record type
or changing how one is stored.

## What is recorded

- **Family tree records:** people, families, events and places.
- **Administrative records:** the tree settings, roles and users. This covers the initial setup,
  role changes, user approvals and the roles that sign-ins assign.

Each change becomes one *change event*:

| Field           | Meaning                                                                            |
|-----------------|------------------------------------------------------------------------------------|
| `id`            | UUIDv7 of the event.                                                               |
| `at`            | When it happened, to the millisecond.                                              |
| `actor`         | The user who made the change. At sign-in, that is the user who signs in.           |
| `entityType`    | `person`, `family`, `event`, `place`, `tree`, `role` or `user`.                    |
| `entityId`      | The record's id; for roles, the role key.                                          |
| `entityVersion` | The record's version after the change, which is also its ETag.                     |
| `kind`          | `created`, `changed`, `deleted` or `restored`.                                     |
| `correlationId` | Shared by all events of one action, such as the initial setup.                     |
| `changes`       | The fields the change affected: `[{field, before, after}]`.                        |
| `removedLinks`  | For a deletion: the references to the record that the deletion removed.            |

The time of a user's last sign-in is not recorded: it changes at every sign-in and would bury
the real changes. A sign-in that changes nothing else leaves no event.

## The idea: a hybrid of event sourcing

[Event sourcing](https://martinfowler.com/eaaDev/EventSourcing.html) stores every change to an
application's state as an event, and the events are the only source of truth: the current state
is rebuilt from them. That gives a complete history for free, the state at any point in time
("temporal query"), and the ability to replay events to correct mistakes.

Dulo keeps its ordinary tables as the source of truth, and stores a complete event next to every
change, in the same transaction:

<pre class="mermaid">
sequenceDiagram
    participant C as Client
    participant S as Use case (core)
    participant R as Repository
    participant E as ChangeEventRepository
    C->>S: PATCH /api/v1/admin/roles/helpers (If-Match: "3")
    S->>S: check access and version
    rect rgba(127, 127, 127, 0.1)
        note over S,E: one transaction
        S->>R: save(role)
        R-->>S: stored role, version 4
        S->>E: append(changed: name "Helpers" → "Family helpers")
    end
    S-->>C: 200, ETag "4"
</pre>

The events are *complete*:

- a creation holds every field that has a value;
- a change holds every field that differs, with its value before and after;
- a deletion holds every field as it was.

So folding a record's events in order gives its exact state after any of them, as full event
sourcing would. That is what the history, "how did this look last year" and restoring deleted or
changed records build on.

### Why not full event sourcing

- **Every read would need projections.** People lists, search, and later the pedigree, map and
  timeline views all query tables. With full event sourcing those tables become projections that
  must be built from the events and kept in sync, with rebuild tooling for when they drift.
- **Events would have to stay readable forever,** and the state could only be changed through
  them. A plain table can be migrated with Liquibase like the rest of the schema.
- **It would break with how the existing records are stored,** for a gain we get anyway.
- **Four databases.** Dulo runs on SQLite, PostgreSQL, MySQL and MariaDB. An event store with
  projections is a lot of machinery to keep portable.

### What the hybrid keeps

- A complete, append-only history, written atomically with the change it describes: a change
  can't happen without its event, and an event can't exist without its change.
- The state of any record at any point, by replaying its events.
- Restore, by replaying events up to a point and saving the result as a new, audited change.
  History is never rewritten.
- A way out: since the events are complete, Dulo could still move to full event sourcing later.

The price is that the state is stored twice, in the table and in the events. The tables win if
they ever disagree.

## How it works

- **`ChangeRecorder` and `ChangeSet`** (`com.dulo.core.audit.recording`). Every write use case
  starts a change set for the acting user, inside its unit of work, and reports each stored
  record to it with `created`, `changed` or `deleted`. One change set is one action: its events
  share the time and the correlation id.
- **`RecordFields`.** Each record type has a codec that turns a record into a flat map of fields,
  such as `TreeFields`, `RoleFields` and `UserFields`. The values are what JSON can hold without
  loss (`FieldValues`): strings, booleans, whole numbers, finite decimals, lists and maps.
  `ChangeSet` compares the maps of the record before and after, so the HTTP format of a request
  doesn't matter: only the fields that really changed are recorded.
- **Order.** Events are ordered by a database sequence (`seq`), not by their UUIDv7 ids, which
  aren't ordered within one millisecond.
- **Storage.** The table `change_event` holds the fields above. `changes` and `removed_links` are
  JSON, and `format_version` says which format they use.

## Deleting with links, and restoring

Deleting a record that others refer to, such as a person who is a partner in a family, first
fails with a `has-links` problem listing those references. A confirmed deletion
(`?confirmed=true`) removes them in the same action: each record it changes gets a `changed`
event, and the deletion's own event lists the references in `removedLinks`. All these events share
one correlation id.

`POST /api/v1/{people|places|families|events}/{id}/restore` with `{"eventId": "…"}` brings a
record back to its state right after one of its events:

1. Replay the record's events up to that one (`Replay.stateAfter`), and turn the fields back into
   a record (`RestorableFields.fromFields`).
2. Drop the references to records that no longer exist, such as a family partner who was deleted
   since.
3. For a deleted record, plan to put back the links its deletion removed, from `removedLinks`.
   Details that the link doesn't hold come from the other record's own event in the deletion's
   correlation, such as a child's relation or a participant's role. A link can't come back if the
   other record is gone, is full (a family with two partners), or now refers elsewhere (a village
   that lies in another county now).
4. If something from steps 2 and 3 can't come back, answer `restore-conflict` with the list, unless
   the restore is confirmed.
5. Save the record and record a `restored` event with the differences, then save each relinked
   record with its own `changed` event, all in one action.

Restoring never rewrites history: it is a new change, and it can itself be undone the same way.

<pre class="mermaid">
sequenceDiagram
    actor U as User
    participant P as Person service
    participant F as Family
    participant A as Audit history
    U->>P: DELETE /people/anna?confirmed=true
    P->>F: remove Anna from partners
    P->>A: family changed (partners), correlation c1
    P->>A: person deleted, removedLinks: [family/partners], correlation c1
    U->>P: POST /people/anna/restore {eventId}
    P->>A: replay Anna's events up to eventId
    P->>A: person restored, correlation c2
    P->>F: add Anna back to partners
    P->>A: family changed (partners), correlation c2
</pre>

## Who sees what

The audit log, `GET /api/v1/audit`, needs view access to the `audit-log` feature. Changes to the
tree settings, roles and users also need view access to `administration`: without it they are
left out, and filtering by them is refused. The log is read newest first, in pages.

## Rules for contributors

- **Every write records its changes,** in the same unit of work as the write, after the record is
  stored, so the event gets the new version. A test of the use case checks the events.
- **A new record type** gets an `EntityType`, a `RecordFields` codec and a test that its fields
  are what you expect. To be restorable, the codec implements `RestorableFields`, and a test checks
  that a record survives the round trip through its fields.
- **Field names and value formats are a stored format.** Renaming or reshaping a field changes
  how old events read. Either keep the old shape, or raise `format_version` and upcast the old
  rows when they are read.
- **Never update or delete rows of `change_event`.**
