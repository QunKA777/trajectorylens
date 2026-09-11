package dev.soityy.trajectorylens.client.track;

import dev.soityy.trajectorylens.client.Lang;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.PrimedTnt;
import net.minecraft.world.entity.projectile.ThrowableProjectile;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.entity.projectile.arrow.ThrownTrident;
import net.minecraft.world.entity.projectile.hurtingprojectile.LargeFireball;
import net.minecraft.world.entity.projectile.hurtingprojectile.SmallFireball;
import net.minecraft.world.entity.projectile.throwableitemprojectile.AbstractThrownPotion;
import net.minecraft.world.entity.projectile.throwableitemprojectile.Snowball;
import net.minecraft.world.entity.projectile.throwableitemprojectile.ThrownEgg;
import net.minecraft.world.entity.projectile.throwableitemprojectile.ThrownEnderpearl;

/**
 * Physical parameters per projectile family, extracted from the 26.2 sources:
 *   AbstractArrow : gravity 0.05, air drag 0.99 (after move), water drag 0.6 (before move)
 *   ThrowableProjectile : gravity 0.03, air drag 0.99, water drag 0.8
 *   AbstractHurtingProjectile : no gravity, inertia 0.95, +0.1 acceleration along motion
 *   PrimedTnt : gravity 0.04, air drag 0.98, ground bounce (0.7, -0.5, 0.7), fuse
 */
public enum ProjectileKind {
    ARROW("箭", 0xFFE8E8E8, 0.05, 0.99, 0.6, 0.0, 0.0F, false),
    TRIDENT("三叉戟", 0xFF39E0D2, 0.05, 0.99, 0.6, 0.0, 0.0F, false),
    SNOWBALL("雪球", 0xFFFFFFFF, 0.03, 0.99, 0.8, 0.0, 0.0F, false),
    EGG("鸡蛋", 0xFFF6E3B4, 0.03, 0.99, 0.8, 0.0, 0.0F, false),
    ENDER_PEARL("末影珍珠", 0xFF2AA79B, 0.03, 0.99, 0.8, 0.0, 0.0F, false),
    POTION("药水", 0xFFCB6BFF, 0.03, 0.99, 0.8, 0.0, 0.0F, false),
    FIREBALL("火球", 0xFFFF7A1A, 0.0, 0.95, 0.95, 0.1, 1.0F, false),
    SMALL_FIREBALL("小火球", 0xFFFFA94A, 0.0, 0.95, 0.95, 0.1, 0.0F, false),
    TNT("TNT", 0xFFFF4A3A, 0.04, 0.98, 0.98, 0.0, 4.0F, true),
    CREEPER("苦力怕", 0xFF5BD24A, 0.0, 1.0, 1.0, 0.0, 3.0F, false);

    /** Chinese source text, also the translation key. */
    public final String key;
    public final int color;
    public final double gravity;
    public final double airDrag;
    public final double waterDrag;
    public final double acceleration;
    public final float explosionPower;   // 0 = no explosion
    public final boolean moveCollision;  // PrimedTnt-style move() instead of ray clip

    /** Translated projectile name. */
    public String label() {
        return Lang.tr(this.key);
    }

    ProjectileKind(String key, int color, double gravity, double airDrag, double waterDrag,
                   double acceleration, float explosionPower, boolean moveCollision) {
        this.key = key;
        this.color = color;
        this.gravity = gravity;
        this.airDrag = airDrag;
        this.waterDrag = waterDrag;
        this.acceleration = acceleration;
        this.explosionPower = explosionPower;
        this.moveCollision = moveCollision;
    }

    /** Classify a live entity; returns null for unsupported kinds. */
    public static ProjectileKind of(Entity e) {
        if (e instanceof ThrownTrident) {
            return TRIDENT;
        }
        if (e instanceof AbstractArrow) {
            return ARROW;
        }
        if (e instanceof ThrownEnderpearl) {
            return ENDER_PEARL;
        }
        if (e instanceof AbstractThrownPotion) {
            return POTION;
        }
        if (e instanceof Snowball) {
            return SNOWBALL;
        }
        if (e instanceof ThrownEgg) {
            return EGG;
        }
        if (e instanceof ThrowableProjectile) {
            return SNOWBALL; // other throwable items behave like snowballs
        }
        if (e instanceof LargeFireball) {
            return FIREBALL;
        }
        if (e instanceof SmallFireball) {
            return SMALL_FIREBALL;
        }
        if (e instanceof PrimedTnt) {
            return TNT;
        }
        return null;
    }
}
