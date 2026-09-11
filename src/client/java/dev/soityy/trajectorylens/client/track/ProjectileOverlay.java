package dev.soityy.trajectorylens.client.track;

import dev.soityy.trajectorylens.client.Lang;

import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;

/**
 * Tracks nearby supported projectiles (arrows, throwables, fireballs) and primed
 * TNT, and keeps a freshly simulated flight path for each of them.
 */
public final class ProjectileOverlay {

    public static final int MAX_ENTRIES = 12;
    public static final double SCAN_RADIUS = 48.0;
    private static final int RECOMPUTE_TICKS = 3;

    private boolean projectilesOn = true;
    private boolean tntOn = true;
    private boolean aimOn = true;          // held-item aiming preview
    private boolean chainOn = true;        // causal chain forecast
    private boolean fallingOn = true;      // falling-block landing forecast
    private final java.util.List<Falling> falling = new java.util.ArrayList<>();
    private ProjectileKind aimKind;
    private ProjectileSim.Result aimResult;
    private String aimLabel = "";
    private Vec3 aimPearlDest;
    private String aimPearlText = "";
    private boolean aimPearlDanger;
    private final Map<Integer, Entry> entries = new LinkedHashMap<>();
    private ProjectileSim sim;
    private int tick;

    public static final class Entry {
        public Entity entity;
        public ProjectileKind kind;
        public ProjectileSim.Result result;
        public Vec3 lastPos = Vec3.ZERO;
        public Vec3 lastVel = Vec3.ZERO;
        public int lastFuse = -1;
        public int counter;
        // blast-warning extras (TNT / creeper)
        public double explosionPower;
        public int fuseObserved;
        public long impactTick = -100;
        public String impactText = "";
        public final java.util.List<net.minecraft.core.BlockPos> risky = new java.util.ArrayList<>();
        // ender pearl teleport analysis
        public Vec3 pearlDest;
        public String pearlText = "";
        public boolean pearlDanger;
        public ChainForecast.Plan chain;
    }

    public void register() {
        ClientTickEvents.END_CLIENT_TICK.register(this::onTick);
        net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLevelEvents.AFTER_CLIENT_LEVEL_CHANGE.register((c, level) -> {
            this.entries.clear();
            this.sim = null;
        });
    }

    public boolean projectilesEnabled() {
        return this.projectilesOn;
    }

    public boolean tntEnabled() {
        return this.tntOn;
    }

    public void setProjectiles(boolean on) {
        this.projectilesOn = on;
    }

    public void setTnt(boolean on) {
        this.tntOn = on;
    }

    public void toggleProjectiles() {
        this.projectilesOn = !this.projectilesOn;
    }

    public void toggleTnt() {
        this.tntOn = !this.tntOn;
    }

    public boolean aimEnabled() {
        return this.aimOn;
    }

    public void setAim(boolean on) {
        this.aimOn = on;
        if (!on) {
            this.aimKind = null;
            this.aimResult = null;
            this.aimLabel = "";
        }
    }

    public void toggleAim() {
        this.setAim(!this.aimOn);
    }

    public boolean chainEnabled() {
        return this.chainOn;
    }

    public void setChain(boolean on) {
        this.chainOn = on;
        if (!on) {
            for (Entry e : this.entries.values()) {
                e.chain = null;
            }
        }
    }

    public void toggleChain() {
        this.setChain(!this.chainOn);
    }

    /** Chain forecast depth (blast layers) and horizon (seconds of knock-back flight). */
    public int chainDepth() {
        return ChainForecast.maxDepth;
    }

    public void setChainDepth(int depth) {
        ChainForecast.maxDepth = Math.max(1, Math.min(4, depth));
    }

    public void cycleChainDepth() {
        int[] c = ChainForecast.DEPTH_CHOICES;
        for (int v : c) {
            if (v > ChainForecast.maxDepth) {
                ChainForecast.maxDepth = v;
                return;
            }
        }
        ChainForecast.maxDepth = c[0];
    }

    public int chainHorizonSeconds() {
        return ChainForecast.horizonTicks / 20;
    }

    public void setChainHorizonSeconds(int seconds) {
        ChainForecast.horizonTicks = Math.max(20, Math.min(600, seconds * 20));
    }

    public void cycleChainHorizon() {
        int[] c = ChainForecast.HORIZON_CHOICES;
        for (int v : c) {
            if (v > ChainForecast.horizonTicks) {
                ChainForecast.horizonTicks = v;
                return;
            }
        }
        ChainForecast.horizonTicks = c[0];
    }

