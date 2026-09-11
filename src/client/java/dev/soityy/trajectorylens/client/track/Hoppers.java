package dev.soityy.trajectorylens.client.track;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.vehicle.minecart.MinecartHopper;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.HopperBlock;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Shared vanilla hopper geometry. The suck volume is exactly what
 * {@code HopperBlockEntity.getItemsAtAndAbove} uses (SUCK_AABB = column(16, 11, 32)
 * moved into world space -&gt; x..x+1, y+0.1875..y+1.5, z..z+1), so anything built on
 * this helper agrees with the real game about which items a hopper can take.
 */
public final class Hoppers {

    /** A candidate hopper: a block hopper ({@code pos}) or a hopper minecart ({@code cart}). */
    public record Hit(BlockPos pos, MinecartHopper cart, boolean locked) {

        /** Stable map key for this hopper. */
        public long key() {
            return this.pos != null ? this.pos.asLong() : (0x4000_0000_0000_0000L | (this.cart != null ? this.cart.getId() : 0));
        }

        public Vec3 center() {
            if (this.pos != null) {
                return new Vec3(this.pos.getX() + 0.5, this.pos.getY() + 0.5, this.pos.getZ() + 0.5);
            }
            return this.cart != null ? this.cart.position().add(0, 0.35, 0) : Vec3.ZERO;
        }

        public AABB box() {
            if (this.pos != null) {
                return new AABB(this.pos);
            }
            return this.cart != null ? this.cart.getBoundingBox() : AABB.ofSize(Vec3.ZERO, 1, 1, 1);
        }
    }

    private Hoppers() {
    }

    public static AABB suckBox(BlockPos p) {
        return new AABB(p.getX(), p.getY() + 0.1875, p.getZ(), p.getX() + 1.0, p.getY() + 1.5, p.getZ() + 1.0);
    }

    /**
     * Nearest hopper able to swallow an item last seen at {@code at}: scans a
     * (2r+1) x (down+up+1) x (2r+1) block box around it plus hopper minecarts, and only
     * accepts a hit within {@code tol} blocks of the suck volume (or the cart body), so
     * an item merely lying beside a hopper is never blamed on it.
     *
     * @param includeLocked keep redstone-locked hoppers (they can never suck, but they are
     *                      exactly what you want to *show* when diagnosing a jam)
     */
    public static Hit find(Level level, Vec3 at, int r, int down, int up, double tol, boolean includeLocked) {
        return find(level, at, r, down, up, tol, includeLocked, true);
    }

    /**
     * @param checkCarts also look for hopper minecarts. Pass false from per-tick scans
     *                   (the entity lookup is the expensive half) and handle carts in bulk.
     */
    public static Hit find(Level level, Vec3 at, int r, int down, int up, double tol, boolean includeLocked, boolean checkCarts) {
        if (level == null || at == null) {
            return null;
        }
        BlockPos base = BlockPos.containing(at);
        Hit best = null;
        double bestD = Double.MAX_VALUE;
        for (int dy = -down; dy <= up; dy++) {
            for (int dx = -r; dx <= r; dx++) {
                for (int dz = -r; dz <= r; dz++) {
                    BlockPos p = base.offset(dx, dy, dz);
                    var st = level.getBlockState(p);
                    if (!(st.getBlock() instanceof HopperBlock)) {
                        continue;
                    }
                    boolean locked = !st.getValue(HopperBlock.ENABLED);
                    if (locked && !includeLocked) {
                        continue;
                    }
                    double d = suckBox(p).distanceToSqr(at);
                    if (d < bestD) {
                        bestD = d;
                        best = new Hit(p, null, locked);
                    }
                }
            }
        }
        for (MinecartHopper cart : checkCarts
            ? level.getEntitiesOfClass(MinecartHopper.class, new AABB(at, at).inflate(r + 1.5))
            : java.util.List.<MinecartHopper>of()) {
            if (!cart.isAlive() || cart.isRemoved()) {
                continue;
            }
            double d = cart.getBoundingBox().distanceToSqr(at);
            if (d < bestD) {
                bestD = d;
                best = new Hit(null, cart, false);
            }
        }
        return bestD <= tol * tol ? best : null;
    }
}
