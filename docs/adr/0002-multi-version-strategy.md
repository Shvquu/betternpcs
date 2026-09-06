# 0002 — One adapter module per Minecraft version

**Status:** accepted

## Context

BetterNPCs supports Minecraft 1.21.4 through 26.2 — six versions whose internals differ. Two
numbering schemes are in play: `1.MAJOR.PATCH` up to 1.21.11, then `YEAR.DROP` from 26.1, so the
release after 1.21.11 is 26.1. Any code that detects versions by looking for a leading `1.` or that
compares version strings lexically is wrong on both counts; lexically, `1.21.4` sorts after
`1.21.11`.

The plugin must load on all six from one jar, and must refuse cleanly on anything else.

## Decision

One Gradle module per NMS-incompatible version, each compiling against **its own** Paper dev bundle.
All six ship in the jar; exactly one is class-loaded at runtime, chosen by `VersionAdapterResolver`
from the detected version and loaded reflectively by name.

The `v1_21_4` module is the **reference implementation**. The other five are generated from it by
`tools/sync-adapters.sh`, which applies the genuine per-version differences as a small, documented
set of deltas.

Unsupported versions are **refused at startup**, never approximated with the nearest adapter.

## Why

**Compiling each module against its own bundle is what makes the linkage a guarantee rather than a
hope.** If a packet constructor changed shape in 26.1, that module fails to compile — at build time,
on a developer's machine, rather than at runtime on a user's server. This paid for itself
repeatedly: the compiler found all three real differences between the versions, and they were not
the ones anyone would have guessed.

**Loading reflectively is what lets six mutually incompatible modules coexist.** Nothing references
them statically, so the JVM never verifies the five that do not match, and their missing classes
never matter.

**Refusing an unknown version is the kinder failure.** Falling back to the newest adapter produces a
plugin that starts cleanly and then sends packets the client cannot parse — a symptom that looks
like a rendering bug and leads nowhere. A refusal at startup names the problem.

**Generating from a reference** keeps five near-identical files honest. They were previously
copy-pasted by hand, which works right up until a fix lands in four of five.

### Why the modules are not merged

`v1_21_4`, `v1_21_5` and `v1_21_8` currently produce **byte-identical** source. Merging them into
one module is tempting and was rejected.

Merging means an adapter compiled against 1.21.4 running on a 1.21.8 server, which relies on NMS
staying binary compatible across patch releases. It usually is. Occasionally it is not, and when it
is not the failure is a `NoSuchMethodError` at runtime on a version nobody tested — precisely the
class of failure this whole design exists to prevent.

The cost of not merging is disk space in the jar and a script run. The cost of merging is a category
of bug that only users find. That is not a close trade.

## The differences, and when they appeared

| Change | From | Handled by |
|---|---|---|
| authlib 7 turned `GameProfile` into a record: `getProperties()` → `properties()` | 1.21.9 | a `sed` delta in the sync script |
| `ServerboundInteractPacket`'s visitor `Handler` replaced by a plain record | 26.1 | `tools/patch-interact-record.py` |
| Static `EntityType` constants removed | 26.2 | reference code looks types up in the registry by key, so no delta is needed |

The third is worth noting: rather than adding a fourth delta, the reference implementation was
changed to walk the registry and compare keys as text — which works identically on every version and
removed the difference entirely. Where a version difference can be designed away instead of
patched around, it should be.

## Consequences

- **Never edit `versions/v1_21_5` and up directly.** The next sync overwrites it. Edit `v1_21_4` and
  run `tools/sync-adapters.sh`.
- A genuine new difference goes in the script's delta section, with a comment naming what changed and
  when.
- Adding a Minecraft version: add a module with its dev bundle, add it to `settings.gradle.kts`,
  `plugin/build.gradle.kts`, `VersionAdapterResolver.withDefaults()` and the sync script's target
  list.
- The build needs six dev bundles. From a cold cache that is several minutes; CI caches them keyed on
  the version modules' build scripts.
- The 26.x modules need a **JDK 25** toolchain, because Minecraft 26.1+ ships Java 25 class files and
  `javac` cannot read class files newer than itself. They still emit Java 21 bytecode via
  `--release`, which is what lets `plugin` — compiled at 21 — depend on them.
