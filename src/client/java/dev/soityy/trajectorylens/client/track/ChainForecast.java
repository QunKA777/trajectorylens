package dev.soityy.trajectorylens.client.track;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import net.minecraft.core.BlockPos;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.item.PrimedTnt;
import net.minecraft.world.entity.vehicle.minecart.MinecartTNT;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.FallingBlock;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;

/**
 * Causal chain forecast for an explosion: what it re-triggers (TNT minecarts,
 * primed TNT flying off), who gets launched where (and whether that means lava,
 * cactus, a long drop or the void), and which falling blocks will come down on
 * somebody. Knockback magnitudes are approximated from the vanilla model
 * (direction * (1 - distance/radius) * factor), so treat the numbers as estimates.
 */
public final class ChainForecast {

    /** How many blast layers to follow (1-4). Configurable from the panel / commands. */
    public static int maxDepth = 3;
    /** Flight window for launched entities, in ticks (3-15 s). */
    public static int horizonTicks = 120;
    public static final int[] DEPTH_CHOICES = {1, 2, 3, 4};
    public static final int[] HORIZON_CHOICES = {60, 120, 180, 240};

    private static final int MAX_PUSH = 8;

    public static final class Step {
        public int delayTicks;
        public Vec3 pos;
        public String label;
        public float power;
        public int order;
    }

    public static final class PushArc {
        public Vec3 from;
        public final List<Vec3> points = new ArrayList<>();
        public Vec3 landing;
        public String note;
        public int color;
        public boolean player;
    }

    public static final class Plan {
        public final List<Step> steps = new ArrayList<>();
        public final List<PushArc> pushes = new ArrayList<>();
        public final List<String> notes = new ArrayList<>();
    }

    private ChainForecast() {
    }

    public static Plan compute(Level level, Vec3 center, float power, int fuseTicks, Entity source) {
        Plan plan = new Plan();
        if (level == null) {
            return plan;
        }
        Set<Integer> chainedCarts = new HashSet<>();
        Step first = new Step();
        first.delayTicks = Math.max(0, fuseTicks);
        first.pos = center;
        first.power = power;
        first.label = source instanceof PrimedTnt ? "TNT" : "爆炸";
        first.order = 1;
        plan.steps.add(first);
        // every phase is isolated: a broken entity / unloaded chunk must degrade the plan, not crash
        try {
            expand(level, plan, first, chainedCarts, 1);
        } catch (Exception ignored) {
        }
        try {
            pushes(level, plan, center, power, source);
        } catch (Exception ignored) {
        }
        try {
            falling(level, plan, center, power);
        } catch (Exception ignored) {
        }
        return plan;
    }

    private static void expand(Level level, Plan plan, Step parent, Set<Integer> carts, int depth) {
        if (depth >= maxDepth) {
            return;
        }
        double radius = parent.power * 2.0 + 1.0;
        for (MinecartTNT cart : level.getEntitiesOfClass(MinecartTNT.class, new AABB(parent.pos, parent.pos).inflate(radius))) {
            if (!cart.isAlive() || cart.isRemoved() || !carts.add(cart.getId())) {
                continue;
            }
            Step s = new Step();
            s.delayTicks = parent.delayTicks + 20; // destroyed carts fuse 0-38 ticks; 20 is the average
            s.pos = cart.position();
            s.power = 4.0F;
            s.label = "TNT矿车连锁";
            s.order = plan.steps.size() + 1;
            plan.steps.add(s);
            expand(level, plan, s, carts, depth + 1);
        }
        for (PrimedTnt tnt : level.getEntitiesOfClass(PrimedTnt.class, new AABB(parent.pos, parent.pos).inflate(radius))) {
            if (!tnt.isAlive() || tnt.isRemoved()) {
                continue;
            }
            PushArc arc = simulateArc(level, tnt, tnt.position(), pushVelocity(tnt, parent.pos, parent.power));
            Step s = new Step();
            s.delayTicks = Math.max(0, tnt.getFuse());
            s.pos = arc.landing != null ? arc.landing : tnt.position();
            s.power = 4.0F;
            s.label = "被炸飞的 TNT";
            s.order = plan.steps.size() + 1;
            plan.steps.add(s);
        }
        int tntBlocks = 0;
        BlockPos origin = BlockPos.containing(parent.pos);
        int r = (int) Math.ceil(parent.power * 2.0);
        for (BlockPos bp : BlockPos.betweenClosed(origin.offset(-r, -r, -r), origin.offset(r, r, r))) {
            if (level.getBlockState(bp).is(Blocks.TNT)) {
                tntBlocks++;
            }
        }
        if (tntBlocks > 0) {
            plan.notes.add("§c" + tntBlocks + " 个 TNT 方块将被摧毁(不会被引爆)");
        }
    }

    private static Vec3 pushVelocity(Entity e, Vec3 center, float power) {
        double radius = Math.max(1.0, power * 2.0);
        Vec3 diff = e.position().add(0, e.getBbHeight() * 0.5, 0).subtract(center);
        double dist = diff.length();
        if (dist > radius || dist < 1.0E-4) {
            return Vec3.ZERO;
        }
        double d = dist / radius;
        double mag = (1.0 - d) * 1.4;                // approximated vanilla knockback
        return diff.normalize().scale(mag);
    }