    public @org.jspecify.annotations.Nullable ProjectileKind aimKind() {
        return this.aimKind;
    }

    public ProjectileSim.Result aimResult() {
        return this.aimResult;
    }

    public String aimLabel() {
        return this.aimLabel;
    }

    public Vec3 aimPearlDest() {
        return this.aimPearlDest;
    }

    public String aimPearlText() {
        return this.aimPearlText;
    }

    public boolean aimPearlDanger() {
        return this.aimPearlDanger;
    }

    public Map<Integer, Entry> entries() {
        return this.entries;
    }

    public boolean fallingEnabled() {
        return this.fallingOn;
    }

    public void setFalling(boolean on) {
        this.fallingOn = on;
        if (!on) {
            this.falling.clear();
        }
    }

    public void toggleFalling() {
        this.setFalling(!this.fallingOn);
    }

    /** Nearby falling blocks (sand/gravel/anvil/dripstone) with their predicted landing spot. */
    public java.util.List<Falling> falling() {
        return this.falling;
    }

    /** One falling block: where it started, where it will land, what it will hit. */
    public record Falling(Vec3 from, Vec3 landing, String block, String crush, int ticks) {
    }

    public String summary() {
        return "projectiles=" + (this.projectilesOn ? "on" : "off")
            + ", tnt=" + (this.tntOn ? "on" : "off")
            + ", aim=" + (this.aimOn ? "on" : "off")
            + ", chain=" + (this.chainOn ? "on" : "off")
            + ", falling=" + (this.fallingOn ? "on" : "off")
            + ", tracked=" + this.entries.size();
    }

    /**
     * Held-item aiming preview: bows while drawing, charged crossbows, tridents
     * while charging, and throwables in hand. Launch velocity mirrors vanilla
     * (direction * power + shooter movement; BowItem#getPowerForTime).
     */
    private void updateAim(Minecraft mc) {
        this.aimKind = null;
        this.aimResult = null;
        this.aimLabel = "";
        var p = mc.player;
        if (p == null) {
            return;
        }
        for (InteractionHand hand : InteractionHand.values()) {
            ItemStack st = p.getItemInHand(hand);
            if (st.isEmpty()) {
                continue;
            }
            ProjectileKind kind = null;
            double speed = 0;
            String label = "";
            if (st.is(Items.BOW)) {
                if (!p.isUsingItem() || !ItemStack.isSameItemSameComponents(p.getUseItem(), st)) {
                    continue;
                }
                float power = BowItem.getPowerForTime(p.getTicksUsingItem());
                if (power <= 0.05F) {
                    continue;
                }
                kind = ProjectileKind.ARROW;
                speed = power * 3.0;
                label = String.format(Lang.tr("拉弦 %d%%"), Math.round(power * 100));
            } else if (st.is(Items.CROSSBOW)) {
                if (!CrossbowItem.isCharged(st)) {
                    continue;
                }
                kind = ProjectileKind.ARROW;
                speed = 3.15;
                label = Lang.tr("弩已上膛");
            } else if (st.is(Items.TRIDENT)) {
                if (!p.isUsingItem() || !ItemStack.isSameItemSameComponents(p.getUseItem(), st)) {
                    continue;
                }
                kind = ProjectileKind.TRIDENT;
                speed = 2.5;
                label = Lang.tr("三叉戟");
            } else if (st.is(Items.SNOWBALL)) {
                kind = ProjectileKind.SNOWBALL;
                speed = 1.5;
                label = Lang.tr("雪球");
            } else if (st.is(Items.EGG)) {
                kind = ProjectileKind.EGG;
                speed = 1.5;
                label = Lang.tr("鸡蛋");
            } else if (st.is(Items.ENDER_PEARL)) {
                kind = ProjectileKind.ENDER_PEARL;
                speed = 1.5;
                label = Lang.tr("末影珍珠");
            } else if (st.is(Items.SPLASH_POTION) || st.is(Items.LINGERING_POTION)) {
                kind = ProjectileKind.POTION;
                speed = 1.5;
                label = Lang.tr("药水");
            } else if (st.is(Items.EXPERIENCE_BOTTLE)) {
                kind = ProjectileKind.SNOWBALL;
                speed = 1.5;
                label = Lang.tr("经验瓶");
            }
            if (kind == null) {
                continue;
            }
            Vec3 dir = p.getViewVector(1.0F);
            Vec3 v = dir.scale(speed);
            Vec3 mv = p.getDeltaMovement();
            v = v.add(mv.x, p.onGround() ? 0.0 : mv.y, mv.z);
            this.aimKind = kind;
            this.aimLabel = label;
            this.aimResult = this.sim.simulate(kind, p.getEyePosition(), v, -1, p.getId(), -1, 200);
            if (kind == ProjectileKind.ENDER_PEARL) {
                PearlInfo info = analyzePearl(this.aimResult);
                if (info != null) {
                    this.aimPearlDest = info.dest();
                    this.aimPearlText = info.text();
                    this.aimPearlDanger = info.danger();
                }
            }
            return;
        }
    }

