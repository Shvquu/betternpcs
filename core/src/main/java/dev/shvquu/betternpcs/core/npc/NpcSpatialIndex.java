package dev.shvquu.betternpcs.core.npc;

import dev.shvquu.betternpcs.api.npc.property.NpcPosition;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * A grid index of spawned NPCs, so the tracker can ask "what is near this player" without walking
 * every NPC on the server.
 *
 * <p>The naive tracker compares every player against every NPC on every pass. At a thousand NPCs and
 * a hundred players that is a hundred thousand distance checks several times a second, and it is the
 * reason NPC plugins get a reputation for being heavy. Bucketing by a coarse grid turns it into a
 * scan of the handful of cells around each player.
 *
 * <p>Cells are {@value #CELL_SIZE} blocks square, and deliberately coarse: a finer grid would mean
 * more cells to visit per player and more re-bucketing as NPCs move, while NPCs are overwhelmingly
 * stationary and clustered. Height is not indexed at all, because NPC clusters spread horizontally
 * and a vertical dimension would add bookkeeping for almost no filtering.
 *
 * <p>Thread safe for reads; writes are expected on the main thread.
 *
 * @since 1.0.0
 */
public final class NpcSpatialIndex {

    /** The side length of one grid cell, in blocks. */
    public static final int CELL_SIZE = 64;

    private record Cell(String world, int x, int z) {
    }

    private final Map<Cell, Set<NpcHandle>> cells = new ConcurrentHashMap<>();
    private final Map<NpcHandle, Cell> placement = new ConcurrentHashMap<>();

    /**
     * Adds or moves an NPC to the cell its position falls in.
     *
     * <p>Called on spawn and after every move. Doing nothing when the cell has not changed is what
     * makes it cheap enough to call from a per-tick movement script.
     *
     * @param npc the NPC to place
     * @throws NullPointerException if {@code npc} is {@code null}
     */
    public void place(NpcHandle npc) {
        Objects.requireNonNull(npc, "npc");
        Cell target = cellOf(npc.position());
        Cell current = placement.get(npc);

        if (target.equals(current)) {
            return;
        }
        if (current != null) {
            removeFrom(current, npc);
        }
        cells.computeIfAbsent(target, key -> ConcurrentHashMap.newKeySet()).add(npc);
        placement.put(npc, target);
    }

    /**
     * Removes an NPC from the index.
     *
     * @param npc the NPC to remove
     * @throws NullPointerException if {@code npc} is {@code null}
     */
    public void remove(NpcHandle npc) {
        Objects.requireNonNull(npc, "npc");
        Cell current = placement.remove(npc);
        if (current != null) {
            removeFrom(current, npc);
        }
    }

    private void removeFrom(Cell cell, NpcHandle npc) {
        Set<NpcHandle> occupants = cells.get(cell);
        if (occupants == null) {
            return;
        }
        occupants.remove(npc);
        // An empty cell left behind would accumulate for every place an NPC has ever stood.
        if (occupants.isEmpty()) {
            cells.remove(cell, occupants);
        }
    }

    /**
     * Returns the NPCs whose cell is within {@code radius} of a point.
     *
     * <p>A superset of what is actually in range: the caller still has to check the real distance.
     * That is the trade — a cheap, conservative filter that removes almost everything, followed by an
     * exact check on what is left.
     *
     * @param world  the world name
     * @param x      the x coordinate
     * @param z      the z coordinate
     * @param radius the radius in blocks
     * @return the candidate NPCs
     * @throws NullPointerException if {@code world} is {@code null}
     */
    public Collection<NpcHandle> near(String world, double x, double z, double radius) {
        Objects.requireNonNull(world, "world");

        int minCellX = Math.floorDiv((int) Math.floor(x - radius), CELL_SIZE);
        int maxCellX = Math.floorDiv((int) Math.ceil(x + radius), CELL_SIZE);
        int minCellZ = Math.floorDiv((int) Math.floor(z - radius), CELL_SIZE);
        int maxCellZ = Math.floorDiv((int) Math.ceil(z + radius), CELL_SIZE);

        List<NpcHandle> candidates = new ArrayList<>();
        for (int cellX = minCellX; cellX <= maxCellX; cellX++) {
            for (int cellZ = minCellZ; cellZ <= maxCellZ; cellZ++) {
                Set<NpcHandle> occupants = cells.get(new Cell(world, cellX, cellZ));
                if (occupants != null) {
                    candidates.addAll(occupants);
                }
            }
        }
        return candidates;
    }

    /**
     * Returns every indexed NPC in a world.
     *
     * @param world the world name
     * @return the NPCs in that world
     * @throws NullPointerException if {@code world} is {@code null}
     */
    public Collection<NpcHandle> inWorld(String world) {
        Objects.requireNonNull(world, "world");
        Set<NpcHandle> found = new LinkedHashSet<>();
        cells.forEach((cell, occupants) -> {
            if (cell.world().equals(world)) {
                found.addAll(occupants);
            }
        });
        return found;
    }

    /**
     * Empties the index.
     */
    public void clear() {
        cells.clear();
        placement.clear();
    }

    /**
     * Returns how many NPCs are indexed.
     *
     * @return the count
     */
    public int size() {
        return placement.size();
    }

    /**
     * Returns how many cells are occupied.
     *
     * <p>For diagnostics: a cell count close to the NPC count means the NPCs are spread out and the
     * index is doing little, which is worth knowing when tuning.
     *
     * @return the occupied cell count
     */
    public int cellCount() {
        return cells.size();
    }

    private static Cell cellOf(NpcPosition position) {
        return new Cell(
                position.world(),
                Math.floorDiv((int) Math.floor(position.x()), CELL_SIZE),
                Math.floorDiv((int) Math.floor(position.z()), CELL_SIZE));
    }
}
