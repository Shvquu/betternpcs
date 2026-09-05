package dev.shvquu.betternpcs.core.action;

import dev.shvquu.betternpcs.api.action.ActionContext;
import dev.shvquu.betternpcs.api.action.ActionRegistry;
import dev.shvquu.betternpcs.api.action.BuiltinActions;
import dev.shvquu.betternpcs.api.action.NpcActionHandler;
import dev.shvquu.betternpcs.api.animation.NpcAnimation;
import java.time.Duration;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.title.Title;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Server;
import org.bukkit.World;
import org.bukkit.entity.Player;

/**
 * The action handlers BetterNPCs registers itself.
 *
 * <p>Kept in one file because they are one feature, they are each a handful of lines, and reading
 * them side by side is how their consistency — argument parsing, validation, error wording — stays
 * visible. Anything an extension would add belongs in the extension, not here.
 *
 * <p>Every handler validates its argument in {@link NpcActionHandler#validate(String)} where the
 * argument has a shape at all. That check runs when an action is added or loaded, so a typo is
 * reported to whoever made it rather than to the first player who clicks the NPC.
 *
 * @since 1.0.0
 */
public final class BuiltinActionHandlers {

    private BuiltinActionHandlers() {
        throw new AssertionError("No instances");
    }

    /**
     * Registers every built-in handler.
     *
     * @param registry where to register them
     * @throws NullPointerException  if {@code registry} is {@code null}
     * @throws IllegalStateException if any built-in name is already taken
     */
    public static void registerAll(ActionRegistry registry) {
        Objects.requireNonNull(registry, "registry");

        registry.register(new Message());
        registry.register(new Broadcast());
        registry.register(new ActionBar());
        registry.register(new TitleAction());
        registry.register(new PlayerCommand());
        registry.register(new ConsoleCommand());
        registry.register(new Sound());
        registry.register(new ParticleAction());
        registry.register(new Teleport());
        registry.register(new Animation());
        registry.register(new RequirePermission());
        registry.register(new Cooldown());
    }

    // -------------------------------------------------------------------------------------------
    // Messaging
    // -------------------------------------------------------------------------------------------

    /** Sends MiniMessage text to the interacting player. */
    private static final class Message implements NpcActionHandler {

        @Override
        public String id() {
            return BuiltinActions.MESSAGE;
        }

        @Override
        public Set<String> aliases() {
            return Set.of("msg", "tell");
        }

        @Override
        public String argumentDescription() {
            return "<MiniMessage text>";
        }

        @Override
        public void execute(ActionContext context) {
            context.player().sendMessage(context.render(context.argument()));
        }
    }

    /** Sends MiniMessage text to every online player. */
    private static final class Broadcast implements NpcActionHandler {

        @Override
        public String id() {
            return BuiltinActions.BROADCAST;
        }

        @Override
        public String argumentDescription() {
            return "<MiniMessage text>";
        }

        @Override
        public void execute(ActionContext context) {
            // Rendered once per recipient rather than once overall, so that a placeholder resolves
            // to the reader rather than to whoever happened to click the NPC.
            Component shared = context.render(context.argument());
            for (Player online : context.player().getServer().getOnlinePlayers()) {
                online.sendMessage(shared);
            }
        }
    }

    /** Shows MiniMessage text in the interacting player's action bar. */
    private static final class ActionBar implements NpcActionHandler {

        @Override
        public String id() {
            return BuiltinActions.ACTIONBAR;
        }

        @Override
        public String argumentDescription() {
            return "<MiniMessage text>";
        }

        @Override
        public void execute(ActionContext context) {
            context.player().sendActionBar(context.render(context.argument()));
        }
    }

    /** Shows a title and subtitle, separated by {@code |}. */
    private static final class TitleAction implements NpcActionHandler {

        private static final String SEPARATOR = "|";

        @Override
        public String id() {
            return BuiltinActions.TITLE;
        }

