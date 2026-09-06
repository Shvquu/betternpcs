# 0001 — Modular architecture and the licence boundary

**Status:** accepted

## Context

BetterNPCs has to do two things that pull in opposite directions. It needs deep access to Minecraft
internals to render NPCs as packets, and it needs to offer a stable public API that third-party
plugins can compile against for years.

Those are incompatible in one artifact. NMS changes shape every Minecraft release; a public API that
must not. And a plugin that depends on an API is entitled to know what licence it has taken on.

## Decision

Separate Gradle modules with a dependency direction that runs one way:

```
api          public, MIT, no NMS, no engine
core         engine: model, tracking, config, i18n, storage SPI. No NMS, no direct database access
storage/sql  JDBC implementation of core's repository interface
versions/v*  NMS adapters, one per Minecraft version. The only NMS-aware code
plugin       assembles everything into the distributable jar
examples     a sample integration, built in CI
```

`api` is **MIT**; everything else is **GPL-3.0**.

## Why

**The licence split is the reason the API is a separate module at all.** A single GPL artifact would
mean every plugin that wanted to create an NPC had to be GPL too, which for a Minecraft plugin
ecosystem is a serious imposition. Splitting them lets the implementation stay copyleft — so
improvements come back — while anyone can build against the API freely.

**`core` not touching NMS is what makes six Minecraft versions maintainable.** Version-specific code
is confined to one small, reviewable surface, `VersionAdapter`. Everything else is written once. An
`ArchitectureTest` enforces this rather than trusting discipline: one `net.minecraft` import in
`core` would be a class linked to a single Minecraft version inside a module loaded on all of them,
and it would compile perfectly.

**`core` not touching a database** means the engine can be tested without one, and a storage backend
cannot develop opinions about rendering. The seam is `NpcRepository`, which sees only immutable
snapshots.

**The example module is part of the normal build** on purpose. It compiles against nothing but the
API, exactly as a third-party plugin does, so a change that breaks API compatibility fails
`./gradlew build` here rather than in someone else's project weeks later.

## Consequences

- API changes are deliberate. Adding something to `core` is cheap; adding it to `api` is a
  commitment under [semantic versioning](../../api/src/main/java/dev/shvquu/betternpcs/api/ApiVersion.java).
- Contributors have to know which module a change belongs in. `CONTRIBUTING.md` covers it, and the
  architecture tests catch the common mistake.
- The published artifact is only `betternpcs-api`. Nobody can accidentally compile against the
  engine.
- Anyone forking the implementation is bound by GPL-3.0. That is the intent.