    private void onTick(Minecraft mc) {
        this.tick++;
        ClientLevel level = mc.level;
        if (level == null || mc.player == null) {
            if (!this.entries.isEmpty()) {
                this.entries.clear();
            }
            return;
        }
        if (this.sim == null) {
            this.sim = new ProjectileSim(level);
        }
        if (this.aimOn && this.tick % 2 == 0) {
            updateAim(mc);
        }

        Iterator<Map.Entry<Integer, Entry>> it = this.entries.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<Integer, Entry> e = it.next();
            Entity ent = level.getEntity(e.getKey());
            if (ent == null || !ent.isAlive()) {
                it.remove();
            }
        }

        for (Entity ent : level.getEntitiesOfClass(Entity.class, mc.player.getBoundingBox().inflate(SCAN_RADIUS))) {
            ProjectileKind kind = ProjectileKind.of(ent);
            if (kind == null || !ent.isAlive()) {
                continue;
            }
            boolean on = kind == ProjectileKind.TNT ? this.tntOn : this.projectilesOn;
            if (!on) {
                continue;
            }
            Vec3 vel = ent.getDeltaMovement();
            boolean interesting = kind == ProjectileKind.TNT || vel.lengthSqr() > 1.0E-6 || ent instanceof AbstractArrow;
            if (!interesting) {
                continue;
            }
            int fuse = kind == ProjectileKind.TNT && ent instanceof net.minecraft.world.entity.item.PrimedTnt tnt
                ? tnt.getFuse() : -1;
            int id = ent.getId();
            Entry en = this.entries.get(id);
            if (en == null) {
                if (this.entries.size() >= MAX_ENTRIES) {
                    continue;
                }
                en = new Entry();
                en.entity = ent;
                en.kind = kind;
                en.explosionPower = kind.explosionPower;
                this.entries.put(id, en);
            }
            en.entity = ent;
            en.counter++;
            boolean due = en.result == null
                || en.counter >= RECOMPUTE_TICKS
                || en.lastPos.distanceToSqr(ent.position()) > 0.04
                || en.lastVel.distanceToSqr(vel) > 0.0025
                || (fuse >= 0 && Math.abs(fuse - en.lastFuse) > 2);
            if (due) {
                en.counter = 0;
                en.lastPos = ent.position();
                en.lastVel = vel;
                en.lastFuse = fuse;
                int ownerId = ent instanceof Projectile proj && proj.getOwner() != null ? proj.getOwner().getId() : -1;
                en.result = this.sim.simulate(kind, ent.position(), vel, fuse, ownerId, id, 200);
                if (kind == ProjectileKind.ENDER_PEARL) {
                    PearlInfo info = analyzePearl(en.result);
                    if (info != null) {
                        en.pearlDest = info.dest();
                        en.pearlText = info.text();
                        en.pearlDanger = info.danger();
                    }
                }
            }
            if (en.explosionPower > 0 && this.tick - en.impactTick >= 20) {
                Vec3 center = en.result != null && en.result.explosionPos != null
                    ? en.result.explosionPos : ent.position();
                scanImpact(en, center, en.explosionPower);
                if (this.chainOn) {
                    int chainFuse = en.result != null ? Math.max(0, en.result.fuseRemaining) : 0;
                    try {
                        en.chain = ChainForecast.compute(level, center, (float) en.explosionPower, chainFuse, ent);
                    } catch (Exception ex) {
                        en.chain = null; // never let the forecast break the tick
                    }
                } else {
                    en.chain = null;
                }
                en.impactTick = this.tick;
            }
        }

