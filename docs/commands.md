# Commands

Everything lives under `/npc`. Aliases: `/npcs`, `/betternpcs`.

`/npc` with no arguments, or `/npc help`, lists the subcommands **you** may use — a command you lack
the permission for is not listed, and is not offered in tab completion either.

## Naming the NPC

Most subcommands take the NPC's name. All of the ones marked `[npc]` below also work with the name
omitted, in which case they act on **the NPC you are looking at**, within 8 blocks. Standing in
front of an NPC and typing `/npc info` is the common case, so it is the short one.

The console has no line of sight, so it must always name the NPC.

NPC names are 1–32 characters of letters, digits, `_` and `-`, matched case-insensitively for
lookups. They are restricted because a name is typed into commands and turned into a game profile
name; a name with a space in it would need quoting everywhere and would still be ambiguous in
`/npc create`.

---

## Lifecycle

### `/npc create <name> [type]`
`betternpcs.command.create` · player only

Creates an NPC where you are standing and spawns it. `type` is any living entity type the server
knows — `PLAYER` (the default), `VILLAGER`, `ZOMBIE`, `IRON_GOLEM` and so on. Tab completion lists
what the running version supports.

### `/npc remove [npc]` · `/npc delete [npc]`
`betternpcs.command.remove`

Despawns the NPC, removes it from the registry and deletes it from storage. The handle is
invalidated, so any plugin still holding it gets an exception rather than silently mutating
something that no longer exists.

### `/npc spawn [npc]` · `/npc despawn [npc]`
`betternpcs.command.spawn`

Activates or deactivates an NPC without deleting it. A despawned NPC keeps all its configuration and
is still listed; it is simply not rendered.

Whether an NPC spawns automatically when its world loads is stored per NPC and defaults to yes.

---

## Inspecting

### `/npc list [page]`
`betternpcs.command.list`

Ten per page, sorted by name, showing type, world and whether each is spawned.

### `/npc info [npc]`
`betternpcs.command.info`

Everything about one NPC: id, type, state, position, current viewer count, display name, skin
source, filled equipment slots, visibility, look mode, nametag, hologram line count, action counts
per interaction, and any extension metadata keys.

Worth pasting into a bug report.

---

## Position

### `/npc move [npc]`
`betternpcs.command.move` · player only

Brings the NPC **to you**, facing you — not facing the way you were, which would put its back to
whoever just placed it.

### `/npc teleport <npc>` · `/npc tp <npc>`
`betternpcs.command.teleport` · player only

Takes **you to the NPC**. Fails if the NPC's world is not loaded.

---

## Appearance

All of these need `betternpcs.command.edit`.

### `/npc name <npc> [text...]`

Sets the display name from MiniMessage. **With no text, clears it** and the NPC falls back to its
plain name. There is no `clear` literal, because an NPC whose display name should genuinely be the
word "clear" would then be impossible to configure.

```
/npc name shopkeeper <gradient:#00c6ff:#0072ff><bold>Shopkeeper</bold></gradient>
/npc name shopkeeper
```

The name is stored as MiniMessage source rather than as rendered text, so placeholders inside it
resolve per viewer.

### `/npc rename <npc> <new>`

Changes the name used in commands. The display name is unaffected.

Renaming respawns the NPC for everyone currently seeing it, because a player NPC's game profile name
is derived from it and a client cannot be told a new profile for an entity it already knows.

### `/npc type <npc> <type>`

Changes what the NPC is rendered as. Respawns it — the client cannot change an existing entity's
type. Properties that do not apply to the new type, such as a skin on a zombie, are kept but not
sent, so switching back restores them.

### `/npc skin <npc> <player>` · `/npc skin <npc> clear`

Applies the skin of a player, by name. Only player NPCs have skins.

The lookup is a network call: the command returns immediately and reports the result when it
arrives. The resolved texture is cached and stored with the NPC, so a restart does not fetch it
again — and the NPC keeps working when Mojang's session servers are down.

### `/npc equipment <npc> <slot> [clear]`

Puts **the item in your main hand** into the given slot, or clears it. Slots are `HEAD`, `CHEST`,
`LEGS`, `FEET`, `HAND`, `OFF_HAND` and whatever else the running version has.

The item is taken from your hand rather than typed, because there is no sane way to type an item
with its full component data.

---

## Actions

All of these need `betternpcs.command.action`.

An action chain runs top to bottom when a player interacts with the NPC. `<interaction>` is one of
`left_click`, `right_click`, `shift_left_click`, `shift_right_click`.

If a sneaking interaction has no actions of its own, the plain one runs instead — so a `right_click`
chain keeps working when the player happens to be crouching.