        @Override
        public String argumentDescription() {
            return "<title>|<subtitle>";
        }

        @Override
        public void execute(ActionContext context) {
            String argument = context.argument();
            int separator = argument.indexOf(SEPARATOR);

            String main = separator < 0 ? argument : argument.substring(0, separator);
            String sub = separator < 0 ? "" : argument.substring(separator + 1);

            context.player().showTitle(Title.title(context.render(main), context.render(sub)));
        }
    }

    // -------------------------------------------------------------------------------------------
    // Commands
    // -------------------------------------------------------------------------------------------

    /** Runs a command as the interacting player, with the player's own permissions. */
    private static final class PlayerCommand implements NpcActionHandler {

        @Override
        public String id() {
            return BuiltinActions.PLAYER_COMMAND;
        }

        @Override
        public Set<String> aliases() {
            return Set.of("player-command", "run");
        }

        @Override
        public String argumentDescription() {
            return "<command without the leading slash>";
        }

        @Override
        public void validate(String argument) {
            requireNotBlank(argument, "A command action needs a command to run");
        }

        @Override
        public void execute(ActionContext context) {
            // performCommand carries the player's own permissions, so this action can never grant
            // access to something the player could not already run themselves.
            context.player().performCommand(stripSlash(context.resolve(context.argument())));
        }
    }

    /** Runs a command from the console, with full permissions. */
    private static final class ConsoleCommand implements NpcActionHandler {

        @Override
        public String id() {
            return BuiltinActions.CONSOLE_COMMAND;
        }

        @Override
        public Set<String> aliases() {
            return Set.of("console-command");
        }

        @Override
        public String argumentDescription() {
            return "<command without the leading slash>";
        }

        @Override
        public void validate(String argument) {
            requireNotBlank(argument, "A console action needs a command to run");
        }

        @Override
        public void execute(ActionContext context) {
            Server server = context.player().getServer();
            server.dispatchCommand(
                    server.getConsoleSender(), stripSlash(context.resolve(context.argument())));
        }
    }

    private static String stripSlash(String command) {
        String trimmed = command.strip();
        return trimmed.startsWith("/") ? trimmed.substring(1) : trimmed;
    }

    // -------------------------------------------------------------------------------------------
    // Effects
    // -------------------------------------------------------------------------------------------

    /** Plays a sound to the interacting player. */
    private static final class Sound implements NpcActionHandler {

        @Override
        public String id() {
            return BuiltinActions.SOUND;
        }

        @Override
        public String argumentDescription() {
            return "<sound key> [volume] [pitch]";
        }

        @Override
        public void validate(String argument) {
            String[] parts = split(argument);
            requireNotBlank(parts.length == 0 ? "" : parts[0], "A sound action needs a sound key");
            if (parts.length > 1) {
                parseFloat(parts[1], "volume");
            }
            if (parts.length > 2) {
                parseFloat(parts[2], "pitch");
            }
        }

        @Override
        public void execute(ActionContext context) {
            String[] parts = split(context.argument());
            float volume = parts.length > 1 ? parseFloat(parts[1], "volume") : 1.0f;
            float pitch = parts.length > 2 ? parseFloat(parts[2], "pitch") : 1.0f;

            Player player = context.player();
            // The string overload takes a raw sound key, which sidesteps the registry lookups that
            // changed shape several times across the versions this plugin supports — and lets a
            // resource pack's custom sound work without any special handling.
            player.playSound(player.getLocation(), parts[0].toLowerCase(Locale.ROOT), volume, pitch);
        }
    }

    /** Shows particles at the NPC. */
    private static final class ParticleAction implements NpcActionHandler {

        @Override
        public String id() {
            return BuiltinActions.PARTICLE;
        }

        @Override
        public String argumentDescription() {
            return "<particle> [count] [spread]";
        }