        // falling blocks: where they land and whether somebody is standing there
        this.falling.clear();
        if (this.fallingOn) {
            for (net.minecraft.world.entity.item.FallingBlockEntity fb
                : level.getEntitiesOfClass(net.minecraft.world.entity.item.FallingBlockEntity.class,
                    mc.player.getBoundingBox().inflate(SCAN_RADIUS))) {
                if (!fb.isAlive() || this.falling.size() >= 10) {
                    continue;
                }
                ChainForecast.PushArc arc = ChainForecast.simulateArc(level, fb, fb.position(), fb.getDeltaMovement());
                Vec3 land = arc.landing != null ? arc.landing : fb.position();
                String crush = "";
                var box = new net.minecraft.world.phys.AABB(land.x - 0.6, land.y - 2.0, land.z - 0.6,
                    land.x + 0.6, land.y + 0.4, land.z + 0.6);
                for (net.minecraft.world.entity.LivingEntity le
                    : level.getEntitiesOfClass(net.minecraft.world.entity.LivingEntity.class, box)) {
                    crush = le == mc.player ? Lang.tr("砸到你!") : (Lang.tr("砸到 ") + le.getName().getString() + "!");
                    break;
                }
                this.falling.add(new Falling(fb.position(), land,
                    fb.getBlockState().getBlock().getName().getString(), crush, Math.max(1, arc.points.size() - 1)));
            }
        }

