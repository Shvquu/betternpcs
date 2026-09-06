# Configuration

`config.yml` is written to `plugins/BetterNPCs/` on first start.

**Every setting is optional.** A missing one falls back to its default, which is what lets a file
written by an older version keep working after an update. A setting that is *present but unusable*
is refused at startup with the exact path named — guessing at what a wrong value was meant to be is
how a plugin silently does something other than what was asked.

```
config.yml: 'storage.mysql.port' must be between 1 and 65535, was 70000
```

`/npc reload` re-reads this file and the language files. It does **not** re-open storage or rebind
the version adapter; see [commands.md](commands.md#npc-reload).

---

## `plugin`

| Setting | Default | |
|---|---|---|
| `debug` | `false` | Logs detail only useful while diagnosing a problem: registered permissions, adapter resolution failures, update-check failures. Noisy. |
| `metrics` | `true` | Anonymous usage statistics: NPC count, plugin version, Minecraft version, storage type. No player data, no IP addresses, no world or NPC names. |

---

## `storage`

| Setting | Default | |
|---|---|---|
| `type` | `SQLITE` | `SQLITE`, `MYSQL`, `MARIADB`, `POSTGRESQL` or `MONGODB` |

**Stay on SQLite unless you need to share NPCs between servers.** It needs no setup, keeps
everything in one file, and handles thousands of NPCs without difficulty. Sharing between servers is
the one thing it cannot do.

All five backends ship and work.

Every block below is parsed at startup, not only the active one, so a typo in the MySQL block is
reported even while SQLite is in use.

### `storage.sqlite`

| Setting | Default | |
|---|---|---|
| `file` | `npcs.db` | A plain file name inside the plugin folder. Paths and traversal are refused. |

SQLite is opened in WAL mode with a single pooled connection. Extra connections would not make
writes faster — SQLite serialises them at the file level — they would only turn contention into
`SQLITE_BUSY` errors that look like random save failures.

### `storage.mysql`, `storage.postgresql`

MariaDB uses the `mysql` block; the two differ only in the driver.

| Setting | Default | |
|---|---|---|
| `host` | `localhost` | |
| `port` | `3306` / `5432` | |
| `database` | `betternpcs` | |
| `username` | `root` / `postgres` | |
| `password` | `""` | Never written to the log, an exception message or any `toString`. |
| `pool-size` | `10` | More is not faster. A pool larger than the database's own thread count just moves the queue. |
| `connection-timeout` | `10000` | Milliseconds to wait for a free connection. |
| `properties` | `{}` | Passed straight to the JDBC driver. |

`properties` is empty on purpose. Shipping `useSSL: "false"` — as plugins commonly do — would
silently turn off transport encryption to the database for everyone who never read this far.

### `storage.mongodb`

| Setting | Default | |
|---|---|---|
| `connection-string` | `mongodb://localhost:27017` | May contain credentials; never logged. Only the host and database are ever printed. |
| `database` | `betternpcs` | |

MongoDB has no connection-pool settings here because the driver manages its own pool, and one more
knob whose right answer is "leave it alone" is not worth putting in front of a server owner. Put
pool options in the connection string if you genuinely need them.

One behaviour differs from the SQL backends and is worth knowing: a single-node MongoDB has no
multi-document transaction, so a batch save that fails part-way leaves the earlier writes in place.
The SQL backends roll the whole batch back. See [storage.md](storage.md#writes).

---

## `npc`

The three settings that decide how BetterNPCs behaves under load are here.

| Setting | Default | |
|---|---|---|
| `default-view-distance` | `32` | Blocks. **The biggest single lever.** |
| `tracker-interval` | `2` | Ticks between visibility passes. |
| `max-tracked-per-player` | `200` | Most NPCs one player is sent at once. |
| `save-interval` | `300` | Seconds between writes of changed NPCs. `0` disables periodic saving. |
| `interaction-cooldown` | `250` | Milliseconds a repeat click on the same NPC is ignored. |
| `use-packets` | `true` | Render as packets rather than server entities. |
| `cache-skins` | `true` | |
| `skin-cache-duration` | `3600` | Seconds a cached skin stays valid. |
| `skin-request-timeout` | `5000` | Milliseconds a skin lookup may take. |

### Tuning for a large server

Visibility cost grows with **players × NPCs near each of them**. The tracker already avoids the
naive walk — NPCs are bucketed into a 64-block grid, so each player only examines the handful of
cells around them — but these three still divide directly into what is left.

- **`default-view-distance`** bounds how many NPCs can be near anyone. Going from 48 to 32 removes
  more than half the work, and 32 blocks is already further than most players notice an NPC.
- **`tracker-interval`** bounds how often. `2` is visually indistinguishable from `1` for a walking
  player and costs half as much; `4` is reasonable with thousands of NPCs, and its only visible
  effect is an NPC appearing a fraction of a second later.
- **`max-tracked-per-player`** is a safety limit for a player standing in the middle of a hub full
  of NPCs. The nearest are kept.

`interaction-cooldown` is not a nicety: clients send one packet per click, so a held mouse button
would otherwise run an NPC's action chain a dozen times a second.

`save-interval: 0` is faster but loses changes if the server crashes. Changes are still written on
shutdown and on `/npc save`.

---

## `language`

| Setting | Default | |
|---|---|---|
| `default` | `en_US` | Which file in `languages/` to use |
| `follow-client-locale` | `false` | Serve each player the language their client is set to |

Shipped: `en_US`, `de_DE`, `es_ES`, `fr_FR`.

Files in `languages/` are yours to edit. BetterNPCs writes the shipped ones only when they are
**missing** and never overwrites them, so your edits survive an update. The cost is that a
translation falls behind when a release adds messages; those lines fall back to English and the
count is reported once at startup.

Adding a language is copying a file. `follow-client-locale` falls back from a country to its
language, so a player on Austrian German gets `de_DE` without needing a `de_AT` file.

---

## `updates`

| Setting | Default | |
|---|---|---|
| `check` | `true` | Ask GitHub at startup whether a newer release exists |

**BetterNPCs never downloads or installs anything.** The check produces one console line with a
version and a link. A plugin that updates itself is a plugin that can break a server while nobody is
watching.

The check runs once, at startup, off the main thread. It fails silently unless `plugin.debug` is on
— plenty of servers have no outbound internet, and a warning on every start for an optional
convenience feature trains people to ignore the log.
