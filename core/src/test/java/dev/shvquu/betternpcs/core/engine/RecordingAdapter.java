package dev.shvquu.betternpcs.core.engine;

import dev.shvquu.betternpcs.api.animation.NpcAnimation;
import dev.shvquu.betternpcs.core.render.AdapterContext;
import dev.shvquu.betternpcs.core.render.NpcView;
import dev.shvquu.betternpcs.core.render.TextView;
import dev.shvquu.betternpcs.core.version.VersionAdapter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.bukkit.entity.Player;

/**
 * A {@link VersionAdapter} that records what it was asked to send instead of sending it.
 *
 * <p>What makes the engine testable at all. Every question worth asking about the engine — did the
 * NPC get spawned for the right player, was a teleport sent or a respawn, did a rotation packet go
 * out when nothing had rotated — is a question about the sequence of calls that reached the adapter,
 * and this records exactly that.
 */
public final class RecordingAdapter implements VersionAdapter {

    /**
     * One recorded call.
     *
     * @param kind     which method was called
     * @param entityId the primary entity id involved, or -1
     * @param viewers  the players it was sent to
     * @param detail   anything else worth asserting on
     */
    public record Call(String kind, int entityId, List<String> viewers, String detail) {
    }

    private final List<Call> calls = new ArrayList<>();
    private final AtomicInteger nextEntityId = new AtomicInteger(1000);

    private boolean textDisplays = true;
    private AdapterContext context;
    private boolean enabled;

    @Override
    public String describe() {
        return "Recording test adapter";
    }

    @Override
    public void enable(AdapterContext adapterContext) {
        this.context = adapterContext;
        this.enabled = true;
    }

    @Override
    public void disable() {
        this.enabled = false;
    }

    @Override
    public int allocateEntityId() {
        return nextEntityId.incrementAndGet();
    }

    @Override
    public boolean supportsTextDisplays() {
        return textDisplays;
    }

    @Override
    public void trackPlayer(Player player) {
        record("trackPlayer", -1, List.of(player), player.getName());
    }

    @Override
    public void untrackPlayer(Player player) {
        record("untrackPlayer", -1, List.of(player), player.getName());
    }

    @Override
    public void spawnNpc(NpcView npc, Collection<? extends Player> viewers) {
        record("spawnNpc", npc.entityId(), viewers, npc.profileName());
    }

    @Override
    public void hideFromTabList(NpcView npc, Collection<? extends Player> viewers) {
        record("hideFromTabList", npc.entityId(), viewers, npc.profileName());
    }

    @Override
    public void removeEntities(int[] entityIds, Collection<? extends Player> viewers) {
        record("removeEntities", entityIds.length == 0 ? -1 : entityIds[0], viewers,
                Arrays.toString(entityIds));
    }

    @Override
    public void updateMetadata(NpcView npc, Collection<? extends Player> viewers) {
        record("updateMetadata", npc.entityId(), viewers, npc.appearance().toString());
    }

    @Override
    public void updateEquipment(NpcView npc, Collection<? extends Player> viewers) {
        record("updateEquipment", npc.entityId(), viewers, npc.equipment().toString());
    }

    @Override
    public void teleport(NpcView npc, Collection<? extends Player> viewers) {
        record("teleport", npc.entityId(), viewers, npc.position().toString());
    }

    @Override
    public void rotate(
            int entityId, float yaw, float pitch, boolean headOnly, Collection<? extends Player> viewers) {
        record("rotate", entityId, viewers, "yaw=" + Math.round(yaw) + " pitch=" + Math.round(pitch));
    }

    @Override
    public void playAnimation(
            NpcView npc, NpcAnimation animation, Collection<? extends Player> viewers) {
        record("playAnimation", npc.entityId(), viewers, animation.name());
    }

    @Override
    public void spawnText(TextView text, Player viewer) {
        record("spawnText", text.entityId(), List.of(viewer), plain(text));
    }

    @Override
    public void updateText(TextView text, Player viewer) {
        record("updateText", text.entityId(), List.of(viewer), plain(text));
    }

    private static String plain(TextView text) {
        return net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText()
                .serialize(text.text());
    }

    private void record(String kind, int entityId, Collection<? extends Player> viewers, String detail) {
        calls.add(new Call(kind, entityId, viewers.stream().map(Player::getName).sorted().toList(), detail));
    }

    // -----------------------------------------------------------------------------------------
    // Assertions
    // -----------------------------------------------------------------------------------------

    /**
     * Returns every recorded call, in order.
     *
     * @return the calls
     */
    public List<Call> calls() {
        return List.copyOf(calls);
    }

    /**
     * Returns the recorded calls of one kind.
     *
     * @param kind the method name
     * @return the matching calls, in order
     */
    public List<Call> calls(String kind) {
        return calls.stream().filter(call -> call.kind().equals(kind)).toList();
    }

    /**
     * Returns the names of the methods called, in order, with consecutive repeats kept.
     *
     * @return the call kinds
     */
    public List<String> kinds() {
        return calls.stream().map(Call::kind).toList();
    }

    /**
     * Returns how many times a method was called.
     *
     * @param kind the method name
     * @return the count
     */
    public int count(String kind) {
        return calls(kind).size();
    }

    /**
     * Forgets every recorded call.
     */
    public void clear() {
        calls.clear();
    }

    /**
     * Returns the context the engine passed to {@link #enable(AdapterContext)}.
     *
     * @return the context, or {@code null} if the adapter was never enabled
     */
    public AdapterContext context() {
        return context;
    }

    /**
     * Returns whether the adapter is enabled.
     *
     * @return {@code true} between enable and disable
     */
    public boolean isEnabled() {
        return enabled;
    }

    /**
     * Sets what {@link #supportsTextDisplays()} reports.
     *
     * @param supported the value to report
     */
    public void textDisplays(boolean supported) {
        this.textDisplays = supported;
    }
}