        // creepers about to blow up: swell direction is synced, fuse is 30 ticks
        if (this.tntOn) {
            for (net.minecraft.world.entity.monster.Creeper creeper
                : level.getEntitiesOfClass(net.minecraft.world.entity.monster.Creeper.class,
                    mc.player.getBoundingBox().inflate(SCAN_RADIUS))) {
                boolean swelling = creeper.getSwellDir() > 0;
                int cid = creeper.getId();
                Entry en = this.entries.get(cid);
                if (!swelling) {
                    if (en != null && en.kind == ProjectileKind.CREEPER) {
                        this.entries.remove(cid);
                    }
                    continue;
                }
                if (en == null) {
                    if (this.entries.size() >= MAX_ENTRIES) {
                        continue;
                    }
                    en = new Entry();
                    en.entity = creeper;
                    en.kind = ProjectileKind.CREEPER;
                    this.entries.put(cid, en);
                }
                en.fuseObserved++;
                en.explosionPower = creeper.isPowered() ? 6.0 : 3.0;
                Vec3 pos = creeper.position();
                ProjectileSim.Result r = new ProjectileSim.Result();
                r.kind = ProjectileKind.CREEPER;
                r.points.add(pos);
                r.points.add(pos);
                r.exploded = true;
                r.explosionPos = pos;
                r.fuseRemaining = Math.max(0, 30 - en.fuseObserved);
                r.ticks = en.fuseObserved;
                en.result = r;
                if (this.tick - en.impactTick >= 20 || en.impactText.isEmpty()) {
                    scanImpact(en, pos, en.explosionPower);
                    if (this.chainOn) {
                        en.chain = ChainForecast.compute(level, pos, (float) en.explosionPower, r.fuseRemaining, creeper);
                    } else {
                        en.chain = null;
                    }
                    en.impactTick = this.tick;
                }
            }
        }
    }

    public record PearlInfo(Vec3 dest, String text, boolean danger) {
    }

    /**
     * Ender pearl landing analysis. The teleport target is the pearl position one
     * tick before impact (vanilla uses oldPosition()), fall distance is reset by
     * the teleport so any drop from there causes real fall damage.
     */
    private PearlInfo analyzePearl(ProjectileSim.Result r) {
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null || r == null || r.points.size() < 2 || r.hit == null) {
            return null;
        }
        Vec3 dest = r.points.get(r.points.size() - 2);
        net.minecraft.core.BlockPos feet = net.minecraft.core.BlockPos.containing(dest);
        boolean danger = false;
        StringBuilder sb = new StringBuilder();
        if (dest.y < level.getMinY() + 1) {
            sb.append(Lang.tr("§c虚空! 传过去就没了"));
            danger = true;
        } else {
            var state = level.getBlockState(feet);
            boolean inLava = state.getFluidState().is(net.minecraft.tags.FluidTags.LAVA);
            boolean inWater = state.getFluidState().is(net.minecraft.tags.FluidTags.WATER);
            boolean blocked = !state.getCollisionShape(level, feet).isEmpty();
            if (inLava) {
                sb.append(Lang.tr("§c落点岩浆: 传送即重伤"));
                danger = true;
            } else if (inWater) {
                sb.append(Lang.tr("§a落点水中: 无落地伤害"));
            } else if (blocked) {
                sb.append(Lang.tr("§c落点在方块内部: 窒息风险"));
                danger = true;
            } else {
                int groundY = Integer.MIN_VALUE;
                for (int y = feet.getY() - 1; y > Math.max(level.getMinY(), feet.getY() - 64); y--) {
                    var below = level.getBlockState(new net.minecraft.core.BlockPos(feet.getX(), y, feet.getZ()));
                    if (below.getFluidState().is(net.minecraft.tags.FluidTags.WATER)) {
                        groundY = y + 1;
                        sb.append(Lang.tr("§a落点水面: 无落地伤害"));
                        break;
                    }
                    if (!below.getCollisionShape(level, new net.minecraft.core.BlockPos(feet.getX(), y, feet.getZ())).isEmpty()) {
                        groundY = y + 1;
                        int fall = (int) Math.floor(dest.y - groundY);
                        if (fall > 3) {
                            int dmg = fall - 3;
                            sb.append(Lang.tr("§e下坠 ")).append(fall).append(Lang.tr(" 格: 约 ")).append(dmg).append(Lang.tr(" 点摔落伤害(未计护具)"));
                            danger = dmg >= 6;
                        } else {
                            sb.append(Lang.tr("§a可直接落地"));
                        }
                        break;
                    }
                }
                if (groundY == Integer.MIN_VALUE) {
                    sb.append(Lang.tr("§c下方无地面: 持续下坠"));
                    danger = true;
                }
            }
        }
        sb.append(Lang.tr("  §7(传送自伤 5)"));
        return new PearlInfo(dest, sb.toString(), danger);
    }

    /** Lists what an explosion would hit: entities by category and risky blocks. */
    private void scanImpact(Entry en, Vec3 center, double power) {
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null) {
            return;
        }
        double radius = power * 2.0;
        net.minecraft.world.phys.AABB box = new net.minecraft.world.phys.AABB(center, center).inflate(radius);
        int players = 0;
        int villagers = 0;
        int tamed = 0;
        int mobs = 0;
        for (net.minecraft.world.entity.LivingEntity le
            : level.getEntitiesOfClass(net.minecraft.world.entity.LivingEntity.class, box)) {
            if (le instanceof net.minecraft.world.entity.player.Player) {
                players++;
            } else if (le instanceof net.minecraft.world.entity.npc.villager.Villager) {
                villagers++;
            } else if (le instanceof net.minecraft.world.entity.TamableAnimal ta && ta.isTame()) {
                tamed++;
            } else {
                mobs++;
            }
        }
        int containers = 0;
        int hoppers = 0;
        int spawners = 0;
        en.risky.clear();
        int r = (int) Math.ceil(radius);
        net.minecraft.core.BlockPos origin = net.minecraft.core.BlockPos.containing(center);
        for (net.minecraft.core.BlockPos p : net.minecraft.core.BlockPos.betweenClosed(
            origin.offset(-r, -r, -r), origin.offset(r, r, r))) {
            var be = level.getBlockEntity(p);
            if (be == null) {
                continue;
            }
            if (be instanceof net.minecraft.world.level.block.entity.SpawnerBlockEntity) {
                spawners++;
            } else if (be instanceof net.minecraft.world.level.block.entity.ChestBlockEntity
                || be instanceof net.minecraft.world.level.block.entity.BarrelBlockEntity
                || be instanceof net.minecraft.world.level.block.entity.ShulkerBoxBlockEntity) {
                containers++;
            } else if (be instanceof net.minecraft.world.level.block.entity.HopperBlockEntity) {
                hoppers++;
            } else {
                continue;
            }
            if (en.risky.size() < 8) {
                en.risky.add(p.immutable());
            }
        }
        StringBuilder sb = new StringBuilder(Lang.tr("波及: "));
        boolean any = false;
        if (players > 0) {
            sb.append(Lang.tr("玩家")).append(players).append(' ');
            any = true;
        }
        if (villagers > 0) {
            sb.append(Lang.tr("村民")).append(villagers).append(' ');
            any = true;
        }
        if (tamed > 0) {
            sb.append(Lang.tr("宠物")).append(tamed).append(' ');
            any = true;
        }
        if (mobs > 0) {
            sb.append(Lang.tr("其他生物")).append(mobs);
            any = true;
        }
        if (!any) {
            sb.append(Lang.tr("无生物"));
        }
        var pl = Minecraft.getInstance().player;
        if (pl != null && pl.position().distanceTo(center) <= radius) {
            sb.append(Lang.tr(" §c⚠你在范围内"));
        }
        if (containers + hoppers + spawners > 0) {
            sb.append(Lang.tr("  §e风险: "));
            if (containers > 0) {
                sb.append(Lang.tr("容器")).append(containers).append(' ');
            }
            if (hoppers > 0) {
                sb.append(Lang.tr("漏斗")).append(hoppers).append(' ');
            }
            if (spawners > 0) {
                sb.append(Lang.tr("刷怪笼")).append(spawners);
            }
        }
        en.impactText = sb.toString();
    }
}
