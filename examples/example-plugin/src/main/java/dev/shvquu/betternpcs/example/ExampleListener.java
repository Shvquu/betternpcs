package dev.shvquu.betternpcs.example;

import dev.shvquu.betternpcs.api.animation.NpcAnimation;
import dev.shvquu.betternpcs.api.event.NpcDeleteEvent;
import dev.shvquu.betternpcs.api.event.NpcInteractEvent;
import dev.shvquu.betternpcs.api.event.NpcLoadEvent;
import dev.shvquu.betternpcs.api.interaction.InteractionType;
import java.util.Objects;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * The event half of the integration example.
 *
 * <p>Every BetterNPCs event is fired on the main thread, so a listener can call the Bukkit API
 * without scheduling anything.
 */
final class ExampleListener implements Listener {

    private final JavaPlugin plugin;

    ExampleListener(JavaPlugin plugin) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
    }

    /**
     * Reacts to a player clicking an NPC.
     *
     * <p>Runs at {@link EventPriority#NORMAL} and honours {@code ignoreCancelled}, so a permission
     * plugin that cancelled the interaction earlier is respected rather than worked around.
     *
     * @param event the interaction
     */
    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onInteract(NpcInteractEvent event) {
        if (!event.getNpc().name().equals("example-greeter")) {
            return;
        }

        if (event.getInteraction() == InteractionType.LEFT_CLICK) {
            event.getPlayer().sendMessage(
                    MiniMessage.miniMessage().deserialize("<red>Please do not hit the guide."));
            event.getNpc().playAnimation(NpcAnimation.TAKE_DAMAGE, event.getPlayer());

            // Stops the NPC's own configured actions from running as well.
            event.setCancelled(true);
            return;
        }

        // The exact point on the hit box is only present for the interact-at form of the packet, so
        // it always has to be treated as optional.
        event.getClickedPosition().ifPresent(offset ->
                plugin.getLogger().fine(() -> "Clicked at y=" + offset.getY() + " on the guide."));
    }

    /**
     * Re-attaches state after NPCs have been loaded.
     *
     * <p>{@code /npc reload} invalidates every NPC handle, so this event — not plugin enable — is
     * where a cached reference becomes valid again.
     *
     * @param event the load
     */
    @EventHandler
    public void onLoad(NpcLoadEvent event) {
        event.getNpc().metadata("example:created-by").ifPresent(creator ->
                plugin.getLogger().fine(() ->
                        "Loaded NPC " + event.getNpc().name() + " created by " + creator + "."));
    }

    /**
     * Refuses to let the example NPC be deleted while this plugin is running.
     *
     * <p>Shown because it is the one place where an extension can still read what it stored on an
     * NPC. A real plugin would clean up rather than veto.
     *
     * @param event the deletion
     */
    @EventHandler(priority = EventPriority.HIGH)
    public void onDelete(NpcDeleteEvent event) {
        if (!event.getNpc().name().equals("example-greeter")) {
            return;
        }
        if (event.getRemover() != null) {
            event.getRemover().sendMessage(MiniMessage.miniMessage().deserialize(
                    "<red>The example NPC is managed by " + plugin.getName() + "."));
        }
        event.setCancelled(true);
    }
}
