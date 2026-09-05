package dev.shvquu.betternpcs.core.npc;

import dev.shvquu.betternpcs.api.npc.NpcVisibilityRule;
import dev.shvquu.betternpcs.api.npc.property.NpcVisibility;
import dev.shvquu.betternpcs.core.config.BetterNpcsConfig;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Supplier;
import org.bukkit.Location;
import org.bukkit.entity.Player;

/**
 * Decides whether one player may see one NPC.
 *
 * <p>Checks run cheapest first, and the order is the whole design. This method is called for every
 * player against every nearby NPC several times a second, so an expensive check placed before a
 * cheap one that would have rejected the pairing anyway multiplies straight into the server's tick
 * time.
 *
 * <ol>
 *   <li>a per-player override — one map lookup, and it settles the question outright</li>
 *   <li>the world — a string comparison</li>
 *   <li>distance — arithmetic, with no square root</li>
 *   <li>a permission node — a permission lookup, which is not free on a server with a large
 *       permissions plugin</li>
 *   <li>registered rules — arbitrary third-party code</li>
 * </ol>
 *
 * <p>Called on the main server thread.
 *
 * @since 1.0.0
 */
public final class VisibilityService {

    private final List<NpcVisibilityRule> rules = new CopyOnWriteArrayList<>();
    private final Supplier<BetterNpcsConfig> config;

    /**
     * Creates the service.
     *
     * @param config supplies the current configuration, re-read after a reload
     * @throws NullPointerException if {@code config} is {@code null}
     */
    public VisibilityService(Supplier<BetterNpcsConfig> config) {
        this.config = Objects.requireNonNull(config, "config");
    }

    /**
     * Returns whether a player may see an NPC.
     *
     * @param npc    the NPC
     * @param player the player
     * @return {@code true} if the NPC should be rendered for that player
     * @throws NullPointerException if either argument is {@code null}
     */
    public boolean canSee(NpcHandle npc, Player player) {
        Objects.requireNonNull(npc, "npc");
        Objects.requireNonNull(player, "player");

        if (!npc.isSpawned()) {
            return false;
        }

        NpcVisibility visibility = npc.visibility();

        Boolean override = npc.renderState().overrideFor(player);
        if (override != null && !override) {
            // An explicit hide beats everything, including a rule that would show the NPC.
            return false;
        }

        Location location = player.getLocation();
        if (location.getWorld() == null || !location.getWorld().getName().equals(npc.position().world())) {
            return false;
        }

        if (npc.position().distanceSquaredTo(location) > viewDistanceSquared(visibility)) {
            return false;
        }

        // An explicit show skips the NPC's own rules but not distance: "show this NPC to that player"
        // means "when they are near it", not "render it from across the world".
        boolean explicitlyShown = override != null && override;

        if (!explicitlyShown) {
            if (!visibility.visibleByDefault()) {
                return false;
            }
            if (visibility.permission().filter(node -> !player.hasPermission(node)).isPresent()) {
                return false;
            }
        }

        for (NpcVisibilityRule rule : rules) {
            if (!rule.isVisible(npc, player)) {
                return false;
            }
        }
        return true;
    }

    /**
     * Returns the squared radius within which an NPC is rendered.
     *
     * @param visibility the NPC's visibility settings
     * @return the squared radius in blocks
     */
    public double viewDistanceSquared(NpcVisibility visibility) {
        double distance = viewDistance(visibility);
        return distance * distance;
    }

    /**
     * Returns the radius within which an NPC is rendered, resolving the "use the server default"
     * marker.
     *
     * @param visibility the NPC's visibility settings
     * @return the radius in blocks
     */
    public double viewDistance(NpcVisibility visibility) {
        return visibility.usesServerViewDistance()
                ? config.get().npc().defaultViewDistance()
                : visibility.viewDistance();
    }

    /**
     * Registers an extra condition.
     *
     * @param rule the rule to add
     * @throws NullPointerException if {@code rule} is {@code null}
     */
    public void register(NpcVisibilityRule rule) {
        rules.add(Objects.requireNonNull(rule, "rule"));
    }

    /**
     * Removes a previously registered condition.
     *
     * @param rule the rule to remove, compared by identity
     * @return {@code true} if the rule was registered
     * @throws NullPointerException if {@code rule} is {@code null}
     */
    public boolean unregister(NpcVisibilityRule rule) {
        Objects.requireNonNull(rule, "rule");
        return rules.removeIf(candidate -> candidate == rule);
    }

    /**
     * Returns how many rules are registered.
     *
     * @return the rule count
     */
    public int ruleCount() {
        return rules.size();
    }
}
