package dev.soityy.trajectorylens.client.track;

import dev.soityy.trajectorylens.client.Lang;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.item.PrimedTnt;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Replays one projectile's flight with the same per-tick physics the game uses.
 * Arrows/throwables/fireballs use ray clipping; TNT uses real move() collision
 * (including its bounce) via a detached shadow entity.
 */
public final class ProjectileSim {

    public static final int HORIZON = 200; // ticks

    public record Hit(Vec3 pos, String label, boolean block) {
    }

    public static final class Result {
        public final List<Vec3> points = new ArrayList<>();
        public ProjectileKind kind;
        public Hit hit;
        public int ticks;
        public int fuseTicks = -1;      // remaining fuse at the END of the simulated path (0 = detonates there)
        public int fuseRemaining = -1;  // fuse right now (for the on-screen countdown)
        public boolean exploded;
        public Vec3 explosionPos;
    }

    private final Level level;
    private final ItemEntity clipCtx;
    private PrimedTnt tntShadow;

    public ProjectileSim(Level level) {
        this.level = level;
        this.clipCtx = new ItemEntity(level, 0, 0, 0, ItemStack.EMPTY);
    }

    public Result simulate(ProjectileKind kind, Vec3 start, Vec3 velocity, int fuse,
                           int ownerId, int selfId, int horizon) {
        Result r = new Result();
        r.kind = kind;
        r.fuseRemaining = fuse > 0 ? fuse : (kind == ProjectileKind.TNT ? 80 : -1);
        if (kind.moveCollision) {
            simulateTnt(r, start, velocity, fuse, horizon, selfId);
        } else {
            simulateRay(r, start, velocity, ownerId, selfId, horizon);
        }
        return r;
    }

    // ---------- arrows / throwables / fireballs ----------

    private void simulateRay(Result r, Vec3 pos, Vec3 v, int ownerId, int selfId, int horizon) {
        r.points.add(pos);
        boolean inWater = isWater(pos);
        boolean arrowLike = r.kind == ProjectileKind.ARROW || r.kind == ProjectileKind.TRIDENT;
        for (int t = 0; t < horizon; t++) {
            if (!this.level.isLoaded(net.minecraft.core.BlockPos.containing(pos))) {
                break;
            }
            // per-tick physics, mirroring 26.2 exactly:
            //  arrow      : water inertia before the move, air inertia + gravity after it
            //  throwable  : gravity, then inertia (0.99 air / 0.8 water), then the move
            //  fireball   : acceleration along motion, then 0.95 inertia, then the move
            if (arrowLike) {
                if (inWater) {
                    v = v.scale(r.kind.waterDrag);
                }
            } else if (r.kind.acceleration > 0) {
                if (v.lengthSqr() > 1.0E-8) {
                    v = v.add(v.normalize().scale(r.kind.acceleration));
                }
                v = v.scale(inWater ? r.kind.waterDrag : r.kind.airDrag);
            } else {
                v = v.add(0, -r.kind.gravity, 0);
                v = v.scale(inWater ? r.kind.waterDrag : r.kind.airDrag);
            }
            Vec3 to = pos.add(v);
            BlockHitResult blockHit = this.level.clip(new ClipContext(pos, to,
                ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, this.clipCtx));
            Hit entHit = findEntityHit(pos, to, ownerId, selfId);
            Hit chosen = null;
            Vec3 end = to;
            if (blockHit.getType() != HitResult.Type.MISS) {
                chosen = new Hit(blockHit.getLocation(), Lang.tr("方块"), true);
                end = blockHit.getLocation();
            }
            if (entHit != null && (chosen == null || pos.distanceToSqr(entHit.pos()) < pos.distanceToSqr(chosen.pos()))) {
                chosen = entHit;
                end = entHit.pos();
            }
            r.points.add(end);
            r.ticks = t + 1;
            if (chosen != null) {
                r.hit = chosen;
                if (r.kind.explosionPower > 0) {
                    r.exploded = true;
                    r.explosionPos = end;
                }
                break;
            }
            pos = end;
            if (arrowLike) {
                if (!inWater) {
                    v = v.scale(r.kind.airDrag);
                }
                v = v.add(0, -r.kind.gravity, 0);
            }
            inWater = isWater(pos);
            if (v.lengthSqr() < 1.0E-6 && !inWater) {
                break;
            }
        }
    }

    // ---------- TNT ----------

    private void simulateTnt(Result r, Vec3 pos, Vec3 v, int fuse, int horizon, int selfId) {
        if (this.tntShadow == null) {
            this.tntShadow = new PrimedTnt(this.level, 0, 0, 0, null);
        }
        PrimedTnt tnt = this.tntShadow;
        tnt.setPos(pos.x, pos.y, pos.z);
        tnt.setDeltaMovement(v);
        r.points.add(pos);
        int remaining = fuse > 0 ? fuse : 80;
        int limit = Math.min(horizon, remaining + 1);
        for (int t = 0; t < limit; t++) {
            tnt.setDeltaMovement(tnt.getDeltaMovement().add(0, -r.kind.gravity, 0));
            tnt.move(MoverType.SELF, tnt.getDeltaMovement());
            tnt.setDeltaMovement(tnt.getDeltaMovement().scale(r.kind.airDrag));
            if (tnt.onGround()) {
                tnt.setDeltaMovement(tnt.getDeltaMovement().multiply(0.7, -0.5, 0.7));
            }
            r.points.add(tnt.position());
            r.ticks = t + 1;
            if (t + 1 >= remaining) {
                r.fuseTicks = 0;
                r.exploded = true;
                r.explosionPos = tnt.position();
                break;
            }
            r.fuseTicks = remaining - (t + 1);
        }
    }

    // ---------- helpers ----------

    private Hit findEntityHit(Vec3 from, Vec3 to, int ownerId, int selfId) {
        AABB sweep = new AABB(from, to).inflate(1.0);
        Entity best = null;
        Vec3 bestPos = null;
        double bestDist = Double.MAX_VALUE;
        for (Entity e : this.level.getEntities(this.clipCtx, sweep)) {
            if (e == null || e.getId() == selfId || e.getId() == ownerId) {
                continue;
            }
            if (!(e instanceof LivingEntity) || !e.isAlive() || e.isSpectator()) {
                continue;
            }
            Optional<Vec3> hit = e.getBoundingBox().inflate(0.3).clip(from, to);
            if (hit.isPresent()) {
                double d = from.distanceToSqr(hit.get());
                if (d < bestDist) {
                    bestDist = d;
                    best = e;
                    bestPos = hit.get();
                }
            }
        }
        return best == null ? null : new Hit(bestPos, best.getName().getString(), false);
    }

    private boolean isWater(Vec3 pos) {
        return this.level.isLoaded(net.minecraft.core.BlockPos.containing(pos))
            && this.level.getFluidState(net.minecraft.core.BlockPos.containing(pos))
                .is(net.minecraft.tags.FluidTags.WATER);
    }
}
