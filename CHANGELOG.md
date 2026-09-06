# Changelog

All notable changes to this project are documented here.

The format follows [Keep a Changelog](https://keepachangelog.com/en/1.1.0/), and the project follows
[Semantic Versioning](https://semver.org/spec/v2.0.0.html).

The **API** is versioned separately from the plugin — see
[`ApiVersion`](api/src/main/java/dev/shvquu/betternpcs/api/ApiVersion.java). A plugin version bump
does not imply an API change, and API breaking changes only happen on an API major version.

## [Unreleased]

### Added

- **NPC engine**: player and mob NPCs rendered entirely with packets, a grid-based visibility
  tracker, per-viewer look tracking, animations, equipment, and per-player visibility overrides.
- **Public API** (`dev.shvquu.betternpcs:betternpcs-api`, MIT): `Npc`, `NpcManager`, immutable
  property values, nine Bukkit events, action handlers, skin providers, visibility rules and an
  extension system with dependency ordering.
- **Six Minecraft version adapters** covering 1.21.4 through 26.2, each compiled against its own
  Mojang-mapped dev bundle.
- **Storage**: SQLite, MySQL, MariaDB and PostgreSQL through HikariCP, with a versioned migration
  system, transactional batch writes and retry with backoff on transient failures.
- **MongoDB storage**, storing the same document shape as the SQL backends, with a unique
  case-insensitive index on the NPC name. Tested against a real `mongod`.
- **Anonymous metrics** through bStats, honouring `plugin.metrics`. NPC count, storage type,
  Minecraft version, adapter, language and whether PlaceholderAPI is hooked — no player data, no
  world or NPC names.
- **Commands**: `/npc` with 19 subcommands built on Paper's Brigadier API, with tab completion and
  per-subcommand permissions.
- **Permissions**: 14 nodes, all defaulting to operator, with `betternpcs.admin` as their parent.
- **Localisation**: English, German, Spanish and French, all messages MiniMessage.
- **Actions**: twelve built-in handlers — messages, broadcasts, action bars, titles, player and
  console commands, sounds, particles, teleports, animations, permission gates and cooldowns.
- **Skins** from a player name, UUID or raw texture, cached, resolved off the main thread, with a
  provider chain that other plugins can extend.
- **Update check** against GitHub Releases at startup. Reports only; never downloads or installs.
- **PlaceholderAPI** support when it is installed, with placeholder values inserted as literal text
  so that a value cannot inject formatting or a clickable command.

### Security

- Placeholder values are escaped individually before MiniMessage parsing, so a player name shaped
  like a tag cannot inject a `<click:run_command>` into a message another player sees.
- `betternpcs.command.action.console` is deliberately not implied by `betternpcs.command.action`:
  a console action runs with full server permissions.
- Database credentials and MongoDB connection strings are redacted from every log line, exception
  message and `toString`.
- The SQLite file name is refused if it contains a path traversal or an absolute path.

[Unreleased]: https://github.com/Shvquu/betternpcs/commits/main
