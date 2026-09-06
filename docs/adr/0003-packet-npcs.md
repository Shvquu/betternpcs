# 0003 — Packet NPCs built from detached entities

**Status:** accepted

## Context

NPCs are rendered with packets rather than as real server entities. That is what makes thousands of
them practical: they cost nothing when nobody is near them, they are unaffected by mob caps and
chunk ticking, and nothing can push them out of position.

The difficulty is that Minecraft's clientbound packets are not designed to be constructed without an
entity. Several take an `Entity` rather than an id — head rotation, animation, entity event — and
entity metadata is a list of typed values whose **numeric ids change between Minecraft versions**.

Hard-coding those ids is the usual approach and the usual source of silent breakage: an id that
shifts by one renders the wrong property, with no error anywhere.

## Decision

Each adapter builds a **real Minecraft entity and never adds it to a level**. It exists purely as a
packet builder, cached by entity id, and is rebuilt whenever the engine allocates new ids.

Where a property can be set through the entity's **Bukkit wrapper**, it is — in particular for text
displays, which are configured through `org.bukkit.entity.TextDisplay` rather than by writing
metadata indices.

Entity ids are allocated by the engine, counting down from `Integer.MAX_VALUE`, and assigned to the
detached entity with `setId`.

## Why

**A detached entity solves both problems at once.** The constructors that demand an `Entity` are
satisfied, and `getEntityData()` produces correctly numbered metadata for whatever version the
adapter was compiled against — without a single magic number in the adapter.

**The Bukkit wrapper is the more stable interface.** `TextDisplay`'s methods — `text`,
`setBillboard`, `setAlignment`, `setBackgroundColor`, `setTextOpacity`, `setTransformation` — have
been unchanged across every version BetterNPCs supports, while the metadata indices behind them were
renumbered more than once. Going through the wrapper made three of the six adapters compile
unchanged that would otherwise have needed per-version index tables.

**Counting ids down from the top** keeps them clear of the server's own, which count up from one. A
collision would need a session that spawned two billion entities, and a collision matters: a client
told about two entities with the same id renders neither correctly, and the symptom looks like a
rendering bug rather than an id clash.

**Fresh ids on respawn.** Changing an NPC's type or skin means despawning and respawning it, and the
new entity gets new ids rather than reusing the old ones. Reusing them asks the client to accept a
different entity under an id it already has, which several client versions handle by rendering
neither.

## Consequences

- Adapters hold per-NPC state after all — a cache of detached entities. It is an implementation
  detail behind a view-driven interface, and is reconstructible from the `NpcView` at any time.
- Creating a detached `ServerPlayer` needs a `MinecraftServer` and a `ServerLevel`, so an NPC in an
  unloaded world simply is not built. The engine handles that: NPCs spawn when their world loads.
- The one value still written by metadata id is a player's skin-layer byte, whose accessor is not
  public and has no Bukkit equivalent. It is set through the entity's own data holder rather than by
  constructing a raw data value, so the serialiser stays whatever the running version says it is.
- Two entity-event ids (hurt, critical hit, villager particles) are still numeric constants. Being
  wrong there shows up as the wrong particle rather than a malformed packet, which is why it is
  tolerable.
- Interactions arrive through a netty channel handler, because a packet NPC does not exist on the
  server and no Bukkit event is ever fired for one. The handler decodes and forwards; deciding
  whether an entity id belongs to an NPC, and hopping to the main thread, is the engine's job — so
  the common case, a click on a real entity, costs one map lookup on the network thread.