### `/npc action <npc> list <interaction>`

Numbered from 1, matching what `remove` expects.

### `/npc action <npc> add <interaction> <action...>`

The action is written as `type: argument`. Only the **first** colon separates them, so the argument
can contain as many more as it likes — which it will, because MiniMessage tags and namespaced keys
both use them.

```
/npc action shop add right_click message: <green>Welcome, <player_name>!
/npc action shop add right_click sound: entity.villager.yes 1.0 1.2
/npc action shop add right_click command: shop
```

The argument is validated when you add it, not when a player first clicks the NPC, so a typo is
reported to whoever made it.

**`console:` actions need `betternpcs.command.action.console` on top of `.action`.** They run with
full server permissions.

The built-in types: `message`, `broadcast`, `actionbar`, `title`, `command`, `console`, `sound`,
`particle`, `teleport`, `animation`, `require-permission`, `cooldown`. Extensions can add more, and
tab completion lists whatever is registered.

### `/npc action <npc> remove <interaction> <index>`
### `/npc action <npc> clear <interaction>`

---

## Maintenance

### `/npc save`
`betternpcs.command.save`

Writes every NPC with unsaved changes. Rarely needed — changes are saved periodically and on
shutdown. Useful before something risky.

### `/npc reload`
`betternpcs.command.reload`

Re-reads `config.yml` and the language files, then reloads NPCs from storage. Unsaved changes are
written first, so nothing is lost.

**Storage and the version adapter are deliberately not reloaded.** Swapping a database connection
under a running engine, or rebinding to a different Minecraft version, cannot be done safely while
NPCs are spawned — and anyone who changed either is going to restart anyway. Changing the storage
backend and reloading logs a warning saying exactly that.

Reloading invalidates every NPC handle, including ones other plugins are holding. Extensions should
look their NPCs up again after `NpcLoadEvent`.

---

## Backup and restore

Backups are plain JSON under `plugins/BetterNPCs/backups/`. They hold every NPC in full — position,
skin, equipment, actions, metadata, the lot — and nothing else: no configuration, no language files.

The format is the same document the database stores, so a backup taken on SQLite restores into
MongoDB without conversion, and a backup taken before a new NPC property existed still restores
afterwards, with the missing field taking its default.

### `/npc backup [name]`
`betternpcs.command.backup`

Writes every NPC to `backups/<name>.json`. Without a name the file is named after the moment it was
taken, so repeated backups never overwrite each other.

A name may contain letters, digits, `.`, `-` and `_`. Anything that could reach outside the backups
folder is refused.

Backing up a named file **overwrites** an existing one of that name. The file is written to a
temporary file and moved into place, so an interrupted backup leaves the previous one intact rather
than a truncated file that looks like a backup and is not one.

### `/npc restore`
`betternpcs.command.restore`

Lists the backups that exist, newest first.

### `/npc restore <file> [--replace] [--confirm]`
`betternpcs.command.restore`

**Without `--confirm` this is a dry run.** It reports what would be created, what would be
overwritten and what would be skipped, and changes nothing. Add `--confirm` to apply the same
command.

**Restore never deletes anything.** An NPC that is on the server but not in the backup is left
completely alone. A restore is usually run in a hurry by somebody who is not certain what the file
contains, and a command that silently deleted whatever the backup happened not to mention would turn
a partial loss into a total one.

Given that, each entry in the file goes one of three ways:

| Situation | Default | With `--replace` |
| --- | --- | --- |
| No NPC with that unique id | created | created |
| The same NPC still exists | skipped, reported | overwritten in place |
| A **different** NPC holds that name | skipped, reported | skipped, reported |

The last row is not a bug. Giving the name to the NPC from the file would mean taking it from the one
that has it, and restore does not delete. Rename one of them and restore again.

Overwriting keeps the unique id, so an extension holding a reference to that NPC keeps resolving it.

`--replace` and `--confirm` are separate on purpose: one decides whether existing NPCs are touched,
the other whether anything happens at all. Both may be given in either order.

### Restoring a single NPC

There is no per-NPC restore. The backup is readable JSON, so the way to do it is to copy the one
`npcs` entry into a new file and restore that.

### Editing a backup by hand

It is a plain text file and editing it works. One thing to know before you do: **a value the plugin
does not recognise is replaced by the default rather than reported.** That is what makes a backup
taken on an older version restore on a newer one, but it also means a typo in an enum — writing
`NEAREST_PLAYER` where the value is `LOOK_AT_NEAREST_PLAYER` — silently yields `NONE` instead of an
error.

So check the result with `/npc info <name>` after restoring a file you edited. The dry run will not
catch it: it validates names and identity, not the contents of `data`.
