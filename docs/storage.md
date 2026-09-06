# Storage

NPCs are persisted through one interface,
[`NpcRepository`](../core/src/main/java/dev/shvquu/betternpcs/core/storage/NpcRepository.java). It
sees nothing but immutable `NpcSnapshot` values, never a live NPC — which is what makes it safe to
run entirely off the main thread, and what keeps an SQL implementation from having opinions about
rendering.

## Backends

| | Ships | Notes |
|---|---|---|
| SQLite | yes | The default. No setup, one file, handles thousands of NPCs. |
| MySQL | yes | For sharing NPCs across servers |
| MariaDB | yes | Same block as MySQL; differs only in the driver |
| PostgreSQL | yes | |
| MongoDB | **not yet** | Configurable, not implemented |
| In-memory | yes | Not selectable — the automatic fallback when a backend cannot be opened |

Configuration: [configuration.md](configuration.md#storage).

### If the backend cannot be opened

The plugin logs a `SEVERE` line saying NPCs will not be persisted this session, and runs on
in-memory storage. It does not refuse to start.

That is a deliberate trade. A hub whose NPCs work but do not survive a restart is recoverable; a hub
that will not boot because a database is briefly down is an outage.

## Threading

Every method returns a `CompletableFuture` and runs on the repository's own named thread pool
(`BetterNPCs-Storage-N`). None of them may block the caller.

The pool is dedicated rather than the common fork-join pool, for two reasons: a blocking database
call would starve other work sharing the common pool, and a named thread makes it obvious in a
thread dump which plugin is waiting on a database.

Futures complete on a background thread. Anything touching the Bukkit API afterwards has to be
scheduled back onto the main thread — the engine does this; extensions must too.

## Retries

Transient failures are retried three times with a doubling delay starting at 200 ms:

- `SQLTransientException`
- SQL state `08xxx` — connection exception, such as a link dropped by a firewall's idle timeout
- SQL state `40xxx` — transaction rollback, such as a deadlock
- an `IOException` cause

**Everything else fails immediately.** A syntax error or a unique constraint violation will not
succeed a moment later; retrying only produces the same error several times with delays between
them, burying the first one.

## Writes

Changes mark an NPC dirty rather than writing immediately. A script that moves an NPC every tick
would otherwise be a database write every tick.

Dirty NPCs are written on a timer (`npc.save-interval`, default 300 s), on `/npc save`, and on
shutdown. The whole batch goes in **one transaction**: a partial write would leave the database
describing a state the server was never in.

The dirty flag is cleared *before* the write, not after — so a change made while the write is in
flight leaves the NPC dirty again rather than being discarded. If the write fails, the flag is
restored and the next cycle tries again.

## Schema

One table. Identity and position are real columns because they are the only things ever queried or
indexed; everything else is one JSON document.

```sql
CREATE TABLE betternpcs_npcs (
    uuid              TEXT NOT NULL PRIMARY KEY,
    name              TEXT NOT NULL,          -- unique, case-insensitive
    type              TEXT NOT NULL,          -- PLAYER, VILLAGER, ...
    world             TEXT NOT NULL,
    x, y, z           REAL NOT NULL,
    yaw, pitch        REAL NOT NULL,
    spawn_by_default  INTEGER NOT NULL,
    data              TEXT NOT NULL           -- everything else, as JSON
);
```

Fifty columns would mean a schema migration for every new NPC property, on four database engines —
and NPCs are always read and written whole anyway. See
[ADR 0004](adr/0004-npc-persistence.md).

Name uniqueness is enforced **by the database**, not only in memory, so a hand-edited file cannot
produce two NPCs that the engine would then refuse to load.

Equipment inside `data` is stored with Bukkit's `ItemStack.serializeAsBytes()`, which carries
Minecraft's own data version — an item written on one Minecraft version is upgraded rather than
misread when loaded on a newer one.

### Reading is deliberately tolerant

Every field in the JSON falls back to a default rather than failing. A row written by an older
version is missing whatever a newer one added; a row written by a *newer* version may contain things
this one does not understand, and a server owner who downgrades should get their NPCs back rather
than an exception.

The exception is a value that is present but nonsensical, which is reported — silently replacing it
would hide corruption. One unreadable row is skipped with a warning naming the NPC, so it never
costs the other thousand.

## Migrations

Numbered, run in order, each in its own transaction, each recorded in `betternpcs_schema` only once
its statements have committed. A migration that fails leaves the database at the previous version
rather than half-way through a new one — the difference between "start the old version again" and
"restore a backup".

**Migrations are never edited once released.** Changing one would leave every existing database with
the old shape and every new one with the new shape, and nothing to tell them apart. A change is a
new migration.

Running the migrator against an up-to-date database is a no-op, so it runs on every start.

## Testing

`SqlNpcRepositoryTest` runs against a **real SQLite file** in a temporary folder — no mocked JDBC.
Every statement in the SQLite dialect is executed by the real driver, which is the only way to find
out whether it is valid SQL; a mocked connection would happily accept a statement with a typo in it.

Covered: schema creation, migration idempotence, the unique-name constraint, full round-trip of a
fully configured NPC, batch rollback, persistence across a reopen, and a deliberately corrupted
`data` column.

MySQL, MariaDB and PostgreSQL are exercised only through their dialect definitions at present. Their
SQL has not been run against a live server; that needs service containers in CI and is not yet set
up.
