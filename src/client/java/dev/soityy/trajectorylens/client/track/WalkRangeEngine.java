package dev.soityy.trajectorylens.client.track;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import net.minecraft.core.BlockPos;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * Reachable-region ("how far could this walking mob get in T ticks") engine.
 *
 * Pure geometric upper bound, deliberately AI-agnostic: any cell the mob could
 * physically reach while walking/jumping is included. Real AI pauses and turns
 * only shrink the used region, so this is a true "maximum walking range".
 *
 * v1 rules: walk on solid ground; auto-jump 1 block up (11 tick penalty);
 * free falling allowed; water slows to 45% speed; lava/void are walls;
 * closed doors, fences and walls are walls; headroom = entity height.
 */
public final class WalkRangeEngine {

    public static final int MAX_CELL_RADIUS = 40;
    private static final int JUMP_PENALTY_TICKS = 11;
    private static final double WATER_SLOW = 0.45;

    private WalkRangeEngine() {
    }

    public static final class Region {
        public final List<Vec3> boundarySegments = new ArrayList<>();
        public final List<Long> cellKeys = new ArrayList<>();
        public int cellCount;
        public double usedTicks; // cheapest explored cell cost (debug/info)
    }

    /** Decode a packed cell key back into (x, y, z) block coordinates. */
    public static int cellX(long k) {
        return (int) (k >> 38) - 30000;
    }

    public static int cellY(long k) {
        return (int) ((k >> 20) & 0x3FFFF) - 30000;
    }

    public static int cellZ(long k) {
        return (int) ((k >> 2) & 0x3FFFF) - 30000;
    }

    private record Cell(int x, int y, int z, double cost) {
    }

