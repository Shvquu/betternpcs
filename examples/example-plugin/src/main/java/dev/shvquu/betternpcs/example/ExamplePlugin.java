package dev.shvquu.betternpcs.example;

import dev.shvquu.betternpcs.api.ApiVersion;
import dev.shvquu.betternpcs.api.BetterNPCs;
import dev.shvquu.betternpcs.api.BetterNPCsApi;
import dev.shvquu.betternpcs.api.action.ActionDefinition;
import dev.shvquu.betternpcs.api.animation.NpcAnimation;
import dev.shvquu.betternpcs.api.interaction.InteractionType;
import dev.shvquu.betternpcs.api.npc.Npc;
import dev.shvquu.betternpcs.api.npc.NpcType;
import dev.shvquu.betternpcs.api.npc.property.HologramSettings;
import dev.shvquu.betternpcs.api.npc.property.LookMode;
import dev.shvquu.betternpcs.api.npc.property.LookSettings;
import dev.shvquu.betternpcs.api.npc.property.NametagSettings;
import dev.shvquu.betternpcs.api.npc.property.NpcAppearance;
import dev.shvquu.betternpcs.api.npc.property.NpcEquipment;
import dev.shvquu.betternpcs.api.npc.property.NpcPosition;
import dev.shvquu.betternpcs.api.npc.property.NpcVisibility;
import dev.shvquu.betternpcs.api.skin.SkinSource;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * A worked example of integrating with BetterNPCs.
 *
 * <p>This module is part of the normal build, not a documentation folder that rots. It compiles
 * against nothing but {@code betternpcs-api}, exactly as a third-party plugin does, so a change that
 * breaks API compatibility fails {@code ./gradlew build} here rather than in someone else's project.
 *
 * <p>The companion {@link ExampleListener} shows the event side.
 */
public final class ExamplePlugin extends JavaPlugin {

    /** The API minor version this example relies on. */
    private static final int REQUIRED_API_MINOR = 0;

    @Override
    public void onEnable() {
        if (!BetterNPCs.isAvailable()) {
            // Reachable only if the dependency was not declared in plugin.yml. Saying so beats a
            // NoClassDefFoundError from somewhere deeper.
            getLogger().severe("BetterNPCs is not installed. Disabling.");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        BetterNPCsApi api = BetterNPCs.get();

        ApiVersion apiVersion = api.apiVersion();
        if (!apiVersion.isCompatibleWith(ApiVersion.CURRENT.major(), REQUIRED_API_MINOR)) {
            getLogger().severe(
                    "This plugin needs BetterNPCs API " + ApiVersion.CURRENT.major() + "."
                            + REQUIRED_API_MINOR + " or newer, but the server has " + apiVersion + ".");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        getServer().getPluginManager().registerEvents(new ExampleListener(this), this);
        createGreeterIfMissing(api);
    }

    /**
     * Creates the example NPC, unless it is already there from a previous run.
     *
     * <p>Checking first matters: NPCs are persistent, so a plugin that creates one on every enable
     * ends up with a pile of duplicates after a few restarts.
     *
     * @param api the BetterNPCs API
     */
    private void createGreeterIfMissing(BetterNPCsApi api) {
        if (api.npcManager().exists("example-greeter")) {
            getLogger().info("The example NPC already exists; leaving it as it is.");
            return;
        }

        // A real plugin would take this from its own configuration rather than assume a world name.
        if (getServer().getWorlds().isEmpty()) {
            getLogger().warning("No world is loaded; skipping the example NPC.");
            return;
        }
        NpcPosition spawn = NpcPosition.of(getServer().getWorlds().get(0).getSpawnLocation());

        Npc greeter = api.npcManager().create("example-greeter", NpcType.PLAYER, spawn);

        greeter.setDisplayName("<gradient:#00c6ff:#0072ff><bold>Guide</bold></gradient>");

        greeter.setNametag(NametagSettings.builder()
                .text("<gray>[<aqua>Info<gray>] <white>" + NametagSettings.NAME_PLACEHOLDER)
                .viewDistance(24)
                .build());

        greeter.setHologram(HologramSettings.builder()
                .line("<yellow>Right-click me")
                .line("<gray>to learn the basics")
                .verticalOffset(0.3)
                .build());

        // Skin resolution is a network call, so it returns a future and never blocks the main
        // thread. Everything else about the NPC is already applied by the time this starts.
        greeter.setSkin(SkinSource.playerName("Notch")).exceptionally(failure -> {
            getLogger().warning("Could not load the example skin: " + failure.getMessage());
            return null;
        });

        greeter.setEquipment(NpcEquipment.builder()
                .mainHand(new ItemStack(Material.MAP))
                .helmet(new ItemStack(Material.GOLDEN_HELMET))
                .build());

        greeter.setAppearance(NpcAppearance.builder()
                .glowing(true)
                .collidable(false)
                .build());

        greeter.setLook(LookSettings.builder()
                .mode(LookMode.LOOK_AT_VIEWER)
                .range(12)
                .updateInterval(2)
                .build());

        greeter.setVisibility(NpcVisibility.withinDistance(32));

        greeter.addAction(InteractionType.RIGHT_CLICK,
                ActionDefinition.parse("message: <green>Welcome, <player_name>!"));
        greeter.addAction(InteractionType.RIGHT_CLICK,
                ActionDefinition.parse("sound: entity.villager.yes"));
        greeter.addAction(InteractionType.SHIFT_RIGHT_CLICK,
                ActionDefinition.parse("message: <gray>Nothing hidden here."));

        // Extension data. Namespaced, because every plugin writes into the same map.
        greeter.setMetadata("example:created-by", getName());

        greeter.spawn();
        greeter.playAnimation(NpcAnimation.SWING_MAIN_HAND);

        getLogger().info("Created the example NPC at " + greeter.position() + ".");
    }

    @Override
    public void onDisable() {
        // Nothing to do. The NPC is persistent on purpose, so that a restart does not lose whatever
        // an administrator changed about it. Delete it explicitly if a clean uninstall is wanted:
        //
        //   BetterNPCs.find().ifPresent(api -> api.npcManager().delete("example-greeter"));
    }
}
