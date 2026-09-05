package dev.shvquu.betternpcs.api.event;

import dev.shvquu.betternpcs.api.npc.Npc;
import dev.shvquu.betternpcs.api.npc.NpcSnapshot;
import java.util.Objects;
import org.bukkit.event.Cancellable;
import org.bukkit.event.HandlerList;

/**
 * Fired on the main thread when an NPC's state has been captured for writing, before the write is
 * handed to storage.
 *
 * <p>The snapshot is the exact value that will be persisted. A listener can replace it with
 * {@link #setSnapshot(NpcSnapshot)} — the intended use is adding or rewriting
 * {@link NpcSnapshot#metadata()} entries an extension owns, without having to keep the live NPC in
 * sync as it changes.
 *
 * <p>Cancelling skips the write for this NPC only. The NPC stays marked as having unsaved changes,
 * so the next save cycle tries again.
 *
 * @since 1.0.0
 */
public class NpcSaveEvent extends NpcEvent implements Cancellable {

    private static final HandlerList HANDLERS = new HandlerList();

    private NpcSnapshot snapshot;
    private boolean cancelled;

    /**
     * Creates the event.
     *
     * @param npc      the NPC being saved
     * @param snapshot the state that will be written
     * @throws NullPointerException if either argument is {@code null}
     */
    public NpcSaveEvent(Npc npc, NpcSnapshot snapshot) {
        super(npc);
        this.snapshot = Objects.requireNonNull(snapshot, "snapshot");
    }

    /**
     * Returns the state that will be written.
     *
     * @return the snapshot
     */
    public NpcSnapshot getSnapshot() {
        return snapshot;
    }

    /**
     * Replaces the state that will be written.
     *
     * <p>Changing anything other than metadata means the stored NPC will differ from the live one
     * until the next load, which is almost never what a listener wants. Derive the replacement from
     * {@link NpcSnapshot#toBuilder()} rather than building one from scratch.
     *
     * @param snapshot the state to write instead
     * @throws NullPointerException     if {@code snapshot} is {@code null}
     * @throws IllegalArgumentException if the snapshot is for a different NPC
     */
    public void setSnapshot(NpcSnapshot snapshot) {
        Objects.requireNonNull(snapshot, "snapshot");
        if (!snapshot.uniqueId().equals(getNpc().uniqueId())) {
            throw new IllegalArgumentException(
                    "Snapshot belongs to a different NPC: " + snapshot.uniqueId()
                            + " instead of " + getNpc().uniqueId());
        }
        this.snapshot = snapshot;
    }

    @Override
    public boolean isCancelled() {
        return cancelled;
    }

    @Override
    public void setCancelled(boolean cancel) {
        this.cancelled = cancel;
    }

    @Override
    public HandlerList getHandlers() {
        return HANDLERS;
    }

    /**
     * Returns the handler list, as Bukkit's event system requires.
     *
     * @return the handler list
     */
    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}
