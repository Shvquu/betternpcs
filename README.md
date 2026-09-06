# BetterNPCs

[![Build](https://github.com/Shvquu/betternpcs/actions/workflows/build.yml/badge.svg)](https://github.com/Shvquu/betternpcs/actions/workflows/build.yml)
[![License](https://img.shields.io/badge/license-GPL--3.0-blue.svg)](LICENSE)
[![API](https://img.shields.io/badge/API-MIT-blue.svg)](api/LICENSE)
[![Minecraft](https://img.shields.io/badge/Minecraft-1.21.4%20–%2026.2-brightgreen.svg)](#supported-versions)

A free, modern NPC plugin for Paper, built as an alternative to Citizens2 and FancyNPCs.

NPCs are rendered entirely with packets. They are not entities on the server, so they cost nothing
when nobody is near them, they are not affected by mob caps or chunk ticking, and they cannot be
pushed out of position. That is what makes several thousand of them practical.

> **Status: pre-release.** Everything below is implemented and tested, and the plugin has been run
> on a real Paper 1.21.4 server. What has *not* yet been confirmed is NPC rendering with a connected
> client — the packet code compiles against all six supported Mojang-mapped server jars, but no
> screenshot has been taken. Treat 1.0.0 as a first release, not a battle-tested one.

## Features

- **Player and mob NPCs** — any living entity type the running server knows about.
- **Skins** from a player name, a UUID or a raw texture, cached and resolved off the main thread.
- **Nametags and holograms** with MiniMessage, placeholders, per-line permissions and view distances.
- **Look tracking** — an NPC can face each viewer individually, or the nearest player, or a fixed point.
- **Actions** on every kind of click: messages, commands, sounds, particles, teleports, cooldowns,
  permission gates. Extensible with your own.
- **Visibility rules** — distance, permission, opt-in NPCs, and an extension point for regions,
  parties or quest state.
- **Four storage backends**: SQLite, MySQL, MariaDB and PostgreSQL, with schema migrations.
- **Four languages** out of the box: English, German, Spanish and French. Adding one is a file.
- **A real public API** under MIT, published separately from the GPL implementation.

## Supported versions

| Minecraft | Adapter | Java |
|---|---|---|
| 1.21.4 | `v1_21_4` | 21 |
| 1.21.5 | `v1_21_5` | 21 |
| 1.21.6 – 1.21.8 | `v1_21_8` | 21 |
| 1.21.9 – 1.21.11 | `v1_21_11` | 21 |
| 26.1 | `v26_1` | 25 |
| 26.2 | `v26_2` | 25 |

**Paper or a Paper fork is required** — BetterNPCs uses Paper's plugin loader and its Brigadier
command API, neither of which Spigot has.

An unsupported Minecraft version is refused at startup with a clear message rather than approximated
with the closest adapter. Guessing produces a plugin that appears to start and then sends packets
the client cannot parse, which is a much harder failure to diagnose.

## Installation

1. Download `BetterNPCs-<version>.jar` from [Releases](https://github.com/Shvquu/betternpcs/releases).
2. Drop it into `plugins/` and start the server.

The database driver and connection pool are downloaded on first start by Paper's plugin loader —
only the driver your configuration actually names, so a default SQLite install does not pull in
three JDBC drivers it will never use. That means **the first start needs internet access**; after
that it does not.

## Quick start

```
/npc create shopkeeper
/npc skin shopkeeper Notch
/npc name shopkeeper <gradient:#00c6ff:#0072ff>Shopkeeper</gradient>
/npc action shopkeeper add right_click message: <green>Welcome!
/npc action shopkeeper add right_click command: shop
```

Most commands take the NPC's name, and all of them work without it if you are looking at the NPC.
`/npc help` lists only the commands you have permission for.

Full reference: [docs/commands.md](docs/commands.md).

## Configuration

`config.yml` is written on first start and every setting is optional — a file from an older version
keeps working, and the defaults are documented inline.

The three settings that matter on a large server:

| Setting | Default | Why |
|---|---|---|
| `npc.default-view-distance` | `32` | Tracking cost grows with players × nearby NPCs. This bounds the second factor; going from 48 to 32 removes more than half the work. |
| `npc.tracker-interval` | `2` | Ticks between visibility passes. `2` is visually identical to `1` for a walking player and costs half as much. |
| `npc.max-tracked-per-player` | `200` | Caps what one player standing in a hub full of NPCs is sent. The nearest are kept. |

Full reference: [docs/configuration.md](docs/configuration.md).

## Permissions

Everything defaults to operators. `betternpcs.admin` grants all of it.

| Node | Grants |
|---|---|
| `betternpcs.command` | Use `/npc` at all. Without it the command is not even offered in tab completion. |
| `betternpcs.command.create` / `.remove` | Create and delete NPCs |
| `betternpcs.command.list` / `.info` | List and inspect |
| `betternpcs.command.spawn` | Spawn and despawn |
| `betternpcs.command.move` / `.teleport` | Move an NPC to you, or you to it |
| `betternpcs.command.edit` | Name, type, skin, equipment |
| `betternpcs.command.action` | Edit action chains |
| `betternpcs.command.action.console` | Add `console:` actions — **deliberately not implied by `.action`** |
| `betternpcs.command.reload` / `.save` | Reload and force a save |

A console action runs with full server permissions, so an NPC carrying one is as powerful as
whoever configured it. Being trusted to edit NPC actions is not the same as being trusted with
`/op`, which is why that node is separate.

Full reference: [docs/permissions.md](docs/permissions.md).

## For developers

The API is a separate, MIT-licensed artifact. A plugin that compiles against it is **not** bound by
the GPL that covers the implementation — that split is the entire reason the modules are separate.

```kotlin
repositories {
    maven("https://maven.pkg.github.com/Shvquu/betternpcs")
}

dependencies {
    compileOnly("dev.shvquu.betternpcs:betternpcs-api:1.0.0")
}
```

> GitHub Packages requires a GitHub token to download from, even for public packages. That is a
> limitation of the registry rather than of this project; see [docs/api.md](docs/api.md) for the
> setup, and for the Maven Central plan.

```java
BetterNPCsApi api = BetterNPCs.get();

Npc npc = api.npcManager().create("guide", NpcType.PLAYER, position);
npc.setDisplayName("<green>Guide");
npc.setSkin(SkinSource.playerName("Notch"));
npc.addAction(InteractionType.RIGHT_CLICK, ActionDefinition.parse("message: <green>Hello!"));
npc.spawn();
```

`examples/example-plugin/` is a working integration that is compiled by CI, so it cannot drift out
of date without the build going red.

Full reference: [docs/api.md](docs/api.md).

## Building

Java 21 is the minimum; the 26.x adapters additionally need a JDK 25, which Gradle provisions
automatically if you do not have one.

```bash
./gradlew build
```

That is the whole setup — no local configuration, no manual steps. The first run takes several
minutes because it derives six Mojang-mapped server jars.

See [CONTRIBUTING.md](CONTRIBUTING.md) for running a test server and for how the version adapters
are maintained.

## Documentation

| | |
|---|---|
| [Configuration](docs/configuration.md) | Every `config.yml` setting and what changing it costs |
| [Commands](docs/commands.md) | All subcommands and their permissions |
| [Permissions](docs/permissions.md) | The permission tree |
| [Storage](docs/storage.md) | The four backends, the schema and migrations |
| [API](docs/api.md) | Depending on, and building against, the public API |
| [Architecture decisions](docs/adr/) | Why the project is shaped the way it is |

## Licence

- **`api/`** — [MIT](api/LICENSE). Depend on it freely.
- **Everything else** — [GPL-3.0](LICENSE).

Contributions are welcome; see [CONTRIBUTING.md](CONTRIBUTING.md).