        @Override
        public void validate(String argument) {
            String[] parts = split(argument);
            requireNotBlank(parts.length == 0 ? "" : parts[0], "A particle action needs a particle");
            parseParticle(parts[0]);
            if (parts.length > 1) {
                parseInt(parts[1], "count");
            }
            if (parts.length > 2) {
                parseFloat(parts[2], "spread");
            }
        }

        @Override
        public void execute(ActionContext context) {
            String[] parts = split(context.argument());
            Particle particle = parseParticle(parts[0]);
            int count = parts.length > 1 ? parseInt(parts[1], "count") : 10;
            double spread = parts.length > 2 ? parseFloat(parts[2], "spread") : 0.5;

            context.npc().position().toLocation().ifPresent(location ->
                    // Shown to the interacting player alone. A particle burst visible to everyone in
                    // range every time anybody clicks a busy NPC is a hub full of confetti.
                    context.player().spawnParticle(
                            particle, location.add(0, 1, 0), count, spread, spread, spread));
        }

        private static Particle parseParticle(String name) {
            String normalised = name.trim().toUpperCase(Locale.ROOT);
            int colon = normalised.indexOf(':');
            if (colon >= 0) {
                normalised = normalised.substring(colon + 1);
            }
            try {
                return Particle.valueOf(normalised);
            } catch (IllegalArgumentException unknown) {
                // Mojang renamed several particles between the versions this plugin supports, so an
                // unknown name is as likely to be a version difference as a typo. Say both.
                throw new IllegalArgumentException(
                        "'" + name + "' is not a particle on this Minecraft version", unknown);
            }
        }
    }

    /** Teleports the interacting player. */
    private static final class Teleport implements NpcActionHandler {

        @Override
        public String id() {
            return BuiltinActions.TELEPORT;
        }

        @Override
        public Set<String> aliases() {
            return Set.of("tp");
        }

        @Override
        public String argumentDescription() {
            return "<world> <x> <y> <z> [yaw] [pitch]";
        }

        @Override
        public void validate(String argument) {
            String[] parts = split(argument);
            if (parts.length < 4) {
                throw new IllegalArgumentException(
                        "A teleport action needs a world and three coordinates");
            }
            parseDouble(parts[1], "x");
            parseDouble(parts[2], "y");
            parseDouble(parts[3], "z");
            if (parts.length > 4) {
                parseFloat(parts[4], "yaw");
            }
            if (parts.length > 5) {
                parseFloat(parts[5], "pitch");
            }
        }

        @Override
        public void execute(ActionContext context) {
            String[] parts = split(context.resolve(context.argument()));
            Player player = context.player();

            World world = player.getServer().getWorld(parts[0]);
            if (world == null) {
                throw new IllegalStateException("The world '" + parts[0] + "' is not loaded");
            }

            Location target = new Location(
                    world,
                    parseDouble(parts[1], "x"),
                    parseDouble(parts[2], "y"),
                    parseDouble(parts[3], "z"),
                    parts.length > 4 ? parseFloat(parts[4], "yaw") : player.getLocation().getYaw(),
                    parts.length > 5 ? parseFloat(parts[5], "pitch") : player.getLocation().getPitch());

            player.teleport(target);
        }
    }

    /** Plays an animation on the NPC, for the interacting player alone. */
    private static final class Animation implements NpcActionHandler {

        @Override
        public String id() {
            return BuiltinActions.ANIMATION;
        }

        @Override
        public String argumentDescription() {
            return "<" + String.join("|", animationNames()) + ">";
        }

        @Override
        public void validate(String argument) {
            NpcAnimation.byName(argument).orElseThrow(() -> new IllegalArgumentException(
                    "'" + argument + "' is not an animation. Known: " + String.join(", ", animationNames())));
        }

        @Override
        public void execute(ActionContext context) {
            NpcAnimation.byName(context.argument()).ifPresent(animation ->
                    context.npc().playAnimation(animation, context.player()));
        }

