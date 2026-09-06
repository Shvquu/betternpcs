# 0004 — Identity in columns, everything else as JSON

**Status:** accepted

## Context

An NPC has a lot of state: identity, position, type, display name, skin source and resolved texture,
equipment across six slots, ten appearance flags, visibility settings, six look settings, two text
styles of eight fields each, a hologram line list, action chains per interaction type, and arbitrary
extension metadata.

That has to be stored on four database engines, survive plugin updates that add properties, and
survive a downgrade without destroying data it does not understand.

## Decision

**One table.** Identity and position are real columns. Everything else is a single JSON document in
a `data` column.

```sql
uuid, name, type, world, x, y, z, yaw, pitch, spawn_by_default, data
```

Reading the JSON is deliberately tolerant: every field falls back to a default rather than failing.

## Why

**The columns are exactly the things ever queried or indexed.** Name has a unique index — enforced
by the database, so a hand-edited file cannot produce two NPCs the engine would then refuse to load.
World has one. Nothing has ever needed to query "all NPCs whose nametag opacity is below 200", and a
schema shaped for queries nobody makes is a schema that costs a migration every time a property is
added.

**Fifty columns would mean a migration per property, on four engines.** Each one an opportunity to
get a dialect wrong on a database nobody testing the change has running. Against that: NPCs are
always read and written **whole**, so normalising buys nothing at read time either.

**Tolerant reads are what make updates and downgrades safe.** A row written by an older version is
missing whatever a newer one added — the default fills it. A row written by a *newer* version
contains fields this one does not know — they are ignored, and the NPC still loads. Someone who
downgrades gets their NPCs back rather than an exception.

The exception is a value that is present but nonsensical: that is reported, because silently
replacing it would hide corruption. One unreadable row is skipped with a warning naming the NPC, so
it never costs the other thousand.

**Items go through `ItemStack.serializeAsBytes()`**, base64 into the JSON. That format carries
Minecraft's own data version, so an item written on 1.21.4 is *upgraded* rather than misread when
loaded on 26.2. Hand-rolling item serialisation would lose that for no benefit.

## What was rejected

**A fully normalised schema** — separate tables for actions, metadata and equipment. Correct, and
three joins or an N+1 for an operation that always wants everything. The migration cost is the
decisive argument.

**Serialising the whole snapshot** with Java serialization or a Bukkit `ConfigurationSerializable`
graph. Both make the stored form a function of the class shape, so renaming a field breaks every
stored NPC, and Java deserialization of stored data is a security liability besides. Explicit
field-by-field JSON is more code and is worth it.

**Postgres' native `UUID` and `JSONB` types.** They would need casts in every statement and a
dialect-specific read path, in exchange for nothing — the JSON is never queried into. `VARCHAR(36)`
and `TEXT` behave identically everywhere.

## Consequences

- Adding an NPC property is a code change and no migration at all: write it in `SnapshotCodec`, read
  it with a default. Old rows keep working.
- The migration system still exists and is still used, for changes to the **columns** — which are
  now rare by design.
- `data` is not queryable by SQL. If a future feature genuinely needs to filter on something inside
  it, that field is promoted to a column with a migration, which is a normal thing to do once.
- The codec has to be written by hand, roughly one method per property type. It is mechanical, and
  `SqlNpcRepositoryTest` round-trips a fully configured NPC through a real SQLite database to prove
  no field was forgotten.
