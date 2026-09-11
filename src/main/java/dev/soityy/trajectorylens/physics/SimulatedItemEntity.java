package dev.soityy.trajectorylens.physics;

import net.minecraft.tags.FluidTags;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * A "shadow" ItemEntity that replays the authoritative 26.2 drop physics
 * without ever being added to the world. Every movement step calls the same
 * engine methods the vanilla server tick uses (updateFluidInteraction,
 * applyGravity, move, applyEffectsFromBlocks, drag/friction), so predictions
 * are bit-for-bit consistent with real dropped items as long as no living
 * entity pushes the item and the item is not picked up/merged (tracker ends
 * the prediction in those cases).
 */
public class SimulatedItemEntity extends ItemEntity {

    public enum StopReason {
        CONTINUE, REST, BURNED, VOID, HORIZON, UNLOADED
    }

    public static final int HORIZON_TICKS = 120;      // max predicted ticks (~6 s)
    public static final double REST_EPS = 1.0E-5;

    private StopReason lastStop = StopReason.CONTINUE;

    private int simTicks;
    private int simHealth = 5;      // ItemEntity.DEFAULT_HEALTH
    private int ignitedTick = -1;   // first tick spent in lava
    private int damageTicks;

    public SimulatedItemEntity(Level level, double x, double y, double z, ItemStack stack, double vx, double vy, double vz) {
        super(level, x, y, z, stack, vx, vy, vz);
    }

    public int simTicks() {
        return this.simTicks;
    }

    public StopReason lastStop() {
        return this.lastStop;
    }

    /** Re-seed this shadow from the current real entity state. */
    public void reset(double x, double y, double z, double vx, double vy, double vz, ItemStack stack) {
        this.setPos(x, y, z);
        this.setDeltaMovement(vx, vy, vz);
        if (stack != null && !stack.isEmpty()) {
            this.setItem(stack);
        }
        this.simTicks = 0;
        this.simHealth = 5;
        this.ignitedTick = -1;
        this.damageTicks = 0;
        this.lastStop = StopReason.CONTINUE;
    }

    @Override
    protected void doWaterSplashEffect() {
        // Prediction must never spawn particles or play sounds.
    }

    /**
     * The shadow simulates the authoritative (server-side) tick, but it lives in the
     * client level. Entity.move() skips collision restitution/onGround updates for
     * non-authoritative client entities; without this override the leftover downward
     * velocity gets flipped by ItemEntity's legacy *-0.5 rule and the predicted path
     * shows a bounce that never happens in game.
     */
    @Override
    protected boolean isLocalClientAuthoritative() {
        return true;
    }

    /** Mirrors Entity.baseTick's fluid interaction (before movement each tick). */
    private void tickFluidInteraction() {
        this.updateFluidInteraction();
    }

    /** Mirrors ItemEntity#setFluidMovement. */
    private void fluidMovement(double multiplier) {
        Vec3 movement = this.getDeltaMovement();
        double y = movement.y;
        if (y < 0.06F) {
            y += 5.0E-4F;
        }
        this.setDeltaMovement(movement.x * multiplier, y, movement.z * multiplier);
    }

    private boolean touchingLava() {
        return this.isInLava() && this.getFluidHeight(FluidTags.LAVA) > 0.1F;
    }

    public boolean resting() {
        Vec3 v = this.getDeltaMovement();
        return this.onGround() && v.horizontalDistanceSqr() <= REST_EPS && Math.abs(v.y) <= REST_EPS;
    }

    /** Advances the shadow by one authoritative tick. Returns whether it should keep going. */
    public boolean advanceTick() {
        this.tickFluidInteraction();

        Vec3 oldMovement = this.getDeltaMovement();
        if (this.isInWater() && this.getFluidHeight(FluidTags.WATER) > 0.1F) {
            this.fluidMovement(0.99F);
        } else if (this.touchingLava()) {
            this.fluidMovement(0.95F);
        } else {
            this.applyGravity();
        }

        // Server-side noPhysics handling (item stuck inside a block): replicate.
        this.noPhysics = !this.level().noCollision(this, this.getBoundingBox().deflate(1.0E-7));
        if (this.noPhysics) {
            this.moveTowardsClosestSpace(this.getX(), (this.getBoundingBox().minY + this.getBoundingBox().maxY) / 2.0, this.getZ());
        }

        if (!this.resting()) {
            this.move(MoverType.SELF, this.getDeltaMovement());
            this.applyEffectsFromBlocks();

            float airDrag = this.getAirDrag();
            float groundFriction = airDrag;
            if (this.onGround()) {
                groundFriction *= this.level().getBlockState(this.getBlockPosBelowThatAffectsMyMovement()).getBlock().getFriction();
            }
            Vec3 v = this.getDeltaMovement();
            this.setDeltaMovement(v.multiply(groundFriction, airDrag, groundFriction));
            if (this.onGround() && this.getDeltaMovement().y < 0.0) {
                Vec3 m = this.getDeltaMovement();
                this.setDeltaMovement(m.x, m.y * -0.5, m.z);
            }
        }

        this.simTicks++;

        // Burn model for lava (server fire ticks equivalent): items hurtable by
        // fire ignite on first lava contact and take 1 damage every 20 ticks.
        if (this.ignitedTick < 0 && this.touchingLava() && !this.fireImmune()) {
            this.ignitedTick = this.simTicks;
        }
        boolean burning = this.ignitedTick >= 0
            && (this.touchingLava() || this.simTicks - this.ignitedTick < 100)
            && !this.fireImmune();
        if (burning) {
            this.damageTicks++;
            if (this.damageTicks % 20 == 0) {
                this.simHealth--;
            }
        }

        if (!this.level().isLoaded(this.blockPosition())) {
            this.lastStop = StopReason.UNLOADED;
            return false; // chunk went out of scope; real entity freezes/vanishes there
        }
        if (this.simHealth <= 0) {
            this.lastStop = StopReason.BURNED;
            return false;
        }
        if (this.getY() < this.level().getMinY() - 32.0) {
            this.lastStop = StopReason.VOID;
            return false;
        }
        if (this.simTicks >= HORIZON_TICKS) {
            this.lastStop = StopReason.HORIZON;
            return false;
        }
        if (this.resting()) {
            this.lastStop = StopReason.REST;
            return false;
        }
        this.lastStop = StopReason.CONTINUE;
        return true;
    }
}
