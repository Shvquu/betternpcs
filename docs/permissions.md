# Permissions

Fourteen nodes, **all defaulting to operator**. A fresh install grants ordinary players nothing.

They are declared in `paper-plugin.yml`, so they show up in permission plugins with descriptions
rather than having to be typed from memory.

## The tree

| Node | Grants |
|---|---|
| `betternpcs.admin` | Every node below |
| `betternpcs.command` | Use `/npc` at all |
| `betternpcs.command.create` | `/npc create` |
| `betternpcs.command.remove` | `/npc remove`, `/npc delete` |
| `betternpcs.command.list` | `/npc list` |
| `betternpcs.command.info` | `/npc info` |
| `betternpcs.command.spawn` | `/npc spawn`, `/npc despawn` |
| `betternpcs.command.teleport` | `/npc teleport`, `/npc tp` |
| `betternpcs.command.move` | `/npc move` |
| `betternpcs.command.edit` | `/npc name`, `rename`, `type`, `skin`, `equipment` |
| `betternpcs.command.action` | `/npc action …` |
| `betternpcs.command.action.console` | Adding `console:` actions |
| `betternpcs.command.reload` | `/npc reload` |
| `betternpcs.command.save` | `/npc save` |

## Two things worth knowing

### `betternpcs.command` hides rather than refuses

Commands are registered through Brigadier, which omits nodes whose permission check fails. A player
without `betternpcs.command` does not get "you may not use that" — they get "unknown command", and
`/npc` never appears in their tab completion. `/npc help` likewise lists only the subcommands the
reader can actually run.

That is usually what you want on a public server: an admin command that advertises its own existence
invites people to try it.

### `betternpcs.command.action.console` is deliberately separate

It is **not** a child of `betternpcs.command.action`. It is a child only of `betternpcs.admin`.

A `console:` action runs with full server permissions, which means an NPC carrying one is exactly as
powerful as whoever configured it. Someone you trust to write `message:` and `sound:` chains is not
automatically someone you want able to make an NPC run `/op`.

`command:` actions, by contrast, run as the interacting **player** with that player's own
permissions, so they can never grant access to something the player could not already run
themselves. Those need only `betternpcs.command.action`.

This split is enforced by a test — `PackagedResourcesTest` fails if `betternpcs.command.action` ever
gains children — so a well-meant tidy-up of the permission tree cannot quietly undo it.

## Giving out a subset

A common arrangement is builders who may place and configure NPCs but not delete them or run
arbitrary console commands:

```yaml
# LuckPerms, for illustration
groups:
  builder:
    permissions:
      - betternpcs.command
      - betternpcs.command.create
      - betternpcs.command.list
      - betternpcs.command.info
      - betternpcs.command.spawn
      - betternpcs.command.move
      - betternpcs.command.teleport
      - betternpcs.command.edit
      - betternpcs.command.action
```

Note that `betternpcs.command` is required for any of the others to be reachable.

## Per-NPC permissions

Separately from command permissions, an individual NPC can require a node to be **seen**:

```
visibility:
  permission: myserver.vip
```

and its nametag can require a different one, so a staff-only label above an NPC everyone can see
does not need two NPCs. Both are per NPC and are configured through the API or by editing stored
data; see [api.md](api.md) for `NpcVisibility` and `NametagSettings`.

For anything richer — regions, parties, quest state — register an
`NpcVisibilityRule`. It is consulted after the built-in distance and permission checks, so it only
sees pairings that already passed the cheap tests.

## Verifying what registered

With `plugin.debug` on, BetterNPCs logs every permission node the **server** actually registered,
with its default and its children, at startup:

```
[BetterNPCs] Registered 14 permission node(s):
[BetterNPCs]   betternpcs.admin (op) -> betternpcs.command, betternpcs.command.create, ...
[BetterNPCs]   betternpcs.command (op)
[BetterNPCs]   betternpcs.command.action (op)
[BetterNPCs]   betternpcs.command.action.console (op)
```

"Which node do I grant?" is the most common question a permission tree gets, and the answer that
matters is what the server registered rather than what a document claims.