        private static List<String> animationNames() {
            return java.util.Arrays.stream(NpcAnimation.values()).map(Enum::name).toList();
        }
    }

    // -------------------------------------------------------------------------------------------
    // Flow control
    // -------------------------------------------------------------------------------------------

    /** Stops the chain unless the player holds a permission. */
    private static final class RequirePermission implements NpcActionHandler {

        @Override
        public String id() {
            return BuiltinActions.REQUIRE_PERMISSION;
        }

        @Override
        public Set<String> aliases() {
            return Set.of("permission");
        }

        @Override
        public String argumentDescription() {
            return "<permission node>";
        }

        @Override
        public void validate(String argument) {
            requireNotBlank(argument, "A permission action needs a permission node");
        }

        @Override
        public void execute(ActionContext context) {
            if (!context.player().hasPermission(context.argument().strip())) {
                context.stopChain();
            }
        }
    }

    /**
     * Stops the chain if the same player triggered the same NPC too recently.
     *
     * <p>The one built-in handler with state of its own. It is keyed by NPC and player rather than
     * kept in the context, because the whole point is to remember something across separate
     * interactions.
     */
    private static final class Cooldown implements NpcActionHandler {

        private final Map<String, Long> lastRun = new ConcurrentHashMap<>();

        @Override
        public String id() {
            return BuiltinActions.COOLDOWN;
        }

        @Override
        public String argumentDescription() {
            return "<seconds>";
        }

        @Override
        public void validate(String argument) {
            double seconds = parseDouble(argument, "cooldown");
            if (seconds < 0) {
                throw new IllegalArgumentException("A cooldown must not be negative");
            }
        }

        @Override
        public void execute(ActionContext context) {
            long cooldownMillis = (long) (parseDouble(context.argument(), "cooldown") * 1000L);
            String key = key(context.npc().uniqueId(), context.player().getUniqueId());
            long now = System.nanoTime() / 1_000_000L;

            Long previous = lastRun.get(key);
            if (previous != null && now - previous < cooldownMillis) {
                context.stopChain();
                return;
            }
            lastRun.put(key, now);

            // Entries are only ever added, so a server that has been up for months with thousands of
            // NPCs would accumulate one per pairing. Sweeping on write keeps that bounded without a
            // scheduled task.
            if (lastRun.size() > MAX_TRACKED) {
                long expiry = now - Duration.ofHours(1).toMillis();
                lastRun.values().removeIf(timestamp -> timestamp < expiry);
            }
        }

        private static final int MAX_TRACKED = 10_000;

        private static String key(UUID npc, UUID player) {
            return npc + ":" + player;
        }
    }

    // -------------------------------------------------------------------------------------------
    // Argument parsing
    // -------------------------------------------------------------------------------------------

    private static String[] split(String argument) {
        String trimmed = argument.strip();
        return trimmed.isEmpty() ? new String[0] : trimmed.split("\\s+");
    }

    private static void requireNotBlank(String argument, String message) {
        if (argument == null || argument.isBlank()) {
            throw new IllegalArgumentException(message);
        }
    }

    private static float parseFloat(String value, String what) {
        return (float) parseDouble(value, what);
    }

    private static double parseDouble(String value, String what) {
        try {
            double parsed = Double.parseDouble(value.strip());
            if (!Double.isFinite(parsed)) {
                throw new IllegalArgumentException("The " + what + " must be a finite number");
            }
            return parsed;
        } catch (NumberFormatException notANumber) {
            throw new IllegalArgumentException(
                    "The " + what + " must be a number, was '" + value + "'", notANumber);
        }
    }

    private static int parseInt(String value, String what) {
        try {
            return Integer.parseInt(value.strip());
        } catch (NumberFormatException notANumber) {
            throw new IllegalArgumentException(
                    "The " + what + " must be a whole number, was '" + value + "'", notANumber);
        }
    }
}
