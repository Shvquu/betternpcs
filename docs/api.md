# API

The public API is a separate artifact under the **MIT licence**. A plugin that compiles against it
is not bound by the GPL-3.0 that covers the implementation — that split is the entire reason the
modules are separate.

Only `dev.shvquu.betternpcs.api` is API. Anything in `core`, `storage` or `nms` is implementation
detail and changes without notice, as do members annotated `@Internal`.

## Depending on it

```kotlin
repositories {
    maven("https://maven.pkg.github.com/Shvquu/betternpcs") {
        credentials {
            username = providers.gradleProperty("gpr.user").orNull
            password = providers.gradleProperty("gpr.token").orNull
        }
    }
}

dependencies {
    compileOnly("dev.shvquu.betternpcs:betternpcs-api:1.0.0")
}
```

> **GitHub Packages requires authentication even for public packages.** That is a limitation of the
> registry, not of this project. Put a personal access token with the `read:packages` scope in
> `~/.gradle/gradle.properties` as `gpr.user` and `gpr.token`.
>
> Maven Central needs no token and is the better home for a public API; it is planned but requires
> namespace verification and signing keys that are not yet set up. Until then the artifact is also
> attached to every [GitHub Release](https://github.com/Shvquu/betternpcs/releases) if you would
> rather vendor it.

Declare a hard dependency so Bukkit loads you afterwards:

```yaml
depend: [BetterNPCs]
```

## Getting the API

```java
BetterNPCsApi api = BetterNPCs.get();
```

It is available from the moment BetterNPCs enables. Without the `depend` above, use
`BetterNPCs.isAvailable()` or `BetterNPCs.find()` rather than assuming.

The API is also registered with Bukkit's services manager under `BetterNPCsApi`, if you would rather
not use a static holder.

### Version checks

The API is versioned separately from the plugin, following semantic versioning. If you use anything
added after 1.0.0, check at startup — otherwise the failure is a `NoSuchMethodError` at some
arbitrary later point:

```java
if (!api.apiVersion().isCompatibleWith(1, 2)) {
    getLogger().severe("BetterNPCs API 1.2 or newer is required.");
    getServer().getPluginManager().disablePlugin(this);
    return;
}
```

## Threading

**Every method must be called on the main server thread** unless its documentation says otherwise.

Methods returning a `CompletableFuture` do their work elsewhere and **complete on a background
thread**. Schedule back before touching Bukkit:

```java
npc.setSkin(SkinSource.playerName("Notch")).thenRun(() ->
        getServer().getScheduler().runTask(this, () -> /* Bukkit API here */));
```

Events are always fired on the main thread, including those whose cause was asynchronous.

## NPCs

```java
Npc npc = api.npcManager().create("guide", NpcType.PLAYER, NpcPosition.of(location));

npc.setDisplayName("<gradient:#00c6ff:#0072ff>Guide</gradient>");
npc.setSkin(SkinSource.playerName("Notch"));
npc.setEquipment(EquipmentSlot.HEAD, new ItemStack(Material.GOLDEN_HELMET));
npc.setLook(LookSettings.lookAtViewer());
npc.addAction(InteractionType.RIGHT_CLICK, ActionDefinition.parse("message: <green>Hello!"));

npc.spawn();
```

Setters take effect immediately for everyone who can see the NPC. They **mark the NPC dirty rather
than writing to storage**; the engine saves periodically and on shutdown, and `save()` forces it.

Display names, nametags and hologram lines are MiniMessage **source**, not rendered components,
because placeholders resolve per viewer.

### Lifetime

A handle is invalidated when the NPC is deleted, and every mutating method then throws
`IllegalStateException`. Check `isRemoved()` if you may have held a reference across an
administrator's `/npc remove`.

`NpcManager.reload()` invalidates **every** handle. Treat `NpcLoadEvent` rather than plugin enable
as the point at which your NPC references become valid.

### Snapshots

`npc.snapshot()` freezes everything into an immutable `NpcSnapshot`, safe to hand to another thread
— unlike the handle. `npc.apply(snapshot)` puts it back, which is how an undo, a template or a
migration works.

## Events

All extend `NpcEvent`, all fire on the main thread.

| Event | Cancellable | |
|---|---|---|
| `NpcCreateEvent` | yes | Before registration. Configure defaults here. |
| `NpcDeleteEvent` | yes | Last chance to read metadata |
| `NpcSpawnEvent` | yes | NPC becomes active |
| `NpcDespawnEvent` | no | After the fact, with a reason |
| `NpcInteractEvent` | yes | A click, of any kind |
| `NpcDamageEvent` | yes | An attack on a non-invulnerable NPC |
| `NpcLoadEvent` | no | Per NPC, after loading |
| `NpcSaveEvent` | yes | Snapshot captured; you may replace it |

**There is no per-viewer spawn event.** The tracker decides visibility for every nearby player
several times a second, and firing a Bukkit event each time would cost more than the rendering. Use
an `NpcVisibilityRule` instead.

## Extension points

### Actions

```java
api.actionRegistry().register(new NpcActionHandler() {
    @Override public String id() { return "myplugin:quest"; }

    @Override public void validate(String argument) {
        if (argument.isBlank()) throw new IllegalArgumentException("needs a quest id");
    }

    @Override public void execute(ActionContext context) {
        startQuest(context.player(), context.resolve(context.argument()));
    }
});
```

Registering makes `myplugin:quest: some-id` usable in every NPC's configuration and in
`/npc action`. `validate` runs when the action is added or loaded, so a typo is reported to whoever
typed it rather than to the first player who clicks.

Handlers must hold no per-execution state — `execute` may be entered for several players in the same
tick. Use `context.attribute(...)` for chain-scoped state.

### Visibility rules

```java
api.npcManager().registerVisibilityRule((npc, player) ->
        !npc.metadata("quest:required").isPresent()
                || hasCompleted(player, npc.metadata("quest:required").get()));
```

Consulted **after** the built-in distance and permission checks, so it only sees pairings that
already passed the cheap tests. Every registered rule must agree.

It is still called for every candidate player-NPC pairing, several times a second. Make it an
in-memory lookup, cache the answer, and call `npc.refreshVisibility()` when the thing it depends on
changes.

### Skin providers

```java
api.skinService().registerProvider(new SkinProvider() {
    @Override public String id() { return "myplugin"; }
    @Override public int priority() { return MOJANG_PRIORITY + 1; }   // consulted before Mojang
    @Override public boolean supports(SkinSource source) { return source.kind() == Kind.NAME; }
    @Override public CompletableFuture<Optional<NpcSkin>> fetch(SkinSource source) { ... }
});
```

Called off the main thread. Providers are tried highest priority first and the first non-empty
answer wins, so a custom provider can shadow Mojang for some names and defer for the rest. Apply
your own timeout — a provider that hangs stalls every skin request behind it.

### Extensions

For a plugin registering several related things, or one that needs to be reloadable:

```java
api.extensionManager().register(new NpcExtension() {
    @Override public String id() { return "myplugin"; }
    @Override public Set<String> dependencies() { return Set.of("otherplugin"); }
    @Override public void onEnable(BetterNPCsApi api) { /* register handlers here */ }
    @Override public void onDisable() { /* anything else */ }
});
```

Dependencies are honoured regardless of plugin load order: an extension whose dependency is not
registered yet waits, and is enabled as soon as it arrives. Handlers, providers and rules registered
during `onEnable` are removed automatically on disable.

## Per-NPC data

`npc.setMetadata(key, value)` stores arbitrary strings with the NPC, persisted and otherwise
untouched by the engine. Namespace your keys — every plugin writes into the same map.

## A worked example

[`examples/example-plugin/`](../examples/example-plugin) is a complete integration compiled by CI
against nothing but `betternpcs-api`, exactly as your plugin does. If a change breaks API
compatibility, that module stops compiling and the build goes red — so it cannot quietly rot.
