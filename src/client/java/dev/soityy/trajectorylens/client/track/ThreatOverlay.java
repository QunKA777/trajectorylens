package dev.soityy.trajectorylens.client.track;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Client-side threat estimate for hostile mobs: uses distance vs the mob's
 * follow-range attribute, line of sight and facing direction to label each one
 * as likely-locked / alerted / unaware. Purely an estimate (the real AI target
 * lives on the server), but very handy for survival awareness.
 */
public final class ThreatOverlay {

    public static final double RADIUS = 32.0;

    public enum Level {
        LOCKED, ALERT, UNAWARE
    }

    public static final class Threat {
        public Mob mob;
        public Level level;
        public double distance;
    }

    private boolean on = true;
    private final List<Threat> threats = new ArrayList<>();
    private int tick;

    public boolean enabled() {
        return this.on;
    }

    public void setEnabled(boolean on) {
        this.on = on;
        if (!on) {
            this.threats.clear();
        }
    }

    public void toggle() {
        this.setEnabled(!this.on);
    }

    public List<Threat> threats() {
        return this.threats;
    }

    public String summary() {
        int locked = 0;
        int alert = 0;
        for (Threat t : this.threats) {
            if (t.level == Level.LOCKED) {
                locked++;
            } else if (t.level == Level.ALERT) {
                alert++;
            }
        }
        return "威胁指示=" + (this.on ? "on" : "off") + ", 锁定" + locked + " 警戒" + alert
            + " 未察觉" + (this.threats.size() - locked - alert);
    }

    public void tick(Minecraft mc) {
        this.tick++;
        ClientLevel level = mc.level;
        var player = mc.player;
        if (level == null || player == null) {
            this.threats.clear();
            return;
        }
        if (!this.on || this.tick % 5 != 0) {
            return;
        }
        Vec3 eye = player.getEyePosition();
        List<Threat> found = new ArrayList<>();
        for (Mob mob : level.getEntitiesOfClass(Mob.class, player.getBoundingBox().inflate(RADIUS))) {
            if (!(mob instanceof Enemy) || !mob.isAlive()) {
                continue;
            }
            double dist = mob.distanceTo(player);
            double follow = 16.0;
            var attr = mob.getAttribute(Attributes.FOLLOW_RANGE);
            if (attr != null) {
                follow = attr.getValue();
            }
            Vec3 mobEye = mob.getEyePosition();
            boolean los = level.clip(new ClipContext(mobEye, eye,
                ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, mob)).getType() == HitResult.Type.MISS;
            Vec3 toPlayer = eye.subtract(mobEye).normalize();
            boolean facing = mob.getViewVector(1.0F).dot(toPlayer) > 0.4;
            Level state;
            if (los && ((facing && dist <= 12) || dist <= 4)) {
                state = Level.LOCKED;
            } else if (los && dist <= follow) {
                state = Level.ALERT;
            } else {
                state = Level.UNAWARE;
            }
            Threat t = new Threat();
            t.mob = mob;
            t.level = state;
            t.distance = dist;
            found.add(t);
        }
        found.sort(Comparator.comparingDouble(t -> t.distance));
        this.threats.clear();
        this.threats.addAll(found.subList(0, Math.min(12, found.size())));
    }
}
