package dev.shvquu.betternpcs.core.backup;

import dev.shvquu.betternpcs.api.npc.Npc;
import dev.shvquu.betternpcs.api.npc.NpcSnapshot;
import dev.shvquu.betternpcs.core.npc.DefaultNpcManager;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Works out what restoring a backup would do, and then does it.
 *
 * <h2>Restore never deletes anything</h2>
 *
 * <p>That is the rule the whole design rests on. An NPC in the file that does not exist is created;
 * one that exists under the same id is overwritten only when asked; and an NPC that is on the server
 * but <em>not</em> in the file is left completely alone.
 *
 * <p>The reasoning is that a restore is usually run in a hurry after something went wrong, by
 * somebody who is not certain what the file contains. A command that silently deleted whatever the
 * backup happened not to mention would turn a partial loss into a total one.
 *
 * <h2>Planning is separate from applying</h2>
 *
 * <p>{@link #plan(BackupFile)} changes nothing and reports exactly what would happen. That is what
 * {@code /npc restore} shows without {@code --confirm}, so nobody finds out what a restore does by
 * running one.
 *
 * @since 1.0.0
 */
public final class RestoreService {

    /**
     * Why an NPC in the backup cannot simply be created.
     *
     * @since 1.0.0
     */
    public enum Conflict {

        /**
         * A <em>different</em> NPC already uses that name.
         *
         * <p>Always skipped, even with {@code --replace}: resolving it would mean deleting the NPC
         * that holds the name, and restore does not delete. Rename one of them and restore again.
         */
        NAME_TAKEN,

        /**
         * The entry could not be turned into an NPC at all — an unusable name, or a type this
         * Minecraft version does not have.
         */
        UNUSABLE
    }

    /**
     * One entry of the backup that will not be created as-is.
     *
     * <p>Carries the reason as an enum rather than a sentence, because the sentence belongs in the
     * language files where a server owner can read it in their own language.
     *
     * @param snapshot the entry from the file
     * @param conflict why
     * @since 1.0.0
     */
    public record Blocked(NpcSnapshot snapshot, Conflict conflict) {

        /**
         * Creates the record.
         *
         * @param snapshot the entry
         * @param conflict why it is blocked
         * @throws NullPointerException if either argument is {@code null}
         */
        public Blocked {
            Objects.requireNonNull(snapshot, "snapshot");
            Objects.requireNonNull(conflict, "conflict");
        }
    }

    /**
     * What restoring a backup would do.
     *
     * @param created     entries that would be created
     * @param overwritten entries that would replace an existing NPC, given {@code --replace}
     * @param blocked     entries that will be skipped whatever the options
     * @since 1.0.0
     */
    public record Plan(
            List<NpcSnapshot> created, List<NpcSnapshot> overwritten, List<Blocked> blocked) {

        /**
         * Creates the plan.
         *
         * @param created     entries to create
         * @param overwritten entries to overwrite
         * @param blocked     entries to skip
         * @throws NullPointerException if any argument is {@code null}
         */
        public Plan {
            created = List.copyOf(created);
            overwritten = List.copyOf(overwritten);
            blocked = List.copyOf(blocked);
        }

        /**
         * Returns how many entries the backup held.
         *
         * @return the total
         */
        public int total() {
            return created.size() + overwritten.size() + blocked.size();
        }

        /**
         * Returns whether applying this plan would change anything.
         *
         * @param replace whether overwriting was requested
         * @return {@code true} if at least one NPC would be created or overwritten
         */
        public boolean changesAnything(boolean replace) {
            return !created.isEmpty() || (replace && !overwritten.isEmpty());
        }
    }

    /**
     * What applying a plan actually did.
     *
     * @param created     how many NPCs were created
     * @param overwritten how many were overwritten
     * @param failed      how many could not be applied after all
     * @since 1.0.0
     */
    public record Result(int created, int overwritten, int failed) {
    }

    private final DefaultNpcManager manager;
    private final Logger logger;

    /**
     * Creates the service.
     *
     * @param manager where NPCs are created and looked up
     * @param logger  where per-entry failures are reported
     * @throws NullPointerException if either argument is {@code null}
     */
    public RestoreService(DefaultNpcManager manager, Logger logger) {
        this.manager = Objects.requireNonNull(manager, "manager");
        this.logger = Objects.requireNonNull(logger, "logger");
    }

    /**
     * Works out what restoring a backup would do, changing nothing.
     *
     * @param backup the backup to plan
     * @return the plan
     * @throws NullPointerException if {@code backup} is {@code null}
     */
    public Plan plan(BackupFile backup) {
        Objects.requireNonNull(backup, "backup");

        List<NpcSnapshot> created = new ArrayList<>();
        List<NpcSnapshot> overwritten = new ArrayList<>();
        List<Blocked> blocked = new ArrayList<>();

        for (NpcSnapshot snapshot : backup.npcs()) {
            Optional<Npc> sameId = manager.byUniqueId(snapshot.uniqueId());
            if (sameId.isPresent()) {
                overwritten.add(snapshot);
                continue;
            }

            if (!DefaultNpcManager.isValidName(snapshot.name())) {
                // Checked before the name clash, so a hand-edited file reports the real problem
                // rather than a clash the name could never have caused.
                blocked.add(new Blocked(snapshot, Conflict.UNUSABLE));
                continue;
            }

            if (manager.byName(snapshot.name()).isPresent()) {
                // A different NPC holds the name. Resolving this would mean deleting it, and restore
                // does not delete.
                blocked.add(new Blocked(snapshot, Conflict.NAME_TAKEN));
                continue;
            }

            created.add(snapshot);
        }

        return new Plan(created, overwritten, blocked);
    }

    /**
     * Applies a plan.
     *
     * <p>Must be called on the main server thread. A single entry that fails is logged and counted;
     * it does not abort the rest, because a restore that stops half-way is worse than one that
     * reports what it could not do.
     *
     * @param plan    the plan, from {@link #plan(BackupFile)}
     * @param replace whether to overwrite NPCs that already exist under the same id
     * @return what was done
     * @throws NullPointerException if {@code plan} is {@code null}
     */
    public Result apply(Plan plan, boolean replace) {
        Objects.requireNonNull(plan, "plan");

        int created = 0;
        int overwritten = 0;
        int failed = 0;

        for (NpcSnapshot snapshot : plan.created()) {
            try {
                manager.create(snapshot);
                created++;
            } catch (RuntimeException failure) {
                failed++;
                logger.log(Level.WARNING, failure,
                        () -> "Could not restore the NPC '" + snapshot.name() + "'.");
            }
        }

        if (replace) {
            for (NpcSnapshot snapshot : plan.overwritten()) {
                try {
                    Npc existing = manager.byUniqueId(snapshot.uniqueId())
                            .orElseThrow(() -> new IllegalStateException(
                                    "The NPC disappeared between planning and applying"));
                    // apply() keeps the unique id and rewrites everything else, so external
                    // references to this NPC keep resolving.
                    existing.apply(snapshot);
                    overwritten++;
                } catch (RuntimeException failure) {
                    failed++;
                    logger.log(Level.WARNING, failure,
                            () -> "Could not overwrite the NPC '" + snapshot.name() + "'.");
                }
            }
        }

        return new Result(created, overwritten, failed);
    }
}