    private static void pushes(Level level, Plan plan, Vec3 center, float power, Entity source) {
        double radius = Math.max(1.0, power * 2.0);
        List<Entity> ents = level.getEntitiesOfClass(Entity.class, new AABB(center, center).inflate(radius));
        int count = 0;
        for (Entity e : ents) {
            if (e == source || !e.isAlive() || e.isRemoved() || count >= MAX_PUSH) {
                continue;
            }
            if (!(e instanceof LivingEntity) && !(e instanceof ItemEntity) && !(e instanceof PrimedTnt) && !(e instanceof MinecartTNT)) {
                continue;
            }
            Vec3 push = pushVelocity(e, center, power);
            if (push.lengthSqr() < 1.0E-6) {
                continue;
            }
            PushArc arc = simulateArc(level, e, e.position(), e.getDeltaMovement().add(push));
            if (arc.points.size() < 2) {
                continue;
            }
            arc.from = e.position();
            arc.player = e instanceof net.minecraft.world.entity.player.Player;
            arc.note = describe(level, e, arc, center);
            arc.color = arc.note.contains("岩浆") ? 0xFFFF5020
                : arc.note.contains("仙人掌") ? 0xFF40C040
                : arc.note.contains("虚空") ? 0xFFB060FF
                : arc.player ? 0xFFFFE040 : 0xFFA0A0A0;
            plan.pushes.add(arc);
            count++;
        }
    }

    private static String describe(Level level, Entity e, PushArc arc, Vec3 center) {
        String name = e instanceof net.minecraft.world.entity.player.Player ? "你" : e.getName().getString();
        if (arc.landing == null) {
            return "§7" + name + ": 轨迹未落地";
        }
        BlockPos lp = BlockPos.containing(arc.landing);
        if (arc.landing.y < level.getMinY() + 1) {
            return "§5" + name + " → 虚空";
        }
        if (level.getFluidState(lp).is(FluidTags.LAVA)) {
            return "§c" + name + " → 岩浆!";
        }
        if (level.getBlockState(lp).is(Blocks.CACTUS)) {
            return "§2" + name + " → 仙人掌";
        }
        if (level.getFluidState(lp).is(FluidTags.WATER)) {
            return "§b" + name + " → 水里(安全)";
        }
        double fall = center.y - arc.landing.y;
        if (fall > 4) {
            int dmg = (int) Math.floor(fall - 3);
            return "§e" + name + " → 摔落 " + dmg + " 点";
        }
        return "§7" + name + " → 安全落地";
    }

    /** Simple push-flight: gravity + drag per tick, ray-clipped against the world. */
    public static PushArc simulateArc(Level level, Entity e, Vec3 start, Vec3 velocity) {
        PushArc arc = new PushArc();
        // vanilla falling blocks use the same 0.04 gravity / 0.98 drag as items and TNT
        double gravity = e instanceof ItemEntity || e instanceof PrimedTnt || e instanceof MinecartTNT
            || e instanceof net.minecraft.world.entity.item.FallingBlockEntity ? 0.04 : 0.08;
        Vec3 pos = start;
        Vec3 v = velocity;
        arc.points.add(pos);
        for (int t = 0; t < horizonTicks; t++) {
            v = v.add(0, -gravity, 0);
            boolean inWater = level.getFluidState(BlockPos.containing(pos)).is(FluidTags.WATER);
            if (inWater) {
                v = v.scale(0.8);
            } else {
                v = v.scale(0.98);
            }
            Vec3 to = pos.add(v);
            // NOTE: ClipContext's Entity overload calls CollisionContext.of(entity), which NPEs on null;
            // non-living entities (items / TNT) therefore use the empty context explicitly.
            CollisionContext ctx = e instanceof LivingEntity && e.level() != null
                ? CollisionContext.of(e) : CollisionContext.empty();
            var hit = level.clip(new ClipContext(pos, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, ctx));
            if (hit.getType() != HitResult.Type.MISS) {
                arc.points.add(hit.getLocation());
                arc.landing = hit.getLocation();
                return arc;
            }
            arc.points.add(to);
            pos = to;
            if (v.lengthSqr() < 1.0E-5) {
                break;
            }
        }
        arc.landing = pos;
        return arc;
    }

    private static void falling(Level level, Plan plan, Vec3 center, float power) {
        int r = (int) Math.ceil(power * 0.7);
        int columns = 0;
        int crushing = 0;
        BlockPos origin = BlockPos.containing(center);
        for (int dx = -r; dx <= r; dx++) {
            for (int dz = -r; dz <= r; dz++) {
                for (int dy = 2; dy <= 6; dy++) {
                    BlockPos p = origin.offset(dx, dy, dz);
                    var st = level.getBlockState(p);
                    if (!(st.getBlock() instanceof FallingBlock)) {
                        continue;
                    }
                    var below = level.getBlockState(p.below());
                    if (!(below.getBlock() instanceof FallingBlock) && !below.isAir()) {
                        columns++;
                        int landY = p.getY();
                        for (int y = p.getY() - 1; y > level.getMinY(); y--) {
                            var s2 = level.getBlockState(new BlockPos(p.getX(), y, p.getZ()));
                            if (!s2.getCollisionShape(level, new BlockPos(p.getX(), y, p.getZ())).isEmpty()) {
                                landY = y + 1;
                                break;
                            }
                        }
                        AABB box = new AABB(p.getX(), landY - 1, p.getZ(), p.getX() + 1, landY + 1, p.getZ() + 1);
                        if (!level.getEntitiesOfClass(LivingEntity.class, box).isEmpty()) {
                            crushing++;
                        }
                        break;
                    }
                }
            }
        }
        if (columns > 0) {
            plan.notes.add("§6" + columns + " 个下落方块将砸落" + (crushing > 0 ? " §c(其中 " + crushing + " 处下面有生物!)" : ""));
        }
    }
}