    /**
     * @param speedBlocksPerTick attribute walking speed (blocks per tick)
     * @param heightCeil        ceil(bbHeight) in blocks
     * @param startX/startY/startZ feet position
     */
    public static Region compute(Level level, double speedBlocksPerTick, int heightCeil,
                                 double startX, double startY, double startZ, int budgetTicks) {
        Region out = new Region();
        if (speedBlocksPerTick <= 0.001 || heightCeil <= 0 || level == null) {
            return out;
        }
        int fx = (int) Math.floor(startX);
        int fy = (int) Math.floor(startY);
        int fz = (int) Math.floor(startZ);

        Map<Long, Double> best = new HashMap<>();
        ArrayDeque<Cell> queue = new ArrayDeque<>();
        Cell start = new Cell(fx, fy, fz, 0.0);
        best.put(key(fx, fy, fz), 0.0);
        queue.add(start);
        Set<Long> closed = new HashSet<>();
        while (!queue.isEmpty()) {
            Cell cur = queue.poll();
            long k = key(cur.x, cur.y, cur.z);
            if (!closed.add(k)) {
                continue;
            }
            if (cur.cost > best.getOrDefault(k, Double.MAX_VALUE) + 1.0E-9) {
                continue;
            }
            out.cellKeys.add(k);
            out.cellCount++;
            if (cur.cost > out.usedTicks) {
                out.usedTicks = cur.cost;
            }

            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    if (dx == 0 && dz == 0) {
                        continue;
                    }
                    int nx = cur.x + dx;
                    int nz = cur.z + dz;
                    double horiz = Math.sqrt(dx * dx + dz * dz) / speedBlocksPerTick;
                    int nDist = Math.max(Math.abs(nx - fx), Math.abs(nz - fz));
                    if (nDist > MAX_CELL_RADIUS) {
                        continue;
                    }
                    boolean waterHere = isWater(level, cur.x, cur.y, cur.z);
                    double waterCost = (waterHere || isWater(level, nx, cur.y, nz)) ? horiz / WATER_SLOW - horiz : 0.0;

                    // 1) same level
                    tryAdd(level, out, best, queue, nx, cur.y, nz, cur.cost + horiz + waterCost, heightCeil, fx, fz, budgetTicks);
                    // 2) one block up (auto jump on top of a full block)
                    if (isSolid(level, nx, cur.y, nz) && isStandableAt(level, nx, cur.y + 1, nz, heightCeil, true)) {
                        tryAdd(level, out, best, queue, nx, cur.y + 1, nz, cur.cost + horiz + JUMP_PENALTY_TICKS, heightCeil, fx, fz, budgetTicks);
                    }
                    // 3) fall down to first solid floor
                    if (isAirish(level, nx, cur.y, nz)) {
                        int gy = cur.y - 1;
                        int limit = Math.max(level.getMinY() + 1, cur.y - 24);
                        while (gy > limit && isAirish(level, nx, gy, nz)) {
                            gy--;
                        }
                        if (gy > limit && isStandableAt(level, nx, gy + 1, nz, heightCeil, true)) {
                            // falling is "free" in time for the upper bound; charge a small tick for realism
                            tryAdd(level, out, best, queue, nx, gy + 1, nz, cur.cost + horiz + 3.0, heightCeil, fx, fz, budgetTicks);
                        }
                    }
                }
            }
        }

        // boundary: reachable cell whose 4-neighbor at the same level is not reachable
        Set<Long> cells = new HashSet<>(best.keySet());
        for (long k : cells) {
            int x = (int) (k >> 38) - 30000;
            int y = (int) ((k >> 20) & 0x3FFFF) - 30000;
            int z = (int) ((k >> 2) & 0x3FFFF) - 30000;
            emitEdge(out.boundarySegments, cells, x, y, z, 1, 0);
            emitEdge(out.boundarySegments, cells, x, y, z, -1, 0);
            emitEdge(out.boundarySegments, cells, x, y, z, 0, 1);
            emitEdge(out.boundarySegments, cells, x, y, z, 0, -1);
        }
        return out;
    }

    private static void emitEdge(List<Vec3> segs, Set<Long> cells, int x, int y, int z, int dx, int dz) {
        long nk = key(x + dx, y, z + dz);
        if (cells.contains(nk)) {
            return;
        }
        // IMPORTANT: block column n occupies [n, n+1); its edges are at INTEGER
        // coordinates. All range geometry must sit on integers to align with blocks.
        double top = y + 0.05;
        if (dx == 1) {
            segs.add(new Vec3(x + 1.0, top, z));
            segs.add(new Vec3(x + 1.0, top, z + 1.0));
        } else if (dx == -1) {
            segs.add(new Vec3(x, top, z));
            segs.add(new Vec3(x, top, z + 1.0));
        } else if (dz == 1) {
            segs.add(new Vec3(x, top, z + 1.0));
            segs.add(new Vec3(x + 1.0, top, z + 1.0));
        } else {
            segs.add(new Vec3(x, top, z));
            segs.add(new Vec3(x + 1.0, top, z));
        }
    }

    private static void tryAdd(Level level, Region out, Map<Long, Double> best, ArrayDeque<Cell> queue,
                               int x, int y, int z, double cost, int heightCeil, int fx, int fz, int budgetTicks) {
        if (cost > budgetTicks) {
            return;
        }
        if (!isStandableAt(level, x, y, z, heightCeil, false)) {
            return;
        }
        long k = key(x, y, z);
        Double prev = best.get(k);
        if (prev != null && prev <= cost) {
            return;
        }
        best.put(k, cost);
        queue.add(new Cell(x, y, z, cost));
    }

    /** A mob can stand here: feet cell clear (or water), proper headroom, valid floor. */
    private static boolean isStandableAt(Level level, int x, int y, int z, int heightCeil, boolean floorPreValidated) {
        if (!level.isLoaded(new BlockPos(x, y, z))) {
            return false;
        }
        if (!isAirish(level, x, y, z)) {
            return false;
        }
        // headroom above feet
        for (int h = 1; h < heightCeil; h++) {
            if (!isAirish(level, x, y + h, z)) {
                return false;
            }
        }
        if (isWater(level, x, y, z)) {
            return true; // swimming / wading
        }
        if (floorPreValidated) {
            return true; // caller already verified the support below
        }
        BlockPos below = new BlockPos(x, y - 1, z);
        BlockState belowState = level.getBlockState(below);
        if (!belowState.isAir() && belowState.getFluidState().is(FluidTags.LAVA)) {
            return false;
        }
        if (belowState.is(BlockTags.FENCES) || belowState.is(BlockTags.WALLS)
            || belowState.is(BlockTags.DOORS) || belowState.is(BlockTags.FENCE_GATES)) {
            return false; // cannot stand on top of these for pathing purposes
        }
        return belowState.isCollisionShapeFullBlock(level, below);
    }

    private static boolean isAirish(Level level, int x, int y, int z) {
        if (!level.isLoaded(new BlockPos(x, y, z))) {
            return false;
        }
        BlockState s = level.getBlockState(new BlockPos(x, y, z));
        if (s.isAir()) {
            return true;
        }
        if (s.getFluidState().is(FluidTags.WATER)) {
            return true; // swim/wade
        }
        if (s.getFluidState().is(FluidTags.LAVA)) {
            return false;
        }
        if (s.is(BlockTags.FENCES) || s.is(BlockTags.WALLS) || s.is(BlockTags.DOORS) || s.is(BlockTags.FENCE_GATES)) {
            return false;
        }
        return !s.isCollisionShapeFullBlock(level, new BlockPos(x, y, z));
    }

    private static boolean isSolid(Level level, int x, int y, int z) {
        if (!level.isLoaded(new BlockPos(x, y, z))) {
            return false;
        }
        BlockState s = level.getBlockState(new BlockPos(x, y, z));
        if (s.is(BlockTags.FENCES) || s.is(BlockTags.WALLS) || s.is(BlockTags.DOORS) || s.is(BlockTags.FENCE_GATES)) {
            return true; // treat as climb walls / blockers
        }
        return s.isCollisionShapeFullBlock(level, new BlockPos(x, y, z));
    }

    private static boolean isWater(Level level, int x, int y, int z) {
        return level.isLoaded(new BlockPos(x, y, z))
            && level.getBlockState(new BlockPos(x, y, z)).getFluidState().is(FluidTags.WATER);
    }

    private static long key(int x, int y, int z) {
        return ((long) (x + 30000) << 38) | ((long) (y + 30000) << 20) | ((long) (z + 30000) << 2);
    }

    /** Public alias for packed cell key (same encoding as stored in Region.cellKeys). */
    public static long keyOf(int x, int y, int z) {
        return key(x, y, z);
    }
}
