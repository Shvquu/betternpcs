# Contributing to BetterNPCs

Thanks for wanting to help. This document covers the things that are specific to this project — the
parts where doing the obvious thing produces a build failure or, worse, something that compiles and
is quietly wrong.

## Building

Java 21 is required. The 26.x version adapters additionally need a JDK 25, because Minecraft 26.1+
ships Java 25 class files and `javac` cannot read class files newer than itself. If you do not have
one, Gradle's toolchain resolver downloads it.

```bash
./gradlew build
```

No local configuration, no environment variables, no manual steps. The first run takes several
minutes because paperweight derives six Mojang-mapped server jars; after that they are cached.

Running the tests alone:

```bash
./gradlew test
```

## Running a test server

```bash
./gradlew runServer
```

Two things you will hit:

**The EULA.** The task refuses to start until `plugin/run/eula.txt` exists containing `eula=true`.
The build script does not create it for you — agreeing to a licence is not something a build script
should do on your behalf. See <https://aka.ms/MinecraftEULA>.

**TLS interception.** On a machine behind a corporate proxy or an antivirus that intercepts TLS,
Paperclip cannot validate Mojang's certificate and the server never starts, with a
`PKIX path building failed` error. The certificate is usually in the operating system's trust store
rather than the JDK's:

```bash
./gradlew runServer "-Pruntime.jvmArgs=-Djavax.net.ssl.trustStoreType=WINDOWS-ROOT"
```

To test against the newest supported version instead of the oldest:

```bash
./gradlew runServer -Pruntime.mc=26.2
```

## The version adapters

**Edit `versions/v1_21_4` and nothing else**, then regenerate the rest:

```bash
tools/sync-adapters.sh
./gradlew build
```

The five other modules are generated from that one. Editing them directly works right up until the
next sync silently overwrites your change.

A **genuine** difference between Minecraft versions goes in the delta section of
`tools/sync-adapters.sh`, with a comment naming what changed and when. Three exist today: authlib 7
turned `GameProfile` into a record, Minecraft 26.1 replaced the interact packet's visitor handler
with a record, and 26.2 removed the static `EntityType` constants.

Each module compiles against its own Paper dev bundle, which is what guarantees the adapter links
against the server it will actually run on. Modules whose generated source comes out identical are
**deliberately not merged** — that would mean code compiled against 1.21.4 running on 1.21.8, which
relies on NMS staying binary compatible across patch releases. It usually is, and occasionally,
silently, is not. See [ADR 0002](docs/adr/0002-multi-version-strategy.md).

## Invariants the build enforces

These fail the build rather than being caught in review, so you will find out quickly. Knowing why
they exist saves you working out what the failure means.

**The engine never imports NMS.** `ArchitectureTest` fails if anything outside `versions/v*` touches
`net.minecraft` or `org.bukkit.craftbukkit`. One such import in `core` is a class linked to a single
Minecraft version inside a module that loads on all of them.

**The public API is fully documented.** `:api:javadoc` runs with `Xdoclint:all` and
`failOnError`, so a missing `@param` on a public method fails the build.

**The API compiles against the oldest supported Paper.** `api/` and `core/` build against 1.21.4 on
purpose, so reaching for something added in 1.21.9 is a compile error here rather than a crash
report from a user later. Do not raise `paperApi` in the version catalogue to make something
compile.

**Messages exist in all four languages.** A new `Message` constant needs an entry in each file under
`plugin/src/main/resources/languages/`. `PackagedResourcesTest` checks completeness, that no unknown
keys are present, that every file is valid MiniMessage, and that a translation has not dropped a
placeholder the English text uses.

**Permissions are declared in both places.** A node needs a constant in `Permissions.java` *and* a
declaration in `paper-plugin.yml`, including as a child of `betternpcs.admin`.
`PackagedResourcesTest` checks both directions — and that `betternpcs.command.action` never gains
children, because a console action must not become implied by ordinary action editing.

**The shipped config matches the code defaults.** `PackagedResourcesTest` parses the packaged
`config.yml` and compares it to `BetterNpcsConfig.defaults()`. Two sources of truth for a default
drift apart silently otherwise.

**MockBukkit and `paperApiTest` move together.** MockBukkit ships one build per Minecraft version
and refuses to start against another. Bumping either alone red-builds every engine test. Dependabot
is configured not to try.

## If you contribute from Windows

Windows filesystems do not carry a Unix executable bit, so a shell script committed from Windows
arrives in git as mode `100644`. Everything works locally, and then CI fails with
`./gradlew: Permission denied` and exit code 126 — or a Linux contributor cannot run
`tools/sync-adapters.sh`.

When you add or restore an executable file, set the bit in the index by hand:

```bash
git update-index --chmod=+x path/to/script.sh
```

To check what is tracked:

```bash
git ls-files -s | grep -E '\.(sh|bash)$'   # 100755 is executable, 100644 is not
git ls-files -s gradlew
```

## Code conventions

The codebase has a particular commenting style, and matching it is the main thing that makes a
contribution feel at home.

**Comments explain why, not what.** If a line needs a comment to say what it does, the line usually
wants rewriting instead. What earns a comment is the reasoning that is not in the code: why this
approach and not the obvious one, what breaks if it changes, what a reader is likely to assume that
is wrong.

**Javadoc on anything public** — mandatory in `api/`, expected elsewhere. Say what the caller needs
to know: threading, nullability, what throws, and what the method deliberately does *not* do.

**Tests describe behaviour, not methods.** `refusesADuplicateName`, not `testCreate`. Where a test
guards against a specific mistake, a comment saying which one is worth more than the assertion.

**No `TODO` and no `UnsupportedOperationException` for anything documented as working.** If a
feature is not finished, do not ship the interface for it.

## Pull requests

Small and focused. A pull request that fixes a bug and reorganises three files is two pull requests.

The checklist in the template is not ceremony — every item is an invariant above, and skipping one
means CI tells you instead.

For anything larger than a bug fix, open an issue first. It is a much cheaper place to find out that
the approach will not work than a finished branch.
